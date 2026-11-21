# Currency review: architecture-agentic-interaction spine

- Lens: was each committed technology and version checked against reality (registries, upstream source), not asserted from training data
- Reviewed: `architecture-agentic-interaction.md` (draft, 2026-11-21) and its `.memlog.md` version entries
- Date: 2026-11-21. I did not edit the repo and ran no builds. Registry data came from Maven Central and npm with curl, and upstream source from GitHub with `gh api`. I downloaded and read the `@a2ui/react` and `@a2ui/web_core` 0.15.0 tarballs.

## Verdict

The versions are real and current. But AD-11 fixes the combination "A2UI v1.0 + `@a2ui/react` 0.15.0" when the renderer's own docs still point new projects at v0.9. The spine also leaves out two practical blockers: a zod 3 vs 4 split with the frontend, and the lack of any Java-side A2UI validator. The model-client decision (AD-14) holds up.

## Findings

### F1 (High): React support for A2UI v1.0 is unproven. The package documents v0.9.

- **Checked:** `@a2ui/react` 0.15.0 (npm latest, published 2026-11-02, Apache-2.0) exports only the root plus `./v0_8` and `./v0_9`. There is no `./v1_0` entry. Its README says: "Built specifically for the A2UI v0.9 protocol" and "For new projects, it is recommended to use the **v0.9** protocol". Every example uses `version: 'v0.9'` and `new Catalog(id, 'v0.9', ...)`. On the v1.0 side, `@a2ui/web_core` 0.15.0 exports `./v1_0` and `./catalogs/basic/v1`. The react CHANGELOG for 0.14.0 says the root `A2uiSurface` renders v1.0 surfaces.
- **Checked:** the a2ui.org v1.0 spec is labelled "Current Specification" (created 2025-11-20, last updated 2026-06-08). It is **not backward compatible** with v0.9: it renames the envelopes, uses `@path`/`@call`/`@catalogId`, and treats a `theme` in `createSurface` as invalid. Since web_core 0.15.0 the wire value is `"1.0"`, with no `v` prefix.
- **I believe, not verified:** a custom React catalog on v1.0 works through the root `A2uiSurface` plus `@a2ui/web_core/v1_0` `MessageProcessor`. No example or README section shows this.
- **Fix:** keep AD-11 at A2UI v1.0, but turn the memlog assumption into a gate. The first UI story runs a spike: one custom Tailwind component, a v1.0 `createSurface`/`updateComponents`, and one `action` round trip, all through `@a2ui/react` 0.15.0 + `@a2ui/web_core` 0.15.0. If the spike fails, fall back to v0.9 explicitly in the spine instead of drifting there. Add `@a2ui/web_core` 0.15.0 to the Stack table, because the renderer cannot run without it (README: `yarn add @a2ui/react @a2ui/web_core`). Record the wire version `"1.0"` in the conventions.

### F2 (Medium): zod 3 inside A2UI, zod 4 in web-frontend. `legacy-peer-deps` hides the clash.

- **Checked:** `@a2ui/react` 0.15.0 peer-depends on **and** depends on `zod ^3.25.76`, and so does `@a2ui/web_core` 0.15.0. Its `.d.ts` types are zod 3 (`ZodEffects`). `web-frontend/package.json` has `zod ^4.1.12`. `web-frontend/.npmrc` sets `legacy-peer-deps=true`, so `npm ci` will not fail. It will nest a second zod.
- **Consequence (inferred):** the README's custom-component pattern (`z.object({... CommonSchemas.DynamicString ...})` with `import {z} from 'zod'`) mixes zod 4 objects with A2UI's zod 3 schemas. The frontend's zod 4 cannot build those schemas directly.
- **Fix:** define the BATbern components from the JSON catalog with `Catalog.fromJson(...)` (web_core ≥0.14). That also makes `docs/api/agent-ui-catalog.json` the single source, as AD-11 wants. Write no Zod in the app for catalog props. If Zod is needed, import A2UI's zod 3. Note the clash in the Stack table.

### F3 (Medium): No Java library validates A2UI. Validation in agent-service is unplanned.

- **Checked:** Maven Central has no `a2ui` artifact. Upstream ships agent SDKs for Python (`python/a2ui_agent`, `a2ui_core`, ...) and an unpublished `kotlin/agent_sdk_legacy`. The repo has no JSON Schema validator in Gradle (no networknt, for example).
- **Checked:** v1.0 catalogs are JSON Schema **draft 2020-12**. Allowed top-level keys: `$schema, $id, protocolVersion, title, description, catalogId, instructions, metadata, components, functions, $defs`. Only `catalogId` is required. Catalogs reference the shared `common_types.json`. Since web_core 0.15.0, a `$ref` to any other document is rejected.
- **Fix:** AD-11 says "agent-service validates every message against the catalog". The Stack table should name the Java JSON Schema 2020-12 validator, with its version checked on Maven Central, and the spine should say that `common_types.json` and the v1.0 envelope schemas are vendored next to `docs/api/agent-ui-catalog.json`. Without that, the Java validator and the TypeScript renderer validate against different rules.

