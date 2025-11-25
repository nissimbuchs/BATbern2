# Story 5.13: Moderation Assignment - Wireframe

**Epic:** Epic 5 - Enhanced Organizer Workflows
**Story:** 5.13 - Moderation Assignment (Step 14)
**User Role:** Organizer, Moderator
**Status:** Phase F - Communication & Logistics

---

## Overview

This wireframe defines the organizer interface for assigning a moderator to an event and the moderator's view of their assigned events. The system provides a simple dropdown selection with notification to the assigned moderator.

**Key Features:**
- Simple moderator selection dropdown (select from organizers)
- Notification to assigned moderator
- Moderator dashboard showing assigned events
- Event details visible to moderator

**MVP Scope:**
- ✅ Simple dropdown field (select from organizers)
- ✅ Notification to assigned moderator
- ✅ Moderator sees event dashboard
- ❌ No special briefing materials (Phase 2)
- ❌ No day-of tools (Phase 2)

---

## Screen 1: Moderator Assignment (Organizer View)

**Context:** Event Detail page → "Settings" tab → "Moderation" section

```
┌────────────────────────────────────────────────────────────────────────┐
│ Event: Spring BATbern 2025 - Nachhaltiges Bauen                       │
│ [Event Details] [Speakers] [Agenda] [Settings] [Communication]        │
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│ Event Settings                                                          │
├────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│ Basic Information                                                       │
│ ┌─────────────────────────────────────────────────────────────────┐  │
│ │ Event Type: [Hauptveranstaltung                          ▼]    │  │
│ │ Date:       [2025-03-15                   ] Time: [18:00]      │  │
│ │ Location:   [Zunfthaus zur Zimmerleuten, Bern            ]    │  │
│ └─────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│ Moderation                                                              │
│ ┌─────────────────────────────────────────────────────────────────┐  │
│ │ Moderator Assignment                                            │  │
│ │                                                                 │  │
│ │ Assign Moderator:                                              │  │
│ │ [Thomas Müller (mueller@batbern.ch)                      ▼]    │  │
│ │                                                                 │  │
│ │ Status: ✓ Assigned                                             │  │
│ │ Assigned: 2025-02-01 by Anna Schmidt                           │  │
│ │ Notification sent: 2025-02-01 14:30                            │  │
│ │                                                                 │  │
│ │ [Change Moderator] [Send Reminder]                             │  │
│ │                                                                 │  │
│ └─────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│ Contact Information                                                     │
│ ┌─────────────────────────────────────────────────────────────────┐  │
│ │ Primary Contact: [Anna Schmidt                            ▼]   │  │
│ │ Email:           [schmidt@batbern.ch                      ]    │  │
│ │ Phone:           [+41 31 123 45 67                        ]    │  │
│ └─────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│                                              [Cancel] [Save Changes]   │
│                                                                         │
└────────────────────────────────────────────────────────────────────────┘
```

**Interactions:**

1. **Moderator Dropdown**
   - Lists all users with "Organizer" role
   - Shows name and email address
   - Selecting moderator enables "Save Changes" button
   - Empty option: "-- Select Moderator --" (unassign moderator)

2. **Status Indicator**
   - Shows current assignment status
   - "✓ Assigned" (moderator assigned)
   - "⧗ Pending" (moderator assigned but not yet notified)
   - "○ Unassigned" (no moderator)

3. **Assignment Metadata**
   - Shows when moderator was assigned
   - Shows who assigned the moderator
   - Shows when notification email was sent

4. **Change Moderator Button**
   - Clears current selection
   - Allows new moderator selection
   - Confirmation dialog: "Send notification to new moderator?"

5. **Send Reminder Button**
   - Only visible if moderator assigned
   - Sends reminder email to moderator with event details
   - Shows toast: "Reminder sent to Thomas Müller"

6. **Save Changes**
   - Saves moderator assignment
   - Sends notification email to moderator
   - Shows success toast: "Moderator assigned. Notification sent to Thomas Müller."

---

## Screen 2: Moderator Assignment Notification Email

**Context:** Email sent to moderator when assigned to event

