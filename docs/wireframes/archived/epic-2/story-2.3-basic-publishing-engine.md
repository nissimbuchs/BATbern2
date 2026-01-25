# Story 2.3: Basic Publishing Engine - Wireframe

**Story**: Epic 2, Story 2.3 - Topic & Content Management Service
**Screen**: Basic Publishing Engine (from Epic 2)
**User Role**: Organizer
**Related FR**: FR19 (Progressive Publishing), FR6 (Current Event Prominence)

---

## Basic Publishing Engine (FR19 - Basic Version)

**Note**: This is the basic version from Epic 2. See story-4.3-progressive-publishing.md for the full version from Epic 4.

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back              Publishing Control Center - Spring Conference                    │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── PUBLISHING TIMELINE ──────────────────────────────────────────────────────┐   │
│  │                                                                               │   │
│  │  Jan 1          Feb 1          Mar 1          Apr 1          May 15          │   │
│  │  ──┬─────────────┬──────────────┬──────────────┬──────────────┬──           │   │
│  │    ↓             ↓              ↓              ↓              ↓              │   │
│  │  Topic ✓    Speakers ✓    Agenda Draft    Final Agenda    Event Day         │   │
│  │  Published   Published      Mar 15          May 1                            │   │
│  │                                                                               │   │
│  │  Current Phase: SPEAKERS PUBLISHED - Next: Agenda Draft (12 days)            │   │
│  └───────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                       │
│  ┌─── CONTENT VALIDATION DASHBOARD ─────────────────────────────────────────────┐   │
│  │                                                                               │   │
│  │  Publishing Readiness: 73%  ████████████████████░░░░░░░                      │   │
│  │                                                                               │   │
│  │  ┌─── Required Items ──────────────────────────────────────────────────┐     │   │
│  │  │                                                                      │     │   │
│  │  │  Component              Status    Validation               Action   │     │   │
│  │  │  ─────────────────────────────────────────────────────────────────  │     │   │
│  │  │  ✓ Event Title          Ready     Passed all checks        [Edit]   │     │   │
│  │  │  ✓ Date & Venue         Ready     Venue confirmed          [View]   │     │   │
│  │  │  ✓ Topic Description    Ready     Within 500 chars         [Edit]   │     │   │
│  │  │  ⚠️ Speaker List        Partial   5/8 confirmed            [Manage] │     │   │
│  │  │  ⚠️ Abstracts           Partial   5/8 validated            [Review] │     │   │
│  │  │    └─ Length Check      Failed    3 exceed 1000 chars      [Fix]    │     │   │
│  │  │    └─ Lessons Learned  Passed    All included             ✓        │     │   │
│  │  │    └─ Quality Review   Pending   3 await moderation       [Go]     │     │   │
│  │  │  ✗ Speaker Photos      Missing   3/8 uploaded             [Upload] │     │   │
│  │  │  ⚠️ Session Timings     Partial   5/8 sessions assigned   [Assign] │     │   │
│  │  │    └─ Workshop 1        ✗ Missing  No start/end time       [Assign] │     │   │
│  │  │    └─ Workshop 2        ✗ Missing  No start/end time       [Assign] │     │   │
│  │  │    └─ Closing Session  ✗ Missing  No start/end time       [Assign] │     │   │
│  │  │  ✗ Agenda Times        Not Set   Slots unassigned         [Assign] │     │   │
│  │  │  ✓ Registration Link   Ready     Tested & working         [Test]   │     │   │
│  │  │                                                                      │     │   │
│  │  └──────────────────────────────────────────────────────────────────────┘     │   │
│  └───────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                       │
│  ┌─── LIVE PREVIEW ──────────────────┬─── PUBLISHING CONTROLS ──────────────────┐  │
│  │                                   │                                           │  │
│  │  ┌─────────────────────────┐      │  Publishing Mode:                        │  │
│  │  │ BATbern Spring 2025     │      │  ○ Draft (internal only)                 │  │
│  │  │                         │      │  ● Progressive (public, partial)         │  │
│  │  │ Cloud Native            │      │  ○ Complete (all content)                │  │
│  │  │ Architecture            │      │                                           │  │
│  │  │                         │      │                                           │  │
│  │  │ May 15, 2025           │      │                                           │  │
│  │  │ Kursaal Bern           │      │                                           │  │
│  │  │                         │      │                                           │  │
│  │  │ Speakers:              │      │                                           │  │
│  │  │ • Sara Kim - Docker    │      │  Version Control:                        │  │
│  │  │ • Peter Muller - K8s   │      │  Current: v3 (Feb 28, 14:30)            │  │
│  │  │ • [3 more confirmed]   │      │  [View History] [Rollback]               │  │
│  │  │ • [3 slots available]  │      │                                           │  │
│  │  │                         │      │  Actions:                                │  │
│  │  │ [Register Now]         │      │  [Publish Now] [Preview]                 │  │
│  │  └─────────────────────────┘      │                                           │  │
│  │                                   │  ⚠️ Warning: 3 validation errors         │  │
│  │  [Desktop] [Mobile] [Print]       │     Publishing will show partial content  │  │
│  └───────────────────────────────────┴───────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Publishing Controls with Subscriber Notifications (AC24)

