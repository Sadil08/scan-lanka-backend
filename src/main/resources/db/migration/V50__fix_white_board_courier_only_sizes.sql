-- V50 — Fix three small white-board sizes that V49 mis-assigned to the wrong delivery rail.
--
-- V49 enumerated the courier-only sizes (Scan White Board 1x1 .. 4x2, which ship by Domex only) as
-- '1 x 1', '2 x 1' and '2.5 x 1 1/2' — but the seeded spec_option values for those sizes are actually
-- '1x1', '2x1' and '2 1/2 x 1 1/2'. The spelling mismatch meant V49's "sizes NOT IN courier_only_sizes"
-- clause treated all three as ABOVE 4x2: it switched their courier OFF and their lorry ON (lorry-only) —
-- the exact opposite of the intended launch rail. A customer ordering just a 1x1 or 2x1 white board was
-- being offered the in-house lorry instead of Domex.
--
-- Restore the intended rail: courier ON, lorry OFF in every zone, no outer-WhatsApp flag (with the lorry
-- off entirely an outer address reads "lorry unavailable, use the courier", not "contact us for the lorry")
-- — matching how V49 configured the other 1x1..4x2 sizes.
UPDATE product_variant pv
SET courier_enabled = true,
    lorry_colombo_enabled = false,
    lorry_suburb_enabled = false,
    lorry_outer_enabled = false,
    lorry_outer_whatsapp = false
FROM product p, spec_option so
WHERE p.id = pv.product_id AND p.slug = 'scan-white-board'
  AND so.id::text = pv.options_signature
  AND so.value IN ('1x1', '2x1', '2 1/2 x 1 1/2');
