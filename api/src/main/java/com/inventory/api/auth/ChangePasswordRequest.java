package com.inventory.api.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 72 is the longest password BCrypt can hash.
public record ChangePasswordRequest(@NotBlank String currentPassword,
		@NotNull @Size(min = 12, max = 72) String newPassword) {
}
