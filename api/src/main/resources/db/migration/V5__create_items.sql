CREATE TABLE items (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku           VARCHAR(50)    NOT NULL,
    name          VARCHAR(200)   NOT NULL,
    category      VARCHAR(100)   NOT NULL,
    quantity      INTEGER        NOT NULL,
    reorder_level INTEGER        NOT NULL,
    unit_price    NUMERIC(10, 2) NOT NULL,
    version       BIGINT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMPTZ,
    -- Covers soft-deleted rows too, so a deleted item's SKU stays reserved (A7).
    CONSTRAINT items_sku_key UNIQUE (sku),
    CONSTRAINT items_quantity_check CHECK (quantity >= 0),
    CONSTRAINT items_reorder_level_check CHECK (reorder_level >= 0),
    CONSTRAINT items_unit_price_check CHECK (unit_price >= 0)
);
