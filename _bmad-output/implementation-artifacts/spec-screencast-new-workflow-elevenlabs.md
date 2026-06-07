---
title: 'Screencast: button-driven kanban rework + ElevenLabs narration pipeline'
type: 'feature'
created: '2026-06-07'
status: 'done'
baseline_commit: 'ffc9a40a63ecde7190f6d2bcee3ad2562c1a34f3'
context:
  - '{project-root}/docs/plans/playwright-staging-hardening.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `screencast-event-workflow.spec.ts` (the tutorial-video recording test) still drives the speaker kanban with manual-mouse drag-and-drop, which the workflow rework (ADR-009) replaced with primary-action buttons — the spec is broken and flaky. Narration audio is produced manually in the ElevenLabs web UI and synced via hand-maintained timing files, making every workflow change expensive to re-record.

**Approach:** Rework the spec's speaker phases to the proven button-driven testid flow (log-outreach → promote → accept-on-behalf/decline → content → review, as in `speaker-pool-golden-path.spec.ts`), all through the web frontend. Replace the manual timing system with a narration manifest + a pre-step script that generates per-segment audio via the ElevenLabs API (cached, duration-measured), duration-driven waits in the test, and a post-run script that assembles the full audio track + SRT from the recorded timeline.

## Boundaries & Constraints

**Always:**
- Every workflow transition in the spec goes through a real `data-testid`'d control; the API is used only for cleanup (existing `cleanupAfterTests`). No `page.mouse` drag sequences remain.
- READY→ACCEPTED via drawer "Accept on behalf" (reason dialog) — never via send-invitation (no real emails; staging IS production).
- Promote-created users use REAL names (Nissim Buchs, Baltisar Oswald, Andreas Grütter) so the tutorial video reads authentically. Their CUMS rows are removed by Playwright global-teardown's existing `cums/users_by_email prefix=@e2e.batbern.invalid` sweep (the factory `email()` address) — name-independent, verified deleting 3 user_profiles. No per-test user delete needed. Cognito accounts are NOT removed (CUMS delete is DB-only) — documented manual sweep. [Renegotiated with Nissim 2026-06-07: Bruno/Test → real names; cleanup is the existing email sweep, not a username lever or afterAll delete.]
- German first: manifest, audio, SRT for `de`; the pipeline is language-parameterised (`SCREENCAST_LANG`, default `de`) so EN is a follow-up manifest, not a code change.
- ElevenLabs client must run without an API key via a `--fake` mode (silent placeholder MP3s with text-length-estimated durations) so the whole pipeline is testable offline.
- Segment audio cached by hash(text+voice+model); unchanged segments never re-bill.

**Ask First:**
- Making real ElevenLabs API calls (user is still verifying v3 plan/API access).
- Changing the narration tone/persona or rewriting segments other than those invalidated by the workflow change (21–28).

**Never:**
- No English audio generation in this story. No changes to production frontend components. No new heavyweight npm deps (use `fetch` + `ffprobe`/`ffmpeg`, already required by the video pipeline).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Generate, no key | `--fake`, no `ELEVENLABS_API_KEY` | Silent MP3 per segment + `narration-timings-de.json` | N/A |
| Generate, key set | `ELEVENLABS_API_KEY`+`ELEVENLABS_VOICE_ID` | MP3 per segment via `POST /v1/text-to-speech/{voice}` (model `eleven_v3`, emotion tags preserved) | Non-2xx → abort with response body; cache untouched |
| Re-generate, text unchanged | Cache hit (same hash) | Segment skipped, "cached" logged, no API call | N/A |
| Test run, timings missing | `narration-timings-de.json` absent | Test fails fast in `beforeAll` with instruction to run `npm run screencast:narration` | N/A |
| Test run completes | All markers logged | `narration-timeline-de.json` with actual start offsets + durations | Timeline flushed even on test failure (afterAll) |
| Assemble | timeline + segment MP3s + video | `narration-de.m4a` (segments placed at offsets) + `subtitles-de.srt` (emotion tags stripped) | Missing segment file → abort listing marker |

