package com.inventory.api.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ItemDeleteTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TokenService tokens;

	@Autowired
	private ItemRepository items;

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

	private ResultActions deleteItem(Long id, Role as) throws Exception {
		return mockMvc.perform(delete("/api/items/{id}", id).header(HttpHeaders.AUTHORIZATION, bearerFor(as)));
	}

	@Test
	void adminSoftDeletesAnItem() throws Exception {
		deleteItem(paper.getId(), Role.ADMIN).andExpect(status().isNoContent());

		Item stored = items.findById(paper.getId()).orElseThrow();
		assertThat(stored.getDeletedAt()).isNotNull();
		assertThat(stored.getSku()).isEqualTo("PAP-A4");
	}

	@ParameterizedTest
	@EnumSource(value = Role.class, names = { "STAFF", "VIEWER" })
	void onlyAdminsCanDelete(Role role) throws Exception {
		deleteItem(paper.getId(), role).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("Access denied"));

		assertThat(items.findById(paper.getId()).orElseThrow().getDeletedAt()).isNull();
	}

	@Test
	void aDeletedItemDisappearsFromReadsAndEdits() throws Exception {
		deleteItem(paper.getId(), Role.ADMIN).andExpect(status().isNoContent());
		String bearer = bearerFor(Role.STAFF);

		mockMvc.perform(get("/api/items").header(HttpHeaders.AUTHORIZATION, bearer))
			.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get("/api/items/{id}", paper.getId()).header(HttpHeaders.AUTHORIZATION, bearer))
			.andExpect(status().isNotFound());
		mockMvc
			.perform(put("/api/items/{id}", paper.getId()).header(HttpHeaders.AUTHORIZATION, bearer)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "x", "category": "y", "reorderLevel": 1, "unitPrice": 1, "version": 1 }
						"""))
			.andExpect(status().isNotFound());
	}

	@Test
	void theSkuOfADeletedItemStaysReserved() throws Exception {
		deleteItem(paper.getId(), Role.ADMIN).andExpect(status().isNoContent());

		mockMvc
			.perform(post("/api/items").header(HttpHeaders.AUTHORIZATION, bearerFor(Role.STAFF))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "sku": "pap-a4", "name": "New paper", "category": "Stationery",
						  "quantity": 1, "reorderLevel": 1, "unitPrice": 1 }
						"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("SKU already exists"));
	}

	@Test
	void deletingTwiceOrAnUnknownItemIsNotFound() throws Exception {
		deleteItem(paper.getId(), Role.ADMIN).andExpect(status().isNoContent());

		deleteItem(paper.getId(), Role.ADMIN).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Item not found"));
		deleteItem(999_999L, Role.ADMIN).andExpect(status().isNotFound());
	}

}
