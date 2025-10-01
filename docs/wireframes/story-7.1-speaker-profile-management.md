# Speaker Profile Management - Wireframe

**Story**: Epic 7, Story 7.1
**Screen**: Speaker Profile Management
**User Role**: Speaker
**Related FR**: FR3 (Speaker Self-Service)

---

## Speaker Profile Management

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Dashboard                   My Speaker Profile                   [Preview] │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── PUBLIC PROFILE ──────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  ┌────────┐  Peter Muller                          [Edit]                       │ │
│  │  │ [Photo]│  Principal Cloud Architect @ TechCorp AG                            │ │
│  │  │        │  Zurich, Switzerland                                                │ │
│  │  └────────┘  Member since: March 2020 • 12 talks • 4.7★ avg rating             │ │
│  │                                                                                  │ │
│  │  Bio:                                                                            │ │
│  │  ┌──────────────────────────────────────────────────────────────────────────┐   │ │
│  │  │ Cloud native enthusiast with 10+ years building scalable platforms.       │   │ │
│  │  │ Specializing in Kubernetes, DevOps, and cloud architecture. CNCF          │   │ │
│  │  │ contributor and regular conference speaker across Europe.                 │   │ │
│  │  └──────────────────────────────────────────────────────────────────────────┘   │ │
│  │                                                                                  │ │
│  │  [🔗 LinkedIn] [🐦 Twitter] [💻 GitHub] [🌐 Website]                           │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── EXPERTISE & TOPICS ──────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Core Expertise (select 3-5)                                                    │ │
│  │  ☑ Kubernetes     ☑ Cloud Architecture    ☑ DevOps                             │ │
│  │  ☑ Microservices  ☐ Security             ☐ Data Engineering                    │ │
│  │  ☐ AI/ML          ☐ Blockchain            ☐ IoT                                │ │
│  │                                                                                  │ │
│  │  Topics I Can Speak About                              [+ Add Topic]            │ │
│  │  ┌────────────────────────────────────────────────────────────────────┐        │ │
│  │  │ • Kubernetes at Scale                               [Remove]       │        │ │
│  │  │ • GitOps and CI/CD Best Practices                   [Remove]       │        │ │
│  │  │ • Cloud Cost Optimization                           [Remove]       │        │ │
│  │  │ • Container Security                                [Remove]       │        │ │
│  │  │ • Microservices Architecture                        [Remove]       │        │ │
│  │  └────────────────────────────────────────────────────────────────────┘        │ │
│  │                                                                                  │ │
│  │  Languages                          Presentation Formats                        │ │
│  │  ☑ English (Fluent)                ☑ Keynote (30-45 min)                       │ │
│  │  ☑ German (Native)                 ☑ Technical Deep Dive (45-60 min)           │ │
│  │  ☐ French (Basic)                  ☑ Workshop (Half/Full day)                  │ │
│  │                                     ☐ Panel Discussions                         │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── AVAILABILITY & PREFERENCES ──────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  General Availability                                                           │ │
│  │  ● Available for speaking engagements                                          │ │
│  │  ○ Limited availability (specify below)                                        │ │
│  │  ○ Not currently accepting invitations                                         │ │
│  │                                                                                  │ │
│  │  Blocked Dates (Not available)                          [+ Add Dates]          │ │
│  │  • July 15-30, 2025 (Vacation)                                                 │ │
│  │  • September 10-15, 2025 (KubeCon)                                             │ │
│  │                                                                                  │ │
│  │  Travel Preferences                      Speaking Fees                         │ │
│  │  ☑ Local events (no travel)             ○ Pro bono only                       │ │
│  │  ☑ Switzerland                          ● Negotiable                           │ │
│  │  ☑ Europe                               ○ Standard fee: CHF _______           │ │
│  │  ☐ Worldwide                                                                   │ │
│  │                                                                                  │ │
│  │  ☑ Notify me about relevant speaking opportunities                             │ │
│  │  ☑ Show profile in speaker directory                                           │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  [Save Changes] [Cancel] [Preview Public View]                                       │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Photo management**: Upload, crop, and preview professional photo
- **Expertise tags**: Multi-select with popular topics suggested
- **Availability calendar**: Date picker for blocked dates
- **Travel preferences**: Geographic scope selection
- **Social links**: Direct integration with professional networks
- **Public preview**: See profile as attendees will see it
- **Auto-save**: Changes saved automatically

