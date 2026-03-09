# Doc Audit Findings — Partner Coordination (Epic 8)
**Audited:** 2026-03-09
**Doc:** `docs/prd/epic-8-partner-coordination.md`
**Tests searched:** `services/partner-coordination-service/src/test/java`

## Summary
- VALIDATED: 22
- MISMATCH: 1
- UNTESTED: 7
- UNDOCUMENTED: 7

---

## MISMATCH

### M1 — Topic title has a minimum length constraint not mentioned in the doc
**Doc claims:** "Topic suggestion: title (required) + short description (optional) — no mandatory justification"
**Test asserts:** `TopicControllerIntegrationTest#should_return400_when_titleTooShort` — 400 returned for title `"Hi"` (2 chars), comment says `"< 5 chars"`
**Action:** Update doc to say: "Topic suggestion: title (required, minimum 5 characters) + short description (optional) — no mandatory justification"

---

## UNTESTED

### U1 — Caffeine cache TTLs not validated
**Doc claims:** "Data queried on demand; Caffeine-cached 15 minutes — no nightly job, no materialized views" (Story 8.1) and "EventManagementClient.getEventSummary(eventCode) (Caffeine cache 1h)" (Story 8.3)
**Risk:** medium — if cache is misconfigured (e.g. wrong TTL or disabled) tests would still pass; stale data bugs could surface only in production

### U2 — Performance targets for Epic 8 endpoints not covered
**Doc claims:** "Dashboard P95 < 5s | Cached response < 50ms | Cold response < 500ms" (8.1); "Topic list P95 < 3s | Vote toggle < 500ms" (8.2); "Meeting list P95 < 3s | Invite 202 response < 200ms" (8.3)
**Risk:** medium — `PartnerPerformanceTest` only measures the base partner CRUD list/detail endpoints; no performance test covers analytics, topics, or meetings

### U3 — ICS file content (two VEVENTs) not validated
**Doc claims:** "Calendar invite: standard .ics file (RFC 5545) with two VEVENTs (partner lunch + BATbern event itself), sent via AWS SES to all partner contacts"
**Risk:** high — `PartnerMeetingControllerIntegrationTest#should_return202_when_sendInviteCalled` only asserts HTTP 202 and the JSON response body; the ICS byte content (structure, two VEVENTs, RFC 5545 headers) is never inspected

### U4 — Attendance table percentage column not validated
**Doc claims:** "Attendance table: event name, date, company attendees, total attendees, percentage — sorted by date descending"
**Risk:** low — `PartnerAnalyticsControllerIntegrationTest#should_returnDashboard_when_partnerRequestsOwnCompany` only asserts `eventCode`, `totalAttendees`, and `companyAttendees`; the `percentage` field (and its formula) is never asserted

### U5 — Attendance table date ordering not validated
**Doc claims:** "Attendance table: … sorted by date descending" (Story 8.1)
**Risk:** low — no test verifies the order of rows in `$.attendanceSummary`; test data has two events but order is not asserted

### U6 — Email recipients for calendar invite not validated
**Doc claims:** "sent via AWS SES to all partner contacts on record"
**Risk:** medium — `PartnerMeetingControllerIntegrationTest#should_setInviteSentAt_when_sendInviteCalled` mocks `userServiceClient.getUsersByRole("PARTNER")` but does not assert that the SES call was made with those recipients; actual email dispatch is `@Async` and not inspected

### U7 — Agenda included in ICS invite description not validated
**Doc claims:** "Agenda as free text — included in the calendar invite description" (Story 8.3)
**Risk:** low — tests confirm agenda is persisted via PATCH; no test checks that the ICS `DESCRIPTION` field contains the agenda text

---

## UNDOCUMENTED

### N1 — Topic title minimum length (≥ 5 characters) enforced
**Test:** `TopicControllerIntegrationTest#should_return400_when_titleTooShort` — 400 for title `"Hi"` (< 5 chars)
**Action:** Add to Story 8.2 scope: "Title must be at least 5 characters (validation enforced at API level)"

### N2 — Partner note title maximum length (500 characters) enforced
**Test:** `PartnerNoteControllerIntegrationTest#should_return400_when_titleExceeds500CharsOnUpdate` — 400 for title ≥ 501 chars
**Action:** Add to Story 8.4 scope: "Title has a 500-character maximum length"

### N3 — Partner note content is required (not optional)
**Test:** `PartnerNoteControllerIntegrationTest#should_return400_when_contentMissing` — 400 when only `title` is supplied
**Action:** Update Story 8.4 scope: "title (required) + content (required)" — the doc says "title + free-text content" but does not state content is mandatory

### N4 — Cross-partner note ownership scoping enforced
**Test:** `PartnerNoteControllerIntegrationTest#should_return404_when_updatingNoteFromDifferentPartner` and `#should_return404_when_deletingNoteFromDifferentPartner` — 404 when an organizer tries to update/delete a note belonging to a different partner via the wrong company URL
**Action:** Add to Story 8.4: "Note PATCH/DELETE validate that the noteId belongs to the companyName in the URL path; cross-partner access returns 404"

### N5 — Partner cannot spoof companyName in topic suggestion request body
**Test:** `TopicControllerIntegrationTest#should_ignoreCompanyName_in_body_when_partnerSubmits` — when a PARTNER submits a topic with `companyName: "SomeOtherCompany"` the body field is silently ignored; `suggestedByCompany` is resolved from the JWT instead
**Action:** Add security note to Story 8.2: "For PARTNER users, `companyName` in the request body is ignored; the company is always resolved from the authenticated user's JWT. Only ORGANIZER users may specify `companyName` explicitly."

