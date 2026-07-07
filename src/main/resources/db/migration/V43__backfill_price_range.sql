-- V43 — Every VARIANT-priced product's price_range_min_cents/max_cents was NULL (46 of the 47 variant
-- products in the catalog, discovered 2026-07-07 while verifying V41): these two columns are
-- denormalized cache fields, only ever populated by ProductService.persistVariants() when a product is
-- created through the admin app — the V36 catalog seeder inserted variants directly via SQL and never
-- set them, so the storefront listing/admin list showed a blank price for nearly every variant product.
-- Product 1 (Scan White Board) had a stale range (37500/75000) left over from its old 3-variant test
-- data, now wrong after V41 added the real 22 sizes. Backfilling all of them from actual variant prices.

UPDATE product p
SET price_range_min_cents = v.min_cents, price_range_max_cents = v.max_cents
FROM (
    SELECT product_id, MIN(price_cents) AS min_cents, MAX(price_cents) AS max_cents
    FROM product_variant
    WHERE active = true
    GROUP BY product_id
) v
WHERE p.id = v.product_id AND p.price_mode = 'VARIANT';
