CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    role_name   VARCHAR(50)  NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE users (
    id         BIGSERIAL    PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(100) NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id),
    role_id BIGINT NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

INSERT INTO roles (role_name, description) VALUES
    ('USER',  'Standard customer account'),
    ('ADMIN', 'Administrator account');
