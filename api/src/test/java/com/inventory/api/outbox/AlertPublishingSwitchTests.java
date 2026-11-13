package com.inventory.api.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import software.amazon.awssdk.services.sns.SnsClient;

/**
 * In AWS the topic comes from the task's environment variables (service.yaml), not from a properties file.
 */
class AlertPublishingSwitchTests {

	private static boolean publishingOnWith(Map<String, Object> environmentVariables) {
		StandardEnvironment environment = new StandardEnvironment();
		// Replaces the real environment variables, under the name Spring Boot gives them.
		environment.getPropertySources()
			.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, new SystemEnvironmentPropertySource(
					StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, environmentVariables));
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(AlertPublishingConfig.class)
			.web(WebApplicationType.NONE)
			.environment(environment)
			.run()) {
			return context.getBeanNamesForType(SnsClient.class).length == 1;
		}
	}

	@Test
	void theTopicFromTheTasksEnvironmentTurnsPublishingOn() {
		assertThat(publishingOnWith(Map.of("INVENTORY_ALERTS_TOPICARN",
				"arn:aws:sns:eu-north-1:000000000000:inventory-low-stock", "INVENTORY_ALERTS_REGION", "eu-north-1")))
			.isTrue();
	}

	@Test
	void withoutATopicPublishingStaysOff() {
		assertThat(publishingOnWith(Map.of("INVENTORY_ALERTS_REGION", "eu-north-1"))).isFalse();
	}

}
