# Story 1.20: User Profile Screen - Wireframe

**Story**: Epic 1, Story 20 - User Role Management
**Screen**: User Profile Screen
**User Role**: All (Organizer, Speaker, Partner, Attendee)
**Related FR**: FR1 (Role-based authentication with distinct interfaces)

---

## Visual Wireframe

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ ← Back                    My Profile                         [Settings ⚙️]  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  ┌─── PROFILE HEADER ──────────────────────────────────────────────────────┐ │
│  │                                                                           │ │
│  │   ┌──────────────┐   Dr. Anna Müller                                    │ │
│  │   │              │   Senior Software Architect                           │ │
│  │   │   [👤 Photo] │   TechCorp AG                                         │ │
│  │   │   200×200px  │                                                       │ │
│  │   │              │   📧 anna.mueller@techcorp.ch                         │ │
│  │   └──────────────┘   📞 +41 31 123 4567                                 │ │
│  │                                                                           │ │
│  │   Active Role:  🎯 Organizer  🎤 Speaker  👤 Attendee                   │ │
│  │   Member Since: January 2020                                             │ │
│  │                                                                           │ │
│  └───────────────────────────────────────────────────────────────────────────┘ │
│                                                                               │
│  ┌─── PERSONAL INFORMATION ─────────────────────────────────────────────────┐ │
│  │                                                                           │ │
│  │   Full Name:           Dr. Anna Müller                                   │ │
│  │   Preferred Language:  German                                            │ │
│  │   Time Zone:           Europe/Zurich (CET)                               │ │
│  │   Company:             TechCorp AG  [View Company Profile]               │ │
│  │   Job Title:           Senior Software Architect                         │ │
│  │   Department:          Engineering                                        │ │
│  │                                                                           │ │
│  │   Bio:                                                                    │ │
│  │   ┌───────────────────────────────────────────────────────────────────┐  │ │
│  │   │ Passionate about distributed systems and cloud architecture.       │  │ │
│  │   │ Speaker at multiple tech conferences. Leading digital              │  │ │
│  │   │ transformation initiatives at TechCorp AG.                          │  │ │
│  │   └───────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                           │ │
│  │   Social Links:                                                           │ │
│  │   🔗 LinkedIn: linkedin.com/in/annamueller                               │ │
│  │   🐦 Twitter: @annamueller_tech                                          │ │
│  │   🌐 Website: annamueller.ch                                             │ │
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
│                                          [Edit Profile]  [Go to Settings]     │
│                                                                               │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Interactive Elements

- **Profile Photo**: Click to upload/change profile picture. Drag-and-drop supported. Max 5MB, JPEG/PNG.
- **Role Badges**: Visual indicators showing all active roles for the user (Organizer, Speaker, Partner, Attendee)
- **Company Link**: Click "[View Company Profile]" to navigate to company detail page
- **Social Links**: Clickable links that open in new tab with external link icon
- **Role Tabs**: Switch between role-specific information displays (Organizer/Speaker/Partner/Attendee)
- **Speaker Profile Link**: Opens full speaker public profile in new view
- **Activity Timeline**: Scrollable list of recent user activities with pagination
- **[View All →]**: Expands activity history to full-page view with filters
- **[Edit Profile]**: Opens inline editing mode for profile fields
- **[Settings ⚙️]**: Navigates to User Settings screen (story-5.2-user-settings.md)

---

## Functional Requirements Met

- **FR1**: Role-based authentication with distinct interfaces
  - Displays all active roles for the user with visual badges
  - Shows role-specific information in tabbed interface
  - Adapts content based on user's assigned roles
  - Provides seamless switching between role contexts

---

## User Interactions

### Viewing Profile Information
1. User navigates to profile from main navigation menu
2. System loads user data from API (personal info, roles, activity)
3. Profile displays with default tab (primary role or most recently used role)
4. User can browse personal information, role-specific data, and activity history
5. All role-specific tabs are accessible via tab navigation

### Editing Profile
1. User clicks [Edit Profile] button
2. Form fields become editable (except system-managed fields like email verification status)
3. User modifies fields (name, bio, job title, social links, etc.)
4. Real-time client-side validation provides feedback
5. User clicks [Save Changes] or [Cancel]
6. If saved, API updates profile data and refreshes display
7. Success toast notification confirms save

### Managing Profile Photo
1. User clicks on profile photo or [Change Photo] button
2. File picker opens (or drag-and-drop zone activates)
3. User selects image file
4. Client validates file size (<5MB) and type (JPEG/PNG)
5. Image preview shows cropping interface (200×200px square)
6. User adjusts crop and clicks [Upload]
7. API uploads to S3, updates user profile with image URL
8. Profile refreshes with new photo

