package com.inventory.api.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.api.TestcontainersConfiguration;
import com.inventory.api.auth.TokenService;
import com.inventory.api.item.ItemRepository;
import com.inventory.api.stock.StockMovementRepository;
import com.inventory.api.user.Role;
import com.inventory.api.user.User;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

/**
 * INV-20: the low-stock alerts recorded by INV-10, read through the API until INV-19 moves them to DynamoDB.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AlertTests {

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

	@BeforeEach
	@AfterEach
	void clean() {
		jdbc.update("DELETE FROM outbox_events");
		movements.deleteAll();
		items.deleteAll();
	}

	private String bearer(Role role) {
		return "Bearer " + tokens.issue(new User(role.name().toLowerCase(), "hash", role));
	}

	private Long createItem(String sku, String name, int quantity, int reorderLevel) throws Exception {
		String body = mockMvc
			.perform(post("/api/items").header(HttpHeaders.AUTHORIZATION, bearer(Role.STAFF))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "sku": "%s", "name": "%s", "category": "Test", "quantity": %d, "reorderLevel": %d,
						  "unitPrice": 1 }
						""".formatted(sku, name, quantity, reorderLevel)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private void move(Long itemId, String type, int quantity) throws Exception {
		mockMvc
			.perform(post("/api/items/{id}/movements", itemId).header(HttpHeaders.AUTHORIZATION, bearer(Role.STAFF))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"type\": \"%s\", \"quantity\": %d }".formatted(type, quantity)))
			.andExpect(status().isCreated());
	}

	private ResultActions alerts(String query) throws Exception {
		return mockMvc.perform(get(URI.create("/api/alerts" + query)).header(HttpHeaders.AUTHORIZATION, bearer(Role.VIEWER)));
	}

	private List<String> skus(String query) throws Exception {
		String body = alerts(query).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.alerts[*].sku");
	}

	// Three alerts, oldest first: USB cable created low, A4 paper crossing on a stock-out, toner crossing.
	private void recordThreeAlerts() throws Exception {
		createItem("CBL-USB", "USB cable", 2, 5);
		Long paper = createItem("PAP-A4", "A4 paper", 12, 10);
		move(paper, "OUT", 4);
		Long toner = createItem("TON-1", "Toner", 3, 1);
		move(toner, "OUT", 2);
	}

	@Test
	void listsAlertsNewestFirstWithTheirDetails() throws Exception {
		recordThreeAlerts();

		alerts("").andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(3))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.alerts[0].sku").value("TON-1"))
			.andExpect(jsonPath("$.alerts[1].sku").value("PAP-A4"))
			.andExpect(jsonPath("$.alerts[1].itemName").value("A4 paper"))
			.andExpect(jsonPath("$.alerts[1].quantity").value(8))
			.andExpect(jsonPath("$.alerts[1].reorderLevel").value(10))
			.andExpect(jsonPath("$.alerts[1].cause").value("STOCK_MOVEMENT"))
			.andExpect(jsonPath("$.alerts[1].occurredAt").isNotEmpty())
			.andExpect(jsonPath("$.alerts[1].id").isNotEmpty())
			.andExpect(jsonPath("$.alerts[1].itemId").isNumber())
			.andExpect(jsonPath("$.alerts[2].sku").value("CBL-USB"))
			.andExpect(jsonPath("$.alerts[2].cause").value("CREATED"));
	}

	@Test
	void showsWhetherTheItemIsStillLow() throws Exception {
		Long paper = createItem("PAP-A4", "A4 paper", 12, 10);
		move(paper, "OUT", 4);

		alerts("").andExpect(jsonPath("$.alerts[0].currentQuantity").value(8))
			.andExpect(jsonPath("$.alerts[0].stillLow").value(true));

		move(paper, "IN", 20);

		alerts("").andExpect(jsonPath("$.alerts[0].quantity").value(8))
			.andExpect(jsonPath("$.alerts[0].currentQuantity").value(28))
			.andExpect(jsonPath("$.alerts[0].stillLow").value(false));
		assertThat(skus("?stillLow=true")).isEmpty();
	}

	@Test
	void filtersBySearchAndCause() throws Exception {
		recordThreeAlerts();

		assertThat(skus("?search=paper")).containsExactly("PAP-A4");
		assertThat(skus("?search=cbl")).containsExactly("CBL-USB");
		assertThat(skus("?cause=CREATED")).containsExactly("CBL-USB");
		assertThat(skus("?cause=STOCK_MOVEMENT")).containsExactly("TON-1", "PAP-A4");
	}

	@Test
	void leavesOutAlertsForDeletedItems() throws Exception {
		recordThreeAlerts();
		jdbc.update("UPDATE items SET deleted_at = now() WHERE sku = 'PAP-A4'");

		assertThat(skus("")).containsExactly("TON-1", "CBL-USB");
	}

	@Test
	void returnsPages() throws Exception {
		recordThreeAlerts();

		alerts("?size=2").andExpect(jsonPath("$.alerts.length()").value(2))
			.andExpect(jsonPath("$.totalPages").value(2));
		assertThat(skus("?size=2&page=1")).containsExactly("CBL-USB");
	}

	@Test
	void rejectsInvalidParameters() throws Exception {
		alerts("?cause=SOMETHING").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("cause: must be one of STOCK_MOVEMENT, CREATED, REORDER_LEVEL_RAISED"));
		alerts("?size=101").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("size: must be between 1 and 100"));
		alerts("?page=-1").andExpect(status().isBadRequest());
	}

	@ParameterizedTest
	@EnumSource(Role.class)
	void everyRoleCanReadAlerts(Role role) throws Exception {
		mockMvc.perform(get("/api/alerts").header(HttpHeaders.AUTHORIZATION, bearer(role))).andExpect(status().isOk());
	}

	@Test
	void requiresAToken() throws Exception {
		mockMvc.perform(get("/api/alerts")).andExpect(status().isUnauthorized());
	}

}
