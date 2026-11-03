package com.inventory.api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.api.TestcontainersConfiguration;
import com.inventory.api.auth.TokenService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TokenService tokens;

	@Autowired
	private UserRepository users;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private User staff;

	@BeforeEach
	void createUser() {
		staff = users.save(new User("staff", passwordEncoder.encode("forgotten-password"), Role.STAFF));
	}

	@AfterEach
	void deleteUsers() {
		users.deleteAll();
	}

	private ResultActions reset(Long id, Role as) throws Exception {
		return mockMvc.perform(post("/api/users/{id}/reset-password", id).header(HttpHeaders.AUTHORIZATION,
				"Bearer " + tokens.issue(new User(as.name().toLowerCase(), "hash", as))));
	}

	private ResultActions login(String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("{ \"username\": \"staff\", \"password\": \"" + password + "\" }"));
	}

	@Test
	void adminIssuesANewTemporaryPasswordAndFlagsTheUser() throws Exception {
		String response = reset(staff.getId(), Role.ADMIN).andExpect(status().isOk())
			.andExpect(jsonPath("$.user.username").value("staff"))
			.andExpect(jsonPath("$.user.mustChangePassword").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String temporaryPassword = JsonPath.read(response, "$.temporaryPassword");

		assertThat(temporaryPassword).hasSizeGreaterThanOrEqualTo(16);
		login("forgotten-password").andExpect(status().isUnauthorized());
		login(temporaryPassword).andExpect(status().isOk()).andExpect(jsonPath("$.user.mustChangePassword").value(true));
	}

	@Test
	void signsTheUserOutOfExistingSessions() throws Exception {
		String refreshToken = JsonPath.read(login("forgotten-password").andReturn().getResponse().getContentAsString(),
				"$.refreshToken");

		reset(staff.getId(), Role.ADMIN).andExpect(status().isOk());

		mockMvc
			.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"refreshToken\": \"" + refreshToken + "\" }"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void onlyAdminsCanReset() throws Exception {
		reset(staff.getId(), Role.STAFF).andExpect(status().isForbidden());

		login("forgotten-password").andExpect(status().isOk());
	}

	@Test
	void returnsNotFoundForAnUnknownUser() throws Exception {
		reset(999_999L, Role.ADMIN).andExpect(status().isNotFound());
	}

}
