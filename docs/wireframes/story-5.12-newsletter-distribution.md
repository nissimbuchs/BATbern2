# Story 5.12: Newsletter Distribution - Wireframe

**Epic:** Epic 5 - Enhanced Organizer Workflows
**Story:** 5.12 - Newsletter Distribution (Step 13)
**User Role:** Organizer
**Status:** Phase F - Communication & Logistics

---

## Overview

This wireframe defines the organizer interface for distributing newsletters to event attendees. The system provides 3 pre-defined templates (topic announcement, speaker lineup, final agenda) with automated recipient list management.

**Key Features:**
- 3 pre-defined newsletter templates
- Recipient list pulled from event registrations + newsletter subscribers
- Preview functionality before sending
- Send status tracking (sent/pending)
- Send history log

**MVP Scope:**
- ✅ AWS SES integration
- ✅ 3 pre-defined templates
- ✅ Pull recipients from registrations + newsletter subscribers
- ✅ Track "sent" status only
- ❌ No template editor (Phase 2)
- ❌ No open/click tracking (Phase 2)

---

## Screen 1: Event Newsletter Management

**Context:** Accessed from Event Detail page → "Communication" tab → "Newsletter" section

```
┌────────────────────────────────────────────────────────────────────────┐
│ Event: Spring BATbern 2025 - Nachhaltiges Bauen                       │
│ [Event Details] [Speakers] [Agenda] [Registrations] [Communication]   │
└────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│ Newsletter Distribution                                                 │
├────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│ Send Progressive Newsletter                                            │
│                                                                         │
│ ┌─────────────────────────────────────────────────────────────────┐  │
│ │ Template Selection                                              │  │
│ │                                                                 │  │
│ │ ○ Topic Announcement                                           │  │
│ │   Send when: Event created, topics finalized                   │  │
│ │   Status: ✓ Sent (2025-01-15, 142 recipients)                 │  │
│ │                                                                 │  │
│ │ ● Speaker Lineup Announcement                                  │  │
│ │   Send when: Speakers confirmed (1 month before event)         │  │
│ │   Status: ⧗ Pending                                            │  │
│ │   Recipients: 156 (142 registered + 14 new subscribers)        │  │
│ │                                                                 │  │
│ │ ○ Final Agenda                                                 │  │
│ │   Send when: Agenda finalized (2 weeks before event)           │  │
│ │   Status: ⧗ Pending                                            │  │
│ │                                                                 │  │
│ └─────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│ Selected Template: Speaker Lineup Announcement                         │
│                                                                         │
│ ┌─────────────────────────────────────────────────────────────────┐  │
│ │ Preview                                                         │  │
│ │                                                                 │  │
│ │ Subject: Unsere Referenten für Spring BATbern 2025             │  │
│ │                                                                 │  │
│ │ -------------------------------------------------------------- │  │
│ │                                                                 │  │
│ │ Liebe Architektinnen und Architekten,                          │  │
│ │                                                                 │  │
│ │ Wir freuen uns, die Referenten für unser Spring BATbern 2025   │  │
│ │ Event zum Thema "Nachhaltiges Bauen" bekannt zu geben:         │  │
│ │                                                                 │  │
│ │ • Dr. Anna Müller - Kreislaufwirtschaft im Bauwesen            │  │
│ │ • Prof. Thomas Schneider - CO2-neutrale Baustoffe              │  │
│ │ • Dipl.-Arch. Sarah Weber - Energieeffizienz in der Praxis     │  │
│ │                                                                 │  │
│ │ Event Details:                                                  │  │
│ │ Datum: 15. März 2025, 18:00 Uhr                                │  │
│ │ Ort: Zunfthaus zur Zimmerleuten, Bern                          │  │
│ │                                                                 │  │
│ │ [Jetzt anmelden]                                                │  │
│ │                                                                 │  │
│ │ Mit freundlichen Grüssen,                                       │  │
│ │ BATbern Organisationsteam                                       │  │
│ │                                                                 │  │
│ └─────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│ Recipients: 156                                                         │
│ • 142 from event registrations                                         │
│ • 14 from general newsletter subscribers                               │
│                                                                         │
│ [View Recipient List]                          [Send Newsletter]       │
│                                                                         │
├────────────────────────────────────────────────────────────────────────┤
│ Newsletter History                                                      │
│                                                                         │
│ ┌──────────────┬──────────────────────────┬────────────┬──────────┐  │
│ │ Date Sent    │ Template                 │ Recipients │ Status   │  │
│ ├──────────────┼──────────────────────────┼────────────┼──────────┤  │
│ │ 2025-01-15   │ Topic Announcement       │ 142        │ ✓ Sent   │  │
│ │ 14:30        │                          │            │          │  │
│ └──────────────┴──────────────────────────┴────────────┴──────────┘  │
│                                                                         │
└────────────────────────────────────────────────────────────────────────┘
```

