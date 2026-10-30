---
stepsCompleted: [step-01-document-discovery, step-02-prd-analysis, step-03-epic-coverage-validation, step-04-ux-alignment, step-05-epic-quality-review, step-06-final-assessment]
scope: Epic 16 Mail Center
filesIncluded: [docs/prd/epic-16-mail-center.md, docs/prd-enhanced.md, _bmad-output/implementation-artifacts/16-0-stop-losing-replies-and-calendar-declines.md, _bmad-output/implementation-artifacts/16-1-record-every-outbound-mail.md, _bmad-output/implementation-artifacts/16-3-communications-tab-becomes-mail-page.md, _bmad-output/implementation-artifacts/16-4-new-mail-picker.md]
---

# Implementation Readiness Assessment Report

**Date:** 2026-10-30
**Project:** BATbern
**Scope:** Epic 16 — Mail Center (stories 16.0, 16.1, 16.3, 16.4 ready-for-dev; 16.2, 16.5–16.7 backlog)

## Document Inventory

| Type | Used |
|---|---|
| PRD | `docs/prd/epic-16-mail-center.md` (epic-level PRD), `docs/prd-enhanced.md` (context: FR7, FR12, FR20, NFR3) |
| Architecture | None for Epic 16. Decisions in the epic and in story Dev Notes. Background: `docs/architecture/06-backend-architecture.md`, `02-infrastructure-deployment.md`, ADR-008, ADR-013, ADR-015 |
| Epics & stories | Epic file above; story files `16-0`, `16-1`, `16-3`, `16-4` in `_bmad-output/implementation-artifacts/`; `sprint-status.yaml` |
| UX | None in repo. Prototype v4 (scratchpad, real data, Tailscale only) |

No duplicates. Warnings: no Epic 16 architecture document; no UX specification in the repo.

## PRD Analysis

The main PRD (`prd-enhanced.md`) has no mail-center requirement. Closest: FR7 (email workflows and template management with variable substitution), FR12 (venue/catering/partner coordination with stakeholder notifications), FR20 (cross-stakeholder visibility, audit trails), NFR3 (external email integration). The requirements below are extracted from the epic, numbered here for traceability (MC = Mail Center).

### Functional Requirements

- MC-FR1: Every outbound mail for an event, or triggered by an organizer, is recorded with subject, rendered body, audience, sender, template-or-own-text, and recipients.
- MC-FR2: Per-recipient delivery status (delivered, failed with reason) is stored from SES events.
- MC-FR3: The event's Communications page lists everything that went out, newest first, with counts, and shows the exact mail on click. The newsletter is included.
- MC-FR4: A "Handled automatically" section counts accepts, declines, bounces, out-of-office replies and calendar RSVPs, collapsed by default.
- MC-FR5: A "Needs an answer" section lists human mail for the event, status Open/Waiting, with assignee.
- MC-FR6: A "+ New mail" picker replaces the four audience sub-tabs and opens the existing compose flows unchanged.
- MC-FR7: Own text with audience-specific placeholders, per-recipient preview, unknown placeholders block sending (frontend and backend), "use template as starting point", "save as template".
- MC-FR8: Inbound human mail from `replies@` and aliases is stored as conversations, threaded by token, then headers, then alias.
- MC-FR9: Organizers answer inside a conversation; the answer is visible to the team; status moves Open → Waiting → Open.
- MC-FR10: Conversations can be assigned; the assignee is notified once. Speaker conversations default to the event moderator.
- MC-FR11: Global "Mail" navigation: open items across events, mail without an event with "assign to event", spam, badge with open count.
- MC-FR12: Speaker mails and `-speaker@` broadcasts carry Reply-To `batbern{N}-moderator@`.
- MC-FR13: Unrecognized mail at `replies@` reaches the organizers instead of being discarded; auto-replies and bounces filtered.
- MC-FR14: A registrant's calendar "Declined" triggers the deregistration-link mail; seat released only on click.
- MC-FR15: After 16.6, alias forwarding to private inboxes is replaced by a body-less notification (owner decision 2).

