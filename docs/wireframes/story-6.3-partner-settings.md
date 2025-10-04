# Story 6.3: Partner Settings Screen - Wireframe

**Story**: Epic 6, Story 6.3 - Partner Coordination
**Screen**: Partner Settings
**User Role**: Partner (Company Administrator)
**Related FR**: FR8 (Partner Strategic Input), FR19 (Multi-Organizer Coordination)

---

## Visual Wireframe

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Dashboard            Partner Settings                        [Save]    │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                   │
│  ┌─── SETTINGS NAVIGATION ──────┐  ┌─── COMPANY SETTINGS ────────────────────┐  │
│  │                               │  │                                          │  │
│  │  ● Company Settings           │  │  Partner Information                     │  │
│  │  ○ Notification Preferences   │  │                                          │  │
│  │  ○ Integration Settings       │  │  Company Name                            │  │
│  │  ○ Billing & Subscription     │  │  ┌─────────────────────────────────────┐│  │
│  │  ○ Team & Access              │  │  │ TechCorp AG                         ││  │
│  │  ○ Privacy & Data             │  │  └─────────────────────────────────────┘│  │
│  │                               │  │  (Contact support to change company name)│  │
│  │                               │  │                                          │  │
│  │                               │  │  Partnership Tier                        │  │
│  │                               │  │  🥇 Gold Partner                         │  │
│  │                               │  │  [View Tier Benefits] [Upgrade Tier]     │  │
│  │                               │  │                                          │  │
│  │                               │  │  Industry                                │  │
│  │                               │  │  ┌─────────────────────────────────────┐│  │
│  │                               │  │  │ Software & IT Services              ││  │
│  │                               │  │  └─────────────────────────────────────┘│  │
│  │                               │  │                                          │  │
│  │                               │  │  Company Website                         │  │
│  │                               │  │  ┌─────────────────────────────────────┐│  │
│  │                               │  │  │ https://www.techcorp.ch             ││  │
│  │                               │  │  └─────────────────────────────────────┘│  │
│  │                               │  │                                          │  │
│  │                               │  │  Company Logo                            │  │
│  │                               │  │  ┌─────────────────────┐                │  │
│  │                               │  │  │   [🏢 Logo]         │                │  │
│  │                               │  │  │   400×200px         │                │  │
│  │                               │  │  └─────────────────────┘                │  │
│  │                               │  │  [Upload New Logo] [Remove]              │  │
│  │                               │  │  (PNG/SVG, max 5MB, transparent bg)      │  │
│  │                               │  │                                          │  │
│  │                               │  │  Primary Contact                         │  │
│  │                               │  │  ┌─────────────────────────────────────┐│  │
│  │                               │  │  │ Maria Schmidt                       ││  │
│  │                               │  │  │ m.schmidt@techcorp.ch               ││  │
│  │                               │  │  │ +41 44 123 45 67                    ││  │
│  │                               │  │  └─────────────────────────────────────┘│  │
│  │                               │  │  [Change Primary Contact]                │  │
│  │                               │  │                                          │  │
│  │                               │  │  Partnership Start Date                  │  │
│  │                               │  │  January 15, 2020                        │  │
│  │                               │  │                                          │  │
│  └───────────────────────────────┘  └──────────────────────────────────────────┘  │
│                                                                                   │
│                             [Cancel]  [Save Changes]                             │
│                                                                                   │
└─────────────────────────────────────────────────────────────────────────────────┘

[NOTIFICATION PREFERENCES TAB]
┌─────────────────────────────────────────────────────────────────────────────────┐
│  ┌─── SETTINGS NAVIGATION ──────┐  ┌─── NOTIFICATION PREFERENCES ────────────┐  │
│  │                               │  │                                          │  │
│  │  ○ Company Settings           │  │  Email Notifications                     │  │
│  │  ● Notification Preferences   │  │                                          │  │
│  │  ○ Integration Settings       │  │  Topic Voting & Strategy                 │  │
│  │  ○ Billing & Subscription     │  │  ☑ New voting cycle opened               │  │
│  │  ○ Team & Access              │  │  ☑ Voting deadline approaching (3 days)  │  │
│  │  ○ Privacy & Data             │  │  ☑ Topic adopted from your suggestion    │  │
│  │                               │  │  ☑ Voting results published              │  │
│  │                               │  │                                          │  │
│  │                               │  │  Partner Meetings                        │  │
│  │                               │  │  ☑ Meeting scheduled                     │  │
│  │                               │  │  ☑ Meeting agenda published              │  │
│  │                               │  │  ☑ Meeting materials available           │  │
│  │                               │  │  ☑ Meeting reminder (1 day before)       │  │
│  │                               │  │  ☑ Meeting notes published               │  │
│  │                               │  │                                          │  │
│  │                               │  │  Event Updates                           │  │
│  │                               │  │  ☑ Upcoming events announcement          │  │
│  │                               │  │  ☑ Event registration opens              │  │
│  │                               │  │  ☑ Final agenda published                │  │
│  │                               │  │  ☑ Employee registration summary         │  │
│  │                               │  │                                          │  │
│  │                               │  │  Reports & Analytics                     │  │
│  │                               │  │  ☑ Monthly engagement report             │  │
│  │                               │  │  ☑ Quarterly partnership review          │  │
│  │                               │  │  ☐ Real-time ROI updates                 │  │
│  │                               │  │                                          │  │
│  │                               │  │  Communication Channels                  │  │
│  │                               │  │  ☑ Email (primary contact)               │  │
│  │                               │  │  ☐ Email (all team members)              │  │
│  │                               │  │  ☐ In-app notifications                  │  │
│  │                               │  │                                          │  │
│  │                               │  │  Quiet Hours                             │  │
│  │                               │  │  ☑ Enable quiet hours                    │  │
│  │                               │  │  From: [18:00 ⏷] To: [08:00 ⏷]          │  │
│  │                               │  │  Timezone: Europe/Zurich                 │  │
│  │                               │  │                                          │  │
│  │                               │  │  [Test Notifications] [Reset to Defaults]│  │
│  │                               │  │                                          │  │
│  └───────────────────────────────┘  └──────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘

