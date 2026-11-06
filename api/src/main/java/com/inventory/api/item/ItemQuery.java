package com.inventory.api.item;

import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * The item list's search, filters, sort and page (INV-11). Every part is optional; deleted items are never listed.
 */
public record ItemQuery(String search, String category, Boolean lowStock, String sort, Integer page, Integer size) {

	static final int DEFAULT_SIZE = 20;

	static final int MAX_SIZE = 100;

	// The request names are the entity's property names.
	private static final List<String> SORTABLE = List.of("sku", "name", "category", "quantity", "unitPrice",
			"updatedAt");

	private static final char ESCAPE = '\\';

	Specification<Item> specification() {
		Specification<Item> spec = (root, query, cb) -> cb.isNull(root.get("deletedAt"));
		if (StringUtils.hasText(search)) {
			String pattern = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
			spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("sku")), pattern, ESCAPE),
					cb.like(cb.lower(root.get("name")), pattern, ESCAPE)));
		}
		if (StringUtils.hasText(category)) {
			String wanted = category.trim().toLowerCase(Locale.ROOT);
			spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("category")), wanted));
		}
		if (Boolean.TRUE.equals(lowStock)) {
			spec = spec.and((root, query, cb) -> cb.le(root.get("quantity"), root.<Integer>get("reorderLevel")));
		}
		return spec;
	}

	Pageable pageable() {
		int pageNumber = page == null ? 0 : page;
		int pageSize = size == null ? DEFAULT_SIZE : size;
		if (pageNumber < 0) {
			throw badRequest("page: must be 0 or more");
		}
		if (pageSize < 1 || pageSize > MAX_SIZE) {
			throw badRequest("size: must be between 1 and " + MAX_SIZE);
		}
		return PageRequest.of(pageNumber, pageSize, sortOrder());
	}

	// "field" or "field,asc|desc". SKU breaks ties, so pages never overlap or skip items.
	private Sort sortOrder() {
		if (!StringUtils.hasText(sort)) {
			return Sort.by("sku");
		}
		String[] parts = sort.split(",", 2);
		String property = parts[0].trim();
		if (!SORTABLE.contains(property)) {
			throw badRequest("sort: must be one of " + String.join(", ", SORTABLE));
		}
		Sort.Direction direction = Sort.Direction.ASC;
		if (parts.length == 2) {
			direction = Sort.Direction.fromOptionalString(parts[1].trim())
				.orElseThrow(() -> badRequest("sort: direction must be asc or desc"));
		}
		Sort order = Sort.by(direction, property);
		return property.equals("sku") ? order : order.and(Sort.by("sku"));
	}

	// % and _ are wildcards in LIKE; the user means them literally.
	private static String escapeLike(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

}
