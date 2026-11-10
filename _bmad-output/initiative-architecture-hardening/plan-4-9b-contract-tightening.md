---
title: '4.9 owner additions: idn-email, no fields projection, always-set properties required'
type: 'refactor'
ticket: ''
created: '2026-11-09'
status: 'in-review'
baseline_revision: '6156367789162f7f927093bae625e935d6e68e3b'
route: 'full'
route_source: 'auto'
risk: 'high'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/initiative-architecture-hardening/null-vs-optional-options.md'
  - '{project-root}/docs/architecture/coding-standards.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The specs misdescribe what the services send.
- CUMS stores IDN addresses (`juerg.brunner@bücherstube.example`), while the specs say `format: email`, which is ASCII only.
- `GET /companies` and `GET /users` offer a `fields` projection that no caller uses. On users it does nothing; on companies it breaks the schema's `required`.
- Many response properties every code path sets are typed optional, so the frontend guards against values that never go missing.

**Approach:** These are the owner decisions of 2026-11-09 (deferred-work.md, last entry).
- Switch `format: email` to `idn-email` where an address can be IDN.
- Remove `fields` with its projection code, and add one ADR-013 §3 line saying a list offers `fields` only when a caller needs it.
- Mark `required` the response properties that every producing path sets, spec by spec, with the response conformance gate as the safety net. Then remove the frontend guards that existed only because of the optional type.
- The CUMS response-conformance baseline loses its 4 lines.

## Boundaries & Constraints

**Always:**
- No deferred work (owner 2026-11-09). Failures that surface are fixed in the code within this PR, with no workarounds.
- The #1102 pattern (`EmailAddresses.OPENAPI_PATTERN`) stays on every property that has it today, and `EmailAddressesTest` stays green.
- Requests keep rejecting what the pattern rejects. Losing `@Email` is acceptable only because `@Pattern` and `@Size` remain; prove it with a request integration test using the IDN person, plus one invalid address that gets 400.
- A property becomes `required` only when both hold:
  - every 2xx response for it in the CUMS, EMS and PCS integration suites carries it, measured by a temporary recorder that is not committed;
  - reading its producing code paths confirms it is always set.
- Do this per schema and record the evidence per schema in Implementation Notes.
- The frontend MSW validator learns `idn-email`, so the format is checked there too.

**Never:**
- Do not mark a property `required` from code reading alone, or from the tests alone.
- Do not use `!` or `as` to satisfy a type that became non-optional.
- Do not make a nullable property non-nullable here. `required` means the property is present, not that it is non-null.
- Do not change the path-parameter `email` operations (10b owns them).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| IDN request | `POST /users` with email `jürg@bücherstube.example`, ASCII local part required | local part `ü` → 400, as the pattern says; `juerg.brunner@bücherstube.example` → 201 | 400 names email |
| IDN response | user with IDN address read via `getOrCreateInternalUser` | conforms, baseline line gone | — |
| fields gone | `GET /companies?fields=name` | parameter unknown: the spec matcher and frontend check reject it; the server ignores it or returns 400 per the existing unknown-param behaviour | — |

</frozen-after-approval>

## Code Map

- `format: email` sites, 26 in total, listed in the investigation:
  - users-api 3466/3880/3843/3923/4079/4150/4198/4318
  - registrations 533/1504/1539/1654/1802
  - newsletter 1127/1557
  - speakers 2003/2635/2695/2755/2788
  - internal pcs 76 and ems 576
  - Path params at users-api 2293/2350/2385/2428 are out of scope.
- Generator: `ModelUtils.isEmailSchema` keys on `email` only, so `idn-email` drops `@Email` and keeps `@Pattern`/`@Size`.
- Java validator: networknt 2.0.1 supports `idn-email` (`OpenApiContract`, shared-kernel testFixtures).
- `web-frontend/src/test/msw/openapi.ts` `FORMATS` L164-169: add `idn-email`, because unknown formats are skipped at L246.
- `fields`:
  - `companies-api` L251-253/307-313 and `users-api` L396-398/442-448.
  - `CompanyController.java` L77-81, `CompanyQueryService` L67/85/146-217, `UserController.listUsers` L170-172 (unused there).
  - Test `AdvancedQueryIntegrationTest` L282.
  - Shared-kernel `FieldSelector` stays: the gateway `TestResourceController` uses it.
  - ADR-013 L107-111.
