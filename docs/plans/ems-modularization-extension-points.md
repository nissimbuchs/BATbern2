# EMS Modularization & Internal Extension-Point Architecture

**Author:** Winston (System Architect)
**Date:** 2026-05-29
**Status:** Draft for review — to be sharded into BMad epics/stories
**Related:** [`multi-tenant-saas-transformation.md`](./multi-tenant-saas-transformation.md) (productization — deferred),
[`speaker-workflow-refactor.md`](./speaker-workflow-refactor.md) (Epic 11), ADR-003, ADR-004, ADR-006, ADR-009

---

## 1. Context — why this work

The platform is **live**: two events have run successfully on it, and the team ships features **weekly** to
`develop → staging (= production)`. Functionality is badly unevenly distributed:

- **EMS is a ball-of-mud** — ~400–760 Java files, 39 controllers, ~78 services, 99+ Flyway migrations (V107). It owns
  event CRUD + the 9-state event workflow, **session management, the speaker-pool kanban + 8-state speaker workflow,
  participant/registration, newsletter, notifications, topics/AI, analytics, email templates, and media**.
- The other backend services are hollow: `speaker-coordination-service` (4 files — logic migrated into EMS during
  Epic 11), `attendee-experience-service` (1-file stub).
- CUMS (users/companies) and partner-coordination are genuinely separate and **out of scope** for this work.
- The **frontend** is a static role-vertical: `navigationConfig.ts` is a hand-curated array, `App.tsx` hard-codes
  ~69 routes, `EventPage` hard-codes 8 tabs, the admin page hard-codes 9. No registry, no dynamic contribution.

Every feature change pays a "find-the-blast-radius" tax in EMS. **Goal:** convert EMS's flat, layer-organized package
tree into **vertical domain modules with enforced boundaries and an internal extension-point model**, so EMS becomes
safe to change and optional features become toggleable — the foundation for eventual productization.

### Scope decisions (locked with the product owner, 2026-05-29)

| Decision | Choice | Consequence |
|---|---|---|
| Deployment topology | **Modularize EMS internally; leave topology as-is** | Not a microservice split. Not a service-consolidation. One deployable per existing service stays. |
| Extension authors | **This team only (internal)** | Spring-native registry. No public/versioned plugin SDK, no OSGi classloader isolation. |
| Multi-tenancy | **Seam only — no infra** | One `FeatureResolver` interface + static config impl. No `tenant_id` columns. Productization is aspirational; no customer signed. |

### Anti-goals (explicit — do NOT do these)

- ❌ Split EMS (or anything) into more microservices.
- ❌ Consolidate gateway + EMS + CUMS + partner into one app as part of this work (separate, deferrable infra effort, no feature payoff).
- ❌ Build a public plugin SDK / external extension contract.
- ❌ Build row-level multi-tenancy (`tenant_id`, shared-DB isolation) for an unsigned customer (Rule of Three).
- ❌ Touch CUMS or partner-coordination internals (not the bottleneck).
- ❌ Edit, renumber, or re-own the existing 99 EMS Flyway migrations (staging IS prod; checksums are frozen).
- ❌ Big-bang: no long-lived "modularization branch" that re-slices everything then merges.

---

## 2. Target architecture (summary)

### Backend — Spring Modulith + Spring-native extension registry

Re-package `ch.batbern.events` from layer-first to **module-first**, each first-level package a Spring Modulith
module exposing `api`/`spi`, hiding `internal`:

```
ch.batbern.events
├─ EventManagementApplication.java        (@Modulithic root)
├─ eventcore/    {api,internal,web}        package-info.java @ApplicationModule
├─ sessions/     {api,internal,web}        (owns session_users — the speaker↔session join)
├─ speakerpool/  {api,spi,internal,web}    (SpeakerProvisioningHook → spi/ as reference WorkflowHook)
├─ participants/ newsletter/ notifications/ topicsai/ analytics/ media/ email-templates/
```

