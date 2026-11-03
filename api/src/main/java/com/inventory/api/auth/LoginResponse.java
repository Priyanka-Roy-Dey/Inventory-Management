package com.inventory.api.auth;

import com.inventory.api.user.Role;

public record LoginResponse(String accessToken, String refreshToken, UserSummary user) {

	public record UserSummary(String username, Role role, boolean mustChangePassword) {
	}

}
