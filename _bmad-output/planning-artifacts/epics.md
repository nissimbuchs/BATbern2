---
stepsCompleted: [step-01-validate-prerequisites]
inputDocuments:
  - _bmad-output/planning-artifacts/prd.md
  - _bmad-output/planning-artifacts/architecture.md
  - _bmad-output/planning-artifacts/ux-design-specification.md
---

# BATbern Watch - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for BATbern Watch, decomposing the requirements from the PRD, UX Design, and Architecture into implementable stories.

## Requirements Inventory

### Functional Requirements

FR1: Organizer can view the current session's speaker name, talk title, and remaining time on the Watch face complication
FR2: Organizer can view the next upcoming session (speaker name and talk title) at a glance
FR3: Organizer can scroll through the full remaining event schedule using the Digital Crown
FR4: Organizer can view a speaker's portrait photo alongside their session information
FR5: Organizer can see the current event state on the always-on display without raising their wrist
FR6: Organizer can mark the current session as complete to advance the schedule to the next item
FR7: System detects when a session runs past its allocated time and displays overrun duration
FR8: Organizer can initiate a schedule cascade when a session overruns, shifting all remaining items by a chosen increment
FR9: System automatically recalculates all downstream session times and break durations after a cascade
FR10: Organizer can view whether the event is on time, ahead, or behind schedule
FR11: Organizer receives a haptic alert when 5 minutes remain in the current session
FR12: Organizer receives a distinct haptic alert when 2 minutes remain in the current session
FR13: Organizer receives a distinct haptic alert when the session time reaches zero
FR14: Organizer receives escalating haptic alerts at intervals when a session runs over time
FR15: Organizer receives a haptic alert at a configured time before a break ends (gong reminder)
FR16: All connected organizers receive haptic alerts simultaneously for the same timing events
FR17: All connected organizer watches display identical event state in real time
FR18: When one organizer advances the schedule, all other watches update within 3 seconds
FR19: When one organizer triggers a schedule cascade, all other watches reflect the new times within 3 seconds
FR20: Organizer can see how many fellow organizers are currently connected to the event
FR21: Organizer can authenticate with the BATbern platform directly from the Watch
FR22: Organizer can select and join an active event from the Watch
FR23: System syncs the full event schedule (sessions, speakers, times, portraits) to the Watch on join
FR24: Watch connects directly to the BATbern backend over WiFi without requiring an iPhone
FR25: System caches the complete event schedule locally after initial sync
FR26: Countdown timer and haptic alerts continue functioning when WiFi connectivity is lost
FR27: Actions taken offline (session advance, cascade) queue locally and sync when connectivity is restored
FR28: System indicates connectivity status to the organizer (connected / offline)

### NonFunctional Requirements

NFR1: Complication updates within 1 second of state change
NFR2: Haptic alerts fire within 1 second of scheduled time
NFR3: Schedule cascade propagates to all connected watches within 3 seconds
NFR4: Event schedule initial sync completes within 5 seconds on venue WiFi
NFR5: App launch to usable state within 3 seconds
NFR6: App must not crash during a live event (3-hour continuous session)
NFR7: Haptic alerts must fire even if the app moves to background
NFR8: Offline mode activates seamlessly on WiFi loss — no user action required
NFR9: Queued offline actions must not be lost on app restart
NFR10: System must handle conflicting actions from multiple organizers gracefully
NFR11: Organizer authentication via existing BATbern Cognito credentials
NFR12: Session token persists for event duration (no re-authentication mid-event)
NFR13: Only authenticated organizers assigned to an event can join that event's Watch session
NFR14: Communication with backend encrypted via TLS
NFR15: Full 3-hour event operation on a single charge (Watch battery > 30% remaining at event end)
NFR16: Network polling frequency adapts to battery level (reduce frequency below 20% battery)
NFR17: Cached data storage under 50MB per event (schedule + portraits)

### Additional Requirements

**From Architecture:**
- Xcode watchOS App template as starter (standalone Watch app, SwiftUI, SwiftData)
- Backend extension of existing Event Management Service (new `ch.batbern.events.watch` package)
- Flyway schema migration: 4 new columns on `sessions` table (`actual_start_time`, `actual_end_time`, `overrun_minutes`, `completed_by_username`)
- STOMP over WebSocket for real-time sync (extending existing `WebSocketConfig.java`)
- JWT authentication in STOMP CONNECT headers (`JwtStompInterceptor`)
- REST fallback endpoints (4 new: state, portrait, actions replay, active-events)
- ALB configuration changes (stickiness, idle timeout 3600s, deregistration delay 30s)
- In-memory organizer presence tracking (no external message broker)
- Server-authoritative state model with last-write-wins conflict resolution
- SwiftData for local cache, Keychain for credentials

**From UX Design:**
- Complication-first architecture: Watch face complication IS the primary interface (90% of usage)
- Context-aware display: different content based on event state (during talk / between talks / during break)
- Haptic vocabulary: 7 distinct patterns (5-min, 2-min, time's up, overrun pulse, gong, action confirm, connection lost)
- Color-coded urgency: green (>5min) → yellow (5min) → orange (2min) → red (0:00/overrun)
- Layout zones: status bar → countdown (dominant) → speaker info → action button
- Typography: SF Mono ~40pt for countdown, SF Pro 16pt for names, SF Pro 13pt for titles
- Accessibility: Dynamic Type, VoiceOver, Reduce Motion, Bold Text, visual flash with every haptic
- Anti-patterns to avoid: no multi-level navigation during events, no confirmation on "Done" (only on cascade), no text input ever

### FR Coverage Map

{{requirements_coverage_map}}

## Epic List

{{epics_list}}
