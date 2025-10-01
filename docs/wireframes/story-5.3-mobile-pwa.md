# Story: Mobile PWA Experience - Wireframe

**Story**: Epic 5, Story 3
**Screen**: Mobile PWA Experience
**User Role**: Attendee
**Related FR**: FR13 (Mobile)

---

## 7. Mobile PWA Experience (iPhone/Android)

### Mobile: Current Event Landing
```
┌──────────────────────┐
│ ☰ BATbern    🔍  EN  │
├──────────────────────┤
│                      │
│  SPRING CONF 2025    │
│  ═══════════════     │
│                      │
│  CLOUD NATIVE        │
│  ARCHITECTURE        │
│                      │
│  📅 May 15, 2025     │
│  📍 Kursaal Bern     │
│                      │
│  ┌────────────────┐  │
│  │                │  │
│  │  REGISTER FREE │  │
│  │                │  │
│  └────────────────┘  │
│                      │
│  🎟️ FREE ADMISSION   │
│  Limited Seats!      │
│                      │
├──────────────────────┤
│  FEATURED SPEAKERS   │
│                      │
│  ┌─────┐ ┌─────┐    │
│  │Sara │ │Peter│ ►  │
│  │ Kim │ │Müller│    │
│  └─────┘ └─────┘    │
│                      │
│  [View All Speakers] │
│                      │
├──────────────────────┤
│  QUICK INFO          │
│                      │
│  ⏰ 08:30 - 18:00    │
│  🍽️ Lunch included   │
│  👥 200+ attendees   │
│  📚 8 sessions       │
│                      │
│  [Full Schedule ↓]   │
│                      │
├──────────────────────┤
│ [📚 Past Events]     │
│ [🔍 Search Archive]  │
│ [👤 My Account]      │
└──────────────────────┘
```

### Mobile: Content Discovery
```
┌──────────────────────┐
│ ← Search    Filter ⚙ │
├──────────────────────┤
│                      │
│ ┌──────────────────┐ │
│ │ 🔍 Search...     │ │
│ └──────────────────┘ │
│                      │
│ Recent: "kubernetes" │
│                      │
├──────────────────────┤
│ RESULTS (247)        │
│                      │
│ ┌──────────────────┐ │
│ │ K8s Security     │ │
│ │ Best Practices   │ │
│ │                  │ │
│ │ T. Weber • 2024  │ │
│ │ ⭐ 4.8 • 👁️ 1.2K  │ │
│ │                  │ │
│ │ [View] [Save]    │ │
│ └──────────────────┘ │
│                      │
│ ┌──────────────────┐ │
│ │ Container        │ │
│ │ Security         │ │
│ │                  │ │
│ │ S. Kim • 2023    │ │
│ │ ⭐ 4.9 • 👁️ 2.1K  │ │
│ │                  │ │
│ │ [View] [Save]    │ │
│ └──────────────────┘ │
│                      │
│ [Load More...]       │
│                      │
├──────────────────────┤
│ 🏠 📚 🔍 💾 👤        │
└──────────────────────┘
```

### Mobile: Registration Flow
```
┌──────────────────────┐
│ ← Register   Step 1/3 │
├──────────────────────┤
│                      │
│ YOUR DETAILS         │
│                      │
│ First Name *         │
│ ┌──────────────────┐ │
│ │ John             │ │
│ └──────────────────┘ │
│                      │
│ Last Name *          │
│ ┌──────────────────┐ │
│ │ Smith            │ │
│ └──────────────────┘ │
│                      │
│ Email *              │
│ ┌──────────────────┐ │
│ │ john@company.ch  │ │
│ └──────────────────┘ │
│                      │
│ Company *            │
│ ┌──────────────────┐ │
│ │ TechCorp AG      │ │
│ └──────────────────┘ │
│                      │
│ Experience           │
│ ○ 0-2  ○ 3-5        │
│ ● 6-10 ○ 10+        │
│                      │
│ ┌────────────────┐   │
│ │     NEXT →     │   │
│ └────────────────┘   │
│                      │
└──────────────────────┘
```

