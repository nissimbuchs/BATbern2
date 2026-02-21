# Story 5.14: Catering Coordination - Wireframe

**Epic:** Epic 5 - Enhanced Organizer Workflows
**Story:** 5.14 - Catering Coordination (Step 15)
**User Role:** Organizer
**Status:** Phase F - Communication & Logistics

---

## Overview

This wireframe defines the organizer interface for coordinating catering for events. The system provides a simple task reminder system with free-text notes for menu details and headcount estimation from registration data.

**Key Features:**
- Task reminder: "Contact caterer 1 month before event"
- Free-text notes field for menu details
- Registration count display for headcount estimate
- Task completion tracking

**MVP Scope:**
- ✅ Task reminder: "Contact caterer 1 month before"
- ✅ Free-text notes field for menu details
- ✅ Show registration count for headcount estimate
- ✅ Mark task complete when done
- ❌ No dietary preferences collection (user confirmed NOT NEEDED)
- ❌ No menu selection UI (Phase 2)
- ❌ No caterer email integration (Phase 2)

---

## Screen 1: Catering Coordination Dashboard

**Context:** Event Detail page → "Logistics" tab → "Catering" section

```
┌────────────────────────────────────────────────────────────────────────┐
│ Event: Spring BATbern 2025 - Nachhaltiges Bauen                       │
│ [Event Details] [Speakers] [Agenda] [Logistics] [Communication]       │
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│ Logistics                                                               │
├────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│ [Catering] [Venue Setup] [Materials] [Other]                          │
│                                                                         │
│ ┌─────────────────────────────────────────────────────────────────┐  │
│ │ Catering Coordination                                           │  │
│ │                                                                 │  │
│ │ Event Date: 15. März 2025, 18:00 Uhr                           │  │
│ │ Expected Attendees: 142 registered                              │  │
│ │                                                                 │  │
│ │ ┌───────────────────────────────────────────────────────────┐ │  │
│ │ │ Tasks                                                     │ │  │
│ │ ├───────────────────────────────────────────────────────────┤ │  │
│ │ │                                                           │ │  │
│ │ │ ☐ Contact caterer 1 month before event                   │ │  │
│ │ │   Due: 15. Februar 2025 (in 14 days)                     │ │  │
│ │ │   Status: ⚠ Due soon                                      │ │  │
│ │ │                                                           │ │  │
│ │ │ ☐ Confirm final headcount with caterer (1 week before)   │ │  │
│ │ │   Due: 8. März 2025                                       │ │  │
│ │ │   Status: Pending                                         │ │  │
│ │ │                                                           │ │  │
│ │ │ ☐ Confirm delivery time and setup                        │ │  │
│ │ │   Due: 8. März 2025                                       │ │  │
│ │ │   Status: Pending                                         │ │  │
│ │ │                                                           │ │  │
│ │ └───────────────────────────────────────────────────────────┘ │  │
│ │                                                                 │  │
│ │ ┌───────────────────────────────────────────────────────────┐ │  │
│ │ │ Caterer Information                                       │ │  │
│ │ ├───────────────────────────────────────────────────────────┤ │  │
│ │ │                                                           │ │  │
│ │ │ Caterer Name:                                            │ │  │
│ │ │ [Berner Apéro Service AG                            ]    │ │  │
│ │ │                                                           │ │  │
│ │ │ Contact Person:                                          │ │  │
│ │ │ [Frau Müller                                        ]    │ │  │
│ │ │                                                           │ │  │
│ │ │ Phone:                                                   │ │  │
│ │ │ [+41 31 987 65 43                                   ]    │ │  │
│ │ │                                                           │ │  │
│ │ │ Email:                                                   │ │  │
│ │ │ [mueller@apero-service.ch                           ]    │ │  │
│ │ │                                                           │ │  │
│ │ └───────────────────────────────────────────────────────────┘ │  │
│ │                                                                 │  │
│ │ ┌───────────────────────────────────────────────────────────┐ │  │
│ │ │ Menu Details                                              │ │  │
│ │ ├───────────────────────────────────────────────────────────┤ │  │
│ │ │                                                           │ │  │
│ │ │ [Apéro Riche Package:                                    │ │  │
│ │ │                                                           │ │  │
│ │ │ - Käseplatte (Emmentaler, Gruyère, Appenzeller)         │ │  │
│ │ │ - Aufschnittplatte                                       │ │  │
│ │ │ - Gemüse-Sticks mit Dips                                 │ │  │
│ │ │ - Canapés (Lachs, Trüffel, vegetarisch)                 │ │  │
│ │ │ - Brot und Butter                                        │ │  │
│ │ │                                                           │ │  │
│ │ │ Getränke:                                                 │ │  │
│ │ │ - Weisswein (2 Flaschen pro 10 Personen)                │ │  │
│ │ │ - Rotwein (1 Flasche pro 10 Personen)                   │ │  │
│ │ │ - Mineralwasser, Orangensaft                             │ │  │
│ │ │                                                           │ │  │
│ │ │ Geschätzter Preis: CHF 45.- pro Person                   │ │  │
│ │ │ Total (142 Personen): CHF 6'390.-                        │ │  │
│ │ │                                                           │ │  │
│ │ │ Lieferzeit: 17:30 Uhr (Setup bis 18:00)                 │ │  │
│ │ │                                                           │ │  │
│ │ │ Kontaktiert am: 15.02.2025                               │ │  │
│ │ │ Bestätigt am: 18.02.2025                                 │ │  │
│ │ │                                                           │ │  │
│ │ │ ]                                                         │ │  │
│ │ │                                                           │ │  │
│ │ └───────────────────────────────────────────────────────────┘ │  │
│ │                                                                 │  │
│ │ ┌───────────────────────────────────────────────────────────┐ │  │
│ │ │ Headcount Estimate                                        │ │  │
│ │ ├───────────────────────────────────────────────────────────┤ │  │
│ │ │                                                           │ │  │
│ │ │ Current Registrations: 142                               │ │  │
│ │ │ Estimated Walk-ins: [10                              ]   │ │  │
│ │ │ Total Headcount: 152                                     │ │  │
│ │ │                                                           │ │  │
│ │ │ ℹ Registration count updates automatically              │ │  │
│ │ │                                                           │ │  │
│ │ └───────────────────────────────────────────────────────────┘ │  │
│ │                                                                 │  │
│ │                                        [Save Changes]          │ │  │
│ │                                                                 │  │
│ └─────────────────────────────────────────────────────────────────┘  │
│                                                                         │
└────────────────────────────────────────────────────────────────────────┘
```

