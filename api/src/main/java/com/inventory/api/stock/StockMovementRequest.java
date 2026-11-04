package com.inventory.api.stock;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * For IN and OUT, {@code quantity} is how many units arrive or leave. For ADJUST it is a signed correction, and a
 * reason is required (A34).
 */
public record StockMovementRequest(@NotNull MovementType type,
		@NotNull @Min(-1_000_000) @Max(1_000_000) Integer quantity, @Size(max = 500) String reason) {

	public StockMovementRequest {
		reason = reason == null || reason.isBlank() ? null : reason.trim();
	}

}
