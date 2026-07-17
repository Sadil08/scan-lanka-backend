-- V49 — Launch rail availability (owner 2026-07-16):
--   * Scan White Board sizes 1x1 .. 4x2 ship by COURIER ONLY — the in-house lorry is switched off
--     for every zone on those variants (V35 per-zone switches; cells stay seeded for reference).
--   * Everything else ships by the IN-HOUSE LORRY ONLY: courier is switched off (V48 courier_enabled),
--     Colombo/Suburb keep their seeded per-cell prices/gates, and OUTER lorry delivery is arranged
--     via WhatsApp contact (lorry_outer_whatsapp) — the customer messages us for a charge and order.
-- All of this is data over the loosely-coupled per-item switches; admin can flip any of it later.

-- Duplicate-size cleanup first: V36 seeded the sheet's "2.5 x 1 1/2" and V41 (written against a DB
-- where the V36 white board had only 3 sizes) re-added the same size as "2 1/2 x 1 1/2" — on a
-- freshly-seeded database BOTH exist. Retire the V41 duplicate: deactivate its variant and remove
-- its size option so the PDP dropdown can't select it. Guarded on the V36 spelling existing.
UPDATE product_variant pv
SET active = false
FROM product p, spec_option so
WHERE p.id = pv.product_id AND p.slug = 'scan-white-board'
  AND so.id::text = pv.options_signature AND so.value = '2 1/2 x 1 1/2'
  AND EXISTS (SELECT 1 FROM spec_option s2 JOIN spec_group g2 ON g2.id = s2.spec_group_id
              WHERE g2.product_id = p.id AND s2.value = '2.5 x 1 1/2');
DELETE FROM spec_option so
USING spec_group g, product p
WHERE so.spec_group_id = g.id AND g.product_id = p.id AND p.slug = 'scan-white-board'
  AND so.value = '2 1/2 x 1 1/2'
  AND EXISTS (SELECT 1 FROM spec_option s2 WHERE s2.spec_group_id = g.id AND s2.value = '2.5 x 1 1/2');

-- The white board sizes that keep the courier (everything up to and including 4 x 2).
CREATE TEMPORARY TABLE courier_only_sizes (value TEXT PRIMARY KEY);
INSERT INTO courier_only_sizes (value) VALUES
    ('1 x 1'), ('1 x 1 1/2'), ('2 x 1'), ('1 1/2 x 1 1/2'), ('2 x 1 1/2'), ('2.5 x 1 1/2'),
    ('2 x 2'), ('3 x 1 1/2'), ('2 x 2 1/2'), ('2 x 3'), ('2 1/2 x 2 1/2'), ('3 x 2 1/2'),
    ('3 x 3'), ('4 x 2');

-- 1) Courier off for every product except Scan White Board (per-variant refinement below).
UPDATE product SET courier_enabled = false WHERE slug <> 'scan-white-board';

-- 2) Scan White Board: the sizes ABOVE 4x2 lose the courier too (lorry-only like everything else),
--    and being lorry-only their outer delivery is WhatsApp-arranged.
UPDATE product_variant pv
SET courier_enabled = false, lorry_outer_whatsapp = true
FROM product p, spec_option so
WHERE p.id = pv.product_id AND p.slug = 'scan-white-board'
  AND so.id::text = pv.options_signature
  AND so.value NOT IN (SELECT value FROM courier_only_sizes);

-- 3) Scan White Board 1x1..4x2: courier only — no lorry into any zone. Clear the seed's
--    lorry_outer_whatsapp on them too: with the lorry off entirely, an outer address must read
--    "lorry unavailable, use the courier", not "contact us for the lorry".
UPDATE product_variant pv
SET lorry_colombo_enabled = false, lorry_suburb_enabled = false, lorry_outer_enabled = false,
    lorry_outer_whatsapp = false
FROM product p, spec_option so
WHERE p.id = pv.product_id AND p.slug = 'scan-white-board'
  AND so.id::text = pv.options_signature
  AND so.value IN (SELECT value FROM courier_only_sizes);

-- 4) Everything else: outer lorry delivery is WhatsApp-arranged (product-level flag ORs into every
--    variant). Colombo/Suburb keep their seeded prices/gates untouched.
UPDATE product SET lorry_outer_whatsapp = true WHERE slug <> 'scan-white-board';

DROP TABLE courier_only_sizes;
