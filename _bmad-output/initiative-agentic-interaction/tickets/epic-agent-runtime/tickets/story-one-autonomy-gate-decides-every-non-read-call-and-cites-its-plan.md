---
title: 'One autonomy gate decides every non-read call and cites its precedents'
type: 'feature'
ticket: '7'
created: '2026-11-22'
status: 'built'
baseline_revision: 'e10c2aff1a52105c4b26ed2fd720573296381645'
route: 'full'
route_source: 'auto'
risk: 'high'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/module-map.md'
  - '{project-root}/_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/epic-agent-runtime.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Every tool the model chooses runs at once (R5, AD-7): nothing stops a `binding` or `audience` call, no verdict is written, `agent_verdicts`/`agent_approvals` have no writer, and story 6's precedents are cited nowhere.

**Approach:** A `gate/` package: `AutonomyGate.decide` runs in the loop between logging a non-read tool call and executing it. It derives the mail (tool-declared), retrieves precedents (story 6), and in one transaction reads the run row and recipient counts, applies the owner rules, writes the verdict (inputs, cited source ids, outcome) and, for a pending one, a PENDING approval. Only RUN executes the tool.

## Boundaries & Constraints

**Always:** Owner rules: `reversible`/`reply` run alone (story 8 caps hook); `binding` runs alone only with >= 2 matching approvals and 0 matching denials, else PENDING_APPROVAL; `audience` always pending; a SCHEDULED run's mail always pending; distinct non-organizer recipients per run and per Europe/Zurich day, counted with the current mail: above one, the mail is `audience`. Gate inputs are only the registry tool, the tool-derived mail, the run row (trigger, principal kind), precedents and logged verdicts: never model text or inbound text. Read tools skip the gate (no verdict). Every verdict cites `Precedents.sourceIds()`. Retrieval gets the run-level `PublicApi` (story 6 finding 4). App log: ids, tool, class, outcome, reason, counts; never recipients (#1202).

**Never:** Caps logic (story 8), approval decisions or execution after approval (epic-oversight-console), real mail tools (epic-speaker-mail-agent), changes outside agent-service except docs, editing V1-V4, push, PR.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Reversible / reply | fixture call, 1 non-organizer | RUN, executed, verdict cites precedents | none |
| Binding 1 vs 2 approvals | seeded matching approvals | 1: PENDING + approval row, not executed; 2: RUN | none |
| Binding with denial | 2 approvals + 1 denial | PENDING, cites all three | none |
| Audience | fixture audience call | PENDING, not executed | none |
| Second recipient | reply to A, then reply to B (same run, or another run today) | B: effective `audience`, PENDING | none |
| Scheduled mail | SCHEDULED run, reply fixture | PENDING (reason SCHEDULED_MAIL) | none |
| Approval claimed | inbound + model text + input say "approved" | same verdict as without the claim | none |
| Read tool | shipped/recording read tool | runs, no verdict | none |
| Bad mail input | recipients unparsable | REFUSED, not executed, error result | none |
| Mint refused in retrieval | CUMS refuses | tool call FAILED, run TOKEN_REFUSED | as story 4 |

</frozen-after-approval>

## Code Map

