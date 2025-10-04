# System Settings/Configuration Screen

## Header Information

**Story:** Epic 1 - Foundation & Core Infrastructure
**Screen:** System Settings/Configuration Screen
**User Role:** Organizer (Admin-level)
**Related FR:** FR1 (Role-based authentication), FR2 (Event workflow management), FR5 (Progressive publishing)

---

## Visual Wireframe

```
┌───────────────────────────────────────────────────────────────────────────┐
│ ← Back to Dashboard    System Settings                    [Save Changes]  │
├───────────────────────────────────────────────────────────────────────────┤
│                                                                            │
│  ┌─── SETTINGS NAVIGATION ──────────────────────────────────────┐         │
│  │  ● Platform Configuration    ○ Workflow Configuration        │         │
│  │  ○ Integration Settings       ○ Feature Flags                │         │
│  │  ○ System Status             ○ Security & Access             │         │
│  └───────────────────────────────────────────────────────────────┘         │
│                                                                            │
│  ┌─── PLATFORM CONFIGURATION ───────────────────────────────────┐         │
│  │                                                               │         │
│  │  General Settings                                             │         │
│  │  ┌──────────────────────────────────────────────────────┐   │         │
│  │  │ Platform Name: [BATbern Event Platform          ]    │   │         │
│  │  │ Support Email: [support@batbern.ch              ]    │   │         │
│  │  │ Admin Contact: [admin@batbern.ch                ]    │   │         │
│  │  └──────────────────────────────────────────────────────┘   │         │
│  │                                                               │         │
│  │  Internationalization                                         │         │
│  │  ┌──────────────────────────────────────────────────────┐   │         │
│  │  │ Default Language:    [German (de-CH)        ▼]       │   │         │
│  │  │ Supported Languages: ☑ German  ☑ English             │   │         │
│  │  │ Date Format:         [DD.MM.YYYY            ▼]       │   │         │
│  │  │ Time Zone:           [Europe/Zurich         ▼]       │   │         │
│  │  └──────────────────────────────────────────────────────┘   │         │
│  │                                                               │         │
│  │  Email Configuration                                          │         │
│  │  ┌──────────────────────────────────────────────────────┐   │         │
│  │  │ Email Provider:      AWS SES (Frankfurt)             │   │         │
│  │  │ Sender Name:         [BATbern Events        ]        │   │         │
│  │  │ Reply-to Address:    [noreply@batbern.ch    ]        │   │         │
│  │  │ Daily Send Limit:    [50,000 emails         ]        │   │         │
│  │  │                      [Test Email Configuration]       │   │         │
│  │  └──────────────────────────────────────────────────────┘   │         │
│  │                                                               │         │
│  │  Storage & CDN                                                │         │
│  │  ┌──────────────────────────────────────────────────────┐   │         │
│  │  │ S3 Bucket:           batbern-content-eu-central-1    │   │         │
│  │  │ CDN Domain:          d3tsrt2aaweqwh.cloudfront.net   │   │         │
│  │  │ Max File Size:       [200 MB                ]        │   │         │
│  │  │ Allowed File Types:  PDF, PPTX, PNG, JPG, MP4, ZIP   │   │         │
│  │  │ Storage Quota/User:  [5 GB                  ]        │   │         │
│  │  └──────────────────────────────────────────────────────┘   │         │
│  │                                                               │         │
│  └───────────────────────────────────────────────────────────────┘         │
│                                                                            │
│  ┌─── WORKFLOW CONFIGURATION ──────────────────────────────────┐         │
│  │                                                               │         │
│  │  Event Workflow Steps (16-Step Process)                      │         │
│  │  ┌──────────────────────────────────────────────────────┐   │         │
│  │  │  Step  Name                    Duration   Auto-Skip  │   │         │
│  │  │  ──────────────────────────────────────────────────  │   │         │
│  │  │  1.  Topic Definition           7 days    ☐         │   │         │
│  │  │  2.  Speaker Outreach          14 days    ☐         │   │         │
│  │  │  3.  Speaker Confirmation      21 days    ☐         │   │         │
│  │  │  4.  Slot Assignment            7 days    ☐         │   │         │
│  │  │  5.  Content Submission        30 days    ☐         │   │         │
│  │  │  6.  Quality Review            14 days    ☐         │   │         │
│  │  │  7.  Preliminary Publish        3 days    ☐         │   │         │
│  │  │  ...                                                 │   │         │
│  │  │  16. Post-Event Archive         ∞        ☐         │   │         │
│  │  │                                [Edit Workflow Steps] │   │         │
│  │  └──────────────────────────────────────────────────────┘   │         │
│  │                                                               │         │
│  │  Deadline Management                                          │         │
│  │  ┌──────────────────────────────────────────────────────┐   │         │
│  │  │ ☑ Send deadline reminders (7 days, 3 days, 1 day)   │   │         │
│  │  │ ☑ Enable auto-escalation for missed deadlines        │   │         │
│  │  │ ☑ Notify moderator on workflow blockages             │   │         │
│  │  │ Grace Period: [2 days                        ]       │   │         │
│  │  └──────────────────────────────────────────────────────┘   │         │
│  │                                                               │         │
│  └───────────────────────────────────────────────────────────────┘         │
│                                                                            │
│  ┌─── INTEGRATION SETTINGS ────────────────────────────────────┐         │
│  │                                                               │         │
│  │  AWS Services                                                 │         │
│  │  ┌──────────────────────────────────────────────────────┐   │         │
│  │  │ Cognito User Pool:  [eu-central-1_ABC123   ]  ✓      │   │         │
│  │  │ EventBridge Bus:    [batbern-events        ]  ✓      │   │         │
│  │  │ S3 Bucket:          [batbern-content-prod  ]  ✓      │   │         │
│  │  │ CloudFront Dist:    [E1234ABCD567890      ]  ✓      │   │         │
│  │  │                     [Test AWS Connections]            │   │         │
│  │  └──────────────────────────────────────────────────────┘   │         │
│  │                                                               │         │
│  │  External APIs                                                │         │
│  │  ┌──────────────────────────────────────────────────────┐   │         │
│  │  │ Swiss UID API:      [Configured            ]  ✓      │   │         │
│  │  │   API Key: ****************************             │   │         │
│  │  │                     [Test UID Verification]           │   │         │
│  │  └──────────────────────────────────────────────────────┘   │         │
│  │                                                               │         │
│  │  Calendar Integration                                         │         │
│  │  ┌──────────────────────────────────────────────────────┐   │         │
│  │  │ ☑ Enable iCal export for attendees                   │   │         │
│  │  │ ☑ Enable Google Calendar sync                        │   │         │
│  │  │ ☑ Enable Outlook Calendar sync                       │   │         │
│  │  └──────────────────────────────────────────────────────┘   │         │
│  │                                                               │         │
│  └───────────────────────────────────────────────────────────────┘         │
│                                                                            │
│  ┌─── FEATURE FLAGS ───────────────────────────────────────────┐         │
│  │                                                               │         │
│  │  Core Features                                                │         │
│  │  ┌──────────────────────────────────────────────────────┐   │         │
│  │  │ ☑ Progressive Publishing                              │   │         │
│  │  │ ☑ Speaker Overflow Management                         │   │         │
│  │  │ ☑ Multi-Organizer Collaboration                       │   │         │
│  │  │ ☑ Real-time Workflow Updates                          │   │         │
│  │  │ ☑ Topic Similarity Detection (ML)                     │   │         │
│  │  └──────────────────────────────────────────────────────┘   │         │
│  │                                                               │         │
│  │  Advanced Features (Beta)                                     │         │
│  │  ┌──────────────────────────────────────────────────────┐   │         │
│  │  │ ☐ AI-Powered Content Recommendations                  │   │         │
│  │  │ ☐ Automated Report Generation                         │   │         │
│  │  │ ☐ Advanced Partner Analytics                          │   │         │
│  │  │ ☐ Attendee Community Features                         │   │         │
│  │  └──────────────────────────────────────────────────────┘   │         │
│  │                                                               │         │
│  │  Experimental Features (Dev/Staging Only)                     │         │
│  │  ┌──────────────────────────────────────────────────────┐   │         │
│  │  │ ☐ GraphQL API Gateway                                 │   │         │
│  │  │ ☐ WebSocket Real-time Events                          │   │         │
│  │  │ ☐ Mobile App Push Notifications                       │   │         │
│  │  └──────────────────────────────────────────────────────┘   │         │
│  │                                                               │         │
│  └───────────────────────────────────────────────────────────────┘         │
│                                                                            │
│  ┌─── SYSTEM STATUS ───────────────────────────────────────────┐         │
│  │                                                               │         │
│  │  Service Health                    Last Updated: 2 min ago   │         │
│  │  ┌──────────────────────────────────────────────────────┐   │         │
│  │  │ Service Name              Status    Response Time     │   │         │
│  │  │ ──────────────────────────────────────────────────   │   │         │
│  │  │ API Gateway              ✓ UP      42ms             │   │         │
│  │  │ Event Management         ✓ UP      89ms             │   │         │
│  │  │ Speaker Coordination     ✓ UP      76ms             │   │         │
│  │  │ Partner Analytics        ✓ UP      112ms            │   │         │
│  │  │ Attendee Experience      ✓ UP      58ms             │   │         │
│  │  │ Company Management       ✓ UP      67ms             │   │         │
│  │  │ PostgreSQL Database      ✓ UP      12ms             │   │         │
│  │  │ Redis Cache              ✓ UP      3ms              │   │         │
│  │  │ AWS S3                   ✓ UP      24ms             │   │         │
│  │  │ AWS SES                  ✓ UP      156ms            │   │         │
│  │  │ CloudFront CDN           ✓ UP      18ms             │   │         │
│  │  │                          [Refresh Status]             │   │         │
│  │  └──────────────────────────────────────────────────────┘   │         │
│  │                                                               │         │
│  │  System Metrics (Last 24 Hours)                              │         │
│  │  ┌──────────────────────────────────────────────────────┐   │         │
│  │  │ API Requests:           1,234,567 requests           │   │         │
│  │  │ Average Response Time:  145ms                        │   │         │
│  │  │ Error Rate:             0.12%                        │   │         │
│  │  │ Active Users:           2,341 users                  │   │         │
│  │  │ Storage Used:           1.2 TB / 5 TB (24%)          │   │         │
│  │  │ Cache Hit Rate:         94.3%                        │   │         │
│  │  └──────────────────────────────────────────────────────┘   │         │
│  │                                                               │         │
│  │  Recent Activity Log                                          │         │
│  │  ┌──────────────────────────────────────────────────────┐   │         │
│  │  │ 14:23  System settings updated by admin@batbern.ch   │   │         │
│  │  │ 12:45  Feature flag enabled: Progressive Publishing  │   │         │
│  │  │ 09:15  AWS integration test: SUCCESS                 │   │         │
│  │  │ 08:00  Scheduled backup completed                    │   │         │
│  │  │                         [View Full Activity Log]      │   │         │
│  │  └──────────────────────────────────────────────────────┘   │         │
│  │                                                               │         │
│  └───────────────────────────────────────────────────────────────┘         │
│                                                                            │
│  ┌─── SECURITY & ACCESS ───────────────────────────────────────┐         │
│  │                                                               │         │
│  │  Access Control                                               │         │
│  │  ┌──────────────────────────────────────────────────────┐   │         │
│  │  │ Admin Users:  [3 users with system admin access]     │   │         │
│  │  │               • admin@batbern.ch (You)               │   │         │
│  │  │               • organizer1@batbern.ch                │   │         │
│  │  │               • organizer2@batbern.ch                │   │         │
│  │  │               [Manage Admin Access]                   │   │         │
│  │  └──────────────────────────────────────────────────────┘   │         │
│  │                                                               │         │
│  │  Security Settings                                            │         │
│  │  ┌──────────────────────────────────────────────────────┐   │         │
│  │  │ ☑ Require MFA for admin users                        │   │         │
│  │  │ ☑ Enable audit logging                               │   │         │
│  │  │ ☑ Enable IP-based access restrictions                │   │         │
│  │  │ Session Timeout: [30 minutes            ]            │   │         │
│  │  │ Password Policy:  Minimum 12 chars, symbols required │   │         │
│  │  └──────────────────────────────────────────────────────┘   │         │
│  │                                                               │         │
│  │  Rate Limiting                                                │         │
│  │  ┌──────────────────────────────────────────────────────┐   │         │
│  │  │ Public API:      [100 requests/minute       ]        │   │         │
│  │  │ Authenticated:   [1000 requests/minute      ]        │   │         │
│  │  │ Admin API:       [Unlimited                 ]        │   │         │
│  │  └──────────────────────────────────────────────────────┘   │         │
│  │                                                               │         │
│  └───────────────────────────────────────────────────────────────┘         │
│                                                                            │
│                      [Cancel]              [Save All Changes]              │
│                                                                            │
└───────────────────────────────────────────────────────────────────────────┘
```

