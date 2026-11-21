---
review: adversary
target: ../architecture-agentic-interaction.md (status draft, 2026-11-21)
lens: two units built by different agents, each obeying every AD, that still build incompatibly or exceed a user's rights / #1179
date: 2026-11-21
mode: read-only, no builds
---

# Adversarial review: agentic interaction spine

**Verdict.** The spine is not yet build-safe. Its paradigm (agent = one more adapter on the public
API) is right, but four ADs rest on facts the code contradicts: the Mail Center is organizer-only,
the admin settings live in EMS, the Watch-token "precedent" is a symmetric secret, and services
derive the actor from claims the delegated token does not carry. Two agents can each follow
AD-1 to AD-14 to the letter and produce an agent that impersonates an organizer, mails an
audience one person at a time, or keeps acting after the kill-switch is off.

## What I checked and what I only believe

Checked in code (this worktree, 2026-11-21):

- `AdminSettingsService` and `app_settings` are in **EMS** (`services/event-management-service/.../service/AdminSettingsService.java`); `AdminSettingsController` is `hasRole('ORGANIZER')` for read and write.
- Every Mail Center endpoint in `MailConversationController` (7) and `EventMailController` (9) is `@PreAuthorize("hasRole('ORGANIZER')")`.
- `ConversationReplyService` sends answers from `info@` with Reply-To `replies+{token}@`; `replies+` mail goes to the replies path (`ForwardingAlias`, `AliasMailProcessor`), not to any new alias.
- `MailAuthentication` (DMARC/DKIM from the topmost SES `Authentication-Results`) is used only by `ForwardingSenderAuthorizer` for organizer-only aliases. `InboundEmailRouter.route` keys on the bare From address.
- The multi-issuer decoder is written per service (`api-gateway`, CUMS, EMS `SecurityConfig`), not in shared-kernel; PCS has none. The Watch token is HS256 with a shared secret (`watch.jwt.secret`). `shared-kernel/.../security/` holds `JwtRolesConverter`, `S2sHeaders`, audit classes, no decoder.
- Actor resolution: CUMS `SecurityContextHelper.actorOr` and gateway `AccountActiveFilter` use `custom:username`, else the raw `sub`. EMS `getCurrentUsername` uses `custom:username`, else a DB lookup by `cognito_user_id`, else `sub`. No code reads an `act` claim.
- `AutomaticTriggerAudienceGuardTest` exists in EMS only (project-context), and covers `@Scheduled`/`@EventListener`/`@SqsListener` methods there.

Believed, not verified: how A2UI 0.15 carries user input on an action (I assume a data-model
payload travels with the POST, as in the A2UI spec); that "scheduled work" in AD-3 means
recurring jobs inside agent-service.

## Holes, in order of severity

### H1 [Critical] A speaker-mail run cannot reply without becoming an organizer

- **Unit A:** the speaker mail channel story (AD-3, AD-4): mints a delegated token for the speaker
  and runs the speaker's tools with it.
- **Unit B:** the Mail Center reply tool (AD-5): "every outgoing mail is a tool call against a
  Mail Center operation". Every such operation is `hasRole('ORGANIZER')`.
- **Clash:** the sequence diagram's step "Mail Center reply with card link (as speaker)" is
  impossible. To make it work, B mints an organizer token. AD-3 does not forbid that (it says
  "the user" without saying which), so B picks an allowlisted organizer. The agent now acts with
  an organizer's full rights on a speaker's mail, an organizer who never asked for anything, and
  attribution names that organizer. The organizer token also reaches `EventMailController`
  compose and send, which is the #1179 surface, guarded only by agent-service's gate.
- **AD fix (new AD "One principal per run"):** every run has exactly one principal: the person
  whose intent started it (sender of the mail, organizer in chat, card subject). Every tool
  executes with that principal's token and no other. A tool that needs a right the principal lacks
  is not on that role's list. A reply to the principal about their own case is a new narrow public
  operation in EMS (contract-first, e.g. `POST /mail-conversations/{id}/messages` with an
  ownership rule: the caller may write only into a conversation whose counterpart is the caller),
  recorded by the same Mail Center recorder. Anything beyond that becomes a pending approval that
  executes under the approving organizer's own JWT (see H6).

### H2 [Critical] #1179 by aggregation: one-person tools, called in a loop

- **Unit A:** a tool author writes `remindMissingMaterials(eventCode, username)`: one recipient,
  so by AD-6's definition ("reaches more than one person") it is not `audience`. The author may
  even declare it `reversible`, because AD-6 lets the tool declare its own class and nothing says
  a sent mail is not undoable.
- **Unit B:** the brief/scheduler story runs as `system:agent-<job>` with a delegated token and,
  per AD-7, lets `reversible` run and `binding` run above threshold. It calls A once per speaker
  who has not submitted.
