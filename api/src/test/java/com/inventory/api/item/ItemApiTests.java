package com.inventory.api.item;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.api.TestcontainersConfiguration;
import com.inventory.api.auth.TokenService;
import com.inventory.api.user.Role;
import com.inventory.api.user.User;
import org.junit.jupiter.api.AfterEach;
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
class ItemApiTests {

	private static final String PAPER = """
			{ "sku": " pap-a4 ", "name": "A4 paper", "category": "Stationery",
			  "quantity": 40, "reorderLevel": 10, "unitPrice": 4.5 }
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TokenService tokens;

	@Autowired
	private ItemRepository items;

	@AfterEach
	void deleteItems() {
		items.deleteAll();
	}

	private String bearerFor(Role role) {
		return "Bearer " + tokens.issue(new User(role.name().toLowerCase(), "hash", role));
	}

	private ResultActions create(String body, Role as) throws Exception {
		return mockMvc.perform(post("/api/items").header(HttpHeaders.AUTHORIZATION, bearerFor(as))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions create(String body) throws Exception {
		return create(body, Role.STAFF);
	}

	private static String item(String sku, int quantity, int reorderLevel, String unitPrice) {
		return """
				{ "sku": "%s", "name": "Item %s", "category": "Cables",
				  "quantity": %d, "reorderLevel": %d, "unitPrice": %s }
				""".formatted(sku, sku, quantity, reorderLevel, unitPrice);
	}

	@Test
	void staffCreatesAnItem() throws Exception {
		create(PAPER).andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.sku").value("PAP-A4"))
			.andExpect(jsonPath("$.name").value("A4 paper"))
			.andExpect(jsonPath("$.category").value("Stationery"))
			.andExpect(jsonPath("$.quantity").value(40))
			.andExpect(jsonPath("$.reorderLevel").value(10))
			.andExpect(jsonPath("$.unitPrice").value(4.50))
			.andExpect(jsonPath("$.lowStock").value(false))
			.andExpect(jsonPath("$.version").value(0))
			.andExpect(jsonPath("$.createdAt").isNotEmpty());
	}

	@Test
	void adminCanCreateToo() throws Exception {
		create(PAPER, Role.ADMIN).andExpect(status().isCreated());
	}

	@Test
	void viewerCannotCreate() throws Exception {
		create(PAPER, Role.VIEWER).andExpect(status().isForbidden());
	}

	@Test
	void flagsAnItemCreatedAtOrBelowItsReorderLevel() throws Exception {
		create(item("CBL-USB", 5, 5, "3.99")).andExpect(jsonPath("$.lowStock").value(true));
	}

	@Test
	void rejectsADuplicateSkuIgnoringCase() throws Exception {
		create(PAPER).andExpect(status().isCreated());

		create(item("PAP-A4", 1, 1, "1")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("SKU already exists"));
	}

	@Test
	void allowsAFreeItem() throws Exception {
		create(item("FREE-1", 1, 0, "0")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.unitPrice").value(0));
	}

	@Test
	void requiresEveryField() throws Exception {
		create("{}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("category: must not be blank; name: must not be blank; "
					+ "quantity: must not be null; reorderLevel: must not be null; sku: must not be blank; "
					+ "unitPrice: must not be null"));
	}

	@Test
	void rejectsNegativeNumbers() throws Exception {
		create(item("NEG-1", -1, -1, "-0.01")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("quantity: must be greater than or equal to 0; "
					+ "reorderLevel: must be greater than or equal to 0; "
					+ "unitPrice: must be greater than or equal to 0.00"));
	}

	@Test
	void rejectsMoreThanTwoDecimalPlaces() throws Exception {
		create(item("DEC-1", 1, 1, "1.999")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message")
				.value("unitPrice: must have at most 8 digits before and 2 after the decimal point"));
	}

	@Test
	void rejectsAnInvalidSku() throws Exception {
		create(item("HAS SPACE", 1, 1, "1")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("sku: may contain only letters, digits, '.', '_' and '-'"));
	}

	@Test
	void listIsEmptyAtFirst() throws Exception {
		mockMvc.perform(get("/api/items").header(HttpHeaders.AUTHORIZATION, bearerFor(Role.VIEWER)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(0))
			.andExpect(jsonPath("$.totalItems").value(0));
	}

	@ParameterizedTest
	@EnumSource(Role.class)
	void everyRoleListsItemsSortedBySku(Role role) throws Exception {
		create(item("ZZZ-9", 1, 1, "1")).andExpect(status().isCreated());
		create(PAPER).andExpect(status().isCreated());

		mockMvc.perform(get("/api/items").header(HttpHeaders.AUTHORIZATION, bearerFor(role)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2))
			.andExpect(jsonPath("$.items[0].sku").value("PAP-A4"))
			.andExpect(jsonPath("$.items[1].sku").value("ZZZ-9"));
	}

	@Test
	void listRequiresAToken() throws Exception {
		mockMvc.perform(get("/api/items")).andExpect(status().isUnauthorized());
	}

}
