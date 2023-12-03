package com.xuecheng.media.service.impl;

import com.alibaba.nacos.common.utils.IoUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.j256.simplemagic.ContentInfo;
import com.j256.simplemagic.ContentInfoUtil;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.base.model.PageParams;
import com.xuecheng.base.model.PageResult;
import com.xuecheng.media.mapper.MediaFilesMapper;
import com.xuecheng.media.model.dto.QueryMediaParamsDto;
import com.xuecheng.media.model.dto.UploadFileParamsDto;
import com.xuecheng.media.model.dto.UploadFileResultDto;
import com.xuecheng.media.model.po.MediaFiles;
import com.xuecheng.media.service.MediaFileService;
import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import lombok.extern.slf4j.Slf4j;
import model.RestResponse;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.omg.PortableInterceptor.INACTIVE;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * @description TODO
 * @author Mr.M
 * @date 2022/9/10 8:58
 * @version 1.0
 */
 @Service
 @Slf4j
public class MediaFileServiceImpl implements MediaFileService {

 @Autowired
 MediaFilesMapper mediaFilesMapper;
 @Autowired
 MinioClient minioClient;
 //存储普通文件
 @Value("${minio.bucket.files}")
 private String bucketMediaFiles;
 //存储mp4文件
//  @Value("${minio.bucket.videofiles}")
//  private String bucketVedio;
 @Value("${minio.bucket.videofiles}")
 private String bucketVideo;

 @Override
 public PageResult<MediaFiles> queryMediaFiels(Long companyId, PageParams pageParams, QueryMediaParamsDto queryMediaParamsDto) {

  //构建查询条件对象
  LambdaQueryWrapper<MediaFiles> queryWrapper = new LambdaQueryWrapper<>();

  //分页对象
  Page<MediaFiles> page = new Page<>(pageParams.getPageNo(), pageParams.getPageSize());
  // 查询数据内容获得结果
  Page<MediaFiles> pageResult = mediaFilesMapper.selectPage(page, queryWrapper);
  // 获取数据列表
  List<MediaFiles> list = pageResult.getRecords();
  // 获取数据总数
  long total = pageResult.getTotal();
  // 构建结果集
  PageResult<MediaFiles> mediaListResult = new PageResult<>(list, total, pageParams.getPageNo(), pageParams.getPageSize());
  return mediaListResult;

 }

 private String getMimeType(String extension) {
  if (extension == null) {
   extension = "";
  }
  ContentInfo extensionMatch = ContentInfoUtil.findExtensionMatch(extension);
  String mimeType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
  if (extensionMatch != null) {
   mimeType = extensionMatch.getMimeType();
  }
  return mimeType;
 }

 public boolean addMediaFilesToMinio(String localFilePath, String mimeType, String bucket, String objectName) {
  try {
   minioClient.uploadObject(UploadObjectArgs.builder()
           .bucket(bucket)
           .filename(localFilePath)
           .object(objectName)
           .contentType(mimeType)
           .build());
   log.debug("上传文件到minio成功,bucket:{}, objectname:{}, 错误信息:{}", bucket, objectName);
   return true;
  } catch (Exception e) {
   e.printStackTrace();
   log.error("上传文件出错,bucket:{}, objectname:{}, 错误信息:{},", bucket, objectName, e.getMessage());
  }
  return false;
 }

 private String getFileFolder() {
  SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
  String folder = simpleDateFormat.format(new Date()).replace("-", "/") + "/";
  return folder;
 }


