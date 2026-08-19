ALTER TABLE users
    ADD COLUMN IF NOT EXISTS guest_retention_hold BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE support_tickets
    ADD COLUMN IF NOT EXISTS contact_email_encrypted TEXT,
    ADD COLUMN IF NOT EXISTS contact_consent_at TIMESTAMPTZ;

ALTER TABLE support_tickets
    ADD CONSTRAINT ck_support_ticket_contact_consent
        CHECK (contact_email_encrypted IS NULL OR contact_consent_at IS NOT NULL);

CREATE INDEX IF NOT EXISTS ix_users_abandoned_guests
    ON users (updated_at)
    WHERE is_guest = TRUE
      AND status = 'active'
      AND guest_retention_hold = FALSE;

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    SELECT tc.constraint_name
      INTO constraint_name
      FROM information_schema.table_constraints tc
      JOIN information_schema.constraint_column_usage ccu
        ON ccu.constraint_name = tc.constraint_name
       AND ccu.constraint_schema = tc.constraint_schema
     WHERE tc.table_schema = current_schema()
       AND tc.table_name = 'receipt'
       AND tc.constraint_type = 'FOREIGN KEY'
       AND ccu.table_name = 'users'
     LIMIT 1;

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE receipt DROP CONSTRAINT %I', constraint_name);
    END IF;

    ALTER TABLE receipt
        ADD CONSTRAINT fk_receipt_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
END $$;

COMMENT ON COLUMN users.guest_retention_hold IS
    'Prevents automated abandoned-guest cleanup while a legal or support retention hold applies.';

COMMENT ON COLUMN support_tickets.contact_email_encrypted IS
    'Optional consented contact address encrypted by the application; never copied to the guest user profile.';
