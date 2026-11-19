---
title: 'A newsletter subscription becomes active only after its owner confirms it'
type: 'feature'
ticket: '4'
created: '2026-11-19'
status: 'done'
baseline_revision: 'ffb9172626795735c3da86ff076811b45051f5fc'
route: 'full'
route_source: 'auto'
risk: 'high'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context: ['{project-root}/docs/architecture/coding-standards.md', '{project-root}/docs/architecture/module-map.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `POST /newsletter/subscribers` (public, Turnstile fail-open, 5 per IP in 5 min) stores the address as active and sends no mail (#1288). Anyone can therefore subscribe anyone. The same holds for re-subscribing an address whose owner unsubscribed. The access-control notes and the 4.10c ticket claim a double opt-in that does not exist.

**Approach:** Double opt-in (owner #1288 option 1). An anonymous subscribe of a new, pending or unsubscribed address makes it pending: `confirmed_at` is null and the row carries a `confirmation_token`. It sends a DE/EN confirmation mail whose link points to a public confirmation page. The page posts the token to a new public endpoint, which activates the subscription (sets `confirmed_at`, clears `unsubscribed_at`). A pending row receives no newsletter and counts in no audience. A subscription from a registration is pending and becomes active when that registration is confirmed. A logged-in user's own confirmed address (`patchAddressSubscription`) and an organizer's resubscribe are active at once. Existing rows are backfilled as confirmed. The public subscribe answers 200 for every valid address, so it no longer reveals whether an address is subscribed (it answered 409 before). An already active or suppressed address gets no mail.

## Boundaries & Constraints

**Always:**
- Test first. Mail goes through the real `EmailService` into the `SesRecorder`. Assert what SES received.
- New template `newsletter-confirmation-{de,en}.html` on the default layout, seeded like the others. It names the 7-day window that story 3.5 enforces. Subject and body are in both languages. The language follows the request (EN only for `en`, otherwise DE).
- The confirm endpoint gets `@PreAuthorize("permitAll()")` plus `permitAll` in BOTH the EMS SecurityConfig and the API gateway SecurityConfig. Its row goes into `scripts/ci/access-control-public.txt`. Correct the subscribe row there.
- Spec first (`docs/api/newsletter*.openapi.yml`): the new endpoint, the subscribe response, and the `pending` status in `SubscriberResponse` and the organizer list's status filter. Then regenerate the frontend types.
- Every "active" query (repository methods named in the Code Map, the audience adapter, the counts) also requires `confirmed_at IS NOT NULL`. Grep for every other reader.
- A token is single-use: a used or unknown token answers 404 and changes nothing.
- The frontend shows "check your inbox" after subscribing. A route `/newsletter/confirm` shows success or invalid. New keys go into all 10 locales.
- Update the docs named by `.github/doc-drift-mappings.yml` for these paths.

**Never:**
- A newsletter to a pending row.
- Changing an active subscriber on an anonymous request (name, language, source).
- Logging an address unmasked or a token.
- Polling, EventBridge or a message bus.
- Changing the Turnstile fail-open behaviour (#1222 item 9).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| New address | anonymous subscribe | row pending, 1 confirmation mail in the request language, 200 | reserved domain: 400 as today |
| Pending address | anonymous subscribe again | still pending, a new mail with the same token, language updated, 200 | none |
| Unsubscribed address | anonymous subscribe | stays unsubscribed and is pending until confirmed, 1 mail, 200 | none |
| Active address | anonymous subscribe | unchanged, no mail, 200 | none |
| Suppressed address | anonymous subscribe | unchanged, no mail, 200 | none |
| Confirm | valid token | active (`confirmed_at` set, `unsubscribed_at` null), token cleared, 200 | used or unknown token: 404 |
| Registration opt-in | registration with newsletter checkbox | pending, no extra mail; active once the registration is confirmed (`confirmRegistration`, `confirmByEmail`) | already active: unchanged |
| Account toggle | logged-in user, own confirmed address | active at once | none |
| Organizer resubscribe | organizer action | active at once, `confirmed_at` set if null | none |
| Audience | newsletter send, counts, Mail Center newsletter audience | pending rows excluded | none |

</frozen-after-approval>

## Code Map

- `services/event-management-service/src/main/java/ch/batbern/events/service/NewsletterSubscriberService.java:90` `subscribe(email, firstName, language, source, username)`: today it creates the row active and throws `DuplicateSubscriberException` (409) for an active one. Split it into the anonymous path (pending plus mail) and the trusted path (account, organizer). Callers: `NewsletterController:67` (explicit), `RegistrationService:219` (registration, catches `DuplicateSubscriberException`), `patchAddressSubscription:231` (account), `resubscribeById:382` (organizer). `toResponse:286` and `findSubscribers/countSubscribers:324/345` (the status filter) need `pending`.
- `services/event-management-service/src/main/java/ch/batbern/events/repository/NewsletterSubscriberRepository.java`: active queries at lines 41, 49, 57 and 66 (`findByUnsubscribedAtIsNullAndSuppressedAtIsNull`, `findActiveAfterId`, `countBy...`, `findActiveSubscribersNotInSend`). Other readers: `mailcenter/compose/NewsletterAudienceAdapter`, `BounceProcessingService`, `NewsletterEmailService`, `SlidesOnlineEmailService`, `forwarding/InboundEmailRouter`, `specification/NewsletterSubscriberSpecification`.
- `services/event-management-service/src/main/java/ch/batbern/events/domain/NewsletterSubscriber.java`: add `confirmedAt` and `confirmationToken`.
- Migration `V158__newsletter_double_opt_in.sql` (V157 is story 3.1's): add the columns, a unique index on the token, and backfill `confirmed_at = subscribed_at`.
- `services/event-management-service/src/main/java/ch/batbern/events/controller/NewsletterController.java`: the subscribe Javadoc (line 60) says "Sends no mail". Add the confirm endpoint. Pattern: the unsubscribe verify/confirm at lines 77-95.
- `services/event-management-service/src/main/java/ch/batbern/events/service/RegistrationService.java:764` `confirmRegistration`, `:843` `confirmByEmail`: activate the pending subscription of that registration's address.
- Mail: the templates live in `src/main/resources/email-templates/`. The `deregistration-link-{de,en}.html` mail is the nearest pattern for a token-link mail: its sender and how it is recorded in the Mail Center outbound log. `EmailTemplateSeedService` inserts missing rows.
- Frontend: the subscribe form (grep `createNewsletterSubscriber` or `newsletter` in `web-frontend/src/services` and `components/public`). `pages/public/UnsubscribePage.tsx` is the pattern for the confirm page and its route in `App.tsx`. Organizer: `components/organizer/NewsletterSubscribers/NewsletterSubscriberFilters.tsx` gets the `pending` status.
- `scripts/ci/access-control-public.txt:49`: the subscribe row says "no double opt-in (#1288)". Add the confirm row.

## Tasks & Acceptance

**Execution:**
- [ ] Spec, migration and entity -- columns, endpoint, `pending` status
- [ ] Service, controller, both SecurityConfigs, registration hook -- the matrix
- [ ] Repository and every active reader -- confirmed only
- [ ] Templates DE/EN plus mail sender -- the confirmation mail
- [ ] Frontend: form message, confirm page and route, organizer filter, 10 locales, regenerated types
- [ ] Integration tests for each matrix row, Vitest through MSW for the form and the page, docs, access-control list

**Acceptance Criteria:**
- Given a pending subscriber and an active one, when an organizer sends a newsletter, then SES receives it for the active one only.
- Given the confirmation mail in the SesRecorder, when its link's token is posted to the confirm endpoint, then the subscriber appears as active in the organizer list.
- Given an anonymous caller, when they subscribe an active address, then the answer is 200 and the SesRecorder holds no mail.

## Verification

**Commands:**
- `./gradlew :services:event-management-service:test --tests '*Newsletter*' --tests '*Registration*' --max-workers=4` -- expected: green
- full EMS and gateway suites plus checkstyle -- expected: green (`EndpointAuthorizationGuardTest` included)
- `cd web-frontend && npx vitest run src --maxWorkers=4` on the touched folders, `npm run type-check`, `npm run lint`, `npm run check:test-ratchet`, `npm run check:i18n-keys` -- expected: green

## Review Triage Log

Quick review, 6 findings: 6 real (1 high, 3 medium, 2 low), 0 false. All patched.
- high, patch: unsubscribe paths left `confirmation_token` set, so the old confirmation link re-activated an address its owner or an organizer had removed. Every unsubscribe now clears the token.
- medium, patch: an organizer's move of a registration to confirmed left the registration's opt-in pending forever, with no mail. That path now sends the newsletter confirmation mail and does not activate, since the organizer does not prove the address.
- medium, patch: an anonymous caller using many IPs could mail one address without limit. Now at most one confirmation mail per address per hour (`confirmation_sent_at`), and the answer is still 200.
- medium (maybe-false, timing not measured), patch if simple: the template render ran in the request thread. It moves into the async step.
- low, patch: the doc sentence on the registration opt-in did not match `confirmFromRegistration`. The doc now says what the code does.
- low, patch: a test comment named a class that does not exist.
