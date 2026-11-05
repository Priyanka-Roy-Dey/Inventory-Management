package com.inventory.api.item;

import com.inventory.api.image.ImageStorage;
import org.springframework.stereotype.Component;

/**
 * Builds item responses, including a short-lived download URL for the item's image.
 */
@Component
public class ItemResponses {

	private final ImageStorage images;

	public ItemResponses(ImageStorage images) {
		this.images = images;
	}

	public ItemResponse of(Item item) {
		return ItemResponse.from(item, item.getImageKey() == null ? null : images.downloadUrl(item.getImageKey()));
	}

}
