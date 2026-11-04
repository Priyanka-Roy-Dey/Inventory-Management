package com.inventory.api.item;

import java.math.RoundingMode;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ItemService {

	private final ItemRepository items;

	public ItemService(ItemRepository items) {
		this.items = items;
	}

	public ItemResponse create(CreateItemRequest request) {
		Item item = new Item(request.sku(), request.name(), request.category(), request.quantity(),
				request.reorderLevel(), request.unitPrice().setScale(2, RoundingMode.UNNECESSARY));
		try {
			return ItemResponse.from(items.saveAndFlush(item));
		}
		catch (DataIntegrityViolationException ex) {
			// Validation covers the other constraints, so this is the unique SKU (including deleted items).
			throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists");
		}
	}

	public ItemResponse get(Long id) {
		return ItemResponse.from(findActive(id));
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
		item.update(request.name(), request.category(), request.reorderLevel(),
				request.unitPrice().setScale(2, RoundingMode.UNNECESSARY));
		try {
			// Another request may have saved between our read and this write; @Version catches that too.
			return ItemResponse.from(items.saveAndFlush(item));
		}
		catch (ObjectOptimisticLockingFailureException ex) {
			throw changedBySomeoneElse();
		}
	}

	public List<ItemResponse> list() {
		return items.findAllByDeletedAtIsNull(Sort.by("sku")).stream().map(ItemResponse::from).toList();
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
