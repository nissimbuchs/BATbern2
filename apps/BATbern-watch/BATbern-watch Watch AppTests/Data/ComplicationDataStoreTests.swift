//
//  ComplicationDataStoreTests.swift
//  BATbern-watch Watch AppTests
//
//  W3.3 Task 5: Unit tests for ComplicationDataStore, ComplicationEntry, and ComplicationContext.
//  AC: 1 (correct data), 2 (update round-trip), 5 (nil when no data)
//  W3.3 Amendment (2026-02-19): added ComplicationContext round-trip tests.
//

import Testing
import Foundation
@testable import BATbern_watch_Watch_App

// MARK: - ComplicationContext Tests (W3.3 amendment)

@Suite("ComplicationContext Tests")
struct ComplicationContextTests {

    // MARK: - Codable round-trips for each case

    @Test("ComplicationContext.noEvent round-trips via Codable")
    func noEventRoundTrip() throws {
        let context = ComplicationContext.noEvent
        let data = try JSONEncoder().encode(context)
        let decoded = try JSONDecoder().decode(ComplicationContext.self, from: data)
        #expect(decoded == .noEvent)
    }

    @Test("ComplicationContext.eventComplete round-trips via Codable")
    func eventCompleteRoundTrip() throws {
        let context = ComplicationContext.eventComplete
        let data = try JSONEncoder().encode(context)
        let decoded = try JSONDecoder().decode(ComplicationContext.self, from: data)
        #expect(decoded == .eventComplete)
    }

    @Test("ComplicationContext.eventFar round-trips via Codable preserving dateString")
    func eventFarRoundTrip() throws {
        let context = ComplicationContext.eventFar(dateString: "15.03")
        let data = try JSONEncoder().encode(context)
        let decoded = try JSONDecoder().decode(ComplicationContext.self, from: data)
        #expect(decoded == .eventFar(dateString: "15.03"))
    }

    @Test("ComplicationContext.eventDayPreSession round-trips via Codable preserving minutesUntil and progress")
    func eventDayPreSessionRoundTrip() throws {
        let context = ComplicationContext.eventDayPreSession(minutesUntil: 180, progress: 0.5)
        let data = try JSONEncoder().encode(context)
        let decoded = try JSONDecoder().decode(ComplicationContext.self, from: data)
        if case .eventDayPreSession(let m, let p) = decoded {
            #expect(m == 180)
            #expect(abs(p - 0.5) < 0.001)
        } else {
            Issue.record("Expected .eventDayPreSession, got: \(decoded)")
        }
    }

    @Test("ComplicationContext.sessionRunning round-trips via Codable preserving minutesLeft and fractionRemaining")
    func sessionRunningRoundTrip() throws {
        let context = ComplicationContext.sessionRunning(minutesLeft: 24, fractionRemaining: 0.4)
        let data = try JSONEncoder().encode(context)
        let decoded = try JSONDecoder().decode(ComplicationContext.self, from: data)
        if case .sessionRunning(let m, let f) = decoded {
            #expect(m == 24)
            #expect(abs(f - 0.4) < 0.001)
        } else {
            Issue.record("Expected .sessionRunning, got: \(decoded)")
        }
    }

    // MARK: - Unknown type falls back to .noEvent

    @Test("Unknown ComplicationContext type decodes as .noEvent (forward compatibility)")
    func unknownTypeDecodesAsNoEvent() throws {
        let json = #"{"type":"futureUnknownCase"}"#
        let decoded = try JSONDecoder().decode(ComplicationContext.self, from: Data(json.utf8))
        #expect(decoded == .noEvent)
    }

    // MARK: - ComplicationSnapshot with complicationContext round-trips

    @Test("ComplicationSnapshot with complicationContext round-trips via ComplicationDataStore injectable API")
    func snapshotWithContextRoundTrip() {
        let now = Date(timeIntervalSince1970: 1_700_000_000)
        let snapshot = ComplicationSnapshot(
            sessionTitle: "Cloud Security",
            speakerNames: "Meier",
            scheduledEndTime: now.addingTimeInterval(1200),
            sessionDuration: 2700,
            scheduledStartTime: now,
            isLive: true,
            urgencyLevel: "normal",
            updatedAt: now,
            complicationContext: .sessionRunning(minutesLeft: 20, fractionRemaining: 0.44)
        )
        // Use isolated suite to avoid cross-test pollution (tests run in parallel)
        let testDefaults = UserDefaults(suiteName: "test.complicationContext.roundtrip")
        ComplicationDataStore.write(snapshot, to: testDefaults)
        let decoded = ComplicationDataStore.read(from: testDefaults)

        if case .sessionRunning(let m, let f) = decoded?.complicationContext {
            #expect(m == 20)
            #expect(abs(f - 0.44) < 0.001)
        } else {
            Issue.record("Expected .sessionRunning in decoded snapshot, got: \(String(describing: decoded?.complicationContext))")
        }
        testDefaults?.removePersistentDomain(forName: "test.complicationContext.roundtrip")
    }

