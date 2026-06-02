# Plan: `beta.batbern.ch` — a frontend canary on production data

**Status:** Draft (not started)
**Author:** Amelia (dev agent) — 2026-06-02
**Branch origin:** discussed while on `perf/public-homepage-followup` (the lazy-Amplify perf work is the first intended payload for beta)

---

## 1. Goal & motivation

We have **one** AWS environment — the `staging` account (188701360969) **is** production
(www.batbern.ch / api.batbern.ch / cdn.batbern.ch). There is no separate place to preview a
change against real data and the real CDN before it reaches every visitor.

The frontend is **build-once / environment-agnostic**: it ships no `VITE_` env vars and pulls
all runtime config from `GET /api/v1/config` at boot (see `web-frontend/vite.config.ts` header
and `web-frontend/src/config/runtime-config.ts`). That means we can stand up a **second
frontend** — a different static bundle behind a different hostname — that talks to the **same**
backend, Cognito pool, and database. For a **frontend-only** change (like the lazy-Amplify perf
work) this gives a faithful preview: real CDN, real images, real data → trustworthy
Lighthouse/PSI numbers, on a URL we can hand to a few people before flipping prod.

### 1.1 The non-negotiable caveat — beta is NOT a sandbox

Because beta shares the **production** backend + Cognito + RDS:

- Every action on beta hits **live production data** with **real accounts**. A delete on beta is
  a delete on prod.
- Beta is a **UI canary**, appropriate for: frontend perf, layout/CSS, client-side routing,
  bundle/loading changes, copy — anything that does not change what the backend does.
- Beta is **NOT** appropriate for: testing migrations, new/changed API contracts, destructive
  workflows, or anything you would not want executed against production.

This caveat must be stated wherever beta is documented or linked. When the work to preview is a
**backend** change, beta gives you nothing — use a real ephemeral env or staging-of-staging
instead (out of scope here).

---

## 2. Current architecture (verified 2026-06-02)

- **Frontend stack** (`infrastructure/lib/stacks/frontend-stack.ts`): one S3 bucket
  `batbern-frontend-staging` (OAC, block-public), one CloudFront `Distribution`, a `stableBucket`
  (`batbern-frontend-stable-staging`) used by the Playwright rollback gate, two CloudFront
  Functions (`staging-spa-router` rewriting extension-less/`/`-terminated URIs to `/index.html`;
  `staging-html-no-cache`), cache policies, a security-headers RHP (with CSP), `domainNames`
  `www.batbern.ch` + apex `batbern.ch`, Route53 A records, and a `BucketDeployment` with
  `distributionPaths: ['/*']`.
- **Runtime config:** `GET /api/v1/config` returns `apiBaseUrl`, `cognito`, `environment`, etc.
  A beta bundle calls the same endpoint → same backend/Cognito/data.
- **CSP** (`frontend-stack.ts` ~L193) already allows `connect-src ... https://api.batbern.ch ...`
  — a beta origin can reach the API as far as CSP is concerned.
- **Gateway CORS allowlist** (`api-gateway/.../config/SecurityConfig.java:107`) is exactly:
  `http://localhost:*`, `http://127.0.0.1:*`, `https://www.batbern.ch`, `https://batbern.ch`.
  **Any new hostname is CORS-blocked until added here** (the one backend change this needs).
- **ACM cert** (`staging-config.ts` `frontendCertificateArn`) covers `www.batbern.ch` +
  `batbern.ch` **only**. ACM certs are immutable → a new cert is required for `beta.batbern.ch`.
- **Login:** the app signs in via `amplifySignIn` (username/password / SRP), **not** the OAuth
  hosted-UI redirect. The `redirectSignIn/Out` URLs pinned to www in `config/amplify.ts` are only
  used by `signInWithRedirect` (hosted UI), which the app does not use. → password login works on
  beta without touching those URLs. (If hosted-UI/social login is ever added, beta would need its
  callback URL registered in Cognito — documented limitation, see §7.)

### 2.1 Why subdomain, not `batbern.ch/beta` (path)

