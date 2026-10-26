//
//  PublicEventServiceTests.swift
//  BATbern-watch Watch AppTests
//
//  Tests for PublicEventService: DTO mapping, and request/decoding through a
//  stubbed URLSession (StubURLProtocol) against a production-shaped payload.
//

import Testing
import Foundation
@testable import BATbern_watch_Watch_App

@Suite("PublicEventService Tests")
struct PublicEventServiceTests {
    @Test("DTO mapping: EventDetail correctly converts to WatchEvent")
    func test_dtoMapping_eventDetailToWatchEvent() {
        // Given: EventDetail DTO (generated type)
        let dto = EventDetail(
            eventCode: "BATbern57",
            title: "Test Event",
            eventNumber: 57,
            date: Date(timeIntervalSince1970: 1742040000), // 2025-03-15
            registrationDeadline: Date(timeIntervalSince1970: 1742040000),
            venueName: "Bern",
            venueAddress: "Street 1",
            venueCapacity: 100,
            organizerUsername: "test.organizer",
            themeImageUrl: "https://example.com/theme.jpg",
            typicalStartTime: "18:00",
            typicalEndTime: "22:00",
            currentPublishedPhase: .agenda
        )

        // When: Converting to WatchEvent
        let watchEvent = dto.toWatchEvent()

        // Then: Conversion is correct
        #expect(watchEvent.id == "BATbern57", "Event code should match")
        #expect(watchEvent.title == "Test Event", "Title should match")
        #expect(watchEvent.venueName == "Bern", "Venue should match")
        #expect(watchEvent.currentPublishedPhase == "AGENDA", "Phase should match")
    }

    @Test("DTO mapping: EventDetail with sessions converts correctly")
    func test_dtoMapping_eventDetailWithSessions() {
        // Given: Session DTO with speaker (generated types)
        let sessionDTO = SessionResponse(
            sessionSlug: "keynote-1",
            eventCode: "BATbern57",
            title: "Opening Keynote",
            description: "Welcome to BATbern",
            sessionType: .keynote,
            startTime: Date(timeIntervalSince1970: 1742040000),
            endTime: Date(timeIntervalSince1970: 1742043600),
            speakers: [
                SessionSpeaker(
                    username: "jane.doe",
                    firstName: "Jane",
                    lastName: "Doe",
                    company: "TechCorp",
                    profilePictureUrl: "https://example.com/jane.jpg",
                    bio: "Expert speaker",
                    speakerRole: .primarySpeaker,
                    isConfirmed: true
                )
            ]
        )

        let dto = EventDetail(
            eventCode: "BATbern57",
            title: "Test Event",
            eventNumber: 57,
            date: Date(timeIntervalSince1970: 1742040000),
            registrationDeadline: Date(timeIntervalSince1970: 1742040000),
            venueName: "Bern",
            venueAddress: "Street 1",
            venueCapacity: 100,
            organizerUsername: "test.organizer",
            typicalStartTime: "18:00",
            typicalEndTime: "22:00",
            sessions: [sessionDTO],
            currentPublishedPhase: .agenda
        )

        // When: Converting to WatchEvent
        let watchEvent = dto.toWatchEvent()

        // Then: Sessions are converted correctly
        #expect(watchEvent.sessions.count == 1, "Should have one session")
        #expect(watchEvent.sessions[0].title == "Opening Keynote", "Session title should match")
        #expect(watchEvent.sessions[0].speakers.count == 1, "Should have one speaker")
        #expect(watchEvent.sessions[0].speakers[0].fullName == "Jane Doe", "Speaker name should match")
    }
}

// MARK: - Public read model (GET /api/v1/public/events/*) — #1070, #848