</frozen-after-approval>

## Code Map

- `web-frontend/e2e/workflows/documentation/screencast-event-workflow.spec.ts` — the test; Phases B/B.5/C rewritten
- `web-frontend/e2e/organizer/speaker-pool-golden-path.spec.ts` — donor for all button-flow locators (logOutreach L119, promoteCreatingNewUser L130, drawerStatusChange L163, content L319, review L339)
- `web-frontend/e2e/workflows/documentation/page-objects/SpeakerManagementPage.ts` — page object; gets new button-flow methods, loses drag/old-contact methods
- `web-frontend/e2e/workflows/documentation/test-data.config.ts` — speaker/outreach data; gains per-speaker promote-user data, drops `speakerSearchTerm`
- `web-frontend/e2e/workflows/documentation/screencast/timing-helper.ts` — rewritten: duration-driven waits + timeline recording
- `web-frontend/e2e/workflows/documentation/screencast/timing-config{,-de,-en}.ts`, `verify-timing.spec.ts` — superseded, delete
- `web-frontend/e2e/helpers/test-data-factory.ts` — `email()` for sweep-compatible promote users
- `web-frontend/playwright.config.ts` L212-237 — `screencast-training` project (unchanged; confirms global setup/teardown applies)

## Tasks & Acceptance

