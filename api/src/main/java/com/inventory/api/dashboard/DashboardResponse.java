package com.inventory.api.dashboard;

import com.inventory.api.stock.MovementType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Stock overview across all items that are not deleted (A7).
 */
public record DashboardResponse(long totalItems, long lowStockCount, long totalUnits, BigDecimal stockValue,
		long movementsLast24Hours, List<LowStockItem> lowStockItems, List<RecentMovement> recentMovements) {

	public record LowStockItem(Long id, String sku, String name, int quantity, int reorderLevel) {
	}

	public record RecentMovement(Long id, Long itemId, String sku, String itemName, MovementType type,
			int quantityChange, String createdBy, Instant createdAt) {
	}

}