/// Synthetic payload with the exact shape `GET /api/v1/public/events/current` returns in
/// production (captured from BATbern60, 2026-10-26; names and text replaced). Keeps the traits
/// that matter for decoding: fractional-second timestamps, top-level `speakers[]` (PublicSpeaker
/// with `talkTitle`), `materials: []`, null logo URLs, and the apéro as `networking` (V127, #1123;
/// before that production sent `aperitif`, which the 2.3 closed enum could not decode).
private let publicCurrentEventJSON = """
{
  "eventCode": "BATbern60", "title": "Digital Sovereignty", "eventNumber": 60,
  "date": "2026-11-06T00:00:00Z", "registrationDeadline": "2026-11-05T00:00:00Z",
  "venueName": "Zentrum Paul Klee", "venueAddress": "Monument im Fruchtland 3, 3006 Bern",
  "venueCapacity": 200, "registrationCapacity": 200, "qnaEnabled": true,
  "qnaOpenTrigger": "SPEAKERS_PUBLISHED", "qnaWindowDays": 14, "confirmedCount": 120,
  "waitlistCount": 0, "spotsRemaining": 80, "realAttendeeCount": 0,
  "organizerUsername": "test.organizer", "currentAttendeeCount": 120,
  "description": "An evening on digital sovereignty.",
  "createdAt": "2026-02-26T06:22:03.968837Z", "updatedAt": "2026-10-12T19:05:17.468031Z",
  "updatedBy": "test.organizer",
  "themeImageUrl": null, "teaserImages": [], "topicCode": "digital-sovereignty",
  "eventType": "EVENING", "typicalStartTime": "16:00", "typicalEndTime": "19:00",
  "workflowState": "AGENDA_PUBLISHED",
  "topic": {"code": "digital-sovereignty", "name": "Digital Sovereignty", "description": null, "category": "technical"},
  "venue": {"id": "BATbern60", "name": "Zentrum Paul Klee", "address": "Monument im Fruchtland 3, 3006 Bern", "capacity": 200},
  "currentPublishedPhase": "AGENDA",
  "speakers": [
    {"username": "anna.muster", "firstName": "Anna", "lastName": "Muster", "company": "acme",
     "companyDisplayName": "ACME AG", "companyLogoUrl": null, "profilePictureUrl": null,
     "bio": "Synthetic bio.", "speakerRole": "PRIMARY_SPEAKER", "talkTitle": "Less Cloud, More Clarity",
     "sessionSlug": "batbern60-anna-muster"}
  ],
  "sessions": [
    {"sessionSlug": "moderation-start-1", "eventCode": "BATbern60", "title": "Welcome",
     "sessionType": "moderation", "startTime": "2026-11-06T15:00:00Z", "endTime": "2026-11-06T15:05:00Z",
     "room": "Main Hall", "capacity": null, "language": "de",
     "createdAt": "2026-09-09T17:49:40.756177Z", "updatedAt": "2026-10-12T19:05:17.468031Z",
     "materialsStatus": "NONE", "materials": [], "materialsCount": 0,
     "speakers": [
       {"username": "max.moderator", "firstName": "Max", "lastName": "Moderator", "company": "acme",
        "companyDisplayName": "ACME AG", "companyLogoUrl": null, "profilePictureUrl": null, "bio": null,
        "speakerRole": "MODERATOR", "presentationTitle": null, "isConfirmed": true}
     ]},
    {"sessionSlug": "batbern60-anna-muster", "eventCode": "BATbern60", "title": "Less Cloud, More Clarity",
     "description": "Synthetic abstract.",
     "sessionType": "presentation", "startTime": "2026-11-06T15:05:00Z", "endTime": "2026-11-06T15:40:00Z",
     "room": "Main Hall", "capacity": null, "language": "de",
     "createdAt": "2026-09-09T17:49:40.756177Z", "updatedAt": "2026-10-12T19:05:17.468031Z",
     "materialsStatus": "NONE", "materials": [], "materialsCount": 0,
     "speakers": [
       {"username": "anna.muster", "firstName": "Anna", "lastName": "Muster", "company": "acme",
        "companyDisplayName": "ACME AG", "companyLogoUrl": null,
        "profilePictureUrl": "https://cdn.example.com/anna.png", "bio": "Synthetic bio.",
        "speakerRole": "PRIMARY_SPEAKER", "presentationTitle": "Less Cloud, More Clarity", "isConfirmed": true},
       {"username": "ben.beispiel", "firstName": "Ben", "lastName": "Beispiel", "company": "acme",
        "companyDisplayName": "ACME AG", "companyLogoUrl": null, "profilePictureUrl": null, "bio": null,
        "speakerRole": "CO_SPEAKER", "presentationTitle": null, "isConfirmed": false}
     ]},
    {"sessionSlug": "pause-1", "eventCode": "BATbern60", "title": "Break",
     "sessionType": "break", "startTime": "2026-11-06T15:40:00Z", "endTime": "2026-11-06T16:00:00Z",
     "room": null, "capacity": null, "language": "de",
     "createdAt": "2026-09-09T17:49:40.756177Z", "updatedAt": "2026-10-12T19:05:17Z",
     "materialsStatus": "NONE", "materials": [], "materialsCount": 0, "speakers": []},
    {"sessionSlug": "apero-1", "eventCode": "BATbern60", "title": "Apéro",
     "sessionType": "networking", "startTime": "2026-11-06T17:50:00Z", "endTime": "2026-11-06T19:00:00Z",
     "room": null, "capacity": null, "language": "de",
     "createdAt": "2026-09-09T17:49:40.756177Z", "updatedAt": "2026-10-12T19:05:17Z",
     "materialsStatus": "NONE", "materials": [], "materialsCount": 0, "speakers": []}
  ]
}
"""