**Interactions:**

1. **Template Selection**
   - Radio button selection of one of 3 templates
   - Clicking template shows preview
   - Status indicator shows if already sent or pending

2. **Preview**
   - Read-only preview of email content
   - Subject line and body text displayed
   - Template variables replaced with actual event data

3. **Recipient List**
   - Click "View Recipient List" to see modal with all recipients
   - Shows breakdown: registrations vs subscribers

4. **Send Newsletter**
   - Click "Send Newsletter" button
   - Confirmation dialog appears
   - After send, status updates to "✓ Sent" with timestamp and recipient count

5. **Newsletter History**
   - Table shows all previously sent newsletters
   - Timestamp, template name, recipient count, status

---

## Screen 2: Recipient List Modal

**Context:** Clicked "View Recipient List" from Newsletter Management screen

```
┌────────────────────────────────────────────────────────────────────────┐
│ Newsletter Recipients                                       [Close ×]  │
├────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│ Template: Speaker Lineup Announcement                                  │
│ Total Recipients: 156                                                  │
│                                                                         │
│ Source Breakdown:                                                      │
│ • Event Registrations: 142                                             │
│ • Newsletter Subscribers: 14                                           │
│                                                                         │
│ ┌─────────────────────────────────────────────────────────────────┐  │
│ │ Search: [________________]                         [Filter ▼]   │  │
│ ├─────────────────────────────────────────────────────────────────┤  │
│ │ ┌──────────────────────────────┬────────────┬─────────────────┐│  │
│ │ │ Email                        │ Source     │ Registration    ││  │
│ │ ├──────────────────────────────┼────────────┼─────────────────┤│  │
│ │ │ mueller@architektur-ag.ch    │ Event Reg  │ 2025-01-10      ││  │
│ │ │ schneider@bau-gmbh.ch        │ Event Reg  │ 2025-01-12      ││  │
│ │ │ weber@planer.ch              │ Event Reg  │ 2025-01-08      ││  │
│ │ │ info@batbern-partner.ch      │ Subscriber │ 2024-11-20      ││  │
│ │ │ ...                          │            │                 ││  │
│ │ └──────────────────────────────┴────────────┴─────────────────┘│  │
│ │                                                                 │  │
│ │ Showing 1-50 of 156                    [Previous] [1] [2] [3]  │  │
│ └─────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│                                              [Export CSV] [Close]      │
│                                                                         │
└────────────────────────────────────────────────────────────────────────┘
```

**Interactions:**

1. **Search**: Filter recipients by email address
2. **Filter**: Dropdown to filter by source (Event Reg / Subscriber / All)
3. **Export CSV**: Download recipient list as CSV
4. **Pagination**: Navigate through recipient pages (50 per page)
5. **Close**: Close modal and return to Newsletter Management screen

---

## Screen 3: Send Confirmation Dialog

**Context:** Clicked "Send Newsletter" button