Total: 15.

### Non-Functional Requirements

- MC-NFR1 (privacy/retention): mail content and addresses deleted at event date + 12 months; event-less mail at created-at + 12 months; privacy documentation updated.
- MC-NFR2 (security): every mail-center endpoint `hasRole('ORGANIZER')` (ADR-008); rendered mail shown sandboxed.
- MC-NFR3 (safety): no test sends real mail (ADR-015); test-marked content never sent and never recorded.
- MC-NFR4 (i18n): UI keys in all 10 locales; mail templates DE + EN only.
- MC-NFR5 (deployability): each story part independently deployable to production; additive migrations only.
- MC-NFR6 (fidelity): the stored rendered body equals what the recipient got and what the preview showed.
- MC-NFR7 (accessibility/mobile): keyboard-accessible picker and panel; single column at phone width.

Total: 7.

### Additional Requirements and Constraints

- Build inside EMS, no third-party helpdesk.
- `-moderator@` means `Event.organizerUsername`, not `SessionUser.MODERATOR`.
- Non-goals: open/click tracking, helpdesk features, templates beyond DE + EN, newsletter delivery changes.
- Volume is small (about 6 human inbound mails per week); no performance requirement is needed beyond the newsletter's ~1300 recipients per send.

### PRD Completeness Assessment

Clear goal, measured problem, decisions recorded, explicit non-goals. Weak spots: no requirement says where spam comes from (MC-FR11), the forwarding switch-over (MC-FR15) has no owner story, and partner-meeting mails are named in the problem but not in any story.

## Epic Coverage Validation

### Coverage Matrix

| Req | Story | Status |
|---|---|---|
| MC-FR1 | 16.1 | ✓ Covered (EMS only; partner-coordination and CUMS sends excluded) |
| MC-FR2 | 16.1 | ✓ Covered |
| MC-FR3 | 16.3 | ✓ Covered |
| MC-FR4 | 16.3 (failures only), 16.2 (rest) | ✓ Covered, split |
| MC-FR5 | 16.3 in the epic; **excluded** in the 16.3 story file | ❌ **Conflict, no owner** |
| MC-FR6 | 16.4 | ✓ Covered |
| MC-FR7 | 16.5 (backlog) | ✓ Covered in epic |
| MC-FR8 | 16.2 (backlog) | ✓ Covered in epic |
| MC-FR9 | 16.6 (backlog) | ✓ Covered in epic |
| MC-FR10 | 16.2 (default), 16.6 (assign + notify) | ✓ Covered in epic |
| MC-FR11 | 16.7 (backlog) | ⚠️ Partial: "spam" has no source |
| MC-FR12 | 16.0 Part A | ✓ Covered |
| MC-FR13 | 16.0 Part A | ✓ Covered |
| MC-FR14 | 16.0 Part B | ✓ Covered |
| MC-FR15 | none | ❌ **Missing** |
| MC-NFR1 | 16.1 AC6 (outbound), 16.2 AC4 (inbound) | ⚠️ Privacy-documentation note has no task |

### Missing Requirements

**Critical**
- **MC-FR5, "Needs an answer" section and status/assignee UI.** The epic puts it in 16.3 (scope bullet "Status and assignee changes", AC2 empty state). The 16.3 story file defers it to 16.2. Story 16.2 in the epic is backend only. Result: nobody builds the section. Recommendation: move section 1 and status/assignee changes into 16.2 in the epic (16.2 becomes backend + section 1), and drop the 16.3 epic bullet and AC2.

**High**
- **MC-FR15, forwarding switch-over (owner decision 2).** No story. Recommendation: add it to 16.6 as a final task, or as story 16.8.
- **MC-FR11 spam.** SES receipt rules have no spam/virus scanning (`ScanEnabled` not set in the infrastructure). Recommendation: 16.7 either enables SES scanning and uses the verdict headers, or drops "spam" from scope.

