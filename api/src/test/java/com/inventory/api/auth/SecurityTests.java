package com.inventory.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.api.TestcontainersConfiguration;
import com.inventory.api.user.Role;
import com.inventory.api.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SecurityTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TokenService tokens;

	@Test
	void healthAndApiDocsArePublic() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
	}

	@Test
	void apiRequiresAToken() throws Exception {
		mockMvc.perform(get("/api/items"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Authentication required"));
	}

	@Test
	void rejectsAnInvalidToken() throws Exception {
		mockMvc.perform(get("/api/items").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid or expired token"));
	}

	@Test
	void acceptsATokenItIssued() throws Exception {
		String token = tokens.issue(new User("viewer", "hash", Role.VIEWER));

		mockMvc.perform(get("/api/does-not-exist").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isNotFound());
	}

}