```
┌────────────────────────────────────────────────────────────────────────┐
│ From: BATbern Event Management <noreply@batbern.ch>                   │
│ To: mueller@batbern.ch                                                 │
│ Subject: Sie wurden als Moderator für Spring BATbern 2025 zugeteilt   │
└────────────────────────────────────────────────────────────────────────┘

Guten Tag Thomas Müller,

Sie wurden als Moderator für die folgende Veranstaltung zugeteilt:

Event: Spring BATbern 2025 - Nachhaltiges Bauen
Datum: 15. März 2025, 18:00 Uhr
Ort: Zunfthaus zur Zimmerleuten, Bern

Aufgaben des Moderators:
• Begrüssung der Teilnehmer
• Vorstellung der Referenten
• Zeitmanagement während der Vorträge
• Moderation der Diskussionsrunde

Eventdetails anzeigen:
https://platform.batbern.ch/events/evt_12345/moderator-view

Bei Fragen wenden Sie sich bitte an:
Anna Schmidt (schmidt@batbern.ch)

Mit freundlichen Grüssen,
BATbern Event Management System

---
Diese E-Mail wurde automatisch generiert. Bitte antworten Sie nicht auf diese E-Mail.
```

---

## Screen 3: Moderator Dashboard

**Context:** Moderator logs in and sees their assigned events

```
┌────────────────────────────────────────────────────────────────────────┐
│ BATbern Event Management                              [Thomas Müller ▼]│
│ [Dashboard] [Events] [Profile]                                         │
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│ My Moderator Assignments                                                │
├────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│ Upcoming Events                                                         │
│                                                                         │
│ ┌─────────────────────────────────────────────────────────────────┐  │
│ │ Spring BATbern 2025 - Nachhaltiges Bauen                       │  │
│ │                                                                 │  │
│ │ Datum: 15. März 2025, 18:00 Uhr                                │  │
│ │ Ort: Zunfthaus zur Zimmerleuten, Bern                          │  │
│ │ Status: Agenda Finalized                                        │  │
│ │                                                                 │  │
│ │ Referenten: 3 confirmed                                         │  │
│ │ Teilnehmer: 142 registered                                      │  │
│ │                                                                 │  │
│ │ Kontakt: Anna Schmidt (schmidt@batbern.ch)                     │  │
│ │                                                                 │  │
│ │                          [View Event Details] [Download Agenda]│  │
│ └─────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│ ┌─────────────────────────────────────────────────────────────────┐  │
│ │ Autumn BATbern 2025 - Digitalisierung in der Architektur       │  │
│ │                                                                 │  │
│ │ Datum: 20. September 2025, 18:00 Uhr                           │  │
│ │ Ort: Kornhausforum, Bern                                        │  │
│ │ Status: Planning                                                │  │
│ │                                                                 │  │
│ │ Referenten: 2 confirmed, 1 pending                              │  │
│ │ Teilnehmer: 45 registered                                       │  │
│ │                                                                 │  │
│ │ Kontakt: Peter Weber (weber@batbern.ch)                        │  │
│ │                                                                 │  │
│ │                          [View Event Details]                   │  │
│ └─────────────────────────────────────────────────────────────────┘  │
│                                                                         │
├────────────────────────────────────────────────────────────────────────┤
│ Past Events                                                             │
│                                                                         │
│ ┌──────────────┬──────────────────────────┬────────────┬──────────┐  │
│ │ Date         │ Event                    │ Attendees  │ Status   │  │
│ ├──────────────┼──────────────────────────┼────────────┼──────────┤  │
│ │ 2024-09-15   │ Autumn BATbern 2024      │ 156        │ Complete │  │
│ │ 2024-03-20   │ Spring BATbern 2024      │ 148        │ Complete │  │
│ └──────────────┴──────────────────────────┴────────────┴──────────┘  │
│                                                                         │
└────────────────────────────────────────────────────────────────────────┘
```

**Interactions:**

1. **Upcoming Events Cards**
   - Shows all events where user is assigned as moderator
   - Sorted by event date (earliest first)
   - Color-coded status (Planning/Agenda Finalized/In Progress)

