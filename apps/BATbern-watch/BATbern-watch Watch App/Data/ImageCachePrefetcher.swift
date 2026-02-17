//
//  ImageCachePrefetcher.swift
//  BATbern-watch Watch App
//
//  Background prefetch coordinator for speaker portraits and company logos.
//  Source: W1.5 - UI Polish & Image Caching (AC#3, AC#4, AC#5)
//

import Foundation

// MARK: - Protocol (for testability)

protocol ImageCachePrefetcherProtocol {
    // @MainActor: CachedSpeaker is a non-Sendable PersistentModel; callers are always
    // @MainActor-isolated, so the parameter never crosses an actor boundary.
    @MainActor func prefetchAll(speakers: [CachedSpeaker]) async
}

// MARK: - Production Implementation

/// Concurrently prefetches all speaker portrait and company logo URLs into PortraitCache.
/// Runs silently in background — errors per-item are swallowed so one failure cannot stop others.
class ImageCachePrefetcher: ImageCachePrefetcherProtocol {

    private let portraitCache: PortraitCache
    private let maxCacheSizeBytes: Int64 = 40 * 1024 * 1024  // 40MB headroom (NFR24: <50MB total)

    init(portraitCache: PortraitCache = .shared) {
        self.portraitCache = portraitCache
    }

    /// Prefetch portrait and logo for every speaker concurrently.
    /// Uses TaskGroup so all downloads run in parallel; one failure does NOT stop others.
    @MainActor func prefetchAll(speakers: [CachedSpeaker]) async {
        // Respect 40MB storage limit before starting any download
        guard portraitCache.cacheSize() < maxCacheSizeBytes else {
            print("⚠️ ImageCachePrefetcher: Cache ≥40MB — skipping prefetch to stay within NFR24 budget")
            return
        }

        // Extract only Sendable value types before entering TaskGroup.
        // CachedSpeaker is a SwiftData PersistentModel (non-Sendable) and must not
        // be captured in @Sendable closures that cross actor boundaries.
        let uniqueInfos: [SpeakerPrefetchInfo] = Dictionary(grouping: speakers, by: { $0.username })
            .compactMap { $0.value.first }
            .map { SpeakerPrefetchInfo(username: $0.username, profilePictureUrl: $0.profilePictureUrl, company: $0.company) }

        await withTaskGroup(of: Void.self) { group in
            for info in uniqueInfos {
                group.addTask {
                    await self.prefetchSpeaker(info)
                }
            }
        }

        let totalKB = portraitCache.cacheSize() / 1024
        print("✅ ImageCachePrefetcher: Prefetch complete — total cache \(totalKB)KB")
    }

    // MARK: - Private

    /// Value-type snapshot of the fields needed for prefetch — safe to send across actor boundaries.
    private struct SpeakerPrefetchInfo: Sendable {
        let username: String
        let profilePictureUrl: String?
        let company: String?
    }

    private func prefetchSpeaker(_ speaker: SpeakerPrefetchInfo) async {
        // Re-check budget per speaker to limit overshoot when many tasks run concurrently
        guard portraitCache.cacheSize() < maxCacheSizeBytes else {
            print("⚠️ ImageCachePrefetcher: Cache ≥40MB mid-prefetch — stopping speaker \(speaker.username)")
            return
        }

        // Portrait
        if let urlString = speaker.profilePictureUrl, let url = URL(string: urlString) {
            await prefetchPortrait(url: url, label: speaker.username)
        }

        // Company logo (via company API)
        if let companyName = speaker.company {
            await prefetchLogo(companyName: companyName)
        }
    }

    private func prefetchPortrait(url: URL, label: String) async {
        guard !portraitCache.isCached(url: url) else { return }

        do {
            _ = try await portraitCache.downloadAndCache(url: url)
            print("✅ ImageCachePrefetcher: Portrait cached — \(label)")
        } catch {
            print("⚠️ ImageCachePrefetcher: Portrait download failed for \(label): \(error.localizedDescription)")
        }
    }

    private func prefetchLogo(companyName: String) async {
        do {
            // Delegates to PortraitCache.downloadAndCacheLogo — single implementation, no duplication
            try await portraitCache.downloadAndCacheLogo(companyName: companyName)
            if portraitCache.isLogoCached(companyName: companyName) {
                print("✅ ImageCachePrefetcher: Logo cached — \(companyName)")
            }
        } catch {
            print("⚠️ ImageCachePrefetcher: Logo download failed for \(companyName): \(error.localizedDescription)")
        }
    }
}

