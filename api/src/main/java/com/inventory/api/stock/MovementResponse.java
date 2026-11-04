package com.inventory.api.stock;

import java.time.Instant;

public record MovementResponse(Long id, MovementType type, int quantityChange, int quantityBefore, int quantityAfter,
		String reason, String createdBy, Instant createdAt) {

	static MovementResponse from(StockMovement movement) {
		return new MovementResponse(movement.getId(), movement.getType(), movement.getQuantityChange(),
				movement.getQuantityBefore(), movement.getQuantityAfter(), movement.getReason(),
				movement.getCreatedBy(), movement.getCreatedAt());
	}

}
