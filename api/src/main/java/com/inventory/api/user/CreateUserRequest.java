package com.inventory.api.user;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
		@NotNull @Size(min = 3, max = 50) @Pattern(regexp = "[A-Za-z0-9._-]*",
				message = "may contain only letters, digits, '.', '_' and '-'") String username,
		@NotNull Role role) {
}
