-- Offline-first transaction synchronization. The Spring backend is the only
-- writer; these tables remain unavailable through Supabase's Data API.

-- This release deliberately uses explicit reauthentication after JWT expiry.
-- The stored value is an access-token hash used for revocation/session display,
-- not a refresh token.
ALTER TABLE public.user_sessions
    RENAME COLUMN refresh_token_hash TO access_token_hash;

ALTER TABLE public.users
    ADD COLUMN account_id UUID DEFAULT gen_random_uuid(),
    ADD COLUMN client_guest_id_hash TEXT,
    ADD COLUMN guest_installation_credential_hash TEXT;

UPDATE public.users
SET account_id = gen_random_uuid()
WHERE account_id IS NULL;

ALTER TABLE public.users
    ALTER COLUMN account_id SET NOT NULL,
    ADD CONSTRAINT uq_users_account_id UNIQUE (account_id),
    ADD CONSTRAINT ck_users_guest_bootstrap_pair CHECK (
        (client_guest_id_hash IS NULL AND guest_installation_credential_hash IS NULL)
        OR
        (client_guest_id_hash IS NOT NULL AND guest_installation_credential_hash IS NOT NULL)
    );

CREATE UNIQUE INDEX uq_users_client_guest_id_hash
    ON public.users (client_guest_id_hash)
    WHERE client_guest_id_hash IS NOT NULL;

COMMENT ON COLUMN public.users.account_id IS
    'Immutable public account identifier used for local account scoping.';
COMMENT ON COLUMN public.users.client_guest_id_hash IS
    'SHA-256 hash of the installation-provided guest UUID; never an authentication secret.';
COMMENT ON COLUMN public.users.guest_installation_credential_hash IS
    'SHA-256 hash of the installation credential required to resume an idempotently bootstrapped guest.';
COMMENT ON COLUMN public.user_sessions.access_token_hash IS
    'SHA-256 hash of the expiring JWT used for session revocation; no refresh-token contract is enabled.';

ALTER TABLE public.receipt
    ADD COLUMN client_transaction_id UUID DEFAULT gen_random_uuid();

UPDATE public.receipt
SET client_transaction_id = gen_random_uuid()
WHERE client_transaction_id IS NULL;

ALTER TABLE public.receipt
    ALTER COLUMN client_transaction_id SET NOT NULL,
    ADD CONSTRAINT uq_receipt_user_client_transaction
        UNIQUE (user_id, client_transaction_id);

COMMENT ON COLUMN public.receipt.client_transaction_id IS
    'Immutable client-generated transaction identity used for idempotent offline synchronization.';

CREATE TABLE public.user_change_log (
    sequence BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    entity_type VARCHAR(30) NOT NULL,
    client_entity_id UUID NOT NULL,
    change_type VARCHAR(20) NOT NULL,
    entity_revision BIGINT NOT NULL,
    snapshot JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_user_change_log_entity_type
        CHECK (entity_type = 'TRANSACTION'),
    CONSTRAINT ck_user_change_log_change_type
        CHECK (change_type IN ('UPSERT', 'DELETE')),
    CONSTRAINT ck_user_change_log_revision
        CHECK (entity_revision >= 1)
);

CREATE INDEX ix_user_change_log_user_sequence
    ON public.user_change_log (user_id, sequence);

COMMENT ON TABLE public.user_change_log IS
    'Immutable ordered sync feed. Retained indefinitely until a documented offline retention window and cursor-floor cleanup job are introduced.';

CREATE TABLE public.sync_mutation_receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    operation_id UUID NOT NULL,
    mutation_type VARCHAR(30) NOT NULL,
    request_hash TEXT NOT NULL,
    response_body JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL DEFAULT (now() + INTERVAL '180 days'),
    CONSTRAINT uq_sync_mutation_receipts_operation UNIQUE (user_id, operation_id),
    CONSTRAINT ck_sync_mutation_receipts_type
        CHECK (mutation_type IN ('TRANSACTION_CREATE', 'TRANSACTION_UPDATE', 'TRANSACTION_DELETE'))
);

CREATE INDEX ix_sync_mutation_receipts_expiry
    ON public.sync_mutation_receipts (expires_at);

COMMENT ON TABLE public.sync_mutation_receipts IS
    'Terminal per-operation responses for safe retries after ambiguous network failures.';

CREATE OR REPLACE FUNCTION public.log_receipt_sync_change()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
DECLARE
    changed_receipt public.receipt%ROWTYPE;
    next_sequence BIGINT;
    sync_snapshot JSONB;
    sync_change_type VARCHAR(20);
