package com.inventory.api.image;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(ImageStorageProperties.class)
public class ImageStorageConfig {

	// S3Mock accepts any credentials; these are not secrets.
	private static final StaticCredentialsProvider LOCAL_CREDENTIALS = StaticCredentialsProvider
		.create(AwsBasicCredentials.create("local", "local"));

	@Bean
	S3Client s3Client(ImageStorageProperties properties) {
		S3ClientBuilder builder = S3Client.builder()
			.region(Region.of(properties.region()))
			// Only add checksums when S3 requires them, so presigned browser uploads stay plain PUTs.
			.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED);
		if (isLocal(properties)) {
			builder.endpointOverride(URI.create(properties.endpoint()))
				.forcePathStyle(true)
				.credentialsProvider(LOCAL_CREDENTIALS);
		}
		return builder.build();
	}

	@Bean
	S3Presigner s3Presigner(ImageStorageProperties properties) {
		S3Presigner.Builder builder = S3Presigner.builder().region(Region.of(properties.region()));
		if (isLocal(properties)) {
			builder.endpointOverride(URI.create(properties.endpoint()))
				.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
				.credentialsProvider(LOCAL_CREDENTIALS);
		}
		return builder.build();
	}

	private static boolean isLocal(ImageStorageProperties properties) {
		return StringUtils.hasText(properties.endpoint());
	}

}
