# Story 2.6: User Account Management (Basic Profile + Settings) - Wireframe

**Story**: Epic 2, Story 6 - User Account Management Frontend (Basic Features)
**Screen**: My Account (Profile + Basic Settings)
**User Role**: All (Organizer, Speaker, Partner, Attendee)
**Related FR**: FR1 (Role-based authentication), FR22 (User role management), FR14 (Personal engagement - basic)

---

## Epic 2 Context

This story implements the **foundational user account management** features that are essential for all users regardless of role. It consolidates basic profile viewing/editing from story-1.20 with essential settings from story-5.2.

**Scope**: Basic profile + essential settings only
**Advanced Features**: Content preferences, quiet hours, GDPR data export deferred to Epic 7 (Story 5.2)

---

## Visual Wireframe

### Profile View (Default Tab)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ ← Back                    My Account                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  [Tab: 👤 Profile]  [Tab: ⚙️ Settings]                                       │
│  ─────────────────────────────────────────────────────────────────────────  │
│                                                                               │
│  ┌─── PROFILE HEADER ──────────────────────────────────────────────────────┐ │
│  │                                                                           │ │
│  │   ┌──────────────┐   Anna Müller                                        │ │
│  │   │              │   TechCorp AG                                         │ │
│  │   │   [👤 Photo] │                                                       │ │
│  │   │   200×200px  │   📧 anna.mueller@techcorp.ch                         │ │
│  │   │              │                                                       │ │
│  │   └──────────────┘   Active Roles:  🎯 Organizer  🎤 Speaker  👤        │ │
│  │                      Member Since: January 2020                         │ │
│  │                                                                           │ │
│  │   [Upload New Photo] [Remove Photo] [Edit Profile]                      │ │
│  │                                                                           │ │
│  └───────────────────────────────────────────────────────────────────────────┘ │
│                                                                               │
│  ┌─── PERSONAL INFORMATION ─────────────────────────────────────────────────┐ │
│  │                                                                           │ │
│  │   Full Name:           Anna Müller                                       │ │
│  │   Email:               anna.mueller@techcorp.ch  ✓ Verified (Cognito)   │ │
│  │   Company:             TechCorp AG  [View Company Profile]               │ │
│  │                                                                           │ │
│  │   Bio: (max 2000 characters)                                             │ │
│  │   ┌───────────────────────────────────────────────────────────────────┐  │ │
│  │   │ Passionate about distributed systems and cloud architecture.       │  │ │
│  │   │ Speaker at multiple tech conferences. Leading digital              │  │ │
│  │   │ transformation initiatives at TechCorp AG.                          │  │ │
│  │   │                                                                     │  │ │
│  │   │ [... up to 2000 characters of biography text ...]                  │  │ │
│  │   └───────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                           │ │
│  └───────────────────────────────────────────────────────────────────────────┘ │
│                                                                               │
│  ┌─── ROLE-SPECIFIC INFORMATION ────────────────────────────────────────────┐ │
│  │                                                                           │ │
│  │   [Tab: 🎯 Organizer]  [Tab: 🎤 Speaker]  [Tab: 👤 Attendee]            │ │
│  │   ─────────────────────────────────────────────────────────────────────  │ │
│  │                                                                           │ │
│  │   Speaker Profile (Active)                                               │ │
│  │                                                                           │ │
│  │   Expertise Areas:                                                        │ │
│  │   • Cloud Architecture  • Distributed Systems  • DevOps  • Kubernetes    │ │
│  │                                                                           │ │
│  │   Speaking Experience:  12 presentations at BATbern events               │ │
│  │   Average Rating:       ★★★★★ 4.8/5.0 (156 attendee reviews)            │ │
│  │   Last Presentation:    "Microservices at Scale" - May 2024              │ │
│  │                                                                           │ │
│  │   Upcoming Speaking Engagements:                                          │ │
│  │   • BATbern Fall 2025 - "Event-Driven Architecture"  [View Details]      │ │
│  │                                                                           │ │
│  │   [View Full Speaker Profile]  [Manage Presentations]                    │ │
│  │                                                                           │ │
│  └───────────────────────────────────────────────────────────────────────────┘ │
│                                                                               │
│  ┌─── ACTIVITY HISTORY ──────────────────────────────────────────────────────┐ │
│  │                                                                           │ │
│  │   Recent Activity                                          [View All →]   │ │
│  │   ─────────────────────────────────────────────────────────────────────  │ │
│  │                                                                           │ │
│  │   ✓  Submitted presentation materials for Fall 2025 event                │ │
│  │      2 days ago                                                           │ │
│  │                                                                           │ │
│  │   📅 Registered for BATbern Fall 2025                                    │ │
│  │      1 week ago                                                           │ │
│  │                                                                           │ │
│  │   ⭐ Received 5-star rating for "Microservices at Scale"                 │ │
│  │      2 weeks ago                                                          │ │
│  │                                                                           │ │
│  │   📝 Updated speaker profile and bio                                     │ │
│  │      3 weeks ago                                                          │ │
│  │                                                                           │ │
│  │   🎤 Accepted speaking invitation for Fall 2025                          │ │
│  │      1 month ago                                                          │ │
│  │                                                                           │ │
│  └───────────────────────────────────────────────────────────────────────────┘ │
│                                                                               │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Settings Tab (Basic Configuration)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ ← Back                    My Account                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  [Tab: 👤 Profile]  [Tab: ⚙️ Settings]                                       │
│  ─────────────────────────────────────────────────────────────────────────  │
│                                                                               │
│  ┌─── SETTINGS NAVIGATION ──────┐  ┌─── ACCOUNT SETTINGS ─────────────────┐ │
│  │                               │  │                                       │ │
│  │  ● Account                    │  │  Account Information                  │ │
│  │  ○ Notifications              │  │                                       │ │
│  │  ○ Privacy                    │  │  Email Address                        │ │
│  │                               │  │  ┌─────────────────────────────────┐ │ │
│  │  ─────────────────────────    │  │  │ anna.mueller@techcorp.ch        │ │ │
│  │  Advanced Settings (Epic 7):  │  │  └─────────────────────────────────┘ │ │
│  │  • Content Preferences        │  │  ✓ Verified (managed by Cognito)    │ │
│  │  • Language & Accessibility   │  │                                       │ │
│  │  • Data & Export              │  │  Password                             │ │
│  │                               │  │  [Change Password]                    │ │
│  │                               │  │                                       │ │
│  │                               │  │  Appearance                           │ │
│  │                               │  │  Theme: ● Light  ○ Dark  ○ Auto      │ │
│  │                               │  │                                       │ │
│  │                               │  │  Regional Settings                    │ │
│  │                               │  │  Timezone:                            │ │
│  │                               │  │  ┌─────────────────────────────────┐ │ │
│  │                               │  │  │ Europe/Zurich (CET) ▼           │ │ │
│  │                               │  │  └─────────────────────────────────┘ │ │
│  │                               │  │  (Autocomplete search)                │ │
│  │                               │  │                                       │ │
│  └───────────────────────────────┘  └───────────────────────────────────────┘ │
│                                                                               │
│  ┌─── NOTIFICATIONS ─────────────┐  ┌─── NOTIFICATION SETTINGS ────────────┐ │
│  │  (When "Notifications" tab     │  │                                       │ │
│  │   is selected)                 │  │  Notification Channels                │ │
│  │                               │  │                                       │ │
│  │                               │  │  ☑ Email notifications                │ │
│  │                               │  │  ☑ In-app notifications               │ │
│  │                               │  │  ☑ Push notifications                 │ │
│  │                               │  │                                       │ │
│  │                               │  │  Notification Frequency               │ │
│  │                               │  │  ● Immediate                          │ │
│  │                               │  │  ○ Daily digest                       │ │
│  │                               │  │  ○ Weekly digest                      │ │
│  │                               │  │                                       │ │
│  │                               │  │  ℹ️ Advanced notification settings    │ │
│  │                               │  │  (quiet hours, granular controls)     │ │
│  │                               │  │  available in Advanced Settings       │ │
│  │                               │  │  (Epic 7)                             │ │
│  │                               │  │                                       │ │
│  └───────────────────────────────┘  └───────────────────────────────────────┘ │
│                                                                               │
│  ┌─── PRIVACY ───────────────────┐  ┌─── PRIVACY CONTROLS ─────────────────┐ │
│  │  (When "Privacy" tab is        │  │                                       │ │
│  │   selected)                    │  │  Profile Visibility                   │ │
│  │                               │  │                                       │ │
│  │                               │  │  Who can see your profile?            │ │
│  │                               │  │  ● Public                             │ │
│  │                               │  │  ○ Members only                       │ │
│  │                               │  │  ○ Private                            │ │
│  │                               │  │                                       │ │
│  │                               │  │  Profile Information Display          │ │
│  │                               │  │  ☑ Show email address                 │ │
│  │                               │  │  ☑ Show company on profile            │ │
│  │                               │  │  ☑ Show activity history              │ │
│  │                               │  │                                       │ │
│  │                               │  │  Communication                        │ │
│  │                               │  │  ☑ Allow messaging from other users   │ │
│  │                               │  │                                       │ │
│  │                               │  │  [View Privacy Policy]                │ │
│  │                               │  │                                       │ │
│  └───────────────────────────────┘  └───────────────────────────────────────┘ │
│                                                                               │
│                             [Cancel]  [Save Changes]                          │
│                                                                               │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Scope Definition

