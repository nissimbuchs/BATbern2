# Story 5.2: User Settings Screen (Attendee) - Wireframe

**Story**: Epic 5, Story 2 - Personal Dashboard (User Settings)
**Screen**: User Settings Screen
**User Role**: Attendee
**Related FR**: FR14 (Personal Engagement Management), NFR1 (Responsive Design), NFR4 (Multi-language Support)

---

## Visual Wireframe

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Dashboard            Settings                          [Save]      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  ┌─── SETTINGS NAVIGATION ──────┐  ┌─── ACCOUNT SETTINGS ─────────────────┐ │
│  │                               │  │                                       │ │
│  │  ● Account                    │  │  Profile Information                  │ │
│  │  ○ Notifications              │  │                                       │ │
│  │  ○ Privacy                    │  │  Email Address                        │ │
│  │  ○ Content Preferences        │  │  ┌─────────────────────────────────┐ │ │
│  │  ○ Language & Accessibility   │  │  │ john.smith@techcorp.ch          │ │ │
│  │  ○ App Settings (PWA)         │  │  └─────────────────────────────────┘ │ │
│  │  ○ Data & Export              │  │  ✓ Verified                          │ │
│  │                               │  │                                       │ │
│  │                               │  │  First Name                           │ │
│  │                               │  │  ┌─────────────────────────────────┐ │ │
│  │                               │  │  │ John                            │ │ │
│  │                               │  │  └─────────────────────────────────┘ │ │
│  │                               │  │                                       │ │
│  │                               │  │  Last Name                            │ │
│  │                               │  │  ┌─────────────────────────────────┐ │ │
│  │                               │  │  │ Smith                           │ │ │
│  │                               │  │  └─────────────────────────────────┘ │ │
│  │                               │  │                                       │ │
│  │                               │  │  Company                              │ │
│  │                               │  │  ┌─────────────────────────────────┐ │ │
│  │                               │  │  │ TechCorp AG                     │ │ │
│  │                               │  │  └─────────────────────────────────┘ │ │
│  │                               │  │  [Search Companies]                   │ │
│  │                               │  │                                       │ │
│  │                               │  │  Job Title (optional)                 │ │
│  │                               │  │  ┌─────────────────────────────────┐ │ │
│  │                               │  │  │ Senior DevOps Engineer          │ │ │
│  │                               │  │  └─────────────────────────────────┘ │ │
│  │                               │  │                                       │ │
│  │                               │  │  Password                             │ │
│  │                               │  │  [Change Password]                    │ │
│  │                               │  │                                       │ │
│  └───────────────────────────────┘  └───────────────────────────────────────┘ │
│                                                                               │
│                             [Cancel]  [Save Changes]                          │
│                                                                               │
└─────────────────────────────────────────────────────────────────────────────┘

[NOTIFICATIONS TAB]
┌─────────────────────────────────────────────────────────────────────────────┐
│  ┌─── SETTINGS NAVIGATION ──────┐  ┌─── NOTIFICATION PREFERENCES ─────────┐ │
│  │                               │  │                                       │ │
│  │  ○ Account                    │  │  Email Notifications                  │ │
│  │  ● Notifications              │  │                                       │ │
│  │  ○ Privacy                    │  │  Event Updates                        │ │
│  │  ○ Content Preferences        │  │  ☑ Upcoming event reminders          │ │
│  │  ○ Language & Accessibility   │  │     Send reminders:                   │ │
│  │  ○ App Settings (PWA)         │  │     ☑ 7 days before event            │ │
│  │  ○ Data & Export              │  │     ☑ 1 day before event             │ │
│  │                               │  │     ☐ 1 hour before event            │ │
│  │                               │  │                                       │ │
│  │                               │  │  ☑ Schedule changes & updates         │ │
│  │                               │  │  ☑ Speaker announcements              │ │
│  │                               │  │  ☑ Venue or logistics changes         │ │
│  │                               │  │                                       │ │
│  │                               │  │  Content & Recommendations            │ │
│  │                               │  │  ☑ New content matching interests     │ │
│  │                               │  │  ☑ Weekly content digest              │ │
│  │                               │  │  ☐ Trending presentations             │ │
│  │                               │  │                                       │ │
│  │                               │  │  Newsletter                           │ │
│  │                               │  │  ☑ Subscribe to BATbern newsletter    │ │
│  │                               │  │     Frequency: ● Monthly ○ Quarterly  │ │
│  │                               │  │                                       │ │
│  │                               │  │  Push Notifications (Mobile PWA)      │ │
│  │                               │  │  ☑ Enable push notifications          │ │
│  │                               │  │  ☑ Event day reminders                │ │
│  │                               │  │  ☐ Breaking news & announcements      │ │
│  │                               │  │                                       │ │
│  │                               │  │  Email Digest Format                  │ │
│  │                               │  │  ● HTML (Rich formatting)             │ │
│  │                               │  │  ○ Plain Text                         │ │
│  │                               │  │                                       │ │
│  │                               │  │  [Test Email Notifications]           │ │
│  │                               │  │                                       │ │
│  └───────────────────────────────┘  └───────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘

[PRIVACY TAB]
┌─────────────────────────────────────────────────────────────────────────────┐
│  ┌─── SETTINGS NAVIGATION ──────┐  ┌─── PRIVACY CONTROLS ─────────────────┐ │
│  │                               │  │                                       │ │
│  │  ○ Account                    │  │  Profile Visibility                   │ │
│  │  ○ Notifications              │  │                                       │ │
│  │  ● Privacy                    │  │  Who can see your profile?            │ │
│  │  ○ Content Preferences        │  │  ● BATbern community members          │ │
│  │  ○ Language & Accessibility   │  │  ○ Only organizers                    │ │
│  │  ○ App Settings (PWA)         │  │  ○ Private (no one)                   │ │
│  │  ○ Data & Export              │  │                                       │ │
│  │                               │  │  Activity Visibility                  │ │
│  │                               │  │  ☑ Show events I'm attending          │ │
│  │                               │  │  ☑ Show my bookmarked content         │ │
│  │                               │  │  ☐ Show my download history           │ │
│  │                               │  │                                       │ │
│  │                               │  │  Company Information                  │ │
│  │                               │  │  ☑ Display company on profile         │ │
│  │                               │  │  ☑ Allow company analytics            │ │
│  │                               │  │  ☐ Share attendance with employer     │ │
│  │                               │  │                                       │ │
│  │                               │  │  Data Collection                      │ │
│  │                               │  │  ☑ Allow analytics for personalization│ │
│  │                               │  │  ☑ Track content recommendations      │ │
│  │                               │  │  ☐ Share anonymous usage data         │ │
│  │                               │  │                                       │ │
│  │                               │  │  Cookies & Tracking                   │ │
│  │                               │  │  ● Essential + Functional             │ │
│  │                               │  │  ○ Essential only                     │ │
│  │                               │  │                                       │ │
│  │                               │  │  [View Privacy Policy]                │ │
│  │                               │  │                                       │ │
│  └───────────────────────────────┘  └───────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘

[CONTENT PREFERENCES TAB]
┌─────────────────────────────────────────────────────────────────────────────┐
│  ┌─── SETTINGS NAVIGATION ──────┐  ┌─── CONTENT PREFERENCES ──────────────┐ │
│  │                               │  │                                       │ │
│  │  ○ Account                    │  │  Interests & Topics                   │ │
│  │  ○ Notifications              │  │                                       │ │
│  │  ○ Privacy                    │  │  Select your areas of interest:       │ │
│  │  ● Content Preferences        │  │                                       │ │
│  │  ○ Language & Accessibility   │  │  [✓ Kubernetes]  [✓ Cloud Native]    │ │
│  │  ○ App Settings (PWA)         │  │  [✓ DevOps]      [✓ Security]        │ │
│  │  ○ Data & Export              │  │  [  Microservices] [  Databases]     │ │
│  │                               │  │  [  AI/ML]       [  Frontend]        │ │
│  │                               │  │  [  Agile]       [  Leadership]      │ │
│  │                               │  │                                       │ │
│  │                               │  │  + Add custom interest                │ │
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
│  │  ○ App Settings (PWA)         │  │                                       │ │
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