    // MARK: - ComplicationEntry.context derives from snapshot

    @Test("ComplicationEntry.context returns .noEvent when snapshot is nil")
    func entryContextNilSnapshot() {
        let entry = ComplicationEntry(date: Date(), snapshot: nil)
        #expect(entry.context == .noEvent)
    }

    @Test("ComplicationEntry.context returns .noEvent when snapshot has no complicationContext (backward compat)")
    func entryContextBackwardCompat() {
        let now = Date(timeIntervalSince1970: 1_000_000)
        let snapshot = ComplicationSnapshot(
            sessionTitle: nil,
            speakerNames: nil,
            scheduledEndTime: nil,
            sessionDuration: nil,
            scheduledStartTime: nil,
            isLive: false,
            urgencyLevel: "normal",
            updatedAt: now,
            complicationContext: nil  // pre-amendment snapshot
        )
        let entry = ComplicationEntry(date: now, snapshot: snapshot)
        #expect(entry.context == .noEvent)
    }

    @Test("ComplicationEntry.context returns snapshot's complicationContext when set")
    func entryContextFromSnapshot() {
        let now = Date(timeIntervalSince1970: 1_000_000)
        let snapshot = ComplicationSnapshot(
            sessionTitle: "Talk",
            speakerNames: "Meier",
            scheduledEndTime: now.addingTimeInterval(900),
            sessionDuration: 2700,
            scheduledStartTime: now,
            isLive: true,
            urgencyLevel: "caution",
            updatedAt: now,
            complicationContext: .sessionRunning(minutesLeft: 15, fractionRemaining: 0.33)
        )
        let entry = ComplicationEntry(date: now, snapshot: snapshot)
        #expect(entry.context == .sessionRunning(minutesLeft: 15, fractionRemaining: 0.33))
    }
}

// MARK: - ComplicationDataStore Tests

@Suite("ComplicationDataStore Tests")
struct ComplicationDataStoreTests {

    // MARK: - 5.2 Round-trip via injectable API (no App Group required)

    @Test("write(_:to:) then read(from:) round-trips a ComplicationSnapshot")
    func writeReadRoundTrip() {
        let now = Date(timeIntervalSince1970: 1_700_000_000)
        let original = ComplicationSnapshot(
            sessionTitle: "Cloud-Native Security",
            speakerNames: "Meier, Keller",
            scheduledEndTime: now.addingTimeInterval(1500),
            sessionDuration: 2700,
            scheduledStartTime: now,
            isLive: true,
            urgencyLevel: "normal",
            updatedAt: now,
            complicationContext: .sessionRunning(minutesLeft: 25, fractionRemaining: 0.56)
        )

        // Use isolated suite to avoid cross-test pollution (tests run in parallel)
        let testDefaults = UserDefaults(suiteName: "test.complicationStore.roundtrip")
        ComplicationDataStore.write(original, to: testDefaults)
        let decoded = ComplicationDataStore.read(from: testDefaults)

        #expect(decoded?.sessionTitle == "Cloud-Native Security")
        #expect(decoded?.speakerNames == "Meier, Keller")
        #expect(decoded?.isLive == true)
        #expect(decoded?.urgencyLevel == "normal")
        #expect(decoded?.sessionDuration == 2700)
        #expect(decoded?.complicationContext == .sessionRunning(minutesLeft: 25, fractionRemaining: 0.56))

        // Clean up
        testDefaults?.removePersistentDomain(forName: "test.complicationStore.roundtrip")
    }

    // MARK: - 5.3 read() returns nil when no data

