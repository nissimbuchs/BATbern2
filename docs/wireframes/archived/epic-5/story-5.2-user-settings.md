# Story 5.2: Advanced User Settings (Attendee Experience) - Wireframe

> **📦 EPIC 7 (DEFERRED TO PHASE 2)**
> **⚠️ BASIC SETTINGS MOVED TO STORY 2.6 (Epic 2)**
> Basic account settings (profile info, email, password, basic notifications, basic privacy) have been moved to **Story 2.6: User Account Management Frontend** in Epic 2.
> **This wireframe now focuses exclusively on ADVANCED attendee-specific features:**
> - Content Preferences (interests, topics, experience level, content formats)
> - Language & Accessibility (UI language, date/time formats, accessibility options)
> - Data & Export (GDPR data export, account deactivation, account deletion)
>
> See `docs/wireframes/story-2.6-user-account-management.md` for basic settings implementation.

**Story**: Epic 7, Story 2 - Personal Engagement Management (Advanced Settings)
**Screen**: Advanced User Settings Screen
**User Role**: Attendee (All roles can access, but attendee-focused features)
**Related FR**: FR14 (Personal Engagement Management - Advanced), NFR4 (Multi-language Support)
**Status**: Deferred to Phase 2 (Epic 7)

---

## Scope Clarification

### ❌ REMOVED - Moved to Story 2.6 (Epic 2)
The following features are now in **Story 2.6** as foundational account management:
- **Account Tab**: Profile information, email, password, profile picture upload
- **Notifications Tab (Basic)**: Event update toggles, newsletter subscription, notification frequency
- **Privacy Tab (Basic)**: Profile visibility, activity visibility, company display

### ✅ RETAINED - Advanced Features in Story 5.2 (Epic 7)
The following **advanced** features remain in this story:
- **Content Preferences Tab**: Interests/topics selection, content language, experience level, content format preferences, default view mode
- **Language & Accessibility Tab**: UI language selection, date/time format preferences, accessibility options (high contrast, larger text, screen reader optimizations, keyboard shortcuts)
- **Data & Export Tab**: GDPR data export request, account deactivation (60-day retention), permanent account deletion

---

## Visual Wireframe

> **Note**: Account, Notifications (basic), and Privacy (basic) tabs have been moved to Story 2.6.
> See `docs/wireframes/story-2.6-user-account-management.md` for those wireframes.
> This wireframe now shows only the **advanced settings** tabs that remain in Epic 7.

[CONTENT PREFERENCES TAB]
┌─────────────────────────────────────────────────────────────────────────────┐
│  ┌─── SETTINGS NAVIGATION ──────┐  ┌─── CONTENT PREFERENCES ──────────────┐ │
│  │                               │  │                                       │ │
│  │  ○ Account                    │  │  Interests & Topics                   │ │
│  │  ○ Notifications              │  │                                       │ │
│  │  ○ Privacy                    │  │  Select your areas of interest:       │ │
│  │  ● Content Preferences        │  │                                       │ │
│  │  ○ Language & Accessibility   │  │  [✓ Kubernetes]  [✓ Cloud Native]    │ │
│  │  ○ Data & Export              │  │  [  Microservices] [  Databases]     │ │
│  │                               │  │  [  AI/ML]       [  Frontend]        │ │
│  │                               │  │  [  Agile]       [  Leadership]      │ │
│  │                               │  │                                       │ │
│  │                               │  │  + Add custom interest (unlimited)    │ │
│  │                               │  │                                       │ │
│  │                               │  │  Content Language                     │ │
│  │                               │  │  Preferred presentation language:     │ │
│  │                               │  │  ☑ German                             │ │
│  │                               │  │  ☑ English                            │ │
│  │                               │  │  ☐ Both (bilingual sessions)          │ │
│  │                               │  │                                       │ │
│  │                               │  │  Experience Level                     │ │
│  │                               │  │  Show content for:                    │ │
│  │                               │  │  ☐ Beginner                           │ │
│  │                               │  │  ☑ Intermediate                       │ │
│  │                               │  │  ☑ Advanced                           │ │
│  │                               │  │                                       │ │
│  │                               │  │  Content Format                       │ │
│  │                               │  │  ☑ Presentations                      │ │
│  │                               │  │  ☑ Workshops                          │ │
│  │                               │  │  ☑ Panels & Discussions               │ │
│  │                               │  │  ☐ Lightning talks                    │ │
│  │                               │  │                                       │ │
│  │                               │  │  Default View Mode                    │ │
│  │                               │  │  ● Grid view  ○ List view             │ │
│  │                               │  │                                       │ │
│  └───────────────────────────────┘  └───────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘

