package com.inventory.api.item;

import java.util.List;

/**
 * One page of the item list. {@code page} counts from 0.
 */
public record ItemPage(List<ItemResponse> items, int page, int size, long totalItems, int totalPages) {
}