### ✅ Included in Story 2.6 (Epic 2 - Foundational)

**Profile Tab:**
- View user profile with personal information (name, email, company, bio)
- Edit basic profile fields (firstName, lastName, bio - max 2000 chars)
- Upload/change/remove profile picture (ADR-002 Generic File Upload)
- Role-specific information display (tabbed interface)
- Recent activity history (last 5 items)
- View company affiliation

**Settings Tab - Account:**
- Email display (read-only, verified status from Cognito)
- Password change workflow (redirects to Cognito)
- Theme selector (light/dark/auto)
- Timezone selector (autocomplete)

**Settings Tab - Notifications:**
- Notification channel toggles (email, in-app, push)
- Notification frequency (immediate/daily digest/weekly digest)
- Note about advanced features (quiet hours) in Epic 7

**Settings Tab - Privacy:**
- Profile visibility (public/members only/private)
- Profile information display toggles (email, company, activity history)
- Allow messaging toggle

### ❌ Deferred to Story 5.2 (Epic 7 - Advanced Attendee Features)

**Advanced Content Preferences:**
- Interest tags and topic selection
- Content language preferences
- Experience level filtering
- Content format preferences
- Default view mode

**Advanced Notification Settings:**
- Quiet hours configuration
- Granular notification channels (email/in-app/push)
- Per-event notification customization
- Content recommendation notifications

