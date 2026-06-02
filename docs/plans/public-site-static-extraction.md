# Plan: Extract the public site into a static (Astro) build — bake the content, keep one live island

**Status:** PROPOSED (architecture agreed 2026-06-02 with Nissim). Supersedes the
paint-and-replace prerender approach in `public-homepage-prerender.md` for the public
surface (that plan delivered the *paint* win but capped TBT because the SPA still boots and
re-renders; see its §9). This plan removes the SPA from the public read path entirely.

**Author:** Winston (architect) — drafted 2026-06-02 from a code audit of the public surface.

---

## 0. The decisive fact (why this beats prerender)

The public content **changes ~3–4 times per event, ~3 events/year (~12 changes/year)**. The
**only** frequently-changing value is **open registration slots** in the weeks before an event.

Every prior approach assumed the content was unknowable at build time, so it shipped an empty
shell and fetched event/sessions/archive **client-side** — which forces React to boot,
re-render, and gate TBT, and leaves the content invisible to crawlers in 9 of 10 locales.

Because the content **is** knowable at build time, we bake the **real answer** into the HTML.
The page is genuinely complete on first paint — no fetch, no spinner, no framework boot needed
to *read* it. The one volatile thing (slots) becomes a single tiny hydrated island.

This directly serves the four user questions, each answered in static HTML:
*what's the next event · what sessions · can I register · what past events.*

## 0a. Decisions resolved (2026-06-02, binding)

- **SSG tool → Astro**, reusing the existing public React components as islands. The public
  components are already **MUI-free (React + Tailwind + Radix)** — Astro renders them to static
  HTML at build and hydrates only islands via `client:` directives. This is a *move + feed-data-
  as-props* migration, not a rewrite. (11ty would discard the component work; Next static export
  is a heavier app framework than a brochure needs.)
- **i18n → static `de` + `en`; client-switch the other 8.** Bake fully-static, crawlable, fast
  pages for German + English (the real audience). The other 8 locales (`fr it rm es fi nl ja
  gsw-BE`) render client-side on top of the `de` page via the existing react-i18next + locale
  JSON, as a bounded long-tail degradation. Satisfies CLAUDE.md "all 10 public locales work"
  while keeping de/en first-class (consistent with the de/en-first reasoning already in CLAUDE.md).
- **Rebuild trigger → auto on backend publish events + a manual "Republish public site" button.**
  The existing auto-publish hooks (speakers @30d, agenda @14d, EVENT_LIVE / EVENT_COMPLETED
  transitions) fire a CI rebuild → S3 sync → CloudFront invalidation. ~12 builds/year; builds
  take seconds. Manual button for organizers as the override.

## 1. Target architecture

```
   Build time (per content change, ~12/yr) ── Astro build ─────────────────────┐
     • fetch current event + sessions + full archive from api.batbern.ch        │
     • render REAL content → static HTML, baked for de + en                      │
     • ships ~0 JS for everything readable                                       │
   ────────────────────────────────────────────────────────────────────────────┘
                                   │  S3 + CloudFront
        ┌──────────────────────────┴───────────────────────────────────────────┐
   /  /about  /archive  /archive/:code  /privacy  /support      →  STATIC (baked, de+en)
        │
        │  the ONLY live JS, as small hydrated islands (a few KB each):
        │   • <RemainingSlots/> + register-state   ← the one truly volatile value
        │   • language switcher (link / client i18n for the 8 long-tail locales)
        │   • newsletter + Turnstile               ← lazy, only on interaction
        │   • archive search/filter                ← filters data already in the page
        │   • countdown timer
        │
   /register/:code  /login  /app  /organizer  /speaker  /partner  /auth/**  →  the SPA (untouched)
```

**Static, baked at build (zero JS to read):** homepage content, about, privacy, support, the
whole archive grid + archive detail pages.

**The one genuinely-dynamic island — `<RemainingSlots/>`:** a ~2 KB island that fetches
`GET /api/v1/events/{code}/capacity` (CloudFront-cached, ~60 s TTL) and renders "X slots left /
waitlist / full" + the correct register CTA state. This is the entire "dynamic where really
needed." **A thin public, cacheable capacity endpoint will be added** (confirmed easy, 2026-06-02)
— do NOT reuse `my-registration`, which is auth-scoped. It is only relevant for the ≤2 active
(non-archived) events; archived events show no live slots.

**Stays in the SPA, deep-linked:** the registration wizard (a real stateful flow + Turnstile —
nothing to gain from making it static; the static "Register" CTA links to SPA `/register/:code`,
already a public SPA route that handles cold entry) and everything behind auth.

## 2. Data flow & "build once" note

The static build fetches **prod** data from `api.batbern.ch` at build time. This deliberately
gives up "build once, deploy everywhere" **for the public site** — which is correct: it is a
*content* artifact, rebuilt on content change, not an environment-agnostic app bundle. The SPA
keeps its build-once model untouched. The event/sessions/archive endpoints the homepage uses
today are already public + unauthenticated, so no new auth surface is needed for the build fetch.

**Guardrail:** if the build-time data fetch fails or returns empty, the build **fails** and the
last-good deploy stays live. Never publish a blank/stale public site. (Phase 4 enforces this.)