## Functional Requirements Met

- **FR3**: Speaker self-service profile management
- Complete control over public profile appearance
- Availability management for smart invitation targeting
- Topic expertise for organizer matching
- Travel and fee preferences for efficient coordination

## User Interactions

1. **Public Profile**: Edit photo, bio, company info, social links
2. **Expertise**: Select core skills and speaking topics
3. **Languages**: Specify language proficiency levels
4. **Formats**: Indicate preferred presentation formats
5. **Availability**: Set general availability and block specific dates
6. **Preferences**: Configure travel scope and speaking fees
7. **Notifications**: Opt in/out of opportunity alerts
8. **Preview**: See public-facing profile before publishing

## Technical Notes

- Component: SpeakerProfile.tsx with tabbed interface
- Image upload with crop/resize using react-image-crop
- Date picker integration for availability blocking
- Real-time profile preview with mock data
- Tag input component for topics and expertise
- Form validation with react-hook-form
- Auto-save with debounced updates
- Public/private field visibility controls

---

## API Requirements

### Initial Page Load APIs

When the Speaker Profile Management screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/speakers/{speakerId}/profile**
   - Returns: Complete speaker profile (photo URL, bio, company, location, member since, talk count, rating, social links)
   - Used for: Populate public profile section with current speaker information

2. **GET /api/v1/speakers/{speakerId}/expertise**
   - Returns: Selected core expertise tags, speaking topics list, languages with proficiency, presentation formats
   - Used for: Populate expertise & topics section with selections

3. **GET /api/v1/speakers/{speakerId}/availability**
   - Returns: General availability status, blocked date ranges with reasons, last updated timestamp
   - Used for: Display availability status and blocked dates

4. **GET /api/v1/speakers/{speakerId}/preferences**
   - Returns: Travel preferences, speaking fee settings, notification preferences, directory visibility
   - Used for: Populate preferences section

5. **GET /api/v1/topics/popular**
   - Query params: limit (20)
   - Returns: List of popular speaking topics for suggestions
   - Used for: Suggest topics when adding new speaking topics

6. **GET /api/v1/speakers/{speakerId}/statistics**
   - Returns: Total talks delivered, average rating, attendance metrics, engagement scores
   - Used for: Display speaker statistics in profile header

---

## Action APIs

### Profile Management

1. **PUT /api/v1/speakers/{speakerId}/profile**
   - Payload: `{ name, title, company, location, bio, socialLinks: { linkedin, twitter, github, website } }`
   - Response: Updated profile, validation errors if any
   - Used for: Update public profile information

2. **POST /api/v1/speakers/{speakerId}/profile/photo**
   - Payload: Image file upload (multipart/form-data)
   - Response: Photo URL, thumbnail URL
   - Used for: Upload and update profile photo

3. **DELETE /api/v1/speakers/{speakerId}/profile/photo**
   - Response: Deletion confirmation, reverts to default avatar
   - Used for: Remove profile photo

4. **POST /api/v1/speakers/{speakerId}/profile/auto-save**
   - Payload: Partial profile updates
   - Response: Auto-save confirmation, timestamp
   - Used for: Auto-save profile changes as user types (debounced)

### Expertise & Topics

5. **PUT /api/v1/speakers/{speakerId}/expertise**
   - Payload: `{ coreExpertise: [], languages: [{ language, proficiency: "native|fluent|basic" }], presentationFormats: [] }`
   - Response: Updated expertise configuration
   - Used for: Update core expertise, languages, and presentation formats

