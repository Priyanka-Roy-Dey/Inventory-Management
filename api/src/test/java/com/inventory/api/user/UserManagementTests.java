package com.inventory.api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.api.TestcontainersConfiguration;
import com.inventory.api.auth.TokenService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
class UserManagementTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TokenService tokens;

	@Autowired
	private UserRepository users;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@AfterEach
	void deleteUsers() {
		users.deleteAll();
	}

	private String bearerFor(Role role) {
		return "Bearer " + tokens.issue(new User(role.name().toLowerCase(), "hash", role));
	}

	private ResultActions createUser(String body) throws Exception {
		return mockMvc.perform(post("/api/users").header(HttpHeaders.AUTHORIZATION, bearerFor(Role.ADMIN))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	@Test
	void adminCreatesAUserWithAGeneratedTemporaryPassword() throws Exception {
		String response = createUser("{ \"username\": \"sam.staff\", \"role\": \"STAFF\" }")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.user.id").isNumber())
			.andExpect(jsonPath("$.user.username").value("sam.staff"))
			.andExpect(jsonPath("$.user.role").value("STAFF"))
			.andExpect(jsonPath("$.user.mustChangePassword").value(true))
			.andExpect(jsonPath("$.user.passwordHash").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();

		String temporaryPassword = JsonPath.read(response, "$.temporaryPassword");
		assertThat(temporaryPassword).hasSizeGreaterThanOrEqualTo(16);
		User saved = users.findByUsername("sam.staff").orElseThrow();
		assertThat(passwordEncoder.matches(temporaryPassword, saved.getPasswordHash())).isTrue();
		assertThat(saved.isMustChangePassword()).isTrue();
	}

	@Test
	void theNewUserCanSignInWithTheTemporaryPassword() throws Exception {
		String response = createUser("{ \"username\": \"vic\", \"role\": \"VIEWER\" }").andReturn()
			.getResponse()
			.getContentAsString();
		String temporaryPassword = JsonPath.read(response, "$.temporaryPassword");

		mockMvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"username\": \"vic\", \"password\": \"" + temporaryPassword + "\" }"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.role").value("VIEWER"));
	}

	@Test
	void everyUserGetsADifferentTemporaryPassword() throws Exception {
		String first = createUser("{ \"username\": \"one\", \"role\": \"STAFF\" }").andReturn()
			.getResponse()
			.getContentAsString();
		String second = createUser("{ \"username\": \"two\", \"role\": \"STAFF\" }").andReturn()
			.getResponse()
			.getContentAsString();

		assertThat((String) JsonPath.read(first, "$.temporaryPassword"))
			.isNotEqualTo(JsonPath.read(second, "$.temporaryPassword"));
	}

	@Test
	void rejectsADuplicateUsername() throws Exception {
		createUser("{ \"username\": \"sam\", \"role\": \"STAFF\" }").andExpect(status().isCreated());

		createUser("{ \"username\": \"sam\", \"role\": \"VIEWER\" }").andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("Username already exists"));
	}

	@Test
	void rejectsAnInvalidUsername() throws Exception {
		createUser("{ \"username\": \"no spaces!\", \"role\": \"STAFF\" }").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message")
				.value("username: may contain only letters, digits, '.', '_' and '-'"));
		createUser("{ \"username\": \"ab\", \"role\": \"STAFF\" }").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("username: size must be between 3 and 50"));
	}

	@Test
	void requiresARole() throws Exception {
		createUser("{ \"username\": \"sam\" }").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("role: must not be null"));
	}

	@Test
	void adminListsUsersWithoutPasswordHashes() throws Exception {
		users.save(new User("zoe", passwordEncoder.encode("pass"), Role.VIEWER));
		users.save(new User("amy", passwordEncoder.encode("pass"), Role.STAFF));

		mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearerFor(Role.ADMIN)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].username").value("amy"))
			.andExpect(jsonPath("$[0].role").value("STAFF"))
			.andExpect(jsonPath("$[0].createdAt").isNotEmpty())
			.andExpect(jsonPath("$[1].username").value("zoe"))
			.andExpect(jsonPath("$[0].passwordHash").doesNotExist());
	}

	@ParameterizedTest
	@EnumSource(value = Role.class, names = { "STAFF", "VIEWER" })
	void onlyAdminsCanManageUsers(Role role) throws Exception {
		mockMvc.perform(get("/api/users").header(HttpHeaders.AUTHORIZATION, bearerFor(role)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("Access denied"));
		mockMvc
			.perform(post("/api/users").header(HttpHeaders.AUTHORIZATION, bearerFor(role))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"username\": \"sneaky\", \"role\": \"ADMIN\" }"))
			.andExpect(status().isForbidden());
		assertThat(users.findByUsername("sneaky")).isEmpty();
	}

	@Test
	void requiresAToken() throws Exception {
		mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
	}

}
