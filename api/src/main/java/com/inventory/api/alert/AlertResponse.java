package com.inventory.api.alert;

import java.time.Instant;

/**
 * A low-stock alert: the item as it was when it became low, and as it is now.
 */
public record AlertResponse(String id, Long itemId, String sku, String itemName, int quantity, int reorderLevel,
		String cause, Instant occurredAt, int currentQuantity, int currentReorderLevel, boolean stillLow) {
}
