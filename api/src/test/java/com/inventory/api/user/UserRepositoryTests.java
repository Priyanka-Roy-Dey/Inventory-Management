package com.inventory.api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.inventory.api.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UserRepositoryTests {

	@Autowired
	private UserRepository users;

	@Test
	void savesAndFindsAUserByUsername() {
		users.saveAndFlush(new User("staff", "hash", Role.STAFF));

		User found = users.findByUsername("staff").orElseThrow();

		assertThat(found.getId()).isNotNull();
		assertThat(found.getPasswordHash()).isEqualTo("hash");
		assertThat(found.getRole()).isEqualTo(Role.STAFF);
		assertThat(found.getCreatedAt()).isNotNull();
	}

	@Test
	void findsNothingForAnUnknownUsername() {
		assertThat(users.findByUsername("nobody")).isEmpty();
	}

	@Test
	void rejectsADuplicateUsername() {
		users.saveAndFlush(new User("admin", "hash", Role.ADMIN));

		assertThatThrownBy(() -> users.saveAndFlush(new User("admin", "other", Role.VIEWER)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
