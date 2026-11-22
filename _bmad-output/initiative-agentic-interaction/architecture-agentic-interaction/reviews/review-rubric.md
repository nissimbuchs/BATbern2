---
reviews: architecture-agentic-interaction.md (status draft, updated 2026-11-21)
against: .memlog.md (updated 2026-11-21T21:59), docs/architecture/module-map.md, _bmad-output/project-context.md, code on branch fix/deploy-reconcile-after-rollback
reviewer: rubric review, read-only (no builds, no tests run, spine not edited)
date: 2026-11-21
---

# Rubric review: BATbern agentic interaction spine

## Verdict

**Not ready for slicing.** The paradigm and most ADs are sound and enforceable, but three ADs
(AD-3, AD-4, AD-10) make concrete claims about the codebase that are false, the delegated-identity
model has two unresolved seams (whose token executes an approved action, and how outbound mail
works when the agent acts as a speaker), mail sender authentication is missing entirely, and the
operational envelope (production-only environment, model cost limits, observability, test stand-in
for the model, pilot evaluation) is almost wholly undecided or parked under a Deferred item that
lets units diverge.

Labels: **[code]** = checked in code during this review (file named). **[belief]** = inference,
not verified by running anything.

## Findings (ordered by severity)

### F1 critical: inbound mail path does not authenticate the sender, so anyone can make the agent act as a speaker [code + belief]

- AD-3 mints a delegated token for the *sender* of a mail; AD-4/AD-10 gate only on "allowlisted
  sender". The From address is attacker-controlled.
- [code] EMS already has the defence: `forwarding/MailAuthentication.java` reads the topmost SES
  `Authentication-Results` (DMARC pass, or DKIM pass from the From domain). Its own javadoc says
  "The organizer-only aliases authorize by the From address, which anyone can type. This verdict
  is what makes that From trustworthy." The spine never mentions it.
- [belief] A spoofed From of an allowlisted speaker would get a token minted for that speaker and
  run `reversible` tools (e.g. `submitTalkMaterials`, `proposeTitleChange`) without any human.
- **Fix:** add to AD-4: "EMS hands a mail to the agent only when `MailAuthentication` passes for the
  From address; otherwise it forwards to the organizers. The contract payload carries the verdict;
  agent-service refuses to mint for an unauthenticated mail." Add a contract test on both sides.

### F2 critical: AD-10's three checkpoints cannot read the setting as specified [code]

- [code] `AdminSettingsService` lives only in EMS
  (`services/event-management-service/.../service/AdminSettingsService.java`), backed by EMS's own
  table `app_settings` (EMS `V88__create_app_settings_table.sql`). There is no `/internal` settings
  operation in `docs/api/internal/*.yml`.
- [code] The public `GET/PUT /admin/settings/{key}` (`AdminSettingsController`) is
  `@PreAuthorize("hasRole('ORGANIZER')")`.
- **CUMS cannot read it:** module map, "CUMS (identity) is upstream of all"; CUMS
  `ServiceDirectionTest` forbids `*EventManagement*Client*`; "no new reads of another service's
  tables (ADR-016)". So the minting check in CUMS has no legal path. [code: module-map.md]
- **agent-service cannot read it with a speaker's delegated token** (organizer-only endpoint), and
  AD-2 forbids `/internal` and the DB. It would need an identity of its own, which AD-3 rules out.
- **Fix (owner decision, pick one):** (a) the kill-switch stays an EMS admin setting, EMS publishes
  it through a new EMS-owned operation agent-service may call, and the allowlist moves to CUMS as
  CUMS-owned user data (a pilot flag or role on `user_profiles`), which is where minting happens;
  or (b) CUMS owns both keys in a CUMS settings store and EMS reads them over CUMS `/internal`
  (downstream to upstream, legal). Either way, rewrite AD-10 to name the owner of each key and the
  exact read path at each of the three checkpoints. Note the conflict with the module-map line
  "Admin or feature setting: `AdminSettingsService`" and record the amendment.

### F3 high: "the shared-kernel multi-issuer decoder" does not exist; token key management is the real divergence point [code]