| Module | Core/Opt | Owns (tables) | Published API | Emits / Consumes |
|---|---|---|---|---|
| **event-core** | CORE | `events`, `event_types`, `app_settings`, `event_tasks`, `task_templates` | `EventQuery`, `EventWorkflowApi` | emits EventCreated/WorkflowTransition |
| **sessions** | CORE | `sessions`, `session_users`, `session_materials`, timing/slot config | `SessionQuery`, `SessionAssignmentApi`, `TimetableApi`, `SlotAssignmentApi` | emits SessionTimingAssigned; consumes WorkflowTransition |
| **speaker-pool** | CORE (BATbern) / OPT (product) | `speaker_pool`, `speaker_status_history`, `speaker_outreach_history`, `speaker_slot_preferences`, `session_content_history` | `SpeakerWorkflowApi`, `SpeakerPoolQuery` | emits Speaker* events; consumes SessionTimingAssigned |
| **participants** | OPT | `registrations` (+ waitlist) | `RegistrationQuery`, `WaitlistApi` | emits RegistrationCreated/WaitlistPromoted; consumes EventCreated |
| **newsletter** | OPT | `newsletter_subscribers/sends/recipients` | `NewsletterApi` | consumes RegistrationCreated |
| **notifications** | CORE (pure consumer) | `notifications` | `NotificationChannel` SPI | consumes ~everything |
| **topics-ai** | OPT | `topics`, `topic_usage_history`, `ai_prompts`, `ai_generation_log` | `TopicQuery`, `AiAssistApi` | — |
| **analytics** | OPT | derived/read-model | `AnalyticsApi` | consumes many (read) |
| **media** | OPT | `event_photos`, `event_teaser_images`, `logos` | `MediaApi`, `LogoApi` | — |
| **email-templates** | CORE (infra) | `email_templates` | `EmailTemplateApi` | inbound email |

**Hardest coupling (migrate LAST):** speaker-pool ↔ sessions share `session_users`. `SpeakerWorkflowService` today
writes `session_users` directly via `SessionUserRepository` (and wires `SpeakerPoolRepository + SessionRepository +
EventRepository`). Target: `session_users` belongs to **sessions**; speaker-pool calls
`sessions.api.SessionAssignmentApi.assignSpeakerToSession(sessionSlug, username)`. Reverse direction via
`SpeakerPoolQuery` or the existing `SpeakerWorkflowStateChangeEvent`. `SessionRepository` is referenced by ~32
classes — migrate behind a progressively-tightened Modulith allow-list.

**Enforcement:** Spring Modulith primary (`ApplicationModules.of(...).verify()` — one CI test, static bytecode
analysis, no DB). One narrow ArchUnit rule for the ADR-003 boundary: no `..api..` class depends on another module's
`@Entity`; cross-boundary references use **meaningful IDs** (`eventCode`, `username`, `sessionSlug`), never UUID FKs,
never cross-module FK constraints.

**Extension registry (generalizes the existing `SpeakerProvisioningHook`):** SPI interfaces +
`List<T>`/`ObjectProvider<T>` injection + `@ConditionalOnProperty` guards. Disabled module → its beans don't exist →
contributes nothing. SPIs: `WorkflowHook`, `TaskTemplateContributor`, `NotificationChannel`, `PublishingValidator`,
`AdminCapabilityDescriptor`. A `CapabilityRegistry` bean powers the existing `/api/v1/public/settings/features`.

**Feature toggle + tenancy seam:** `@ConfigurationProperties(prefix="batbern.modules")` →
`Map<String,ModuleConfig>{enabled}` drives the guards; `ModuleRegistry.isEnabled(moduleId)` is the source of truth.
`FeatureResolver{ boolean isEnabled(String moduleId, FeatureScope scope) }` in shared-kernel; only
`DeploymentFeatureResolver` exists now (ignores scope). Future `TenantFeatureResolver` swaps in with zero call-site
changes. No tenant column anywhere yet.

