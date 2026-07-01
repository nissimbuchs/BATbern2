# ADR-015: Test-Data Markers & Email Send-Guards (Never Mail Real People from a Test)

**Status**: Accepted
**Date**: 2026-07-01
**Decision Makers**: Amelia (SW engineer), Nissim (PO)
**Related ADRs**: ADR-003 (Meaningful Identifiers), ADR-009 (Unified Speaker Workflow)
**Related incident**: 2026-07-01 newsletter blast (see `docs/plans/playwright-staging-hardening.md` §"Email safety")

## Context

Staging **is** production (single AWS account, live SES). On 2026-07-01 a Playwright spec
(`event-newsletter-send.spec.ts`) tagged `@gate` ran in the nightly full-`@gate` suite against
staging and — because CI `retries=2` re-ran its event-creating `beforeAll` three times — sent the
**entire ~1,050-subscriber newsletter list three real "BATPW-E2E" test newsletters** (3,175 SES
sends). The Playwright-side guards (removing `@gate`, an env `describe.skip`, and a
`@sends-real-email` runner exclusion) stop *that* spec from running there, but they are the
*first* wall only: any future test that drives a send on staging would still mail real people.

We need a **second, backend wall** that is independent of the test layer, plus a durable
convention so every test's data is recognisable to it. Two facts make this tractable:

1. **All Java outbound mail funnels through one class.** The only SES reference outside
   `shared-kernel/.../service/EmailService` is `AwsSesConfig` (the bean definition). So a single
   guard in `EmailService` covers registration, deregistration, speaker invite/reminder/acceptance,
   partner meeting invite, newsletter, slides-online, Q&A, and every future send path.
   *(Caveat: mail sent by Cognito/Lambda — e.g. auth flows — bypasses Java and is out of scope here.)*
2. **The test suites already mark their data.** Bruno and Playwright stamp canonical prefixes into
   the entities they create (for cleanup sweeps). Those prefixes render verbatim into email content
   (a newsletter subject/body carries the event title; an invite carries the speaker/event name),
   and test recipients live on reserved email domains.

## Decision

### 1. Two complementary send-guards in `EmailService` (always on, all environments)

| Guard | Fires on | Blocks | Implementation |
|---|---|---|---|
| **Recipient-domain guard** | reserved/special-use recipient domain (RFC 2606/6761/6762: `example.com`, `*.test`, `*.invalid`, `*.localhost`, `*.local`) | `ReservedEmailRecipientException` | `assertSendable(to)` → `ReservedEmailDomain` (pre-existing; extended with `.local`) |
| **Content-marker guard** | subject **or** body contains a canonical test marker, **real-send path only** (non-null `SesClient`) | `TestMarkedEmailException` + ERROR log | `assertNotTestMarked(subject, htmlBody)` → `EmailContentTestMarker` |

The content-marker guard runs **only when a live `SesClient` is present**, so local dev / test
(where `LocalEmailCapture` intercepts) is unaffected and dev-only send specs still exercise the
full pipeline end-to-end.

**Invariant:** *every test email must be caught by at least one guard* — either its content carries
a marker, or its recipient is on a reserved domain (ideally both).

A third, defence-in-depth guard lives one layer up: `NewsletterEmailService.sendNewsletter`
refuses a test-marked event **before** the async bulk loop starts, so a marked event can never fan
out to the real subscriber list even if a template omits `{{eventTitle}}`.

### 2. Canonical content markers (recognised by `EmailContentTestMarker`, case-insensitive)

| Marker | Occupies | Subsumes (case-insensitive) |
|---|---|---|
| `BATPW-E2E` | Playwright event & session **title** | `BATPW-E2E-TPL-`, `batpw-e2e-tpl-` |
| `BRUNO-TEST-` | Bruno event **code** | every `bruno-test-*` prefix: `bruno-test-session-`, `bruno-test-topic-`, `bruno-test-portal-`, additional-email `bruno-test-` |
| `BRUNOTESTCO` | Bruno/Playwright **company name** | — |
| `bruno.test` | Bruno/Playwright **username** (firstName "Bruno" + lastName "Test") | `bruno.test.N` |

**Deliberately NOT content-matched** (too short/generic → false-positive risk): the partner
company-name prefix `brtest`, the bare human names `"Bruno"` / `"Test"`, and the reserved test-event
number range (`event_number >= 10000`). Entities whose only test identity is one of these (a test
partner, a test speaker's display name) are caught instead by the **recipient-domain guard** — their
email address is on `.invalid` / `.test` / `.local` / `example.com`.

### 3. Author contract for ALL new Bruno & Playwright tests

Any test that creates data which can reach an email MUST ensure that data is caught by a guard:

- **Names / titles / codes** that render into email subject/body → prefix with a canonical marker
  above (events `BATPW-E2E`/`BRUNO-TEST-`, companies `BRUNOTESTCO`, users `Bruno`/`Test` → `bruno.test`).
- **Recipient email addresses** → use a reserved domain. The canonical choice is
  **`@e2e.batbern.invalid`** (`.invalid` is refused *before* any SES call → zero send, zero bounce).
  Never use `.local` for a fresh address (historically bounced; now also blocked) and never use a
  live domain for a synthetic recipient.
- **Never** tag a spec that drives a real outbound send `@smoke` or `@gate`; tag it
  `@sends-real-email` and hard-guard it to `TEST_ENV=development` (see the hardening plan).

## Consequences

- **Positive.** A test email can no longer reach a real recipient: content markers stop the
  newsletter-class leak (test content → real list); the recipient-domain guard stops the invite-class
  leak (test content → test address) and eliminates the `.local` bounce drip that hurt SES reputation.
  One chokepoint (`EmailService`) covers every send path, present and future.
- **Cost.** New test data must follow the marker/reserved-domain contract. A test that invents a
  novel un-marked name AND sends to a live domain would still leak — the invariant is a convention,
  enforced by review + this ADR, not by the compiler.
- **False positives.** Effectively zero: the four content markers are long and distinctive, and the
  guard fires only on the live-SES path. If a genuine email ever needed one of these literal strings,
  the send would be refused — an acceptable trade for the safety.
- **Follow-ups.** Converge the secondary test email domain `@batbern-test.ch` onto
  `@e2e.batbern.invalid`. Consider giving test human display names a distinctive token so greeting
  lines are content-guardable too (today they rely on the recipient-domain guard).
