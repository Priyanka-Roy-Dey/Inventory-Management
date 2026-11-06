package com.inventory.api.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.api.TestcontainersConfiguration;
import com.inventory.api.auth.TokenService;
import com.inventory.api.user.Role;
import com.inventory.api.user.User;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * INV-11: search, filters, sorting and pages over the item list. The items are created once for the whole class.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ItemSearchTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TokenService tokens;

	@Autowired
	private ItemRepository items;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeAll
	void createItems() {
		// sku, name, category, quantity, reorder level, price
		save("PAP-A4", "A4 paper", "Stationery", 40, 10, "4.50");
		save("PAP-A3", "A3 paper", "Stationery", 2, 10, "6.00");
		save("PEN-BLU", "Blue pen", "Stationery", 100, 20, "1.20");
		save("CBL-USB", "USB cable", "Cables", 5, 5, "3.99");
		save("CBL-HDMI", "HDMI cable", "Cables", 30, 5, "8.75");
		save("TON-1", "Toner 100%", "Printing", 3, 2, "45.00");
		save("RIB_2", "Ribbon", "Printing", 1, 0, "2.00");
		Item gone = save("PAP-OLD", "Old paper", "Stationery", 0, 10, "1.00");
		jdbc.update("UPDATE items SET deleted_at = now() WHERE id = ?", gone.getId());
	}

	@AfterAll
	void deleteItems() {
		items.deleteAll();
	}

	private static String md5(String text) {
		try {
			return java.util.HexFormat.of()
				.formatHex(java.security.MessageDigest.getInstance("MD5").digest(text.getBytes()));
		}
		catch (java.security.NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private Item save(String sku, String name, String category, int quantity, int reorderLevel, String price) {
		return items.saveAndFlush(new Item(sku, name, category, quantity, reorderLevel, new BigDecimal(price)));
	}

	// A URI rather than a template string, so the already-encoded query is sent exactly as written.
	private ResultActions list(String query) throws Exception {
		MockHttpServletRequestBuilder request = get(URI.create("/api/items" + query)).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokens.issue(new User("viewer", "hash", Role.VIEWER)));
		return mockMvc.perform(request);
	}

	private List<String> skus(String query) throws Exception {
		String body = list(query).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<String> skus = com.jayway.jsonpath.JsonPath.read(body, "$.items[*].sku");
		return new ArrayList<>(skus);
	}

	@Test
	void listsActiveItemsBySkuWithPageInformation() throws Exception {
		list("").andExpect(jsonPath("$.items.length()").value(7))
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalItems").value(7))
			.andExpect(jsonPath("$.totalPages").value(1));
		assertThat(skus("")).containsExactly("CBL-HDMI", "CBL-USB", "PAP-A3", "PAP-A4", "PEN-BLU", "RIB_2", "TON-1");
	}

	@Test
	void searchesSkuAndNameIgnoringCase() throws Exception {
		assertThat(skus("?search=PAPER")).containsExactly("PAP-A3", "PAP-A4");
		assertThat(skus("?search=cbl")).containsExactly("CBL-HDMI", "CBL-USB");
		assertThat(skus("?search=%20blue%20")).containsExactly("PEN-BLU");
		assertThat(skus("?search=nothing-like-this")).isEmpty();
	}

	@Test
	void treatsPercentAndUnderscoreAsPlainText() throws Exception {
		assertThat(skus("?search=%25")).containsExactly("TON-1");
		assertThat(skus("?search=_")).containsExactly("RIB_2");
	}

	@Test
	void filtersByCategoryIgnoringCase() throws Exception {
		assertThat(skus("?category=cables")).containsExactly("CBL-HDMI", "CBL-USB");
		assertThat(skus("?category=Printing")).containsExactly("RIB_2", "TON-1");
	}

	@Test
	void filtersToLowStock() throws Exception {
		assertThat(skus("?lowStock=true")).containsExactly("CBL-USB", "PAP-A3");
		assertThat(skus("?lowStock=false")).hasSize(7);
	}

	@Test
	void combinesFilters() throws Exception {
		assertThat(skus("?search=pa&category=stationery&lowStock=true")).containsExactly("PAP-A3");
		list("?search=pa&category=stationery&lowStock=true").andExpect(jsonPath("$.totalItems").value(1));
	}

	@Test
	void sortsByTheChosenFieldAndDirection() throws Exception {
		assertThat(skus("?sort=quantity,desc")).containsExactly("PEN-BLU", "PAP-A4", "CBL-HDMI", "CBL-USB", "TON-1",
				"PAP-A3", "RIB_2");
		assertThat(skus("?sort=unitPrice,asc&category=cables")).containsExactly("CBL-USB", "CBL-HDMI");
		assertThat(skus("?sort=name")).containsExactly("PAP-A3", "PAP-A4", "PEN-BLU", "CBL-HDMI", "RIB_2", "TON-1",
				"CBL-USB");
		assertThat(skus("?sort=sku,desc")).first().isEqualTo("TON-1");
	}

	@Test
	void breaksTiesBySku() throws Exception {
		assertThat(skus("?sort=category,asc")).containsExactly("CBL-HDMI", "CBL-USB", "RIB_2", "TON-1", "PAP-A3",
				"PAP-A4", "PEN-BLU");
	}

	@Test
	void returnsPages() throws Exception {
		list("?size=3&page=0").andExpect(jsonPath("$.items.length()").value(3))
			.andExpect(jsonPath("$.totalItems").value(7))
			.andExpect(jsonPath("$.totalPages").value(3));
		assertThat(skus("?size=3&page=1")).containsExactly("PAP-A4", "PEN-BLU", "RIB_2");
		assertThat(skus("?size=3&page=2")).containsExactly("TON-1");
		assertThat(skus("?size=3&page=9")).isEmpty();
	}

	@Test
	void listsTheCategoriesOfActiveItems() throws Exception {
		mockMvc
			.perform(get("/api/items/categories").header(HttpHeaders.AUTHORIZATION,
					"Bearer " + tokens.issue(new User("viewer", "hash", Role.VIEWER))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").value(org.hamcrest.Matchers.contains("Cables", "Printing", "Stationery")));
	}

	@Test
	void rejectsInvalidParameters() throws Exception {
		list("?sort=price").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message")
				.value("sort: must be one of sku, name, category, quantity, unitPrice, updatedAt"));
		list("?sort=sku,up").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("sort: direction must be asc or desc"));
		list("?size=0").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("size: must be between 1 and 100"));
		list("?size=101").andExpect(status().isBadRequest());
		list("?page=-1").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("page: must be 0 or more"));
	}

	/**
	 * With about 10,000 items (A9) the planner must choose the INV-11 indexes on its own. The rows are inserted and
	 * rolled back inside one transaction, so other tests never see them.
	 */
	@Test
	void thePlannerUsesTheSearchIndexesAtRealisticSize() {
		List<String> plans = jdbc.execute((ConnectionCallback<List<String>>) connection -> {
			boolean autoCommit = connection.getAutoCommit();
			connection.setAutoCommit(false);
			List<String> lines = new ArrayList<>();
			try (Statement statement = connection.createStatement()) {
				statement.execute("""
						INSERT INTO items (sku, name, category, quantity, reorder_level, unit_price)
						SELECT 'GEN-' || upper(substr(md5('s' || n), 1, 10)), 'Item ' || md5('n' || n),
						       'Category ' || (n % 100), 50, 10, 1.00
						FROM generate_series(1, 10000) AS n
						""");
				statement.execute("ANALYZE items");
				// Varied names and SKUs, like real ones; search for a distinctive piece of row 4242.
				String name = md5("n4242").substring(0, 10);
				String sku = md5("s4242").substring(0, 8);
				for (String where : List.of("lower(name) LIKE '%" + name + "%'", "lower(sku) LIKE '%" + sku + "%'",
						"lower(category) = 'category 42'", "quantity <= reorder_level")) {
					try (ResultSet plan = statement
						.executeQuery("EXPLAIN SELECT id FROM items WHERE deleted_at IS NULL AND " + where)) {
						StringBuilder text = new StringBuilder();
						while (plan.next()) {
							text.append(plan.getString(1)).append('\n');
						}
						lines.add(text.toString());
					}
				}
			}
			finally {
				connection.rollback();
				connection.setAutoCommit(autoCommit);
			}
			return lines;
		});

		assertThat(plans.get(0)).contains("items_name_trgm_idx");
		assertThat(plans.get(1)).contains("items_sku_trgm_idx");
		assertThat(plans.get(2)).contains("items_category_idx");
		assertThat(plans.get(3)).contains("items_low_stock_idx");
	}

}