- CUMS baseline: `services/company-user-management-service/src/test/resources/response-conformance-baseline.txt`, 4 lines.
- Required candidates named by the owner: `UserResponse.active` (users-api 3493), the merged `UserSettings` from the core 4.9 plan, `RsvpDTO` (partner-meetings 503). The recorder decides the rest.
- Frontend guards: `userAccountApi.ts` L61-65, `AuthContext.tsx` L150-155, `PartnerMeetingRsvpPanel.tsx` L101/113, UserManagement `active` reads.

## Tasks & Acceptance

**Execution:**
- [x] After the core 4.9 plan is built: set `baseline_revision`, then record the baseline counts.
- [x] IDN: red integration tests (IDN person create plus an invalid local part), then specs, regenerate, the MSW format, then remove the baseline line.
- [x] fields: spec, then remove the CUMS projection, the test and the unused controller parameter, then add the ADR-013 line, then remove the 3 baseline lines, then regenerate.
- [x] Required: write a temporary recorder that is not committed (or an env flag on `ResponseConformance` that dumps per schema and property: present or absent counts). Run the three suites one at a time. For each always-present property confirmed by code reading, add it to `required`. Regenerate, then remove the frontend guards that are now dead and fix the callers properly. Run the suites again with the gate.
- [x] Docs: coding-standards (response conformance: baseline empty for CUMS), deferred-work entry resolved.

**Acceptance Criteria:**
- Given the CUMS full suite, when it runs, then the response-conformance baseline file has no entries and the run is green. The same holds for EMS and PCS.
- Given the specs, when grepped, then no response or request property that can hold a stored address uses `format: email`. Path params are the exception, left to 10b.
- Given the frontend, when `npm run type-check` and vitest run, then they are green with no new `!` or `as`.

## Implementation Notes

Built 2026-11-09/10 on `chore/17-0-tickets-done` (00e33140 idn-email, 5bce62ea fields, then the
required commit). The machine rebooted mid-run; the work resumed from the uncommitted tree.

**IDN.** Finding: the Java validator (networknt 2.0.1 → Apache Commons `EmailValidator`) accepts IDN
domains under `format: email` too; it rejected `juerg.brunner@bücherstube.example` because its IANA
TLD list has no `.example` (RFC 2606). Measured with the jar: `x@bücherstube.ch` passes `email` and
`idn-email`, `…@….example` fails both. The spec switch to `idn-email` is still right (JSON Schema
reads `email` as RFC 5321), but the baseline line only went away with `GeneratedCodeSemantics` rule 4:
both e-mail formats are checked as a mailbox shape, the #1102 pattern deciding where present
(ContractServerTest positive control). AccountMergeRequest and PutMyNewsletterSubscriptionRequest had
only `@Email`, so they gained the #1102 pattern (EmailAddressesTest: 14 → 16 properties, two of them registration responses that echo the request; 18 after review pass 1 added the two internal request properties and scanned `docs/api/internal/`). Red-first ITs in
UserControllerIntegrationTest (IDN create 201 + conforming response; Unicode local part 400).

**fields.** Removed from both specs, CompanyQueryService projection, UserController parameter;
AdvancedQueryIntegrationTest now asserts a sent `fields` is ignored and items stay whole. Stale
`fields` docs in 04-api-company/user-management and the EventController Javadoc fixed.