[LANGUAGE & ACCESSIBILITY TAB]
┌─────────────────────────────────────────────────────────────────────────────┐
│  ┌─── SETTINGS NAVIGATION ──────┐  ┌─── LANGUAGE & ACCESSIBILITY ─────────┐ │
│  │                               │  │                                       │ │
│  │  ○ Account                    │  │  Interface Language                   │ │
│  │  ○ Notifications              │  │                                       │ │
│  │  ○ Privacy                    │  │  Select your interface language:      │ │
│  │  ○ Content Preferences        │  │  ● Deutsch (German)                   │ │
│  │  ● Language & Accessibility   │  │  ○ English                            │ │
│  │  ○ Data & Export              │  │  Date & Time Format                   │ │
│  │                               │  │  ● DD.MM.YYYY (European)              │ │
│  │                               │  │  ○ MM/DD/YYYY (US)                    │ │
│  │                               │  │  ○ YYYY-MM-DD (ISO)                   │ │
│  │                               │  │                                       │ │
│  │                               │  │  Time Format                          │ │
│  │                               │  │  ● 24-hour (14:30)                    │ │
│  │                               │  │  ○ 12-hour (2:30 PM)                  │ │
│  │                               │  │                                       │ │
│  │                               │  │  Accessibility Options                │ │
│  │                               │  │                                       │ │
│  │                               │  │  Visual                               │ │
│  │                               │  │  ☐ High contrast mode                 │ │
│  │                               │  │  ☐ Larger text size                   │ │
│  │                               │  │  ☐ Reduce animations                  │ │
│  │                               │  │  ☐ Focus indicators                   │ │
│  │                               │  │                                       │ │
│  │                               │  │  Navigation                           │ │
│  │                               │  │  ☐ Keyboard shortcuts enabled         │ │
│  │                               │  │  ☐ Screen reader optimizations        │ │
│  │                               │  │                                       │ │
│  │                               │  │  [View Keyboard Shortcuts]            │ │
│  │                               │  │                                       │ │
│  └───────────────────────────────┘  └───────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘

[DATA & EXPORT TAB]
┌─────────────────────────────────────────────────────────────────────────────┐
│  ┌─── SETTINGS NAVIGATION ──────┐  ┌─── DATA & EXPORT ────────────────────┐ │
│  │                               │  │                                       │ │
│  │  ○ Account                    │  │  Export Your Data                     │ │
│  │  ○ Notifications              │  │                                       │ │
│  │  ○ Privacy                    │  │  Download a copy of all your data:    │ │
│  │  ○ Content Preferences        │  │  • Profile information                │ │
│  │  ○ Language & Accessibility   │  │  • Event registrations                │ │
│  │  ● Data & Export              │  │  • Download history                   │ │
│  │                               │  │  • Preferences & settings             │ │
│  │                               │  │                                       │ │
│  │                               │  │  Format: ● JSON  ○ CSV                │ │
│  │                               │  │                                       │ │
│  │                               │  │  [Request Data Export]                │ │
│  │                               │  │                                       │ │
│  │                               │  │  ℹ️ You'll receive an email when ready│ │
│  │                               │  │     (usually within 24 hours)         │ │
│  │                               │  │                                       │ │
│  │                               │  │  Account Management                   │ │
│  │                               │  │                                       │ │
│  │                               │  │  Account Status: Active               │ │
│  │                               │  │  Member since: March 2023             │ │
│  │                               │  │  Events attended: 8                   │ │
│  │                               │  │                                       │ │
│  │                               │  │  Deactivate Account                   │ │
│  │                               │  │  Temporarily disable your account     │ │
│  │                               │  │  (60-day retention, then auto-deleted)│ │
│  │                               │  │  ⚠️ Unregisters from all events       │ │
│  │                               │  │  [Deactivate Account]                 │ │
│  │                               │  │                                       │ │
│  │                               │  │  Delete Account                       │ │
│  │                               │  │  ⚠️ Permanently delete all your data  │ │
│  │                               │  │  [Delete My Account]                  │ │
│  │                               │  │                                       │ │
│  └───────────────────────────────┘  └───────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Interactive Elements

- **Tab Navigation**: Six distinct settings categories accessible from left sidebar navigation (Account, Notifications, Privacy, Content Preferences, Language & Accessibility, Data & Export)
- **Form Controls**: Text inputs, checkboxes, radio buttons for various preferences
- **Company Search**: Autocomplete search for company affiliation with validation
- **Profile Picture Upload**: Upload/change profile photo in Account settings (JPEG/PNG, max 5MB)
- **Tag Selection**: Multi-select interest tags with visual feedback (unlimited custom tags allowed)
- **Toggle Switches**: Enable/disable notifications, privacy options, and features
- **Data Export**: Request button triggering asynchronous data export process
- **Danger Zone Actions**: Account deactivation and deletion with confirmation workflows
- **Save/Cancel**: Persistent save bar for unsaved changes with auto-save indication
- **Test Actions**: "Test Email Notifications" to verify notification settings

---

## Functional Requirements Met

- **FR14 (Personal Engagement Management)**: Complete control over content preferences, bookmarks, newsletters, and personal engagement settings
- **NFR1 (Responsive Design)**: Settings layout adapts for mobile, tablet, and desktop with appropriate control sizing
- **NFR4 (Multi-language Support)**: Interface language selection between German and English with date/time format preferences
- **FR15 (Mobile-Optimized Experience)**: Responsive settings layout optimized for mobile, tablet, and desktop devices

---

## User Interactions

