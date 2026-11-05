package com.inventory.api.item;

import java.math.BigDecimal;
import java.time.Instant;

public record ItemResponse(Long id, String sku, String name, String category, int quantity, int reorderLevel,
		BigDecimal unitPrice, boolean lowStock, long version, Instant createdAt, Instant updatedAt,
		String imageUrl) {

	/**
	 * Use {@link ItemResponses#of(Item)}, which also signs the image URL.
	 */
	static ItemResponse from(Item item, String imageUrl) {
		return new ItemResponse(item.getId(), item.getSku(), item.getName(), item.getCategory(), item.getQuantity(),
				item.getReorderLevel(), item.getUnitPrice(), item.isLowStock(), item.getVersion(),
				item.getCreatedAt(), item.getUpdatedAt(), imageUrl);
	}

}