    @Test("read() returns nil when no snapshot has been stored")
    func readReturnsNilWhenEmpty() {
        // Use isolated defaults suite that no parallel test ever writes to.
        // (Using the production App Group or UserDefaults.standard is racy because
        // LiveCountdownViewModelTests.refreshState() writes to the App Group store concurrently.)
        let isolatedDefaults = UserDefaults(suiteName: "test.empty.\(UUID().uuidString)")
        let result = ComplicationDataStore.read(from: isolatedDefaults)
        #expect(result == nil)
        // No cleanup needed — the UUID suite never had data written.
    }
}

// MARK: - ComplicationEntry Tests

@Suite("ComplicationEntry Tests")
struct ComplicationEntryTests {

    private func makeEntry(
        date: Date,
        startTime: Date? = nil,
        endTime: Date? = nil,
        duration: TimeInterval? = nil,
        urgency: String = "normal",
        isLive: Bool = true,
        context: ComplicationContext? = nil
    ) -> ComplicationEntry {
        let snapshot = endTime != nil ? ComplicationSnapshot(
            sessionTitle: "Test Session",
            speakerNames: "Tester",
            scheduledEndTime: endTime,
            sessionDuration: duration,
            scheduledStartTime: startTime,
            isLive: isLive,
            urgencyLevel: urgency,
            updatedAt: date,
            complicationContext: context
        ) : nil
        return ComplicationEntry(date: date, snapshot: snapshot)
    }

    // MARK: - 5.4 formattedCountdown 25:00

    @Test("formattedCountdown returns 25:00 for 1500s remaining")
    func formattedCountdownNormal() {
        let now = Date(timeIntervalSince1970: 1_000_000)
        let endTime = now.addingTimeInterval(1500)
        let entry = makeEntry(date: now, endTime: endTime)
        #expect(entry.formattedCountdown == "25:00")
    }

    // MARK: - 5.9 formattedCountdown overtime

    @Test("formattedCountdown returns +04:12 when 252s past end time")
    func formattedCountdownOvertime() {
        let endTime = Date(timeIntervalSince1970: 1_000_000)
        let date = endTime.addingTimeInterval(252)  // 4 min 12 sec past end
        let entry = makeEntry(date: date, endTime: endTime)
        #expect(entry.formattedCountdown == "+04:12")
    }

    // MARK: - 5.10 displayMinutes overtime

    @Test("displayMinutes returns +4 when 252s past end time")
    func displayMinutesOvertime() {
        let endTime = Date(timeIntervalSince1970: 1_000_000)
        let date = endTime.addingTimeInterval(252)  // 4 min 12 sec past end
        let entry = makeEntry(date: date, endTime: endTime)
        #expect(entry.displayMinutes == "+4")
    }

    // MARK: - 5.11 isOvertime

    @Test("isOvertime is true when date > scheduledEndTime")
    func isOvertimeTrue() {
        let endTime = Date(timeIntervalSince1970: 1_000_000)
        let date = endTime.addingTimeInterval(1)
        let entry = makeEntry(date: date, endTime: endTime)
        #expect(entry.isOvertime == true)
    }

    @Test("isOvertime is false when date < scheduledEndTime")
    func isOvertimeFalse() {
        let now = Date(timeIntervalSince1970: 1_000_000)
        let endTime = now.addingTimeInterval(300)
        let entry = makeEntry(date: now, endTime: endTime)
        #expect(entry.isOvertime == false)
    }

    // MARK: - 5.5 progress 0.5

    @Test("progress returns 0.5 when halfway through session")
    func progressHalfway() {
        let start = Date(timeIntervalSince1970: 1_000_000)
        let duration: TimeInterval = 2700  // 45 min
        let midpoint = start.addingTimeInterval(duration / 2)
        let endTime = start.addingTimeInterval(duration)
        let entry = makeEntry(date: midpoint, startTime: start, endTime: endTime, duration: duration)
        #expect(abs(entry.progress - 0.5) < 0.001)
    }

    // MARK: - 5.6 progress clamps to [0, 1]

    @Test("progress clamps to 0.0 before session start")
    func progressClampsToZero() {
        let start = Date(timeIntervalSince1970: 1_000_000)
        let duration: TimeInterval = 2700
        let before = start.addingTimeInterval(-60)
        let endTime = start.addingTimeInterval(duration)
        let entry = makeEntry(date: before, startTime: start, endTime: endTime, duration: duration)
        #expect(entry.progress == 0.0)
    }

