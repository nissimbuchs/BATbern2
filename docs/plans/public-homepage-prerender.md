# Plan: SSG / prerender the public homepage (CSR → instant first paint)

**Status:** Draft (not started) — the deferred "#4" big lever from `project_homepage_perf_followup`.
**Author:** Amelia (dev agent) — 2026-06-02
**Prereq:** measure real Lighthouse/PSI on **beta.batbern.ch** first (see §7) so scope is sized from data, not guesses.

---

## 1. Why this is the highest-impact remaining lever

The public site is a **client-side-rendered (CSR) SPA**. Today's first-paint path (`web-frontend/src/main.tsx`):

1. Browser receives a near-empty `index.html` (`<div id="root">`).
2. Download + parse + execute the eager JS (~549 KB gz after the carve-outs).
3. `main.tsx` renders a **spinner**, then **`await loadRuntimeConfig()`** → a `GET /api/v1/config` **network round-trip** — this blocks `<App>` from mounting at all.
4. `<App>` mounts → `HomePage` fetches event data (another round-trip).
5. Only now does real content paint.

So FCP/LCP are structurally gated on **"all JS executed + config fetched + data fetched, *then* paint."** The bundle carve-outs (Amplify/recharts/rhf/zod/tinymce/MUI-off-public) shrink step 2 — real, but the page stays **blank until JS runs**, and the LCP element (the hero, a CDN-resized theme image) waits on config+data. Byte-shaving plateaus here.

**Prerendering puts real HTML — hero, headings, nav, layout — in the initial response**, painted *before any JS executes*; React hydrates afterward. This removes the blank period and is the change most likely to actually reach the original **PageSpeed 80+** target (prod was 25 → 59 after round 1). Carve-outs make the JS smaller; prerender makes the page **not wait for JS to paint**.

> Relationship to the other deferred item (autosplit): autosplit is a manualChunks maintainability cleanup with marginal perf upside now that the big libs are carved. **Prerender ≫ autosplit in user-felt impact.** Do prerender for the score; do autosplit only as housekeeping.

---

## 2. The design crux (read before scoping)

**The prerender environment has no backend.** At build time there is no `/api/v1/config` and no event API. Two consequences drive the whole design:

1. **`main.tsx` currently blocks the entire app on `loadRuntimeConfig()`** and shows only a spinner until it resolves. If we prerender as-is, the static HTML is *just the spinner* — useless. → We must **decouple the static shell (hero copy, nav, layout, footer, above-the-fold chrome) from the config-gated dynamic content (live event data)** so the shell renders with zero config, and only the dynamic bits suspend/skeleton until the client-side config+data arrive. **This refactor is the heart of the work**, not the prerender tooling.
2. **"Build once, deploy everywhere"** must survive: we cannot bake environment-specific config or live event data into the static HTML (it would defeat the single-artifact model and go stale). The prerendered output is the **static shell**; live data hydrates in on the client after the normal runtime-config fetch.

Net: the win is **instant static shell + chrome (huge FCP/LCP improvement)**, with live event data filling in a beat later — not a fully-static page. That's still the dominant perceived-load win for a landing page.

**Other wrinkles:**
- **10-locale i18n** (`de en fr it rm es fi nl ja gsw-BE`): which locale(s) to prerender? Mismatch → hydration flicker + SEO. Options: prerender per-locale routes, or prerender `de`+`en` and client-switch others. Decide in Phase 1 (Open Q1).
- **Hydration correctness:** prerendered markup must match React's first client render exactly or React logs hydration errors / re-paints (flicker). The shell must be deterministic (no `Date.now()`/random/locale-dependent text in the prerendered subtree unless that locale is the one prerendered).
- **CloudFront routing:** the SPA router function (`frontend-stack.ts`) rewrites extensionless URIs → `/index.html`. Prerendered routes emit `/about/index.html` etc.; the function must serve the **route-specific** prerendered HTML, not the generic shell. Small CloudFront-function change (infra, prod-safe-incremental).

**In our favour:** the public routes are already **MUI-free (Tailwind)** after follow-up #1 — no runtime CSS-in-JS to SSR, which is the single biggest thing that makes React prerender painful. Public routes in scope: `/`, `/about`, `/archive` (+`/archive/:code`), `/privacy`, `/support`. Admin/auth/portal routes stay CSR (untouched).

---

## 3. Tooling decision (pick in Phase 0)

| Option | Fit | Notes |
|---|---|---|
| **`vite-react-ssg`** | 👍 likely best | Purpose-built Vite+React SPA→SSG; per-route prerender + hydration + router integration; least app restructuring. Lean here first. |
| Custom build-time **puppeteer** prerender (react-snap-style) | possible | Crawl the running prod-build app, save HTML per route. `react-snap` is unmaintained → hand-roll a small puppeteer script. Fiddly with the config-fetch (must stub `/api/v1/config` during crawl) and hydration. |
| **`vike`** (vite-plugin-ssr) | heavier | More powerful (true SSR/SSG hybrid) but restructures routing/app — bigger commitment than needed for 5 static-ish routes. |

