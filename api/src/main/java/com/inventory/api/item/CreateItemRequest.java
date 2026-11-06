package com.inventory.api.item;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

public record CreateItemRequest(
		@NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9._-]*",
				message = "may contain only letters, digits, '.', '_' and '-'") String sku,
		@NotBlank @Size(max = 200) String name, @NotBlank @Size(max = 100) String category,
		@NotNull @Min(0) Integer quantity, @NotNull @Min(0) Integer reorderLevel,
		@NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2,
				message = "must have at most 8 digits before and 2 after the decimal point") BigDecimal unitPrice,
		@Size(max = 200) String imageKey, List<AttributeRequest> attributes, List<String> deliveryPincodes) {

	// Normalised before validation: SKUs are compared trimmed and in uppercase (A32).
	public CreateItemRequest {
		sku = sku == null ? null : sku.trim().toUpperCase(Locale.ROOT);
		name = name == null ? null : name.trim();
		category = category == null ? null : category.trim();
		// An image already uploaded for the new item (A36), if any.
		imageKey = imageKey == null || imageKey.isBlank() ? null : imageKey.trim();
	}

}
