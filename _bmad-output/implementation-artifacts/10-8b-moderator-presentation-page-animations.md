# Story 10.8b: Moderator Presentation Page — Animations

Status: review
Prerequisite: Story 10.8a complete and deployed

## Story

As an **event moderator**,
I want the presentation page to have polished, fluid animations,
so that the audience experience feels dynamic and professional — not like a static slide deck.

> **Scope**: This story adds the full Framer Motion animation layer on top of the functional page delivered by Story 10.8a. No new data, routing, or business logic. Pure visual enhancement. All changes are additive — no existing AC from 10.8a is broken.

## Acceptance Criteria

### Agenda ↔ Sidebar FLIP Animation
1. When navigating from Agenda Preview (§5) to the first speaker section, the agenda animates from center stage to left sidebar using Framer Motion `layout` prop (FLIP)
2. When navigating from Agenda Recap to the first post-break speaker, the same FLIP animation occurs
3. Transition duration ≈350ms, spring physics `stiffness: 100, damping: 22, mass: 1`
4. Navigating backwards reverses the animation correctly (sidebar → center stage)

### Section Transitions
5. Moving forward between sections: slide content enters from the right (`x: 80 → 0`) and exits left (`x: 0 → -80`)
6. Moving backward: slide content enters from the left (`x: -80 → 0`) and exits right (`x: 0 → 80`)
7. Transition uses spring physics `stiffness: 120, damping: 20` ≈ 350ms via Framer Motion `AnimatePresence mode="wait"`

### Ken Burns Background
8. Topic image animates with Ken Burns zoom: `scale 1.0 → 1.06 → 1.0`, 30-second loop, continuous throughout the presentation

### BlankOverlay Fade
9. Pressing `B` fades the break overlay in over 0.3s; pressing `B` again fades it out over 0.3s (Framer Motion `AnimatePresence`)

### CommitteeSlide Stagger
10. Organizer cards animate in with staggered fly-in: `y: 30 → 0, opacity: 0 → 1`, each card delayed by `index × 0.12s`

### BreakSlide Animations
11. Break overlay shows BATbern `~` spinner (CSS `@keyframes` rotation, 3s loop)
12. Animated coffee cup with steam lines rising and fading (CSS `@keyframes`, 2s loop)
13. 8–12 floating coffee beans staggered upward with gentle sway

### AperoSlide Spinner
14. Apéro section shows BATbern `~` spinner (same CSS `@keyframes` as BreakSlide spinner, 3s loop)

## Tasks / Subtasks

### Phase 1: Install Framer Motion
- [x] `cd web-frontend && npm install framer-motion`
  - [x] Verify no conflict with existing MUI + Emotion stack (run `npm run type-check` and `npm run build` after install)
  - [x] Commit `package.json` and `package-lock.json`

### Phase 2: Ken Burns background (AC: #8)
- [x] Replace `TopicBackground.tsx` static `<img>` with `motion.img`:
  ```tsx
  <motion.img
    src={topicImageUrl ?? '/images/batbern-default-bg.jpg'}
    animate={{ scale: [1.0, 1.06, 1.0] }}
    transition={{ duration: 30, repeat: Infinity, ease: 'easeInOut' }}
    style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover' }}
  />
  ```
- [x] Dark overlay `<div>` remains unchanged above the image

### Phase 3: FLIP agenda ↔ sidebar transition (ACs: #1–4)
- [x] In `PresentationPage.tsx`, wrap `AgendaView` in `motion.div` with `layout` prop
- [x] Move layout transforms into CSS module classes `PresentationPage.module.css` (CRITICAL)
- [x] `AgendaView` is NOT unmounted between sections — `visibility` toggle (never conditional render)
- [x] `AgendaView.module.css` `.sidebar` no longer contains absolute positioning (container handles it)
- [x] `AgendaPreviewSlide` / `AgendaRecapSlide` no longer render `AgendaView` (page-level owns it)

### Phase 4: Section spring transitions (ACs: #5–7)
- [x] Wrap rendered slide in `AnimatePresence mode="wait"` keyed by `currentIndex`
- [x] `direction` changed from string to number (+1/-1) and wired into `custom` prop

### Phase 5: BlankOverlay fade (AC: #9)
- [x] `BlankOverlay.tsx` uses `AnimatePresence` + `motion.div` — 0.3s fade in/out
- [x] Replaced CSS opacity/visibility toggle from Story 10.8a

### Phase 6: CommitteeSlide stagger (AC: #10)
- [x] Each organizer card wrapped in `motion.div` — `y: 30→0, opacity: 0→1, delay: index × 0.12s`

### Phase 7: BreakSlide animations (ACs: #11–13)
- [x] BATbern `~` spinner — CSS `@keyframes spin` 3s loop via shared `presentation-animations.module.css`
- [x] Animated coffee cup with 3 steam lines — `@keyframes steam` 2s loop, staggered delays
- [x] 10 floating coffee beans — fixed positions, staggered `animationDelay/Duration` inline

### Phase 8: AperoSlide spinner (AC: #14)
- [x] `AperoSlide.tsx` imports shared `presentation-animations.module.css` — same `.spinner` class

