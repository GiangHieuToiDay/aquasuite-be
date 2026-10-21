-- Preserve the mapped recovery table; develop has no legacy numeric accounts to backfill.
CREATE TABLE public.user_password_recovery (
    user_id UUID PRIMARY KEY REFERENCES public.users(id) ON DELETE CASCADE,
    verification_code VARCHAR(255),
    verification_expired_at TIMESTAMP,
    email_sent_date DATE,
    email_sent_count INTEGER,
    password_last_changed_at TIMESTAMP
);
