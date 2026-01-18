-- V46__Fix_technical_requirements_column_type.sql
-- Fixes technical_requirements column type from TEXT[] to TEXT
-- This simplifies Hibernate mapping and avoids array type compatibility issues

-- Drop the column if it exists with the old type and recreate
-- We need to handle both cases: TEXT[] and TEXT
DO $$
BEGIN
    -- Check if column exists and is an array type
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'speaker_invitations'
        AND column_name = 'technical_requirements'
        AND data_type = 'ARRAY'
    ) THEN
        -- Convert array to comma-separated string
        ALTER TABLE speaker_invitations
        ALTER COLUMN technical_requirements TYPE TEXT
        USING array_to_string(technical_requirements, ',');
    END IF;
END $$;
