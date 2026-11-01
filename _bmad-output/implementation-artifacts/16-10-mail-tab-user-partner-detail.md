# Story 16.10: Mail tab on the user and partner detail pages

Status: ready-for-dev

## Story

As an **organizer**,
I want **a "Mail" tab on a user's detail page and on a partner's detail page that lists every mail we sent to them and every mail they sent us**,
so that **before I write to a speaker, registrant or partner I can see in one place what they already received and asked, across all events**.

> Source: owner request 2026-10-31 (memory note "Mail tab on user + partner detail", to be added to Epic 16). Not yet in `docs/prd/epic-16-mail-center.md`; the lead adds it.
> Depends on **16.1a** (outbound record), **16.2a** PR 1 (inbound columns: `mail_message.from_address`, `direction = 'IN'`), and `GET /mails` (defined in **16.7** Task 3). **Whichever of 16.7 and 16.10 is built first builds `GET /mails` and `GET /mails/{mailKey}`** exactly as specified in 16.7 AC9/AC10; the other one reuses it.
> Partner meeting invites and cancellations appear here once **16.9** records them (partner-coordination → `POST /internal/mails`); no work in this story for that, as long as 16.9 writes one `mail_recipient` per address.
> **UX:** the same rows and inline detail as the event page (`communications/MailSentList`, `MailDetail`), full-screen dialog on mobile. No new visual pattern.

## Acceptance Criteria

1. **AC1 (user tab)** — GIVEN an organizer opens `/organizer/users/{username}`, THEN a "Mail" tab lists, newest first, every recorded mail where one of the user's addresses is a recipient (To or Cc) or the sender of an inbound mail. Each row shows date, direction (sent / received), event code (or "no event"), audience, subject, and for sent mail that recipient's delivery status.
2. **AC2 (all addresses)** — The user's addresses are resolved **on the server**: the primary email plus every additional email from CUMS (`user_additional_emails`), verified **and** unverified, compared case-insensitively. GIVEN a mail sent to an unverified additional email, THEN it is listed.
3. **AC3 (partner tab)** — GIVEN an organizer opens a partner's detail page, THEN a "Mail" tab lists the same for all of the partner's contacts, i.e. the users with role PARTNER and that company (the set the Contacts tab shows), all their addresses. A partner user (role PARTNER) viewing their own company page does not see the tab.
4. **AC4 (one API)** — `GET /mails` (16.7) gains the filters `participantUsername` and `participantCompany` (mutually exclusive, 400 if both), resolved to addresses server-side. The participant view uses `direction=ANY` and `grouped=false`. Unknown username or company → 404. `hasRole('ORGANIZER')`, `no-store`. No other list endpoint is added.
5. **AC5 (detail)** — A click opens the mail inline below its row on desktop and as a full-screen dialog on mobile (`useBreakpoints().isMobile`), with `GET /mails/{mailKey}` (works across events and for no-event mail). A received mail that belongs to a conversation shows a link to it (`/organizer/events/{eventCode}?tab=communications&conversation={key}`, or the global Mail page for no-event conversations once 16.7 exists).
6. **AC6 (newsletter, scope)** — Newsletters are not listed (each user would see every newsletter; the per-recipient newsletter status lives in `newsletter_recipients`, not in `mail_recipient`). See open question.
7. **AC7 (empty state)** — No mails → one line ("No mail to or from this person yet."), no table.
8. **AC8 (limits stated)** — Mail sent before 16.1a shipped and inbound mail before 16.2a shipped are not listed; mail to an address the user no longer has is not listed. The tab says so in one line of help text.
9. **AC9** — New UI keys in all 10 locales (`userManagement`, `partners` namespaces). Synthetic fixtures only. Read-only feature: nothing is sent.

## Tasks / Subtasks

- [ ] **Task 0 — `GET /mails` present?** — If 16.7 Task 3 has not shipped, build it here first, exactly as specified there (contract, query refactor, tests). Note in Completion Notes which story built it.
- [ ] **Task 1 — Resolve participants to addresses** (AC: 2, 3; TDD first)
  - [ ] RED: `MailParticipantResolverTest` (CUMS client mocked): primary + verified + unverified additional, lowercased, deduplicated; company → PARTNER users of that company, all addresses; unknown user → `UserNotFoundException` → 404; company without contacts → empty set (200 with empty list).
  - [ ] `mailcenter/MailParticipantResolver`. User: `UserApiClient.getUserByUsername` (`client/UserApiClient.java:37`; `UserResponse.additionalEmails` is always present, CUMS `UserResponseMapper.java:66`); same extraction as `ForwardingDirectory.emailsOf` (`forwarding/ForwardingDirectory.java:40–56`). Company: new `UserApiClient.getUsersByCompany(String companyName, String role)` calling CUMS `GET /internal/users?role=PARTNER&company=…` (`InternalUserController.java:54–64`), paged with the existing `MAX_PAGE_SIZE`/`MAX_PAGES` guards of `UserApiClientImpl` (around line 696–700).
