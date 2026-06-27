-- V27 — Retire the legacy DIM/zone delivery model (replaced by the two-rail model: postal_zone +
-- courier_rate_card + per-product lorry charges, V25/V26). These tables are no longer read by any code.
DROP TABLE IF EXISTS delivery_zone_postal_code;
DROP TABLE IF EXISTS delivery_zone;
DROP TABLE IF EXISTS delivery_config;
