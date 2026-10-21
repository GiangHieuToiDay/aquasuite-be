-- ====================================================================
-- V1: Module IAM
-- Entity: com.aquasuite.ims.module.iam.internal.entity.User
-- Table: users
-- ====================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100),
    email VARCHAR(150) UNIQUE,
    password VARCHAR(255),
    password_last_changed_at TIMESTAMPTZ,
    avatar VARCHAR(255),
    created_at TIMESTAMPTZ,
    provider_id VARCHAR(255),
    status VARCHAR(50),
    verification_code VARCHAR(100),
    fcm_token TEXT,
    verification_expired_at TIMESTAMPTZ,
    is_verified BOOLEAN DEFAULT FALSE,
    email_sent_count INTEGER DEFAULT 0,
    email_sent_date DATE,
    phone VARCHAR(20),
    role_id BIGINT,
    permission_id BIGINT
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