---

## Key Interactive Elements

### Navigation & Organization
- **Tab Navigation**: Six primary configuration sections with visual active state indicator
- **Sticky Header**: Header with back button and save action remains visible during scroll
- **Collapsible Sections**: Each configuration panel can be expanded/collapsed for focused editing

### Platform Configuration
- **Text Inputs**: Editable fields for platform name, contact emails, and system identifiers
- **Dropdowns**: Language selection, date format, timezone with searchable options
- **Checkboxes**: Multi-select for supported languages and feature toggles
- **Test Buttons**: "Test Email Configuration" triggers validation and sends test email

### Workflow Configuration
- **Editable Table**: Inline editing of workflow step names, durations, and auto-skip settings
- **Duration Input**: Number inputs with day/week unit selectors
- **Auto-Skip Toggles**: Checkboxes to enable/disable automatic step progression
- **Workflow Editor Modal**: "Edit Workflow Steps" opens detailed step configuration

### Integration Settings
- **Connection Status Indicators**: Visual checkmarks (✓) showing successful connections
- **Masked Inputs**: API keys displayed as asterisks with "Reveal" button
- **Test Integration Buttons**: Validate connections to AWS services and external APIs
- **Toggle Switches**: Enable/disable specific integration features

### Feature Flags
- **Tiered Checkboxes**: Core features (always enabled), Beta features (opt-in), Experimental (dev/staging only)
- **Environment Badges**: Visual indicators showing which environments support experimental features
- **Bulk Actions**: "Enable All Beta Features" / "Disable All Experimental" quick actions