**Before Publishing**: Organizer can choose to notify subscribers when publishing

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│  ┌─── PUBLISHING CONTROLS ──────────────────────────────────────────────────────┐   │
│  │                                                                               │   │
│  │  Publishing Mode:                                                            │   │
│  │  ○ Draft (internal only)                                                     │   │
│  │  ● Progressive (public, partial)                                             │   │
│  │  ○ Complete (all content)                                                    │   │
│  │                                                                               │   │
│  │  ─────────────────────────────────────────────────────────────────────────   │   │
│  │                                                                               │   │
│  │  Subscriber Notifications:                                                   │   │
│  │  ☑ Notify subscribers when publishing                                        │   │
│  │     Will send email to 247 active subscribers                                │   │
│  │     [Preview Newsletter]                                                     │   │
│  │                                                                               │   │
│  │  Auto-Publish Schedule:                                                      │   │
│  │  • Phase 1 (Topic): Immediately                                              │   │
│  │  • Phase 2 (Speakers): June 1, 2025 at 10:00                                │   │
│  │  • Phase 3 (Agenda): June 8, 2025 at 10:00                                  │   │
│  │                                                                               │   │
│  │  ─────────────────────────────────────────────────────────────────────────   │   │
│  │                                                                               │   │
│  │  Version Control:                                                            │   │
│  │  Current: v3 (Feb 28, 14:30)                                                 │   │
│  │  [View History] [Rollback]                                                   │   │
│  │                                                                               │   │
│  │  Actions:                                                                    │   │
│  │  [Publish Now] [Schedule Publish] [Preview]                                 │   │
│  │                                                                               │   │
│  │  ⚠️ Warning: 3 validation errors                                             │   │
│  │     Publishing will show partial content                                     │   │
│  │                                                                               │   │
│  └───────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘

User Action: Click [Preview Newsletter]
Result: Opens Newsletter Preview Modal (see below)
```

**Notification Checkbox States**:
- **Checked (default)**: Newsletter will be sent on publish
- **Unchecked**: No newsletter sent (silent publish)
- **Tooltip**: "Send update email to all active subscribers when Phase X is published"

**Subscriber Count**:
- Shows real-time count of active subscribers
- Updates when subscribers added/removed
- Link to view subscriber list (if needed)

---

## Newsletter Preview Modal

**Trigger**: User clicks [Preview Newsletter] button

**Purpose**: Allow organizer to review email content before publishing

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│ 📧 Newsletter Preview                                                      [X] │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─── Email Details ──────────────────────────────────────────────────────┐    │
│  │                                                                         │    │
│  │  To: 247 active subscribers                                            │    │
│  │  From: BATbern Conference <noreply@batbern.ch>                         │    │
│  │  Subject: New Speakers Announced for BATbern 2025                      │    │
│  │                                                                         │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                 │
│  ┌─── Email Preview ──────────────────────────────────────────────────────┐    │
│  │                                                                         │    │
│  │  ┌───────────────────────────────────────────────────────────────────┐ │    │
│  │  │ BATbern 2025 Update                                               │ │    │
│  │  │                                                                   │ │    │
│  │  │ Dear BATbern Community,                                           │ │    │
│  │  │                                                                   │ │    │
│  │  │ We're excited to announce the speaker lineup for BATbern 2025!   │ │    │
│  │  │                                                                   │ │    │
│  │  │ Featured Speakers:                                                │ │    │
│  │  │ • Dr. Sarah Miller - AI in Architecture                           │ │    │
│  │  │ • Prof. John Chen - Sustainable Design Patterns                  │ │    │
│  │  │ • Dr. Maria Lopez - DevOps for Architects                        │ │    │
│  │  │ • Robert Williams - Cloud Native Architecture                    │ │    │
│  │  │ • Jane Doe - Microservices Best Practices                        │ │    │
│  │  │                                                                   │ │    │
│  │  │ Event Details:                                                    │ │    │
│  │  │ Date: May 15, 2025                                               │ │    │
│  │  │ Location: Kursaal Bern                                           │ │    │
│  │  │ Theme: Cloud Native Architecture                                 │ │    │
│  │  │                                                                   │ │    │
│  │  │ [View Full Program] [Register Now]                               │ │    │
│  │  │                                                                   │ │    │
│  │  │ Stay tuned for the full agenda, which will be published soon!    │ │    │
│  │  │                                                                   │ │    │
│  │  │ Best regards,                                                     │ │    │
│  │  │ The BATbern Team                                                 │ │    │
│  │  │                                                                   │ │    │
│  │  │ ───────────────────────────────────────────────────────────      │ │    │
│  │  │ You're receiving this email because you subscribed to BATbern   │ │    │
│  │  │ updates. [Unsubscribe] [Update Preferences]                     │ │    │
│  │  └───────────────────────────────────────────────────────────────────┘ │    │
│  │                                                                         │    │
│  │  Device Preview: [Desktop] [●Mobile] [Tablet]                          │    │
│  │                                                                         │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                 │
│  ┌─── Template Customization ─────────────────────────────────────────────┐    │
│  │                                                                         │    │
│  │  Email template: Phase 2 - Speakers Announcement (default)             │    │
│  │                                                                         │    │
│  │  Customizable Fields:                                                  │    │
│  │  • Header image: [Upload] or use default BATbern logo                  │    │
│  │  • Button color: #1976d2 (Brand blue)                                  │    │
│  │  • Custom message (optional):                                          │    │
│  │    ┌─────────────────────────────────────────────────────────────┐     │    │
│  │    │ [Add custom message to appear before speaker list...]       │     │    │
│  │    └─────────────────────────────────────────────────────────────┘     │    │
│  │                                                                         │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                 │
│  ┌─── Testing ─────────────────────────────────────────────────────────────┐    │
│  │                                                                         │    │
│  │  Send test email to verify formatting and links:                       │    │
│  │  ┌─────────────────────────────────────────────────────────────┐       │    │
│  │  │ organizer@batbern.ch                                        │       │    │
│  │  └─────────────────────────────────────────────────────────────┘       │    │
│  │  [Send Test Email]                                                     │    │
│  │                                                                         │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                 │
│  ┌──────────────────────────────────────────────────────────────────────┐      │
│  │ [Close]                                              [Save & Close]   │      │
│  └──────────────────────────────────────────────────────────────────────┘      │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘

User Actions:
1. Review email content and formatting
2. Switch between device previews (Desktop/Mobile/Tablet)
3. Customize header image or add custom message (optional)
4. Send test email to verify
5. Close modal (returns to publishing controls)
```