### Account Tab
1. **Upload Profile Picture**: Upload or change profile photo (JPEG/PNG, max 5MB, 200x200px minimum)
2. **Edit Profile**: Update email, name, company, and job title
3. **Company Search**: Type to search and select company from database
4. **Change Password**: Navigate to password change flow with security validation
5. **Save Changes**: Persist profile updates with validation

### Notifications Tab
1. **Toggle Notifications**: Enable/disable specific notification types
2. **Configure Frequency**: Set reminder timing preferences
3. **Newsletter Subscription**: Subscribe/unsubscribe with frequency selection
4. **Test Notifications**: Send test email to verify notification settings

### Privacy Tab
1. **Profile Visibility**: Control who can view profile and activity
2. **Activity Settings**: Toggle visibility of registrations, bookmarks, downloads
3. **Company Data Sharing**: Control employer analytics and attendance sharing
4. **Analytics Opt-in/Out**: Enable/disable personalization tracking
5. **Cookie Preferences**: Select cookie policy level

### Content Preferences Tab
1. **Select Interests**: Multi-select tags for topic interests
2. **Add Custom Interest**: Create unlimited custom interest tags
3. **Language Preferences**: Select preferred content language(s)
4. **Experience Level**: Filter content by difficulty level
5. **Content Format**: Choose preferred session formats
6. **View Mode**: Toggle between grid and list views

### Language & Accessibility Tab
1. **Interface Language**: Switch between German and English UI
2. **Date/Time Format**: Select regional format preferences
3. **Accessibility Options**: Enable high contrast, larger text, reduced animations
4. **Keyboard Shortcuts**: Enable keyboard navigation with shortcuts reference
5. **Screen Reader**: Optimize for assistive technologies

### Data & Export Tab
1. **Export Data**: Request complete data export in JSON or CSV format
2. **Deactivate Account**: Temporarily disable account (60-day retention, auto-unregisters from events)
3. **Delete Account**: Permanently remove account and all associated data

---

## Technical Notes

- **Component Structure**: Settings organized with tab-based navigation using React Router nested routes
- **Form Management**: React Hook Form for form state with real-time validation
- **Auto-save**: Debounced auto-save every 3 seconds for text inputs, immediate save for toggles
- **Unsaved Changes Warning**: Browser prompt on navigation with unsaved changes
- **Optimistic Updates**: Immediate UI feedback with server sync in background
- **Error Handling**: Inline error messages with retry capability for failed saves
- **Company Autocomplete**: Debounced search with typeahead suggestions from company database
- **Image Upload**: File upload with preview, crop functionality, and format/size validation (max 5MB)
- **i18n Integration**: React-i18next for multi-language support with locale switching
- **ARIA Labels**: Complete accessibility support with proper ARIA attributes and keyboard navigation
- **Responsive Layout**: Sidebar navigation collapses to top tabs on mobile (<768px)
- **Theme Support**: Settings respect user's selected theme (light/dark mode)

---

## API Requirements

### Initial Page Load APIs

**Updated with Story 1.23 Consolidated User APIs**

When the Settings screen loads, the following APIs are called:

1. **GET /api/v1/users/me?include=profile,preferences,settings**
   - Query params: `include` parameter for related data
   - Returns: Complete user object with settings, profile, and preferences
   - **Consolidated**: Single endpoint replaces /users/{id}/settings, /users/{id}/profile, /users/{id}/preferences (3 → 1)
   - **Performance**: <150ms (P95)
     ```json
     {
       "userId": "uuid",
       "profile": {
         "email": "string",
         "firstName": "string",
         "lastName": "string",
         "companyId": "uuid",
         "companyName": "string",
         "jobTitle": "string | null"
       },
       "notifications": {
         "eventReminders": {
           "enabled": "boolean",
           "timing": ["7_days", "1_day", "1_hour"]
         },
         "scheduleChanges": "boolean",
         "speakerAnnouncements": "boolean",
         "venueChanges": "boolean",
         "contentRecommendations": "boolean",
         "weeklyDigest": "boolean",
         "trendingContent": "boolean",
         "newsletter": {
           "subscribed": "boolean",
           "frequency": "monthly | quarterly"
         },
         "emailFormat": "html | plain"
       },
       "privacy": {
         "profileVisibility": "community | organizers | private",
         "showEvents": "boolean",
         "showBookmarks": "boolean",
         "showDownloads": "boolean",
         "displayCompany": "boolean",
         "allowCompanyAnalytics": "boolean",
         "shareAttendance": "boolean",
         "allowPersonalization": "boolean",
         "trackRecommendations": "boolean",
         "shareAnonymousData": "boolean",
         "cookieLevel": "essential_functional | essential_only"
       },
       "contentPreferences": {
         "interests": ["string[]"],
         "contentLanguages": ["de", "en"],
         "experienceLevels": ["beginner", "intermediate", "advanced"],
         "contentFormats": ["presentation", "workshop", "panel", "lightning"],
         "defaultViewMode": "grid | list"
       },
       "language": {
         "interfaceLanguage": "de | en",
         "dateFormat": "DD.MM.YYYY | MM/DD/YYYY | YYYY-MM-DD",
         "timeFormat": "24h | 12h"
       },
       "accessibility": {
         "highContrast": "boolean",
         "largerText": "boolean",
         "reduceAnimations": "boolean",
         "focusIndicators": "boolean",
         "keyboardShortcuts": "boolean",
         "screenReaderOptimized": "boolean"
       },
       "profilePictureUrl": "string | null"
     }
     ```
   - Used for: Populate all settings fields across all tabs with current user preferences

