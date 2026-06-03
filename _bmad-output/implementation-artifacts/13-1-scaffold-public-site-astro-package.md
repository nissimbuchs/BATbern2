# Story 13.1: Scaffold the `public-site/` Astro package

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

> **Prerequisite:** Story 13.0 (monorepo build toolchain → Node 22) must land first — Astro 6 requires Node ≥ 22.12.0. 13.0 is a separate PR.

## Story

As a developer on the platform team,
I want a new Astro `public-site/` package that shares the SPA's Tailwind config and design tokens and can render a reused public React component to static HTML,
so that we have a proven, visually-consistent foundation to migrate public pages onto — a move, not a rewrite — with zero production impact.

## Acceptance Criteria

**AC1 — Astro package builds to static HTML (zero prod impact)**
**Given** the monorepo
**When** the `public-site/` package is created with Astro + `@astrojs/react` + Tailwind and `npm run build` is run
**Then** it produces a `dist/` containing static HTML
**And** no existing SPA build, deploy, or route is affected (zero production impact; **NO deploy step in this story**).

**AC2 — Shared Tailwind config + tokens, no fork/drift (NFR4)**
**Given** the SPA's existing `tailwind.config.js` + design tokens (`web-frontend/src/index.css` `:root` OKLCH vars + `@theme inline`)
**When** the Astro package builds
**Then** it consumes the same config and tokens via a shared path/import
**And** there are **no duplicated or forked token values** that could drift — the token values exist in exactly one source file referenced by both packages.

**AC3 — A reused MUI-free public component renders static + hydrates as an island**
**Given** an existing MUI-free public React component (React + Tailwind + Radix)
**When** it is rendered inside an Astro page
**Then** it renders to correct static HTML at build time
**And** a `client:` directive successfully hydrates it as an island (proven with one trivial, observable example).