## Key Interactive Elements

- **PWA Installation**: Add to home screen for app-like experience
- **Responsive Design**: Optimized layouts for mobile devices
- **Touch Gestures**: Swipe, long-press, pull-to-refresh interactions
- **Bottom Navigation**: Fixed navigation bar with key actions
- **Mobile-First Registration**: Streamlined multi-step form
- **Offline Support**: Service worker for offline content access
- **Push Notifications**: Event reminders and updates

## Functional Requirements Met

- **FR13**: Complete mobile-optimized experience
- **PWA Standards**: Installable, offline-capable, fast loading
- **Touch-Optimized**: Large tap targets, swipe gestures
- **Mobile Registration**: Simplified flow for small screens
- **Responsive Content**: Adaptive layouts for all screen sizes
- **Performance**: Fast loading, optimized assets

## User Interactions

1. **Browse Events**: Mobile-optimized event landing page
2. **Register**: Multi-step mobile registration flow
3. **Search Content**: Touch-friendly search and discovery
4. **Save Content**: Quick save to library with offline access
5. **Navigate**: Bottom navigation for key sections
6. **Install PWA**: Add to home screen prompt

## Technical Notes

- Service Worker for offline functionality
- Web App Manifest for PWA installation
- Touch event handlers for gestures
- Responsive breakpoints (320px, 375px, 414px)
- Image optimization for mobile networks
- Lazy loading for performance
- Push notification API integration
- IndexedDB for offline content storage

---

## API Requirements

APIs needed to load and display data for this screen:

### Initial Page Load (Mobile Event Landing)

1. **GET /api/v1/events/current/mobile**
   - Retrieve current event data optimized for mobile
   - Response includes: event details, featured speakers, quick info
   - Used for: Mobile event landing page
   - Optimized: Reduced payload, compressed images

2. **GET /api/v1/events/{eventId}/speakers/featured**
   - Retrieve featured speakers
   - Query params: `limit=4`, `imageSize=mobile`
   - Response includes: speaker names, photos (optimized), thumbnails
   - Used for: Featured speakers carousel

3. **GET /api/v1/events/past**
   - Retrieve past events list
   - Query params: `limit=10`, `mobile=true`
   - Response includes: event summaries, dates, attendance
   - Used for: Past events access

### Content Discovery (Mobile)

4. **GET /api/v1/content/search**
   - Retrieve search results (mobile-optimized)
   - Query params: `query`, `limit=10`, `imageSize=thumbnail`
   - Response includes: content cards with minimal data
   - Used for: Mobile search results
   - Optimized: Smaller images, reduced metadata

5. **GET /api/v1/content/search/recent**
   - Retrieve user's recent searches
   - Query params: `userId`, `limit=5`
   - Response includes: recent search queries
   - Used for: Recent searches display

### Registration Flow (Mobile)

6. **GET /api/v1/events/{eventId}/registration/form**
   - Retrieve registration form configuration
   - Response includes: required fields, validation rules, steps
   - Used for: Multi-step registration form
   - Optimized: Progressive loading of form steps

7. **POST /api/v1/events/{eventId}/registration/validate-step**
   - Validate registration step before proceeding
   - Payload: Step-specific data
   - Response: Validation result
   - Used for: Real-time validation during registration

### PWA & Offline

8. **GET /api/v1/pwa/manifest**
   - Retrieve PWA manifest configuration
   - Response: Web app manifest JSON
   - Used for: PWA installation and app configuration

9. **GET /api/v1/content/offline/sync**
   - Retrieve content for offline sync
   - Query params: `userId`, `savedContent=true`
   - Response: Content files for offline storage
   - Used for: Background sync to IndexedDB

---

## Action APIs

