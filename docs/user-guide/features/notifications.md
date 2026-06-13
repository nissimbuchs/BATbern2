# Notification System

> **Last Updated:** 2026-06-13

## Overview

The BATbern notification system keeps organizers, speakers, partners, and the wider community informed through a broad, automated email system. Email is the platform's primary notification channel today:

- **Transactional email** - Speaker invitations/acceptances/reminders, registration confirmations, task deadline reminders, "slides are online" mailings
- **Calendar invites** - RFC 5545 `.ics` partner meeting invites with RSVP tracking
- **Newsletter** - Account-free subscription, organizer-driven sending with template selection, and a full subscriber-management admin page
- **Inbound email handling** - Reply-based unsubscribe / deregistration / attendance confirmation, SES forwarding & distribution lists, and bounce-driven list hygiene

The system is designed to reduce manual monitoring while respecting subscriber preferences, GDPR requirements, and SES sender reputation.

## Implementation Status

| Channel | Status | Details |
|---------|--------|---------|
| Transactional Email | ✅ `[IMPLEMENTED]` | Speaker invitations / acceptances / reminders (DE/EN), registration confirmations, task deadline reminders, "slides are online" mail |
| Newsletter System (Epic 10) | ✅ `[IMPLEMENTED]` | Account-free subscription, organizer sending with template selection, subscriber-management admin page |
| Inbound Email Handling (Epic 10) | ✅ `[IMPLEMENTED]` | Reply-based unsubscribe / cancel / accept, SES forwarding & distribution lists, bounce processing & list hygiene |
| Partner Calendar Invites (Epic 8) | ✅ `[IMPLEMENTED]` | `.ics` meeting invites with iCal RSVP reply parsing |
| In-App Notification Center | 📋 `[PLANNED]` | Real-time in-app notification feed with mark-as-read, priority levels, and preference rules |

---

## Implemented: Email Notifications