- [code] There is no JWT decoder in `shared-kernel/src/main/java/ch/batbern/shared/security/`
  (contents: `AuditLoggingAspect`, `AuditLog`, `ClientIp`, `JwtRolesConverter`, `S2sHeaders`).
- [code] The multi-issuer decoder is copy-pasted per service: `api-gateway/.../SecurityConfig.java`
  (`compositeJwtDecoder`), EMS `SecurityConfig.java:204`, CUMS `SecurityConfig.java:190`. **PCS
  accepts Cognito only** (`partner-coordination-service/.../SecurityConfig.java:156-160`). Routing is
  a string `payload.contains("\"iss\":\"batbern-watch\"")`.
- [code] The Watch precedent is HS256 with one secret shared by every verifier
  (`watch.jwt.secret`, `WatchJwtService` with `sub = username`, `role = ORGANIZER`, TTL 3600 s). It
  differs from AD-3 on three points: algorithm/key, `sub` (username vs Cognito sub) and role claim.
  Copying it means every service holding the secret can mint agent tokens.
- [code] The **gateway** also authenticates the JWT; AD-3's "Binds" list omits it, so a delegated
  token would be rejected at the gateway before any service sees it.
- **Fix:** AD-3 Rule must (1) create the decoder once in shared-kernel (issuer -> decoder map) and
  have gateway, CUMS, EMS, PCS and agent-service use it, replacing the four copies; (2) fix the key:
  asymmetric (RS256/ES256), private key only in CUMS (Secrets Manager), public key via a CUMS JWKS
  or config; (3) fix the claim set: `iss`, `sub` = Cognito sub, `custom:username`, `act`, `exp`
  (state TTL), no `custom:role`/`role` (so `JwtRolesConverter` falls back to `role_assignments`,
  which works today, [code] `JwtRolesConverter.java:75-84`); (4) name where `act` is turned into
  `agent:<run-id>` attribution (shared-kernel `AuditLoggingAspect` / `S2sHeaders` forwarding), or
  every service invents it. Take the key out of the Deferred "Deployment detail".

### F4 high: AD-4 names the wrong component and leaves the contract semantics open [code]

- [code] `InboundEmailRouter` is the `replies@` keyword router; its `ParsedEmail` is
  `(senderEmail, subject, bodyFirstLine)`: no body, no attachments. The sequence diagram's
  "abstract + photo" cannot pass through it.
- [code] Aliases are classified in `forwarding/ForwardingAlias.java` (enum `Kind`) and handled by
  `AliasMailProcessor` / `EmailForwardingListener` (SQS). The model is fail-closed: "a new kind is
  organizer-only unless it is added to `Kind#openToAnyone` on purpose." A speaker-facing agent alias
  must be a new `Kind` opened deliberately.
- [code] Follow-ups do not come back to the alias: Mail Center mail carries `replies+{token}@` as
  Reply-To, routed as `CONVERSATION_REPLY` to the replies path. If the agent replies through Mail
  Center (AD-5), the speaker's answer lands in a Mail Center conversation, not with the agent. The
  spine does not say how a thread returns to the agent.
- [code] `ContractCalls` is synchronous `RestTemplate`; inbound runs in an `@SqsListener`. [belief]
  An agent run (several model calls) inside that call risks SQS redelivery and duplicate runs.
- **Fix:** rewrite AD-4: new `ForwardingAlias.Kind` (e.g. `AGENT`) in `openToAnyone`, handled in
  `AliasMailProcessor`; contract payload = full parsed message incl. attachments (or S3 object key),
  SES message id as idempotency key, MailAuthentication verdict (F1); agent-service answers 202 and
  runs asynchronously; on any contract failure or timeout EMS forwards to the organizers. Decide how
  conversation replies (`replies+{token}`) on agent-started threads reach the agent (same contract,
  keyed by conversation).

### F5 high: whose identity executes an approved action and an outbound mail is undecided [code + belief]

- [code] All 16 Mail Center endpoints are `hasRole('ORGANIZER')` (`mailcenter/EventMailController`,
  `MailConversationController`). AD-3 says the agent acts as the user; in the mail flow the user is
  a speaker. AD-5 says every outgoing mail is a Mail Center call. With a speaker token both the
  "Mail Center reply with card link" and "Mail Center reply to speaker" steps in the sequence
  diagram return 403.
