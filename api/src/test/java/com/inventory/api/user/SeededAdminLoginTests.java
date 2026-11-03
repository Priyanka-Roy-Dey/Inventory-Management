package com.inventory.api.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.api.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "inventory.admin.username=first-admin", "inventory.admin.password=seeded-pass" })
@AutoConfigureMockMvc
class SeededAdminLoginTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void theSeededAdminCanSignInAtStartup() throws Exception {
		mockMvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{ \"username\": \"first-admin\", \"password\": \"seeded-pass\" }"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.role").value("ADMIN"));
	}

}
