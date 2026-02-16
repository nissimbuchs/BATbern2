//
//  PortraitCache.swift
//  BATbern-watch Watch App
//
//  File-based image cache for speaker portraits with offline support.
//  Source: W1.2 - Session Card Browsing (AC#5)
//

import Foundation

/// File-based cache for speaker portrait images
/// Storage: ~100KB per portrait, max ~1MB per event (NFR24 compliance)
class PortraitCache {
    // MARK: - Properties

    private let fileManager = FileManager.default
    private let cacheDirectory: URL

    // MARK: - Initialization

    init() {
        // Use caches directory (can be purged by system when storage is low)
        let cachesDir = fileManager.urls(for: .cachesDirectory, in: .userDomainMask).first!
        self.cacheDirectory = cachesDir.appendingPathComponent("PortraitCache", isDirectory: true)

        // Create cache directory if needed
        try? fileManager.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)
    }

    // MARK: - Cache Operations

    /// Generates cache key from CDN URL
    private func cacheKey(for url: URL) -> String {
        // Use URL path as filename (sanitized)
        let sanitized = url.lastPathComponent
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: ":", with: "_")
        return sanitized
    }

    /// Returns cached file URL for portrait
    private func cacheFileURL(for url: URL) -> URL {
        return cacheDirectory.appendingPathComponent(cacheKey(for: url))
    }

    /// Checks if portrait is cached locally
    func isCached(url: URL) -> Bool {
        let fileURL = cacheFileURL(for: url)
        return fileManager.fileExists(atPath: fileURL.path)
    }

    /// Retrieves cached portrait data
    func getCachedPortrait(url: URL) -> Data? {
        let fileURL = cacheFileURL(for: url)
        return try? Data(contentsOf: fileURL)
    }

    /// Saves portrait data to cache
    func savePortrait(url: URL, data: Data) {
        let fileURL = cacheFileURL(for: url)
        try? data.write(to: fileURL, options: .atomic)
    }

    /// Downloads portrait from CDN and caches it
    func downloadAndCache(url: URL) async throws -> Data {
        // Check cache first
        if let cachedData = getCachedPortrait(url: url) {
            return cachedData
        }

        // Download from CDN
        let (data, _) = try await URLSession.shared.data(from: url)

        // Save to cache
        savePortrait(url: url, data: data)

        return data
    }

    /// Pre-downloads all speaker portraits for offline availability
    func prefetchPortraits(urls: [URL]) async {
        for url in urls {
            // Skip if already cached
            guard !isCached(url: url) else { continue }

            do {
                _ = try await downloadAndCache(url: url)
                print("✅ PortraitCache: Cached portrait - \(url.lastPathComponent)")
            } catch {
                print("⚠️ PortraitCache: Failed to cache portrait - \(url.lastPathComponent): \(error)")
            }
        }
    }

    /// Clears all cached portraits
    func clearCache() {
        try? fileManager.removeItem(at: cacheDirectory)
        try? fileManager.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)
        print("🗑️ PortraitCache: Cache cleared")
    }

    /// Returns total cache size in bytes
    func cacheSize() -> Int64 {
        guard let enumerator = fileManager.enumerator(at: cacheDirectory, includingPropertiesForKeys: [.fileSizeKey]) else {
            return 0
        }

        var totalSize: Int64 = 0
        for case let fileURL as URL in enumerator {
            guard let resourceValues = try? fileURL.resourceValues(forKeys: [.fileSizeKey]),
                  let fileSize = resourceValues.fileSize else {
                continue
            }
            totalSize += Int64(fileSize)
        }

        return totalSize
    }
}