6. **POST /api/v1/speakers/{speakerId}/topics**
   - Payload: `{ topicName, customTopic: boolean }`
   - Response: Topic ID, added confirmation
   - Used for: Add speaking topic to profile

7. **DELETE /api/v1/speakers/{speakerId}/topics/{topicId}**
   - Response: Deletion confirmation
   - Used for: Remove speaking topic from profile

8. **GET /api/v1/topics/search**
   - Query params: query, limit (10)
   - Returns: Matching topics for autocomplete
   - Used for: Search and suggest topics while typing

### Availability Management

9. **PUT /api/v1/speakers/{speakerId}/availability/status**
   - Payload: `{ status: "available|limited|unavailable", message }`
   - Response: Updated availability status
   - Used for: Update general availability status

10. **POST /api/v1/speakers/{speakerId}/availability/block**
    - Payload: `{ startDate, endDate, reason }`
    - Response: Blocked date ID, confirmation
    - Used for: Add blocked date range

11. **DELETE /api/v1/speakers/{speakerId}/availability/block/{blockId}**
    - Response: Deletion confirmation
    - Used for: Remove blocked date range

12. **PUT /api/v1/speakers/{speakerId}/availability/block/{blockId}**
    - Payload: `{ startDate, endDate, reason }`
    - Response: Updated block confirmation
    - Used for: Edit existing blocked date range

### Preferences & Settings

13. **PUT /api/v1/speakers/{speakerId}/preferences/travel**
    - Payload: `{ local: boolean, switzerland: boolean, europe: boolean, worldwide: boolean }`
    - Response: Updated travel preferences
    - Used for: Update travel willingness settings

14. **PUT /api/v1/speakers/{speakerId}/preferences/fees**
    - Payload: `{ feeType: "pro-bono|negotiable|standard", standardFee: number, currency: "CHF" }`
    - Response: Updated fee preferences
    - Used for: Update speaking fee settings

15. **PUT /api/v1/speakers/{speakerId}/preferences/notifications**
    - Payload: `{ opportunityAlerts: boolean, directoryVisibility: boolean, eventReminders: boolean }`
    - Response: Updated notification preferences
    - Used for: Update notification and visibility preferences

### Profile Preview & Publishing

16. **GET /api/v1/speakers/{speakerId}/profile/preview**
   - Returns: Public-facing profile as attendees/organizers will see it
   - Used for: Generate preview of public profile

17. **POST /api/v1/speakers/{speakerId}/profile/publish**
    - Response: Publication confirmation, profile URL
    - Used for: Publish profile changes (if draft mode is enabled)

18. **GET /api/v1/speakers/{speakerId}/profile/completeness**
    - Returns: Profile completion percentage, missing fields, recommendations
    - Used for: Show profile completion status and suggestions

---

## Navigation Map

### Primary Navigation Actions

1. **← Back to Dashboard button** → Navigate back to `Speaker Dashboard`
   - Auto-saves any pending changes
   - Returns to main speaker dashboard

2. **[Preview] button** → Opens public profile preview
   - Shows profile as attendees/organizers see it
   - Opens in modal or new tab
   - Can navigate to full preview screen

3. **[Edit] button (Public Profile section)** → Enables inline editing
   - Makes fields editable
   - Shows save/cancel buttons
   - No screen navigation

4. **Photo upload area click** → Opens file picker
   - Select image file
   - Opens crop/resize interface
   - Uploads and updates photo
   - No screen navigation

5. **Social link button ([🔗 LinkedIn], [🐦 Twitter], etc.)** → Opens link input modal
   - Enter/edit social media URL
   - Validate URL format
   - Updates link
   - No screen navigation

6. **Expertise checkbox** → Toggles expertise selection
   - Updates selected expertise
   - Auto-saves change
   - May show limit warning if > 5 selected
   - No screen navigation

