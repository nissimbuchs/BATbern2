# Notification System `[PLANNED]`

## Overview

The BATbern notification system keeps organizers informed about important workflow events, deadlines, and required actions. Notifications can be delivered through multiple channels:

- **In-app alerts** - Real-time notifications within the platform
- **Email notifications** - Detailed messages sent to your registered email
- **Escalation alerts** - Priority notifications for time-sensitive actions

The system is designed to reduce manual monitoring while preventing information overload through intelligent prioritization and customizable preferences.

> **Note**: This feature is currently in planning stages. The documentation below describes the intended functionality. Check [Feature Status](../appendix/feature-status.md) for implementation timeline.

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
