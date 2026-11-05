-- Object key of the item's image in the images bucket (A36), or NULL when it has none.
ALTER TABLE items ADD COLUMN image_key VARCHAR(200);
