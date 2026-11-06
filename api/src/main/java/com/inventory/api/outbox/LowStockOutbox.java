package com.inventory.api.outbox;

import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a LOW_STOCK event when an item becomes low (design note §2.3, A13, A14). The caller's transaction makes
 * the change and the event one unit: both are saved, or neither is (§2.5).
 */
@Service
public class LowStockOutbox {

	public enum Cause {

		STOCK_MOVEMENT, CREATED, REORDER_LEVEL_RAISED

	}

	// The payload is built from the item row as it is inside the caller's transaction.
	private static final String INSERT = """
			INSERT INTO outbox_events (event_id, event_type, aggregate_id, payload)
			SELECT CAST(:eventId AS uuid), 'LOW_STOCK', id,
			       jsonb_build_object('eventId', CAST(:eventId AS text), 'itemId', id, 'sku', sku, 'name', name,
			                          'quantity', quantity, 'reorderLevel', reorder_level,
			                          'cause', CAST(:cause AS text), 'occurredAt', CAST(:occurredAt AS text))
			FROM items
			WHERE id = :itemId
			""";

	private final JdbcClient jdbc;

	private final Clock clock;

	public LowStockOutbox(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/**
	 * True when a change took the quantity from above the reorder level to at or below it. Staying low is not a
	 * crossing, so it does not alert again.
	 */
	public static boolean crossed(int quantityBefore, int quantityAfter, int reorderLevel) {
		return quantityBefore > reorderLevel && quantityAfter <= reorderLevel;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void record(Long itemId, Cause cause) {
		jdbc.sql(INSERT)
			.param("eventId", UUID.randomUUID().toString())
			.param("cause", cause.name())
			.param("occurredAt", clock.instant().toString())
			.param("itemId", itemId)
			.update();
	}

}
