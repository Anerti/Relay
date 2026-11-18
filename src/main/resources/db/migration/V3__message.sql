DO $$ BEGIN
CREATE TYPE relay_app.message_issuer AS ENUM ('AGENT', 'HUMAN');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

CREATE TABLE IF NOT EXISTS relay_app.message (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id   UUID        REFERENCES relay_app."user" (id) ON DELETE SET NULL,
    receiver_id UUID        REFERENCES relay_app."user" (id) ON DELETE SET NULL,
    reply_to    UUID,
    issuer      relay_app.message_issuer NOT NULL DEFAULT 'HUMAN',
    content     TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    updated_at  TIMESTAMPTZ
    );