**Interactions:**

1. **Task Checklist**
   - Checkboxes for each catering task
   - Clicking checkbox marks task as complete
   - Status indicators:
     - ⚠ Due soon (within 2 weeks)
     - ⏰ Overdue (past due date)
     - ✓ Complete
     - ○ Pending (not due yet)
   - Due dates calculated automatically based on event date

2. **Caterer Information Fields**
   - Free-text input fields for caterer details
   - Optional fields (not required)
   - Saved on "Save Changes"

3. **Menu Details**
   - Large free-text area for menu notes
   - No structured format required (MVP = flexibility)
   - Organizer can paste caterer quote, menu options, pricing
   - Auto-saved on blur

4. **Headcount Estimate**
   - Current Registrations: Read-only, pulled from event registration data
   - Estimated Walk-ins: Editable number field (organizer's judgment)
   - Total Headcount: Calculated automatically (Registrations + Walk-ins)
   - Info tooltip: Registration count updates in real-time

5. **Save Changes**
   - Saves all catering information
   - Shows success toast: "Catering details saved"

---

## Screen 2: Task Completion Confirmation

**Context:** Organizer clicks checkbox to mark task complete

```
┌────────────────────────────────────────────────────────────────────────┐
│ Mark Task Complete                                          [Close ×]  │
├────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│ Task: Contact caterer 1 month before event                             │
│                                                                         │
│ Mark this task as complete?                                            │
│                                                                         │
│ Optional: Add completion note                                          │
│ [Called Frau Müller, confirmed Apéro Riche package for 150 persons.   │
│ Delivery at 17:30. Total CHF 6'750.- (incl. service)                  │
│                                                                         │
│ ]                                                                       │
│                                                                         │
│                                          [Cancel] [Mark Complete]      │
│                                                                         │
└────────────────────────────────────────────────────────────────────────┘
```

**Interactions:**

1. **Completion Note**
   - Optional free-text field
   - Timestamped and saved with task completion
   - Visible in task history

2. **Mark Complete**
   - Marks task as ✓ Complete
   - Saves completion timestamp and note
   - Shows success toast: "Task marked complete"

3. **Cancel**
   - Closes dialog without marking complete

---

## Screen 3: Task History Sidebar

**Context:** Click "View History" link in Tasks section

```
┌────────────────────────────────────────────────────────────────────────┐
│ Task History                                                [Close ×]  │
├────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│ ┌─────────────────────────────────────────────────────────────────┐  │
│ │ ✓ Contact caterer 1 month before event                         │  │
│ │                                                                 │  │
│ │ Completed: 15.02.2025, 14:30 by Anna Schmidt                   │  │
│ │                                                                 │  │
│ │ Note: Called Frau Müller, confirmed Apéro Riche package for    │  │
│ │ 150 persons. Delivery at 17:30. Total CHF 6'750.-              │  │
│ │                                                                 │  │
│ └─────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│ ┌─────────────────────────────────────────────────────────────────┐  │
│ │ ✓ Confirm final headcount with caterer (1 week before)         │  │
│ │                                                                 │  │
│ │ Completed: 08.03.2025, 10:15 by Peter Weber                    │  │
│ │                                                                 │  │
│ │ Note: Final headcount 152 (142 registered + 10 walk-ins).      │  │
│ │ Updated order to 155 portions (buffer).                         │  │
│ │                                                                 │  │
│ └─────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│ ┌─────────────────────────────────────────────────────────────────┐  │
│ │ ✓ Confirm delivery time and setup                              │  │
│ │                                                                 │  │
│ │ Completed: 08.03.2025, 10:20 by Peter Weber                    │  │
│ │                                                                 │  │
│ │ Note: Delivery confirmed 17:30. Caterer needs access to        │  │
│ │ kitchen. Setup time: 30 min.                                    │  │
│ │                                                                 │  │
│ └─────────────────────────────────────────────────────────────────┘  │
│                                                                         │
└────────────────────────────────────────────────────────────────────────┘
```

**Interactions:**

1. **Task History List**
   - Shows all completed tasks in chronological order (newest first)
   - Each task shows completion timestamp, completing user, and note
   - Read-only view

2. **Close**
   - Closes sidebar and returns to Catering Coordination dashboard

---

## Screen 4: Catering Reminder Notification

**Context:** Automated reminder 1 month before event (if task not complete)

```
┌────────────────────────────────────────────────────────────────────────┐
│ BATbern Event Management                              [Anna Schmidt ▼] │
│ [Dashboard] [Events] [Profile]                        🔔 Notifications │
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│ Notifications                                                           │
├────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│ ┌─────────────────────────────────────────────────────────────────┐  │
│ │ ⚠ Catering Reminder                            Today, 09:00     │  │
│ │                                                                 │  │
│ │ Event: Spring BATbern 2025 - Nachhaltiges Bauen                │  │
│ │ Date: 15. März 2025 (in 30 days)                               │  │
│ │                                                                 │  │
│ │ Task: Contact caterer 1 month before event                     │  │
│ │ Due: Today                                                      │  │
│ │                                                                 │  │
│ │ Expected attendees: 142 registered                              │  │
│ │                                                                 │  │
│ │                         [View Event Logistics] [Mark Complete]  │  │
│ └─────────────────────────────────────────────────────────────────┘  │
│                                                                         │
└────────────────────────────────────────────────────────────────────────┘
```

**Interactions:**

1. **Notification Card**
   - Shows catering task reminder
   - Includes event details and due date
   - Shows current registration count for headcount reference

2. **View Event Logistics**
   - Navigates to Event Detail → Logistics → Catering
   - Opens catering coordination dashboard (Screen 1)

3. **Mark Complete**
   - Opens task completion dialog (Screen 2)
   - Allows quick completion without navigating to event

---

## Screen 5: Dashboard Catering Overview (Organizer Dashboard)

**Context:** Organizer Dashboard showing all events with catering status

```
┌────────────────────────────────────────────────────────────────────────┐
│ BATbern Event Management                              [Anna Schmidt ▼] │
│ [Dashboard] [Events] [Profile]                                         │
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│ My Events                                                               │
├────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│ Filter: [All Events ▼]                Sort by: [Date ▼]               │
│                                                                         │
│ ┌─────────────────────────────────────────────────────────────────┐  │
│ │ Spring BATbern 2025 - Nachhaltiges Bauen                       │  │
│ │                                                                 │  │
│ │ Date: 15. März 2025, 18:00 Uhr                                 │  │
│ │ Status: Planning                                                │  │
│ │ Attendees: 142 registered                                       │  │
│ │                                                                 │  │
│ │ Catering: ⚠ 1 task due soon                                    │  │
│ │                                                                 │  │
│ │               [View Details] [View Logistics]                   │  │
│ └─────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│ ┌─────────────────────────────────────────────────────────────────┐  │
│ │ Autumn BATbern 2025 - Digitalisierung in der Architektur       │  │
│ │                                                                 │  │
│ │ Date: 20. September 2025, 18:00 Uhr                            │  │
│ │ Status: Planning                                                │  │
│ │ Attendees: 45 registered                                        │  │
│ │                                                                 │  │
│ │ Catering: ✓ All tasks complete                                 │  │
│ │                                                                 │  │
│ │               [View Details] [View Logistics]                   │  │
│ └─────────────────────────────────────────────────────────────────┘  │
│                                                                         │
└────────────────────────────────────────────────────────────────────────┘
```

**Interactions:**

1. **Catering Status Badge**
   - ⚠ X tasks due soon (orange)
   - ⏰ X tasks overdue (red)
   - ✓ All tasks complete (green)
   - ○ No tasks due (gray)

2. **View Logistics**
   - Navigates directly to Event Detail → Logistics → Catering
   - Quick access to catering coordination

---

## Navigation Map

```
Organizer Dashboard (Screen 5)
│
├─ Event Card → View Logistics
│  │
│  └─ Catering Coordination Dashboard (Screen 1)
│     │
│     ├─ Task Checkbox → Task Completion Dialog (Screen 2)
│     │
│     ├─ View History → Task History Sidebar (Screen 3)
│     │
│     └─ Save Changes → Saves all catering info

System Notifications (Screen 4)
│
├─ Catering Reminder → View Event Logistics → Screen 1
│
└─ Mark Complete → Task Completion Dialog (Screen 2)
```

---

## API Requirements

### GET /api/events/{eventId}/catering

**Purpose:** Retrieve catering coordination details for an event

**Response:**
```json
{
  "eventId": "evt_12345",
  "eventDate": "2025-03-15T18:00:00Z",
  "registrationCount": 142,
  "tasks": [
    {
      "taskId": "task_1",
      "title": "Contact caterer 1 month before event",
      "dueDate": "2025-02-15",
      "status": "completed",
      "completedAt": "2025-02-15T14:30:00Z",
      "completedBy": {
        "userId": "usr_12345",
        "name": "Anna Schmidt"
      },
      "note": "Called Frau Müller, confirmed Apéro Riche package..."
    },
    {
      "taskId": "task_2",
      "title": "Confirm final headcount with caterer (1 week before)",
      "dueDate": "2025-03-08",
      "status": "pending",
      "completedAt": null,
      "completedBy": null,
      "note": null
    }
  ],
  "caterer": {
    "name": "Berner Apéro Service AG",
    "contactPerson": "Frau Müller",
    "phone": "+41 31 987 65 43",
    "email": "mueller@apero-service.ch"
  },
  "menuDetails": "Apéro Riche Package:\n\n- Käseplatte...",
  "headcountEstimate": {
    "registrations": 142,
    "estimatedWalkins": 10,
    "total": 152
  }
}
```

---

### PUT /api/events/{eventId}/catering

**Purpose:** Update catering coordination details

**Request:**
```json
{
  "caterer": {
    "name": "Berner Apéro Service AG",
    "contactPerson": "Frau Müller",
    "phone": "+41 31 987 65 43",
    "email": "mueller@apero-service.ch"
  },
  "menuDetails": "Apéro Riche Package:\n\n- Käseplatte...",
  "estimatedWalkins": 10
}
```

**Response:**
```json
{
  "eventId": "evt_12345",
  "saved": true,
  "updatedAt": "2025-02-15T15:00:00Z"
}
```

---

### POST /api/events/{eventId}/catering/tasks/{taskId}/complete

**Purpose:** Mark catering task as complete

**Request:**
```json
{
  "note": "Called Frau Müller, confirmed Apéro Riche package for 150 persons..."
}
```

**Response:**
```json
{
  "taskId": "task_1",
  "status": "completed",
  "completedAt": "2025-02-15T14:30:00Z",
  "completedBy": {
    "userId": "usr_12345",
    "name": "Anna Schmidt"
  },
  "note": "Called Frau Müller, confirmed Apéro Riche package..."
}
```

---

### POST /api/events/{eventId}/catering/tasks/{taskId}/reopen

**Purpose:** Reopen completed task (if organizer needs to redo)

**Response:**
```json
{
  "taskId": "task_1",
  "status": "pending",
  "completedAt": null,
  "completedBy": null,
  "note": null
}
```

---

### GET /api/events/{eventId}/catering/tasks/history

**Purpose:** Get task completion history

**Response:**
```json
{
  "eventId": "evt_12345",
  "history": [
    {
      "taskId": "task_1",
      "title": "Contact caterer 1 month before event",
      "completedAt": "2025-02-15T14:30:00Z",
      "completedBy": {
        "userId": "usr_12345",
        "name": "Anna Schmidt"
      },
      "note": "Called Frau Müller, confirmed Apéro Riche package..."
    }
  ]
}
```

---

## Validation Rules

1. **Registration Count**
   - Read-only, pulled from event registration data
   - Updates in real-time as registrations come in

2. **Estimated Walk-ins**
   - Must be non-negative integer
   - Default: 0
   - Warning if > 20% of registrations (potential overestimate)

3. **Task Due Dates**
   - Calculated automatically based on event date
   - Cannot be manually changed (Phase 2 feature)

4. **Task Completion**
   - Only tasks with status "pending" can be marked complete
   - Completed tasks can be reopened if needed

---

## Edge Cases

1. **Event Rescheduled**
   - Task due dates recalculate automatically based on new event date
   - Show warning: "Event date changed. Task due dates updated."

2. **Low Registration Count**
   - If registrations < 20, show warning: "Low registration count. Confirm caterer minimum order quantity."

3. **High Walk-in Estimate**
   - If estimated walk-ins > 20% of registrations, show info tooltip: "High walk-in estimate. Consider increasing order to avoid shortages."

4. **Task Overdue**
   - Task status changes to ⏰ Overdue
   - Notification sent to event organizer
   - Highlighted in red on dashboard

5. **No Caterer Information**
   - If caterer fields empty, show info message: "Add caterer information to track catering coordination."
   - Not blocking - organizer can skip if catering not needed

---

## Internationalization (i18n)

**FROM MVP LAUNCH (Day 1):**

### Language Support
- **Primary**: German (de-CH) - Swiss German locale (default)
- **Secondary**: English (en-US)
- **Language Selector**: Header shows `🌐 DE ▼ | EN` for language switching

### UI Text Translation
All interface text translated in both languages:
- Task titles and descriptions
- Status indicators (Due Soon/Overdue/Complete/Pending)
- Field labels (Caterer Name, Contact Person, Phone, Email, Menu Details)
- Headcount section labels (Current Registrations, Estimated Walk-ins, Total Headcount)
- Button labels (Save Changes, Mark Complete, View History)
- Table headers (Date Sent, Task, Status)
- Success/error toast messages
- Notification messages

### Translation Keys
```javascript
// Catering Coordination
catering.title = "Catering Coordination" | "Catering-Koordination"
catering.eventDate = "Event Date" | "Event-Datum"
catering.expectedAttendees = "Expected Attendees" | "Erwartete Teilnehmer"
catering.registered = "registered" | "registriert"

// Tasks
catering.tasks.title = "Tasks" | "Aufgaben"
catering.tasks.contact = "Contact caterer 1 month before event" | "Caterer 1 Monat vor Event kontaktieren"
catering.tasks.confirmHeadcount = "Confirm final headcount with caterer (1 week before)" | "Finale Teilnehmerzahl mit Caterer bestätigen (1 Woche vorher)"
catering.tasks.confirmDelivery = "Confirm delivery time and setup" | "Lieferzeit und Aufbau bestätigen"
catering.tasks.due = "Due" | "Fällig"
catering.tasks.dueSoon = "Due soon" | "Bald fällig"
catering.tasks.overdue = "Overdue" | "Überfällig"
catering.tasks.complete = "Complete" | "Erledigt"
catering.tasks.pending = "Pending" | "Ausstehend"
catering.tasks.inDays = "in {days} days" | "in {days} Tagen"

// Caterer Information
catering.caterer.title = "Caterer Information" | "Caterer-Informationen"
catering.caterer.name = "Caterer Name" | "Caterer-Name"
catering.caterer.contact = "Contact Person" | "Kontaktperson"
catering.caterer.phone = "Phone" | "Telefon"
catering.caterer.email = "Email" | "E-Mail"

// Menu Details
catering.menu.title = "Menu Details" | "Menü-Details"
catering.menu.placeholder = "Enter menu details, pricing, delivery time, etc." | "Menü-Details, Preise, Lieferzeit usw. eingeben"

// Headcount
catering.headcount.title = "Headcount Estimate" | "Teilnehmerzahl-Schätzung"
catering.headcount.registrations = "Current Registrations" | "Aktuelle Registrierungen"
catering.headcount.walkins = "Estimated Walk-ins" | "Geschätzte Walk-ins"
catering.headcount.total = "Total Headcount" | "Gesamtteilnehmerzahl"
catering.headcount.info = "Registration count updates automatically" | "Registrierungsanzahl wird automatisch aktualisiert"

// Actions
catering.button.save = "Save Changes" | "Änderungen speichern"
catering.button.markComplete = "Mark Complete" | "Als erledigt markieren"
catering.button.viewHistory = "View History" | "Verlauf anzeigen"
catering.button.cancel = "Cancel" | "Abbrechen"

// Task Completion
catering.complete.title = "Mark Task Complete" | "Aufgabe als erledigt markieren"
catering.complete.question = "Mark this task as complete?" | "Diese Aufgabe als erledigt markieren?"
catering.complete.note = "Optional: Add completion note" | "Optional: Abschlussnotiz hinzufügen"
catering.complete.notePlaceholder = "E.g., Called caterer, confirmed menu..." | "Z.B. Caterer angerufen, Menü bestätigt..."

// Messages
catering.success.saved = "Catering details saved" | "Catering-Details gespeichert"
catering.success.taskComplete = "Task marked complete" | "Aufgabe als erledigt markiert"
catering.error.save = "Failed to save catering details" | "Fehler beim Speichern der Catering-Details"

// History
catering.history.title = "Task History" | "Aufgabenverlauf"
catering.history.completed = "Completed" | "Erledigt"
catering.history.by = "by" | "von"
catering.history.note = "Note" | "Notiz"

// Warnings
catering.warning.lowRegistrations = "Low registration count. Confirm caterer minimum order quantity." | "Geringe Registrierungsanzahl. Mindestbestellmenge beim Caterer prüfen."
catering.warning.highWalkins = "High walk-in estimate. Consider increasing order to avoid shortages." | "Hohe Walk-in-Schätzung. Bestellung erhöhen, um Engpässe zu vermeiden."
```

### Implementation
- **Framework**: react-i18next with i18next-browser-languagedetector
- **Translation Files**:
  - `/locales/de/catering.json`
  - `/locales/en/catering.json`
- **Dynamic Switching**: Language change applies immediately without page reload
- **Namespace**: `catering` namespace for all catering-related translations
- **Date Formatting**: Use `date-fns` with locale support
  - German: "15. Februar 2025" (due date), "15.02.2025, 14:30" (completion timestamp)
  - English: "February 15, 2025" (due date), "02/15/2025, 2:30 PM" (completion timestamp)
- **Relative Time**: Use `date-fns` for "in X days" calculation
  - German: "in 14 Tagen", "vor 2 Stunden"
  - English: "in 14 days", "2 hours ago"

### Notification Messages
- **Task Due Soon** (sent 3 days before due date):
  - German: "Erinnerung: Catering-Aufgabe '{taskTitle}' fällig am {dueDate} für Event '{eventTitle}'"
  - English: "Reminder: Catering task '{taskTitle}' due on {dueDate} for event '{eventTitle}'"
- **Task Overdue**:
  - German: "Überfällig: Catering-Aufgabe '{taskTitle}' für Event '{eventTitle}'"
  - English: "Overdue: Catering task '{taskTitle}' for event '{eventTitle}'"

### Dashboard Integration
- **Catering Status Badge** (shown on event cards in organizer dashboard):
  - German: "⚠ 1 Aufgabe bald fällig" | "⏰ 1 Aufgabe überfällig" | "✓ Alle Aufgaben erledigt" | "○ Keine fälligen Aufgaben"
  - English: "⚠ 1 task due soon" | "⏰ 1 task overdue" | "✓ All tasks complete" | "○ No tasks due"

---

## Future Enhancements (Phase 2)

- **Dietary Preferences Collection**: Collect dietary restrictions from attendees (vegetarian, vegan, allergies) - **EXPLICITLY NOT NEEDED IN MVP**
- **Menu Selection UI**: Structured menu builder with predefined packages (bilingual menu items)
- **Caterer Database**: Reusable caterer contacts across events
- **Email Integration**: Send caterer emails directly from platform (bilingual email templates)
- **Budget Tracking**: Track catering costs vs. event budget (currency formatting de-CH: CHF, en-US: CHF)
- **Automated Headcount Updates**: Send updated headcount to caterer automatically
- **Vendor Portal**: Caterer can confirm orders via portal (bilingual caterer interface)
- **Invoice Upload**: Upload and track caterer invoices
- **French Support**: Add French language option for trilingual Switzerland

---

## Related Stories

- **Epic 4 Story 4.6**: Event Registration (provides registration count)
- **Story 5.1**: Event Type Definition (defines event date for task calculation)
- **Story 5.11**: Agenda Finalization (triggers final headcount confirmation)
- **Epic 2 Story 2.9**: User Management (provides notification recipients)

---

## Accessibility Notes

- Task checkboxes must be keyboard accessible
- Screen reader support for task status indicators
- Free-text fields must support screen reader descriptions
- Color-blind safe status colors (use icons + text)
- Task due dates must be announced by screen readers

---

## Responsive Behavior

- **Desktop (>1024px)**: Full 2-column layout (tasks left, details right)
- **Tablet (768-1024px)**: Single column, stacked sections
- **Mobile (<768px)**: Single column, collapsible sections

---

## Testing Checklist

- [ ] Registration count updates in real-time
- [ ] Task due dates calculated correctly based on event date
- [ ] Task completion saves timestamp and note
- [ ] Task status indicators display correctly (due soon/overdue/complete)
- [ ] Headcount total calculates correctly (registrations + walk-ins)
- [ ] Caterer information fields save correctly
- [ ] Menu details free-text field saves correctly
- [ ] Task history shows all completed tasks
- [ ] Notification sent when task becomes due
- [ ] Dashboard catering status badge shows correct state
- [ ] Event date change recalculates task due dates
- [ ] Task reopen functionality works