**Medium**
- Partner-meeting invites (partner-coordination) are in the problem statement but excluded from 16.1. Recommendation: record as an explicit follow-up story or a non-goal.
- Privacy-documentation note for MC-NFR1: add a task to 16.1.

### Coverage Statistics

- Requirements: 15 FR + 7 NFR
- FR fully covered: 12 of 15 (80 %); conflict 1, partial 1, missing 1

## UX Alignment Assessment

### UX Document Status

Not found in the repo. The only UX source is the clickable prototype v4 (German, Tailwind, real BATbern60 data) in a session scratchpad under `/tmp`, served over Tailscale. `/tmp` on the dev host is a RAM tmpfs: the prototype is lost on reboot, and a new dev-agent session will not know the path.

### Alignment Issues

- Prototype and epic agree on the three-section page, the "+ New mail" picker, the [Template | Own text] toggle, per-recipient preview, and the global Mail view with "assign to event".
- Prototype "Handled automatically" shows accepts, declines, out-of-office counts. Story 16.3 shows failed deliveries only (the rest comes with 16.2). Documented in 16.3, consistent with the sequence.
- Prototype marks sends as "eigener Text" / "Vorlage". 16.1 records `content_source`, 16.3 AC2 displays it. Aligned.
- Story 16.4 specifies a right-hand MUI Drawer of about 560 px; the prototype uses a right side panel. Aligned.

### Warnings

