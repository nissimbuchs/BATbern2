-- Add phase column to migration_errors table
-- Story: 3.2.1 - Task 8: Error Handling & Retry Logic
-- Purpose: Track which phase (READ, PROCESS, WRITE) the error occurred in

ALTER TABLE migration_errors
ADD COLUMN phase VARCHAR(20);

COMMENT ON COLUMN migration_errors.phase IS 'Migration phase where error occurred: READ, PROCESS, or WRITE';

CREATE INDEX idx_migration_errors_phase ON migration_errors(phase);
