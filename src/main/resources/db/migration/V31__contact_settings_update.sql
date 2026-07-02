-- Updated customer contact numbers (client request, 2026).
UPDATE app_setting SET value = '0717817447' WHERE key = 'whatsapp_local';
UPDATE app_setting SET value = '0717817447' WHERE key = 'whatsapp_intl';

INSERT INTO app_setting (key, value) VALUES
    ('whatsapp_local', '0717817447'),
    ('whatsapp_intl', '0717817447')
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;