- **Clash:** each call obeys AD-6 and AD-7. Together they are an automatic mail to the speakers
  of an event, the exact shape of `DeadlineReminderJob` mailing 267 BATbern60 registrations on
  2026-11-02. EMS's `AutomaticTriggerAudienceGuardTest` does not see agent-service, and the
  Mail Center does not know the calls came from a machine.
- **AD fix (tighten AD-6 and AD-7, add one core guard):**
  1. Any tool that causes mail to a non-organizer is at least `binding`, never `reversible`.
     The class is not self-declared: the registry holds a risk floor per public operation and a
     tool's class is the maximum of the operations it composes (also closes H8).
  2. The gate counts distinct non-organizer recipients per run and per tool over a rolling
     window (e.g. 24 h). More than one means `audience`, whatever the tool says.
  3. A run started by a scheduler never executes a mail-sending tool without an organizer approval.
  4. Defence in depth in the core: Mail Center operations that send to an audience refuse a token
     that carries `act`. Only an organizer's own Cognito JWT triggers them. #1179 then holds in
     EMS even when the gate is wrong. An agent-service equivalent of the audience guard test lists
     every agent scheduler and every mail tool.

### H3 [Critical] The kill-switch and allowlist live where two of three checkers cannot reach

- **Unit A:** the CUMS minting story (AD-3, AD-10): checks `agent.enabled` and `agent.allowlist`.
- **Unit B:** the agent-service run-start story (AD-10): checks the same keys.
- **Unit C:** the EMS inbound router story (AD-4, AD-10): checks the same keys.
- **Clash:** the keys are `AdminSettingsService` keys, which is EMS. CUMS is upstream of EMS and
  may call it only through a contract CUMS owns, never read `app_settings` (ADR-016). agent-service
  may not call `/internal` (AD-2), and the public `/admin/settings/{key}` needs ORGANIZER, which a
  speaker-mail run does not have. So A reads EMS's table directly, B mints an organizer token to
  read a setting, C reads it locally: three readings, two of them breaking the module map. The
  allowlist shape clashes too: EMS sees a sender address, CUMS sees a `sub`. A speaker writing
  from a verified additional email (Story 10.32) is allowlisted in one place and not the other.
  The chat path (organizer's own Cognito JWT, AD-3) never passes the mint check at all, and
  pending approvals and cards created before the switch went off execute after it, because the
  only agent-service check is "the start of every run".
- **AD fix (rewrite AD-10):** the switch and the allowlist are owned by CUMS (identity, upstream of
  all), keyed by `username` only. Minting is the one choke point: every agent action, chat
  included, runs with a delegated token (token exchange of the organizer's Cognito JWT in chat),
  so a refused mint stops every path. EMS resolves the sender to a username through CUMS
  `/internal` (`PersonAddresses`) before it decides whether to hand off, and asks CUMS, not its own
  settings. agent-service re-mints (and is therefore re-checked) before every non-`read` tool
  call, every approval execution and every card POST. Token TTL is minutes, not the run.

### H4 [High] Whoever can call the mint endpoint, or holds the key, is a super-user

- **Unit A:** the CUMS mint story copies the stated precedent, the Watch JWT: HS256 with a secret
  that every verifier holds.
- **Unit B:** the decoder story adds that secret to every service, agent-service included.
- **Clash:** with a symmetric key every verifier is a minter: agent-service can sign a token for
  any user without asking CUMS, which skips the allowlist and the switch. Separately, AD-2 leaves
  no legal way to reach the mint endpoint in a mail run: agent-service has no user token yet, may
  not call `/internal`, and a machine-only operation may not sit in a public spec. A builder will
  open one of these doors, and anyone who can call mint can act as any allowlisted user,
  organizers included.
- **AD fix (tighten AD-3):** CUMS signs with an asymmetric key (private key in CUMS only, JWKS
  for verifiers), `aud` limited to the gateway and the services. Mint is a CUMS `/internal`
  operation, named as the single exception to AD-2, authenticated by the calling service's own
  identity, and bound to a trigger proof: for a mail run, an EMS-issued handoff reference from
  the `agent-inbound-mail` payload naming the resolved username; for a card POST, the card's
  subject; for chat, the organizer's live Cognito JWT (exchange); for a scheduler, only organizers
  who opted in. No proof, no token.

### H5 [High] A forged From address makes the agent act as a speaker

- **Unit A:** the EMS router story (AD-4) adds the agent alias to `InboundEmailRouter`, which
  today keys on the From address only, like the replies path.
- **Unit B:** the mint story (AD-3) mints for the user that address belongs to.
- **Clash:** both obey the ADs; anyone can write `From: <allowlisted speaker>` and have the agent
  submit materials or propose a title change in that speaker's name. EMS already has the answer,
  `MailAuthentication`, but uses it only for organizer-only aliases.
- **AD fix (tighten AD-4):** EMS hands a mail to the agent only when `MailAuthentication` passes
  for the From domain (DMARC pass, or aligned DKIM when no policy is published), the sender
  resolves to exactly one user, and the SES spam and virus verdicts pass. Otherwise it forwards
  as today. The contract payload carries the resolved username and the verdict, so agent-service
  decides nothing about identity itself.

### H6 [High] Who executes an approved action, and under which rights

- **Unit A:** the approval story executes an approved `binding` action with a re-minted token of
  the run's principal (the speaker).
- **Unit B:** the console story executes it with the approving organizer's JWT, which it has.
- **Clash:** different rights, different attribution, different #1179 behaviour (B can send to an
  audience, A cannot). The card POST adds a third executor: an anonymous bearer of the token link.
  If the card carries form inputs (A2UI data model), the POSTer supplies tool arguments and the
  tool runs under the card subject's delegated token, so a forwarded card link lets anyone act as
  that speaker with arguments of their choosing.
- **AD fix (tighten AD-7 and AD-12):** an approved action runs under the approver's own JWT when it
  needs organizer rights, otherwise under the principal's re-minted token; both are logged. A card
  freezes tool and arguments at creation; a POST may only fill inputs the card declares, validated
  against the tool's schema, and executes only as the card's subject, through the gate, after the
  switch check.

### H7 [High] The delegated token's claims break ownership and attribution

- **Unit A:** CUMS mints exactly what AD-3 says: `iss`, `sub`, `act`, no role.
- **Unit B:** a CUMS or gateway story consumes it with today's code.
- **Clash:** CUMS `actorOr` and the gateway `AccountActiveFilter` fall back to the raw `sub` when
  `custom:username` is missing; EMS resolves it through the DB. In CUMS every `me`/`{username}`
  ownership check compares a username with a UUID: 403 for the delegated speaker, or a builder
  "fixes" it locally in a third way. No service reads `act`, so "services record the actor as
  `agent:<run-id>` for `<username>`" is built four times, or not at all. AD-3 cites a
  "shared-kernel multi-issuer decoder" that does not exist; PCS has no second issuer.
- **AD fix (tighten AD-3):** the claim set is fixed in the AD: `iss`, `aud`, `sub`,
  `custom:username`, `act: {sub: "agent:<run-id>"}`, `exp`, no role (Pattern 3b). One shared-kernel
  decoder and one shared-kernel `ActorResolver` (username, `agent:<run>` for `<username>`,
  `system:<job>`) are built in one story and adopted by the gateway, CUMS, EMS and PCS before any
  tool story starts.

### H8 [Medium] Two tools, one operation, two classes; undo as a second writer

- **Unit A:** `updateTalkTitle`, `reversible`. **Unit B:** `proposeTitleChange`, `binding`. Both
  compose the same PATCH. The gate lets A run what B asks about.
- Undo (AD-7) writes the previous value back. For a speaker status, that is a second status path
  next to `SpeakerWorkflowService`, and a blind write-back overwrites whatever happened since
  (lost update).
- **AD fix:** the risk floor per operation from H2.1. Every workflow or status transition is
  `binding`, never `reversible`. Undo invokes a declared inverse tool through the gate, with
  optimistic concurrency (version or `If-Match`), and refuses if the resource changed since.

### H9 [Medium] Confidence can be raised by the mail it judges

- AD-7 lets `binding` run alone on "confidence and precedent support"; Deferred allows "model
  self-report". The inbound mail is the model's input, so a sender can write text that raises the
  self-reported confidence (prompt injection), and one organizer approval becomes precedent for
  every later case.
- **AD fix:** model self-report is never a sufficient input. A `binding` action runs alone only
  when it stays inside the principal's own case and the precedent is at least N organizer
  approvals of the same tool with the same argument shape, none denied or undone since. Arguments
  taken from inbound text are marked as such in the log.

### H10 [Medium] Kill-switch versus retired organizer screens

- AD-13 retires organizer screens once a generated view replaces them; AD-10 lets the owner turn
  the agent off. After a few retirements, pulling the switch removes organizer functions. The
  owner will hesitate to pull it, which defeats the switch.
- **AD fix:** a screen is retired only if its operations stay reachable with `agent.enabled` off
  (a deterministic generated view that needs no model, or the screen stays until the pilot opens).

### H11 [Low] agent-service's own role check reads CUMS tables

- agent-service's console API needs ORGANIZER for approvals. A delegated token carries no role, so
  shared-kernel `JwtRolesConverter` queries `user_profiles` and `role_assignments`, CUMS's tables,
  which AD-2 forbids. Every service does this today, so a builder will copy it.
- **AD fix:** say so: either the converter's DB fallback is the accepted exception for agent-service
  too, or the console API accepts only Cognito JWTs (which carry `custom:role`).

### H12 [Low] Stored cards versus a catalog version bump

- AD-12 makes a card immutable; the conventions make the renderer refuse an unknown catalog
  version. After a breaking bump, cards still in mailboxes stop rendering.
- **AD fix:** the renderer supports every catalog version of a card that has not expired; a card
  expires before its version leaves support.

## Not covered

Partner flows (deferred by the spine), deployment and network path (deferred), the A2UI
renderer's XSS surface beyond "text as text". No build or test was run.