### System Status
- **Real-Time Status Grid**: Color-coded service health with response times
- **Auto-Refresh**: Status updates every 2 minutes with manual refresh button
- **Metrics Dashboard**: Key performance indicators with visual progress bars
- **Activity Log**: Scrollable log with timestamp, user, and action details
- **Drill-Down Links**: Click service names to view detailed metrics

### Security & Access
- **User Management**: List of admin users with inline add/remove capabilities
- **Security Toggles**: Enable/disable MFA, audit logging, IP restrictions
- **Input Validation**: Real-time validation for session timeout and password policy settings
- **Rate Limit Sliders**: Adjustable request limits with visual indicators

---

## Functional Requirements Met

- **FR1 (Role-based Authentication)**: Admin access control, MFA enforcement, session management
- **FR2 (Event Workflow Management)**: Configurable 16-step workflow with customizable durations and automation rules
- **FR5 (Progressive Publishing)**: Feature flag control for publishing engine, email template management
- **FR7 (Email Notifications)**: AWS SES configuration, sender settings, daily send limits
- **NFR2 (Performance)**: System metrics monitoring, service health tracking, response time analytics
- **NFR3 (External Integrations)**: AWS service configuration, Swiss UID API setup, calendar sync settings
- **NFR4 (Multi-language Support)**: i18n configuration, default language, supported languages management

---

## User Interactions

### Loading State
1. User clicks "System Settings" from Event Management Dashboard
2. Screen displays loading skeleton with animated placeholders
3. API calls fetch configuration data in parallel
4. Sections populate as data arrives (progressive rendering)

### Editing Configuration
1. User navigates to desired configuration tab
2. User modifies settings (text input, dropdown selection, checkbox toggle)
3. "Save Changes" button becomes highlighted to indicate unsaved changes
4. Browser warns user if attempting to navigate away with unsaved changes

### Testing Integrations
1. User clicks "Test Email Configuration" button
2. Modal prompts for recipient email address
3. System sends test email via AWS SES
4. Success/failure notification displays with detailed error messages if applicable
5. Connection status indicator updates based on test results

### Managing Workflow Steps
1. User clicks "Edit Workflow Steps" button
2. Modal opens showing detailed workflow step editor
3. User can:
   - Reorder steps via drag-and-drop
   - Edit step names, descriptions, and durations
   - Set conditional step logic
   - Configure notification templates per step