APIs called by user interactions and actions:

### Navigation & Layout

1. **POST /api/v1/users/{userId}/preferences/view**
   - Triggered by: View preference changes
   - Payload: `{ mobileView: "compact|comfortable", theme: "light|dark" }`
   - Response: Preferences saved
   - Updates: Layout adjustments

### Event Registration (Mobile)

2. **POST /api/v1/events/{eventId}/registration**
   - Triggered by: Completing registration flow
   - Payload: Multi-step form data collected
   - Response: Registration confirmation, ticket details
   - Side effects:
     - Sends confirmation email
     - Creates attendee record
     - Generates ticket QR code
   - Mobile-specific: May use device location for timezone

3. **POST /api/v1/events/{eventId}/registration/draft**
   - Triggered by: Auto-save during registration
   - Payload: Partial registration data
   - Response: Draft saved
   - Used for: Recovering form progress if abandoned

4. **GET /api/v1/events/{eventId}/registration/prefill**
   - Triggered by: Starting registration (logged in users)
   - Response: Pre-filled form data from profile
   - Used for: Auto-completing registration form

### Content Discovery (Mobile)

5. **POST /api/v1/content/search/mobile**
   - Triggered by: Search submission on mobile
   - Payload: `{ query, filters, location: { lat, lng } }`
   - Response: Mobile-optimized search results
   - Features: Location-aware results if permitted

6. **POST /api/v1/attendees/{attendeeId}/library/save**
   - Triggered by: [Save] button on mobile
   - Payload: `{ contentId, offline: true }`
   - Response: Content saved, queued for offline download
   - Background: Initiates service worker download

7. **GET /api/v1/content/{contentId}/mobile**
   - Triggered by: [View] button on mobile
   - Response: Mobile-optimized content viewer
   - Format: Responsive PDF or mobile-friendly HTML

### PWA Installation

8. **POST /api/v1/pwa/install-prompt**
   - Triggered by: PWA install prompt shown
   - Payload: `{ userId, platform, action: "shown|accepted|dismissed" }`
   - Response: Event logged
   - Analytics: Track PWA installation rates

9. **POST /api/v1/pwa/installed**
   - Triggered by: PWA successfully installed
   - Payload: `{ userId, platform, standalone: boolean }`
   - Response: Installation confirmed
   - Side effects: Enable PWA-specific features

### Push Notifications

10. **POST /api/v1/push/subscribe**
    - Triggered by: User enables notifications
    - Payload: `{ subscription: PushSubscription, deviceId }`
    - Response: Subscription registered
    - Used for: Sending push notifications

11. **DELETE /api/v1/push/unsubscribe**
    - Triggered by: User disables notifications
    - Payload: `{ subscriptionId }`
    - Response: Subscription removed

12. **POST /api/v1/push/test**
    - Triggered by: Test notification button
    - Response: Test notification sent
    - Used for: Verifying notification setup

### Offline Sync

13. **POST /api/v1/sync/request**
    - Triggered by: Manually requesting offline sync
    - Payload: `{ userId, contentIds: [] }`
    - Response: Sync job queued
    - Background: Service worker downloads content

14. **GET /api/v1/sync/status**
    - Triggered by: Checking sync progress
    - Response: Sync status, downloaded items, errors
    - Used for: Progress indicator

15. **POST /api/v1/sync/queue**
    - Triggered by: Queuing actions while offline
    - Payload: `{ actions: [{ type, data, timestamp }] }`
    - Response: Actions queued
    - Background: Executed when online

### Touch Gestures

16. **POST /api/v1/content/{contentId}/gesture**
    - Triggered by: Swipe left (save) or right (dismiss)
    - Payload: `{ gesture: "swipe-left|swipe-right|long-press" }`
    - Response: Action executed
    - Used for: Quick actions via gestures

### Mobile-Specific Features

