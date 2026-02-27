---
stepsCompleted: ["step-01-document-discovery", "step-02-prd-analysis", "step-03-epic-coverage-validation", "step-04-ux-alignment", "step-05-epic-quality-review", "step-06-final-assessment"]
scope: "Story 10-8 (Moderator Presentation Page)"
documentsSelected:
  prd: "docs/prd/epic-10-additional-stories.md"
  architecture: "docs/architecture/ (sharded)"
  storySpec: "_bmad-output/implementation-artifacts/10-8-moderator-presentation-page.md"
  ux: null
---

# Implementation Readiness Assessment Report

**Date:** 2026-02-27
**Project:** BATbern
**Scope:** Story 10-8 — Moderator Presentation Page

---

## PRD Analysis

### Functional Requirements

**Navigation (AC #1–6)**
- FR1: Route `/present/:eventCode` loads all event data and renders Welcome section without authentication
- FR2: `→` / `PageDown` / `Space` advances; `←` / `PageUp` goes back one section
- FR3: At last section (Apéro), `→` does nothing (no wrap-around)
- FR4: At first section (Welcome), `←` does nothing
- FR5: `F` key toggles browser fullscreen via `document.documentElement.requestFullscreen()`
- FR6: `F11` also triggers fullscreen (native)

**Section Content (AC #7–16)**
- FR7: Welcome — BATbern logo, `#BATbernXX`, topic title, date, venue name
- FR8: About — admin-configurable `aboutText` + `partnerCount` from `GET /api/v1/public/settings/presentation`
- FR9: Committee — all active organizers (photo, name, company) from `GET /api/v1/public/organizers`, staggered fly-in
- FR10: Topic Reveal — topic name + topic image, visually dominant layout
- FR11: Agenda Preview — all sessions chronological, all styled as upcoming/neutral
- FR12: Speaker session — photo, full name, company, session title; multi-speaker → side-by-side cards
- FR13: Structural sessions (`moderation`, `networking`) excluded from section generation AND sidebar
- FR14: Agenda Recap — pre-break sessions greyed with ✓, post-break sessions lit
- FR15: Upcoming Events — next 3 future events, date + topic (placeholder if TBD)
- FR16: Apéro — closing visual + BATbern `~` spinner animation

**Sidebar (AC #17–22)**
- FR17: Sidebar hidden for sections 1–4 (Welcome, About, Committee, Topic Reveal), Upcoming Events, and Apéro
- FR18: Sidebar visible for sections 5–N (Agenda Preview through last speaker section + Break + Agenda Recap)
- FR19: Sidebar highlight follows moderator navigation position, NOT wall clock
- FR20: Break slot in sidebar rendered as `─── Pause ───` with scheduled time
- FR21: Structural sessions NOT shown in sidebar
- FR22: Sidebar session times update silently on 60-second poll

**Break Behaviour (AC #23–29)**
- FR23: `B` key shows break overlay (full-screen animated); section index unchanged
- FR24: `B` again dismisses overlay; returns to exact same section
- FR25: Break overlay shows: BATbern `~` spinner, animated coffee cup + steam, floating coffee beans, "Pause" text, "Weiter um HH:MM" from next post-break session start time
- FR26: `→` from last pre-break speaker section → Break section (sequential nav)
- FR27: `→` from Break section → Agenda Recap section
- FR28: `→` from Agenda Recap → first post-break speaker section
- FR29: B-key overlay and navigation Break section both render the same `BreakSlide` component

**Agenda ↔ Sidebar Animation (AC #30–33)**
- FR30: Agenda Preview (§5) → first speaker: agenda animates center→sidebar via Framer Motion `layout` FLIP
- FR31: Agenda Recap → first post-break speaker: same FLIP animation
- FR32: Transition ≈350ms, spring `stiffness: 100, damping: 22, mass: 1`
- FR33: Backward navigation reverses the animation correctly

**Background (AC #34–37)**
- FR34: Topic image as full-bleed background, Ken Burns zoom `scale 1.0 → 1.06`, 30s loop
- FR35: Dark overlay `rgba(0,0,0,0.65)` for text legibility
- FR36: Fallback to default dark BATbern brand image when no topic image available
- FR37: Background does NOT transition between sections — persists throughout

**Real-time Resilience (AC #38–39)**
- FR38: 60-second poll; sidebar times + break resume time update within 60s without moderator action
- FR39: API poll failure → page continues with last-loaded data (graceful degradation)

**Display (AC #40–42)**
- FR40: Page renders correctly at 1920×1080 and 2560×1440
- FR41: All text legible at projector-viewing distances (minimum 5m)
- FR42: No horizontal scrollbar at any supported resolution

**Backend — New Presentation Settings API**
- FR43: `GET /api/v1/public/settings/presentation` → `{ aboutText, partnerCount }` (public, no auth)
- FR44: `PUT /api/v1/settings/presentation` → update settings (ORGANIZER role required)
- FR45: Single-row `presentation_settings` table in company-user-management-service (Flyway **V10**)
- FR46: Default row seeded with BATbern Verein text + `partnerCount=9`
- FR47: `GET` returns default values when no row exists (fallback in service layer)

**Admin UI**
- FR48: "Presentation Settings" card on organizer admin settings page (aboutText textarea + partnerCount number field + save)

**Total FRs: 48**

---

### Non-Functional Requirements

- NFR1: **Security** — `/present/:eventCode` is fully public; zero authentication required; no auth guard in frontend routing
- NFR2: **Security** — `GET /api/v1/public/settings/presentation` is `permitAll` in BOTH api-gateway and company-user-management-service `SecurityConfig.java` (3 filter chain locations in CUMS)
- NFR3: **Security** — `PUT /api/v1/settings/presentation` requires ORGANIZER role; NOT under `/public/` path
- NFR4: **Performance** — Data loaded once at page load; 60-second poll is sessions-only (lightweight)
- NFR5: **Resilience** — Per-source error handling; no single API failure crashes the page
- NFR6: **Process** — OpenAPI spec written BEFORE any implementation (ADR-006); types regenerated after spec
- NFR7: **Process** — TDD: `PresentationSettingsControllerIntegrationTest` written first (RED before GREEN)
- NFR8: **Compatibility** — Framer Motion is NOT currently installed; must be added (`npm install framer-motion`); confirm no conflict with MUI + Emotion stack
- NFR9: **Animation correctness** — `AgendaView` must NEVER be unmounted between sections; FLIP animation requires persistent DOM element
- NFR10: **Code quality** — TypeScript type-check passes with zero errors; Checkstyle passes
- NFR11: **i18n** — `navigation.presentationMode` key added to `public/locales/en/common.json` + `de/common.json`
- NFR12: **Routing** — `PUT /api/v1/settings/presentation` (NOT under `/public/`) may need explicit DomainRouter rule to reach CUMS; verify against `DomainRouter.java:99`
- NFR13: **Database** — Flyway V10 in company-user-management-service (latest is V9); no conflicts with event-management-service

**Total NFRs: 13**

---

### Additional Requirements / Constraints

- **Dependency**: No prerequisite stories — Story 10.8 is independent
- **Scope constraint**: Backend scope can be deferred with frontend constant fallback for `aboutText` (v1 fallback noted in PRD)
- **Existing API reuse**: `GET /api/v1/public/organizers` already implemented (PublicOrganizerController); use as-is
- **Route placement**: New route in `App.tsx` at ~line 277 with other public routes; NOT inside ProtectedRoute wrapper; use React.lazy()
- **CSS FLIP constraint**: Transforms must be in CSS classes (not inline style), not in Framer Motion `animate` prop — otherwise FLIP geometry capture breaks
- **60-second poll**: Use React Query `refetchInterval: 60_000` (NOT useEffect + setInterval to avoid memory leak)

---

---

## Epic Coverage Validation

### Coverage Matrix

| FR | Requirement Summary | Story Phase | Status |
|----|---------------------|-------------|--------|
| FR1 | Public route loads without auth | Phase 8 (App.tsx route, no ProtectedRoute) | ✓ Covered |
| FR2 | Arrow/PageDown/Space advance; ArrowLeft/PageUp back | Phase 5 (useKeyboardNavigation.ts) | ✓ Covered |
| FR3 | No wrap at last section (Apéro) | Phase 5 (goNext guard) | ✓ Covered |
| FR4 | No wrap at first section (Welcome) | Phase 5 (goPrev guard) | ✓ Covered |
| FR5 | F key toggles fullscreen | Phase 5 (useKeyboardNavigation: `f → toggleFullscreen()`) | ✓ Covered |
| FR6 | F11 triggers fullscreen (native) | Phase 5 (noted; native browser behavior) | ✓ Covered |
| FR7 | Welcome: logo, hashtag, topic, date, venue | Phase 7 (WelcomeSlide.tsx) | ✓ Covered |
| FR8 | About: adminText + partnerCount from API | Phase 7 (AboutSlide.tsx) + Phase 1 (GET endpoint) | ✓ Covered |
| FR9 | Committee: organizers with photo/name/company, staggered | Phase 7 (CommitteeSlide.tsx) | ✓ Covered |
| FR10 | Topic Reveal: name + image dominant | Phase 7 (TopicRevealSlide.tsx) | ✓ Covered |
| FR11 | Agenda Preview: chronological, all neutral | Phase 7 (AgendaPreviewSlide.tsx) | ✓ Covered |
| FR12 | Speaker session: photo/name/company/title; multi = side-by-side | Phase 7 (SessionSlide + SpeakerCard + TwoSpeakerCard) | ✓ Covered |
| FR13 | Structural sessions excluded from sections + sidebar | Phase 5 (usePresentationSections: filter) | ✓ Covered |
| FR14 | Agenda Recap: pre-break grey+✓, post-break lit | Phase 7 (AgendaRecapSlide.tsx) | ✓ Covered |
| FR15 | Upcoming Events: next 3 future events | Phase 7 (UpcomingEventsSlide.tsx) | ✓ Covered |
| FR16 | Apéro: closing visual + spinner | Phase 7 (AperoSlide.tsx) | ✓ Covered |
| FR17 | Sidebar hidden for sections 1–4, Upcoming, Apéro | Phase 8 (PresentationPage visibility logic) | ✓ Covered |
| FR18 | Sidebar visible for sections 5–N | Phase 8 | ✓ Covered |
| FR19 | Sidebar highlight = nav position (not wall clock) | Phase 8 (currentIndex drives highlight) | ✓ Covered |
| FR20 | Break in sidebar as `─── Pause ───` + time | Phase 6 (AgendaView.tsx) | ✓ Covered |
| FR21 | Structural sessions NOT in sidebar | Phase 5 (usePresentationSections filter) | ✓ Covered |
| FR22 | Sidebar times update on 60s poll | Phase 5 (usePresentationData 60s refetchInterval) | ✓ Covered |
| FR23 | B key shows break overlay; section unchanged | Phase 5 (useKeyboardNavigation: toggleBlank) + Phase 6 (BlankOverlay.tsx) | ✓ Covered |
| FR24 | B again dismisses overlay; returns to same section | Phase 5 + Phase 6 | ✓ Covered |
| FR25 | Break overlay: spinner, coffee cup, beans, Pause, resume time | Phase 7 (BreakSlide.tsx) | ✓ Covered |
| FR26 | → from last pre-break speaker → Break section | Phase 5 (usePresentationSections: insert Break) | ✓ Covered |
| FR27 | → from Break → Agenda Recap | Phase 5 | ✓ Covered |
| FR28 | → from Recap → first post-break speaker | Phase 5 | ✓ Covered |
| FR29 | B-key overlay + nav Break both render BreakSlide | Phase 6 (BlankOverlay wraps BreakSlide) + Phase 7 | ✓ Covered |
| FR30 | FLIP animation: AgendaPreview → first speaker | Phase 8 (motion.div layout) | ✓ Covered |
| FR31 | FLIP animation: AgendaRecap → first post-break speaker | Phase 8 | ✓ Covered |
| FR32 | Transition ≈350ms, spring stiffness:100, damping:22, mass:1 | Phase 8 (explicit spring params) | ✓ Covered |
| FR33 | Backward navigation reverses animation | Phase 8 (direction state: forward/back) | ✓ Covered |
| FR34 | Ken Burns background: scale 1.0→1.06, 30s loop | Phase 6 (TopicBackground.tsx) | ✓ Covered |
| FR35 | Dark overlay rgba(0,0,0,0.65) | Phase 6 | ✓ Covered |
| FR36 | Fallback to default BATbern brand image | Phase 6 | ✓ Covered |
| FR37 | Background persists, does not transition | Phase 6 (TopicBackground never unmounts) | ✓ Covered |
| FR38 | 60s poll; sidebar + break resume time update | Phase 5 (useQuery refetchInterval:60_000) | ✓ Covered |
| FR39 | Poll failure: graceful degradation | Phase 5 (per-source error handling) | ✓ Covered |
| FR40 | Renders at 1920×1080 and 2560×1440 | DoD only — no explicit Phase task | ⚠️ Implicit |
| FR41 | Text legible at 5m projector distance | DoD only — no explicit task or test | ⚠️ Implicit |
| FR42 | No horizontal scrollbar at any supported resolution | DoD only — no explicit task or test | ⚠️ Implicit |
| FR43 | GET /api/v1/public/settings/presentation | Phase 1 (OpenAPI) + Phase 2 (impl) | ✓ Covered |
| FR44 | PUT /api/v1/settings/presentation (organizer auth) | Phase 1 + Phase 2 + Phase 3 | ⚠️ Routing gap (see below) |
| FR45 | Flyway V10 in CUMS | Phase 2 | ✓ Covered |
| FR46 | Default row seeded | Phase 2 (INSERT in V10 migration) | ✓ Covered |
| FR47 | GET returns defaults when no row exists | Phase 2 (fallback in service) | ✓ Covered |
| FR48 | Admin UI "Presentation Settings" card | Phase 9 | ✓ Covered |

---

### Missing / Incomplete Coverage

#### ⚠️ Implicit Only — No Explicit Test Tasks

**FR40, FR41, FR42** (Display requirements): These three requirements appear only in the Definition of Done, not as explicit implementation or test tasks in the 10 phases.
- **FR40**: 1920×1080 / 2560×1440 rendering — no responsive layout test task exists
- **FR41**: 5m legibility — no font-size audit task or test
- **FR42**: No horizontal scrollbar — no CSS overflow check task
- **Recommendation**: Add a Phase 10 task: "Manual QA at both resolutions — verify no horizontal scroll, font sizes legible at 5m" (3 sub-items)

#### ⚠️ Routing Gap — FR44 (PUT /api/v1/settings/presentation)

Phase 3 says: *"confirm routing; if not caught, add explicit rule in DomainRouter.java"* — this is conditional and unresolved. The path `/api/v1/settings/presentation` (no `/public/` prefix) has no confirmed DomainRouter rule routing it to CUMS. If it falls through, PUT will fail with 404 or incorrect routing.
- **Recommendation**: Verify `DomainRouter.java` before implementation. If no `startsWith("/api/v1/settings")` rule exists, add it. This must be a confirmed task, not a conditional note.

#### Minor: PRD Story File Reference Stale

The epic-10 PRD references Story 10.8 story file as `_bmad-output/implementation-artifacts/moderator-presentation-page.md` (the now-archived draft). Should be updated to reference `10-8-moderator-presentation-page.md`.
- **Impact**: Low — documentation only, no implementation risk

---

### Coverage Statistics

- **Total PRD FRs**: 48
- **FRs fully covered in story phases**: 43
- **FRs implicitly covered (DoD only, no task)**: 3 (FR40, FR41, FR42)
- **FRs with routing concern**: 1 (FR44)
- **FRs missing**: 0
- **Coverage percentage**: 89.6% explicit / 100% nominal

---

---

## UX Alignment Assessment

### UX Document Status

**Not Found** — No dedicated wireframe or UX design document exists for Story 10-8. UX is fully embedded in the story spec (10-8-moderator-presentation-page.md) as textual AC descriptions, CSS module code, animation parameter specs, and component layout descriptions.

### UX Coverage In Story Spec

The story spec substitutes for a UX doc with high specificity:

| UX Concern | Specified In Story Spec? | Fidelity |
|------------|--------------------------|----------|
| Section flow order | ✓ Section Flow table + algorithm | High |
| Animation: FLIP transition | ✓ Spring params, CSS class names | High |
| Animation: Ken Burns | ✓ motion.img, scale, duration | High |
| Animation: directional section transitions | ✓ initial/exit x-offsets, spring params | High |
| Animation: Break slide | ✓ CSS @keyframes description, steam, beans | Medium (no mockup) |
| Animation: Committee fly-in | ✓ stagger delay formula | High |
| Sidebar layout dimensions | ✓ CSS classes: 200px width, 2rem left | High |
| Center-stage dimensions | ✓ CSS classes: 560px width, centered | High |
| Background: dark overlay | ✓ rgba(0,0,0,0.65) | High |
| Break overlay background | ✓ rgba(0,0,0,0.85) + amber tint | Medium |
| Typography for 5m legibility | ⚠️ Not specified — no font sizes defined | **Low** |
| Committee card layout | ⚠️ Not specified — number of columns, card size | **Low** |
| Upcoming Events card layout | ⚠️ Not specified beyond "3 staggered cards" | **Low** |
| SectionDots appearance | ⚠️ "subtle progress indicator, dot per section" only | **Low** |
| Welcome slide visual hierarchy | ⚠️ Elements listed but no sizing/positioning | **Low** |

### Architecture ↔ UX Alignment

| UX Requirement | Architecture Support | Status |
|----------------|---------------------|--------|
| FLIP animation (AgendaView layout shift) | Framer Motion `layout` prop — new dependency (not installed) | ⚠️ Dependency must be installed first |
| Section transitions (AnimatePresence) | Framer Motion AnimatePresence — same dependency | ⚠️ |
| 60-second poll without page reload | React Query `refetchInterval` — already used in project | ✓ |
| Full-screen API | `document.documentElement.requestFullscreen()` — browser native | ✓ |
| Ken Burns animation | Framer Motion `motion.img` animate array | ✓ (after install) |
| Public route (no auth guard) | Existing React Router + App.tsx patterns | ✓ |
| CSS FLIP constraint (transforms in CSS classes, not inline) | Story spec explicitly calls this out with correct pattern | ✓ |
| Break resume time calculation | `date-fns` `format()` — already installed | ✓ |

### Warnings

1. **⚠️ No visual mockups for 5 slide components**: WelcomeSlide, AboutSlide, CommitteeSlide, BreakSlide, AperoSlide have no visual reference. Dev will need to make visual judgment calls on typography scale, card layouts, grid columns, and spacing. Risk of needing UI rework after first visual review.

2. **⚠️ Font size legibility (FR41) unspecified**: "Legible at 5m" is a DoD requirement but no minimum `rem` values, `font-size`, or `line-height` specs are given. Recommend: add a note to dev spec with minimum sizes (e.g., `1.5rem` body, `3rem` headings, `4rem` topic title).

3. **⚠️ Framer Motion is a new, uninstalled dependency**: UX animations depend on it. Installing it before writing any component is both a Phase 4 task and a prerequisite for all Phase 6–8 work. Failure to install first will cascade.

4. **ℹ️ INFO**: The CSS FLIP pattern (transforms in `.agendaSidebar`/`.agendaCenterStage` CSS classes) is correctly specified in the dev notes. This is a nuanced constraint and the spec has captured it accurately — low implementation risk.

---

### PRD Completeness Assessment

The PRD (epic-10-additional-stories.md) provides a solid high-level summary for Story 10.8 but delegates full detail to the story spec file. The story spec (`10-8-moderator-presentation-page.md`) is highly detailed: 42 ACs, 10 implementation phases, section flow algorithm, CSS patterns, animation snippets, and routing notes. Together they provide comprehensive coverage. Minor gap: no dedicated UX wireframe doc — UX is fully embedded in the story spec ACs and dev notes (acceptable for this story type).

---

## Epic Quality Review

### Story User Value

✅ **Story 10.8 delivers clear user value**: "As an event moderator, I want to open a fullscreen webpage and navigate using my presentation remote, so that I can guide the audience without PowerPoint — content always live from the platform." This is a concrete, tangible deliverable for a real user role (event moderator). Not a technical milestone.

### Story Independence

✅ **Story 10.8 is genuinely independent**. Declared prerequisite: none. All data sources use existing public APIs (Epic 2/4 foundation). New backend is self-contained in CUMS. No dependency on Stories 10.1–10.7.

### Best Practices Compliance Checklist

| Check | Result |
|-------|--------|
| Story delivers user value | ✅ Pass |
| Story functions independently | ✅ Pass |
| No forward dependencies | ✅ Pass |
| Database tables created when needed (Flyway V10, CUMS) | ✅ Pass |
| Traceability to FRs maintained | ✅ Pass |
| ACs testable and specific | ✅ Pass (but not BDD format) |
| Story appropriately sized | ⚠️ Concern (see below) |
| Error conditions covered | ⚠️ Partial (initial load failure missing) |

---

### 🟠 Major Issues

**M1: Story is significantly oversized (42 ACs, 10 implementation phases)**

Story 10.8 is the scope of a small epic — it spans a full backend service (CUMS), 2 security configs, 1 routing rule, 10+ frontend components, 10 slide components, 3 custom hooks, 1 service layer file, 1 new npm dependency install, admin UI, and i18n. At typical sprint velocity, this is a multi-sprint delivery risk.

- **Impact**: Dev sprint underestimated; risk of partial delivery in a sprint without a clear "shippable" intermediate state
- **Recommendation**: Consider splitting into **10.8a** (Backend: OpenAPI + CUMS controller + security/routing, 5 ACs from backend group) and **10.8b** (Frontend: PresentationPage + all slides + hooks, 37 ACs). Each can be deployed independently — the backend endpoint is useful standalone for admin settings; the frontend degrades gracefully if about text is hardcoded.

**M2: Initial load failure has no AC**

AC #39 covers graceful degradation on **poll failure** only. No AC covers the scenario where **initial page load fails** (e.g., the event-management-service is down and `GET /api/v1/events/{eventCode}?include=...` returns 503 on first load).

- **Impact**: Moderator navigates to `/present/BATbern57` 5 minutes before the event, service is restarting — blank white page, no feedback, no retry button. Embarrassing failure scenario at exactly the wrong moment.
- **Recommendation**: Add AC: *"If the initial event data load fails, the page shows a branded error screen with the event hashtag and a 'Retry' button; retry re-fetches all data sources."*

**M3: DomainRouter routing for PUT /api/v1/settings/presentation is unresolved**

Phase 3 currently reads: *"confirm routing; if not caught, add explicit rule"* — this is a conditional task not a concrete implementation task. The PUT path is NOT under `/api/v1/public/` and therefore does not match the existing `startsWith("/api/v1/public")` CUMS rule in DomainRouter.

- **Impact**: PUT endpoint implemented but silently routing to wrong service (or returning 404). Will only be caught during integration testing, creating a debugging surprise.
- **Recommendation**: Resolve now — add concrete task to Phase 3: *"Add `startsWith("/api/v1/settings")` → CUMS routing rule in DomainRouter.java (line ~120). Confirmed: no existing rule matches this path."*

---

### 🟡 Minor Concerns

**m1: ACs not in Given/When/Then format**
ACs are numbered declarative statements, not BDD format. They are specific and testable, which is what matters. Consistent with the project's existing story patterns (10.1–10.7 use the same format). Low impact.

**m2: Frontend test coverage is minimal for story scope**
Phase 10 specifies only 3 unit tests (usePresentationSections, useKeyboardNavigation, PresentationPage smoke). For 42 ACs and 10 slide components, this is sparse. No Playwright E2E test is specified.
- **Recommendation**: Add Phase 10 task: *"Playwright E2E test: navigate to `/present/BATbernXX`, verify Welcome section renders, press ArrowRight 3 times, verify sidebar appears on session section."*

**m3: PRD story file reference is stale**
`docs/prd/epic-10-additional-stories.md` references `_bmad-output/implementation-artifacts/moderator-presentation-page.md` (now archived). Should reference `10-8-moderator-presentation-page.md`.
- **Recommendation**: Update the story file path in the PRD (1-line change, low priority).

---

## Summary and Recommendations

### Overall Readiness Status

> **⚠️ NEEDS WORK — Conditionally Ready**

Story 10-8 is exceptionally well-specified — 42 precise ACs, detailed implementation phases, explicit CSS patterns, and architecture-consistent design decisions. It is implementable as-is. However, two issues should be resolved before dev starts to avoid embarrassing gaps at event time, and one sizing concern should be explicitly acknowledged.

---

### Issues Requiring Action Before Dev Starts

**1. Add AC for initial load failure** *(🟠 Major — 30 min fix)*

The current spec handles poll failures (AC #39) but not initial page load failure. A moderator hitting `/present/BATbernXX` when the API is slow will see a blank page. Add this AC to the story spec:

> *"AC #43: If the initial event data load fails (network error or service unavailable), the page shows a branded error screen with the event hashtag, an error message, and a 'Retry' button that re-fetches all data sources."*

**2. Resolve DomainRouter routing for PUT /api/v1/settings/presentation** *(🟠 Major — 15 min fix)*

Replace the conditional note in Phase 3 with a concrete, verified task. The path `/api/v1/settings/presentation` (no `/public/` prefix) likely has no DomainRouter rule to CUMS. Add it explicitly:

```java
// DomainRouter.java — add near line 120
if (cleanPath.startsWith("/api/v1/settings")) {
    return companyUserManagementServiceUrl;
}
```

**3. Add display QA tasks for FR40/FR41/FR42** *(🟡 Minor — 20 min to add tasks)*

Three display requirements are in the DoD but have no implementation task:
- Verify renders at 1920×1080 and 2560×1440 — no horizontal scrollbar
- Verify font sizes are legible at 5m (minimum suggested: `1.5rem` body, `3rem` headings, `4.5rem` topic title)

Add these as explicit Phase 10 QA sub-tasks with minimum font-size guidelines.

---

### Recommended Next Steps

1. **Now (pre-dev)**: Add AC #43 (initial load failure) to `10-8-moderator-presentation-page.md`
2. **Now (pre-dev)**: Verify DomainRouter — add CUMS routing rule for `/api/v1/settings` if absent; make Phase 3 task concrete
3. **Now (pre-dev)**: Add Phase 10 QA sub-tasks for display/legibility requirements + Playwright E2E smoke test
4. **Dev start**: Install Framer Motion FIRST (Phase 4 is a strict prerequisite for all animation work in Phases 6–8)
5. **Dev sequence**: Follow phases 1→2→3→4→5→6→7→8→9→10 strictly — Phase 1 (OpenAPI) must precede ALL implementation
6. **Sprint planning**: If this is one sprint, allocate 2 developers (backend + frontend in parallel after Phase 1 completes); or split into 10.8a/10.8b if capacity is limited
7. **Post-dev**: Update PRD story file reference from archived path to `10-8-moderator-presentation-page.md`

---

### Final Note

This assessment identified **9 issues** across **4 categories**:
- 🟠 3 Major issues (1 missing AC, 1 routing gap, 1 sizing concern)
- ⚠️ 3 Implicit-only display requirements
- 🟡 3 Minor concerns (AC format, test coverage, stale reference)

The story spec is high quality and production-ready in its design intent. The issues found are fixable in under 2 hours before dev begins. The architecture is sound, dependencies are correctly identified, and the implementation phases are logical and sequenced correctly.

**Assessor**: Winston (Architect Agent) | **Date**: 2026-02-27 | **Model**: claude-sonnet-4-6
