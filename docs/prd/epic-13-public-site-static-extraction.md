---
stepsCompleted: [step-01-validate-prerequisites, step-02-design-epics, step-03-create-stories, step-04-final-validation]
inputDocuments:
  - docs/plans/public-site-static-extraction.md
---

# Public Site Static Extraction (Astro) - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for the **Public Site Static Extraction** initiative, decomposing the requirements from the architect-authored plan (`docs/plans/public-site-static-extraction.md`, Winston, 2026-06-02) into implementable stories.

The plan supersedes the paint-and-replace prerender approach for the public surface. It bakes the (rarely-changing, ~12×/year) public content into a static Astro build, keeps one live `<RemainingSlots/>` island, and leaves the registration wizard + all auth paths in the existing SPA. Each phase is independently shippable to prod and verified on the beta canary first.

> **Source note:** This initiative had no separate formal PRD/Architecture/UX docs — the plan functions as a combined lightweight PRD + architecture, with binding decisions (§0a), risks (§5), and resolved open questions (§7). Requirements below are extracted from it.

## Requirements Inventory

### Functional Requirements

FR1: The public read-path pages (`/`, `/about`, `/archive`, `/archive/:code`, `/privacy`, `/support`) are served as static, pre-baked HTML from a new Astro `public-site/` monorepo package — content is complete on first paint with no client-side fetch required to read it.

FR2: Static public pages are baked at build time for `de` and `en`; the other 8 locales (`fr it rm es fi nl ja gsw-BE`) render client-side on top of the `de` page via the existing react-i18next + locale JSON, as a bounded long-tail degradation.

FR3: The homepage bakes the real current-event + sessions content at build time (event card, session list, speaker grid, logistics, photos, testimonials, partners), achieved via a data-as-props refactor of the presentational child components (data passed as props instead of fetched via hooks).

FR4: A `<RemainingSlots/>` hydrated island fetches live capacity from a public, CloudFront-cached endpoint (~60s TTL) and renders the correct "X slots left / waitlist / full" state plus the matching Register CTA state — only for the ≤2 active (non-archived) events. This replaces today's `CapacityIndicator`, which reads the baked `event.spotsRemaining`/`waitlistCount` fields, with a live fetch so the volatile value isn't frozen at build time.

FR4a (REALITY — added 2026-06-02 from frontend code audit): The shared `PublicNavigation` (on every public page) is auth- and role-dependent: logged-out shows Login + Join-Up; organizer/partner shows the "Portal" link to `/dashboard`; speaker shows "My Sessions" + "My Profile"; logged-in shows the user-avatar dropdown + Logout. The static pages bake the anonymous nav and a `<PublicNavigation/>` hydrated island upgrades it to the logged-in/role-specific variant after auth resolves. (The plan had listed only a "language switcher" nav island — this is the larger reality.)

FR5 (REVISED — decision 2026-06-02: keep inline, do NOT deep-link): The homepage registration experience stays the current INLINE flow, preserved as a hydrated React island cluster on the static homepage (`<RegistrationIsland client:load>`). It comprises: the per-user `RegistrationStatusBanner` (CONFIRMED/REGISTERED/WAITLIST/CANCELLED/ATTENDED), the inline `RegistrationWizard` (`inline={true}`, anonymous register + Turnstile), the `AttendeeUnregisterPanel` (for logged-in registered users), the `DeregistrationByEmailModal` (anonymous unregister by email), and the `sessionStorage.pendingRegistration` check. The island reads `useMyRegistration` + `useAuth`. This supersedes the plan's §1/§8 assumption of a static "Register" CTA deep-linking to SPA `/register/:code` — that would have been a UX regression (loss of inline register/unregister on the homepage).

FR6: A thin public, CloudFront-cacheable `GET /api/v1/events/{code}/capacity` endpoint is added to the backend (NOT a reuse of the auth-scoped `my-registration`).

FR7: The `/archive` grid bakes all ~60 events × (de+en) as static HTML — no archive API call, no pagination, no infinite-scroll JS; `useInfiniteEvents` / `fetchNextPage` / the scroll sentinel are removed.

