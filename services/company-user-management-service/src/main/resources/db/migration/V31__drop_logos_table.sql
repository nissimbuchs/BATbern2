-- V31: drop the shared logos table (#1307, owner go 2026-11-13).
--
-- Story 17.11 (#1308) moved uploads to company_user_uploads (V30) and event_management_uploads
-- (event-management V154 drops the same table); no code reads or writes logos any more. Its upload
-- history is discarded by owner decision. The S3 objects are untouched: companies.logo_url and
-- events.theme_image_url keep pointing at them.
--
-- Both services created logos (company-user-management V10, event-management V5) in the one shared
-- database, so both drop it. IF EXISTS makes the order irrelevant. Nothing depends on the table
-- (no FK, view, trigger or function); its indexes go with it.

DROP TABLE IF EXISTS logos;
