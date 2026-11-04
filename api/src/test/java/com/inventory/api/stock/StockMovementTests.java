package com.inventory.api.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.api.TestcontainersConfiguration;
import com.inventory.api.auth.TokenService;
import com.inventory.api.item.Item;
import com.inventory.api.item.ItemRepository;
import com.inventory.api.user.Role;
import com.inventory.api.user.User;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class StockMovementTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TokenService tokens;

	@Autowired
	private ItemRepository items;

	@Autowired
	private StockMovementRepository movements;

	@Autowired
	private JdbcTemplate jdbc;

	private Item paper;

	@BeforeEach
	void createItem() {
		paper = items.saveAndFlush(new Item("PAP-A4", "A4 paper", "Stationery", 5, 10, new BigDecimal("4.50")));
	}

	@AfterEach
	void deleteAll() {
		movements.deleteAll();
		items.deleteAll();
	}

	private String bearerFor(Role role) {
		return "Bearer " + tokens.issue(new User(role.name().toLowerCase(), "hash", role));
	}

	private ResultActions move(Long itemId, String body, Role as) throws Exception {
		return mockMvc.perform(post("/api/items/{id}/movements", itemId).header(HttpHeaders.AUTHORIZATION, bearerFor(as))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions move(String body) throws Exception {
		return move(paper.getId(), body, Role.STAFF);
	}

	private int quantityNow() {
		return items.findById(paper.getId()).orElseThrow().getQuantity();
	}

	@Test
	void stockInAddsToTheQuantityAndRecordsWhoDidIt() throws Exception {
		move("""
				{ "type": "IN", "quantity": 20, "reason": "Delivery 4711" }
				""").andExpect(status().isCreated())
			.andExpect(jsonPath("$.movement.type").value("IN"))
			.andExpect(jsonPath("$.movement.quantityChange").value(20))
			.andExpect(jsonPath("$.movement.quantityBefore").value(5))
			.andExpect(jsonPath("$.movement.quantityAfter").value(25))
			.andExpect(jsonPath("$.movement.reason").value("Delivery 4711"))
			.andExpect(jsonPath("$.movement.createdBy").value("staff"))
			.andExpect(jsonPath("$.movement.createdAt").isNotEmpty())
			.andExpect(jsonPath("$.item.quantity").value(25))
			.andExpect(jsonPath("$.item.lowStock").value(false))
			.andExpect(jsonPath("$.item.version").value(1));

		assertThat(quantityNow()).isEqualTo(25);
	}

	@Test
	void stockOutRemovesFromTheQuantity() throws Exception {
		move("""
				{ "type": "OUT", "quantity": 5 }
				""").andExpect(status().isCreated())
			.andExpect(jsonPath("$.movement.quantityChange").value(-5))
			.andExpect(jsonPath("$.movement.quantityAfter").value(0))
			.andExpect(jsonPath("$.item.quantity").value(0));
	}

	@Test
	void anOversizedStockOutIsRejectedAndChangesNothing() throws Exception {
		move("""
				{ "type": "OUT", "quantity": 6 }
				""").andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("Not enough stock: only 5 available"));

		assertThat(quantityNow()).isEqualTo(5);
		assertThat(movements.count()).isZero();
	}

	@Test
	void adjustAppliesASignedChangeWithAReason() throws Exception {
		move("""
				{ "type": "ADJUST", "quantity": -2, "reason": "Water damage" }
				""").andExpect(status().isCreated())
			.andExpect(jsonPath("$.movement.quantityChange").value(-2))
			.andExpect(jsonPath("$.item.quantity").value(3));
		move("""
				{ "type": "ADJUST", "quantity": 4, "reason": "Stocktake found more" }
				""").andExpect(status().isCreated())
			.andExpect(jsonPath("$.item.quantity").value(7));
	}

	@Test
	void anAdjustBelowZeroIsRejected() throws Exception {
		move("""
				{ "type": "ADJUST", "quantity": -6, "reason": "Lost" }
				""").andExpect(status().isConflict());
	}

	@Test
	void adjustNeedsAReason() throws Exception {
		move("""
				{ "type": "ADJUST", "quantity": -1, "reason": "  " }
				""").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("reason: is required for ADJUST"));
	}

	@Test
	void adjustCannotBeZero() throws Exception {
		move("""
				{ "type": "ADJUST", "quantity": 0, "reason": "Nothing" }
				""").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("quantity: must not be 0 for ADJUST"));
	}

	@Test
	void stockInAndOutNeedAPositiveQuantity() throws Exception {
		move("""
				{ "type": "IN", "quantity": 0 }
				""").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("quantity: must be at least 1 for IN and OUT"));
		move("""
				{ "type": "OUT", "quantity": -3 }
				""").andExpect(status().isBadRequest());
	}

	@Test
	void requiresATypeAndQuantity() throws Exception {
		move("{}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("quantity: must not be null; type: must not be null"));
	}

	@Test
	void viewerCannotMoveStock() throws Exception {
		move(paper.getId(), """
				{ "type": "IN", "quantity": 1 }
				""", Role.VIEWER).andExpect(status().isForbidden());
	}

	@Test
	void anUnknownOrDeletedItemIsNotFound() throws Exception {
		move(999_999L, """
				{ "type": "IN", "quantity": 1 }
				""", Role.STAFF).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Item not found"));

		jdbc.update("UPDATE items SET deleted_at = now() WHERE id = ?", paper.getId());
		move("""
				{ "type": "IN", "quantity": 1 }
				""").andExpect(status().isNotFound());
	}

	@Test
	void anyRoleReadsTheHistoryNewestFirst() throws Exception {
		move("""
				{ "type": "IN", "quantity": 10 }
				""").andExpect(status().isCreated());
		move("""
				{ "type": "OUT", "quantity": 3, "reason": "Order 12" }
				""").andExpect(status().isCreated());

		mockMvc
			.perform(get("/api/items/{id}/movements", paper.getId()).header(HttpHeaders.AUTHORIZATION,
					bearerFor(Role.VIEWER)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].type").value("OUT"))
			.andExpect(jsonPath("$[0].quantityBefore").value(15))
			.andExpect(jsonPath("$[0].quantityAfter").value(12))
			.andExpect(jsonPath("$[1].type").value("IN"));
	}

	@Test
	void historyOfAnUnknownItemIsNotFound() throws Exception {
		mockMvc
			.perform(get("/api/items/{id}/movements", 999_999).header(HttpHeaders.AUTHORIZATION,
					bearerFor(Role.VIEWER)))
			.andExpect(status().isNotFound());
	}

}
