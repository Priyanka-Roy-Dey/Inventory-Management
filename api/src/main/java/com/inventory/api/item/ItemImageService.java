package com.inventory.api.item;

import com.inventory.api.image.ImageStorage;
import com.inventory.api.image.ImageStorage.PresignedUpload;
import com.inventory.api.image.ImageStorage.StoredObject;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * Images arrive with stock (A36): the API hands out a presigned URL, the browser uploads to storage, and a stock-in
 * that names the upload checks the stored file and attaches it to the item.
 */
@Service
public class ItemImageService {

	private static final Map<String, String> EXTENSIONS = Map.of("image/jpeg", "jpg", "image/png", "png",
			"image/webp", "webp");

	private static final String ALLOWED_TYPES = "image/jpeg, image/png or image/webp";

	private final ItemRepository items;

	private final ImageStorage storage;

	public ItemImageService(ItemRepository items, ImageStorage storage) {
		this.items = items;
		this.storage = storage;
	}

	public ImageUploadResponse requestUpload(Long itemId, ImageUploadRequest request) {
		findActive(itemId);
		String extension = EXTENSIONS.get(request.contentType());
		if (extension == null) {
			throw badRequest("contentType: must be " + ALLOWED_TYPES);
		}
		if (request.size() > storage.maxSizeInBytes()) {
			throw badRequest("size: must be at most " + maxSizeText());
		}
		String key = keyPrefix(itemId) + UUID.randomUUID() + "." + extension;
		PresignedUpload upload = storage.presignUpload(key, request.contentType(), request.size());
		return new ImageUploadResponse(upload.url(), key, request.contentType(), upload.expiresAt());
	}

	/**
	 * Checks the file the browser uploaded for this item. A file that breaks the rules is deleted.
	 */
	public void checkUpload(Long itemId, String key) {
		if (!key.startsWith(keyPrefix(itemId))) {
			throw badRequest("Image does not belong to this item");
		}
		StoredObject stored = storage.find(key)
			.orElseThrow(() -> badRequest("Upload not found. Upload the image first."));
		// The browser could have sent a different file than it asked for, so check what actually arrived.
		if (stored.size() > storage.maxSizeInBytes()) {
			storage.delete(key);
			throw badRequest("Image is larger than " + maxSizeText());
		}
		if (!EXTENSIONS.containsKey(stored.contentType())) {
			storage.delete(key);
			throw badRequest("Image must be " + ALLOWED_TYPES);
		}
	}

	/**
	 * Makes {@code key} the item's image, replacing the previous one. Call inside the caller's transaction, after
	 * {@link #checkUpload}.
	 */
	public void attach(Item item, String key) {
		String previous = item.getImageKey();
		item.attachImage(key);
		if (previous != null && !previous.equals(key)) {
			deleteAfterCommit(previous);
		}
	}

	// Only remove the old file once the database no longer points at it.
	private void deleteAfterCommit(String key) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				storage.delete(key);
			}
		});
	}

	private Item findActive(Long itemId) {
		return items.findByIdAndDeletedAtIsNull(itemId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
	}

	private static String keyPrefix(Long itemId) {
		return "items/" + itemId + "/";
	}

	private String maxSizeText() {
		return storage.maxSizeInBytes() / (1024 * 1024) + " MB";
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

}