[INTEGRATION SETTINGS TAB]
┌─────────────────────────────────────────────────────────────────────────────────┐
│  ┌─── SETTINGS NAVIGATION ──────┐  ┌─── INTEGRATION SETTINGS ────────────────┐  │
│  │                               │  │                                          │  │
│  │  ○ Company Settings           │  │  Calendar Integration                    │  │
│  │  ○ Notification Preferences   │  │                                          │  │
│  │  ● Integration Settings       │  │  ● Microsoft Outlook                     │  │
│  │  ○ Billing & Subscription     │  │  ○ Google Calendar                       │  │
│  │  ○ Team & Access              │  │  ○ Apple Calendar                        │  │
│  │  ○ Privacy & Data             │  │                                          │  │
│  │                               │  │  Status: ✅ Connected (expires Mar 2026)│  │
│  │                               │  │  [Reconnect] [Disconnect]                │  │
│  │                               │  │                                          │  │
│  │                               │  │  Sync Settings:                          │  │
│  │                               │  │  ☑ Auto-add events to calendar           │  │
│  │                               │  │  ☑ Auto-add partner meetings             │  │
│  │                               │  │  ☑ Sync RSVP status                      │  │
│  │                               │  │                                          │  │
│  │                               │  │  ─────────────────────────────────────    │  │
│  │                               │  │                                          │  │
│  │                               │  │  Single Sign-On (SSO)                    │  │
│  │                               │  │                                          │  │
│  │                               │  │  ☑ Enable SSO for team members           │  │
│  │                               │  │  Provider: Azure AD / Okta / Generic SAML│  │
│  │                               │  │                                          │  │
│  │                               │  │  Status: ✅ Active                       │  │
│  │                               │  │  Entity ID: techcorp.batbern.ch          │  │
│  │                               │  │  [Configure SSO] [Test Connection]       │  │
│  │                               │  │                                          │  │
│  │                               │  │  ─────────────────────────────────────    │  │
│  │                               │  │                                          │  │
│  │                               │  │  Webhooks (Advanced)                     │  │
│  │                               │  │                                          │  │
│  │                               │  │  Receive real-time notifications for:    │  │
│  │                               │  │  ☑ Topic voting opens                    │  │
│  │                               │  │  ☑ Meeting scheduled                     │  │
│  │                               │  │  ☑ Employee registered for event         │  │
│  │                               │  │                                          │  │
│  │                               │  │  Webhook URL:                            │  │
│  │                               │  │  ┌─────────────────────────────────────┐│  │
│  │                               │  │  │ https://api.techcorp.ch/webhooks    ││  │
│  │                               │  │  └─────────────────────────────────────┘│  │
│  │                               │  │                                          │  │
│  │                               │  │  Secret Key: ••••••••••••••••••••••••    │  │
│  │                               │  │  [Regenerate Secret] [Test Webhook]      │  │
│  │                               │  │                                          │  │
│  │                               │  │  ─────────────────────────────────────    │  │
│  │                               │  │                                          │  │
│  │                               │  │  API Access                              │  │
│  │                               │  │                                          │  │
│  │                               │  │  API Key: pk_live_••••••••••••••••••     │  │
│  │                               │  │  [View API Key] [Regenerate] [Revoke]    │  │
│  │                               │  │                                          │  │
│  │                               │  │  [View API Documentation →]              │  │
│  │                               │  │                                          │  │
│  └───────────────────────────────┘  └──────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘

[BILLING & SUBSCRIPTION TAB]
┌─────────────────────────────────────────────────────────────────────────────────┐
│  ┌─── SETTINGS NAVIGATION ──────┐  ┌─── BILLING & SUBSCRIPTION ──────────────┐  │
│  │                               │  │                                          │  │
│  │  ○ Company Settings           │  │  Current Plan                            │  │
│  │  ○ Notification Preferences   │  │                                          │  │
│  │  ○ Integration Settings       │  │  🥇 Gold Partner Tier                   │  │
│  │  ● Billing & Subscription     │  │  Annual Partnership Agreement            │  │
│  │  ○ Team & Access              │  │                                          │  │
│  │  ○ Privacy & Data             │  │  Benefits:                               │  │
│  │                               │  │  ✓ Weighted voting (3x votes)            │  │
│  │                               │  │  ✓ Quarterly partnership meetings        │  │
│  │                               │  │  ✓ Logo on event materials               │  │
│  │                               │  │  ✓ Priority speaker slots                │  │
│  │                               │  │  ✓ Monthly analytics reports             │  │
│  │                               │  │  ✓ Up to 50 employee registrations/event │  │
│  │                               │  │                                          │  │
│  │                               │  │  Renewal Date: January 15, 2026          │  │
│  │                               │  │  Auto-Renewal: ☑ Enabled                 │  │
│  │                               │  │                                          │  │
│  │                               │  │  [View All Tier Benefits →]              │  │
│  │                               │  │  [Upgrade to Premium ⭐] [Manage Plan]   │  │
│  │                               │  │                                          │  │
│  │                               │  │  ─────────────────────────────────────    │  │
│  │                               │  │                                          │  │
│  │                               │  │  Billing Information                     │  │
│  │                               │  │                                          │  │
│  │                               │  │  Annual Fee: CHF 25,000                  │  │
│  │                               │  │  Payment Method: Invoice (Net 30)        │  │
│  │                               │  │                                          │  │
│  │                               │  │  Billing Contact                         │  │
│  │                               │  │  ┌─────────────────────────────────────┐│  │
│  │                               │  │  │ Finance Department                  ││  │
│  │                               │  │  │ finance@techcorp.ch                 ││  │
│  │                               │  │  └─────────────────────────────────────┘│  │
│  │                               │  │                                          │  │
│  │                               │  │  Billing Address                         │  │
│  │                               │  │  ┌─────────────────────────────────────┐│  │
│  │                               │  │  │ TechCorp AG                         ││  │
│  │                               │  │  │ Bahnhofstrasse 45                   ││  │
│  │                               │  │  │ 8001 Zürich, Switzerland            ││  │
│  │                               │  │  └─────────────────────────────────────┘│  │
│  │                               │  │  [Update Billing Info]                   │  │
│  │                               │  │                                          │  │
│  │                               │  │  ─────────────────────────────────────    │  │
│  │                               │  │                                          │  │
│  │                               │  │  Payment History                         │  │
│  │                               │  │                                          │  │
│  │                               │  │  Jan 15, 2025  CHF 25,000  [Invoice]    │  │
│  │                               │  │  Jan 15, 2024  CHF 20,000  [Invoice]    │  │
│  │                               │  │  Jan 15, 2023  CHF 20,000  [Invoice]    │  │
│  │                               │  │                                          │  │
│  │                               │  │  [View All Invoices →]                   │  │
│  │                               │  │                                          │  │
│  └───────────────────────────────┘  └──────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘

[TEAM & ACCESS TAB]
┌─────────────────────────────────────────────────────────────────────────────────┐
│  ┌─── SETTINGS NAVIGATION ──────┐  ┌─── TEAM & ACCESS MANAGEMENT ────────────┐  │
│  │                               │  │                                          │  │
│  │  ○ Company Settings           │  │  Team Members                            │  │
│  │  ○ Notification Preferences   │  │                                          │  │
│  │  ○ Integration Settings       │  │  Total Members: 5 (3 active, 2 pending)  │  │
│  │  ○ Billing & Subscription     │  │                                          │  │
│  │  ● Team & Access              │  │  [+ Invite Team Member]                  │  │
│  │  ○ Privacy & Data             │  │                                          │  │
│  │                               │  │  ┌────────────────────────────────────┐  │  │
│  │                               │  │  │ 👤 Maria Schmidt                   │  │  │
│  │                               │  │  │    m.schmidt@techcorp.ch           │  │  │
│  │                               │  │  │    Role: Administrator             │  │  │
│  │                               │  │  │    Status: Active • Primary Contact│  │  │
│  │                               │  │  │    Last Active: 2 hours ago        │  │  │
│  │                               │  │  │    [Edit] [Remove]                 │  │  │
│  │                               │  │  └────────────────────────────────────┘  │  │
│  │                               │  │                                          │  │
│  │                               │  │  ┌────────────────────────────────────┐  │  │
│  │                               │  │  │ 👤 Peter Weber                     │  │  │
│  │                               │  │  │    p.weber@techcorp.ch             │  │  │
│  │                               │  │  │    Role: Manager                   │  │  │
│  │                               │  │  │    Status: Active                  │  │  │
│  │                               │  │  │    Last Active: 1 day ago          │  │  │
│  │                               │  │  │    [Edit] [Remove]                 │  │  │
│  │                               │  │  └────────────────────────────────────┘  │  │
│  │                               │  │                                          │  │
│  │                               │  │  ┌────────────────────────────────────┐  │  │
│  │                               │  │  │ 👤 Thomas Klein                    │  │  │
│  │                               │  │  │    t.klein@techcorp.ch             │  │  │
│  │                               │  │  │    Role: Member                    │  │  │
│  │                               │  │  │    Status: ⏳ Invitation Pending   │  │  │
│  │                               │  │  │    Invited: 3 days ago             │  │  │
│  │                               │  │  │    [Resend Invite] [Revoke]        │  │  │
│  │                               │  │  └────────────────────────────────────┘  │  │
│  │                               │  │                                          │  │
│  │                               │  │  ─────────────────────────────────────    │  │
│  │                               │  │                                          │  │
│  │                               │  │  Roles & Permissions                     │  │
│  │                               │  │                                          │  │
│  │                               │  │  Administrator:                          │  │
│  │                               │  │  • Full access to all partner features   │  │
│  │                               │  │  • Manage team members and roles         │  │
│  │                               │  │  • Access billing and subscription       │  │
│  │                               │  │  • Configure integrations                │  │
│  │                               │  │                                          │  │
│  │                               │  │  Manager:                                │  │
│  │                               │  │  • Cast votes on topics                  │  │
│  │                               │  │  • Attend partner meetings               │  │
│  │                               │  │  • View analytics and reports            │  │
│  │                               │  │  • Manage employee registrations         │  │
│  │                               │  │                                          │  │
│  │                               │  │  Member:                                 │  │
│  │                               │  │  • View voting results                   │  │
│  │                               │  │  • View event information                │  │
│  │                               │  │  • Access content library                │  │
│  │                               │  │                                          │  │
│  │                               │  │  [View Full Permission Matrix →]         │  │
│  │                               │  │                                          │  │
│  └───────────────────────────────┘  └──────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘

[PRIVACY & DATA TAB]
┌─────────────────────────────────────────────────────────────────────────────────┐
│  ┌─── SETTINGS NAVIGATION ──────┐  ┌─── PRIVACY & DATA MANAGEMENT ───────────┐  │
│  │                               │  │                                          │  │
│  │  ○ Company Settings           │  │  Data Sharing & Analytics                │  │
│  │  ○ Notification Preferences   │  │                                          │  │
│  │  ○ Integration Settings       │  │  ☑ Share employee attendance with BATbern│  │
│  │  ○ Billing & Subscription     │  │     (Required for partnership analytics) │  │
│  │  ○ Team & Access              │  │                                          │  │
│  │  ● Privacy & Data             │  │  ☑ Receive aggregated analytics reports  │  │
│  │                               │  │  ☑ Include company in partner directory  │  │
│  │                               │  │  ☐ Share voting patterns (anonymized)    │  │
│  │                               │  │                                          │  │
│  │                               │  │  Employee Data Privacy                   │  │
│  │                               │  │  ☑ Notify employees of company tracking  │  │
│  │                               │  │  ☑ Allow employees to opt-out            │  │
│  │                               │  │  ☐ Share individual employee activity    │  │
│  │                               │  │                                          │  │
│  │                               │  │  Logo & Branding Usage                   │  │
│  │                               │  │  ☑ Allow logo on event materials         │  │
│  │                               │  │  ☑ Allow logo on website                 │  │
│  │                               │  │  ☑ Allow logo in newsletters             │  │
│  │                               │  │  ☐ Allow logo in press releases          │  │
│  │                               │  │                                          │  │
│  │                               │  │  ─────────────────────────────────────    │  │
│  │                               │  │                                          │  │
│  │                               │  │  Data Retention & Export                 │  │
│  │                               │  │                                          │  │
│  │                               │  │  Your Data:                              │  │
│  │                               │  │  • 145 topic votes                       │  │
│  │                               │  │  • 24 meeting attendances                │  │
│  │                               │  │  • 1,247 employee event registrations    │  │
│  │                               │  │  • 89 engagement reports                 │  │
│  │                               │  │                                          │  │
│  │                               │  │  [Export All Data (JSON)] [Export PDF]   │  │
│  │                               │  │                                          │  │
│  │                               │  │  Data Retention: 7 years (compliance)    │  │
│  │                               │  │                                          │  │
│  │                               │  │  ─────────────────────────────────────    │  │
│  │                               │  │                                          │  │
│  │                               │  │  Account Management                      │  │
│  │                               │  │                                          │  │
│  │                               │  │  ⚠️ Danger Zone                          │  │
│  │                               │  │                                          │  │
│  │                               │  │  [Request Data Deletion]                 │  │
│  │                               │  │  [Downgrade Partnership]                 │  │
│  │                               │  │  [Terminate Partnership]                 │  │
│  │                               │  │                                          │  │
│  │                               │  │  [View Privacy Policy] [View Terms]      │  │
│  │                               │  │                                          │  │
│  └───────────────────────────────┘  └──────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Interactive Elements

