//
//  MockImageCachePrefetcher.swift
//  BATbern-watch Watch AppTests
//
//  Test mock for ImageCachePrefetcherProtocol — records prefetchAll invocations.
//

import Foundation
@testable import BATbern_watch_Watch_App

class MockImageCachePrefetcher: ImageCachePrefetcherProtocol {
    var prefetchCallCount = 0
    var lastPrefetchedSpeakers: [CachedSpeaker] = []

    func prefetchAll(speakers: [CachedSpeaker]) async {
        prefetchCallCount += 1
        lastPrefetchedSpeakers = speakers
    }
}