The following emails are sent automatically by the platform today. Email content is authored in **German and English** (the platform's first-class email languages); other locales fall back to English at render time.

### Speaker Invitation, Acceptance & Reminder Emails (Epic 6 / Epic 11)

Sent when an organizer invites a speaker to an event, when a speaker accepts, and as deadline reminders. Speakers log in to the speaker portal via **AWS Cognito** (email/password or "Continue with Google") to respond and submit materials — the original magic-link login was removed in Epic 11. See [Speaker Portal: Invitation & Response](../speaker-portal/invitation-response.md) for the full flow.

### Speaker Deadline Reminder Emails (Epic 6)

Three-tier escalation triggered automatically before the content submission deadline:

| Timing | Tone | Action |
|--------|------|--------|
| 1 month before | Friendly reminder | Links to submission portal |
| 2 weeks before | Moderate urgency | Emphasises deadline date |
| 3 days before | Urgent | If still no action, organizer is notified for follow-up |

### Registration Confirmation Emails (Epic 4)

Sent automatically when an attendee completes event registration. Includes event details (including the event's start and end times), a QR code for day-of check-in, and an `.ics` calendar attachment so attendees can add the event to their calendar with accurate times.

### Partner Calendar Invites (Epic 8)

RFC 5545 `.ics` calendar invites delivered via email to partners. Each file contains two `VEVENT` entries: the spring/autumn partner lunch and the BATbern event itself. See [Partner Portal: Meeting Coordination](../partner-portal/meetings.md).

### Task Deadline Reminders (Epic 5 / Epic 10)

Organizers receive email reminders for tasks approaching their due dates. Timing is controlled by the task template's trigger configuration (e.g., 1 day before deadline). See [Task System](../workflow/task-system.md).

### "The Slides Are Online" Mail (Epic 7)

About a day after an event, the platform auto-creates an organizer task to send a "slides are online" email to the event's active registrants. The organizer triggers the send with one click; the mail goes out in **DE + EN**, honours each recipient's email opt-out, and is protected by a double-send guard so registrants never receive it twice. This is part of the Epic 7 attendee-touchpoint set.

---

## Implemented: Newsletter System (Epic 10)

The platform includes an in-house newsletter system that replaced the previous Meetup.com / Hostpoint mailing lists. It is GDPR-aware and respects subscriber preferences end-to-end.

### Subscription

- **Account-free subscribe** — community members subscribe via a public `POST /api/v1/newsletter/subscribe` endpoint (no login). Re-subscribing a previously unsubscribed email reactivates the existing record.
- **Account settings** — logged-in users manage their subscription from the BATbern account settings page.
- **Registration opt-in** — subscriptions can also originate from event registration, tracked via a `source` field (`explicit` / `registration` / `account`).
- **Bot protection** — the public subscribe form is protected by Cloudflare Turnstile (Story 10.31) at the API gateway.

### Sending & Template Selection

Organizers send newsletters directly from the event newsletter tab:

- **Template dropdown** — choose any `NEWSLETTER`-category email template (e.g., general newsletter, event reminder, partner announcement) without a code deploy. Templates are managed in the admin **Email Templates** tab (TinyMCE for content, Monaco for layout).
- **Locale toggle** — switching DE↔EN re-filters the template list and the preview to the selected locale.
- **Preview & confirm** — a preview renders the chosen template; the confirmation dialog names the template and the recipient count before sending.

### Subscriber Management (Story 10.28)

A dedicated organizer-only **Newsletter Subscribers** page (top-level navigation) provides a paginated, searchable, sortable list of all subscribers, with management actions (unsubscribe, re-subscribe, delete) so the subscriber base can be audited without touching the database.

---

## Implemented: Inbound Email Handling (Epic 10)

### Reply-Based Unsubscribe, Deregistration & Acceptance (Story 10.17)

Subscribers and attendees can manage their preferences simply by **replying** to any BATbern email (no links to click — convenient on mobile). All outbound mail carries a `Reply-To: replies@batbern.ch` header, and an inbound handler (SES → S3 → SQS → EMS) routes the reply by its first line:

- **`UNSUBSCRIBE`** (incl. `abmelden` / `désinscription`) → unsubscribes the sender; a confirmation email is sent.
- **`CANCEL`** (incl. `deregister` / `absagen`) on a registration-confirmation reply → cancels the registration, fires waitlist promotion, and sends a cancellation confirmation.
- **`ACCEPT`** (incl. `bestätigen` / `confirmer`) on an event-coded email → confirms attendance for the matching active registration.
- Unrecognised replies are silently discarded; an anti-abuse rate limiter caps replies from a single sender.

### Email Forwarding & Distribution Lists (2026-03)

The platform provides serverless email forwarding for 6 inboxes: `ok@`, `info@`, `events@`, `partner@`, `support@`, and `batbernNN@batbern.ch`. Emails received at these addresses are automatically forwarded to the appropriate recipients based on their role or event registration status:

- **Dynamic recipient resolution** — Lambda resolves recipients via role-based and registration-based API calls
- **Sender exclusion** — Original sender is excluded from forwarding to prevent bounce loops
- **CC address forwarding** — CC recipients on inbound emails are recognised as mailing-list addresses and resolved through the same recipient-resolution pipeline; they are forwarded alongside the primary recipients
- **All mailing list variants recognised** — All `batbernNN@batbern.ch` series addresses (e.g. `batbern44@batbern.ch`) are detected and handled correctly, not only the latest series
- **Environment isolation** — Staging uses `replies@staging.batbern.ch` with `noreply@berner-architekten-treffen.ch`; production uses `replies@batbern.ch` with `noreply@batbern.ch`
- **Admin configuration** — Organizers manage forwarding rules via Admin Settings UI
- **Additional user emails** (Story 10.32) — A user can register additional email addresses on their profile. Those addresses then both **receive** fan-out mail destined for the user's primary email and are treated as **authorised senders** by the forwarder's sender-auth check (so mail sent from a legacy/shared/personal address is not silently dropped).

### Partner Meeting iCal RSVP Parsing (2026-03)

When partners reply to calendar invites (Accept / Decline / Tentative), the platform's inbound email handler automatically parses `METHOD:REPLY` ICS attachments and updates the RSVP status in the `partner_meeting_rsvps` table. Organizers see RSVP status in the Partner Meeting management UI.

### SES Bounce Processing & List Hygiene (Story 10.29)

Emails that permanently bounce or generate spam complaints are automatically suppressed from future newsletter sends, keeping BATbern's SES sender reputation healthy. A SES configuration set routes BOUNCE and COMPLAINT events through SNS → SQS to the event-management service, which records `bounce_type` and `bounce_count` on the affected subscriber and removes hard-bounced / complained addresses from active sends. The AWS account-level suppression list is also enabled for BOUNCE and COMPLAINT.

---

## Planned: In-App Notification Center

A real-time, in-app notification center (bell icon in the navigation bar) is designed but **not yet built**. The sections below document the intended behaviour — notification feed, priority levels, escalation chains, quiet hours, and per-category preference rules. Today, notifications are delivered by email (see the Implemented sections above). Note that the organizer dashboard's **Team Activity Feed** (Epic 5) provides a chronological log of organizer actions and is a separate, already-shipped capability — see [Analytics](analytics.md).

---

## When to Use This Feature

### Primary Use Cases

1. **Workflow Monitoring**
   - Receive alerts when events progress through workflow stages
   - Get notified when speakers respond to invitations
   - Track content submission deadlines

2. **Deadline Management**
   - Reminders for registration deadlines
   - Alerts for content review due dates
   - Escalations for overdue actions

3. **Collaboration Coordination**
   - Notifications when team members comment or make changes
   - Alerts for assigned tasks or reviews
   - Updates on partner meeting scheduling

4. **System Events**
   - Failed file uploads or processing errors
   - Authentication issues (token expiry, password reset)
   - Database maintenance or downtime schedules

### Recommended Configuration

**Default Settings** (recommended for most organizers):
- In-app notifications: Enabled for all event types
- Email notifications: Daily digest + urgent alerts only
- Escalation: 48 hours before critical deadlines

**Power User Settings**:
- In-app notifications: Enabled, grouped by event
- Email notifications: Immediate for assigned tasks only
- Escalation: Custom rules per workflow phase

## How It Works

### Notification Channels

#### 1. In-App Notifications

**Location**: Bell icon (🔔) in top navigation bar

**Features**:
- Real-time alerts without page refresh
- Grouped by event or context
- Mark as read/unread
- Quick actions (e.g., "Review Now", "Dismiss")
- 30-day retention

**Priority Indicators**:
- 🔴 **Critical** - Red badge, requires immediate action
- 🟠 **High** - Orange badge, action needed within 24 hours
- 🟡 **Medium** - Yellow badge, informational with optional action
- 🔵 **Low** - Blue badge, background updates

**Example In-App Notification**:
```
🟠 High Priority
Speaker Response Required - Event #45
3 speakers responded to invitations. Review and update status.
[Review Now] [Dismiss] [Snooze 1h]
Received: 2 hours ago
```

#### 2. Email Notifications

**Delivery Options**:
- **Immediate** - Sent within 5 minutes of trigger event
- **Hourly Digest** - Batched every hour (reduce email volume)
- **Daily Digest** - Single email with all updates at 8:00 AM
- **Weekly Summary** - Sunday evening recap of all activity

**Email Template Structure**:
```
Subject: [BATbern] [Priority] Brief description

---
Hi [Organizer Name],

[Notification message with context]

Quick Actions:
• Review Now: [Deep link to specific page]
• View Details: [Link to related entity]
• Snooze: [Link to snooze for 24h]

Event Context:
• Event: BATbern #45
• Current Step: 6 - Content Collection
• Deadline: 2024-12-15

Notification Settings: [Link to preferences]

---
BATbern Event Management Platform
```

#### 3. Escalation Alerts

**Triggered By**:
- Approaching deadlines (48 hours, 24 hours, 12 hours)
- Overdue actions (immediate notification)
- Critical errors (system issues, failed processing)

**Escalation Chain** (for overdue actions):
1. **T+0**: Initial notification to assigned organizer
2. **T+24h**: Reminder to assigned organizer + in-app escalation badge
3. **T+48h**: Email to assigned organizer + team lead (if configured)
4. **T+72h**: High-priority alert to all organizers for the event

**Override Options**:
- Snooze escalations (1h, 4h, 24h, custom)
- Delegate to another organizer
- Mark as "Working on it" (resets escalation timer)

### Notification Types

#### Workflow Events

| Trigger | Default Channel | Priority | Example |
|---------|----------------|----------|---------|
| Event advances to next step | In-app | Medium | "Event #45 advanced to Step 6: Content Collection" |
| Speaker responds to invitation | In-app + Email (immediate) | High | "3 speakers accepted invitations for Event #45" |
| Content submission received | In-app | Medium | "Speaker J. Smith submitted presentation materials" |
| Quality review completed | In-app + Email (digest) | Medium | "Event #45 passed quality threshold (12/10 speakers)" |

#### Deadline Reminders

| Deadline Type | Reminder Schedule | Priority |
|---------------|-------------------|----------|
| Registration deadline | 7 days, 3 days, 1 day | Medium → High |
| Content submission | 5 days, 2 days, 12 hours | Medium → Critical |
| Review completion | 3 days, 1 day, 4 hours | High → Critical |
| Publishing deadline | 48 hours, 24 hours | Critical |

#### System Notifications

| Event | Channel | Priority | User Control |
|-------|---------|----------|--------------|
| File upload failed | In-app + Email (immediate) | High | Cannot disable |
| Authentication error | In-app + Email (immediate) | Critical | Cannot disable |
| Scheduled maintenance | Email (48h notice) | Medium | Cannot disable |
| Feature release | In-app | Low | Can disable |

### Customizing Preferences

**Navigation**: Settings → Notifications

#### Global Settings

- **Notification Frequency**: Immediate, Hourly, Daily, Weekly
- **Quiet Hours**: No notifications between 10 PM - 7 AM (customizable)
- **Vacation Mode**: Pause all non-critical notifications
- **Consolidation**: Group similar notifications (e.g., "5 speakers responded" vs 5 individual alerts)

#### Per-Event Settings

Override global settings for specific events:
- **High-Priority Event**: Immediate notifications for all changes
- **Background Event**: Daily digest only
- **Archived Event**: No notifications (view-only mode)

#### Per-Category Settings

Customize by notification category:
- Workflow events: Immediate / Digest / Off
- Deadline reminders: Enabled / Off (not recommended)
- Team collaboration: Immediate / Digest / Off
- System events: Always on (cannot disable critical alerts)

### Managing Notifications

#### Bulk Actions

Select multiple notifications and:
- Mark all as read
- Archive all (moves to 30-day history)
- Snooze all for 1 hour / 4 hours / 24 hours
- Dismiss all (permanent deletion)

#### Filtering & Search

- **Filter by Priority**: Critical, High, Medium, Low
- **Filter by Event**: Show only notifications for Event #45
- **Filter by Type**: Workflow, Deadline, Collaboration, System
- **Search**: Full-text search across notification messages

#### Notification History

Access 30-day history:
- View dismissed or archived notifications
- Re-open closed notifications
- Export notification log (CSV format)

## Tips & Best Practices

### Avoiding Notification Overload

1. **Use Digests for Non-Urgent Events**
   - Switch to daily digest during early planning phases (CREATED, TOPIC_SELECTION states)
   - Use immediate notifications during active phases (Phase B outreach, Phase C quality review, Phase D assignment)

2. **Leverage Quiet Hours**
   - Set quiet hours to match your schedule (e.g., 6 PM - 8 AM)
   - Critical alerts will still break through if needed

3. **Enable Consolidation**
   - "5 speakers responded" is less overwhelming than 5 individual alerts
   - Reduces notification count by 60-80% on average

4. **Create Event-Specific Rules**
   - High-priority events (next month) → Immediate notifications
   - Long-term planning events (6+ months) → Daily digests

### Advanced Techniques

**Delegation Workflow**:
1. Receive notification about task
2. Click "Delegate" action
3. Select team member and add note
4. They receive notification with context
5. You get confirmation when task is completed

**Snooze Strategy**:
- **Snooze 1h**: Quick tasks you'll do after current work
- **Snooze 4h**: Tasks for later in the day
- **Snooze 24h**: Tasks for tomorrow's planning session
- **Custom snooze**: Specific deadline-driven tasks

**Smart Routing**:
- Create rules to auto-route notifications based on content
- Example: Speaker responses go to "Speaker Coordinator" role
- Example: File upload errors go to "Technical Lead" role

## Troubleshooting

### Common Issues

#### "Not receiving email notifications"

**Possible Causes**:
- Email address not verified in profile
- Notifications stuck in spam folder
- Email delivery frequency set to "Off" or "Digest"

**Solutions**:
1. Verify email: Settings → Profile → Verify Email Address
2. Check spam folder and mark BATbern as "Not Spam"
3. Add noreply@batbern.ch to your email contacts
4. Review Settings → Notifications → Email Delivery
5. Test with "Send Test Email" button in settings

#### "Receiving too many notifications"

**Solutions**:
1. Switch to Daily Digest mode: Settings → Notifications → Frequency
2. Enable consolidation: Settings → Notifications → Consolidate Similar Alerts
3. Set quiet hours: Settings → Notifications → Quiet Hours
4. Review per-event settings and set low-priority events to "Digest"
5. Disable low-priority categories: Settings → Notifications → Categories

#### "Missed a critical notification"

**Solutions**:
1. Check Notification History: Bell icon → View All → Show Archived
2. Enable email delivery for critical priorities only
3. Verify quiet hours aren't blocking important alerts
4. Use "Vacation Mode" with critical alert exceptions when on leave

#### "In-app badge shows count but no notifications visible"

**Possible Causes**:
- Notifications filtered or archived
- Browser cache issue
- Notifications from archived events

**Solutions**:
1. Click "Show All" to clear filters
2. Clear browser cache and reload
3. Check Settings → Notifications → Show Archived Events
4. Contact support if issue persists

## Related Features

- **[Workflow Management](../workflow/)** - Workflow events trigger many notifications
- **[Deadline Tracking](../workflow/phase-a-setup.md#deadlines)** - Deadline-based notification triggers
- **[Team Collaboration](../entity-management/users.md#roles-permissions)** - Collaboration notifications and assignments

## Future Enhancements `[ROADMAP]`

### Planned Features

- **Mobile App Push Notifications** - iOS and Android native notifications
- **Slack/Teams Integration** - Send notifications to team chat channels
- **SMS Alerts** - Critical deadline reminders via SMS (opt-in)
- **Notification Templates** - Customize message content and formatting
- **Smart Scheduling** - AI-powered optimal notification timing
- **Read Receipts** - Track when team members view notifications
- **Notification Analytics** - Dashboard showing response times and patterns

### Configuration Examples

**Example 1: Solo Organizer**
```yaml
Notification Preferences:
  In-app: Enabled (all priorities)
  Email: Daily digest at 8:00 AM
  Escalation: 24h before deadlines
  Quiet Hours: 9 PM - 7 AM
  Consolidation: Enabled
```

**Example 2: Team Lead (Multi-Organizer)**
```yaml
Notification Preferences:
  In-app: Enabled (High + Critical only)
  Email: Immediate (Critical), Daily digest (others)
  Escalation: 48h before deadlines, escalate to team after 72h
  Quiet Hours: None (on-call)
  Consolidation: Disabled (need details)
  Smart Routing: Speaker issues → Speaker Coordinator
```

**Example 3: Part-Time Organizer**
```yaml
Notification Preferences:
  In-app: Enabled (consolidated)
  Email: Weekly summary (Sundays at 6 PM)
  Escalation: Delegate to team lead after 24h
  Quiet Hours: Always (except Critical)
  Vacation Mode: Enabled during off-weeks
```

---

**Next**: Learn about [File Uploads](file-uploads.md) for managing speaker materials →
