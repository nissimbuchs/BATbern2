-- W4.x: Fix actual_start_time and actual_end_time to use TIMESTAMP WITH TIME ZONE
-- V56 accidentally used plain TIMESTAMP (without timezone), inconsistent with start_time/end_time.
-- JPA maps both to java.time.Instant; DB column must be timezone-aware to store correctly.
ALTER TABLE sessions
    ALTER COLUMN actual_start_time TYPE TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN actual_end_time TYPE TIMESTAMP WITH TIME ZONE;
