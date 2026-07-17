-- V46 — Storefront ordering + category umbrella (owner 2026-07-16, "(1)" product sheet).
--
-- display_order: the owner's product sheet lists products in the order they must appear on the
-- home page and the shop/all-products page (best sellers first). Seeded per product by V47 from
-- the sheet's row order; admin-created products default to 100000 (after all seeded ones).
--
-- category_group: the sheet's numbered top-level storefront groups ("1.) Writing Boards" ..
-- "8.) Portable Partition"). The PDF's letter categories stay as product.category (the middle
-- level), the numbered sub-lists (e.g. "01. Double sided white board") are the products
-- themselves, and sizes are the variants — so no further columns are needed for the taxonomy.

ALTER TABLE product ADD COLUMN display_order INTEGER NOT NULL DEFAULT 100000;
ALTER TABLE product ADD COLUMN category_group VARCHAR(120);

CREATE INDEX ix_product_display_order ON product(display_order);