**Language & Accessibility:**
- UI language selection
- Date/time format preferences
- Accessibility options (high contrast, larger text, screen reader optimizations)

**Data & Export:**
- GDPR data export request
- Account deactivation (60-day retention)
- Account deletion
- Data download history

---

## Key Interactive Elements

### Profile Tab
- **Profile Photo**: Click to upload/change profile picture. Drag-and-drop supported. Max 5MB, JPEG/PNG.
  - **[Upload New Photo]**: Opens file picker, shows crop interface, uses ADR-002 3-phase upload
  - **[Remove Photo]**: Deletes profile picture, reverts to default avatar
- **Role Badges**: Visual indicators showing all active roles (Organizer, Speaker, Partner, Attendee)
- **Company Link**: Click "[View Company Profile]" to navigate to company detail page (Story 2.5.1)
- **Role Tabs**: Switch between role-specific information displays (Organizer/Speaker/Partner/Attendee)
- **Activity Timeline**: Scrollable list of recent 5 activities with timestamps
- **[View All →]**: Expands activity history to full-page view with filters
- **[Edit Profile]**: Opens inline editing mode for name and bio fields

### Settings Tab
- **Tab Navigation**: Three sub-tabs (Account, Notifications, Privacy)
- **Account Sub-Tab**:
  - **[Change Password]**: Redirects to Cognito password change flow
  - **Theme Selector**: Radio buttons for light/dark/auto theme
  - **Timezone Selector**: Autocomplete dropdown with IANA timezone database
