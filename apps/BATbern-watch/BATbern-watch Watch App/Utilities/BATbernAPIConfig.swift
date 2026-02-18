//
//  BATbernAPIConfig.swift
//  BATbern-watch Watch App
//
//  Centralized API configuration — single source of truth for base URL.
//  Source: W1.5 code review fix (H3)
//

import Foundation

/// Shared API configuration for the BATbern Watch app.
/// - Simulator builds default to http://localhost:8000 (local API gateway)
/// - Real device builds (debug or release) always use staging URL
/// - Override at runtime: set BATBERN_API_BASE_URL in Xcode Scheme → Run → Environment Variables
///   (only effective in Simulator; environment variables are not injected on physical devices)
enum BATbernAPIConfig {
    static let baseURL: String = {
        // Runtime override: highest priority (simulator only — not injected on real device)
        if let envURL = ProcessInfo.processInfo.environment["BATBERN_API_BASE_URL"],
           !envURL.isEmpty {
            return envURL
        }
        #if targetEnvironment(simulator)
        return "http://localhost:8000"          // Simulator → local API gateway
        #else
        return "https://api.staging.batbern.ch" // Real device → staging
        #endif
    }()
}
