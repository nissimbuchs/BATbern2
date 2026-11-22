---
title: 'The agent signing key is in production, private to CUMS'
type: 'feature'
ticket: '3'
created: '2026-11-22'
status: 'built'
baseline_revision: '5bf5d3369c84d4aae1667220354e725b4ab467b8'
route: 'full'
route_source: 'auto'
risk: 'high'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/docs/architecture/coding-standards.md'
warnings: ['oversized']
deferred: []
---

<intent-contract>

## Intent

**Problem:** Story 1's decoder turns the agent kind on only when `agent.jwt.public-key` is set, and story 6 needs an RS256 private key in CUMS; production has neither, nor the EMS and agent-service caller secrets story 5 checks. A deploy must stay safe before the owner has generated the key.

**Approach:** CDK (SecretsStack) creates the private-key secret and the public-key SSM String parameter, both holding the placeholder `none`, and two CDK-generated caller secrets. ECS injects them at task start: private key and both caller secrets on CUMS, the EMS caller secret on EMS, the public key (from SSM) on gateway, CUMS, EMS, PCS. The decoder treats `none` like unset (agent off). The owner runs `scripts/deploy/create-agent-signing-key.sh` once after the deploy; it writes the real pair over the placeholders, and a forced new deployment of the four services turns the agent kind on.

## Boundaries & Constraints