2. **GET /api/v1/companies/search?query={}&limit=20**
   - Query params: `query` (user input), `limit` (default 20)
   - Returns: Company autocomplete suggestions (Caffeine cached)
     ```json
     {
       "suggestions": [
         {
           "id": "uuid",
           "name": "string",
           "industry": "string",
           "partnerStatus": "boolean"
         }
       ]
     }
     ```
   - Used for: Company autocomplete search in Account tab
   - **Consolidated**: Uses Story 1.22 company search endpoint with Caffeine in-memory caching (<100ms P95)
   - Note: Called on-demand when user types in company field

---

## User Action APIs

APIs called by user interactions and actions:

### Save Settings (Consolidated)

1. **PATCH /api/v1/users/me**
   - Triggered by: [Save Changes] button or auto-save after field changes
   - Payload: Partial user object with only changed fields
     ```json
     {
       "preferences": { /* only changed preferences */ },
       "settings": { /* only changed settings */ }
     }
     ```
   - Response: Updated user object
     ```json
     {
       "success": "boolean",
       "updatedAt": "ISO-8601 datetime",
       "user": { /* updated user object */ }
     }
     ```
   - Used for: Persist settings changes to server
   - **Consolidated**: Single PATCH endpoint replaces PUT /users/{id}/settings and PUT /users/{id}/settings/section (2 → 1)
   - **Benefits**: Supports partial updates, reduces payload size
   - Debounced: Auto-save triggered 3 seconds after last change, manual save immediate

2. **PUT /api/v1/users/me/preferences** or **PUT /api/v1/users/me/settings**
   - Triggered by: Section-specific bulk updates (optional, for backwards compatibility)
   - Payload: Complete preferences or settings object
   - Response: Updated section
   - Used for: Full section replacement if needed
   - **Note**: PATCH /users/me is preferred for most use cases

### Account Actions

3. **POST /api/v1/users/{userId}/change-password**
   - Triggered by: [Change Password] button in Account tab
   - Payload:
     ```json
     {
       "currentPassword": "string",
       "newPassword": "string",
       "confirmPassword": "string"
     }
     ```
   - Response: Success confirmation
     ```json
     {
       "success": "boolean",
       "message": "string",
       "requireReauth": "boolean"
     }
     ```
   - Used for: Navigate to password change modal or page, handle password update
   - Security: May require re-authentication if sensitive action

4. **POST /api/v1/users/{userId}/verify-email**
   - Triggered by: [Verify Email] link if email not verified
   - Payload: None
   - Response: Verification email sent confirmation
     ```json
     {
       "sent": "boolean",
       "email": "string"
     }
     ```
   - Used for: Resend email verification link

### Notification Actions

6. **POST /api/v1/users/{userId}/test-notifications**
   - Triggered by: [Test Email Notifications] button
   - Payload: None
   - Response: Test email sent confirmation
     ```json
     {
       "sent": "boolean",
       "sentAt": "ISO-8601 datetime"
     }
     ```
   - Used for: Send test email to verify notification settings work correctly

### Data Export Actions

7. **POST /api/v1/users/{userId}/export-data**
   - Triggered by: [Request Data Export] button
   - Payload:
     ```json
     {
       "format": "json | csv"
     }
     ```
   - Response: Export request confirmation
     ```json
     {
       "exportId": "uuid",
       "status": "pending",
       "estimatedTime": "string (e.g., '24 hours')",
       "notificationEmail": "string"
     }
     ```
   - Used for: Initiate asynchronous data export process
   - Note: User receives email when export is ready for download

8. **GET /api/v1/users/{userId}/exports/{exportId}**
   - Triggered by: Link in export ready email or manual check
   - Response: Export file URL
     ```json
     {
       "exportId": "uuid",
       "status": "completed | pending | failed",
       "downloadUrl": "string (presigned S3 URL)",
       "expiresAt": "ISO-8601 datetime",
       "fileSizeMB": "number"
     }
     ```
   - Used for: Download completed data export file

### Account Management Actions

9. **POST /api/v1/users/{userId}/deactivate**
    - Triggered by: [Deactivate Account] button with confirmation dialog
    - Payload: None
    - Response: Deactivation confirmation
      ```json
      {
        "deactivated": "boolean",
        "deactivatedAt": "ISO-8601 datetime",
        "reactivationToken": "string",
        "retentionPeriodDays": 60,
        "permanentDeletionDate": "ISO-8601 datetime",
        "unregisteredEventsCount": "number"
      }
      ```
    - Used for: Temporarily disable account, allow future reactivation within 60 days
    - Side effects:
      - User logged out
      - All upcoming event registrations cancelled automatically
      - Account and data preserved for 60 days, then permanently deleted
      - Past event attendance history preserved
    - **Note**: Deactivation API remains separate for security and audit trail