- **[← Back to Dashboard] button**: Return to Partner Dashboard (if exists) or Topic Voting screen
- **[Save] button**: Save all changes across tabs (global save)
- **Tab navigation**: Switch between settings sections (Company, Notifications, Integration, Billing, Team, Privacy)
- **[Upload New Logo] button**: Opens file picker for company logo upload (PNG/SVG, max 5MB)
- **[View Tier Benefits] button**: Opens modal showing full tier benefits comparison
- **[Upgrade Tier] button**: Navigate to tier upgrade workflow
- **[Change Primary Contact] button**: Opens contact change workflow (requires verification)
- **[Test Notifications] button**: Send test notification to verify settings
- **[Reset to Defaults] button**: Reset notification preferences to default settings
- **[Reconnect] button**: Re-authenticate calendar integration
- **[Configure SSO] button**: Opens SSO configuration wizard
- **[Test Webhook] button**: Send test webhook event to verify integration
- **[View API Key] button**: Reveal full API key (requires password confirmation)
- **[Regenerate Secret] button**: Generate new webhook secret key
- **[View API Documentation] link**: Opens API documentation in new tab
- **[View All Tier Benefits] link**: Navigate to tier comparison page
- **[Manage Plan] button**: Navigate to subscription management
- **[Update Billing Info] button**: Edit billing contact and address
- **[View All Invoices] link**: Navigate to invoice history page
- **[+ Invite Team Member] button**: Opens team member invitation modal
- **[Edit] button**: Edit team member role and permissions
- **[Resend Invite] button**: Resend invitation email to pending member
- **[View Full Permission Matrix] link**: Opens detailed permissions documentation
- **[Export All Data] buttons**: Download partner data in JSON or PDF format
- **[Request Data Deletion] button**: Initiate GDPR data deletion request (requires confirmation)
- **[Terminate Partnership] button**: End partnership agreement (requires confirmation and reason)

---

## Functional Requirements Met

- **FR8**: Partner strategic input through notification preferences for voting and meetings
- **FR19**: Multi-organizer coordination through team access management
- **Company Management**: Centralized company settings and branding
- **Integration**: Calendar, SSO, webhooks, API access for external systems
- **Billing**: Subscription and payment management
- **Privacy**: GDPR-compliant data management and export

---

## Technical Notes

- **Auto-Save**: Auto-save changes every 30 seconds when editing (with visual indicator)
- **Logo Upload**: Direct to S3 with presigned URLs, automatic optimization and resizing
- **SSO Integration**: Support for SAML 2.0 and OAuth 2.0 providers
- **Webhook Security**: HMAC-SHA256 signature verification for webhook payloads
- **API Rate Limiting**: Display API usage and rate limit information
- **Calendar Sync**: OAuth 2.0 flow for calendar integration with token refresh
- **Team Invitations**: Unique invitation links with 7-day expiration
- **Audit Logging**: All settings changes logged with timestamp and user
- **Permission Matrix**: Role-based access control (RBAC) with granular permissions

---

## API Requirements

### Initial Page Load APIs

1. **GET /api/v1/companies/{companyId}**
   - Returns: Company details (id, name, displayName, isPartner, partnerTier, website, industry, logo, primaryContact, partnershipStartDate)
   - Used for: Display company settings tab

2. **GET /api/v1/notifications/preferences**
   - Returns: User notification preferences (channels, quietHours, notificationTypes)
   - Used for: Display notification preferences tab

3. **GET /api/v1/companies/{companyId}/integrations**
   - Returns: Integration settings (calendar: {provider, connected, expiresAt}, sso: {enabled, provider, entityId}, webhooks: {url, secretKey, events}, apiKey: {masked})
   - Used for: Display integration settings tab

4. **GET /api/v1/companies/{companyId}/subscription**
   - Returns: Subscription details (tier, benefits, renewalDate, autoRenewal, annualFee, paymentMethod, billingContact, billingAddress)
   - Used for: Display billing & subscription tab

5. **GET /api/v1/companies/{companyId}/team**
   - Returns: Team members (id, name, email, role, status, lastActive, invitedAt)
   - Used for: Display team & access tab

6. **GET /api/v1/companies/{companyId}/privacy-settings**
   - Returns: Privacy settings (dataSharing, employeePrivacy, logoUsage, dataRetention)
   - Used for: Display privacy & data tab

### User Action APIs

7. **PUT /api/v1/companies/{companyId}**
   - Triggered by: User clicks [Save Changes] on Company Settings tab
   - Payload: `{ website: "string", industry: "string", primaryContactId: "uuid" }`
   - Returns: Updated company object
   - Used for: Update company information

8. **POST /api/v1/files/presigned-upload-url**
   - Triggered by: User clicks [Upload New Logo]
   - Payload: `{ filename: "logo.png", contentType: "logo", fileSizeBytes: 524288, mimeType: "image/png" }`
   - Returns: Presigned upload URL for logo
   - Used for: Upload company logo to S3

9. **PUT /api/v1/notifications/preferences**
   - Triggered by: User clicks [Save Changes] on Notifications tab
   - Payload: `{ channels: {...}, quietHours: {...}, notificationTypes: {...} }`
   - Returns: Updated preferences
   - Used for: Update notification preferences

10. **POST /api/v1/notifications/test**
    - Triggered by: User clicks [Test Notifications]
    - Payload: `{ email: "string", channels: ["email", "inApp"] }`
    - Returns: Test send confirmation
    - Used for: Send test notification to verify settings

11. **PUT /api/v1/companies/{companyId}/integrations/calendar**
    - Triggered by: User connects calendar integration
    - Payload: `{ provider: "outlook" | "google" | "apple", authCode: "string", syncSettings: {...} }`
    - Returns: Calendar integration status with OAuth tokens
    - Used for: Connect/update calendar integration

