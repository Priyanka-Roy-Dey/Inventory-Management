package com.inventory.api.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.inventory.api.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

/**
 * Nobody calls the publisher in production: the schedule must run it on its own.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "inventory.alerts.topic-arn=arn:aws:sns:eu-north-1:000000000000:inventory-low-stock",
		"inventory.alerts.region=eu-north-1", "inventory.alerts.publish-interval=200ms" })
class OutboxScheduleTests {

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private SnsClient sns;

	@AfterEach
	void clean() {
		jdbc.update("DELETE FROM outbox_events");
	}

	@Test
	void theScheduleSendsANewEventWithinSeconds() throws InterruptedException {
		when(sns.publish(any(PublishRequest.class))).thenReturn(PublishResponse.builder().messageId("m").build());
		UUID eventId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO outbox_events (event_id, event_type, aggregate_id, payload)
				VALUES (?, 'LOW_STOCK', 1, CAST(? AS jsonb))
				""", eventId, "{\"eventId\": \"%s\", \"sku\": \"PAP-A4\", \"name\": \"A4 paper\"}".formatted(eventId));

		boolean published = false;
		for (int attempt = 0; attempt < 50 && !published; attempt++) {
			Thread.sleep(100);
			published = jdbc.queryForObject("SELECT published_at IS NOT NULL FROM outbox_events WHERE event_id = ?",
					Boolean.class, eventId);
		}

		assertThat(published).isTrue();
	}

}
