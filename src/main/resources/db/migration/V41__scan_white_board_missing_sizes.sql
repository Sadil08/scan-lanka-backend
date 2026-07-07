-- V41 — "Scan White Board" (product 1) was missing 19 of its 22 sheet sizes (2026-07-07 catalog
-- audit): only 1x1, 1x1½, and 2x1 (variants 1-3) existed, left over from before the V36 seeder ran —
-- it skipped this product because a product with a matching name already existed. Adding the rest
-- (1½x1½ through 8x4) exactly as priced/weighted/zoned in the source sheet, following the same
-- per-cell pattern V36 used for every other product (e.g. "Scan Green Board Alumium Frame"):
--   - no sheet outer price -> lorry_outer_cents NULL, lorry_outer_whatsapp true (contact required)
--   - sheet outer price present -> lorry_outer_cents set, lorry_outer_whatsapp false
--   - 6x3/6x4/8x4 -> lorry_outer_whatsapp true AND courier_outer_blocked true regardless of the sheet's
--     outer number (owner 2026-07-07: big boards never get an outer lorry/courier charge, contact only)
--   - board_size_tier: UNDER_2FT iff both dimensions < 2ft, else BETWEEN_2FT_6FT (no OVER_6FT tier)

WITH
s6  AS (INSERT INTO spec_option (spec_group_id, value, display_order) VALUES (1, '1 1/2 x 1 1/2', 3)  RETURNING id),
s7  AS (INSERT INTO spec_option (spec_group_id, value, display_order) VALUES (1, '2 x 1 1/2', 4)       RETURNING id),
s8  AS (INSERT INTO spec_option (spec_group_id, value, display_order) VALUES (1, '2 1/2 x 1 1/2', 5)   RETURNING id),
s9  AS (INSERT INTO spec_option (spec_group_id, value, display_order) VALUES (1, '2 x 2', 6)           RETURNING id),
s10 AS (INSERT INTO spec_option (spec_group_id, value, display_order) VALUES (1, '3 x 1 1/2', 7)       RETURNING id),
s11 AS (INSERT INTO spec_option (spec_group_id, value, display_order) VALUES (1, '2 x 2 1/2', 8)       RETURNING id),
s12 AS (INSERT INTO spec_option (spec_group_id, value, display_order) VALUES (1, '2 x 3', 9)           RETURNING id),
s13 AS (INSERT INTO spec_option (spec_group_id, value, display_order) VALUES (1, '2 1/2 x 2 1/2', 10)  RETURNING id),
s14 AS (INSERT INTO spec_option (spec_group_id, value, display_order) VALUES (1, '3 x 2 1/2', 11)      RETURNING id),
s15 AS (INSERT INTO spec_option (spec_group_id, value, display_order) VALUES (1, '3 x 3', 12)          RETURNING id),
s16 AS (INSERT INTO spec_option (spec_group_id, value, display_order) VALUES (1, '4 x 2', 13)          RETURNING id),
s17 AS (INSERT INTO spec_option (spec_group_id, value, display_order) VALUES (1, '4 x 2 1/2', 14)      RETURNING id),
s18 AS (INSERT INTO spec_option (spec_group_id, value, display_order) VALUES (1, '4 x 3', 15)          RETURNING id),
s19 AS (INSERT INTO spec_option (spec_group_id, value, display_order) VALUES (1, '4 x 4', 16)          RETURNING id),
s20 AS (INSERT INTO spec_option (spec_group_id, value, display_order) VALUES (1, '5 x 3', 17)          RETURNING id),
s21 AS (INSERT INTO spec_option (spec_group_id, value, display_order) VALUES (1, '5 x 4', 18)          RETURNING id),
s22 AS (INSERT INTO spec_option (spec_group_id, value, display_order) VALUES (1, '6 x 3', 19)          RETURNING id),
s23 AS (INSERT INTO spec_option (spec_group_id, value, display_order) VALUES (1, '6 x 4', 20)          RETURNING id),
s24 AS (INSERT INTO spec_option (spec_group_id, value, display_order) VALUES (1, '8 x 4', 21)          RETURNING id),

v6 AS (INSERT INTO product_variant (product_id, sku, price_cents, weight_kg, options_signature,
        lorry_colombo_cents, lorry_colombo_enabled, lorry_suburb_cents, lorry_suburb_enabled,
        lorry_outer_cents, lorry_outer_enabled, lorry_outer_whatsapp, courier_outer_blocked, board_size_tier)
    SELECT 1, 'SEED-SCANWHITEBOARD-' || id, 84375, 2, id::text,
        60000, true, 60000, true, NULL, true, true, false, 'UNDER_2FT' FROM s6),
v7 AS (INSERT INTO product_variant (product_id, sku, price_cents, weight_kg, options_signature,
        lorry_colombo_cents, lorry_colombo_enabled, lorry_suburb_cents, lorry_suburb_enabled,
        lorry_outer_cents, lorry_outer_enabled, lorry_outer_whatsapp, courier_outer_blocked, board_size_tier)
    SELECT 1, 'SEED-SCANWHITEBOARD-' || id, 112500, 2, id::text,
        60000, true, 60000, true, NULL, true, true, false, 'BETWEEN_2FT_6FT' FROM s7),