12. **POST /api/v1/companies/{companyId}/integrations/sso**
    - Triggered by: User configures SSO
    - Payload: `{ enabled: true, provider: "azure" | "okta" | "saml", metadataUrl: "string", entityId: "string" }`
    - Returns: SSO configuration with SAML endpoints
    - Used for: Configure SSO for team members

13. **PUT /api/v1/companies/{companyId}/integrations/webhooks**
    - Triggered by: User updates webhook settings
    - Payload: `{ url: "https://api.techcorp.ch/webhooks", events: ["topic_voting_open", "meeting_scheduled"], active: true }`
    - Returns: Updated webhook configuration with secret key
    - Used for: Configure webhook integration

14. **POST /api/v1/companies/{companyId}/integrations/webhooks/regenerate-secret**
    - Triggered by: User clicks [Regenerate Secret]
    - Payload: `{}`
    - Returns: New secret key
    - Used for: Generate new webhook secret for security

15. **POST /api/v1/companies/{companyId}/integrations/webhooks/test**
    - Triggered by: User clicks [Test Webhook]
    - Payload: `{ eventType: "test" }`
    - Returns: Test webhook delivery status
    - Used for: Verify webhook integration

16. **GET /api/v1/companies/{companyId}/api-key**
    - Triggered by: User clicks [View API Key] (requires password confirmation)
    - Returns: Full API key (unhashed)
    - Used for: Display API key for external integration

17. **POST /api/v1/companies/{companyId}/api-key/regenerate**
    - Triggered by: User clicks [Regenerate] API key
    - Payload: `{}`
    - Returns: New API key
    - Used for: Generate new API key (invalidates old one)

18. **PUT /api/v1/companies/{companyId}/subscription**
    - Triggered by: User updates billing information
    - Payload: `{ billingContact: {...}, billingAddress: {...}, autoRenewal: boolean }`
    - Returns: Updated subscription details
    - Used for: Update billing settings

19. **GET /api/v1/companies/{companyId}/invoices**
    - Triggered by: User clicks [View All Invoices]
    - Query params: limit=50, offset=0
    - Returns: Array of invoices (id, date, amount, status, pdfUrl)
    - Used for: Display invoice history

20. **POST /api/v1/companies/{companyId}/team/invite**
    - Triggered by: User clicks [+ Invite Team Member]
    - Payload: `{ email: "string", role: "administrator" | "manager" | "member", message?: "string" }`
    - Returns: Invitation created with unique link
    - Used for: Invite new team member

21. **PUT /api/v1/companies/{companyId}/team/{userId}/role**
    - Triggered by: User edits team member role
    - Payload: `{ role: "administrator" | "manager" | "member" }`
    - Returns: Updated team member
    - Used for: Change team member role/permissions

22. **DELETE /api/v1/companies/{companyId}/team/{userId}**
    - Triggered by: User removes team member
    - Payload: `{ reason?: "string" }`
    - Returns: Removal confirmation
    - Used for: Remove team member access

23. **PUT /api/v1/companies/{companyId}/privacy-settings**
    - Triggered by: User updates privacy settings
    - Payload: `{ dataSharing: {...}, employeePrivacy: {...}, logoUsage: {...} }`
    - Returns: Updated privacy settings
    - Used for: Update privacy and data sharing preferences

24. **GET /api/v1/companies/{companyId}/data-export**
    - Triggered by: User clicks [Export All Data]
    - Query params: format=json|pdf
    - Returns: Downloadable data export file
    - Used for: GDPR data export

25. **POST /api/v1/companies/{companyId}/data-deletion-request**
    - Triggered by: User clicks [Request Data Deletion]
    - Payload: `{ reason: "string", confirmPassword: "string" }`
    - Returns: Data deletion request created (requires approval)
    - Used for: GDPR right to erasure

26. **POST /api/v1/companies/{companyId}/termination-request**
    - Triggered by: User clicks [Terminate Partnership]
    - Payload: `{ reason: "string", effectiveDate: "2025-12-31", confirmPassword: "string" }`
    - Returns: Termination request created (requires organizer approval)
    - Used for: End partnership agreement

---

## Navigation Map

### Primary Navigation Actions

1. **← Back button** → Navigate to `Topic Voting Screen` or `Partner Dashboard` (if exists)
   - Target: Main partner interface
   - Context: Return with updated settings

