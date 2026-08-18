-- These tables are accessed through the Spring/JDBC backend, not directly through
-- Supabase's anon or authenticated Data API roles. RLS provides default-deny
-- protection if a grant is added later; explicit revokes remove the current API
-- exposure.

ALTER TABLE public.user_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notification_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.privacy_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.privacy_consent_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.email_change_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.support_tickets ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.data_export_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_deletion_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.idempotency_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_sync_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.security_audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.faq_articles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.avatar_uploads ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transaction_clear_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.outbound_email_jobs ENABLE ROW LEVEL SECURITY;

-- Supabase defines these Data API roles, while a plain local PostgreSQL
-- installation usually does not. Apply the revokes only where the roles exist.
DO $$
DECLARE
    api_role TEXT;
BEGIN
    FOREACH api_role IN ARRAY ARRAY['anon', 'authenticated']
    LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = api_role) THEN
            EXECUTE format(
                'REVOKE ALL PRIVILEGES ON TABLE
                    public.user_preferences,
                    public.notification_preferences,
                    public.user_devices,
                    public.privacy_preferences,
                    public.privacy_consent_history,
                    public.user_sessions,
                    public.email_change_requests,
                    public.support_tickets,
                    public.data_export_jobs,
                    public.account_deletion_requests,
                    public.idempotency_records,
                    public.user_sync_state,
                    public.security_audit_log,
                    public.faq_articles,
                    public.avatar_uploads,
                    public.transaction_clear_requests,
                    public.outbound_email_jobs
                 FROM %I',
                api_role
            );

            -- Keep future backend-managed public tables private by default.
            EXECUTE format(
                'ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
                 REVOKE ALL PRIVILEGES ON TABLES FROM %I',
                api_role
            );
        END IF;
    END LOOP;
END
$$;