4. Changes validated before saving (no circular dependencies, minimum durations)
5. Preview shows workflow timeline with new configuration

### Monitoring System Health
1. Status panel auto-refreshes every 2 minutes
2. User can click "Refresh Status" for immediate update
3. Clicking service name opens detailed metrics modal with:
   - Response time trends (last 24 hours)
   - Error rate breakdown
   - Recent error logs
   - Historical uptime (30/60/90 days)
4. Degraded service displays warning icon with diagnostic information

### Saving Changes
1. User clicks "Save All Changes" button
2. Validation runs across all modified settings
3. If validation fails:
   - Errors highlighted inline with specific field messages
   - Screen scrolls to first error
   - Save button remains disabled until errors resolved
4. If validation passes:
   - Confirmation modal shows summary of changes
   - User confirms or cancels
   - On confirm: API calls execute, success notification displays
   - On error: Detailed error message with rollback option

---

## Technical Notes

### Frontend Implementation
- **Framework**: React 18.2 with TypeScript, Material-UI components
- **State Management**: Zustand for local form state, React Query for server state and caching
- **Form Handling**: React Hook Form with Zod schema validation
- **Real-time Updates**: Polling (30s interval) for system status, WebSocket for critical alerts
- **Responsive Design**: Tabs collapse to dropdown on mobile, collapsible sections for small screens

### Backend Integration
- **Configuration Storage**: PostgreSQL `system_configuration` table with JSONB columns for flexible schema
- **Audit Trail**: All changes logged to `configuration_audit_log` with timestamp, user, old/new values
- **Validation**: Server-side validation mirrors client-side rules, business logic validation (e.g., workflow dependencies)
- **Caching**: Redis cache for read-heavy configuration with 5-minute TTL, cache invalidation on updates

### Performance Optimization
- **Code Splitting**: Each configuration tab lazy-loaded separately
- **Debounced Validation**: Client-side validation debounced 500ms to reduce unnecessary API calls
- **Optimistic Updates**: UI updates immediately, rollback on API failure
- **Stale-While-Revalidate**: React Query serves cached data while revalidating in background

### Security Considerations
- **Authorization**: Admin-only endpoint, JWT role check, additional ADMIN_SETTINGS permission
- **Input Sanitization**: All inputs sanitized to prevent XSS, SQL injection
- **Audit Logging**: All configuration changes logged with user ID, timestamp, IP address
- **Change Approval**: Critical settings (security, integrations) require two-admin approval (future enhancement)

---

## API Requirements

### Initial Page Load APIs

1. **GET /api/v1/admin/system-config**
   - **Authorization**: Requires `ADMIN_SETTINGS` permission
   - **Query Params**: None
   - **Returns**: Complete system configuration object
     ```json
     {
       "platform": {
         "name": "BATbern Event Platform",
         "supportEmail": "support@batbern.ch",
         "adminContact": "admin@batbern.ch"
       },
       "i18n": {
         "defaultLanguage": "de-CH",
         "supportedLanguages": ["de", "en"],
         "dateFormat": "DD.MM.YYYY",
         "timezone": "Europe/Zurich"
       },
       "email": {
         "provider": "AWS_SES",
         "senderName": "BATbern Events",
         "replyToAddress": "noreply@batbern.ch",
         "dailySendLimit": 50000
       },
       "storage": {
         "s3Bucket": "batbern-content-eu-central-1",
         "cdnDomain": "d3tsrt2aaweqwh.cloudfront.net",
         "maxFileSizeMB": 200,
         "allowedFileTypes": ["PDF", "PPTX", "PNG", "JPG", "MP4", "ZIP"],
         "userStorageQuotaGB": 5
       },
       "workflow": {
         "steps": [...], // 16-step workflow configuration
         "deadlineReminders": true,
         "autoEscalation": true,
         "gracePeriodDays": 2
       },
       "integrations": {
         "aws": {...},
         "swissUid": {...},
         "calendar": {...}
       },
       "featureFlags": {...},
       "security": {...}
     }
     ```
   - **Used for**: Populating all configuration forms with current values

2. **GET /api/v1/admin/system-status**
   - **Authorization**: Requires `ADMIN_SETTINGS` permission
   - **Query Params**: None
   - **Returns**: Real-time system health and metrics
     ```json
     {
       "services": [
         {
           "name": "API Gateway",
           "status": "UP",
           "responseTimeMs": 42,
           "lastChecked": "2024-03-15T14:23:00Z"
         },
         // ... other services
       ],
       "metrics": {
         "apiRequests24h": 1234567,
         "avgResponseTimeMs": 145,
         "errorRate": 0.0012,
         "activeUsers": 2341,
         "storageUsedTB": 1.2,
         "storageLimitTB": 5.0,
         "cacheHitRate": 0.943
       },
       "activityLog": [
         {
           "timestamp": "2024-03-15T14:23:00Z",
           "action": "System settings updated",
           "userId": "admin@batbern.ch",
           "details": "Platform configuration changed"
         },
         // ... recent activities
       ]
     }
     ```
   - **Used for**: System Status dashboard, real-time monitoring

3. **GET /api/v1/admin/feature-flags**
   - **Authorization**: Requires `ADMIN_SETTINGS` permission
   - **Query Params**: `?environment={dev|staging|production}`
   - **Returns**: Feature flag states for specified environment
     ```json
     {
       "core": {
         "progressivePublishing": true,
         "speakerOverflowManagement": true,
         "multiOrganizerCollaboration": true,
         "realtimeWorkflowUpdates": true,
         "topicSimilarityDetection": true
       },
       "beta": {
         "aiContentRecommendations": false,
         "automatedReportGeneration": false,
         "advancedPartnerAnalytics": false,
         "attendeeCommunityFeatures": false
       },
       "experimental": {
         "graphqlApiGateway": false,
         "websocketRealtime": false,
         "mobilePushNotifications": false
       }
     }
     ```
   - **Used for**: Feature Flags section, environment-specific toggle states

