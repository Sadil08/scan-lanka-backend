-- V54 — Merge the split menu-board products into one product per style (owner 2026-07-24).
--
-- The "Single Side Teak Menu Board 2 x 2 1/2" and "Dual Teak Menu Board 2 x 2 1/2" rows sit in the
-- middle of their size lists, so the seed's base-name grouping broke each style into three products:
--   single-side-menu-board (2x1½, 2x2½) · single-side-teak-menu-board (teak) · single-side-menu-board-2 (2x3, 4x2)
--   dual-menu-board        (2x1½, 2x2½) · dual-teak-menu-board        (teak) · dual-menu-board-2        (2x3, 4x2)
-- Per owner, each style is ONE product; the teak row becomes a size option labelled "2 x 2 1/2
-- (Teak)" placed right after "2 x 2 1/2".
--
-- The plain sibling (a VARIANT) has its size options + variants re-pointed (V51 pattern); the teak
-- product (SINGLE) becomes a new option + variant built from its own row (price/weight/shipping
-- copied). Options are then renumbered into sheet order. Emptied products are dropped. Order-line
-- snapshots use ON DELETE SET NULL, so history is unaffected. Naturally idempotent (siblings/teak
-- slugs are gone after the first run, so re-runs are no-ops).

DO $$
DECLARE
    fam           RECORD;
    canonical_id  BIGINT;
    canonical_grp BIGINT;
    base          INT;
    sib_id        BIGINT;
    sib_grp       BIGINT;
    teak_id       BIGINT;
    teak_opt      BIGINT;
BEGIN
    FOR fam IN
        SELECT * FROM (VALUES
            ('single-side-menu-board', 'single-side-menu-board-2', 'single-side-teak-menu-board'),
            ('dual-menu-board',        'dual-menu-board-2',        'dual-teak-menu-board')
        ) AS v(canonical, sibling, teak)
    LOOP
        SELECT id INTO canonical_id FROM product WHERE slug = fam.canonical;
        CONTINUE WHEN canonical_id IS NULL;

        SELECT id INTO canonical_grp FROM spec_group
         WHERE product_id = canonical_id AND price_affecting
         ORDER BY display_order, id LIMIT 1;
        CONTINUE WHEN canonical_grp IS NULL;

        -- Plain sibling (VARIANT): move its size options into the canonical group and re-point its
        -- variants + images. (INTO sets both vars to NULL when the sibling is already gone.)
        SELECT p.id, sg.id INTO sib_id, sib_grp
          FROM product p
          JOIN spec_group sg ON sg.product_id = p.id AND sg.price_affecting
         WHERE p.slug = fam.sibling;
        IF sib_id IS NOT NULL THEN
            SELECT COALESCE(MAX(display_order), -1) + 1 INTO base
              FROM spec_option WHERE spec_group_id = canonical_grp;
            UPDATE spec_option SET spec_group_id = canonical_grp, display_order = base + display_order
             WHERE spec_group_id = sib_grp;
            UPDATE product_variant SET product_id = canonical_id WHERE product_id = sib_id;
            UPDATE product_image SET product_id = canonical_id, is_preview = false
             WHERE product_id = sib_id;
        END IF;

        -- Teak (SINGLE): new size option + a variant built from its own row.
        SELECT id INTO teak_id FROM product WHERE slug = fam.teak;
        IF teak_id IS NOT NULL THEN
            SELECT COALESCE(MAX(display_order), -1) + 1 INTO base
              FROM spec_option WHERE spec_group_id = canonical_grp;
            INSERT INTO spec_option (spec_group_id, value, display_order)
            VALUES (canonical_grp, '2 x 2 1/2 (Teak)', base)
            RETURNING id INTO teak_opt;

            INSERT INTO product_variant (product_id, sku, price_cents, stock_qty, weight_kg,
                length_cm, width_cm, height_cm, handling_class, board_size_tier, whatsapp_only,
                lorry_colombo_cents, lorry_suburb_cents, lorry_outer_cents,
                lorry_colombo_gate_cents, lorry_suburb_gate_cents, lorry_outer_gate_cents,
                lorry_colombo_enabled, lorry_suburb_enabled, lorry_outer_enabled,
                lorry_outer_whatsapp, courier_outer_blocked, courier_enabled,
                active, options_signature)
            SELECT canonical_id, sku || '-' || teak_opt, single_price_cents, stock_qty, weight_kg,
                length_cm, width_cm, height_cm, handling_class, board_size_tier, whatsapp_only,
                lorry_colombo_cents, lorry_suburb_cents, lorry_outer_cents,
                lorry_colombo_gate_cents, lorry_suburb_gate_cents, lorry_outer_gate_cents,
                lorry_colombo_enabled, lorry_suburb_enabled, lorry_outer_enabled,
                lorry_outer_whatsapp, courier_outer_blocked, courier_enabled,
                true, teak_opt::text
            FROM product WHERE id = teak_id;

            UPDATE product_image SET product_id = canonical_id, is_preview = false
             WHERE product_id = teak_id;
        END IF;

        DELETE FROM product WHERE slug IN (fam.sibling, fam.teak);

        -- Safety: if the canonical ended up with images but no preview, promote one.
        UPDATE product_image SET is_preview = true
         WHERE id = (SELECT id FROM product_image WHERE product_id = canonical_id
                      ORDER BY display_order, id LIMIT 1)
           AND NOT EXISTS (SELECT 1 FROM product_image
                            WHERE product_id = canonical_id AND is_preview);

        -- Renumber into the sheet's size order (teak sits right after 2 x 2 1/2).
        UPDATE spec_option SET display_order = CASE value
                WHEN '2 x 1 1/2'        THEN 0
                WHEN '2 x 2 1/2'        THEN 1
                WHEN '2 x 2 1/2 (Teak)' THEN 2
                WHEN '2 x 3'            THEN 3
                WHEN '4 x 2'            THEN 4
                ELSE display_order END
         WHERE spec_group_id = canonical_grp;

        UPDATE product
           SET price_range_min_cents = (SELECT MIN(price_cents) FROM product_variant
                                         WHERE product_id = canonical_id AND active),
               price_range_max_cents = (SELECT MAX(price_cents) FROM product_variant
                                         WHERE product_id = canonical_id AND active)
         WHERE id = canonical_id;
    END LOOP;
END $$;
