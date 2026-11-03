package com.inventory.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.api.TestcontainersConfiguration;
import com.inventory.api.user.Role;
import com.inventory.api.user.User;
import com.inventory.api.user.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PasswordChangeTests {

	private static final String TEMPORARY = "temporary-pass-1";

	private static final String NEW_PASSWORD = "my-own-new-password";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository users;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtDecoder jwtDecoder;

	@BeforeEach
	void createUsers() {
		users.save(new User("newbie", passwordEncoder.encode(TEMPORARY), Role.VIEWER, true));
		users.save(new User("settled", passwordEncoder.encode("settled-password"), Role.VIEWER));
	}

	@AfterEach
	void deleteUsers() {
		users.deleteAll();
	}

	private String login(String username, String password) throws Exception {
		return mockMvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"username\": \"" + username + "\", \"password\": \"" + password + "\" }"))
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private ResultActions changePassword(String accessToken, String current, String next) throws Exception {
		return mockMvc.perform(post("/api/auth/change-password").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{ \"currentPassword\": \"" + current + "\", \"newPassword\": \"" + next + "\" }"));
	}

	private static String accessToken(String response) {
		return JsonPath.read(response, "$.accessToken");
	}

	@Test
	void loginTellsTheClientAPasswordChangeIsRequired() throws Exception {
		String response = login("newbie", TEMPORARY);

		assertThat((Boolean) JsonPath.read(response, "$.user.mustChangePassword")).isTrue();
		assertThat(jwtDecoder.decode(accessToken(response)).getClaimAsBoolean("must_change_password")).isTrue();
		assertThat((Boolean) JsonPath.read(login("settled", "settled-password"), "$.user.mustChangePassword"))
			.isFalse();
	}

	@Test
	void aFlaggedUserCanOnlyChangeTheirPassword() throws Exception {
		String token = accessToken(login("newbie", TEMPORARY));

		mockMvc.perform(get("/api/does-not-exist").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("Password change required"));
		mockMvc.perform(post("/api/does-not-exist").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("Password change required"));
	}

	@Test
	void changingThePasswordClearsTheFlagAndReturnsFreshTokens() throws Exception {
		String token = accessToken(login("newbie", TEMPORARY));

		String response = changePassword(token, TEMPORARY, NEW_PASSWORD).andExpect(status().isOk())
			.andExpect(jsonPath("$.user.mustChangePassword").value(false))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(users.findByUsername("newbie").orElseThrow().isMustChangePassword()).isFalse();
		mockMvc.perform(get("/api/does-not-exist").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(response)))
			.andExpect(status().isNotFound());
	}

	@Test
	void onlyTheNewPasswordWorksAfterwards() throws Exception {
		changePassword(accessToken(login("newbie", TEMPORARY)), TEMPORARY, NEW_PASSWORD).andExpect(status().isOk());

		assertThat(login("newbie", TEMPORARY)).contains("Invalid username or password");
		assertThat(login("newbie", NEW_PASSWORD)).contains("accessToken");
	}

	@Test
	void signsTheUserOutOfOtherSessions() throws Exception {
		String otherSession = JsonPath.read(login("newbie", TEMPORARY), "$.refreshToken");

		changePassword(accessToken(login("newbie", TEMPORARY)), TEMPORARY, NEW_PASSWORD).andExpect(status().isOk());

		mockMvc
			.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"refreshToken\": \"" + otherSession + "\" }"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void anyRoleMayChangeTheirOwnPassword() throws Exception {
		changePassword(accessToken(login("settled", "settled-password")), "settled-password", NEW_PASSWORD)
			.andExpect(status().isOk());
	}

	@Test
	void rejectsAWrongCurrentPassword() throws Exception {
		changePassword(accessToken(login("newbie", TEMPORARY)), "not-the-password", NEW_PASSWORD)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Current password is incorrect"));
	}

	@Test
	void rejectsATooShortNewPassword() throws Exception {
		changePassword(accessToken(login("newbie", TEMPORARY)), TEMPORARY, "short")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("newPassword: size must be between 12 and 72"));
	}

	@Test
	void rejectsReusingTheCurrentPassword() throws Exception {
		changePassword(accessToken(login("settled", "settled-password")), "settled-password", "settled-password")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("New password must be different from the current password"));
	}

	@Test
	void requiresAToken() throws Exception {
		mockMvc.perform(post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
	}

}
