package com.soukly.listing.photo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 client and presigner (AWS SDK for Java v2). Path-style access, since Garage and the S3 compatibility API of
 * Oracle Object Storage do not serve buckets as subdomains. Checksums are only computed when an operation requires
 * them: the default CRC32 checksums of recent SDK versions are not supported by every S3-compatible storage.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ StorageProperties.class, PhotoProperties.class })
class StorageConfig {

    @Bean(destroyMethod = "close")
    S3Client s3Client(StorageProperties storage) {
        return S3Client.builder()
                .endpointOverride(storage.endpoint())
                .region(Region.of(storage.region()))
                .credentialsProvider(credentials(storage))
                .forcePathStyle(true)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    /** Signs URLs for the host the browsers reach, which the signature covers. */
    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(StorageProperties storage) {
        return S3Presigner.builder()
                .endpointOverride(storage.publicEndpoint() == null ? storage.endpoint() : storage.publicEndpoint())
                .region(Region.of(storage.region()))
                .credentialsProvider(credentials(storage))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private static StaticCredentialsProvider credentials(StorageProperties storage) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(storage.accessKey(), storage.secretKey()));
    }
}