2. **View Event Details**
   - Navigates to Event Detail page (read-only for moderator)
   - Shows full agenda, speaker bios, registered attendees
   - Shows organizer contact information

3. **Download Agenda**
   - Only available when agenda status = "finalized"
   - Downloads PDF with full agenda and speaker information
   - Used for moderator preparation

4. **Past Events Table**
   - Shows historical moderator assignments
   - Clicking row navigates to archived event details

---

## Screen 4: Moderator Event Detail View

**Context:** Moderator clicks "View Event Details" from dashboard

```
┌────────────────────────────────────────────────────────────────────────┐
│ Event: Spring BATbern 2025 - Nachhaltiges Bauen                       │
│ [Overview] [Agenda] [Speakers] [Attendees]                            │
│                                                         Moderator View │
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│ Overview                                                                │
├────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│ Your Role: Moderator                                                   │
│                                                                         │
│ Event Information                                                       │
│ ┌─────────────────────────────────────────────────────────────────┐  │
│ │ Event: Spring BATbern 2025 - Nachhaltiges Bauen                │  │
│ │ Date: 15. März 2025                                             │  │
│ │ Time: 18:00 - 21:00 Uhr                                         │  │
│ │ Location: Zunfthaus zur Zimmerleuten, Limmatquai 40, Bern      │  │
│ │ Expected Attendees: 142 registered                              │  │
│ └─────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│ Moderator Responsibilities                                              │
│ ┌─────────────────────────────────────────────────────────────────┐  │
│ │ ☐ Review event agenda (see Agenda tab)                         │  │
│ │ ☐ Familiarize with speaker bios (see Speakers tab)             │  │
│ │ ☐ Prepare opening remarks                                      │  │
│ │ ☐ Prepare speaker introductions                                │  │
│ │ ☐ Prepare closing remarks                                      │  │
│ └─────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│ Organizer Contact                                                       │
│ ┌─────────────────────────────────────────────────────────────────┐  │
│ │ Primary Contact: Anna Schmidt                                   │  │
│ │ Email: schmidt@batbern.ch                                       │  │
│ │ Phone: +41 31 123 45 67                                         │  │
│ │                                                                 │  │
│ │ [Send Message]                                                  │  │
│ └─────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│                                                      [Download Agenda]  │
│                                                                         │
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│ Agenda                                                                  │
├────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│ ┌──────┬──────────────────────────────────────────────────────────┐  │
│ │ Time │ Session                                                  │  │
│ ├──────┼──────────────────────────────────────────────────────────┤  │
│ │ 18:00│ Begrüssung & Einleitung (Moderator: Thomas Müller)      │  │
│ │      │ • Welcome attendees                                      │  │
│ │      │ • Event overview                                         │  │
│ │      │ • Introduce first speaker                               │  │
│ ├──────┼──────────────────────────────────────────────────────────┤  │
│ │ 18:10│ Dr. Anna Müller - Kreislaufwirtschaft im Bauwesen       │  │
│ │      │ Abstract: Strategien für nachhaltiges Bauen...          │  │
│ │      │ Duration: 25 min presentation + 5 min Q&A               │  │
│ ├──────┼──────────────────────────────────────────────────────────┤  │
│ │ 18:40│ Prof. Thomas Schneider - CO2-neutrale Baustoffe         │  │
│ │      │ Abstract: Innovative Materialien für klimaneutrales...  │  │
│ │      │ Duration: 25 min presentation + 5 min Q&A               │  │
│ ├──────┼──────────────────────────────────────────────────────────┤  │
│ │ 19:10│ Pause & Networking                                       │  │
│ ├──────┼──────────────────────────────────────────────────────────┤  │
│ │ 19:30│ Dipl.-Arch. Sarah Weber - Energieeffizienz in Praxis   │  │
│ │      │ Abstract: Praxisbeispiele aus realisierten Projekten... │  │
│ │      │ Duration: 25 min presentation + 5 min Q&A               │  │
│ ├──────┼──────────────────────────────────────────────────────────┤  │
│ │ 20:00│ Diskussionsrunde (Moderator: Thomas Müller)             │  │
│ │      │ • Panel discussion with all speakers                    │  │
│ │      │ • Audience Q&A                                           │  │
│ ├──────┼──────────────────────────────────────────────────────────┤  │
│ │ 20:30│ Schlusswort & Apéro (Moderator: Thomas Müller)          │  │
│ │      │ • Thank speakers and attendees                          │  │
│ │      │ • Announce next event                                    │  │
│ └──────┴──────────────────────────────────────────────────────────┘  │
│                                                                         │
└────────────────────────────────────────────────────────────────────────┘
```

