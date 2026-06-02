---
stepsCompleted: [1, 2, 3]
inputDocuments:
  - docs/epics.md
  - docs/plans/public-site-static-extraction.md
  - infrastructure/lib/stacks/frontend-stack.ts
  - docs/architecture/ADR-008-simplified-api-gateway-routing.md
  - docs/architecture/ADR-010-federated-identity-via-cognito.md
  - web-frontend/src/main.tsx
  - web-frontend/src/App.tsx
  - web-frontend/src/contexts/AuthContext.tsx
  - web-frontend/src/config/runtime-config.ts
  - web-frontend/src/config/amplify.ts
---

# ADR-011: Public Site Static Extraction — CloudFront Routing & Island Auth-Bootstrap

**Status**: Accepted (implementation pending — tracked in Epic 13)
**Date**: 2026-06-02
**Decision Makers**: Nissim Buchs (owner), Architecture (John/PM facilitating the focused pass)
**Related ADRs**: ADR-008 (Simplified API Gateway — CORS/edge single-layer), ADR-010 (Federated Identity via Cognito — JWT/session contract), ADR-003 (Meaningful Identifiers — `eventCode` lookups)
**Related Plan**: `docs/plans/public-site-static-extraction.md` (the source plan; this ADR ratifies its two open design questions)
**Related Epic**: `docs/epics.md` → Epic 13 (Stories 13.1, 13.2, 13.4)
**Affects**: `infrastructure/lib/stacks/frontend-stack.ts` (CloudFront Function), the new `public-site/` Astro package, `scripts/deploy/publish-beta-frontend.sh`

## Context

Epic 13 extracts the BATbern public read-path (`/`, `/about`, `/archive`, `/archive/:code`,
`/privacy`, `/support`) into a static Astro build that bakes the (rarely-changing, ~12×/year)
content into HTML, keeps the volatile/auth surfaces as small hydrated islands, and leaves the
registration wizard's stateful path + everything behind auth in the existing SPA. The source
plan left two design questions open for this focused pass:

- **Q#3 — CloudFront layout:** "one bucket with path prefixes vs two origins" for routing
  static-site-vs-SPA traffic on the shared distribution.
- **Island auth-bootstrap:** how `useAuth()` / `useMyRegistration()` work inside isolated
  hydrated islands without booting the whole SPA — flagged as the main hydration risk.

Two findings from a code audit (2026-06-02) collapsed most of the uncertainty:

1. **The "two origins vs one bucket" dichotomy is false — the repo already runs the answer.**
   `frontend-stack.ts` (lines 120–160) ships a CloudFront **Function** (`routerFunction`,
   `viewer-request`) that already rewrites the prerendered public routes to baked HTML:
   `/` → `/home/index.html`, and exact matches for `/privacy`, `/about`, `/support` →
   `/<route>/index.html`. Everything else extensionless falls through to the SPA's neutral
   `/index.html` shell. `403/404 → /index.html` error responses (lines 412–425) are a built-in
   graceful-degradation safety net: a missing baked object silently serves the CSR shell. The
   homepage is already parked at `/home/index.html` specifically so it does **not** collide with
   the SPA fallback `/index.html`. This is **one bucket, one distribution** today — the exact
   shape the plan was deliberating, already in production.

2. **Same-origin Cognito session sharing makes islands cheap.** The static site is served from
   the **same origin** as the SPA (`www.batbern.ch`, the same CloudFront distribution). Amplify v6
   stores Cognito tokens in **origin-scoped `localStorage`/`sessionStorage`**, so a separately-
   bootstrapped island on that origin reads the **same session** with no cross-origin/cookie work.
   Amplify is **lazy** — `ensureAmplifyConfigured()` (amplify.ts) only loads the ~426 KB library
   on an auth path, gated by `hasCognitoSession()` (cognitoSession.ts) which scans storage first.
   Runtime config (`/api/v1/config`) loads in the background via `ConfigProvider` (main.tsx) and
   **does not block render**. So anonymous visitors and crawlers pay ~0 auth JS; only logged-in
   visitors (who have tokens in storage) trigger Amplify.

**Hard constraints carried in:** the staging account *is* production (every CloudFront change is a
prod change → deploy via the beta canary first, per `docs/plans/beta-frontend-canary.md`); CORS and
edge concerns are single-layer at the AWS API Gateway only (ADR-008); the JWT/session contract is
owned by Cognito and must not change (ADR-010); public endpoints are looked up by `eventCode`
(ADR-003).

## Decision

