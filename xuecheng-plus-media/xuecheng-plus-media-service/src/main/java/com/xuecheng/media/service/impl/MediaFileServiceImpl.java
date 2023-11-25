package com.xuecheng.media.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.j256.simplemagic.ContentInfo;
import com.j256.simplemagic.ContentInfoUtil;
import com.xuecheng.base.exception.XueChengPlusException;
import com.xuecheng.base.model.PageParams;
import com.xuecheng.base.model.PageResult;
import com.xuecheng.media.config.MinioConfig;
import com.xuecheng.media.mapper.MediaFilesMapper;
import com.xuecheng.media.model.dto.QueryMediaParamsDto;
import com.xuecheng.media.model.dto.UploadFileParamsDto;
import com.xuecheng.media.model.dto.UploadFileResultDto;
import com.xuecheng.media.model.po.MediaFiles;
import com.xuecheng.media.service.MediaFileService;
import io.minio.BucketArgs;
import io.minio.MinioClient;
import io.minio.UploadObjectArgs;
import io.minio.errors.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.x509.extension.X509ExtensionUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cglib.core.MethodWrapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.swing.plaf.metal.MetalDesktopIconUI;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

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
  @Value("${mino.bucket.videofiles}")
  private String bucketVedio;

 @Override
 public PageResult<MediaFiles> queryMediaFiels(Long companyId,PageParams pageParams, QueryMediaParamsDto queryMediaParamsDto) {

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
   log.debug("上传文件到minio成功,bucket:{}, objectname:{}, 错误信息:{}",bucket,objectName);
   return true;
  } catch (Exception e) {
   e.printStackTrace();
   log.error("上传文件出错,bucket:{}, objectname:{}, 错误信息:{},",bucket,objectName,e.getMessage());
  }
  return false;
 }
 private String getDefaultFolderPath() {
  SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
  String folder =  simpleDateFormat.format(new Date()).replace("-","/")+ "/";
  return folder;
 }
 private String getFileMd5(File file) {
  try {
   FileInputStream fileInputStream = new FileInputStream(file);
   String md5Hex = DigestUtils.md5Hex(fileInputStream);
   return md5Hex;
  } catch (Exception e) {
    e.printStackTrace();
    return null;
  }

 }
 @Transactional
 @Override
 public UploadFileResultDto uploadFile(Long companyId, UploadFileParamsDto uploadFileParamsDto, String localFilePath) {
  //将文件上传到minio
  String filename = uploadFileParamsDto.getFilename();
  String extension = filename.substring(filename.lastIndexOf("."));
  String mimeType = getMimeType(extension);
  String defaultFolderPath = getDefaultFolderPath();
  String fileMd5 = getFileMd5(new File(filename));
  String objectName = defaultFolderPath + fileMd5 + extension;
  boolean result = addMediaFilesToMinio(localFilePath, mimeType, bucketMediaFiles, objectName);
  if (!result) {
   XueChengPlusException.cast("上传文件到minio失败");
  }
  //将文件信息保存到数据库
  MediaFiles mediaFiles = addMediaFilesToDb(companyId, fileMd5, uploadFileParamsDto, bucketMediaFiles, objectName);
  if (mediaFiles == null) {
   XueChengPlusException.cast("文件上传失败");
  }
  UploadFileResultDto uploadFileResultDto = new UploadFileResultDto();
  BeanUtils.copyProperties(mediaFiles, uploadFileResultDto);
  return uploadFileResultDto;
 }



 public MediaFiles addMediaFilesToDb(Long companyId, String fileMd5, UploadFileParamsDto uploadFileParamsDto, String bucket,String objectName ) {
  //将文件信息保存到数据库
  MediaFiles mediaFiles = mediaFilesMapper.selectById(fileMd5);
  if (mediaFiles == null) {
   MediaFiles mediaFiles1 = new MediaFiles();
   BeanUtils.copyProperties(uploadFileParamsDto, mediaFiles1);
   mediaFiles1.setFileId(fileMd5);
   mediaFiles1.setCompanyId(companyId);
   mediaFiles1.setBucket(bucketMediaFiles);
   mediaFiles1.setBucket(bucket);
   mediaFiles1.setFilePath(objectName);
   mediaFiles1.setUrl("/" + bucket + objectName);
   mediaFiles1.setCreateDate(LocalDateTime.now());
   mediaFiles1.setStatus("1");
   mediaFiles1.setAuditStatus("002003");
   //插入数据库
   int insert = mediaFilesMapper.insert(mediaFiles1);
   if (insert <= 0) {
       log.debug("文件保存到数据库失败,bucket:{},objectName:{}",bucket,objectName);
       return null;
   }
   return mediaFiles1;
  }
  return mediaFiles;
 }
}
