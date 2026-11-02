package com.inventory.api.error;

/**
 * The body of every error response: {@code { "message": "..." }}.
 */
public record ApiError(String message) {
}
