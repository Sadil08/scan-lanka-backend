-- Customer hotline / WhatsApp changed to 071 977 7447 (client request, 2026-10).
INSERT INTO app_setting (key, value) VALUES
    ('whatsapp_local', '0719777447'),
    ('whatsapp_intl', '0719777447')
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;

UPDATE content_page
SET body_html = replace(replace(body_html, '071 781 7447', '071 977 7447'), '0717817447', '0719777447'),
    updated_at = now()
WHERE body_html LIKE '%071 781 7447%' OR body_html LIKE '%0717817447%';