**DB / Flyway:** one PostgreSQL DB, shared schema, ownership by code (tables already prefix naturally). **Never move
V1–V107.** New module migrations continue the global counter (V108+). Cross-module refs use meaningful IDs (mirror
ADR-003 at the module edge). Fix the known violation: local `EventCreatedEvent extends DomainEvent<UUID>` → shared
`DomainEvent<String>` (eventCode).

### Frontend — feature-contribution registry ("Eclipse RCP for web", internal)

A **feature module** = `web-frontend/src/features/<name>/` exporting a `FeatureModule` manifest (`id`, `enabledWhen`,
`i18nNamespaces`, `contributions {navItems, routes, eventTabs, adminTabs, settingsPanels}`) referencing components via
`React.lazy` factories (no mass file moves up front).

- **Registry** (`src/features/registry.ts`): plain typed object, **static** manifest imports (type-safety,
  tree-shaking, HMR; reject `import.meta.glob`). Pure collector functions, sorted by `order`. No React dependency.
- **Gating** (`src/features/gate.ts`): one pure `isVisible(rule, ctx)` collapsing scattered role checks — shows iff
  `anyRole` matches **AND** `requiresFeature` on **AND** `tenantAllows`. `GateContext` from existing `useAuth()` +
  `useConfig()`; `tenant: undefined` reserved. Extend `AppConfig.features` **additively** so old `/api/v1/config`
  payloads default modules ON. Server stays the authority (`ProtectedRoute` + backend `@PreAuthorize` unchanged).
- **Hosts:** `EventPage.tsx` / `EventManagementAdminPage.tsx` render registry tabs (lazy `Suspense`, stable `order`,
  string tab-ids + numeric→id back-compat redirect for admin). `App.tsx` keeps **public routes static**, folds module
  routes in. `navigationConfig.ts` consumers render `[...static, ...registry]` deduped by path during migration.

---

## 3. Prod-safety doctrine (applies to EVERY step below)

This is a live system where **staging IS production**. Every increment must satisfy ALL of these before it ships:

1. **Additive-first.** New code is added alongside old; old paths are deleted only after the new path is proven in a
   *subsequent* step. No step both adds and removes a behavior path in the same deploy unless the removal is provably
   inert.
2. **Flag-gated behavior changes.** Any change that could alter runtime behavior ships behind a config flag
   defaulting to **current behavior**. Flip the flag in a *later, separate* deploy.
3. **Toggle-off == byte-identical.** For each module slice, CI proves that with the module flag OFF the system behaves
   exactly as the pre-slice baseline (Playwright + Bruno diff).
4. **One PR = one deployable = one rollback unit.** Reuse the existing `deploy-staging.yml` auto-rollback. No step
   depends on a not-yet-deployed step.
5. **Flyway append-only.** New migrations are forward-only (V108+). V1–V107 are frozen. Every new migration is
   idempotent/guarded and tested against a PostgreSQL clone (never H2) before deploy.
6. **Green main at every step.** Modulith `verify()`, ArchUnit, JUnit+Testcontainers, Vitest, Bruno, and Playwright
   all green. A step that can't stay green is too big — split it.
7. **Reversible by default.** Prefer changes that can be reverted by a single `git revert` + redeploy. Event-driven
   decouplings use `@TransactionalEventListener(AFTER_COMMIT)` with the flag defaulting to the synchronous path until
   proven.

A step is "done" only when it is **deployed to prod and observed healthy**, not when the PR merges.

---

## 4. Incremental roadmap — every step independently deployable & prod-safe

Notation per step: **[Add]** pure addition / **[Flag]** behind a default-off-behavior flag / **[Cutover]** removes an
old path now proven inert. Each step is a PR. BMad epics group the steps.

### EPIC 0 — Guardrails & Seams (no behavior change at all)

> Pure-upside, zero-customer-risk. Do this even if everything after is deferred. Every step here is **[Add]**.

