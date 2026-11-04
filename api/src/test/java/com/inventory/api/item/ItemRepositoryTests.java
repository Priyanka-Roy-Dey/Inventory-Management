package com.inventory.api.item;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inventory.api.TestcontainersConfiguration;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

// The database rules are the last line of defence if a bug bypasses validation.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ItemRepositoryTests {

	@Autowired
	private ItemRepository items;

	@Test
	void rejectsADuplicateSku() {
		items.saveAndFlush(new Item("PAP-A4", "A4 paper", "Stationery", 1, 1, BigDecimal.ONE));

		assertThatThrownBy(
				() -> items.saveAndFlush(new Item("PAP-A4", "Other", "Stationery", 1, 1, BigDecimal.ONE)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsANegativeQuantity() {
		assertThatThrownBy(() -> items.saveAndFlush(new Item("NEG-1", "Bad", "Test", -1, 0, BigDecimal.ONE)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsANegativePrice() {
		assertThatThrownBy(
				() -> items.saveAndFlush(new Item("NEG-2", "Bad", "Test", 1, 0, new BigDecimal("-1.00"))))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