4. **GET /api/v1/admin/access-control**
   - **Authorization**: Requires `ADMIN_SETTINGS` permission
   - **Query Params**: None
   - **Returns**: Admin user list and access control settings
     ```json
     {
       "adminUsers": [
         {
           "userId": "user-123",
           "email": "admin@batbern.ch",
           "name": "Admin User",
           "roles": ["ORGANIZER", "SYSTEM_ADMIN"],
           "mfaEnabled": true,
           "lastLogin": "2024-03-15T08:30:00Z"
         },
         // ... other admin users
       ],
       "securitySettings": {
         "requireMfaForAdmins": true,
         "auditLoggingEnabled": true,
         "ipRestrictions": true,
         "sessionTimeoutMinutes": 30,
         "passwordPolicy": {
           "minLength": 12,
           "requireSymbols": true,
           "requireNumbers": true,
           "requireUppercase": true
         }
       },
       "rateLimits": {
         "publicApi": 100,
         "authenticatedApi": 1000,
         "adminApi": null
       }
     }
     ```
   - **Used for**: Security & Access section

---

### User Action APIs

1. **PUT /api/v1/admin/system-config**
   - **Triggered by**: "Save All Changes" button click
   - **Authorization**: Requires `ADMIN_SETTINGS` permission
   - **Payload**: Partial or complete system configuration object
     ```json
     {
       "platform": { ... },
       "i18n": { ... },
       "email": { ... },
       "storage": { ... },
       "workflow": { ... },
       "integrations": { ... }
     }
     ```
   - **Validation**:
     - Email addresses must be valid format
     - Storage quotas must be positive integers
     - Workflow step durations must be > 0
     - Integration credentials validated if changed
   - **Response**:
     ```json
     {
       "success": true,
       "updatedFields": ["email.senderName", "workflow.gracePeriodDays"],
       "changeId": "change-789",
       "auditLogId": "audit-456"
     }
     ```
   - **Error Response** (400):
     ```json
     {
       "success": false,
       "errors": [
         {
           "field": "email.replyToAddress",
           "message": "Invalid email format"
         },
         {
           "field": "workflow.steps[2].duration",
           "message": "Duration must be at least 1 day"
         }
       ]
     }
     ```
   - **Used for**: Persisting all configuration changes, triggering audit log entry

2. **POST /api/v1/admin/test-email-config**
   - **Triggered by**: "Test Email Configuration" button
   - **Authorization**: Requires `ADMIN_SETTINGS` permission
   - **Payload**:
     ```json
     {
       "recipientEmail": "test@example.com",
       "testEmailType": "SYSTEM_TEST"
     }
     ```
   - **Response**:
     ```json
     {
       "success": true,
       "messageId": "ses-msg-123456",
       "deliveryStatus": "SENT",
       "timestamp": "2024-03-15T14:30:00Z"
     }
     ```
   - **Error Response** (500):
     ```json
     {
       "success": false,
       "error": "SES_CONNECTION_FAILED",
       "message": "Unable to connect to AWS SES. Check credentials and region.",
       "details": "InvalidClientTokenId: The security token included in the request is invalid."
     }
     ```
   - **Used for**: Validating AWS SES configuration, testing email delivery

3. **POST /api/v1/admin/test-integration**
   - **Triggered by**: "Test AWS Connections", "Test UID Verification" buttons
   - **Authorization**: Requires `ADMIN_SETTINGS` permission
   - **Payload**:
     ```json
     {
       "integrationType": "AWS_S3" | "AWS_COGNITO" | "SWISS_UID",
       "testData": {
         // Integration-specific test parameters
       }
     }
     ```
   - **Response**:
     ```json
     {
       "success": true,
       "integrationType": "AWS_S3",
       "testResults": {
         "connectionStatus": "SUCCESS",
         "responseTimeMs": 24,
         "message": "Successfully connected to S3 bucket and verified permissions"
       }
     }
     ```
   - **Used for**: Validating external service integrations, updating connection status indicators

4. **PATCH /api/v1/admin/feature-flags**
   - **Triggered by**: Feature flag checkbox toggle
   - **Authorization**: Requires `ADMIN_SETTINGS` permission
   - **Payload**:
     ```json
     {
       "environment": "production",
       "flag": "beta.aiContentRecommendations",
       "enabled": true
     }
     ```
   - **Response**:
     ```json
     {
       "success": true,
       "flag": "beta.aiContentRecommendations",
       "previousValue": false,
       "newValue": true,
       "effectiveTimestamp": "2024-03-15T14:35:00Z"
     }
     ```
   - **Used for**: Real-time feature flag updates, propagating changes to all services

5. **PUT /api/v1/admin/workflow-config**
   - **Triggered by**: "Save" in Edit Workflow Steps modal
   - **Authorization**: Requires `ADMIN_SETTINGS` permission
   - **Payload**:
     ```json
     {
       "steps": [
         {
           "stepNumber": 1,
           "name": "Topic Definition",
           "durationDays": 7,
           "autoSkip": false,
           "notificationTemplate": "TOPIC_DEFINITION_REMINDER"
         },
         // ... all 16 steps
       ],
       "deadlineReminders": true,
       "reminderDaysBefore": [7, 3, 1],
       "autoEscalation": true,
       "gracePeriodDays": 2
     }
     ```
   - **Validation**:
     - Step numbers must be sequential 1-16
     - No circular dependencies
     - Durations must be positive integers
   - **Response**:
     ```json
     {
       "success": true,
       "validationWarnings": [
         "Step 5 duration increased from 30 to 45 days. Existing events will not be affected."
       ],
       "affectedEventsCount": 0
     }
     ```
   - **Used for**: Updating workflow configuration, affecting new events only (existing events retain original workflow)

