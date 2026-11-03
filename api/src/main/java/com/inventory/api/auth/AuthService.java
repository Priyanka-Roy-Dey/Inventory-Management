package com.inventory.api.auth;

import com.inventory.api.auth.LoginResponse.UserSummary;
import com.inventory.api.user.User;
import com.inventory.api.user.UserRepository;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final TokenService tokens;

	private final RefreshTokenService refreshTokens;

	// Checked when the username is unknown, so a wrong username takes as long as a wrong password.
	private final String unknownUserHash;

	public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
			RefreshTokenService refreshTokens) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.tokens = tokens;
		this.refreshTokens = refreshTokens;
		this.unknownUserHash = passwordEncoder.encode("unknown-user");
	}

	@Transactional
	public LoginResponse login(String username, String password) {
		Optional<User> user = users.findByUsername(username);
		boolean passwordMatches = passwordEncoder.matches(password,
				user.map(User::getPasswordHash).orElse(unknownUserHash));
		if (user.isEmpty() || !passwordMatches) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
		}
		return issueTokens(user.get());
	}

	@Transactional
	public LoginResponse refresh(String refreshToken) {
		return issueTokens(refreshTokens.consume(refreshToken));
	}

	/**
	 * Replaces the signed-in user's password, signs them out of other sessions and returns fresh tokens.
	 */
	@Transactional
	public LoginResponse changePassword(String username, String currentPassword, String newPassword) {
		User user = users.findByUsername(username)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
		if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
		}
		if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"New password must be different from the current password");
		}
		user.changePassword(passwordEncoder.encode(newPassword));
		refreshTokens.revokeAll(user);
		return issueTokens(user);
	}

	private LoginResponse issueTokens(User user) {
		return new LoginResponse(tokens.issue(user), refreshTokens.issue(user),
				new UserSummary(user.getUsername(), user.getRole(), user.isMustChangePassword()));
	}

}
