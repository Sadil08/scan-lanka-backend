-- V22 — allow system/automated audit entries with no admin actor.
-- SettingsService.put(key, value, adminId) accepts a null adminId for non-admin-initiated changes;
-- the NOT NULL constraint turned those into a constraint violation (HTTP 500). A null admin_id now
-- denotes a system actor; the app_user FK still applies when an id is present.
ALTER TABLE admin_audit_log ALTER COLUMN admin_id DROP NOT NULL;
