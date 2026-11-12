DO $$ BEGIN
CREATE TYPE relay_app.host_status AS ENUM ('AUTHORIZED', 'BANNED');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
CREATE TYPE relay_app.event_log_severity AS ENUM ('INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

CREATE TABLE IF NOT EXISTS relay_app.host (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        REFERENCES relay_app."user" (id) ON DELETE SET NULL,
    ip_address   INET        NOT NULL,
    status       relay_app.host_status NOT NULL DEFAULT 'AUTHORIZED',
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ,
    UNIQUE (user_id, ip_address)
    );

CREATE TABLE IF NOT EXISTS relay_app.event_log (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    host_id     UUID         REFERENCES relay_app.host (id) ON DELETE SET NULL,
    user_agent  VARCHAR(512) NOT NULL,
    severity    relay_app.event_log_severity NOT NULL,
    description VARCHAR(100),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
    );
