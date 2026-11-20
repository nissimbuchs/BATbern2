# AI/ML Architecture

This document describes the AI assist that runs in production. The 2025 vision for FR13
(Intelligent Content Discovery) and FR16 (Community Features) was removed from the MVP on
2025-10-01 and never built; its text was deleted from this file on 2026-11-20 (#1211) and is in
git history (`git log -- docs/architecture/09-aiml-architecture.md`).

## AI assist as built

Organizer-facing AI assistance was introduced by Story 10.16 (AI-assisted event content creation)
and was hardened by Story 15.9 (OWASP LLM checklist). It is small, synchronous and advisory: an
organizer triggers a generation, reviews the result, and decides whether to use it. There is no
recommendation engine, no model training and no ML infrastructure.

### Where it lives

All AI code is in **event-management-service (EMS)**:

| Component | Role |
|---|---|
| `config/AiConfig` | Feature switch `batbern.ai.enabled` (env `AI_ENABLED`, default `false`), OpenAI key `openai.api-key` (env `OPENAI_API_KEY`, excluded from `toString`), base URL |
| `controller/AiAssistController` | Implements the generated `AiAssistApi`: event description, theme image (generate and apply), speaker abstract analysis. Every method `@PreAuthorize("hasRole('ORGANIZER')")` |
| `controller/AiPromptController` | Implements the generated `AiPromptsApi`: list, read, update and reset the editable prompts. Organizer only (class-level `@PreAuthorize`) |
| `service/BatbernAiService` | Calls the OpenAI REST API through a Spring `RestClient`: chat completions (`chat-latest`, setting `openai.chat-model`) for description and abstract analysis, image generation (`chatgpt-image-latest`, setting `openai.image-model`) for theme images, which are stored in S3 and served via the CDN. Results are cached in a Caffeine cache |
| `service/AiPromptService` | Reads the organizer-editable prompt text from `ai_prompts` |
| `service/TrendingTopicsService` | Ten trending IT topics for topic planning (the chat model above; a reply wrapped in a code fence is unwrapped), cached for one hour, with a hard-coded fallback list when no key is configured or the call fails |
| `controller/PublicSettingsController` | `GET /api/v1/public/settings/features` returns `aiContentEnabled`, which the frontend uses to show or hide AI controls |

**Models (owner decision 2026-11-07).** Every call uses OpenAI's "latest" alias, so a retired model
cannot break a feature: `dall-e-3` was retired in May 2026 and `gpt-image-1` by November 2026, and each
time theme images failed with "model does not exist" until the code changed. The aliases come from
`AiConfig` and can be pinned per environment with `OPENAI_CHAT_MODEL` / `OPENAI_IMAGE_MODEL`.
Trade-off: an alias can change behaviour or accepted parameters without notice. `chat-latest`
accepts only the default temperature (0.3 and 0.7 return 400, measured 2026-11-07), so no request
sends one. A failed call is logged as a WARN by `BatbernAiService` / `TrendingTopicsService`.

**Theme images run in the background (2026-11-07).** An image takes about 40 s; the AWS HTTP API ends
every request at 30 s (a hard limit). `POST /events/{code}/ai/theme-image` returns 202 with a job,
`ThemeImageJobs` generates on a virtual thread and keeps the job state in memory for one hour, and the
page polls `GET …/ai/theme-image/jobs/{jobId}` every 3 s for up to 150 s. With one EMS task this is
enough; a poll that reaches another task during a rolling deploy gets 404 and the page reports an error.

Data (EMS Flyway):

- `ai_prompts` (V82, placeholders updated in V83): one row per prompt key (`event_description`,
  `theme_image`, `abstract_quality`) with the current text and the default for "reset".
- `ai_generation_log` (V77): event code, type, a hash of the input, timestamp. No prompt or
  output text is stored.

API contract: `docs/api/event-ai-api.openapi.yml` (`/ai-prompts`, `/events/{eventCode}/ai/*`,
`/events/{eventCode}/speakers/{speakerId}/abstract-analysis`). The abstract analysis loads the
speaker pool entry scoped to the event (`findByIdAndEventId`): an entry of another event answers
404 (Story 4.9, which moved it from `/speakers/{speakerId}/ai/analyze-abstract`). The Spring
gateway routes `/api/v1/ai*` and `/api/v1/events/**` to EMS (`DomainRouter`).

Runtime: the EMS CDK stack sets `AI_ENABLED=true` and injects the key from Secrets Manager
(`batbern/{env}/openai/api-key`) only when `aiEnabled` is passed, which
`bin/batbern-infrastructure.ts` does for the staging account (production). Locally AI is off
unless you set both variables.

Frontend: `useAiAssist`, `useFeatureFlags` and `aiPromptService` in `web-frontend/src`, used by
the event form, the event info tab and the speaker quality review.

### Security guards (Story 15.9)

- A constant `SYSTEM_GUARD` system message precedes every chat-completions call; organizer prompts
  and interpolated event or speaker content go only into the user message, as untrusted data.
- `{{` / `}}` delimiters are stripped from interpolated values before they reach the model.
- Output is treated as text: returned, cached or shown for review, never executed or used for
  authorization. Applying a theme image accepts only a URL under the CDN domain.
- The key is sent only in the `Authorization` header; the generation log stores input hashes only.
- The image API has no system message, so the guard cannot apply there. That residual LLM01 risk
  is accepted and documented.

Flow-by-flow mapping, tests that enforce each guard, and the checklist for adding a new AI flow:
[docs/security/owasp-llm-agentic-checklist.md](../security/owasp-llm-agentic-checklist.md). The
CI job `.github/workflows/ai-security.yml` runs on EMS changes and is advisory
(`continue-on-error: true`). Platform security in general: [08-operations-security.md](./08-operations-security.md).