**Newsletter Content by Phase**:

- **Phase 1 (Topic)**: Event announcement with theme/topic reveal
- **Phase 2 (Speakers)**: Speaker lineup announcement (as shown above)
- **Phase 3 (Agenda)**: Full schedule with session timings and rooms
- **Phase 4 (Updates)**: Last-minute changes, reminders, or announcements

**Template Features**:
- **Responsive Design**: Mobile-optimized (most subscribers read on mobile)
- **Call-to-Action Buttons**: [View Full Program], [Register Now]
- **Unsubscribe Link**: Required by email regulations (GDPR, CAN-SPAM)
- **Brand Consistency**: Uses BATbern colors and logo

---

## Publishing Success State with Delivery Status

**Trigger**: After user clicks [Publish Now] and publishing completes

**Display**: Success message with subscriber notification delivery status

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│ ✓ Published Successfully                                                   [X] │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  Phase 2 (Speakers) is now live.                                               │
│                                                                                 │
│  ┌─── Publication Details ────────────────────────────────────────────────┐    │
│  │                                                                         │    │
│  │  Published: Feb 28, 2025 at 14:35                                      │    │
│  │  Version: v4                                                            │    │
│  │  Publishing Mode: Progressive                                           │    │
│  │  Live URL: https://batbern.ch/events/2025                              │    │
│  │                                                                         │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                 │
│  ┌─── Subscriber Notifications ───────────────────────────────────────────┐    │
│  │                                                                         │    │
│  │  ✓ Newsletter sent at 14:35                                            │    │
│  │                                                                         │    │
│  │  Delivery Status:                                                      │    │
│  │  • 245 delivered successfully                                          │    │
│  │  • 2 pending (retry in progress)                                       │    │
│  │  • 0 failed                                                             │    │
│  │                                                                         │    │
│  │  Subject: "New Speakers Announced for BATbern 2025"                    │    │
│  │                                                                         │    │
│  │  ┌─── Engagement Metrics (Live) ──────────────────────────────────┐    │    │
│  │  │                                                                 │    │    │
│  │  │  Email opens: 67 (27% open rate) - Updated 2 mins ago          │    │    │
│  │  │  Link clicks: 23 (9% click rate)                               │    │    │
│  │  │                                                                 │    │    │
│  │  │  Top clicked links:                                             │    │    │
│  │  │  1. [View Full Program] - 15 clicks                            │    │    │
│  │  │  2. [Register Now] - 8 clicks                                  │    │    │
│  │  │                                                                 │    │    │
│  │  │  [View Full Analytics]                                          │    │    │
│  │  │                                                                 │    │    │
│  │  └─────────────────────────────────────────────────────────────────┘    │    │
│  │                                                                         │    │
│  │  [View Sent Emails] [Resend Failed]                                    │    │
│  │                                                                         │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                 │
│  ┌─── Next Steps ──────────────────────────────────────────────────────────┐    │
│  │                                                                         │    │
│  │  • Monitor engagement metrics to see subscriber interest                │    │
│  │  • Continue with Phase 3 (Agenda) when session timings complete        │    │
│  │  • Respond to any subscriber replies or questions                      │    │
│  │                                                                         │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                 │
│  ┌──────────────────────────────────────────────────────────────────────┐      │
│  │ [View Live Page]    [Continue to Next Phase]    [Close]             │      │
│  └──────────────────────────────────────────────────────────────────────┘      │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘

User Actions:
1. Click [View Live Page] → Opens public event page in new tab
2. Click [View Sent Emails] → Shows list of sent emails with delivery status per recipient
3. Click [Resend Failed] → Retry sending to failed recipients
4. Click [View Full Analytics] → Opens email analytics dashboard
5. Click [Continue to Next Phase] → Navigate to next workflow step
6. Auto-dismiss after 10 seconds or click [Close]
```

**Delivery Status States**:
- **Delivered**: Email successfully delivered to recipient's inbox
- **Pending**: Email queued for delivery (retry in progress)
- **Failed**: Delivery failed (invalid email, bounce, spam filter)
- **Opened**: Recipient opened the email (tracked via pixel)
- **Clicked**: Recipient clicked a link in email

**Engagement Metrics**:
- **Open Rate**: Percentage of recipients who opened email
- **Click Rate**: Percentage who clicked any link
- **Click-Through Rate (CTR)**: Clicks as percentage of opens
- **Real-time Updates**: Metrics refresh every 2 minutes

---

## Publishing Error State (Newsletter Failure)

**Scenario**: Publishing succeeded but newsletter delivery failed

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│ ⚠️ Published with Warnings                                                 [X] │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  Content published successfully, but:                                          │
│                                                                                 │
│  ┌─── Newsletter Delivery Issue ─────────────────────────────────────────┐     │
│  │                                                                         │     │
│  │  ⚠️ Email delivery partially failed                                     │     │
│  │                                                                         │     │
│  │  • 210 delivered successfully                                          │     │
│  │  • 15 pending (retry in progress)                                      │     │
│  │  • 22 failed (invalid email addresses)                                 │     │
│  │                                                                         │     │
│  │  Reason: Email service rate limit exceeded                             │     │
│  │                                                                         │     │
│  │  Impact: 22 subscribers did not receive the update email.              │     │
│  │  Emails with these issues will be retried automatically.               │     │
│  │                                                                         │     │
│  │  Failed Recipients:                                                     │     │
│  │  • john.doe@invalid-domain.com (invalid email)                         │     │
│  │  • jane.smith@example.com (mailbox full)                               │     │
│  │  • ... (20 more)                                                        │     │
│  │                                                                         │     │
│  │  [View Failed Recipients] [Retry Failed] [Export CSV]                  │     │
│  │                                                                         │     │
│  └─────────────────────────────────────────────────────────────────────────┘     │
│                                                                                 │
│  ✓ Phase 2 (Speakers) Published Successfully                                   │
│  ✓ Content is live at: https://batbern.ch/events/2025                          │
│                                                                                 │
│  ┌──────────────────────────────────────────────────────────────────────┐      │
│  │ [View Live Page]    [Retry Failed Emails]    [Close]                │      │
│  └──────────────────────────────────────────────────────────────────────┘      │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

**Automatic Retry Logic**:
- Failed emails automatically retried after 5 minutes
- Maximum 3 retry attempts
- Permanent failures (invalid email) not retried
- Admin notification sent if >10% failure rate

---

## CDN Invalidation Status Display (AC25)

### Publishing Progress with CDN Status

**Trigger**: User clicks [Publish Now], system begins multi-step publishing process

**Display**: Modal showing real-time progress including CDN cache invalidation

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│ 🔄 Publishing in Progress...                                              [X] │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  Phase 2 (Speakers) - Publishing to production                                 │
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                                                                          │   │
│  │  ████████████████████████████████░░░░░░  78%                            │   │
│  │                                                                          │   │
│  │  1. ✓ Content validated                               (completed 2s)   │   │
│  │     All validation checks passed                                        │   │
│  │                                                                          │   │
│  │  2. ✓ Content saved to database                       (completed 1s)   │   │
│  │     Version v4 created successfully                                     │   │
│  │                                                                          │   │
│  │  3. ✓ CDN cache invalidating...                       (in progress)    │   │
│  │     CloudFront Distribution: E2X7XYZ123                                 │   │
│  │     Invalidation ID: I3ABCD8FGH9                                        │   │
│  │     Estimated time: 30-60 seconds                                       │   │
│  │     ⏳ Purging cached pages: /events/2025, /events/2025/speakers        │   │
│  │                                                                          │   │
│  │  4. ⏳ Preparing notifications...                     (queued)          │   │
│  │     Newsletter to 247 subscribers                                       │   │
│  │                                                                          │   │
│  │  5. ⏳ Updating search index...                       (queued)          │   │
│  │     Indexing new speaker content                                        │   │
│  │                                                                          │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                 │
│  Publishing started 45 seconds ago                                             │
│                                                                                 │
│  [Cancel Publishing] (warning: partial state)                                  │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘

User Experience:
- Modal shows live progress updates every 2 seconds
- Step status icons: ✓ (complete), ⏳ (in progress), - (queued)
- Progress bar fills as steps complete
- Estimated completion time shown for CDN invalidation
- User can cancel (with warning about partial state)
```

**CDN Invalidation Step Details**:
- **CloudFront Distribution ID**: Shows which CDN distribution is being invalidated
- **Invalidation ID**: Unique identifier for tracking this invalidation request
- **Estimated Time**: AWS CloudFront typically takes 30-60 seconds
- **Paths Being Purged**: List of URL paths being removed from cache
  - `/events/2025` (event landing page)
  - `/events/2025/speakers` (speakers page, if Phase 2)
  - `/events/2025/agenda` (agenda page, if Phase 3)
  - `/api/events/2025` (API endpoint cache)

---

### Publishing Success State with CDN Performance

**Trigger**: All publishing steps complete successfully, including CDN invalidation

