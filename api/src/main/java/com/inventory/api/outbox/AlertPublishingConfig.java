package com.inventory.api.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

/**
 * Turns on the outbox publisher only when a topic is configured (in AWS, from the alerts stack).
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(AlertPublishingProperties.class)
@ConditionalOnExpression(AlertPublishingConfig.TOPIC_CONFIGURED)
public class AlertPublishingConfig {

	static final String TOPIC_CONFIGURED = "!'${inventory.alerts.topic-arn:}'.isBlank()";

	@Bean
	SnsClient snsClient(AlertPublishingProperties properties) {
		return SnsClient.builder().region(Region.of(properties.region())).build();
	}

}