 @Override
 public UploadFileResultDto uploadFile(Long companyId, UploadFileParamsDto uploadFileParamsDto, byte[] bytes, String folder, String objectName) {
  String fileMD5 = DigestUtils.md5Hex(bytes);
  if (StringUtils.isEmpty(folder)) {
   // 如果目录不存在，则自动生成一个目录
   folder = getFileFolder();
  }
  if (StringUtils.isEmpty(objectName)) {
   // 如果文件名为空，则设置其默认文件名为文件的md5码 + 文件后缀名
   String filename = uploadFileParamsDto.getFilename();
   objectName = fileMD5 + filename.substring(filename.lastIndexOf("."));
  }
  objectName = folder + objectName;
  try {
   ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
   // 上传到minio
   minioClient.putObject(PutObjectArgs.builder()
           .bucket(bucketMediaFiles)
           .object(objectName)
           .stream(byteArrayInputStream, byteArrayInputStream.available(), -1)
           .contentType(uploadFileParamsDto.getContentType())
           .build());
   // 保存到数据库
   MediaFiles mediaFiles = addMediaFilesToDb(companyId, uploadFileParamsDto, objectName, fileMD5);
   UploadFileResultDto uploadFileResultDto = new UploadFileResultDto();
   BeanUtils.copyProperties(mediaFiles, uploadFileResultDto);
   return uploadFileResultDto ;
  } catch (Exception e) {
   XueChengPlusException.cast("上传过程中出错");
  }
  return null;
 }

@Transactional
public MediaFiles addMediaFilesToDb(Long companyId, UploadFileParamsDto uploadFileParamsDto, String objectName, String fileMD5) {
 MediaFiles mediaFiles = mediaFilesMapper.selectById(fileMD5);
 if (mediaFiles == null) {
  mediaFiles = new MediaFiles();
  BeanUtils.copyProperties(uploadFileParamsDto, mediaFiles);
  mediaFiles.setId(fileMD5);
  mediaFiles.setFileId(fileMD5);
  mediaFiles.setCompanyId(companyId);
  mediaFiles.setBucket(bucketMediaFiles);
  mediaFiles.setCreateDate(LocalDateTime.now());
  mediaFiles.setStatus("1");
  mediaFiles.setFilePath(objectName);
  mediaFiles.setUrl("/" + bucketMediaFiles + "/" + objectName);
  // 查阅数据字典，002003表示审核通过
  mediaFiles.setAuditStatus("002003");
  int insert = mediaFilesMapper.insert(mediaFiles);
  if (insert <= 0) {
   log.debug("向数据库保存文件失败,bucket:{},objectName:{}", bucketMediaFiles, objectName);
   return null;
  }
  return mediaFiles;
 }
 return mediaFiles;
}
 @Override
 public RestResponse<Boolean> checkFile(String fileMd5) {
  MediaFiles mediaFiles = mediaFilesMapper.selectById(fileMd5);
  // 数据库中不存在，则直接返回false 表示不存在
  if (mediaFiles == null) {
   return RestResponse.success(false);
  }
  // 若数据库中存在，根据数据库中的文件信息，则继续判断bucket中是否存在
  try {
   InputStream inputStream = minioClient.getObject(GetObjectArgs
           .builder()
           .bucket(mediaFiles.getBucket())
           .object(mediaFiles.getFilePath())
           .build());
   if (inputStream == null) {
    return RestResponse.success(false);
   }
  } catch (Exception e) {
   return RestResponse.success(false);
  }
  return RestResponse.success(true);
 }

 @Override
 public RestResponse<Boolean> checkChunk(String fileMd5, int chunkIndex) {
  // 获取分块目录
  String chunkFileFolderPath = getChunkFileFolderPath(fileMd5);
  String chunkFilePath = chunkFileFolderPath + chunkIndex;
  try {
   // 判断分块是否存在
   InputStream inputStream = minioClient.getObject(GetObjectArgs
           .builder()
           .bucket(bucketVideo)
           .object(chunkFilePath)
           .build());
   // 不存在返回false
   if (inputStream == null) {
    return RestResponse.success(false);
   }
  } catch (Exception e) {
   // 出异常也返回false
   return RestResponse.success(false);
  }
  // 否则返回true
  return RestResponse.success();
 }

 private String getChunkFileFolderPath(String fileMd5) {
  return fileMd5.substring(0, 1) + "/" + fileMd5.substring(1, 2) + "/" + fileMd5 + "/" + "chunk" + "/";
 }

 /**
  * 根据md5值获取合并后的文件路径
  *
  * @param fileMd5
  * @param fileExt
  * @return
  */
 private String getFilePathByMd5(String fileMd5, String fileExt) {
  return fileMd5.substring(0, 1) + "/" + fileMd5.substring(1, 2) + "/" + fileMd5 + fileExt;
 }

