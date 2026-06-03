# Story 13.0: Bump the monorepo build toolchain to Node 22

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a developer on the platform team,
I want the monorepo's **build/CI/dev** Node.js baseline raised from Node 20 to Node 22 (LTS) across all workflows, the dev Docker image, and codified version pins,
so that the toolchain is on a supported LTS (Node 20 is EOL-bound in 2026) and the upcoming Astro `public-site/` package (Story 13.1) can adopt the current Astro 6 line, which requires Node ≥ 22.12.0.

> **Separate PR / prerequisite:** this is a standalone monorepo-hygiene change shipped as its **own PR**, landing **before** Story 13.1. It is a build-host change only — it does **not** touch any deployed runtime (Java services, AWS Lambda runtimes, ECS images).

## Acceptance Criteria

**AC1 — All CI workflows run on Node 22**
**Given** the GitHub Actions workflows that set up Node
**When** they run
**Then** every active `actions/setup-node` step uses `node-version: '22'` (the 7 active workflows; the 4 `.disabled` workflows are bumped too for consistency)
**And** the full build + test matrix passes green on Node 22 (web-frontend Vitest, infrastructure Jest, API-contract validation, deploy/promote pipelines).

**AC2 — Dev container + codified pins updated**
**Given** the local/dev toolchain
**When** the baseline is raised
**Then** `web-frontend/Dockerfile.dev` uses `node:22-alpine`
**And** a root `.nvmrc` (`22`) is added and `"engines": { "node": ">=22.12.0" }` is added to `web-frontend/package.json` and `infrastructure/package.json` (none exist today — this codifies the new floor and prevents silent drift)
**And** `scripts/dev/start-all-native.sh`'s Node-version guard accepts 22 (it already requires `>= 20`; update the message/threshold to 22).

**AC3 — Deployed runtimes are explicitly NOT changed (orthogonal)**
**Given** the AWS Lambda runtimes and bundling
**When** this story is implemented
**Then** the Lambda `Runtime.NODEJS_20_X` / `NODEJS_18_X` declarations, the `storage-stack.ts` esbuild `--target=node20`, and all ECS/Java images are **left unchanged**
**And** the story documents that these are separate, prod-affecting concerns to be handled (if ever) in their own change — a build-host bump must not alter deployed-runtime behavior.

**AC4 — Docs reflect the new baseline**
**Given** `CLAUDE.md` states "Node.js 20+" in prerequisites
**When** the baseline changes
**Then** `CLAUDE.md` (Local Development "Prerequisites") is updated to Node.js 22+
**And** the change is doc-drift compliant (`.github/doc-drift-mappings.yml` consulted; `[no-doc]` not used since a doc IS updated).

## Tasks / Subtasks

