//
//  PortraitCacheTests.swift
//  BATbern-watch Watch AppTests
//
//  Tests for PortraitCache logo caching (W1.5 AC#5).
//

import Testing
import Foundation
@testable import BATbern_watch_Watch_App

@Suite("PortraitCache Tests")
struct PortraitCacheTests {

    // Use an isolated cache instance per test (not .shared)
    private let cache: PortraitCache

    init() {
        cache = PortraitCache()
    }

    // MARK: - Logo Cache Roundtrip

    @Test("Logo roundtrip: saveLogo then getLogoForCompany returns same data")
    func test_logoRoundtrip_saveAndRetrieve() {
        // Given: Some binary data representing a logo
        let companyName = "ACME Corp Test \(UUID().uuidString)"
        let logoData = Data("fake-logo-data".utf8)

        // When: Save logo
        cache.saveLogo(companyName: companyName, data: logoData)

        // Then: Can retrieve it
        let retrieved = cache.getLogoForCompany(companyName)
        #expect(retrieved == logoData, "Retrieved logo data should match saved data")
    }

    @Test("isLogoCached returns false before saving")
    func test_isLogoCached_falseBeforeSave() {
        let companyName = "Unknown Corp \(UUID().uuidString)"
        #expect(cache.isLogoCached(companyName: companyName) == false)
    }

    @Test("isLogoCached returns true after saving")
    func test_isLogoCached_trueAfterSave() {
        let companyName = "Known Corp \(UUID().uuidString)"
        cache.saveLogo(companyName: companyName, data: Data("logo".utf8))
        #expect(cache.isLogoCached(companyName: companyName) == true)
    }

    // MARK: - Key Generation (special chars)

    @Test("Logo key handles spaces in company name")
    func test_logoKey_handlesSpaces() {
        let company1 = "ACME Corp Test \(UUID().uuidString)"
        let data = Data("logo".utf8)
        cache.saveLogo(companyName: company1, data: data)
        // Same name should retrieve same data (key normalisation is stable)
        let retrieved = cache.getLogoForCompany(company1)
        #expect(retrieved == data)
    }

    @Test("Logo key handles mixed case (lowercased)")
    func test_logoKey_handlesMixedCase() {
        let base = "Corp\(UUID().uuidString)"
        let upperName = base.uppercased()
        let lowerName = base.lowercased()
        let data = Data("logo".utf8)

        // Save under uppercase
        cache.saveLogo(companyName: upperName, data: data)

        // Retrieve under lowercase — same key expected (key is lowercased)
        let retrieved = cache.getLogoForCompany(lowerName)
        #expect(retrieved == data, "Key should be case-insensitive (lowercased)")
    }

    // MARK: - cacheSize includes logos

    @Test("cacheSize includes logo bytes")
    func test_cacheSize_includesLogos() {
        let sizeBefore = cache.cacheSize()

        let companyName = "SizableCompany \(UUID().uuidString)"
        let logoData = Data(repeating: 0xAB, count: 1024)  // 1KB
        cache.saveLogo(companyName: companyName, data: logoData)

        let sizeAfter = cache.cacheSize()
        #expect(sizeAfter > sizeBefore, "cacheSize() should grow after saving a logo")
        #expect(sizeAfter - sizeBefore >= 1024, "cacheSize() should include at least the 1KB logo")
    }

    // MARK: - getLogoForCompany returns nil when not cached

    @Test("getLogoForCompany returns nil for unknown company")
    func test_getLogoForCompany_nilForUnknown() {
        let retrieved = cache.getLogoForCompany("NonExistentCompany_\(UUID().uuidString)")
        #expect(retrieved == nil)
    }
}