 @Override
 public RestResponse uploadChunk(String fileMd5, int chunk, String localChunkFilePath) {
  // 分块文件路径
  String chunkFilePath = getChunkFileFolderPath(fileMd5) + chunk;
  String mimeType = getMimeType(null);
  boolean b = addMediaFilesToMinio(localChunkFilePath, mimeType, bucketVideo, chunkFilePath);
  if (!b) {
   return RestResponse.validfail("上传分片文件失败", false);

  }
  return RestResponse.success(true);
 }

 public RestResponse mergechunks(Long companyId, String fileMd5, int chunkTotal, UploadFileParamsDto uploadFileParamsDto) {
  //找到分块文件调用minio的SDK进行文件合并
  List<ComposeSource> sources = new ArrayList<>();
  //分块文件所在目录
  String chunkFileFolderPath = getChunkFileFolderPath(fileMd5);
  for (int i = 0; i < chunkTotal; i++) {
   ComposeSource composeSource = ComposeSource.builder()
           .bucket(bucketVideo)
           .object(chunkFileFolderPath + i)
           .build();
   sources.add(composeSource);
  }
  String filename = uploadFileParamsDto.getFilename();
  String extension = filename.substring(filename.lastIndexOf("."));
  String objectName = getFilePathByMd5(fileMd5, extension);
  try {
   minioClient.composeObject(ComposeObjectArgs.builder()
           .bucket(bucketVideo)
           .object(objectName)
           .sources(sources)
           .build());
  } catch (Exception e) {
   e.printStackTrace();
   log.error("合并文件出错,bucket:{},objectName:{},错误信息:{}", bucketVideo, objectName, e.getMessage());
   return RestResponse.validfail("合并文件异常", false);
  }

  //检验合并后的文件和源文件是否一致
  File file = downloadFileFromMinio(bucketVideo, objectName);
  try {
   FileInputStream fileInputStream = new FileInputStream(file);
   String mergeFile_md5 = DigestUtils.md5Hex(fileInputStream);
   if (!fileMd5.equals(mergeFile_md5)) {
    log.error("文件校验失败,原始文件:{},合并文件:{}", fileMd5, mergeFile_md5);
    return RestResponse.validfail("文件校验失败", false);
   }
   uploadFileParamsDto.setFileSize(file.length());
  } catch (Exception e) {
   return RestResponse.validfail("文件校验失败", false);
  }
  //将文件信息入库
  MediaFiles mediaFiles = addMediaFilesToDb(companyId, uploadFileParamsDto, objectName, fileMd5);
  if (mediaFiles == null) {
   return RestResponse.validfail("文件入库失败", false);
  }
  // TODO: 2023/12/3  
//  //清理分块文件
//  try {
//   clearChunkFiles(chunkFileFolderPath, chunkTotal);
//  } catch (ServerException | InsufficientDataException | ErrorResponseException | IOException |
//           NoSuchAlgorithmException | InvalidKeyException | InvalidResponseException | XmlParserException |
//           InternalException e) {
//   throw new RuntimeException(e);
//  }
  return RestResponse.success(true  );
 }
// TODO: 2023/12/3 清理分片文件 
//public void clearChunkFiles(String chunkFileFolderPath, int chunkTotal) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
// List<DeleteObject> objects = Stream.iterate(0, i->++i).limit(chunkTotal).map(i->new  DeleteObject(chunkFileFolderPath.concat(Integer.toString(i)))).collect(Collectors.toList());
// RemoveObjectArgs removeObjectArgs = RemoveObjectArgs.builder()
//         .bucket(bucketVideo)
//         .object(objects)
//         .build();
//
//  Iterable<Result<DeleteError>> results = minioClient.removeObjects(removeObjectArgs);
//  results.forEach(r ->{
//   DeleteError deleteError = null;
//   try {
//    deleteError = r.get();
//   } catch (Exception e) {
//    e.printStackTrace();
//   }
//  });
//}

 public File downloadFileFromMinio(String bucket, String objectName) {
  File minioFile = null;
  FileOutputStream outputStream = null;
  try {
   InputStream stream = minioClient.getObject(GetObjectArgs.builder()
           .bucket(bucket)
           .object(objectName)
           .build());
   minioFile = File.createTempFile("minio", ".merge");
   outputStream = new FileOutputStream(minioFile);
   IoUtils.copy(stream, outputStream);
   return minioFile;
  } catch (Exception e) {
   e.printStackTrace();
  }
  return minioFile;
 }
}