**Interactions:**

1. **Moderator View Badge**
   - Indicates this is read-only view for moderator
   - No edit capabilities

2. **Moderator Responsibilities Checklist**
   - Static checklist (not interactive in MVP)
   - Provides guidance on moderator tasks
   - Future: Make interactive with progress tracking

3. **Organizer Contact**
   - Click "Send Message" to email organizer
   - Opens default email client with pre-filled subject/recipient

4. **Download Agenda**
   - Downloads PDF with full agenda
   - Includes speaker bios and session details
   - Used for moderator preparation

5. **Agenda Tab**
   - Shows detailed time-blocked agenda
   - Highlights moderator responsibilities (Begrüssung, Diskussionsrunde, Schlusswort)
   - Read-only view

6. **Speakers Tab**
   - Shows speaker bios, photos, contact info
   - Helps moderator prepare introductions

7. **Attendees Tab**
   - Shows registered attendee count
   - Future: Show attendee list (privacy permitting)

---

## Navigation Map

```
Event Detail Page (Organizer)
│
├─ Settings Tab
│  │
│  └─ Moderation Section
│     │
│     ├─ Moderator Dropdown (Screen 1)
│     │  │
│     │  └─ Save → Sends Notification Email (Screen 2)
│     │
│     └─ Send Reminder → Sends Reminder Email

Moderator Dashboard (Screen 3)
│
└─ View Event Details → Moderator Event Detail View (Screen 4)
   │
   ├─ Overview Tab
   ├─ Agenda Tab
   ├─ Speakers Tab
   └─ Attendees Tab
```

---

## API Requirements

**Service:** Event Management Service

All moderator assignment functionality is part of the Event Management Service. The moderator is simply a field on the Event entity referencing a user with the Organizer role.

---

### PATCH /api/v1/events/{eventCode}

**Purpose:** Update event details including moderator assignment

**Request:**
```json
{
  "moderatorUserId": "john.doe"
}
```

**Response:**
```json
{
  "id": "BATbern56",
  "eventNumber": 56,
  "title": "Spring BATbern 2025 - Nachhaltiges Bauen",
  "eventDate": "2025-03-15T18:00:00Z",
  "status": "agenda_finalized",
  "organizerId": "anna.schmidt",
  "moderatorUserId": "john.doe",
  "moderatorAssignedAt": "2025-02-01T14:30:00Z",
  "moderatorAssignedBy": "anna.schmidt",
  "updatedAt": "2025-02-01T14:30:00Z"
}
```

**Notes:**
- `eventCode` uses meaningful identifier (e.g., "BATbern56") per ADR-003
- `moderatorUserId` is username (e.g., "john.doe"), not UUID, per Story 1.16.2
- Validation: `moderatorUserId` must reference a user with Organizer role
- Side effect: Triggers email notification to assigned moderator

**Error Response:**
```json
{
  "error": "INVALID_MODERATOR",
  "message": "User 'john.doe' does not have Organizer role",
  "code": "MODERATOR_NOT_ORGANIZER"
}
```

---

### POST /api/v1/events/{eventCode}/notifications/moderator-reminder

**Purpose:** Send reminder email to assigned moderator

**Response:**
```json
{
  "notificationId": "notif_12345",
  "eventId": "BATbern56",
  "recipientUserId": "john.doe",
  "recipientEmail": "mueller@batbern.ch",
  "notificationType": "MODERATOR_REMINDER",
  "sentAt": "2025-02-10T09:00:00Z",
  "status": "sent"
}
```

