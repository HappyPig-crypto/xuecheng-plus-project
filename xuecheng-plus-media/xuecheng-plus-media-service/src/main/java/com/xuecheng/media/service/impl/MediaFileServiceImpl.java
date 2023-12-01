package com.xuecheng.media.service.impl;

import cn.hutool.crypto.digest.MD5;
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
import io.minio.PutObjectArgs;
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
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.io.File;
import java.io.FileInputStream;
import org.apache.commons.codec.digest.DigestUtils;


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
  @Value("${minio.bucket.videofiles}")
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
 private String getFileFolder() {
  SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
  String folder =  simpleDateFormat.format(new Date()).replace("-","/")+ "/";
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
   }
   int insert = mediaFilesMapper.insert(mediaFiles);
   if (insert <= 0) {
    XueChengPlusException.cast("保存文件信息失败");
   }
   UploadFileResultDto uploadFileResultDto = new UploadFileResultDto();
   BeanUtils.copyProperties(mediaFiles, uploadFileResultDto);
   return uploadFileResultDto;
  } catch (Exception e) {
   XueChengPlusException.cast("上传过程中出错");
  }
  return null;
 }


}
