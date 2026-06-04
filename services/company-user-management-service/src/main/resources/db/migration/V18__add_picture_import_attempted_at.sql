-- V18__add_picture_import_attempted_at.sql
-- Story 12.12: one-time Google profile-picture import on federated sign-in.
-- Records the single import attempt (success OR failure) so the avatar import
-- never re-runs: no clobbering a user-uploaded picture, no re-import after the
-- user deletes their picture, no repeated fetches of a broken Google URL.
-- Deliberately simple one-attempt-ever semantics; a richer "sync from Google"
-- feature can supersede this later.
--
-- NOTE: numbered V18 (not V17) because Story 12.11 — developed in parallel —
-- claims V17 for terms_accepted_at/newsletter_opt_in. If 12.11 is abandoned,
-- the V17 gap is harmless (Flyway allows gaps); only out-of-order application
-- would be a problem, and both stories ship on the same branch.

ALTER TABLE user_profiles
    ADD COLUMN picture_import_attempted_at TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN user_profiles.picture_import_attempted_at IS
    'Story 12.12: when the one-time federated (Google) avatar import was attempted (success or failure). NULL = never attempted. Never reset — one attempt per user, ever.';