**Error Response:**
```json
{
  "error": "NO_MODERATOR_ASSIGNED",
  "message": "Event BATbern56 has no moderator assigned",
  "code": "MODERATOR_NOT_SET"
}
```

---

### GET /api/v1/events?moderatorUserId={username}

**Purpose:** Get all events where current user is assigned as moderator

**Query Parameters:**
- `moderatorUserId`: Filter events by moderator username (e.g., "john.doe")
- `status`: Filter by event status (optional)
- `timeframe`: Filter by timeframe (`upcoming`, `past`, `all`) (default: `all`)

**Response:**
```json
{
  "events": [
    {
      "id": "BATbern56",
      "eventNumber": 56,
      "title": "Spring BATbern 2025 - Nachhaltiges Bauen",
      "eventDate": "2025-03-15T18:00:00Z",
      "location": {
        "name": "Zunfthaus zur Zimmerleuten",
        "address": "Limmatquai 40, 8001 Bern"
      },
      "status": "agenda_finalized",
      "speakerCount": 3,
      "attendeeCount": 142,
      "organizerId": "anna.schmidt",
      "moderatorUserId": "john.doe",
      "moderatorAssignedAt": "2025-02-01T14:30:00Z"
    }
  ],
  "pagination": {
    "total": 2,
    "page": 1,
    "limit": 20
  }
}
```

**Usage Example:**
```javascript
// Get current user's moderator assignments
const response = await fetch('/api/v1/events?moderatorUserId=john.doe&timeframe=upcoming');
```

---

### GET /api/v1/events/{eventCode}

**Purpose:** Get full event details (includes moderator information for moderator view)

**Response:**
```json
{
  "id": "BATbern56",
  "eventNumber": 56,
  "title": "Spring BATbern 2025 - Nachhaltiges Bauen",
  "description": "Event zum Thema nachhaltiges Bauen...",
  "eventDate": "2025-03-15T18:00:00Z",
  "eventType": "HAUPTVERANSTALTUNG",
  "venue": {
    "name": "Zunfthaus zur Zimmerleuten",
    "address": "Limmatquai 40, 8001 Bern",
    "capacity": 200
  },
  "status": "agenda_finalized",
  "organizerId": "anna.schmidt",
  "organizerDetails": {
    "userId": "anna.schmidt",
    "name": "Anna Schmidt",
    "email": "schmidt@batbern.ch",
    "phone": "+41 31 123 45 67"
  },
  "moderatorUserId": "john.doe",
  "moderatorDetails": {
    "userId": "john.doe",
    "name": "Thomas Müller",
    "email": "mueller@batbern.ch"
  },
  "moderatorAssignedAt": "2025-02-01T14:30:00Z",
  "moderatorAssignedBy": "anna.schmidt",
  "sessions": [
    {
      "sessionId": "session_1",
      "time": "18:10",
      "title": "Kreislaufwirtschaft im Bauwesen",
      "abstract": "Strategien für nachhaltiges Bauen...",
      "duration": 30,
      "speaker": {
        "name": "Dr. Anna Müller",
        "bio": "Dr. Müller ist Expertin für...",
        "photo": "https://cdn.batbern.ch/speakers/mueller.jpg"
      }
    }
  ],
  "attendeeCount": 142,
  "capacity": 200
}
```

**Notes:**
- Standard event detail endpoint used by both organizers and moderators
- Moderator sees same data as organizers (read-only)
- Frontend determines UI based on user role

---

## Validation Rules

1. **Moderator Selection**
   - Only users with "Organizer" role can be assigned as moderators
   - Cannot assign same user as both organizer and moderator (warning only, not blocking)

2. **Notification**
   - Notification email sent immediately after assignment
   - If email send fails, show error but still save assignment
   - Organizer can manually resend notification via "Send Reminder"

3. **Unassignment**
   - Can unassign moderator at any time
   - Sends unassignment notification email to moderator
   - Confirmation dialog: "Remove Thomas Müller as moderator?"

---

## Edge Cases

