-- #1183 C/D: one row per merge of two profiles of the same person.
--
-- A merge moves everything event-management holds under the source username to the target
-- username (registrations, speaker slots, arrivals, notifications, Q&A, newsletter rows, ...).
-- Where the target already holds a row with the same key (same event, same session), the source
-- row is removed, unless it is the better registration (see UserMergeService). This table keeps
-- what is needed to undo a merge by hand:
--   moved        {"<table>.<column>": [<key of each moved row>, ...]}
--   removed_rows [{"table": "<table>", "row": {<full row as it was>}}, ...]
--   source_profile  the deleted user_profiles row of the source, with its roles and addresses,
--                   as company-user-management sent it before it deleted the profile.
CREATE TABLE user_merge_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_username VARCHAR(100) NOT NULL,
    target_username VARCHAR(100) NOT NULL,
    performed_by    VARCHAR(100) NOT NULL,
    trigger         VARCHAR(20)  NOT NULL CHECK (trigger IN ('ORGANIZER', 'SELF_CLAIM')),
    performed_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    moved           JSONB        NOT NULL,
    removed_rows    JSONB        NOT NULL,
    source_profile  JSONB
);

CREATE INDEX idx_user_merge_log_source ON user_merge_log (source_username);
CREATE INDEX idx_user_merge_log_target ON user_merge_log (target_username);
