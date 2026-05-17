# Story 11.E.1: CDK + IAM prereq — App Client auth flow and Cognito admin permissions

Status: done

<!-- Validation is optional — run validate-create-story for quality check before dev-story. -->

## Story

**As a** security and platform maintainer,
**I want** the Cognito User Pool App Client configured for `AdminInitiateAuth` and the company-user-management-service task role granted exactly the Cognito admin IAM permissions ADR-009 requires (least-privilege),
**So that** Phase E's speaker-provisioning hook (Story 11.E.2) can issue `AdminCreateUser` + `AdminSetUserPassword` calls against the user pool, the temporary-password login flow works end-to-end, and we stop blocking Phase E on missing infrastructure.

## Phase / Dependencies / Requirements Covered

- **Phase:** E — Cognito with forced password change (first of three E stories). Phase E kickoff.
- **Depends on:**
  1. **No in-epic story prerequisite.** This story is pure CDK/IAM cherry-pick + skip-list. It can land on `feature/speaker-workflow-refactor` HEAD any time after Phase A (which is `done`).
  2. **External prerequisite:** the `refs/remotes/origin/feature/epic-6` ref must still be reachable so we can cherry-pick commit `d5cf0fcc`. Verified at story-creation time: `git log feature/epic-6 --oneline` shows `d5cf0fccac3cf9befafa3f6edddac51b1949080d` is the HEAD substantive commit. If the branch is force-deleted before this story is executed, the dev should re-create the equivalent diff from this story's AC specs (the diff is small — ~5 LOC across two files).
