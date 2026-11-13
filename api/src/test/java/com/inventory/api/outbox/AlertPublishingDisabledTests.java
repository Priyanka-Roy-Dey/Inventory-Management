package com.inventory.api.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.inventory.api.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.sns.SnsClient;

/**
 * Locally there is no topic: events stay in the outbox (A41) and nothing tries to reach AWS.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AlertPublishingDisabledTests {

	@Autowired
	private ApplicationContext context;

	@Test
	void withoutATopicNothingIsPublished() {
		assertThat(context.getBeanNamesForType(OutboxPublisher.class)).isEmpty();
		assertThat(context.getBeanNamesForType(SnsClient.class)).isEmpty();
	}

}