10. **POST /api/v1/users/{userId}/reactivate**
    - Triggered by: Login attempt with deactivated account
    - Payload:
      ```json
      {
        "reactivationToken": "string"
      }
      ```
    - Response: Reactivation confirmation
    - Used for: Restore previously deactivated account

11. **DELETE /api/v1/users/{id}**
    - Triggered by: [Delete My Account] button with double confirmation
    - Payload: Confirmation input (e.g., type "DELETE" to confirm)
      ```json
      {
        "confirmation": "string",
        "password": "string"
      }
      ```
    - Response: Deletion confirmation
      ```json
      {
        "deleted": "boolean",
        "deletedAt": "ISO-8601 datetime"
      }
      ```
    - Used for: Permanently delete user account and all associated data
    - **Consolidated**: Part of Story 1.23 with GDPR-compliant cascade deletion
    - Security: Requires password confirmation and explicit consent
    - Side effects: User logged out, all data marked for deletion, cleanup jobs triggered

---

## Navigation Map

Screen transitions triggered by actions and events:

### Primary Navigation Actions

1. **← Back to Dashboard**
   - Target: Personal Dashboard (story-5.2-personal-dashboard.md)
   - Context: User returns to main dashboard
   - Warning: If unsaved changes, show confirmation: "You have unsaved changes. Save before leaving?"
   - Options: [Save & Leave] [Discard Changes] [Cancel]

2. **[Save] / [Save Changes] button**
   - Action: PUT /api/v1/users/{userId}/settings
   - No Navigation: Remains on current settings tab
   - Feedback: Success toast: "Settings saved successfully"
   - Update: Last saved timestamp displayed
   - Error: Inline error message if save fails with [Retry] button

3. **[Cancel] button**
   - Action: Revert form to last saved state
   - No Navigation: Remains on current settings tab
   - Feedback: Toast: "Changes discarded"
   - Confirm: If significant changes, show "Discard changes?" dialog

### Tab Navigation (Within Settings)

4. **Settings Tab Click** (Account, Notifications, Privacy, etc.)
   - Target: Different settings section (same page, content swap)
   - Context: Switch between settings categories
   - Warning: If unsaved changes in current tab, prompt to save first
   - Behavior: Highlight active tab, load section-specific content

### Account Tab Navigation

5. **[Change Password] button**
   - Target: Password Change Modal or dedicated page
   - Type: Modal overlay (preferred) or full page navigation
   - Context: Pass userId
   - On Success: Return to Account tab with success message

6. **[Search Companies] autocomplete**
   - Action: GET /api/v1/reference/companies?search={query}
   - No Navigation: Inline autocomplete dropdown
   - Selection: Populate company field with selected company
   - Feedback: Company logo displayed if available

### Notification Tab Navigation

7. **[Test Email Notifications] button**
   - Action: POST /api/v1/users/{userId}/test-notifications
   - No Navigation: Remains on Notifications tab
   - Feedback: Toast: "Test email sent to {email}"
   - Check: User should check inbox for test email

### Privacy Tab Navigation

8. **[View Privacy Policy] link**
   - Target: Privacy Policy page
   - Type: Open in new tab/window
   - Context: External link to legal privacy policy document
   - No Navigation: Settings page remains open in original tab

### Content Preferences Tab Navigation

9. **Interest Tag Click**
    - Action: Toggle interest selection (local state)
    - No Navigation: Remains on Content Preferences tab
    - Feedback: Visual toggle (filled vs outlined)
    - Save: Changes saved when [Save Changes] clicked

10. **+ Add Custom Interest**
    - Action: Show inline text input to create custom interest
    - No Navigation: Inline expansion on Content Preferences tab
    - On Submit: Add custom interest to selection, close input
    - Validation: Check for duplicates, max length

### Language & Accessibility Tab Navigation

11. **Interface Language Change**
    - Action: PUT /api/v1/users/{userId}/settings + page reload
    - Feedback: "Language changed. Reloading..." message
    - Reload: Full page reload to apply new language
    - Context: All UI text switches to selected language (i18n)

12. **[View Keyboard Shortcuts] link**
    - Target: Keyboard Shortcuts Modal
    - Type: Modal overlay with shortcut reference table
    - Context: Display all available keyboard shortcuts
    - Close: Remains on Language & Accessibility tab

### Data & Export Tab Navigation

13. **[Request Data Export] button**
    - Action: POST /api/v1/users/{userId}/export-data
    - Feedback: Toast: "Export requested. You'll receive an email when ready."
    - No Navigation: Remains on Data & Export tab
    - Update: Show export status: "Export pending... Estimated: 24 hours"


14. **[Deactivate Account] button**
   - Confirmation: Modal dialog with warning:
     - "Are you sure you want to deactivate your account?"
     - "⚠️ This will unregister you from all {count} upcoming events"
     - "You can reactivate within 60 days. After that, your account will be permanently deleted."
   - Action: POST /api/v1/users/{userId}/deactivate
   - On Confirm: Account deactivated, all event registrations cancelled, user logged out
   - Target: Login page with message: "Account deactivated. You can reactivate by logging in within 60 days."

