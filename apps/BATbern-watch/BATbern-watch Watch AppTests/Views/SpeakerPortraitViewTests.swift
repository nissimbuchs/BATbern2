//
//  SpeakerPortraitViewTests.swift
//  BATbern-watch Watch AppTests
//
//  Tests for SpeakerPortraitView PortraitCache integration (W1.5 AC#4, AC#5).
//

import Testing
import Foundation
@testable import BATbern_watch_Watch_App

@Suite("SpeakerPortraitView Cache Tests")
struct SpeakerPortraitViewTests {

    // MARK: - AC#4: Portrait served from PortraitCache when cached

    @Test("Portrait cache: getCachedPortrait returns data when portrait was previously saved")
    func test_portrait_cacheHit_returnsData() {
        // Given: Portrait cached for a URL
        let cache = PortraitCache()
        let url = URL(string: "https://cdn.batbern.ch/speakers/anna.jpg")!
        let fakeData = Data("portrait-data".utf8)
        cache.savePortrait(url: url, data: fakeData)

        // When: Retrieve from cache
        let retrieved = cache.getCachedPortrait(url: url)

        // Then: Returns the cached data (no network call)
        #expect(retrieved == fakeData, "Cache hit should return saved portrait data")
    }

    @Test("Portrait cache: isCached returns false when portrait not yet cached")
    func test_portrait_cacheMiss_returnsNil() {
        let cache = PortraitCache()
        let url = URL(string: "https://cdn.batbern.ch/speakers/never-cached-\(UUID().uuidString).jpg")!

        #expect(cache.isCached(url: url) == false, "Fresh URL should not be cached")
        #expect(cache.getCachedPortrait(url: url) == nil, "getCachedPortrait should return nil for uncached URL")
    }

    // MARK: - AC#5: Company logo served from cache on second access

    @Test("Logo cache: second access returns cached data without re-fetch")
    func test_logo_secondAccess_servedFromCache() {
        // Given: Logo cached for a company
        let cache = PortraitCache()
        let companyName = "SwissCloud AG"
        let logoData = Data("logo-bytes".utf8)
        cache.saveLogo(companyName: companyName, data: logoData)

        // When: Second lookup
        let retrieved1 = cache.getLogoForCompany(companyName)
        let retrieved2 = cache.getLogoForCompany(companyName)

        // Then: Same data returned both times (from file, not network)
        #expect(retrieved1 == logoData)
        #expect(retrieved2 == logoData)
        #expect(retrieved1 == retrieved2, "Repeated cache access should return identical data")
    }

    @Test("Logo cache: isLogoCached is true after saving")
    func test_logo_isLogoCached_trueAfterSave() {
        let cache = PortraitCache()
        let companyName = "TechBern \(UUID().uuidString)"
        #expect(cache.isLogoCached(companyName: companyName) == false, "Should not be cached before save")

        cache.saveLogo(companyName: companyName, data: Data("x".utf8))
        #expect(cache.isLogoCached(companyName: companyName) == true, "Should be cached after save")
    }

    // MARK: - AC#4: Shared singleton used across views

    @Test("Singleton: PortraitCache.shared is consistent instance")
    func test_singleton_sharedInstanceIsConsistent() {
        // Given: Two references to .shared
        let ref1 = PortraitCache.shared
        let ref2 = PortraitCache.shared

        // Then: Same object identity
        #expect(ref1 === ref2, "PortraitCache.shared should always return the same instance")
    }

    @Test("Singleton: Portrait saved via shared is accessible via shared")
    func test_singleton_savedPortraitAccessibleViaShared() {
        let url = URL(string: "https://cdn.example.com/shared-test-\(UUID().uuidString).jpg")!
        let data = Data("shared-portrait".utf8)

        // Save via shared
        PortraitCache.shared.savePortrait(url: url, data: data)

        // Retrieve via shared
        let retrieved = PortraitCache.shared.getCachedPortrait(url: url)
        #expect(retrieved == data, "Portrait saved via .shared should be retrievable via .shared")

        // Cleanup: won't affect other tests since URLs are unique
    }
}