```
┌────────────────────────────────────────────────────────────────────────┐
│ Confirm Newsletter Send                                     [Close ×]  │
├────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│ You are about to send the following newsletter:                        │
│                                                                         │
│ Template: Speaker Lineup Announcement                                  │
│ Recipients: 156 (142 registered + 14 subscribers)                      │
│ Event: Spring BATbern 2025 - Nachhaltiges Bauen                       │
│                                                                         │
│ Subject: Unsere Referenten für Spring BATbern 2025                    │
│                                                                         │
│ ⚠ This action cannot be undone. Newsletters will be sent immediately. │
│                                                                         │
│                                          [Cancel] [Send Newsletter]    │
│                                                                         │
└────────────────────────────────────────────────────────────────────────┘
```

**Interactions:**

1. **Cancel**: Close dialog without sending
2. **Send Newsletter**:
   - Triggers AWS SES send
   - Shows loading spinner
   - On success: Close dialog, show success toast, update status to "✓ Sent"
   - On failure: Show error toast with retry option

---

## Navigation Map

```
Event Detail Page
│
├─ Communication Tab
│  │
│  ├─ Newsletter Section
│  │  │
│  │  ├─ Template Selection (Screen 1)
│  │  │  │
│  │  │  ├─ View Recipient List → Recipient List Modal (Screen 2)
│  │  │  │
│  │  │  └─ Send Newsletter → Send Confirmation Dialog (Screen 3)
│  │  │
│  │  └─ Newsletter History Table
│  │
│  └─ Other Communication Tools (Future)
```

---

## API Requirements

### GET /api/events/{eventId}/newsletters

**Purpose:** Retrieve newsletter status and history for an event

**Response:**
```json
{
  "eventId": "evt_12345",
  "templates": [
    {
      "templateId": "tpl_topic_announcement",
      "name": "Topic Announcement",
      "status": "sent",
      "sentAt": "2025-01-15T14:30:00Z",
      "recipientCount": 142
    },
    {
      "templateId": "tpl_speaker_lineup",
      "name": "Speaker Lineup Announcement",
      "status": "pending",
      "recipientCount": 156
    },
    {
      "templateId": "tpl_final_agenda",
      "name": "Final Agenda",
      "status": "pending",
      "recipientCount": null
    }
  ],
  "history": [
    {
      "newsletterId": "nwsl_67890",
      "templateId": "tpl_topic_announcement",
      "sentAt": "2025-01-15T14:30:00Z",
      "recipientCount": 142,
      "status": "sent"
    }
  ]
}
```

---

### GET /api/events/{eventId}/newsletters/recipients

**Purpose:** Retrieve recipient list for newsletter

**Query Parameters:**
- `templateId` (required): Template ID
- `page` (optional): Page number (default: 1)
- `limit` (optional): Results per page (default: 50)
- `source` (optional): Filter by source (registration/subscriber/all)

**Response:**
```json
{
  "eventId": "evt_12345",
  "templateId": "tpl_speaker_lineup",
  "totalRecipients": 156,
  "sourceBreakdown": {
    "registrations": 142,
    "subscribers": 14
  },
  "recipients": [
    {
      "email": "mueller@architektur-ag.ch",
      "source": "registration",
      "registeredAt": "2025-01-10T10:15:00Z"
    },
    {
      "email": "schneider@bau-gmbh.ch",
      "source": "registration",
      "registeredAt": "2025-01-12T09:30:00Z"
    },
    {
      "email": "info@batbern-partner.ch",
      "source": "subscriber",
      "subscribedAt": "2024-11-20T14:00:00Z"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 50,
    "totalPages": 4
  }
}
```

---

### POST /api/events/{eventId}/newsletters/preview

**Purpose:** Generate newsletter preview with event data

**Request:**
```json
{
  "templateId": "tpl_speaker_lineup"
}
```

**Response:**
```json
{
  "templateId": "tpl_speaker_lineup",
  "subject": "Unsere Referenten für Spring BATbern 2025",
  "bodyText": "Liebe Architektinnen und Architekten,\n\nWir freuen uns...",
  "bodyHtml": "<html>...</html>"
}
```

