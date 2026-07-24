-- V53 — Merge the four wooden easels into one "Wooden Easel" product (owner 2026-07-24).
--
-- Easel "sizes" are words (Mini/Kids/Medium/Large), not dimensions, so the seed's grouping engine
-- could not see them as sizes of one product and emitted four separate single-price products. This
-- creates one canonical "Wooden Easel" (VARIANT) with a named Size option per easel, building each
-- variant from the source product row (price/weight/shipping copied, not hardcoded) and re-pointing
-- any images. The four originals are then dropped.
--
-- Per owner: the height differs per size, so the single product description lists all four heights
-- inline (there is no per-variant description in the schema). "Wooden Easel Art Board" is a distinct
-- item (a board that ships with a Large Easel) and stays its own product — it is NOT touched here.
--
-- Order-line snapshots use ON DELETE SET NULL, so history is unaffected. Guarded (no-op once merged).

DO $$
DECLARE
    new_id   BIGINT;
    grp_id   BIGINT;
    ord      INT;
    rec      RECORD;
    new_opt  BIGINT;
    src_id   BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM product WHERE slug = 'wooden-easel') THEN
        RETURN;  -- already merged
    END IF;
    IF NOT EXISTS (SELECT 1 FROM product
                    WHERE slug IN ('mini-easel', 'kids-easel', 'medium-easel', 'large-easel')) THEN
        RETURN;  -- sources gone; nothing to merge
    END IF;

    SELECT MIN(display_order) INTO ord FROM product
     WHERE slug IN ('mini-easel', 'kids-easel', 'medium-easel', 'large-easel');

    INSERT INTO product (name, slug, sku, category, category_group, price_mode,
        description, display_order, active)
    VALUES ('Wooden Easel', 'wooden-easel', 'SEED-WOODENEASEL', 'Wooden Easel', 'Art Supplies',
        'VARIANT',
        'Made Out Of High-Quality Pine Wood, Eco friendly & Easy To Assemble With Instruction Notes '
        'Provided. Made in Sri Lanka by Scan Lanka. Height by size — Mini: 7 inch · Kids: 3.5 ft '
        '(adjustable) · Medium: 2 ft · Large: 5 ft (adjustable).',
        ord, true)
    RETURNING id INTO new_id;

    INSERT INTO spec_group (product_id, name, price_affecting, display_order)
    VALUES (new_id, 'Size', true, 0)
    RETURNING id INTO grp_id;

    FOR rec IN
        SELECT * FROM (VALUES
            ('mini-easel',   'Mini',   0),
            ('kids-easel',   'Kids',   1),
            ('medium-easel', 'Medium', 2),
            ('large-easel',  'Large',  3)
        ) AS v(slug, label, dord) ORDER BY v.dord
    LOOP
        SELECT id INTO src_id FROM product WHERE slug = rec.slug;
        CONTINUE WHEN src_id IS NULL;

        INSERT INTO spec_option (spec_group_id, value, display_order)
        VALUES (grp_id, rec.label, rec.dord)
        RETURNING id INTO new_opt;

        INSERT INTO product_variant (product_id, sku, price_cents, stock_qty, weight_kg,
            length_cm, width_cm, height_cm, handling_class, board_size_tier, whatsapp_only,
            lorry_colombo_cents, lorry_suburb_cents, lorry_outer_cents,
            lorry_colombo_gate_cents, lorry_suburb_gate_cents, lorry_outer_gate_cents,
            lorry_colombo_enabled, lorry_suburb_enabled, lorry_outer_enabled,
            lorry_outer_whatsapp, courier_outer_blocked, courier_enabled,
            active, options_signature)
        SELECT new_id, sku || '-' || new_opt, single_price_cents, stock_qty, weight_kg,
            length_cm, width_cm, height_cm, handling_class, board_size_tier, whatsapp_only,
            lorry_colombo_cents, lorry_suburb_cents, lorry_outer_cents,
            lorry_colombo_gate_cents, lorry_suburb_gate_cents, lorry_outer_gate_cents,
            lorry_colombo_enabled, lorry_suburb_enabled, lorry_outer_enabled,
            lorry_outer_whatsapp, courier_outer_blocked, courier_enabled,
            true, new_opt::text
        FROM product WHERE id = src_id;

        UPDATE product_image SET product_id = new_id, is_preview = false
         WHERE product_id = src_id;
    END LOOP;

    -- Promote one preview from whatever images were re-pointed (if any, and none already set).
    UPDATE product_image SET is_preview = true
     WHERE id = (SELECT id FROM product_image WHERE product_id = new_id
                  ORDER BY display_order, id LIMIT 1)
       AND NOT EXISTS (SELECT 1 FROM product_image WHERE product_id = new_id AND is_preview);

    DELETE FROM product WHERE slug IN ('mini-easel', 'kids-easel', 'medium-easel', 'large-easel');

    UPDATE product
       SET price_range_min_cents = (SELECT MIN(price_cents) FROM product_variant
                                     WHERE product_id = new_id AND active),
           price_range_max_cents = (SELECT MAX(price_cents) FROM product_variant
                                     WHERE product_id = new_id AND active)
     WHERE id = new_id;
END $$;
