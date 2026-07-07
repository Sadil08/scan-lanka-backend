-- V40 — Two data corrections found in a 2026-07-07 full audit of the seeded catalog (V36) against the
-- owner's source sheet: a 50-cent price typo and a mis-filed product category.

-- "Scan White Board 1 x 1½ ft" (product 1, variant 2) was seeded at Rs 562.00; the sheet says Rs 562.50.
UPDATE product_variant SET price_cents = 56250 WHERE id = 2 AND price_cents = 56200;

-- "Scan Canvas Board / A4 Size Canvas Board" (product 383) is a canvas product, filed under
-- "A4 Size Writing Board" by mistake (its "A3"/"A2" siblings are correctly under "Canvas").
UPDATE product SET category = 'Canvas' WHERE id = 383 AND category = 'A4 Size Writing Board';
