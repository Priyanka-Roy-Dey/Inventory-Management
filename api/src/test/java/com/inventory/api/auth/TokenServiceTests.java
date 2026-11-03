package com.inventory.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inventory.api.user.Role;
import com.inventory.api.user.User;
import java.security.KeyPair;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class TokenServiceTests {

	private static final Duration TTL = Duration.ofMinutes(15);

	private final JwtConfig config = new JwtConfig();

	private final KeyPair keys = JwtConfig.generateRsaKeyPair();

	private final JwtDecoder decoder = config.jwtDecoder(keys);

	private final User staff = new User("staff", "hash", Role.STAFF);

	@Test
	void issuesATokenWithTheUsernameRoleAndExpiry() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		TokenService tokens = new TokenService(config.jwtEncoder(keys), Clock.fixed(now, ZoneOffset.UTC), TTL);

		Jwt jwt = decoder.decode(tokens.issue(staff));

		assertThat(jwt.getSubject()).isEqualTo("staff");
		assertThat(jwt.getClaimAsString("role")).isEqualTo("STAFF");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo(JwtConfig.ISSUER);
		assertThat(jwt.getIssuedAt()).isEqualTo(now);
		assertThat(jwt.getExpiresAt()).isEqualTo(now.plus(TTL));
		assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
	}

	@Test
	void rejectsAnExpiredToken() {
		Clock anHourAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
		TokenService tokens = new TokenService(config.jwtEncoder(keys), anHourAgo, TTL);

		String expired = tokens.issue(staff);

		assertThatThrownBy(() -> decoder.decode(expired)).isInstanceOf(JwtException.class);
	}

	@Test
	void rejectsATokenSignedWithAnotherKey() {
		TokenService otherSigner = new TokenService(config.jwtEncoder(JwtConfig.generateRsaKeyPair()),
				Clock.systemUTC(), TTL);

		String forged = otherSigner.issue(staff);

		assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
	}

}