**Display**: Success modal with CDN performance metrics

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│ ✓ Published Successfully                                                   [X] │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  Phase 2 (Speakers) is now live.                                               │
│                                                                                 │
│  ┌─── Publication Details ────────────────────────────────────────────────┐    │
│  │                                                                         │    │
│  │  Published: Feb 28, 2025 at 14:35                                      │    │
│  │  Version: v4                                                            │    │
│  │  Publishing Mode: Progressive                                           │    │
│  │  Live URL: https://batbern.ch/events/2025                              │    │
│  │                                                                         │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                 │
│  ┌─── Performance Metrics ─────────────────────────────────────────────────┐    │
│  │                                                                         │    │
│  │  ✓ Total publish time: 1 min 42 seconds                                │    │
│  │                                                                         │    │
│  │  Step Performance:                                                      │    │
│  │  • Content validation:       2s                                         │    │
│  │  • Database save:            1s                                         │    │
│  │  • CDN cache invalidation:   42s ✓                                      │    │
│  │  • Newsletter delivery:      28s (245/247 delivered)                    │    │
│  │  • Search index update:      29s                                        │    │
│  │                                                                         │    │
│  │  ┌─── CDN Status ──────────────────────────────────────────────────┐   │    │
│  │  │                                                                 │   │    │
│  │  │  ✓ Cache invalidation completed in 42 seconds                  │   │    │
│  │  │                                                                 │   │    │
│  │  │  CloudFront Distribution: E2X7XYZ123                           │   │    │
│  │  │  Invalidation ID: I3ABCD8FGH9 (completed)                      │   │    │
│  │  │                                                                 │   │    │
│  │  │  Paths invalidated:                                             │   │    │
│  │  │  • /events/2025 (event landing)                                │   │    │
│  │  │  • /events/2025/speakers (speaker list)                        │   │    │
│  │  │  • /api/events/2025 (API cache)                                │   │    │
│  │  │                                                                 │   │    │
│  │  │  Status: All edge locations updated                            │   │    │
│  │  │                                                                 │   │    │
│  │  │  ℹ️ New visitors will see updated content immediately.         │   │    │
│  │  │     Existing cached content cleared from all regions.          │   │    │
│  │  │                                                                 │   │    │
│  │  │  [View CloudFront Dashboard →]                                 │   │    │
│  │  │                                                                 │   │    │
│  │  └─────────────────────────────────────────────────────────────────┘   │    │
│  │                                                                         │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                 │
│  ┌─── Subscriber Notifications ───────────────────────────────────────────┐    │
│  │  ✓ Newsletter sent at 14:35                                            │    │
│  │  • 245 delivered successfully • 2 pending • 0 failed                   │    │
│  │  [View Sent Emails]                                                    │    │
│  └─────────────────────────────────────────────────────────────────────────┘    │
│                                                                                 │
│  ┌──────────────────────────────────────────────────────────────────────┐      │
│  │ [View Live Page]    [Continue to Next Phase]    [Close]             │      │
│  └──────────────────────────────────────────────────────────────────────┘      │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

**CDN Performance Indicators**:
- **Completion Time**: 42 seconds (typical range: 30-90s)
- **Status**: "All edge locations updated" (AWS CloudFront confirmation)
- **Paths Invalidated**: List of cleared URLs
- **Impact Message**: Clear explanation that all cached content is cleared
- **Link to Dashboard**: Direct link to AWS CloudFront console for advanced monitoring

**Color Coding**:
- Green checkmark (✓): CDN invalidation completed successfully
- Performance time: Green if <60s, Yellow if 60-90s, Red if >90s

---

### CDN Invalidation Error State

**Scenario**: Publishing succeeded but CDN cache invalidation failed or timed out

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│ ⚠️ Published with CDN Warning                                              [X] │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  Content published successfully, but:                                          │
│                                                                                 │
│  ┌─── CDN Cache Invalidation Issue ───────────────────────────────────────┐     │
│  │                                                                         │     │
│  │  ⚠️ CloudFront cache invalidation failed                                │     │
│  │                                                                         │     │
│  │  Distribution: E2X7XYZ123                                              │     │
│  │  Invalidation ID: I3ABCD8FGH9 (failed)                                 │     │
│  │                                                                         │     │
│  │  Error Code: ThrottlingException                                       │     │
│  │  Error Message: "Rate of invalidation requests exceeded. Maximum 3000 │     │
│  │                  paths per invalidation, 15 concurrent invalidations   │     │
│  │                  per distribution."                                     │     │
│  │                                                                         │     │
│  │  ┌─── Impact ────────────────────────────────────────────────────┐     │     │
│  │  │                                                               │     │     │
│  │  │  Cached content may persist for up to 24 hours (default TTL) │     │     │
│  │  │                                                               │     │     │
│  │  │  • New visitors: Will see updated content immediately        │     │     │
│  │  │  • Returning visitors: May see cached (old) content for up to│     │     │
│  │  │    24 hours, depending on when they last visited             │     │     │
│  │  │                                                               │     │     │
│  │  │  Paths affected:                                              │     │     │
│  │  │  • /events/2025 (event landing) - may show old content       │     │     │
│  │  │  • /events/2025/speakers - may show old speaker list         │     │     │
│  │  │                                                               │     │     │
│  │  └───────────────────────────────────────────────────────────────┘     │     │
│  │                                                                         │     │
│  │  ┌─── Resolution Options ──────────────────────────────────────────┐   │     │
│  │  │                                                                 │   │     │
│  │  │  [1] Retry Invalidation Now                                    │   │     │
│  │  │      Immediately retry the invalidation request                │   │     │
│  │  │      (May fail again if rate limit still exceeded)             │   │     │
│  │  │                                                                 │   │     │
│  │  │  [2] Schedule Retry in 5 Minutes                               │   │     │
│  │  │      Automatically retry after waiting period                  │   │     │
│  │  │      (Recommended if rate limit error)                         │   │     │
│  │  │                                                                 │   │     │
│  │  │  [3] Accept Warning & Continue                                 │   │     │
│  │  │      Content will be updated on next cache expiration         │   │     │
│  │  │      (Within 24 hours)                                         │   │     │
│  │  │                                                                 │   │     │
│  │  │  [4] View AWS CloudFront Console                               │   │     │
│  │  │      Manually trigger invalidation via AWS console            │   │     │
│  │  │                                                                 │   │     │
│  │  └─────────────────────────────────────────────────────────────────┘   │     │
│  │                                                                         │     │
│  │  Automatic Retry: Scheduled for 14:40 (in 5 minutes)                   │     │
│  │  [Cancel Auto-Retry]                                                   │     │
│  │                                                                         │     │
│  └─────────────────────────────────────────────────────────────────────────┘     │
│                                                                                 │
│  ✓ Phase 2 (Speakers) Published Successfully                                   │
│  ✓ Content is live at: https://batbern.ch/events/2025                          │
│  ✓ Newsletter sent to 247 subscribers                                          │
│                                                                                 │
│  ┌──────────────────────────────────────────────────────────────────────┐      │
│  │ [Retry CDN Invalidation]    [View Live Page]    [Close]             │      │
│  └──────────────────────────────────────────────────────────────────────┘      │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

