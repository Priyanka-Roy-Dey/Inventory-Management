package com.inventory.api.auth;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * RS256 signing and verification of access tokens.
 */
@Configuration
public class JwtConfig {

	public static final String ISSUER = "inventory-api";

	private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

	@Bean
	KeyPair jwtSigningKeyPair() {
		log.warn("Using a generated JWT signing key; issued tokens stop working when the API restarts");
		return generateRsaKeyPair();
	}

	@Bean
	JwtEncoder jwtEncoder(KeyPair jwtSigningKeyPair) {
		return NimbusJwtEncoder
			.withKeyPair((RSAPublicKey) jwtSigningKeyPair.getPublic(), (RSAPrivateKey) jwtSigningKeyPair.getPrivate())
			.build();
	}

	@Bean
	JwtDecoder jwtDecoder(KeyPair jwtSigningKeyPair) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) jwtSigningKeyPair.getPublic())
			.build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
		return decoder;
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	static KeyPair generateRsaKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("RSA is not available", ex);
		}
	}

}
