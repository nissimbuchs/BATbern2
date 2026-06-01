# Public Homepage Performance Optimization

> On approval, copy this file to `docs/plans/public-homepage-performance.md` (per the prod-safe-incremental-plans convention) and execute phase-by-phase.

## Context

Users report the public homepage (`www.batbern.ch`, `/` route) loads in 5–7s. A PageSpeed run (mobile / Slow 4G, `PageSpeed Insights.pdf`) confirms it: **Performance 25/100**, FCP 7.4s, **LCP 15.8s**, CLS 0.529, TBT 460ms. Accessibility/Best-Practices/SEO are all 96–100, so this is purely a loading-performance problem.

Four root causes, in impact order:

1. **Images are shipped raw and oversized.** The LCP element is a **1.36 MB PNG** hero (`HeroSection.tsx`) served at full resolution; speaker photos are 337 KB at 541×542 but displayed 67×67; company logos are e.g. 1882×734 PNGs displayed at 123×48. Est. 1,943 KiB wasted. **An image-resize Lambda@Edge already exists** (`infrastructure/lib/lambda/image-resize/index.ts`) that converts to WebP and resizes via `?w=&h=&fit=` with `Cache-Control: immutable` — but the frontend only uses it for event-photo thumbnails. Everything else sends unsized URLs.
2. **No `Cache-Control` reaches the browser.** Every asset shows "Cache TTL: None" → 5,635 KiB re-downloaded on repeat visits. CloudFront *does* edge-cache (long `CachePolicy` TTLs), but the shared `SecurityHeaders` ResponseHeadersPolicy never emits `Cache-Control`, and S3 objects carry no metadata. Browser therefore never caches.
3. **Render-blocking Google Fonts.** `fonts.googleapis.com` CSS is 120 KiB / **3,030 ms** on the critical path (Inter + Noto Sans JP). Noto Sans JP is only needed for Japanese.
4. **Heavy, under-split JS.** Single `vendor` chunk ~1.84 MB; auth components (`LoginForm`, `RegistrationWizard`, `ResetPasswordForm`, `ForgotPasswordForm`) are statically imported into the entry chunk; ~566 KiB unused JS. This delays React mount, which delays hero-image discovery (LCP "resource load delay" = 2,740 ms).

**Decisions (confirmed with user):** full scope incl. infra; self-host Inter + load Noto Sans JP only for `ja` locale.

**Intended outcome:** mobile Performance from 25 → 80+, LCP < 2.5s, CLS < 0.1, repeat-visit transfer cut by ~5.6 MiB.

## Constraints

- **Staging IS production** (account 188701360969). Each phase must be independently deployable to `develop` without endangering prod.
- **PRs to `develop` run the full deploy-staging workflow with a blocking `@smoke` Playwright gate + auto-rollback** (see `project_playwright_staging_hardening`). A flaky/failed smoke = real staging rollback. Keep `@smoke` green every phase.
- Reuse existing infrastructure (image Lambda, CDN cache policy that already keys on `w/h/fit`). Do **not** add new image pipelines.
- Frontend phases (1–5) are code-only → fast-path/hotswap deploy. Phase 6 is CDK → layer-based deploy (20–30 min), highest risk, goes **last**.

---

## Phase 1 — Route images through the existing resize Lambda (frontend, highest ROI)

The single biggest win and zero infra risk — the Lambda + CDN cache policy already exist.

**Status: ✅ DONE** (commit per phase). Added `cdnImage.ts` (`buildCdnImageUrl` + `buildCdnImageSrcSet`, 12 unit tests). Applied to hero (responsive `srcset` 768/1280/1920), speaker photos (`w=160 h=160`), speaker + partner logos (`h=128`/`h=256 contain`, SVGs skipped), event-photo marquee (`w=512 h=384`). type-check + lint clean; 117 component tests green.

**New util:** `web-frontend/src/utils/cdnImage.ts`
- `buildCdnImageUrl(url, { w?, h?, fit? })` — returns `url` unchanged if it's not a `cdn.batbern.ch` raster image (skip SVGs: the Lambda rasterizes them and we want logos to stay vector) or if no `w/h` given; otherwise appends `?w=&h=&fit=`. Cap at the Lambda's `MAX_DIM=2000`.
- `buildCdnImageSrcSet(url, widths[], { fit })` — for the hero, builds a `srcset` of WebP variants.

