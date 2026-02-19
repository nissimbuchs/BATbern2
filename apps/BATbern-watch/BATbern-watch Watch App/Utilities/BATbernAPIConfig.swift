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
///
/// WebSocket note: The API gateway (port 8000) uses RestTemplate and cannot proxy WebSocket
/// upgrade requests. In simulator, WebSocket connects directly to EMS on port 8002.
/// In production the ALB handles WebSocket upgrade natively, so staging uses the same base URL.
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

    /// Base URL for WebSocket connections.
    /// Simulator connects directly to EMS (port 8002) — the gateway cannot proxy WS upgrades.
    /// Staging/production: ALB handles WS upgrade transparently, same host as REST API.
    static let webSocketBaseURL: String = {
        if let envURL = ProcessInfo.processInfo.environment["BATBERN_WS_BASE_URL"],
           !envURL.isEmpty {
            return envURL
        }
        #if targetEnvironment(simulator)
        return "http://localhost:8002"          // Simulator → EMS directly (bypasses gateway)
        #else
        return "https://api.staging.batbern.ch" // Real device → staging ALB (supports WS upgrade)
        #endif
    }()
}