- **Notifications Sub-Tab**:
  - **Channel Toggles**: Checkboxes for email, in-app, push notifications
  - **Frequency Selector**: Radio buttons for immediate/daily digest/weekly digest
- **Privacy Sub-Tab**:
  - **Visibility Selector**: Radio buttons for public/members only/private
  - **Information Display Toggles**: Checkboxes for email, company, activity history, messaging
- **Save/Cancel**: Persistent save bar at bottom, auto-save indication

---

## Functional Requirements Met

- **FR1**: Role-based authentication with distinct interfaces
  - Displays all active roles for the user with visual badges
  - Shows role-specific information in tabbed interface
  - Adapts content based on user's assigned roles

- **FR22**: User role management
  - Displays current roles
  - Links to role management (organizers only)

- **FR14 (Partial)**: Personal engagement management - basic features only
  - Basic notification preferences
  - Newsletter subscription
  - Profile visibility controls
  - Advanced features deferred to Epic 7

---

## User Interactions

### Viewing Profile Information
1. User navigates to "My Account" from main navigation menu
2. System loads user data from API (personal info, roles, activity)
3. Profile tab displays by default
4. User can browse personal information, role-specific data, and recent activity
5. User can switch to Settings tab for configuration

### Editing Profile
1. User clicks [Edit Profile] button in Profile tab
2. Form fields become editable (firstName, lastName, bio)
3. System-managed fields remain read-only (email from Cognito, company from company affiliation)
4. User modifies editable fields
5. Real-time client-side validation provides feedback (bio max 2000 chars)
6. User clicks [Save Changes] or [Cancel]
7. If saved, API call: `PATCH /api/v1/users/me` with changed fields
8. Success toast notification confirms save, display refreshes

### Managing Profile Photo (ADR-002 Generic File Upload Service)
1. User clicks on profile photo or [Upload New Photo] button
2. File picker opens (or drag-and-drop zone activates)
3. User selects image file
4. Client validates file size (<5MB) and type (JPEG/PNG)
5. Image preview shows cropping interface (200×200px square)
6. User adjusts crop and clicks [Upload]
7. **Three-Phase Upload (ADR-002)**:
   - Phase 1: Call `POST /logos/presigned-url` to initiate upload
   - Phase 2: Upload directly to S3 using presigned URL with progress tracking
   - Phase 3: Call `POST /logos/{uploadId}/confirm` to verify upload
8. **Association**: Call `PATCH /users/me` with `profilePictureFileId`
9. User Service associates logo with user profile and updates `profilePictureUrl`
10. Profile refreshes with new photo from CloudFront CDN

### Configuring Notifications (Settings Tab)
1. User clicks Settings tab
2. User clicks "Notifications" sub-tab
3. User toggles notification channels (email, in-app, push)
4. User selects notification frequency (immediate/daily digest/weekly digest)
5. User clicks [Save Changes]
6. API call: `PUT /api/v1/users/me/preferences` with updated notification settings
7. Success toast confirms save
8. Note displayed: Advanced options (quiet hours, granular controls) in Epic 7

### Configuring Privacy (Settings Tab)
1. User clicks Settings tab
2. User clicks "Privacy" sub-tab
3. User selects profile visibility level (public/members only/private)
4. User toggles profile information display (email, company, activity history)
5. User toggles allow messaging preference
6. User clicks [Save Changes]
7. API call: `PUT /api/v1/users/me/settings` with updated privacy settings
8. Success toast confirms save

