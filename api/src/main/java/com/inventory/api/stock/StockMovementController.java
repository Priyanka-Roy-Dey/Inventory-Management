package com.inventory.api.stock;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Any role may read the history; recording a movement needs Staff or Admin (see {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/items/{itemId}/movements")
public class StockMovementController {

	private final StockMovementService stock;

	public StockMovementController(StockMovementService stock) {
		this.stock = stock;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public MovementResult record(@PathVariable Long itemId, @Valid @RequestBody StockMovementRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return stock.record(itemId, request, jwt.getSubject());
	}

	@GetMapping
	public List<MovementResponse> history(@PathVariable Long itemId) {
		return stock.history(itemId);
	}

}
