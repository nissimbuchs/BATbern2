-- Swiss UID removed from the company model (#1119): unused (0 of 691 companies had one on
-- 2026-10-25) and no feature reads it. The index from V3 goes with the column.
DROP INDEX IF EXISTS idx_company_uid;
ALTER TABLE companies DROP COLUMN IF EXISTS swiss_uid;
