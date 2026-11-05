package com.inventory.api.dashboard;

import com.inventory.api.dashboard.DashboardResponse.LowStockItem;
import com.inventory.api.dashboard.DashboardResponse.RecentMovement;
import com.inventory.api.stock.MovementType;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only aggregates computed by Postgres; every query leaves out deleted items (A7).
 */
@Service
public class DashboardService {

	private static final int LIST_SIZE = 10;

	private static final String TOTALS = """
			SELECT count(*) AS total_items,
			       count(*) FILTER (WHERE quantity <= reorder_level) AS low_stock_count,
			       coalesce(sum(quantity), 0) AS total_units,
			       coalesce(sum(quantity * unit_price), 0) AS stock_value
			FROM items
			WHERE deleted_at IS NULL
			""";

	// Furthest below its reorder level first.
	private static final String LOW_STOCK = """
			SELECT id, sku, name, quantity, reorder_level
			FROM items
			WHERE deleted_at IS NULL AND quantity <= reorder_level
			ORDER BY quantity - reorder_level, sku
			LIMIT :limit
			""";

	private static final String RECENT_MOVEMENTS = """
			SELECT m.id, m.item_id, i.sku, i.name, m.type, m.quantity_change, m.created_by, m.created_at
			FROM stock_movements m
			JOIN items i ON i.id = m.item_id
			WHERE i.deleted_at IS NULL
			ORDER BY m.created_at DESC, m.id DESC
			LIMIT :limit
			""";

	private static final String MOVEMENTS_SINCE = """
			SELECT count(*)
			FROM stock_movements m
			JOIN items i ON i.id = m.item_id
			WHERE i.deleted_at IS NULL AND m.created_at > :since
			""";

	private final JdbcClient jdbc;

	private final Clock clock;

	public DashboardService(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	// One read-only transaction, so all the numbers come from the same snapshot of the data.
	@Transactional(readOnly = true)
	public DashboardResponse summary() {
		Totals totals = jdbc.sql(TOTALS).query(Totals.class).single();
		long movementsLast24Hours = jdbc.sql(MOVEMENTS_SINCE)
			.param("since", Timestamp.from(clock.instant().minus(Duration.ofHours(24))))
			.query(Long.class)
			.single();
		return new DashboardResponse(totals.totalItems(), totals.lowStockCount(), totals.totalUnits(),
				totals.stockValue(), movementsLast24Hours, lowStockItems(), recentMovements());
	}

	private List<LowStockItem> lowStockItems() {
		return jdbc.sql(LOW_STOCK)
			.param("limit", LIST_SIZE)
			.query((rs, row) -> new LowStockItem(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
					rs.getInt("quantity"), rs.getInt("reorder_level")))
			.list();
	}

	private List<RecentMovement> recentMovements() {
		return jdbc.sql(RECENT_MOVEMENTS)
			.param("limit", LIST_SIZE)
			.query((rs, row) -> new RecentMovement(rs.getLong("id"), rs.getLong("item_id"), rs.getString("sku"),
					rs.getString("name"), MovementType.valueOf(rs.getString("type")), rs.getInt("quantity_change"),
					rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class).toInstant()))
			.list();
	}

	record Totals(long totalItems, long lowStockCount, long totalUnits, BigDecimal stockValue) {
	}

}
