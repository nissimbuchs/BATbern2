# BATbern Watch

Standalone Apple Watch companion app for BATbern conferences.

## Overview

A dual-purpose watchOS app:
- **Public zone** (no login): Browse event sessions, speaker bios, abstracts via Digital Crown
- **Organizer zone** (paired): Live countdown, haptic alerts, team-synced session control

## Tech Stack

- **Platform:** watchOS 11+
- **Language:** Swift
- **UI:** SwiftUI
- **Storage:** SwiftData (local cache), Keychain (pairing token)
- **Networking:** URLSession (REST), URLSessionWebSocketTask (real-time sync)
- **Build:** Xcode 16+

## Project Structure

```
BATbern-watch/
├── BATbernWatch/              # Main Watch app target
│   ├── App/                   # App entry point, navigation
│   ├── Views/                 # SwiftUI views
│   │   ├── Public/            # Public zone screens
│   │   └── Organizer/         # Organizer zone screens
│   ├── Models/                # SwiftData models
│   ├── Services/              # API client, WebSocket, haptics
│   ├── ViewModels/            # ObservableObject view models
│   └── Resources/             # Assets, colors, fonts
├── BATbernWatchTests/         # Unit tests
├── BATbernWatch.xcodeproj/    # Xcode project
└── README.md                  # This file
```

## Relationship to Main Platform

This app is **independent** of the Gradle/Java/TypeScript build system. It consumes the BATbern public REST API and (for organizers) authenticates via a pairing token flow.

- **Public data:** `GET /api/v1/events/current?expand=sessions,speakers` (existing endpoint)
- **Pairing:** `POST /api/v1/watch/pair` (new endpoint, Epic W2)
- **Real-time sync:** `wss://.../ws/events/{eventCode}/live` (new WebSocket, Epic W4)

## Documentation

All planning docs: [`docs/watch-app/`](../../docs/watch-app/)

## Build & Run

> Xcode project will be created in Epic W1.

```bash
# Open in Xcode
open BATbernWatch.xcodeproj

# Build for Watch Simulator
xcodebuild -scheme BATbernWatch -destination 'platform=watchOS Simulator,name=Apple Watch Series 9 (45mm)'
```
