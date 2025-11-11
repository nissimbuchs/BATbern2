-- V2__create_partner_coordination_schema.sql
-- Partner Coordination Service Database Schema
-- Source: docs/architecture/03-data-architecture.md (lines 1676-1754)
-- ADR-003: Uses meaningful IDs (companyName, username) instead of UUIDs for cross-service references

-- Partners table (ADR-003: stores companyName, NOT company_id UUID)
CREATE TABLE IF NOT EXISTS partners (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    company_name VARCHAR(12) NOT NULL UNIQUE, -- ADR-003: Meaningful ID, NO UUID FK
    partnership_level VARCHAR(50) NOT NULL CHECK (partnership_level IN (
        'BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'STRATEGIC'
    )),
    partnership_start_date DATE NOT NULL,
    partnership_end_date DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create updated_at trigger for partners
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_partners_updated_at') THEN
        CREATE TRIGGER update_partners_updated_at
            BEFORE UPDATE ON partners
            FOR EACH ROW
            EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;

-- Index on company_name for efficient lookups (primary access pattern)
CREATE INDEX IF NOT EXISTS idx_partners_company_name ON partners(company_name);

-- Index on partnership_level for filtering
CREATE INDEX IF NOT EXISTS idx_partners_partnership_level ON partners(partnership_level);

-- Note: is_active is calculated via @Transient method in Partner entity (not stored in DB)

-- Partner contacts (ADR-003: stores username, NOT user_id UUID)
CREATE TABLE IF NOT EXISTS partner_contacts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    partner_id UUID NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    username VARCHAR(100) NOT NULL, -- ADR-003: Meaningful ID, NO UUID FK to users
    contact_role VARCHAR(50) NOT NULL CHECK (contact_role IN (
        'PRIMARY', 'BILLING', 'TECHNICAL', 'MARKETING'
    )),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(partner_id, username)
);

-- Create updated_at trigger for partner_contacts
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_partner_contacts_updated_at') THEN
        CREATE TRIGGER update_partner_contacts_updated_at
            BEFORE UPDATE ON partner_contacts
            FOR EACH ROW
            EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;

-- Index on partner_id for efficient lookups
CREATE INDEX IF NOT EXISTS idx_partner_contacts_partner_id ON partner_contacts(partner_id);

-- Index on username for cross-service lookups
CREATE INDEX IF NOT EXISTS idx_partner_contacts_username ON partner_contacts(username);

-- Topic voting
CREATE TABLE IF NOT EXISTS topic_votes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    topic_id UUID NOT NULL, -- References topic in Event Management Service
    partner_id UUID NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    vote_weight INTEGER DEFAULT 1, -- Based on partnership_level
    vote_value INTEGER NOT NULL CHECK (vote_value BETWEEN 1 AND 5),
    voted_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(topic_id, partner_id)
);

-- Index on topic_id for vote aggregation
CREATE INDEX IF NOT EXISTS idx_topic_votes_topic_id ON topic_votes(topic_id);

-- Index on partner_id for partner vote history
CREATE INDEX IF NOT EXISTS idx_topic_votes_partner_id ON topic_votes(partner_id);

-- Partner topic suggestions
CREATE TABLE IF NOT EXISTS topic_suggestions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    partner_id UUID NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    suggested_topic VARCHAR(500) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    business_justification VARCHAR(1000),
    suggested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL CHECK (status IN (
        'SUBMITTED', 'UNDER_REVIEW', 'ACCEPTED', 'REJECTED', 'IMPLEMENTED'
    )) DEFAULT 'SUBMITTED',
    reviewed_at TIMESTAMP WITH TIME ZONE,
    reviewed_by UUID, -- References organizer
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create updated_at trigger for topic_suggestions
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_topic_suggestions_updated_at') THEN
        CREATE TRIGGER update_topic_suggestions_updated_at
            BEFORE UPDATE ON topic_suggestions
            FOR EACH ROW
            EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;

-- Index on partner_id for partner suggestions
CREATE INDEX IF NOT EXISTS idx_topic_suggestions_partner_id ON topic_suggestions(partner_id);

-- Index on status for filtering
CREATE INDEX IF NOT EXISTS idx_topic_suggestions_status ON topic_suggestions(status);

-- Partner meetings
CREATE TABLE IF NOT EXISTS partner_meetings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    meeting_type VARCHAR(50) NOT NULL CHECK (meeting_type IN ('spring', 'autumn', 'ad_hoc')),
    scheduled_date DATE NOT NULL,
    location VARCHAR(255),
    agenda TEXT,
    materials_generated BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create updated_at trigger for partner_meetings
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_partner_meetings_updated_at') THEN
        CREATE TRIGGER update_partner_meetings_updated_at
            BEFORE UPDATE ON partner_meetings
            FOR EACH ROW
            EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;

-- Index on scheduled_date for meeting lookups
CREATE INDEX IF NOT EXISTS idx_partner_meetings_scheduled_date ON partner_meetings(scheduled_date);

-- Index on meeting_type for filtering
CREATE INDEX IF NOT EXISTS idx_partner_meetings_meeting_type ON partner_meetings(meeting_type);

-- Partner meeting attendance
CREATE TABLE IF NOT EXISTS partner_meeting_attendance (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    meeting_id UUID NOT NULL REFERENCES partner_meetings(id) ON DELETE CASCADE,
    partner_id UUID NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    rsvp_status VARCHAR(50) NOT NULL CHECK (rsvp_status IN (
        'invited', 'accepted', 'declined', 'tentative', 'attended'
    )) DEFAULT 'invited',
    rsvp_at TIMESTAMP WITH TIME ZONE,
    attended BOOLEAN DEFAULT FALSE,
    UNIQUE(meeting_id, partner_id)
);

-- Index on meeting_id for attendance lookups
CREATE INDEX IF NOT EXISTS idx_partner_meeting_attendance_meeting_id ON partner_meeting_attendance(meeting_id);

-- Index on partner_id for partner attendance history
CREATE INDEX IF NOT EXISTS idx_partner_meeting_attendance_partner_id ON partner_meeting_attendance(partner_id);

-- Schema creation complete
-- Note: NO foreign key constraints to companies/users tables (microservices boundary per ADR-003)