@Suite("PublicEventService — public read model")
struct PublicEventServicePublicReadModelTests {

    @Test("fetchCurrentEvent calls GET /api/v1/public/events/current without include params")
    func test_fetchCurrentEvent_requestsPublicCurrentEndpoint() async throws {
        let host = "current-path.test"
        let service = PublicEventService(
            baseURL: "https://\(host)",
            session: StubURLProtocol.session(host: host, body: publicCurrentEventJSON)
        )

        _ = try await service.fetchCurrentEvent()

        let requests = StubURLProtocol.receivedRequests(host: host)
        #expect(requests.count == 1)
        #expect(requests.first?.url?.path == "/api/v1/public/events/current")
        #expect(requests.first?.url?.query == nil)
    }

    @Test("fetchCurrentEvent decodes the production-shaped payload, including the apéro slot")
    func test_fetchCurrentEvent_decodesProductionShapedPayload() async throws {
        let host = "current-decode.test"
        let service = PublicEventService(
            baseURL: "https://\(host)",
            session: StubURLProtocol.session(host: host, body: publicCurrentEventJSON)
        )

        let event = try await service.fetchCurrentEvent()

        #expect(event.id == "BATbern60")
        #expect(event.currentPublishedPhase == "AGENDA")
        #expect(event.sessions.map(\.id) == ["moderation-start-1", "batbern60-anna-muster", "pause-1", "apero-1"])
        #expect(event.sessions.map(\.sessionType) == [.moderation, .presentation, .breakTime, .networking])
        let talk = event.sessions[1]
        #expect(talk.abstract == "Synthetic abstract.")
        #expect(talk.speakers.map(\.id) == ["anna.muster", "ben.beispiel"])
        #expect(talk.speakers.map(\.speakerRole) == [.primarySpeaker, .coSpeaker])
        #expect(talk.speakers[0].profilePictureUrl == "https://cdn.example.com/anna.png")
    }

    @Test("the apéro (networking) slot counts as a break so the break card and gong show for it")
    func test_aperoSession_isBreak() async throws {
        let host = "apero-break.test"
        let service = PublicEventService(
            baseURL: "https://\(host)",
            session: StubURLProtocol.session(host: host, body: publicCurrentEventJSON)
        )

        let event = try await service.fetchCurrentEvent()

        let apero = try #require(event.sessions.first { $0.id == "apero-1" })
        #expect(apero.isBreak)
    }

