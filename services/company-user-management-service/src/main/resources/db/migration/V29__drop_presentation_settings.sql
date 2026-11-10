-- The presenter screen settings live in the EMS app settings store (key `presentation`) since
-- Story 4.9; EMS V152 copied the row. The table was kept one release (#1281 -> #1286) so an old
-- CUMS image that still mapped it could start mid-deploy or after a rollback. No CUMS image since
-- then reads it. Production check before this drop (2026-11-10): GET /public/settings/presentation
-- serves the copied text from EMS.
DROP TABLE IF EXISTS presentation_settings;
