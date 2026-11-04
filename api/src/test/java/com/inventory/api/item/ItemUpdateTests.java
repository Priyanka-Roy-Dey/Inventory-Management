package com.inventory.api.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.api.TestcontainersConfiguration;
import com.inventory.api.auth.TokenService;
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
class ItemUpdateTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TokenService tokens;

	@Autowired
	private ItemRepository items;

	@Autowired
	private JdbcTemplate jdbc;

	private Item paper;

	@BeforeEach
	void createItem() {
		paper = items.saveAndFlush(new Item("PAP-A4", "A4 paper", "Stationery", 40, 10, new BigDecimal("4.50")));
	}

	@AfterEach
	void deleteItems() {
		items.deleteAll();
	}

	private String bearerFor(Role role) {
		return "Bearer " + tokens.issue(new User(role.name().toLowerCase(), "hash", role));
	}

	private ResultActions update(Long id, String body, Role as) throws Exception {
		return mockMvc.perform(put("/api/items/{id}", id).header(HttpHeaders.AUTHORIZATION, bearerFor(as))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions update(String body) throws Exception {
		return update(paper.getId(), body, Role.STAFF);
	}

	private static String changes(long version) {
		return """
				{ "name": "A4 paper, 80gsm", "category": "Office", "reorderLevel": 15,
				  "unitPrice": 5.25, "version": %d }
				""".formatted(version);
	}

	@Test
	void anyRoleGetsAnItemById() throws Exception {
		mockMvc.perform(get("/api/items/{id}", paper.getId()).header(HttpHeaders.AUTHORIZATION, bearerFor(Role.VIEWER)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sku").value("PAP-A4"))
			.andExpect(jsonPath("$.version").value(0));
	}

	@Test
	void returnsNotFoundForAnUnknownItem() throws Exception {
		mockMvc.perform(get("/api/items/{id}", 999_999).header(HttpHeaders.AUTHORIZATION, bearerFor(Role.VIEWER)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Item not found"));
		update(999_999L, changes(0), Role.STAFF).andExpect(status().isNotFound());
	}

	@Test
	void treatsADeletedItemAsNotFound() throws Exception {
		jdbc.update("UPDATE items SET deleted_at = now() WHERE id = ?", paper.getId());

		mockMvc.perform(get("/api/items/{id}", paper.getId()).header(HttpHeaders.AUTHORIZATION, bearerFor(Role.ADMIN)))
			.andExpect(status().isNotFound());
		update(changes(0)).andExpect(status().isNotFound());
	}

	@Test
	void staffUpdatesTheEditableFields() throws Exception {
		update(changes(0)).andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("A4 paper, 80gsm"))
			.andExpect(jsonPath("$.category").value("Office"))
			.andExpect(jsonPath("$.reorderLevel").value(15))
			.andExpect(jsonPath("$.unitPrice").value(5.25))
			.andExpect(jsonPath("$.version").value(1));

		Item saved = items.findById(paper.getId()).orElseThrow();
		assertThat(saved.getName()).isEqualTo("A4 paper, 80gsm");
		assertThat(saved.getVersion()).isEqualTo(1);
		assertThat(saved.getUpdatedAt()).isAfter(paper.getUpdatedAt());
	}

	@Test
	void ignoresSkuAndQuantity() throws Exception {
		update("""
				{ "sku": "NEW-SKU", "quantity": 999, "name": "A4 paper", "category": "Stationery",
				  "reorderLevel": 10, "unitPrice": 4.50, "version": 0 }
				""").andExpect(status().isOk())
			.andExpect(jsonPath("$.sku").value("PAP-A4"))
			.andExpect(jsonPath("$.quantity").value(40));
	}

	@Test
	void rejectsAnUpdateBasedOnAnOldVersion() throws Exception {
		update(changes(0)).andExpect(status().isOk());

		update("""
				{ "name": "Someone else's edit", "category": "Stationery", "reorderLevel": 1,
				  "unitPrice": 1, "version": 0 }
				""").andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("Item was changed by someone else. Reload it and try again."));

		assertThat(items.findById(paper.getId()).orElseThrow().getName()).isEqualTo("A4 paper, 80gsm");
	}

	@Test
	void viewerCannotUpdate() throws Exception {
		update(paper.getId(), changes(0), Role.VIEWER).andExpect(status().isForbidden());
	}

	@Test
	void validatesTheChanges() throws Exception {
		update("""
				{ "name": "", "category": "Stationery", "reorderLevel": -1, "unitPrice": 1.999 }
				""").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("name: must not be blank; "
					+ "reorderLevel: must be greater than or equal to 0; "
					+ "unitPrice: must have at most 8 digits before and 2 after the decimal point; "
					+ "version: must not be null"));
	}

}
