# Doc Audit Findings — User Guide — Partner Portal
**Audited:** 2026-03-09
**Doc:** `docs/user-guide/partner-portal` (README.md, analytics.md, meetings.md, topic-voting.md)
**Tests searched:** `services/partner-coordination-service/src/test/java`

## Summary
- VALIDATED: 27
- MISMATCH: 2
- UNTESTED: 8
- UNDOCUMENTED: 5

---

## MISMATCH

### M1 — Partner meeting list visibility
**Doc claims:** `README.md` data visibility table: "Meetings | All upcoming meetings | All meetings + management tools" — implying partners can view a list of upcoming meetings.
**Test asserts:** `PartnerMeetingControllerIntegrationTest#should_return403_when_partnerTriesToListMeetings` — `GET /api/v1/partner-meetings` with PARTNER role returns `403 Forbidden`.
**Action:** Update README.md data visibility table to: "Meetings | Receive .ics calendar invite via email only | All meetings + management tools" — partners have no read access to the meeting list; they only receive the .ics attachment.

### M2 — Location field required on meeting creation
**Doc claims:** `meetings.md` creation form table: "Location | Yes | Venue name and/or address"
**Test asserts:** `PartnerMeetingControllerIntegrationTest` helper `createMeeting(eventCode)` omits the `location` field entirely and expects `status().isCreated()` (201). No test validates that omitting location returns 400.
**Action:** Update meetings.md to mark Location as optional: "Location | No | Venue name and/or address (shown in .ics invite if provided)"

---

## UNTESTED

### U1 — 15-minute analytics cache
**Doc claims:** `analytics.md`: "Data is loaded on demand with a 15-minute cache, so the first load per session may take up to 5 seconds; subsequent loads are near-instant."
**Risk:** medium — cache layer may be misconfigured, absent, or have wrong TTL without any test catching it.

### U2 — XLSX export footer row
**Doc claims:** `analytics.md`: "A footer row with column totals and the cost-per-attendee metric" in the exported Excel file.
**Risk:** medium — `should_returnXlsx_when_exportRequested` only asserts `Content-Type` and `Content-Disposition` headers; actual Excel content (columns, footer) is never inspected.

### U3 — One vote per company, multi-user scenario
**Doc claims:** `topic-voting.md`: "If multiple users from the same company vote, only one vote is counted (last action wins)."
**Risk:** high — all vote tests use users from *different* companies. Two users from the same company voting simultaneously is the edge case that makes this claim testable but untested.

### U4 — Topic description max 500 characters
**Doc claims:** `topic-voting.md` suggestion form table: "Description | No | 500 characters"
**Risk:** low — constraint may not be enforced server-side.

### U5 — Topic title max 255 characters
**Doc claims:** `topic-voting.md` suggestion form table: "Title | Yes | 255 characters (minimum 5)"
**Risk:** low — minimum-5 is tested; maximum-255 is not.

### U6 — .ics file contains exactly two VEVENT blocks
**Doc claims:** `meetings.md`: "The calendar file contains exactly two VEVENT blocks — the partner lunch and the BATbern event."
**Risk:** high — no test inspects the .ics payload at all. A one-VEVENT implementation would pass all existing tests.

### U7 — .ics follows RFC 5545 with METHOD:REQUEST
**Doc claims:** `meetings.md`: "The .ics file follows RFC 5545 (iCalendar specification) and uses `METHOD:REQUEST` so the invite appears as an actionable calendar event in supported email clients."
**Risk:** medium — AWS SES attachment format and METHOD value are never verified.

### U8 — Analytics dashboard sorted by event date (most recent first)
**Doc claims:** `analytics.md`: "The main view is a table sorted by event date (most recent first)."
**Risk:** low — the dashboard test `should_returnDashboard_when_partnerRequestsOwnCompany` asserts row count and field values but not that rows are ordered by date descending.

---

## UNDOCUMENTED

### N1 — Organizer must supply `companyName` when suggesting a topic
**Test:** `TopicControllerIntegrationTest#should_return400_when_organizerSuggestsTopic_withoutCompanyName` — organizer `POST /api/v1/partners/topics` without a `companyName` field in the body returns 400.
**Action:** Add to `topic-voting.md` → "For Organisers" section: "When suggesting a topic on behalf of a partner, the `companyName` field is required in the request body. Omitting it returns 400."

### N2 — Cross-partner note ownership validation
**Test:** `PartnerNoteControllerIntegrationTest#should_return404_when_updatingNoteFromDifferentPartner` and `#should_return404_when_deletingNoteFromDifferentPartner` — patching or deleting a note ID that belongs to a *different* partner company via the wrong company URL returns 404, not 200.
**Action:** Add to `meetings.md` (or a future notes guide): "Notes are scoped to their partner. Attempting to update or delete a note via another partner company's URL returns 404."

