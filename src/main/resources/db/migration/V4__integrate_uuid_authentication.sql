-- Integrate authentication without rewriting the existing V1/V2/V3 history.
-- Retain the complete original account rows and a stable identity mapping.
ALTER TABLE public.users RENAME TO iam_legacy_users;
CREATE TABLE public.iam_legacy_user_ids (
    legacy_id BIGINT PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE
);
INSERT INTO public.iam_legacy_user_ids(legacy_id,user_id)
SELECT legacy_id, md5('aquasuite:legacy-user:' || legacy_id::text)::uuid
FROM (
    SELECT id AS legacy_id FROM public.iam_legacy_users
    UNION SELECT actor_id FROM public.audit_entries
    UNION SELECT counted_by FROM public.stock_counts WHERE counted_by IS NOT NULL
    UNION SELECT approved_by FROM public.stock_counts WHERE approved_by IS NOT NULL
    UNION SELECT created_by FROM public.stock_movements WHERE created_by IS NOT NULL
) identifiers;

-- V1__create_iam_foundation.sql
-- Baseline IAM Foundation Schema for AquaSuite IMS (Spec v0.4.0)

-- 1. Table: users
CREATE TABLE public.users (
    id UUID NOT NULL,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(2048) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_ACTIVATION',
    phone VARCHAR(32) NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT chk_users_username_not_empty CHECK (length(username) > 0),
    CONSTRAINT chk_users_username_trimmed CHECK (username = btrim(username)),
    CONSTRAINT chk_users_email_not_empty CHECK (length(email) > 0),
    CONSTRAINT chk_users_email_trimmed CHECK (email = btrim(email)),
    CONSTRAINT chk_users_status CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'INACTIVE', 'LOCKED'))
);

CREATE UNIQUE INDEX uq_users_username_lower ON public.users (lower(username));
CREATE UNIQUE INDEX uq_users_email_lower ON public.users (lower(email));

-- 2. Table: roles
CREATE TABLE public.roles (
    role_id UUID NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_roles PRIMARY KEY (role_id),
    CONSTRAINT chk_roles_role_name_not_empty CHECK (length(role_name) > 0)
);

CREATE UNIQUE INDEX uq_roles_role_name_lower ON public.roles (lower(role_name));

-- 3. Table: permissions
CREATE TABLE public.permissions (
    id UUID NOT NULL,
    code VARCHAR(150) NOT NULL,
    description VARCHAR(500) NULL,
    CONSTRAINT pk_permissions PRIMARY KEY (id),
    CONSTRAINT chk_permissions_code_not_empty CHECK (length(code) > 0)
);

CREATE UNIQUE INDEX uq_permissions_code_lower ON public.permissions (lower(code));

-- 4. Table: user_roles (Spec v0.4.0: only user_id and role_id)
CREATE TABLE public.user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES public.users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES public.roles (role_id) ON DELETE RESTRICT
);

CREATE INDEX idx_user_roles_role_id ON public.user_roles (role_id);

-- 5. Table: role_permissions
CREATE TABLE public.role_permissions (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES public.roles (role_id) ON DELETE RESTRICT,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES public.permissions (id) ON DELETE RESTRICT
);

CREATE INDEX idx_role_permissions_permission_id ON public.role_permissions (permission_id);

-- Incomplete legacy identities cannot authenticate. Original values remain in the archive.
-- Conflicting case-insensitive names/emails fail atomically rather than dropping accounts.
INSERT INTO public.users(id,username,email,password_hash,avatar_url,created_at,status,phone)
SELECT m.user_id,
       COALESCE(NULLIF(btrim(u.username),''), 'legacy-' || u.id::text),
       COALESCE(NULLIF(btrim(u.email),''), 'legacy-' || u.id::text || '@users.invalid'),
       COALESCE(NULLIF(u.password,''), '{disabled-legacy-account}'),
       u.avatar, COALESCE(u.created_at AT TIME ZONE 'UTC', CURRENT_TIMESTAMP),
       CASE WHEN NULLIF(btrim(u.email),'') IS NULL OR NULLIF(u.password,'') IS NULL THEN 'LOCKED'
            WHEN u.status IN ('ACTIVE','INACTIVE') THEN u.status
            WHEN u.status='PENDING' THEN 'PENDING_ACTIVATION' ELSE 'LOCKED' END,
       u.phone
FROM public.iam_legacy_users u JOIN public.iam_legacy_user_ids m ON m.legacy_id=u.id;

ALTER TABLE public.audit_entries ALTER COLUMN actor_id TYPE UUID
    USING md5('aquasuite:legacy-user:' || actor_id::text)::uuid;
ALTER TABLE public.stock_counts ALTER COLUMN counted_by TYPE UUID
    USING md5('aquasuite:legacy-user:' || counted_by::text)::uuid;
ALTER TABLE public.stock_counts ALTER COLUMN approved_by TYPE UUID
    USING md5('aquasuite:legacy-user:' || approved_by::text)::uuid;
ALTER TABLE public.stock_movements ALTER COLUMN created_by TYPE UUID
    USING md5('aquasuite:legacy-user:' || created_by::text)::uuid;
