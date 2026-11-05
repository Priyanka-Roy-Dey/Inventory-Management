package com.inventory.api.item;

import java.time.Instant;

/**
 * PUT the file to {@code uploadUrl} with this {@code contentType}, then confirm with {@code key}.
 */
public record ImageUploadResponse(String uploadUrl, String key, String contentType, Instant expiresAt) {
}
