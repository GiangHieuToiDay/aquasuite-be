-- Keep password recovery separate from the incoming authentication User model.
CREATE TABLE public.user_password_recovery (
    user_id UUID PRIMARY KEY REFERENCES public.users(id) ON DELETE CASCADE,
    verification_code VARCHAR(255),
    verification_expired_at TIMESTAMP,
    email_sent_date DATE,
    email_sent_count INTEGER,
    password_last_changed_at TIMESTAMP
);
INSERT INTO public.user_password_recovery(user_id,verification_code,verification_expired_at,
    email_sent_date,email_sent_count,password_last_changed_at)
SELECT m.user_id,u.verification_code,u.verification_expired_at,u.email_sent_date,u.email_sent_count,u.password_last_changed_at
FROM public.iam_legacy_users u JOIN public.iam_legacy_user_ids m ON m.legacy_id=u.id;
