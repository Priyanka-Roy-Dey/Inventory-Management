package com.inventory.api.auth;

import com.inventory.api.user.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	@Modifying
	@Query("delete from RefreshToken t where t.id = :id")
	int deleteReturningCount(Long id);

	@Modifying
	@Query("delete from RefreshToken t where t.user = :user")
	void deleteAllFor(User user);

}
