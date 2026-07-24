-- V55 — Fold the Buffet Tag into the Table Top product (owner 2026-07-24).
--
-- The sheet lists three table tops together — 4" x 2" Buffet Tag / Table Top, 7" x 4", 9" x 7" — but
-- the seed split the 4" x 2" row off (its "Buffet Tag" name broke the base-name match) into its own
-- single-price product, leaving table-top with just 7" x 4" and 9" x 7". This folds the buffet tag
-- in as the 4" x 2" size option (built from its own row: price/weight/shipping copied), reorders the
-- three by size, and drops the emptied product. The "8" x 3" Price Tag With Stick" is a different
-- item and stays its own product. Order-line snapshots use ON DELETE SET NULL. No-op once merged.

DO $$
DECLARE
    canonical_id  BIGINT;
    canonical_grp BIGINT;
    base          INT;
    buffet_id     BIGINT;
    new_opt       BIGINT;
BEGIN
    SELECT id INTO canonical_id FROM product WHERE slug = 'table-top';
    IF canonical_id IS NULL THEN
        RETURN;
    END IF;
    SELECT id INTO buffet_id FROM product WHERE slug = 'buffet-tag-table-top';
    IF buffet_id IS NULL THEN
        RETURN;  -- already merged
    END IF;

    SELECT id INTO canonical_grp FROM spec_group
     WHERE product_id = canonical_id AND price_affecting
     ORDER BY display_order, id LIMIT 1;
    IF canonical_grp IS NULL THEN
        RETURN;
    END IF;

    SELECT COALESCE(MAX(display_order), -1) + 1 INTO base
      FROM spec_option WHERE spec_group_id = canonical_grp;
    INSERT INTO spec_option (spec_group_id, value, display_order)
    VALUES (canonical_grp, '4" x 2" (Buffet Tag)', base)
    RETURNING id INTO new_opt;

    INSERT INTO product_variant (product_id, sku, price_cents, stock_qty, weight_kg,
        length_cm, width_cm, height_cm, handling_class, board_size_tier, whatsapp_only,
        lorry_colombo_cents, lorry_suburb_cents, lorry_outer_cents,
        lorry_colombo_gate_cents, lorry_suburb_gate_cents, lorry_outer_gate_cents,
        lorry_colombo_enabled, lorry_suburb_enabled, lorry_outer_enabled,
        lorry_outer_whatsapp, courier_outer_blocked, courier_enabled,
        active, options_signature)
    SELECT canonical_id, sku || '-' || new_opt, single_price_cents, stock_qty, weight_kg,
        length_cm, width_cm, height_cm, handling_class, board_size_tier, whatsapp_only,
        lorry_colombo_cents, lorry_suburb_cents, lorry_outer_cents,
        lorry_colombo_gate_cents, lorry_suburb_gate_cents, lorry_outer_gate_cents,
        lorry_colombo_enabled, lorry_suburb_enabled, lorry_outer_enabled,
        lorry_outer_whatsapp, courier_outer_blocked, courier_enabled,
        true, new_opt::text
    FROM product WHERE id = buffet_id;

    UPDATE product_image SET product_id = canonical_id, is_preview = false
     WHERE product_id = buffet_id;

    DELETE FROM product WHERE id = buffet_id;

    -- Safety: if the canonical ended up with images but no preview, promote one.
    UPDATE product_image SET is_preview = true
     WHERE id = (SELECT id FROM product_image WHERE product_id = canonical_id
                  ORDER BY display_order, id LIMIT 1)
       AND NOT EXISTS (SELECT 1 FROM product_image WHERE product_id = canonical_id AND is_preview);

    -- Order the three sizes smallest first.
    UPDATE spec_option SET display_order = CASE value
            WHEN '4" x 2" (Buffet Tag)' THEN 0
            WHEN '7" x 4"'              THEN 1
            WHEN '9" x 7"'              THEN 2
            ELSE display_order END
     WHERE spec_group_id = canonical_grp;

    UPDATE product
       SET price_range_min_cents = (SELECT MIN(price_cents) FROM product_variant
                                     WHERE product_id = canonical_id AND active),
           price_range_max_cents = (SELECT MAX(price_cents) FROM product_variant
                                     WHERE product_id = canonical_id AND active)
     WHERE id = canonical_id;
END $$;