| Concern | `beta.batbern.ch` (chosen) | `batbern.ch/beta` (rejected) |
|---|---|---|
| localStorage / sessionStorage | **isolated** per origin | **shared with prod** — Cognito tokens collide |
| PWA service worker | own scope per origin | one SW would control both prod + beta |
| Vite `base` + Router `basename` + SPA rewrite | unchanged (served at `/`) | all three must fork for the beta build |
| Backend CORS | needs `+1` allowlist line | none (origin stays `batbern.ch`) |
| Cert + DNS | new cert + record | none |

The path approach's only win is "no CORS/cert/DNS", but it pays for it with **shared Cognito
tokens and a shared service worker across prod and beta** — a real correctness footgun. Subdomain
isolation is worth the cert+DNS+one-CORS-line cost.

---

## 3. The main infra gotcha — resource-name collisions

`FrontendStack` derives **every** physical name from `envName` (`staging`):
`batbern-frontend-${envName}`, `batbern-frontend-stable-${envName}`, `${envName}-spa-router`,
`${envName}-html-no-cache`, `${envName}-static-assets`, `${envName}-html-no-cache` (cache),
`${envName}-seo-cache`, `${envName}-security-headers`, `${envName}-static-assets-headers`,
`logFilePrefix frontend-cloudfront/${envName}/`, and CfnOutput export names
`${envName}-Frontend*`.

A second instance built with `envName: 'staging'` would **collide on all of them** and fail to
deploy. The fix is a new optional **`variant`** discriminator on `FrontendStackProps`:

- `variant` **omitted/empty** → names are produced **exactly as today** (no diff on the prod
  stack — verified in Phase 0; this is the safety gate).
- `variant: 'beta'` → `batbern-frontend-beta`, `beta-spa-router`, `beta-static-assets`,
  `beta-FrontendUrl`, etc.

Introduce a single helper inside the stack, e.g.
`const name = (base: string) => props.variant ? \`${props.variant}-${base}\` : \`${envName}-${base}\``
for the `${envName}-...` names, and analogous logic for the two bucket names
(`batbern-frontend${variant ? '-'+variant : ''}-${envName}`). **Default path must reproduce the
current string byte-for-byte** so CDK sees no change to the live distribution/bucket (a changed
bucket name or distribution comment can force replacement of the production site).

> The beta site does **not** need a `stableBucket` or the rollback gate (it is itself the
> pre-prod check). Guard the `stableBucket` creation behind `if (!props.variant)` so beta skips it.

---

## 4. Phased delivery (each phase independently deployable, prod-safe)

Ordering follows the house rule: every phase is shippable to prod on its own and **cannot
endanger the live site**. `npm run diff:staging` is reviewed before each `deploy`.

### Phase 0 — Parameterize `FrontendStack` with `variant` (pure no-op for prod) 🟢 safety gate — ✅ DONE (commit `b98d388e`, 2026-06-02)
- Add optional `variant?: string` to `FrontendStackProps`; thread it through all name
  derivations and gate `stableBucket` on `!variant` (see §3).
- Add/extend unit tests in `infrastructure/test/unit/frontend-stack.test.ts`:
  (a) default (no variant) still produces `batbern-frontend-staging`, `staging-spa-router`, etc.;
  (b) `variant:'beta'` produces the beta-prefixed names and **no** `stableBucket`.
- **Acceptance:** `npm run diff:staging` shows **ZERO changes** to the existing `*-Frontend`
  stack. This proves the refactor is invisible to prod. Merge + (optionally) deploy — it changes
  nothing live.
- **Rollback:** trivial (revert; no live resource touched).
- **✅ Result (2026-06-02):** implemented via optional `variant` prop on `FrontendStackProps`
  (`prefix`/`bucketSuffix`/`isPrimary` discriminators; `stableBucket` + its output gated on
  primary; buckets destroyable + `PRICE_CLASS_100` for variants). 14 unit tests pass. Verified two
  ways: (1) `cdk synth BATbern-staging-Frontend` of **HEAD vs the change** → **byte-for-byte
  identical** template; (2) real `cdk diff BATbern-staging-Frontend` → *"There were no
  differences"* on all structural sections, the only delta being the `DeployWebsite` bundle asset
  hash (the separately-committed lazy-Amplify build, unrelated to this refactor). No beta stack
  instantiated yet (Phase 2).

