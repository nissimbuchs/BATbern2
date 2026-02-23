-- V3__add_partnership_cost.sql
-- Story 8.1: Partner Attendance Dashboard - AC3 (Cost Per Attendee)
-- Adds partnership_cost column to partners table for computing cost-per-attendee KPI.

ALTER TABLE partners ADD COLUMN IF NOT EXISTS partnership_cost NUMERIC(10,2);

COMMENT ON COLUMN partners.partnership_cost IS
    'Annual partnership cost in CHF. Used by partner analytics to compute cost-per-attendee KPI (Story 8.1).';
