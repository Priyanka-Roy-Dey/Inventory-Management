package com.inventory.api.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTests {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void malformedJsonIsABadRequest() throws Exception {
		mockMvc.perform(post("/test/items").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Malformed JSON request body"));
	}

	@Test
	void validationErrorsListEachInvalidField() throws Exception {
		mockMvc.perform(post("/test/items")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ \"name\": \"\", \"quantity\": -1 }"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message")
				.value("name: must not be blank; quantity: must be greater than or equal to 0"));
	}

	@Test
	void responseStatusExceptionKeepsItsStatusAndReason() throws Exception {
		mockMvc.perform(get("/test/conflict"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("SKU already exists"));
	}

	@Test
	void notFoundKeepsASpecificReason() throws Exception {
		mockMvc.perform(get("/test/missing"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Item not found"));
	}

	@Test
	void unsupportedMethodIsMethodNotAllowed() throws Exception {
		mockMvc.perform(delete("/test/items"))
			.andExpect(status().isMethodNotAllowed())
			.andExpect(jsonPath("$.message").exists());
	}

	@Test
	void unexpectedErrorsHideTheirDetails() throws Exception {
		mockMvc.perform(get("/test/boom"))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.message").value("Unexpected error"));
	}

	record ItemRequest(@NotBlank String name, @Min(0) int quantity) {
	}

	@RestController
	static class TestController {

		@PostMapping("/test/items")
		void create(@Valid @RequestBody ItemRequest request) {
		}

		@GetMapping("/test/conflict")
		void conflict() {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists");
		}

		@GetMapping("/test/missing")
		void missing() {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found");
		}

		@GetMapping("/test/boom")
		void boom() {
			throw new IllegalStateException("database password is hunter2");
		}

	}

}
