package com.inventory.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.inventory.api.TestcontainersConfiguration;
import com.inventory.api.user.Role;
import com.inventory.api.user.User;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ConfiguredSigningKeyTests {

	private static final KeyPair CONFIGURED = JwtConfig.generateRsaKeyPair();

	@DynamicPropertySource
	static void signingKey(DynamicPropertyRegistry registry) {
		registry.add("inventory.jwt.private-key", () -> SigningKeyTests.pem(CONFIGURED));
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TokenService tokens;

	@Test
	void publishesAndSignsWithTheConfiguredKeySoTokensSurviveARestart() throws Exception {
		String jwks = mockMvc.perform(get("/.well-known/jwks.json")).andReturn().getResponse().getContentAsString();
		RSAKey published = (RSAKey) JWKSet.parse(jwks).getKeys().get(0);
		String token = tokens.issue(new User("staff", "hash", Role.STAFF));

		assertThat(published.toRSAPublicKey()).isEqualTo(CONFIGURED.getPublic());
		assertThat(NimbusJwtDecoder.withPublicKey((RSAPublicKey) CONFIGURED.getPublic()).build().decode(token)
			.getSubject()).isEqualTo("staff");
	}

}