- **0.1 [Add] Spring Modulith dependency + baseline test (report-only).**
  Add `spring-modulith-starter-test` to `services/event-management-service/build.gradle`. Add `ModularityTests` that
  calls `ApplicationModules.of(EventManagementApplication.class)` but **does NOT** `verify()` yet — instead
  `Documenter` writes the current (messy) dependency graph as a committed PlantUML baseline. *Prod impact: none
  (test-only dep). Rollback: revert.*

- **0.2 [Add] ArchUnit dependency + one disabled rule.**
  Add ArchUnit; write the ADR-003 boundary rule (`..api..` must not depend on another module's `@Entity`) but mark it
  `@ArchIgnore`/disabled with a TODO. Documents intent without failing CI. *Prod impact: none.*

- **0.3 [Add] `FeatureResolver` seam in shared-kernel.**
  Add `FeatureResolver` interface + `FeatureScope` (nullable tenantId/eventCode) + `ModuleRegistry`
  (`@ConfigurationProperties(prefix="batbern.modules")`) + `DeploymentFeatureResolver` (delegates to ModuleRegistry,
  ignores scope). **Nothing calls it yet.** Default config: all modules enabled. *Prod impact: one inert bean.
  Rollback: revert.*

- **0.4 [Add] Frontend registry scaffolding with `MODULES = []`.**
  Add `src/features/{types,gate,registry,useGateContext}.ts`. Registry getters return `[]`. Add unit tests for
  `gate.isVisible` and the empty registry. **Nothing renders from it.** *Prod impact: dead code (tree-shaken).
  Rollback: revert.*

- **0.5 [Add] Dual-source the nav + tab hosts.**
  In `navigationConfig` consumers (`AppHeader`/`NavigationMenu`/`MobileDrawer`), `EventPage.tsx`, and
  `EventManagementAdminPage.tsx`, render `[...staticItems, ...registryItems]` deduped by path/id. Registry is empty →
  **output is byte-identical**. Snapshot/RTL tests assert no change. *Prod impact: none (registry empty). Rollback:
  revert.*

**Epic 0 exit:** CI green, dependency graph published, seams in place, zero runtime change. Each of 0.1–0.5 ships to
prod on its own.

### EPIC 1 — Newsletter proof slice (the full pattern, end-to-end)

> Newsletter chosen: self-contained (~32 EMS refs), organizer-only, low-traffic, non-critical on event day, touches
> all three frontend host types (nav + route + event-tab), and is a genuine optional/productization unit.

- **1.1 [Add] Create the `newsletter` module package, move classes, keep wiring identical.**
  Move newsletter classes into `ch.batbern.events.newsletter.{api,internal,web}` + `package-info.java`
  `@ApplicationModule`. **No call-graph change** — `RegistrationService` still calls `NewsletterSubscriberService`
  directly (now via the module's `api`). Add a `@ApplicationModule(allowedDependencies=...)` permissive enough to
  pass. *Prod impact: package moves only, behavior identical. Verify: full EMS test suite + Bruno. Rollback: revert.*

- **1.2 [Add] Introduce `RegistrationCreatedEvent` + newsletter listener, alongside the direct call (dormant).**
  Add the shared event; `RegistrationService` now **both** calls the direct path **and** publishes the event; the
  newsletter `@TransactionalEventListener(AFTER_COMMIT)` is present but **guarded by a flag defaulting OFF**, so it
  no-ops. Integration test asserts the listener fires when the flag is on. *Prod impact: event published but listener
  inert. Rollback: revert.*

- **1.3 [Flag] Flip newsletter to the event path; direct call becomes conditional.**
  Separate deploy: turn the listener flag ON; make the direct `RegistrationService → newsletter` call conditional on
  the flag being OFF (so exactly one path runs). Observe prod (subscriber counts, SES sends) for a few days. *Prod
  impact: behavior path swaps, but result is equivalent and reversible by flipping the flag back. Rollback: flip flag
  (no redeploy needed).*

- **1.4 [Cutover] Remove the direct call; enforce the Modulith rule for newsletter.**
  Once 1.3 is proven healthy, delete the now-dead direct call, tighten the newsletter `@ApplicationModule`
  dependencies, and enable the ArchUnit rule scoped to `newsletter`. *Prod impact: removes proven-inert code.
  Rollback: revert.*

- **1.5 [Add] `@ConditionalOnProperty` guard on newsletter beans + Bruno contract collection.**
  Guard newsletter `@Component`s with `@ConditionalOnProperty(name="batbern.modules.newsletter.enabled",
  matchIfMissing=true)`. Add a Bruno collection pinned to the newsletter API. Add a CI job that boots EMS with
  `newsletter.enabled=false` and asserts the endpoints 404/disabled cleanly (no crash). *Prod impact: none (default
  on). Rollback: revert.*

- **1.6 [Add] Frontend `newsletter` manifest, dual-sourced.**
  Add `src/features/newsletter/manifest.ts` (nav item + route + event-tab) and register it in `MODULES`. Because the
  hosts dual-source (0.5) and dedup by path/id, the static entries and the manifest entries collapse to one. Add the
  `newsletter` feature flag (default ON) additively to `AppConfig.features`. *Prod impact: none (deduped). Verify:
  RTL host tests + Playwright. Rollback: revert.*

- **1.7 [Cutover] Remove the static newsletter entries.**
  Delete the static nav item (`navigationConfig.ts`), the `EventPage` `newsletter` tab entry, and the static route in
  `App.tsx`. Now newsletter is sourced **only** from the registry. *Prod impact: none (registry already provided
  them). Rollback: revert.*

- **1.8 [Flag] Prove toggle-off.**
  Add a CI check: with `batbern.modules.newsletter.enabled=false` (backend) and `features.newsletter=false`
  (frontend), Playwright + Bruno show newsletter fully absent and **everything else byte-identical** to the Epic-0
  baseline. This is the reusable template gate for every future slice. *Prod impact: none (default on in prod).*

**Epic 1 exit:** Newsletter is a fully self-contained, toggleable module end-to-end (backend + frontend + flag +
contract test + toggle-off proof). The pattern is now boringly repeatable.

### EPIC 2 — Generalize the extension registry

- **2.1 [Add]** Move `SpeakerProvisioningHook` → `speakerpool/spi/`; define it as the reference `WorkflowHook`. No
  behavior change (same bean, new package).
- **2.2 [Add]** Define `NotificationChannel`, `TaskTemplateContributor`, `PublishingValidator`,
  `AdminCapabilityDescriptor` SPIs + `CapabilityRegistry` bean. Existing concrete behaviors register themselves;
  collection injection is additive.
- **2.3 [Flag]** Route `NotificationService` dispatch through `List<NotificationChannel>` behind a flag; flip in a
  later deploy once parity is observed.
- **2.4 [Add]** Wire `CapabilityRegistry` into `/api/v1/public/settings/features` so the frontend menu/admin metadata
  reflects enabled modules. Default output unchanged.

### EPIC 3 — Fan out, one module per story, in risk order

Each module repeats the **Epic-1 template** (package move → event-decouple if needed → flag guard → frontend manifest
→ cutover → toggle-off proof), each as its own set of independently-deployable steps:

1. **media** (leaf, low coupling)
2. **topics-ai** (leaf-ish, read-heavy)
3. **analytics** (read-model consumer)
4. **participants** (waitlist/auto-enroll chain — convert capacity reads to `EventQuery`, newsletter coupling already
   cut in Epic 1)
5. **notifications** (listens to everything — only after the event-subscription pattern is proven on ≥3 modules)
6. **speaker-pool / sessions / event-core core-knot — LAST.** The `session_users` / `SessionAssignmentApi` surgery,
   migrating the ~32 `SessionRepository` consumers behind a progressively-tightened Modulith allow-list, one consumer
   group per PR.

### Deferred indefinitely (not part of this effort)

- Monolith consolidation (infra-only, no feature payoff).
- Any tenancy beyond the `FeatureResolver` seam — until a second customer signs (see
  `multi-tenant-saas-transformation.md`).

---

## 5. Guardrails & success metrics

| Guardrail | Mechanism | When |
|---|---|---|
| Boundary verification | Spring Modulith `verify()` | report-only Epic 0 → enforced per module from Epic 1 |
| ADR-003 module edge | One ArchUnit rule (meaningful-ID, no cross-module `@Entity`) | disabled Epic 0 → enabled per module |
| Acyclic module graph | Modulith CI test fails on new cycles | baseline Epic 0, enforced Epic 1+ |
| Module API contracts | Bruno collection per module (extends existing domain-split suite) | per slice |
| Toggle-off = zero change | Flag off → Playwright + Bruno diff vs baseline | per slice (template from 1.8) |
| Flyway append-only | Forward-only V108+; V1–V107 frozen; PostgreSQL-clone test | always |
| Per-slice rollback | One PR = one rollback unit; `deploy-staging.yml` auto-rollback | per slice |

**Success metrics (BMad retro inputs):** dependency graph acyclic (binary); EMS files-per-module balancing;
files-touched-per-feature trending down; count of "permanently-on-everywhere" toggles (a toggle that never varies was
speculative — delete it).

---

## 6. Critical files

**Backend**
- `services/event-management-service/src/main/java/ch/batbern/events/service/workflow/SpeakerProvisioningHook.java`
  (+ `NoOpSpeakerProvisioningHook`) — capability-interface + No-Op-default template to generalize.
- `services/event-management-service/src/main/java/ch/batbern/events/service/RegistrationService.java` — first
  event-driven decoupling cut (newsletter via `RegistrationCreatedEvent`).
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java` — core-knot:
  remove `SessionUserRepository`/`SessionRepository`, call `SessionAssignmentApi` (last).
- `shared-kernel/src/main/java/ch/batbern/shared/events/DomainEventPublisher.java` (+ `EventBridgeEventPublisher`) —
  the existing in-process event bus the strangler leans on.
- `services/event-management-service/build.gradle` (+ root `build.gradle`) — Spring Modulith + ArchUnit, CI wiring.

**Frontend**
- `web-frontend/src/config/navigationConfig.ts` — replaced by registry-driven nav.
- `web-frontend/src/App.tsx` — fold module routes in; keep public routes static.
- `web-frontend/src/components/organizer/EventPage/EventPage.tsx` — hard-coded `TABS` → registry tabs.
- `web-frontend/src/pages/organizer/EventManagementAdminPage.tsx` — 9 hard-coded tabs → registry tabs (string ids).
- `web-frontend/src/config/runtime-config.ts` (+ `src/contexts/useFeature.ts`, `ConfigContext.tsx`) — gating seam.
- New: `web-frontend/src/features/{types,gate,registry,useGateContext}.ts` + `src/features/newsletter/manifest.ts`.

---

## 7. Verification (per slice)

1. `./gradlew :services:event-management-service:test 2>&1 | tee /tmp/ems-test.log` — Modulith `verify()` + ArchUnit
   pass; integration tests green against Testcontainers PostgreSQL (never H2).
2. `./scripts/ci/run-bruno-tests.sh` — module contract collection passes; **zero** `Skipping invalid file` warnings.
3. `cd web-frontend && npm run test 2>&1 | tee /tmp/fe-test.log && npm run type-check && npm run lint`.
4. Toggle-off proof: set `batbern.modules.<slice>.enabled=false` + frontend flag off; Playwright
   (`chromium`/`speaker`/`partner`) + Bruno identical to pre-slice baseline.
5. New UI i18n keys present in **all 10 locales**; new module migrations forward-only (V108+), V1–V107 untouched.
6. Deploy to prod; observe healthy (logs, SES/subscriber counts where relevant) before declaring the step done.
