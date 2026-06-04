-- V19__update_picture_import_attempt_comment.sql
-- Story 12.12 code review (findings #1 + #5): the avatar-import attempt semantics were
-- refined from "never reset, one attempt ever" to "one TERMINAL attempt ever; TRANSIENT
-- fetch failures (upstream 5xx/429, network errors, executor rejection) release the
-- claim so a later federated request retries". The claim is now taken via an atomic
-- conditional UPDATE (UserRepository.claimPictureImportAttempt) instead of
-- load-check-save, closing the concurrent double-dispatch race.
--
-- V18 may already be applied in the shared environment, so its file is frozen
-- (Flyway checksum); this forward migration only refreshes the column COMMENT to match
-- the new semantics. No schema or data change.

COMMENT ON COLUMN user_profiles.picture_import_attempted_at IS
    'Story 12.12: when the one-time federated (Google) avatar import was claimed (atomic CAS). NULL = never attempted. Terminal outcomes (success, 3xx/4xx, non-image, oversize, invalid claim) keep the claim forever; transient fetch failures (5xx/429, network/IO, executor rejection) reset it to NULL so a later federated request retries.';
