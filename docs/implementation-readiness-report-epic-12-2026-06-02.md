---
stepsCompleted: [step-01-document-discovery, step-02-prd-analysis, step-03-epic-coverage-validation, step-04-ux-alignment, step-05-epic-quality-review, step-06-final-assessment]
scope: "Epic 12 — SSO / OIDC Federation (Google)"
filesIncluded:
  requirements_source:
    - docs/plans/sso-oidc-federation.md
  architecture:
    - docs/architecture/ADR-010-federated-identity-via-cognito.md
    - docs/architecture/06b-user-lifecycle-sync.md
  stories:
    - _bmad-output/implementation-artifacts/12-1-cognito-attribute-hygiene.md
    - _bmad-output/implementation-artifacts/12-2-gateway-is-active-gate.md
    - _bmad-output/implementation-artifacts/12-3-canonical-jit-provisioning.md
    - _bmad-output/implementation-artifacts/12-4-google-cloud-oauth-consent-setup.md
    - _bmad-output/implementation-artifacts/12-5-google-idp-attribute-mapping.md
    - _bmad-output/implementation-artifacts/12-6-account-linking-presignup-trigger.md
    - _bmad-output/implementation-artifacts/12-7-frontend-callback-route-and-service.md
    - _bmad-output/implementation-artifacts/12-8-federated-provisioning-inactive-gating-verify.md
    - _bmad-output/implementation-artifacts/12-9-continue-with-google-button-feature-flag.md
    - _bmad-output/implementation-artifacts/12-10-apple-oidc-and-trigger-retirement-cleanup.md
  sprint_tracking:
    - _bmad-output/implementation-artifacts/sprint-status.yaml
  ux_design: []
---

# Implementation Readiness Assessment Report

**Date:** 2026-06-02
**Project:** BATbern
**Scope:** Epic 12 — SSO / OIDC Federation ("Sign in with Google"; Apple deferred)

---

## Step 1 — Document Inventory

### Requirements source (PRD/Epic substitute)
- `docs/plans/sso-oidc-federation.md` (22 KB) — **the binding requirements + design spec for Epic 12.** Phased delivery (PR 0, PR 1, Phases 0–6 + cleanup track), §8 Resolved Decisions (owner: Nissim).

> ⚠️ **STRUCTURAL NOTE (not a blocker):** Epic 12 has **no `docs/prd/epic-12-*.md` file** — unlike every other epic (epic-1…epic-11 each have one). The plan file + ADR-010 jointly play the PRD+epic role. Requirements traceability in this assessment is therefore anchored on the plan's phases/§8 decisions rather than a formal FR/NFR inventory.

### Architecture
- `docs/architecture/ADR-010-federated-identity-via-cognito.md` (9.7 KB) — the broker-model decision, Google-first, Apple deferral.
- `docs/architecture/06b-user-lifecycle-sync.md` (51 KB) — user-lifecycle/sync patterns; Cognito trigger inventory; Pattern 3b DB-fallback; the is_active enforcement narrative (modified 2026-06-02 12:37, post 12-1 merge).

