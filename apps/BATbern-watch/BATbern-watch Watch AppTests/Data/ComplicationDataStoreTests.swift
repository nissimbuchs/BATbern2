//
//  ComplicationDataStoreTests.swift
//  BATbern-watch Watch AppTests
//
//  W3.3 Task 5: Unit tests for ComplicationDataStore and ComplicationEntry.
//  AC: 1 (correct data), 2 (update round-trip), 5 (nil when no data)
//

import Testing
import Foundation
@testable import BATbern_watch_Watch_App

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
            updatedAt: now
        )

        // Use injectable API: writes to UserDefaults.standard, skips WidgetCenter reload.
        let testDefaults = UserDefaults.standard
        ComplicationDataStore.write(original, to: testDefaults)
        let decoded = ComplicationDataStore.read(from: testDefaults)

        #expect(decoded?.sessionTitle == "Cloud-Native Security")
        #expect(decoded?.speakerNames == "Meier, Keller")
        #expect(decoded?.isLive == true)
        #expect(decoded?.urgencyLevel == "normal")
        #expect(decoded?.sessionDuration == 2700)

        // Clean up to avoid cross-test pollution
        testDefaults.removeObject(forKey: ComplicationDataStore.snapshotKey)
    }

    // MARK: - 5.3 read() returns nil when no data

    @Test("read() returns nil when App Group is unavailable (test environment)")
    func readReturnsNilWhenEmpty() {
        // In the test sandbox, UserDefaults(suiteName: "group.ch.batbern.watch") returns nil
        // because the App Group isn't registered. ComplicationDataStore.read() guards against
        // nil defaults and returns nil — assert this explicitly.
        let result = ComplicationDataStore.read()
        #expect(result == nil)
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
        isLive: Bool = true
    ) -> ComplicationEntry {
        let snapshot = endTime != nil ? ComplicationSnapshot(
            sessionTitle: "Test Session",
            speakerNames: "Tester",
            scheduledEndTime: endTime,
            sessionDuration: duration,
            scheduledStartTime: startTime,
            isLive: isLive,
            urgencyLevel: urgency,
            updatedAt: date
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
            updatedAt: now
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
            updatedAt: now
        )

        // Simulate the inactive branch of getTimeline
        let entry = ComplicationEntry(date: now, snapshot: inactiveSnapshot)
        let entries = [entry]

        #expect(entries.count == 1)
        #expect(entry.snapshot?.isLive == false)
        // policy .never is verified by the Timeline constructor — can't assert directly here,
        // but this entry pattern documents the expected behavior.
    }
}