**Execution:**
- [x] `screencast/narration-manifest-de.json` — NEW (json instead of .ts so the zero-dep .mjs scripts can read it): all 36 segments; 21–24, 26, 28 rewritten for button workflow — single source of truth for TTS, SRT, and spec comments
- [x] `screencast/scripts/generate-narration-audio.mjs` — NEW pre-step script: per-segment ElevenLabs TTS (or `--fake` silence), cache `screencast/audio-cache/de/{marker}-{hash8}.mp3`, measure via `ffprobe`, write `narration-timings-de.json`, prune stale variants
- [x] `screencast/timing-helper.ts` — REWRITE: load timings JSON; `logNarration` records segment start offset; `waitForNarration` waits until start+duration(+buffer); `flushTimeline()` writes `narration-timeline-de.json`; `assertNarrationTimings()` fail-fast
- [x] `page-objects/SpeakerManagementPage.ts` — added `getCardByName`, `resolveSpeakerId`, `logOutreach`, `promoteCreatingNewUser`, `drawerStatusChange`, `enterContent`, `approveContent`; removed `dragSpeakerToColumn`/`changeStatusBatch`/`submitSpeakerContent`/`approvePresentation`; `contactSpeaker` kept `@deprecated` (still used by legacy complete-event-workflow.spec.ts — removing it would break that file's compile)
- [x] `test-data.config.ts` — added `promotedSpeakers` (firstName + lastName 'Demo'), `acceptOnBehalfReason`, `declinedSpeaker` (Daniel); `cardName` added alongside legacy fields (`displayName`/`speakerSearchTerm` retained for complete-event-workflow.spec.ts)
- [x] `screencast-event-workflow.spec.ts` — Phase B rewritten: 4× log-outreach, **3× promote** (not 4 — Daniel declines from CONTACTED so no orphan placeholder session blocks agenda publishing), 3× accept-on-behalf, 1× decline; Phase B.5/C via primary actions; `beforeAll` fails fast; `afterAll` flushes timeline
- [x] `screencast/scripts/assemble-narration.mjs` — NEW post-step: ffmpeg `adelay`+`amix` per timeline → `narration-de.m4a`; SRT from timeline+manifest (tags stripped, ≤120 chars/cue)
- [x] `web-frontend/package.json` — scripts `screencast:narration`, `screencast:assemble`
- [x] Deleted `screencast/timing-config{,-de,-en}.ts`, `verify-timing.spec.ts`, `scripts/generate-subtitles.ts`; added `screencast/.gitignore` for generated artifacts
- [x] `screencast/README.md` — rewritten for the 3-command pipeline (narration → record → assemble)
- [x] AMENDMENT (disclosed): `public/locales/*/organizer.json` ×10 — added missing `speakerDrawer.secondaryActions.acceptOnBehalf` key (UI fell back to English "Accept on behalf (skip invitation)" in the German video); translation data only, no component change

**Acceptance Criteria:**
- Given no API key, when `npm run screencast:narration -- --fake` runs, then 36 placeholder MP3s and `narration-timings-de.json` exist and the spec's `beforeAll` passes its timings check
- Given a generated cache, when the script re-runs with unchanged manifest, then zero ElevenLabs requests are made
- Given the spec source, when grepped for `page.mouse`, `dragTo`, `status-lane-READY`, then zero matches
- Given a completed (or aborted) test run, when inspecting the screencast dir, then `narration-timeline-de.json` lists every reached marker with monotonic start offsets
- Given timeline + fake segments, when `npm run screencast:assemble` runs, then `narration-de.m4a` and `subtitles-de.srt` are produced and SRT cue count == reached-marker count

## Spec Change Log

- **2026-06-07 (loop 2, intent_gap):** Acceptance auditor proved the frozen cleanup boundary factually wrong — the `bruno.test%` sweep keys on the CUMS-derived username (`firstname.lastname`), not the email, so `Nissim/Balti/Andreas Demo` users were never swept. Nissim renegotiated: promoted users are now named Bruno/Test (factory constants); the card shows the brainstorm name as caption so lookup and video readability survive. Cognito leak stays a documented manual sweep (pre-existing CUMS limitation, also true of the original mechanism). **KEEP:** everything else from the verified green run — button-driven flow, lane-containment success signals, `waitUntil: 'load'` on the Turnstile homepage (never `networkidle` there), decline-Daniel-from-CONTACTED (avoids the orphan-session agenda gate), the 3-script pipeline. Review patches applied in the same loop: fake/real audio cache separation, ElevenLabs fetch timeout+retry+body check, assemble guards (sort, clamp, empty/zero-duration/missing-text/partial-timeline), `--lang` validation, ambiguous-card guard, manifest N24 description, legacy `subtitles-*.srt` removed from git + ignored.

- **2026-06-07 (post-delivery polish, Nissim review of the rendered video):** (1) Added Vanessa Deubel as a 5th brainstorm candidate (BKW/SAP) + 2 task assignments — contacted but left CONTACTED (never promoted), showing a realistic open card. (2) **55s silence fix**: the single NARRATION_24 covering 3 accepts left ~55s of silent UI; split into per-speaker NARRATION_24/24B/24C (+24D for Daniel decline & Vanessa mention), each logged/awaited inside the accept loop. (3) Promote users renamed Bruno/Test → real names + afterAll API cleanup (see boundary). (4) NARRATION_27/28: moved the "Qualitätsprüfung" framing off the publish-speakers action onto the actual review action. (5) Archive pacing: log NARRATION_35 → 5s → tick override checkbox → 2.5s → await rest of narration → save, so the checkbox action is explained on-screen instead of flashing past. **KEEP:** the green button-driven flow, lane-containment signals, per-segment cache, Turnstile homepage wait.

## Design Notes

- Speaker-card ids are unknown (created via UI, no API): `getCardByName` filters `[data-testid^="speaker-card-"]` on visible text and extracts the id from the attribute — keeps the "UI-only" constraint while reusing id-keyed testids (`primary-action-button-{id}`).
- Timing model change: old = pre-computed cumulative waits (audio recorded first); new = video drives, audio follows. Each segment starts when the prior UI step is done AND prior narration finished; actual offsets recorded → assembly places audio to match the video exactly. No drift correction ever needed.
- `eleven_v3` keeps the existing `[excited]`-style audio tags verbatim; voice/model/key via env (`ELEVENLABS_API_KEY`, `ELEVENLABS_VOICE_ID`, `ELEVENLABS_MODEL_ID`).
- Promote provisions Cognito users out-of-band (Pattern N) — intermittently flaky on local dev; acceptable for a manually-run recording (retry = re-run), and created users are swept by global teardown.

## Verification

**Commands:**
- `cd web-frontend && npx tsc --noEmit` — expected: clean
- `cd web-frontend && npx eslint e2e/workflows/documentation --max-warnings 0` — expected: clean
- `cd web-frontend && npm run screencast:narration -- --fake` — expected: 36 MP3s + timings JSON
- `cd web-frontend && grep -nE "page\.mouse|dragTo" e2e/workflows/documentation/screencast-event-workflow.spec.ts` — expected: no matches

**Manual checks (if no CLI):**
- Full recording run (`npm run test:e2e:screencast`) against local dev env — performed by Nissim when recording the actual video; not CI-gated.

## Suggested Review Order

**Narration pipeline (the new core)**

- Single source of truth: texts + voice/model per language; 21–24B/26/28 rewritten for the button workflow
  [`narration-manifest-de.json:1`](../../web-frontend/e2e/workflows/documentation/screencast/narration-manifest-de.json#L1)
- Hash-keyed cache; fake (silent) audio isolated in its own dir so dry-runs never delete paid MP3s
  [`generate-narration-audio.mjs:84`](../../web-frontend/e2e/workflows/documentation/screencast/scripts/generate-narration-audio.mjs#L84)
- TTS call: 2-min timeout, one retry on 429/5xx, body sanity check
  [`generate-narration-audio.mjs:126`](../../web-frontend/e2e/workflows/documentation/screencast/scripts/generate-narration-audio.mjs#L126)
- v2 timing model: video drives, audio follows — waits = measured durations; actual offsets recorded
  [`timing-helper.ts:139`](../../web-frontend/e2e/workflows/documentation/screencast/timing-helper.ts#L139)
- Loop pacing: iterations spread across the narration window (never extends a UI-bound segment)
  [`timing-helper.ts:180`](../../web-frontend/e2e/workflows/documentation/screencast/timing-helper.ts#L180)
- Assembly: segments placed at recorded offsets via adelay+amix; SRT chunked + clamped per segment
  [`assemble-narration.mjs:56`](../../web-frontend/e2e/workflows/documentation/screencast/scripts/assemble-narration.mjs#L56)

**Button-driven kanban (replaces drag-and-drop)**

- New workflow methods mirroring the golden-path donor; UI-only id resolution from card testid
  [`SpeakerManagementPage.ts:172`](../../web-frontend/e2e/workflows/documentation/page-objects/SpeakerManagementPage.ts#L172)
- Lane-containment as the UI-only success signal (replaces the donor's API polling)
  [`SpeakerManagementPage.ts:204`](../../web-frontend/e2e/workflows/documentation/page-objects/SpeakerManagementPage.ts#L204)
- Phase B rewrite: 4 contacts → 3 promotes → 3 accepts (N24) → Daniel declines (N24B, split for sync)
  [`screencast-event-workflow.spec.ts:385`](../../web-frontend/e2e/workflows/documentation/screencast-event-workflow.spec.ts#L385)
- Bruno/Test promote users: names are the sweep lever (CUMS username = firstname.lastname)
  [`test-data.config.ts:151`](../../web-frontend/e2e/workflows/documentation/test-data.config.ts#L151)

**Peripherals**

- Turnstile homepage: `waitUntil: 'load'` — networkidle never fires there (30-min hang observed)
  [`screencast-event-workflow.spec.ts:110`](../../web-frontend/e2e/workflows/documentation/screencast-event-workflow.spec.ts#L110)
- Missing i18n key added in all 10 locales (drawer showed English in the German video)
  [`organizer.json:329`](../../web-frontend/public/locales/de/organizer.json#L329)
- Pipeline docs: 3 commands, plan requirements, cleanup semantics
  [`README.md:1`](../../web-frontend/e2e/workflows/documentation/screencast/README.md#L1)
