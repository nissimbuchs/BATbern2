-- V116 — Repair thank-you notes whose `thanked_by_username` was stored as the Cognito `sub`
-- (a UUID) instead of the canonical username.
--
-- Root cause: OrganizerThanksController captured `authentication.getName()` (= Cognito sub) at
-- submit time instead of the `custom:username` claim. Fixed in code to
-- SecurityContextHelper.getCurrentUsernameOrNull() (custom:username + Pattern 3b twin DB
-- fallback). This forward migration retro-fixes already-stored rows so the read-time enrichment
-- (JOIN user_profiles ON username = thanked_by_username) resolves the author's name again.
--
-- Forward migration only — never edit an applied migration. Idempotent: rows already holding a
-- real username do not match user_profiles.cognito_user_id and are left untouched. The NOT EXISTS
-- guard avoids violating the partial unique index ux_organizer_thanks_user (event_id, username)
-- in the (unlikely) case a canonical-username row already exists for the same event.
UPDATE organizer_thanks t
SET thanked_by_username = up.username
FROM user_profiles up
WHERE t.thanked_by_username IS NOT NULL
  AND up.cognito_user_id = t.thanked_by_username
  AND NOT EXISTS (
    SELECT 1 FROM organizer_thanks x
    WHERE x.event_id = t.event_id
      AND x.thanked_by_username = up.username
      AND x.id <> t.id
  );
