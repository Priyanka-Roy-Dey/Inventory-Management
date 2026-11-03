package com.inventory.api.error;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.inventory.api.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class UnknownRouteTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void unknownUrlReturnsNotFoundAsJson() throws Exception {
		mockMvc.perform(get("/api/does-not-exist").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VIEWER"))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Resource not found"))
			.andExpect(jsonPath("$.timestamp").doesNotExist());
	}

}
