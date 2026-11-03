package com.inventory.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.api.TestcontainersConfiguration;
import com.inventory.api.user.Role;
import com.inventory.api.user.User;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class JwksTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TokenService tokens;

	private String jwks() throws Exception {
		return mockMvc.perform(get("/.well-known/jwks.json"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	@Test
	void publishesOnePublicRsaSigningKeyWithoutAToken() throws Exception {
		mockMvc.perform(get("/.well-known/jwks.json"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.keys.length()").value(1))
			.andExpect(jsonPath("$.keys[0].kty").value("RSA"))
			.andExpect(jsonPath("$.keys[0].alg").value("RS256"))
			.andExpect(jsonPath("$.keys[0].use").value("sig"))
			.andExpect(jsonPath("$.keys[0].kid").isNotEmpty())
			.andExpect(jsonPath("$.keys[0].n").isNotEmpty())
			.andExpect(jsonPath("$.keys[0].e").isNotEmpty());
	}

	@Test
	void neverPublishesThePrivateKey() throws Exception {
		RSAKey key = (RSAKey) JWKSet.parse(jwks()).getKeys().get(0);

		assertThat(key.isPrivate()).isFalse();
	}

	@Test
	void tokensCanBeVerifiedWithThePublishedKey() throws Exception {
		RSAKey key = (RSAKey) JWKSet.parse(jwks()).getKeys().get(0);
		String token = tokens.issue(new User("staff", "hash", Role.STAFF));

		Jwt jwt = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build().decode(token);

		assertThat(jwt.getHeaders()).containsEntry("kid", key.getKeyID());
		assertThat(jwt.getSubject()).isEqualTo("staff");
	}

}
