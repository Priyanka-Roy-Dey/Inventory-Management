-- One row per stock change. Rows are only ever inserted, so this is the item's full history.
CREATE TABLE stock_movements (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_id          BIGINT       NOT NULL REFERENCES items (id),
    type             VARCHAR(10)  NOT NULL,
    quantity_change  INTEGER      NOT NULL,
    quantity_before  INTEGER      NOT NULL,
    quantity_after   INTEGER      NOT NULL,
    reason           VARCHAR(500),
    created_by       VARCHAR(50)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT stock_movements_type_check CHECK (type IN ('IN', 'OUT', 'ADJUST')),
    CONSTRAINT stock_movements_change_check CHECK (quantity_change <> 0),
    CONSTRAINT stock_movements_after_check CHECK (quantity_after = quantity_before + quantity_change)
);

CREATE INDEX stock_movements_item_created_idx ON stock_movements (item_id, created_at DESC);