---

## API Requirements

### Initial Page Load APIs

**Updated with Story 1.23 Consolidated User APIs**

1. **GET /api/v1/users/me?include=profile,roles,activity,companies**
   - Auth: Required (JWT token)
   - Query params: `include` parameter for related data
   - Returns: Complete user object with all related data
   - Performance: <150ms (P95) with caching
   - Consolidates: Replaces /users/{id}/profile, /users/{id}/roles, /users/{id}/activity (3 → 1)

### User Action APIs

**Updated with Story 1.23 Consolidated APIs**

1. **PATCH /api/v1/users/me**
   - Triggered by: [Save Changes] button after editing profile
   - Auth: Required (JWT token)
   - Payload: Partial update with only changed fields
   - Response: Updated user object
   - Performance: <150ms (P95)

2. **PUT /api/v1/users/me/preferences**
   - Triggered by: [Save Changes] in Notifications tab
   - Auth: Required (JWT token)
   - Payload: User preferences object
   - Response: Updated preferences

3. **PUT /api/v1/users/me/settings**
   - Triggered by: [Save Changes] in Privacy tab
   - Auth: Required (JWT token)
   - Payload: User settings object
   - Response: Updated settings

4. **POST /api/v1/logos/presigned-url** (ADR-002 - Phase 1)
5. **PUT {uploadUrl}** (ADR-002 - Phase 2)
6. **POST /api/v1/logos/{uploadId}/confirm** (ADR-002 - Phase 3)
7. **PATCH /api/v1/users/me** (Associate Logo)
8. **DELETE /api/v1/users/me/picture** (Remove Profile Picture)

See story-1.20-user-profile.md for detailed API specifications.

---

## Navigation Map

### Primary Navigation Actions

1. **← Back button** → Navigate to role-specific dashboard
2. **Profile/Settings Tabs** → Switch between Profile and Settings views (client-side)
3. **Settings Sub-Tabs** → Switch between Account, Notifications, Privacy (client-side)
4. **[View Company Profile]** → Navigate to Company Management Screen (Story 2.5.1)
5. **[View Full Speaker Profile]** → Navigate to Public Speaker Profile (Epic 6)
6. **[Manage Presentations]** → Navigate to Speaker Dashboard (Epic 6)
7. **[View All →]** → Navigate to full Activity History page
8. **Social media links** → Open external URLs (new tab)

### Advanced Features Link

- **Settings Tab Sidebar** shows grayed-out items for advanced features:
  - "Content Preferences" (Coming in Epic 7)
  - "Language & Accessibility" (Coming in Epic 7)
  - "Data & Export" (Coming in Epic 7)
- Users can click these to see "Feature coming soon" message with Epic 7 timeline

---

## Technical Notes

- **Profile Photo Storage (ADR-002)**: Generic File Upload Service with 3-phase pattern
- **Image Processing**: Lambda function for resizing/cropping to 200×200px (future enhancement)
- **Role Data Source**: AWS Cognito custom attributes + PostgreSQL user_roles table
- **Activity Timeline**: EventSourcing pattern - query from event store for audit trail
- **Real-time Updates**: WebSocket connection for activity feed updates when user is active
- **Caching Strategy**: Profile data cached in Caffeine for 5 minutes, invalidated on update
- **Responsive Design**: Two-column layout collapses to single column on mobile (<768px)
- **State Management**:
  - Zustand store for global user state
  - React Query for server state (user profile, preferences, settings)
- **Internationalization (i18n)**:
  - **Library**: i18next + react-i18next
  - **Namespace**: `userAccount`
  - **Supported Languages**: German (de-CH) primary, English (en-US) secondary
  - All UI text must be fully translatable

---

## Acceptance Criteria

### Profile Tab

