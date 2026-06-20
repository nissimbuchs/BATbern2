-- Story 15.7 (Epic 15 — post-Event-#2 hardening): per-user Q&A notification cadence preference.
--
-- A dedicated control for how often a session's speaker / co-speaker / moderator is emailed
-- about new audience questions in their session Q&A:
--   live  = bundled digest during the session (~15 min window)   [default]
--   daily = at most once per 24h
--   off   = never
--
-- Additive, prod-safe (staging IS production). Lives on user_profiles via the UserPreferences
-- @Embeddable (pref_ column prefix), String-typed like the sibling pref_notification_frequency.

-- Nullable with a DEFAULT, mirroring the sibling pref_* columns (V4): a user may have a NULL
-- preferences embeddable (Hibernate writes NULLs to every pref_ column), so this column must
-- tolerate NULL. The application default lives on the UserPreferences @Embeddable (= 'live') and
-- reads null-coalesce to 'live'. The CHECK passes for NULL (3-valued logic).
ALTER TABLE user_profiles
    ADD COLUMN pref_qna_notification_frequency VARCHAR(10) DEFAULT 'live'
        CHECK (pref_qna_notification_frequency IN ('live', 'daily', 'off'));
