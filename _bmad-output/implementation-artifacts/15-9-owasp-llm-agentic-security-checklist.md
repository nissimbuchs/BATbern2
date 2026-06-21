# Story 15.9: OWASP LLM + Agentic security checklist in CI

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **BATbern maintainer responsible for the platform's AI surface**,
I want **the AI/LLM endpoints mapped to their OWASP-LLM threats with mitigation status, plus automated CI tests that prove prompt-injection containment, untrusted-output handling, and no secret/PII leakage into prompts**,
so that **the AI features (event description, theme image, abstract analysis, organizer-editable prompts — and the upcoming LinkedIn-draft generator) cannot be turned into an exfiltration or injection vector, and any regression is caught by CI before it reaches production**.

> Source: `docs/prd/epic-15-post-event-2-hardening.md` → **Story 15.9 (Item #9)**.
> Epic 15 = post-Event-#2 architectural hardening. Design principle (Nissim): **no backward
> compatibility (one prod, one user) — forward solutions only.** But **staging IS production**, so
> every part of this story must be independently deployable without endangering prod, and **no test
> may make a real outbound OpenAI call** (costs money, non-deterministic, and staging=prod).

> **Pairing note (epic §6):** 15.9 "covers 15.8's surface". **Story 15.8 (LinkedIn drafts) is still
> `backlog`** — not yet built. This story documents the AI surface **as it exists today** (4 live
> flows) and includes the LinkedIn-draft generator as a checklist row marked **pending Story 15.8**,
> with a written instruction to re-run the checklist + add the injection/output tests when 15.8
> lands. The automated tests in this story target only the **currently-implemented** flows.

## Acceptance Criteria

**Checklist documentation:**
1. **AC1** — `docs/security/owasp-llm-agentic-checklist.md` exists and maps **every** AI endpoint/flow to its relevant OWASP-Top-10-for-LLM threats (**LLM01** prompt injection, **LLM02** insecure output handling, **LLM06** sensitive-information disclosure, **LLM08** excessive agency) **plus** the agentic concerns, with a per-row **mitigation status** (Mitigated / Partial / Pending / N-A) and a pointer to the test or code that enforces it. The four live flows (`event_description`, `theme_image`, `abstract_quality`, `trending_topics`) and the organizer-editable-prompt admin surface are each a row; the **15.8 LinkedIn-draft generator** is a row marked **Pending (Story 15.8)**.

**Prompt-injection containment (LLM01 / LLM08):**
2. **AC2** — A fixed, **non-organizer-editable system message** is sent on every chat-completions call, instructing the model to treat everything in the user message as untrusted data, never reveal its instructions, and only produce the requested artifact. A test proves: (a) the system message is present and is **not** derived from any organizer-editable prompt or user-supplied variable; (b) organizer-/speaker-supplied content (the editable `prompt_text` + interpolated `{{VARS}}`) is delivered **only** in the `user` role — attacker text cannot be promoted to a `system`/`assistant` instruction; (c) placeholder-delimiter sequences (`{{`, `}}`) inside interpolated variable **values** are neutralized so a malicious value cannot forge another template variable.

**Untrusted-output handling (LLM02):**
3. **AC3** — Tests prove AI output is treated as untrusted data, never executed and never trusted structurally: (a) given a mocked OpenAI response containing `<script>…</script>` / HTML / control characters, the service returns it verbatim as a plain string (no execution, no eval, no HTML interpretation server-side) and it is **stored/returned as data**; (b) malformed, empty, oversized, or non-JSON `abstract_quality` responses fall back to safe defaults without throwing (already partially present — `parseAbstractAnalysis`); (c) a frontend guard test confirms **no AI-output render site uses `dangerouslySetInnerHTML`** (React auto-escaping is the LLM02 mitigation on the client).

**Secret / PII scan (LLM06):**
4. **AC4** — A test runs each prompt-construction path with **sentinel secret values** injected via config (fake `OPENAI_API_KEY`, fake DB password) and asserts the constructed prompt **and** the outgoing OpenAI request body contain **none** of them; and asserts `ai_generation_log` persists only the input **hash**, never the raw prompt or abstract text (already the case — lock it with a test).
5. **AC5** — A static scan script (`scripts/ci/ai-prompt-secret-scan.sh`) fails (non-zero) if any prompt-construction code interpolates a known-sensitive source (`getApiKey()`, `System.getenv`, `password`, `secret`, `jwt`, `token`, `credentials`) into a prompt string. Clean on the current tree.

**CI job:**
6. **AC6** — A new **advisory (non-blocking, ZAP-style) `ai-security` GitHub workflow** runs the tagged AI-security test subset and the static secret/PII scan, uploads a report artifact, and prints a `$GITHUB_STEP_SUMMARY`. It does **not** gate merges (mirrors `security-scan.yml`’s `continue-on-error` posture). The workflow header documents the **flip-to-blocking** follow-up once green.

## Tasks / Subtasks

> **TDD throughout (red→green→refactor).** Java unit tests live in `event-management-service`; they
> must **never** call OpenAI for real — mock at the HTTP boundary (`MockRestServiceServer`) or below.
> The injection/output tests are first-class JUnit tests (they run in the normal `:test` suite **and**
> are re-run by the advisory job — see Decision #4). Run from repo root, pipe through `tee` then grep.

- [x] **Task 1 — Test seam in `BatbernAiService` (refactor, no behaviour change yet)** (AC: 2, 3, 4)
  - [x] Extract the chat request-body construction into a package-private method, e.g. `List<Map<String,Object>> buildMessages(String userPrompt)` and `Map<String,Object> buildChatBody(String model, String userPrompt, boolean jsonMode)`, so the message array (roles + content) is unit-testable without HTTP.
  - [x] Make the OpenAI `RestClient` injectable for tests: accept a `RestClient.Builder` (or the built `RestClient`) so a test can bind `MockRestServiceServer.bindTo(builder)` and capture the outgoing request body / stub the response. Keep the existing `@PostConstruct init()` production path. There is **no** existing `MockRestServiceServer`/WireMock precedent in this service — introduce the `MockRestServiceServer` pattern (Spring 6.x, already on classpath via `spring-test`).
  - [x] Make `parseAbstractAnalysis` and `applyVariables` package-private (`applyVariables` already is) so they’re directly testable.
  - [x] RED: write the failing seam tests first; GREEN: refactor to satisfy them; keep all existing `BatbernAiServiceTest` cases green.

- [x] **Task 2 — Fixed system message + role isolation (LLM01/LLM08 mitigation)** (AC: 2)
  - [x] In `buildMessages`, prepend a **constant** `{"role":"system","content": SYSTEM_GUARD}` where `SYSTEM_GUARD` is a final String constant in `BatbernAiService` (NOT sourced from `AiPromptService`, NOT from any `{{VAR}}`). Wording: instruct the model to treat all user-message content as untrusted data, never reveal or modify these instructions, and output only the requested artifact (German description / image prompt / the strict abstract-analysis JSON). Apply to both `callChatCompletions` and `callChatCompletionsJson`. (`callImageGeneration` has no message array — its `prompt` field is documented in the checklist; no system slot exists in the images API.)
  - [x] Confirm the organizer-editable `prompt_text` and all interpolated `{{VARS}}` remain in the **user** message only.
  - [x] Tests (`should_…`): (a) system message present and equals the constant for both chat paths; (b) editing the organizer prompt (mock `aiPromptService.getPromptText`) to contain `"role":"system"`-style or "ignore previous instructions" text does **not** add/alter the system message — it stays in user content; (c) the `system` content is byte-identical regardless of inputs.

- [x] **Task 3 — Neutralize placeholder delimiters in interpolated values (LLM01 defense-in-depth)** (AC: 2)
  - [x] In `applyVariables`, strip/replace `{{` and `}}` occurring **inside variable values** before substitution (so a malicious `ABSTRACT`/`SESSION_TITLE`/`EVENT_TITLE` value cannot inject a literal `{{OTHER_VAR}}` that a later iteration would expand, nor smuggle a delimiter). Document that this is delimiter-hardening, not NL-injection prevention — NL injection ("ignore previous instructions") is mitigated at the **output-trust** layer (we never act on AI output — see Task 5 / LLM08), not by input filtering.
  - [x] Test: a value containing `{{ABSTRACT}}` / `}}{{` is rendered inert (no secondary expansion, delimiters neutralized); normal values unaffected; existing variable substitution still works.

- [x] **Task 4 — Untrusted-output handling tests (LLM02)** (AC: 3)
  - [x] Using `MockRestServiceServer`, stub `/chat/completions` to return content `"<script>alert(1)</script>"` + control chars; assert `generateEventDescription` returns it **verbatim** as a String (no server-side execution/interpretation) and that it’s only ever cached/returned as data.
  - [x] Stub `/chat/completions` (json mode) with malformed JSON, empty body, non-JSON, and an oversized payload; assert `analyzeAbstract` returns safe defaults `(5,"",5,"",0,null)` and never throws (lock the existing `parseAbstractAnalysis` fallback).
  - [x] Stub `/images/generations` with invalid base64 and with empty `data`; assert `generateThemeImage` returns `Optional.empty()` (existing behaviour) and never persists garbage to S3.
  - [x] **Frontend guard test (AC3c):** add a Vitest test asserting the AI-output render sites do not use `dangerouslySetInnerHTML`. Render sites to check: `AiAssistDrawer.tsx`, `EventInfoTab.tsx`, `QualityReviewSubView.tsx`, plus public description render (`EventCard.tsx`, `HeroSection.tsx`). Prefer a source-level assertion (grep the component sources for `dangerouslySetInnerHTML`) or a render-and-assert-escaped test. If any site DOES use it for AI output, that is a finding → escape it (HALT and flag to Nissim per Open Questions).

- [x] **Task 5 — Excessive-agency assertions (LLM08)** (AC: 1, 3)
  - [x] Test/lock that AI output drives **no** authorization or control-flow: `analyzeAbstract` scores are advisory (no auto accept/reject); `applyThemeImage` rejects a URL not prefixed with the configured CloudFront domain (existing guard — add a regression test if absent). Document both in the checklist as Mitigated.

- [x] **Task 6 — Secret/PII-in-prompt runtime test (LLM06)** (AC: 4)
  - [x] Configure the service under test with sentinel secrets (e.g. `openai.api-key=SENTINEL_OPENAI_KEY_DO_NOT_LEAK`, a fake DB password property). Build each prompt (`event_description`, `theme_image`, `abstract_quality`) and capture the outgoing request body via `MockRestServiceServer`; assert it contains **none** of the sentinels (the key belongs only in the `Authorization` header, never the body/prompt).
  - [x] Assert `ai_generation_log` rows persist only `inputHash` (+ type/eventCode/timestamp) — never the raw prompt or abstract text (verify against `AiGenerationLog` domain + `logGeneration`).

- [x] **Task 7 — Static secret/PII prompt scan script** (AC: 5)
  - [x] `scripts/ci/ai-prompt-secret-scan.sh`: grep prompt-construction sources (`BatbernAiService.java`, `TrendingTopicsService.java`, `AiPromptService.java`, and — when present — the 15.8 LinkedIn generator) for interpolation of sensitive sources (`getApiKey`, `System.getenv`, `password`, `secret`, `jwt`, `token`, `credentials`) into prompt/message strings. Exit non-zero with a clear message on any hit; exit 0 clean. Make it `set -o pipefail`-safe and self-contained (no network).
  - [x] Verify it’s clean on the current tree; add a tiny negative fixture in the test (or a `--self-test` mode) proving it would catch a planted leak.

- [x] **Task 8 — JUnit tag + advisory `ai-security` workflow** (AC: 6)
  - [x] Tag the new AI-security tests with `@Tag("ai-security")` (Tasks 2–6). No `@Tag` precedent exists in this service — confirm JUnit Platform tag filtering works with the current Gradle test config; do **not** exclude the tag from the normal `:test` run (they should also gate from day one — Decision #4).
  - [x] New workflow `.github/workflows/ai-security.yml`, modeled on `security-scan.yml`:
    - Triggers: `workflow_dispatch` + `pull_request`/`push` on AI-relevant paths (`services/event-management-service/**`, `docs/security/**`, `scripts/ci/ai-prompt-secret-scan.sh`, the workflow itself) + a weekly `schedule` (de-collided cron, off the top of the hour).
    - Job is **advisory**: build shared-kernel → `publishToMavenLocal` first (per build.yml), then run `./gradlew :services:event-management-service:test --tests '*' -PincludeTags=ai-security` (or the equivalent tag-filter the Gradle config supports — verify), `continue-on-error: true`.
    - Run `scripts/ci/ai-prompt-secret-scan.sh` (advisory).
    - Upload the JaCoCo/test-results artifact (`retention-days: 30`) and write a `$GITHUB_STEP_SUMMARY` table linking the checklist doc.
    - Header comment documents the **flip-to-blocking** plan (drop `continue-on-error` once green for ≥1 cycle).

- [x] **Task 9 — Write the checklist doc** (AC: 1)
  - [x] `docs/security/owasp-llm-agentic-checklist.md` (new dir `docs/security/`). One table: columns = `AI flow / endpoint`, `Trigger & role`, `Attacker-influenceable input`, `LLM01/02/06/08 + Agentic`, `Mitigation status`, `Enforced by (test/code ref)`. Rows = the four live flows + organizer-editable-prompts admin + **LinkedIn drafts (Pending — Story 15.8)**. Include a short "How to extend when a new AI flow is added" section (add a row, tag a test `ai-security`, keep the scan green). Cross-link from `docs/architecture/08-operations-security.md`.
  - [x] Doc-drift: consult `.github/doc-drift-mappings.yml` for the AI/security paths touched; update mapped docs in the same commit or add `[no-doc]`.

- [x] **Task 10 — Full regression + coverage**
  - [x] `./gradlew :services:event-management-service:test` green (0 failures), no regression in existing AI tests; frontend Vitest green. Every AC has ≥1 test; AC2/AC3 have multiple.

### Review Findings (code review 2026-06-21)

**Patch (unresolved until applied):**
- [x] [Review][Patch] `applyThemeImage` CloudFront prefix-confusion — `startsWith(cloudFrontDomain)` admits `https://cdn.batbern.ch.evil.com/x.png` [services/event-management-service/.../controller/AiAssistController.java:117]
- [x] [Review][Patch] Hash assertion `≤32` contradicts the "SHA-256" doc claim; the stored hash is a 16-char truncated SHA-256 [BatbernAiSecurityTest.java + docs/security/owasp-llm-agentic-checklist.md]
- [x] [Review][Patch] `neutralizeDelimiters` is single-pass `.replace` — make it loop-until-stable so the "no `{{`/`}}` survive" claim holds [BatbernAiService.java#neutralizeDelimiters]
- [x] [Review][Patch] `TrendingTopicsService` sends a chat call with NO system guard — contradicts the "every chat-completions call" contract [services/event-management-service/.../service/TrendingTopicsService.java:102]
- [x] [Review][Patch] `should_notContainSecrets_when_buildingChatBody` is tautological (passes regardless of regression) — remove; the wire-level test is the real AC4 proof [BatbernAiSecurityTest.java]
- [x] [Review][Patch] `should_returnHtmlVerbatimAsData` asserts `contains` not `isEqualTo` — wrapping/appended markup would pass [BatbernAiSecurityTest.java]
- [x] [Review][Patch] Checklist marks `theme_image` LLM01 "✅ Mitigated"; image-prompt injection is actually unmitigated (images API has no system slot) — mark "Partial (accepted)" [docs/security/owasp-llm-agentic-checklist.md]
- [x] [Review][Patch] Secret-scan `grep -vE` exclusions (`*`, `// `, `secret\.`) over-match → false negatives; missing-file is a silent pass; no `trap` cleanup [scripts/ci/ai-prompt-secret-scan.sh]
- [x] [Review][Patch] `buildMessages` NPEs on a null `userPrompt` via `Map.of` — defensive guard for the documented seam [BatbernAiService.java#buildMessages]
- [x] [Review][Patch] Advisory `ai-security` job reports green if the `@Tag` is ever dropped (0 tests selected) — add a min-count guard [.github/workflows/ai-security.yml]
- [x] [Review][Patch] `callImageGeneration` has no in-code note that the missing system guard is deliberate [BatbernAiService.java#callImageGeneration]

**Deferred:**
- [x] [Review][Defer] Frontend `dangerouslySetInnerHTML` guard is a 5-file allowlist — a new AI-output render site (e.g. Story 15.8 LinkedIn preview) or a `react-markdown`+`rehype-raw` path is uncovered. Future: an ESLint rule / repo-wide lint is the right mechanism. [web-frontend/src/components/__tests__/aiOutputEscaping.test.ts]

**Dismissed as noise (5):** scan script "missing from diff" (false positive — file exists on tree, just outside my `git diff HEAD` pathspec); lossy stripping of legitimate `{{`-bearing content (documented strip-not-escape tradeoff); single-shot test-only `openAiClientBuilder` seam (no production path change); Task-8 stale planning prose vs the real `aiSecurityTest` task (cosmetic; Completion Notes are correct); "template itself not delimiter-neutralized" (a malicious organizer template is contained by the system guard at the NL layer — outside delimiter-hardening scope by design).

## Dev Notes

### Chosen approach (and why)

The epic frames 15.9 as *checklist + advisory tests + advisory CI job*. The exhaustive surface
analysis (below) shows the AI layer is **ORGANIZER-only, advisory, and already fails safe** in most
respects — but it has **one real structural gap**: every chat call sends the organizer-editable
prompt as a **single `user` message with no system message** (`BatbernAiService.callChatCompletions*`,
L232–265). So the highest-value mitigation in this story is **adding a fixed system guard message**
(Task 2) — that is the concrete substance behind the epic’s "organizer prompts can't reach
system-level instructions". Everything else is **lock-in tests** of behaviour that is already correct
(graceful JSON fallback, hash-only logging, CloudFront-prefix check) plus **defense-in-depth**
(delimiter neutralization, secret scan).

**We deliberately do NOT attempt to "prevent prompt injection" by filtering natural language.** That
is not achievable and would give false confidence. The real containment is: (1) a privileged system
message the attacker can't reach, (2) AI output is **never executed and never drives
authorization/control flow** (LLM08), and (3) output is rendered escaped (React) / stored as data
(LLM02). The tests assert *those* properties — they are deterministic and don't require a live LLM.

### AI attack surface — the map (cite when implementing)

All AI lives in **`event-management-service`**. Feature-gated by `batbern.ai.enabled`
(`AiConfig`, env `AI_ENABLED`, **default `false`**); key `openai.api-key` (env `OPENAI_API_KEY`,
`@ToString.Exclude`). All endpoints `@PreAuthorize("hasRole('ORGANIZER')")` (`AiAssistController`
L61/86/112/128, `AiPromptController` L34) — except the public read-only `GET /public/settings/features`.

| Flow | Code | Model | Editable prompt? | Attacker-influenceable input | Output use |
|---|---|---|---|---|---|
| Event description | `BatbernAiService.generateEventDescription` L101 → `callChatCompletions` (gpt-4o, temp 0.7) | `event_description` (V82, organizer-editable) | yes | Event/Topic title+description (organizer-entered) | Returned to organizer UI; cached; **not** auto-persisted; full-text indexed |
| Theme image | `generateThemeImage` L139 → `callImageGeneration` (gpt-image-1) | `theme_image` (V82, editable) | yes | Topic/Event title+desc | base64→S3 PNG; URL persisted only via `applyThemeImage` which **validates CloudFront prefix** |
| Abstract analysis | `analyzeAbstract` L183 → `callChatCompletionsJson` (gpt-4o, temp 0.3, `json_object`) | `abstract_quality` (V82, editable) | yes | **Speaker-supplied `ABSTRACT` + session title** (speaker controls own abstract!) | JSON parsed w/ safe defaults `parseAbstractAnalysis` L315; advisory scores; **not** persisted; no auto accept/reject |
| Trending topics | `TrendingTopicsService` L99 (gpt-4o-mini, temp 0.3) | **hardcoded** prompt L39–42, no vars | no | none (static) | JSON array parsed; 1h cache; hardcoded fallback list |
| Organizer-editable prompts | `AiPromptController` GET/PUT/`{key}/reset` (ORGANIZER) | `ai_prompts` table (V82: `prompt_text` mutable, `default_text` immutable) | — | organizer rewrites `prompt_text` | feeds the 3 flows above |
| **LinkedIn drafts** | **NOT BUILT — Story 15.8 (backlog)** | reuses AI-prompt machinery | yes (planned) | event/session data | draft-only `{text, imageUrls[]}` | → **Pending** row; re-run checklist when 15.8 lands |

`TopicSimilarityService` does **not** call an LLM (keyword clustering only) — N/A row.

**Already-correct, lock with tests:**
- API key never logged (`@ToString.Exclude`); error handlers log `e.getMessage()` only (L126/178/212/300).
- `ai_generation_log` stores only `inputHash` (SHA-256, `BatbernAiService.hash` L305, `logGeneration` L335) — no raw prompt/PII (LLM06 ✔).
- `parseAbstractAnalysis` (L315) catches everything → safe defaults (LLM02 ✔).
- `applyThemeImage` validates the CloudFront domain prefix before persisting (LLM08 ✔).
- Image API returns base64; invalid base64 → `Optional.empty()` (L297–302).

**The gap to fix:** no system message on chat calls (L236, L254 send only `{role:user}`); `applyVariables` (L223) does naive `.replace` with no delimiter neutralization.

### Test seam — important

There is **no `MockRestServiceServer`/WireMock precedent** in `event-management-service` tests
(`BatbernAiServiceTest` only exercises the AI-disabled / no-key short-circuits). The `RestClient` is
built privately inside `@PostConstruct init()` from `RestClient.builder()`. To assert outgoing request
bodies and stub responses **without hitting OpenAI**, Task 1 introduces injectability +
`MockRestServiceServer.bindTo(builder)` (Spring 6.x, on classpath via `spring-test`). Do not add
WireMock — `MockRestServiceServer` is lighter and sufficient.

### CI model — `security-scan.yml` is the template

Mirror `.github/workflows/security-scan.yml`: `continue-on-error: true` jobs, `workflow_dispatch` +
`schedule` (cron off the top of the hour, de-collided from ZAP `42 2 * * 1` and nightly-e2e), artifact
upload `retention-days: 30`, `$GITHUB_STEP_SUMMARY` table. Java steps must `publishToMavenLocal` the
shared-kernel first (build.yml L66) before the service test. Secrets convention is
`SCREAMING_SNAKE_CASE`; the advisory tests use **sentinel** values, so **no real `OPENAI_API_KEY` is
needed** in CI (and must not be added — tests must not call OpenAI).

### Project Structure Notes

- New dir `docs/security/` (only `docs/architecture/08-operations-security.md` + a QA gate exist today).
- New `scripts/ci/ai-prompt-secret-scan.sh` (sibling of existing `scripts/ci/*.sh`).
- New `.github/workflows/ai-security.yml`.
- All Java changes confined to `event-management-service` (service-local refactor + tests).
- Frontend: one Vitest guard test (no production frontend change unless an AI render site uses `dangerouslySetInnerHTML`).

### Testing standards summary

- TDD (red→green→refactor). Unit tests via Mockito + `MockRestServiceServer`; **never** call OpenAI.
- Naming `should_<behavior>_when_<condition>`. Coverage: business logic ≥90%. Every AC ≥1 test; AC2/AC3 multiple.
- Run from repo root, `tee /tmp/<name>.log` then grep. Single class: `./gradlew :services:event-management-service:test --tests BatbernAiSecurityTest`.
- No Bruno/E2E additions that could trigger a real AI call (staging=prod). Grep Bruno output for `Skipping invalid file` if any `.bru` is touched (it shouldn't be).

### References

- [Source: docs/prd/epic-15-post-event-2-hardening.md#Story 15.9] and §"Sequencing" (15.8+15.9 paired).
- [Source: services/event-management-service/.../service/BatbernAiService.java#L101-265 (flows), L223-230 (applyVariables), L232-265 (chat bodies — no system msg), L277-303 (image), L305-349 (hash + logGeneration)]
- [Source: services/event-management-service/.../service/TrendingTopicsService.java#L39-117 (static prompt, JSON parse)]
- [Source: services/event-management-service/.../service/AiPromptService.java; controller/AiPromptController.java#L34 (ORGANIZER); domain/AiPrompt.java; resources/db/migration/V82__add_ai_prompts_table.sql (prompt_text mutable / default_text immutable)]
- [Source: services/event-management-service/.../controller/AiAssistController.java#L61,86,112,128 (ORGANIZER; applyThemeImage CloudFront-prefix check)]
- [Source: services/event-management-service/.../config/AiConfig.java (AI_ENABLED default false; @ToString.Exclude key)]
- [Source: services/event-management-service/.../domain/AiGenerationLog.java (hash-only) ; src/test/.../service/BatbernAiServiceTest.java (existing test shape — extend, keep green)]
- [Source: .github/workflows/security-scan.yml (advisory ZAP template — continue-on-error, schedule, artifacts, summary)]
- [Source: .github/workflows/build.yml#L66 (publishToMavenLocal), L244-255 (service test invocation)]
- [Source: web-frontend/src/components/organizer/EventPage/AiAssistDrawer.tsx, EventInfoTab.tsx, SpeakerDrawer/QualityReviewSubView.tsx (AI-output render sites — LLM02 frontend guard)]
- [Source: docs/architecture/08-operations-security.md (cross-link target)]
- [Source: _bmad-output/project-context.md — TDD/Testcontainers, GlobalExceptionHandler, no-secrets-in-VC, Bruno docs{}, staging=prod]

## Resolved Decisions

1. **Add a fixed system guard message (Task 2) — IN SCOPE.** This is the concrete mitigation behind the epic’s "organizer prompts can't reach system-level instructions". It slightly changes prompt structure (a short, constant system message precedes the user content) and *may* marginally shift generated wording, but the AI feature is ORGANIZER-only and advisory (output is reviewed before use), so the risk is low and the safety gain is high.
2. **Tests assert OUR code, not the LLM.** All AI-security tests mock OpenAI (`MockRestServiceServer`) and assert deterministic properties (message roles, request body, output-as-data, fallbacks, no-secret-leak). No test makes a live OpenAI call. This is mandatory (cost + non-determinism + staging=prod).
3. **15.8 LinkedIn surface is documented as Pending, not tested here.** 15.8 isn’t built. The checklist carries a Pending row and a written instruction to add `@Tag("ai-security")` injection/output tests when 15.8 lands. Tests in this story cover the 4 live flows only.
4. **The injection/output JUnit tests gate from day one; the *job* is advisory.** Because the tests live in the normal `event-management-service:test` suite, they already gate merges via build.yml — which is *stronger* than the epic’s "advisory" minimum and the correct posture (a real injection/output regression should block). The dedicated `ai-security` **workflow** is advisory (`continue-on-error`) and adds: separate reporting, the static secret-scan, and the future home for ZAP-style live probing. "Flip-to-blocking" therefore refers to the **static-scan / future-live-probe** portion of the advisory job. Flagged so the dev agent doesn’t mistakenly *exclude* the tag from the main suite to make the job "the only place" they run.

## Open Questions

1. **AC3c — if an AI-output render site uses `dangerouslySetInnerHTML`:** the analysis did not find one, but the dev must confirm across `AiAssistDrawer`, `EventInfoTab`, `QualityReviewSubView`, and the public description renders. If one exists, escaping it is a real (small) production frontend change — HALT and confirm with Nissim before altering rendering, since it could affect intended formatting.
2. **System-guard wording language:** the `event_description` prompt is German; confirm the system guard can be English (model-instruction layer, not user-facing) — assumed yes.

## Dev Agent Record

### Agent Model Used

claude-opus-4-8[1m] (Amelia / dev-story)

### Debug Log References

- `/tmp/aisec-test.log` — first run: 18 new security tests + 5 existing `BatbernAiServiceTest` all PASSED.
- `/tmp/aisec-tag.log` / `/tmp/aisec-final.log` — `aiSecurityTest` tag-filtered task runs ONLY the 5 @Tag("ai-security") classes (18 tests, 0 fail/err).
- `/tmp/ems-full.log` — full `:services:event-management-service:test` **BUILD SUCCESSFUL in 10m 57s** (no regression from the BatbernAiService refactor).
- `/tmp/ems-checkstyle2.log` — checkstyleMain + checkstyleTest **BUILD SUCCESSFUL** (after putting each `@Mock` on its own line — AnnotationLocation rule).
- `/tmp/fe-aisec.log` — frontend Vitest LLM02 guard: 5/5 passed.
- `ai-prompt-secret-scan.sh --self-test` detects a planted leak; real-tree scan is clean.

### Completion Notes List

**Story COMPLETE — all 10 tasks implemented, all tests green, no regressions.**

- **Core mitigation (AC2, LLM01/LLM08):** added a constant `BatbernAiService.SYSTEM_GUARD` system message, prepended on every chat call via the new package-private `buildMessages`/`buildChatBody` seam. The organizer-editable prompt + interpolated `{{VARS}}` stay in the `user` role only — proven by a test that an organizer prompt rewritten to attempt a system takeover still yields exactly 2 messages with the guard untouched.
- **Delimiter hardening (AC2, Task 3):** `applyVariables` strips `{{`/`}}` from interpolated values (`neutralizeDelimiters`), so a value cannot forge another placeholder. Confirmed a forged `{{SESSION_TITLE}}` inside an earlier value is NOT expanded by the later real variable.
- **Untrusted output (AC3, LLM02):** `MockRestServiceServer`-backed tests prove script/HTML output is returned verbatim as data; malformed/empty/non-JSON abstract responses fall back to safe defaults; invalid/empty image payloads yield empty and never call S3 `putObject`. Frontend source-guard test asserts no AI-output render site uses `dangerouslySetInnerHTML` (5 sites).
- **Secrets/PII (AC4/AC5, LLM06):** test proves the API key travels only in the `Authorization` header (never the request body) and `ai_generation_log` stores a hash, never the raw prompt. Static `scripts/ci/ai-prompt-secret-scan.sh` (with `--self-test`) trips on secret interpolation; clean on the tree.
- **Test seam (AC, Task 1):** introduced the `MockRestServiceServer` pattern (no prior precedent in this service) via an injectable `RestClient.Builder` (`useClientBuilder`) used only by `init()`; production path unchanged. `parseAbstractAnalysis` made package-private. All 5 existing `BatbernAiServiceTest` cases stay green.
- **Excessive agency (AC, Task 5, LLM08):** `AiAssistControllerSecurityTest` locks the `applyThemeImage` CloudFront-prefix guard (rejects non-CloudFront + null; persists only valid CloudFront URL).
- **CI (AC6):** advisory `.github/workflows/ai-security.yml` (path/PR/push + weekly cron, `continue-on-error: true`) runs the new `aiSecurityTest` Gradle task (`useJUnitPlatform { includeTags 'ai-security' }`) + the static scan, uploads the report, prints a summary, and documents the flip-to-blocking follow-up. The JUnit tests ALSO run in the normal suite, so they gate today (Decision #4).
- **Docs (AC1):** `docs/security/owasp-llm-agentic-checklist.md` maps every flow → LLM01/02/06/08 + agentic + status + enforcing test; LinkedIn drafts (Story 15.8, backlog) is a **Pending** row with re-run instructions. Cross-linked from `08-operations-security.md`; new doc-drift mapping (`BatbernAiService.java` → checklist) added.
- **Open Question #1 resolved:** no AI-output render site uses `dangerouslySetInnerHTML` — no production frontend change needed; the guard test locks it. Open Question #2: system guard authored in English (model-instruction layer) — accepted.

### File List

**UPDATE — backend (EMS):**
- `services/event-management-service/src/main/java/ch/batbern/events/service/BatbernAiService.java` (SYSTEM_GUARD, buildMessages/buildChatBody seam, RestClient.Builder injection, delimiter neutralization, parseAbstractAnalysis package-private)
- `services/event-management-service/build.gradle` (new `aiSecurityTest` tag-filtered Test task)

**NEW — backend tests (EMS):**
- `services/event-management-service/src/test/java/ch/batbern/events/service/BatbernAiSecurityTest.java` (15 tests — AC2/AC3/AC4)
- `services/event-management-service/src/test/java/ch/batbern/events/controller/AiAssistControllerSecurityTest.java` (3 tests — LLM08)

**NEW — CI / scripts / docs:**
- `scripts/ci/ai-prompt-secret-scan.sh`
- `.github/workflows/ai-security.yml`
- `docs/security/owasp-llm-agentic-checklist.md`

**NEW — frontend test:**
- `web-frontend/src/components/__tests__/aiOutputEscaping.test.ts` (LLM02 guard, 5 sites)

**UPDATE — docs / config:**
- `docs/architecture/08-operations-security.md` (AI/LLM Security subsection + cross-link)
- `.github/doc-drift-mappings.yml` (BatbernAiService.java → checklist mapping)

## Change Log

| Date | Change |
|------|--------|
| 2026-06-21 | Story created (ready-for-dev). Comprehensive AI attack-surface analysis; scoped checklist (AC1) + system-guard mitigation (AC2) + untrusted-output tests (AC3) + secret/PII runtime test & static scan (AC4/AC5) + advisory `ai-security` workflow (AC6). LinkedIn-draft generator (Story 15.8, backlog) documented as Pending. |
| 2026-06-21 | **Implemented (all 10 tasks).** Added `SYSTEM_GUARD` + `buildMessages`/`buildChatBody` seam + delimiter neutralization in `BatbernAiService`; 18 new `@Tag("ai-security")` tests (15 service + 3 controller) via `MockRestServiceServer` (no live OpenAI call); `aiSecurityTest` Gradle task; advisory `ai-security.yml`; `ai-prompt-secret-scan.sh` (+self-test); OWASP-LLM checklist doc + `08-operations-security.md` cross-link + doc-drift mapping; frontend `dangerouslySetInnerHTML` guard. Full EMS suite BUILD SUCCESSFUL (no regression); Checkstyle clean (each `@Mock` on its own line). Story → review. |
| 2026-06-21 | **Code review (3-layer adversarial): 11 patches applied, 1 deferred, 5 dismissed.** Real fixes: (1) `applyThemeImage` prefix-confusion → `startsWith(cloudFrontDomain + "/")` + look-alike-host test (LLM08); (2) `TrendingTopicsService` now routes through `buildMessages` so the system guard covers EVERY chat call. Hardening: loop-stable `neutralizeDelimiters`; honest checklist label for `theme_image` LLM01 (Partial/accepted); secret-scan exclusions anchored to real comment lines (dropped over-broad `*`/`// `/`secret.`) + `trap` cleanup + missing-file warning; `buildMessages` null guard; advisory-job 0-test guard; `callImageGeneration` no-guard rationale comment. Test quality: removed a tautological AC4 test; `verbatim` output now asserted by equality; hash test tightened to the `desc:` + 16-char pattern. Re-ran: 19 ai-security tests green, full EMS suite re-verified, Checkstyle + scan clean. Deferred: frontend guard allowlist → future ESLint rule (`deferred-work.md`). |