### Stories (10 found — `_bmad-output/implementation-artifacts/`)
| Story | File | Sprint status |
|---|---|---|
| 12-1 Cognito attribute hygiene | `12-1-cognito-attribute-hygiene.md` | **MERGED** (#731, `aee2bcc3`) |
| 12-2 Gateway is_active gate | `12-2-gateway-is-active-gate.md` | done + PR'd (`feature/12-2-…`) |
| 12-3 Canonical JIT provisioning | `12-3-canonical-jit-provisioning.md` | ready-for-dev |
| 12-4 Google Cloud OAuth + consent | `12-4-google-cloud-oauth-consent-setup.md` | done |
| 12-5 Google IdP + attribute mapping | `12-5-google-idp-attribute-mapping.md` | ready-for-dev |
| 12-6 Account-linking PreSignUp trigger | `12-6-account-linking-presignup-trigger.md` | ready-for-dev |
| 12-7 Federated provisioning verify-only | `12-7-federated-provisioning-inactive-gating-verify.md` | ready-for-dev |
| 12-8 Frontend callback route + service | `12-8-frontend-callback-route-and-service.md` | ready-for-dev |
| 12-9 Continue-with-Google button + flag | `12-9-continue-with-google-button-feature-flag.md` | ready-for-dev |
| 12-10 Apple/OIDC + trigger-retirement cleanup | `12-10-apple-oidc-and-trigger-retirement-cleanup.md` | **backlog (deferred)** |

### Sprint tracking
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — Epic 12 block (lines ~205-220).

### UX Design
- **None.** No SSO-specific UX spec. The only UI surface is the "Continue with Google" button (Story 12-9); its placement/behaviour is specified inline in that story + plan §5 Phase 5. (Acceptable for a single-button additive surface.)

### Duplicates
- **None.** No whole-vs-sharded conflicts.

### Missing (by convention, not necessarily blocking)
- Dedicated `docs/prd/epic-12-*.md` (see structural note above).
- UX design doc (judged acceptable — single additive button).

---

## Step 2 — Requirements Analysis (PRD substitute = plan + ADR-010)

> Epic 12 has no numbered FR/NFR list. The inventory below is **derived** from the plan's goal/§3 gotchas/§4 constraint/§5 phases/§8 decisions and ADR-010 decisions D1–D9. IDs are assigned here for traceability in Step 3.

### Functional Requirements (derived)

- **FR1** — Federated "Sign in with Google" via Cognito hosted-UI OIDC (broker model). [Goal; ADR D1]
- **FR2** — Existing email/password users link Google to the **same account** transparently (no migration, no lost roles) via `AdminLinkProviderForUser` in `PreSignUp_ExternalProvider`, matched by email, **preserving `sub`**. [§3; ADR D3]
- **FR3** — Brand-new Google users auto-provision with default **ATTENDEE** role. [§8 Q3; ADR D4]
- **FR4** — After linking, users can sign in with **either** password **or** Google. [ADR D3]
- **FR5** — "Continue with Google" button on **all** login surfaces, behind a `features.sso` flag. [§8 Q2; Phase 5; ADR D8]
- **FR6** — Backend stays provider-agnostic; the app receives the **same JWT shape**. [Goal; ADR D1]
- **FR7** — **Canonical JIT provisioning**: any authenticated identity (native or federated) provisions a correct `user_profiles` row (create + email-link + default ATTENDEE) on first request. [PR1-B; ADR D5]
- **FR8** — **API-Gateway `is_active` gate**: deactivated accounts blocked at request time (`403 ACCOUNT_DEACTIVATED`) across all auth methods incl. federated. [PR1-A; ADR D5]
- **FR9** — Frontend `signInWithFederated('Google')` + `/auth/callback` route/handler + `/logout`. [Phase 4]
- **FR10** — Token carries identity + authorization only (`sub`, `email`, `custom:role`, `custom:username`); `companyId` removed; dead `custom:role` stored attr gets `"UNUSED"` sentinel. [PR0; ADR D6]
- **FR11** — Define Google IdP in Cognito (`UserPoolIdentityProviderGoogle`) + attribute mapping; add `GOOGLE` to `supportedIdentityProviders` (COGNITO stays → password auth intact). [Phase 1; ADR D9]
- **FR12** — Google Cloud project + OAuth Web client + consent screen published to Production + client id/secret in Secrets Manager. [Phase 0; ADR D9]
- **FR13** *(deferred)* — Apple + generic corporate OIDC. [§8 Q1; Phase 6; ADR D2]
- **FR14** *(cleanup)* — Retire `PostAuthentication`, `PreAuthentication`, `PostConfirmation` → 3-trigger target set (`PreSignUp`, `PreTokenGeneration`, `CustomEmailSender`). [§8 Q5; cleanup track; ADR D7]

**Total FRs: 14** (FR1–FR12 in-scope for this delivery; FR13 deferred; FR14 deferred-cleanup).

### Non-Functional Requirements (derived)

- **NFR1** *(security)* — Deactivation effective platform-wide within **~60s** (was ≤24h); applies to every auth method. Caffeine-cached, configurable `security.active-gate.ttl-seconds`. [PR1-A; ADR Consequences]
- **NFR2** *(resilience)* — `is_active` gate **fail-open** on CUMS error (allow + WARN + metric); **kill-switch** `security.active-gate.enabled`; gate only ever tightens vs. today's no-gate state. [PR1-A]
- **NFR3** *(prod-safety)* — Single real pool; each phase independently prod-deployable **without breaking password logins**, verified before the next; only user-visible flip behind a feature flag. [§4; ADR hard constraint]
- **NFR4** *(auth-path reliability)* — Linking Lambda must not fail module-load (a module-load failure 503s **all** auth) → **handler unit test mandatory**. [Phase 2; ADR negative]
- **NFR5** *(no refresh-loop)* — Deactivated → **403 not 401** to avoid the frontend token-refresh loop. [PR1-A]
- **NFR6** *(i18n)* — Button keys in **all 10 locales** (EN+DE first-class). [Phase 5; CLAUDE.md]
- **NFR7** *(privacy/least-scope)* — Non-sensitive scopes `openid email profile` only ⇒ no Google security assessment. [§8 Q4; ADR D9]
- **NFR8** *(secret mgmt)* — Google client secret in Secrets Manager, never inlined in CDK. [Phase 0]
- **NFR9** *(testability)* — 4-layer test framework; Testcontainers for integration tests. [§6]

**Total NFRs: 9.**

### Additional constraints / assumptions
- **C1** — Staging account (188701360969) **is production**; one real Cognito pool; every CDK pool change is a prod change. [§4]
- **C2** — Cognito custom attributes are **permanent** — cleanup = stop-using/sentinel, not delete. [PR0; ADR Neutral]
- **C3** — Ordering: **PR0 → PR1 → Phases 0–5** (Phase 3 verify-only) → cleanup track (after PR1 verified in prod). [§5; ADR Implementation]
- **C4** — Provisioning becomes **lazy** (DB row on first authenticated request) — the `custom:preferences` name divergence (2026-05-18 incident) must be closed **before** `PostConfirmation` removal. [ADR negative/D5]

### Requirements completeness assessment
The plan + ADR-010 are an unusually thorough requirements baseline for a brownfield infra epic: explicit per-phase risk/rollback, resolved decisions (§8, owner Nissim), an **Accepted** ADR with alternatives-considered, and a §6 test strategy. The only formal gap vs. a standard PRD is the absence of numbered FR/NFR + epic-level acceptance metrics — these live in the individual story files instead, so Step 3 traceability is **plan-phase/ADR-decision → story → AC**, not FR-number → story.

---

## Step 3 — Coverage Validation (FR → Story)

Traceability is **plan-phase/ADR-decision → story** (no FR-coverage map exists in a PRD, so this matrix is built here).

### Coverage Matrix

| FR | Requirement (short) | Story home | Status |
|---|---|---|---|
| FR1 | Federated Sign-in with Google (broker) | 12-5 + 12-8 + 12-9 (composite) | ✓ Covered |
| FR2 | Transparent account-linking, preserve `sub` | 12-6 | ✓ Covered |
| FR3 | New Google user → ATTENDEE | 12-3 (default) + 12-7 (verify) | ✓ Covered |
| FR4 | Sign in with either password or Google | emergent from 12-6; verified 12-7/12-9 | ✓ Covered (no dedicated story needed) |
| FR5 | "Continue with Google" button on all surfaces + flag | 12-9 | ✓ Covered |
| FR6 | Provider-agnostic backend / same JWT shape | design invariant (no backend change) | ✓ Covered by design |
| FR7 | Canonical JIT provisioning | 12-3 | ✓ Covered |
| FR8 | API-Gateway `is_active` gate (`403 ACCOUNT_DEACTIVATED`) | 12-2 (gateway) | ⚠️ **Partial** — frontend half unhomed (see gap G1) |
| FR9 | `signInWithFederated` + `/auth/callback` + `/logout` | 12-8 | ✓ Covered |
| FR10 | Token = identity+authz only; drop companyId; UNUSED sentinel | 12-1 (merged) | ✓ Covered |
| FR11 | Google IdP + attribute mapping + `supportedIdentityProviders` | 12-5 | ✓ Covered |
| FR12 | Google Cloud project + OAuth + consent + secret | 12-4 (done) | ✓ Covered |
| FR13 | Apple + generic OIDC | 12-10 | ⏸️ Covered as **deferred** |
| FR14 | Retire PostAuth/PreAuth/PostConfirmation → 3 triggers | 12-10 | ⏸️ Covered as **deferred** |

### NFR coverage (summary)
- NFR1/NFR2/NFR5 → **12-2**; NFR3 (prod-safe phasing) → cross-cutting (per-story deploy/rollback notes); NFR4 (handler test) → **12-6**; NFR6 (i18n) → **12-9**; NFR7/NFR8 (scopes/secret) → **12-4 + 12-5**; NFR9 (4-layer tests) → all stories.

### Coverage gaps

**G1 — FR8 frontend half is unhomed (MAJOR for full FR8 closure; not a blocker for 12-2 itself).**
The plan (PR1-A / NFR5) states *"Frontend maps the code [`ACCOUNT_DEACTIVATED`] to a forced logout + 'account deactivated' message."* `grep` confirms `ACCOUNT_DEACTIVATED` appears only in **12-2** (explicitly marked *out of scope* there) and **12-7** (verify-only). **No story owns the frontend handler** that turns the gateway's `403 ACCOUNT_DEACTIVATED` into a forced logout + user message + i18n keys. Until this is homed, a deactivated user hitting the live gate gets a raw 403 / generic error, not the intended UX.
- **Impact:** Without it, the gate works (security holds) but the user experience on deactivation is undefined; and FR8 is only ~½ delivered.
- **Recommendation:** Either (a) add a small new story **12-11 (frontend `ACCOUNT_DEACTIVATED` handling)**, or (b) fold it into 12-8 (the SSO frontend-plumbing story already touching the auth/session path) with an explicit AC + 10-locale i18n keys. Pick before 12-2's gate is flipped `enabled=true` in prod, or accept a known UX rough edge in the interim.

### Coverage statistics
- Total derived FRs: **14** (FR1–FR12 in-scope, FR13–FR14 deferred).
- FRs with a traceable story home: **14 / 14 (100%)**.
- Fully covered in-scope FRs: **11 / 12** (FR8 partial — frontend half unhomed → G1).
- Deferred FRs correctly parked in 12-10: **2 / 2**.

---

## Step 4 — UX Alignment

### UX Document Status
**Not Found** — no `*ux*.md` for SSO. **Acceptable.** Epic 12 introduces exactly one new UI surface (the "Continue with Google" button); a standalone UX spec is not warranted.

### Implied UX surfaces & where they're specified
| Surface | Spec home | Architecture support |
|---|---|---|
| "Continue with Google" button (all login surfaces, above form, with divider, behind `features.sso`) | 12-9 + plan §5 Phase 5 + ADR D8 / §8 Q2 | 12-9 (LoginForm) → `authService.signInWithFederated` (12-8) → Amplify `signInWithRedirect` → Cognito hosted UI. ✓ Supported. |
| Hosted-UI redirect / `/auth/callback` "signing you in" state | 12-8 | Amplify OAuth block already wired (`amplify.ts:54-61`); callback completes `fetchAuthSession`. ✓ Supported. |
| i18n for the button (10 locales, EN+DE first-class) | 12-9 (NFR6) | `auth.json` namespace, all 10 locale dirs present. ✓ Supported. |

### Alignment issues
- **No misalignment** between the implied UX, the plan/ADR, and the architecture. The button is additive; password login is unaffected (COGNITO stays in `supportedIdentityProviders`).

### Warnings
- **UX-W1 (ties to G1):** The **deactivation UX** (`403 ACCOUNT_DEACTIVATED` → forced logout + "account deactivated" message) is a user-facing experience the plan calls for, but it has **no story home** (see Step 3 / G1). This is the only UX-relevant gap.
- **UX-W2 (minor):** The hosted-UI consent/redirect screens are Google/Cognito-branded (outside our control) — acceptable and expected for the broker model; no action needed, noted for awareness.

---

## Step 5 — Epic Quality Review (against create-epics-and-stories standards)

### Epic-level: user value & independence
- **Epic delivers clear user value:** "Sign in with Google" (FR1). ✅
- **Epic independence:** Epic 12 depends only on already-shipped auth foundations (Cognito, JIT, `/users/me`, Pattern 3b). No dependency on a *future* epic. ✅
- **Decomposition rationale:** Stories are sliced by **deployability/risk against the single production pool** (§4 / NFR3), not by user-facing value. This is the correct engineering decomposition for a prod-safe incremental rollout — but see DEV-1.

### Story dependency graph (forward-dependency check)
| Story | Declared prereq | Direction |
|---|---|---|
| 12-1 | none | — (merged) |
| 12-2 | none | — (done) |
| 12-3 | none | — |
| 12-4 | none (blocks 12-5) | — (done) |
| 12-5 | 12-4 | ◀ backward |
| 12-6 | 12-5 | ◀ backward |
| 12-7 | 12-2 + 12-3 + 12-6 (+12-5 deployed) | ◀ backward |
| 12-8 | 12-6; references 12-1 rename | ◀ backward |
| 12-9 | 12-8 + all prior | ◀ backward |
| 12-10 | 12-2 + 12-3 *verified in prod* | ◀ backward (deferred) |

✅ **No forward dependencies.** Every prereq points to a lower-numbered/earlier story. The chain is internally consistent and matches the plan's PR0→PR1→Phase0-5→cleanup ordering. 12-8 even handles the "12-1 not yet merged" case defensively (no third name introduced).

### AC & sizing quality (per story)
| Story | ACs | Tasks | Assessment |
|---|---|---|---|
| 12-2 | 14 | 7 | Strong: active→pass / inactive→403 / cache-hit / fail-open / kill-switch all testable. |
| 12-3 | 8 | 6 | Strong; AC7 (`UserReconciliationService` language parity) is a soft "SHOULD/record-if-deferred" → DEV-2. |
| 12-5 | 9 | 6 | Strong; resolves the `custom:preferences` JSON-fold infeasibility explicitly. |
| 12-6 | 8 | 8 | Strong: missing-email guard, IAM least-privilege, native-path-verbatim, **mandatory handler test** (NFR4). |
| 12-7 | 6 | 6 | Appropriate for **verify-only** (pass/fail observations) with a Task-0 prereq gate. |
| 12-8 | 9 | 7 | Strong; defensively handles the 12-1 rename dependency. |
| 12-9 | 9 | 7 | Strong: flag on/off render, 10-locale i18n, Playwright smoke. |
| 12-10 | 7 | 0 | Deliberate **deferred umbrella** ("split into PRs when scheduled") — 0 tasks is correct here. |

- **Sizing:** all in-scope stories are small / single-PR (as requested). 12-10 is intentionally an umbrella. ✅
- **Brownfield integration points** are explicit in every story (existing `UserServiceClient`, `JITUserProvisioningInterceptor`, inline `preSignUp`, `amplify.ts`). ✅ No spurious greenfield "project setup" story.

### Findings by severity

#### 🔴 Critical violations
- **None.** No technical-milestone-masquerading-as-epic; no forward dependencies; no epic-sized stories.

#### 🟠 Major issues
- **G1 (carried from Step 3) — FR8 frontend `ACCOUNT_DEACTIVATED` handler is unhomed.** 12-2 (now `done`) explicitly parks it (OQ-3) as a follow-up; no story owns it. Decision needed **before the gate is flipped `enabled=true` in prod**, else deactivated users get a raw 403 instead of a forced-logout UX. Remediation: new story **12-11** or fold an AC into 12-8.

#### 🟡 Minor concerns
- **DEV-1 — "Invisible/backend-only" stories (12-2, 12-3, 12-5, 12-8) read as technical enablers, not user-value slices.** A purist BMAD review flags this. **Accepted/justified here:** the governing constraint is prod-safe phasing against one real Cognito pool (§4); each slice is independently deployable + revertible + verified — satisfying "independently completable" via deploy-safety rather than user-facing value. Documented, not a defect.
- **DEV-2 — 12-3 AC7 (`UserReconciliationService.createMissingUser` language parity) is a soft AC** ("SHOULD … if deferred, record it"). Recommend hardening to a firm AC or explicitly scoping it out, so the two reconcile paths don't silently diverge again (the 2026-05-18 incident).
- **DEV-3 — 12-7 verification needs a manually hand-driven OAuth flow** (it precedes 12-8/12-9, so no button/callback UI exists yet). Feasible but fiddly; the story flags it. Consider whether to verify 12-7 *after* 12-8 is deployed (12-8 isn't a hard prereq but makes token acquisition trivial).
- **DEV-4 — AC format is grounded prose, not Given/When/Then BDD.** This is the established repo-wide convention (consistent with 12-1 and epics 1-11); flagged only against the literal rubric, not as a defect.

### Best-practices compliance checklist (epic-level)
- [x] Epic delivers user value
- [x] Epic can function independently (no future-epic dependency)
- [x] Stories appropriately sized
- [x] No forward dependencies
- [x] DB/schema changes created when needed (12-3 touches `user_profiles` via existing path; no premature upfront schema story)
- [x] Clear, testable acceptance criteria (prose+file:line convention)
- [x] Traceability to requirements maintained (Step 3 matrix, 14/14)

---

## Summary and Recommendations

### Overall Readiness Status
**✅ READY** (continue implementation) — with **one** major item to home before the production gate-flip / Phase 5.

Epic 12 is in strong shape: a thorough plan + an **Accepted** ADR-010 as the requirements baseline, 14/14 FRs with a traceable story home, a dependency-clean story chain (no forward references), and detailed, source-grounded stories. 12-1/12-2/12-4 are done; 12-3/12-5/12-6/12-7/12-8/12-9 are ready-for-dev; 12-10 is correctly deferred. The story files even pre-corrected several plan-vs-code drifts (12-3 JIT premise, 12-5 attribute-mapping infeasibility, 12-2 missing gateway client/Caffeine).

### Critical Issues Requiring Immediate Action
- **None block continued implementation.** The next stories in the merge chain (12-3 canonical JIT, then 12-5) can proceed now.

### Issues by severity
- 🔴 Critical: **0**
- 🟠 Major: **1** — **G1**: FR8's frontend `ACCOUNT_DEACTIVATED` forced-logout handler is unhomed (12-2 parked it as OQ-3; no story owns it).
- 🟡 Minor: **4** — DEV-1 (backend-only stories as technical enablers — justified), DEV-2 (12-3 soft language-parity AC), DEV-3 (12-7 manual OAuth verification ordering), DEV-4 (prose-vs-BDD AC format — repo convention).
- ⚠️ Structural note: **1** — no `docs/prd/epic-12-*.md` (plan + ADR substitute; acceptable).

### Recommended Next Steps
1. **Resolve G1 before flipping `security.active-gate.enabled=true` in prod.** Either create a small **Story 12-11 (frontend `ACCOUNT_DEACTIVATED` handling: forced logout + message + 10-locale i18n)** or add an explicit AC to **12-8** (already in the auth/session frontend path). This is the only major gap.
2. **Proceed with 12-3 (canonical JIT) next** — it has no prereq, is the immediate merge-chain successor to 12-1, and unblocks 12-7's verify-only scope. While there, **harden DEV-2** (firm up the `UserReconciliationService` language-parity AC or scope it out explicitly).
3. **Keep the deploy ordering gate** (12-4✅ → 12-5 → 12-6 → 12-7 verify; 12-8 → 12-9 with flag off → smoke → flip on). Consider deploying **12-8 before running 12-7's verification** (DEV-3) so federated-token acquisition is trivial rather than hand-driven.
4. **Leave 12-10 `backlog`.** Split into independent PRs (Apple, C1/C2/C3 cleanup) only after 12-2 + 12-3 are verified in prod.

### Final Note
This assessment identified **6 issues** (1 major, 4 minor, 1 structural note) across 5 review categories, plus **0 critical**. None block starting the ready-for-dev stories; address **G1** before the production gate-flip. The epic is otherwise well-structured, traceable, and dependency-clean — proceed.

---
*Assessment by: Implementation Readiness workflow (bmad-check-implementation-readiness), facilitated as PM. Date: 2026-06-02. Scope: Epic 12 only.*

---

## Addendum — Actions taken (2026-06-02, post-assessment, per Nissim)

1. **G1 RESOLVED — frontend `ACCOUNT_DEACTIVATED` handler homed.** Folded into the frontend-plumbing story as **AC10 + Task 8** (forced logout + message + 10-locale i18n, distinct-from-401 / no refresh-loop). Story 12-2's OQ-3 repointed and marked ✅ RESOLVED.
2. **Renumber — 12-7 ⇄ 12-8 swapped** so numeric order = execution order:
   - **NEW 12-7** = `12-7-frontend-callback-route-and-service.md` (frontend callback + `signInWithFederated` + the G1 handler) — *was 12-8*.
   - **NEW 12-8** = `12-8-federated-provisioning-inactive-gating-verify.md` (VERIFY-ONLY) — *was 12-7*.
   - Rationale: the `/auth/callback` route now lands **before** the verification, so the verify story acquires a real federated token through the actual app flow instead of hand-driving the OAuth exchange — **resolves DEV-3**.
3. **Cross-references updated** across 12-2, 12-3, 12-5, 12-6, 12-9, 12-10, and `sprint-status.yaml` (keys, prereqs, "makes verify-only" notes, 12-9 prereq 12-8→12-7).

**Net effect on findings:** 🟠 Major **G1 → resolved**; 🟡 **DEV-3 → resolved** by the renumber. Remaining open: DEV-1 (justified), DEV-2 (12-3 soft AC — recommend hardening during 12-3 dev), DEV-4 (AC-format convention). Updated execution order: PR0(12-1✅) → 12-2✅ → 12-3 → 12-4✅ → 12-5 → 12-6 → **12-7 (callback+deactivation)** → **12-8 (verify)** → 12-9 (button); 12-10 deferred.
