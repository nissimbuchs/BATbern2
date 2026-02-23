CREATE TABLE IF NOT EXISTS partner_notes (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    partner_id      UUID         NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    title           VARCHAR(500) NOT NULL,
    content         TEXT         NOT NULL,
    author_username VARCHAR(100) NOT NULL,  -- ADR-003: organizer username (not UUID)
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_partner_notes_partner_id ON partner_notes(partner_id);
CREATE INDEX IF NOT EXISTS idx_partner_notes_created_at ON partner_notes(created_at DESC);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_partner_notes_updated_at') THEN
        CREATE TRIGGER update_partner_notes_updated_at
            BEFORE UPDATE ON partner_notes
            FOR EACH ROW
            EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;
