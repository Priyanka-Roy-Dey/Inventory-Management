package com.inventory.api.image;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * Loads only the application's properties files, so what is checked is the files themselves.
 */
class AwsProfileTests {

	@Configuration
	@EnableConfigurationProperties(ImageStorageProperties.class)
	static class PropertiesOnly {

	}

	private static ImageStorageProperties imagesWithProfiles(String... profiles) {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(PropertiesOnly.class)
			.web(WebApplicationType.NONE)
			.profiles(profiles)
			.run()) {
			return context.getBean(ImageStorageProperties.class);
		}
	}

	@Test
	void theAwsProfileUsesRealS3InsteadOfTheLocalS3Mock() {
		assertThat(imagesWithProfiles("aws").endpoint()).isEmpty();
	}

	@Test
	void withoutItTheLocalS3MockIsUsed() {
		assertThat(imagesWithProfiles().endpoint()).isEqualTo("http://localhost:9090");
	}

}
