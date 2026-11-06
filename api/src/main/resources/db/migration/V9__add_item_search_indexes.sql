-- Indexes for the item list's search, filters and sorting (INV-11, A9). Only items that are not deleted are listed,
-- so each index covers just those rows.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- "Contains" search on SKU and name, ignoring case. fastupdate = off keeps the index fully up to date: items are
-- written rarely and searched often, and a pending list would make the planner skip the index.
CREATE INDEX items_sku_trgm_idx ON items USING gin (lower(sku) gin_trgm_ops) WITH (fastupdate = off)
    WHERE deleted_at IS NULL;
CREATE INDEX items_name_trgm_idx ON items USING gin (lower(name) gin_trgm_ops) WITH (fastupdate = off)
    WHERE deleted_at IS NULL;

CREATE INDEX items_category_idx ON items (lower(category)) WHERE deleted_at IS NULL;

-- The "low stock only" filter, in SKU order.
CREATE INDEX items_low_stock_idx ON items (sku) WHERE deleted_at IS NULL AND quantity <= reorder_level;