6. **POST /api/v1/admin/access-control/grant**
   - **Triggered by**: "Add Admin User" button
   - **Authorization**: Requires `ADMIN_SETTINGS` permission
   - **Payload**:
     ```json
     {
       "userId": "user-456",
       "grantAdminRole": true,
       "reason": "New platform administrator"
     }
     ```
   - **Response**:
     ```json
     {
       "success": true,
       "userId": "user-456",
       "rolesGranted": ["SYSTEM_ADMIN"],
       "auditLogId": "audit-789"
     }
     ```
   - **Used for**: Granting system admin access to organizer users

7. **DELETE /api/v1/admin/access-control/revoke/{userId}**
   - **Triggered by**: Remove icon next to admin user name
   - **Authorization**: Requires `ADMIN_SETTINGS` permission
   - **Payload**:
     ```json
     {
       "reason": "User no longer requires admin access"
     }
     ```
   - **Response**:
     ```json
     {
       "success": true,
       "userId": "user-456",
       "rolesRevoked": ["SYSTEM_ADMIN"],
       "auditLogId": "audit-790"
     }
     ```
   - **Used for**: Revoking system admin privileges

8. **PATCH /api/v1/admin/security-settings**
   - **Triggered by**: Security settings checkbox or input change + Save
   - **Authorization**: Requires `ADMIN_SETTINGS` permission
   - **Payload**:
     ```json
     {
       "requireMfaForAdmins": true,
       "sessionTimeoutMinutes": 30,
       "rateLimits": {
         "publicApi": 100,
         "authenticatedApi": 1000
       }
     }
     ```
   - **Response**:
     ```json
     {
       "success": true,
       "updatedSettings": ["requireMfaForAdmins", "sessionTimeoutMinutes"],
       "affectedUsersCount": 3
     }
     ```
   - **Used for**: Updating security and access control settings

---

## Navigation Map

### Primary Navigation Actions

1. **← Back to Dashboard** → Navigate to `Event Management Dashboard`
   - **Target**: `story-1.16-event-management-dashboard.md`
   - **Context**: None (return to main organizer dashboard)
   - **Unsaved Changes**: Warn user if configuration modified but not saved

2. **[Save Changes] button** → Execute save, remain on current screen
   - **Action**: Triggers `PUT /api/v1/admin/system-config`
   - **Success**: Toast notification "Configuration saved successfully", button disabled
   - **Failure**: Inline error messages, scroll to first error field

3. **Tab Navigation (6 tabs)** → Switch between configuration sections
   - **Tabs**: Platform Configuration, Workflow Configuration, Integration Settings, Feature Flags, System Status, Security & Access
   - **Behavior**: Client-side navigation, no page reload
   - **State**: Preserve scroll position per tab, highlight unsaved changes

---

### Secondary Navigation (Data Interactions)

1. **Click service name in System Status** → Open service detail modal
   - **Target**: Modal overlay with detailed metrics for selected service
   - **Content**:
     - Response time trends (chart)
     - Error rate breakdown
     - Recent error logs (last 50 entries)
     - Historical uptime (30/60/90 days)
   - **Actions**: Export logs, view documentation, restart service (if applicable)

2. **[View Full Activity Log] link** → Navigate to Activity Log screen
   - **Target**: Dedicated activity log viewer (full-page)
   - **Context**: Pre-filtered to system configuration changes
   - **Features**: Search, date range filter, export to CSV

3. **[Edit Workflow Steps] button** → Open workflow editor modal
   - **Target**: Modal overlay with draggable workflow step editor
   - **Content**:
     - Visual workflow timeline
     - Editable step details (name, duration, notifications)
     - Conditional logic configuration
   - **Actions**: Save, Cancel, Preview changes

4. **[Manage Admin Access] link** → Open admin user management modal
   - **Target**: Modal overlay with admin user list and add/remove capabilities
   - **Content**:
     - Current admin users (name, email, last login)
     - [Add Admin User] button (opens user search)
     - Remove icon per user (confirmation required)
   - **Actions**: Add admin, remove admin, view audit log

---

### Event-Driven Navigation

1. **Configuration save success** → Display toast notification, remain on screen
   - **Notification**: "Configuration saved successfully" (green toast, 5s duration)
   - **Button State**: "Save Changes" button disabled until next edit
   - **Audit Log**: New entry added to activity log

2. **Integration test failure** → Display error notification with diagnostic information
   - **Notification**: "Integration test failed" (red toast, persistent until dismissed)
   - **Details Modal**: Option to view full error details and suggested fixes
   - **Connection Status**: Update status indicator to red X

3. **System status degradation** → Display alert banner at top of screen
   - **Trigger**: API polling detects service status change to DOWN or DEGRADED
   - **Banner**: "System Alert: {Service Name} is experiencing issues. View details →"
   - **Action**: Click banner navigates to System Status tab, scrolls to affected service

4. **Real-time configuration change** → Display notification if another admin modifies settings
   - **Trigger**: WebSocket event `CONFIG_CHANGED` received
   - **Notification**: "Configuration updated by {admin@batbern.ch}. Refresh to see latest changes."
   - **Action**: [Refresh] button reloads configuration, discarding local unsaved changes (with warning)

---

### Error States & Redirects