### F4 (Medium): The action message carries client-resolved context. AD-11/AD-12 must say whose arguments count.

- **Checked:** v1.0 does define a renderer-to-agent `action` message: `{version:"1.0", action:{name, surfaceId, sourceComponentId, timestamp, context, userMessage?, metadata?}}`. A component declares `action.event.{name, context}`, and the renderer resolves the bindings in `context` (`{"@path": ...}`) **on the client** before sending. So "a button maps to a tool call" works: `event.name` = tool name, `context` = arguments.
- **Gap:** under AD-12 a card is immutable and confirmed by POST. If the server ran the tool with the `context` the client posted, any token holder could change the arguments.
- **Fix:** add one sentence to AD-12. On POST, agent-service runs the **stored** pending action. From the posted `context` it accepts only input fields the card declared as user inputs, and validates them against the catalog.

### F5 (Low): The model client checks out. One open runtime point.

- **Checked:** `com.anthropic:anthropic-java` 3.12.0 is the Maven Central `latest`/`release` (metadata updated 2026-11-20), and GitHub tag `v3.12.0` is the newest. The README says "Java 8+", so Java 21 is fine. Tool use is GA in `MessageCreateParams` (`tools`, `toolChoice`). The tool runner sits in the beta package (`models/beta/messages/ToolRunnerCreateParams`, `helpers/BetaToolRunner`), so a custom loop needs no beta API. API key: `AnthropicOkHttpClient.fromEnv()` reads `ANTHROPIC_API_KEY`, or call `.apiKey(String)` on the builder. The natural fit is ECS task-definition `secrets` from Secrets Manager into that env var.
- **Inferred, not run:** core 3.12.0 depends on Jackson 2.19.4 and kotlin-stdlib 1.9.0. Spring Boot 4.0.9 manages `jackson-2-bom` 2.21.7 and Kotlin 2.2.21, so Gradle will raise both. The SDK's runtime `checkJacksonVersionCompatibility` (read on `main`) needs major 2, at least 2.13.4, and not 2.18.1, so 2.21.7 passes. Confirm with a context-load test in the first agent-service story.
- **Fix (optional):** in AD-14, name the mechanism ("ECS secret → `ANTHROPIC_API_KEY`, client built via `fromEnv()`").

### F6 (Low): Markdown path in the renderer vs "text never as HTML"

- **Checked:** `@a2ui/react` depends on `markdown-it` and `@a2ui/markdown-it`. The basic catalog's `Text` renders Markdown through `MarkdownContext` (HTML string) or `MarkdownComponentContext`.
- **Fix:** AD-11 holds only if the BATbern catalog does not reuse the basic `Text` and the app never provides a `MarkdownContext`. Say so in AD-11 or the catalog conventions.

## Confirmed without findings

| Item | Spine | Reality (checked 2026-11-21) |
|---|---|---|
| anthropic-java | 3.12.0 | latest on Maven Central, Java 8+ |
| A2UI | v1.0 | "Current Specification" on a2ui.org, not backward compatible with v0.9 |
| @a2ui/react | 0.15.0, pre-1.0 | latest on npm. Peers: `react`/`react-dom` `^18 \|\| ^19` (repo: React ^19.3.1), so React 19 is OK |
| Custom catalog | own components | supported (`createComponentImplementation`, `Catalog`, `Catalog.fromJson`, `Catalog.derive`) |
| Style system | own Tailwind, no MUI | the protocol imposes none (v1.0 dropped theme). Basic-catalog components use `--a2ui-*` CSS variables. The optional `styles` export (v0_8 reset) only applies if imported. Repo has Tailwind 4 (`tailwindcss ^4.1.14`, `tailwind.config.js`) |
| Spring Boot | "4, as the other services" | repo `settings.gradle`: 4.0.9, `docs/versions.json`: 4.x. Central has 4.0.10 and 4.2.0. No spine change needed |
| Java | 21 | `docs/versions.json`: 21 |

## Not checked

- I did not install or run anything, so F1, F2 and F5's runtime behaviour are inference from package metadata and source.
- I read the anthropic-java Jackson check on `main`, not at tag `v3.12.0`.
- I did not read the last 16k characters of the a2ui.org v1.0 spec page.
