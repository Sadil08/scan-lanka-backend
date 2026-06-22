-- V10 — Non-superuser role for RLS enforcement in tests/runtime (global/02 §6).
-- Superusers bypass RLS; integration tests SET ROLE to this user to prove policies work.

DO $$ BEGIN
    CREATE ROLE scanlanka_rls LOGIN PASSWORD 'rls_test_only' NOSUPERUSER NOBYPASSRLS;
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

GRANT USAGE ON SCHEMA public TO scanlanka_rls;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO scanlanka_rls;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO scanlanka_rls;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO scanlanka_rls;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO scanlanka_rls;