- [ ] Profile header displays user photo, name, company, email
- [ ] Role badges display all assigned roles with visual indicators (Organizer, Speaker, Partner, Attendee)
- [ ] Personal information section shows: name, email (Cognito-verified), company
- [ ] Bio displays with proper formatting (max 2000 chars with character counter)
- [ ] Role-specific tabs display only for assigned roles
- [ ] Speaker tab shows expertise, speaking history, upcoming engagements
- [ ] Activity history displays last 5 activities with timestamps
- [ ] [View All →] navigates to full activity history page
- [ ] [Edit Profile] button enables inline editing mode for firstName, lastName, bio
- [ ] [Upload New Photo] button opens file picker with drag-and-drop support
- [ ] Profile photo upload shows preview and cropping interface (200×200px)
- [ ] Profile photo upload uses ADR-002 3-phase pattern (presigned URL → S3 → confirm → associate)
- [ ] [Remove Photo] button deletes profile picture, reverts to default avatar
- [ ] [View Company Profile] link navigates to company detail screen (Story 2.5.1)
- [ ] Email displays with ✓ Verified badge from Cognito

### Settings Tab - Account

- [ ] Email address displays with "Verified (managed by Cognito)" status
- [ ] Email is read-only (cannot be changed in this view)
- [ ] [Change Password] button redirects to Cognito password change flow
- [ ] Theme selector displays three options (Light, Dark, Auto) as radio buttons
- [ ] Timezone selector shows autocomplete dropdown with IANA timezone database
- [ ] Timezone selector displays current timezone (e.g., "Europe/Zurich (CET)")
- [ ] All changes show [Save Changes] button, persist via `PUT /users/me/preferences`

### Settings Tab - Notifications

- [ ] Notification channel toggles for email, in-app, push notifications
- [ ] Notification frequency selector (Immediate, Daily digest, Weekly digest) as radio buttons
- [ ] Info message displays: "Advanced notification settings (quiet hours, granular controls) available in Advanced Settings (Epic 7)"
- [ ] Changes persist to UserPreferences via `PUT /api/v1/users/me/preferences`

### Settings Tab - Privacy

- [ ] Profile visibility selector (Public, Members only, Private) as radio buttons
- [ ] Profile information display toggles: Show email, Show company, Show activity history
- [ ] Communication toggle: Allow messaging from other users
- [ ] [View Privacy Policy] link opens privacy policy document
- [ ] Changes persist to UserSettings via `PUT /api/v1/users/me/settings`

### General

- [ ] Tab navigation works smoothly (Profile ↔ Settings)
- [ ] Settings sub-tab navigation works (Account, Notifications, Privacy)
- [ ] Advanced features clearly marked as "Coming in Epic 7" in navigation sidebar
- [ ] All form validation works (client-side: bio 2000 chars max; server-side: all fields)
- [ ] Success/error toast notifications display appropriately
- [ ] Loading states during API calls (skeleton loaders)
- [ ] Responsive design works on mobile, tablet, desktop
- [ ] Full keyboard navigation support (Tab, Enter, Esc)
- [ ] Screen reader compatible (ARIA labels on all interactive elements)
- [ ] All UI text fully translatable (i18n namespace: `userAccount`)
- [ ] Performance: Initial load <150ms, save operations <200ms (P95)

---

## Responsive Design Considerations

### Mobile Layout Changes (<768px)

- **Single Column Layout**: Profile header, info, role tabs, and activity stack vertically
- **Tabs**: Profile/Settings tabs become horizontal scrollable buttons at top
- **Settings Navigation**: Sub-tabs (Account/Notifications/Privacy) become accordion or bottom sheet
- **Compact Profile Header**: Photo appears above name/title (vertical layout)
- **Sticky Header**: User name/photo sticky at top when scrolling
- **Bottom Action Bar**: [Save Changes] button fixed at bottom on mobile

### Tablet Layout (768px - 1024px)

- **Two Column Layout Maintained**: Left sidebar + right main content
- **Responsive Tabs**: Tabs remain horizontal but with adjusted spacing
- **Touch Targets**: All buttons minimum 44×44px for touch accessibility

