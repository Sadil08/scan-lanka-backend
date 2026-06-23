-- V21 — CMS content pages (15-content-pages).
CREATE TABLE content_page (
    slug       VARCHAR(80)  PRIMARY KEY,
    title      VARCHAR(200) NOT NULL,
    body_html  TEXT         NOT NULL,
    updated_by BIGINT       REFERENCES app_user(id),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO content_page (slug, title, body_html) VALUES
    ('returns', 'Returns & refunds',
     '<p>Returns and refunds are handled offline. Please contact us by phone or WhatsApp with your order number.</p>'),
    ('privacy', 'Privacy policy (PDPA)',
     '<p>Scan Lanka respects your privacy. We collect order and contact details only to fulfil your purchase and support requests. We do not sell personal data.</p>'),
    ('terms', 'Terms & conditions',
     '<p>By using this site you agree to our standard terms of sale for products listed. Prices are in LKR unless shown as indicative for international visitors.</p>'),
    ('about', 'About Scan Lanka',
     '<p>Scan Lanka Trading Co. has supplied boards and teaching equipment across Sri Lanka since 1998.</p>');
