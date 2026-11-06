package com.inventory.api.item;

import java.util.List;

/**
 * An attribute name in use and its values, for the list filter.
 */
public record AttributeOptions(String name, List<String> values) {
}
