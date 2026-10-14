-- V61 — Optional (non-price) spec choices, e.g. pin board colour, ride on the cart line.
-- option_ids = canonical sorted comma list of spec_option ids ('' = none). Two colours of the same
-- board are two cart lines, so the uniqueness key now includes the selection.
ALTER TABLE cart_item ADD COLUMN option_ids VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE cart_item DROP CONSTRAINT ux_cart_line;
ALTER TABLE cart_item ADD CONSTRAINT ux_cart_line UNIQUE (cart_id, product_id, variant_id, option_ids);
