package com.inventory.api.item;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * The file the browser is about to upload. Allowed types and the size limit are checked by the service (A36).
 */
public record ImageUploadRequest(@NotBlank String contentType, @NotNull @Positive Long size) {
}