### D1 — One bucket, one distribution; extend the existing CloudFront Function (reject two origins)
The static site and the SPA continue to share **one S3 bucket and one CloudFront distribution**.
Routing is done by **extending the existing `routerFunction` rewrite table** in
`frontend-stack.ts` — not by adding a second origin, a second distribution, or origin-group
failover. A second origin would duplicate cache policies, security headers (the CSP/HSTS block at
lines 221–258), OAC, and logging for zero benefit; the Function approach is the repo's proven
pattern and keeps the diff to a few lines of inline JS.

### D2 — Archive needs a prefix rewrite, not exact-match
The current rewrite list is exact-match (`/privacy`, `/about`, `/support`). Archive adds two
shapes: `/archive` → `/archive/index.html` (the baked grid) and the **dynamic** `/archive/<code>`
→ `/archive/<code>/index.html` (per-event baked detail). The Function gains a **prefix rule**: an
extensionless URI under `/archive/` that is not the bare grid rewrites to `<uri>/index.html`. This
stays well within the CloudFront Function 10 KB limit. Unknown event codes whose objects don't
exist fall through the `404 → /index.html` net (D4) to the SPA — safe by construction.

### D3 — Build-output layout: two `dist/` trees merged into one bucket, no collision
The Astro `public-site/dist/` and the SPA `web-frontend/dist/` are **both synced into the same
frontend bucket**, with disjoint keys:
- Astro public pages land at their own path dirs: `/home/index.html` (homepage), `/about/…`,
  `/privacy/…`, `/support/…`, `/archive/index.html`, `/archive/<code>/index.html`, plus their
  baked `de`/`en` variants and hashed island assets.
- The SPA keeps the bucket's neutral **`/index.html`** as the universal fallback shell, plus its
  hashed `/assets/*`, `/static/*`, `/*.js`, `/*.css` (already content-addressed, immutable-cached).
- **`prune` semantics:** the current single `BucketDeployment` runs `prune: true` (frontend-stack
  line 488) and would wipe the Astro objects (and vice-versa). The deploy pipeline must sync the
  two trees so neither prunes the other — either a single combined upload of a merged tree, or
  per-prefix syncs with prune scoped to each tree's own prefixes. This is the **one deployment
  mechanic that must be designed in Story 13.2** (it is also why 13.2 is the routing-proving story).

### D4 — Keep `404/403 → /index.html` as the graceful-degradation guardrail
The existing error responses stay. A missing baked page (new public route not yet built, an
archive code with no object, a half-finished deploy) degrades to the SPA CSR shell rather than a
hard 404. This is the same property the current `routerFunction` comment relies on, and it lets the
**CloudFront Function be deployed before the Astro pages exist** (D8).

### D5 — Same-origin: no auth-surface change, deploy through the existing pipeline
Because the static site is the same origin as the SPA, the Cognito session, the JWT contract
(ADR-010), and CORS/edge config (ADR-008) are **untouched**. No new callback URL, no new CORS
origin (beyond the already-allow-listed `beta.batbern.ch`), no cookie-domain work. The static
artifact ships through the **existing frontend deploy + beta-canary** path
(`scripts/deploy/publish-beta-frontend.sh`), verified on `beta.batbern.ch` before prod.

### D6 — Auth islands: ONE interactive-shell React root (not many small auth roots)
The auth-dependent surfaces (role-aware `PublicNavigation` + the hero registration cluster) hydrate
as a **single Astro island = one React root** wrapping the minimal provider subset, mounted
`client:load`:

```
<ErrorBoundary>
  <ConfigProvider>                 // background /api/v1/config; non-blocking
    <QueryClientProvider>          // one TanStack Query cache for the shell
      <AuthProvider>               // one session-restore, one /users/me hydration
        { PublicNavigation + Hero registration cluster }
      </AuthProvider>
    </QueryClientProvider>
  </ConfigProvider>
</ErrorBoundary>
```
plus i18n initialized synchronously (`import './i18n/config'`) exactly as `main.tsx` does. This was
chosen over "many small islands + a shared singleton store": it needs **no refactor of
`AuthContext`** (the per-root `AuthProvider`/`QueryClient` work as-is), runs session-restore and
`/users/me` **once**, and is simplest. The trade-off (a larger hydration boundary → more JS
hydrated → higher TBT) is bounded because Amplify still lazy-loads only for logged-in users, and is
**measured on beta in the Story 13.4 spike**; if TBT is unacceptable we revisit the singleton split.

### D7 — Public-only widgets are separate lazy islands WITHOUT the auth providers
Widgets that need no auth — `<RemainingSlots/>`/capacity (public `/capacity`, ADR-003 by
`eventCode`), the CountdownTimer, the newsletter+Turnstile form, and the archive filter/sort —
hydrate as their own small islands (`client:visible` / `client:idle`), **outside** the auth shell.
They never mount `AuthProvider` and never load Amplify, keeping the static regions between them as
pure baked HTML. (The hero's register-CTA *state*, which combines live capacity + per-user
registration, lives inside the D6 shell where both are available.)