v8 AS (INSERT INTO product_variant (product_id, sku, price_cents, weight_kg, options_signature,
        lorry_colombo_cents, lorry_colombo_enabled, lorry_suburb_cents, lorry_suburb_enabled,
        lorry_outer_cents, lorry_outer_enabled, lorry_outer_whatsapp, courier_outer_blocked, board_size_tier)
    SELECT 1, 'SEED-SCANWHITEBOARD-' || id, 140625, 3, id::text,
        60000, true, 60000, true, NULL, true, true, false, 'BETWEEN_2FT_6FT' FROM s8),
v9 AS (INSERT INTO product_variant (product_id, sku, price_cents, weight_kg, options_signature,
        lorry_colombo_cents, lorry_colombo_enabled, lorry_suburb_cents, lorry_suburb_enabled,
        lorry_outer_cents, lorry_outer_enabled, lorry_outer_whatsapp, courier_outer_blocked, board_size_tier)
    SELECT 1, 'SEED-SCANWHITEBOARD-' || id, 150000, 3, id::text,
        60000, true, 60000, true, NULL, true, true, false, 'BETWEEN_2FT_6FT' FROM s9),
v10 AS (INSERT INTO product_variant (product_id, sku, price_cents, weight_kg, options_signature,
        lorry_colombo_cents, lorry_colombo_enabled, lorry_suburb_cents, lorry_suburb_enabled,
        lorry_outer_cents, lorry_outer_enabled, lorry_outer_whatsapp, courier_outer_blocked, board_size_tier)
    SELECT 1, 'SEED-SCANWHITEBOARD-' || id, 168750, 3, id::text,
        60000, true, 60000, true, NULL, true, true, false, 'BETWEEN_2FT_6FT' FROM s10),
v11 AS (INSERT INTO product_variant (product_id, sku, price_cents, weight_kg, options_signature,
        lorry_colombo_cents, lorry_colombo_enabled, lorry_suburb_cents, lorry_suburb_enabled,
        lorry_outer_cents, lorry_outer_enabled, lorry_outer_whatsapp, courier_outer_blocked, board_size_tier)
    SELECT 1, 'SEED-SCANWHITEBOARD-' || id, 187500, 3, id::text,
        60000, true, 60000, true, NULL, true, true, false, 'BETWEEN_2FT_6FT' FROM s11),
v12 AS (INSERT INTO product_variant (product_id, sku, price_cents, weight_kg, options_signature,
        lorry_colombo_cents, lorry_colombo_enabled, lorry_suburb_cents, lorry_suburb_enabled,
        lorry_outer_cents, lorry_outer_enabled, lorry_outer_whatsapp, courier_outer_blocked, board_size_tier)
    SELECT 1, 'SEED-SCANWHITEBOARD-' || id, 225000, 4, id::text,
        60000, true, 60000, true, NULL, true, true, false, 'BETWEEN_2FT_6FT' FROM s12),
v13 AS (INSERT INTO product_variant (product_id, sku, price_cents, weight_kg, options_signature,
        lorry_colombo_cents, lorry_colombo_enabled, lorry_suburb_cents, lorry_suburb_enabled,
        lorry_outer_cents, lorry_outer_enabled, lorry_outer_whatsapp, courier_outer_blocked, board_size_tier)
    SELECT 1, 'SEED-SCANWHITEBOARD-' || id, 234375, 4, id::text,
        60000, true, 80000, true, NULL, true, true, false, 'BETWEEN_2FT_6FT' FROM s13),
v14 AS (INSERT INTO product_variant (product_id, sku, price_cents, weight_kg, options_signature,
        lorry_colombo_cents, lorry_colombo_enabled, lorry_suburb_cents, lorry_suburb_enabled,
        lorry_outer_cents, lorry_outer_enabled, lorry_outer_whatsapp, courier_outer_blocked, board_size_tier)
    SELECT 1, 'SEED-SCANWHITEBOARD-' || id, 281250, 5, id::text,
        60000, true, 80000, true, NULL, true, true, false, 'BETWEEN_2FT_6FT' FROM s14),
v15 AS (INSERT INTO product_variant (product_id, sku, price_cents, weight_kg, options_signature,
        lorry_colombo_cents, lorry_colombo_enabled, lorry_suburb_cents, lorry_suburb_enabled,
        lorry_outer_cents, lorry_outer_enabled, lorry_outer_whatsapp, courier_outer_blocked, board_size_tier)
    SELECT 1, 'SEED-SCANWHITEBOARD-' || id, 337500, 5, id::text,
        60000, true, 80000, true, NULL, true, true, false, 'BETWEEN_2FT_6FT' FROM s15),