7. **[+ Add Topic] button** → Opens topic input field
   - Text input with autocomplete
   - Search existing topics
   - Add custom topic option
   - No screen navigation

8. **Topic [Remove] button** → Removes topic
   - Confirmation prompt for custom topics
   - Removes from list
   - Updates profile
   - No screen navigation

9. **Language checkbox** → Toggles language selection
   - Opens proficiency selector
   - Updates language list
   - No screen navigation

10. **Presentation format checkbox** → Toggles format selection
    - Updates available formats
    - Auto-saves change
    - No screen navigation

11. **Availability radio buttons** → Changes availability status
    - Updates general availability
    - May show/hide additional fields
    - No screen navigation

12. **[+ Add Dates] button (Blocked Dates)** → Opens date picker modal
    - Select date range
    - Enter reason for block
    - Adds to blocked dates list
    - No screen navigation

13. **Blocked date item click** → Opens edit modal
    - Edit date range
    - Edit reason
    - Option to delete
    - No screen navigation

14. **Travel preference checkbox** → Toggles travel scope
    - Updates travel willingness
    - Auto-saves change
    - No screen navigation

15. **Speaking fee radio button** → Changes fee type
    - Shows/hides standard fee input
    - Updates fee preferences
    - No screen navigation

16. **Notification checkbox** → Toggles notification settings
    - Updates preferences
    - Auto-saves change
    - May show explanation tooltip
    - No screen navigation

17. **[Save Changes] button** → Triggers save
    - Validates all fields
    - Saves profile updates
    - Shows success notification
    - No screen navigation

18. **[Cancel] button** → Reverts unsaved changes
    - Confirmation prompt if changes made
    - Reloads original profile data
    - No screen navigation

19. **[Preview Public View] button** → Navigate to `Public Profile Preview Screen`
    - Full-page public profile view
    - Shows profile as others see it
    - Edit button returns to management screen

### Secondary Navigation (Data Interactions)

20. **Profile photo hover** → Shows edit/remove options
    - Upload new photo
    - Remove current photo
    - No navigation

21. **Bio text field focus** → Shows character count
    - Live character counter
    - Formatting tips
    - No navigation

22. **Social link click (when not editing)** → Opens external link
    - Opens in new tab
    - Validates link works
    - No app navigation

23. **Expertise tag click** → Shows related topics
    - Suggests speaking topics
    - Links to expertise resources
    - No screen navigation

24. **Topic autocomplete suggestion click** → Adds topic
    - Selects suggested topic
    - Adds to topics list
    - Closes autocomplete
    - No screen navigation

### Event-Driven Navigation

25. **Profile saved successfully** → Shows success notification
    - Confirmation message
    - Last saved timestamp
    - No automatic navigation

26. **Profile validation error** → Shows error notifications
    - Highlights invalid fields
    - Provides correction suggestions
    - No automatic navigation

27. **Photo upload complete** → Updates photo display
    - Shows new photo
    - Updates preview
    - No screen navigation

28. **Auto-save triggered** → Shows subtle save indicator
    - Saving... indicator
    - Saved timestamp when complete
    - No screen navigation

29. **Profile completeness milestone** → Shows celebration notification
    - Profile X% complete
    - Suggests next steps
    - No automatic navigation

30. **Speaking opportunity matched** → Shows notification
    - New opportunity based on profile
    - Links to opportunity details
    - No automatic navigation

31. **Profile viewed by organizer** → Shows notification (if enabled)
    - Profile view count
    - Potential opportunity indicator
    - No automatic navigation

32. **Blocked date conflict** → Shows warning notification
    - Invitation received for blocked date
    - Option to adjust availability
    - Links to invitation details

33. **Profile incomplete warning** → Shows reminder banner
    - Lists missing fields
    - Links to incomplete sections
    - Dismissible
    - No automatic navigation

---