[APP SETTINGS (PWA) TAB]
┌─────────────────────────────────────────────────────────────────────────────┐
│  ┌─── SETTINGS NAVIGATION ──────┐  ┌─── APP SETTINGS (PWA) ───────────────┐ │
│  │                               │  │                                       │ │
│  │  ○ Account                    │  │  Installation                         │ │
│  │  ○ Notifications              │  │                                       │ │
│  │  ○ Privacy                    │  │  ✓ BATbern app installed              │ │
│  │  ○ Content Preferences        │  │  Version: 1.0.2                       │ │
│  │  ○ Language & Accessibility   │  │  Last updated: 2025-09-25             │ │
│  │  ● App Settings (PWA)         │  │                                       │ │
│  │  ○ Data & Export              │  │  [Update Available - Install Now]     │ │
│  │                               │  │                                       │ │
│  │                               │  │  Offline Content                      │ │
│  │                               │  │                                       │ │
│  │                               │  │  ☑ Enable offline mode                │ │
│  │                               │  │  ☑ Auto-download upcoming events      │ │
│  │                               │  │  ☑ Cache recent content               │ │
│  │                               │  │                                       │ │
│  │                               │  │  Storage Usage                        │ │
│  │                               │  │  Content: 245 MB / 500 MB             │ │
│  │                               │  │  ████████░░░░░ 49% used               │ │
│  │                               │  │                                       │ │
│  │                               │  │  [Manage Offline Content]             │ │
│  │                               │  │  [Clear Cache]                        │ │
│  │                               │  │                                       │ │
│  │                               │  │  Data Sync                            │ │
│  │                               │  │  ● Auto-sync when online              │ │
│  │                               │  │  ○ Manual sync only                   │ │
│  │                               │  │  ○ Sync on WiFi only                  │ │
│  │                               │  │                                       │ │
│  │                               │  │  Last synced: 2 minutes ago           │ │
│  │                               │  │  [Sync Now]                           │ │
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
│  │  ○ App Settings (PWA)         │  │  • Bookmarked content                 │ │
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

- **Tab Navigation**: Six distinct settings categories accessible from left sidebar navigation
- **Form Controls**: Text inputs, checkboxes, radio buttons for various preferences
- **Company Search**: Autocomplete search for company affiliation with validation
- **Tag Selection**: Multi-select interest tags with visual feedback
- **Toggle Switches**: Enable/disable notifications, privacy options, and features
- **Storage Management**: Visual progress bar showing offline content usage with cache management
- **Data Export**: Request button triggering asynchronous data export process
- **Danger Zone Actions**: Account deactivation and deletion with confirmation workflows
- **Save/Cancel**: Persistent save bar for unsaved changes with auto-save indication
- **Test Actions**: "Test Email Notifications" to verify notification settings

---

## Functional Requirements Met

- **FR14 (Personal Engagement Management)**: Complete control over content preferences, bookmarks, newsletters, and personal engagement settings
- **NFR1 (Responsive Design)**: Settings layout adapts for mobile, tablet, and desktop with appropriate control sizing
- **NFR4 (Multi-language Support)**: Interface language selection between German and English with date/time format preferences
- **FR15 (Mobile-Optimized Experience)**: PWA-specific settings including offline content, storage management, and sync controls

---

## User Interactions

### Account Tab
1. **Edit Profile**: Update email, name, company, and job title
2. **Company Search**: Type to search and select company from database
3. **Change Password**: Navigate to password change flow with security validation
4. **Save Changes**: Persist profile updates with validation

### Notifications Tab
1. **Toggle Notifications**: Enable/disable specific notification types
2. **Configure Frequency**: Set reminder timing preferences
3. **Newsletter Subscription**: Subscribe/unsubscribe with frequency selection
4. **Push Notifications**: Enable mobile push notifications (PWA only)
5. **Test Notifications**: Send test email to verify notification settings