15. **[Delete My Account] button**
    - Confirmation: Multi-step confirmation modal
      1. "This action cannot be undone. Are you sure?"
      2. "Type DELETE to confirm"
      3. "Enter your password"
    - Action: DELETE /api/v1/users/{userId}
    - On Confirm: Account deleted, user logged out
    - Target: Public landing page with message: "Your account has been permanently deleted."
    - GDPR: All personal data marked for deletion within 30 days

### Error States & Redirects

16. **On Unauthorized (401)**
    - Target: Login page
    - Context: Session expired
    - Message: "Your session has expired. Please log in again."
    - Return: After login, redirect back to Settings page

17. **On Forbidden (403)**
    - Target: Dashboard or error page
    - Message: "You don't have permission to access settings."
    - Cause: User role doesn't allow settings modification

18. **On Network Error**
    - No Navigation: Remains on current settings tab
    - Feedback: Error banner: "Unable to connect. Your changes will be saved when online."
    - Auto-retry: Retry save when connection restored

19. **On Validation Error**
    - No Navigation: Remains on current settings tab
    - Feedback: Inline error messages on invalid fields
    - Focus: Auto-scroll to first error field
    - Example: "Email address is invalid" or "Password must be at least 8 characters"

20. **On Save Failure**
    - No Navigation: Remains on current settings tab
    - Feedback: Error toast: "Failed to save settings. Please try again."
    - Action: [Retry] button to attempt save again
    - Fallback: Show detailed error message if retry fails

### Auto-Save Behavior

21. **On Field Blur (Auto-save)**
    - Action: Debounced PUT /api/v1/users/{userId}/settings (3-second delay)

22. **On Page Unload with Unsaved Changes**
   - Browser Warning: "You have unsaved changes. Are you sure you want to leave?"
   - Options: [Stay] [Leave]
27. **On Page Unload with Unsaved Changes**
    - Browser Warning: "You have unsaved changes. Are you sure you want to leave?"
    - Options: [Stay] [Leave]
    - If PWA: Queue changes for background sync when online

---

## Responsive Design Considerations

### Mobile Layout Changes (<768px)

- **Sidebar Navigation**: Collapses into horizontal tabs at top of screen (scrollable)
- **Tab Labels**: Abbreviated labels on mobile (Account → 👤, Notifications → 🔔, etc.)
- **Form Layout**: Single-column form fields, full width
- **Save Button**: Sticky at bottom of viewport for easy access
- **Company Search**: Larger tap target (48×48px minimum)
- **Toggle Switches**: Larger touch targets with adequate spacing (16px between)
- **Interest Tags**: Stack vertically, full width buttons
- **Storage Progress**: Vertical orientation with label above
- **Action Buttons**: Full-width buttons stacked vertically with 12px spacing

### Tablet Layout Changes (768px - 1024px)

- **Sidebar Navigation**: Remains vertical but narrower (160px)
- **Form Columns**: Two-column layout where appropriate (e.g., First/Last Name side-by-side)
- **Tab Content**: Max-width 700px, centered within content area
- **Interest Tags**: 2-3 tags per row depending on text length
- **Modal Dialogs**: Centered with max-width 600px

### Desktop Layout (>1024px)

- **Sidebar Navigation**: Full-width sidebar (240px) with icon + label
- **Form Layout**: Optimal two-column layout for related fields
- **Hover States**: All interactive elements show hover effects
- **Keyboard Shortcuts**: Display keyboard shortcut hints in tooltips
- **Tooltips**: Rich tooltips with additional information on hover

### Mobile-Specific Interactions

- **Swipe Navigation**: Swipe left/right between settings tabs (mobile only)
- **Native Pickers**: Use native date/time pickers on mobile devices
- **Tap Feedback**: Visual tap feedback (ripple effect) on all buttons
- **Autocomplete**: Native keyboard with autocomplete suggestions
- **File Upload**: Native file picker or camera for photo upload
- **Scroll Restoration**: Remember scroll position when switching tabs

---

## Accessibility Notes

### Focus Management

- **Tab Order**: Logical tab order through all form fields and interactive elements
- **Focus Indicators**: 2px solid outline with high contrast color on all focusable elements
- **Skip Links**: "Skip to main content" link at top for keyboard users
- **Focus Trap**: Modal dialogs trap focus within modal until closed
- **Focus Return**: Focus returns to trigger element when closing modals

### Screen Reader Support

- **ARIA Labels**: All form inputs have descriptive `aria-label` or `aria-labelledby`
- **Section Headings**: Proper heading hierarchy (H2 for tab titles, H3 for sections)
- **State Announcements**: Toggle state changes announced via ARIA live regions
- **Error Messages**: Errors associated with fields via `aria-describedby`
- **Progress Updates**: Save status announced to screen readers (e.g., "Settings saved")
- **Modal Dialogs**: Announced as "Dialog: {title}" with `role="dialog"` and `aria-modal="true"`

### Keyboard Navigation