FR8: Archive filter/sort/topics is a single small client island over the baked dataset, preserving the shareable `?q=&topics=&sort=` URLs and SEO; the topic list is baked (the `topicService` call is dropped).

FR9: Archive detail pages for the ~58 archived events are fully static (no slots island, no live data); the ≤2 active events' detail pages carry the `<RemainingSlots/>` island and are re-baked on content change. An event flips from active/dynamic to archived/static on its EVENT_COMPLETED→ARCHIVED transition.

FR10: The detail/active-event enrichment path is aligned with the already-batched list path: add `companyLogoUrl` to `SessionSpeakerResponse` and reuse the existing `findUserPortraitsByUsernames` batch query (NO new endpoint) so the static bake of active-event pages reads portrait + logo URLs from props.

FR11: Newsletter+Turnstile and CountdownTimer become lazy `client:` islands (loaded only on interaction/visibility).

FR12: Backend auto-publish events (speakers @30d, agenda @14d, EVENT_LIVE, EVENT_COMPLETED→ARCHIVED) trigger a CI rebuild → S3 sync → CloudFront invalidation via `repository_dispatch` / workflow.

FR13: An organizer-facing "Republish public site" manual button triggers the same rebuild + deploy pipeline as a manual override.

FR14: The SPA retains all app/auth paths unchanged (`/register*`, `/login`, `/auth/**`, `/organizer*`, `/speaker*`, `/partner*`, `/admin*`, etc.) via CloudFront cache behaviors routing to the SPA origin/prefix.

FR15: The legacy prerender approach is retired — `scripts/prerender.mjs` + the paint-and-replace logic are deleted, the public route components are removed from the SPA bundle (shrinking it), and the sitemap is updated.

### NonFunctional Requirements

NFR1 (Performance): The public read path ships ~0 framework JS to read content; homepage TBT collapses and Lighthouse/PSI shows a large score jump (consistent with the PageSpeed 80+ goal). Performance is measured on beta before each prod promotion.

NFR2 (SEO / Crawlability): Real content is present in view-source pre-JS for `de` + `en` across all public pages (previously invisible to crawlers in 9 of 10 locales).

NFR3 (Reliability / Guardrail): If the build-time data fetch fails or returns empty, the build FAILS and the last-good deploy stays live — a blank/stale public site is never published. A content-assertion test verifies prerendered routes contain expected markers so a silently-broken build cannot ship.

NFR4 (Design Consistency): The static site shares the SPA's `tailwind.config.js` + design tokens + Radix/Tailwind UI primitives (via a shared path/package) so the two artifacts never drift visually.

NFR5 (i18n Coverage): All 10 public locales work — `de`+`en` first-class and baked (flash-free); the 8 long-tail locales render client-side (bounded paint-and-replace flash). Satisfies the CLAUDE.md "all 10 public locales work" rule while keeping de/en first-class.

NFR6 (Build Cadence / Cost): Builds take seconds and run ~12×/year; reuse the existing S3 + CloudFront (no new infra tier).

NFR7 (Render / DOM Cost): The deep archive DOM (~250 session rows across 60 expanded cards) uses `content-visibility: auto` + `contain-intrinsic-size` per card so the browser skips layout/paint of off-screen cards while keeping them crawlable in the DOM — zero-JS.

NFR8 (Prod-Safe Incremental Delivery): Each phase is independently shippable to prod and is verified on the beta canary first (Lighthouse/PSI + click-through), then promoted.

### Additional Requirements

