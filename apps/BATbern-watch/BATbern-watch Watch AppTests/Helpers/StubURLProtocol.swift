import Foundation

/// URLProtocol stub for exercising real `URLSession` request/decoding paths
/// without the network. Responses are keyed by host so suites running in
/// parallel never see each other's stubs — give each test its own host.
final class StubURLProtocol: URLProtocol {

    struct Stub {
        let statusCode: Int
        let body: Data
        /// When set, the request fails at the transport level (no HTTP response).
        let error: URLError?
    }

    private static let lock = NSLock()
    nonisolated(unsafe) private static var stubs: [String: Stub] = [:]
    nonisolated(unsafe) private static var requests: [String: [URLRequest]] = [:]

    /// Register the response for every request to `host`, and return a session that routes through the stub.
    static func session(host: String, statusCode: Int = 200, body: String) -> URLSession {
        register(host: host, stub: Stub(statusCode: statusCode, body: Data(body.utf8), error: nil))
    }

    /// Register a transport failure (e.g. server unreachable) for every request to `host`.
    static func session(host: String, failingWith error: URLError) -> URLSession {
        register(host: host, stub: Stub(statusCode: 0, body: Data(), error: error))
    }

    private static func register(host: String, stub: Stub) -> URLSession {
        lock.withLock {
            stubs[host] = stub
            requests[host] = []
        }
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [StubURLProtocol.self]
        return URLSession(configuration: config)
    }

    /// Requests received for `host`, in order.
    static func receivedRequests(host: String) -> [URLRequest] {
        lock.withLock { requests[host] ?? [] }
    }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        let host = request.url?.host ?? ""
        let stub: Stub? = Self.lock.withLock {
            Self.requests[host, default: []].append(request)
            return Self.stubs[host]
        }
        if let error = stub?.error {
            client?.urlProtocol(self, didFailWithError: error)
            return
        }
        guard let stub, let url = request.url,
              let response = HTTPURLResponse(url: url, statusCode: stub.statusCode, httpVersion: nil, headerFields: nil) else {
            client?.urlProtocol(self, didFailWithError: URLError(.cannotFindHost))
            return
        }
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: stub.body)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}
