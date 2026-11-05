package com.inventory.api.image;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/**
 * The images bucket. Files go straight between the browser and storage through presigned URLs, so image bytes
 * never pass through the API.
 */
@Service
public class ImageStorage {

	private final S3Client s3;

	private final S3Presigner presigner;

	private final ImageStorageProperties properties;

	public ImageStorage(S3Client s3, S3Presigner presigner, ImageStorageProperties properties) {
		this.s3 = s3;
		this.presigner = presigner;
		this.properties = properties;
	}

	/**
	 * A URL the browser can PUT exactly this file to. Type and size are part of the signature on real S3.
	 */
	public PresignedUpload presignUpload(String key, String contentType, long size) {
		PutObjectRequest put = PutObjectRequest.builder()
			.bucket(properties.bucket())
			.key(key)
			.contentType(contentType)
			.contentLength(size)
			.build();
		PresignedPutObjectRequest presigned = presigner
			.presignPutObject(request -> request.signatureDuration(properties.urlTtl()).putObjectRequest(put));
		return new PresignedUpload(presigned.url().toString(), presigned.expiration());
	}

	public String downloadUrl(String key) {
		return presigner
			.presignGetObject(request -> request.signatureDuration(properties.urlTtl())
				.getObjectRequest(get -> get.bucket(properties.bucket()).key(key)))
			.url()
			.toString();
	}

	public Optional<StoredObject> find(String key) {
		try {
			var head = s3.headObject(request -> request.bucket(properties.bucket()).key(key));
			return Optional.of(new StoredObject(head.contentLength(), head.contentType()));
		}
		catch (NoSuchKeyException ex) {
			return Optional.empty();
		}
	}

	public void delete(String key) {
		s3.deleteObject(request -> request.bucket(properties.bucket()).key(key));
	}

	public long maxSizeInBytes() {
		return properties.maxSize().toBytes();
	}

	public record PresignedUpload(String url, Instant expiresAt) {
	}

	public record StoredObject(long size, String contentType) {
	}

}