- **Astro stack:** Astro + `@astrojs/react` + Tailwind, reusing the existing MUI-free public React components (React + Tailwind + Radix) as islands via `client:` directives. This is a move + feed-data-as-props migration, not a rewrite.
- **Two-origin CloudFront routing:** static site is the default origin for public paths; the SPA serves app paths via CloudFront cache behaviors / a CloudFront function mapping app paths to the SPA `index.html` fallback. The exact layout (one bucket with path prefixes vs two origins) is settled empirically in Phase 1 (resolves open Q#3).
- **Build-time prod data fetch:** the static build fetches prod data from `api.batbern.ch` at build time (deliberately giving up "build once" for the public site — correct for a content artifact). The event/sessions/archive endpoints used are already public + unauthenticated, so no new auth surface is needed for the build fetch.
- **Beta canary as staging ground:** `beta.batbern.ch` (shared prod backend) is the verification ground for the whole pipeline — publish each phase to beta, run Lighthouse/PSI + click-through, then promote to prod.
- **Optional nightly safety rebuild** (Phase 4) as a backstop.
- **Auth context for islands (REALITY — added 2026-06-02):** the auth-dependent islands (`PublicNavigation`, the registration cluster, the per-card archive status, capacity CTA state) read `useAuth()` / `useMyRegistration()`, which require the Amplify/Cognito `AuthContext` provider mounted in the island tree. The Astro build must bootstrap a shared auth provider for these `client:` islands (e.g. a shared provider wrapper) so they hydrate with real auth state, without forcing the whole SPA to boot. This is the main hydration-architecture risk and is worth a spike inside the homepage story.
- **`LanguageSync` parity:** the SPA's non-rendering `LanguageSync` fetches a logged-in user's language preference and switches i18n after auth resolves. For baked de/en pages, logged-out visitors see the baked locale; logged-in users may see a post-hydration locale switch (same paint-and-replace trade-off, scoped to authenticated users).

### UX Design Requirements

UX-DR1: `<RemainingSlots/>` island UI — renders "X slots left", "waitlist", or "full" plus the correct Register CTA state for the ≤2 active events; small hydrated island fed by the live `/capacity` endpoint.

UX-DR1a: Role-aware `PublicNavigation` island — bake the anonymous nav (Login + Join-Up), hydrate to the logged-in/role-specific variant (Portal / My Sessions / My Profile / avatar dropdown + Logout). Must hydrate without layout shift (avoid CLS when the nav swaps).

UX-DR1b: Registration island cluster — inline `RegistrationWizard`, `AttendeeUnregisterPanel`, per-user `RegistrationStatusBanner`, `DeregistrationByEmailModal`, and the pending-registration success state. Preserves the current inline homepage registration UX exactly (no regression).

UX-DR2: Language switcher island — `de`/`en` are baked and flash-free; the 8 long-tail locales render client-side on top of the `de` page (a scoped, bounded paint-and-replace flash). Reuses react-i18next + existing locale JSON.

UX-DR3: Archive search/filter/sort island over the baked array — preserves continuous scroll (no page controls) and the shareable `?q=&topics=&sort=` URLs.

UX-DR4: Countdown timer island (lazy `client:`).

UX-DR5: Newsletter + Turnstile lazy island — loads only on interaction.

UX-DR6: PrivacyPage `new Date().getFullYear()` resolved at build time; a tiny re-render island corrects the displayed year if a year boundary is crossed (benign).

UX-DR7: Archive images stream lazily (already handled today via the CDN resize Lambda + `loading="lazy"` + reserved width/height on `SpeakerDisplay` portrait/logo and `EventCard` hero); off-screen cards don't render until scrolled near.

### FR Coverage Map

FR1: Epic 13 — Static Astro public-site package serves the public read-path pages
FR2: Epic 13 — de+en baked; 8 long-tail locales client-rendered
FR3: Epic 13 — Homepage baked-data via data-as-props refactor
FR4: Epic 13 — Live `<RemainingSlots/>` island (replaces baked CapacityIndicator field)
FR4a: Epic 13 — Role-aware `PublicNavigation` hydrated island (every page)
FR5: Epic 13 — Inline registration island cluster (wizard + unregister + status banner + deregister modal)
FR6: Epic 13 — Thin public, CloudFront-cacheable `/capacity` endpoint
FR7: Epic 13 — Archive grid bakes all ~60 events; infinite-scroll JS removed
FR8: Epic 13 — Archive filter/sort/topics client island over baked data; shareable URLs
FR9: Epic 13 — Archive detail static for ~58 archived; ≤2 active carry slots island + re-bake
FR10: Epic 13 — Detail-path enrichment aligned (companyLogoUrl + batch query, no new endpoint)
FR11: Epic 13 — Newsletter+Turnstile + CountdownTimer lazy `client:` islands
FR12: Epic 13 — Auto-rebuild on backend publish events (repository_dispatch → build → S3 → invalidate)
FR13: Epic 13 — Organizer "Republish public site" manual button
FR14: Epic 13 — SPA retains all app/auth paths via CloudFront behaviors
FR15: Epic 13 — Retire prerender.mjs + strip public routes from the SPA bundle

## Epic List

### Epic 13: Public Site Static Extraction (Astro)

Public website visitors — and crawlers — get an instantly-readable, fully-baked static public site (homepage, archive grid, archive detail, about, privacy, support) in `de` + `en` with ~0 framework JS on the read path, so first paint shows the real answer to "what's the next event · what sessions · can I register · what past events." The volatile and auth-dependent surfaces stay live via small hydrated islands — a live slots/capacity indicator, the role-aware navigation, and the existing inline registration flow — and the registration wizard's stateful path remains in the SPA. The site rebuilds automatically (~12×/year) on backend content-publish events plus a manual organizer override, with a fail-build-keep-last-good guardrail so a blank or stale public site can never ship. Each phase is independently shippable to prod and verified on the beta canary first. The initiative finally retires the paint-and-replace prerender that capped TBT.

**FRs covered:** FR1, FR2, FR3, FR4, FR4a, FR5, FR6, FR7, FR8, FR9, FR10, FR11, FR12, FR13, FR14, FR15
**NFRs addressed:** NFR1–NFR8 (performance, SEO, build guardrail, design-token sharing, 10-locale coverage, build cadence, render cost, prod-safe-incremental)
**UX-DRs addressed:** UX-DR1, UX-DR1a, UX-DR1b, UX-DR2–UX-DR7

## Epic 13: Public Site Static Extraction (Astro)

Public website visitors — and crawlers — get an instantly-readable, fully-baked static public site (homepage, archive grid, archive detail, about, privacy, support) in `de` + `en` with ~0 framework JS on the read path. The volatile and auth-dependent surfaces stay live via small hydrated islands; the registration wizard's stateful path remains in the SPA. The site rebuilds automatically (~12×/year) on backend content-publish events plus a manual organizer override, with a fail-build-keep-last-good guardrail. Each story is independently shippable to prod and verified on the beta canary first. The epic finally retires the paint-and-replace prerender that capped TBT.

> **Story ordering is forward-only** — each story builds only on earlier ones. Suggested implementation order is 13.1 → 13.7. (13.3 is a backend prerequisite for 13.4; it may run in parallel with 13.1/13.2 but must land before 13.4.)
>
> **Architecture:** the two flagged design unknowns are resolved in **ADR-011** (`docs/architecture/ADR-011-public-site-static-architecture.md`): (1) CloudFront routing — one bucket / one distribution, extend the existing `routerFunction` Function with an `/archive/:code` prefix rule (reject two origins); (2) island auth-bootstrap — same-origin Cognito session sharing + one interactive-shell React root for the nav + registration cluster, public-only widgets as separate lazy islands. Stories 13.2 and 13.4 implement these.

### Story 13.1: Scaffold the `public-site/` Astro package

As a developer on the platform team,
I want a new Astro `public-site/` package that shares the SPA's Tailwind config and design tokens and can render a reused public React component to static HTML,
So that we have a proven, visually-consistent foundation to migrate public pages onto — a move, not a rewrite — with zero production impact.

**Acceptance Criteria:**

**Given** the monorepo
**When** the `public-site/` package is created with Astro + `@astrojs/react` + Tailwind and `npm run build` is run
**Then** it produces a `dist/` containing static HTML
**And** no existing SPA build, deploy, or route is affected (zero production impact, no deploy step in this story)

**Given** the SPA's existing `tailwind.config.js` + design tokens
**When** the Astro package builds
**Then** it consumes the same config and tokens via a shared path/package
**And** there are no duplicated/forked token values that could drift (NFR4)

**Given** an existing MUI-free public React component (React + Tailwind + Radix)
**When** it is rendered inside an Astro page
**Then** it renders to correct static HTML at build time
**And** a `client:` directive successfully hydrates it as an island (proven with one trivial example)

**Given** CI
**When** the package is added
**Then** build, lint, and type-check wiring runs for `public-site/`
**And** no deploy/publish workflow is added yet

### Story 13.2: Static-only pages (`/about`, `/privacy`, `/support`) + two-origin CloudFront routing + role-aware navigation island

As a public visitor or search-engine crawler,
I want `/about`, `/privacy` and `/support` served as pre-baked static HTML with a working role-aware navigation,
So that these zero-data pages load instantly with real content in the page source — proving the end-to-end static pipeline and CloudFront routing at the lowest possible risk.

**Acceptance Criteria:**

**Given** the three page components moved into Astro
**When** the site builds
**Then** `/about`, `/privacy` and `/support` render as static HTML baked for `de` and `en`
**And** view-source shows the real content before any JavaScript runs (NFR2)

**Given** the 8 long-tail locales (`fr it rm es fi nl ja gsw-BE`)
**When** a visitor selects one
**Then** the page re-renders client-side on top of the `de` page via the existing react-i18next + locale JSON (language-switcher island)
**And** `de`/`en` remain baked and flash-free (UX-DR2, NFR5)

**Given** `PublicNavigation` is auth- and role-dependent
**When** a static page loads
**Then** the anonymous navigation (Login + Join-Up) is baked into the HTML
**And** a hydrated navigation island upgrades it to the logged-in/role-specific variant (Portal / My Sessions / My Profile / avatar dropdown + Logout) after auth resolves, without layout shift (FR4a, UX-DR1a)

**Given** PrivacyPage's `new Date().getFullYear()`
**When** the site is built
**Then** the year is resolved at build time
**And** a tiny re-render island corrects the displayed year if a year boundary is crossed (UX-DR6)

**Given** the shared CloudFront distribution (per ADR-011: one bucket / one distribution)
**When** the existing `routerFunction` CloudFront Function is extended with the new public-route rewrites
**Then** `/about`, `/privacy`, `/support` serve their baked `/<route>/index.html` objects from the same bucket
**And** all app/auth paths (`/register*`, `/login`, `/auth/**`, `/organizer*`, `/speaker*`, `/partner*`, `/admin*`) still fall through to the SPA `/index.html` unchanged (FR14)
**And** the Function is deployed ahead of the baked content (missing objects fall through `404→/index.html` to the SPA — fail-safe, ADR-011 D8)

**Given** the Astro `public-site/dist/` and the SPA `web-frontend/dist/` share one bucket (ADR-011 D3)
**When** the deploy syncs both trees
**Then** they merge with disjoint keys (homepage at `/home/index.html`; SPA `/index.html` stays the fallback)
**And** the `BucketDeployment` `prune` is scoped so neither tree wipes the other (the one real routing hazard — proven on beta first)

**Given** a beta-canary publish
**When** verified
**Then** Lighthouse runs clean with no console errors and real content pre-JS
**And** only then is it promoted to prod (NFR8)

### Story 13.3: Public capacity endpoint + detail-path speaker enrichment alignment

As a developer on the platform team,
I want a thin public, CloudFront-cacheable capacity endpoint plus the active-event detail enrichment aligned to the already-batched list path,
So that the homepage slots island has a live source and the static bake of active-event pages already carries portrait + company-logo URLs.

**Acceptance Criteria:**

**Given** an event code
**When** `GET /api/v1/events/{code}/capacity` is called unauthenticated
**Then** it returns the slots-remaining / waitlist-count / full state for that event
**And** it is CloudFront-cacheable with a short TTL (~60s)
**And** it does NOT reuse the auth-scoped `my-registration` (FR6)

**Given** the detail DTO `SessionSpeakerResponse` currently omits `companyLogoUrl` and enriches portraits per-speaker over HTTP
**When** the detail path is aligned
**Then** `SessionSpeakerResponse` includes `companyLogoUrl`
**And** it reuses the existing `findUserPortraitsByUsernames` batch query (no new endpoint, no per-speaker N+1) (FR10)

**Given** the contract-first workflow (ADR-006)
**When** the endpoints/DTOs change
**Then** the OpenAPI spec is updated before implementation and frontend/back-end types are regenerated and committed

**Given** TDD
**When** the changes are implemented
**Then** integration tests using PostgreSQL via Testcontainers cover the capacity endpoint and the enriched detail payload
**And** the capacity endpoint follows ADR-003 (looked up by `event_code`, no UUID exposure)

### Story 13.4: Homepage baked-data + hydrated island cluster

As a public visitor or search-engine crawler,
I want the homepage to show the real current event, sessions, speakers and logistics as baked static HTML, with live slots and the existing inline registration preserved as hydrated islands,
So that the page is readable on first paint with ~0 framework JS while the volatile and auth-dependent pieces stay correct.

**Acceptance Criteria:**

**Given** the homepage presentational children (event card, session list, speaker grid, logistics, photos, testimonials, partners)
**When** they are refactored to receive build-time data as props instead of via hooks
**Then** they render to static HTML at build for `de` and `en` (FR3)
**And** view-source shows the real event content before any JavaScript runs (NFR2)

**Given** the volatile slots value
**When** the homepage loads
**Then** the `<RemainingSlots/>` island hydrates and fetches the live `/capacity` endpoint, rendering "X slots left / waitlist / full" + the correct Register CTA state — only for active events (FR4, UX-DR1)
**And** it replaces the previously-baked `CapacityIndicator` field so the value is never frozen at build time

**Given** the current inline homepage registration UX
**When** the registration island cluster hydrates
**Then** it preserves the UX exactly: the per-user `RegistrationStatusBanner`, the inline `RegistrationWizard` (+Turnstile), the `AttendeeUnregisterPanel`, the `DeregistrationByEmailModal`, and the `sessionStorage.pendingRegistration` success state — reading `useMyRegistration` + `useAuth` (FR5, UX-DR1b)
**And** there is no regression versus today's behavior across anonymous / logged-in-not-registered / logged-in-registered states

**Given** the auth-reading surfaces (role-aware nav + hero registration cluster) (per ADR-011 D6)
**When** they hydrate
**Then** they mount as ONE interactive-shell React root (`client:load`) owning a minimal `ConfigProvider → QueryClientProvider → AuthProvider` subset + synchronous i18n — reading the same-origin Cognito session for free, with no `AuthContext` refactor and one session-restore
**And** anonymous visitors/crawlers load no Amplify (`hasCognitoSession()` + lazy-Amplify gate, ADR-011 D5/D7)
**And** the single-root TBT trade-off is measured on beta in this story's spike, with the many-islands-+-singleton-store split as the documented fallback (ADR-011 D6)

**Given** the public-only widgets (`<RemainingSlots/>`/capacity, countdown)
**When** they hydrate
**Then** they are separate lazy islands OUTSIDE the auth shell, mounting no `AuthProvider` and no Amplify (ADR-011 D7)

**Given** the newsletter+Turnstile widget and the CountdownTimer
**When** the homepage loads
**Then** they are lazy `client:` islands that load only on visibility/interaction (FR11, UX-DR4, UX-DR5)

**Given** a beta-canary publish
**When** Lighthouse/PSI is run
**Then** TBT collapses and the score improves materially versus the prerender baseline (NFR1)
**And** only then is it promoted to prod (NFR8)

### Story 13.5: Archive grid + archive detail static + client-side filter island

As a public visitor or search-engine crawler,
I want the full archive — the grid and every per-event detail page — baked as static HTML with client-side filter/sort,
So that browsing past events is instant, fully crawlable, and needs zero round-trips.

**Acceptance Criteria:**

**Given** ~60 events
**When** the site builds
**Then** the full archive grid is baked as static HTML for `de` and `en` (all cards) using the already-batched list endpoint at build time
**And** there is no archive API call, no pagination, and no infinite-scroll JS at runtime (FR7)
**And** `useInfiniteEvents`, `fetchNextPage`, and the scroll sentinel are removed

**Given** the deep archive DOM (~250 session rows across the expanded cards)
**When** the grid renders
**Then** each card uses `content-visibility: auto` + `contain-intrinsic-size` so the browser skips layout/paint of off-screen cards while keeping them in the DOM and crawlable (NFR7)

**Given** filter/sort/topics
**When** a visitor filters or sorts
**Then** a single client island operates over the baked array
**And** the shareable `?q=&topics=&sort=` URLs and SEO are preserved
**And** the topic list is baked (the `topicService` call is dropped) (FR8, UX-DR3)

**Given** the ~58 archived events
**When** their `/archive/:code` detail pages build
**Then** they are fully static with no slots island and no live fetch (FR9)

**Given** the ≤2 active (non-archived) events
**When** their `/archive/:code` detail pages build
**Then** they carry the `<RemainingSlots/>` island and reuse the homepage islands, and are re-baked on content change (an event flips active→archived on EVENT_COMPLETED→ARCHIVED)

**Given** archived cards
**When** they render
**Then** `useMyRegistration` is dropped from archived cards (kept only on the ≤2 active events, if at all)

**Given** archive images
**When** the grid loads
**Then** they stream lazily via the existing CDN resize Lambda with `loading="lazy"` and reserved width/height (UX-DR7)

**Given** a beta-canary publish including a CPU-throttled low-end-device measurement
**When** verified
**Then** render cost is acceptable; the escalation lever (render-on-scroll / windowing) is applied only if measured heavy
**And** only then is it promoted to prod (NFR8)

### Story 13.6: Rebuild automation + manual "Republish" button + guardrails

As an organizer (and the platform),
I want the static site to rebuild automatically on content-publish events and via a manual button, with a guardrail that never ships a blank or stale site,
So that public content stays current (~12×/year) without manual ops and a broken build cannot go live.

**Acceptance Criteria:**

**Given** the backend auto-publish events (speakers @30d, agenda @14d, EVENT_LIVE, EVENT_COMPLETED→ARCHIVED)
**When** one fires
**Then** it triggers a CI rebuild → S3 sync → CloudFront invalidation via `repository_dispatch` / a workflow (FR12)

**Given** an organizer in the app
**When** they click "Republish public site"
**Then** the same rebuild + deploy pipeline runs as a manual override (FR13)

**Given** a build-time data fetch that fails or returns empty
**When** the build runs
**Then** the build FAILS and the last-good deploy stays live — a blank or stale public site is never published (NFR3)

**Given** a built artifact
**When** a content-assertion test runs
**Then** it verifies the prerendered routes contain expected content markers
**And** a silently-broken build cannot ship (NFR3)

**Given** an optional nightly schedule
**When** it runs
**Then** a safety rebuild executes as a backstop

### Story 13.7: Retire the prerender + remove public routes from the SPA bundle

As a developer on the platform team,
I want to delete the legacy prerender and remove the public route components from the SPA once the static site owns the public paths in prod,
So that we eliminate the TBT-capping paint-and-replace path and shrink the SPA bundle.

**Acceptance Criteria:**

**Given** the static site owns `/`, `/about`, `/archive*`, `/privacy`, `/support` in prod and is proven
**When** this story runs
**Then** `scripts/prerender.mjs` and the paint-and-replace logic are deleted (FR15)

**Given** the SPA
**When** the public routes are removed
**Then** the public page components are no longer in the SPA bundle (a measurable bundle-size reduction)
**And** the SPA still serves all app/auth routes unchanged

**Given** SEO
**When** the public paths' ownership is finalized
**Then** the sitemap is updated

**Given** beta + prod verification
**When** checked
**Then** no public path regresses
**And** no dead references to the prerender remain in the codebase
