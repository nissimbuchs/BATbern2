# Platform Features

> **Last Updated:** 2026-11-13

## Overview

BATbern provides a comprehensive set of features designed to streamline event management for organizers. This section covers the platform's key capabilities that enhance productivity, improve decision-making, and automate common workflows.

## Feature Categories

### Data Visualization & Analytics
- **[Topic Heat Maps](heat-maps.md)** - Visual exploration of historical topic patterns `[IMPLEMENTED]`
- **[Event Analytics](analytics.md)** - Tabbed analytics dashboard + partner attendance analytics `[IMPLEMENTED]`

### Event Operations
- **[Cockpit and Event Day](cockpit-and-event-day.md)** - Attention cards per event, event-day controls, Live Control, event-day walkthrough `[IMPLEMENTED]`
- **[The Event Page](../entity-management/event-page.md)** - All tabs of an event, registrations, waitlist and name badges `[IMPLEMENTED]`

### Communication & Notifications
- **[Notification System](notifications.md)** - Email notifications, newsletter, and inbound email handling `[IMPLEMENTED]`
- **[Event mail page](event-mail.md)** (Mail Center) - What went out for an event, delivery results, "+ New mail", the Mail page across all events `[IMPLEMENTED]`
- Email templates for speaker outreach and attendee communications `[IMPLEMENTED]`
- In-app notifications: the bell in the top bar opens the Notifications page (`/organizer/notifications`) `[IMPLEMENTED]`. The bell shows no unread count.
- "What's new" for every logged-in user `[IMPLEMENTED]`: a card at the top of the participant dashboard (`/attendee/dashboard#whats-new`) lists the features added since the user last closed it, at most three; on a first visit the three most important. A dot on the user menu (public site and portal) says something is new, the menu item "What's new" opens the full list (`#whats-new`), new entries first and marked. Closing it stores the time with the user's settings, so it stays closed on every device. An earlier profile under the user's name is offered first. A new feature adds its entry to `web-frontend/src/features/whatsNew/catalogue.ts` (texts in `events.json`, all 10 locales) in its own PR.

### Media & Content
- **[Media & Content](media-and-content.md)** - Event photos, teaser images, AI-assisted content, moderator presentation `[IMPLEMENTED]`

### File Management
- **[File Uploads](file-uploads.md)** - Secure S3-backed document management `[IMPLEMENTED]`
- Logo and speaker material handling with validation `[IMPLEMENTED]`

### Workflow Automation
- State-based workflow progression with validation
- Automated speaker invitation tracking
- Task deadline reminders and escalations `[IMPLEMENTED]`

### Search & Discovery
- Full-text search across entities
- Advanced filtering with JSON syntax `[IN PROGRESS]`
- Autocomplete for companies and speakers

### Resource Management
- Resource expansion API for efficient data loading
- Caching strategies for improved performance
- Offline-capable data access `[PLANNED]`

## Feature Status Legend

- `[IMPLEMENTED]` - Feature is fully built and available for use
- `[IN PROGRESS]` - Feature is partially complete; some functionality works
- `[PLANNED]` - Feature is designed but not yet implemented

## How to Use This Section

Each feature page follows a consistent structure:

1. **Overview** - What the feature does and why it matters
2. **When to Use** - Practical scenarios and use cases
3. **How It Works** - Step-by-step instructions with examples
4. **Tips & Best Practices** - Expert guidance from organizers
5. **Troubleshooting** - Common issues and solutions
6. **Related Features** - Cross-references to connected functionality

## Quick Reference

| Feature | Status | Primary Use Case | Documentation |
|---------|--------|------------------|---------------|
| Cockpit & Event Day | `IMPLEMENTED` | What to do now for an event; presentation and Live Control on the day | [View →](cockpit-and-event-day.md) |
| Event Page | `IMPLEMENTED` | All tabs of an event; registrations, waitlist, name badges | [View →](../entity-management/event-page.md) |
| Mail Center | `IMPLEMENTED` | Event mail page, answering mail, + New mail, newsletters | [View →](event-mail.md) |
| Topic Heat Maps | `IMPLEMENTED` | Historical topic analysis for event planning | [View →](heat-maps.md) |
| File Uploads | `IMPLEMENTED` | Company logos, speaker materials | [View →](file-uploads.md) |
| Notifications | `IMPLEMENTED` | Email, newsletter & inbound email handling | [View →](notifications.md) |
| Analytics | `IMPLEMENTED` | Event performance metrics and insights | [View →](analytics.md) |
| Media & Content | `IMPLEMENTED` | Event photos, teasers, AI content, presentation | [View →](media-and-content.md) |

## Getting Started

If you're new to the platform, we recommend starting with:

1. **[File Uploads](file-uploads.md)** - Essential for managing company logos and speaker content
2. **[Topic Heat Maps](heat-maps.md)** - Useful for topic selection during event planning
3. **[Notification System](notifications.md)** - Understanding alert preferences and escalation

## Related Sections

- **[Entity Management](../entity-management/)** - Managing companies, users, events, partners, and speakers
- **[Workflow System](../workflow/)** - How features integrate into the 3 workflow systems
- **[Troubleshooting](../troubleshooting/)** - Solutions for common feature-related issues

---

**Next**: Explore [Topic Heat Maps](heat-maps.md) to learn about historical topic visualization →