- [code] Speaker writes are `hasRole('SPEAKER')` (`SpeakerPortalContentController`,
  `SpeakerPortalResponseController`), so the speaker-side tools do work with a delegated token.
- [belief] A `binding` action approved by an organizer (e.g. "extra minutes") is usually an
  organizer-only operation; run with the speaker's token it fails, run with the approver's token
  the attribution and AD-3 change. Two units (gate, tools) will pick differently.
- **Fix:** add a rule to AD-3/AD-7: "A tool declares the role it runs as. Speaker-side tools run on
  the requester's delegated token; organizer-side tools (incl. every Mail Center send) run on a
  delegated token for a named organizer: the approver for approved actions, the event's responsible
  organizer for autonomous ones." Or (AD-1 route) add a speaker-scoped public "reply in my own
  conversation" operation. Owner decides; until then AD-5 is not implementable for the speaker
  channel.

### F6 high: operational envelope mostly undecided [code + belief]

Altitude owns these; the spine decides only "API key in Secrets Manager" and "log tokens/cost".

| Dimension | State in spine | Gap / proposed rule |
|---|---|---|
| Environments | not mentioned | [code: project-context.md:291-310] every PR to develop deploys to production and there is no dev AWS env. Memlog constraint "every PR to develop deploys to production; next event ~2027-02" did not land. Rule: agent-service ships dark (`agent.enabled` default off, absent key = off); every PR of the initiative must be deploy-safe with the switch off. |
| Pilot personas in prod | "scripted personas" named, not defined | Where they live (real Cognito users in prod, SES simulator addresses like the canonical speaker test user), and the existing no-real-comms rule for Bruno/E2E in prod. Post-deploy E2E/Bruno must never invoke the real model or send mail. |
| Local dev | not mentioned | Local inbound mail (`DevEmailController` exists [code]), local minting, whether local dev calls the real model (cost, key on rack) or a fake. |
| Test stand-in for the model | not mentioned | Test fidelity rules: mock only at the process boundary. Rule: a recorded/fake `LlmPort` adapter (like `SesRecorder`) is the only model in tests; gate and tools tested deterministically. |
| Model cost and limits | logging only | No per-run step cap, token cap, daily budget, Anthropic workspace spend limit, 429/outage behaviour, or mail-loop guard (auto-replies to the agent alias). Rule: hard max steps and tokens per run, daily budget checked in the loop, on model error or budget exhausted the mail falls back to organizer forwarding. |
| Observability / alarms | not mentioned | agent-service in the monitoring stack (log group, ECS alarms like the other services), alarms on model errors, budget, contract failures, approvals pending > N h. Log masking (#1202) vs full prompt logging in CloudWatch. |
| Deploy pipeline | Deferred | New service in `deploy-staging.yml`, post-deploy smoke, access-control DAST probe (#1156) must cover the new `permitAll` card route (AD-12). |
| Existing model use | not mentioned | [code] EMS already calls OpenAI (`BatbernAiService`, `OPENAI_API_KEY`, `AI_ENABLED`). AD-14 "only the agent loop calls the model" is scoped to agent-service; say so, and whether EMS AI stays. |
| Pilot evaluation | "logged for evaluation" | No metric or decision criterion for the Deferred "open beyond allowlist" call. Define 3-5 measures (handled without organizer, gate overrule rate, undo rate, time to reply, cost per exchange) and who reads them when. |
| Retention | not mentioned | Decision log keeps full messages; precedent `EmailForwardingLogRetentionScheduler` [code]. Owner set PII as non-constraint, retention still needs a value. |

**Fix:** add an "Operational envelope" section with one line per row above (decided, deferred
with owner, or open question). Split the Deferred "Deployment detail": the service stack can wait,
the signing key, the dark-ship rule and the budget cannot.

### F7 medium: Deferred items that let units diverge [belief]

- "Deployment detail ... and its secrets": the delegated-token signing key is shared by CUMS and
  every decoder (F3). Not deferrable.
- "Chat transport": the Spring gateway proxies `/api/v1/agent/**`; streaming (SSE) through it and
  the renderer's consumption are two units (gateway, web-frontend) plus agent-service. Decide at
  least "SSE through the gateway, no buffering" or "polling forbidden (ADR-016)".
- "Confidence scoring": safe, the gate is one place (AD-7).
- "Partner flows", "vector store", "retiring screens", "opening the pilot": safe.

### F8 medium: unverified assumptions baked in as rules [belief]

- AD-11 is marked `[ADOPTED]` but A2UI is new to the codebase, and its rule "our own Tailwind
  components (no MUI)" rests on the memlog's open assumption that `@a2ui/react` 0.15.0 (pre-1.0)
  accepts a custom catalog without its own style system. React 19 compatibility is also unchecked.
  Fix: drop `[ADOPTED]`, list the assumption as an open question with the fallback (own thin
  renderer of the same A2UI messages) and make the first UI story the spike.
- Same for anthropic-java 3.12.0 own loop: fine, but say the model id and max tokens are config.

### F9 medium: AD-2 and the module map need an explicit amendment [code]

- [code] module-map.md says PCS "is the most downstream service" and "Calls go ... to `/internal/**`
  only, never through the public API". AD-2 is a deliberate exception; good, but the spine should
  require the module-map update (agent-service as most downstream, the public-API exception, the
  new EMS contract, the settings path from F2) and a guard: an ArchUnit/source rule in
  agent-service that no client URL contains `/internal` and no JPA entity maps a non-`agent_` table.
- AD-2's diagram calls the CUMS arrow "token minting"; it is agent-service calling CUMS (downstream
  to upstream). Which operation? A public endpoint for minting would let any caller ask; it must be
  a CUMS `/internal` operation authorized by operation, which AD-2 forbids agent-service to call.
  Name the exception explicitly (mint is the one `/internal` call agent-service makes) or the
  minting rule cannot be implemented.

### F10 low: memlog and spine drift

- Risk class `reversible-write` (memlog) vs `reversible` (spine). Pick one.
- Memlog constraints "PII not a constraint" and "every PR deploys to production / ~3 months" are
  absent (see F6).
- "Stage story as extra rendering" (memlog purpose) appears nowhere; fine if out of scope, say so.
- Flyway: name the history table (`flyway_schema_history_agent`, pattern [code] in each service's
  `application.yml`).
- Gateway rate limits are per role from the token ([code] `RateLimitingFilter`, speaker 500/min);
  with no role claim the delegated token's bucket is unclear. Check when F3 is fixed.

## Memlog coverage

| Memlog decision | In spine | Note |
|---|---|---|
| Ports and adapters | Paradigm, AD-2 | ok |
| agent runtime downstream, public API only | AD-2 | ok, but minting call contradicts (F9) |
| Delegated token, shared-kernel decoder | AD-3 | decoder claim false (F3) |
| Inbound via EMS contract | AD-4 | wrong component (F4) |
| Outbound only via Mail Center | AD-5 | not implementable as speaker (F5) |
| ~20 intent tools, risk class | AD-6 | naming drift (F10) |
| Autonomy gate | AD-7 | approval identity open (F5) |
| Precedent by retrieval | AD-8 | ok |
| Decision log, own tables, undo | AD-9 | ok |
| Generated UI (superseded by A2UI) | AD-11 | assumption baked in (F8) |
| Card delivery | AD-12 | ok; add DAST coverage (F6) |
| Cockpit -> oversight console | AD-13 | ok |
| Allowlist + kill-switch admin settings | AD-10 | read path impossible (F2) |
| Java service, anthropic-java, own loop | AD-14, Stack | ok |
| API key in Secrets Manager | AD-14, Stack | ok |
| Confidence threshold (question) | Deferred | ok |
| Constraint: PR deploys to prod, ~3 months | missing | F6 |
| Constraint: PII not a constraint | missing | F10 |

## What this review did not do

- No build, test or live probe was run. Every [code] statement is from reading files on this
  branch; behaviour at runtime (e.g. gateway rejecting a non-Cognito token) is inferred from the
  decoder code, not observed.
- Infrastructure (Service Connect route from agent-service to the gateway, CloudFront/API Gateway
  path for `/api/v1/agent/**`) was only skimmed (stack list), not checked.
- The A2UI and anthropic-java version facts in the memlog were not re-verified.
