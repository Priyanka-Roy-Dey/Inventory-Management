package com.inventory.api.stock;

import static org.assertj.core.api.Assertions.assertThat;

import com.inventory.api.TestcontainersConfiguration;
import com.inventory.api.item.Item;
import com.inventory.api.item.ItemRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Many requests change the same item at once. The single conditional UPDATE (design note §2.2) must neither lose
 * a change nor let stock go below zero.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class StockConcurrencyTests {

	private static final int THREADS = 20;

	@Autowired
	private StockMovementService stock;

	@Autowired
	private ItemRepository items;

	@Autowired
	private StockMovementRepository movements;

	@AfterEach
	void deleteAll() {
		movements.deleteAll();
		items.deleteAll();
	}

	private Item itemWith(int quantity) {
		return items.saveAndFlush(new Item("CON-1", "Contended item", "Test", quantity, 0, BigDecimal.ONE));
	}

	/**
	 * Runs {@code request} from {@link #THREADS} threads released at the same moment, and counts the outcomes.
	 */
	private List<HttpStatus> runAtOnce(Runnable request) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(THREADS);
		try {
			List<Future<HttpStatus>> results = new ArrayList<>();
			for (int i = 0; i < THREADS; i++) {
				results.add(pool.submit(() -> {
					start.await();
					try {
						request.run();
						return HttpStatus.CREATED;
					}
					catch (ResponseStatusException ex) {
						return HttpStatus.valueOf(ex.getStatusCode().value());
					}
				}));
			}
			start.countDown();
			List<HttpStatus> statuses = new ArrayList<>();
			for (Future<HttpStatus> result : results) {
				statuses.add(result.get());
			}
			return statuses;
		}
		finally {
			pool.shutdown();
		}
	}

	@Test
	void parallelStockOutsNeverOversellOrLoseACount() throws Exception {
		Item item = itemWith(10);

		List<HttpStatus> statuses = runAtOnce(() -> stock.record(item.getId(),
				new StockMovementRequest(MovementType.OUT, 1, null), "staff"));

		assertThat(statuses).filteredOn(HttpStatus.CREATED::equals).hasSize(10);
		assertThat(statuses).filteredOn(HttpStatus.CONFLICT::equals).hasSize(10);
		assertThat(items.findById(item.getId()).orElseThrow().getQuantity()).isZero();
		assertThat(movements.count()).isEqualTo(10);
	}

	@Test
	void parallelStockInsAreAllCounted() throws Exception {
		Item item = itemWith(0);

		List<HttpStatus> statuses = runAtOnce(() -> stock.record(item.getId(),
				new StockMovementRequest(MovementType.IN, 5, null), "staff"));

		assertThat(statuses).containsOnly(HttpStatus.CREATED);
		assertThat(items.findById(item.getId()).orElseThrow().getQuantity()).isEqualTo(THREADS * 5);
		assertThat(movements.findAll()).extracting(StockMovement::getQuantityAfter)
			.containsExactlyInAnyOrderElementsOf(java.util.stream.IntStream.rangeClosed(1, THREADS)
				.map(n -> n * 5)
				.boxed()
				.toList());
	}

}