17. **POST /api/v1/location/share**
    - Triggered by: Sharing location for venue directions
    - Payload: `{ lat, lng, eventId }`
    - Response: Directions generated
    - Privacy: Temporary, not stored

18. **POST /api/v1/calendar/mobile-add**
    - Triggered by: Add to calendar on mobile
    - Payload: `{ eventId, attendeeId, nativeCalendar: true }`
    - Response: Calendar integration link
    - Platform-specific: iOS/Android calendar integration

---

## Navigation Map

Screen transitions triggered by actions and events:

### Primary Navigation (Bottom Nav Bar)

1. **🏠 Home Icon**
   - **Target**: Current event landing page
   - **Type**: Tab switch
   - **Context**: Main event view

2. **📚 Library Icon**
   - **Target**: Saved content library
   - **Type**: Tab switch
   - **Context**: Personal saved items

3. **🔍 Search Icon**
   - **Target**: Content discovery/search
   - **Type**: Tab switch
   - **Context**: Search and filter interface

4. **💾 Downloads Icon**
   - **Target**: Downloaded/offline content
   - **Type**: Tab switch
   - **Context**: Offline-available content

5. **👤 Profile Icon**
   - **Target**: Personal dashboard
   - **Type**: Tab switch
   - **Context**: User account and settings

### Event Landing Navigation

6. **☰ Menu Button**
   - **Target**: Slide-out menu
   - **Type**: Modal drawer from left
   - **Content**: Navigation links, settings, logout
   - **Gesture**: Swipe right from edge to open

7. **🔍 Search Icon** (top-right)
   - **Target**: Search page
   - **Type**: Full page transition
   - **Animation**: Slide up

8. **[REGISTER FREE] Button**
   - **Target**: Registration flow (Step 1/3)
   - **Type**: Full page transition
   - **Context**: Multi-step form

9. **[View All Speakers]**
   - **Target**: Full speaker list
   - **Type**: Full page with back button
   - **Content**: All speakers with bios

10. **[Full Schedule ↓]**
    - **Target**: Complete event schedule
    - **Type**: Expandable section or new page
    - **Content**: Session-by-session agenda

11. **[📚 Past Events]**
    - **Target**: Past events archive
    - **Type**: Full page transition
    - **Content**: Historical events list

12. **[🔍 Search Archive]**
    - **Target**: Content search (archive mode)
    - **Type**: Search page with archive filter
    - **Context**: 20+ years of content

13. **[👤 My Account]**
    - **Target**: Personal dashboard (Story 5.2)
    - **Type**: Full page transition
    - **Auth Check**: Requires login

### Content Discovery Navigation

14. **← Back Button**
    - **Target**: Previous screen
    - **Type**: Browser back or screen pop
    - **Context**: Navigation history

15. **Filter ⚙ Button**
    - **Target**: Filter modal
    - **Type**: Bottom sheet modal
    - **Content**: Topic, date, type filters
    - **Gesture**: Swipe down to dismiss

16. **Search Box Focus**
    - **Action**: Opens keyboard, shows suggestions
    - **Type**: Inline expansion
    - **Content**: Recent searches, autocomplete

17. **Result Card [View]**
    - **Target**: Content viewer
    - **Type**: Full page transition
    - **Content**: Responsive PDF/video viewer

18. **Result Card [Save]**
    - **Action**: Saves to library
    - **No Navigation**: Stays on search page
    - **Feedback**: Haptic feedback + checkmark animation
    - **Background**: Queues for offline download

19. **Result Card Swipe Left**
    - **Action**: Quick save gesture
    - **Feedback**: Card slides, saves, animates back
    - **Alternative**: Swipe right to dismiss/skip

20. **[Load More...]**
    - **Action**: Loads next page
    - **Type**: Infinite scroll
    - **Animation**: Smooth append

### Registration Flow Navigation

21. **Step 1 → [NEXT →]**
    - **Target**: Step 2/3 (Session Selection)
    - **Type**: Slide left transition
    - **Validation**: Validates before proceeding

