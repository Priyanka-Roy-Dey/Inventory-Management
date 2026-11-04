package com.inventory.api.stock;

import com.inventory.api.item.ItemResponse;

/**
 * The recorded movement and the item as it is now, so a client can update its view without another request.
 */
public record MovementResult(MovementResponse movement, ItemResponse item) {
}
