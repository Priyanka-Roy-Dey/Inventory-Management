-- Optional details on items (INV-35, A40): free-form attributes such as ink colour, and the pincodes an item can be
-- delivered to. Rows go with their item.
CREATE TABLE item_attributes (
    item_id BIGINT       NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    name    VARCHAR(50)  NOT NULL,
    value   VARCHAR(100) NOT NULL
);

-- One value per attribute name on an item, ignoring case.
CREATE UNIQUE INDEX item_attributes_item_name_key ON item_attributes (item_id, lower(name));
-- The attribute filter: name and value, ignoring case.
CREATE INDEX item_attributes_name_value_idx ON item_attributes (lower(name), lower(value));

CREATE TABLE item_delivery_pincodes (
    item_id BIGINT     NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    pincode VARCHAR(6) NOT NULL,
    CONSTRAINT item_delivery_pincodes_pkey PRIMARY KEY (item_id, pincode),
    CONSTRAINT item_delivery_pincodes_pincode_check CHECK (pincode ~ '^[1-9][0-9]{5}$')
);

-- The pincode filter.
CREATE INDEX item_delivery_pincodes_pincode_idx ON item_delivery_pincodes (pincode);