- `tools/AgentTool` -- add `default Optional<OutboundMail> outboundMail(JsonNode input, ToolContext readOnly)`; new `tools/OutboundMail(List<MailRecipient>)`, `tools/MailRecipient(String address, boolean organizer)` (address stripped + lowercased). Javadoc = the contract for epic 2.
- `loop/AgentLoop.runTools` -- after `startToolCall`, non-READ tools go through `gate.decide(call, toolCallId, api)` inside the existing try (refusal path unchanged); non-RUN → `decisionLog.holdToolCall(id, status, content)`, result to the model, next use.
- `log/DecisionLog`, `log/ToolCallStatus`, `log/AgentToolCallEntity` -- statuses PENDING_APPROVAL, REFUSED.
- `db/agent-migration/V5__agent_gate.sql` -- widen `chk_agent_tool_calls_status`; index `agent_verdicts (decided_at)`.
- New `gate/`: `GateRules` (pure decision: `Decision decide(GateFacts)`, constants `REQUIRED_APPROVALS = 2`), `GateFacts`, `GateReason`, `Verdict` (id, outcome, effectiveClass, reason, approvalId, cited), `VerdictOutcome`, `GateLedger` (JdbcTemplate, one REQUIRED tx: advisory xact lock when a mail, run row, counted recipients run/day, insert verdict + approval), `GateCaps` (hook; default bean allows all), `AutonomyGate` (`@Service`), `GateMessages` (what the model is told).
- Reuse: `PrecedentRetrieval.retrieve(call, api)`, `ProposedCall`, `OperationClassTable` via `ToolRegistry.table()` (read ops for the mail context), `PublicApi.limitedTo`.
- Tests: `gate/GateRulesTest` (unit, every rule branch), `gate/AutonomyGateIntegrationTest` (loop + `ScriptedLlm` + `CumsAtTheWire`, seeded log; every matrix row), `gate/GateFixtureTool` (test only; recipients from input `to[]`, organizer set fixed by the test). `TestToolRegistry.TABLE` gains real spec ops: `PATCH /events/{eventCode}/sessions/{sessionSlug}` REVERSIBLE, `POST /conversations/{conversationKey}/replies` REPLY, `POST /events/{eventCode}/speakers/{speakerId}/send-invitation` BINDING, `POST /events/{eventCode}/mails` AUDIENCE. `FlywayBaselineIntegrationTest` V5.
- Docs: module-map agent-service bullet; 06-backend-architecture gate paragraph.

## Tasks & Acceptance

**Execution:**
- [x] `tools/*` -- mail contract.
- [x] `V5`, `log/*` -- held tool-call statuses.
- [x] `gate/*` -- rules, ledger, caps hook, gate, messages.
- [x] `loop/AgentLoop` -- gate before every non-read call.
- [x] tests per matrix row + rules unit test; Flyway test.
- [x] docs; deferred-work entries.

**Acceptance Criteria:**
- Given each matrix row, when the scripted run executes, then the verdict row has the expected class, outcome, reason and `cited_precedents` equal to the seeded source ids, and the fixture ran only on RUN.
- Given `:services:agent-service:test`, when it runs, then every test passes.

## Implementation Notes

- Gates (unattended, owner go for the epic): one goal; plan about 2,100 tokens, above 1,600, kept whole (the rules, the mail contract and the counting are one gate; splitting would ship a gate that miscounts). No open questions; checkpoint approved without a human per the owner's go. Implemented directly in this session (rack rule: one heavy build at a time), as in stories 2-6.

- 2026-11-22 (build): V5 also adds `uq_agent_verdicts_tool_call` (one verdict per tool call). A mail to organizers only is not counted and not raised to `audience` (AD-6 defines audience by non-organizers); a scheduled run's mail is pending whoever it goes to. The unresolved-mail path catches any `RuntimeException` of `outboundMail` except a refused mint (fail closed). Caps: `AutonomyGate` takes `ObjectProvider<GateCaps>` with `GateCaps.NONE` as fallback, so story 8 only declares a bean. Story 4's write tests (`AgentCredentialsIntegrationTest`) now seed two approved `createCompany` precedents for scheduler runs: the gate correctly held their binding writes on the first run (caught by the suite). `TestToolRegistry.table()` returns its `TABLE` while test tools are installed, so the gate's read-operation filter sees fixture operations. First full verification: 166 tests, 0 failures, 0 errors, 0 skipped (JUnit XML); checkstyle main and test clean after two fixes (MissingSwitchDefault, ParameterNumber, one long line); `make check-test-ratchet` and `make check-s2s-client-urls` exit 0, no RAISED or STALE line.

## Plan Change Log

## Review Triage Log