1. **Unauthorized access (403)** → Redirect to Event Management Dashboard
   - **Condition**: User lacks `ADMIN_SETTINGS` permission
   - **Notification**: "You do not have permission to access system settings."
   - **Redirect**: After 3 seconds, navigate to `Event Management Dashboard`

2. **Session timeout (401)** → Redirect to login screen
   - **Condition**: JWT token expired during configuration editing
   - **Notification**: "Your session has expired. Please log in again."
   - **State Preservation**: Save draft configuration to localStorage for recovery after re-login

3. **Network error** → Display error banner with retry option
   - **Condition**: API request fails due to network issue
   - **Banner**: "Network error. Unable to save configuration. [Retry] [Save Offline]"
   - **Retry**: Attempt API call again
   - **Save Offline**: Store changes locally, sync when connection restored

4. **Validation error (400)** → Highlight fields with errors
   - **Condition**: Server-side validation fails
   - **Behavior**:
     - Scroll to first invalid field
     - Display inline error messages below each invalid field
     - "Save Changes" button remains enabled for retry
   - **Example Errors**:
     - "Email address is required"
     - "Workflow step duration must be at least 1 day"
     - "Rate limit must be between 10 and 10,000 requests/minute"

5. **Concurrent modification conflict (409)** → Display merge conflict resolution modal
   - **Condition**: Another admin saved changes while current user was editing
   - **Modal**:
     - Show current values, other admin's changes, user's changes (3-column diff)
     - Options: Keep your changes, Accept their changes, Merge manually
   - **Action**: User selects resolution strategy, re-saves with conflict marker

---

## Responsive Design Considerations

### Mobile Layout Changes (< 768px)

- **Tab Navigation**: Converts to dropdown select menu
- **Configuration Panels**: Stack vertically, full width
- **Multi-column Layouts**: Collapse to single column (e.g., Integration Settings)
- **System Status Table**: Horizontal scroll enabled, sticky service name column
- **Form Fields**: Full width with touch-friendly sizing (min 44px height)
- **Save Button**: Sticky footer button for easy access while scrolling

### Tablet Layout Changes (768px - 1024px)

- **Sidebar Navigation**: Tabs remain horizontal but with condensed labels
- **Configuration Panels**: 2-column grid where appropriate (e.g., Platform + i18n side-by-side)
- **System Status**: Maintain table layout with condensed columns
- **Modals**: Max-width 90% viewport, centered

### Mobile-Specific Interactions

- **Swipe Gestures**: Swipe left/right to navigate between tabs
- **Pull-to-Refresh**: Pull down on System Status tab to refresh metrics
- **Touch Targets**: Minimum 44x44px for all interactive elements
- **Keyboard Avoidance**: Auto-scroll to keep focused input visible when keyboard opens

---

## Accessibility Notes

