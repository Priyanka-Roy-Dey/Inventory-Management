package com.inventory.api.auth;

import com.inventory.api.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

	private final JwtEncoder encoder;

	private final Clock clock;

	private final Duration accessTokenTtl;

	public TokenService(JwtEncoder encoder, Clock clock,
			@Value("${inventory.jwt.access-token-ttl}") Duration accessTokenTtl) {
		this.encoder = encoder;
		this.clock = clock;
		this.accessTokenTtl = accessTokenTtl;
	}

	public String issue(User user) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(JwtConfig.ISSUER)
			.subject(user.getUsername())
			.issuedAt(now)
			.expiresAt(now.plus(accessTokenTtl))
			.claim("role", user.getRole().name())
			.build();
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}
