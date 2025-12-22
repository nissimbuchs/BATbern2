-- V24__add_topic_code_column.sql
-- Story 1: Topics API Migration - ADR-003 Meaningful Identifiers
-- Adds topic_code as the external API identifier (slug format)

-- Add topic_code column (nullable initially for migration)
ALTER TABLE topics ADD COLUMN IF NOT EXISTS topic_code VARCHAR(255);

-- Generate topic_code from title for existing rows
-- Converts title to lowercase, replaces spaces/special chars with hyphens
UPDATE topics
SET topic_code = LOWER(
    REGEXP_REPLACE(
        REGEXP_REPLACE(title, '[^a-zA-Z0-9\s-]', '', 'g'),
        '\s+', '-', 'g'
    )
)
WHERE topic_code IS NULL;

-- Handle potential duplicates by appending a sequence number
WITH duplicates AS (
    SELECT id, topic_code,
           ROW_NUMBER() OVER (PARTITION BY topic_code ORDER BY created_at) as rn
    FROM topics
    WHERE topic_code IS NOT NULL
)
UPDATE topics t
SET topic_code = t.topic_code || '-' || (d.rn - 1)
FROM duplicates d
WHERE t.id = d.id AND d.rn > 1;

-- Now make the column NOT NULL and add unique constraint
ALTER TABLE topics ALTER COLUMN topic_code SET NOT NULL;
ALTER TABLE topics ADD CONSTRAINT uk_topics_topic_code UNIQUE (topic_code);

-- Add index for fast lookups by topic_code (primary API identifier per ADR-003)
CREATE INDEX IF NOT EXISTS idx_topics_topic_code ON topics(topic_code);

-- Update similarity_scores JSONB to use topicCode instead of topicId
-- This requires updating the JSONB array entries
-- Note: This is a complex migration - we need to convert UUID references to topic_code
-- For now, we'll keep both formats until all data is migrated

-- Add comment documenting the column purpose
COMMENT ON COLUMN topics.topic_code IS 'Slug-format identifier for external API (ADR-003). Generated from title. Example: cloud-native-security-2024';
