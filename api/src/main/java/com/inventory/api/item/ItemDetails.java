package com.inventory.api.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Checks and tidies the optional details sent with an item: attributes and delivery pincodes (A40).
 */
final class ItemDetails {

	// Indian PIN codes: six digits, not starting with 0.
	static final Pattern PINCODE = Pattern.compile("[1-9][0-9]{5}");

	private static final int MAX_ATTRIBUTES = 20;

	private static final int MAX_PINCODES = 500;

	private ItemDetails() {
	}

	static List<ItemAttribute> attributes(List<AttributeRequest> requested) {
		if (requested == null) {
			return List.of();
		}
		if (requested.size() > MAX_ATTRIBUTES) {
			throw badRequest("attributes: at most " + MAX_ATTRIBUTES + " per item");
		}
		// Lower-case name -> the first spelling given, which the error message repeats.
		Map<String, String> seen = new HashMap<>();
		List<ItemAttribute> attributes = new ArrayList<>();
		for (AttributeRequest attribute : requested) {
			String name = attribute == null || attribute.name() == null ? "" : attribute.name().trim();
			String value = attribute == null || attribute.value() == null ? "" : attribute.value().trim();
			if (name.isEmpty() || value.isEmpty()) {
				throw badRequest("attributes: every attribute needs a name and a value");
			}
			if (name.length() > 50 || value.length() > 100) {
				throw badRequest("attributes: names are at most 50 and values at most 100 characters");
			}
			// The list filter is written name:value.
			if (name.contains(":")) {
				throw badRequest("attributes: a name cannot contain ':'");
			}
			String first = seen.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
			if (first != null) {
				throw badRequest("attributes: " + first + " is given more than once");
			}
			attributes.add(new ItemAttribute(name, value));
		}
		attributes.sort(Comparator.comparing(ItemAttribute::name, String.CASE_INSENSITIVE_ORDER));
		return attributes;
	}

	static Set<String> pincodes(List<String> requested) {
		if (requested == null) {
			return Set.of();
		}
		Set<String> pincodes = new TreeSet<>();
		for (String pincode : requested) {
			String trimmed = pincode == null ? "" : pincode.trim();
			if (!PINCODE.matcher(trimmed).matches()) {
				throw badRequest("deliveryPincodes: " + trimmed + " is not a 6-digit pincode");
			}
			pincodes.add(trimmed);
		}
		if (pincodes.size() > MAX_PINCODES) {
			throw badRequest("deliveryPincodes: at most " + MAX_PINCODES + " per item");
		}
		return pincodes;
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

}