---

### POST /api/events/{eventId}/newsletters/send

**Purpose:** Send newsletter to recipients via AWS SES

**Request:**
```json
{
  "templateId": "tpl_speaker_lineup"
}
```

**Response:**
```json
{
  "newsletterId": "nwsl_67891",
  "status": "sent",
  "sentAt": "2025-02-15T10:45:00Z",
  "recipientCount": 156,
  "sesMessageId": "0100018d1234abcd-12345678-1234-1234-1234-123456789abc-000000"
}
```

**Error Response (SES failure):**
```json
{
  "error": "SEND_FAILED",
  "message": "Failed to send newsletter via AWS SES",
  "sesError": "MessageRejected: Email address is not verified."
}
```

---

## Validation Rules

1. **Template Selection**
   - At least one template must be selected before preview or send
   - Cannot send same template twice (unless manual override by admin)

2. **Recipient Count**
   - Must have at least 1 recipient to send newsletter
   - Warning if recipient count < 10 (potential configuration issue)

3. **Send Timing**
   - Topic Announcement: Can send anytime after event created
   - Speaker Lineup: Can only send when >= 1 speaker confirmed
   - Final Agenda: Can only send when agenda status = "finalized"

4. **AWS SES Integration**
   - Email domain must be verified in AWS SES
   - Sender email must be verified
   - Respect SES sending limits (14 emails/second in production)

---

## Edge Cases

1. **No Recipients**
   - Show warning: "No recipients found. Ensure event has registrations or newsletter subscribers."
   - Disable "Send Newsletter" button

2. **SES Send Failure**
   - Show error toast with retry option
   - Log failure to newsletter history with status "failed"
   - Allow manual retry

3. **Template Already Sent**
   - Show warning: "This template was already sent on [date]. Send again?"
   - Require confirmation to resend

4. **Large Recipient List (>1000)**
   - Show warning: "Sending to 1,234 recipients. This may take several minutes."
   - Implement batch sending to respect SES limits

5. **Event Registration Changes After Preview**
   - Recalculate recipient count on send (not from cached preview)
   - Show updated recipient count in confirmation dialog

---

## Internationalization (i18n)

**FROM MVP LAUNCH (Day 1):**

### Language Support
- **Primary**: German (de-CH) - Swiss German locale (default)
- **Secondary**: English (en-US)
- **Language Selector**: Header shows `🌐 DE ▼ | EN` for language switching

### UI Text Translation
All interface text translated in both languages:
- Newsletter template names
- Status indicators (Sent/Pending)
- Button labels (Send Newsletter, View Recipient List, Export CSV)
- Table headers (Date Sent, Template, Recipients, Status)
- Confirmation dialog text
- Success/error toast messages

### Email Templates
- **Bilingual AWS SES Templates**: All 3 newsletter templates exist in both German and English
- **Language Detection**: Newsletter sent in recipient's preferred language (from user profile `preferredLanguage` field)
- **Fallback**: German (de-CH) if no preference set
- **Template IDs**:
  - `newsletter_topic_announcement_de` / `newsletter_topic_announcement_en`
  - `newsletter_speaker_lineup_de` / `newsletter_speaker_lineup_en`
  - `newsletter_final_agenda_de` / `newsletter_final_agenda_en`

