package com.inventory.api.item;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff or Admin (see {@code SecurityConfig}). The upload is attached by the request that names its key (a stock-in
 * for an existing item, or creating a new item), and read through {@code imageUrl} on the item.
 */
@RestController
@RequestMapping("/api/items")
public class ItemImageController {

	private final ItemImageService images;

	public ItemImageController(ItemImageService images) {
		this.images = images;
	}

	@PostMapping("/{itemId}/image/upload-url")
	public ImageUploadResponse requestUpload(@PathVariable Long itemId, @Valid @RequestBody ImageUploadRequest request) {
		return images.requestUpload(itemId, request);
	}

	@PostMapping("/image/upload-url")
	public ImageUploadResponse requestNewItemUpload(@Valid @RequestBody ImageUploadRequest request) {
		return images.requestNewItemUpload(request);
	}

}
