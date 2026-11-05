package com.inventory.api.image;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Where item images are stored. An empty {@code endpoint} means real S3 with the default AWS credentials; locally
 * it points at the S3Mock container.
 */
@ConfigurationProperties("inventory.images")
public record ImageStorageProperties(String bucket, String endpoint, String region, DataSize maxSize,
		Duration urlTtl) {
}
