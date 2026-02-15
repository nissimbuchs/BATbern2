import Testing
import Foundation
@testable import BATbern_watch_Watch_App

/// Smoke tests verifying the test infrastructure works.
@Suite("Test Infrastructure")
struct TestInfrastructureTests {

    @Test("MockClock provides controllable time")
    func mockClockWorks() {
        let clock = MockClock(fixedDate: Date(timeIntervalSince1970: 1_000_000))
        let initial = clock.now

        clock.advance(by: 60)

        #expect(clock.now.timeIntervalSince(initial) == 60)
    }

    @Test("TestData factory creates valid event")
    func factoryCreatesEvent() {
        let event = TestData.event()

        #expect(event.id == "bat-2026-spring")
        #expect(event.title == "BATbern Spring 2026")
        #expect(!event.sessions.isEmpty)
    }

    @Test("TestData factory supports overrides")
    func factoryOverrides() {
        let speaker = TestData.speaker(
            firstName: "Marco",
            lastName: "Weber",
            arrived: true
        )

        #expect(speaker.fullName == "Marco Weber")
        #expect(speaker.arrived == true)
        #expect(speaker.arrivedConfirmedBy == "nissim")
    }

    @Test("MockHapticService records played alerts")
    func mockHapticRecords() {
        let service = MockHapticService()

        service.play(.fiveMinuteWarning)
        service.play(.timesUp)

        #expect(service.playedAlerts == [.fiveMinuteWarning, .timesUp])
    }

    @Test("MockAPIClient returns configured results")
    func mockAPIClientWorks() async throws {
        let client = MockAPIClient()
        client.fetchCurrentEventResult = .success(TestData.event())

        let event = try await client.fetchCurrentEvent()

        #expect(event.id == "bat-2026-spring")
        #expect(client.fetchCurrentEventCallCount == 1)
    }

    @Test("MockAPIClient throws configured errors")
    func mockAPIClientErrors() async {
        let client = MockAPIClient()
        client.fetchCurrentEventResult = .failure(MockError.unauthorized)

        await AsyncTestHelpers.assertThrows(
            try await client.fetchCurrentEvent(),
            expected: MockError.unauthorized
        )
    }
}