**Error Types**:
1. **ThrottlingException**: AWS rate limit exceeded (15 concurrent invalidations/distribution)
2. **InvalidationBatchSizeTooLarge**: Too many paths in single invalidation (>3000 paths)
3. **Timeout**: Invalidation took longer than expected (>2 minutes)
4. **NoSuchDistribution**: CloudFront distribution not found (configuration error)
5. **AccessDenied**: Insufficient AWS permissions (deployment configuration error)

**Automatic Retry Logic**:
- **Throttling Errors**: Auto-retry after 5 minutes (recommended by AWS)
- **Timeout Errors**: Auto-retry after 1 minute (may have completed)
- **Other Errors**: Manual retry required (admin intervention needed)
- **Maximum Retries**: 3 attempts before escalating to admin notification

**Impact Messaging**:
- Clear explanation of what "cached content" means
- Differentiate between new vs. returning visitors
- Specific paths affected
- Expected timeline for natural cache expiration (24h default TTL)

---

### Version History with CDN Status

**Enhanced Version History Table** showing CDN invalidation status for each published version:

```
┌─── Version Control ──────────────────────────────────────────────────────────┐
│                                                                               │
│  Current Version: v4 (Feb 28, 14:35)                                         │
│  [View Full History] [Rollback]                                              │
│                                                                               │
└───────────────────────────────────────────────────────────────────────────────┘

Click [View Full History] → Opens modal:

┌─────────────────────────────────────────────────────────────────────────────────┐
│ 📜 Version History                                                         [X] │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                                                                          │   │
│  │  Version  Published         Phase      CDN Status        Actions        │   │
│  │  ─────────────────────────────────────────────────────────────────────  │   │
│  │                                                                          │   │
│  │  v4      Feb 28, 14:35      Speakers   ✓ Cleared (42s)   [Rollback]    │   │
│  │  (current)                             Invalidation:                    │   │
│  │          by Organizer A                I3ABCD8FGH9                      │   │
│  │                                        3 paths cleared                  │   │
│  │          ─────────────────────────────────────────────────────────────  │   │
│  │                                                                          │   │
│  │  v3      Feb 27, 10:15      Speakers   ⚠️ Partial (92s)  [Rollback]    │   │
│  │          by Organizer B                Timeout after 90s                │   │
│  │                                        Retry succeeded                  │   │
│  │          ─────────────────────────────────────────────────────────────  │   │
│  │                                                                          │   │
│  │  v2      Feb 26, 16:20      Topic      ✓ Cleared (38s)   [Rollback]    │   │
│  │          by Organizer A                Invalidation:                    │   │
│  │                                        I2XYZ5CDE7                       │   │
│  │                                        2 paths cleared                  │   │
│  │          ─────────────────────────────────────────────────────────────  │   │
│  │                                                                          │   │
│  │  v1      Feb 25, 09:00      Topic      ✗ Failed                         │   │
│  │  (initial)                             ThrottlingException              │   │
│  │          by System                     Manual retry required            │   │
│  │                                        [Retry CDN Invalidation]         │   │
│  │                                                                          │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                 │
│  CDN Status Legend:                                                            │
│  ✓ Cleared - Successfully invalidated, all edge locations updated             │
│  ⚠️ Partial - Completed with warnings or delays                                │
│  ✗ Failed - Invalidation failed, content may be cached                        │
│                                                                                 │
│  [Close]                                                                       │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

**Version History Columns**:
1. **Version**: Version number (v1, v2, v3, etc.) with "current" indicator
2. **Published**: Timestamp and publisher name
3. **Phase**: Which phase was published (Topic, Speakers, Agenda)
4. **CDN Status**: Visual indicator with details
   - ✓ Cleared: Success with completion time in seconds
   - ⚠️ Partial: Warning with reason (timeout, retry, etc.)
   - ✗ Failed: Error with reason (throttling, access denied, etc.)
5. **Actions**: [Rollback] button, [Retry CDN Invalidation] for failed versions

**Expandable Details** (click version row):
```
│  v3      Feb 27, 10:15      Speakers   ⚠️ Partial (92s)  [View Details ▼]    │
│                                                                                │
│  ┌─── CDN Invalidation Details ────────────────────────────────────────────┐  │
│  │                                                                          │  │
│  │  Invalidation ID: I2YZX9ABC3                                            │  │
│  │  Status: Completed with warning (exceeded 60s threshold)                │  │
│  │  Started: Feb 27, 10:15:05                                              │  │
│  │  Completed: Feb 27, 10:16:37 (92 seconds)                               │  │
│  │                                                                          │  │
│  │  Paths invalidated:                                                     │  │
│  │  • /events/2025                                                         │  │
│  │  • /events/2025/speakers                                                │  │
│  │  • /api/events/2025                                                     │  │
│  │                                                                          │  │
│  │  Timeline:                                                              │  │
│  │  10:15:05 - Invalidation request submitted                              │  │
│  │  10:15:12 - Request acknowledged by CloudFront                          │  │
│  │  10:16:37 - Invalidation completed (all edge locations)                 │  │
│  │                                                                          │  │
│  │  Notes: Completion time exceeded typical 60s threshold. This may        │  │
│  │         indicate high CDN load. Invalidation was successful.            │  │
│  │                                                                          │  │
│  │  [View in AWS Console] [Close Details ▲]                                │  │
│  │                                                                          │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
```

---

### CDN Monitoring Dashboard (Optional)

**Advanced Feature**: Link to real-time CDN monitoring

```
┌─── PUBLISHING CONTROLS ──────────────────────────────────────────────────────┐
│                                                                               │
│  Publishing Mode: ● Progressive  ○ Complete  ○ Draft                         │
│                                                                               │
│  ─────────────────────────────────────────────────────────────────────────   │
│                                                                               │
│  CDN Status (Live):                                                          │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │                                                                         │  │
│  │  CloudFront Distribution: E2X7XYZ123                                   │  │
│  │  Status: ✓ Active                                                      │  │
│  │                                                                         │  │
│  │  Cache Hit Ratio: 87% (last 24 hours)                                  │  │
│  │  Requests: 12,453 (last hour)                                          │  │
│  │                                                                         │  │
│  │  Active Invalidations: 0                                               │  │
│  │  Pending Invalidations: 0                                              │  │
│  │                                                                         │  │
│  │  [View CloudFront Metrics →]                                           │  │
│  │                                                                         │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                               │
│  Actions:                                                                    │
│  [Publish Now] [Schedule Publish] [Preview]                                 │
│                                                                               │
└───────────────────────────────────────────────────────────────────────────────┘
```

**Dashboard Metrics**:
- **Distribution Status**: Active/Disabled
- **Cache Hit Ratio**: Percentage of requests served from cache
- **Request Volume**: Recent traffic statistics
- **Active/Pending Invalidations**: Real-time count
- **Link to AWS Console**: For detailed CloudFront monitoring

---

### Technical Implementation Notes

**API Endpoints**:
```typescript
// Trigger publishing with CDN invalidation
POST /api/v1/events/{eventId}/publishing/publish
Request: {
  mode: "progressive" | "complete",
  notifySubscribers: boolean,
  invalidateCDN: boolean  // Default: true
}
Response: {
  publishingId: string,
  version: string,
  cdnInvalidationId: string,  // AWS CloudFront invalidation ID
  status: "in_progress"
}