### Phase 1 — ACM certificate for `beta.batbern.ch` (us-east-1, DNS-validated) 🟢 no traffic impact — ✅ DONE (commit `13ae9fc0`, 2026-06-02)
> **✅ Result:** cert `arn:aws:acm:us-east-1:188701360969:certificate/7d4daddf-59e8-47e8-aac3-b7db779d5839`
> requested + DNS-validated (CNAME UPSERTed into `Z08825557YYLWVHISLPY`), reached `ISSUED`, and
> pinned as `domain.betaFrontendCertificateArn` in `staging-config.ts`. Unused until Phase 2.
- **Decision (Q2): pre-create the cert and pin its ARN** — mirrors the existing
  `frontendCertificateArn` convention; no `DnsStack` change.
- Issue a cert for `beta.batbern.ch` in **us-east-1** (CloudFront requirement) via console/CLI,
  DNS-validated against the existing `batbern.ch` hosted zone (`Z08825557YYLWVHISLPY`):
  ```
  AWS_PROFILE=batbern-staging aws acm request-certificate \
    --region us-east-1 --domain-name beta.batbern.ch \
    --validation-method DNS --query CertificateArn --output text
  # then create the CNAME validation record in the batbern.ch zone and wait for ISSUED
  ```
- Pin the ARN in `staging-config.ts` as `domain.betaFrontendCertificateArn` (next to
  `frontendCertificateArn`). The cert sits **unused** until Phase 2 — zero impact on the live site.
- **Acceptance:** cert `ISSUED`; ARN committed in config. **Rollback:** delete the unused cert.

### Phase 2 — Beta `FrontendStack` instance (new bucket + new CloudFront + `beta.batbern.ch`) 🟡 new stack, prod untouched — ✅ DONE (commit `5a0bc024`, 2026-06-02)
> **✅ Result:** `BATbern-staging-FrontendBeta` deployed to the prod account (DistributionId
> `E3KG0H0UXSYH2F` / `d182gua1vp827j.cloudfront.net`, bucket `batbern-frontend-beta-staging`).
> `https://beta.batbern.ch` returns HTTP 200, serves the SPA, TLS via the pinned cert, and
> `x-robots-tag: noindex, nofollow`. Primary `*-Frontend` template re-confirmed byte-identical.
> 18 unit tests pass. API calls 403 on CORS until Phase 3.
- In `bin/batbern-infrastructure.ts`, behind a `--context betaFrontend=true` (or a
  `config.betaFrontend` flag so it never synthesizes by accident), create:
  ```
  new FrontendStack(app, `${stackPrefix}-FrontendBeta`, {
    config,
    variant: 'beta',
    domainName: 'beta.batbern.ch',
    apexDomainName: undefined,          // no apex for beta
    hostedZoneId: config.domain.hostedZoneId,
    certificateArn: config.domain.betaFrontendCertificateArn,
    crossRegionReferences: true,
  });
  ```
- **Decision (Q1): public + `noindex`, no auth gate.** Add a **`X-Robots-Tag: noindex, nofollow`**
  response header on the beta distribution (a small CloudFront response function or an extra RHP
  value, gated on `variant`) so beta never competes with www in search. No HTTP Basic auth — the
  site is openly reachable; the §1.1 shared-prod-data caveat is mitigated only by `noindex` + not
  advertising the URL, so do not link it publicly.
- This is a **separate CloudFormation stack**; the prod `*-Frontend` stack is not in its
  dependency graph and is never modified.
- After deploy, `beta.batbern.ch` serves the SPA. **API calls will 403 on CORS** until Phase 3 —
  expected; the static site + routing can still be eyeballed.
- **Acceptance:** `https://beta.batbern.ch` returns `index.html`, SPA routes resolve, TLS valid.
- **Rollback:** destroy the `*-FrontendBeta` stack (RemovalPolicy is DESTROY for non-prod variant
  buckets — confirm the beta bucket is `autoDeleteObjects: true` / `DESTROY` even though
  `isProd` is true; add `|| variant` to the destroy condition so beta is cleanly removable).

### Phase 3 — Add beta origin to gateway CORS (the one backend change) 🟡 additive, reversible
- Add `"https://beta.batbern.ch"` to `setAllowedOriginPatterns(...)` in
  `api-gateway/.../config/SecurityConfig.java:107`. Additive — does not affect existing origins.
