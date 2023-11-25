package com.xuecheng.media;

import com.alibaba.nacos.common.utils.IPUtil;
import com.alibaba.nacos.common.utils.IoUtils;
import io.minio.*;
import io.minio.errors.*;
import jdk.management.resource.internal.inst.SocketOutputStreamRMHooks;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.InputStreamSource;
import org.springframework.util.DigestUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * ClassName: MinioTest
 * Description:
 *
 * @Author HappyPig
 * @Create 2023/11/25 10:26
 * @Version 1.0
 */

public class MinioTest {
    MinioClient minioClient =
            MinioClient.builder()
                    .endpoint("http://127.0.0.1:9000")
                    .credentials("minioadmin", "minioadmin")
                    .build();
    @Test
    public void testUpload() {
        try {
            minioClient.uploadObject(
                    UploadObjectArgs.builder()
                            .bucket("testbucket")
                            .object("67b8090161e22daa.jpg")    // 同一个桶内对象名不能重复
                            .filename("D:\\123\\67b8090161e22daa.jpg")
                            .build()
            );
            System.out.println("上传成功");
        } catch (Exception e) {
            System.out.println("上传失败");
        }
    }

    @Test
    public void deleteTest() {
        try {
            minioClient.removeObject(RemoveObjectArgs
                    .builder()
                    .bucket("testbucket")
                    .object("pic01.cpp")
                    .build());
            System.out.println("删除成功");
        } catch (Exception e) {
            System.out.println("删除失败");
        }
    }
    @Test
    public void getFileTest() {
        try {
            InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket("testbucket")
                    .object("67b8090161e22daa.jpg")
                    .build());
            FileOutputStream fileOutputStream = new FileOutputStream("D:\\123\\123.jpg");
            IOUtils.copy(inputStream,fileOutputStream);
            String source_md5 = DigestUtils.md5DigestAsHex(inputStream);
            String local_md5 = DigestUtils.md5DigestAsHex(new FileInputStream(("D:\\123\\123.jpg")));
            if (source_md5.equals(local_md5)){
                System.out.println("下载成功");
            }
        } catch (Exception e) {
            System.out.println("下载失败");
        }
    }
}