### N6 — Organizer cannot vote on topics (403)
**Test:** `TopicControllerIntegrationTest#should_return403_when_organizerTriesToVote` — ORGANIZER receives 403 on `POST /api/v1/partners/topics/{topicId}/vote`
**Action:** Add to Story 8.2 role-based access section: "Organizers may NOT cast votes (403 Forbidden). Only PARTNER users may vote."

### N7 — Organizer can suggest topics on behalf of a partner company
**Test:** `TopicControllerIntegrationTest#should_createTopic_when_organizerSubmits_onBehalfOfPartnerCompany` — ORGANIZER `POST /api/v1/partners/topics` with `companyName` body field returns 201; `#should_return400_when_organizerSuggestsTopic_withoutCompanyName` — omitting `companyName` as organizer returns 400
**Action:** Add to Story 8.2 scope: "Organizers may suggest topics on behalf of a partner company by supplying `companyName` in the request body. Omitting `companyName` as an organizer returns 400."

---

## VALIDATED
- "Partners see only their own company's data (enforced at API level)" → `PartnerAnalyticsControllerIntegrationTest#should_return403_when_partnerRequestsOtherCompany`
- "Organizers can view any company's analytics" → `PartnerAnalyticsControllerIntegrationTest#should_returnDashboard_when_organizerRequestsAnyCompany`
- "Single KPI: cost-per-attendee = partnership cost ÷ total company attendees" → `PartnerAnalyticsControllerIntegrationTest#should_computeCostPerAttendee_when_attendeesExist` (10000/18 ≈ 555.56)
- "costPerAttendee is null when no attendees" → `PartnerAnalyticsControllerIntegrationTest#should_returnNullCostPerAttendee_when_noAttendees`
- "Excel XLSX export" → `PartnerAnalyticsControllerIntegrationTest#should_returnXlsx_when_exportRequested`
- "fromYear parameter passed through to downstream client" → `PartnerAnalyticsControllerIntegrationTest#should_passFromYearToClient_when_fromYearParamProvided`
- "Unauthenticated analytics access returns 403" → `PartnerAnalyticsControllerIntegrationTest#should_return403_when_notAuthenticated`
- "Partner cannot export another company's data (403)" → `PartnerAnalyticsControllerIntegrationTest#should_return403_when_partnerExportsOtherCompany`
- "Topic list sorted by votes descending" → `TopicControllerIntegrationTest#should_listTopicsSortedByVoteCountDesc`
- "Topic fields: id, title, description, suggestedByCompany, voteCount, status, currentPartnerHasVoted" → `TopicControllerIntegrationTest#should_showTopicFields_whenListingTopics`
- "Toggle vote: cast increments, remove decrements" → `TopicControllerIntegrationTest#should_castVote_and_increment_voteCount`, `#should_removeVote_and_decrement_voteCount`
- "Voting is idempotent (double-vote stays at 1, delete non-existent vote is no-op)" → `TopicControllerIntegrationTest#should_beIdempotent_when_votingTwice`, `#should_beIdempotent_when_removingVoteThatDoesNotExist`
- "New topic status defaults to PROPOSED" → `TopicControllerIntegrationTest#should_createTopic_when_validTitleProvided`
- "Organizer can set status to SELECTED with optional plannedEvent" → `TopicControllerIntegrationTest#should_allowOrganizer_to_updateTopicStatus_to_SELECTED`
- "Organizer can set status to DECLINED" → `TopicControllerIntegrationTest#should_allowOrganizer_to_updateTopicStatus_to_DECLINED`
- "Partners cannot change topic status (403)" → `TopicControllerIntegrationTest#should_return403_when_partnerTriesToUpdateStatus`
- "Partners see plannedEvent when topic is SELECTED" → `TopicControllerIntegrationTest#should_showPlannedEvent_when_topicIsSelected`
- "Meeting date auto-filled from linked BATbern event" → `PartnerMeetingControllerIntegrationTest#should_createMeeting_when_validRequestProvided`
- "202 Accepted for send-invite, inviteSentAt persisted" → `PartnerMeetingControllerIntegrationTest#should_return202_when_sendInviteCalled`, `#should_setInviteSentAt_when_sendInviteCalled`
- "Agenda and notes independently patchable" → `PartnerMeetingControllerIntegrationTest#should_updateAgendaAndNotes_independently`
- "Partners cannot list/create meetings or send invites (403)" → `PartnerMeetingControllerIntegrationTest#should_return403_when_partnerTriesToListMeetings`, `#should_return403_when_partnerTriesToCreateMeeting`, `#should_return403_when_partnerTriesToSendInvite`
- "Notes list sorted by createdAt descending" → `PartnerNoteControllerIntegrationTest#should_returnNotesSortedByCreatedAtDesc_when_multipleNotesExist`
- "Author username captured from JWT" → `PartnerNoteControllerIntegrationTest#should_createNote_when_validRequest` (asserts `authorUsername` notNull)
- "PARTNER role receives 403 on all note endpoints" → `PartnerNoteControllerIntegrationTest#should_return403_when_partnerTriesToListNotes`, `#should_return403_when_partnerTriesToCreateNote`
- "404 on unknown company for notes" → `PartnerNoteControllerIntegrationTest#should_return404_when_companyNameUnknown`