### Switching Role Views
1. User clicks on a role-specific tab (Organizer/Speaker/Partner/Attendee)
2. Tab content area updates to show role-specific information
3. Active tab visually highlighted
4. If role not assigned, tab shows "Not available" message with info about requesting role

### Viewing Activity History
1. Recent 5 activities displayed by default
2. User clicks [View All →] to see full history
3. Navigates to full-page activity timeline
4. Activity can be filtered by type, date range, or role
5. Each activity item can be clicked to view related content

---

## Technical Notes

- **Profile Photo Storage**: S3 bucket with CloudFront CDN for optimized delivery
- **Image Processing**: Lambda function for resizing/cropping to 200×200px, generating thumbnail
- **Role Data Source**: AWS Cognito custom attributes + PostgreSQL user_roles table
- **Activity Timeline**: EventSourcing pattern - query from event store for audit trail
- **Real-time Updates**: WebSocket connection for activity feed updates when user is active
- **Caching Strategy**: Profile data cached in Redis for 5 minutes, invalidated on update
- **Responsive Design**: Two-column layout collapses to single column on mobile (<768px)
- **Lazy Loading**: Activity history loaded on scroll (infinite scroll pattern)

---

## API Requirements

### Initial Page Load APIs

**Updated with Story 1.23 Consolidated User APIs**

1. **GET /api/v1/users/me?include=profile,roles,activity,companies**
   - Auth: Required (JWT token)
   - Query params: `include` parameter for related data
   - Returns: Complete user object with all related data
     ```json
     {
       "userId": "uuid",
       "email": "string",
       "firstName": "string",
       "lastName": "string",
       "fullName": "string",
       "profilePhoto": "s3-url",
       "company": { "id": "uuid", "name": "string" },
       "jobTitle": "string",
       "department": "string",
       "phoneNumber": "string",
       "bio": "string",
       "preferredLanguage": "de|en",
       "timeZone": "string",
       "socialLinks": {
         "linkedin": "url",
         "twitter": "url",
         "website": "url"
       },
       "memberSince": "ISO8601-date",
       "roles": ["ORGANIZER", "SPEAKER", "ATTENDEE", "PARTNER"],
       "primaryRole": "SPEAKER",
       "roleDetails": {
         "SPEAKER": {
           "expertiseAreas": ["Cloud Architecture", "DevOps"],
           "presentationCount": 12,
           "averageRating": 4.8,
           "totalReviews": 156,
           "lastPresentation": { "title": "...", "date": "..." }
         }
       },
       "recentActivity": [ /* last 5 activities */ ]
     }
     ```
   - Used for: Populating all profile sections (profile, roles, activity)
   - **Consolidated**: Single endpoint replaces /users/{id}/profile, /users/{id}/roles, /users/{id}/activity (3 → 1)
   - **Performance**: <150ms (P95) with caching

2. **GET /api/v1/users/{userId}/activity?timeframe={}&limit=50** (Optional for full history)
   - Auth: Required (JWT token)
   - Query params: `timeframe` (7d, 30d, 90d, all), `limit`, `page`
   - Returns: Paginated activity history
   - Used for: Full activity timeline when clicking "View All"
   - **Consolidated**: Part of Story 1.23 user activity tracking

### User Action APIs

**Updated with Story 1.23 Consolidated APIs**

1. **PATCH /api/v1/users/me**
   - Triggered by: [Save Changes] button after editing profile
   - Auth: Required (JWT token)
   - Payload: Partial update with only changed fields
     ```json
     {
       "firstName": "string",
       "lastName": "string",
       "jobTitle": "string",
       "bio": "string",
       "socialLinks": { "linkedin": "url" }
     }
     ```
   - Response: Updated user object with modified fields
   - Used for: Saving profile changes and refreshing display
   - **Consolidated**: Single PATCH endpoint replaces PUT /users/{id}/profile (supports partial updates)
   - **Performance**: <150ms (P95)

2. **POST /api/v1/users/{userId}/profile-photo** (Unchanged)
   - Triggered by: [Upload] button after selecting/cropping photo
   - Auth: Required (JWT token)
   - Content-Type: multipart/form-data
   - Payload: Form data with `photo` file field
   - Response:
     ```json
     {
       "profilePhoto": "s3-url-to-uploaded-photo",
       "thumbnailUrl": "s3-url-to-thumbnail"
     }
     ```
   - Used for: Uploading profile photo and updating display
   - **Note**: Photo upload remains separate endpoint for file handling

3. **DELETE /api/v1/users/{userId}/profile-photo** (Unchanged)
   - Triggered by: [Remove Photo] button
   - Auth: Required (JWT token)
   - Response: 204 No Content
   - Used for: Removing profile photo (reverts to default avatar)

---

## Navigation Map

