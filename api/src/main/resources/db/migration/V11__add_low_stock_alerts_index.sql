-- The Alerts page lists low-stock events newest first (INV-20, A41).
CREATE INDEX outbox_events_low_stock_created_idx ON outbox_events (created_at DESC, id DESC)
    WHERE event_type = 'LOW_STOCK';
