-- V2__Create_events_schema.sql
-- Event Management domain schema
-- Based on architecture document: docs/architecture/03-data-architecture.md, Section 7.1
-- SOURCE OF TRUTH: Architecture document defines canonical schema

-- Events table (matches architecture specification exactly)
CREATE TABLE IF NOT EXISTS events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_number INTEGER UNIQUE NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    event_date TIMESTAMP WITH TIME ZONE NOT NULL,
    registration_deadline TIMESTAMP WITH TIME ZONE NOT NULL,
    venue_name VARCHAR(255) NOT NULL,
    venue_address TEXT NOT NULL,
    venue_capacity INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN (
        'planning', 'topic_defined', 'speakers_invited', 'agenda_draft',
        'published', 'registration_open', 'registration_closed',
        'in_progress', 'completed', 'archived'
    )),
    organizer_id UUID NOT NULL,
    current_attendee_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    published_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB DEFAULT '{}'
);

-- Add missing columns if table already exists (idempotent)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='events' AND column_name='created_by') THEN
        ALTER TABLE events ADD COLUMN created_by VARCHAR(255);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='events' AND column_name='updated_by') THEN
        ALTER TABLE events ADD COLUMN updated_by VARCHAR(255);
    END IF;
END $$;

-- Sessions table (matches architecture specification exactly)
CREATE TABLE IF NOT EXISTS sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    session_type VARCHAR(50) NOT NULL CHECK (session_type IN (
        'keynote', 'presentation', 'workshop', 'panel_discussion',
        'networking', 'break', 'lunch'
    )),
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    room VARCHAR(100),
    capacity INTEGER,
    language VARCHAR(10) DEFAULT 'de',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Registrations table (matches architecture specification)
CREATE TABLE IF NOT EXISTS registrations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    attendee_id UUID NOT NULL,
    attendee_name VARCHAR(255) NOT NULL,
    attendee_email VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN (
        'registered', 'waitlisted', 'confirmed', 'cancelled', 'attended'
    )),
    registration_date TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(event_id, attendee_email)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_events_status ON events(status);
CREATE INDEX IF NOT EXISTS idx_events_event_date ON events(event_date);
CREATE INDEX IF NOT EXISTS idx_events_event_number ON events(event_number);
CREATE INDEX IF NOT EXISTS idx_events_organizer_id ON events(organizer_id);

CREATE INDEX IF NOT EXISTS idx_sessions_event_id ON sessions(event_id);
CREATE INDEX IF NOT EXISTS idx_sessions_start_time ON sessions(start_time);
CREATE INDEX IF NOT EXISTS idx_sessions_session_type ON sessions(session_type);

CREATE INDEX IF NOT EXISTS idx_registrations_event_id ON registrations(event_id);
CREATE INDEX IF NOT EXISTS idx_registrations_attendee_email ON registrations(attendee_email);
CREATE INDEX IF NOT EXISTS idx_registrations_status ON registrations(status);

-- Trigger for automatic updated_at on events
DROP TRIGGER IF EXISTS update_events_updated_at ON events;
CREATE TRIGGER update_events_updated_at BEFORE UPDATE ON events
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Trigger for automatic updated_at on sessions
DROP TRIGGER IF EXISTS update_sessions_updated_at ON sessions;
CREATE TRIGGER update_sessions_updated_at BEFORE UPDATE ON sessions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Trigger for automatic updated_at on registrations
DROP TRIGGER IF EXISTS update_registrations_updated_at ON registrations;
CREATE TRIGGER update_registrations_updated_at BEFORE UPDATE ON registrations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Comments documenting architecture alignment
COMMENT ON TABLE events IS 'Core events managed by the platform - matches architecture 03-data-architecture.md Section 7.1';
COMMENT ON TABLE sessions IS 'Event sessions (keynotes, workshops, presentations) - matches architecture 03-data-architecture.md Section 7.1';
COMMENT ON TABLE registrations IS 'Event registrations - matches architecture 03-data-architecture.md Section 7.1';
COMMENT ON COLUMN events.event_number IS 'Sequential event number (e.g., 142 for BATbern #142)';
COMMENT ON COLUMN events.status IS 'Event lifecycle status tracking workflow progression';
COMMENT ON COLUMN sessions.session_type IS 'Type of session: keynote, presentation, workshop, panel_discussion, networking, break, lunch';
