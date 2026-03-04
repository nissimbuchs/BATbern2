-- Story 10.12: Self-Service Deregistration (AC1)
-- Adds deregistration_token column to registrations table.
-- Two-step approach: add nullable, backfill, then set NOT NULL with default.

-- Step 1: Add column as nullable first (existing rows have no token yet)
ALTER TABLE registrations ADD COLUMN IF NOT EXISTS deregistration_token UUID;

-- Step 2: Backfill all existing rows with unique random UUIDs
UPDATE registrations SET deregistration_token = gen_random_uuid() WHERE deregistration_token IS NULL;

-- Step 3: Enforce NOT NULL constraint
ALTER TABLE registrations ALTER COLUMN deregistration_token SET NOT NULL;

-- Step 4: Set default for new rows (so application code need not always supply it)
ALTER TABLE registrations ALTER COLUMN deregistration_token SET DEFAULT gen_random_uuid();

-- Step 5: Unique index (token is the sole auth mechanism — must be unique)
CREATE UNIQUE INDEX idx_registrations_deregistration_token ON registrations (deregistration_token);
