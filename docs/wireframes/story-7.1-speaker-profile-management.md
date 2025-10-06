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
│  ┌─── TOPICS ───────────────────────────────────────────────────────────────────────┐ │
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
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  [Save Changes] [Cancel] [Preview Public View]                                       │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Photo management**: Upload, crop, and preview professional photo
- **Topic management**: Add and remove speaking topics
- **Social links**: Direct integration with professional networks
- **Public preview**: See profile as attendees will see it
- **Auto-save**: Changes saved automatically

## Functional Requirements Met

- **FR3**: Speaker self-service profile management
- Complete control over public profile appearance
- Topic expertise for organizer matching

## User Interactions

1. **Public Profile**: Edit photo, bio, company info, social links
2. **Topics**: Add and remove speaking topics
3. **Preview**: See public-facing profile before publishing

## Technical Notes

- Component: SpeakerProfile.tsx
- Image upload with crop/resize using react-image-crop
- Real-time profile preview with mock data
- Tag input component for topics
- Form validation with react-hook-form
- Auto-save with debounced updates

---

## API Requirements

### Initial Page Load APIs

When the Speaker Profile Management screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/speakers/{speakerId}?include=profile,events,feedback**
   - **Consolidated API**: Replaces `/profile`, `/topics`, and `/statistics` endpoints
   - Returns: Complete speaker profile with:
     - Profile data (photo URL, bio, company, location, member since, social links)
     - Speaking topics embedded in profile
     - Event history with talk count
     - Feedback summary with average rating
     - Statistics (total talks, attendance metrics, engagement scores)
   - Used for: Populate all profile sections in a single call
   - **Consolidation benefit**: 3 endpoints → 1 endpoint (67% reduction), atomic data load

2. **GET /api/v1/topics/popular**
   - Query params: limit (20)
   - Returns: List of popular speaking topics for suggestions
   - Used for: Suggest topics when adding new speaking topics

---

## Action APIs

### Profile Management

1. **PUT /api/v1/speakers/{speakerId}**
   - **Consolidated API**: Replaces `/profile` PUT endpoint
   - Payload: `{ name, title, company, location, bio, socialLinks: { linkedin, twitter, github, website }, topics: [] }`
   - Response: Updated complete speaker profile, validation errors if any
   - Used for: Update public profile information including topics
   - **Consolidation benefit**: Single update endpoint for all profile data including topics

2. **POST /api/v1/speakers/{speakerId}/profile/photo**
   - Payload: Image file upload (multipart/form-data)
   - Response: Photo URL, thumbnail URL
   - Used for: Upload and update profile photo

3. **DELETE /api/v1/speakers/{speakerId}/profile/photo**
   - Response: Deletion confirmation, reverts to default avatar
   - Used for: Remove profile photo

4. **PATCH /api/v1/speakers/{speakerId}**
   - **Consolidated API**: Uses PATCH for partial updates (auto-save)
   - Payload: Partial profile updates (only changed fields)
   - Response: Auto-save confirmation, timestamp
   - Used for: Auto-save profile changes as user types (debounced)
   - **Consolidation benefit**: Standard PATCH pattern for partial updates

### Topics Management

5. **GET /api/v1/topics/search**
   - Query params: query, limit (10)
   - Returns: Matching topics for autocomplete
   - Used for: Search and suggest topics while typing

### Profile Preview & Publishing

6. **GET /api/v1/speakers/{speakerId}?include=profile,events,feedback**
   - **Consolidated API**: Uses standard GET with includes for preview
   - Returns: Public-facing profile as attendees/organizers will see it
   - Used for: Generate preview of public profile
   - **Consolidation benefit**: Same endpoint as profile view, consistent data representation

7. **POST /api/v1/speakers/{speakerId}/profile/publish**
    - Response: Publication confirmation, profile URL
    - Used for: Publish profile changes (if draft mode is enabled)

8. **GET /api/v1/speakers/{speakerId}?include=profile**
    - **Consolidated API**: Uses standard GET for completeness check
    - Returns: Profile data analyzed for completeness
    - Client calculates: Profile completion percentage, missing fields, recommendations
    - Used for: Show profile completion status and suggestions
    - **Consolidation benefit**: No separate endpoint needed, client-side calculation from standard profile data

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

6. **[+ Add Topic] button** → Opens topic input field
   - Text input with autocomplete
   - Search existing topics
   - Add custom topic option
   - No screen navigation

7. **Topic [Remove] button** → Removes topic
   - Confirmation prompt for custom topics
   - Removes from list
   - Updates profile
   - No screen navigation

8. **[Save Changes] button** → Triggers save
    - Validates all fields
    - Saves profile updates
    - Shows success notification
    - No screen navigation

9. **[Cancel] button** → Reverts unsaved changes
    - Confirmation prompt if changes made
    - Reloads original profile data
    - No screen navigation

10. **[Preview Public View] button** → Navigate to `Public Profile Preview Screen`
    - Full-page public profile view
    - Shows profile as others see it
    - Edit button returns to management screen

### Secondary Navigation (Data Interactions)

11. **Profile photo hover** → Shows edit/remove options
    - Upload new photo
    - Remove current photo
    - No navigation

12. **Bio text field focus** → Shows character count
    - Live character counter
    - Formatting tips
    - No navigation

13. **Social link click (when not editing)** → Opens external link
    - Opens in new tab
    - Validates link works
    - No app navigation

14. **Topic autocomplete suggestion click** → Adds topic
    - Selects suggested topic
    - Adds to topics list
    - Closes autocomplete
    - No screen navigation

### Event-Driven Navigation

15. **Profile saved successfully** → Shows success notification
    - Confirmation message
    - Last saved timestamp
    - No automatic navigation

16. **Profile validation error** → Shows error notifications
    - Highlights invalid fields
    - Provides correction suggestions
    - No automatic navigation

17. **Photo upload complete** → Updates photo display
    - Shows new photo
    - Updates preview
    - No screen navigation

18. **Auto-save triggered** → Shows subtle save indicator
    - Saving... indicator
    - Saved timestamp when complete
    - No screen navigation

19. **Profile completeness milestone** → Shows celebration notification
    - Profile X% complete
    - Suggests next steps
    - No automatic navigation

20. **Profile viewed by organizer** → Shows notification (if enabled)
    - Profile view count
    - Potential opportunity indicator
    - No automatic navigation

21. **Profile incomplete warning** → Shows reminder banner
    - Lists missing fields
    - Links to incomplete sections
    - Dismissible
    - No automatic navigation

---