v16 AS (INSERT INTO product_variant (product_id, sku, price_cents, weight_kg, options_signature,
        lorry_colombo_cents, lorry_colombo_enabled, lorry_suburb_cents, lorry_suburb_enabled,
        lorry_outer_cents, lorry_outer_enabled, lorry_outer_whatsapp, courier_outer_blocked, board_size_tier)
    SELECT 1, 'SEED-SCANWHITEBOARD-' || id, 300000, 5, id::text,
        80000, true, 100000, true, NULL, true, true, false, 'BETWEEN_2FT_6FT' FROM s16),
v17 AS (INSERT INTO product_variant (product_id, sku, price_cents, weight_kg, options_signature,
        lorry_colombo_cents, lorry_colombo_enabled, lorry_suburb_cents, lorry_suburb_enabled,
        lorry_outer_cents, lorry_outer_enabled, lorry_outer_whatsapp, courier_outer_blocked, board_size_tier)
    SELECT 1, 'SEED-SCANWHITEBOARD-' || id, 375000, 7, id::text,
        80000, true, 100000, true, NULL, true, true, false, 'BETWEEN_2FT_6FT' FROM s17),
v18 AS (INSERT INTO product_variant (product_id, sku, price_cents, weight_kg, options_signature,
        lorry_colombo_cents, lorry_colombo_enabled, lorry_suburb_cents, lorry_suburb_enabled,
        lorry_outer_cents, lorry_outer_enabled, lorry_outer_whatsapp, courier_outer_blocked, board_size_tier)
    SELECT 1, 'SEED-SCANWHITEBOARD-' || id, 450000, 8, id::text,
        80000, true, 100000, true, 150000, true, false, false, 'BETWEEN_2FT_6FT' FROM s18),
v19 AS (INSERT INTO product_variant (product_id, sku, price_cents, weight_kg, options_signature,
        lorry_colombo_cents, lorry_colombo_enabled, lorry_suburb_cents, lorry_suburb_enabled,
        lorry_outer_cents, lorry_outer_enabled, lorry_outer_whatsapp, courier_outer_blocked, board_size_tier)
    SELECT 1, 'SEED-SCANWHITEBOARD-' || id, 600000, 10, id::text,
        80000, true, 100000, true, 150000, true, false, false, 'BETWEEN_2FT_6FT' FROM s19),
v20 AS (INSERT INTO product_variant (product_id, sku, price_cents, weight_kg, options_signature,
        lorry_colombo_cents, lorry_colombo_enabled, lorry_suburb_cents, lorry_suburb_enabled,
        lorry_outer_cents, lorry_outer_enabled, lorry_outer_whatsapp, courier_outer_blocked, board_size_tier)
    SELECT 1, 'SEED-SCANWHITEBOARD-' || id, 562500, 10, id::text,
        80000, true, 100000, true, 150000, true, false, false, 'BETWEEN_2FT_6FT' FROM s20),
v21 AS (INSERT INTO product_variant (product_id, sku, price_cents, weight_kg, options_signature,
        lorry_colombo_cents, lorry_colombo_enabled, lorry_suburb_cents, lorry_suburb_enabled,
        lorry_outer_cents, lorry_outer_enabled, lorry_outer_whatsapp, courier_outer_blocked, board_size_tier)
    SELECT 1, 'SEED-SCANWHITEBOARD-' || id, 750000, 13, id::text,
        80000, true, 100000, true, 150000, true, false, false, 'BETWEEN_2FT_6FT' FROM s21),
v22 AS (INSERT INTO product_variant (product_id, sku, price_cents, weight_kg, options_signature,
        lorry_colombo_cents, lorry_colombo_enabled, lorry_suburb_cents, lorry_suburb_enabled,
        lorry_outer_cents, lorry_outer_enabled, lorry_outer_whatsapp, courier_outer_blocked, board_size_tier)
    SELECT 1, 'SEED-SCANWHITEBOARD-' || id, 675000, 11, id::text,
        80000, true, 100000, true, 150000, true, true, true, 'BETWEEN_2FT_6FT' FROM s22),
v23 AS (INSERT INTO product_variant (product_id, sku, price_cents, weight_kg, options_signature,
        lorry_colombo_cents, lorry_colombo_enabled, lorry_suburb_cents, lorry_suburb_enabled,
        lorry_outer_cents, lorry_outer_enabled, lorry_outer_whatsapp, courier_outer_blocked, board_size_tier)
    SELECT 1, 'SEED-SCANWHITEBOARD-' || id, 900000, 15, id::text,
        100000, true, 150000, true, 200000, true, true, true, 'BETWEEN_2FT_6FT' FROM s23),
v24 AS (INSERT INTO product_variant (product_id, sku, price_cents, weight_kg, options_signature,
        lorry_colombo_cents, lorry_colombo_enabled, lorry_suburb_cents, lorry_suburb_enabled,
        lorry_outer_cents, lorry_outer_enabled, lorry_outer_whatsapp, courier_outer_blocked, board_size_tier)
    SELECT 1, 'SEED-SCANWHITEBOARD-' || id, 1200000, 20, id::text,
        100000, true, 150000, true, 200000, true, true, true, 'BETWEEN_2FT_6FT' FROM s24)
SELECT 1;
