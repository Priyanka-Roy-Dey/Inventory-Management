package com.inventory.api.item;

import jakarta.validation.Valid;
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
	 * Search, filters, sort and page are all optional (INV-11). Example:
	 * {@code ?search=pap&category=Stationery&lowStock=true&sort=quantity,desc&page=0&size=20}.
	 */
	@GetMapping
	public ItemPage list(@RequestParam(required = false) String search,
			@RequestParam(required = false) String category, @RequestParam(required = false) Boolean lowStock,
			@RequestParam(required = false) String sort, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return itemService.search(new ItemQuery(search, category, lowStock, sort, page, size));
	}

	// For the category filter on the Items page.
	@GetMapping("/categories")
	public List<String> categories() {
		return itemService.categories();
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
