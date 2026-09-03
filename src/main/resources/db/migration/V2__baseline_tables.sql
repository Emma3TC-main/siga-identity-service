-- TODO: Pegar aquí los CREATE TABLE correspondientes al esquema 'iam'.
-- REGLA: 0 FK hacia otros esquemas. Relaciones inter-dominio solo por ID.
-- ============================================================
-- IAM / identity-service
-- ============================================================
CREATE TABLE iam.user_account (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    username varchar(100) NOT NULL UNIQUE,
    email varchar(254) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    mfa_enabled boolean NOT NULL DEFAULT false,
    mfa_secret_encrypted text,
    failed_login_attempts integer NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0),
    locked_until timestamptz,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (NOT mfa_enabled OR mfa_secret_encrypted IS NOT NULL)
);

CREATE TABLE iam.role (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(80) NOT NULL UNIQUE,
    name varchar(120) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE iam.permission (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(120) NOT NULL UNIQUE,
    description varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE iam.user_role (
    user_id uuid NOT NULL REFERENCES iam.user_account(id),
    role_id uuid NOT NULL REFERENCES iam.role(id),
    assigned_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE iam.role_permission (
    role_id uuid NOT NULL REFERENCES iam.role(id),
    permission_id uuid NOT NULL REFERENCES iam.permission(id),
    assigned_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE iam.refresh_token (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES iam.user_account(id),
    token_hash varchar(255) NOT NULL UNIQUE,
    jti uuid NOT NULL UNIQUE,
    family_id uuid NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    replaced_by_jti uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (expires_at > created_at)
);
CREATE INDEX idx_refresh_token_user_active ON iam.refresh_token(user_id, expires_at) WHERE revoked_at IS NULL;
CREATE INDEX idx_refresh_token_family ON iam.refresh_token(family_id);

CREATE TABLE iam.outbox_event (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type varchar(80) NOT NULL,
    aggregate_id uuid NOT NULL,
    event_type varchar(120) NOT NULL,
    schema_version integer NOT NULL DEFAULT 1 CHECK (schema_version > 0),
    correlation_id uuid,
    causation_id uuid,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PUBLISHED','FAILED')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    published_at timestamptz
);
CREATE INDEX idx_iam_outbox_pending ON iam.outbox_event(status, next_attempt_at, occurred_at) WHERE status='PENDING';
