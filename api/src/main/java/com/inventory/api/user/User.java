package com.inventory.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 50)
	private String username;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	@Column(name = "must_change_password", nullable = false)
	private boolean mustChangePassword;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected User() {
	}

	public User(String username, String passwordHash, Role role) {
		this(username, passwordHash, role, false);
	}

	public User(String username, String passwordHash, Role role, boolean mustChangePassword) {
		this.username = username;
		this.passwordHash = passwordHash;
		this.role = role;
		this.mustChangePassword = mustChangePassword;
	}

	public Long getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Role getRole() {
		return role;
	}

	/**
	 * The user chose a new password; the forced change is done.
	 */
	public void changePassword(String newPasswordHash) {
		this.passwordHash = newPasswordHash;
		this.mustChangePassword = false;
	}

	/**
	 * An Admin issued a temporary password, which the user must replace at the next sign-in.
	 */
	public void resetPassword(String temporaryPasswordHash) {
		this.passwordHash = temporaryPasswordHash;
		this.mustChangePassword = true;
	}

	public boolean isMustChangePassword() {
		return mustChangePassword;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