**required: method.** A temporary, uncommitted recorder in `ResponseConformance` dumped every checked
IT response (CUMS 371, EMS 846, PCS 445) with the handler's declared return type and, by reflection,
every class reachable from it with its `@JsonInclude`/`@JsonIgnore`/serializer flags
(`~/.cache/batbern-test-logs/49b-dump/`). A property became a candidate when it was optional and
present in every response of its schema object (563 candidates, 135 objects). Code reading per object:
the shared ObjectMapper writes every field (no global inclusion, grep of services and shared-kernel),
so a class without `NON_NULL` sends every property it declares; every producing operation of each
object (static walk of all 2xx responses of all specs) was checked to return such a class, or the
same class as an exercised operation where the operation is not exercised (unsuppressSubscriberById,
listUserRegistrations, patchSessionQnaWindow, getEventTimetable, inviteSpeaker,
createTasksFromTemplates, getAllTasks, topics updateTopic/getSimilarTopics, partners updateTopic).
`NON_NULL` classes were read value by value. No candidate is used in a request body or a non-2xx body
except SendReminderResponse.

**Rejected although every IT response had them (code says otherwise):** CompanyLogo url/s3Key/fileId
(`toUri` null for an invalid URL; nullable columns), CompanyResponse.displayName and
CompanySearchResponse.displayName (nullable `display_name`, omitted by NON_NULL),
EventAnalytics.metrics.registrations (only when `?metrics=` asks for it), SendReminderResponse (also
the 400/409 body; `emailAddress` can be null).

**Result:** 554 properties in 131 schema objects. Every one was present in every IT response of its
object (re-measured from the dumps after the reboot). Nullability unchanged. Examples filled (301
insertions, `null` where nullable). Java: required-args constructors changed, so call sites moved to
the no-arg constructor with fluent setters. Frontend: shared wire builders `src/test/fixtures/wire.ts`
(+ `anEventDetail`), test fixtures in the wire shape, no new `!`/`as`; dead guards removed where
typed lint (`no-unnecessary-condition`, compared with the HEAD types) reported them new, plus the plan's
userAccountApi/AuthContext settings guards and PartnerMeetingRsvpPanel; two impossible-state tests
(event without createdAt/updatedAt) removed.