**AC4 — CI + Makefile build/lint/type-check wiring; no deploy**
**Given** CI and the root Makefile
**When** the package is added
**Then** build, lint, and type-check wiring runs for `public-site/` (in `.github/workflows/build.yml` and the root `Makefile` node targets)
**And** **no deploy/publish workflow is added yet** (the new CI job is NOT added to `deploy-to-staging`'s `needs:` list).

## Tasks / Subtasks

- [ ] **Task 1 — Create the `public-site/` package skeleton** (AC: #1)
  - [ ] Create top-level sibling dir `public-site/` (peer of `web-frontend/`, `infrastructure/` — NOT nested, NOT an npm workspace; see Dev Notes "Monorepo layout").
  - [ ] `package.json`: `"name": "@batbern/public-site"`, `"private": true`, `"type": "module"`. Scripts: `dev` (`astro dev`), `build` (`astro build`), `preview` (`astro preview`), `type-check` (`astro check`), `lint` (`eslint . --max-warnings 50`), `lint:ci` (same), `format` (`prettier --write`), `format:check` (`prettier --check`). (`test` optional — not required by ACs; omit unless trivial.)
  - [ ] Pin dependency **major versions to match `web-frontend`** to avoid drift: `react@^19.2`, `react-dom@^19.2`, `typescript@^6`, `tailwindcss@^4.1` (the SPA is **Tailwind v4**), `eslint@^10`, `prettier@^3`. Add Astro deps — **`astro@^6`** (current major; requires Node ≥ 22.12.0, provided by prerequisite Story 13.0) **+ `@astrojs/react@^5`** (the v5 line pairs with Astro 6, full React 19 support), `@tailwindcss/vite@^4` (the Tailwind-v4 Vite plugin — **NOT** the deprecated `@astrojs/tailwind` integration; see Dev Notes "Tailwind v4 gotcha"), `@astrojs/check` + matching `typescript` for `astro check`. (Confirm exact latest-stable patches at install via `npm create astro@latest`.)
  - [ ] Fast path to scaffold + auto-wire Tailwind v4 correctly: `npm create astro@latest` then `npx astro add react` and `npx astro add tailwind` (on Astro 5.2+/6 this installs `@tailwindcss/vite` + `tailwindcss` and registers the Vite plugin for you). Then pin/align versions per the bullet above.
  - [ ] `astro.config.mjs`: `output: 'static'` (default), `integrations: [react()]`, `vite: { plugins: [tailwindcss()] }`.
  - [ ] Add `tsconfig.json` extending `astro/tsconfigs/strict`.
  - [ ] Run `npm install` in `public-site/` (generates `package-lock.json` — commit it; CI caches on it).
  - [ ] Confirm `.gitignore` covers `public-site/dist/`, `public-site/node_modules/`, `public-site/.astro/` (extend root or add a package-local `.gitignore`).

- [ ] **Task 2 — Single-source Tailwind config + design tokens (no fork)** (AC: #2)
  - [ ] **Recommended (single source, prod-safe CSS move):** extract the token layer from `web-frontend/src/index.css` (the `:root { --background … }` OKLCH block + the `@theme inline { … }` block) into a new `web-frontend/src/styles/tokens.css`, then `@import './styles/tokens.css';` from `index.css`. This is a pure CSS move — **no value changes, no behavior change** to the SPA. Verify the SPA still builds + renders identically.
  - [ ] In `public-site/`, create the Astro global stylesheet (e.g. `src/styles/global.css`) that does `@import 'tailwindcss';`, `@import '../../web-frontend/src/styles/tokens.css';` (the single token source), and `@config '../../web-frontend/tailwind.config.js';` (reuse the v3-style JS config for color-name/spacing/radius mappings — Tailwind v4 honors `@config`).
  - [ ] **Fallback (if the extract is deferred):** `@config` the existing `web-frontend/tailwind.config.js` and `@import` the relevant token slice directly — but you MUST NOT copy/paste token values into `public-site/`. If any token value is duplicated, AC2 fails.
  - [ ] Verify: grep `public-site/` for `oklch(` / hardcoded hex from the SPA palette → must be **zero** matches (all values come from the shared source).
  - [ ] Preserve the SPA's reset behavior: the shared config sets `corePlugins.preflight: false` (line 149-151) to avoid MUI conflicts. The public components were styled against preflight-OFF — keep the same effective reset in `public-site/` so visuals match (see Dev Notes "Preflight / reset").

- [ ] **Task 3 — Prove static render + island hydration with a reused component** (AC: #3)
  - [ ] Create a demo Astro page (e.g. `src/pages/index.astro`) — this is a SCAFFOLD proof page, not a final public route.
  - [ ] **Static-render proof:** import and render `TopicBadges` from `web-frontend/src/components/public/Event/TopicBadges.tsx` (MUI-free, no hooks/context/i18n/router — plain `topics` props) with no `client:` directive. Confirm the rendered `dist/index.html` contains the badge text in view-source (pre-JS).
  - [ ] **Hydration proof (observable):** mount one reused, interactive MUI-free primitive — `Button` from `web-frontend/src/components/public/ui/button.tsx` — inside a tiny island wrapper that adds a one-line `useState` click counter, with `client:load`. Confirm clicking increments the counter in the browser (JS executed after mount = hydration proven). One example is sufficient.
  - [ ] Wire the import path so `public-site/` can reach the reused components + their utils: `cn()` at `web-frontend/src/lib/utils.ts` (clsx + tailwind-merge) and `class-variance-authority` (used by `ui/badge.tsx` / `ui/button.tsx`). Add a tsconfig path alias or relative imports; add `clsx`, `tailwind-merge`, `class-variance-authority` to `public-site/package.json` deps (they must resolve in the Astro build).
  - [ ] Confirm the badge/button Tailwind classes (`bg-primary`, `text-foreground`, zinc scale, etc.) resolve via the shared config/tokens — i.e. the rendered HTML is styled, not unstyled.

- [ ] **Task 4 — CI build/lint/type-check wiring (NO deploy)** (AC: #4)
  - [ ] `.github/workflows/build.yml`: add a `build-public-site` job mirroring `build-frontend` (job at ~lines 365-512). Copy: `actions/checkout@v4`, `actions/setup-node@v6` with `node-version: '20'` + `cache: 'npm'` + `cache-dependency-path: public-site/package-lock.json`, `working-directory: ./public-site`, then `npm ci` → `npm run lint:ci` → `npm run type-check` → `npm run build`. Reuse the git-diff change-detection pattern (lines 383-431) but grep `^public-site/`.
  - [ ] **DO NOT** add `build-public-site` to the `deploy-to-staging` `needs:` list (line ~848) — keeps deploy independent; no deploy wiring in this story.
  - [ ] Root `Makefile`: add `@cd public-site && npm ...` lines to `install-node` (~105-111), `build-node` (~126-134), `lint-node` (~199-203), `format` (~205-209), `format-check` (~211-215). (Match the existing hardcoded-`cd` pattern.) `test-node` only if a test script exists.
  - [ ] Verify `make build-node` and `make lint-node` succeed locally including `public-site/`.

- [ ] **Task 5 — Verify zero production impact** (AC: #1)
  - [ ] Confirm no change to `web-frontend/` build output (other than the token-file `@import` indirection, which must be byte-equivalent CSS), no change to `infrastructure/`, no new CloudFront/CDK/deploy artifact.
  - [ ] Confirm `web-frontend` build + type-check + a smoke of its test suite still pass after the Task 2 token extraction.
  - [ ] Add a one-paragraph `public-site/README.md` stating: scaffold only, no deploy yet, deploy/routing lands in Story 13.2.

## Dev Notes

### What this story is (and is NOT)
- **IS:** Phase 0 of the plan (`docs/plans/public-site-static-extraction.md` §4 Phase 0) — a build-only scaffold that proves Astro can render a reused MUI-free public component to static HTML and hydrate it as an island, sharing the SPA's Tailwind config + tokens, with CI/Makefile wiring. **Zero production impact, no deploy.**
- **IS NOT:** any page migration (`/about` etc. is Story 13.2), any CloudFront routing / `routerFunction` change (13.2), any S3 deploy / bucket sync (13.2), the auth-island shell (13.4), the capacity endpoint (13.3), or dev-native server orchestration (`scripts/dev/*-native.sh` — a content-build package needs no running dev server in native dev; **explicitly OUT of scope**). Do not edit `scripts/prerender.mjs` (retired in 13.7).

### Monorepo layout (verified)
- **No npm workspaces.** Root `package.json` has only a `shadcn` devDep. Each node package (`web-frontend`, `infrastructure`) is independent, installed via standalone `npm ci` in its own dir, built via hardcoded `@cd <pkg> && npm run …` in the root `Makefile`. `public-site/` follows the same pattern as a peer package. [Source: root `package.json`; `Makefile` build-node/install-node]
- **Node 22** is the baseline after prerequisite Story 13.0 (whole-repo build toolchain bump: CI `node-version: '22'`, `.nvmrc` 22, `engines >=22.12.0`). Astro 6 requires Node ≥ 22.12.0 — so **13.0 must land before this story**. The `build-public-site` CI job inherits the Node-22 baseline (no per-job override needed once 13.0 is in).
- ✅ **Astro-version decision (RESOLVED — Option B, 2026-06-02):** adopt the **current Astro 6** line (`astro@^6` + `@astrojs/react@^5`). The Node ≥ 22.12.0 requirement is satisfied by the whole-repo Node 22 bump (Story 13.0), which is being done first as its own PR (Node 20 is EOL-bound anyway). This avoids a later Astro-major migration. (Earlier draft considered Astro 5.18 to stay on Node 20; superseded by the Node 22 decision.)

### 🔴 Tailwind v4 gotcha (the #1 mistake to avoid)
- The SPA is **Tailwind v4** (`web-frontend/package.json`: `tailwindcss@^4.1.14`, `@tailwindcss/postcss@^4.2.4`; `index.css` line 6 `@import 'tailwindcss';`, line 118 `@theme inline { … }`). It does **not** use the legacy `@tailwind base/components/utilities` directives.
- For Astro + Tailwind v4, use the **`@tailwindcss/vite` plugin** in `astro.config.mjs` (`vite.plugins`), **NOT** the `@astrojs/tailwind` integration — that integration targets Tailwind v3 and is deprecated/incompatible with v4. Using it is the predictable failure mode here.
- The repo keeps a **hybrid** setup: a v3-style `tailwind.config.js` (color/spacing/radius mappings to CSS vars) **plus** v4 CSS-first tokens (`:root` + `@theme inline`). Tailwind v4 loads the JS config via the `@config` directive. The Astro package must reference **both** layers (the JS config AND the CSS token layer) — that is what "shares the config and tokens" means. [Source: `web-frontend/tailwind.config.js`; `web-frontend/src/index.css:6,10-116,118-176`]

### Design tokens — exact source (do not fork — NFR4)
- CSS token source (single source of truth for values): `web-frontend/src/index.css` — `:root { --background: oklch(…) … }` (lines ~10-116) and `@theme inline { --color-primary: var(--primary) … }` (lines 118-176). These map raw OKLCH vars → Tailwind v4 utility tokens.
- JS config source: `web-frontend/tailwind.config.js` — maps `colors.primary → var(--primary)`, the 8px spacing scale, `borderRadius`, `boxShadow`, `tailwindcss-animate` plugin, `darkMode: ['class']`, and `corePlugins.preflight: false`.
- **Both packages must reference these same files.** Recommended: extract the two CSS blocks into `web-frontend/src/styles/tokens.css` and `@import` it from both `index.css` and the Astro global CSS (a pure, value-preserving CSS move). Then `@config '../../web-frontend/tailwind.config.js'` from the Astro CSS. Zero token values may be copied into `public-site/`.

### Preflight / reset (visual-parity trap)
- The SPA disables Tailwind's reset (`corePlugins.preflight: false`) so it doesn't fight MUI. The public components were styled against preflight-OFF. **Do not "helpfully" re-enable preflight in `public-site/`** — it would change spacing/typography baselines and cause exactly the visual drift AC2/NFR4 forbids. Match the SPA's effective reset. (Note: in Tailwind v4 the `corePlugins` option is largely a no-op; control the reset via the shared config/CSS import strategy and verify rendered output matches the SPA.)

### Island-proof component (verified MUI-free, dependency-light)
- **Static-render:** `web-frontend/src/components/public/Event/TopicBadges.tsx` — props `{ topics: Topic[] | string[] }`, imports only `Badge` from `web-frontend/src/components/public/ui/badge.tsx`. No hooks, context, router, or i18n. Ideal for the static-HTML proof.
- **Hydration:** `web-frontend/src/components/public/ui/button.tsx` (Radix `Slot` + `cva`, MUI-free) wrapped in a tiny `useState` counter island with `client:load` — gives an observable click→state change so hydration is provable.
- **Shared utils these pull in:** `cn()` at `web-frontend/src/lib/utils.ts` (`clsx` + `tailwind-merge`); `class-variance-authority` (used by the `ui/*` primitives). Add `clsx`, `tailwind-merge`, `class-variance-authority` to `public-site/` deps so they resolve in the Astro build.
- **Avoid for the proof:** `CapacityIndicator`, `CountdownTimer`, `PublicFooter`, `RegistrationStatusBanner` — all call `useTranslation` (react-i18next) and would need an i18n provider (that machinery is Story 13.2's language-switcher island, not 13.1).

### Importing across package boundaries
- `public-site/` will import `.tsx` from `web-frontend/src/...` by relative path (or a tsconfig path alias). React + `@astrojs/react` v4 support React 19. Keep major versions aligned with the SPA to avoid duplicate-React / type-mismatch issues during the Astro build.

### CI wiring (verified)
- `.github/workflows/build.yml`, job `build-frontend` (~lines 365-512): `actions/setup-node@v6` `node-version '20'` + `cache: 'npm'` + `cache-dependency-path: web-frontend/package-lock.json`; `working-directory: ./web-frontend`; steps `npm ci` → `npm run lint:ci` → `npm test -- --coverage --run` → `npm run build` (build runs `tsc` for type-check). Change-detection (lines 383-431) greps `^web-frontend/` from a git diff and skips the job when unchanged.
- Mirror this as `build-public-site` (grep `^public-site/`; type-check via `npm run type-check`/`astro check` since the Astro build may not run `tsc` the same way). SonarCloud job is disabled (`if: false`) and its module list is dynamic — **no change needed**. No path-filter or "unexpected files" gate fails on a new top-level dir. **Do not** touch `deploy-to-staging`. [Source: `.github/workflows/build.yml`; `sonar-project.properties:17`]

### Project Structure Notes
- New package `public-site/` is a peer of `web-frontend/` and `infrastructure/`. It follows the same independent-install / hardcoded-Makefile-target convention. It introduces no shared-kernel, no OpenAPI, no DB, no CDK surface.
- The only `web-frontend/` change in this story is the optional token-file extraction (Task 2) — a value-preserving CSS move that must leave the SPA byte-equivalent at the token layer.

### Testing standards summary
- ACs require **build + lint + type-check** wiring, not a unit-test suite. A formal test layer is not mandated for a scaffold; if you add a smoke test, prove the build emits `dist/index.html` containing the static badge markup (a precursor to Story 13.6's content-assertion guard). Keep it minimal.
- Verification is primarily: `npm run build` produces `dist/` with real content in view-source; the island counter increments in a browser; `make build-node`/`make lint-node` green including `public-site/`; `web-frontend` still builds after the token move.

### References
- [Source: docs/prd/epic-13-public-site-static-extraction.md#Story 13.1] — ACs.
- [Source: docs/plans/public-site-static-extraction.md#4 Phase 0] — scaffold scope, "move not rewrite", share `tailwind.config.js` + tokens, no deploy.
- [Source: docs/plans/public-site-static-extraction.md#0a] — Astro decision; public components already MUI-free (React + Tailwind + Radix).
- [Source: docs/architecture/ADR-011-public-site-static-architecture.md#Implementation] — "Story 13.1 — scaffold `public-site/`; prove a reused component renders static + hydrates as an island (establishes the D6/D7 island mechanics on a trivial page)."
- [Source: docs/architecture/ADR-011-public-site-static-architecture.md#D5] — same-origin; no auth-surface change.
- [Source: web-frontend/package.json] — Tailwind v4, React 19, Vite 8, TS 6 version baselines.
- [Source: web-frontend/tailwind.config.js; web-frontend/src/index.css:6,10-176] — shared config + token source.
- [Source: web-frontend/src/components/public/Event/TopicBadges.tsx; .../ui/button.tsx; web-frontend/src/lib/utils.ts] — island-proof components + util.
- [Source: .github/workflows/build.yml:365-512,848; Makefile:105-215] — CI + Makefile extension points.
- [Web, verified 2026-06-02] Astro 6 requires Node ≥ 22.12.0 (dropped Node 18/20); Astro 5.18.0 (latest v5) supports Node 20.3+/22; `@astrojs/react` v5 (React 19) pairs with Astro 6, v4 line pairs with Astro 5 (also React 19); Tailwind v4.3 current; `@tailwindcss/vite` is the supported path (`@astrojs/tailwind` deprecated); `npx astro add tailwind` auto-wires the v4 Vite plugin. Sources: astro.build/blog/astro-6, docs.astro.build/en/guides/upgrade-to/v6, tailwindcss.com/docs/installation/framework-guides/astro, npmjs.com/package/@astrojs/react.

## Open Questions

1. **Shared-token mechanism — extract a `tokens.css` now, or relative-import for 13.1 and extract later?** The cleanest single-source approach is to pull the OKLCH `:root` block and `@theme inline` out of `web-frontend/src/index.css` into a `tokens.css` that both packages import — but that touches a SPA file (a pure CSS move, prod-safe, but still a touch). The lighter alternative is to have `public-site/` `@config` and `@import` straight from the existing `web-frontend` files with no extraction. Both satisfy "no forked values"; the question for the PM is whether we want the shared `tokens.css` refactor in this scaffold story or kept as a follow-up. The story recommends the extraction because it gives a genuine single source and de-risks every later page story.

2. **Should `public-site/` later become a proper shared UI package, or keep cross-importing `web-frontend/src`?** For 13.1 we cross-import the reused components by relative path, which is the smallest move. As more pages migrate (13.2, 13.4, 13.5) we may want a small shared `@batbern/ui` package so the dependency direction is explicit rather than reaching into the SPA's `src`. This is not needed now and would be over-engineering for a scaffold, but it is worth a deliberate decision before 13.4 rather than letting the cross-import sprawl.

3. **Do we want a CI test step for `public-site/` now, or defer until there's content worth asserting?** The epic AC asks only for build/lint/type-check wiring, and a scaffold has little to unit-test. The content-assertion guardrail (view-source contains expected markers) is formally Story 13.6's job. The question is whether to add a tiny "dist contains the static badge" smoke test now as a wiring proof, or keep CI to build/lint/type-check only and add assertions when real pages land in 13.2.

4. **Astro 5 (Node 20) vs Astro 6 (Node 22) — RESOLVED 2026-06-02 (Option B).** The current Astro major (v6) requires Node ≥ 22.12.0, and the monorepo ran Node 20. Decision: bump the **whole repo to Node 22 first** as a separate PR (prerequisite **Story 13.0**) — Node 20 is EOL-bound anyway and the bump is low-risk/build-host-only — then this story adopts the current **Astro 6 + `@astrojs/react@^5`**. This avoids a later Astro-major migration. No open action remains here beyond landing 13.0 first.

## Dev Agent Record

### Agent Model Used

(to be filled by dev-story)

### Debug Log References

### Completion Notes List

### File List