- **Keyboard Navigation**: Full tab navigation support, all interactive elements reachable via keyboard
- **Focus Indicators**: High-contrast focus rings (3px solid #0066CC) on all interactive elements
- **ARIA Labels**:
  - Tab navigation: `aria-label="Configuration section navigation"`
  - Status indicators: `aria-label="Service API Gateway status: Up, response time 42 milliseconds"`
  - Form inputs: Proper `aria-describedby` linking to help text and error messages
- **Screen Reader Announcements**:
  - Configuration saves: "Configuration saved successfully" via `aria-live="polite"`
  - Errors: "Validation error in email configuration" via `aria-live="assertive"`
- **Color Contrast**: All text meets WCAG 2.1 AA standards (4.5:1 for normal text, 3:1 for large text)
- **Error Messaging**: Errors conveyed via text, not color alone (red icon + text message)
- **Form Labels**: All inputs have associated `<label>` elements or `aria-label` attributes
- **Skip Links**: "Skip to configuration content" link for keyboard users to bypass header/tabs

---

## State Management

### Local Component State

- `currentTab`: Active configuration tab (Platform | Workflow | Integrations | Features | Status | Security)
- `isDirty`: Boolean indicating unsaved changes present
- `validationErrors`: Object mapping field paths to error messages
- `testResults`: Results from integration tests (success/failure, timestamps)
- `isRefreshing`: Boolean for System Status refresh loading state
- `expandedSections`: Array of section IDs that are currently expanded

### Global State (Zustand Store)

- `systemConfiguration`: Complete configuration object (mirrored from API)
- `featureFlags`: Environment-specific feature flag states
- `adminUsers`: List of users with admin privileges
- `systemStatus`: Latest system health metrics
- `lastConfigUpdate`: Timestamp of last configuration save (for conflict detection)

### Server State (React Query)

- **Query Keys**:
  - `['admin', 'system-config']`: System configuration (5 min cache)
  - `['admin', 'system-status']`: System health status (2 min cache, auto-refetch)
  - `['admin', 'feature-flags', environment]`: Feature flags per environment (10 min cache)
  - `['admin', 'access-control']`: Admin users and security settings (5 min cache)
  - `['admin', 'activity-log']`: Recent configuration changes (1 min cache)

- **Mutations**:
  - `updateSystemConfig`: Invalidates `system-config` query on success
  - `updateFeatureFlag`: Invalidates `feature-flags` query, broadcasts change via EventBridge
  - `grantAdminAccess`: Invalidates `access-control` query, triggers audit log
  - `testIntegration`: No cache invalidation, updates local `testResults` state

### Real-Time Updates

- **Polling Strategy**: System Status polling every 2 minutes (configurable via `POLL_INTERVAL_MS`)
- **WebSocket Events**:
  - `CONFIG_CHANGED`: Another admin saved configuration, prompt user to refresh
  - `SERVICE_STATUS_CHANGED`: Service health degradation, update status panel immediately
- **Optimistic Updates**: Configuration changes reflected immediately in UI, rolled back on API failure

---

## Form Validation Rules

### Field-Level Validations

- **Platform Name**: Required, 3-100 characters, alphanumeric + spaces
- **Support Email**: Required, valid email format (RFC 5322), must end with `@batbern.ch` or approved domain
- **Admin Contact**: Required, valid email format
- **Default Language**: Required, must be one of supported languages
- **Supported Languages**: At least one language must be selected
- **Date Format**: Required, must match predefined format patterns (DD.MM.YYYY, MM/DD/YYYY, YYYY-MM-DD)
- **Time Zone**: Required, valid IANA timezone identifier (e.g., Europe/Zurich)
- **Sender Name**: Required, 1-50 characters
- **Reply-to Address**: Required, valid email format
- **Daily Send Limit**: Required, integer between 1,000 and 200,000
- **Max File Size**: Required, integer between 1 and 500 MB
- **Storage Quota**: Required, integer between 1 and 100 GB
- **Workflow Step Duration**: Required, integer ≥ 1 day
- **Session Timeout**: Required, integer between 5 and 480 minutes
- **Rate Limit**: Optional, if specified must be integer between 10 and 100,000 requests/minute

### Form-Level Validations

- **Language Consistency**: Default language must be included in supported languages list
- **Email Configuration**: If daily send limit > 50,000, AWS SES must be in production mode (verified)
- **Workflow Dependencies**: No circular dependencies in conditional workflow steps
- **Storage Capacity**: Total allocated user quotas cannot exceed available S3 storage capacity
- **Security Policy**: If MFA required for admins, all current admin users must have MFA enabled
- **Rate Limiting**: Authenticated rate limit must be ≥ public rate limit

---

## Edge Cases & Error Handling

### Empty States

- **No Admin Users**: System requires at least one admin user. UI prevents removing last admin, displays warning.
- **No Workflow Steps**: Default 16-step workflow loads if custom workflow deleted. User cannot save with 0 steps.
- **No Supported Languages**: UI prevents deselecting all languages, enforces minimum of one language.

### Loading States

- **Initial Load**: Display skeleton screens for each configuration section
- **Saving Configuration**: "Save Changes" button shows spinner, disabled during save
- **Testing Integration**: Test buttons show spinner, disabled during test
- **Refreshing Status**: System Status panel shows "Refreshing..." overlay

### Error States

- **API Unavailable**: Display banner "System configuration service unavailable. Your changes are saved locally and will sync when service is restored."
- **Partial Save Failure**: If some settings save but others fail, show summary: "3 of 5 settings saved. Failed: email.replyToAddress, workflow.steps[2].duration. Retry failed items?"
- **Integration Test Timeout**: After 30s, display "Integration test timed out. Service may be unavailable or experiencing high latency."
- **Invalid Configuration**: If server detects invalid configuration state (e.g., corrupted database entry), display full-page error with "Reset to Defaults" option.

### Permission Issues

- **Admin Access Revoked Mid-Session**: If admin permission revoked while user is on screen, display modal "Your admin access has been revoked. You will be redirected." → Redirect to Event Management Dashboard after 5s.
- **Read-Only Mode**: If user has `ADMIN_SETTINGS_READ` but not `ADMIN_SETTINGS_WRITE`, display all fields as disabled with info banner "You have read-only access to system settings."

### Concurrent Editing

- **Another Admin Editing**: Display info banner "Admin user {email} is currently editing system settings. Your changes may conflict."
- **Conflict Resolution**: On save conflict (409), show 3-column diff (current, their changes, your changes) with merge options.

### Data Integrity

- **Workflow Step Deletion**: If deleting workflow step used by active events, warn "3 events are using this workflow step. Deleting will affect those events. Continue?"
- **Integration Credential Rotation**: If AWS credentials changed externally, integration tests fail with "Credentials invalid. Please update integration settings."
- **Storage Quota Exceeded**: If reducing user storage quota below current usage, warn "12 users exceed new quota. They will be notified to reduce storage usage."

---

## Change Log

| Date       | Version | Description                          | Author       |
|------------|---------|--------------------------------------|--------------|
| 2024-03-15 | 1.0     | Initial wireframe creation          | Sally (UX)   |

---

## Review Notes

### Stakeholder Feedback

*To be added after stakeholder review*

### Design Iterations

*To be documented as design evolves*

### Open Questions

1. **Two-Admin Approval Workflow**: Should critical settings (security, integrations) require approval from a second admin before taking effect?
   - **Impact**: Enhanced security, but adds complexity and potential delays
   - **Recommendation**: Consider for post-MVP security enhancement

2. **Rollback Capability**: Should admins be able to rollback configuration to a previous version?
   - **Current**: Audit log tracks changes but no one-click rollback
   - **Recommendation**: Add "Revert to Version" feature in future iteration

3. **Environment-Specific Configuration**: Should each environment (dev/staging/production) have fully independent configuration, or shared with overrides?
   - **Current**: Single production configuration, feature flags per environment
   - **Consideration**: Some settings (e.g., storage quotas, rate limits) may need environment-specific values

4. **Real-Time Configuration Updates**: Should configuration changes propagate immediately to all services, or require a deployment/restart?
   - **Current**: Feature flags update immediately, other config requires service restart
   - **Recommendation**: Evaluate feasibility of hot-reloading for non-critical settings

5. **Mobile Admin Access**: Should system settings be fully functional on mobile, or recommend desktop-only access?
   - **Current**: Fully responsive design, but complex configuration may be difficult on small screens
   - **Recommendation**: Support mobile for viewing/monitoring, encourage desktop for editing