- **Tab Navigation**: TAB cycles forward through interactive elements
- **Shift+Tab**: Cycles backward through interactive elements
- **Enter/Space**: Activates buttons, toggles checkboxes
- **Arrow Keys**: Navigate between radio button options
- **Escape**: Closes modal dialogs and cancels actions
- **Keyboard Shortcuts**: Optional shortcuts for common actions (e.g., Ctrl+S to save)

### Color Contrast

- **WCAG 2.1 AA**: All text meets minimum 4.5:1 contrast ratio for body text
- **Large Text**: 3:1 minimum for text ≥18pt or ≥14pt bold
- **Interactive Elements**: Sufficient contrast in all states (default, hover, focus, disabled)
- **High Contrast Mode**: Optional high contrast mode for enhanced visibility

### Visual Indicators

- **Non-Color Cues**: Icons and text labels supplement color coding
- **Status Indicators**: Visual icons (✓ ✗ ⚠️) with text alternatives
- **Required Fields**: Marked with asterisk (*) and ARIA `required` attribute
- **Disabled States**: Visually distinct disabled states with appropriate ARIA attributes

### Form Accessibility

- **Labels**: All form fields have associated <label> elements
- **Error Messages**: Clear, descriptive error messages linked to fields
- **Help Text**: Contextual help text with `aria-describedby`
- **Validation**: Real-time validation with screen reader announcements
- **Password Visibility**: Toggle password visibility with keyboard access

---

## State Management

### Local Component State

- `currentTab: string` - Active settings tab (account, notifications, privacy, etc.)
- `formData: SettingsFormData` - Current form values for active tab
- `originalData: SettingsFormData` - Original values for change detection
- `hasUnsavedChanges: boolean` - Whether form has unsaved modifications
- `isSaving: boolean` - Loading state during save operation
- `lastSaved: Date | null` - Timestamp of last successful save
- `validationErrors: Record<string, string>` - Field-level validation errors
- `companySearchQuery: string` - Current company search input
- `companySearchResults: Company[]` - Autocomplete results for company search
- `showPasswordModal: boolean` - Password change modal visibility
- `confirmationDialog: ConfirmationDialogState` - State for confirmation dialogs (deactivate, delete)

### Global State (Zustand Store)

- `auth.user` - Current user information (updated on profile changes)
- `auth.userSettings` - User settings synchronized with backend
- `ui.notifications` - Global notification state (for success/error toasts)
- `ui.unsavedChanges` - Global flag to prevent navigation with unsaved changes

### Server State (React Query)

- **userSettings** - Cached user settings, 10-minute stale time
  - Query key: `['user-settings', userId]`
  - Cache time: 30 minutes
  - Refetch on mount: true
  - Refetch on window focus: true (ensure latest settings)

- **companies** - Cached company list for autocomplete
  - Query key: `['companies', searchQuery]`
  - Cache time: 1 hour
  - Enabled only when user types in company field

- **interests** - Available interest tags
  - Query key: `['interests']`
  - Cache time: 1 day (static reference data)
  - Stale time: 24 hours


### Real-Time Updates

- **Settings Sync**: No WebSocket needed (settings are user-specific, no real-time collaboration)
- **Auto-save**: Debounced save every 3 seconds after field changes
  - Debounce: 3000ms
  - Trigger: Field blur or value change
  - Feedback: "Auto-saved {time} ago" indicator


---

## Form Validation Rules

### Field-Level Validations

- **Email Address**:
  - Required
  - Valid email format (RFC 5322)
  - Max length: 255 characters
  - Unique (check against existing users)

- **First Name**:
  - Required
  - Min length: 2 characters
  - Max length: 50 characters
  - Pattern: Letters, spaces, hyphens only

- **Last Name**:
  - Required
  - Min length: 2 characters
  - Max length: 50 characters
  - Pattern: Letters, spaces, hyphens only

- **Company**:
  - Required
  - Must exist in company database (selected from autocomplete)

- **Job Title**:
  - Optional
  - Max length: 100 characters

- **Password** (when changing):
  - Required
  - Min length: 8 characters
  - Max length: 128 characters
  - Must contain: uppercase, lowercase, number, special character
  - Cannot be same as previous password
  - Confirmation must match

- **Custom Interests**:
  - Max length: 30 characters per interest
  - No maximum limit on number of custom interests (unlimited)
  - No duplicates (case-insensitive)

### Form-Level Validations

- **Profile Completeness**: Warn if critical fields empty (company, name)
- **Notification Consistency**: If newsletter enabled, at least one frequency selected
- **Privacy Settings**: If profile private, hide activity visibility options
- **Language Consistency**: If interface language changed, confirm content language preferences

---

## Edge Cases & Error Handling

- **Empty State (New User)**: Show default settings with welcome message: "Welcome! Configure your preferences to get started."

- **Loading State (Initial Load)**: Display skeleton screens for settings content while fetching data

- **Error State (Settings Load Failed)**: Show error message: "Unable to load settings. Please try again." with [Retry] button

- **Error State (Save Failed)**: Inline error message with specific reason (e.g., "Email already in use") and [Retry] button

