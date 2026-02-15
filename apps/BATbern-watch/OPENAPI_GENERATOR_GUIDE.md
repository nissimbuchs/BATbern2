# Swift OpenAPI Generator Integration Guide

## Overview

This guide shows how to use Apple's Swift OpenAPI Generator to automatically generate Swift types from the BATbern OpenAPI specification.

## Setup Steps

### 1. Add Package Dependencies in Xcode

1. Open Xcode: `open BATbern-watch.xcodeproj`
2. Go to **File → Add Package Dependencies...**
3. Add these three packages:

```
https://github.com/apple/swift-openapi-generator
https://github.com/apple/swift-openapi-runtime
https://github.com/apple/swift-openapi-urlsession
```

4. Select version: **1.0.0** or newer
5. Add to target: **BATbern-watch Watch App**

### 2. Files Already Created

✅ `openapi-generator-config.yaml` - Generator configuration
✅ `openapi.yaml` - Symlink to events-api.openapi.yml

### 3. Add Files to Xcode Target

1. In Xcode, right-click **"BATbern-watch Watch App"** folder
2. Select **"Add Files to BATbern-watch Watch App..."**
3. Select both:
   - `openapi-generator-config.yaml`
   - `openapi.yaml`
4. ✅ Check **"Add to targets: BATbern-watch Watch App"**
5. Click **Add**

### 4. Build the Project

Run: **Product → Build** (Cmd+B)

The generator will create files in:
```
DerivedData/.../SourcePackages/plugins/.../GeneratedSources/
```

Generated files:
- `Types.swift` - Model types (Event, Session, Speaker, etc.)
- `Client.swift` - API client (if enabled)

### 5. Use Generated Types

#### Before (Manual DTOs):

```swift
import Foundation

struct EventResponse: Codable {
    let eventCode: String
    let title: String
    let date: String
    let sessions: [SessionResponse]?
}

// Manual mapping required
func toWatchEvent() -> WatchEvent { ... }
```

#### After (Generated Types):

```swift
import OpenAPIRuntime
import OpenAPIURLSession

// Generated automatically from OpenAPI spec
// No manual DTO definitions needed!

class PublicEventService {
    private let client: Client

    init() {
        self.client = Client(
            serverURL: URL(string: "https://api.staging.batbern.ch")!,
            transport: URLSessionTransport()
        )
    }

    func fetchCurrentEvent() async throws -> Components.Schemas.EventDetail {
        let response = try await client.getCurrentEvent(
            query: .init(include: "topics,venue,sessions")
        )

        switch response {
        case .ok(let okResponse):
            return try okResponse.body.json
        case .notFound:
            throw APIError.noCurrentEvent
        case .undocumented(let statusCode, _):
            throw APIError.invalidResponse
        }
    }
}
```

## Benefits

✅ **Type Safety** - Compile-time checking of API responses
✅ **Auto-Sync** - Types update when OpenAPI spec changes
✅ **No Manual Mapping** - DTOs generated automatically
✅ **Better Errors** - Catch API changes at build time, not runtime
✅ **Less Code** - Delete 200+ lines of manual DTO definitions

## Migration Path

### Phase 1: Setup (Now)
- ✅ Add package dependencies
- ✅ Configure generator
- ✅ Build project to verify generation works

### Phase 2: Gradual Migration
- Replace `PublicEventService` to use generated client
- Update `PublicViewModel` to use `Components.Schemas.EventDetail`
- Keep existing SwiftData models (`CachedEvent`, etc.)

### Phase 3: Full Integration
- Consider replacing SwiftData models with generated types
- Add conformance protocols (`@Model` via extensions)

## Reference

- [Swift OpenAPI Generator Docs](https://swiftpackageindex.com/apple/swift-openapi-generator/documentation)
- [Apple Tutorial](https://developer.apple.com/documentation/swift/using-the-openapi-generator)
- [BATbern Events API Spec](../../../docs/api/events-api.openapi.yml)

## Troubleshooting

**Generator not running?**
- Ensure both `openapi.yaml` and `openapi-generator-config.yaml` are added to target
- Check Build Phase logs for generator output
- Clean build folder: Product → Clean Build Folder

**Types not found?**
- Import `OpenAPIRuntime` in files using generated types
- Verify package dependencies are resolved
- Check `DerivedData/.../GeneratedSources/` for generated files

**Compilation errors?**
- Ensure OpenAPI spec is valid YAML
- Check `openapi-generator-config.yaml` syntax
- Verify package versions are compatible
