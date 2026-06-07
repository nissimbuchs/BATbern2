# User Identity Collision Fix Plan

**Status:** Approved 2026-05-18 (with simplifications — see "Decision log" at bottom). Ready to implement.
**Owner:** Nissim Buchs
**Created:** 2026-05-18
**Triggering incident:** Daniel Berger (mobi.ch) registered for BATbern59 today; the confirmation went to a stale `daniel.berger@insel.ch` (his previous job, now hard-bouncing). He received nothing and emailed `info@berner-architekten-treffen.ch` asking what happened.

---

## TL;DR

When a registrant's `firstname + lastname` matches an existing user account, `UserService.getOrCreateUser()` returns that existing account **and ignores the email submitted in the form**. As a result, the confirmation email goes to whatever the old account stored — frequently the registrant's *previous* employer's address. That address has often bounced and been suppressed, so the registrant gets nothing.

Today this affected at minimum:
- `daniel.berger@mobi.ch` (got nothing — old account had `@insel.ch`)
- Probably also `nikolay.borissov@postfinance.ch` and `felix.gruener@swisscom.com` (today's three new SES suppressions all match the firstname.lastname pattern of users who likely re-registered with a new employer).

**Fix in one sentence:** Treat the submitted email as the identity for anonymous registration & newsletter; on a name collision, create a NEW account with a deduplicated username via the existing `.2 / .3 / …` suffix mechanism in `SlugGenerationService.ensureUniqueUsername()`.

---

## Where the bug lives

| File | Line | What it does today | Why it's wrong |
|------|------|-------------------|----------------|
| `services/company-user-management-service/.../service/UserService.java` | 525 | `getOrCreateUser()` entry point | — |
| ↳ same file | 529–538 | Lookup by `email` (good — exact match returns existing user) | OK |
| ↳ same file | 542–545 | Falls through to `findByFirstNameIgnoreCaseAndLastNameIgnoreCase` | **Wrong key** for anonymous public registration |
| ↳ same file | 547–565 | If exactly one name match: return that user with `created:false` | Returns *wrong* user when the submitted email is genuinely new |
| ↳ same file | 553–560 | Conditionally overwrite stored email — only if old one is `null` or `@batbern.ch` | Real external emails (`@insel.ch`, `@swisscom.com`, …) are never overwritten, so the response email stays stale |
| `services/event-management-service/.../service/RegistrationService.java` | 89–100 | Builds `GetOrCreateUserRequest` from the form, calls `userApiClient.getOrCreateUser()` | Trusts the response blindly |
| `services/event-management-service/.../controller/EventController.java` | 1844 | Re-fetches the user by username, then passes that `UserResponse` to the email service | Stale email comes back here |
| `services/event-management-service/.../service/RegistrationEmailService.java` | 116 | Sends to `userProfile.getEmail()` (the *stored* email, not the *submitted* email) | The actual point where the wrong recipient is used |
| `shared-kernel/.../service/SlugGenerationService.java` | 113–133 | `ensureUniqueUsername()` — the `.2 / .3 / …` suffix mechanism the user remembered | Exists and works, just not called in the anonymous-registration path |
| `services/company-user-management-service/.../db/migration/V4__Create_user_profiles_table.sql` | 7, 13 | UNIQUE constraints on both `username` and `email` | Schema already supports the fix |

### Why "Story 3.2 name matching" exists at all

Lines 540–570 of `UserService.getOrCreateUser` were added for **batch import of historical data** — back-fill speakers/attendees from old spreadsheets where rows had a name but possibly no email or only a placeholder `@batbern.ch` email. The fallback was correct for that batch-import context.

It is **wrong for live anonymous registration**, where the form-submitter is asserting "this is me, this is my current email." A stranger filling in their name should never be matched against a different person's account just because the names happen to coincide.

---

## Goal after the fix

| Submitted email | Existing account match? | Resulting behavior |
|-----------------|-------------------------|--------------------|
| New `daniel.berger@mobi.ch`, no existing user | none | Create new user `daniel.berger`, send to `mobi.ch` ✓ |
| Same `daniel.berger@mobi.ch`, exact email match exists | email match | Re-use existing user, send to `mobi.ch` ✓ |
| New `daniel.berger@mobi.ch`, **only a name match (different email)** exists | name only | **Create new user `daniel.berger.2`**, send to `mobi.ch` ✓ |
| Newsletter subscribe with a new email but matching name | n/a (newsletter is already email-keyed) | unchanged — already correct |
| Batch import path | name match used as today | unchanged — preserve historical behavior |

---

## Proposed design (simplified after Q3)

### Principle

Email is the only identity key for `getOrCreateUser`. The name-based fallback was added for historical batch imports — those are no longer in active use, so we just remove the fallback. Anonymous public registration becomes:

> "Look up by email. If found, return that user. If not, create a brand-new user with a deduplicated username (`john.doe`, `john.doe.2`, …)."

No `MatchMode` enum, no flags, no per-caller audit needed.

### Change A — Delete the name-fallback branch in `UserService.getOrCreateUser`

`services/company-user-management-service/.../service/UserService.java:540-570` — remove the entire `findByFirstNameIgnoreCaseAndLastNameIgnoreCase` block and its email-overwrite guard. The method collapses to:

```java
public GetOrCreateUserResponse getOrCreateUser(GetOrCreateUserRequest request) {
    // 1. Email match — return existing user if any.
    Optional<User> userByEmail = userRepository.findByEmail(request.getEmail());
    if (userByEmail.isPresent()) {
        User existingUser = userByEmail.get();
        return new GetOrCreateUserResponse()
                .username(existingUser.getUsername())
                .created(false)
                .user(responseMapper.mapToResponse(existingUser));
    }

    // 2. No match → create new user with a guaranteed-unique username.
    if (Boolean.TRUE.equals(request.getCreateIfMissing())) {
        User newUser = createNewUser(request);   // already calls ensureUniqueUsername (line 610)
        return new GetOrCreateUserResponse()
                .username(newUser.getUsername())
                .created(true)
                .cognitoUserId(newUser.getCognitoUserId())
                .user(responseMapper.mapToResponse(newUser));
    }
    throw new UserNotFoundException("User not found: " + request.getEmail());
}
```

The username-collision handling (`.2 / .3 / …`) is already done inside `createNewUser` via `slugService.ensureUniqueUsername` at line 610. No new code there.

### Change B — Newsletter subscribe: nothing to do

`NewsletterSubscriberService.subscribe` already keys by email. Verified during research.

### Change C — No caller audit needed

Public registration, JIT user provisioning, and `UserReconciliationService` all already call `getOrCreateUser` with the submitted email. The behavior change is uniform: distinct emails now produce distinct users. No call sites need changing.

### Change D — Repository methods that may become unused

After removing the name fallback, `UserRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCase` may have no remaining callers. Check + delete if dead. (Don't bother if other code uses it; it's harmless.)

---

## Tests (TDD as usual)

In `services/company-user-management-service/.../service/UserServiceTest.java`:

1. `getOrCreateUser` with new email + colliding name → creates new user, username gets `.2` suffix, returns `created:true`.
2. `getOrCreateUser` with exact email match → returns existing user (today's behavior preserved).
3. `getOrCreateUser` with new email + no name match → creates new user with the base username (today's behavior).
4. `getOrCreateUser` with new email + ambiguous name match (2+ users) → creates new user (today's fall-through, now uniform with the simple case).
5. Username collision N times: `john.doe` exists → next gets `.2`, then `.3`, then `.4` (locks in the existing `ensureUniqueUsername` contract).
6. **Regression test for today's incident**: prime `daniel.berger@insel.ch` (existing user), submit `daniel.berger@mobi.ch` with same first/last names → asserts new user `daniel.berger.2 / mobi.ch` is created, the old user is untouched.

In `services/event-management-service/.../service/RegistrationServiceTest.java`:

7. End-to-end mock: registration POST with new email + same name as existing user → the resulting `userProfile.email` equals the submitted email; the email service is called with that submitted email.

In `services/event-management-service/.../service/NewsletterSubscriberServiceTest.java`:

8. Sanity test: subscribing with a new email but matching name in `users` table (newsletter has its own table; should not touch users at all). Confirms there is no cross-table coupling.

---

## Backfill — what about the existing stale data?

The fix only prevents *future* incidents. The existing user table still contains:

- Users with `@insel.ch`, `@postfinance.ch`, `@swisscom.com` etc. where the real person has moved on.
- Today's three suppressions are visible markers; there are probably more dormant ones.

Three options for the existing data — pick one based on appetite:

**Option 1 (cheap, recommended)** — let the new code fix it organically. As affected people re-register, they create `daniel.berger.2 / nikolay.borissov.2 / …` accounts under their current employer. The old accounts stay (they still own registration history for past events). After the fix lands, future bounces stop and the SES rolling rate drops on its own.

**Option 2 (medium)** — one-off Flyway migration that finds users whose stored email matches the SES suppression list and either soft-deletes them, marks them inactive, or unlinks their email. Pulls the SES list at migration time via a small admin endpoint. ~2-3 hour task. Stops the next ZAP/scheduled-newsletter wave from re-bouncing the same addresses.

**Option 3 (expensive, not now)** — proactive outreach: identify users whose stored email is on the SES suppression list, find their current email via LinkedIn or a manual lookup, and send them a one-time "please confirm your current email" link. Manual labor for marginal benefit; backlog only.

---

## Edge cases

- **Same person, same name, same employer, registering twice with the same email** — caught by the email match at step 1, no change.
- **Same person, slight email typo on re-register** (`john.doe@example.ch` vs `john.doel@example.ch`) — creates a new `john.doe.2` account because the typo'd email isn't found by step 1 and the name path is now disabled for public registration. Acceptable: support can merge if needed; the alternative (silently sending to the old typo'd address) is what we just hit.
- **Same name, completely different person** — exactly what the fix is for. New `.2` account.
- **Authenticated re-registration** (JWT present, organizer adding someone) — we don't go through `getOrCreateUser` in the same way; out of scope for this plan. Worth confirming, but no log evidence of issues there.
- **Speaker bench / partner contacts pre-loaded by an organizer** — these go through different endpoints (`hasRole('ORGANIZER')`) that should continue to use `EMAIL_OR_NAME` because the organizer is asserting the identity match. Audit during Change E.

---

## Decision log (2026-05-18)

1. **Suffix visibility** — keep the `.2 / .3 / …` invisible to the end user. Server-side detail only.
2. **Backfill** — Option 1: let the new code fix it organically as affected users re-register. Revisit only if bounce rate hasn't dropped within 2 weeks.
3. **Historical batch import** — confirmed inactive. The plan therefore deletes the name-fallback path entirely (no `MatchMode` enum needed).
4. **Today's incident data** — fix manually on staging via bastion tunnel: identify each registration from today that sent to a stale-domain address, update each affected user's email to the email actually submitted in the form, then re-trigger the registration confirmation. Tracked separately as "data fix today" below.

---

## Out of scope

- Hardening Cognito-authenticated user flows (separate auth story).
- Deduplication of users with different names but the same email (different bug class).
- Migration of all stale `@insel.ch / @swisscom.com / @postfinance.ch` accounts.
- UI for users to claim ownership of a collided account.

---

## References

- Today's incident commit + investigation: `a3ac0a97 fix(email): stop iMIP calendar replies from fan-out to organizers`, `85b97894 fix(security): block reserved-domain SES sends + per-IP anon POST limits (Tier 1)`
- Related abuse-defense plan (different concern, same incident day): `docs/plans/registration-abuse-defense-plan.md`
- Slack/email thread from Daniel Berger, 2026-05-18 16:41 GMT+2
