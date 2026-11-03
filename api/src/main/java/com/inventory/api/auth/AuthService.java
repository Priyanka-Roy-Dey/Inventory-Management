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

	// Checked when the username is unknown, so a wrong username takes as long as a wrong password.
	private final String unknownUserHash;

	public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.tokens = tokens;
		this.unknownUserHash = passwordEncoder.encode("unknown-user");
	}

	@Transactional(readOnly = true)
	public LoginResponse login(String username, String password) {
		Optional<User> user = users.findByUsername(username);
		boolean passwordMatches = passwordEncoder.matches(password,
				user.map(User::getPasswordHash).orElse(unknownUserHash));
		if (user.isEmpty() || !passwordMatches) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
		}
		return new LoginResponse(tokens.issue(user.get()),
				new UserSummary(user.get().getUsername(), user.get().getRole()));
	}

}
