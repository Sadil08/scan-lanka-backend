-- V52 — Fold the A4/A3/A2 canvas boards into the single Scan Canvas Board (owner 2026-07-24).
--
-- The sheet's three "letter size" canvas rows carry an alias after the inch dimension
-- ("Scan Canvas Board 12 x 8 Inches / A4 Size Canvas Board"). The seed's size parser keeps only the
-- inch token, so each aliased row lost its base-name match and was seeded as its OWN single-price
-- product (scan-canvas-board-a4/a3/a2-size-canvas-board). V51 merged the plain duplicates but not
-- these, so the storefront shows four canvas products and the canonical one is MISSING the 12x8,
-- 12x17 and 18x24 sizes entirely.
--
-- This folds all three into scan-canvas-board as three appended size options that keep the A-size
-- alias in the label, then drops the emptied single products. Price/weight/shipping are copied from
-- each source product row (not hardcoded) so any admin edits carry over. Order-line snapshots use
-- ON DELETE SET NULL, so history is unaffected. Guarded so it's a no-op once merged.

DO $$
DECLARE
    canonical_id  BIGINT;
    canonical_grp BIGINT;
    base          INT;
    rec           RECORD;
    new_opt       BIGINT;
    src_id        BIGINT;
BEGIN
    SELECT id INTO canonical_id FROM product WHERE slug = 'scan-canvas-board';
    IF canonical_id IS NULL THEN
        RETURN;  -- nothing to merge into
    END IF;
    IF NOT EXISTS (SELECT 1 FROM product WHERE slug IN (
            'scan-canvas-board-a4-size-canvas-board',
            'scan-canvas-board-a3-size-canvas-board',
            'scan-canvas-board-a2-size-canvas-board')) THEN
        RETURN;  -- already merged
    END IF;

    SELECT id INTO canonical_grp
      FROM spec_group
     WHERE product_id = canonical_id AND price_affecting
     ORDER BY display_order, id
     LIMIT 1;
    IF canonical_grp IS NULL THEN
        RETURN;  -- canonical has no size group; leave data untouched rather than guess
    END IF;

    -- Append after every option already in the group (V51 folded the plain duplicates in at
    -- offset 100/200/300, so read the real max rather than assuming 0..n).
    SELECT COALESCE(MAX(display_order), -1) + 1 INTO base
      FROM spec_option WHERE spec_group_id = canonical_grp;

    FOR rec IN
        SELECT * FROM (VALUES
            ('scan-canvas-board-a4-size-canvas-board', '12 x 8 Inches / A4 Size',  0),
            ('scan-canvas-board-a3-size-canvas-board', '12 x 17 Inches / A3 Size', 1),
            ('scan-canvas-board-a2-size-canvas-board', '18 x 24 Inches / A2 Size', 2)
        ) AS v(slug, label, ord) ORDER BY v.ord
    LOOP
        SELECT id INTO src_id FROM product WHERE slug = rec.slug;
        CONTINUE WHEN src_id IS NULL;

        INSERT INTO spec_option (spec_group_id, value, display_order)
        VALUES (canonical_grp, rec.label, base + rec.ord)
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
        FROM product WHERE id = src_id;

        -- Defensive: move any images off the source (usually none — canvas photos live on the
        -- canonical product). Never create a second preview.
        UPDATE product_image SET product_id = canonical_id, is_preview = false
         WHERE product_id = src_id;
    END LOOP;

    DELETE FROM product WHERE slug IN (
        'scan-canvas-board-a4-size-canvas-board',
        'scan-canvas-board-a3-size-canvas-board',
        'scan-canvas-board-a2-size-canvas-board');

    -- Safety: if the canonical ended up with images but no preview, promote one.
    UPDATE product_image SET is_preview = true
     WHERE id = (SELECT id FROM product_image WHERE product_id = canonical_id
                  ORDER BY display_order, id LIMIT 1)
       AND NOT EXISTS (SELECT 1 FROM product_image WHERE product_id = canonical_id AND is_preview);

    UPDATE product
       SET price_range_min_cents = (SELECT MIN(price_cents) FROM product_variant
                                     WHERE product_id = canonical_id AND active),
           price_range_max_cents = (SELECT MAX(price_cents) FROM product_variant
                                     WHERE product_id = canonical_id AND active)
     WHERE id = canonical_id;
END $$;
