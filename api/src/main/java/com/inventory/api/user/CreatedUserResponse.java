package com.inventory.api.user;

/**
 * The temporary password is returned only here, once; the Admin passes it to the new user.
 */
public record CreatedUserResponse(UserResponse user, String temporaryPassword) {
}
