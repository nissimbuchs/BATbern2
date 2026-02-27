# Story 10.8b: Moderator Presentation Page — Animations

Status: ready-for-dev
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
- [ ] `cd web-frontend && npm install framer-motion`
  - [ ] Verify no conflict with existing MUI + Emotion stack (run `npm run type-check` and `npm run build` after install)
  - [ ] Commit `package.json` and `package-lock.json`

### Phase 2: Ken Burns background (AC: #8)
- [ ] Replace `TopicBackground.tsx` static `<img>` with `motion.img`:
  ```tsx
  <motion.img
    src={topicImageUrl ?? '/images/batbern-default-bg.jpg'}
    animate={{ scale: [1.0, 1.06, 1.0] }}
    transition={{ duration: 30, repeat: Infinity, ease: 'easeInOut' }}
    style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover' }}
  />
  ```
- [ ] Dark overlay `<div>` remains unchanged above the image

### Phase 3: FLIP agenda ↔ sidebar transition (ACs: #1–4)
- [ ] In `PresentationPage.tsx`, wrap `AgendaView` in `motion.div` with `layout` prop:
  ```tsx
  <motion.div
    layout
    transition={{ type: 'spring', stiffness: 100, damping: 22, mass: 1 }}
    className={agendaLayout === 'sidebar' ? styles.agendaSidebar : styles.agendaCenterStage}
  >
    <AgendaView layout={agendaLayout} ... />
  </motion.div>
  ```
- [ ] Move layout transforms out of inline `style` into CSS module classes (CRITICAL — FLIP requires DOM geometry capture from CSS, not inline transforms):
  ```css
  /* PresentationPage.module.css */
  .agendaCenterStage { position: absolute; left: 50%; transform: translateX(-50%); width: 560px; font-size: 1.1rem; }
  .agendaSidebar     { position: absolute; left: 2rem; top: 50%; transform: translateY(-50%); width: 200px; font-size: 0.75rem; opacity: 0.85; }
  ```
- [ ] Verify `AgendaView` is NOT unmounted between sections — the FLIP animation requires a persistent DOM element; conditional rendering breaks it

### Phase 4: Section spring transitions (ACs: #5–7)
- [ ] Wrap rendered slide in `AnimatePresence mode="wait"` keyed by `currentIndex`:
  ```tsx
  <AnimatePresence mode="wait" custom={direction}>
    <motion.div
      key={currentIndex}
      custom={direction}
      variants={{
        enter: (dir) => ({ x: dir > 0 ? 80 : -80, opacity: 0 }),
        center: { x: 0, opacity: 1 },
        exit: (dir) => ({ x: dir > 0 ? -80 : 80, opacity: 0 }),
      }}
      initial="enter"
      animate="center"
      exit="exit"
      transition={{ type: 'spring', stiffness: 120, damping: 20 }}
    >
      {renderCurrentSlide()}
    </motion.div>
  </AnimatePresence>
  ```
- [ ] `direction` state is already tracked in `PresentationPage` from Story 10.8a (`+1` forward, `-1` backward)

### Phase 5: BlankOverlay fade (AC: #9)
- [ ] Wrap `BlankOverlay.tsx` content in `AnimatePresence` + `motion.div`:
  ```tsx
  <AnimatePresence>
    {isVisible && (
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        transition={{ duration: 0.3 }}
        style={{ position: 'fixed', inset: 0, zIndex: 1000 }}
      >
        <BreakSlide ... />
      </motion.div>
    )}
  </AnimatePresence>
  ```
- [ ] Replace the CSS opacity/visibility toggle from Story 10.8a

### Phase 6: CommitteeSlide stagger (AC: #10)
- [ ] In `CommitteeSlide.tsx`, animate each organizer card with Framer Motion stagger:
  ```tsx
  {organizers.map((org, index) => (
    <motion.div
      key={org.username}
      initial={{ y: 30, opacity: 0 }}
      animate={{ y: 0, opacity: 1 }}
      transition={{ delay: index * 0.12, duration: 0.4 }}
    >
      <OrganizerCard {...org} />
    </motion.div>
  ))}
  ```

### Phase 7: BreakSlide animations (ACs: #11–13)
- [ ] BATbern `~` spinner — add CSS `@keyframes` rotation to `BreakSlide.module.css`:
  ```css
  @keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
  .spinner { display: inline-block; animation: spin 3s linear infinite; font-size: 4rem; color: var(--bat-blue); }
  ```
- [ ] Animated coffee cup with steam:
  ```css
  @keyframes steam { 0%,100% { opacity: 0; transform: translateY(0) scaleX(1); } 50% { opacity: 0.6; transform: translateY(-12px) scaleX(1.2); } }
  .steamLine { animation: steam 2s ease-in-out infinite; }
  .steamLine:nth-child(2) { animation-delay: 0.4s; }
  .steamLine:nth-child(3) { animation-delay: 0.8s; }
  ```
- [ ] Floating coffee beans — 8–12 `<span>` elements, staggered upward float with gentle sway:
  ```css
  @keyframes floatBean { 0% { transform: translateY(0) rotate(0deg); opacity: 0.8; } 100% { transform: translateY(-60px) rotate(30deg); opacity: 0; } }
  ```
  Each bean has randomised `animation-delay` (0–3s) and `animation-duration` (3–5s) set inline.

### Phase 8: AperoSlide spinner (AC: #14)
- [ ] Add BATbern `~` spinner to `AperoSlide.tsx` — reuse the same `.spinner` CSS class from BreakSlide (extract to shared `presentation.module.css` or a `Spinner.tsx` component if both use it)

### Phase 9: Update tests
- [ ] Update `PresentationPage.test.tsx` — mock `framer-motion` in Jest config (`moduleNameMapper`) so `motion.div` renders as `div` in tests; existing tests should continue to pass
- [ ] Update Playwright E2E test — add assertion that agenda sidebar is visible after navigating past section 5 (verifies FLIP completed; no timing assertions needed since E2E runs against live page)
- [ ] Type-check: `cd web-frontend && npm run type-check` passes with zero errors

## Definition of Done

- [ ] `framer-motion` in `package.json`; `npm run build` clean after install
- [ ] Ken Burns zoom plays on topic image (verified at 1920×1080)
- [ ] FLIP animation: agenda smoothly moves from center → sidebar on section 5→6 transition; reverses correctly
- [ ] Section transitions: directional spring on forward/backward nav (no flicker)
- [ ] BlankOverlay fades in/out smoothly on B key (0.3s)
- [ ] Committee cards stagger in with fly-in on every visit to Committee section
- [ ] BreakSlide: spinner rotates, steam rises, beans float (all three animations visible)
- [ ] AperoSlide: `~` spinner rotates
- [ ] All Story 10.8a ACs continue to pass (no regression)
- [ ] No console errors during navigation
- [ ] Type-check passes, no TypeScript errors

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

### File List