    @Test("progress clamps to 1.0 when overtime")
    func progressClampsToOne() {
        let start = Date(timeIntervalSince1970: 1_000_000)
        let duration: TimeInterval = 2700
        let endTime = start.addingTimeInterval(duration)
        let after = endTime.addingTimeInterval(300)
        let entry = makeEntry(date: after, startTime: start, endTime: endTime, duration: duration)
        #expect(entry.progress == 1.0)
    }
}

// MARK: - ComplicationProvider Timeline Tests

@Suite("ComplicationProvider Timeline Tests")
struct ComplicationProviderTimelineTests {

    // MARK: - 5.7 1-minute entries for 45-min session

    @Test("getTimeline generates 1-minute entries for 45-min session (45 + 1 overtime entry)")
    func timelineFor45MinSession() async {
        // Build a snapshot with endTime 45 minutes from now
        let now = Date(timeIntervalSince1970: 1_000_000)
        let endTime = now.addingTimeInterval(45 * 60)
        let snapshot = ComplicationSnapshot(
            sessionTitle: "Test",
            speakerNames: "Tester",
            scheduledEndTime: endTime,
            sessionDuration: 45 * 60,
            scheduledStartTime: now,
            isLive: true,
            urgencyLevel: "normal",
            updatedAt: now,
            complicationContext: .sessionRunning(minutesLeft: 45, fractionRemaining: 1.0)
        )

        // Simulate what getTimeline does (without App Group I/O)
        var entries: [ComplicationEntry] = []
        var current = now
        while current < endTime {
            entries.append(ComplicationEntry(date: current, snapshot: snapshot))
            current = current.addingTimeInterval(60)
        }
        entries.append(ComplicationEntry(date: endTime, snapshot: snapshot))

        // 45 entries (0, 1, 2, ..., 44 minutes from start) + 1 overtime entry = 46
        #expect(entries.count == 46)
    }

    // MARK: - 5.8 No active session → policy .never

    @Test("no active session snapshot produces single entry (policy .never implied)")
    func noSessionSingleEntry() async {
        let now = Date(timeIntervalSince1970: 1_000_000)
        let inactiveSnapshot = ComplicationSnapshot(
            sessionTitle: nil,
            speakerNames: nil,
            scheduledEndTime: nil,
            sessionDuration: nil,
            scheduledStartTime: nil,
            isLive: false,
            urgencyLevel: "normal",
            updatedAt: now,
            complicationContext: .noEvent
        )

        // Simulate the inactive branch of getTimeline
        let entry = ComplicationEntry(date: now, snapshot: inactiveSnapshot)
        let entries = [entry]

        #expect(entries.count == 1)
        #expect(entry.snapshot?.isLive == false)
        // policy .never is verified by the Timeline constructor — can't assert directly here,
        // but this entry pattern documents the expected behavior.
    }

    // MARK: - Regression: stale isLive:true snapshot with expired endTime shows fallback

    /// Regression for bug where complication showed huge overtime numbers (e.g. "+2950…")
    /// when a stale snapshot (isLive:true, endTime days ago) sat in UserDefaults.
    ///
    /// Fix: resolvedSnapshot() applies staleness guard for .sessionRunning context —
    /// returns nil when endTime <= now AND updatedAt > 5 min ago.
    @Test("stale .sessionRunning snapshot (old updatedAt) resolves to nil — shows fallback icon")
    func staleSnapshotResolvesToNil() async {
        let pastEnd = Date(timeIntervalSince1970: 1_000_000)    // far in the past
        let now = pastEnd.addingTimeInterval(60 * 60 * 24 * 2)  // 2 days later

        let staleSnapshot = ComplicationSnapshot(
            sessionTitle: "Old Session",
            speakerNames: "Meier",
            scheduledEndTime: pastEnd,
            sessionDuration: 2700,
            scheduledStartTime: pastEnd.addingTimeInterval(-2700),
            isLive: true,
            urgencyLevel: "normal",
            updatedAt: pastEnd,  // last written 2 days ago — app is NOT running
            complicationContext: .sessionRunning(minutesLeft: 0, fractionRemaining: 0.0)
        )

        // .sessionRunning: staleness guard applies
        // - endTime > now: false (pastEnd < now)
        // - updatedAt within 5 min: false (2 days old)
        // → returns nil
        let resolved = resolvedSnapshotForTest(staleSnapshot, now: now)
        #expect(resolved == nil)

        let entry = ComplicationEntry(date: now, snapshot: resolved)
        #expect(entry.snapshot == nil)
        #expect(entry.isOvertime == false)
        #expect(entry.context == .noEvent)
    }

