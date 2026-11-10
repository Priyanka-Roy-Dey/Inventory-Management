package com.inventory.api.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
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

	/**
	 * The configured key, so tokens stay valid across restarts and every API task; otherwise a generated one for
	 * local development and tests.
	 */
	@Bean
	KeyPair jwtSigningKeyPair(@Value("${inventory.jwt.private-key:}") String privateKeyPem) {
		if (privateKeyPem.isBlank()) {
			log.warn("Using a generated JWT signing key; issued tokens stop working when the API restarts");
			return generateRsaKeyPair();
		}
		return keyPairFromPem(privateKeyPem);
	}

	private static KeyPair keyPairFromPem(String privateKeyPem) {
		try {
			RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) RsaKeyConverters.pkcs8()
				.convert(new ByteArrayInputStream(privateKeyPem.getBytes(StandardCharsets.US_ASCII)));
			PublicKey publicKey = KeyFactory.getInstance("RSA")
				.generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
			return new KeyPair(publicKey, privateKey);
		}
		catch (IllegalArgumentException | ClassCastException | GeneralSecurityException ex) {
			throw new IllegalStateException("inventory.jwt.private-key must be a PEM-encoded PKCS#8 RSA private key",
					ex);
		}
	}

	@Bean
	JwtEncoder jwtEncoder(KeyPair jwtSigningKeyPair) {
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey(jwtSigningKeyPair))));
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

	/**
	 * The key as a JWK. Its {@code kid} is the RFC 7638 thumbprint, so the same key always has the same id.
	 */
	static RSAKey signingKey(KeyPair keyPair) {
		try {
			return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic()).privateKey((RSAPrivateKey) keyPair.getPrivate())
				.keyUse(KeyUse.SIGNATURE)
				.algorithm(JWSAlgorithm.RS256)
				.keyIDFromThumbprint()
				.build();
		}
		catch (JOSEException ex) {
			throw new IllegalStateException("Could not compute the key id", ex);
		}
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
