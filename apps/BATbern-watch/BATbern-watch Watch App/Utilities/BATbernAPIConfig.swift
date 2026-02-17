//
//  BATbernAPIConfig.swift
//  BATbern-watch Watch App
//
//  Centralized API configuration — single source of truth for base URL.
//  Source: W1.5 code review fix (H3)
//

import Foundation

/// Shared API configuration for the BATbern Watch app.
/// - Debug builds default to http://localhost:9000 (local API gateway, Watch Simulator)
/// - Override at runtime: set BATBERN_API_BASE_URL in Xcode Scheme → Run → Environment Variables
/// - Release builds always use staging URL
enum BATbernAPIConfig {
    static let baseURL: String = {
        // Runtime override: highest priority (useful for ngrok or IP-based testing)
        if let envURL = ProcessInfo.processInfo.environment["BATBERN_API_BASE_URL"],
           !envURL.isEmpty {
            return envURL
        }
        #if DEBUG
        return "http://localhost:8000"   // Watch Simulator → local API gateway
        #else
        return "https://api.staging.batbern.ch"
        #endif
    }()
}