    @Test("an unknown session type keeps the session (timed, as a talk) instead of dropping it")
    func test_unknownSessionType_keepsSession() async throws {
        let host = "unknown-type.test"
        let body = publicCurrentEventJSON.replacingOccurrences(of: "\"sessionType\": \"networking\"", with: "\"sessionType\": \"fireside_chat\"")
        let service = PublicEventService(
            baseURL: "https://\(host)",
            session: StubURLProtocol.session(host: host, body: body)
        )

        let event = try await service.fetchCurrentEvent()

        let slot = try #require(event.sessions.first { $0.id == "apero-1" })
        #expect(slot.sessionType == .presentation)
    }

    @Test("an unknown materials status does not fail the event decode (#1126)")
    func test_unknownMaterialsStatus_stillDecodes() async throws {
        let host = "unknown-materials.test"
        let body = publicCurrentEventJSON.replacingOccurrences(of: "\"materialsStatus\": \"NONE\"", with: "\"materialsStatus\": \"FUTURE_STATE\"")
        let service = PublicEventService(
            baseURL: "https://\(host)",
            session: StubURLProtocol.session(host: host, body: body)
        )

        let event = try await service.fetchCurrentEvent()

        #expect(event.sessions.count == 4)
    }

    @Test("fetchCurrentEvent maps 404 to noCurrentEvent")
    func test_fetchCurrentEvent_404_throwsNoCurrentEvent() async {
        let host = "current-404.test"
        let service = PublicEventService(
            baseURL: "https://\(host)",
            session: StubURLProtocol.session(host: host, statusCode: 404, body: "{}")
        )

        do {
            _ = try await service.fetchCurrentEvent()
            Issue.record("Expected APIError.noCurrentEvent")
        } catch APIError.noCurrentEvent {
            // expected
        } catch {
            Issue.record("Expected APIError.noCurrentEvent, got \(error)")
        }
    }

    @Test("server unreachable surfaces as APIError.networkError, so the controller goes offline (#1122)")
    func test_fetchCurrentEvent_transportFailure_throwsNetworkError() async {
        let host = "unreachable.test"
        let service = PublicEventService(
            baseURL: "https://\(host)",
            session: StubURLProtocol.session(host: host, failingWith: URLError(.cannotConnectToHost))
        )

        do {
            _ = try await service.fetchCurrentEvent()
            Issue.record("Expected APIError.networkError")
        } catch APIError.networkError(let underlying) {
            // URLSession may rewrite the code (-1004 surfaces as -1003), so only the type is stable.
            #expect(underlying is URLError)
        } catch {
            Issue.record("Expected APIError.networkError, got \(type(of: error)): \(error)")
        }
    }

    @Test("fetchEvent(code:) maps a transport failure to APIError.networkError (#1122)")
    func test_fetchEvent_transportFailure_throwsNetworkError() async {
        let host = "unreachable-code.test"
        let service = PublicEventService(
            baseURL: "https://\(host)",
            session: StubURLProtocol.session(host: host, failingWith: URLError(.timedOut))
        )

        do {
            _ = try await service.fetchEvent(code: "BATbern60")
            Issue.record("Expected APIError.networkError")
        } catch APIError.networkError(let underlying) {
            #expect(underlying is URLError)
        } catch {
            Issue.record("Expected APIError.networkError, got \(type(of: error)): \(error)")
        }
    }

    @Test("fetchEvent(code:) calls GET /api/v1/public/events/{code}")
    func test_fetchEvent_requestsPublicEventEndpoint() async throws {
        let host = "event-path.test"
        let service = PublicEventService(
            baseURL: "https://\(host)",
            session: StubURLProtocol.session(host: host, body: publicCurrentEventJSON)
        )

        let event = try await service.fetchEvent(code: "BATbern60")

        let requests = StubURLProtocol.receivedRequests(host: host)
        #expect(requests.first?.url?.path == "/api/v1/public/events/BATbern60")
        #expect(requests.first?.url?.query == nil)
        #expect(event.sessions.count == 4)
    }
}
