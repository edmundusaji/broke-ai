-- Hibernate maps Java String fields to VARCHAR. PostgreSQL reports fixed-width
-- CHAR columns as bpchar, which makes ddl-auto=validate reject the schema.
ALTER TABLE user_preferences
    ALTER COLUMN currency_code TYPE VARCHAR(3) USING BTRIM(currency_code),
    ALTER COLUMN region_code TYPE VARCHAR(2) USING BTRIM(region_code);
