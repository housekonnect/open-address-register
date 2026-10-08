package org.ugaddress.register.field.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * S3 client for the object store. Only the S3 API is used (ADR 0007).
 */
@Configuration(proxyBeanMethods = false)
class ObjectStorageConfig {

    /**
     * Path-style addressing works with any S3-compatible store. Record Store 0.2 implements neither the SDK's default
     * flexible checksums nor aws-chunked streaming uploads, so checksums are sent only when an operation requires them
     * and chunked encoding is disabled.
     */
    @Bean(destroyMethod = "close")
    S3Client s3Client(final StorageProperties properties) {
        return S3Client.builder()
            .endpointOverride(properties.endpoint())
            .region(Region.of(properties.region()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false)
                .build())
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .httpClient(UrlConnectionHttpClient.create())
            .build();
    }
}