| Spec | Schema | Now required | IT responses (each carried all) | Code |
|---|---|---|---|---|
| companies-api | CompanyResponse | createdBy | 116 | NON_NULL class: `createdBy` from `companies.created_by` NOT NULL, set by both mappers |
| companies-api | LogoCleanupStatistics | pendingCount, confirmedCount, associatedCount, expiredPendingCount, expiredConfirmedCount | 1 | NON_NULL class: LogoController sets all five from long counts |
| companies-api | LogoPresignedUploadUrl | uploadUrl, fileId, fileExtension, expiresInMinutes, requiredHeaders | 1 | NON_NULL class: GenericLogoService.generatePresignedUrl sets all five from non-null values |
| ems-internal | EventSchedule | venueName, venueAddress, eventType | 5 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-ai-api | AiThemeImageJob | imageUrl, s3Key | 6 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-analytics-api | AttendanceEventItem | category | 7 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-analytics-api | CompanyAttendanceShare | displayName | 10 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-analytics-api | CompanySessionItem | displayName | 4 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-analytics-api | CompanyYearAttendanceItem | displayName | 14 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-analytics-api | EventAnalytics | eventCode, metrics | 3 | NON_NULL class: `metrics` always set (EventAnalyticsService), `eventCode` from the path (EventController) |
| event-analytics-api | EventAnalytics.metrics.attendance | expected, actual, rate | 1 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-analytics-api | EventAnalytics.metrics.engagement | totalSessions, totalParticipants, averageSessionsPerParticipant | 1 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-analytics-api | EventAnalytics.metrics.registrations | total, byStatus | 3 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-analytics-api | EventAnalytics.metrics.registrations.byStatus | confirmed, pending, cancelled | 3 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-analytics-api | EventAnalytics.timeframe | start, end | 1 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-analytics-api | EventTimelineItem | category | 8 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-app-settings-api | FeatureFlagsResponse | aiContentEnabled | 1 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-mail-api | MailAlreadySent | mailKey | 1 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-mail-api | MailAttachment | contentType, size, kept | 3 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-mail-api | MailComposeRecipient | cc | 16 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-mail-api | MailConversationSummary | eventCode, counterpartName, assigneeUsername, mergeRequestId, logoRequestId | 19 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-mail-api | MailMessageDetail | bodyHtml, progress | 21 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-mail-api | MailMessageSummary | senderUsername, templateKey, subject, newsletterSendId, newsletterStatus, reference, direction, fromAddress, fromName, eventCode, conversationKey, spam, participantStatus, attachments | 54 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-mail-api | MailPreviewResponse | recipient, templateKey, replyTo, highlightedSubject, highlightedHtml, offeredVariables, allowedVariables, alreadySent | 16 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-mail-api | MailRecipientEntry | failureReason | 6 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-media-api | EventPhotoResponse | filename, uploadedBy, sortOrder | 2 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-newsletter-api | EmailTemplateResponse | subject, variables, layoutKey | 78 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-newsletter-api | NewsletterSubscriptionStatusResponse | email | 3 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-newsletter-api | SubscriberResponse | firstName, language, source, username, unsubscribedAt, bounceType, bounceCount, lastBouncedAt, suppressedAt | 306 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-newsletter-api | inline /paths/~1newsletter~1subscribers/get/responses/200/content/application~1json/schema | data, pagination | 27 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-newsletter-api | inline /paths/~1newsletter~1unsubscribe~1verify/get/responses/200/content/application~1json/schema | email | 1 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-notifications-api | DeleteNotificationResponse | success | 2 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-notifications-api | MarkAsReadResponse | success, markedCount, updatedAt | 3 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-notifications-api | NotificationCountResponse | count | 2 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-notifications-api | NotificationPagination | page, limit, totalItems, totalPages | 3 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-notifications-api | NotificationResponse | id, recipientUsername, eventCode, notificationType, channel, priority, subject, body, status, sentAt, readAt, failedAt, failureReason, metadata, createdAt, updatedAt | 15 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-notifications-api | NotificationsResponse | data, pagination | 3 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-registrations-api | CreateRegistrationResponse | companyUpdated | 53 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-registrations-api | MyRegistrationResponse | registrationCode, eventCode, status, registrationDate, waitlistPosition | 10 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-registrations-api | Registration | eventTitle, eventDate, attendeeFirstName, attendeeLastName, attendeeEmail, attendeeCompany, waitlistPosition, registrationDate, createdAt, updatedAt | 44 | hand-written RegistrationResponse (no NON_NULL) for all producers; listUserRegistrations unexercised, same class |
| event-registrations-api | inline /paths/~1events~1{eventCode}~1participants/post/responses/201/content/application~1json/schema | registrationCode, status, attendeeUsername | 4 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-registrations-api | inline /paths/~1registrations~1deregister/post/responses/200/content/application~1json/schema | message | 4 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-registrations-api | inline /paths/~1registrations~1deregister~1by-email/post/responses/200/content/application~1json/schema | message | 4 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-sessions-api | BulkTimingResponse | assignedCount, sessions | 1 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-sessions-api | ConflictAnalysisResponse | hasConflicts, conflictCount, conflicts | 1 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-sessions-api | ConflictDetail | sessionSlug, conflictType, severity, affectedSessions, timeRange, resolution | 1 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-sessions-api | ConflictTimeRange | start, end | 1 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-sessions-api | EventAgendaConfigResponse | typicalStartTime, typicalEndTime | 9 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-sessions-api | QnaPostResponse | parentPostId, postedByUsername, postedByFirstName, postedByLastName, postedByCompanyName, postedByCompanyLogoUrl, body | 7 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-sessions-api | QnaWindowResponse | opensAt, closesAt | 6 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-sessions-api | Session | description, sessionType, startTime, endTime, room, capacity, language, createdAt, updatedAt, speakers, materialsStatus | 154 | generated Session/SessionResponse (no NON_NULL) in every producer; getEventTimetable unexercised, same classes |
| event-sessions-api | SessionImportDetail | sessionSlug | 9 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-sessions-api | SessionMaterialResponse | cloudFrontUrl, contentExtracted, extractionStatus | 6 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-sessions-api | SessionResponse/allOf/1 | materials, materialsCount | 105 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-sessions-api | SessionSpeaker | company, companyDisplayName, profilePictureUrl, companyLogoUrl, bio, presentationTitle | 85 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-sessions-api | TimetableSlot | slotKey, title, slotIndex, sessionSlug, assignedSessionSlug | 56 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-sessions-api | inline /paths/~1events~1{eventCode}~1sessions/get/responses/200/content/application~1json/schema | data, pagination | 5 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-sessions-api | inline /paths/~1sessions/get/responses/200/content/application~1json/schema | data, pagination | 2 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-speakers-api | DashboardUpcomingEventDto | eventCode, eventTitle, eventDate, eventLocation, sessionTitle, workflowState, workflowStateLabel, hasTitle, hasAbstract, hasMaterial, materialFileName, responseDeadline, contentDeadline, reviewerFeedback, organizerName, organizerEmail, respondUrl, contentUrl | 5 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-speakers-api | InviteSpeakerResponse | username, userCreated | 3 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-speakers-api | OutreachHistoryResponse | notes | 5 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-speakers-api | RemindersDisabledResponse | speakerPoolId, remindersDisabled | 2 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-speakers-api | SendInvitationResponse | username, email, contentDeadline | 1 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-speakers-api | SpeakerContentResponse | speakerPoolId, eventId, sessionId, presentationTitle, presentationAbstract, username, speakerName, company, status, hasContent, warning, submittedAt, hasMaterial, materialUrl, materialFileName | 1 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-speakers-api | SpeakerDashboardDto | speakerName, profilePictureUrl, profileCompleteness, upcomingEvents, pastEvents | 4 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-speakers-api | SpeakerPoolResponse | company, companyDisplayName, expertise, assignedOrganizerId, notes, source, proposedByUsername, proposedSessionTitle, proposedAbstract, sessionId, sessionSlug, username, email, invitedAt, responseDeadline, contentDeadline, acceptedAt, declinedAt, declineReason, preferredTimeSlot, travelRequirements, technicalRequirements, initialPresentationTitle, preferenceComments, contentSubmittedAt, submittedTitle, submittedAbstract, isSlotAssigned | 24 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-speakers-api | SpeakerResponseResult | success, speakerName, eventName, nextSteps, contentDeadline | 1 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-speakers-api | SpeakerStatusResponse | speakerId, eventCode, currentStatus, previousStatus, changedByUsername, changeReason, changedAt, isSlotAssigned, isPublishable | 8 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-speakers-api | StatusHistoryItem | id, kind, previousStatus, newStatus, changedByUsername, changeReason, changedAt | 1 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-speakers-api | StatusSummaryResponse | eventCode, statusCounts, totalSpeakers, acceptedCount, declinedCount, pendingCount, acceptanceRate, minSlotsRequired, maxSlotsAllowed, thresholdMet, overflowDetected | 3 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-tasks-api | EventTaskResponse | eventId, eventCode, templateId, dueDate, assignedOrganizerUsername, notes, completedDate, completedByUsername | 12 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-tasks-api | TaskTemplateResponse | dueDateOffsetDays, activationLeadDays, createdByUsername | 24 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-watch-api | ActiveEventDetail | typicalStartTime, typicalEndTime, themeImageUrl, currentPublishedPhase | 5 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-watch-api | LiveTimingResponse | currentSessionSlug | 4 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-watch-api | SpeakerArrivalBroadcast | speakerFirstName, speakerLastName | 4 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-watch-api | WatchSessionDetail | abstract, sessionType, scheduledStartTime, scheduledEndTime, durationMinutes, status, actualStartTime, actualEndTime, overrunMinutes, completedBy | 7 | class without `@JsonInclude(NON_NULL)`: writes every field |
| event-watch-api | WatchSpeakerDetail | firstName, lastName, company, companyLogoUrl, profilePictureUrl, bio, speakerRole | 3 | class without `@JsonInclude(NON_NULL)`: writes every field |
| events-core-api | AdminSettingResponse | key, value | 18 | class without `@JsonInclude(NON_NULL)`: writes every field |
| events-core-api | AttendeeDashboardResponse | attendeeName | 6 | class without `@JsonInclude(NON_NULL)`: writes every field |
| events-core-api | AttendeeEventCardResponse | eventDate, eventLocation, workflowState, registrationStatus | 5 | class without `@JsonInclude(NON_NULL)`: writes every field |
| events-core-api | AutoPublishScheduleResponse | scheduled, phase2Enabled, phase2DaysBeforeEvent, phase2TriggerDate, phase3Enabled, phase3DaysBeforeEvent, phase3TriggerDate | 2 | class without `@JsonInclude(NON_NULL)`: writes every field |
| events-core-api | Event | qnaEnabled, qnaOpenTrigger, qnaWindowDays, createdAt, updatedAt, eventType, workflowState | 233 | NON_NULL Event/EventDetail via EventGeneratedMapper: all from NOT NULL columns; enum conversions total |
| events-core-api | Event.topic | code, name, description, category | 5 | class without `@JsonInclude(NON_NULL)`: writes every field |
| events-core-api | EventSlotConfigurationResponse | moderationStartDuration, moderationEndDuration, breakDuration, lunchDuration, aperitifSlots, aperitifDuration, aperitifPosition | 7 | class without `@JsonInclude(NON_NULL)`: writes every field |
| events-core-api | FeaturedThanksResponse | note, eventCode, thankedByFirstName, thankedByLastName, thankedByCompanyName, thankedByCompanyLogoUrl | 14 | class without `@JsonInclude(NON_NULL)`: writes every field |
| events-core-api | PublicSpeaker | firstName, lastName, company, companyDisplayName, companyLogoUrl, profilePictureUrl, bio, speakerRole, talkTitle, sessionSlug | 26 | class without `@JsonInclude(NON_NULL)`: writes every field |
| events-core-api | PublishPhaseResponse | phase, published, publishedAt, cdnInvalidated | 12 | class without `@JsonInclude(NON_NULL)`: writes every field |
| events-core-api | PublishingStatusResponse | currentPhase | 5 | class without `@JsonInclude(NON_NULL)`: writes every field |
| events-core-api | QnaWindowAdjustResponse | eventCode, windowsAdjusted, status | 3 | class without `@JsonInclude(NON_NULL)`: writes every field |
| events-core-api | ThanksCountResponse | notes | 15 | class without `@JsonInclude(NON_NULL)`: writes every field |
| events-core-api | ThanksNoteResponse | id, note, thankedByUsername, thankedByFirstName, thankedByLastName, thankedByCompanyName, featured, createdAt | 4 | class without `@JsonInclude(NON_NULL)`: writes every field |
| events-core-api | UnassignedSession | sessionSlug, title | 1 | class without `@JsonInclude(NON_NULL)`: writes every field |
| events-core-api | UnpublishPhaseResponse | phase, published, unpublishedAt, cdnInvalidated | 1 | class without `@JsonInclude(NON_NULL)`: writes every field |
| events-core-api | Venue | id, name, address, capacity | 44 | class without `@JsonInclude(NON_NULL)`: writes every field |
| events-core-api | VenueCoordinationPreviewResponse | subject, htmlBody, toEmail, ccEmails, replyToEmail | 3 | class without `@JsonInclude(NON_NULL)`: writes every field |
| events-core-api | VenueCoordinationSendResponse | sentTo | 1 | class without `@JsonInclude(NON_NULL)`: writes every field |
| events-core-api | WorkflowTransitionResponse | updatedAt | 4 | class without `@JsonInclude(NON_NULL)`: writes every field |
| events-core-api | inline /paths/~1events/get/responses/200/content/application~1json/schema | data, pagination | 67 | class without `@JsonInclude(NON_NULL)`: writes every field |
| events-core-api | inline /paths/~1public~1events/get/responses/200/content/application~1json/schema | data, pagination | 12 | class without `@JsonInclude(NON_NULL)`: writes every field |
| partner-meetings-api | MeetingRsvpListResponse | meetingId, inviteSentAt, rsvps, summary | 3 | class without `@JsonInclude(NON_NULL)`: writes every field |
| partner-meetings-api | RsvpDTO | attendeeEmail, status, respondedAt | 12 | class without `@JsonInclude(NON_NULL)`: writes every field |
| partner-meetings-api | RsvpSummary | accepted, declined, tentative | 3 | class without `@JsonInclude(NON_NULL)`: writes every field |
| partners-api | AttendanceSummaryRecord | eventCode, eventTitle, eventDate, totalAttendees, companyAttendees | 6 | class without `@JsonInclude(NON_NULL)`: writes every field |
| partners-api | CompanyInfo | companyName, name, displayName, logoUrl, industry, website | 102 | class without `@JsonInclude(NON_NULL)`: writes every field |
| partners-api | PartnerContactResponse | email, firstName, lastName, profilePictureUrl | 103 | class without `@JsonInclude(NON_NULL)`: writes every field |
| partners-api | PartnerDashboardResponse | attendanceSummary, costPerAttendee | 5 | class without `@JsonInclude(NON_NULL)`: writes every field |
| partners-api | PartnerResponse | partnershipEndDate, company, contacts | 351 | class without `@JsonInclude(NON_NULL)`: writes every field |
| partners-api | PartnerStatistics.tierCounts | BRONZE, SILVER, GOLD, PLATINUM, STRATEGIC | 1 | class without `@JsonInclude(NON_NULL)`: writes every field |
| partners-api | TopicDTO | description, suggestedByCompany, plannedEvent | 38 | class without `@JsonInclude(NON_NULL)`: writes every field |
| topics-api | PartnerTopicGroup | logoUrl | 1 | class without `@JsonInclude(NON_NULL)`: writes every field |
| topics-api | Topic | description, createdDate, lastUsedDate, usageCount, stalenessScore, colorZone, status, similarityScores, active, createdAt, updatedAt, usageHistory | 49 | class without `@JsonInclude(NON_NULL)`: writes every field |
| topics-api | TopicUsageHistory | eventCode, eventDate | 7 | class without `@JsonInclude(NON_NULL)`: writes every field |
| users-api | AdditionalEmail | label, verifiedAt, receiveEventMail | 26 | class without `@JsonInclude(NON_NULL)`: writes every field |
| users-api | Company | id, name, displayName, industry, website | 5 | class without `@JsonInclude(NON_NULL)`: writes every field |
| users-api | CompanyLogoRequestView | requesterName, companyDisplayName, oldLogoUrl, note, decidedBy, decidedAt, reason | 16 | class without `@JsonInclude(NON_NULL)`: writes every field |
| users-api | DuplicateCandidateProfile | firstName, lastName, companyId, createdAt | 14 | class without `@JsonInclude(NON_NULL)`: writes every field |
| users-api | GetOrCreateUserResponse | companyUpdated, cognitoUserId | 50 | class without `@JsonInclude(NON_NULL)`: writes every field |
| users-api | InvitationCredentialsResponse | temporaryPassword | 2 | class without `@JsonInclude(NON_NULL)`: writes every field |
| users-api | PairedWatch | deviceName | 2 | class without `@JsonInclude(NON_NULL)`: writes every field |
| users-api | PairingCodeResponse | pairingCode, expiresAt, hoursUntilExpiry | 3 | class without `@JsonInclude(NON_NULL)`: writes every field |
| users-api | PairingStatusResponse | pairedWatches, pendingCode | 5 | class without `@JsonInclude(NON_NULL)`: writes every field |
| users-api | PendingPairingCode | code, expiresAt | 2 | class without `@JsonInclude(NON_NULL)`: writes every field |
| users-api | PresignedUploadUrl | requiredHeaders | 2 | class without `@JsonInclude(NON_NULL)`: writes every field |
| users-api | ProfileClaimCandidate | companyName, requestPending, placeholderAddress | 4 | class without `@JsonInclude(NON_NULL)`: writes every field |
| users-api | ProfileMergeRequestView | note, decidedBy, decidedAt, reason, preview | 4 | class without `@JsonInclude(NON_NULL)`: writes every field |
| users-api | PublicOrganizerResponse | id, firstName, lastName, bio, profilePictureUrl, companyId | 1 | class without `@JsonInclude(NON_NULL)`: writes every field |
| users-api | PublicUserResponse | firstName, lastName, profilePictureUrl | 2 | class without `@JsonInclude(NON_NULL)`: writes every field |
| users-api | UserMergeProfileSummary | companyId | 6 | class without `@JsonInclude(NON_NULL)`: writes every field |
| users-api | UserResponse | hasCognitoAccount, bio, companyId, profilePictureUrl, active, lastLoginAt, termsAcceptedAt, company, settings, additionalEmails | 190 | class without `@JsonInclude(NON_NULL)`: writes every field |
| users-api | UserSearchResponse | companyId, roles, profilePictureUrl | 27 | class without `@JsonInclude(NON_NULL)`: writes every field |
| users-api | UserSettings | theme, language, timezone, emailNotifications, inAppNotifications, pushNotifications, notificationFrequency, qnaNotificationFrequency, quietHoursStart, quietHoursEnd, profileVisibility, showEmail, showCompany, showActivityHistory, allowMessaging, allowCalendarSync, twoFactorEnabled | 15 | class without `@JsonInclude(NON_NULL)`: writes every field |