Decision criteria: must support **selective prerender** (only the 5 public routes, leave the rest CSR), **hydration** (not just static HTML), and a **no-API build** (the shell renders without config). Prototype on the simplest route before committing.

---

## 4. Phased delivery (each phase prod-safe; staging IS prod; verify on beta first)

### Phase 0 — Spike + tooling decision (no prod impact)
- Decouple-shell proof: prototype rendering the static shell of **one trivial route** (`/privacy` — pure static copy, no dynamic data, no config dependency) to HTML at build, with working hydration, using the chosen tool. Confirm: build produces `/privacy/index.html` with real content; client hydrates with **zero hydration warnings**; no API needed at build.
- Output: a tooling decision + a working pattern. Nothing deployed (or deploy to **beta** only).

### Phase 1 — Prerender the pure-static public routes (`/privacy`, `/support`, `/about`) 🟢 low risk
- These have no live-data dependency → lowest hydration risk. Decide the i18n strategy here (Open Q1) since these are text-heavy.
- Add the CloudFront-function change so `/about` serves `/about/index.html` (infra; prod-safe-incremental).
- **Verify on beta**, then prod. **Acceptance:** view-source shows real content pre-JS; Lighthouse FCP improves on these routes; no hydration errors.

### Phase 2 — Decouple the homepage shell from the config gate ⚠️ the core refactor
- Refactor `main.tsx` / `App` / `HomePage` so the **static shell paints without `loadRuntimeConfig()`**: hero copy + image placeholder (reserve dimensions → no CLS), nav, layout, footer render immediately; the **live event data** moves behind a suspense/skeleton boundary that resolves after the client config+data fetch. The config fetch becomes non-blocking for the shell.
- This is independently shippable as a **CSR improvement** even before prerendering the homepage (faster perceived paint: shell instead of spinner). Verify on beta → prod.

### Phase 3 — Prerender the homepage `/` 🟡 the high-value one
- With Phase 2's decoupled shell, prerender `/` to static HTML (shell only; live event data hydrates in). Reserve the hero LCP image space to avoid CLS; consider a tiny inline critical-CSS / preloaded hero so LCP fires on the prerendered hero.
- **Verify on beta** (real CloudFront + resize Lambda) with Lighthouse/PSI; compare against the pre-prerender beta baseline. Then prod.

### Phase 4 — Prerender `/archive` (+ decide `/archive/:code`) + finalize i18n
- Archive has dynamic content too → same decouple pattern. `/archive/:code` is per-event (likely stays CSR or prerender a shell).
- Finalize the i18n locale strategy across all prerendered routes.

### Phase 5 — Cleanup / guardrails
- Ensure the prerender step is wired into the deploy build (and the beta publish script). Add a check that prerendered routes contain expected content (catch a silently-broken prerender). Document the model.

---

## 5. Risks
- **Hydration mismatches** → flicker / console errors. Mitigate: deterministic shell, prerender the same locale the client first renders, test each route for zero hydration warnings.
- **CLS regression** if prerendered shell layout ≠ hydrated layout (esp. the hero image) → reserve dimensions explicitly.
- **Build complexity / "build once"**: the no-API build must stay environment-agnostic; never bake config/data into HTML.
- **CloudFront routing** for prerendered routes vs the SPA fallback — get the function logic right (route HTML vs generic index.html vs 404→index).
- **Maintenance**: a new public route must be added to the prerender list or it silently falls back to CSR (add the Phase 5 guardrail).

## 6. Cost
- No new infra (uses the existing S3+CloudFront). Adds build time (prerender step). One CloudFront-function tweak.

## 7. Sequencing / prerequisite
**Measure first.** Before committing to this architecture change: finish the in-flight gateway CORS deploy, push `perf/public-homepage-followup` to **beta** (`scripts/deploy/publish-beta-frontend.sh`), and run **Lighthouse/PSI on beta.batbern.ch** (real CloudFront + resize Lambda) to establish the true post-carve-out FCP/LCP. Size the prerender scope (and confirm it's worth it) from those numbers.

## 8. Open questions
1. **i18n:** prerender per-locale (`/de/…`, `/en/…`?) or prerender `de`+`en` and client-switch the other 8? Affects routing + SEO. (Email rule is DE+EN-only; UI is all 10 — but prerendering all 10 × 5 routes may be overkill. Likely prerender the default + `en`, client-switch rest.)
2. **Tooling:** confirm `vite-react-ssg` vs puppeteer vs vike after the Phase 0 spike.
3. **Scope of dynamic data on `/`:** is the live "current event" block worth a skeleton-then-hydrate, or is a cached/edge-rendered variant wanted later? (Start with skeleton-hydrate; edge SSR is a bigger future option.)
4. **Architect involvement:** this changes the build/deploy model — worth a Winston (architect) review of the decouple-shell + prerender approach before Phase 2.
