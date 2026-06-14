# Spec — Dedicated Transactional SES Configuration Set (Option 2)

**Status:** in implementation — 2026-06-14
**Branch:** `feat/speaker-mail-all-roles-and-delivery-tracking`
**Related:** `spec-auto-participant-email-aliases-excel-export.md` §6 (forwarder config set); this spec is the backend-transactional sibling.

## Intent

Today the shared `EmailService` default config set (`batbern.ses.configuration-set-name`)
is applied to **every** backend send. In EMS it is set to `batbern-{env}-newsletter`, so
**all** transactional mail (speaker invitations, reminders, registration confirmations,
partner-meeting ICS, slides-online, etc.) rides the **newsletter** set AND flows through
the newsletter bounce pipeline `SNS → SQS → BounceProcessingService` (suppression). That
is (a) misleadingly named and (b) semantically wrong — a transient speaker-invite bounce
should not run through newsletter unsubscribe/suppression logic. Meanwhile partner-coord
and CUMS sends attach **no** config set at all (zero delivery visibility).

**Goal:** a dedicated `batbern-{env}-transactional` configuration set for all
transactional mail, with delivery tracking (CloudWatch aggregate metrics + per-recipient
SNS→logger, mirroring the forwarder set) but **no auto-suppression**. The
`batbern-{env}-newsletter` set is reserved for actual newsletter blasts and keeps its
bounce→SQS→suppression pipeline.

## Design

### Email paths after this change
| Path | Config set | Bounce handling |
|------|-----------|-----------------|
| Inbound forwarder (Lambda) | `batbern-{env}-forwarder` | CloudWatch + per-recipient SNS logger (no suppression) |
| **Transactional** (speaker/registration/partner/CUMS/venue/slides) | **`batbern-{env}-transactional`** (NEW) | CloudWatch + per-recipient SNS logger (no suppression) |
| Newsletter blasts only | `batbern-{env}-newsletter` | SNS → SQS → BounceProcessingService (suppression) |

### Mechanism (minimal footprint)
The shared `EmailService.configurationSetName` default field already applies to every
send. We **repoint that default** to the transactional set (one env var across all
service stacks). Only the newsletter path needs an explicit override.

- **shared-kernel `EmailService`** — no behavior change; default field is now semantically
  the *transactional* set. Javadoc updated (no longer "routes to BounceProcessingService").
- **`NewsletterEmailService`** — bind its own `@Value("${batbern.ses.newsletter-configuration-set-name:#{null}}")`
  and pass it **explicitly on ALL sends** (lines 533, 562 currently use the 3-arg default →
  add the explicit arg) so newsletter blasts always use the newsletter set regardless of
  the default.
- **`SlidesOnlineEmailService`** — already binds `batbern.ses.configuration-set-name`; gets
  transactional automatically. No code change.
- **`VenueCoordinationService`** — currently binds an unset `app.email.configuration-set`
  (→ no tracking today). Rebind to `batbern.ses.configuration-set-name` → transactional.

### Infrastructure (CDK)
- **`ses-stack.ts`** — add `batbern-{env}-transactional` config set + CloudWatch metrics
  event destination + SNS topic `batbern-{env}-ses-transactional-events` + a
  `transactional-event-logger` Lambda (reuses `lambda/ses-event-logger/index.ts`) + its own
  log group `/aws/lambda/batbern-{env}-transactional-event-logger` + SNS→Lambda
  subscription. Expose `transactionalConfigurationSetName`. Newsletter set + bounce SQS
  untouched.
- **`bin/batbern-infrastructure.ts`** — pass `sesStack.transactionalConfigurationSetName`
  to EMS, partner-coordination, company-management stacks; keep newsletter name → EMS.
- **`event-management-stack.ts`** — env `BATBERN_SES_CONFIGURATION_SET_NAME` = transactional
  (was newsletter); add `BATBERN_SES_NEWSLETTER_CONFIGURATION_SET_NAME` = newsletter. IAM
  config-set resource widened to `configuration-set/batbern-{env}-*`.
- **`partner-coordination-stack.ts`, `company-management-stack.ts`** — add
  `BATBERN_SES_CONFIGURATION_SET_NAME` = transactional + the config-set IAM permission.

## I/O & Edge-Case Matrix
| Scenario | Expectation |
|----------|-------------|
| Speaker invitation send | uses transactional set; delivery/bounce visible in transactional logger; NOT suppressed |
| Newsletter blast (bulk + retry + uncontacted) | uses newsletter set explicitly; bounce→suppression unchanged |
| Partner invite (partner-coord) | uses transactional set (was: no config set) |
| Additional-email verification (CUMS) | uses transactional set (was: no config set) |
| Local/test (no SES, null config set) | unchanged — null default, no config set attached |

## Tests
- `ses-stack.test.ts`: transactional config set; CloudWatch + SNS event destinations;
  logger Lambda + log group; subscription; newsletter set + bounce-SQS destination still present.
- `event-management-stack.test.ts`: both env vars set; IAM config-set ARN wildcard.
- `partner-coordination-stack.test.ts`, `company-management-stack.test.ts`: env + IAM.
- `NewsletterEmailServiceTest` (Java): verify newsletter sends pass the newsletter config set
  (explicit arg) on the bulk + retry + uncontacted paths.

## Rollout / Risk
- **Deploys to production** (staging account). The transactional set must exist before EMS
  boots with the new env (CDK dependency ordering via `addDependency(sesStack)` already present
  for EMS; add for partner/CUMS).
- **Suppression-behavior change:** transactional bounces no longer auto-suppress. This is the
  intent (the previous coupling was accidental). Newsletter suppression is preserved.
- Frozen-migration rule: N/A (no DB migrations).
