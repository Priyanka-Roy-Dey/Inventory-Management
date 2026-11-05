package com.inventory.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	static final String IMAGES_BUCKET = "inventory-images";

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
	}

	@Bean
	GenericContainer<?> s3Container() {
		return new GenericContainer<>(DockerImageName.parse("adobe/s3mock:5.2.2")).withExposedPorts(9090)
			.withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", IMAGES_BUCKET);
	}

	@Bean
	DynamicPropertyRegistrar s3Properties(GenericContainer<?> s3Container) {
		return registry -> {
			registry.add("inventory.images.endpoint",
					() -> "http://" + s3Container.getHost() + ":" + s3Container.getMappedPort(9090));
			registry.add("inventory.images.bucket", () -> IMAGES_BUCKET);
		};
	}

}