---

## Accessibility Notes

- **ARIA Labels**: All interactive elements have clear aria-labels
- **Keyboard Navigation**: Full keyboard support with logical tab order
- **Screen Reader Support**: Activity timeline, role changes, success/error messages announced
- **Color Contrast**: WCAG 2.1 AA compliant (4.5:1 minimum contrast ratio)
- **Focus Indicators**: Clear visible focus indicators (2px solid outline)
- **Alternative Text**: Profile photo has alt text with user's full name
- **Form Labels**: All form fields properly labeled

---

## Internationalization (i18n) Requirements

### Translation Namespace
**Namespace**: `userAccount`

**File Paths**:
- `web-frontend/src/i18n/de/userAccount.json` (German - Primary)
- `web-frontend/src/i18n/en/userAccount.json` (English - Secondary)

### Key Translation Categories

**Tabs & Navigation**:
```json
{
  "tabs.profile": "Profil" / "Profile",
  "tabs.settings": "Einstellungen" / "Settings",
  "settings.account": "Konto" / "Account",
  "settings.notifications": "Benachrichtigungen" / "Notifications",
  "settings.privacy": "Datenschutz" / "Privacy",
  "back": "Zurück" / "Back"
}
```

**Profile Section**:
```json
{
  "profile.editProfile": "Profil bearbeiten" / "Edit Profile",
  "profile.uploadPhoto": "Neues Foto hochladen" / "Upload New Photo",
  "profile.memberSince": "Mitglied seit" / "Member Since",
  "profile.bio": "Über mich" / "Bio",
  "profile.recentActivity": "Letzte Aktivitäten" / "Recent Activity",
  "profile.viewAll": "Alle anzeigen" / "View All"
}
```

**Settings Section**:
```json
{
  "settings.changePassword": "Passwort ändern" / "Change Password",
  "settings.saveChanges": "Änderungen speichern" / "Save Changes",
  "settings.cancel": "Abbrechen" / "Cancel",
  "notifications.emailNotifications": "E-Mail-Benachrichtigungen" / "Email Notifications",
  "privacy.profileVisibility": "Profil-Sichtbarkeit" / "Profile Visibility",
  "privacy.viewPolicy": "Datenschutzrichtlinie anzeigen" / "View Privacy Policy"
}
```

**Success/Error Messages**:
```json
{
  "success.profileUpdated": "Profil erfolgreich aktualisiert" / "Profile updated successfully",
  "success.photoUploaded": "Foto erfolgreich hochgeladen" / "Photo uploaded successfully",
  "success.settingsSaved": "Einstellungen gespeichert" / "Settings saved",
  "error.uploadFailed": "Upload fehlgeschlagen" / "Upload failed",
  "error.saveFailed": "Speichern fehlgeschlagen" / "Save failed"
}
```

See story-2.4b-user-management-screen.md for complete i18n implementation patterns.

---

## Edge Cases & Error Handling

- **Empty State - No Activity**: Show "No recent activity yet" with friendly message
- **Empty State - No Roles Assigned**: Show "No roles assigned. Contact administrator."
- **Loading State - Profile**: Display skeleton loaders during data fetch
- **Loading State - Photo Upload**: Show progress spinner and percentage
- **Error State - Profile Load Failed**: Show error message with [Retry] button
- **Error State - Photo Upload Failed**: Show inline error with specific reason (file size, format, network)
- **Error State - Invalid Photo Format**: Client-side validation before upload
- **Permission Denied**: If user tries to edit another user's profile (should not be accessible)
- **Network Offline**: Show offline indicator, cache data for offline viewing
- **Slow Connection**: Progressive loading - show profile header first, then details
- **Concurrent Edits**: Detect if profile modified during edit, warn before overwriting
- **Session Expired During Edit**: Detect 401 response, prompt to re-authenticate without losing form data

---

## State Management

