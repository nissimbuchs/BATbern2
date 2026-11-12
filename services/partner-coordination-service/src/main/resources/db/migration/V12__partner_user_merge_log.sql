-- Epic 17 sweep (2026-11): one row per profile merge that moved a username held by
-- partner-coordination (user-reference contract, Epic 17 entry 7b). event-management's
-- user_merge_log does not know these rows, so this table keeps what is needed to undo the
-- PCS part of a merge by hand:
--   moved  {"<table>.<column>": [<id of each moved row>, ...]}, only the columns that moved rows.
-- A merge that moves nothing (a repeat, a person without partner rows) writes no row.
CREATE TABLE partner_user_merge_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_username VARCHAR(100) NOT NULL,
    target_username VARCHAR(100) NOT NULL,
    performed_by    VARCHAR(100) NOT NULL,
    trigger         VARCHAR(20)  NOT NULL,
    moved           JSONB        NOT NULL,
    performed_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_partner_user_merge_log_source ON partner_user_merge_log (source_username);
CREATE INDEX idx_partner_user_merge_log_target ON partner_user_merge_log (target_username);