- **Unsaved Changes Warning**: Browser native beforeunload warning: "You have unsaved changes. Are you sure you want to leave?"

- **Email Verification Pending**: Show banner: "⚠️ Please verify your email address. [Resend Verification Email]"

- **Company Not Found**: If selected company deleted, show warning: "Your company is no longer in our system. Please select a new company."


- **Export Request Pending**: Show status: "Data export in progress... You'll receive an email when ready (usually within 24 hours)."

- **Export Failed**: Email user with failure notice and support contact

- **Account Deactivation Warning**: Before deactivation, show warning with upcoming event count: "⚠️ This will unregister you from all {count} upcoming events. You can reactivate within 60 days. After that, your account will be permanently deleted."

- **Account Deletion Confirmation**: Multi-step confirmation with explicit consent: "This action cannot be undone. Type DELETE to confirm."

- **Session Timeout**: Auto-save settings before redirecting to login on session expiration

- **Network Error (Offline)**: Show banner: "You're offline. Changes will be saved when connection is restored."

- **Slow Network**: Show loading skeleton immediately, timeout after 15 seconds with retry option

- **Concurrent Edits**: If settings modified elsewhere (e.g., admin update), detect on save and prompt: "Settings have been updated elsewhere. [Keep Mine] [Use Latest]"

- **Password Change Required**: If password expired, force password change before allowing other settings modifications

- **Data Export Expired**: If export link expired (>7 days), show: "Export link expired. [Request New Export]"

---

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-10-02 | 1.0 | Initial wireframe creation for User Settings Screen (Attendee) | Sally (UX Expert) |
| 2025-10-04 | 1.1 | Updated based on 8 stakeholder decisions: added profile picture upload, removed 2FA/PWA/calendar features, unlimited custom tags, 60-day retention, auto-unregister on deactivation | Sally (UX Expert) |

---

## Review Notes

### Stakeholder Feedback
- ✅ Profile picture upload approved for Account settings (v1.1)
- ✅ 2FA deferred to post-MVP (v1.1)
- ✅ Admin-controlled settings not needed for MVP (v1.1)
- ✅ Auto-unregister from events on deactivation confirmed (v1.1)
- ✅ PWA/offline features removed from MVP scope (v1.1)
- ✅ Calendar integration deferred to post-MVP (v1.1)
- ✅ Unlimited custom interest tags approved (v1.1)
- ✅ 60-day retention period confirmed (matching AWS Cognito default) (v1.1)
- Need confirmation on data export timeframe (24 hours vs faster)

### Design Iterations
- Version 1.0: Initial creation based on FR14, NFR1, NFR4 requirements
- Version 1.1: Simplified MVP scope based on stakeholder decisions - added profile picture upload, removed 2FA/PWA/calendar integration, unlimited custom tags, 60-day account retention with auto-unregistration from events

### Open Questions

All open questions have been resolved:

1. ✅ **Profile Picture Upload**: Should we support profile picture upload in Account settings or separate screen?
   - **DECISION: In Account settings** - Profile picture upload integrated directly into Account tab
   - Upload/change photo inline with other profile fields (JPEG/PNG, max 5MB, 200x200px minimum)

2. ✅ **Two-Factor Authentication (2FA)**: Do we need two-factor authentication (2FA) settings?
   - **DECISION: NO** - Not in MVP scope
   - 2FA can be added as future security enhancement
   - Focus on basic authentication for MVP

3. ✅ **Admin-Controlled Settings**: Should there be admin-controlled settings that users cannot modify?
   - **DECISION: NO** - All settings user-controlled in MVP
   - Admin overrides can be added in future if needed
   - MVP focuses on user autonomy

4. ✅ **Event Registrations on Deactivation**: What happens to scheduled event registrations when account is deactivated?
   - **DECISION: User is automatically unregistered** - All upcoming event registrations cancelled
   - Past event attendance history preserved in deactivated state
   - User warned before deactivation: "This will unregister you from all upcoming events"

5. ✅ **Offline Content Downloads**: Should we allow users to download specific content categories (e.g., only presentations) for offline access?
   - **DECISION: NO - No offline feature** - Removed from MVP scope entirely
   - PWA/offline functionality removed
   - All content accessed online only

6. ✅ **Calendar App Integration**: Do we need integration with external calendar apps beyond iCal export?
   - **DECISION: NO** - No external calendar integration
   - Users manage their schedule within BATbern platform only
   - Can be added as future enhancement if requested

7. ✅ **Interest Tags Limit**: Should interest tags be pre-defined only or allow unlimited custom tags?
   - **DECISION: Allow unlimited custom tags**
   - Users can create as many custom interest tags as needed
   - No maximum limit on custom tags (previously 20, now unlimited)
   - Still limited to 30 characters per tag

8. ✅ **Retention Period for Deactivated Accounts**: What's the retention period for deactivated accounts before auto-deletion?
   - **DECISION: 60 days** (same as AWS Cognito default)
   - User can reactivate by logging in within 60 days
   - After 60 days, account and all data permanently deleted
   - User notified at deactivation: "You can reactivate within 60 days. After that, your account will be permanently deleted."