**Apply (display size → requested size, ~2× for retina):**
- `components/public/Hero/HeroSection.tsx:185` — LCP hero. Use `srcset` (e.g. `w=768/1280/1920`, `fit=cover`) + `sizes="100vw"`, keep `fetchPriority="high"` + `width/height` + `aspect-ratio`. 1.36 MB PNG → ~120–180 KB WebP.
- `components/public/Event/SpeakerDisplay.tsx:99` (`data-testid="speaker-photo"`) — `w=160 h=160 fit=cover` (2× of 80px). Keep `loading="lazy"`.
- `components/public/Event/SpeakerDisplay.tsx:139` (logo) — `h=128 fit=contain`, skip if SVG.
- `components/public/Partners/PartnerShowcaseCard.tsx:43` — `h=256 fit=contain`, skip if SVG; **also add `width`/`height`** (currently none → CLS, see Phase 2).
- `pages/public/HomePage.tsx:174` event photos already use `?w=256&h=192&fit=cover` — refactor to the new helper for consistency.

**Verify:** DevTools Network shows `image/webp`, sizes drop ~90%; existing `?w=` event-photo behavior unchanged; `@smoke` + speaker/partner Playwright still green.

---

## Phase 2 — CLS fixes (frontend, low risk)

**Status: ✅ DONE.** Added explicit `width`/`height` (SVG intrinsic ratio ≈ 2.363:1) to the BATbern logo `<img>` in `PublicNavigation` (189×80), `AppHeader` (95×40), `MobileDrawer` (85×36), and to the `TestimonialCard` avatar (48×48 + `loading="lazy"`). Partner-card logos already have space reserved by the fixed-height card (`max-h-32` inside `h-48`), so no fixed dims (variable aspect ratios). type-check clean; 82 nav/testimonial tests green.

**Phase 2b — CLS root cause (added 2026-06-01).** A `LayoutShift` PerformanceObserver against the production preview (mobile viewport) showed the entire CLS 0.529 was a **single 0.475 shift of the `<footer>`** at ~2.3s — NOT the images sized above. Cause: `HomePage` renders a short `py-24` loading placeholder, so the footer sits ~300px down; when the event data resolves and the real `min-h-screen` hero + sections mount, the footer is shoved far down. Fix: give the loading and error states `min-h-screen` so the footer starts below the fold where it lands. **Re-measured: CLS 0.475 → 0.000.** This is the dominant CLS fix; the image-dimension work above is secondary hardening.

CLS 0.529 comes from images without intrinsic dimensions and late content pop-in.

- Add explicit `width`/`height` to logo `<img>`s lacking them: `PublicNavigation.tsx:75` (`/BATbern_color_logo.svg`), `AppHeader.tsx`, `MobileDrawer.tsx`, `PartnerShowcaseCard.tsx`, `TestimonialCard.tsx` avatar.
- Reserve vertical space (min-height / aspect-ratio) for async-loaded sections on the homepage (speaker grid, partner marquee, event-photos marquee in `HomePage.tsx`) so they don't shift the footer when data resolves.
- Font-swap CLS is addressed in Phase 3 (`size-adjust`).

**Verify:** Lighthouse CLS < 0.1 locally; visually confirm no jump as data/fonts load.

---

## Phase 3 — Remove dead render-blocking Google Fonts (frontend)

**Status: ✅ DONE.** **Scope changed after investigation** (confirmed with user): the loaded Google Fonts (Inter + Noto Sans JP) were **never applied** — the body, Tailwind `sans`, and MUI theme all resolve to `'Helvetica Neue'`/`system-ui`, and nothing in `src/` references Inter or Noto. So self-hosting Inter would add bytes for an unused font. Instead **removed** the render-blocking Google Fonts `<link>` + both preconnects from `index.html`, and dropped the now-dead `google-fonts-stylesheets`/`google-fonts-webfonts` Workbox `runtimeCaching` entries from `vite.config.ts`. Zero visual change; eliminates the ~120 KiB / ~3,030 ms render-blocking request, the 119 KiB unused CSS, and the font-swap CLS. Build verified: 0 `googleapis`/`gstatic` references in `dist/`.

CSP `font-src`/`style-src` in `frontend-stack.ts` still list the Google Fonts origins — harmless (nothing requests them); tightened in Phase 6.

Removes the 3,030 ms render-blocking 3rd-party request.

**Verify:** no `fonts.googleapis.com` request in Network; typography unchanged (system fonts); FCP drops; CLS drops (no swap reflow).

---

## Phase 4 — JS code-splitting & bundle reduction (frontend)