### Translation Keys
```javascript
// Newsletter Management
newsletter.title = "Newsletter Distribution" | "Newsletter-Versand"
newsletter.template.topic = "Topic Announcement" | "Themenankündigung"
newsletter.template.speakers = "Speaker Lineup Announcement" | "Referenten-Bekanntgabe"
newsletter.template.agenda = "Final Agenda" | "Endgültige Agenda"
newsletter.status.sent = "Sent" | "Gesendet"
newsletter.status.pending = "Pending" | "Ausstehend"
newsletter.recipients.total = "Recipients" | "Empfänger"
newsletter.recipients.registrations = "from event registrations" | "aus Event-Registrierungen"
newsletter.recipients.subscribers = "from general newsletter subscribers" | "aus Newsletter-Abonnenten"
newsletter.button.send = "Send Newsletter" | "Newsletter senden"
newsletter.button.viewRecipients = "View Recipient List" | "Empfängerliste anzeigen"
newsletter.button.exportCsv = "Export CSV" | "CSV exportieren"
newsletter.confirm.title = "Confirm Newsletter Send" | "Newsletter-Versand bestätigen"
newsletter.confirm.message = "This action cannot be undone. Newsletters will be sent immediately." | "Diese Aktion kann nicht rückgängig gemacht werden. Newsletter werden sofort versendet."
newsletter.success = "Newsletter sent successfully" | "Newsletter erfolgreich gesendet"
newsletter.error = "Failed to send newsletter" | "Fehler beim Senden des Newsletters"
```

### Implementation
- **Framework**: react-i18next with i18next-browser-languagedetector
- **Translation Files**:
  - `/locales/de/newsletter.json`
  - `/locales/en/newsletter.json`
- **Dynamic Switching**: Language change applies immediately without page reload
- **Namespace**: `newsletter` namespace for all newsletter-related translations
- **Date Formatting**: Use `date-fns` with locale support for "Date Sent" column
  - German: "15.02.2025, 14:30"
  - English: "02/15/2025, 2:30 PM"

### Recipient Language Preferences
- **Per-Recipient Language**: Each recipient receives newsletter in their preferred language
- **Batch Sending**: System sends newsletters in batches grouped by language
  - Example: 100 German recipients → `newsletter_topic_announcement_de`
  - Example: 42 English recipients → `newsletter_topic_announcement_en`
- **Mixed Language Sends**: Single "Send Newsletter" action triggers multiple SES template sends
- **Status Tracking**: Newsletter history shows language breakdown:
  - "Sent to 100 (de), 42 (en)"

---

## Future Enhancements (Phase 2)

- **Template Editor**: Visual template editor for custom newsletters
- **Open/Click Tracking**: Integration with AWS SES for open/click metrics
- **A/B Testing**: Send different subject lines to test engagement
- **Scheduled Sends**: Schedule newsletter for future send time
- **Custom Recipient Lists**: Manually select/exclude recipients
- **Attachment Support**: Attach PDFs (agenda, sponsor materials)
- **French Support**: Add French language option for bilingual Switzerland

---

## Related Stories

- **Story 5.1**: Event Type Definition (defines when newsletters should be sent)
- **Story 5.10**: Progressive Publishing Engine (publishes content used in newsletters)
- **Story 5.11**: Agenda Finalization (triggers final agenda newsletter)
- **Epic 2 Story 2.9**: User Registration (provides newsletter subscribers)
- **Epic 4 Story 4.6**: Event Registration (provides event recipients)

---

## Accessibility Notes

- All interactive elements must be keyboard accessible
- Screen reader support for status indicators (sent/pending)
- Color-blind safe status colors (use icons + text, not just color)
- Preview must be readable with screen readers
- Confirmation dialog must trap focus (cannot interact with background)

---

## Responsive Behavior

- **Desktop (>1024px)**: Full 2-column layout (preview on right)
- **Tablet (768-1024px)**: Single column, preview below template selection
- **Mobile (<768px)**: Stacked layout, preview as collapsible section

---

## Testing Checklist

- [ ] Template selection updates preview correctly
- [ ] Recipient count calculated from registrations + subscribers
- [ ] Preview shows correct event data substitution
- [ ] Send confirmation dialog shows correct details
- [ ] AWS SES integration sends emails successfully
- [ ] Newsletter history table updates after send
- [ ] Status indicators show correct state (sent/pending)
- [ ] Recipient list modal displays all recipients
- [ ] CSV export contains all recipients
- [ ] Error handling for SES failures
- [ ] Prevent duplicate sends (with override option)
- [ ] Validate send timing rules (speaker lineup requires speakers)
