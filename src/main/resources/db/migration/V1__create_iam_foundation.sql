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