- Update any CORS unit/integration test that asserts the allowlist contents.
- Redeploy the gateway (code-only change → fast-path/hotswap per infra CLAUDE.md).
- **Acceptance:** an authenticated request from `beta.batbern.ch` succeeds (no CORS error);
  www.batbern.ch unaffected.
- **Rollback:** remove the line, redeploy.

### Phase 4 — Publish pipeline (build-once → beta bucket) 🟢 tooling only
- A script / npm target `deploy:beta:frontend` (or a `workflow_dispatch` GitHub Action) that:
  `cd web-frontend && npm run build` → `aws s3 sync dist/ s3://batbern-frontend-beta --delete`
  → CloudFront invalidation `/*` on the beta distribution.
- **Decision (Q3): manual-only.** Triggered deliberately (`workflow_dispatch` and/or a local
  script), never on push, so publishing to beta is always an explicit act — typically from a
  feature branch (e.g. push the lazy-Amplify branch to beta for real-device Lighthouse).
- Reuse the existing GitHub-Actions S3 grant pattern (`batbern-*-${envName}` already covers
  `batbern-frontend-beta-staging`… — note: confirm the bucket name pattern matches the IAM grant in
  `cicd-stack.ts`; if the name is `batbern-frontend-beta` without the `-staging` suffix, widen the
  grant or align the bucket name).
- **Acceptance:** running the target publishes the current build to beta and it goes live within
  one invalidation cycle. **Rollback:** re-publish a previous build / `git checkout` + re-run.

### Phase 5 — Lifecycle: always-on
- **Decision (Q4): beta is a permanent, always-on canary.** Cert, DNS record, bucket, distribution,
  and the CORS line stay in place; the only routine operation is Phase 4 (re-publish a build).
- Teardown is therefore not planned, but remains clean if ever needed: destroy `*-FrontendBeta`,
  remove the Phase 3 CORS line + redeploy gateway, delete the cert + Route53 record.

---

## 5. Verification checklist (post Phase 3)
- `https://beta.batbern.ch/` loads with 0 console errors and **real** event data.
- DevTools → Network: API calls to `api.batbern.ch` return 2xx (no CORS errors).
- Login (username/password) works on beta; a logged-in action that reads prod data succeeds.
- `curl -sI https://beta.batbern.ch/ | grep -i x-robots-tag` shows `noindex`.
- www.batbern.ch is byte-for-byte unaffected (separate stack; spot-check it still loads).
- Lighthouse/PSI run against `beta.batbern.ch` for the perf numbers we actually care about.

## 6. Cost
- Negligible: one extra CloudFront distribution (pay-per-use, near-zero at canary traffic) + a
  tiny S3 bucket + one ACM cert (free). No new compute, no new backend.

## 7. Risks & limitations
- **Shared prod data/Cognito/DB** (§1.1) — the defining limitation. Beta = canary, not sandbox.
- **Hosted-UI/OAuth**: only username/password login is exercised; `signInWithRedirect` would need
  beta's callback registered in Cognito + a beta-aware `redirectSignIn` in `config/amplify.ts`.
  Out of scope; document as "not supported on beta".
- **PWA service worker** registers on the beta origin (isolated) — harmless, but a tester who
  previously installed the prod PWA will not see beta in it (different origin) — expected.
- **Name-collision regression**: Phase 0's zero-diff acceptance is the guard. Do not skip it.
- **Bucket removal policy**: the prod variant retains its bucket (`isProd`); ensure the beta
  variant is destroyable (§ Phase 2 rollback) so teardown is clean.

## 8. Resolved decisions (2026-06-02)
1. **Access:** **public + `noindex`** — openly reachable, no HTTP Basic auth. Mitigation for the
   shared-prod-data caveat is `noindex` + not advertising the URL (do not link it publicly).
2. **Cert:** **pre-create once and pin the ARN** in `staging-config.ts`
   (`domain.betaFrontendCertificateArn`) — no `DnsStack` change. See Phase 1.
3. **Publish:** **manual-only** (`workflow_dispatch` and/or local script) — never on push.
4. **Lifetime:** **always-on** permanent canary — cert/DNS/stack/CORS stay in place; teardown not
   planned (Phase 5).