## Plan Change Log

## Review Triage Log

Pass 1 (quick lens, 2026-11-10): medium 2, low 6, false 0. All patches. Applied after the 16.8c implementer finished, so builds do not run in parallel.
- medium, patch: `PutMyNewsletterSubscriptionRequest.email` has no `maxLength` after `@Email` left (the plan rule says `@Size` remains). Add 254 and an IT.
- medium, patch: the internal `RecordRsvpRequest.attendeeEmail` and `OpenRequestConversation.requesterEmail` lack the #1102 pattern, which coding-standards says every request carries. `EmailAddressesTest` does not scan `internal/`. Add the pattern and extend the test.
- low, patch: the MSW `idn-email` regex accepts `x@nodot`, unlike `GeneratedCodeSemantics.IDN_EMAIL_SHAPE`. Align it and add a test case.
- low, patch: stale "field selection" wording in the `companies-api` and `users-api` operation descriptions and in `04-api-*` docs.
- low, patch: the stale CUMS `build.gradle` comment about `?fields=`.
- low, patch: new `!` and `as` in the `fixtureDefaults.test.ts` idn test.
- low, patch: the `UserApiClientImplContractTest` test name says "without password" but the stub sends null.
- low, patch: `EmailAddressesTest` labels its count "request fields" although it also counts two responses.