1. **Moderator User Deleted**
   - If assigned moderator is deleted from system, show warning
   - "Assigned moderator (Thomas Müller) no longer active. Please reassign."
   - Allow reassignment without error

2. **Event Cancelled After Assignment**
   - Send cancellation email to moderator
   - Remove event from moderator dashboard

3. **Multiple Events Same Day**
   - Moderator dashboard shows all events
   - Warning if moderator assigned to events with overlapping times

4. **Email Send Failure**
   - Show error toast: "Failed to send notification email. Assignment saved. Use 'Send Reminder' to retry."
   - Log failure for debugging

5. **Moderator Without Email**
   - Block assignment if moderator user has no email address
   - Show error: "Cannot assign moderator without email address"

---

## Internationalization (i18n)

**FROM MVP LAUNCH (Day 1):**

### Language Support
- **Primary**: German (de-CH) - Swiss German locale (default)
- **Secondary**: English (en-US)
- **Language Selector**: Header shows `🌐 DE ▼ | EN` for language switching

### UI Text Translation
All interface text translated in both languages:
- Moderator assignment dropdown label
- Status indicators (Assigned/Pending/Unassigned)
- Button labels (Change Moderator, Send Reminder, Save Changes)
- Moderator dashboard headings (Upcoming Events, Past Events)
- Moderator responsibilities checklist
- Organizer contact section labels
- All notification and confirmation messages

### Email Notifications
- **Bilingual AWS SES Templates**: Moderator notification emails in both German and English
- **Language Detection**: Email sent in moderator's preferred language (from user profile `preferredLanguage` field)
- **Fallback**: German (de-CH) if no preference set
- **Template IDs**:
  - `moderator_assignment_notification_de`
  - `moderator_assignment_notification_en`
  - `moderator_reminder_de`
  - `moderator_reminder_en`

### Translation Keys
```javascript
// Moderator Assignment (Organizer View)
moderation.title = "Moderation" | "Moderation"
moderation.assignLabel = "Assign Moderator" | "Moderator zuweisen"
moderation.status.assigned = "Assigned" | "Zugeteilt"
moderation.status.pending = "Pending" | "Ausstehend"
moderation.status.unassigned = "Unassigned" | "Nicht zugeteilt"
moderation.assignedBy = "Assigned: {date} by {name}" | "Zugeteilt: {date} von {name}"
moderation.notificationSent = "Notification sent: {date}" | "Benachrichtigung gesendet: {date}"
moderation.button.change = "Change Moderator" | "Moderator ändern"
moderation.button.sendReminder = "Send Reminder" | "Erinnerung senden"
moderation.success.assigned = "Moderator assigned. Notification sent to {name}." | "Moderator zugeteilt. Benachrichtigung an {name} gesendet."
moderation.success.reminder = "Reminder sent to {name}" | "Erinnerung an {name} gesendet"

// Moderator Dashboard
moderator.dashboard.title = "My Moderator Assignments" | "Meine Moderationsaufgaben"
moderator.dashboard.upcoming = "Upcoming Events" | "Anstehende Events"
moderator.dashboard.past = "Past Events" | "Vergangene Events"
moderator.button.viewDetails = "View Event Details" | "Event-Details anzeigen"
moderator.button.downloadAgenda = "Download Agenda" | "Agenda herunterladen"
moderator.contact = "Contact" | "Kontakt"
moderator.attendees = "Attendees" | "Teilnehmer"
moderator.speakers = "Speakers" | "Referenten"
moderator.status = "Status" | "Status"

// Moderator Event Detail View
moderator.role = "Your Role: Moderator" | "Ihre Rolle: Moderator"
moderator.responsibilities.title = "Moderator Responsibilities" | "Moderationsaufgaben"
moderator.responsibilities.reviewAgenda = "Review event agenda" | "Event-Agenda prüfen"
moderator.responsibilities.reviewSpeakers = "Familiarize with speaker bios" | "Mit Referenten-Biografien vertraut machen"
moderator.responsibilities.openingRemarks = "Prepare opening remarks" | "Begrüssungsworte vorbereiten"
moderator.responsibilities.speakerIntros = "Prepare speaker introductions" | "Referenten-Vorstellungen vorbereiten"
moderator.responsibilities.closingRemarks = "Prepare closing remarks" | "Schlussworte vorbereiten"
moderator.organizer.contact = "Organizer Contact" | "Organisator-Kontakt"
moderator.button.sendMessage = "Send Message" | "Nachricht senden"
```

