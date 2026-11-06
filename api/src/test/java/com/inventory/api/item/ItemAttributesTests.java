package com.inventory.api.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.api.TestcontainersConfiguration;
import com.inventory.api.auth.TokenService;
import com.inventory.api.user.Role;
import com.inventory.api.user.User;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
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
 * INV-35: optional attributes (such as ink colour) and delivery pincodes on items, and the matching list filters.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ItemAttributesTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TokenService tokens;

	@Autowired
	private ItemRepository items;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void deleteItems() {
		jdbc.update("DELETE FROM outbox_events");
		items.deleteAll();
	}

	private String bearer(Role role) {
		return "Bearer " + tokens.issue(new User(role.name().toLowerCase(), "hash", role));
	}

	private ResultActions create(String sku, String price, String attributes, String pincodes) throws Exception {
		return mockMvc.perform(post("/api/items").header(HttpHeaders.AUTHORIZATION, bearer(Role.STAFF))
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{ "sku": "%s", "name": "Item %s", "category": "Stationery", "quantity": 20, "reorderLevel": 5,
					  "unitPrice": %s, "attributes": %s, "deliveryPincodes": %s }
					""".formatted(sku, sku, price, attributes, pincodes)));
	}

	private Long createdId(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
	}

	private List<String> skus(String query) throws Exception {
		String body = mockMvc
			.perform(get(URI.create("/api/items" + query)).header(HttpHeaders.AUTHORIZATION, bearer(Role.VIEWER)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.items[*].sku");
	}

	private void createPens() throws Exception {
		create("PEN-BLU", "1.20", "[{\"name\":\"Colour\",\"value\":\"Blue\"},{\"name\":\"Tip\",\"value\":\"0.5 mm\"}]",
				"[\"560001\",\"110001\"]")
			.andExpect(status().isCreated());
		create("PEN-BLK", "1.50", "[{\"name\":\"colour\",\"value\":\"black\"},{\"name\":\"Tip\",\"value\":\"0.7 mm\"}]",
				"[\"560001\"]")
			.andExpect(status().isCreated());
		create("PAP-A4", "4.50", "[{\"name\":\"Size\",\"value\":\"A4\"}]", "[\"400001\"]").andExpect(status().isCreated());
		create("CBL-USB", "9.99", "[]", "[]").andExpect(status().isCreated());
	}

	@Test
	void createsAnItemWithAttributesAndPincodes() throws Exception {
		create("PEN-BLU", "1.20",
				"[{\"name\":\" Tip \",\"value\":\" 0.5 mm \"},{\"name\":\"Colour\",\"value\":\"Blue\"}]",
				"[\"560001\",\"110001\",\"560001\"]")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.attributes.length()").value(2))
			.andExpect(jsonPath("$.attributes[0].name").value("Colour"))
			.andExpect(jsonPath("$.attributes[0].value").value("Blue"))
			.andExpect(jsonPath("$.attributes[1].name").value("Tip"))
			.andExpect(jsonPath("$.attributes[1].value").value("0.5 mm"))
			.andExpect(jsonPath("$.deliveryPincodes.length()").value(2))
			.andExpect(jsonPath("$.deliveryPincodes[0]").value("110001"))
			.andExpect(jsonPath("$.deliveryPincodes[1]").value("560001"));
	}

	@Test
	void attributesAndPincodesAreOptional() throws Exception {
		mockMvc
			.perform(post("/api/items").header(HttpHeaders.AUTHORIZATION, bearer(Role.STAFF))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "sku": "PLAIN-1", "name": "Plain", "category": "Misc", "quantity": 1, "reorderLevel": 0,
						  "unitPrice": 1 }
						"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.attributes.length()").value(0))
			.andExpect(jsonPath("$.deliveryPincodes.length()").value(0));
	}

	@Test
	void rejectsInvalidPincodes() throws Exception {
		for (String pincode : List.of("12345", "012345", "56000A", "5600011")) {
			create("BAD-PIN", "1", "[]", "[\"" + pincode + "\"]").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("deliveryPincodes: " + pincode + " is not a 6-digit pincode"));
		}
	}

	@Test
	void rejectsInvalidAttributes() throws Exception {
		create("BAD-ATT", "1", "[{\"name\":\"Colour\",\"value\":\"Blue\"},{\"name\":\"COLOUR\",\"value\":\"Red\"}]",
				"[]")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("attributes: Colour is given more than once"));
		create("BAD-ATT", "1", "[{\"name\":\"\",\"value\":\"Blue\"}]", "[]").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("attributes: every attribute needs a name and a value"));
		create("BAD-ATT", "1", "[{\"name\":\"Colour:Tone\",\"value\":\"Blue\"}]", "[]")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("attributes: a name cannot contain ':'"));
		create("BAD-ATT", "1", "[{\"name\":\"%s\",\"value\":\"x\"}]".formatted("n".repeat(51)), "[]")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("attributes: names are at most 50 and values at most 100 characters"));
	}

	@Test
	void editingReplacesAttributesAndPincodes() throws Exception {
		Long id = createdId(create("PEN-BLU", "1.20", "[{\"name\":\"Colour\",\"value\":\"Blue\"}]", "[\"560001\"]"));

		mockMvc
			.perform(put("/api/items/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(Role.STAFF))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{ "name": "Item PEN-BLU", "category": "Stationery", "reorderLevel": 5, "unitPrice": 1.20,
						  "version": 0, "attributes": [{"name": "Colour", "value": "Green"}],
						  "deliveryPincodes": ["110001", "400001"] }
						"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.attributes.length()").value(1))
			.andExpect(jsonPath("$.attributes[0].value").value("Green"))
			.andExpect(jsonPath("$.deliveryPincodes[0]").value("110001"))
			.andExpect(jsonPath("$.deliveryPincodes[1]").value("400001"))
			.andExpect(jsonPath("$.version").value(1));
	}

	@Test
	void filtersByAnAttributeIgnoringCase() throws Exception {
		createPens();

		assertThat(skus("?attribute=colour:BLUE")).containsExactly("PEN-BLU");
		assertThat(skus("?attribute=Colour:Black")).containsExactly("PEN-BLK");
		assertThat(skus("?attribute=tip:0.7%20mm")).containsExactly("PEN-BLK");
	}

	@Test
	void everyAttributeFilterMustMatch() throws Exception {
		createPens();

		assertThat(skus("?attribute=Colour:Blue&attribute=Tip:0.5%20mm")).containsExactly("PEN-BLU");
		assertThat(skus("?attribute=Colour:Blue&attribute=Tip:0.7%20mm")).isEmpty();
	}

	@Test
	void filtersByDeliveryPincode() throws Exception {
		createPens();

		assertThat(skus("?pincode=560001")).containsExactly("PEN-BLK", "PEN-BLU");
		assertThat(skus("?pincode=400001")).containsExactly("PAP-A4");
		assertThat(skus("?pincode=999999")).isEmpty();
	}

	@Test
	void filtersByPriceRangeInclusive() throws Exception {
		createPens();

		assertThat(skus("?minPrice=1.50")).containsExactly("CBL-USB", "PAP-A4", "PEN-BLK");
		assertThat(skus("?maxPrice=1.50")).containsExactly("PEN-BLK", "PEN-BLU");
		assertThat(skus("?minPrice=1.21&maxPrice=4.50")).containsExactly("PAP-A4", "PEN-BLK");
	}

	@Test
	void combinesTheNewFiltersWithTheOthers() throws Exception {
		createPens();

		assertThat(skus("?search=pen&pincode=560001&maxPrice=1.30&attribute=colour:blue")).containsExactly("PEN-BLU");
	}

	@Test
	void rejectsInvalidFilters() throws Exception {
		mockMvc.perform(get(URI.create("/api/items?attribute=Colour")).header(HttpHeaders.AUTHORIZATION, bearer(Role.VIEWER)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("attribute: use name:value, for example Colour:Blue"));
		mockMvc.perform(get(URI.create("/api/items?pincode=1234")).header(HttpHeaders.AUTHORIZATION, bearer(Role.VIEWER)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("pincode: must be a 6-digit pincode"));
		mockMvc
			.perform(get(URI.create("/api/items?minPrice=5&maxPrice=2")).header(HttpHeaders.AUTHORIZATION,
					bearer(Role.VIEWER)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("minPrice: must not be more than maxPrice"));
		mockMvc.perform(get(URI.create("/api/items?minPrice=-1")).header(HttpHeaders.AUTHORIZATION, bearer(Role.VIEWER)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("minPrice: must be 0 or more"));
	}

	@Test
	void listsTheAttributeNamesAndValuesInUse() throws Exception {
		createPens();
		Long gone = createdId(create("GONE-1", "1", "[{\"name\":\"Finish\",\"value\":\"Matte\"}]", "[]"));
		jdbc.update("UPDATE items SET deleted_at = now() WHERE id = ?", gone);

		mockMvc.perform(get("/api/items/attributes").header(HttpHeaders.AUTHORIZATION, bearer(Role.VIEWER)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[0].name").value("Colour"))
			.andExpect(jsonPath("$[0].values").value(org.hamcrest.Matchers.contains("black", "Blue")))
			.andExpect(jsonPath("$[1].name").value("Size"))
			.andExpect(jsonPath("$[2].name").value("Tip"))
			.andExpect(jsonPath("$[2].values").value(org.hamcrest.Matchers.contains("0.5 mm", "0.7 mm")));
	}

}
