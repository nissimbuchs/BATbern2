//
//  PortraitCache.swift
//  BATbern-watch Watch App
//
//  File-based image cache for speaker portraits and company logos with offline support.
//  Source: W1.2 - Session Card Browsing (AC#5); W1.5 - UI Polish (AC#3, AC#4, AC#5)
//  W2.3: AC#3 — Watch-optimized portrait resize (200×200px, JPEG 80%, ~100KB).
//

import Foundation
import ImageIO
import UniformTypeIdentifiers

// MARK: - Protocol (for testability)

/// Protocol enabling PortraitCache to be mocked in unit tests.
/// W2.3: Injected into EventSyncService so portrait downloads can be verified in isolation.
protocol PortraitCacheable: AnyObject {
    @discardableResult
    func downloadAndCache(url: URL) async throws -> Data
}

/// File-based cache for speaker portrait images and company logos.
/// Storage: ~100KB per portrait, max ~1MB per event (NFR24 compliance)
/// W2.3 AC#3: Portraits resized to 200×200px max, JPEG 80% (~100KB) on download.
class PortraitCache: PortraitCacheable {
    // MARK: - Singleton

    static let shared = PortraitCache()

    // MARK: - Properties

    private let fileManager = FileManager.default
    private let cacheDirectory: URL
    private let logoDirectory: URL

    // MARK: - Initialization

