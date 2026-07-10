-- V44 — Dedupe guard for image uploads (owner 2026-07-10): a repeated bulk import (or a double-click)
-- was re-adding the same photos, so every product ended up with two copies of each. Store a content
-- hash so an image identical to one already on the product can be skipped. Nullable/back-filled lazily
-- — existing rows have no hash and simply don't participate in dedupe.
ALTER TABLE product_image ADD COLUMN content_hash VARCHAR(64);
CREATE INDEX ix_image_product_hash ON product_image(product_id, content_hash);
