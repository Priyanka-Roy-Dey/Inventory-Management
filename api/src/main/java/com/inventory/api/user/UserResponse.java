package com.inventory.api.user;

import java.time.Instant;

public record UserResponse(Long id, String username, Role role, boolean mustChangePassword, Instant createdAt) {

	static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getUsername(), user.getRole(), user.isMustChangePassword(),
				user.getCreatedAt());
	}

}
