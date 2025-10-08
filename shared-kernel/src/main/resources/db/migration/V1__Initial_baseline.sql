-- V1__Initial_baseline.sql
-- Initial database baseline for BATbern platform
-- This establishes the baseline for Flyway migrations

-- Enable UUID extension for generating UUIDs
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create audit tracking function for updated_at timestamps
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Schema comment
COMMENT ON DATABASE batbern IS 'BATbern Event Management Platform Database';

-- Initial baseline established
-- Future migrations will build upon this foundation
