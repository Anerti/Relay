CREATE SCHEMA IF NOT EXISTS relay_app;

DO $$ BEGIN
CREATE TYPE relay_app.user_role AS ENUM ('SUPERUSER', 'GUEST');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
CREATE TYPE relay_app.user_status AS ENUM ('ACTIVE', 'INACTIVE', 'LOCKED');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

CREATE TABLE IF NOT EXISTS relay_app."user" (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    username   VARCHAR(50) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100) NOT NULL,
    email      VARCHAR(100) NOT NULL,
    verified   BOOLEAN     NOT NULL DEFAULT false,
    role       relay_app.user_role NOT NULL DEFAULT 'GUEST',
    status     relay_app.user_status NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
    );


CREATE UNIQUE INDEX IF NOT EXISTS user_username_lower_key ON relay_app."user" (lower(username));
CREATE UNIQUE INDEX IF NOT EXISTS user_email_lower_key ON relay_app."user" (lower(email));
