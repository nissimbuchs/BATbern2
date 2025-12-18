-- V14__add_topic_speaker_pool_tables.sql
-- Story 5.2: Topic Selection & Speaker Brainstorming
-- Creates topics, topic_usage_history, and speaker_pool tables for event workflow steps 1-3
-- SOURCE OF TRUTH: docs/architecture/03-data-architecture.md, Section 3.5 (Topic) + Story 5.2 requirements

-- Enable UUID extension if not already enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Topics table with usage tracking and similarity scoring
CREATE TABLE IF NOT EXISTS topics (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(500) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL CHECK (category IN (
        'technical', 'management', 'soft_skills',
        'industry_trends', 'tools_platforms'
    )),
    created_date TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_used_date TIMESTAMP WITH TIME ZONE,
    usage_count INTEGER DEFAULT 0,
    staleness_score INTEGER DEFAULT 100 CHECK (staleness_score >= 0 AND staleness_score <= 100),
    calculated_wait_period INTEGER, -- in months
    partner_influence_score DOUBLE PRECISION,
    similarity_scores JSONB DEFAULT '[]', -- Array of {topicId: UUID, score: number}
    is_active BOOLEAN DEFAULT TRUE,
    title_vector tsvector GENERATED ALWAYS AS (to_tsvector('english', title)) STORED,
    description_vector tsvector GENERATED ALWAYS AS (to_tsvector('english', coalesce(description, ''))) STORED,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Topic usage history tracking
CREATE TABLE IF NOT EXISTS topic_usage_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    topic_id UUID NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    used_date TIMESTAMP WITH TIME ZONE NOT NULL,
    attendee_count INTEGER,
    feedback_score DOUBLE PRECISION,
    engagement_score DOUBLE PRECISION, -- 0.00 to 1.00
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Speaker pool for event brainstorming (Step 2-3: Speaker Brainstorming)
-- Note: This is for potential speakers during planning, not confirmed speakers
CREATE TABLE IF NOT EXISTS speaker_pool (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    speaker_name VARCHAR(255) NOT NULL,
    company VARCHAR(255),
    expertise TEXT,
    assigned_organizer_id VARCHAR(255), -- Username of organizer responsible for outreach
    status VARCHAR(50) NOT NULL DEFAULT 'identified' CHECK (status IN (
        'identified', 'contacted', 'ready', 'accepted', 'declined',
        'content_submitted', 'quality_reviewed', 'slot_assigned',
        'confirmed', 'withdrew', 'overflow'
    )),
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes for topics table (performance optimization)
CREATE INDEX IF NOT EXISTS idx_topics_title_vector ON topics USING GIN(title_vector);
CREATE INDEX IF NOT EXISTS idx_topics_description_vector ON topics USING GIN(description_vector);
CREATE INDEX IF NOT EXISTS idx_topics_last_used ON topics(last_used_date);
CREATE INDEX IF NOT EXISTS idx_topics_staleness ON topics(staleness_score DESC);
CREATE INDEX IF NOT EXISTS idx_topics_active ON topics(is_active);
CREATE INDEX IF NOT EXISTS idx_topics_category ON topics(category);

-- Indexes for topic_usage_history table
CREATE INDEX IF NOT EXISTS idx_topic_usage_history_topic_id ON topic_usage_history(topic_id);
CREATE INDEX IF NOT EXISTS idx_topic_usage_history_event_id ON topic_usage_history(event_id);
CREATE INDEX IF NOT EXISTS idx_topic_usage_history_used_date ON topic_usage_history(used_date DESC);

-- Indexes for speaker_pool table
CREATE INDEX IF NOT EXISTS idx_speaker_pool_event_id ON speaker_pool(event_id);
CREATE INDEX IF NOT EXISTS idx_speaker_pool_status ON speaker_pool(status);
CREATE INDEX IF NOT EXISTS idx_speaker_pool_assigned_organizer ON speaker_pool(assigned_organizer_id);

-- Triggers for automatic updated_at timestamps
DROP TRIGGER IF EXISTS update_topics_updated_at ON topics;
CREATE TRIGGER update_topics_updated_at BEFORE UPDATE ON topics
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_topic_usage_history_updated_at ON topic_usage_history;
CREATE TRIGGER update_topic_usage_history_updated_at BEFORE UPDATE ON topic_usage_history
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_speaker_pool_updated_at ON speaker_pool;
CREATE TRIGGER update_speaker_pool_updated_at BEFORE UPDATE ON speaker_pool
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Comments documenting architecture alignment
COMMENT ON TABLE topics IS 'Event topics with staleness scoring and similarity detection - Story 5.2, Architecture 03-data-architecture.md Section 3.5';
COMMENT ON TABLE topic_usage_history IS 'Historical topic usage tracking for staleness calculation - Story 5.2';
COMMENT ON TABLE speaker_pool IS 'Potential speakers during event brainstorming phase (Step 2-3) - Story 5.2';

COMMENT ON COLUMN topics.staleness_score IS '0-100 score where 100 = safe to reuse (>12 months since last use), 0 = too recent';
COMMENT ON COLUMN topics.similarity_scores IS 'JSONB array of {topicId: UUID, score: number} for duplicate detection (>70% triggers warning)';
COMMENT ON COLUMN topics.title_vector IS 'Full-text search vector for title (auto-generated, used for TF-IDF similarity calculation)';
COMMENT ON COLUMN topics.description_vector IS 'Full-text search vector for description (auto-generated, used for TF-IDF similarity calculation)';

COMMENT ON COLUMN speaker_pool.status IS 'SpeakerWorkflowState enum - lowercase_with_underscores in DB, UPPER_CASE in Java';
COMMENT ON COLUMN speaker_pool.assigned_organizer_id IS 'Username (not UUID) of organizer responsible for contacting this potential speaker';