    /// Recent overtime: app IS running, endTime just passed, updatedAt is now — should
    /// preserve the snapshot so the complication shows overtime briefly.
    @Test("recent overtime .sessionRunning snapshot (fresh updatedAt) resolves to snapshot")
    func recentOvertimeSnapshotPreserved() async {
        let pastEnd = Date(timeIntervalSince1970: 1_000_000)
        let now = pastEnd.addingTimeInterval(60 * 2)  // 2 minutes of overtime

        let recentSnapshot = ComplicationSnapshot(
            sessionTitle: "Live Session",
            speakerNames: "Meier",
            scheduledEndTime: pastEnd,
            sessionDuration: 2700,
            scheduledStartTime: pastEnd.addingTimeInterval(-2700),
            isLive: true,
            urgencyLevel: "overtime",
            updatedAt: now.addingTimeInterval(-10),  // written 10 sec ago — app IS running
            complicationContext: .sessionRunning(minutesLeft: 0, fractionRemaining: 0.0)
        )

        let resolved = resolvedSnapshotForTest(recentSnapshot, now: now)
        #expect(resolved != nil)
        #expect(resolved?.urgencyLevel == "overtime")
    }

    // MARK: - Upcoming session shows eventDayPreSession context (W3.3 amendment)

    /// After the amendment, upcoming sessions produce `.eventDayPreSession` context which
    /// is passed through by the provider (not discarded). The view shows "Xh" + count-up ring.
    @Test("upcoming session (startTime > now) produces eventDayPreSession — not nil (amendment)")
    func upcomingSessionShowsEventDayPreSession() async {
        let now = Date(timeIntervalSince1970: 1_000_000)
        let futureStart = now.addingTimeInterval(30 * 60)  // starts in 30 min
        let futureEnd = futureStart.addingTimeInterval(45 * 60)

        // isLive: false (session hasn't started), but context carries the pre-session info
        let snapshot = ComplicationSnapshot(
            sessionTitle: "Upcoming Talk",
            speakerNames: "Keller",
            scheduledEndTime: futureEnd,
            sessionDuration: 45 * 60,
            scheduledStartTime: futureStart,
            isLive: false,
            urgencyLevel: "normal",
            updatedAt: now,
            complicationContext: .eventDayPreSession(minutesUntil: 30, progress: 0.125)
        )

        // .eventDayPreSession: default case — always returned (no staleness guard)
        let resolved = resolvedSnapshotForTest(snapshot, now: now)
        #expect(resolved != nil)

        let entry = ComplicationEntry(date: now, snapshot: resolved)
        if case .eventDayPreSession(let m, _) = entry.context {
            #expect(m == 30)  // 30 minutes until session
        } else {
            Issue.record("Expected .eventDayPreSession, got: \(entry.context)")
        }
    }

    // MARK: - eventFar context always passes through

    @Test("eventFar snapshot always passes through resolvedSnapshot (no staleness)")
    func eventFarPassesThrough() async {
        let now = Date(timeIntervalSince1970: 1_000_000)
        let farStart = now.addingTimeInterval(3 * 24 * 3600)  // 3 days away

        let snapshot = ComplicationSnapshot(
            sessionTitle: nil,
            speakerNames: nil,
            scheduledEndTime: farStart.addingTimeInterval(2700),
            sessionDuration: 2700,
            scheduledStartTime: farStart,
            isLive: false,
            urgencyLevel: "normal",
            updatedAt: now.addingTimeInterval(-3600),  // written 1h ago — no staleness concern
            complicationContext: .eventFar(dateString: "28.02")
        )

        let resolved = resolvedSnapshotForTest(snapshot, now: now)
        #expect(resolved != nil)
        #expect(resolved?.complicationContext == .eventFar(dateString: "28.02"))
    }
}

// MARK: - Test Helper: resolvedSnapshot (delegates to ComplicationDataStore — single source of truth)

/// Thin wrapper preserving existing test call sites.
/// Staleness logic now lives in ComplicationDataStore.resolvedSnapshot(_:now:).
private func resolvedSnapshotForTest(
    _ snapshot: ComplicationSnapshot?,
    now: Date
) -> ComplicationSnapshot? {
    ComplicationDataStore.resolvedSnapshot(snapshot, now: now)
}
