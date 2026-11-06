package com.inventory.api.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.api.TestcontainersConfiguration;
import com.inventory.api.auth.TokenService;
import com.inventory.api.item.Item;
import com.inventory.api.item.ItemRepository;
import com.inventory.api.stock.StockMovementRepository;
import com.inventory.api.user.Role;
import com.inventory.api.user.User;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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

/**
 * Design note §2.3–§2.5: an event is written when stock crosses into "low", once per crossing, in the same
 * transaction as the change.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class LowStockOutboxTests {

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

	private String bearer() {
		return "Bearer " + tokens.issue(new User("staff", "hash", Role.STAFF));
	}

	// quantity 12, reorder level 10: two units above "low".
	private Item paper() {
		return items.saveAndFlush(new Item("PAP-A4", "A4 paper", "Stationery", 12, 10, new BigDecimal("4.50")));
	}

	private ResultActions move(Item item, String type, int quantity) throws Exception {
		return mockMvc.perform(post("/api/items/{id}/movements", item.getId()).header(HttpHeaders.AUTHORIZATION, bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"type\": \"%s\", \"quantity\": %d, \"reason\": \"test\" }".formatted(type, quantity)));
	}

	private List<Map<String, Object>> events() {
		return jdbc.queryForList("""
				SELECT event_id::text AS event_id, event_type, aggregate_id, payload::text AS payload, published_at
				FROM outbox_events ORDER BY id
				""");
	}

	private static Object field(Map<String, Object> event, String path) {
		return JsonPath.read((String) event.get("payload"), path);
	}

	@Test
	void crossingIntoLowStockWritesOneEvent() throws Exception {
		Item paper = paper();

		move(paper, "OUT", 2).andExpect(status().isCreated());

		List<Map<String, Object>> events = events();
		assertThat(events).hasSize(1);
		Map<String, Object> event = events.get(0);
		assertThat(event.get("event_type")).isEqualTo("LOW_STOCK");
		assertThat(((Number) event.get("aggregate_id")).longValue()).isEqualTo(paper.getId());
		assertThat(event.get("published_at")).isNull();
		assertThat(field(event, "$.eventId")).isEqualTo(event.get("event_id"));
		assertThat(field(event, "$.sku")).isEqualTo("PAP-A4");
		assertThat(field(event, "$.name")).isEqualTo("A4 paper");
		assertThat(field(event, "$.quantity")).isEqualTo(10);
		assertThat(field(event, "$.reorderLevel")).isEqualTo(10);
		assertThat(field(event, "$.cause")).isEqualTo("STOCK_MOVEMENT");
		assertThat((String) field(event, "$.occurredAt")).isNotBlank();
	}

	@Test
	void stayingLowDoesNotFireAgain() throws Exception {
		Item paper = paper();
		move(paper, "OUT", 2).andExpect(status().isCreated());

		move(paper, "OUT", 3).andExpect(status().isCreated());
		move(paper, "ADJUST", -1).andExpect(status().isCreated());

		assertThat(events()).hasSize(1);
	}

	@Test
	void staysQuietWhileAboveTheReorderLevel() throws Exception {
		Item paper = paper();

		move(paper, "OUT", 1).andExpect(status().isCreated());
		move(paper, "IN", 5).andExpect(status().isCreated());

		assertThat(events()).isEmpty();
	}

	@Test
	void goingBackAboveAndDroppingAgainIsANewCrossing() throws Exception {
		Item paper = paper();
		move(paper, "OUT", 2).andExpect(status().isCreated());
		move(paper, "IN", 5).andExpect(status().isCreated());

		move(paper, "OUT", 6).andExpect(status().isCreated());

		assertThat(events()).hasSize(2);
		assertThat(field(events().get(1), "$.quantity")).isEqualTo(9);
		assertThat(events().get(0).get("event_id")).isNotEqualTo(events().get(1).get("event_id"));
	}

	@Test
	void anAdjustmentCanCrossToo() throws Exception {
		Item paper = paper();

		move(paper, "ADJUST", -5).andExpect(status().isCreated());

		assertThat(events()).hasSize(1);
		assertThat(field(events().get(0), "$.quantity")).isEqualTo(7);
	}

	@Test
	void aRejectedMovementWritesNothing() throws Exception {
		Item paper = paper();

		move(paper, "OUT", 13).andExpect(status().isConflict());

		assertThat(events()).isEmpty();
	}

	private ResultActions create(int quantity, int reorderLevel) throws Exception {
		return mockMvc.perform(post("/api/items").header(HttpHeaders.AUTHORIZATION, bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "sku": "CBL-USB", "name": "USB cable", "category": "Cables",
					  "quantity": %d, "reorderLevel": %d, "unitPrice": 3.99 }
					""".formatted(quantity, reorderLevel)));
	}

	@Test
	void anItemCreatedAlreadyLowFiresOnce() throws Exception {
		create(5, 5).andExpect(status().isCreated());

		assertThat(events()).hasSize(1);
		assertThat(field(events().get(0), "$.cause")).isEqualTo("CREATED");
		assertThat(field(events().get(0), "$.sku")).isEqualTo("CBL-USB");
	}

	@Test
	void anItemCreatedAboveItsLevelFiresNothing() throws Exception {
		create(6, 5).andExpect(status().isCreated());

		assertThat(events()).isEmpty();
	}

	@Test
	void aRejectedCreateWritesNothing() throws Exception {
		create(5, 5).andExpect(status().isCreated());

		create(1, 5).andExpect(status().isConflict());

		assertThat(events()).hasSize(1);
	}

	private ResultActions setReorderLevel(Item item, int reorderLevel, long version) throws Exception {
		return mockMvc.perform(put("/api/items/{id}", item.getId()).header(HttpHeaders.AUTHORIZATION, bearer())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "name": "A4 paper", "category": "Stationery", "reorderLevel": %d, "unitPrice": 4.50, "version": %d }
					""".formatted(reorderLevel, version)));
	}

	@Test
	void raisingTheReorderLevelAboveTheQuantityFiresOnce() throws Exception {
		Item paper = paper();

		setReorderLevel(paper, 12, 0).andExpect(status().isOk());
		setReorderLevel(paper, 20, 1).andExpect(status().isOk());

		assertThat(events()).hasSize(1);
		assertThat(field(events().get(0), "$.cause")).isEqualTo("REORDER_LEVEL_RAISED");
		assertThat(field(events().get(0), "$.reorderLevel")).isEqualTo(12);
	}

	@Test
	void otherEditsFireNothing() throws Exception {
		Item paper = paper();

		setReorderLevel(paper, 11, 0).andExpect(status().isOk());
		setReorderLevel(paper, 5, 1).andExpect(status().isOk());

		assertThat(events()).isEmpty();
	}

}