**Always:** No real private key in git; tests generate keys at runtime. Private key on the CUMS task definition only (env `AGENT_JWT_PRIVATE_KEY`, property `agent.jwt.private-key`, PKCS#8 PEM). Public key env `AGENT_JWT_PUBLIC_KEY` (property `agent.jwt.public-key`, SPKI PEM) on all four. Caller secrets: env `AGENT_CALLERS_EMS_SECRET` on CUMS and EMS; `AGENT_CALLERS_AGENT_SERVICE_SECRET` on CUMS only. New secrets use the AWS-managed key (no `encryptionKey`), same reason as the Watch secret (cyclic cross-stack KMS policy). Names: secret `batbern/<env>/agent/signing-private-key`, parameter `/batbern/<env>/agent/jwt-public-key`, secrets `batbern/<env>/agent/ems-caller-secret`, `batbern/<env>/agent/agent-service-caller-secret`. The script never prints the private key, never passes it on argv (`file://` from a `umask 077` temp dir removed on exit), forces profile `batbern-staging` unless `AGENT_KEY_AWS_PROFILE` is set, region `eu-central-1`. CDK jest only as `./gradlew --stop && rm -rf /tmp/cdk.out* && npx jest --maxWorkers=2 <files>`.

**Never:** No `cdk deploy`, no AWS call, no run of the script outside `--dry-run` or a stubbed `aws`. No consumer of the private key or caller secrets (stories 5, 6). No agent-service wiring (epic-agent-runtime). No key rotation. No change to Cognito/Watch wiring. No git push/PR.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| First deploy | parameter and secret hold `none` | all four services start, log agent off | — |
| Decoder placeholder | `agent.jwt.public-key` = `none` / ` NONE ` | AGENT not enabled, agent token → `BadJwtException` | — |
| Script dry run | `--dry-run --out DIR` | RSA-2048 pair in DIR (private mode 600), public key printed, planned AWS steps printed; no `aws` invoked | `--out` dir must not already hold a key |
| Script real run | stub `aws`: secret and parameter both `none` | `put-secret-value` + `put-parameter --overwrite --type String` with `file://`; public key printed; restart commands printed | — |
| Script refuses | secret or parameter not `none` (already set) | exits non-zero, writes nothing | `--force` overrides with a warning |
| Script missing resources | `get-secret-value` fails (stack not deployed) | exits non-zero, says to deploy first | — |
| Missing tool | no `openssl` | exits non-zero before any AWS call | — |

</intent-contract>

## Code Map

- `infrastructure/lib/stacks/secrets-stack.ts:62-71` -- Watch secret pattern; add `agentSigningPrivateKey` (Secret, `secretStringValue: SecretValue.unsafePlainText(AGENT_KEY_PLACEHOLDER)`), `agentJwtPublicKey` (`ssm.StringParameter`, value `AGENT_KEY_PLACEHOLDER`), `emsCallerSecret`, `agentServiceCallerSecret` (generateSecretString like Watch). Export `AGENT_KEY_PLACEHOLDER = 'none'`. Comment: do not change these two resources' properties; CloudFormation would write `none` back (agent off; re-run the script with `--force`).
- `infrastructure/lib/stacks/company-management-stack.ts:89-99` -- `additionalSecrets`; add props `agentJwtPublicKey?: ssm.IStringParameter`, `agentSigningPrivateKey?`, `emsCallerSecret?`, `agentServiceCallerSecret?`; public key via `ecs.Secret.fromSsmParameter`. ContainerDefinition grants the execution role read for every container secret.
- `infrastructure/lib/stacks/event-management-stack.ts:175-186` -- add `agentJwtPublicKey?`, `emsCallerSecret?`.
- `infrastructure/lib/stacks/api-gateway-service-stack.ts:91-102` -- `secrets` map; add `agentJwtPublicKey?`.
- `infrastructure/lib/stacks/partner-coordination-stack.ts:56-80` -- no secrets today; add `agentJwtPublicKey?` through `serviceConfig.additionalSecrets` (`lib/constructs/domain-service-construct.ts:103`).
- `infrastructure/bin/batbern-infrastructure.ts:291,330-354,371,398` -- pass the new resources; PCS gets `addDependency(secretsStack)`. Deploy order: Secrets is layer 1 (`.github/workflows/deploy-staging.yml:612`), services layer 4, so resources exist before tasks start.
- `infrastructure/test/unit/helpers/synth-staging-app.ts` -- synthesizes the real staging app; reuse (see `ems-task-role-grants.test.ts` for usage and the s3-deployment mock).
- `infrastructure/test/unit/secrets-stack.test.ts:23` -- counts 2 secrets; becomes 5.
- `shared-kernel/src/main/java/ch/batbern/shared/security/BatbernJwtDecoder.java:108-115` -- agent kind branch; add placeholder `AGENT_PUBLIC_KEY_OFF = "none"`. Test beside `should_turnAgentOff_when_publicKeyMissingOrBlank` (`BatbernJwtDecoderTest.java:315`).
- `scripts/deploy/publish-beta-frontend.sh` -- style model (profile forcing, header usage block).
- Docs (doc-drift `infrastructure/`): `docs/architecture/08-operations-security.md:30`, `docs/architecture/06-backend-architecture.md` ("One JWT decoder for every service"), `docs/architecture/02-infrastructure-deployment.md:118`; runbook `docs/operations/runbooks.md` (new section before "When the triage loop itself is the problem").

## Tasks & Acceptance

**Execution:**
- [x] `shared-kernel/.../BatbernJwtDecoderTest.java` then `BatbernJwtDecoder.java` -- RED/GREEN: `none` (trimmed, case-insensitive) turns the agent kind off with the same log line; Javadoc names the placeholder.
- [x] `infrastructure/test/unit/agent-signing-key.test.ts` -- RED first, on `synthStagingApp()`: per template (`BATbern-staging-{CompanyManagement,EventManagement,PartnerCoordination,ApiGatewayService}`) the container secrets; private key and agent-service caller secret only in CUMS among all templates except Secrets; EMS caller secret only in CUMS and EMS; `AGENT_JWT_PUBLIC_KEY` on all four task definitions; positive control that CUMS's template references each. Match by the SecretsStack logical-id fragment.
- [x] `infrastructure/test/unit/secrets-stack.test.ts` -- names, placeholder `none` in the secret and the parameter, parameter type String, caller secrets generated, 5 secrets.
- [x] `infrastructure/lib/stacks/{secrets,company-management,event-management,api-gateway-service,partner-coordination}-stack.ts`, `bin/batbern-infrastructure.ts` -- implement as in Code Map -- GREEN.
- [x] `scripts/deploy/create-agent-signing-key.sh` -- `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048` (PKCS#8) and `openssl pkey -pubout`; modes per matrix; `--env` default `staging`; checks both values are `none` before writing (`--force` overrides); writes the secret first, then the parameter, reads the parameter back and compares; prints the public key, then `aws ecs list-services` / `update-service --force-new-deployment` commands for the four services and the log line to check.
- [x] `infrastructure/test/unit/agent-signing-key-script.test.ts` -- spawns the script: dry run (pair valid and matching via node `crypto`, private key text absent from stdout/stderr, stub `aws` on PATH records no call); real mode with a stub `aws` (records argv, answers `none`): put calls use `file://`, no argv contains `PRIVATE KEY`, public key printed equals the parameter written; refuses when the stub answers a non-`none` value (no put recorded).
- [x] Docs 08, 06, 02 and runbook section "Agent signing key": deploy first (agent off), run script, force new deployment of the four services, check the log `JWT decoder accepts token kinds [...]` (PCS without WATCH), what resets the key, env/property names for stories 5-6.

**Acceptance Criteria:**
- Given the synthesized staging app, when the four service templates are read, then the private key and agent-service caller secret appear only on CUMS, the EMS caller secret only on CUMS and EMS, and `AGENT_JWT_PUBLIC_KEY` on all four.
- Given a fresh deploy with placeholders, when a service starts, then its decoder leaves the agent kind off without failing (decoder test for `none`).
- Given a stubbed `aws` holding placeholders, when the owner script runs, then it writes the private key and public key by file reference only and prints the restart steps; given an already-set key, it writes nothing.

## Implementation Notes

- Logical ids in SecretsStack: `AgentSigningPrivateKey`, `AgentJwtPublicKey`, `AgentEmsCallerSecret`, `AgentServiceCallerSecret` (the EMS one is prefixed so no fragment is a substring of another; the synth test matches by fragment).
- Extra decoder test `should_readPublicKey_from_AGENT_JWT_PUBLIC_KEY_environmentVariable`: no yml maps the property, so it proves the ECS env name resolves to `agent.jwt.public-key` through a real `SystemEnvironmentPropertySource`.
- Synth checked by running `bin/batbern-infrastructure.ts` with ts-node (not `cdk synth`, which resolves AWS credentials): SSM `ValueFrom` is `arn:...:parameter` + the `/batbern/...` name, and each execution role gets `ssm:GetParameter*` / `secretsmanager:GetSecretValue` on exactly its resources.
- The "do not change" comment and the runbook name tags too: any property change on the two key resources (including stack tags) makes CloudFormation write `none` back.
- `--out` is refused without `--dry-run` (a real run keeps no local copy of the private key). Script test adds a `--force`, an `AGENT_KEY_AWS_PROFILE` and an "openssl missing" case; two mutations (key on argv, parameter check skipped) were each caught.
- Caller-secret Spring property names left to story 5 in the runbook; only the env names are fixed here.

## Plan Change Log

## Review Triage Log

### 2026-11-22 — Review pass
- verdicts: 7 findings — high 0, medium 1, low 4, false 1, maybe-false 0
- findings:
  - `[low]` `[patch]` New test names lack the `_when_` clause required by coding-standards.md "Test Naming Conventions" — renamed (TS, Java), names only.
  - `[medium]` `[patch]` Nothing but a comment stops a change to the two key resources' properties (description, name, value, stack/app tags) from making CloudFormation write `none` back, silently turning agent tokens off in production — added `should_keepKeyResourceProperties_when_stagingAppSynthesized` pinning their complete synthesized Properties on the real staging app (app-level tags included), failure hint points to the runbook and `--force`; mutation (description change) fails it.
  - `[low]` `[patch]` Decoder logged "is not set" when the key is the placeholder, misleading after a reset — distinct line "holds the placeholder none"; 06 and runbook updated.
  - `[low]` `[patch]` Runbook section sat as `###` under the alarm section "Identity and user sync" — now its own `##` section with separator.
  - `[low]` `[patch]` 06 claimed relaxed binding for caller-secret properties story 5 has not named — sentence limited to the two key properties, caller-secret properties named by story 5.
  - `[low]` `[reject]` Script reads the existing private key into a shell variable to compare with `none` — on the owner's own authorised shell, never printed, unset at once; avoiding it needs version-stage heuristics (added complexity) for a refusal path met at most once.
  - `[false]` `[reject]` Restart loop JMESPath may miss the gateway service — services carry no explicit `serviceName`, so CloudFormation names them `<stackName>-<logicalId>-<suffix>`, which contains `ApiGatewayService` / `CompanyManagement` / ... (matches the CLAUDE.md example `BATbern-staging-EventManagement-ServiceD69D759B-...`).

## Design Notes

Architecture note: modules touched -- infrastructure (SecretsStack, four service stacks, bin), shared-kernel security (placeholder), scripts/deploy. New dependencies -- service stacks import SecretsStack's new secret/parameter (same as Watch). New endpoints -- none. Reused -- Watch secret pattern, `ecs.Secret.fromSsmParameter`/`fromSecretsManager` auto-grant, `synthStagingApp`. Deviations -- none.

Why SSM for the public key: one source the script writes and ECS reads at task start, so enabling needs a restart, not a CDK change; a deploy-time `{{resolve:ssm}}` would need the value before the first deploy. Why CDK creates both with `none`: ECS fails a task whose referenced secret does not exist, so the resources must exist before the first deploy; `none` keeps the first deploy safe (an unparseable key fails startup by design). CloudFormation only rewrites a value when that resource's properties change, so the script's value survives later deploys.

## Verification

**Commands:**
- `set -o pipefail; ./gradlew :shared-kernel:test --tests '*BatbernJwtDecoderTest' --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-3-decoder.log` -- expected: BUILD SUCCESSFUL
- `cd infrastructure && set -o pipefail; ./../gradlew --stop; rm -rf /tmp/cdk.out*; npx jest --maxWorkers=2 test/unit/agent-signing-key.test.ts test/unit/agent-signing-key-script.test.ts test/unit/secrets-stack.test.ts test/unit/company-management-stack.test.ts test/unit/ems-task-role-grants.test.ts 2>&1 | tee ~/.cache/batbern-test-logs/0-3-cdk-scoped.log` -- expected: all pass
- `cd infrastructure && set -o pipefail; ./../gradlew --stop; rm -rf /tmp/cdk.out*; npx jest --maxWorkers=2 test/unit 2>&1 | tee ~/.cache/batbern-test-logs/0-3-cdk-unit.log` -- expected: all pass
- `cd infrastructure && npx tsc --noEmit && npx eslint lib bin test/unit --ext .ts` (or the package's lint script) -- expected: clean
- `bash -n scripts/deploy/create-agent-signing-key.sh && shellcheck scripts/deploy/create-agent-signing-key.sh` (if installed) -- expected: clean
- `set -o pipefail; ./gradlew :shared-kernel:checkstyleMain :shared-kernel:checkstyleTest --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-3-checkstyle.log` -- expected: BUILD SUCCESSFUL
- `make check-test-ratchet` -- expected: no RAISED line

## Auto Run Result

**Summary:** SecretsStack creates the agent signing private-key secret and the public-key SSM String parameter, both holding the placeholder `none`, plus two CDK-generated caller secrets (EMS, agent-service), all on the AWS-managed key. ECS injects the private key and both caller secrets into CUMS only, the EMS caller secret into EMS, and `AGENT_JWT_PUBLIC_KEY` (from SSM, read at task start) into gateway, CUMS, EMS, PCS. The shared decoder reads `none` as "agent off", so the first deploy is safe. `scripts/deploy/create-agent-signing-key.sh` (owner, once, after the deploy) generates an RSA-2048 pair, writes it by `file://` over the placeholders, refuses an existing key without `--force`, and prints the restart loop and the log check; `--dry-run [--out DIR]` makes no AWS call.

**Files:** `infrastructure/lib/stacks/secrets-stack.ts` (4 resources, `AGENT_KEY_PLACEHOLDER`); `company-management-stack.ts`, `event-management-stack.ts`, `api-gateway-service-stack.ts`, `partner-coordination-stack.ts` (container secrets); `bin/batbern-infrastructure.ts` (wiring, PCS depends on Secrets); tests `agent-signing-key.test.ts` (staging app synth), `agent-signing-key-script.test.ts` (stub `aws`), `secrets-stack.test.ts` (resources + property pin); `scripts/deploy/create-agent-signing-key.sh`; `BatbernJwtDecoder` + test (placeholder, env-var binding); docs 02, 06, 08, runbook "Agent signing key".

**Review:** quick lens, 7 findings: 5 patched (1 medium, 4 low), 0 deferred, 2 rejected (low: script reads existing key in owner shell, fix adds complexity; false: restart loop filter matches CloudFormation service names).

**Follow-up review recommended:** false (no high patched, one medium).

**Verification:** before review: decoder test RED then GREEN, checkstyle clean, CDK scoped 5 suites/35 tests, full CDK unit 44 suites/797 tests, script test 9/9, tsc and eslint clean, `make check-test-ratchet` no RAISED. After patches: decoder test 41/0 failures + checkstyle BUILD SUCCESSFUL, CDK scoped 5 suites/36 tests passed, ratchet no RAISED. Full CDK unit suite not re-run after the patches (they touched tests, the decoder log line and docs only).

**Residual risks:** nothing ran against AWS; the first deploy (placeholders, agent off) and the owner run are unverified live. Ticket verify "after deploy each service starts with the agent token kind enabled" can only be checked after the owner runs the script and restarts the four services. A CloudFormation property change on the two key resources resets them to `none` (now caught by a test). shellcheck is not installed on rack, so the script had only `bash -n` and its tests.
