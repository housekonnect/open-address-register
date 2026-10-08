package org.ugaddress.register.field.internal;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3-compatible object storage (Record Store, or Garage as a stand-in). All values come from the environment.
 *
 * @param endpoint S3 endpoint, e.g. {@code http://record-store:7600}
 * @param region signing region
 * @param bucket bucket for field photos
 * @param accessKey access key id
 * @param secretKey secret access key
 */
@ConfigurationProperties("register.storage")
record StorageProperties(URI endpoint, String region, String bucket, String accessKey, String secretKey) {
}
