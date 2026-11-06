package com.inventory.api.stock;

import com.inventory.api.item.Item;
import com.inventory.api.item.ItemImageService;
import com.inventory.api.item.ItemRepository;
import com.inventory.api.item.ItemResponses;
import com.inventory.api.outbox.LowStockOutbox;
import com.inventory.api.outbox.LowStockOutbox.Cause;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StockMovementService {

	// One statement, so concurrent movements are serialised by Postgres row locking and stock never goes below 0
	// (design note §2.2).
	private static final String APPLY_CHANGE = """
			UPDATE items
			SET quantity = quantity + :delta, version = version + 1, updated_at = now()
			WHERE id = :id AND deleted_at IS NULL AND quantity + :delta >= 0
			RETURNING quantity - :delta AS quantity_before, quantity AS quantity_after, reorder_level
			""";

	private final JdbcClient jdbc;

	private final ItemRepository items;

	private final StockMovementRepository movements;

	private final ItemResponses responses;

	private final ItemImageService images;

	private final LowStockOutbox outbox;

	public StockMovementService(JdbcClient jdbc, ItemRepository items, StockMovementRepository movements,
			ItemResponses responses, ItemImageService images, LowStockOutbox outbox) {
		this.jdbc = jdbc;
		this.items = items;
		this.movements = movements;
		this.responses = responses;
		this.images = images;
		this.outbox = outbox;
	}

	/**
	 * Applies the change, records it, attaches any image and writes any low-stock event in one transaction: all of
	 * it happens or none does.
	 */
	@Transactional
	public MovementResult record(Long itemId, StockMovementRequest request, String username) {
		int delta = deltaFor(request);
		// Check the image before touching stock, so a bad upload changes nothing.
		if (request.imageKey() != null) {
			images.checkUpload(itemId, request.imageKey());
		}
		StockLevels levels = applyChange(itemId, delta).orElseThrow(() -> rejected(itemId));
		StockMovement movement = movements.save(new StockMovement(itemId, request.type(), delta,
				levels.quantityBefore(), levels.quantityAfter(), request.reason(), username));
		// The before/after values come from the row lock, so concurrent movements see exactly one crossing.
		if (LowStockOutbox.crossed(levels.quantityBefore(), levels.quantityAfter(), levels.reorderLevel())) {
			outbox.record(itemId, Cause.STOCK_MOVEMENT);
		}
		Item item = items.findById(itemId).orElseThrow();
		if (request.imageKey() != null) {
			images.attach(item, request.imageKey());
			items.flush();
		}
		return new MovementResult(MovementResponse.from(movement), responses.of(item));
	}

	@Transactional(readOnly = true)
	public List<MovementResponse> history(Long itemId) {
		findActive(itemId);
		return movements.findAllByItemIdOrderByCreatedAtDescIdDesc(itemId)
			.stream()
			.map(MovementResponse::from)
			.toList();
	}

	private static int deltaFor(StockMovementRequest request) {
		if (request.imageKey() != null && request.type() != MovementType.IN) {
			throw badRequest("imageKey: is only allowed for IN");
		}
		int quantity = request.quantity();
		return switch (request.type()) {
			case IN -> requirePositive(quantity);
			case OUT -> -requirePositive(quantity);
			case ADJUST -> {
				if (quantity == 0) {
					throw badRequest("quantity: must not be 0 for ADJUST");
				}
				if (request.reason() == null) {
					throw badRequest("reason: is required for ADJUST");
				}
				yield quantity;
			}
		};
	}

	private static int requirePositive(int quantity) {
		if (quantity < 1) {
			throw badRequest("quantity: must be at least 1 for IN and OUT");
		}
		return quantity;
	}

	private Optional<StockLevels> applyChange(Long itemId, int delta) {
		return jdbc.sql(APPLY_CHANGE).param("delta", delta).param("id", itemId).query(StockLevels.class).optional();
	}

	// No row was updated: either the item does not exist, or the change would take stock below zero.
	private ResponseStatusException rejected(Long itemId) {
		Item item = findActive(itemId);
		return new ResponseStatusException(HttpStatus.CONFLICT,
				"Not enough stock: only " + item.getQuantity() + " available");
	}

	private Item findActive(Long itemId) {
		return items.findByIdAndDeletedAtIsNull(itemId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

	record StockLevels(int quantityBefore, int quantityAfter, int reorderLevel) {
	}

}
