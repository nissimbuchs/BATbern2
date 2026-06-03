# Plan: SSG / prerender the public homepage (CSR → instant first paint)

**Status:** Stages 2, 1, 3 + CloudFront routing + Stage 5 guardrails/deploy-wiring IMPLEMENTED & locally verified 2026-06-02 (branch `perf/public-homepage-followup`, not committed). NEXT: commit → deploy → measure on beta (§7) → decide Stage 4 (archive).
**Author:** Amelia (dev agent) — drafted 2026-06-02, revised 2026-06-02 after a code audit + tooling spike.
**Prereq:** measure real Lighthouse/PSI on **beta.batbern.ch** before the Stage-3 homepage prerender (see §7) so scope is sized from data, not guesses.

---

## 0. Decisions resolved (2026-06-02)

These were the plan's original open questions; all now decided (with the user) and binding.

- **Tooling → build-time Playwright crawl.** NOT `vite-react-ssg`, NOT React Router 7's first-party prerender. Rationale in §3 (verified against live npm/GitHub, not guessed).
- **Render strategy → paint-and-replace, NOT hydration.** Keep `createRoot`; the prerendered HTML is a fast-painting shell that React replaces on mount. This sidesteps the #1 risk (hydration mismatch) — see §2.
- **i18n → prerender the German default locale only.** Other 9 locales stay client-rendered (they paint the German shell first, then React replaces with the visitor's locale on mount — acceptable under paint-and-replace, zero hydration warnings).
- **Bootstrap spinner → removed entirely.** The bespoke red progress-bar `LoadingScreen` in `main.tsx` is deleted. The page shell renders immediately; the BATbern **logo** spinner (`BATbernLoader`) is used only for known-slow data regions (e.g. the homepage event block, which already does this). No full-screen bootstrap spinner. (Product call by the user: "showing the page immediately and our own logo spinner only where it's slow is better; needing no spinner at all is best.")
- **Ordering → decouple FIRST.** Stage 2 (config-gate decouple) lands before any prerender because it's an independent CSR win on every route and is the structural prerequisite for a good homepage shell. Then Stage 1 (static routes), then Stage 3 (homepage).
- **Scope this pass → through Stage 3, then pause** for beta Lighthouse before deciding on archive (Stage 4).

### Key wiring findings from the audit (de-risk the decouple)

- **The public shell consumes no config.** `useConfig` consumers are: `useTurnstile` (newsletter widget — *the only one on the public path*), `usePresentationData`, `useLiveSessionControl`, `ReleaseNotesInfoBox` (all auth/organizer/presentation, rendered only after config loads).
- **The API base URL does not need the config fetch.** `getApiUrl()` (`runtime-config.ts`) derives it from hostname; `ConfigController.getApiBaseUrl()` returns exactly `<host>/api/v1` in every env (`http://localhost:{port}/api/v1` dev, `https://api.batbern.ch/api/v1` staging+prod). So `apiClient` can self-initialise `baseURL = getApiUrl() + '/api/v1'` at module load (byte-identical to config in prod) → **homepage event data loads in parallel with the config fetch, not after it.** `updateApiClientConfig()` still runs on config-resolve as the authoritative override (no-op in prod).
- **Only `useTurnstile` needs softening** for the null-config window → add `useOptionalConfig()` (returns `AppConfig | null`, no throw); treat null as "turnstile not ready/disabled". `useConfig()` keeps its throw-if-null contract for the non-public consumers.

---

## 1. Why this is the highest-impact remaining lever

The public site is a **client-side-rendered (CSR) SPA**. Today's first-paint path (`web-frontend/src/main.tsx`):

1. Browser receives a near-empty `index.html` (`<div id="root">`).
2. Download + parse + execute the eager JS (~549 KB gz after the carve-outs).
3. `main.tsx` renders a **spinner** (the red bar), then **`await loadRuntimeConfig()`** → a `GET /api/v1/config` **network round-trip** — this blocks `<App>` from mounting at all.
4. `<App>` mounts → `HomePage` fetches event data (another round-trip).
5. Only now does real content paint.

So FCP/LCP are structurally gated on **"all JS executed + config fetched + data fetched, *then* paint."** The bundle carve-outs shrink step 2 — real, but the page stays **blank until JS runs**. Byte-shaving plateaus here.

**Two changes attack this:**
- **The decouple (Stage 2)** removes step 3's blocking config round-trip from the render path and kills the red-bar spinner — the shell paints as soon as the JS runs, data loads in parallel.
- **Prerendering (Stages 1+3)** puts real HTML — hero, headings, nav, layout — in the initial response, painted *before any JS executes*. This removes the blank period and is the change most likely to actually reach the original **PageSpeed 80+** target (prod was 25 → 59 after round 1).

> Relationship to the other deferred item (autosplit): autosplit is a manualChunks maintainability cleanup with marginal perf upside now that the big libs are carved. **Prerender ≫ autosplit in user-felt impact.**

---

## 2. The design crux

**The prerender environment has no backend.** At build time there is no `/api/v1/config` and no event API. Two consequences:

1. **`main.tsx` blocks the whole app on `loadRuntimeConfig()`.** → **Stage 2 decouples this**: render `<App>` immediately, load config in a provider effect, self-init the apiClient base URL from hostname so data still loads. The shell renders with no config; only Cognito/feature-flag-dependent UI waits.
2. **"Build once, deploy everywhere" must survive:** never bake environment-specific config or live event data into the static HTML. The prerendered output is the **static shell**; live data fills in client-side after the normal runtime fetch.

**Render strategy — paint-and-replace (decisive simplification):**
Rather than `hydrateRoot` (which demands the prerendered markup match React's first client render *exactly* — fragile across our 10-locale i18n and lazy-route Suspense boundaries), we keep `createRoot`. The crawl writes the rendered DOM into the route's `index.html`; at runtime the browser **paints that real HTML at first paint**, then the JS boots and React `createRoot().render()` mounts fresh and **replaces** it. Result:
- Full FCP/LCP win (real content paints before JS executes), AND
- **Zero hydration-mismatch risk** (React doesn't hydrate, it re-renders) — the plan's biggest risk is designed out.
- Cost: a re-render on mount (the prerendered shell stays visible the whole time, so no blank flash; for non-German visitors the shell content swaps to their locale when React mounts). Acceptable for a landing page.
- The one thing we MUST get right is that the prerendered shell **looks like** the initial client render (reserve hero/LCP image dimensions → no CLS), so the replace is visually seamless.

**In our favour:** public routes are already **MUI-free (Tailwind)** — no runtime CSS-in-JS to deal with. Public routes in scope: `/`, `/about`, `/archive` (+`/archive/:code`), `/privacy`, `/support`. Admin/auth/portal routes stay CSR (untouched).

---

## 3. Tooling decision — build-time Playwright crawl (RESOLVED)

Verified against live npm + GitHub on 2026-06-02 (not the stale metadata):

| Option | Verdict | Evidence |
|---|---|---|
| **Playwright build-time crawl** ✅ CHOSEN | Version-immune, reuses an installed dep, no production-bootstrap rewrite to ship static routes | Operates on the built `dist` (serve via `vite preview`, drive headless chromium, stub `/api/**`, snapshot DOM → `route/index.html`). Indifferent to Vite/RR/helmet versions. |
| `vite-react-ssg` ❌ | Beta, single-maintainer, officially RR6 | npm `latest` = `0.9.1-beta.1`; published peers cap at `vite ^7` / `react-router-dom ^6.14.1`; Vite 8 support exists only in a 2026-05-07 source commit, unpublished. We're on **Vite 8.0.10 / RR 7.14.2 / helmet-async 2** — install needs `--force` and is unsupported. |
| RR7 first-party prerender ❌ | Most official, but disproportionate | RR7 ships `prerender` in `react-router.config.ts`, but it **requires Framework Mode** + `@react-router/dev` — a full migration off our `<BrowserRouter>` SPA, replacing parts of the tuned vite config (PWA, compression, manualChunks carve-outs, sitemap, static-copy). Treat as its own project if ever wanted. |

Crawl mechanics: `tsc && vite build` → `node scripts/prerender.mjs` which (a) serves `dist` on a local port, (b) launches chromium, (c) for each route in the prerender list: intercepts `**/api/**` (stub `/api/v1/config`; empty/404 the rest) so the app boots backend-free, navigates, waits for content, snapshots `page.content()`, writes `dist/<route>/index.html`. Build-once preserved: the snapshot is the locale-`de` shell with no env config / live data baked in.

---

## 4. Phased delivery (each phase prod-safe; staging IS prod; verify on beta before Stage 3)

### Stage 2 — Decouple the config gate + remove the bootstrap spinner ⚠️ foundation, ships first
- `main.tsx`: delete `LoadingScreen` (red bar) and the blocking `await loadRuntimeConfig()`; render `<App>` immediately under a self-loading `<ConfigProvider>`. Keep `ErrorBoundary`; do NOT blanket-blank on config failure (public pages work without config now).
- `ConfigProvider`: load config in an effect; on resolve call `updateApiClientConfig` + `setAmplifyRuntimeConfig` + set context. Accept an optional `config` prop for tests.
- `apiClient`: self-init `baseURL = getApiUrl() + '/api/v1'` at module load (export a `getDefaultApiBaseUrl()` from `runtime-config.ts`).
- `useOptionalConfig()` for `useTurnstile` (the sole public-path consumer); null ⇒ turnstile not ready/disabled.
- **Independently shippable CSR win**: every route paints its shell instead of the red bar; the homepage shows the logo spinner only for the event block. Verify on beta → prod.

### Stage 1 — Prerender the pure-static public routes (`/privacy`, `/support`, `/about`) 🟢 low risk
- No live-data dependency → lowest risk. German default locale only.
- Add `scripts/prerender.mjs` + wire the build. Make `main.tsx` prerender-aware (paint-and-replace; never overwrite a populated `#root` with a spinner — already true after Stage 2).
- Add the CloudFront-function change so `/about` serves `/about/index.html` (infra; prod-safe-incremental).
- **Verify on beta**, then prod. **Acceptance:** view-source shows real content pre-JS; Lighthouse FCP improves; no console errors.

### Stage 3 — Homepage shell refactor + prerender `/` 🟡 the high-value one
- Refactor `HomePage` so the hero/nav/footer **scaffold** renders immediately (with `BATbernLoader`/skeleton for the event-data region) instead of the current full-page spinner (`HomePage.tsx:111`). Reserve the hero LCP image space (no CLS).
- Add `/` to the prerender list → the shell paints pre-JS; live event data fills in client-side.
- **Verify on beta** (real CloudFront + resize Lambda) with Lighthouse/PSI vs the pre-prerender beta baseline. Then prod. **Then pause** per §7.

### Stage 4 (deferred to next pass) — Prerender `/archive` (+ decide `/archive/:code`)
- Archive has dynamic content too → same shell pattern. `/archive/:code` likely stays CSR or prerenders a shell. Decide after beta numbers from Stage 3.

### Stage 5 — Cleanup / guardrails
- Wire prerender into the deploy build + the beta publish script. Add a guardrail test asserting prerendered routes contain expected content (catch a silently-broken prerender). Document the model.

---

## 5. Risks
- **Locale swap flash** (German shell → visitor locale on mount): inherent to paint-and-replace + default-locale prerender. Mitigate by keeping the shell layout locale-invariant (structure/spacing identical; only text swaps).
- **CLS** if prerendered shell layout ≠ mounted layout (esp. the hero image) → reserve dimensions explicitly.
- **"Build once"**: the crawl must stub the backend and never bake config/data into HTML.
- **CloudFront routing** for prerendered routes vs the SPA fallback — serve route HTML for prerendered paths, generic `index.html` otherwise.
- **Maintenance**: a new public route must be added to the prerender list or it silently falls back to CSR (Stage 5 guardrail).
- **`new Date().getFullYear()` in `PrivacyPage`**: prerendered year is fixed at build; under paint-and-replace this self-corrects on mount (no hydration warning). Benign.

## 6. Cost
No new infra (existing S3+CloudFront). Adds build time (the crawl) + one CloudFront-function tweak. No new runtime dependency (Playwright already present).

## 7. Sequencing / prerequisite
**Measure before Stage 3.** After Stage 2 + Stage 1 land, push `perf/public-homepage-followup` to **beta** (`scripts/deploy/publish-beta-frontend.sh`) and run **Lighthouse/PSI on beta.batbern.ch** (real CloudFront + resize Lambda) to establish the true post-decouple FCP/LCP, then size the Stage-3 homepage prerender from those numbers.

## 8. Open questions — all resolved (see §0)
1. ~~i18n~~ → German default only.
2. ~~Tooling~~ → Playwright crawl.
3. ~~Dynamic data on `/`~~ → skeleton-then-client-load (paint-and-replace shell).
4. ~~Architect involvement~~ → paint-and-replace + crawl avoid the framework-mode/build-model upheaval that would have warranted it; revisit only if Stage 4 grows.
