package com.inventory.api.user;

/**
 * The temporary password is returned only here, once; the Admin passes it to the user.
 */
public record TemporaryPasswordResponse(UserResponse user, String temporaryPassword) {
}
