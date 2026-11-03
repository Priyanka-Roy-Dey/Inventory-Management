package com.inventory.api.auth;

import com.inventory.api.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Issues and rotates refresh tokens. Each token can be exchanged once; using it deletes it.
 */
@Service
public class RefreshTokenService {

	private final RefreshTokenRepository refreshTokens;

	private final Clock clock;

	private final Duration refreshTokenTtl;

	private final SecureRandom random = new SecureRandom();

	public RefreshTokenService(RefreshTokenRepository refreshTokens, Clock clock,
			@Value("${inventory.jwt.refresh-token-ttl}") Duration refreshTokenTtl) {
		this.refreshTokens = refreshTokens;
		this.clock = clock;
		this.refreshTokenTtl = refreshTokenTtl;
	}

	@Transactional
	public String issue(User user) {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		refreshTokens.save(new RefreshToken(user, hash(token), clock.instant().plus(refreshTokenTtl)));
		return token;
	}

	/**
	 * Deletes the token and returns its user, so the caller can issue a new pair.
	 */
	@Transactional
	public User consume(String token) {
		RefreshToken stored = refreshTokens.findByTokenHash(hash(token))
			.filter(found -> !found.isExpiredAt(clock.instant()))
			.orElseThrow(RefreshTokenService::invalid);
		// A concurrent request may have used the same token first; only one delete succeeds.
		if (refreshTokens.deleteReturningCount(stored.getId()) == 0) {
			throw invalid();
		}
		return stored.getUser();
	}

	static String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

	private static ResponseStatusException invalid() {
		return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
	}

}
