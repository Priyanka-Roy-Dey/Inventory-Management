package com.inventory.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.api.TestcontainersConfiguration;
import com.inventory.api.user.Role;
import com.inventory.api.user.User;
import com.inventory.api.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository users;

	@Autowired
	private RefreshTokenRepository refreshTokens;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtDecoder jwtDecoder;

	private User staff;

	@BeforeEach
	void createUser() {
		staff = users.save(new User("staff", passwordEncoder.encode("staff-pass"), Role.STAFF));
	}

	@AfterEach
	void deleteUsers() {
		refreshTokens.deleteAll();
		users.deleteAll();
	}

	private String loginForRefreshToken() throws Exception {
		String response = mockMvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"username\": \"staff\", \"password\": \"staff-pass\" }"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(response, "$.refreshToken");
	}

	private ResultActions refresh(String refreshToken) throws Exception {
		return mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
			.content("{ \"refreshToken\": \"" + refreshToken + "\" }"));
	}

	@Test
	void loginReturnsARefreshTokenThatIsStoredOnlyAsAHash() throws Exception {
		String refreshToken = loginForRefreshToken();

		assertThat(refreshToken).isNotBlank();
		assertThat(refreshTokens.findByTokenHash(refreshToken)).isEmpty();
		assertThat(refreshTokens.findByTokenHash(RefreshTokenService.hash(refreshToken))).isPresent();
	}

	@Test
	void refreshReturnsANewAccessTokenAndANewRefreshToken() throws Exception {
		String refreshToken = loginForRefreshToken();

		String response = refresh(refreshToken).andExpect(status().isOk())
			.andExpect(jsonPath("$.user.username").value("staff"))
			.andExpect(jsonPath("$.user.role").value("STAFF"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(jwtDecoder.decode(JsonPath.read(response, "$.accessToken")).getSubject()).isEqualTo("staff");
		assertThat((String) JsonPath.read(response, "$.refreshToken")).isNotEqualTo(refreshToken);
	}

	@Test
	void aRefreshTokenCanOnlyBeUsedOnce() throws Exception {
		String refreshToken = loginForRefreshToken();
		refresh(refreshToken).andExpect(status().isOk());

		refresh(refreshToken).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));
	}

	@Test
	void rejectsAnUnknownRefreshToken() throws Exception {
		refresh("not-a-real-token").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));
	}

	@Test
	void rejectsAnExpiredRefreshToken() throws Exception {
		refreshTokens.save(new RefreshToken(staff, RefreshTokenService.hash("expired-token"),
				Instant.now().minusSeconds(60)));

		refresh("expired-token").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));
	}

	@Test
	void requiresARefreshToken() throws Exception {
		refresh("").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("refreshToken: must not be blank"));
	}

}
