# OWASP LLM + Agentic Security Checklist — BATbern AI Surface

**Owner:** Engineering · **Last reviewed:** 2026-06-21 (Story 15.9) · **CI:** `.github/workflows/ai-security.yml` (advisory)

This checklist maps every AI/LLM flow in BATbern to the relevant
[OWASP Top-10 for LLM Applications](https://owasp.org/www-project-top-10-for-large-language-model-applications/)
threats and records the mitigation status + the test/code that enforces it. It is the single page to
review whenever a new AI flow is added (see _How to extend_ at the bottom).

## Scope of the AI surface

All AI lives in **`event-management-service`**, gated by `batbern.ai.enabled` (env `AI_ENABLED`,
**default `false`**). The OpenAI key is `openai.api-key` (env `OPENAI_API_KEY`, `@ToString.Exclude`,
sent only in the `Authorization` header). Every AI endpoint is `@PreAuthorize("hasRole('ORGANIZER')")`
except the public read-only feature-flag check. AI output is **advisory** — an organizer reviews it
before anything is published; it never drives authorization or control flow.

## Threats considered

- **LLM01 — Prompt injection** (direct + indirect via stored event/speaker content)
- **LLM02 — Insecure output handling** (treating model output as trusted/executable)
- **LLM06 — Sensitive-information disclosure** (secrets/PII leaking into prompts or logs)
- **LLM08 — Excessive agency** (model output triggering unbounded actions)
- **Agentic** — organizer-editable prompts; any future autonomous/multi-step generation

## Flow-by-flow mapping

| # | AI flow / endpoint | Trigger & role | Attacker-influenceable input | LLM01 | LLM02 | LLM06 | LLM08 | Status | Enforced by |
|---|---|---|---|---|---|---|---|---|---|
| 1 | **Event description** `POST /events/{code}/ai/description` → `BatbernAiService.generateEventDescription` (gpt-4o) | Organizer | Event/topic title + description (organizer-entered), editable `event_description` prompt | System guard + role isolation + delimiter neutralization | Output returned as data; React auto-escapes on render | Key header-only; log is hash-only | Output advisory, not persisted automatically | ✅ Mitigated | `BatbernAiSecurityTest` (SystemGuard, OutputHandling, SecretHandling); frontend escaping |
| 2 | **Theme image** `POST /events/{code}/ai/theme-image` → `generateThemeImage` (gpt-image-1) | Organizer | Topic/event title + description, editable `theme_image` prompt | **⚠️ Partial** — the images API has **no message array**, so the `SYSTEM_GUARD` cannot be applied; the editable prompt is sent unguarded. Accepted: output is a non-textual image, not instructions. Delimiter neutralization still applies to interpolated values. | Invalid base64 / empty data → `Optional.empty()`, no S3 write | Key header-only; hash-only log | `applyThemeImage` persists only a URL under `cloudFrontDomain + "/"` (look-alike-host safe) | ⚠️ **Partial (LLM01 accepted)** | `BatbernAiSecurityTest.OutputHandling`; `AiAssistControllerSecurityTest` |
| 3 | **Abstract analysis** `POST /speakers/{id}/ai/analyze-abstract` → `analyzeAbstract` (gpt-4o, json_object) | Organizer | **Speaker-controlled abstract** + session title, editable `abstract_quality` prompt | System guard + role isolation + delimiter neutralization | `parseAbstractAnalysis` falls back to safe defaults on any malformed/empty/non-JSON response | Key header-only; hash-only log; scores not persisted | Scores are advisory only — no auto accept/reject | ✅ Mitigated | `BatbernAiSecurityTest` (SystemGuard, OutputHandling) |
| 4 | **Trending topics** `TrendingTopicsService.getTrendingTopics` (gpt-4o-mini) | Internal (blob selector) | **None** — static hardcoded prompt, no interpolation | No user input in prompt | JSON parsed; any failure → hardcoded fallback list | Key header-only; no PII in prompt | Output is a topic-string list; advisory | ✅ Mitigated | Static prompt (`TrendingTopicsService` L39–42); `ai-prompt-secret-scan.sh` |
| 5 | **Organizer-editable prompts** `GET/PUT /ai-prompts/{key}`, `POST /ai-prompts/{key}/reset` | Organizer | Organizer rewrites `prompt_text` | The editable prompt is delivered **below** the fixed system guard, in the user role — it cannot reach a system instruction (AC2) | n/a | `default_text` immutable; reset path | Editing a prompt cannot escalate privilege or trigger actions; output still advisory | ✅ Mitigated | `BatbernAiSecurityTest.SystemGuard` (edited prompt stays in user role) |
| 6 | **LinkedIn post drafts** (Story 15.8) | Organizer | event/session data, editable prompt | — | — | — | — | ⏳ **Pending (Story 15.8 — not yet built)** | Add a row + `@Tag("ai-security")` tests + extend `ai-prompt-secret-scan.sh` when 15.8 lands |

`TopicSimilarityService` performs keyword clustering only (no LLM call) — **N/A**.

## Mitigations in detail

- **Fixed system guard (LLM01/LLM08).** Every chat-completions call sends a constant
  `BatbernAiService.SYSTEM_GUARD` system message that instructs the model to treat all user-message
  content as untrusted data, never reveal/modify its instructions, never change role, and never
  execute embedded commands. The organizer-editable prompt + interpolated `{{VARS}}` sit in the
  `user` role only — they cannot become a system instruction.
- **Delimiter neutralization (LLM01).** `applyVariables` strips `{{`/`}}` from every interpolated
  value so a malicious abstract/title cannot forge another template variable. (Defense-in-depth — not
  a claim of NL-injection prevention; that is mitigated by treating output as untrusted.)
- **Untrusted output (LLM02).** Server-side, AI output is only ever returned/cached as a `String` or
  parsed into a strict typed result with safe defaults — never executed, never `eval`'d, never
  HTML-interpreted. Client-side, AI text renders through React (auto-escaping); **no AI-output render
  site uses `dangerouslySetInnerHTML`** (guarded by a frontend test).
- **Secrets/PII (LLM06).** The OpenAI key lives only in the `Authorization` header; it never enters a
  prompt or request body. `ai_generation_log` stores only a **type-prefixed cache key** (`<type>:` + 16-char truncated SHA-256 hash) of the input — never the raw
  prompt or abstract. A static scan (`scripts/ci/ai-prompt-secret-scan.sh`) trips if any
  prompt-construction code interpolates a sensitive source.
- **Excessive agency (LLM08).** AI output is advisory: abstract scores never auto-accept/reject a
  speaker; a generated image URL is persisted only after `applyThemeImage` confirms it is under our
  CloudFront domain.

## CI

`.github/workflows/ai-security.yml` is **advisory** (non-blocking, ZAP-style): it runs the
`@Tag("ai-security")` test subset (`aiSecurityTest` Gradle task) and `ai-prompt-secret-scan.sh`, and
publishes a summary. The injection/output JUnit tests **also** run in the normal
`event-management-service:test` suite, so they gate merges today (stronger than advisory). **Flip to
blocking:** once the advisory job is green for ≥1 cycle, drop `continue-on-error` from the workflow so
the static scan + any future live-probe portion also gate.

## How to extend (every new AI flow)

1. Add a row to the table above (flow, role, attacker input, the four LLM threats, status, enforcing test).
2. Route the call through `BatbernAiService.buildMessages`/`buildChatBody` so it inherits the system guard, or document why it can't (e.g. the images API has no message array).
3. Add `@Tag("ai-security")` tests for injection containment + output handling.
4. Add the new prompt-construction file to `SCAN_FILES` in `scripts/ci/ai-prompt-secret-scan.sh`.
5. Keep secrets out of prompts; log only hashes.

_See also: `docs/architecture/08-operations-security.md`._
