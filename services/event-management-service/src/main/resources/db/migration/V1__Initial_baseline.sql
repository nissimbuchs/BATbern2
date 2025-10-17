-- V1__Initial_baseline.sql
-- Initial database baseline for BATbern platform
-- This establishes the baseline for Flyway migrations

-- Note: H2 database (used for tests) has built-in UUID support, so no extension needed
-- PostgreSQL needs the uuid-ossp extension (this will fail gracefully in H2)

-- Create UUID extension for PostgreSQL (H2 has built-in UUID support)
-- This will fail gracefully in H2 and succeed in PostgreSQL
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
EXCEPTION
    WHEN OTHERS THEN NULL;
END $$;

-- Create trigger function for automatic updated_at column updates
-- This function is used across all tables with updated_at columns
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Schema comment (PostgreSQL only - H2 will ignore)
-- COMMENT ON DATABASE batbern IS 'BATbern Event Management Platform Database';

-- Initial baseline established
-- Future migrations will build upon this foundation
