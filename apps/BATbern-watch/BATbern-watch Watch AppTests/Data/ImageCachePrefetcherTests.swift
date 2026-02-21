//
//  ImageCachePrefetcherTests.swift
//  BATbern-watch Watch AppTests
//
//  Tests for ImageCachePrefetcher background prefetch coordinator (W1.5 AC#3, AC#4, AC#5).
//

import Testing
import Foundation
@testable import BATbern_watch_Watch_App

// MARK: - Testable PortraitCache subclass

/// Spy that records download calls without hitting the network.
/// Uses in-memory storage to avoid file system pollution between test runs.
final class SpyPortraitCache: PortraitCache {
    var downloadedURLs: [URL] = []
    var savedLogos: [String: Data] = [:]
    private(set) var simulatedSizeBytes: Int64 = 0

    // In-memory set so isCached() doesn't read leftover files from prior tests
    private var inMemoryCachedURLs: Set<String> = []

    func simulateCacheSize(_ bytes: Int64) {
        simulatedSizeBytes = bytes
    }

    override func cacheSize() -> Int64 {
        return simulatedSizeBytes > 0 ? simulatedSizeBytes : 0
    }

    /// Check against in-memory set, not the file system
    override func isCached(url: URL) -> Bool {
        inMemoryCachedURLs.contains(url.absoluteString)
    }

    /// Record in-memory only — no disk writes so tests stay isolated
    override func savePortrait(url: URL, data: Data) {
        inMemoryCachedURLs.insert(url.absoluteString)
    }

    override func downloadAndCache(url: URL) async throws -> Data {
        downloadedURLs.append(url)
        let fakeData = Data("fake-portrait".utf8)
        savePortrait(url: url, data: fakeData)
        return fakeData
    }

    override func saveLogo(companyName: String, data: Data) {
        savedLogos[companyName.lowercased().trimmingCharacters(in: .whitespaces)] = data
    }
}

// MARK: - Tests

@Suite("ImageCachePrefetcher Tests")
struct ImageCachePrefetcherTests {

    @Test("prefetchAll calls downloadAndCache for each speaker portrait URL")
    func test_prefetchAll_downloadsPortraits() async {
        // Given
        let spyCache = SpyPortraitCache()
        let prefetcher = ImageCachePrefetcher(portraitCache: spyCache)

        let speakers = [
            CachedSpeaker(username: "s1", firstName: "Anna", lastName: "Meier",
                          profilePictureUrl: "https://cdn.example.com/anna.jpg"),
            CachedSpeaker(username: "s2", firstName: "Tom", lastName: "Keller",
                          profilePictureUrl: "https://cdn.example.com/tom.jpg")
        ]

        // When
        await prefetcher.prefetchAll(speakers: speakers)

        // Then: Both portrait URLs were downloaded
        let downloadedStrings = spyCache.downloadedURLs.map(\.absoluteString)
        #expect(downloadedStrings.contains("https://cdn.example.com/anna.jpg"))
        #expect(downloadedStrings.contains("https://cdn.example.com/tom.jpg"))
    }

    @Test("prefetchAll deduplicates speakers with same username (no duplicate downloads)")
    func test_prefetchAll_deduplicatesByUsername() async {
        // Given: Two speakers with identical usernames (same person on two sessions)
        let spyCache = SpyPortraitCache()
        let prefetcher = ImageCachePrefetcher(portraitCache: spyCache)

        let sameSpeaker = CachedSpeaker(
            username: "shared-speaker",
            firstName: "Anna",
            lastName: "Meier",
            profilePictureUrl: "https://cdn.example.com/anna.jpg"
        )

        // When
        await prefetcher.prefetchAll(speakers: [sameSpeaker, sameSpeaker])

        // Then: Only one download (deduplicated)
        let portraitDownloads = spyCache.downloadedURLs.filter {
            $0.absoluteString == "https://cdn.example.com/anna.jpg"
        }
        #expect(portraitDownloads.count == 1, "Same portrait URL should only be downloaded once")
    }

    @Test("prefetchAll skips download when portrait already cached")
    func test_prefetchAll_skipsAlreadyCachedPortraits() async {
        // Given: Portrait already in cache
        let spyCache = SpyPortraitCache()
        let prefetcher = ImageCachePrefetcher(portraitCache: spyCache)

        let url = URL(string: "https://cdn.example.com/cached.jpg")!
        spyCache.savePortrait(url: url, data: Data("existing".utf8))

        let speaker = CachedSpeaker(
            username: "cached-user",
            firstName: "Max",
            lastName: "Weber",
            profilePictureUrl: url.absoluteString
        )

        // When
        await prefetcher.prefetchAll(speakers: [speaker])

        // Then: No download for already-cached URL
        #expect(!spyCache.downloadedURLs.contains(url), "Should not re-download already cached portrait")
    }

    @Test("prefetchAll respects 40MB cache limit and skips download")
    func test_prefetchAll_respectsCacheLimit() async {
        // Given: Cache already at 40MB
        let spyCache = SpyPortraitCache()
        spyCache.simulateCacheSize(40 * 1024 * 1024)  // 40MB
        let prefetcher = ImageCachePrefetcher(portraitCache: spyCache)

        let speakers = [
            CachedSpeaker(username: "s1", firstName: "Anna", lastName: "Meier",
                          profilePictureUrl: "https://cdn.example.com/anna.jpg")
        ]

        // When
        await prefetcher.prefetchAll(speakers: speakers)

        // Then: No downloads attempted (budget exceeded)
        #expect(spyCache.downloadedURLs.isEmpty, "Should skip all downloads when cache >= 40MB")
    }

    @Test("prefetchAll handles speakers without portrait URLs gracefully")
    func test_prefetchAll_handlesNoPortraitURL() async {
        let spyCache = SpyPortraitCache()
        let prefetcher = ImageCachePrefetcher(portraitCache: spyCache)

        let speakers = [
            CachedSpeaker(username: "no-photo", firstName: "Sara", lastName: "Weber",
                          profilePictureUrl: nil)
        ]

        // When: Should not crash, no download
        await prefetcher.prefetchAll(speakers: speakers)

        #expect(spyCache.downloadedURLs.isEmpty)
    }

    @Test("prefetchAll handles empty speakers array gracefully")
    func test_prefetchAll_emptySpeakers() async {
        let spyCache = SpyPortraitCache()
        let prefetcher = ImageCachePrefetcher(portraitCache: spyCache)

        // When: Empty array — no crash
        await prefetcher.prefetchAll(speakers: [])

        #expect(spyCache.downloadedURLs.isEmpty)
    }
}
