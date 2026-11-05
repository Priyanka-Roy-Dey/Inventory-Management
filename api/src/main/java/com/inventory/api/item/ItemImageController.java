package com.inventory.api.item;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff or Admin (see {@code SecurityConfig}). The upload is attached by a stock-in that names its key, and read
 * through {@code imageUrl} on the item.
 */
@RestController
@RequestMapping("/api/items/{itemId}/image")
public class ItemImageController {

	private final ItemImageService images;

	public ItemImageController(ItemImageService images) {
		this.images = images;
	}

	@PostMapping("/upload-url")
	public ImageUploadResponse requestUpload(@PathVariable Long itemId, @Valid @RequestBody ImageUploadRequest request) {
		return images.requestUpload(itemId, request);
	}

}
