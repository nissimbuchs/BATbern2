-- V154: drop the shared logos table (#1307, owner go 2026-11-13).
--
-- Story 17.11 (#1308) moved uploads to event_management_uploads (V153) and company_user_uploads
-- (company-user-management V31 drops the same table); no code reads or writes logos any more. Its
-- upload history is discarded by owner decision. The S3 objects are untouched: events.theme_image_url
-- and companies.logo_url keep pointing at them.
--
-- Both services created logos (event-management V5, company-user-management V10) in the one shared
-- database, so both drop it. IF EXISTS makes the order irrelevant. Nothing depends on the table
-- (no FK, view, trigger or function); its indexes go with it.

DROP TABLE IF EXISTS logos;
