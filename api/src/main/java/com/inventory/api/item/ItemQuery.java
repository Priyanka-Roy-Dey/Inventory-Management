package com.inventory.api.item;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
public record ItemQuery(String search, String category, Boolean lowStock, String sort, Integer page, Integer size,
		BigDecimal minPrice, BigDecimal maxPrice, List<String> attributes, String pincode) {

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
		checkPriceRange();
		if (minPrice != null) {
			spec = spec.and((root, query, cb) -> cb.ge(root.get("unitPrice"), minPrice));
		}
		if (maxPrice != null) {
			spec = spec.and((root, query, cb) -> cb.le(root.get("unitPrice"), maxPrice));
		}
		if (StringUtils.hasText(pincode)) {
			String wanted = pincode.trim();
			if (!ItemDetails.PINCODE.matcher(wanted).matches()) {
				throw badRequest("pincode: must be a 6-digit pincode");
			}
			spec = spec.and((root, query, cb) -> cb.isMember(wanted, root.<Set<String>>get("deliveryPincodes")));
		}
		for (String attribute : attributes == null ? List.<String>of() : attributes) {
			spec = spec.and(hasAttribute(attribute));
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

	private void checkPriceRange() {
		if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)) {
			throw badRequest((minPrice != null && minPrice.signum() < 0 ? "minPrice" : "maxPrice") + ": must be 0 or more");
		}
		if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
			throw badRequest("minPrice: must not be more than maxPrice");
		}
	}

	// "name:value", both compared ignoring case. A subquery, so an item with many attributes is listed once.
	private static Specification<Item> hasAttribute(String filter) {
		int colon = filter == null ? -1 : filter.indexOf(':');
		String name = colon < 0 ? "" : filter.substring(0, colon).trim().toLowerCase(Locale.ROOT);
		String value = colon < 0 ? "" : filter.substring(colon + 1).trim().toLowerCase(Locale.ROOT);
		if (name.isEmpty() || value.isEmpty()) {
			throw badRequest("attribute: use name:value, for example Colour:Blue");
		}
		return (root, query, cb) -> {
			Subquery<Long> matching = query.subquery(Long.class);
			Root<Item> item = matching.from(Item.class);
			Join<Item, ItemAttribute> attribute = item.join("attributes");
			matching.select(item.get("id"))
				.where(cb.equal(item, root), cb.equal(cb.lower(attribute.get("name")), name),
						cb.equal(cb.lower(attribute.get("value")), value));
			return cb.exists(matching);
		};
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