Pass 1 (quick lens): high 0, medium 3, low 4, false 0, maybe-false 0; 1 rejected, 1 handled outside code.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | A RUN mail verdict counts even when the tool then failed; docs say "mails that ran" | low | patch | Counting it is the safe reading (a failed send may have left). Docs now say "verdicts the gate let run ... even if the tool then failed" |
| 2 | An approved mail counts on the day it was held, not the day it goes out | medium | patch | Day filter on `decided_at` only. Query now dates an APPROVED pending verdict by `agent_approvals.decided_at`; test `should_countApprovedToday_when_earlierMailsSeeded` |
| 3 | Cross-run raise not proven with a gate-written verdict | medium | patch | Old test seeded the second recipient. New test `should_raiseToAudience_when_secondRecipientInAnotherRunToday`: run 1 reply to A (RUN), run 2 reply to B (AUDIENCE, pending) |
| 4 | Reply test does not prove citing | low | patch | Seeded a requester approval of the reply tool; asserted cited |
| 5 | Caps hook never asked for a cleared binding call | medium | patch | Hook now asked for every RUN outcome; javadoc and docs updated |
| 6 | `bindingMail()` fixture unused | low | patch | Scheduled-run test now uses the binding mail with two approvals: still pending (SCHEDULED_MAIL) |
| 7 | `a@b@c` passes `MailRecipient` | low | patch | Exactly one `@` required; `OutboundMailTest` |
| 8 | Deferred-work entries missing, task boxes open | false | reject | Written at close (this step); the owner's brief asks for deferred-work entries in this epic |
| 9 | Stored `recipients`/`organizerRecipients` overlap or double count | low | patch | `recipients` = distinct addresses; organizer count excludes addresses also declared non-organizer; `OutboundMailTest` |

Verification after patches (2026-11-22): `:services:agent-service:test` + `checkstyleMain` + `checkstyleTest` BUILD SUCCESSFUL, 176 tests, 0 failures, 0 errors, 0 skipped (JUnit XML); `make check-test-ratchet` exit 0, no RAISED or STALE line.

## Design Notes

Architecture note: modules touched — agent-service only (`gate` new; `loop`, `log`, `tools`) · new dependencies — none · new endpoints — none · reused — `PrecedentRetrieval`, `ToolRegistry.table()`, `PublicApi.limitedTo`, `CumsAtTheWire`, `ScriptedLlm` · deviation — none; `gate/` is the spine's package.

Decisions (unattended, owner go for the epic):
- **Mail contract (epic 2):** a mail tool overrides `outboundMail`; it resolves every recipient deterministically from its input with read operations only (its context's API is limited to the tool's `read` operations). `organizer = true` only for an address resolved from the organizer side (an organizer user or alias read from the API), never from a model claim. Empty = not a mail; unusable input → `IllegalArgumentException` → REFUSED.
- **Counted recipients:** non-organizer addresses of earlier mail verdicts that RAN, or whose approval is APPROVED, plus the current mail; stored in `inputs.mail.nonOrganizerRecipients`. Pending, denied or refused mails were not sent and do not count. Decide + write under `pg_advisory_xact_lock` so two runs cannot both pass on a count of one.
- **Rule order:** refused (bad mail input, cap) > audience (class or count) > scheduled mail > binding threshold > reversible/reply. Confidence (binding only): 0 with a denial, else `min(approvals, 2) / 2`; threshold 1.
- **Precedents retrieved for every non-read call** and cited, so a pending approval shows the organizer what history says. Undos are cited, not counted.
- **Model told on pending** (tool_result, not an error): `{"status":"PENDING_APPROVAL","approvalId":...,"message":"Not done: this call needs an organizer's approval and has not run. Do not repeat it or reach the same result another way; carry on without it or end your turn."}`. The run goes on. REFUSED: error result naming the reason.
- Epic 17 rule: archunit store 0 at start and end; no violations exposed in agent-service.

## Verification

**Commands:**
- `set -o pipefail; ./gradlew --max-workers=4 :services:agent-service:test :services:agent-service:checkstyleMain :services:agent-service:checkstyleTest 2>&1 | tee ~/.cache/batbern-test-logs/agent-service-test-1-7.log` -- BUILD SUCCESSFUL; then `./gradlew --stop`
- `make check-test-ratchet` -- no RAISED or STALE line