### Privacy Tab
1. **Profile Visibility**: Control who can view profile and activity
2. **Activity Settings**: Toggle visibility of registrations, bookmarks, downloads
3. **Company Data Sharing**: Control employer analytics and attendance sharing
4. **Analytics Opt-in/Out**: Enable/disable personalization tracking
5. **Cookie Preferences**: Select cookie policy level

### Content Preferences Tab
1. **Select Interests**: Multi-select tags for topic interests
2. **Add Custom Interest**: Create custom interest tags
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

### App Settings (PWA) Tab
1. **Check Updates**: Install available app updates
2. **Offline Content**: Toggle auto-download and caching preferences
3. **Manage Storage**: View and manage offline content storage
4. **Clear Cache**: Remove cached content to free space
5. **Sync Settings**: Configure automatic or manual data synchronization

### Data & Export Tab
1. **Export Data**: Request complete data export in JSON or CSV format
2. **Deactivate Account**: Temporarily disable account with reactivation option
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
- **PWA Detection**: Conditionally show "App Settings" tab only when running as installed PWA
- **Storage API**: Use StorageManager API to display quota and usage for offline content
- **Service Worker**: Cache management via Service Worker for offline content control
- **i18n Integration**: React-i18next for multi-language support with locale switching
- **ARIA Labels**: Complete accessibility support with proper ARIA attributes and keyboard navigation
- **Responsive Layout**: Sidebar navigation collapses to top tabs on mobile (<768px)
- **Theme Support**: Settings respect user's selected theme (light/dark mode)

---

## API Requirements

### Initial Page Load APIs

When the Settings screen loads, the following APIs are called:

1. **GET /api/v1/users/{userId}/settings**
   - Query params: None
   - Returns: Complete user settings object
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
         "pushNotifications": {
           "enabled": "boolean",
           "eventDay": "boolean",
           "breakingNews": "boolean"
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
       "pwaSettings": {
         "offlineMode": "boolean",
         "autoDownload": "boolean",
         "cacheRecent": "boolean",
         "syncMode": "auto | manual | wifi_only",
         "storageUsedMB": "number",
         "storageQuotaMB": "number"
       }
     }
     ```
   - Used for: Populate all settings fields across all tabs with current user preferences

2. **GET /api/v1/reference/companies**
   - Query params: `search` (optional, for autocomplete)
   - Returns: List of companies
     ```json
     [
       {
         "companyId": "uuid",
         "name": "string",
         "industry": "string",
         "partnerStatus": "boolean"
       }
     ]
     ```
   - Used for: Company autocomplete search in Account tab
   - Note: Called on-demand when user types in company field

3. **GET /api/v1/reference/interests**
   - Query params: None
   - Returns: Available interest tags
     ```json
     [
       {
         "interestId": "uuid",
         "name": "string",
         "category": "string",
         "popularity": "number"
       }
     ]
     ```
   - Used for: Display available interest tags in Content Preferences tab

4. **GET /api/v1/users/{userId}/pwa-status**
   - Query params: None
   - Returns: PWA installation status
     ```json
     {
       "installed": "boolean",
       "version": "string",
       "lastUpdated": "ISO-8601 datetime",
       "updateAvailable": "boolean",
       "newVersion": "string | null"
     }
     ```
   - Used for: Display PWA status and update availability in App Settings tab
   - Note: Only called if running as PWA (detected via matchMedia or navigator.standalone)

---

## User Action APIs

APIs called by user interactions and actions:

### Save Settings

1. **PUT /api/v1/users/{userId}/settings**
   - Triggered by: [Save Changes] button or auto-save after field changes
   - Payload: Complete or partial settings object (same structure as GET response)
   - Response: Updated settings with confirmation
     ```json
     {
       "success": "boolean",
       "updatedAt": "ISO-8601 datetime",
       "settings": { /* updated settings object */ }
     }
     ```
   - Used for: Persist settings changes to server
   - Debounced: Auto-save triggered 3 seconds after last change, manual save immediate

2. **PUT /api/v1/users/{userId}/settings/section**
   - Triggered by: Section-specific changes (e.g., only notifications)
   - Payload: Section-specific settings
     ```json
     {
       "section": "notifications | privacy | contentPreferences | language | accessibility | pwa",
       "data": { /* section-specific settings */ }
     }
     ```
   - Response: Updated section with confirmation
   - Used for: Optimize API calls by updating only changed sections
   - Alternative: Can be used instead of full PUT for granular updates

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

5. **POST /api/v1/users/{userId}/test-notifications**
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

6. **POST /api/v1/users/{userId}/push-subscription**
   - Triggered by: Toggle push notifications in Notifications tab
   - Payload: Push subscription object from browser
     ```json
     {
       "endpoint": "string",
       "keys": {
         "p256dh": "string",
         "auth": "string"
       }
     }
     ```
   - Response: Subscription confirmation
   - Used for: Register device for push notifications (PWA)
   - Note: Requires user permission via browser Notification API

7. **DELETE /api/v1/users/{userId}/push-subscription**
   - Triggered by: Disable push notifications
   - Payload: None
   - Response: Unsubscribe confirmation
   - Used for: Remove push notification subscription

### Data Export Actions

8. **POST /api/v1/users/{userId}/export-data**
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

9. **GET /api/v1/users/{userId}/exports/{exportId}**
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

10. **POST /api/v1/users/{userId}/deactivate**
    - Triggered by: [Deactivate Account] button with confirmation dialog
    - Payload: None
    - Response: Deactivation confirmation
      ```json
      {
        "deactivated": "boolean",
        "deactivatedAt": "ISO-8601 datetime",
        "reactivationToken": "string"
      }
      ```
    - Used for: Temporarily disable account, allow future reactivation
    - Side effect: User logged out, data preserved

11. **POST /api/v1/users/{userId}/reactivate**
    - Triggered by: Login attempt with deactivated account
    - Payload:
      ```json
      {
        "reactivationToken": "string"
      }
      ```
    - Response: Reactivation confirmation
    - Used for: Restore previously deactivated account

12. **DELETE /api/v1/users/{userId}**
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
    - Security: Requires password confirmation and explicit consent
    - Side effect: User logged out, all data marked for deletion (GDPR compliant)

### PWA-Specific Actions

13. **POST /api/v1/users/{userId}/pwa/sync**
    - Triggered by: [Sync Now] button in App Settings tab
    - Payload: None
    - Response: Sync status
      ```json
      {
        "synced": "boolean",
        "lastSyncedAt": "ISO-8601 datetime",
        "itemsSynced": "number"
      }
      ```
    - Used for: Manual data synchronization in PWA mode

14. **DELETE /api/v1/users/{userId}/pwa/cache**
    - Triggered by: [Clear Cache] button
    - Payload: None
    - Response: Cache clear confirmation
      ```json
      {
        "cleared": "boolean",
        "spaceFreedb": "number"
      }
      ```
    - Used for: Clear offline content cache to free storage
    - Note: Client-side Service Worker also clears local cache

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

8. **Enable Push Notifications Toggle**
   - Action: Browser permission request → POST /api/v1/users/{userId}/push-subscription
   - No Navigation: Remains on Notifications tab
   - Permission: Browser native permission dialog appears
   - On Granted: Subscribe to push, enable toggle
   - On Denied: Show message: "Push notifications blocked. Please enable in browser settings."

### Privacy Tab Navigation

9. **[View Privacy Policy] link**
   - Target: Privacy Policy page
   - Type: Open in new tab/window
   - Context: External link to legal privacy policy document
   - No Navigation: Settings page remains open in original tab

### Content Preferences Tab Navigation

10. **Interest Tag Click**
    - Action: Toggle interest selection (local state)
    - No Navigation: Remains on Content Preferences tab
    - Feedback: Visual toggle (filled vs outlined)
    - Save: Changes saved when [Save Changes] clicked

11. **+ Add Custom Interest**
    - Action: Show inline text input to create custom interest
    - No Navigation: Inline expansion on Content Preferences tab
    - On Submit: Add custom interest to selection, close input
    - Validation: Check for duplicates, max length

### Language & Accessibility Tab Navigation

12. **Interface Language Change**
    - Action: PUT /api/v1/users/{userId}/settings + page reload
    - Feedback: "Language changed. Reloading..." message
    - Reload: Full page reload to apply new language
    - Context: All UI text switches to selected language (i18n)

13. **[View Keyboard Shortcuts] link**
    - Target: Keyboard Shortcuts Modal
    - Type: Modal overlay with shortcut reference table
    - Context: Display all available keyboard shortcuts
    - Close: Remains on Language & Accessibility tab

### App Settings (PWA) Tab Navigation

14. **[Update Available - Install Now] button**
    - Action: Trigger PWA update via Service Worker
    - Feedback: "Updating app..." progress indicator
    - Reload: App reloads automatically after update
    - Success: Toast: "App updated to version {version}"

15. **[Manage Offline Content] button**
    - Target: Offline Content Management screen
    - Type: New page or expanded section within tab
    - Context: Show list of offline content with delete options
    - Return: Back to App Settings tab

16. **[Clear Cache] button**
    - Action: DELETE /api/v1/users/{userId}/pwa/cache + clear local Service Worker cache
    - Confirmation: "Clear all offline content?" dialog
    - Feedback: Toast: "Cache cleared. {MB} freed."
    - Update: Storage usage bar updated

17. **[Sync Now] button**
    - Action: POST /api/v1/users/{userId}/pwa/sync
    - Feedback: Progress spinner during sync
    - Success: Toast: "Synced successfully. {count} items updated."
    - Update: "Last synced" timestamp updated

### Data & Export Tab Navigation

18. **[Request Data Export] button**
    - Action: POST /api/v1/users/{userId}/export-data
    - Feedback: Toast: "Export requested. You'll receive an email when ready."
    - No Navigation: Remains on Data & Export tab
    - Update: Show export status: "Export pending... Estimated: 24 hours"

19. **[Deactivate Account] button**
    - Confirmation: Modal dialog: "Are you sure you want to deactivate your account?"
    - Action: POST /api/v1/users/{userId}/deactivate
    - On Confirm: Account deactivated, user logged out
    - Target: Login page with message: "Account deactivated. You can reactivate by logging in."

20. **[Delete My Account] button**
    - Confirmation: Multi-step confirmation modal
      1. "This action cannot be undone. Are you sure?"
      2. "Type DELETE to confirm"
      3. "Enter your password"
    - Action: DELETE /api/v1/users/{userId}
    - On Confirm: Account deleted, user logged out
    - Target: Public landing page with message: "Your account has been permanently deleted."
    - GDPR: All personal data marked for deletion within 30 days

### Error States & Redirects

21. **On Unauthorized (401)**
    - Target: Login page
    - Context: Session expired
    - Message: "Your session has expired. Please log in again."
    - Return: After login, redirect back to Settings page

22. **On Forbidden (403)**
    - Target: Dashboard or error page
    - Message: "You don't have permission to access settings."
    - Cause: User role doesn't allow settings modification

23. **On Network Error**
    - No Navigation: Remains on current settings tab
    - Feedback: Error banner: "Unable to connect. Your changes will be saved when online."
    - Auto-retry: Retry save when connection restored (PWA offline support)

24. **On Validation Error**
    - No Navigation: Remains on current settings tab
    - Feedback: Inline error messages on invalid fields
    - Focus: Auto-scroll to first error field
    - Example: "Email address is invalid" or "Password must be at least 8 characters"

25. **On Save Failure**
    - No Navigation: Remains on current settings tab
    - Feedback: Error toast: "Failed to save settings. Please try again."
    - Action: [Retry] button to attempt save again
    - Fallback: Show detailed error message if retry fails

### Auto-Save Behavior

26. **On Field Blur (Auto-save)**
    - Action: Debounced PUT /api/v1/users/{userId}/settings (3-second delay)
    - No Navigation: Remains on current field
    - Feedback: Small checkmark icon appears next to saved field
    - Update: "Auto-saved {time} ago" indicator at top of page

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
- **Pull to Refresh**: Pull down to refresh settings data (PWA)
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

- **pwaStatus** - PWA installation status
  - Query key: `['pwa-status', userId]`
  - Cache time: 5 minutes
  - Enabled only in PWA mode

### Real-Time Updates

- **Settings Sync**: No WebSocket needed (settings are user-specific, no real-time collaboration)
- **Auto-save**: Debounced save every 3 seconds after field changes
  - Debounce: 3000ms
  - Trigger: Field blur or value change
  - Feedback: "Auto-saved {time} ago" indicator

### Offline Support (PWA)

- **IndexedDB**: Store pending settings changes when offline
- **Background Sync**: Sync settings when connection restored
- **Conflict Resolution**: Last write wins (settings are user-specific)
- **Sync Indicator**: Show "Offline - changes will sync when online" banner

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
  - Max total: 20 custom interests
  - No duplicates (case-insensitive)

### Form-Level Validations

- **Profile Completeness**: Warn if critical fields empty (company, name)
- **Notification Consistency**: If newsletter enabled, at least one frequency selected
- **Privacy Settings**: If profile private, hide activity visibility options
- **PWA Storage**: Warn if storage quota exceeded (>90% full)
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

- **PWA Not Installed**: Hide "App Settings" tab when not running as installed PWA

- **Storage Quota Exceeded**: When offline storage >90% full, show warning: "Storage almost full. Clear cache to free space."

- **Push Notification Blocked**: If browser blocks push, show: "Push notifications blocked. Enable in browser settings to receive notifications."

- **Export Request Pending**: Show status: "Data export in progress... You'll receive an email when ready (usually within 24 hours)."

- **Export Failed**: Email user with failure notice and support contact

- **Account Deactivation Conflict**: If user has pending speaker invitations, warn: "You have pending speaker invitations. Deactivating your account will decline them."

- **Account Deletion Confirmation**: Multi-step confirmation with explicit consent: "This action cannot be undone. Type DELETE to confirm."

- **Session Timeout**: Auto-save settings before redirecting to login on session expiration

- **Network Error (Offline)**: Show banner: "You're offline. Changes will be saved when connection is restored." (PWA only)

- **Slow Network**: Show loading skeleton immediately, timeout after 15 seconds with retry option

- **Concurrent Edits**: If settings modified elsewhere (e.g., admin update), detect on save and prompt: "Settings have been updated elsewhere. [Keep Mine] [Use Latest]"

- **Password Change Required**: If password expired, force password change before allowing other settings modifications

- **Data Export Expired**: If export link expired (>7 days), show: "Export link expired. [Request New Export]"

---

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-10-02 | 1.0 | Initial wireframe creation for User Settings Screen (Attendee) | Sally (UX Expert) |

---

## Review Notes

### Stakeholder Feedback
- Awaiting product owner review for PWA settings scope
- Need confirmation on data export timeframe (24 hours vs faster)
- Confirm GDPR compliance for account deletion (30-day grace period)

### Design Iterations
- Version 1.0: Initial creation based on FR14, NFR1, NFR4 requirements

### Open Questions
1. Should we support profile picture upload in Account settings or separate screen?
2. Do we need two-factor authentication (2FA) settings?
3. Should there be admin-controlled settings that users cannot modify?
4. What happens to scheduled event registrations when account is deactivated?
5. Should we allow users to download specific content categories (e.g., only presentations) for offline access?
6. Do we need integration with external calendar apps beyond iCal export?
7. Should interest tags be pre-defined only or allow unlimited custom tags?
8. What's the retention period for deactivated accounts before auto-deletion?
