package com.inventory.api.importing;

/**
 * A CSV row that was not imported. {@code row} counts the header as row 1, like a spreadsheet.
 */
public record RowError(long row, String sku, String message) {
}
