-- V37__create_company_merge_log.sql
-- #1251 (issue sweep epic 4, story 4): one row per merge of two companies (organizers fold a
-- duplicate such as "Unic AG" into "Unic"). The merge moves every reference of the source key to
-- the target key in CUMS and at every holder of the company-references contract, fills empty
-- fields of the target, and deletes the source. This table keeps what is needed to undo a merge by
-- hand (holders keep no log of their own):
--   moved_keys      {"<table>.<column>": [<id of each moved row>, ...]}, CUMS and every holder
--   removed_keys    {"<table>.<column>": [<id of each removed row>, ...]}, rows of the source
--                   dropped because the target held the same one; "company_logo_requests.status"
--                   lists open logo requests closed (DECLINED, row kept) for the same reason
--   filled_fields   ["website", "logo", ...], fields of the target taken from the source
--   source_snapshot the deleted companies row of the source, with its aliases
--   outcome         COMPLETED, or INCOMPLETE for an attempt that failed after holders moved rows:
--                   CUMS rolled back, and the retry gets nothing from those holders, so the ids they
--                   moved are only here. An undo reads every row of the source/target pair.
CREATE TABLE company_merge_log (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    source_key      VARCHAR(255) NOT NULL,
    target_key      VARCHAR(255) NOT NULL,
    performed_by    VARCHAR(255) NOT NULL,
    outcome         VARCHAR(10)  NOT NULL CHECK (outcome IN ('COMPLETED', 'INCOMPLETE')),
    merged_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    moved_keys      JSONB        NOT NULL,
    removed_keys    JSONB        NOT NULL,
    filled_fields   JSONB        NOT NULL,
    source_snapshot JSONB        NOT NULL
);

CREATE INDEX idx_company_merge_log_source ON company_merge_log (source_key);
CREATE INDEX idx_company_merge_log_target ON company_merge_log (target_key);
