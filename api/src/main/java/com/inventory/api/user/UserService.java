package com.inventory.api.user;

import com.inventory.api.auth.RefreshTokenService;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final RefreshTokenService refreshTokens;

	private final SecureRandom random = new SecureRandom();

	public UserService(UserRepository users, PasswordEncoder passwordEncoder, RefreshTokenService refreshTokens) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokens = refreshTokens;
	}

	public TemporaryPasswordResponse create(String username, Role role) {
		String temporaryPassword = generateTemporaryPassword();
		try {
			User user = users.saveAndFlush(new User(username, passwordEncoder.encode(temporaryPassword), role, true));
			return new TemporaryPasswordResponse(UserResponse.from(user), temporaryPassword);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
		}
	}

	/**
	 * Gives the user a new temporary password and signs them out everywhere.
	 */
	@Transactional
	public TemporaryPasswordResponse resetPassword(Long id) {
		User user = users.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
		String temporaryPassword = generateTemporaryPassword();
		user.resetPassword(passwordEncoder.encode(temporaryPassword));
		refreshTokens.revokeAll(user);
		return new TemporaryPasswordResponse(UserResponse.from(user), temporaryPassword);
	}

	public List<UserResponse> list() {
		return users.findAll(Sort.by("username")).stream().map(UserResponse::from).toList();
	}

	// 12 random bytes = 96 bits, encoded as 16 URL-safe characters.
	private String generateTemporaryPassword() {
		byte[] bytes = new byte[12];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