BEGIN
    IF TG_OP = 'DELETE' THEN
        changed_receipt := OLD;
        sync_change_type := 'DELETE';
    ELSE
        changed_receipt := NEW;
        sync_change_type := CASE
            WHEN NEW.deleted_at IS NULL THEN 'UPSERT'
            ELSE 'DELETE'
        END;
    END IF;

    sync_snapshot := jsonb_build_object(
        'id', changed_receipt.id,
        'clientTransactionId', changed_receipt.client_transaction_id,
        'date', changed_receipt.transaction_date,
        'amount', changed_receipt.amount,
        'category', changed_receipt.category,
        'paymentMethod', changed_receipt.payment_method,
        'description', changed_receipt.description,
        'inputType', changed_receipt.input_type,
        'validationStatus', changed_receipt.validation_status,
        'captureId', changed_receipt.capture_id,
        'captureMode', changed_receipt.capture_mode,
        'sourcePackage', changed_receipt.source_package,
        'sourceNotificationPostedAt', changed_receipt.source_notification_posted_at,
        'revision', changed_receipt.revision,
        'createdAt', changed_receipt.created_at,
        'updatedAt', changed_receipt.updated_at,
        'deletedAt', CASE
            WHEN TG_OP = 'DELETE' THEN COALESCE(changed_receipt.deleted_at, now())
            ELSE changed_receipt.deleted_at
        END
    );

    INSERT INTO public.user_change_log (
        user_id,
        entity_type,
        client_entity_id,
        change_type,
        entity_revision,
        snapshot
    ) VALUES (
        changed_receipt.user_id,
        'TRANSACTION',
        changed_receipt.client_transaction_id,
        sync_change_type,
        changed_receipt.revision,
        sync_snapshot
    ) RETURNING sequence INTO next_sequence;

    INSERT INTO public.user_sync_state (
        user_id,
        status,
        last_synced_at,
        server_revision,
        created_at,
        updated_at,
        revision
    ) VALUES (
        changed_receipt.user_id,
        'synced',
        now(),
        next_sequence,
        now(),
        now(),
        1
    )
    ON CONFLICT (user_id) DO UPDATE
       SET status = 'synced',
           last_synced_at = EXCLUDED.last_synced_at,
           server_revision = EXCLUDED.server_revision,
           updated_at = EXCLUDED.updated_at,
           revision = public.user_sync_state.revision + 1;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

-- Seed the feed before installing the trigger so every pre-existing active row
-- and tombstone is available to upgraded clients exactly once.
INSERT INTO public.user_change_log (
    user_id,
    entity_type,
    client_entity_id,
    change_type,
    entity_revision,
    snapshot,
    created_at
)
SELECT
    receipt.user_id,
    'TRANSACTION',
    receipt.client_transaction_id,
    CASE WHEN receipt.deleted_at IS NULL THEN 'UPSERT' ELSE 'DELETE' END,
    receipt.revision,
    jsonb_build_object(
        'id', receipt.id,
        'clientTransactionId', receipt.client_transaction_id,
        'date', receipt.transaction_date,
        'amount', receipt.amount,
        'category', receipt.category,
        'paymentMethod', receipt.payment_method,
        'description', receipt.description,
        'inputType', receipt.input_type,
        'validationStatus', receipt.validation_status,
        'captureId', receipt.capture_id,
        'captureMode', receipt.capture_mode,
        'sourcePackage', receipt.source_package,
        'sourceNotificationPostedAt', receipt.source_notification_posted_at,
        'revision', receipt.revision,
        'createdAt', receipt.created_at,
        'updatedAt', receipt.updated_at,
        'deletedAt', receipt.deleted_at
    ),
    receipt.updated_at
FROM public.receipt;

UPDATE public.user_sync_state AS sync_state
SET server_revision = COALESCE((
        SELECT MAX(change_log.sequence)
        FROM public.user_change_log AS change_log
        WHERE change_log.user_id = sync_state.user_id
    ), 0),
    updated_at = now(),
    revision = sync_state.revision + 1
;

DROP TRIGGER IF EXISTS trg_receipt_sync_change ON public.receipt;
CREATE TRIGGER trg_receipt_sync_change
AFTER INSERT OR UPDATE OR DELETE ON public.receipt
FOR EACH ROW EXECUTE FUNCTION public.log_receipt_sync_change();

ALTER TABLE public.user_change_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.sync_mutation_receipts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.receipt ENABLE ROW LEVEL SECURITY;

ALTER FUNCTION public.set_updated_at() SET search_path = public, pg_temp;
REVOKE EXECUTE ON FUNCTION public.set_updated_at() FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.log_receipt_sync_change() FROM PUBLIC;

DO $$
DECLARE
    api_role TEXT;
BEGIN
    FOREACH api_role IN ARRAY ARRAY['anon', 'authenticated']
    LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = api_role) THEN
            EXECUTE format(
                'REVOKE ALL PRIVILEGES ON TABLE
                    public.users,
                    public.receipt,
                    public.user_change_log,
                    public.sync_mutation_receipts
                 FROM %I',
                api_role
            );
            EXECUTE format(
                'REVOKE USAGE, SELECT ON SEQUENCE public.user_change_log_sequence_seq FROM %I',
                api_role
            );
            EXECUTE format(
                'REVOKE EXECUTE ON FUNCTION public.log_receipt_sync_change() FROM %I',
                api_role
            );
        END IF;
    END LOOP;
END
$$;
