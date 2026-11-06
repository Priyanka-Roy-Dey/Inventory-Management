package com.inventory.api.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.api.TestcontainersConfiguration;
import com.inventory.api.auth.TokenService;
import com.inventory.api.item.Item;
import com.inventory.api.item.ItemRepository;
import com.inventory.api.stock.StockMovement;
import com.inventory.api.stock.StockMovementRepository;
import com.inventory.api.user.Role;
import com.inventory.api.user.User;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * INV-21: CSV import (A5–A8, A42). The sample file is the one the Import page offers for download.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CsvImportTests {

	private static final String HEADER = "sku,name,category,quantity,reorder_level,unit_price\n";

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

	private ResultActions upload(String csv, Role as) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "items.csv", "text/csv",
				csv.getBytes(StandardCharsets.UTF_8));
		return mockMvc.perform(multipart("/api/items/import").file(file)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.issue(new User(as.name().toLowerCase(), "hash", as))));
	}

	private ResultActions upload(String csv) throws Exception {
		return upload(csv, Role.STAFF);
	}

	private Item item(String sku) {
		return items.findAll().stream().filter(each -> each.getSku().equals(sku)).findFirst().orElseThrow();
	}

	private List<String> alertCauses(String sku) {
		return jdbc.queryForList("SELECT payload ->> 'cause' FROM outbox_events WHERE payload ->> 'sku' = ? ORDER BY id",
				String.class, sku);
	}

	@Test
	void importsTheSampleFile() throws Exception {
		String sample = Files.readString(Path.of("../web/public/sample-import.csv"));

		upload(sample).andExpect(status().isOk())
			.andExpect(jsonPath("$.rows").value(6))
			.andExpect(jsonPath("$.imported").value(3))
			.andExpect(jsonPath("$.created").value(3))
			.andExpect(jsonPath("$.updated").value(0))
			.andExpect(jsonPath("$.restored").value(0))
			.andExpect(jsonPath("$.errors.length()").value(2))
			.andExpect(jsonPath("$.errors[0].row").value(6))
			.andExpect(jsonPath("$.errors[0].sku").value("BAD-1"))
			.andExpect(jsonPath("$.errors[0].message").value("quantity: must be a whole number of 0 or more"))
			.andExpect(jsonPath("$.errors[1].row").value(7))
			.andExpect(jsonPath("$.errors[1].message").value("sku: is required"));

		// A repeated SKU is an update: the last row wins (A5).
		Item pen = item("PEN-001");
		assertThat(pen.getName()).isEqualTo("Blue pen, box of 10");
		assertThat(pen.getQuantity()).isEqualTo(120);
		assertThat(pen.getUnitPrice()).isEqualByComparingTo("11.00");
		// Items created already low alert once (design note §2.4).
		assertThat(alertCauses("PAP-A4")).containsExactly("CREATED");
		assertThat(alertCauses("CBL-USB")).containsExactly("CREATED");
		assertThat(alertCauses("PEN-001")).isEmpty();
	}

	@Test
	void updatesAnExistingItemAndRecordsTheQuantityChange() throws Exception {
		items.saveAndFlush(new Item("PAP-A4", "Old name", "Old", 12, 10, new BigDecimal("4.00")));

		upload(HEADER + "pap-a4,A4 paper,Stationery,4,10,4.50\n").andExpect(status().isOk())
			.andExpect(jsonPath("$.imported").value(1))
			.andExpect(jsonPath("$.created").value(0))
			.andExpect(jsonPath("$.updated").value(1))
			.andExpect(jsonPath("$.errors.length()").value(0));

		Item paper = item("PAP-A4");
		assertThat(paper.getName()).isEqualTo("A4 paper");
		assertThat(paper.getCategory()).isEqualTo("Stationery");
		assertThat(paper.getQuantity()).isEqualTo(4);
		assertThat(paper.getUnitPrice()).isEqualByComparingTo("4.50");
		// Quantity changes only through movements (A33), so the import leaves a record.
		List<StockMovement> history = movements.findAllByItemIdOrderByCreatedAtDescIdDesc(paper.getId());
		assertThat(history).hasSize(1);
		assertThat(history.get(0).getQuantityChange()).isEqualTo(-8);
		assertThat(history.get(0).getReason()).isEqualTo("CSV import");
		assertThat(history.get(0).getCreatedBy()).isEqualTo("staff");
		assertThat(alertCauses("PAP-A4")).containsExactly("STOCK_MOVEMENT");
	}

	@Test
	void leavesTheQuantityAloneWhenItIsUnchanged() throws Exception {
		Item paper = items.saveAndFlush(new Item("PAP-A4", "A4 paper", "Stationery", 12, 10, new BigDecimal("4.00")));

		upload(HEADER + "PAP-A4,A4 paper,Stationery,12,15,4.00\n").andExpect(jsonPath("$.updated").value(1));

		assertThat(movements.findAllByItemIdOrderByCreatedAtDescIdDesc(paper.getId())).isEmpty();
		assertThat(alertCauses("PAP-A4")).containsExactly("REORDER_LEVEL_RAISED");
	}

	@Test
	void restoresADeletedItem() throws Exception {
		Item gone = items.saveAndFlush(new Item("PAP-A4", "A4 paper", "Stationery", 0, 10, BigDecimal.ONE));
		jdbc.update("UPDATE items SET deleted_at = now() WHERE id = ?", gone.getId());

		upload(HEADER + "PAP-A4,A4 paper,Stationery,30,10,4.50\n").andExpect(status().isOk())
			.andExpect(jsonPath("$.restored").value(1))
			.andExpect(jsonPath("$.updated").value(0))
			.andExpect(jsonPath("$.created").value(0))
			.andExpect(jsonPath("$.imported").value(1));

		Item paper = items.findByIdAndDeletedAtIsNull(gone.getId()).orElseThrow();
		assertThat(paper.getQuantity()).isEqualTo(30);
	}

	@Test
	void reportsEachKindOfBadRow() throws Exception {
		upload(HEADER + """
				OK-1,Fine,Misc,1,1,1.00
				SHORT-1,Too few fields,Misc,1,1
				NEG-1,Negative,Misc,-1,1,1.00
				PRICE-1,Bad price,Misc,1,1,1.999
				BAD SKU,Spaces,Misc,1,1,1.00
				NONAME-1,,Misc,1,1,1.00
				""").andExpect(status().isOk())
			.andExpect(jsonPath("$.imported").value(1))
			.andExpect(jsonPath("$.errors.length()").value(5))
			.andExpect(jsonPath("$.errors[0].row").value(3))
			.andExpect(jsonPath("$.errors[0].message").value("expected 6 fields but found 5"))
			.andExpect(jsonPath("$.errors[1].message").value("quantity: must be a whole number of 0 or more"))
			.andExpect(jsonPath("$.errors[2].message")
				.value("unit_price: must be 0 or more with at most 2 decimal places"))
			.andExpect(jsonPath("$.errors[3].message").value("sku: may contain only letters, digits, '.', '_' and '-'"))
			.andExpect(jsonPath("$.errors[4].message").value("name: is required"));
	}

	@Test
	void acceptsColumnsInAnyOrderAndQuotedValues() throws Exception {
		upload("""
				unit_price,sku,quantity,name,category,reorder_level
				"1,250.00",X-1,1,"Desk, oak",Furniture,0
				2.50,X-2,3,"Lamp \"\"LED\"\"",Lighting,1
				""").andExpect(jsonPath("$.imported").value(1))
			.andExpect(jsonPath("$.errors[0].row").value(2))
			.andExpect(jsonPath("$.errors[0].message").value("unit_price: must be 0 or more with at most 2 decimal places"));

		assertThat(item("X-2").getName()).isEqualTo("Lamp \"LED\"");
	}

	@Test
	void rejectsAFileWithoutTheRequiredColumns() throws Exception {
		upload("sku,name,category,quantity\nPAP-A4,A4 paper,Stationery,5\n").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Missing columns: reorder_level, unit_price"));

		assertThat(items.count()).isZero();
	}

	@Test
	void rejectsAnEmptyFile() throws Exception {
		upload("").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("The file is empty"));
	}

	@Test
	void viewerCannotImport() throws Exception {
		upload(HEADER + "PAP-A4,A4 paper,Stationery,5,10,4.50\n", Role.VIEWER).andExpect(status().isForbidden());

		assertThat(items.count()).isZero();
	}

}