### Local Component State
- `activeTab: 'profile' | 'settings'` - Current main tab
- `activeSettingsTab: 'account' | 'notifications' | 'privacy'` - Current settings sub-tab
- `editMode: boolean` - Whether profile is in edit mode
- `activeRoleTab: UserRole` - Currently displayed role tab in profile view
- `photoUploadDialogOpen: boolean` - Photo upload dialog visibility
- `formData: ProfileFormData` - Temporary form data during editing
- `formErrors: Record<string, string>` - Validation errors

### Global State (Zustand Store)
- `auth.user: User` - Current user profile data (synced after updates)
- `auth.currentRole: UserRole` - Active role context
- `auth.availableRoles: UserRole[]` - All roles assigned to user

### Server State (React Query)
- Query Key: `['user-profile', userId]` (Stale: 5min, Cache: 30min)
- Query Key: `['user-preferences', userId]` (Stale: 10min, Cache: 1hr)
- Query Key: `['user-settings', userId]` (Stale: 10min, Cache: 1hr)
- Query Key: `['user-activity', userId, { limit: 5 }]` (Stale: 1min, Cache: 10min)

---

## Performance Targets

- **Initial Page Load**: <150ms (P95) for `GET /users/me?include=profile,roles,activity,companies`
- **Profile Update**: <150ms (P95) for `PATCH /users/me`
- **Settings Update**: <200ms (P95) for `PUT /users/me/preferences` or `/settings`
- **Photo Upload (Phase 1)**: <100ms for presigned URL generation
- **Photo Upload (Phase 2)**: Progress tracking for S3 upload
- **Photo Upload (Phase 3)**: <200ms for confirmation
- **Activity History**: <100ms for last 5 items

---

## Change Log

| Date       | Version | Description                                      | Author     |
|------------|---------|--------------------------------------------------|------------|
| 2025-11-02 | 1.0     | Initial creation consolidating story-1.20 and story-5.2 basic features | Winston (Architect) |

---

## Review Notes

### Consolidation Rationale

This story consolidates **basic** user account management features from:
- **story-1.20-user-profile.md**: Profile viewing and editing
- **story-5.2-user-settings.md**: Basic account, notification, and privacy settings

**Advanced features** (content preferences, language/accessibility, GDPR data export) are intentionally deferred to Epic 7 (Story 5.2) to focus Epic 2 on foundational entity management.

### Design Decisions

1. **Two-Tab Structure**: Profile (view-focused) + Settings (config-focused) provides clear separation
2. **Settings Sub-Tabs**: Three basic tabs (Account, Notifications, Privacy) with advanced features clearly marked as "Coming in Epic 7"
3. **Photo Upload Consistency**: Available in both Profile and Settings > Account tabs
4. **API Consolidation**: Uses Story 1.23 consolidated user APIs to minimize HTTP requests
5. **Responsive First**: Mobile, tablet, desktop layouts all considered

### Open Questions

- **Q1**: Should we show "Advanced Settings (Epic 7)" items as disabled/grayed-out or hide them completely?
  - **Decision**: Show as grayed-out with "Coming soon" tooltip to set expectations
- **Q2**: Should password change open a modal or navigate to dedicated page?
  - **Decision**: Modal for better UX (keeps user in context)
- **Q3**: Should we allow users to delete their profile photo or just replace it?
  - **Decision**: Both [Upload New Photo] and [Remove] buttons for flexibility

---

## Related Stories

- **story-1.20-user-profile.md**: Original user profile wireframe (source for Profile tab)
- **story-5.2-user-settings.md**: Original user settings wireframe (source for Settings tab basic features)
- **Story 2.1b**: User Management Service Foundation + API Consolidation (backend implementation)
- **Story 2.4**: User Role Management (role display in profile)
- **Story 2.5.2**: User Management Frontend (admin user management screen)
- **Story 5.2 (Epic 7)**: Advanced user settings (content preferences, language, GDPR, deferred features)
