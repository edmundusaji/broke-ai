-- PostgreSQL uses the citext extension in production. This H2-only alias keeps
-- test DDL aligned with the production entity mapping.
CREATE DOMAIN IF NOT EXISTS CITEXT AS VARCHAR_IGNORECASE;
CREATE DOMAIN IF NOT EXISTS JSONB AS JSON;