- [ ] **Task 2 — Query** (AC: 1, 4, 6)
  - [ ] Spec first: the two query parameters on `GET /mails` in `docs/api/event-mail-api.openapi.yml`, 400/404 documented; `MailMessageSummary` already carries `direction`, `eventCode`, `fromAddress` (16.2a). Add `participantStatus` (the matched recipient's `SENT|DELIVERED|FAILED`, null for received mail).
  - [ ] `MailMessageQueryService`: with `:addresses` (lowercased text array): OUT rows `EXISTS (SELECT 1 FROM mail_recipient r WHERE r.mail_message_id = m.id AND lower(r.address) = ANY(:addresses))`, IN rows `lower(m.from_address) = ANY(:addresses)`; no newsletter backfill branch and no newsletter rows (AC6); `is_spam` excluded; test sends (`is_test`) excluded.
  - [ ] Migration `V{next}__mail_participant_indexes.sql` (re-check the head; 16.2a/16.7/16.8/16.9 add migrations): `CREATE INDEX … ON mail_recipient (lower(address))`, and on `mail_message (lower(from_address))` if 16.2a did not create it.
  - [ ] Integration test (Testcontainers): to primary, to unverified additional, Cc, inbound from additional, other user's mail not listed, newsletter not listed, both filters → 400, unknown → 404, non-organizer → 403.
  - [ ] Bruno: `GET /mails?participantUsername=` with an unknown name → 404; read-only, prose in `docs { }`.
- [ ] **Task 3 — Reusable list component** (AC: 1, 5, 7, 8; Vitest first)
  - [ ] Generalise `communications/MailSentList.tsx` (it is event-bound: `eventCode` prop and `useMailMessages(eventCode, …)`): accept either `eventCode` or a `scope` `{ participantUsername } | { participantCompany }`; in participant scope hide the audience filter chips that make no sense (keep them, minus Newsletter), show event code and direction per row, use `GET /mails` via a new `listMails(params)` in `mailMessageService.ts` and a `useMails(params)` hook. 16.7 needs the same global mode; if it already exists, only add the participant scope.
  - [ ] `MailDetail.tsx`: `eventCode?`; without it, load `GET /mails/{mailKey}`; for direction `IN` show sender instead of the recipient list and the conversation link (AC5). `MailDetailDialog` (or 16.2a's `FullScreenDetailDialog`) unchanged for mobile.
  - [ ] Vitest: participant scope renders direction + event, empty state, inline vs dialog by breakpoint, IN row shows conversation link.
- [ ] **Task 4 — User detail tab** (AC: 1, 7, 8, 9)
  - [ ] `components/organizer/UserManagement/UserDetailView.tsx`: third tab "Mail" (`user-tab-2`, after "Events participated", lines 266–275), panel `<TabPanel value={activeTab} index={2}>` (pattern at 281–289) rendering the list with `participantUsername={user.id}` (`user.id` is the username: `EventsParticipatedTable` sends it as `X-Attendee-Username`, `EventsParticipatedTable.tsx:57`); a third `BottomNavigationAction` with a mail icon (lines 303–306). Mount the list only when the tab is active, so the page does not call `/mails` for every user view.
- [ ] **Task 5 — Partner detail tab** (AC: 3, 7, 8, 9)
  - [ ] `PartnerDetailScreen.tsx` and `PartnerTabNavigation.tsx` both define the tab list (`allTabs`, lines 269–277 and 22–30). Append `{ key: 'mail' }` **at the end** (index 7): tabs are addressed by index (`partnerDetailStore.activeTab`), so inserting in the middle would shift Notes/Topics/Settings. Filter it out for PARTNER in both lists; render `effectiveTab === 7 && currentUser.role !== 'PARTNER'` with `participantCompany={partner.companyName}`; add the bottom-navigation icon.
  - [ ] Vitest: tab visible for ORGANIZER, absent for PARTNER, list mounted with the company scope. Update `PartnerTabNavigation.test.tsx`, `PartnerDetailScreen.test.tsx`.
- [ ] **Task 6 — Local dev check** — anonymized local data has `@example.invalid` addresses that `EmailService` refuses (16.1a Completion Notes), so create a synthetic user with an SES-simulator primary address and one additional address, send a deregistration-link mail to each, simulate an inbound reply (16.2a simulator), and check both tabs. Delete the test rows afterwards.
- [ ] **Task 7 — Docs** — user guide: `docs/user-guide/features/event-mail.md` (new section "Mail of one person or partner"); `06-backend-architecture.md` (participant filter resolves addresses at read time).

### Suggested PR split

One PR (backend filter + both tabs) when `GET /mails` already exists. Otherwise two: PR 1 `GET /mails` + participant filter, PR 2 the tabs.

## Dev Notes

### Why resolve at read time (decision in this story)

- Rows store addresses, not usernames (`mail_recipient.address`, V135; inbound `from_address`, 16.2a). Resolving the user to addresses when the tab is opened picks up additional emails added later and needs no backfill. Cost: one CUMS call (cached in `userApiCache`) per user view, one paged call per partner view.
- Additional emails are globally unique across primary and additional (DB triggers in CUMS `V16__create_user_additional_emails.sql`), so an address never belongs to two users and the list cannot show someone else's mail. Unverified ones are included on purpose: fan-out and CC already go to them (project-context, "Additional Emails": do not gate fan-out on `verified_at`), so mail did reach those addresses.
- A changed primary address is not kept in history; older mail to the old address is not listed (AC8).

### Existing pages (verified)

- User detail: route `/organizer/users/*` → `UserManagement` → `:userId` → `UserDetailView` (`App.tsx:691`, `UserManagement.tsx:80–83`). Tabs: MUI `Tabs` desktop only (`!isMobile`, lines 258–278), `TabPanel` by index, `BottomNavigation` fixed on mobile (291–309). `isMobile` there is `useMediaQuery(theme.breakpoints.down('md'))` (line 79), the same breakpoint as `useBreakpoints().isMobile` (`hooks/useBreakpoints/useBreakpoints.ts:20`).
- Partner detail: route `/organizer/partners/:companyName` (`App.tsx:609`) → `PartnerDetailScreen`; PARTNER users reach the same screen for their own company, so the role guard matters. Contacts = `useUserList({ filters: { company, role: ['PARTNER'] } })` (lines 64–67); the server-side resolver must use the same rule.
- **Observed, not fixed here:** for a PARTNER, `PartnerTabNavigation` hides Topics (lines 35–40) but the mobile bottom navigation in `PartnerDetailScreen` keeps it (lines 278–280) at index 4, and `PARTNER_MAX_TAB = 3` (line 161) clamps that to Overview. Read from code, not reproduced. Do not widen it with the new tab; report it rather than fixing it in this story.

### Not in scope

- Sending from the tab (that is the composer, 16.8).
- Newsletters (AC6) and forwarded alias copies (never recorded as OUT; the inbound original is the record).

### Owner decisions (2026-11-01: recommendations below accepted, see epic "Decisions taken 2026-11-01")

- Newsletters in the person's tab (AC6). Recommendation: leave them out; if wanted later, add a "Newsletters received" count from `newsletter_recipients` instead of listing every send.

### References

- Owner request 2026-10-31 (memory `mail-tab-on-user-and-partner-detail.md`)
- `_bmad-output/implementation-artifacts/16-7-global-mail-navigation.md` (`GET /mails` contract), `16-2a-speaker-conversations.md` (inbound columns, conversation link)
- `docs/api/event-mail-api.openapi.yml`
- `services/event-management-service/src/main/java/ch/batbern/events/mailcenter/MailMessageQueryService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/client/UserApiClient.java`, `client/impl/UserApiClientImpl.java`, `forwarding/ForwardingDirectory.java`
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/controller/InternalUserController.java`
- `web-frontend/src/components/organizer/UserManagement/UserDetailView.tsx`
- `web-frontend/src/components/organizer/PartnerManagement/{PartnerDetailScreen,PartnerTabNavigation}.tsx`, `web-frontend/src/stores/partnerDetailStore.ts`
- `web-frontend/src/components/organizer/EventPage/communications/{MailSentList,MailDetail}.tsx`

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List

## Change Log

- 2026-11-01: Story drafted from the owner request of 2026-10-31.
