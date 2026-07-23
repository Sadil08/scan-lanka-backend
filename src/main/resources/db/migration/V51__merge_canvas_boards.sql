-- V51 — Merge the duplicate "Scan Canvas Board" products into one (owner 2026-07-23).
--
-- The seed created four separate VARIANT products all named "Scan Canvas Board"
-- (scan-canvas-board / -2 / -3 / -4), each carrying a different slice of the size range, so the
-- storefront and admin list showed the same product four times with only a few sizes each. The
-- size ranges don't overlap, so we fold every size option and its variant into the canonical
-- scan-canvas-board and drop the emptied duplicates — leaving one canvas product with all sizes.
--
-- Variants are re-pointed (not recreated), so each keeps its own price, weight, shipping tiers and
-- options_signature (the signature is the spec_option id, which is globally unique — no collision).
-- Order/quote line snapshots reference product/variant with ON DELETE SET NULL, and we never delete
-- a variant here, so historical orders are unaffected. Guarded so it's a no-op if already merged.

DO $$
DECLARE
    canonical_id  BIGINT;
    canonical_grp BIGINT;
    dup           RECORD;
    base          INT := 100;   -- push merged options above the canonical group's existing 0..n
BEGIN
    SELECT id INTO canonical_id FROM product WHERE slug = 'scan-canvas-board';
    IF canonical_id IS NULL THEN
        RETURN;  -- nothing to merge into
    END IF;

    -- The canonical product's price-affecting "Size" group receives all the sizes.
    SELECT id INTO canonical_grp
      FROM spec_group
     WHERE product_id = canonical_id AND price_affecting
     ORDER BY display_order, id
     LIMIT 1;
    IF canonical_grp IS NULL THEN
        RETURN;  -- canonical has no size group; leave data untouched rather than guess
    END IF;

    -- Process the duplicates in size order (-2 < -3 < -4) so the merged options stay ascending.
    FOR dup IN
        SELECT p.id AS pid, sg.id AS gid, p.slug
          FROM product p
          JOIN spec_group sg ON sg.product_id = p.id AND sg.price_affecting
         WHERE p.slug IN ('scan-canvas-board-2', 'scan-canvas-board-3', 'scan-canvas-board-4')
         ORDER BY array_position(
             ARRAY['scan-canvas-board-2','scan-canvas-board-3','scan-canvas-board-4'], p.slug)
    LOOP
        -- Move this duplicate's size options into the canonical group, offset so they sort after
        -- everything merged before them (relative order within the group is preserved).
        UPDATE spec_option
           SET spec_group_id = canonical_grp,
               display_order  = base + display_order
         WHERE spec_group_id = dup.gid;
        base := base + 100;

        -- Re-point this duplicate's variants onto the canonical product.
        UPDATE product_variant SET product_id = canonical_id WHERE product_id = dup.pid;

        -- Defensive: move any images off the duplicate (there normally are none — the bulk import
        -- put all canvas photos on the canonical product). Never create a second preview.
        UPDATE product_image
           SET product_id = canonical_id, is_preview = false
         WHERE product_id = dup.pid;
    END LOOP;

    -- Drop the now-empty duplicate products (cascades their emptied Size groups).
    DELETE FROM product
     WHERE slug IN ('scan-canvas-board-2', 'scan-canvas-board-3', 'scan-canvas-board-4');

    -- Refresh the canonical product's displayed price range from its full variant set.
    UPDATE product
       SET price_range_min_cents = (SELECT MIN(price_cents) FROM product_variant
                                     WHERE product_id = canonical_id AND active),
           price_range_max_cents = (SELECT MAX(price_cents) FROM product_variant
                                     WHERE product_id = canonical_id AND active)
     WHERE id = canonical_id;
END $$;
