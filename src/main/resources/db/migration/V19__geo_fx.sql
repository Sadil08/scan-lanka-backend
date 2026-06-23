-- V19 — FX rates + currency config seed (13-geo-pricing-i18n).
CREATE TABLE fx_rate (
    currency     VARCHAR(3)   PRIMARY KEY,
    rate_to_lkr  NUMERIC(14,6) NOT NULL CHECK (rate_to_lkr > 0),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO fx_rate (currency, rate_to_lkr) VALUES ('USD', 300.000000);

INSERT INTO app_setting (key, value) VALUES
    ('currency_default_foreign', 'USD'),
    ('currency_enabled', 'LKR,USD')
ON CONFLICT (key) DO NOTHING;
