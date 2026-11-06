package com.inventory.api.item;

import com.inventory.api.outbox.LowStockOutbox;
import com.inventory.api.outbox.LowStockOutbox.Cause;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ItemService {

	private final ItemRepository items;

	private final ItemResponses responses;

	private final ItemImageService images;

	private final LowStockOutbox outbox;

	private final Clock clock;

	public ItemService(ItemRepository items, ItemResponses responses, ItemImageService images, LowStockOutbox outbox,
			Clock clock) {
		this.items = items;
		this.responses = responses;
		this.images = images;
		this.outbox = outbox;
		this.clock = clock;
	}

	/**
	 * An item created already at or below its reorder level alerts once (A13), in the same transaction.
	 */
	@Transactional
	public ItemResponse create(CreateItemRequest request) {
		// Check the image first, so a bad upload creates nothing.
		if (request.imageKey() != null) {
			images.checkNewItemUpload(request.imageKey());
		}
		Item item = new Item(request.sku(), request.name(), request.category(), request.quantity(),
				request.reorderLevel(), request.unitPrice().setScale(2, RoundingMode.UNNECESSARY));
		if (request.imageKey() != null) {
			item.attachImage(request.imageKey());
		}
		try {
			Item saved = items.saveAndFlush(item);
			if (saved.isLowStock()) {
				outbox.record(saved.getId(), Cause.CREATED);
			}
			return responses.of(saved);
		}
		catch (DataIntegrityViolationException ex) {
			// Validation covers the other constraints, so this is the unique SKU (including deleted items).
			throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists");
		}
	}

	public ItemResponse get(Long id) {
		return responses.of(findActive(id));
	}

	/**
	 * Applies the changes only if nobody saved the item since the client read it.
	 */
	@Transactional
	public ItemResponse update(Long id, UpdateItemRequest request) {
		Item item = findActive(id);
		if (item.getVersion() != request.version()) {
			throw changedBySomeoneElse();
		}
		boolean wasLow = item.isLowStock();
		item.update(request.name(), request.category(), request.reorderLevel(),
				request.unitPrice().setScale(2, RoundingMode.UNNECESSARY));
		try {
			// Another request may have saved between our read and this write; @Version catches that too.
			Item saved = items.saveAndFlush(item);
			// Quantity cannot change here, so only a raised reorder level can make the item low (A14).
			if (!wasLow && saved.isLowStock()) {
				outbox.record(saved.getId(), Cause.REORDER_LEVEL_RAISED);
			}
			return responses.of(saved);
		}
		catch (ObjectOptimisticLockingFailureException ex) {
			throw changedBySomeoneElse();
		}
	}

	@Transactional
	public void delete(Long id) {
		findActive(id).softDelete(clock.instant());
	}

	public List<String> categories() {
		return items.findActiveCategories();
	}

	public ItemPage search(ItemQuery query) {
		Page<Item> page = items.findAll(query.specification(), query.pageable());
		return new ItemPage(page.getContent().stream().map(responses::of).toList(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages());
	}

	private Item findActive(Long id) {
		return items.findByIdAndDeletedAtIsNull(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found"));
	}

	private static ResponseStatusException changedBySomeoneElse() {
		return new ResponseStatusException(HttpStatus.CONFLICT,
				"Item was changed by someone else. Reload it and try again.");
	}

}