- [ ] **Task 1 — Bump CI workflow Node version** (AC: #1)
  - [ ] In each ACTIVE workflow, change `node-version: '20'` → `'22'`:
    - `.github/workflows/build.yml` (lines 437, 587, 806)
    - `.github/workflows/deploy-staging.yml` (lines 66, 1592)
    - `.github/workflows/deploy-production.yml` (line 174)
    - `.github/workflows/promote-to-production.yml` (lines 175, 254)
    - `.github/workflows/nightly-e2e.yml` (line 47)
    - `.github/workflows/sync-versions.yml` (line 54)
    - `.github/workflows/api-contract-validation.yml` (line 33)
  - [ ] Bump the 4 DISABLED workflows too (consistency; non-blocking): `build-reports.yml.disabled`, `codeql-analysis.yml.disabled`, `deploy-dev.yml.disabled`, `security-scan.yml.disabled`.
  - [ ] `cache-dependency-path` and the rest of each `setup-node` block are unchanged.

- [ ] **Task 2 — Dev image + version pins** (AC: #2)
  - [ ] `web-frontend/Dockerfile.dev:1`: `FROM node:20-alpine` → `FROM node:22-alpine`.
  - [ ] Add root `.nvmrc` containing `22`.
  - [ ] Add `"engines": { "node": ">=22.12.0" }` to `web-frontend/package.json` and `infrastructure/package.json`. (Pick the floor Astro 6 requires; ≥22.12.0 is safe and current.)
  - [ ] `scripts/dev/start-all-native.sh` (~lines 101-114): raise the guard from `< 20` to `< 22` and update the message text.

- [ ] **Task 3 — Validate on Node 22** (AC: #1)
  - [ ] Locally (Node 22): `make install` → `make build-node` → `make test-node`. Confirm web-frontend Vitest + infrastructure Jest pass.
  - [ ] Confirm `cd infrastructure && npx cdk synth` succeeds on Node 22 (no behavior diff expected — synth output should be identical; verify no template drift).
  - [ ] Java/Gradle is unaffected (Java 21) — note it, no action.
  - [ ] Push the PR; confirm the full `build.yml` matrix is green on Node 22.

- [ ] **Task 4 — Leave deployed runtimes untouched + document** (AC: #3)
  - [ ] Verify NO change to: `infrastructure/lib/stacks/*` Lambda `Runtime.NODEJS_*_X` (storage, cognito, inbound-email, incident-management, cognito-user-sync-triggers, github-issues), `storage-stack.ts:171` esbuild `--target=node20`, and the service Dockerfiles (Java).
  - [ ] Add a short note (PR description + optionally `docs/architecture/`) stating the Lambda runtime / esbuild-target bump is a deliberate non-goal here and tracked separately. (Aside: `cognito-stack.ts:52` still uses `NODEJS_18_X` — flag as a separate follow-up, do NOT fix here.)

- [ ] **Task 5 — Docs** (AC: #4)
  - [ ] `CLAUDE.md` "Local Development → Prerequisites": Node.js 20+ → Node.js 22+.
  - [ ] Consult `.github/doc-drift-mappings.yml`; update any mapped doc in the same commit.

## Dev Notes

### Why this is low-risk and prod-safe
- **Build-host Node ≠ deployed runtime.** The Node version that runs `npm`/`vite`/`vitest`/`jest`/`cdk synth` in CI and locally is independent of the Node version AWS runs deployed Lambdas on (`Runtime.NODEJS_20_X`) and is irrelevant to the Java/ECS services. Bumping the build host does not change any deployed artifact's behavior. This story deliberately scopes to the build host only (AC3).
- **Node 20 is EOL-bound (2026)** — the bump is due regardless of Astro. Doing it as a clean standalone PR keeps it reviewable and reversible.
- **Toolchain is already Node-22-ready (verified):** web-frontend uses Vite 8 / Vitest 4 / TS 6 / ESLint 10 / `@vitejs/plugin-react` 6; infrastructure uses aws-cdk-lib 2.254 / esbuild 0.28 / jest 30 / ts-node 10.9 / TS 6 — all fully support Node 22. The only native dep is `sharp@^0.34` (image-resize Lambda), which supports Node 18.17/20/22; and it's built for the **Lambda** runtime, not the host (untouched here).
- **No existing pins to fight:** there is no `.nvmrc`, no `engines`, no `volta`, no `packageManager` field anywhere today — so nothing currently hard-blocks Node 22, and this story *adds* the pins so the new floor is explicit.

### Exact Node-20 surface (verified inventory)
- **CI:** `node-version: '20'` in 7 active + 4 disabled workflow files (Task 1 lists files + lines).
- **Dev image:** `web-frontend/Dockerfile.dev:1` `FROM node:20-alpine` (dev-only; the prod frontend ships as static S3 assets, not a Node container).
- **Native-dev guard:** `scripts/dev/start-all-native.sh:101-114` checks Node `>= 20`.
- **Untouched (orthogonal):** Lambda runtimes `NODEJS_20_X` (storage-stack:158/164, inbound-email:222, incident-management:96/142/181/234, cognito-user-sync-triggers:58, github-issues:38) and `NODEJS_18_X` (cognito-stack:52); esbuild `--target=node20` (storage-stack:171); all Java service Dockerfiles.

### Project Structure Notes
- Pure CI/tooling/config change. No application code, no API, no DB, no CDK resource change, no test-logic change. The only "risk" is a transitive dep with a hard `engines: <22` — none found in web-frontend or infrastructure.

### Testing standards summary
- No new tests. Validation = the existing suites passing on Node 22 (the CI matrix is the proof). `cdk synth` template equality is the guard that infra output didn't drift.

### References
- [Source: .github/workflows/*.yml] — `node-version: '20'` pins (Task 1).
- [Source: web-frontend/Dockerfile.dev:1; scripts/dev/start-all-native.sh:101-114] — dev image + guard.
- [Source: infrastructure/lib/stacks/*.ts] — Lambda runtimes + esbuild target (explicitly out of scope, AC3).
- [Source: web-frontend/package.json; infrastructure/package.json] — toolchain versions (Vite 8 / Vitest 4 / CDK 2.254 / esbuild 0.28), confirmed Node-22-ready; no `engines` today.
- [Web, verified 2026-06-02] Astro 6 requires Node ≥ 22.12.0 (dropped Node 18/20); this bump unblocks adopting Astro 6 in Story 13.1. Source: docs.astro.build/en/guides/upgrade-to/v6.

## Open Questions

1. **Should the AWS Lambda runtimes be bumped to Node 22 too, as a follow-up?** This story deliberately leaves `NODEJS_20_X` (and the lone `NODEJS_18_X` in `cognito-stack.ts:52`) alone because they are deployed-runtime changes that affect prod and deserve their own deploy + verification, not a quiet ride-along on a build-host bump. The `NODEJS_18_X` one is already on an EOL runtime and is worth its own small story soon; the question for the PM is whether to schedule that Lambda-runtime sweep now or after Epic 13.

2. **`engines` floor — `>=22.12.0` or a looser `>=22`?** Astro 6 needs ≥ 22.12.0 specifically, so pinning the exact floor is the safest signal, but a looser `>=22` reads more conventionally and any current Node 22.x is ≥ 22.12. The story uses `>=22.12.0` to match Astro's hard requirement; flag if you'd prefer the looser form.

## Dev Agent Record

### Agent Model Used

(to be filled by dev-story)

### Debug Log References

### Completion Notes List

### File List
