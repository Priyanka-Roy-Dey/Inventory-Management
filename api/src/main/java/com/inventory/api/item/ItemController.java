package com.inventory.api.item;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Any role may read; creating and updating need Staff or Admin, and deleting needs Admin (see
 * {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/items")
public class ItemController {

	private final ItemService itemService;

	public ItemController(ItemService itemService) {
		this.itemService = itemService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ItemResponse create(@Valid @RequestBody CreateItemRequest request) {
		return itemService.create(request);
	}

	/**
	 * Search, filters, sort and page are all optional (INV-11, INV-35). Example:
	 * {@code ?search=pen&category=Stationery&attribute=Colour:Blue&pincode=560001&minPrice=1&maxPrice=5&page=0}.
	 * {@code attribute} can be repeated; every one must match.
	 */
	@GetMapping
	public ItemPage list(@RequestParam(required = false) String search,
			@RequestParam(required = false) String category, @RequestParam(required = false) Boolean lowStock,
			@RequestParam(required = false) String sort, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) BigDecimal minPrice,
			@RequestParam(required = false) BigDecimal maxPrice,
			@RequestParam(name = "attribute", required = false) List<String> attributes,
			@RequestParam(required = false) String pincode) {
		return itemService.search(new ItemQuery(search, category, lowStock, sort, page, size, minPrice, maxPrice,
				attributes, pincode));
	}

	// For the category filter on the Items page.
	@GetMapping("/categories")
	public List<String> categories() {
		return itemService.categories();
	}

	// For the attribute filter on the Items page.
	@GetMapping("/attributes")
	public List<AttributeOptions> attributes() {
		return itemService.attributeOptions();
	}

	@GetMapping("/{id}")
	public ItemResponse get(@PathVariable Long id) {
		return itemService.get(id);
	}

	@PutMapping("/{id}")
	public ItemResponse update(@PathVariable Long id, @Valid @RequestBody UpdateItemRequest request) {
		return itemService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		itemService.delete(id);
	}

}
