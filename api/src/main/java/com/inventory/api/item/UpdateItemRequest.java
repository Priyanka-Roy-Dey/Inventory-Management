package com.inventory.api.item;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * The editable fields, plus the {@code version} the client last read (optimistic locking, A17).
 */
public record UpdateItemRequest(@NotBlank @Size(max = 200) String name, @NotBlank @Size(max = 100) String category,
		@NotNull @Min(0) Integer reorderLevel,
		@NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2,
				message = "must have at most 8 digits before and 2 after the decimal point") BigDecimal unitPrice,
		@NotNull Long version) {

	public UpdateItemRequest {
		name = name == null ? null : name.trim();
		category = category == null ? null : category.trim();
	}

}
