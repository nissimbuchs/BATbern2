# Open GitHub issues, triage 2026-11-15

Source of the issue sweep initiative. Every open issue created since 2026-09-15 (39), read in full with
its comments and checked against `origin/develop` f8f09ef05 by grep, plus read-only production probes
where named. Issue numbers are the ids the initiative cites (`#1217`). The issue text on GitHub is the
detailed spec; this file records what is still true of it.

## Closed on 2026-11-15 (not in scope)

| # | Why |
|---|---|
| #1221 | Fixed by #1244 (`EventMailCopies`: only verified, opted-in addresses get a CC) |
| #1225 | `dangerouslyIgnoreUnhandledErrors` removed by #1277 |
| #1209 | Done by #1224, except event-day cockpit screenshots |
| #1278 | Flagged docs rewritten in #1224 |
| #1319 | Owner: too rare, close |
| #1178, #1276 | ZAP 110009 false positive: the `path` field of the error body is the request URL. Ignore entry still to add |

## Superseded by later decisions

- **EventBridge is gone** (Story 17.2: `DomainEventPublisher` is in-process only) and **ADR-016 decision 4**
  (owner 2026-11-11) rules out any event bus or polling: a downstream reacts to an upstream change only
  through an `/internal` contract the upstream owns (`docs/architecture/module-map.md`).
  - #1120: owner chose "cascade via event" (2026-10-29) with an EventBridge rule and SQS per consumer.
    That delivery path is obsolete. The cascade goes through CUMS's `user-references` contract
    (EMS and PCS implement), like merge and primary-email.
  - #1220 item 3 and #1222 item 8 (EventBridge bus without consumers): resolved by 17.2.
  - Leftover: `infrastructure/lib/stacks/event-management-stack.ts:291` still grants `events:PutRule`
    etc.; no code uses the EventBridge SDK any more.
- #1212 items 2 (RegistrationsApi, EventTopicsApi implemented, 17.13), 3 (speakers-api removed, 17.1),
  5 (internal routes specified, 17.0b2), 6 (PII replaced by example.com): done.
- #1210, #1211, #1213: largely done by #1224; residue below.
- #1144: the deploy-time flapping is smaller (4 services since 17.2, not 6); the availability alarm is
  unchanged.

## Still open, no owner decision needed (checked)

