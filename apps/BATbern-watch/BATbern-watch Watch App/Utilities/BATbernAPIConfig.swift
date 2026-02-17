//
//  BATbernAPIConfig.swift
//  BATbern-watch Watch App
//
//  Centralized API configuration — single source of truth for base URL.
//  Source: W1.5 code review fix (H3)
//

import Foundation

/// Shared API configuration for the BATbern Watch app.
/// Change baseURL here when switching environments (staging → production).
enum BATbernAPIConfig {
    // nonisolated(unsafe): immutable string constant — safe to access from any concurrency context
    nonisolated(unsafe) static let baseURL = "https://api.staging.batbern.ch"
}
