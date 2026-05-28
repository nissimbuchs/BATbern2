-- V106__add_registration_resend_tracking.sql
-- Feature: unconfirmed-registration auto-resend (link-validity hardening).
--
-- Adds bookkeeping columns used by RegistrationResendService — a daily job that re-sends the
-- registration-confirmation email to attendees who registered but never clicked the confirmation
-- link. The columns cap how many times a single registration is auto-resent and enforce a minimum
-- gap between resends, so a daily run never floods inboxes.
--
--   confirmation_resent_at    — timestamp of the most recent automated resend (NULL = never resent)
--   confirmation_resend_count — number of automated resends so far (default 0)
--
-- Backward compatible: both columns are nullable / defaulted, so existing rows are unaffected.
-- Tuning lives in application.yml under app.registration.resend.*.

ALTER TABLE registrations
    ADD COLUMN IF NOT EXISTS confirmation_resent_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS confirmation_resend_count INTEGER NOT NULL DEFAULT 0;

-- Speeds up the daily resend scan: only still-pending ('registered') rows are ever eligible.
CREATE INDEX IF NOT EXISTS idx_registrations_pending_resend
    ON registrations (created_at)
    WHERE status = 'registered';
