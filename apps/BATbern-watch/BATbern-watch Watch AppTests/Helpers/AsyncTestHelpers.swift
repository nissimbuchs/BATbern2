import Foundation
import Testing

/// Utilities for async and time-dependent testing patterns.
enum AsyncTestHelpers {

    /// Wait for a condition to become true within a timeout.
    /// Avoids arbitrary `Task.sleep` in tests — polls with short intervals instead.
    static func waitFor(
        timeout: TimeInterval = 2.0,
        interval: TimeInterval = 0.05,
        condition: @escaping () -> Bool
    ) async throws {
        let deadline = Date().addingTimeInterval(timeout)

        while !condition() {
            if Date() > deadline {
                throw TestError.timeout("Condition not met within \(timeout)s")
            }
            try await Task.sleep(for: .milliseconds(Int(interval * 1000)))
        }
    }

    /// Assert that an async throwing expression throws a specific error type.
    static func assertThrows<T, E: Error & Equatable>(
        _ expression: @autoclosure () async throws -> T,
        expected: E
    ) async {
        do {
            _ = try await expression()
            Issue.record("Expected error \(expected) but no error was thrown")
        } catch let error as E {
            #expect(error == expected)
        } catch {
            Issue.record("Expected \(E.self) but got \(type(of: error)): \(error)")
        }
    }
}

enum TestError: Error, LocalizedError {
    case timeout(String)

    var errorDescription: String? {
        switch self {
        case .timeout(let message): return message
        }
    }
}