| # | What is still true |
|---|---|
| #1229, #1232 | Both DLQ alarms still in ALARM (checked). Forwarding DLQ 4 messages (sent 2026-11-05 and 2026-11-06, BATbern60 day), replies DLQ 1 (2026-11-05). DLQ retention 14 days: gone ~2026-11-19/20. S3 raw mail kept 7 days: probably gone. No alert since, because the alarms never returned to OK |
| #1217 | `/claim-profile` is in no role's `pathAccess` (`authRules.ts`), `ProtectedRoute` redirects every user to `/dashboard`. Severity high |
| #1218 | `CustomTaskModal.tsx:52` offers `speaker_brainstorming`; `TaskTemplateEditModal.tsx:35` offers `REGISTRATION_OPEN` etc.; backend rejects them |
| #1219 | LiveControl strings hard-coded German; `App.tsx:199` passes no notifications to `BaseLayout`; `cockpitCards.ts:164` (speaker/line-up) matches before `:169` (newsletter) |
| #1222 | 1 quiet hours inverted (`NotificationService:122`); 2 `MailPipeline.DEFAULT_LOCALE = "de"` for every non-EN locale (rule: EN); 5 `gsw-BE` not selectable, `pref_language VARCHAR(2)`; 6 dates German in non-EN locales; 7 15 `@Scheduled(cron)` without `zone` |
| #1223 | Items 1-11 (spot-checked 3 "Due in {{days}} days", 7 `EventForm.tsx` `eventNumber: 1`, 8 `pages/Speakers.tsx` placeholder) |
| #1313 | `ClientErrorMetricsFilter` records before the async dispatch. Owner chose option 1 (AsyncListener) |
| #1318 | The ratchet counts `@Mock XClient` and `@MockitoBean`, not `@Mock EmailService` in unit tests |
| #1119 | A3 German default in `SpeakerReminderService:342`, `DeregistrationEmailService:122`; leftovers: commented infra E2E step in `deploy-staging.yml`, shared-kernel README type list |
| #1213 | Code: `Makefile:444` `ci-test` depends on a missing `test-coverage` target; `start-all-native.sh` hint and ports; `CLAUDE.md:712` `date -v` (BSD). Docs: cicd guide (Snyk/Sonar, 9 hits), README ports, `rate-limiting.md`, `forgot-password-flow.md`, `BACKEND_CONFIGURATION.md` describe removed tooling |
| #1211 | `scripts/update-versions.js:144,204` hard-code Gradle 8 / Node 20 |
| #1231 | `build.gradle:17,118` postgresql 42.7.7, `shared-kernel/build.gradle:81` 42.7.13, guava, httpcore5 pinned below the BOM |
| #1202 | Masking per call site only (`LoggingUtils.maskEmailsIn`); no encoder-level masking; stack traces unmasked |
| #1254 | `SharedObjectMapperConfig.java:30` enables `INDENT_OUTPUT`; production JSON is indented (seen in a probe) |
| #1288 | No confirmation mail, no template. Owner chose option 1 (double opt-in) |
| #1263 | The public `Event` schema has no `currentPublishedPhase`; the card CTA is never shown. Owner decisions in the issue (card split, anonymous visitors) |
| #1287 | Owner chose option 1. Row count in production not yet taken. EMS may not read `user_profiles` (ADR-016) |
| #1251 | Not started. Four owner decisions in the comment of 2026-11-07. Since 17.11 the `logos` table is gone (uploads in `company_user_uploads`) |
| #1320 | Owner prefers the company-scoped path; needs a production backfill of logo conversations |
| #1214 | Exemption on any `docs/` change, skipped scopes, `doc-drift-resolver.yml:241` `--base main`, `claude-code-review.yml:5` lacks `edited` |
| #1212 | 1 dead op `patchSessionQnaWindow` (spec + generated types); 4 admin settings keys unlisted; CUMS watch controllers implement no generated interface; 04-api narratives untouched since 2025-11 |
| #1210 | FR7, 14, 15, 18, 20 and §4.2 of `docs/prd-enhanced.md` carry "pending owner decision" notes |
| #1220 | 2 the four Dockerfiles build with `gradle:8.14.5`, the wrapper is 9.x; 4 no restore runbook; 5 `storage-stack.ts:92` CORS has `localhost:3000`, no `beta.batbern.ch` |
| #1152 | Owner chose the internal ALB + VPC link + trusted client-IP header, maintenance window. Also closes the direct internet path to the ALB (not filed publicly) |
| #1144 | Availability alarm fires on one 500 at night; latency alarm flaps during deploys |
| #1215 | Gateway `/api/v1/gdpr/delete` and `/export` are stubs that report success |
| #1124 | ~13 enum-like fields still free strings; the issue asks for one spec at a time |
| #1260 | Not started. Open design point: non-public materials and the CDN prefix |
| #1120 | Cascade missing (`UserService.deleteUser`, comment at :364). Row-by-row list and two judgment calls (Q&A text, speaker name in the public archive) pending |

## Owner decisions still open (all answered 2026-11-15: see the initiative's Notes)

| # | Question |
|---|---|
| #1254 | Option 1 (Spring Boot builds the mapper, CUMS `non_null` applies), 2 (drop `INDENT_OUTPUT` only), 3 (keep) |
| #1120 | Row-by-row action list; Q&A text; speaker name in the public archive; last-organizer guard; Cognito disable or delete |
| #1215 | Delete the gateway stubs, `@Profile("local")`, or 501 until #1120 |
| #1260 | Non-public files: stay in the CDN prefix with the URL never handed out, move to a private prefix, or presigned only |
| #1320 | Go for the production backfill of logo conversations |
| #1210 | Per FR (7, 14, 15, 18, 20) and §4.2: strike, defer or rewrite |
| #1212 | 04-api narratives: retire in favour of the specs, or rewrite; dead op `patchSessionQnaWindow`: delete or implement |
| #1214 | Doc-drift resolver: keep disabled, target develop without auto-merge, or retire |
| #1220 | Item 1 Fargate Spot in production: keep on-demand or enable for some services |
| #1222 | 4 WebSocket push (localhost only): fix or remove; 9 Turnstile fail-open: keep; 10 failed access-control probe: roll back or not |
| #1223 | 12 Partner sees "Change tier" on My Company: intended or not |
| #1119 | A2 coverage gates at 0.60: raise or keep; A4 frontend error reporting (Sentry or none) |
| #1152, #1144, #1220 | Maintenance window evening |

## Deferred by date

- #1070: remove `/events/current` early 2027 (Watch 3.4 uses `/public/events/current`). The spec text still
  says "Watch 2.4".
