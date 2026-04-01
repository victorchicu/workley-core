CREATE
EXTENSION IF NOT EXISTS vector;

CREATE TABLE users
(
    id            UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    status        VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE embeddings
(
    id         BIGSERIAL PRIMARY KEY,
    model      VARCHAR(100) NOT NULL,
    actor      VARCHAR(100) NOT NULL,
    dimension  INTEGER      NOT NULL,
    embedding  vector(1536),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE chats
(
    id         BIGSERIAL PRIMARY KEY,
    chat_id    VARCHAR(100) NOT NULL UNIQUE,
    summary    JSONB,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE chat_participants
(
    id             BIGSERIAL PRIMARY KEY,
    chat_id        VARCHAR(100) NOT NULL REFERENCES chats (chat_id),
    participant_id VARCHAR(100) NOT NULL,
    UNIQUE (chat_id, participant_id)
);

CREATE TABLE chat_messages
(
    id         BIGSERIAL PRIMARY KEY,
    message_id VARCHAR(100) NOT NULL UNIQUE,
    chat_id    VARCHAR(100) NOT NULL,
    role       VARCHAR(50)  NOT NULL,
    owned_by   VARCHAR(100) NOT NULL,
    content    JSONB        NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE idempotency_keys
(
    id            VARCHAR(100) PRIMARY KEY,
    resource_id   VARCHAR(100),
    state         VARCHAR(50) NOT NULL,
    response_body JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens
(
    id         UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);

CREATE TABLE onboarding_steps
(
    id           BIGSERIAL PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    step_name    VARCHAR(50) NOT NULL,
    step_order   INTEGER     NOT NULL DEFAULT 0,
    completed    BOOLEAN     NOT NULL DEFAULT false,
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, step_name)
);

CREATE INDEX idx_onboarding_steps_user_id ON onboarding_steps (user_id);

CREATE TABLE otp_codes
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    email      VARCHAR(255) NOT NULL,
    code_hash  VARCHAR(255) NOT NULL,
    attempts   INTEGER      NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ  NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_otp_codes_user_id ON otp_codes (user_id);
CREATE INDEX idx_otp_codes_email_created ON otp_codes (email, created_at);

CREATE TABLE user_profiles
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    UUID         NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    full_name  VARCHAR(255) NOT NULL,
    age        INTEGER      NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE user_linked_providers
(
    id           BIGSERIAL PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider     VARCHAR(20)  NOT NULL,
    subject      VARCHAR(255) NOT NULL,
    email        VARCHAR(255),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (provider, subject)
);

CREATE INDEX idx_user_linked_providers_user_id ON user_linked_providers (user_id);

CREATE TABLE attachments
(
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    storage_key    VARCHAR(512) NOT NULL,
    filename       VARCHAR(255) NOT NULL,
    mime_type      VARCHAR(128) NOT NULL,
    file_size      BIGINT       NOT NULL,
    extracted_text TEXT,
    uploaded_by    UUID         NOT NULL,
    expires_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_attachments_orphan_cleanup ON attachments (expires_at) WHERE expires_at IS NOT NULL;

CREATE TABLE token_usage
(
    id                BIGSERIAL PRIMARY KEY,
    chat_id           VARCHAR(100) NOT NULL,
    message_id        VARCHAR(100) NOT NULL,
    model             VARCHAR(100) NOT NULL,
    prompt_tokens     INTEGER      NOT NULL DEFAULT 0,
    completion_tokens INTEGER      NOT NULL DEFAULT 0,
    total_tokens      INTEGER      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_token_usage_chat_id ON token_usage (chat_id);
CREATE INDEX idx_token_usage_message_id ON token_usage (message_id);

-- Seed local provider entries for existing email+password users
INSERT INTO user_linked_providers (user_id, provider, subject, email)
SELECT id, 'local', id::text, email FROM users;
