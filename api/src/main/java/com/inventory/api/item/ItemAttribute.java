package com.inventory.api.item;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * A free-form detail of an item, such as Colour = Blue (A40).
 */
@Embeddable
public record ItemAttribute(@Column(name = "name", nullable = false, length = 50) String name,
		@Column(name = "value", nullable = false, length = 100) String value) {
}