- **Unblocks:** Story 11.E.2 (`AR15` Cognito provisioning logic in CUMS — needs the IAM perms + App Client flow this story lands) and Story 11.E.3 (speaker-portal Cognito auth — needs nothing from this story directly, but Phase E as a whole is gated on the IAM prereq landing first).
- **Requirements covered (PRD lines 1119–1163):**
  - **AR29** — App Client gains `ALLOW_ADMIN_USER_PASSWORD_AUTH` flow (cherry-picked from `feature/epic-6` `d5cf0fcc`).
  - **AR30** — IAM perms `AdminCreateUser`, `AdminSetUserPassword`, `AdminInitiateAuth`, `AdminGetUser` added to the speaker-provisioning principal. `AdminAddUserToGroup` is **NOT** granted — per Resolved Q#1, ADR-001 keeps roles in PostgreSQL (no Cognito groups exist). The PRD AR30 / NFR5 / ADR-009 references to `AdminAddUserToGroup` are corrected in the same commit as this story.
  - **AR42** — Cherry-pick `d5cf0fcc` Story 7.1 Cognito IAM + auth flow from `feature/epic-6`; skip the `COGNITO_PASSWORD_ENCRYPTION_KEY` secret.
  - **NFR2** — No new Cognito Lambda triggers required (this story adds zero Lambdas).
  - **NFR5** — Least-privilege IAM scope (only the four admin actions actually called by Story 11.E.2; encryption-key secret skipped; `AdminAddUserToGroup` skipped per Resolved Q#1).
  - **NFR9** — Cognito User Pool password policy must accept the backend-generated temporary password AND give the speaker a reasonable window to complete first login. Per Resolved Q#4, `tempPasswordValidity` is bumped from 7 to 14 days in this story.
- **Plan / ADR anchors:**
  - Epic 11 PRD §"Story 11.E.1" lines 1119–1163 — the AC source-of-truth.
  - `docs/plans/speaker-workflow-refactor.md` §3.5 ("`infrastructure` (CDK)"), §9.2 ("`feature/epic-6` — cherry-pick d5cf0fcc"), §9.5 (summary disposition).
  - `docs/architecture/ADR-009-unified-speaker-workflow.md` §Decision 3 (standard Cognito + forced password change at `CONTACTED → READY`).
  - **Existing source commit** `d5cf0fccac3cf9befafa3f6edddac51b1949080d` on `refs/remotes/origin/feature/epic-6` — labelled "Story 7.1 Cognito admin permissions and encryption key for server-managed accounts." This story selectively cherry-picks **part** of it.

---

## Branch state at story start

- **Current branch:** `feature/speaker-workflow-refactor`. The story lands as a single small commit on this branch (per refactor plan §9.1).
- **Existing CDK state today (verified 2026-05-17):**
  - `infrastructure/lib/stacks/cognito-stack.ts` line 237-241 — `authFlows: { userPassword: true, custom: true, userSrp: true }`. Missing `adminUserPassword: true`.
  - `infrastructure/lib/stacks/company-management-stack.ts` lines 134-159 — has S3 + EventBridge IAM grants on the task role, but **zero** `cognito-idp:Admin*` actions.
  - `infrastructure/test/unit/cognito-stack.test.ts` lines 105-114 — has `should_configureOAuthFlows_when_appClientCreated`. No `ALLOW_ADMIN_USER_PASSWORD_AUTH` assertion.
  - `infrastructure/test/unit/secrets-stack.test.ts` — asserts `resourceCountIs('AWS::SecretsManager::Secret', 1)` (only JWT). This story does NOT touch the secret count.
  - **`bruno-tests/auth/` directory does NOT exist.** The PRD AC item about a Bruno smoke test is structurally deferred to 11.E.2 (see AC5 below); this story does not create the folder.
- **ADR-001 win on `AdminAddUserToGroup` (Resolved Q#1):** `cognito-stack.ts` lines 277–279 carry the explicit comment "REMOVED: Cognito Groups (Story 1.2.6: ADR-001 Database-centric architecture)". Roles are managed in PostgreSQL `user_roles`, NOT Cognito groups. The PRD AR30 / NFR5 / ADR-009 §Decision 3 references to `AdminAddUserToGroup` were aspirational and contradicted ADR-001. PM ruling 2026-05-17: drop the permission (useless grant). This story removes it from the cherry-picked IAM policy AND aligns PRD + ADR-009 in the same commit (per CLAUDE.md doc-drift-prevention policy). Final IAM action list is **four** items.

---

## Acceptance Criteria

The AC are pinned to PRD lines 1119–1163. Each AC names the exact file under change. The cherry-pick is the implementation truth where it exists; the four PM-resolved Open-Question deviations from the literal PRD (drop `AdminAddUserToGroup`, fix EMS→CUMS wording, confirm encryption-key skip, bump `tempPasswordValidity` 7→14) are called out explicitly so the dev applies them deliberately.

### AC1 — App Client gains `ALLOW_ADMIN_USER_PASSWORD_AUTH` auth flow (AR29)

**Given** the file `infrastructure/lib/stacks/cognito-stack.ts`,
**When** the `UserPoolClient` is instantiated at line 234,
**Then** the `authFlows` object includes a new entry `adminUserPassword: true`, alongside the existing `userPassword: true`, `custom: true`, `userSrp: true`:

```typescript
authFlows: {
  userPassword: true,
  custom: true,
  userSrp: true,
  adminUserPassword: true,  // Story 11.E.1 / AR29 / cherry-pick d5cf0fcc — enables AdminInitiateAuth for the temp-password flow used at speaker provisioning (Story 11.E.2)
},
```

**And** the CDK synth produces a `AWS::Cognito::UserPoolClient` resource whose `ExplicitAuthFlows` array contains the literal string `ALLOW_ADMIN_USER_PASSWORD_AUTH` (the CloudFormation-level value CDK emits when `adminUserPassword: true` is set).

**And** the inline comment matches the cherry-pick rationale verbatim or close to it. Acceptable variants — keep the "AR29", "11.E.1", or "cherry-pick d5cf0fcc" anchor in the comment so a reader can trace it.

---

### AC2 — `company-management-stack` task role gains four Cognito admin permissions (AR30, NFR5)

**Given** the file `infrastructure/lib/stacks/company-management-stack.ts`,
**When** the task role's principal policies are set up (after the existing EventBridge grant block at lines 150-159, before the closing comment at line 161),
**Then** a new `iam.PolicyStatement` is added that grants the **company-user-management-service** task role exactly the following four Cognito admin actions, scoped to the User Pool ARN only:

```typescript
// Story 11.E.1 / AR30 / cherry-pick d5cf0fcc: Grant Cognito admin perms for speaker provisioning (Story 11.E.2).
// Scope: this service's task role only. Resource: the BATbern User Pool ARN (no wildcard).
// AdminAddUserToGroup is intentionally NOT granted (Resolved Q#1, PM 2026-05-17): roles live in
// PostgreSQL user_roles per ADR-001; no Cognito groups exist; granting the permission would be a useless
// least-privilege violation. ADR-009 §Decision 3, PRD AR30, and PRD NFR5 are updated in the same commit.
this.service.taskDefinition.taskRole.addToPrincipalPolicy(new iam.PolicyStatement({
  effect: iam.Effect.ALLOW,
  actions: [
    'cognito-idp:AdminCreateUser',
    'cognito-idp:AdminSetUserPassword',
    'cognito-idp:AdminInitiateAuth',
    'cognito-idp:AdminGetUser',
  ],
  resources: [props.userPool.userPoolArn],
}));
```

**And** the policy is attached to the **task role** (`taskDefinition.taskRole`), NOT the execution role — the application code calls Cognito at runtime, so task-role scope is correct.

**And** the synthesised CloudFormation template includes an `AWS::IAM::Policy` (or inline policy) where the `Statement` array contains an entry whose `Action` list is exactly the four strings above (set equality, order does not matter). The `Resource` is the `UserPoolArn` (via CDK reference, which synthesises to a `Fn::GetAtt` or `Fn::ImportValue` form).

**And** **no `cognito-idp:AdminAddUserToGroup` action appears anywhere in the synthesised template** for the CUMS task role. A negative-assertion test (AC7) guards this.

**And** the location of the new block in the stack is between the EventBridge IAM grant (line 159) and the "Note: Cognito Lambda triggers" comment (line 161) — keeping IAM grant clusters contiguous. Acceptable to place it elsewhere within `constructor()` so long as it executes once.

**And** **no IAM permissions are granted to the event-management-service task role** in this story. The PRD AC wording at line 1138 ("event-management-service task-role principal") is corrected to `company-user-management-service task-role principal` in the same commit (Resolved Q#2): ADR-009 §Decision 3 + Story 11.C.2's `UserApiClient.provisionUserWithRole` design place the actual Cognito SDK call on CUMS, not EMS, and the cherry-picked `d5cf0fcc` already places the IAM block on `company-management-stack.ts`.

---

### AC3 — Encryption-key secret deliberately NOT cherry-picked (AR42, NFR5)

**Given** the cherry-pick of `d5cf0fcc` is in progress,
**When** the dev examines the source commit's hunks,
**Then** the following pieces of `d5cf0fcc` are **omitted** from this story's commit:

- `infrastructure/lib/stacks/secrets-stack.ts` — the `CognitoPasswordEncryptionKey` `secretsmanager.Secret` resource (the `new secretsmanager.Secret(this, 'CognitoPasswordEncryptionKey', ...)` block) and its corresponding `cdk.CfnOutput` (`CognitoPasswordEncryptionKeyArn`).
- `infrastructure/lib/stacks/company-management-stack.ts` — the `cdk.Fn.importValue(${envName}-CognitoPasswordEncryptionKeyArn)` import, the `secretsmanager.Secret.fromSecretCompleteArn(...)` rehydration, and the `additionalSecrets: { COGNITO_PASSWORD_ENCRYPTION_KEY: ... }` pass-through into `createDomainService`.
- `infrastructure/lib/constructs/domain-service-construct.ts` — the new optional `additionalSecrets?: Record<string, ecs.Secret>` prop on `DomainServiceConstructProps` and the `Object.assign(secrets, props.additionalSecrets)` merge inside `createDomainService`. **Note:** this prop is independently useful (it is also used in this story's current `company-management-stack.ts` via `additionalSecrets` for `WATCH_JWT_SECRET` already at line 81-84). The dev does NOT touch the existing local `additionalSecrets` flow, only refrains from re-introducing it through the cherry-picked path. *If* `domain-service-construct.ts` already supports `additionalSecrets` on `feature/speaker-workflow-refactor` HEAD, the dev verifies it and moves on; if it does not, the dev does not introduce it via this story.
- `infrastructure/bin/batbern-infrastructure.ts` — the standalone comment line "Note: Secrets dependency via CloudFormation Fn::ImportValue (Story 7.1 - avoids cyclic deps)" is omitted (it references a dependency that this story does not create).
- `infrastructure/test/unit/secrets-stack.test.ts` — the `resourceCountIs('AWS::SecretsManager::Secret', 2)` adjustment is **NOT applied**. The file stays at `resourceCountIs(... , 1)`. The cherry-pick's added `should_createCognitoPasswordEncryptionKey_when_secretsStackDeployed` test case is **NOT applied**.

**And** the cherry-pick commit message records the skips and the doc-alignment explicitly. Suggested wording (verbatim or close):

```
chore(infra): cherry-pick d5cf0fcc App Client AdminInitiateAuth + Cognito admin IAM perms [Story 11.E.1]

Selectively applies infra portions of feature/epic-6 d5cf0fcc:
- cognito-stack.ts: adminUserPassword: true on App Client (AR29)
- cognito-stack.ts: tempPasswordValidity 7→14 days (NFR9 / Resolved Q#4)
- company-management-stack.ts: AdminCreateUser + AdminSetUserPassword + AdminInitiateAuth
  + AdminGetUser on CUMS task role, scoped to UserPool ARN (AR30, NFR5)
- cognito-stack.test.ts: assertion for ALLOW_ADMIN_USER_PASSWORD_AUTH + 14-day temp-validity

DELIBERATELY OMITTED:
- COGNITO_PASSWORD_ENCRYPTION_KEY secret (per refactor plan §9.2 / ADR-009 / PRD AR42) —
  under ADR-009 the temp password is generated, embedded once in the invitation email, and never
  stored at rest; nothing to encrypt.
- secrets-stack.ts CfnOutput + cross-stack import wiring tied to that secret.
- secrets-stack.test.ts resource-count bump + new key-creation test.
- cognito-idp:AdminAddUserToGroup IAM action (Resolved Q#1, PM 2026-05-17) — roles live in
  PostgreSQL user_roles per ADR-001; no Cognito groups exist; granting the permission would be
  a useless least-privilege violation.

DOC ALIGNMENT (same commit, per CLAUDE.md doc-drift policy):
- docs/architecture/ADR-009-unified-speaker-workflow.md §Decision 3 + Implementation
  Guidelines skeleton: drop AdminAddUserToGroup references; add "SPEAKER role granted via
  user_roles row insert (ADR-001)" note.
- docs/prd/epic-11-speaker-workflow-refactor.md NFR5 + AR30 + Story 11.E.1 AC + Story 11.E.2 AC:
  remove AdminAddUserToGroup from IAM lists.
- docs/prd/epic-11-speaker-workflow-refactor.md Story 11.E.1 AC line 1138:
  "event-management-service task-role principal" → "company-user-management-service task-role
  principal" (Resolved Q#2, PM 2026-05-17).

Source commit: d5cf0fccac3cf9befafa3f6edddac51b1949080d (refs/remotes/origin/feature/epic-6)
```

**And** the rationale ("never stored at rest, nothing to encrypt") is captured in the commit message so a future reader of `git log` understands the omission without re-reading the refactor plan.

---

### AC4 — Bump `tempPasswordValidity` from 7 to 14 days; verify policy admits temp password (NFR9, Resolved Q#4)

**Given** the existing `UserPool` password policy in `infrastructure/lib/stacks/cognito-stack.ts` lines 197-205:

```typescript
passwordPolicy: {
  minLength: 8,
  requireLowercase: true,
  requireUppercase: true,
  requireDigits: true,
  requireSymbols: true,
  tempPasswordValidity: cdk.Duration.days(7),
},
```

**When** the dev edits the policy,
**Then** `tempPasswordValidity` is changed from `cdk.Duration.days(7)` to `cdk.Duration.days(14)`:

```typescript
passwordPolicy: {
  minLength: 8,
  requireLowercase: true,
  requireUppercase: true,
  requireDigits: true,
  requireSymbols: true,
  tempPasswordValidity: cdk.Duration.days(14),  // Story 11.E.1 / Resolved Q#4 — 14-day window for invitation→first-login (was 7)
},
```

**And** the dev confirms the rest of the policy admits a strong random generator: minimum length 8, contains lowercase + uppercase + digit + symbol — Story 11.E.2's generator (≥16 chars, all four character classes) trivially satisfies this. **No other policy fields are changed** in this story.

**And** the dev does **not** weaken any policy constraint to match a generator deficiency. If a generator-vs-policy conflict surfaces in 11.E.2 design, the **generator** is fixed (per NFR9), not the policy.

**And** the change is captured in the PR description's "Verification → NFR9 password-policy review" section: one sentence on the 7→14 bump and its rationale (invitation→first-login window now spans two weekends instead of one).

**And** a CDK unit test asserts the new value. Add to `infrastructure/test/unit/cognito-stack.test.ts` — the existing `should_createUserPool_when_cognitoStackDeployed` test already asserts `TemporaryPasswordValidityDays: 7` at line 39. **Update that assertion to `14`** (single-character change):

```typescript
Policies: {
  PasswordPolicy: {
    MinimumLength: 8,
    RequireLowercase: true,
    RequireUppercase: true,
    RequireNumbers: true,
    RequireSymbols: true,
    TemporaryPasswordValidityDays: 14,  // Story 11.E.1 / Resolved Q#4 — was 7
  },
},
```

This is a regression guard — if anyone later reverts the policy to 7 days, the unit test fails loudly.

---

### AC5 — Bruno smoke test for `AdminInitiateAuth` is deferred to Story 11.E.2

**Given** the PRD AC item at lines 1161-1163 ("a smoke test in `bruno-tests/auth/` confirms `AdminInitiateAuth` succeeds with a freshly-created admin-flow user"),
**When** the dev evaluates feasibility,
**Then** the dev confirms that **no Cognito-user-creating code path runs in this story** — the IAM perms + App Client flow only become exercisable once Story 11.E.2 implements `provisionUserWithRole` in CUMS. A Bruno test added now would have to seed a Cognito user out-of-band (manual `aws cognito-idp admin-create-user` from a developer's terminal), which provides no contract-test value.

**And** the PR description includes an explicit note:

> **Bruno smoke test deferred to Story 11.E.2.** That story implements `UserApiClient.provisionUserWithRole` + the `cognitoClient.adminCreateUser` call in CUMS; the natural smoke-test seam is the `POST /api/v1/users/provision` endpoint (added by 11.C.2 and wired to Cognito by 11.E.2). Adding a Bruno test in this story would require the dev to manually pre-create a Cognito user via the AWS CLI, which exercises CDK config, not application contract.

**And** the **directory `bruno-tests/auth/` is NOT created** in this story. Story 11.E.2 will create it if needed, or extend `bruno-tests/users-api/` (which already covers user provisioning per the existing `22-provision-user-with-role.bru` test).

**And** the dev adds a manual-verification checklist item to the PR description: after merge + deploy to staging, run `AWS_PROFILE=batbern-staging aws cognito-idp describe-user-pool-client --user-pool-id $POOL --client-id $CLIENT --query 'UserPoolClient.ExplicitAuthFlows'` and confirm the output contains `"ALLOW_ADMIN_USER_PASSWORD_AUTH"`. The dev runs this themselves and pastes the output into the PR description before requesting review.

---

### AC6 — CDK unit test added for `ALLOW_ADMIN_USER_PASSWORD_AUTH`

**Given** the file `infrastructure/test/unit/cognito-stack.test.ts`,
**When** the dev adds the test from the cherry-pick (lines 194-201 of the source commit's diff),
**Then** the new test case is placed after the existing `should_configureOAuthFlows_when_appClientCreated` test (after line 115 in the current file), and reads:

```typescript
// Test: ALLOW_ADMIN_USER_PASSWORD_AUTH required for server-side Cognito authentication (Story 11.E.1 / AR29)
test('should_enableAdminUserPasswordAuth_when_appClientCreated', () => {
  template.hasResourceProperties('AWS::Cognito::UserPoolClient', {
    ExplicitAuthFlows: Match.arrayWith([
      'ALLOW_ADMIN_USER_PASSWORD_AUTH',
    ]),
  });
});
```

**And** the comment header is updated from the cherry-pick's "(Story 7.1)" to "(Story 11.E.1 / AR29)" — the latter is the current owner of the change.

**And** `Match` is already imported at line 2 of the test file (verified — no new import needed).

---

### AC7 — CDK unit test added for `company-management-stack` IAM Cognito grants

**Given** the file `infrastructure/test/unit/company-management-stack.test.ts` (if it exists) **OR** a new test file if it does not,
**When** the dev verifies the new IAM policy from AC2 is synthesised,
**Then** **at least one** of the following two assertion shapes is added (whichever fits the file's existing convention; if no test file exists for this stack today, the dev adds one):

**Shape (a) — explicit Action-list assertion:**

```typescript
template.hasResourceProperties('AWS::IAM::Policy', {
  PolicyDocument: {
    Statement: Match.arrayWith([
      Match.objectLike({
        Effect: 'Allow',
        Action: Match.arrayWith([
          'cognito-idp:AdminCreateUser',
          'cognito-idp:AdminSetUserPassword',
          'cognito-idp:AdminInitiateAuth',
          'cognito-idp:AdminGetUser',
        ]),
      }),
    ]),
  },
});
```

**Shape (b) — full Action-list set-equality (preferred for least-privilege regression guard):**

```typescript
template.hasResourceProperties('AWS::IAM::Policy', {
  PolicyDocument: {
    Statement: Match.arrayWith([
      Match.objectLike({
        Effect: 'Allow',
        Action: [
          'cognito-idp:AdminCreateUser',
          'cognito-idp:AdminSetUserPassword',
          'cognito-idp:AdminInitiateAuth',
          'cognito-idp:AdminGetUser',
        ],
        // Resource: scoped to the User Pool ARN (CDK references it via Fn::GetAtt or Fn::ImportValue)
      }),
    ]),
  },
});
```

The dev picks one based on what neighbouring stack tests do. The least-privilege regression-guard (shape b) is **preferred** since it would fail loudly if someone later adds `cognito-idp:*`, `AdminAddUserToGroup`, or another Admin* action without explicit review — which is the NFR5 invariant we want a test to enforce.

**And** a separate negative-assertion test confirms `AdminAddUserToGroup` is NOT granted (regression guard for Resolved Q#1):

```typescript
test('should_notGrantAdminAddUserToGroup_when_companyManagementStackDeployed', () => {
  const policies = template.findResources('AWS::IAM::Policy');
  const allActions = Object.values(policies).flatMap((p: any) =>
    (p.Properties?.PolicyDocument?.Statement ?? []).flatMap((s: any) =>
      Array.isArray(s.Action) ? s.Action : [s.Action]
    )
  );
  expect(allActions).not.toContain('cognito-idp:AdminAddUserToGroup');
});
```

**And** the primary test name follows the project convention from `project-context.md` "Testing Rules" §"Test Naming Convention": `should_grantCognitoAdminPerms_when_companyManagementStackDeployed`.

---

### AC8 — All existing CDK unit tests still pass

**Given** the full infrastructure test suite,
**When** `cd infrastructure && npm test 2>&1 | tee /tmp/cdk-test.log` is run,
**Then** the output ends with **zero failing tests** across all stack and Lambda test files,
**And** the new AC6 + AC7 test cases are visible in the suite output,
**And** the `secrets-stack.test.ts` resource-count assertion still asserts `resourceCountIs('AWS::SecretsManager::Secret', 1)` (because AC3 skipped the encryption-key secret).

**And** any pre-existing snapshot tests that compare against fixture JSON in `infrastructure/test/__snapshots__/` are regenerated if and only if the diff is justified by the AC1 / AC2 changes; the dev should NOT run a blanket `--updateSnapshot` without manual review of each diff.

---

### AC9 — Cherry-pick commit message and PR description capture the deviation

**Given** the dev creates the single commit for this story (or two commits if they choose to separate the cherry-pick from the test addition — both are fine),
**When** the commit message is composed,
**Then** the message includes:
- The literal phrase "Story 11.E.1" in the subject or body.
- A reference to source commit `d5cf0fccac3cf9befafa3f6edddac51b1949080d`.
- An explicit list of what is **omitted** from the source commit (per AC3).
- The least-privilege scope justification ("scoped to userPool ARN, five admin actions only").

**And** the PR description includes:
- The verbatim AC4 NFR9 password-policy review note.
- The verbatim AC5 Bruno-test deferral note.
- The AWS-CLI verification output from AC5's manual check (after deploy to staging).
- A "Cherry-pick deviation" section listing the four omitted hunks from AC3 in plain language.

---

### AC10 — Branch-deletion decision is NOT in scope (PRD line 1155-1159 clarification)

**Given** PRD line 1155-1159 says "Given the `feature/epic-6` branch is reviewed after the cherry-pick, when the team decides it is fully drained, then the branch is deleted ... execution of the delete is **not** part of this story's acceptance",
**When** the dev finalises the PR,
**Then** the dev does **not** run `git push origin --delete feature/epic-6` as part of this story,
**And** the PR description adds a "Follow-up" note: "After this PR merges and Phase E settles, the team / Phase F operator may delete `refs/remotes/origin/feature/epic-6`. Not part of this story per PRD line 1156-1159."

---

## Tasks / Subtasks

Tasks ordered to compile + test incrementally. Each task names the AC it satisfies and the test/command to run after to lock it in.

### Task 1 — Cherry-pick `d5cf0fcc` and immediately revert the unwanted hunks (AC1, AC2, AC3, AC6) [x]

1.1. Verify `refs/remotes/origin/feature/epic-6` is reachable: `git log feature/epic-6 --oneline -1` should show `d5cf0fcc`. If not, `git fetch origin feature/epic-6` first.
1.2. `git cherry-pick --no-commit d5cf0fcc` — stages the full diff without auto-committing. (Using `--no-commit` makes the surgical revert in 1.3 easier and keeps a single clean commit.)
1.3. **Selectively un-stage and revert the deletions** (the dev has two equivalent paths — pick one):
   - **Path A (preferred — cleaner history):** `git restore --staged --worktree -- infrastructure/lib/stacks/secrets-stack.ts infrastructure/test/unit/secrets-stack.test.ts` to drop the secret + its test. Then `git restore --staged --worktree -- infrastructure/lib/constructs/domain-service-construct.ts infrastructure/bin/batbern-infrastructure.ts` to drop the comment + construct-prop hunks. Then manually edit `infrastructure/lib/stacks/company-management-stack.ts` to **remove the encryption-key import + ECS secret pass-through hunks** while **keeping the IAM-policy hunk**. (The cherry-picked stack file has both; we want only the IAM block.)
   - **Path B:** `git cherry-pick --abort`, then **manually re-create** AC1 + AC2 + AC6 changes by hand using the diffs in AC1/AC2/AC6. This is fine — the diffs are small.
1.4. Verify the staged diff is exactly: AC1's one-line addition to `cognito-stack.ts`, AC2's IAM block in `company-management-stack.ts`, and AC6's new test in `cognito-stack.test.ts`. Run `git diff --cached` to confirm.
1.5. **Verify `AdminAddUserToGroup` is NOT in the IAM action list.** The original `d5cf0fcc` did not include it (lucky); Resolved Q#1 keeps it out. Run `git diff --cached infrastructure/lib/stacks/company-management-stack.ts | grep -c AdminAddUserToGroup` → expects `0`.
1.6. Update inline comments per AC1's "AR29 / 11.E.1" anchor and AC6's "Story 11.E.1 / AR29" header (the cherry-pick comments say "Story 7.1"; update to current owner). Also add the AC2 inline comment block (Resolved Q#1 rationale for omitting `AdminAddUserToGroup`).
1.7. **Verify**: `cd infrastructure && npx tsc --noEmit 2>&1 | tee /tmp/cdk-tsc.log` — TypeScript compilation clean.

### Task 2 — Add the `company-management-stack` IAM test (AC7) [x]

2.1. Check whether `infrastructure/test/unit/company-management-stack.test.ts` exists.
   - **If yes**: extend the existing `describe` block with the new test case named per `project-context.md` test-naming convention.
   - **If no**: create the file. Boilerplate pattern from neighbouring stack tests:
     ```typescript
     import { App } from 'aws-cdk-lib';
     import { Template, Match } from 'aws-cdk-lib/assertions';
     import { CompanyManagementStack } from '../../lib/stacks/company-management-stack';
     import { devConfig } from '../../lib/config/dev-config';
     // Plus minimum stub props for the constructor (cluster, vpc, security group, userPool, userPoolClient)
     // — copy the construction pattern from cognito-stack.test.ts or another existing test if no
     // company-management test exists yet.
     ```
   - The dev should look at one of the existing microservice stack tests (e.g. `event-management-stack.test.ts` if it exists) to see how it stubs the cross-stack props.
2.2. Add the test case from AC7 (preferred shape b — set-equality regression guard).
2.3. **Verify**: `cd infrastructure && npm test -- company-management 2>&1 | tee /tmp/cdk-test-company.log` — green for the new test.
2.4. **Run the full suite**: `cd infrastructure && npm test 2>&1 | tee /tmp/cdk-test-full.log`. Confirm zero failures across all stack tests (AC8).

### Task 3 — Bump `tempPasswordValidity` 7→14 days + update test assertion (AC4) [x]

3.1. Open `infrastructure/lib/stacks/cognito-stack.ts` line 204. Change `tempPasswordValidity: cdk.Duration.days(7)` to `cdk.Duration.days(14)`. Add the inline comment from AC4.
3.2. Open `infrastructure/test/unit/cognito-stack.test.ts` line 39. Change `TemporaryPasswordValidityDays: 7` to `TemporaryPasswordValidityDays: 14`. Add the trailing comment from AC4.
3.3. Mentally validate that a random temp password of e.g. 24 chars containing all four character classes (e.g. `K8s#mQ2$pL9*nR4!vF6@xZ7&`) satisfies the rest of the policy: ≥ 8 chars ✓, has lowercase ✓, uppercase ✓, digit ✓, symbol ✓. No further policy change required.
3.4. Document in the PR description under "Verification → NFR9 password-policy review": one sentence on the 7→14 bump rationale (invitation→first-login window now spans two weekends), one sentence confirming the rest of the policy admits a strong random temp password.
3.5. **Verify**: `cd infrastructure && npm test -- cognito-stack 2>&1 | tee /tmp/cdk-test-cognito.log` — the updated `should_createUserPool_when_cognitoStackDeployed` test and new `should_enableAdminUserPasswordAuth_when_appClientCreated` test both pass.

### Task 4 — Doc alignment (Resolved Q#1, Q#2) — same commit per CLAUDE.md doc-drift policy [x]

4.1. **ADR-009** (`docs/architecture/ADR-009-unified-speaker-workflow.md`):
   - Line 263 ("SPEAKER role is granted via `cognito-idp:AdminAddUserToGroup`."): replace with "SPEAKER role is granted via a row insert into PostgreSQL `user_roles` (per ADR-001 database-centric role storage). No Cognito group operations are performed."
   - Lines 500-505 (the `SpeakerProvisioningService` skeleton): replace the `cognitoClient.adminAddUserToGroup(email, "SPEAKER")` call with a comment block describing the `user_roles` insert path (the actual Java implementation lands in Story 11.E.2; the ADR skeleton is illustrative).
   - Add a revision-history row dated 2026-05-17 noting "Story 11.E.1 PM-resolved Q#1: AdminAddUserToGroup dropped from Decision 3; role grant uses PostgreSQL user_roles per ADR-001."
4.2. **PRD** (`docs/prd/epic-11-speaker-workflow-refactor.md`):
   - Line 93 (NFR2): replace "`AdminCreateUser` + `AdminAddUserToGroup` + `AdminSetUserPassword`" with "`AdminCreateUser` + `AdminSetUserPassword`".
   - Lines 102-106 (NFR5): drop `AdminAddUserToGroup` from the IAM action list.
   - Lines 192-193 (AR30): drop `AdminAddUserToGroup`. Final list: `AdminCreateUser`, `AdminSetUserPassword`, `AdminInitiateAuth`, `AdminGetUser`.
   - Line 1136 (Story 11.E.1 AC): drop `AdminAddUserToGroup`.
   - **Line 1138** (Resolved Q#2): change "event-management-service task-role principal" to "company-user-management-service task-role principal".
   - Lines 1190-1191 (Story 11.E.2 AC — "And calls `cognito-idp:AdminAddUserToGroup` (or equivalent role-grant) to assign the SPEAKER role group"): replace with "And inserts a row into PostgreSQL `user_roles` (SPEAKER role) per ADR-001. No Cognito group operations occur."
4.3. **sprint-status.yaml**: update the line-192 comment for `11-e-1-cdk-iam-prereq-cognito-admin-flow` to drop `AdminAddUserToGroup` from the perm list it embeds.
4.4. **Verify**: `grep -n AdminAddUserToGroup docs/` should return zero matches (apart from this story file's "Open Questions (resolved)" section, which references the resolution).

### Task 5 — Commit + PR description (AC9, AC10) [deferred to user — see Completion Notes]

5.1. `git add -p` the staged changes (cognito-stack.ts, company-management-stack.ts, cognito-stack.test.ts, company-management-stack.test.ts, ADR-009, PRD, sprint-status.yaml). Verify nothing else is staged.
5.2. Commit using the AC3 message template (or close — keep the "Story 11.E.1", source SHA, omitted list, doc-alignment section, and least-privilege scope statement).
5.3. Push the branch and open a PR against `feature/speaker-workflow-refactor`. The PR description must include:
   - The "Cherry-pick deviation" section listing the four omitted hunks.
   - The AC4 NFR9 password-policy review note (with the 7→14 bump rationale).
   - The AC5 Bruno-test deferral note.
   - A "Doc alignment" section listing the ADR-009 + PRD edits from Task 4.
   - The AC10 "branch-deletion not in scope" follow-up note.
5.4. After merge + auto-deploy to staging, run the AC5 AWS-CLI verification command and paste the output into the PR description as a post-merge edit (or into the merge-commit description if the PR is already closed).

### Task 6 — Mark `sprint-status.yaml` `review` once the PR is open [x]

6.1. Update `_bmad-output/implementation-artifacts/sprint-status.yaml`: set `11-e-1-cdk-iam-prereq-cognito-admin-flow` from `ready-for-dev` (set by this story) → `in-progress` (when dev starts) → `review` (when PR opens). Bump `last_updated`.

---

### Review Findings (`/bmad-code-review` 2026-05-17)

Three parallel adversarial review layers (Blind Hunter, Edge Case Hunter, Acceptance Auditor) executed against the uncommitted diff. **Acceptance Auditor: 8/8 implementable ACs satisfied** (AC9/AC10 are user-side commit/PR follow-ups). 22 findings dismissed as noise / sanctioned-by-spec / out-of-scope. The remaining items below.

- [x] [Review][Decision] **`cognito-idp:AdminInitiateAuth` grant — confirmed in scope** — Resolved 2026-05-17 by PM (Nissim): **keep all four actions**, trust ADR-009 §Decision 3. The impersonation surface (CUMS can `AdminInitiateAuth(ADMIN_USER_PASSWORD_AUTH, anyUser, knownTempPw)`) is acknowledged at the architecture-decision level. If Story 11.E.2's `provisionUserWithRole` ends up not actually calling `AdminInitiateAuth`, the action can be dropped in 11.E.2's own PR — keeping it now keeps the cherry-pick faithful to the source commit and avoids a churning follow-up. Side-note clarification during resolution: the IAM grant attaches to the **ECS task role** (an AWS IAM principal), NOT to Cognito user-pool users — task-role credentials are picked up from the ECS task metadata endpoint and used transparently by the AWS SDK in CUMS code. Frontend Cognito JWTs are NOT AWS credentials and cannot call `cognito-idp:Admin*`. [Blind Hunter #9 — dismissed]

- [x] [Review][Patch] **Positive IAM test now asserts `Resource` is scoped to the User Pool ARN** [`infrastructure/test/unit/company-management-stack.test.ts:88-114`] — Applied 2026-05-17. Added `Resource: Match.objectLike({ 'Fn::Join': Match.arrayWith([Match.arrayWith([Match.stringLikeRegexp(':cognito-idp:.*:userpool/eu-central-1_test$')])]) })` to the positive test. Catches regressions to `'*'`, to broader ARN patterns (`userpool/*`), or to multi-element `resources` arrays — all collapse the Resource shape away from the single-element Fn::Join that CDK emits today. 2/2 patched tests + 289/289 full infra suite pass. [Edge Case Hunter #4]

- [x] [Review][Patch] **Negative regression scan now walks inline role policies too** [`infrastructure/test/unit/company-management-stack.test.ts:117-150`] — Applied 2026-05-17. Refactored to scan BOTH standalone `AWS::IAM::Policy` resources AND inline `AWS::IAM::Role.Properties.Policies[*]` statements. A future `Role.attachInlinePolicy(...)` grant of `AdminAddUserToGroup` now fires the guard. Scope-to-CUMS is implicit: within `CompanyManagementStack`'s template the only IAM roles are the CUMS service's task role + execution role, so scanning all roles in the stack is effectively scoped to CUMS without an explicit logical-ID filter. Block-scope helper `collectFromStatements()` extracted to avoid duplication. 2/2 patched tests + 289/289 full infra suite pass. [Blind Hunter #3 + #18 + Edge Case Hunter #1]

- [x] [Review][Defer] **`fromUserPoolId(...) as unknown as cognito.UserPool` cast in test stub** [`infrastructure/test/unit/company-management-stack.test.ts:47-51`] — deferred, pre-existing. Spec dev-agent record line 558 explicitly acknowledges: "widening the prop to `IUserPool` is outside this story's scope." If `props.userPool` is ever read for anything beyond `.userPoolArn` (e.g., `.addClient`), the test stub will throw at synth time and reveal the prop-type mismatch. [Blind Hunter #4 + Edge Case Hunter #7]

- [x] [Review][Defer] **No IAM `Condition` keys narrowing `AdminSetUserPassword` to specific usernames** [`infrastructure/lib/stacks/company-management-stack.ts:163-176`] — deferred, defense-in-depth hardening. Resource-ARN scoping is already in place; condition-key tightening (e.g., `aws:RequestTag/Role: SPEAKER` or `cognito-idp:username` patterns) would harden against a compromised CUMS service resetting any user's password. Out of scope for the cherry-pick. [Blind Hunter #10]

- [x] [Review][Defer] **`should_configureOAuthFlows_when_appClientCreated` may not pin all four `ExplicitAuthFlows`** [`infrastructure/test/unit/cognito-stack.test.ts:~70-88`] — deferred, pre-existing test gap. The new AC6 test uses `Match.arrayWith(['ALLOW_ADMIN_USER_PASSWORD_AUTH'])`, which per the AC is correct (presence-only). But the broader gap is whether SRP / USER_PASSWORD_AUTH / CUSTOM_AUTH are actually pinned anywhere. Tightening to a closed set would go beyond AC6's literal wording — better addressed in a separate test-hardening sweep. [Blind Hunter #19 + Edge Case Hunter #5]

- [x] [Review][Defer] **ECS task-role IAM policy picked up at next STS credential refresh (≤6h), not immediately** — deferred, operational note for 11.E.2 rollout. IAM-policy-only changes do NOT trigger ECS task replacement. Story 11.E.2's first deploy must force a service redeploy (or wait ≤6h) to guarantee task-credential refresh, or `AdminCreateUser` calls fail with AccessDenied. Capture in 11.E.2's "Verification" section. [Edge Case Hunter #8]

- [x] [Review][Defer] **14-day temp password window security-tradeoff comment** [`infrastructure/lib/stacks/cognito-stack.ts:204`] — deferred. The inline comment captures UX rationale ("two weekends") but not the security tradeoff (longer email-resident credential validity). The 11.E.2 invitation-email rewrite story is the better seam to document the threat model (email transport, inbox-retention assumptions, recovery-via-forgot-password fallback). [Blind Hunter #6]

- [x] [Review][Defer] **Tests not parametrized across `isProduction: true|false`** [`infrastructure/test/unit/company-management-stack.test.ts`] — deferred, coverage enhancement. The new test only exercises `stagingConfig` (which is `isProduction: true`). A future config-conditional grant in `CompanyManagementStack` would not be caught. [Edge Case Hunter #6]

---

## Dev Notes

### Why this story exists as a separate Phase E kickoff

Phase E's narrative arc (per refactor plan §5 + ADR-009 §Decision 3) is "speakers authenticate via standard Cognito with a forced password change." Three discrete stories carry it:

- **11.E.1 (this story)** — Land the infrastructure preconditions (App Client flow + IAM perms). Tiny CDK diff. No application code. Easy to review, deploy, and roll back independently.
- **11.E.2** — The substantive provisioning hook in CUMS (`AdminCreateUser` + temp-password generation + email rewrite + 10-locale i18n). Depends on this story for the IAM perms + App Client flow.
- **11.E.3** — The frontend half: `@PreAuthorize("hasRole('SPEAKER')")` on `/api/v1/speaker-portal/**`, frontend Cognito-session refactor, multi-role nav cherry-pick.

Coupling all three into one story would create a giant PR mixing CDK, backend Java, and frontend TypeScript across two services and one cherry-pick from a different branch. Splitting the IAM prerequisite into its own tiny PR is the correct seam — it lets Phase E settle one slice at a time and isolates the cherry-pick mechanics from the substantive application work.

### Why this story is "blocked-on nothing in-epic"

Per sprint-status.yaml line 192's comment ("Phase E; no in-epic prereq"), this story has zero in-epic prerequisites. It can land on `feature/speaker-workflow-refactor` HEAD any time. The only external prerequisite is reachability of `refs/remotes/origin/feature/epic-6` for the cherry-pick.

That said, in practice the dev will probably run this story right before starting Story 11.E.2, so the deploy of the IAM perms and the application code that consumes them happen on the same overall Phase-E cycle. But the **story itself** can land independently without breaking anything — the new App Client flow and IAM perms are simply unused until 11.E.2 lands the calling code.

### Reuse, don't recreate — the d5cf0fcc commit is the playbook

This story does NOT design anything new. It cherry-picks an existing, reviewed, tested CDK change from `feature/epic-6` and applies four small modifications from PM-resolved Open Questions. The architecture decision was made for ADR-009 / refactor plan §3.5 / PRD AR29-AR30; this story is purely mechanical execution.

The trap to avoid: do not re-derive the IAM policy structure from scratch. The cherry-pick is the implementation truth. The **net-new** content this story authors:
- `tempPasswordValidity` 7→14 days in `cognito-stack.ts` + the matching test-assertion bump (Resolved Q#4).
- Updated inline comments (anchoring to "11.E.1" instead of "Story 7.1"; explanatory note that `AdminAddUserToGroup` is deliberately omitted per Resolved Q#1 / ADR-001).
- A new CDK unit test on `company-management-stack` (AC7) — the original commit's IAM test on that stack did not exist.
- A negative-assertion test that `AdminAddUserToGroup` is NOT granted (AC7 regression guard for Resolved Q#1).
- ADR-009 + PRD doc edits to drop `AdminAddUserToGroup` and fix the PRD line 1138 EMS→CUMS wording (Task 4 — same commit per CLAUDE.md doc-drift policy).

### What this story is NOT doing (scope guard)

- **No application code.** Zero Java changes, zero TypeScript backend changes, zero frontend changes. CDK + tests + doc-alignment edits only.
- **No new Cognito Lambda triggers** (per NFR2 — ADR-009 §Decision 3 explicitly says "no Lambda triggers required").
- **No `COGNITO_PASSWORD_ENCRYPTION_KEY` secret** (per AC3 / AR42 / refactor plan §9.2 / Resolved Q#3).
- **No password-policy weakening.** AC4 bumps only `tempPasswordValidity` (7→14 days, Resolved Q#4); the minimum-length and character-class requirements stay at their current strong values.
- **No `AdminAddUserToGroup` IAM grant** (Resolved Q#1 — roles in DB per ADR-001, no Cognito groups). PRD AR30 / NFR5 + ADR-009 §Decision 3 + Implementation Guidelines updated in same commit.
- **No new `bruno-tests/auth/` directory** (per AC5 — deferred to 11.E.2).
- **No deletion of `feature/epic-6`** (per AC10 / PRD line 1156-1159).
- **No `event-management-service` task-role IAM changes.** The Cognito calls happen from CUMS, not EMS, per ADR-009 §Decision 3 + Story 11.C.2's `UserApiClient.provisionUserWithRole` design. PRD line 1138 is fixed in this story per Resolved Q#2.
- **No deploy to staging.** This story merges; CI/CD then deploys to staging automatically. The dev's post-merge AC5 verification (AWS CLI describe-user-pool-client) confirms the deploy worked, but is not a manual deploy step.

### Project Structure Notes

- Primary files under change:
  - `infrastructure/lib/stacks/cognito-stack.ts` — one-line addition at line 237-241.
  - `infrastructure/lib/stacks/company-management-stack.ts` — one new IAM `PolicyStatement` block between lines 159 and 161.
  - `infrastructure/test/unit/cognito-stack.test.ts` — one new `test()` block after line 115.
  - `infrastructure/test/unit/company-management-stack.test.ts` — one new test case (or new file if not present).
- No new files outside `infrastructure/`. No changes to `services/`, `web-frontend/`, `docs/`, or any other top-level directory.
- The total diff should be on the order of **15-25 lines** of net additions (excluding the new test).

### Testing Standards (infrastructure-only story)

- **CDK unit tests (Vitest):** `cd infrastructure && npm test`. Coverage target per `infrastructure/CLAUDE.md` §"Testing Standards": > 90% for all stacks. This story adds two test cases and touches one stack constructor; coverage stays above target.
- **No integration tests** required (no application code).
- **No Bruno tests** in this story (per AC5).
- **No Playwright tests** required (no UI).
- **Pre-commit hook:** runs ESLint + Prettier on `infrastructure/`. The dev should run `cd infrastructure && npx tsc --noEmit` before committing to catch type issues that the hook does not block on.
- **Manual post-merge verification (AC5 step 4):** AWS CLI describe-user-pool-client confirms `ALLOW_ADMIN_USER_PASSWORD_AUTH` lands in the deployed pool.

### Cherry-pick mechanics: pitfalls to watch for

The `d5cf0fcc` commit modified files that have continued to evolve on `feature/speaker-workflow-refactor`. Specifically:
- `infrastructure/lib/stacks/company-management-stack.ts` on `feature/speaker-workflow-refactor` HEAD already uses the `additionalSecrets` mechanism (lines 81-84) for `WATCH_JWT_SECRET`. The cherry-pick may produce a merge conflict in this region — the dev should keep the existing `WATCH_JWT_SECRET` flow and only add the new Cognito IAM block.
- `infrastructure/lib/stacks/cognito-stack.ts` may have evolved around line 237-241. The dev should verify the cherry-pick lands the `adminUserPassword: true` line correctly and not in the wrong `authFlows` object (there may be multiple `UserPoolClient` instances if any have been added).
- The `cognito-stack.test.ts` test ordering may have shifted; the dev places the new AC6 test in a structurally sensible location (after the existing OAuth-flows test) regardless of exact line numbers.

If `git cherry-pick d5cf0fcc` produces conflict markers, **Path B (manual re-creation)** from Task 1.3 is the cleanest fallback — the diff is small enough to write by hand from the AC specs.

### References

- [Source: docs/prd/epic-11-speaker-workflow-refactor.md lines 1119-1163] — Story 11.E.1 AC list (this story's primary spec).
- [Source: docs/prd/epic-11-speaker-workflow-refactor.md lines 86-124] — NFR2, NFR5, NFR9 definitions.
- [Source: docs/prd/epic-11-speaker-workflow-refactor.md lines 189-218] — AR29, AR30, AR42 architectural-requirement definitions.
- [Source: docs/plans/speaker-workflow-refactor.md §3.5 "infrastructure (CDK)"] — high-level CDK delta description.
- [Source: docs/plans/speaker-workflow-refactor.md §9.2 "feature/epic-6" cherry-pick disposition] — what to take from `d5cf0fcc`, what to skip.
- [Source: docs/plans/speaker-workflow-refactor.md §9.5] — summary disposition table.
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md §Decision 3] — standard Cognito + forced password change architectural decision.
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md §Implementation Guidelines lines 482-510] — `SpeakerProvisioningService` skeleton showing `cognitoClient.adminCreateUser` + `adminAddUserToGroup` call shape.
- [Source: infrastructure/lib/stacks/cognito-stack.ts lines 234-267] — current `UserPoolClient` definition (the `adminUserPassword: true` line lands here).
- [Source: infrastructure/lib/stacks/cognito-stack.ts lines 197-205] — current password policy (AC4 review target).
- [Source: infrastructure/lib/stacks/cognito-stack.ts lines 277-279] — "REMOVED: Cognito Groups (ADR-001)" comment — relevant to Open Question #1.
- [Source: infrastructure/lib/stacks/company-management-stack.ts lines 134-159] — existing IAM grant cluster (new Cognito block lands after this).
- [Source: infrastructure/test/unit/cognito-stack.test.ts lines 105-115] — existing `should_configureOAuthFlows_when_appClientCreated` test (AC6 test inserted after).
- [Source: infrastructure/test/unit/secrets-stack.test.ts lines 19-22] — `resourceCountIs(... , 1)` assertion that this story does NOT bump.
- [Source: infrastructure/CLAUDE.md §"Testing Standards"] — > 90% coverage target, TDD requirement.
- [Source: _bmad-output/implementation-artifacts/11-c-2-userapiclient-provisioning-contentsubmissionservice-shared.md] — the prior story that placed `provisionUserWithRole` on the CUMS side (architecture truth for "where do Cognito Admin* calls live").
- [Source: _bmad-output/project-context.md §"Authentication & Roles"] — "Roles stored exclusively in PostgreSQL `role_assignments` — NOT in Cognito groups." (Relevant to Open Question #1.)
- [Source: _bmad-output/project-context.md §"Test Naming Convention"] — `should_expectedBehavior_when_condition` (AC7).
- **External source commit:** `d5cf0fccac3cf9befafa3f6edddac51b1949080d` on `refs/remotes/origin/feature/epic-6` — "feat(infra): add Cognito admin permissions and encryption key for server-managed accounts (Story 7.1)".

---

## Dev Agent Record

### Agent Model Used

claude-opus-4-7[1m]

### Debug Log References

- `/tmp/cdk-tsc-2.log` — TypeScript compile clean (exit 0) after all edits.
- `/tmp/cdk-test-company.log` — `company-management-stack.test.ts` initial run: 2/2 tests pass.
- `/tmp/cdk-test-cognito.log` — `cognito-stack.test.ts` after edits: 7/7 tests pass (was 6; new `should_enableAdminUserPasswordAuth_when_appClientCreated`).
- `/tmp/cdk-test-full.log` — full infrastructure suite: `Test Suites: 2 skipped, 19 passed, 19 of 21 total. Tests: 22 skipped, 289 passed, 311 total.` Zero failures.
- `/tmp/cherry-diffs.log` — `git show d5cf0fcc` reference inspected; Path B (manual re-creation per AC1/AC2/AC4/AC6 specs) used. Diff is small (~15 LOC net additions), so no `cherry-pick --no-commit` was run; AC content was authored directly per story specs.
- `/tmp/git-status-root.log` — final file changeset (6 modified/created).

### Completion Notes List

**AC1 ✅** — `infrastructure/lib/stacks/cognito-stack.ts:241` adds `adminUserPassword: true` with the AR29 / cherry-pick d5cf0fcc anchor comment. CDK now synthesises `ALLOW_ADMIN_USER_PASSWORD_AUTH` in `ExplicitAuthFlows` (asserted by AC6 test).

**AC2 ✅** — `infrastructure/lib/stacks/company-management-stack.ts:161-176` adds the four-action `iam.PolicyStatement` on `taskDefinition.taskRole`, scoped to `props.userPool.userPoolArn`. Inline comment cites AR30 / cherry-pick d5cf0fcc / Resolved Q#1. No `AdminAddUserToGroup`. No event-management-stack changes.

**AC3 ✅** — `COGNITO_PASSWORD_ENCRYPTION_KEY` deliberately NOT cherry-picked. `infrastructure/test/unit/secrets-stack.test.ts:24` unchanged (still asserts `resourceCountIs('AWS::SecretsManager::Secret', 2)` — JWT + WATCH_JWT). The "1" reference in the story branch-state note (line 42) was outdated at story-creation; the **intent** (do not bump the count) is preserved. `domain-service-construct.ts` already supports `additionalSecrets` (verified pre-existing). No `secrets-stack.ts`, no `bin/batbern-infrastructure.ts`, no construct-prop hunks introduced.

**AC4 ✅** — `cognito-stack.ts:204` now `cdk.Duration.days(14)` with the Resolved Q#4 anchor comment. `cognito-stack.test.ts:39` updated to assert `TemporaryPasswordValidityDays: 14`. Rest of the policy (length, character classes) admits Story 11.E.2's strong random temp password generator without modification — generator-vs-policy invariant from NFR9 preserved.

**AC5 ✅** — Bruno smoke test deliberately deferred to Story 11.E.2. `bruno-tests/auth/` not created in this story. Post-merge AWS CLI verification (`describe-user-pool-client ... ExplicitAuthFlows`) is the operator-side check; to be performed by the human running the merge.

**AC6 ✅** — `cognito-stack.test.ts:117-124` adds `should_enableAdminUserPasswordAuth_when_appClientCreated` with the Story 11.E.1 / AR29 comment header. `Match.arrayWith(['ALLOW_ADMIN_USER_PASSWORD_AUTH'])`.

**AC7 ✅** — New file `infrastructure/test/unit/company-management-stack.test.ts` with two tests: positive `should_grantCognitoAdminPerms_when_companyManagementStackDeployed` (set-equality regression guard — preferred shape b per AC7), negative `should_notGrantAdminAddUserToGroup_when_companyManagementStackDeployed` (Resolved Q#1 regression guard). Uses `IMAGE_TAG=test-image-tag` + `fromUserPoolId(...) as unknown as cognito.UserPool` cast — the cast is necessary because the stack types `userPool` as the concrete `cognito.UserPool` class but only accesses `.userPoolArn` at runtime; widening the prop to `IUserPool` is outside this story's scope.

**AC8 ✅** — Full `npm test` in `infrastructure/`: 289 passed, 22 skipped, **zero failures**. New tests visible in suite (`--listTests` confirms `company-management-stack.test.ts`).

**AC9 deferred to user** — Commit message + PR description authoring is a manual user step. All the substantive content (cherry-pick anchor SHA `d5cf0fccac3cf9befafa3f6edddac51b1949080d`, omitted list, four-action least-privilege scope, doc-alignment scope) is captured in this story file and the inline code comments for re-use in the commit/PR text.

**AC10 deferred to user** — `git push origin --delete feature/epic-6` deliberately NOT performed; remains for the team / Phase F operator per PRD line 1166-1168. Should be captured in PR follow-up section by the user.

**Task 4 doc alignment** — Verified that ADR-009, PRD epic-11, and sprint-status comment were already aligned at story creation (per story closing note line 562-563). Two further drifted references found and corrected in the same scope:
- `docs/prd/epic-9-speaker-authentication.md:51` — Phase A residue (positive grant of AdminAddUserToGroup) rewritten as the negative-assertion four-action list with the Resolved Q#1 anchor.
- `docs/api/users-api.openapi.yml:1390-1395` — provision-user-with-role description's "Cognito wiring will include AdminAddUserToGroup" rewritten to "SPEAKER role is granted via PostgreSQL `user_roles` per ADR-001 — NOT via AdminAddUserToGroup".

Other `AdminAddUserToGroup` mentions in `docs/` are either negative assertions (already correct), revision-history entries, or belong to deferred Story 10.24 / archived stories / point-in-time reports — all out of this story's scope.

**Sprint-status** — Story key `11-e-1-cdk-iam-prereq-cognito-admin-flow` transitioned `ready-for-dev` → `in-progress` (during impl) → `review` (final). `last_updated` line 37 updated with the AC1+AC2+AC4+AC6+AC7 changelog.

### File List

Source code (3 files):
- `infrastructure/lib/stacks/cognito-stack.ts` — adminUserPassword:true on App Client (AC1); tempPasswordValidity 7→14 days (AC4).
- `infrastructure/lib/stacks/company-management-stack.ts` — four-action Cognito admin IAM policy on CUMS task role (AC2).
- `infrastructure/test/unit/cognito-stack.test.ts` — new test `should_enableAdminUserPasswordAuth_when_appClientCreated` (AC6); TemporaryPasswordValidityDays assertion bumped 7→14 (AC4).

Test code (new file, 1 file):
- `infrastructure/test/unit/company-management-stack.test.ts` — new file with positive + negative AdminAddUserToGroup IAM regression-guard tests (AC7).

Doc alignment (2 files):
- `docs/prd/epic-9-speaker-authentication.md` — line 51 rewritten to drop the positive AdminAddUserToGroup mention and list the four actually-called admin actions with negative-assertion explanation.
- `docs/api/users-api.openapi.yml` — provision-user-with-role description (lines 1390-1395) rewritten to attribute role-grant to PostgreSQL `user_roles` per ADR-001 (NOT AdminAddUserToGroup).

Sprint tracking (1 file):
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — last_updated line 37 changelog entry + line 192 `ready-for-dev` → `review`.

Story file (this file):
- `_bmad-output/implementation-artifacts/11-e-1-cdk-iam-prereq-cognito-admin-flow.md` — Status `ready-for-dev` → `review`; Tasks 1-4, 6 marked [x]; Task 5 deferred to user (commit/PR); Dev Agent Record, Change Log, File List populated.

### Change Log

| Date | Author | Change |
|------|--------|--------|
| 2026-05-17 | Nissim (PM) | Story drafted by `bmad-create-story`; 4 Open Questions resolved with PM same-day. |
| 2026-05-17 | Claude Opus 4.7 (1M) via `bmad-dev-story` | Implementation: CDK + IAM + tests + doc alignment. All 8 implementable ACs satisfied (AC9/AC10 are user-side commit/PR steps). 289/289 CDK tests pass. Status `ready-for-dev` → `review`. |
| 2026-05-17 | Claude Opus 4.7 (1M) via `bmad-code-review` | Adversarial review (Blind Hunter + Edge Case Hunter + Acceptance Auditor). Auditor: 8/8 ACs satisfied. 1 decision dismissed (AdminInitiateAuth grant — PM confirmed: trust ADR-009). 2 patches applied to `company-management-stack.test.ts`: (1) positive test now asserts `Resource: Match.objectLike({'Fn::Join': ...userpool/eu-central-1_test$})` to catch wildcard-scope regressions; (2) negative regression scan extended to walk `AWS::IAM::Role.Properties.Policies[*]` inline policies in addition to standalone `AWS::IAM::Policy` resources, with shared `collectFromStatements()` helper. 6 items deferred to `deferred-work.md`. 289/289 CDK tests + type-check clean. Status `review` → `done`. |

---

## Open Questions (resolved 2026-05-17)

All four questions were resolved with PM (Nissim) before development. The AC, Tasks, and Dev Notes above already reflect the decisions. Listed here for traceability.

1. ✅ **Drop `AdminAddUserToGroup` from the IAM policy.** Roles live in PostgreSQL `user_roles` per ADR-001; no Cognito groups exist. Granting the permission would be a useless least-privilege violation (NFR5). The PRD's AR30 / NFR5 / Story 11.E.1 AC / Story 11.E.2 AC and ADR-009 §Decision 3 + Implementation Guidelines skeleton were aspirational and contradicted ADR-001; they are all updated in this same commit per CLAUDE.md doc-drift policy. AC2 + AC7 reflect the four-action policy (`AdminCreateUser`, `AdminSetUserPassword`, `AdminInitiateAuth`, `AdminGetUser`). AC7 adds a negative-assertion test as a regression guard.

2. ✅ **PRD line 1138 corrected from "event-management-service" to "company-user-management-service".** Architecture truth wins: ADR-009 §Decision 3, refactor plan §3.2 ("CUMS — Add: Cognito provisioning logic"), Story 11.C.2's `UserApiClient.provisionUserWithRole` design, and the cherry-picked `d5cf0fcc` commit itself all place Cognito SDK calls on CUMS. The PRD wording was an editorial slip and is fixed in this same commit. AC2 places IAM perms on `company-management-stack.ts` (the cherry-pick's location).

3. ✅ **`COGNITO_PASSWORD_ENCRYPTION_KEY` stays skipped.** The temp password is generated, embedded once in the invitation email, and never stored at rest. Nothing to encrypt. Confirmed by PM. AC3 + the commit message explicitly call out the skip so a future reader of `git log` sees the rationale without re-reading the refactor plan.

4. ✅ **Bump `tempPasswordValidity` from 7 to 14 days.** A 14-day window lets the invitation→first-login flow span two weekends comfortably. Speakers who miss even that window can still use Cognito's "Forgot password" path (handled by Story 11.E.3's frontend), so the bump is a UX-improvement-without-functional-regression. AC4 now requires the CDK edit + the test-assertion update on `should_createUserPool_when_cognitoStackDeployed`. The change is a one-token edit (`days(7)` → `days(14)`) in `cognito-stack.ts` and one digit in `cognito-stack.test.ts`.

---

_Story created via `bmad-create-story` skill on 2026-05-17. All 4 Open Questions resolved with PM the same day. Story revised in place to reflect: (1) drop `AdminAddUserToGroup` from IAM policy + align PRD AR30/NFR5/AC + ADR-009 §Decision 3; (2) fix PRD line 1138 EMS→CUMS; (3) confirm `COGNITO_PASSWORD_ENCRYPTION_KEY` skip; (4) bump `tempPasswordValidity` 7→14 days with regression-guard test. Phase E kickoff. No in-epic prerequisites. Ready for `bmad-dev-story` execution._
