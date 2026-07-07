-- V42 — Let a product image be tied to a specific size/variant (owner 2026-07-07): when a customer
-- picks a size on the product page, the gallery should be able to show that size's own photo instead
-- of always falling back to the product-level images. Nullable: existing images (variant_id IS NULL)
-- keep working exactly as before as the product-level default gallery.

ALTER TABLE product_image ADD COLUMN variant_id BIGINT REFERENCES product_variant(id) ON DELETE CASCADE;
CREATE INDEX ix_image_variant ON product_image(variant_id, display_order);