// Poll publishing status
GET /api/v1/events/{eventId}/publishing/status/{publishingId}
Response: {
  status: "in_progress" | "completed" | "failed",
  steps: [
    { name: "validation", status: "completed", duration: 2 },
    { name: "database", status: "completed", duration: 1 },
    { name: "cdn_invalidation", status: "in_progress", duration: 42,
      details: {
        cloudFrontId: "E2X7XYZ123",
        invalidationId: "I3ABCD8FGH9",
        paths: ["/events/2025", "/events/2025/speakers"],
        estimatedCompletion: 60
      }
    },
    { name: "notifications", status: "queued", duration: null },
    { name: "search_index", status: "queued", duration: null }
  ],
  totalDuration: 45
}

// Retry CDN invalidation
POST /api/v1/events/{eventId}/publishing/cdn/retry
Request: {
  version: string,
  invalidationId: string  // Previous failed invalidation
}
Response: {
  newInvalidationId: string,
  status: "in_progress"
}

// Get version history with CDN status
GET /api/v1/events/{eventId}/publishing/versions
Response: [
  {
    version: "v4",
    publishedAt: "2025-02-28T14:35:00Z",
    publishedBy: "user@example.com",
    phase: "speakers",
    cdnStatus: {
      status: "cleared",
      invalidationId: "I3ABCD8FGH9",
      duration: 42,
      pathsCleared: 3
    }
  },
  // ... more versions
]
```

**WebSocket Events** (real-time progress updates):
```json
{
  "type": "PUBLISHING_PROGRESS",
  "data": {
    "publishingId": "pub-123",
    "currentStep": "cdn_invalidation",
    "status": "in_progress",
    "progress": 78,
    "cdnDetails": {
      "invalidationId": "I3ABCD8FGH9",
      "elapsedTime": 42,
      "estimatedCompletion": 60
    }
  }
}
```

**Polling Fallback**:
- If WebSocket unavailable, poll status endpoint every 2 seconds
- Stop polling when status is "completed" or "failed"
- Maximum polling duration: 5 minutes (timeout after)

## Key Interactive Elements

- **Publishing Timeline**: Visual representation of content release phases
- **Validation Dashboard**: Real-time readiness assessment with detailed checks
- **Live Preview**: See exactly how content appears to public
- **Publishing Modes**: Control visibility (draft, progressive, complete)
- **Version Control**: Track and rollback content versions

## Functional Requirements Met

- **FR19**: Progressive publishing with validation checks
- **FR6**: Ensures current event prominence on public site
- **Content Validation**: Multi-criteria checking before publication
- **Version Control**: Track all content changes with rollback capability
- **Preview Modes**: Test on desktop, mobile, and print layouts
- **Manual Publishing**: Explicit publish action required

## User Interactions

1. **Review Validation**: Check all content requirements before publishing
2. **Fix Issues**: Click action buttons to resolve validation errors
3. **Preview Content**: See live preview of public-facing page
4. **Set Publishing Mode**: Choose draft, progressive, or complete visibility
5. **Publish**: Manually publish content when ready

## Technical Notes

- Real-time validation engine checks all content criteria
- Preview iframe shows actual public site rendering
- Version control system tracks all content changes
- Manual publishing workflow with validation checks
- Integration with workflow Step 11 (Publish Progress)
- Responsive preview for mobile/desktop testing

---

## API Requirements

### Initial Page Load APIs

When the Publishing Control Center screen loads, the following APIs are called to provide the necessary data:

**CONSOLIDATED API APPROACH (Story 1.17):**

1. **GET /api/v1/events/{eventId}?include=workflow,sessions,publishing**
   - Returns: Complete event data with workflow state, sessions, and publishing configuration in a single call
   - Response includes:
     - Event core data: eventNumber, title, description, eventDate, status, venue
     - workflow: Current workflow state (currentPhase, phaseHistory, nextMilestone, publishingReadiness)
     - sessions: Session and speaker data (sessions with speaker assignments, confirmation status, abstract submission status)
     - publishing: Publishing configuration (currentMode, requiresApproval, preview content, version history, validation status)
   - Used for: Populate all publishing control center sections in a single request
   - **Performance**: Reduced from 7 API calls to 1 (86% reduction in HTTP requests)

---

**MIGRATION NOTE (Story 1.17):**
The original implementation required 7 separate API calls on page load:
- Event details
- Workflow state
- Validation status
- Sessions
- Publishing preview
- Version history
- Publishing config

The new consolidated API includes all this data via the `?include=workflow,sessions,publishing` parameter, reducing to a single call. This provides:
- Page load time: ~85% faster (from ~2.5s to <400ms)
- Single loading state instead of 7 separate loading indicators
- Atomic data consistency across all publishing components
- Reduced network overhead and latency
- Simpler error handling (one failure point instead of seven)

### User Action APIs

8. **PUT /api/v1/events/{eventId}/workflow**
   - Triggered by: User clicks action buttons to transition phases
   - Payload: `{ phaseTransition: "to_agenda_draft" }`
   - Returns: Updated workflow state
   - Used for: Updates timeline visualization and current phase display

9. **POST /api/v1/events/{eventId}/publishing/publish**
   - Triggered by: User clicks [Publish Now] button
   - Payload: `{ mode: "progressive|complete", approvalOverride: boolean }`
   - Returns: Publication confirmation with timestamp
   - Used for: Content becomes visible on public site, version incremented

10. **PUT /api/v1/events/{eventId}/publishing/config**
    - Triggered by: User changes publishing mode radio buttons
    - Payload: `{ mode: "draft|progressive|complete" }`
    - Returns: Updated configuration
    - Used for: Changes content visibility rules

11. **POST /api/v1/events/{eventId}/publishing/versions/{versionId}/rollback**
    - Triggered by: User clicks [Rollback] button in version control
    - Payload: `{ versionId: uuid, reason: string }`
    - Returns: Rolled back version details
    - Used for: Restores previous content version, creates new version entry

12. **GET /api/v1/events/{eventId}/publishing/preview?mode={mode}&device={device}**
    - Triggered by: User clicks [Desktop], [Mobile], or [Print] preview buttons
    - Query params: mode (current publishing mode), device (desktop|mobile|print)
    - Returns: Rendered preview HTML
    - Used for: Updates preview pane with device-specific rendering

13. **PUT /api/v1/sessions/{sessionId}/quality-review**
    - Triggered by: User clicks [Review] or [Go] on quality review validation items
    - Payload: Session/content for review submission
    - Returns: Updated review status
    - Used for: Updates validation dashboard status for abstracts/quality review

---

## Navigation Map

### Primary Navigation Actions

1. **← Back button** → Navigate to `Event Management Dashboard` (story-1.16-event-management-dashboard.md)
   - Returns to event list
   - No context passed

2. **Event Title or Topic Description [Edit] button** → Navigate to `Event Edit Screen`
   - Opens event editing interface
   - Context: eventId, specific field to edit

3. **Date & Venue [View] button** → Navigate to `Venue Details`
   - Opens venue details modal or screen
   - Context: eventId, venueId

4. **Speaker List [Manage] button** → Navigate to `Speaker Matching Interface` (story-3.1-speaker-matching-interface.md)
   - Opens speaker management screen
   - Context: eventId, filter to show confirmed/pending speakers

5. **Abstracts [Review] button** → Navigate to `Content Review Screen`
   - Opens moderator content review interface
   - Context: eventId, filter to show pending abstracts

6. **Abstract validation [Fix] button** → Navigate to `Abstract Editing Screen`
   - Opens abstract editor
   - Context: eventId, list of sessionIds with issues

7. **Speaker Photos [Upload] button** → Navigate to `Speaker Photo Management`
   - Opens photo upload interface
   - Context: eventId, list of speakerIds missing photos

8. **Session Timings [Assign] button** → Navigate to `/organizer/events/:eventCode/slot-assignment`
   - Opens **dedicated slot assignment page** (story-5.7-slot-assignment-page.md)
   - Context: eventCode, optionally focus on specific session if clicked on sub-item
   - **Blocking Validation**: Phase 3 (Agenda) publish is blocked if any session lacks timing
   - Shows: Full drag-drop interface with speaker pool, timeline grid, preference matching

8b. **Agenda Times [Assign] button** → Navigate to `Slot Assignment Screen`
   - Opens slot assignment interface (part of Story 3.1 or new screen)
   - Context: eventId, focus on unassigned slots

9. **Registration Link [Test] button** → Opens new tab to `Event Registration` (story-2.4-event-registration.md)
   - Opens registration page in test mode
   - Context: eventId, test mode parameter

10. **Publishing Controls [Preview] button** → Opens new tab to `Current Event Landing` (story-2.4-current-event-landing.md)
    - Opens public event page in preview mode
    - Context: eventId, preview mode parameter

11. **Version Control [View History] button** → Opens `Version History Modal`
    - Shows version history in modal overlay (same screen)
    - Context: eventId, loads version list via API

---
