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

1. **GET /api/v1/speakers/{speakerId}/profile**
   - Returns: Complete speaker profile (photo URL, bio, company, location, member since, talk count, rating, social links)
   - Used for: Populate public profile section with current speaker information

2. **GET /api/v1/speakers/{speakerId}/topics**
   - Returns: Speaking topics list
   - Used for: Populate topics section

3. **GET /api/v1/topics/popular**
   - Query params: limit (20)
   - Returns: List of popular speaking topics for suggestions
   - Used for: Suggest topics when adding new speaking topics

4. **GET /api/v1/speakers/{speakerId}/statistics**
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

### Topics Management

5. **POST /api/v1/speakers/{speakerId}/topics**
   - Payload: `{ topicName, customTopic: boolean }`
   - Response: Topic ID, added confirmation
   - Used for: Add speaking topic to profile

6. **DELETE /api/v1/speakers/{speakerId}/topics/{topicId}**
   - Response: Deletion confirmation
   - Used for: Remove speaking topic from profile

7. **GET /api/v1/topics/search**
   - Query params: query, limit (10)
   - Returns: Matching topics for autocomplete
   - Used for: Search and suggest topics while typing

### Profile Preview & Publishing

8. **GET /api/v1/speakers/{speakerId}/profile/preview**
   - Returns: Public-facing profile as attendees/organizers will see it
   - Used for: Generate preview of public profile

9. **POST /api/v1/speakers/{speakerId}/profile/publish**
    - Response: Publication confirmation, profile URL
    - Used for: Publish profile changes (if draft mode is enabled)

10. **GET /api/v1/speakers/{speakerId}/profile/completeness**
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
