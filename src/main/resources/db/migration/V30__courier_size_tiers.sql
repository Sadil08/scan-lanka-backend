-- Courier charges by board size tier + four area zones (client rate card, 2026).
-- Replaces weight × per-kg model.

ALTER TABLE product ADD COLUMN IF NOT EXISTS board_size_tier VARCHAR(32);
ALTER TABLE product_variant ADD COLUMN IF NOT EXISTS board_size_tier VARCHAR(32);

-- Existing couriable products default to the larger tier.
UPDATE product SET board_size_tier = 'BETWEEN_2FT_6FT'
  WHERE board_size_tier IS NULL AND weight_kg IS NOT NULL;
UPDATE product_variant SET board_size_tier = 'BETWEEN_2FT_6FT'
  WHERE board_size_tier IS NULL AND weight_kg IS NOT NULL;

CREATE TABLE courier_rate_card_new (
    zone       VARCHAR(32)  NOT NULL,
    size_tier  VARCHAR(32)  NOT NULL,
    flat_cents BIGINT       NOT NULL CHECK (flat_cents >= 0),
    PRIMARY KEY (zone, size_tier)
);

INSERT INTO courier_rate_card_new (zone, size_tier, flat_cents) VALUES
    ('CITY_LIMITS',  'UNDER_2FT',        50000),
    ('CITY_LIMITS',  'BETWEEN_2FT_6FT', 100000),
    ('SUBURBS',      'UNDER_2FT',        75000),
    ('SUBURBS',      'BETWEEN_2FT_6FT', 125000),
    ('OUTSTATION',   'UNDER_2FT',       100000),
    ('OUTSTATION',   'BETWEEN_2FT_6FT', 150000),
    ('FARAWAY',      'UNDER_2FT',       150000),
    ('FARAWAY',      'BETWEEN_2FT_6FT', 200000);

DROP TABLE courier_rate_card;
ALTER TABLE courier_rate_card_new RENAME TO courier_rate_card;

-- Remap courier zones on postal codes (Colombo area definitions).
ALTER TABLE postal_zone DROP CONSTRAINT IF EXISTS postal_zone_courier_zone_check;

UPDATE postal_zone SET courier_zone = 'CITY_LIMITS'  WHERE courier_zone = 'COLOMBO_1_15';
UPDATE postal_zone SET courier_zone = 'FARAWAY'       WHERE courier_zone = 'JAFFNA_NORTH';
UPDATE postal_zone SET courier_zone = 'SUBURBS'
  WHERE courier_zone = 'OTHER' AND lorry_zone IN ('SUBURB', 'COLOMBO');
UPDATE postal_zone SET courier_zone = 'OUTSTATION'    WHERE courier_zone = 'OTHER';

ALTER TABLE postal_zone ADD CONSTRAINT postal_zone_courier_zone_check
    CHECK (courier_zone IN ('CITY_LIMITS', 'SUBURBS', 'OUTSTATION', 'FARAWAY'));