22. **Step 2 → [NEXT →]**
    - **Target**: Step 3/3 (Review & Confirm)
    - **Type**: Slide left transition

23. **Step 3 → [COMPLETE]**
    - **Target**: Registration confirmation page
    - **Type**: Success animation + page transition
    - **Content**: Ticket, QR code, calendar add

24. **Registration ← Back**
    - **Target**: Previous step
    - **Type**: Slide right transition
    - **Draft**: Auto-saves progress

25. **Registration Complete**
    - **Target**: Ticket page with actions
    - **Actions**: [Add to Calendar], [Add to Wallet], [Share]
    - **Follow-up**: "Download our app" PWA prompt

### PWA Installation

26. **PWA Install Prompt**
    - **Trigger**: After 2nd visit or specific action
    - **Type**: Bottom sheet modal
    - **Actions**: [Install], [Not Now]
    - **Dismissed**: Won't show again for 7 days

27. **[Install] PWA**
    - **Action**: Browser-native install flow
    - **Platform-specific**: Different on iOS vs Android
    - **After Install**: Redirect to standalone app

28. **Launch from Home Screen**
    - **Mode**: Standalone (no browser chrome)
    - **Target**: Splash screen → Dashboard or last page
    - **Enhanced**: Full-screen, faster loading

### Offline & Sync

29. **Pull to Refresh**
    - **Gesture**: Pull down from top
    - **Action**: Refreshes current page data
    - **Feedback**: Loading spinner at top
    - **Offline**: Shows "Offline" message

30. **Go Offline**
    - **Trigger**: Network connection lost
    - **Feedback**: Offline banner appears
    - **Behavior**: Switches to cached content
    - **Actions**: Queue writes for later sync

31. **Come Back Online**
    - **Trigger**: Network connection restored
    - **Feedback**: "Back online" toast
    - **Action**: Auto-syncs queued actions
    - **Progress**: Shows sync status

32. **[Download for Offline]**
    - **Action**: Queues content for offline
    - **Type**: Background download via service worker
    - **Progress**: Shows in notification/status
    - **Complete**: "Available offline" badge

### Gestures & Touch Interactions

33. **Long Press Content Card**
    - **Action**: Opens context menu
    - **Options**: Save, Share, Report, Hide
    - **Feedback**: Haptic vibration

34. **Swipe Right on Bottom Nav**
    - **Action**: Activates "quick share" mode
    - **Behavior**: Next tap shares content
    - **Cancel**: Swipe left or tap X

35. **Pinch to Zoom** (in viewer)
    - **Supported**: PDF viewer, images
    - **Action**: Zooms content
    - **Gesture**: Standard pinch

### Error States & Special Cases

36. **On Registration Error**
    - **No Navigation**: Stays on form
    - **Feedback**: Shake animation, inline errors
    - **Action**: Highlight invalid fields

37. **On Network Timeout**
    - **Feedback**: "Slow connection" warning
    - **Actions**: [Retry], [Go Offline]

38. **On Storage Full**
    - **Feedback**: "Storage full" warning
    - **Actions**: [Manage Downloads], [Free Space]
    - **Target**: Download management page

### Mobile-Specific Navigation

39. **iOS: Pull Down from Top**
    - **Action**: Closes modal/page
    - **Type**: iOS-style dismissal gesture

40. **Android: Back Button**
    - **Action**: Goes to previous screen
    - **Behavior**: Follows navigation stack
    - **Exit**: Double-tap to exit app (from home)

41. **Landscape Mode**
    - **Adaptation**: Wider layout, side-by-side content
    - **No Navigation**: Stays on same page
    - **Enhanced**: Better for viewing content

42. **Split Screen Mode** (tablets)
    - **Support**: Responsive to half-screen
    - **Adaptation**: Compact layout
    - **No Navigation**: Continues current view

---