- **High:** the visual reference for 16.3 and 16.4 is not durable and not reachable from a new session. Recommendation: commit an anonymized copy (synthetic names, `@example.com`) as `docs/ux/mail-center-prototype.html` plus a short `docs/ux/mail-center-spec.md` (sections, states, empty states, mobile), and reference both from the epic and stories 16.3/16.4.
- Medium: the organizer UI is MUI (theme primary #2C5F7C); the prototype is Tailwind. The stories must state that the prototype is a layout reference, not a component source. 16.3 implies this; make it explicit.

## Epic Quality Review

Brownfield epic; no starter-template story needed. Tables are created by the story that first needs them (16.1 `mail_message`/`mail_recipient`, 16.2 `mail_conversation`). No cross-epic forward dependency. The epic delivers user value (visibility, no lost replies).

### 🔴 Critical Violations

1. **"Needs an answer" has no owner** (see coverage). The epic's 16.3 includes section 1 and status/assignee changes; the 16.3 story file defers them; 16.2 is backend only. Remediation: move section 1 and status/assignee UI to 16.2 in the epic, remove them from the epic's 16.3.

### 🟠 Major Issues

2. **16.1 is too large for one deployable PR.** It spans the shared kernel (SPI + overloads), a migration, 16 EMS call sites, an inventory guard, CDK (new SQS queue on the transactional topic), an SQS listener and a retention job. An infra change in the same PR carries the CFN/deploy risk documented for infra PRs. Remediation: split into **16.1a** (schema, SPI, callers, guard, retention) and **16.1b** (SQS subscription + delivery-status listener).
3. **16.1 send-group definition is open.** "One `mail_message` per send action" is clear for an organizer click, not for scheduled sends (daily speaker reminders, task reminders, Q&A digests, registration confirmations). Remediation: define the group per path in Dev Notes, e.g. scheduler run × event × template key for reminders; one group per mail for individual transactional mails (registration confirmation).
4. **16.1 leaves an API-identity decision to the developer** ("`mail_key` ... ADR-003 convention to be checked"). Remediation: decide now. Proposal: `mail_message` keeps a UUID PK; the 16.3 API exposes it as an opaque id, as other internal resources do; or a readable key `{eventCode}-{yyyyMMddHHmmss}-{audience}`.
5. **16.1 inventory guard has no tool.** The story says "modelled on `EndpointAuthorizationGuardTest`'s classpath scan", but that test reads annotations. Detecting calls to `EmailService.send*` needs bytecode analysis; ArchUnit is not a dependency. Remediation: either add ArchUnit (test scope) or specify a source-scan test over `src/main/java` with an explicit allow-list.
6. **16.0 Part B de-duplication has no storage.** AC9 (no second mail within 7 days) needs state; no table or column is named. Remediation: name it (e.g. `calendar_decline_log` keyed by registration, or a nullable `decline_link_sent_at` on `registrations`) in the story.
7. **16.1 delivers no visible user value alone.** Accepted for brownfield because data starts accumulating before 16.3 ships, but 16.1 and 16.3 should deploy close together. Note in the epic.

### 🟡 Minor Concerns

8. Epic text vs story 16.1: SES tag names differ (`event`/`audience`/`mail_id` vs `mail_group`); the epic says newsletter configuration-set events update per-recipient status, the story keeps the newsletter in `BounceProcessingService`. Align the epic to the story.
9. Epic 16.2 still says "Depends on #1160" (now 16.0 Part A) and "see open decision 2" (now a taken decision).
10. Some ACs are not Given/When/Then (16.4 AC2, AC4, AC5; 16.3 AC5–AC8). They are testable as written; formatting only.
11. 16.0 Part A keeps the existing keyword routing at `replies@`: a human question containing "cancel" or "absagen" plus an event code in the subject still cancels a registration automatically. Pre-existing behaviour, out of 16.0 scope; worth a separate issue.
12. 16.3/16.4: state explicitly that the Tailwind prototype is a layout reference; components are MUI.

### Best Practices Checklist

| Check | Result |
|---|---|
| Epic delivers user value | ✓ |
| Epic independent of later epics | ✓ |
| Stories appropriately sized | ✗ 16.1 too large |
| No forward dependencies | ✗ section 1 defined in 16.3, built by nobody |
| Tables created when needed | ✓ |
| Clear acceptance criteria | ⚠️ mostly; 16.1 grouping and 16.0B de-dup storage unclear |
| Traceability to requirements | ⚠️ MC-FR15 and spam untraced |

## Summary and Recommendations

### Overall Readiness Status

**NEEDS WORK, per story:**

| Story | Status |
|---|---|
| 16.0 Part A (#1160) | **READY.** Start now |
| 16.0 Part B (#1161) | READY after one fix (de-dup storage, issue 6) |
| 16.1 | NOT READY: split (2), define send groups (3), decide the id (4), choose the guard tool (5) |
| 16.3 | NEEDS WORK: epic/story conflict on section 1 (1), durable UX reference (UX) |
| 16.4 | READY once 16.3 is |

### Critical Issues Requiring Immediate Action

1. The "Needs an answer" section and status/assignee UI have no owner story.
2. 16.1 is too large and leaves three design choices to the developer.
3. The only UX reference lives in `/tmp` and contains real names.

### Recommended Next Steps

1. Fix the epic: move section 1 to 16.2; add the forwarding switch-over (MC-FR15) to 16.6; decide spam for 16.7 (enable SES scanning, or drop it); align 16.1 text with the story; replace stale "#1160" and "open decision" references.
2. Split 16.1 into 16.1a and 16.1b; write send-group rules, the id decision and the guard tool into the story.
3. Name the de-dup storage in 16.0 Part B.
4. Commit an anonymized prototype and a short UX spec under `docs/ux/`; reference them from 16.3 and 16.4.
5. File a separate issue for keyword auto-cancel on human replies at `replies@` (issue 11).

### Final Note

The assessment found 12 issues across 4 categories (1 critical, 6 major, 5 minor), plus 3 coverage gaps and 1 UX warning. 16.0 Part A can start immediately; the rest should be fixed before its story starts.

**Assessor:** John (PM), BMAD check-implementation-readiness · **Date:** 2026-10-30
