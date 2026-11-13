package com.inventory.api.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.api.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

/**
 * INV-17, design note §2.5: events the stock change committed are sent to SNS afterwards, at least once, and marked
 * as published. A failed send leaves the event for the next run.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "inventory.alerts.topic-arn=" + OutboxPublisherTests.TOPIC,
		// The test calls the publisher itself instead of waiting for the schedule.
		"inventory.alerts.publish-interval=1h" })
class OutboxPublisherTests {

	static final String TOPIC = "arn:aws:sns:eu-north-1:000000000000:inventory-low-stock";

	private final ObjectMapper json = new ObjectMapper();

	@Autowired
	private OutboxPublisher publisher;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private SnsClient sns;

	@BeforeEach
	@AfterEach
	void clean() {
		jdbc.update("DELETE FROM outbox_events");
		reset(sns);
	}

	private UUID event(String sku) {
		UUID eventId = UUID.randomUUID();
		jdbc.update("""
				INSERT INTO outbox_events (event_id, event_type, aggregate_id, payload)
				VALUES (?, 'LOW_STOCK', 1, CAST(? AS jsonb))
				""", eventId, """
				{"eventId": "%s", "itemId": 1, "sku": "%s", "name": "A4 paper", "quantity": 9, "reorderLevel": 10,
				 "cause": "STOCK_MOVEMENT", "occurredAt": "2026-11-13T05:00:00Z"}
				""".formatted(eventId, sku));
		return eventId;
	}

	private boolean published(UUID eventId) {
		return jdbc.queryForObject("SELECT published_at IS NOT NULL FROM outbox_events WHERE event_id = ?",
				Boolean.class, eventId);
	}

	private List<PublishRequest> sent(int count) {
		ArgumentCaptor<PublishRequest> requests = ArgumentCaptor.forClass(PublishRequest.class);
		verify(sns, times(count)).publish(requests.capture());
		return requests.getAllValues();
	}

	@Test
	void sendsEachUnpublishedEventToTheTopicAndMarksItPublished() throws Exception {
		when(sns.publish(any(PublishRequest.class))).thenReturn(PublishResponse.builder().messageId("m").build());
		UUID first = event("PAP-A4");
		UUID second = event("CBL-USB");

		assertThat(publisher.publishPending()).isEqualTo(2);

		List<PublishRequest> requests = sent(2);
		assertThat(requests).allSatisfy(request -> assertThat(request.topicArn()).isEqualTo(TOPIC));
		// Oldest first, and the queue gets the event exactly as the outbox stored it.
		JsonNode message = json.readTree(requests.get(0).message());
		JsonNode queued = json.readTree(message.get("default").asText());
		assertThat(queued.get("eventId").asText()).isEqualTo(first.toString());
		assertThat(queued.get("sku").asText()).isEqualTo("PAP-A4");
		assertThat(requests.get(0).messageStructure()).isEqualTo("json");
		assertThat(published(first)).isTrue();
		assertThat(published(second)).isTrue();
	}

	@Test
	void emailsAReadableMessage() throws Exception {
		when(sns.publish(any(PublishRequest.class))).thenReturn(PublishResponse.builder().messageId("m").build());
		event("PAP-A4");

		publisher.publishPending();

		PublishRequest request = sent(1).get(0);
		assertThat(request.subject()).isEqualTo("Low stock: PAP-A4 A4 paper");
		assertThat(json.readTree(request.message()).get("email").asText())
			.contains("A4 paper (PAP-A4) is low on stock: 9 left, reorder level 10.");
	}

	@Test
	void doesNotSendAnEventTwice() {
		when(sns.publish(any(PublishRequest.class))).thenReturn(PublishResponse.builder().messageId("m").build());
		event("PAP-A4");
		publisher.publishPending();
		reset(sns);

		assertThat(publisher.publishPending()).isZero();

		verify(sns, never()).publish(any(PublishRequest.class));
	}

	@Test
	void aFailedSendLeavesTheEventAndTheOnesAfterItForTheNextRun() {
		when(sns.publish(any(PublishRequest.class))).thenReturn(PublishResponse.builder().messageId("m").build())
			.thenThrow(SdkClientException.create("SNS is unreachable"));
		UUID first = event("PAP-A4");
		UUID second = event("CBL-USB");
		UUID third = event("PEN-001");

		assertThat(publisher.publishPending()).isEqualTo(1);

		assertThat(published(first)).isTrue();
		assertThat(published(second)).isFalse();
		assertThat(published(third)).isFalse();
		sent(2);
	}

}