### Primary Navigation Actions

1. **← Back button** → Navigate to previous screen (role-specific dashboard)
   - Target: Depends on context - Dashboard for current role
   - Context: User's current role and previous location

2. **[Settings ⚙️] button** → Navigate to User Settings Screen
   - Target: `story-5.2-user-settings.md` (User Settings Screen)
   - Type: Full page navigation
   - Context: Current user ID passed

3. **Main Navigation Menu** → Access via global navigation
   - Available from all authenticated screens
   - Shows "My Profile" link in user dropdown menu

### Secondary Navigation (Data Interactions)

1. **[View Company Profile] link** → Navigate to Company Detail Screen
   - Target: Company Management Screen (Story 1.14)
   - Type: Full page navigation
   - Context: Company ID passed

2. **Role-specific tabs** → Switch displayed content (client-side, no navigation)
   - Tabs: Organizer, Speaker, Partner, Attendee
   - Type: In-page content switch
   - Shows only tabs for roles assigned to user

3. **[View Full Speaker Profile] link** → Navigate to Public Speaker Profile
   - Target: `story-7.1-speaker-profile-detail-view.md` (public view)
   - Type: Full page navigation or modal overlay
   - Context: Speaker ID (current user)

4. **[Manage Presentations] link** → Navigate to Speaker Dashboard
   - Target: Speaker Dashboard / Material Submission
   - Type: Full page navigation
   - Context: Speaker role context

5. **[View All →] activity link** → Navigate to full Activity History page
   - Target: Activity History Screen (dedicated full-page view)
   - Type: Full page navigation
   - Context: User ID, initial filters

6. **Activity item click** → Navigate to related entity
   - Target: Depends on activity type (Event, Presentation, etc.)
   - Type: Context-dependent navigation
   - Context: Related entity type and ID

7. **Social media links** → Open external URLs
   - Target: External websites (LinkedIn, Twitter, personal website)
   - Type: New tab/window (target="_blank")
   - Security: rel="noopener noreferrer"

### Event-Driven Navigation

1. **After successful profile update** → Refresh current screen
   - Shows success toast notification
   - Updates displayed data without full page reload
   - Updates global user state (Zustand store)

2. **After photo upload** → Refresh profile photo display
   - Updates profile photo in header
   - Updates global navigation avatar
   - Invalidates cached profile data

### Error States & Redirects

1. **Unauthorized access (401)** → Redirect to login
   - Target: Login screen (story-1.2-login-screen.md)
   - Clear authentication state
   - Set return URL to current profile page

2. **User not found (404)** → Show error message
   - Display: "Profile not found" error state
   - Action: [Return to Dashboard] button

3. **Failed profile update** → Show error message inline
   - Display validation errors next to relevant fields
   - Keep user on current screen
   - Allow retry with corrected data

4. **Failed photo upload** → Show error toast
   - Display error message (file too large, invalid format, etc.)
   - Keep photo picker open
   - Allow user to select different file

---

## Responsive Design Considerations

### Mobile Layout Changes

- **Single Column Layout**: Profile header, personal info, role tabs, and activity stack vertically
- **Compact Profile Header**: Photo appears above name/title (vertical layout)
- **Collapsible Sections**: Personal info and activity history collapsible accordions
- **Tab Navigation**: Horizontal scrollable tabs for role selection
- **Sticky Header**: Profile name/photo sticky at top when scrolling
- **Bottom Action Bar**: [Edit Profile] and [Settings] buttons fixed at bottom

### Tablet Layout Changes

- **Two Column Layout Maintained**: Left sidebar (photo, contact) + right main content
- **Responsive Tabs**: Tabs remain horizontal but with adjusted spacing
- **Touch Targets**: All buttons minimum 44×44px for touch accessibility

### Mobile-Specific Interactions

- **Swipe Navigation**: Swipe between role tabs on mobile
- **Pull to Refresh**: Pull down to refresh activity timeline
- **Photo Upload**: Camera option available on mobile devices
- **Click-to-Call**: Phone number becomes clickable tel: link on mobile
- **Native Sharing**: Share profile button uses native share sheet on mobile

---

## Accessibility Notes

- **ARIA Labels**: All interactive elements have clear aria-labels
  - Profile photo: "Profile picture, click to upload"
  - Role badges: "Active roles: Organizer, Speaker, Attendee"
  - Social links: "LinkedIn profile (opens in new tab)"
- **Keyboard Navigation**: Full keyboard support with logical tab order
  - Tab through all interactive elements
  - Enter/Space to activate buttons
  - Arrow keys to navigate role tabs
- **Screen Reader Support**:
  - Activity timeline announced with time and type
  - Role changes announced when switching tabs
  - Success/error messages announced via aria-live regions