    init() {
        // Use caches directory (can be purged by system when storage is low)
        let cachesDir = fileManager.urls(for: .cachesDirectory, in: .userDomainMask).first!
        self.cacheDirectory = cachesDir.appendingPathComponent("PortraitCache", isDirectory: true)
        self.logoDirectory = cachesDir.appendingPathComponent("LogoCache", isDirectory: true)

        // Create cache directories if needed
        try? fileManager.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)
        try? fileManager.createDirectory(at: logoDirectory, withIntermediateDirectories: true)
    }

    // MARK: - Cache Operations

    /// Generates a collision-resistant cache key from a full URL.
    /// Uses djb2 hash of the full absolute URL to prevent collisions when
    /// different URLs share the same lastPathComponent (e.g. two CDNs both serve "photo.jpg").
    private func cacheKey(for url: URL) -> String {
        let fullString = url.absoluteString
        var hash: UInt32 = 5381
        for byte in fullString.utf8 {
            hash = hash &* 33 &+ UInt32(byte)
        }
        let sanitized = url.lastPathComponent
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: ":", with: "_")
        return "\(sanitized)_\(String(hash, radix: 16))"
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

    /// Downloads portrait from CDN, resizes to Watch-optimized resolution, and caches it.
    /// AC#3: Resizes to max 200×200px at JPEG 80% quality (~100KB) before caching.
    @discardableResult
    func downloadAndCache(url: URL) async throws -> Data {
        // Check cache first
        if let cachedData = getCachedPortrait(url: url) {
            return cachedData
        }

        // Download from CDN
        let (rawData, _) = try await URLSession.shared.data(from: url)

        // AC#3: Resize to Watch-optimized resolution (200×200px max, JPEG 80%)
        let data = resizeToWatchOptimized(data: rawData) ?? rawData

        // Save to cache
        savePortrait(url: url, data: data)

        return data
    }

    /// Resizes image data to max 200×200px at JPEG 80% quality using ImageIO.
    /// Falls back to nil if the source data is not a supported image format.
    /// W2.3 AC#3: Target ~100KB per portrait (vs ~2MB raw CDN image).
    private func resizeToWatchOptimized(data: Data, maxDimension: Int = 200) -> Data? {
        let sourceOptions: [CFString: Any] = [kCGImageSourceShouldCache: false]
        guard let source = CGImageSourceCreateWithData(data as CFData, sourceOptions as CFDictionary) else {
            return nil
        }

        let thumbOptions: [CFString: Any] = [
            kCGImageSourceThumbnailMaxPixelSize: maxDimension,
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true
        ]
        guard let thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, thumbOptions as CFDictionary) else {
            return nil
        }

        let destData = NSMutableData()
        guard let dest = CGImageDestinationCreateWithData(
            destData,
            UTType.jpeg.identifier as CFString,
            1,
            nil
        ) else {
            return nil
        }

        CGImageDestinationAddImage(
            dest, thumbnail,
            [kCGImageDestinationLossyCompressionQuality: 0.8] as CFDictionary
        )
        guard CGImageDestinationFinalize(dest) else { return nil }
        return destData as Data
    }

    // MARK: - Logo Cache Operations (W1.5 AC#5)

    /// Generates a file-system safe cache key from company name
    private func logoKey(for companyName: String) -> String {
        companyName
            .lowercased()
            .trimmingCharacters(in: .whitespaces)
            .replacingOccurrences(of: " ", with: "_")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: ":", with: "_")
    }

    private func logoFileURL(for companyName: String) -> URL {
        return logoDirectory.appendingPathComponent(logoKey(for: companyName))
    }

    /// Checks if a company logo is cached locally
    func isLogoCached(companyName: String) -> Bool {
        let fileURL = logoFileURL(for: companyName)
        return fileManager.fileExists(atPath: fileURL.path)
    }

    /// Retrieves cached company logo data
    func getLogoForCompany(_ companyName: String) -> Data? {
        let fileURL = logoFileURL(for: companyName)
        return try? Data(contentsOf: fileURL)
    }

    /// Saves company logo data to cache
    func saveLogo(companyName: String, data: Data) {
        let fileURL = logoFileURL(for: companyName)
        try? data.write(to: fileURL, options: .atomic)
    }

    /// Downloads a company logo via the BATbern company API and caches it under the company name key.
    /// Cache-first: no-op if already cached. Throws on network/decode failure — caller handles silently.
    /// Single implementation consumed by both ImageCachePrefetcher and SpeakerPortraitView.
    func downloadAndCacheLogo(
        companyName: String,
        apiBaseURL: String = BATbernAPIConfig.baseURL
    ) async throws {
        guard !isLogoCached(companyName: companyName) else { return }
        guard let encoded = companyName.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed),
              var components = URLComponents(string: "\(apiBaseURL)/api/v1/companies/\(encoded)") else {
            return
        }
        components.queryItems = [URLQueryItem(name: "expand", value: "logo")]
        guard let url = components.url else { return }

        let (data, _) = try await URLSession.shared.data(from: url)
        let response = try JSONDecoder().decode(CompanyLogoAPIResponse.self, from: data)
        if let logoUrlString = response.logo?.url,
           let logoUrl = URL(string: logoUrlString) {
            let (logoData, _) = try await URLSession.shared.data(from: logoUrl)
            saveLogo(companyName: companyName, data: logoData)
        }
    }

    // MARK: - Portrait Cache Operations

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

    /// Clears all cached portraits AND logos
    func clearCache() {
        try? fileManager.removeItem(at: cacheDirectory)
        try? fileManager.createDirectory(at: cacheDirectory, withIntermediateDirectories: true)
        try? fileManager.removeItem(at: logoDirectory)
        try? fileManager.createDirectory(at: logoDirectory, withIntermediateDirectories: true)
        print("🗑️ PortraitCache: Cache cleared (portraits + logos)")
    }

    /// Returns total cache size in bytes (portraits + logos)
    func cacheSize() -> Int64 {
        var totalSize: Int64 = 0

        for directory in [cacheDirectory, logoDirectory] {
            guard let enumerator = fileManager.enumerator(at: directory, includingPropertiesForKeys: [.fileSizeKey]) else {
                continue
            }
            for case let fileURL as URL in enumerator {
                guard let resourceValues = try? fileURL.resourceValues(forKeys: [.fileSizeKey]),
                      let fileSize = resourceValues.fileSize else {
                    continue
                }
                totalSize += Int64(fileSize)
            }
        }

        return totalSize
    }
}

// MARK: - Private API Response Types (used by downloadAndCacheLogo)

private struct CompanyLogoAPIResponse: Codable {
    let logo: CompanyLogoURLField?
}

private struct CompanyLogoURLField: Codable {
    let url: String
}
