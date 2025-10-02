# BATbern Attendee Experience - Wireframes Index

## Overview
This document serves as an index for all attendee interface wireframes, covering the complete attendee journey from discovering events to accessing historical content.

**Individual wireframe files have been separated by story for better organization and development workflow alignment.**

---

## Purpose

The attendee portal provides a comprehensive, engaging experience for event discovery and learning:
- **Current Event Discovery**: Prominent display of upcoming events (FR6)
- **Intelligent Content Search**: AI-powered discovery of 20+ years of content (FR13)
- **Easy Registration**: Frictionless event signup process
- **Personal Learning**: Customized dashboard and content recommendations
- **Community Engagement**: Ratings, reviews, and social features
- **Mobile-First**: Progressive Web App with offline capabilities

---

## Wireframe Files by Story

### Epic 1: Foundation & Infrastructure

**Story 1.18 - Basic Event Display**
- `story-1.18-historical-archive.md` - Historical Archive Browser

### Epic 2: Event Creation & Publishing

**Story 2.4 - Current Event Landing**
- `story-2.4-current-event-landing.md` - Current Event Landing Page
- `story-2.4-event-registration.md` - Event Registration Flow

### Epic 5: Attendee Experience

**Story 5.1 - Content Discovery**
- `story-5.1-content-discovery.md` - AI-Powered Content Discovery Engine

**Story 5.2 - Personal Engagement**
- `story-5.2-personal-dashboard.md` - Personal Attendee Dashboard

**Story 5.3 - Mobile PWA**
- `story-5.3-mobile-pwa.md` - Mobile PWA Experience
- `story-5.3-offline-content.md` - Offline Content & Download Manager

### Epic 7: Enhanced Features

**Story 7.4 - Community Features**
- `story-7.4-community-features.md` - Community Features & Engagement

---

## Key Features Covered

### FR6: Prominent Current Event Landing
Large, prominent display of current/upcoming events above the fold with complete logistics.

### FR13: Intelligent Content Discovery
AI-powered search and personalized recommendations across 20+ years of content.

### FR14: Personal Engagement Management
Newsletter subscriptions, content bookmarking, and download management.

### FR15: Mobile PWA Experience
Progressive Web App with offline capabilities and native app-like experience.

### FR16: Community Features
Content ratings, reviews, social sharing, and learning pathways.

---

## Attendee Journey Stages

### 1. Discovery (Story 2.4 & 5.1)
- Land on homepage with prominent current event
- Browse historical archive
- Search for specific content
- Discover recommended presentations

### 2. Registration (Story 2.4)
- Quick event registration
- Session preference selection
- Account creation (optional)
- Confirmation and calendar integration

### 3. Pre-Event (Story 5.2)
- Personal dashboard with upcoming events
- Event reminders and updates
- Browse speaker information
- Download pre-event materials

### 4. Event Day (Mobile)
- Mobile-optimized event information
- Real-time schedule updates
- Speaker contact information
- Venue directions

### 5. Post-Event (Story 5.2 & 7.4)
- Access event recordings
- Download presentations
- Rate and review sessions
- Share on social media

### 6. Continued Learning (Story 5.1 & 5.3)
- Explore related content
- Follow learning paths
- Offline content access
- Community participation

---

## User Types

### Anonymous Visitor
- Browse public content
- View current events
- Search historical archive
- Limited downloads

### Registered Attendee
- Full dashboard access
- Event registration
- Personalized recommendations
- Community features

### Recurring Member
- Saved preferences
- Learning path progress
- Community reputation
- Priority notifications

---

## Design Principles

1. **Content-First**: Valuable content immediately visible
2. **Mobile-Optimized**: Touch-friendly, responsive design
3. **Progressive Enhancement**: Works offline, better online
4. **Personalization**: Learn from user behavior
5. **Community-Driven**: Social features encourage engagement

---

## Navigation Structure

```
Attendee Portal
├── Home (Story 2.4)
│   ├── Current Event Hero
│   ├── Quick Registration
│   ├── Featured Speakers
│   └── Event Highlights
│
├── Discover (Story 5.1)
│   ├── AI-Powered Search
│   ├── Content Filters
│   ├── Recommendations
│   └── Learning Paths
│
├── My Account (Story 5.2)
│   ├── Personal Dashboard
│   ├── Upcoming Events
│   ├── Content Library
│   └── Preferences
│
├── Archive (Story 1.18)
│   ├── Browse by Year
│   ├── Browse by Topic
│   ├── Speaker Directory
│   └── Popular Content
│
├── Community (Story 7.4)
│   ├── Reviews & Ratings
│   ├── Discussion Forums
│   └── Social Sharing
│
└── Offline (Story 5.3)
    ├── Downloaded Content
    ├── Sync Settings
    └── Storage Management
```

---

## Technical Considerations

### Progressive Web App
- Service worker for offline functionality
- App manifest for installability
- Push notifications for updates
- Background sync for downloads

### Content Discovery
- Elasticsearch for full-text search
- AI/ML recommendations engine
- Semantic search capabilities
- Real-time suggestions

### Performance
- CDN for static assets
- Lazy loading images
- Virtual scrolling for large lists
- Aggressive caching strategy

### Accessibility
- WCAG 2.1 AA compliance
- Keyboard navigation
- Screen reader support
- High contrast mode

---

## Key User Interactions

### Event Registration
- One-click registration for logged-in users
- Multi-step wizard for new users
- Session preference selection
- Immediate confirmation

### Content Search
- Auto-complete suggestions
- Filter by topic, date, speaker
- Sort by relevance, date, rating
- Save search preferences

### Personal Dashboard
- Drag-and-drop widget arrangement
- Customizable content sections
- One-click actions
- Activity timeline

### Offline Mode
- Automatic content sync
- Manual download controls
- Storage management
- Conflict resolution

---

## Personalization Features

### AI Recommendations
- Based on viewing history
- Topic interest tracking
- Similar content suggestions
- Learning path recommendations

### Content Preferences
- Favorite topics
- Preferred speakers
- Content format preferences
- Notification settings

### Progress Tracking
- Learning path completion
- Certificates and badges
- Viewing statistics
- Engagement metrics

---

## Related Documentation

- **PRD**: See `docs/prd/epic-2-event-creation-publishing-stories.md`, `epic-5-attendee-experience-stories.md`, and `epic-7-enhanced-features-stories.md`
- **User Stories**: Individual story files in PRD folder
- **Coverage Report**: `wireframes-coverage-report.md`

---

## Notes

All detailed wireframes have been extracted into individual story-specific files for:
- Better alignment with development workflow
- Easier version control and reviews
- Clearer traceability to user stories
- Independent updates per feature

Each wireframe file is self-contained with complete ASCII art, interaction descriptions, and technical notes.