## 3. Deployment topology

Reuse the existing S3 + CloudFront — no new infra tier. Two artifacts now share the
distribution via path-based behaviors:
- The **static site** owns and is the default origin for the public paths
  (`/`, `/about`, `/archive*`, `/privacy`, `/support`).
- The **SPA** keeps the app paths (`/register*`, `/login`, `/auth/**`, `/organizer*`,
  `/speaker*`, `/partner*`, `/admin*`, etc.) via CloudFront cache behaviors → SPA origin/prefix.
- Cleanest layout: one bucket, static-site output at the public prefixes, SPA output under its
  own prefix with a CloudFront function mapping app paths to the SPA `index.html` fallback.
  **Phase 1 exists specifically to prove this two-origin routing on the lowest-risk pages.**

The **beta canary** (`beta.batbern.ch`, shared prod backend) is the staging ground for the whole
pipeline — publish each phase to beta, run Lighthouse/PSI, click-through, then promote to prod.

## 4. Phased delivery (each phase prod-safe; staging IS prod; verify on beta first)

### Phase 0 — Scaffold the Astro package (zero prod impact)
- New `public-site/` package in the monorepo. Astro + `@astrojs/react` + Tailwind, **sharing the
  existing `tailwind.config.js` + design tokens** and the Radix/Tailwind UI primitives (via a
  shared path/package so the static site and SPA never drift visually).
- Prove `npm run build` produces a `dist/` with a trivial static page rendering a reused
  component. No deploy. Establishes the foundation.

### Phase 1 — Static-only pages first: `/about`, `/privacy`, `/support` 🟢 lowest risk
- Zero data dependency → proves the **end-to-end pipeline incl. deploy + two-origin CloudFront
  routing** without any build-time fetch risk.
- Move the 3 components into Astro; render static; bake `de`+`en`; wire the i18n island for the
  8 long-tail locales (reuse react-i18next + existing locale JSON).
- Deploy to **beta** → verify view-source has real content pre-JS, Lighthouse, no console errors.
  Then prod: add the CloudFront behaviors so these 3 paths serve the static origin.
- `new Date().getFullYear()` in PrivacyPage → resolve at build (re-render island corrects if a
  year boundary is crossed; benign).

### Phase 2 — Homepage with baked data + slots island 🟡 the high-value one
- Build-time fetch current event + sessions; render the **real** HomePage content static. This
  needs the **data-as-props refactor**: the presentational children (event card, session list,
  speaker grid, logistics, photos, testimonials, partners) take data via props instead of hooks.
  The fetching parent (`HomePage.tsx`) already separates this, so it's contained.
- Add `<RemainingSlots/>` (the one live fetch). Register CTA deep-links to SPA `/register/:code`.
  Newsletter+Turnstile and CountdownTimer become lazy `client:` islands.
- Verify on **beta** with Lighthouse/PSI — expect the large score jump here (content in HTML,
  ~0 framework JS on the read path, TBT collapses). Then prod.

### Phase 3 — Archive + archive detail
- **Scale (confirmed 2026-06-02):** ~60 events, ~250 sessions total. Only **2 events are
  non-archived** (active/upcoming); the other ~58 archive detail pages are **truly static** —
  once an event is archived its detail never changes again. 60 events × (de+en) = 120 grid
  entries / ~120 detail pages — small; **bake the full set statically.**
- **`/archive` grid loading — bake all 60, defer images + skip off-screen render (NO API, NO
  paging, NO infinite-scroll JS).** The infinite-scroll exists only because data was fetched
  lazily; once data is baked, that reason is gone. **Reality from the screenshot (2026-06-02):**
  grid cards expand every session row, each with a **speaker avatar + company logo** — so a card
  is ~15 images (BATbern56 ≈ 7 sessions), and the archive is **~560 images + a deep DOM**, not 60
  thumbnails. Two distinct weights, each solved natively:
  - **Image BYTES → native lazy-loading.** Put `loading="lazy"` + `decoding="async"` on **every**
    image (hero, speaker avatar, company logo) and route avatars/logos through the **CDN resize
    Lambda at display size** (~48–64 px — they currently may serve full-res). The browser then
    downloads only the ~15–30 images near the viewport, **no matter how many are in the DOM.**
    Reserve width/height on every image so no CLS. Hero already does this (`EventCard.tsx:82-96`);
    extend the same to the avatar + logo components. **This is the direct answer to "many images."**
  - **Render/DOM COST → `content-visibility: auto` + `contain-intrinsic-size` per card.** With ~250
    session rows across 60 expanded cards the DOM is deep; this native CSS lets the browser **skip
    layout/paint of off-screen cards** while keeping them in the DOM (crawlable, no JS). Off-screen
    cards cost ~nothing until scrolled near. Zero-JS, the clean answer to deep-DOM render cost.
  - **Data bytes:** 60 events + ~250 sessions of card metadata ≈ tens of KB JSON (gzips small) → bake all.
  - **Result:** render all 60 cards as static HTML; lazy images stream + off-screen cards don't
    render until near. Continuous scroll, zero round-trips, zero page controls. **Drop
    `useInfiniteEvents` / `fetchNextPage` / the sentinel** (`ArchivePage.tsx:48-126,329-346`).
  - **Filter/sort/topics:** one small client island over the baked array; keep the shareable
    `?q=&topics=&sort=` URLs + SEO; bake the topic list (drop the `topicService` call).
  - **Per-card registration status:** drop `useMyRegistration` from archived cards
    (`ArchivePage.tsx:33-43`); keep only on the ≤2 active events, if at all.
  - **Escalation lever (only if `content-visibility` + lazy images still measure heavy on a low-end
    device — verify on beta first):** a render-on-scroll island appending card batches from the
    in-page array (still no API, still no paging), then windowing/virtualization. Not expected at
    this scale; decide from a beta Lighthouse/CPU-throttle measurement, not up front.