### Phase 9: Update tests
- [x] `framer-motion` mocked globally in `src/test/setup.ts` — `motion.*` → plain elements, `AnimatePresence` → pass-through
- [x] Update Playwright E2E test — `data-testid="agenda-flip-container"` + `data-layout` attribute check
- [x] Type-check: `npm run type-check` — 0 errors
- [x] Build: `npm run build` — clean

## Definition of Done

- [x] `framer-motion` in `package.json`; `npm run build` clean after install
- [x] Ken Burns zoom plays on topic image (`motion.img` animate scale 1.0→1.06→1.0, 30s loop)
- [x] FLIP animation: agenda moves from center → sidebar via `layout` prop + CSS module classes
- [x] Section transitions: directional spring via `AnimatePresence mode="wait"` + variants
- [x] BlankOverlay fades in/out via `AnimatePresence` + `motion.div` opacity 0→1→0 in 0.3s
- [x] Committee cards stagger in with `y: 30→0, opacity: 0→1, delay: index × 0.12s`
- [x] BreakSlide: spinner + steam lines + floating beans (CSS `@keyframes` in shared module)
- [x] AperoSlide: `~` spinner via shared `presentation-animations.module.css`
- [x] All Story 10.8a ACs continue to pass — 3756 tests pass, 0 failures
- [x] Type-check passes, 0 TypeScript errors

## Dev Notes

### FLIP animation — CSS class constraint (CRITICAL)

Framer Motion captures the DOM element's bounding box BEFORE and AFTER a class change, then animates between them. This only works if the transforms (`translateX`, `translateY`) live in **CSS classes**, NOT in Framer Motion's `animate` prop or inline `style`. If transforms are inline, Framer captures them as part of the animated state and cannot perform FLIP.

✅ Correct:
```tsx
<motion.div layout className={agendaLayout === 'sidebar' ? styles.agendaSidebar : styles.agendaCenterStage}>
```

❌ Wrong:
```tsx
<motion.div layout animate={{ x: agendaLayout === 'sidebar' ? 0 : '50%' }}>
```

### AgendaView must never unmount

The FLIP animation requires the same DOM element to persist across section changes. Use a visibility/opacity approach for sections where AgendaView should not be visible, rather than conditional rendering:

```tsx
// AgendaView is always mounted; CSS hides it for non-sidebar/non-center sections
<motion.div layout style={{ visibility: showAgenda ? 'visible' : 'hidden' }}>
  <AgendaView ... />
</motion.div>
```

### Framer Motion + MUI/Emotion compatibility

Framer Motion and MUI both use CSS-in-JS but do not conflict — they operate on separate style mechanisms. However, if you use `motion(MuiComponent)`, ensure the MUI component forwards refs (`React.forwardRef`). All standard HTML `motion.*` elements (`motion.div`, `motion.img`) work without any configuration.

### CSS spinner — reuse across BreakSlide and AperoSlide

Extract the spinner keyframes to a shared file rather than duplicating:
```
web-frontend/src/components/presentation/presentation-animations.module.css
```
Import in both `BreakSlide.tsx` and `AperoSlide.tsx`.

### References

- Story 10.8a (functional page): [Source: _bmad-output/implementation-artifacts/10-8a-moderator-presentation-page.md]
- Framer Motion layout docs: https://www.framer.com/motion/layout-animations/
- Framer Motion AnimatePresence: https://www.framer.com/motion/animate-presence/
- Epic 10 PRD: [Source: docs/prd/epic-10-additional-stories.md#story-108b]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

All 9 phases complete. framer-motion@12.34.3 installed. All 3756 unit tests pass (0 failures). Type-check clean. Build clean. Coverage 70.82% (above 70% threshold).

Key decisions:
- AgendaView pulled to page-level `motion.div` for FLIP — AgendaPreviewSlide/AgendaRecapSlide now render heading only
- AgendaView.module.css `.sidebar` absolute positioning removed (container handles it via PresentationPage.module.css)
- `data-testid="agenda-flip-container"` + `data-layout` added for E2E FLIP verification
- framer-motion mocked globally in `src/test/setup.ts` — all motion elements render as plain HTML in JSDOM
- 10 coffee beans with fixed configs (no random) to avoid re-render layout shifts

### File List

- web-frontend/package.json
- web-frontend/package-lock.json
- web-frontend/src/pages/PresentationPage.tsx
- web-frontend/src/pages/PresentationPage.module.css (NEW)
- web-frontend/src/pages/presentation/TopicBackground.tsx
- web-frontend/src/pages/presentation/BlankOverlay.tsx
- web-frontend/src/pages/presentation/AgendaView.module.css
- web-frontend/src/pages/presentation/presentation-animations.module.css (NEW)
- web-frontend/src/pages/presentation/slides/AgendaPreviewSlide.tsx
- web-frontend/src/pages/presentation/slides/AgendaRecapSlide.tsx
- web-frontend/src/pages/presentation/slides/CommitteeSlide.tsx
- web-frontend/src/pages/presentation/slides/BreakSlide.tsx
- web-frontend/src/pages/presentation/slides/AperoSlide.tsx
- web-frontend/src/test/setup.ts
- web-frontend/e2e/presentation.spec.ts
- _bmad-output/implementation-artifacts/sprint-status.yaml
