package com.inventory.api.alert;

import java.util.List;

public record AlertPage(List<AlertResponse> alerts, int page, int size, long totalItems, int totalPages) {
}