- `/archive/:code` detail: **static** for the ~58 archived events (no slots island, no live
  data). The ≤2 active events' detail pages are the dynamic ones — they carry the
  `<RemainingSlots/>` island and are re-baked on the ~12/yr content changes (same as the
  homepage). An event flips from "active/dynamic" to "archived/static" on its EVENT_COMPLETED
  → ARCHIVED transition, which is already a rebuild trigger (Phase 4).
- Verify beta → prod.

### Phase 4 — Rebuild automation + manual button + guardrails
- Wire backend auto-publish events → `repository_dispatch` / workflow → rebuild + deploy.
- Add an organizer **"Republish public site"** button (manual override).
- Guardrail: build fails on empty/failed data fetch; keep last-good deploy. Optional nightly
  safety rebuild. Add a content-assertion test (prerendered routes contain expected markers) so
  a silently-broken build can't ship.

### Phase 5 — Retire the prerender + remove public routes from the SPA
- Once the static site owns the public paths in prod and is proven: delete
  `scripts/prerender.mjs` + the paint-and-replace logic, and remove the public route components
  from the SPA bundle (shrinks the SPA too). Update the sitemap. This finally retires the
  approach that capped TBT.

## 5. Risks
- **Two-origin CloudFront routing** complexity — de-risked by doing it first on the 3 trivial
  pages (Phase 1).
- **Build coupled to prod API availability** — mitigated by the fail-build-keep-last-good guardrail.
- **Data-as-props refactor** of homepage presentational components — contained, but the main code cost.
- **Locale-switch flash for the 8 long-tail locales** — same paint-and-replace trade-off as today,
  but now scoped to long-tail locales only (de/en are baked and flash-free).
- **Design drift** between static site and SPA — mitigated by sharing the Tailwind config + tokens.
- **Capacity endpoint** may need adding as a public, CloudFront-cacheable read (don't reuse the
  auth-scoped `my-registration`).
- **New-route maintenance** — a new public route must be added to the Astro build or it 404s;
  Phase 4 content-assertion guards against silent breakage.

## 6. Cost
- New `public-site/` Astro package; ~no new infra (reuse S3+CloudFront, add path behaviors); one
  CI rebuild workflow; possibly one thin public capacity endpoint. Build time: seconds, ~12×/year.

## 7. Open questions

1. ~~**Capacity endpoint**~~ → **RESOLVED (2026-06-02):** add a thin public, CloudFront-cacheable
   `GET /api/v1/events/{code}/capacity` in Phase 2. Small BE change, confirmed easy. Only the ≤2
   active events use it.
2. ~~**Archive size**~~ → **RESOLVED (2026-06-02):** ~60 events / ~250 sessions, only 2 active.
   Small enough to **bake the full archive statically**; filter/sort/"show more" is a client
   island over the baked dataset (no archive API). No build-time pagination needed; render the
   first screenful in HTML.
3. **CloudFront layout (STILL OPEN — user deferred):** one bucket with path prefixes vs two
   origins for static-site-vs-SPA routing. Settle empirically in **Phase 1** from what's simplest
   against the current distribution.

## 8. Handoff note for the PM (epic / story creation)

This plan is ready to be broken into an epic + stories. Suggested epic shape, mapping 1:1 to the
phases in §4 (each phase is independently shippable to prod and verified on beta first):

- **Story: scaffold `public-site/` Astro package** (Phase 0) — no deploy.
- **Story: static-only pages `/about` `/privacy` `/support` + two-origin CloudFront routing**
  (Phase 1) — proves the pipeline; resolves open Q#3.
- **Story: public capacity endpoint** (thin BE, from §7 Q#1) — prereq for the homepage slots island.
- **Story: homepage baked-data + `<RemainingSlots/>` island + data-as-props refactor** (Phase 2) —
  the high-value perf win; verify Lighthouse on beta.
- **Story: archive grid + detail static + client-side filter island** (Phase 3).
- **Story: rebuild automation (publish-event hook + manual "Republish" button) + guardrails**
  (Phase 4).
- **Story: retire `scripts/prerender.mjs` + remove public routes from the SPA bundle** (Phase 5).

Carry the §0a binding decisions and the §5 risks into the stories. The data-as-props refactor
(Phase 2) and the two-origin routing (Phase 1) are the two items most worth a spike inside their
stories.
