-- V2__Create_events_schema.sql
-- Event Management domain schema

CREATE TABLE IF NOT EXISTS events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_code VARCHAR(50) UNIQUE NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    event_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    start_date TIMESTAMP WITH TIME ZONE NOT NULL,
    end_date TIMESTAMP WITH TIME ZONE NOT NULL,
    location_name VARCHAR(255),
    location_address TEXT,
    location_city VARCHAR(100),
    location_country VARCHAR(2),
    max_attendees INTEGER,
    organizer_company_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    version INTEGER DEFAULT 0 NOT NULL
);

CREATE INDEX idx_events_event_code ON events(event_code);
CREATE INDEX idx_events_status ON events(status);
CREATE INDEX idx_events_organizer_company_id ON events(organizer_company_id);
CREATE INDEX idx_events_start_date ON events(start_date);

-- Trigger for automatic updated_at
CREATE TRIGGER update_events_updated_at BEFORE UPDATE ON events
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE events IS 'Core events managed by the platform';
COMMENT ON COLUMN events.event_code IS 'Unique human-readable event identifier';
COMMENT ON COLUMN events.status IS 'Event lifecycle status: DRAFT, PUBLISHED, ONGOING, COMPLETED, CANCELLED';
COMMENT ON COLUMN events.version IS 'Optimistic locking version';
