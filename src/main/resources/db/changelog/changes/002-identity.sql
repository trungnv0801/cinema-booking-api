--liquibase formatted sql

--changeset trung:002-01-users
--comment Customers and staff share one table: staff also buy tickets for themselves,
--comment and splitting them would force every "person" foreign key to branch.
CREATE TABLE users (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    email             VARCHAR(255),
    phone             VARCHAR(20),
    password_hash     VARCHAR(255),
    full_name         VARCHAR(255) NOT NULL,
    date_of_birth     DATE,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    email_verified_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_users_status  CHECK (status IN ('ACTIVE', 'LOCKED', 'DELETED')),
    CONSTRAINT ck_users_contact CHECK (email IS NOT NULL OR phone IS NOT NULL)
);

CREATE UNIQUE INDEX uq_users_public_id ON users (public_id);
CREATE UNIQUE INDEX uq_users_email     ON users (lower(email)) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX uq_users_phone     ON users (phone)        WHERE phone IS NOT NULL;

COMMENT ON TABLE  users IS 'Customers and staff. Role assignment lives in user_roles.';
COMMENT ON COLUMN users.public_id IS
    'Identifier exposed through the API. Sequential ids are never exposed, so the '
    'total record count cannot be inferred and resources cannot be enumerated.';
COMMENT ON COLUMN users.date_of_birth IS
    'Used to surface age warnings for T16/T18 films. Real verification happens at the door.';
COMMENT ON CONSTRAINT ck_users_contact ON users IS
    'Online customers must have an email; walk-in customers may only have a phone number.';
--rollback DROP TABLE users;

--changeset trung:002-02-roles
CREATE TABLE roles (
    code        VARCHAR(30)  PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT
);

COMMENT ON TABLE roles IS 'Reference data. Populated by the reference-data changelog.';
--rollback DROP TABLE roles;

--changeset trung:002-03-user-roles
--comment cinema_id NULL means the role applies system-wide (ADMIN). For CASHIER,
--comment USHER and CINEMA_MANAGER it must be set: a Cau Giay cashier may not sell
--comment tickets for Ha Dong.
CREATE TABLE user_roles (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_code  VARCHAR(30) NOT NULL REFERENCES roles (code),
    cinema_id  BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_user_roles_scoped ON user_roles (user_id, role_code, cinema_id)
    WHERE cinema_id IS NOT NULL;
CREATE UNIQUE INDEX uq_user_roles_global ON user_roles (user_id, role_code)
    WHERE cinema_id IS NULL;
CREATE INDEX ix_user_roles_user ON user_roles (user_id);

COMMENT ON COLUMN user_roles.cinema_id IS
    'Scope of the role. NULL means system-wide. Enforced on every staff endpoint.';
--rollback DROP TABLE user_roles;

--changeset trung:002-04-refresh-tokens
CREATE TABLE refresh_tokens (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    issued_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked_at TIMESTAMPTZ,
    user_agent VARCHAR(255),
    ip_address INET
);

CREATE UNIQUE INDEX uq_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX ix_refresh_tokens_user_active ON refresh_tokens (user_id) WHERE revoked_at IS NULL;

COMMENT ON COLUMN refresh_tokens.token_hash IS
    'Only the hash is stored. A database leak must not allow session hijacking.';
--rollback DROP TABLE refresh_tokens;
