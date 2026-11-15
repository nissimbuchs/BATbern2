# Fix Summary — User guide — partner portal
**Fixed:** 2026-03-09

## Changes made

- **M1** (`README.md`): Corrected the Data Visibility table — "Meetings" row for partners changed from "All upcoming meetings" to "Receive .ics calendar invite via email only". `PartnerMeetingControllerIntegrationTest#should_return403_when_partnerTriesToListMeetings` confirms partners get 403 on `GET /api/v1/partner-meetings`; they have no read access to the meeting list.
- **M2** (`meetings.md`): Marked **Location** field as optional (`No`) in the meeting creation form table, with note "(shown in .ics invite if provided)". The `createMeeting(eventCode)` test helper omits location and expects 201; no test validates that a missing location returns 400.
- **N1** (`topic-voting.md`, For Organisers section): Added that `companyName` is required when an organiser suggests a topic on behalf of a partner; omitting it returns 400. Based on `TopicControllerIntegrationTest#should_return400_when_organizerSuggestsTopic_withoutCompanyName`.
- **N2** (`meetings.md`, Adding Meeting Notes section): Added warning that notes are scoped to their partner company — updating or deleting a note via another partner's URL returns 404. Based on `PartnerNoteControllerIntegrationTest#should_return404_when_updatingNoteFromDifferentPartner` and `#should_return404_when_deletingNoteFromDifferentPartner`.
- **N3** (`README.md`, new Constraints section): Added that partner company names are limited to 12 characters per ADR-003. Based on `PartnerTest#should_enforceMaxLength_when_companyNameTooLong`.
- **N4** (`analytics.md`, Time Period Toggle section): Documented the `fromYear` query parameter on the dashboard endpoint. Last 5 Years sends `fromYear = currentYear - 5`; Full History omits it. Based on `PartnerAnalyticsControllerIntegrationTest#should_passFromYearToClient_when_fromYearParamProvided`.
- **N5** (`topic-voting.md`, For Organisers section): Added that organisers cannot cast or remove votes — vote endpoints return 403 for the ORGANIZER role. Based on `TopicControllerIntegrationTest#should_return403_when_organizerTriesToVote`.

## Skipped — needs manual decision

- **U1**: "Data is loaded on demand with a 15-minute cache" — no test validates cache TTL or cache layer presence. May need a unit/integration test or removal of the performance claim if the cache was not implemented.
- **U2**: "A footer row with column totals and the cost-per-attendee metric" in XLSX — `should_returnXlsx_when_exportRequested` only checks headers, not file content. Needs an Apache POI-based content assertion or doc should be softened to "may include".
- **U3**: "If multiple users from the same company vote, only one vote is counted (last action wins)" — all vote tests use different companies. A same-company concurrent-vote scenario is the key edge case but has no test.
- **U4**: "Description | No | 500 characters" — max-500 constraint may not be enforced server-side; no test validates rejection of a 501-character description.
- **U5**: "Title | Yes | 255 characters (minimum 5)" — minimum-5 is tested; maximum-255 is not.
- **U6**: "The calendar file contains exactly two VEVENT blocks" — no test inspects the .ics payload at all. A one-VEVENT implementation would pass all existing tests.
- **U7**: ".ics follows RFC 5545 with METHOD:REQUEST" — AWS SES attachment format and METHOD value are never verified.
- **U8**: "The main view is a table sorted by event date (most recent first)" — dashboard test asserts row count and field values but not row ordering.
