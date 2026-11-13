package com.inventory.api.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

/**
 * Sends committed outbox events to SNS, oldest first, and marks each one published (design note §2.5, INV-17).
 * Delivery is at least once: an event sent just before a crash is sent again, which the alert recorder ignores
 * (INV-18). {@code SKIP LOCKED} lets several API tasks run this at the same time without sending an event twice.
 */
@Service
@ConditionalOnExpression(AlertPublishingConfig.TOPIC_CONFIGURED)
public class OutboxPublisher {

	private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

	private static final int BATCH_SIZE = 50;

	// SNS subjects are limited to 100 printable ASCII characters.
	private static final int MAX_SUBJECT_LENGTH = 100;

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final String PENDING = """
			SELECT id, event_type, CAST(payload AS text) AS payload
			FROM outbox_events
			WHERE published_at IS NULL
			ORDER BY id
			LIMIT :limit
			FOR UPDATE SKIP LOCKED
			""";

	private final JdbcClient jdbc;

	private final SnsClient sns;

	private final AlertPublishingProperties properties;

	public OutboxPublisher(JdbcClient jdbc, SnsClient sns, AlertPublishingProperties properties) {
		this.jdbc = jdbc;
		this.sns = sns;
		this.properties = properties;
		log.info("Publishing outbox events to {}", properties.topicArn());
	}

	record PendingEvent(long id, String eventType, String payload) {
	}

	/**
	 * Sends up to one batch and returns how many were sent. Stops at the first failure, so events keep their order
	 * and the rest are tried on the next run.
	 */
	@Scheduled(fixedDelayString = "${inventory.alerts.publish-interval:10s}",
			initialDelayString = "${inventory.alerts.publish-interval:10s}")
	@Transactional
	public int publishPending() {
		List<PendingEvent> pending = jdbc.sql(PENDING).param("limit", BATCH_SIZE).query(PendingEvent.class).list();
		int sent = 0;
		for (PendingEvent event : pending) {
			try {
				sns.publish(request(event));
			}
			catch (SdkException ex) {
				log.warn("Could not publish outbox event {}; retrying on the next run: {}", event.id(), ex.getMessage());
				break;
			}
			jdbc.sql("UPDATE outbox_events SET published_at = now() WHERE id = :id").param("id", event.id()).update();
			sent++;
		}
		if (sent > 0) {
			log.info("Published {} of {} pending outbox events", sent, pending.size());
		}
		return sent;
	}

	private PublishRequest request(PendingEvent event) {
		JsonNode payload = read(event.payload());
		String sku = payload.path("sku").asText();
		String name = payload.path("name").asText();
		// "default" goes to the alert queue unchanged; "email" is what the manager reads.
		String email = """
				%s (%s) is low on stock: %d left, reorder level %d.

				Cause: %s
				Time (UTC): %s
				""".formatted(name, sku, payload.path("quantity").asInt(), payload.path("reorderLevel").asInt(),
				payload.path("cause").asText(), payload.path("occurredAt").asText());
		return PublishRequest.builder()
			.topicArn(properties.topicArn())
			.messageStructure("json")
			.message(write(Map.of("default", event.payload(), "email", email)))
			.subject(subject("Low stock: " + sku + " " + name))
			.messageAttributes(Map.of("eventType",
					MessageAttributeValue.builder().dataType("String").stringValue(event.eventType()).build()))
			.build();
	}

	private static String subject(String text) {
		String ascii = text.replaceAll("[^\\x20-\\x7E]", "?");
		return ascii.length() <= MAX_SUBJECT_LENGTH ? ascii : ascii.substring(0, MAX_SUBJECT_LENGTH);
	}

	private static JsonNode read(String text) {
		try {
			return JSON.readTree(text);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Outbox payload is not JSON", ex);
		}
	}

	private static String write(Object value) {
		try {
			return JSON.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Could not build the SNS message", ex);
		}
	}

}
