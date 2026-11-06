package com.inventory.api.importing;

import java.util.List;

/**
 * The outcome of one import. {@code rows} counts data rows; {@code imported} counts distinct SKUs that were
 * created, updated or restored.
 */
public record ImportReport(int rows, int imported, int created, int updated, int restored, List<RowError> errors) {
}
