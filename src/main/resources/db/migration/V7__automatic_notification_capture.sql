ALTER TABLE public.user_devices
    ADD COLUMN notification_capture_token_hash TEXT,
    ADD COLUMN notification_capture_enabled_at TIMESTAMPTZ,
    ADD COLUMN notification_capture_revoked_at TIMESTAMPTZ,
    ADD COLUMN notification_capture_last_used_at TIMESTAMPTZ;

CREATE UNIQUE INDEX uq_user_devices_notification_capture_token_hash
    ON public.user_devices (notification_capture_token_hash)
    WHERE notification_capture_token_hash IS NOT NULL;

ALTER TABLE public.receipt
    ADD COLUMN capture_id UUID,
    ADD COLUMN capture_mode VARCHAR(20),
    ADD COLUMN capture_device_id UUID,
    ADD COLUMN source_package VARCHAR(255),
    ADD COLUMN source_notification_posted_at TIMESTAMPTZ,
    ADD COLUMN source_payload_hash TEXT;

-- Existing notification rows came from the pasted-text endpoint.
UPDATE public.receipt
SET capture_mode = 'PASTED'
WHERE input_type = 'NOTIFICATION'
  AND capture_mode IS NULL;

ALTER TABLE public.receipt
    ADD CONSTRAINT fk_receipt_capture_device
        FOREIGN KEY (capture_device_id)
        REFERENCES public.user_devices(id)
        ON DELETE SET NULL,
    ADD CONSTRAINT ck_receipt_capture_mode
        CHECK (
            (input_type = 'NOTIFICATION' AND capture_mode IN ('AUTOMATIC', 'PASTED'))
            OR (input_type IS DISTINCT FROM 'NOTIFICATION' AND capture_mode IS NULL)
        );

CREATE UNIQUE INDEX uq_receipt_user_capture_active
    ON public.receipt (user_id, capture_id)
    WHERE capture_id IS NOT NULL
      AND deleted_at IS NULL;

CREATE INDEX ix_receipt_capture_device
    ON public.receipt (capture_device_id)
    WHERE capture_device_id IS NOT NULL;

COMMENT ON COLUMN public.user_devices.notification_capture_token_hash IS
    'SHA-256 hash of the one-time device-scoped notification capture credential. Plaintext is never stored.';
COMMENT ON COLUMN public.receipt.source_payload_hash IS
    'One-way hash used for notification ingestion deduplication. Raw notification text must never be persisted.';

