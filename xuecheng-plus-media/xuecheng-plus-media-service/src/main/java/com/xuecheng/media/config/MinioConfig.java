package com.xuecheng.media.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * ClassName: MinioConfig
 * Description:
 *
 * @Author HappyPig
 * @Create 2023/11/22 17:16
 * @Version 1.0
// */
@Configuration
public class MinioConfig {
    @Value("${minio.endpoint}")
    private String endpoint;
    @Value("${minio.accessKey}")
    private String accessKey;
    @Value("${minio.secretKey}")
    private String secretKey;
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder().
                endpoint(endpoint).
                credentials(accessKey, secretKey).
                build();
    }
}