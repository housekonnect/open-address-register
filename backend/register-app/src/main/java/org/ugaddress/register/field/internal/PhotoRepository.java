package org.ugaddress.register.field.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Stores field photos in the object store. Keys are deterministic per capture, so a retried upload overwrites the
 * same object instead of creating a second one.
 */
@Repository
public class PhotoRepository {

    private static final Logger LOG = LoggerFactory.getLogger(PhotoRepository.class);

    private final S3Client s3;
    private final String bucket;

    PhotoRepository(final S3Client s3, final StorageProperties properties) {
        this.s3 = s3;
        this.bucket = properties.bucket();
    }

    /**
     * Stores a photo, creating the bucket on first use.
     *
     * @param key object key
     * @param content photo bytes
     * @param contentType {@code image/jpeg} or {@code image/png}
     */
    public void store(final String key, final byte[] content, final String contentType) {
        final PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(contentType)
            .contentLength((long) content.length)
            .build();
        try {
            s3.putObject(request, RequestBody.fromBytes(content));
        } catch (final NoSuchBucketException e) {
            LOG.info("Creating bucket {}", bucket);
            s3.createBucket(b -> b.bucket(bucket));
            s3.putObject(request, RequestBody.fromBytes(content));
        }
    }

    /**
     * Checks whether an object exists.
     *
     * @param key object key
     * @return {@code true} if it exists
     */
    public boolean exists(final String key) {
        try {
            s3.headObject(b -> b.bucket(bucket).key(key));
            return true;
        } catch (final NoSuchKeyException e) {
            return false;
        }
    }
}
