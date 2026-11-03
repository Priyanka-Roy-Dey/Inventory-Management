package com.inventory.api.auth;

import com.nimbusds.jose.jwk.JWKSet;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import java.security.KeyPair;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publishes the public signing key so other services (the API Gateway authorizer) can verify our tokens.
 */
@RestController
public class JwksController {

	private final Map<String, Object> jwks;

	public JwksController(KeyPair jwtSigningKeyPair) {
		this.jwks = new JWKSet(JwtConfig.signingKey(jwtSigningKeyPair).toPublicJWK()).toJSONObject();
	}

	@SecurityRequirements
	@GetMapping("/.well-known/jwks.json")
	public Map<String, Object> jwks() {
		return jwks;
	}

}
