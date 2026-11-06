package com.inventory.api.importing;

import java.math.BigDecimal;

/**
 * A CSV row that passed every check, ready to apply.
 */
record ImportRow(String sku, String name, String category, int quantity, int reorderLevel, BigDecimal unitPrice) {
}
