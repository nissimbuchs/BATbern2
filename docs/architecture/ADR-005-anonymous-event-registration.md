# ADR-005: Anonymous Event Registration

## Status
Superseded by [ADR-007: Unified User Profile for Anonymous and Authenticated Users](./ADR-007-unified-user-profile.md).
This file is a stub that keeps existing references resolvable.

## What it was

ADR-005 "Anonymous Event Registration" (accepted 2025-11-08) decided that visitors can register
for an event without a Cognito account: `user_profiles` becomes the single source of truth for
anonymous and authenticated users alike, `cognito_user_id` becomes nullable, and `registrations`
references the user instead of copying name and email.

The same decision, with the data model that was actually built, is recorded in ADR-007 (same
date). ADR-005 was deleted in commit `db45c42a` (2025-12-24, Epic 5.5, #386) without a
replacement file.

## Why this stub exists

ADR-005 is still cited by name in code comments, tests and applied Flyway migrations, for example:

- `services/company-user-management-service/src/main/resources/db/migration/V11__Make_cognito_id_nullable_for_anonymous_users.sql`
- `services/company-user-management-service/src/main/resources/db/migration/V17__add_terms_accepted_at_to_user_profiles.sql`
- `services/event-management-service/src/main/resources/db/migration/V9__Remove_denormalized_attendee_data.sql`
- `RegistrationService`, `Registration`, `UserApiClient` (EMS) and `User` (CUMS)

Applied migrations must not be edited (their checksum would change), so the references stay and
this stub points them at the current decision. Read ADR-007 for the model.

The original text is available from git history:
`git show db45c42a^:docs/architecture/ADR-005-anonymous-event-registration.md`.