### D8 — Ship the CloudFront Function change first; it is backward-compatible
The `routerFunction` extension (D1/D2) is deployed **before** the Astro pages exist. Until the baked
objects are present, the new rewrites resolve to missing S3 objects and fall through `404 →
/index.html` (D4) to the unchanged SPA — so the infra change is a no-op for users until content
lands. This decouples the (CDK, infra-layer) Function deploy from the (publish-pipeline, content)
Astro deploy and removes ordering risk.

## Consequences

**Positive**
- Q#3 resolved with the **smallest possible infra change** — a few lines in one existing CloudFront
  Function; no new origin, distribution, cache policy, OAC, or headers policy to maintain or drift.
- The island auth model needs **zero changes to the Cognito/JWT contract** (ADR-010) and **zero
  CORS/edge changes** (ADR-008); same-origin session sharing is automatic.
- Anonymous visitors and crawlers (the dominant traffic, and the SEO target) load **no Amplify and
  no auth JS** — the `hasCognitoSession()` + lazy-Amplify gate already guarantees this.
- The `404 → /index.html` net makes every step **fail-safe**: missing/incomplete bakes degrade to
  the working SPA shell, never a hard error; the Function can ship ahead of content.
- One React root for the auth shell means **one** session-restore and **one** `/users/me` — no
  duplicate-fetch fan-out, no `AuthContext` refactor.

**Negative / risk**
- **`prune` collision (D3)** is the sharpest implementation hazard: the two `dist/` trees share one
  bucket and the current deploy prunes. Mis-scoped prune wipes one site. Mitigated by designing the
  sync in Story 13.2 and proving it on beta on the lowest-risk pages first.
- **Larger hydration boundary (D6)** raises TBT versus a maximally-split island layout. Bounded by
  lazy Amplify; **must be measured on beta in the 13.4 spike**, with the singleton-split as the
  documented fallback.
- **CloudFront Function 10 KB limit** — the prefix rule keeps it small, but future public routes add
  lines; if it ever approaches the limit, promote to a CloudFront Function v2 / Lambda@Edge (not
  expected at this route count).
- **New-route maintenance** — a new public route must be added to both the Astro build and the
  Function rewrite table or it 404s→SPA (degrades, doesn't break). Epic 13's Phase-4 content-
  assertion test (Story 13.6) guards against silent gaps.

**Neutral**
- The beta canary already allow-lists at the edge and shares the prod backend, so it is the exact
  verification surface for both decisions with no extra setup.

## Implementation

Maps onto Epic 13 stories (each independently prod-shippable, beta-first):
- **Story 13.1** — scaffold `public-site/` Astro package; prove a reused component renders static +
  hydrates as an island (establishes the D6/D7 island mechanics on a trivial page).
- **Story 13.2** — the **routing-proving** story: extend `routerFunction` (D1/D2), design the
  two-tree bucket sync without prune collision (D3), keep the `404` net (D4), deploy the Function
  ahead of content (D8), and bake `/about` `/privacy` `/support` + the role-aware nav island (D6) on
  the lowest-risk pages. Verifies Q#3 end-to-end on beta.
- **Story 13.4** — the homepage interactive-shell island (D6) + public-only islands (D7); the
  **hydration spike** measures the D6 TBT trade-off on beta and confirms or revises the single-root
  choice.

## Alternatives considered

- **Two origins / second CloudFront distribution for the static site** — rejected (D1). Duplicates
  cache policies, the shared CSP/HSTS security-headers block, OAC, and logging; adds an origin-group
  or host-routing layer for no benefit over the existing one-Function pattern. The repo already
  proves one-bucket routing in production.
- **A separate `static.batbern.ch` (or path-prefixed `/s/*`) sub-surface** — rejected. A different
  origin/host would break the same-origin Cognito session sharing (D5), forcing cross-origin auth
  plumbing and a new CORS origin — the exact cost the same-origin finding avoids.
- **Many small auth islands + a shared singleton store (module-level `authStore` + `queryClient`)**
  — considered and deferred (D6). Maximizes static HTML / minimizes TBT, but requires refactoring
  `AuthContext` to read a singleton so N roots dedupe session-restore. Kept as the documented
  fallback if the single-root shell's TBT proves too high on the 13.4 beta measurement.
- **Server-side rendering the public site (Astro SSR / a Node origin)** — rejected. The content
  changes ~12×/year; a static bake + rebuild-on-publish (Epic 13 Phase 4) is cheaper, has no runtime
  origin to operate, and the `404` net already covers freshness gaps.