## Design Notes

Work rules on rack:
- One heavy job at a time: never run a Gradle suite, Vitest, Playwright or a `git commit`/`git push` hook in parallel.
- Gradle runs with `--max-workers=4`, Vitest with `--maxWorkers=4`.
- Logs go to `~/.cache/batbern-test-logs/` via `tee`, with `set -o pipefail`.
- Commit on the current branch in conventional-commit steps (tests first where sensible), never `--no-verify`, never push.
- Services are managed only with `make dev-native-*`.

Architecture note:
- **Modules:** specs only, plus CUMS company query and users controller.
- **New dependencies:** none.
- **Endpoints:** none new; one parameter removed from two lists.
- **Reused:** `ResponseConformance`, `ProductionShapedPeople.IDN_EMAIL`.
- **Deviations:** none.

Decisions by the agent under the owner's blanket go (2026-11-09):
- `idn-email` applies to every non-path email property, because each can carry the stored address.
- Built after the core plan, because the `UserSettings` schema changes there.

## Verification

**Commands:**
- `set -o pipefail; ./gradlew :services:company-user-management-service:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/49b-cums.log`: expected BUILD SUCCESSFUL with no stale baseline line. Then EMS, then PCS, one at a time.
- `./gradlew :shared-kernel:test --tests '*EmailAddressesTest*'`: expected green.
- `cd web-frontend && npm run generate:api-types && npm run type-check && npx vitest run --maxWorkers=4`: expected green.
