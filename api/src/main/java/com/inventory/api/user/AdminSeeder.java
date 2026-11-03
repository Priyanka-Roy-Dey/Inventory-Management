package com.inventory.api.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Creates the first Admin at startup from {@code inventory.admin.username} and {@code inventory.admin.password}
 * when no Admin exists yet. An existing Admin is never changed.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final String username;

	private final String password;

	public AdminSeeder(UserRepository users, PasswordEncoder passwordEncoder,
			@Value("${inventory.admin.username:}") String username,
			@Value("${inventory.admin.password:}") String password) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.username = username;
		this.password = password;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (users.existsByRole(Role.ADMIN)) {
			return;
		}
		if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
			log.warn("No Admin exists and inventory.admin.username/password are not set; nobody can sign in");
			return;
		}
		try {
			users.save(new User(username, passwordEncoder.encode(password), Role.ADMIN));
			log.info("Created the first Admin '{}'", username);
		}
		catch (DataIntegrityViolationException ex) {
			// Another instance seeded at the same moment, or the username belongs to a non-Admin user.
			log.warn("Did not create Admin '{}': the username is already taken", username);
		}
	}

}