### N3 — Partner company name max 12 characters
**Test:** `PartnerTest#should_enforceMaxLength_when_companyNameTooLong` — `companyName` exceeding 12 characters throws `IllegalArgumentException("...12 characters")`.
**Action:** Add to `README.md` or a partner management guide: "Partner company names are limited to 12 characters per ADR-003 (meaningful short identifiers)."

### N4 — Analytics `fromYear` query parameter
**Test:** `PartnerAnalyticsControllerIntegrationTest#should_passFromYearToClient_when_fromYearParamProvided` — the dashboard endpoint accepts an optional `fromYear` integer query param that is forwarded to the event management service for filtering.
**Action:** Add to `analytics.md` → "Time Period Toggle" section: "The toggle maps to a `fromYear` query parameter on `GET /api/v1/partners/{companyName}/analytics/dashboard`. The Last 5 Years option defaults to `currentYear - 5`; Full History omits the parameter."

### N5 — Organisers cannot vote on topics
**Test:** `TopicControllerIntegrationTest#should_return403_when_organizerTriesToVote` — `POST /api/v1/partners/topics/{id}/vote` with ORGANIZER role returns 403.
**Action:** Add to `topic-voting.md` → "For Organisers" section: "Organisers cannot cast or remove votes — the vote endpoints are restricted to the PARTNER role."

---

## VALIDATED
- "Each partner sees only their own company's data" (analytics) → `PartnerAnalyticsControllerIntegrationTest#should_return403_when_partnerRequestsOtherCompany`
- "Organiser can view any company's analytics" → `PartnerAnalyticsControllerIntegrationTest#should_returnDashboard_when_organizerRequestsAnyCompany`
- "Cost per attendee = Partnership cost ÷ Total company attendees" → `should_computeCostPerAttendee_when_attendeesExist` (10 000 / 18 ≈ 555.56)
- "If no employees attended … shows as N/A" (null) → `should_returnNullCostPerAttendee_when_noAttendees`
- "Export XLSX" returns correct content-type + Content-Disposition → `should_returnXlsx_when_exportRequested`
- "Partner cannot export another company's data" (403) → `should_return403_when_partnerExportsOtherCompany`
- "Unauthenticated access returns 403" (analytics) → `should_return403_when_notAuthenticated`
- "Topics sorted by vote count descending" → `TopicControllerIntegrationTest#should_listTopicsSortedByVoteCountDesc`
- "Vote toggle: cast adds, remove decrements" → `should_castVote_and_increment_voteCount`, `should_removeVote_and_decrement_voteCount`
- "Voting twice is idempotent (still 1 vote)" → `should_beIdempotent_when_votingTwice`
- "Remove vote idempotent (no error if not voted)" → `should_beIdempotent_when_removingVoteThatDoesNotExist`
- "Multiple companies each contribute one vote" → `should_countVotesAccurately_when_multiplePartnersVote`
- "New topic starts with status PROPOSED" → `should_createTopic_when_validTitleProvided`
- "Title minimum 5 characters" → `should_return400_when_titleTooShort`
- "Title is required" → `should_return400_when_titleMissing`
- "Partner cannot spoof company name in body" → `should_ignoreCompanyName_in_body_when_partnerSubmits`
- "Organisers can suggest topics on behalf of a partner" → `should_createTopic_when_organizerSubmits_onBehalfOfPartnerCompany`
- "Partner cannot update topic status (403)" → `should_return403_when_partnerTriesToUpdateStatus`
- "SELECTED status with plannedEvent note visible to partners" → `should_showPlannedEvent_when_topicIsSelected`
- "DECLINED status set by organiser" → `should_allowOrganizer_to_updateTopicStatus_to_DECLINED`
- "`currentPartnerHasVoted` flag correctly reflects vote state" → `should_markCurrentPartnerHasVoted_when_partnerHasVoted`
- "Send invite API responds 202 Accepted immediately" → `PartnerMeetingControllerIntegrationTest#should_return202_when_sendInviteCalled`
- "Invite Sent timestamp appears after send" → `should_setInviteSentAt_when_sendInviteCalled`
- "Meeting date auto-filled from linked event" → `should_createMeeting_when_validRequestProvided` (meetingDate = 2026-05-14 from mocked event)
- "Partner cannot create meetings (403)" → `should_return403_when_partnerTriesToCreateMeeting`
- "Partner cannot send invite (403)" → `should_return403_when_partnerTriesToSendInvite`
- "Partner notes hidden from partners (403)" → `PartnerNoteControllerIntegrationTest#should_return403_when_partnerTriesToListNotes`, `#should_return403_when_partnerTriesToCreateNote`