- **Color Contrast**: WCAG 2.1 AA compliant (4.5:1 minimum contrast ratio)
- **Focus Indicators**: Clear visible focus indicators on all interactive elements (2px solid outline)
- **Alternative Text**: Profile photo has alt text with user's full name
- **Form Labels**: All form fields properly labeled (visible labels + aria-label fallback)

---

## State Management

### Local Component State

- `editMode: boolean` - Whether profile is in edit mode
- `activeRoleTab: UserRole` - Currently displayed role tab
- `photoUploadDialogOpen: boolean` - Photo upload dialog visibility
- `formData: ProfileFormData` - Temporary form data during editing
- `formErrors: Record<string, string>` - Validation errors

### Global State (Zustand Store)

- `auth.user: User` - Current user profile data (synced after updates)
- `auth.currentRole: UserRole` - Active role context
- `auth.availableRoles: UserRole[]` - All roles assigned to user
- `ui.notifications: Notification[]` - System notifications

### Server State (React Query)

- Query Key: `['user-profile', userId]`
  - Data: Full user profile
  - Stale Time: 5 minutes
  - Cache Time: 30 minutes
  - Refetch on window focus: true

- Query Key: `['user-roles', userId]`
  - Data: User roles and role-specific details
  - Stale Time: 10 minutes
  - Cache Time: 1 hour

- Query Key: `['user-activity', userId, { limit, offset, type }]`
  - Data: Paginated activity timeline
  - Stale Time: 1 minute
  - Cache Time: 10 minutes
  - Infinite query pattern for pagination

### Real-Time Updates

- **WebSocket Event**: `user.profile.updated`
  - Triggered when user updates profile from another device/session
  - Invalidates React Query cache
  - Shows toast: "Profile updated on another device. Refreshing..."
  - Reloads profile data

- **WebSocket Event**: `user.activity.new`
  - Triggered when new activity recorded for user
  - Prepends new activity to timeline (max 5 items)
  - Shows subtle animation for new item

---

## Form Validation Rules

### Field-Level Validations

- **First Name**: Required, min 2 chars, max 50 chars, letters and spaces only
- **Last Name**: Required, min 2 chars, max 50 chars, letters and spaces only
- **Job Title**: Optional, max 100 chars
- **Department**: Optional, max 100 chars
- **Phone Number**: Optional, E.164 format validation (international format)
- **Bio**: Optional, max 500 chars, show character count
- **LinkedIn URL**: Optional, must be valid LinkedIn profile URL pattern
- **Twitter URL**: Optional, must be valid Twitter/X handle or profile URL
- **Website URL**: Optional, must be valid URL (http/https)

### Form-Level Validations

- **Unique Email**: Email uniqueness checked server-side (pre-populated, read-only in this view)
- **Social Link Accessibility**: Validate that provided URLs are publicly accessible (optional check)
- **Company Association**: If company selected, must be valid company ID from database

---

## Edge Cases & Error Handling

- **Empty State - No Activity**: Show "No recent activity yet" with friendly icon and message
- **Empty State - No Roles Assigned**: Show "No roles assigned. Contact administrator to request access."
- **Loading State - Profile**: Display skeleton loaders for profile sections during data fetch
- **Loading State - Photo Upload**: Show progress spinner and percentage during upload
- **Error State - Profile Load Failed**: Show error message with [Retry] button
- **Error State - Photo Upload Failed**: Show inline error message with specific reason (file size, format, network)
- **Error State - Invalid Photo Format**: Client-side validation before upload, show error toast
- **Permission Denied - Edit Profile**: If user tries to edit another user's profile (should not be accessible)
- **Network Offline**: Show offline indicator, cache data for offline viewing (PWA capability)
- **Slow Connection**: Progressive loading - show profile header first, then details, then activity
- **Multiple Concurrent Edits**: Detect if profile was modified during edit session, warn user before overwriting
- **Session Expired During Edit**: Detect 401 response, prompt to re-authenticate without losing form data

---

## Change Log

| Date       | Version | Description                                      | Author     |
|------------|---------|--------------------------------------------------|------------|
| 2025-10-04 | 1.0     | Initial wireframe creation for user profile screen | Sally (UX Expert) |

---

## Review Notes

### Stakeholder Feedback
- Awaiting initial review from product owner and dev team

### Design Iterations
- v1.0: Initial design based on Story 1.20 requirements and existing user-settings wireframe patterns

### Open Questions
- **Q1**: Should organizers see additional management capabilities in their profile (e.g., team members they manage)?
- **Q2**: Should we display email verification status and allow re-verification from profile screen?
- **Q3**: Should activity history be filterable by date range and activity type?
- **Q4**: Do we need a "Public Profile Preview" mode to show how others see your profile?