**Status: ✅ DONE (conservative).** Lazy-loaded the 5 previously-eager auth forms (`LoginForm`, `ForgotPasswordForm`, `ResetPasswordForm`, `RegistrationWizard`, `EmailVerification`) via `React.lazy` — they were the only non-page eager imports in `App.tsx` and the public homepage never needs them. They now ship as small standalone chunks (≤3 KB gzip each); the entry `index` chunk dropped ~304 → ~297 KB gzip (raw 1247 → 1217 KB). All render inside the existing route `<Suspense>`. type-check clean; 90 App/auth tests green.

**Intentionally NOT done:** splitting the single `~1.84 MB vendor` chunk. `vite.config.ts:216-232` documents that one-vendor-chunk is deliberate — finer splitting reintroduces the `@emotion`/`@mui` CJS→ESM factory-boundary init-order crash (TDZ / "Cannot set properties of undefined"). Against the auto-rollback staging gate that gamble isn't worth a few KB. The remaining homepage JS cost (`vendor` 573 KB gzip + `vendor-mui` 157 KB, both loaded because the root wraps everything in MUI `ThemeProvider`) would require separating the public app from the authed app — a larger refactor out of scope here. Skipped `rollup-plugin-visualizer` to avoid a lockfile change; sizes read from build output.

Smaller critical JS → React mounts sooner → earlier hero discovery (helps LCP delay) + lower TBT.