2. **Tab navigation** → Switch between settings sections
   - Target: Same page, different tab content
   - Context: Tab state persisted in URL (e.g., #notifications)

3. **[View Tier Benefits] link** → Opens `Tier Comparison Modal`
   - Target: Modal overlay with tier comparison table
   - Context: Current tier highlighted

4. **[Upgrade Tier] button** → Navigate to `Tier Upgrade Workflow`
   - Target: Subscription upgrade page
   - Context: Current tier, available upgrades

5. **[View API Documentation] link** → Opens external documentation
   - Target: New tab with API docs (https://api.batbern.ch/docs)
   - Context: API key and authentication info

6. **[View All Invoices] link** → Navigate to `Invoice History Page`
   - Target: Billing portal with invoice list
   - Context: companyId, date range filter

7. **[View Full Permission Matrix] link** → Opens `Permissions Documentation`
   - Target: Modal or new page with detailed RBAC matrix
   - Context: Current user's role highlighted

### Event-Driven Navigation

8. **[Save Changes] button** → Saves and shows confirmation
   - Action: Save settings via API
   - Effect: Show success toast, update UI
   - Navigation: Stay on page

9. **[Invite Team Member] button** → Opens `Invite Modal`
   - Target: Modal with invitation form
   - Context: Available roles, company info

10. **[Configure SSO] button** → Opens `SSO Configuration Wizard`
    - Target: Multi-step modal or dedicated page
    - Context: Company domain, provider selection

### Error States & Redirects

11. **Insufficient permissions** → Show error modal
    - Condition: User not admin trying to access certain tabs
    - Action: Display "Administrator access required" message
    - Options: [Contact Administrator] or [Return to Dashboard]

12. **Logo upload failure** → Show error notification
    - Condition: File too large, wrong format, or upload failed
    - Action: Display specific error message
    - Options: [Try Again] or [Cancel]

13. **SSO configuration error** → Show validation errors
    - Condition: Invalid metadata URL or configuration
    - Action: Highlight errors in form, show troubleshooting tips
    - Options: [Fix Errors] or [Contact Support]

14. **Webhook test failure** → Show failure details
    - Condition: Webhook endpoint unreachable or returns error
    - Action: Display error details (status code, response)
    - Options: [View Debug Info] or [Update Webhook URL]

15. **Team invitation limit reached** → Show upgrade prompt
    - Condition: Maximum team members for tier reached
    - Action: Display "Team member limit reached" with current usage
    - Options: [Upgrade Tier] or [Remove Inactive Members]

16. **Data deletion request** → Show confirmation modal
    - Condition: User initiates data deletion
    - Action: Display GDPR warning, require password confirmation
    - Effect: Creates request for organizer approval
    - Navigation: Email confirmation sent, request tracked

---

## Responsive Design Considerations

### Mobile Layout Changes

**Stacked Layout (320px - 768px):**
- Settings navigation: Horizontal scrollable tabs (not sidebar)
- Forms: Full-width inputs, stacked labels above fields
- Logo preview: Centered with upload button below
- Team member cards: Full-width stacked
- Integration status: Compact cards with icons
- Billing history: Simplified list view (date, amount, download)
- Action buttons: Full-width CTAs
- Tab content: Single column, progressive disclosure

**Tablet Layout (768px - 1024px):**
- Settings navigation: Collapsible sidebar
- Forms: Two-column layout where appropriate
- Integration cards: 2-column grid
- Team members: 2-column grid

### Mobile-Specific Interactions

- **Bottom sheet modals**: Settings confirmations, team invitations
- **Pull-to-refresh**: Refresh settings data
- **Touch-optimized**: 44px minimum touch targets
- **Sticky save button**: [Save] button sticky at bottom on mobile
- **Collapsible sections**: Tap to expand/collapse detailed settings
- **Swipe navigation**: Swipe between settings tabs
- **Mobile file picker**: Native file picker for logo upload
- **Responsive tables**: Horizontal scroll for invoice history on mobile

---

## Accessibility Notes

- **Keyboard Navigation**: Full tab navigation through all form fields and buttons
- **ARIA Labels**:
  - `aria-label="Partner settings navigation"` on tab sidebar
  - `aria-label="Upload company logo"` on upload button
  - `role="tabpanel"` on settings content areas
  - `aria-labelledby` pointing to tab headers
- **Screen Reader Announcements**:
  - Live region (`aria-live="polite"`) for save confirmations
  - Status updates announced: "Settings saved successfully"
  - Error messages associated with form fields
- **Color Contrast**: WCAG 2.1 AA compliance
  - Tier badges: Sufficient contrast with background
  - Status indicators: Color + icon + text
- **Focus Indicators**: 2px solid outline on focused elements
- **Form Labels**: All inputs properly labeled with `<label>` elements
- **Error Messaging**: Errors associated with fields using `aria-describedby`
- **Semantic HTML**: Proper heading hierarchy, form structure

---

## State Management

### Local Component State

- `activeTab`: Current settings tab ('company', 'notifications', 'integration', 'billing', 'team', 'privacy')
- `isDirty`: Whether form has unsaved changes
- `formData`: Current form values for each tab
- `validationErrors`: Field-level validation errors
- `uploadProgress`: Logo upload progress percentage

### Global State (Zustand Store)

- `partnerSettings`: Complete settings object from API
- `companyInfo`: Company details and partnership status
- `teamMembers`: Array of team member objects
- `integrationStatus`: Status of all integrations (calendar, SSO, webhooks)

### Server State (React Query)

- `useCompanySettings(companyId)`: Company settings with 5-minute cache
- `useNotificationPreferences()`: Notification preferences with 10-minute cache
- `useIntegrations(companyId)`: Integration status with 2-minute cache
- `useSubscription(companyId)`: Billing and subscription with 15-minute cache
- `useTeamMembers(companyId)`: Team members with 1-minute refetch

### Auto-Save Behavior

- **Auto-Save Trigger**: Debounced auto-save after 30 seconds of inactivity
- **Visual Indicator**: "Saving..." spinner, then "All changes saved" checkmark
- **Conflict Resolution**: Alert if settings changed by another admin
- **Draft State**: Unsaved changes survive browser refresh (localStorage)

---

## Form Validation Rules

### Company Settings Tab

- **Company Name**: Display only (cannot edit, contact support)
- **Industry**: Required, dropdown selection
- **Website**: Optional, must be valid URL format
- **Logo**: Max 5MB, PNG/SVG only, transparent background recommended
- **Primary Contact**: Required, must be existing team member

### Notification Preferences Tab

- **Quiet Hours**: `startTime` < `endTime`, valid 24-hour format
- **Timezone**: Required if quiet hours enabled
- **Email Digest**: At least one notification type must be enabled

### Integration Settings Tab

- **Webhook URL**: Required if webhooks enabled, must be valid HTTPS URL
- **SSO Provider**: Required metadata URL for SAML configuration
- **API Key**: Cannot be empty if regenerating

### Billing & Subscription Tab

- **Billing Contact**: Valid email required
- **Billing Address**: Complete address required for invoicing

### Team & Access Tab

- **Invite Email**: Must be valid email, cannot be duplicate
- **Role**: Required selection from available roles
- **At least one Administrator**: Cannot remove last admin

### Privacy & Data Tab

- **Data Deletion Request**: Password confirmation required
- **Termination Request**: Reason required (min 50 characters)

---

## Edge Cases & Error Handling

- **Empty State (First-time Setup)**:
  - Show setup wizard for first-time partners
  - Guide through essential settings (logo, primary contact, notifications)

- **Loading State**:
  - Skeleton screens for each tab content
  - Loading spinner for save operations
  - Disable form while loading

- **Error State (API Failure)**:
  - Show error banner: "Unable to save settings. Please try again."
  - Preserve form data, provide [Retry] button
  - Auto-retry with exponential backoff

- **Concurrent Edit Conflict**:
  - Detect if another admin changed settings
  - Show warning: "Settings were updated by [Admin Name]. Reload to see latest?"
  - Options: [Reload] (discard changes) or [Overwrite] (keep local changes)

- **Logo Upload Failure**:
  - Show specific error: File too large, invalid format, upload timeout
  - Provide [Try Again] or [Choose Different File]
  - Fall back to existing logo on failure

- **SSO Configuration Error**:
  - Validate metadata URL before saving
  - Show specific SAML errors (invalid XML, missing endpoints)
  - Provide [Test SSO] button to verify before enabling

- **Webhook Delivery Failure**:
  - Log failed webhook attempts with retry count
  - Show warning after 3 consecutive failures
  - Suggest: "Check endpoint availability or update webhook URL"

- **Team Invitation Already Sent**:
  - Detect duplicate email invitation
  - Show: "Invitation already sent to this email address"
  - Options: [Resend Invitation] or [Cancel]

- **Role Change Validation**:
  - Prevent removing last administrator
  - Show: "Cannot remove last administrator. Assign another admin first."
  - Suggest promoting another member to admin

- **Subscription Tier Limitation**:
  - Show tier limits when reached (e.g., max team members for tier)
  - Suggest: [Upgrade to Next Tier] with benefits comparison
  - Display current usage vs. tier limits

- **Data Export Large Size**:
  - Show warning if export >100MB
  - Offer: "Large export detected. Generate in background and email download link?"
  - Progress indicator for long-running exports

- **Partnership Termination Request**:
  - Require detailed reason and password confirmation
  - Show impact: "This will disable access for all team members"
  - Cooling-off period: 30 days before final termination
  - Provide cancellation option during cooling-off

---

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-10-04 | 1.0 | Initial wireframe creation for Partner Settings Screen | ux-expert |

---

## Review Notes

### Stakeholder Feedback

_To be added during review_

### Design Iterations

_To be documented as design evolves_

### Open Questions

1. **SSO Multi-Provider Support**: Should we support multiple SSO providers simultaneously?
   - Current design: Single provider per partner
   - Alternative: Multiple providers with priority/fallback
   - Decision needed from security team

2. **API Rate Limiting**: What rate limits should apply to partner API access?
   - Consideration: Tier-based rate limits (Bronze: 1000/hour, Gold: 5000/hour, Premium: unlimited)
   - Technical feasibility assessment needed

3. **Team Member Limits**: Should there be hard limits on team size per tier?
   - Current design: Soft limits with upgrade prompts
   - Alternative: Hard blocks preventing addition
   - Business rule clarification required

4. **Webhook Retry Policy**: How many times should failed webhooks retry?
   - Recommendation: 3 retries with exponential backoff (30s, 5min, 1hour)
   - After 3 failures, disable webhook and notify admin
   - Engineering team confirmation needed

5. **Data Deletion Timeline**: How long should data deletion requests take?
   - GDPR requirement: 30 days maximum
   - Current design: 7-day review period + 23-day processing
   - Legal team review required

6. **Billing Currency**: Support multiple currencies beyond CHF?
   - Current design: CHF only
   - Alternative: EUR, USD support with currency conversion
   - Finance team decision needed
