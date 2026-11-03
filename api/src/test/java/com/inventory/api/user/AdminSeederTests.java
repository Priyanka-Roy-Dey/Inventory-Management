package com.inventory.api.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.inventory.api.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Not transactional: the seeder commits on its own, as it does at startup.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminSeederTests {

	@Autowired
	private UserRepository users;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	@AfterEach
	void deleteUsers() {
		users.deleteAll();
	}

	private void seed(String username, String password) {
		new AdminSeeder(users, passwordEncoder, username, password).run(null);
	}

	@Test
	void createsTheFirstAdminWithAHashedPassword() {
		seed("admin", "first-admin-pass");

		User admin = users.findByUsername("admin").orElseThrow();
		assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
		assertThat(admin.getPasswordHash()).isNotEqualTo("first-admin-pass");
		assertThat(passwordEncoder.matches("first-admin-pass", admin.getPasswordHash())).isTrue();
	}

	@Test
	void leavesAnExistingAdminAlone() {
		users.save(new User("owner", passwordEncoder.encode("owner-pass"), Role.ADMIN));

		seed("admin", "first-admin-pass");

		assertThat(users.findByUsername("admin")).isEmpty();
		assertThat(users.count()).isEqualTo(1);
	}

	@Test
	void doesNotChangeTheSeededPasswordOnRestart() {
		seed("admin", "first-admin-pass");
		String hash = users.findByUsername("admin").orElseThrow().getPasswordHash();

		seed("admin", "a-different-pass");

		assertThat(users.findByUsername("admin").orElseThrow().getPasswordHash()).isEqualTo(hash);
	}

	@Test
	void skipsSeedingWhenNotConfigured() {
		seed("", "");
		seed(null, null);

		assertThat(users.count()).isZero();
	}

	@Test
	void doesNotFailWhenTheUsernameIsAlreadyTaken() {
		users.save(new User("admin", passwordEncoder.encode("staff-pass"), Role.STAFF));

		seed("admin", "first-admin-pass");

		assertThat(users.findByUsername("admin").orElseThrow().getRole()).isEqualTo(Role.STAFF);
	}

}