- **Lazy-load auth components** currently static in `App.tsx:18-22` (`LoginForm`, `ForgotPasswordForm`, `ResetPasswordForm`, `RegistrationWizard`) via `React.lazy` + `Suspense` — they're not needed for the homepage.
- Audit that heavy libs (`tinymce`, `tone`, `d3`, `recharts`, `framer-motion`) are imported **only** by already-lazy non-public routes, never pulled into the entry/`vendor` chunk. Split into async chunks where they leak in.
- Revisit `manualChunks` in `vite.config.ts:244-260` — split safe vendors out of the single ~1.84 MB `vendor` chunk, respecting the documented CJS/ESM factory-boundary caveat (test each split; don't regress the `@mui`/`@emotion` grouping).
- Add `rollup-plugin-visualizer` (dev-only) to measure before/after; record sizes in the plan doc.

**Verify:** entry + homepage-critical JS shrinks (target the 566 KiB unused-JS finding); `npm run build` clean; full app routes still load (lazy auth/admin verified via Playwright).

---

## Phase 5 — LCP discoverability & preconnect (frontend)

**Status: ✅ DONE.** Added `<link rel="preconnect" href="https://cdn.batbern.ch">` + a `dns-prefetch` fallback to `index.html`, so the TLS handshake to the media CDN is warm before JS resolves the hero URL (report lists this origin as a preconnect candidate, ~340 ms LCP saving). No `crossorigin` — hero/logo `<img>` requests are not CORS. Build verified: both tags present in `dist/index.html`.

**Skipped (optional):** injecting a `<link rel="preload" as="image">` for the dynamic hero once `event.themeImageUrl` resolves. With `fetchPriority="high"` already set, the hero now a small WebP (Phase 1), and the connection pre-warmed, the marginal gain didn't justify the added moving part (helmet head injection on a per-event URL).

**Verify:** LCP "resource load delay" drops in a fresh Lighthouse run.

---

## Phase 6 — Infra: viewer `Cache-Control` + verify compression (CDK, prod deploy, LAST)

**Status: ✅ CODE DONE — DEPLOY PENDING.** `frontend-stack.ts`: extracted the shared `securityHeadersBehavior` + `baseCustomHeaders` into consts, kept the existing `SecurityHeaders` policy for HTML/SEO (no Cache-Control → index.html stays uncached), and added a second `StaticAssetsHeaders` policy (security headers + `Cache-Control: public, max-age=31536000, immutable`) attached to `/assets/*`, `/*.js`, `/*.css`, `/static/*` only. `storage-stack.ts`: added a `ContentCacheHeaders` ResponseHeadersPolicy with the same immutable Cache-Control on the CDN default behavior (covers pass-through originals/SVGs the resize Lambda doesn't touch). CDK `tsc` compiles; **20 stack tests pass** incl. 5 new assertions (Cache-Control present on static-assets + CDN policies, absent on the HTML policy, 2 distinct frontend policies).

**Deferred to keep this risky deploy minimal:** (1) CSP `font-src`/`style-src` tightening — leaving the now-unused Google Fonts origins is strictly safe (nothing requests them); removing risks a CSP regression that could trigger auto-rollback, not worth it in the same deploy. (2) `BucketDeployment` `cacheControl` — redundant since the ResponseHeadersPolicy overrides at CloudFront and the bucket is OAC-locked (never served directly).

**⚠️ Deploy is a real prod CloudFront update (layer-based, ~20–30 min) and not yet run.** Before deploy: review `cd infrastructure && npm run diff:staging`. After deploy verify with the curl checks below.

Recovers the 5,635 KiB repeat-visit waste. Riskiest (touches live CloudFront) → ships last, alone.

**`infrastructure/lib/stacks/frontend-stack.ts`:**
- A CloudFront behavior allows only one ResponseHeadersPolicy, and the current one is shared with the no-cache HTML behavior — so create a **second** policy `staticAssetsHeaders`: same `securityHeadersBehavior` + `customHeadersBehavior` as `SecurityHeaders` **plus** `{ header: 'Cache-Control', value: 'public, max-age=31536000, immutable', override: true }`.
- Attach `staticAssetsHeaders` to the hashed-asset behaviors only: `/assets/*` (266), `/*.js` (275), `/*.css` (284), `/static/*` (257). **Leave the default (HTML) behavior** on the existing no-cache policy + `htmlNoCacheFunction`.
- Optionally set `cacheControl` on the `BucketDeployment` (line ~382) as S3-metadata belt-and-suspenders.

**`infrastructure/lib/stacks/storage-stack.ts` (cdn.batbern.ch):**
- Add a ResponseHeadersPolicy to the default behavior with `Cache-Control: public, max-age=31536000, immutable` (media keys are content-addressed UUIDs → effectively immutable). The Lambda already sets this on resized responses; this covers pass-through originals/SVGs that currently get nothing.

**Compression:** PageSpeed flagged "No compression applied" on the document despite `compress:true`. After deploy, `curl -sI` the HTML and an asset and confirm `content-encoding: br|gzip`; if HTML is uncompressed, investigate the `htmlNoCacheFunction` / content-type (likely a non-issue once asset headers land, but verify).

- Tighten CSP `font-src`/`style-src` to drop Google Fonts origins (safe now that Phase 3 self-hosts).
- Add/extend CDK unit tests (`infrastructure/test/unit/frontend-stack.test.ts`, `storage-stack.test.ts`) asserting the `Cache-Control` custom header on static behaviors and the no-cache HTML behavior.

**Verify:** `npm run diff:staging` reviewed before deploy; post-deploy `curl -sI https://www.batbern.ch/assets/index-*.js` shows `cache-control: public, max-age=31536000, immutable`; HTML still `no-cache`; `curl -sI https://cdn.batbern.ch/<media>` shows immutable; repeat-visit PageSpeed transfer drops ~5.6 MiB.

---

## Overall Verification

- **Per phase:** `cd web-frontend && npm run build && npm run lint && npm run type-check`; run `@smoke` before merge (`scripts/ci/run-playwright-tests.sh staging --scope smoke`) — staging gate auto-rollback makes this mandatory.
- **End-to-end:** re-run PageSpeed on `https://batbern.ch/` (mobile). Targets: Performance 80+, LCP < 2.5s, CLS < 0.1, TBT < 200ms. Compare first-visit vs repeat-visit transfer (Phase 6 win).
- **Bundle:** record `rollup-plugin-visualizer` before/after sizes in the plan doc.
- **Doc drift:** per `CLAUDE.md`, any infra/behavior change consults `.github/doc-drift-mappings.yml`; pure perf refactors get `[no-doc]`.

## Critical Files

| File | Phase |
|---|---|
| `web-frontend/src/utils/cdnImage.ts` (new) | 1 |
| `web-frontend/src/components/public/Hero/HeroSection.tsx` | 1 |
| `web-frontend/src/components/public/Event/SpeakerDisplay.tsx` | 1 |
| `web-frontend/src/components/public/Partners/PartnerShowcaseCard.tsx` | 1,2 |
| `web-frontend/src/pages/public/HomePage.tsx` | 1,2,5 |
| `web-frontend/src/components/public/Navigation/PublicNavigation.tsx` + AppHeader/MobileDrawer | 2 |
| `web-frontend/index.html` | 3,5 |
| `web-frontend/src/index.css`, `tailwind.config.js`, MUI theme, `src/i18n/config.ts` | 3 |
| `web-frontend/vite.config.ts` (manualChunks, PWA caching, visualizer) | 3,4 |
| `web-frontend/src/App.tsx` (lazy auth routes) | 4 |
| `infrastructure/lib/stacks/frontend-stack.ts` | 6 |
| `infrastructure/lib/stacks/storage-stack.ts` | 6 |
| `infrastructure/test/unit/{frontend,storage}-stack.test.ts` | 6 |
| `infrastructure/lib/lambda/image-resize/index.ts` (reference only — no change) | 1 |
