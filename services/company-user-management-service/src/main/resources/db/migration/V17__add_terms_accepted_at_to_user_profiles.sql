-- Story 12.11 (AC1): ToS/Privacy consent timestamp for the federated onboarding gate.
--
-- terms_accepted_at records WHEN the user accepted the Terms of Service + Privacy
-- Policy. NULL = consent not on record -> the frontend onboarding gate redirects the
-- user to /profile?onboarding=1 until they explicitly accept (write-once via
-- PUT /api/v1/users/me, server clock).
--
-- NOTE: newsletter consent intentionally has NO column here — it lives exclusively in
-- the event-management-service `newsletter_subscribers` table (Story 10.7) and is
-- managed via GET/PATCH /api/v1/newsletter/my-subscription.

ALTER TABLE user_profiles
    ADD COLUMN terms_accepted_at TIMESTAMP WITH TIME ZONE NULL;

-- Backfill: rows created BEFORE the SSO go-live moment cannot be federated and were
-- either (a) native self-registrations, which required the RegistrationStep2 ToS
-- checkbox, or (b) organizer-provisioned accounts (accepted tradeoff, Story 12.11
-- Dev Notes) — both are treated as consented at creation time.
--
-- Discriminator (verified against the live DB, 2026-06-04): cognito_user_id stores the
-- Cognito sub UUID for ALL users — federated users included — so a username-shape
-- pattern (e.g. LIKE 'google_%') matches NOTHING. The cutoff '2026-06-04 16:00:00+00'
-- is the SSO go-live moment; the first federated row was created 2026-06-04 16:04:51
-- UTC. Federated rows therefore all remain NULL -> retroactive consent gate.
-- Anonymous rows (cognito_user_id IS NULL, ADR-005) remain NULL; they cannot
-- authenticate, and the post-confirmation Lambda records consent when they later
-- register natively.
UPDATE user_profiles SET terms_accepted_at = created_at
WHERE cognito_user_id IS NOT NULL
AND created_at < TIMESTAMP WITH TIME ZONE '2026-06-04 16:00:00+00'
AND terms_accepted_at IS NULL;
