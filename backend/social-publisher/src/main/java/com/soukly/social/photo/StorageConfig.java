package com.soukly.social.photo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * S3 client (AWS SDK for Java v2), configured like the one of listing-service (ANN-02): path-style access, checksums
 * only when an operation requires them.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ StorageProperties.class, PhotoProperties.class })
class StorageConfig {

    @Bean(destroyMethod = "close")
    S3Client s3Client(StorageProperties storage) {
        return S3Client.builder()
                .endpointOverride(storage.endpoint())
                .region(Region.of(storage.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(storage.accessKey(), storage.secretKey())))
                .forcePathStyle(true)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }
}
