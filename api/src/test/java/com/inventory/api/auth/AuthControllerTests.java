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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository users;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtDecoder jwtDecoder;

	@BeforeEach
	void createUser() {
		users.save(new User("staff", passwordEncoder.encode("staff-pass"), Role.STAFF));
	}

	@AfterEach
	void deleteUsers() {
		users.deleteAll();
	}

	private ResultActions login(String body) throws Exception {
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	@Test
	void returnsATokenAndTheUserForCorrectCredentials() throws Exception {
		String response = login("{ \"username\": \"staff\", \"password\": \"staff-pass\" }")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.username").value("staff"))
			.andExpect(jsonPath("$.user.role").value("STAFF"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		Jwt jwt = jwtDecoder.decode(JsonPath.read(response, "$.accessToken"));
		assertThat(jwt.getSubject()).isEqualTo("staff");
		assertThat(jwt.getClaimAsString("role")).isEqualTo("STAFF");
	}

	@Test
	void rejectsAWrongPassword() throws Exception {
		login("{ \"username\": \"staff\", \"password\": \"wrong\" }")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid username or password"));
	}

	@Test
	void rejectsAnUnknownUserWithTheSameMessage() throws Exception {
		login("{ \"username\": \"nobody\", \"password\": \"staff-pass\" }")
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid username or password"));
	}

	@Test
	void requiresAUsernameAndPassword() throws Exception {
		login("{ \"username\": \"\", \"password\": \"\" }")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("password: must not be blank; username: must not be blank"));
	}

}
