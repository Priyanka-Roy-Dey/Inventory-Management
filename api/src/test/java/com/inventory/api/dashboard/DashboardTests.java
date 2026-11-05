package com.inventory.api.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.api.TestcontainersConfiguration;
import com.inventory.api.auth.TokenService;
import com.inventory.api.item.Item;
import com.inventory.api.item.ItemRepository;
import com.inventory.api.stock.MovementType;
import com.inventory.api.stock.StockMovement;
import com.inventory.api.stock.StockMovementRepository;
import com.inventory.api.user.Role;
import com.inventory.api.user.User;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DashboardTests {

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

	@AfterEach
	void deleteAll() {
		movements.deleteAll();
		items.deleteAll();
	}

	private ResultActions dashboard(Role as) throws Exception {
		return mockMvc.perform(get("/api/dashboard").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokens.issue(new User(as.name().toLowerCase(), "hash", as))));
	}

	private Item item(String sku, int quantity, int reorderLevel, String unitPrice) {
		return items.saveAndFlush(new Item(sku, "Item " + sku, "Test", quantity, reorderLevel, new BigDecimal(unitPrice)));
	}

	private StockMovement movement(Item item, int change, String by) {
		return movements.saveAndFlush(new StockMovement(item.getId(), change > 0 ? MovementType.IN : MovementType.OUT,
				change, 10, 10 + change, null, by));
	}

	private void softDelete(Item item) {
		jdbc.update("UPDATE items SET deleted_at = now() WHERE id = ?", item.getId());
	}

	@Test
	void isEmptyWithNoItems() throws Exception {
		dashboard(Role.VIEWER).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(0))
			.andExpect(jsonPath("$.lowStockCount").value(0))
			.andExpect(jsonPath("$.totalUnits").value(0))
			.andExpect(jsonPath("$.stockValue").value(0))
			.andExpect(jsonPath("$.movementsLast24Hours").value(0))
			.andExpect(jsonPath("$.lowStockItems.length()").value(0))
			.andExpect(jsonPath("$.recentMovements.length()").value(0));
	}

	@Test
	void totalsCoverActiveItemsOnly() throws Exception {
		item("PAP-A4", 40, 10, "4.50");
		item("CBL-USB", 5, 5, "3.99");
		softDelete(item("OLD-1", 1000, 0, "100.00"));

		dashboard(Role.STAFF).andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(2))
			.andExpect(jsonPath("$.lowStockCount").value(1))
			.andExpect(jsonPath("$.totalUnits").value(45))
			.andExpect(jsonPath("$.stockValue").value(199.95));
	}

	@Test
	void listsLowStockItemsMostUrgentFirst() throws Exception {
		item("OK-1", 50, 10, "1");
		item("LOW-A", 9, 10, "1");
		item("LOW-B", 0, 5, "1");
		softDelete(item("LOW-GONE", 0, 10, "1"));

		dashboard(Role.VIEWER).andExpect(jsonPath("$.lowStockItems.length()").value(2))
			.andExpect(jsonPath("$.lowStockItems[0].sku").value("LOW-B"))
			.andExpect(jsonPath("$.lowStockItems[0].quantity").value(0))
			.andExpect(jsonPath("$.lowStockItems[0].reorderLevel").value(5))
			.andExpect(jsonPath("$.lowStockItems[1].sku").value("LOW-A"));
	}

	@Test
	void showsTheTenMostRecentMovementsWithTheirItem() throws Exception {
		Item paper = item("PAP-A4", 40, 10, "1");
		for (int i = 1; i <= 12; i++) {
			movement(paper, i, "staff");
		}

		dashboard(Role.VIEWER).andExpect(jsonPath("$.recentMovements.length()").value(10))
			.andExpect(jsonPath("$.recentMovements[0].quantityChange").value(12))
			.andExpect(jsonPath("$.recentMovements[0].sku").value("PAP-A4"))
			.andExpect(jsonPath("$.recentMovements[0].itemName").value("Item PAP-A4"))
			.andExpect(jsonPath("$.recentMovements[0].type").value("IN"))
			.andExpect(jsonPath("$.recentMovements[0].createdBy").value("staff"))
			.andExpect(jsonPath("$.recentMovements[9].quantityChange").value(3));
	}

	@Test
	void leavesOutMovementsOfDeletedItems() throws Exception {
		Item gone = item("GONE-1", 10, 0, "1");
		movement(gone, 5, "staff");
		softDelete(gone);

		dashboard(Role.VIEWER).andExpect(jsonPath("$.recentMovements.length()").value(0))
			.andExpect(jsonPath("$.movementsLast24Hours").value(0));
	}

	@Test
	void countsOnlyMovementsFromTheLast24Hours() throws Exception {
		Item paper = item("PAP-A4", 40, 10, "1");
		movement(paper, 1, "staff");
		movement(paper, 2, "staff");
		StockMovement old = movement(paper, 3, "staff");
		jdbc.update("UPDATE stock_movements SET created_at = now() - interval '25 hours' WHERE id = ?", old.getId());

		dashboard(Role.VIEWER).andExpect(jsonPath("$.movementsLast24Hours").value(2));
	}

	@ParameterizedTest
	@EnumSource(Role.class)
	void everyRoleCanSeeIt(Role role) throws Exception {
		dashboard(role).andExpect(status().isOk());
	}

	@Test
	void requiresAToken() throws Exception {
		mockMvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
	}

}
