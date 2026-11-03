package com.inventory.api.user;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final SecureRandom random = new SecureRandom();

	public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
	}

	public CreatedUserResponse create(String username, Role role) {
		String temporaryPassword = generateTemporaryPassword();
		try {
			User user = users.saveAndFlush(new User(username, passwordEncoder.encode(temporaryPassword), role, true));
			return new CreatedUserResponse(UserResponse.from(user), temporaryPassword);
		}
		catch (DataIntegrityViolationException ex) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
		}
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
