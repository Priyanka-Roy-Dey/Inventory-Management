package com.inventory.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncodingTests {

	private final PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

	@Test
	void storesABcryptHashNotThePassword() {
		String hash = encoder.encode("s3cret-pass");

		assertThat(hash).isNotEqualTo("s3cret-pass").startsWith("$2");
	}

	@Test
	void matchesOnlyTheOriginalPassword() {
		String hash = encoder.encode("s3cret-pass");

		assertThat(encoder.matches("s3cret-pass", hash)).isTrue();
		assertThat(encoder.matches("wrong-pass", hash)).isFalse();
	}

}