### Implementation
- **Framework**: react-i18next with i18next-browser-languagedetector
- **Translation Files**:
  - `/locales/de/moderation.json`
  - `/locales/en/moderation.json`
- **Dynamic Switching**: Language change applies immediately without page reload
- **Namespace**: `moderation` namespace for all moderator-related translations
- **Date Formatting**: Use `date-fns` with locale support
  - German: "15. März 2025, 18:00 Uhr"
  - English: "March 15, 2025, 6:00 PM"

### Email Template Content (Example)

**German (de-CH):**
```
Betreff: Sie wurden als Moderator für {eventTitle} zugeteilt

Guten Tag {moderatorName},

Sie wurden als Moderator für die folgende Veranstaltung zugeteilt:

Event: {eventTitle}
Datum: {eventDate}
Ort: {eventLocation}

Aufgaben des Moderators:
• Begrüssung der Teilnehmer
• Vorstellung der Referenten
• Zeitmanagement während der Vorträge
• Moderation der Diskussionsrunde

Eventdetails anzeigen:
{eventUrl}

Bei Fragen wenden Sie sich bitte an:
{organizerName} ({organizerEmail})

Mit freundlichen Grüssen,
BATbern Event Management System
```

**English (en-US):**
```
Subject: You have been assigned as moderator for {eventTitle}

Dear {moderatorName},

You have been assigned as moderator for the following event:

Event: {eventTitle}
Date: {eventDate}
Location: {eventLocation}

Moderator Responsibilities:
• Welcome attendees
• Introduce speakers
• Time management during presentations
• Moderate discussion panel

View event details:
{eventUrl}

For questions, please contact:
{organizerName} ({organizerEmail})

Best regards,
BATbern Event Management System
```

---

## Future Enhancements (Phase 2)

- **Moderator Briefing Materials**: Auto-generate moderator briefing PDF with speaker bios, agenda, Q&A guidelines (bilingual PDF support)
- **Day-of Tools**: Real-time agenda timer, speaker cue cards, Q&A tracking (bilingual UI)
- **Interactive Checklist**: Track moderator preparation tasks with completion status
- **Speaker Communication**: Direct messaging between moderator and speakers
- **Feedback Collection**: Post-event moderator feedback form (bilingual)
- **Moderator Availability**: Check moderator calendar before assignment
- **French Support**: Add French language option for trilingual Switzerland

---

## Related Stories

- **Story 5.10**: Progressive Publishing Engine (publishes agenda for moderator view)
- **Story 5.11**: Agenda Finalization (triggers moderator notification)
- **Epic 2 Story 2.1**: User Management (defines Organizer role)
- **Epic 4**: Public Website (publishes event details visible to moderator)

---

## Accessibility Notes

- Moderator dropdown must be keyboard accessible (arrow keys to navigate)
- Screen reader support for status indicators
- Email notifications must be accessible (plain text + HTML)
- Moderator dashboard must support screen readers
- Color-blind safe status colors

---

## Responsive Behavior

- **Desktop (>1024px)**: Full layout with sidebar
- **Tablet (768-1024px)**: Stacked layout
- **Mobile (<768px)**: Single column, simplified moderator dashboard

---

## Testing Checklist

- [ ] Moderator dropdown lists all users with Organizer role
- [ ] Assigning moderator sends notification email
- [ ] Notification email contains correct event details
- [ ] Moderator sees assigned events in dashboard
- [ ] Moderator can view event details (read-only)
- [ ] Moderator can download agenda PDF
- [ ] Unassigning moderator removes from dashboard
- [ ] Send reminder button triggers email
- [ ] Email send failures handled gracefully
- [ ] Assignment metadata (assigned by, assigned at) tracked correctly
- [ ] Past events shown in moderator dashboard
