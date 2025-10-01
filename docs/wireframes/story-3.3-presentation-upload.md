# Story 3.3: Presentation Upload & Management - Wireframe

**Story**: Epic 3, Story 3 - Presentation Upload & Management
**Screen**: Presentation Upload & Management
**User Role**: Speaker
**Related FR**: FR5 (Speaker Management)

---

## 7. Presentation Upload & Management

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back               Presentation Materials - Spring Conference 2025                 │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  Kubernetes Best Practices for Production Environments                               │
│  Due: April 30, 2025 (27 days remaining)                    Status: In Progress      │
│                                                                                       │
│  ┌─── PRESENTATION SLIDES ─────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Main Presentation *                                                            │ │
│  │  ┌──────────────────────────────────────────────────────────────────────┐      │ │
│  │  │                                                                        │      │ │
│  │  │                         📊 Drop PowerPoint/PDF                        │      │ │
│  │  │                             or Browse Files                           │      │ │
│  │  │                                                                        │      │ │
│  │  │                    Accepted: .pptx, .pdf, .key                        │      │ │
│  │  │                    Max size: 50MB                                     │      │ │
│  │  │                                                                        │      │ │
│  │  │                         [Browse Files]                                │      │ │
│  │  └──────────────────────────────────────────────────────────────────────┘      │ │
│  │                                                                                  │ │
│  │  ✓ k8s-best-practices-v2.pptx                                                  │ │
│  │    Uploaded: Mar 28, 14:30 • 28 slides • 12.3MB                                │ │
│  │    [Preview] [Replace] [Delete]                                                 │ │
│  │                                                                                  │ │
│  │  Version History:                                                               │ │
│  │  • v2 - Current (Mar 28)                                                        │ │
│  │  • v1 - Initial draft (Mar 20) [Restore]                                        │ │
│  │                                                                                  │ │
│  │  Backup/Demo Materials (optional)                                               │ │
│  │  ┌──────────────────────────────────────────────────────────────────────┐      │ │
│  │  │ + Add backup slides, demo scripts, or video fallbacks                │      │ │
│  │  └──────────────────────────────────────────────────────────────────────┘      │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── SUPPLEMENTARY MATERIALS ─────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Code Examples / GitHub Repository                                              │ │
│  │  ┌─────────────────────────────────────────────────────────────┐               │ │
│  │  │ https://github.com/pmuller/k8s-production-patterns          │               │ │
│  │  └─────────────────────────────────────────────────────────────┘               │ │
│  │                                                                                  │ │
│  │  Additional Resources for Attendees                                             │ │
│  │  ┌─────────────────────────────────────────────────────────────┐               │ │
│  │  │ • Monitoring Setup Guide (monitoring-guide.pdf)             │ [Remove]      │ │
│  │  │ • Example Configurations (configs.zip)                      │ [Remove]      │ │
│  │  │ • Recommended Reading List (resources.md)                   │ [Remove]      │ │
│  │  └─────────────────────────────────────────────────────────────┘               │ │
│  │  [+ Add Resource]                                                               │ │
│  │                                                                                  │ │
│  │  ☑ Make resources available for download after event                           │ │
│  │  ☑ Include my contact info for follow-up questions                             │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── PRESENTATION REQUIREMENTS ────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  ✓ Format: 16:9 aspect ratio                  ⚠️ Branding: Add BATbern logo    │ │
│  │  ✓ Duration: Fits 45-minute slot              ✓ Accessibility: High contrast   │ │
│  │  ✓ Font size: Minimum 24pt                    ⚠️ Page numbers: Add to slides   │ │
│  │                                                                                  │ │
│  │  [Download Template] [View Guidelines]                                          │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  [Save Progress] [Submit for Review] [Request Feedback]                              │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Drag & Drop**: Upload presentation files directly
- **Version Control**: Maintain history of presentation versions
- **Preview**: View slides before submission
- **Supplementary Materials**: Add code repos, resources, documentation
- **Requirements Checklist**: Track compliance with presentation guidelines

## Functional Requirements Met

- **FR5**: Speaker management with presentation upload
- Version control for presentation materials
- Supplementary resource management
- Template and guideline access

## User Interactions

1. **Upload Presentation**: Drag & drop or browse for main presentation file
2. **Manage Versions**: View history, restore previous versions
3. **Add Resources**: Upload supplementary materials for attendees
4. **Check Compliance**: Validate against event requirements
5. **Preview Slides**: Review presentation before submission
6. **Submit for Review**: Send to organizers for feedback

## Technical Notes

- Resumable file uploads for large presentations
- PDF preview generation for all formats
- Version control with S3 versioning
- Support for multiple file formats (PPTX, PDF, KEY)

---

## API Requirements

APIs needed to load and display data for this screen:

### Initial Page Load

1. **GET /api/v1/speakers/{speakerId}/events/{eventId}/presentation**
   - Retrieve presentation details and upload status
   - Response includes: uploaded files, version history, supplementary materials, compliance status
   - Used for: Populating all presentation information and status

2. **GET /api/v1/events/{eventId}/presentation-requirements**
   - Retrieve event-specific presentation requirements
   - Response includes: format requirements, branding guidelines, accessibility standards, deadline
   - Used for: Requirements checklist display and validation

3. **GET /api/v1/speakers/{speakerId}/events/{eventId}/presentation/versions**
   - Retrieve version history of uploaded presentations
   - Response includes: version number, upload timestamp, file details, uploader
   - Used for: Version history display

4. **GET /api/v1/speakers/{speakerId}/events/{eventId}/supplementary-materials**
   - Retrieve all supplementary materials
   - Response includes: file list, URLs, metadata, download settings
   - Used for: Additional resources section

### Template & Guidelines

5. **GET /api/v1/events/{eventId}/presentation/template**
   - Retrieve presentation template file
   - Response: Template download URL or redirect to S3
   - Used for: [Download Template] action

6. **GET /api/v1/events/{eventId}/presentation/guidelines**
   - Retrieve presentation guidelines document
   - Response: Guidelines PDF or markdown content
   - Used for: [View Guidelines] action

---

## Action APIs

APIs called by user interactions and actions:

### File Upload

1. **POST /api/v1/speakers/{speakerId}/events/{eventId}/presentation/upload**
   - Triggered by: Drop file or [Browse Files] button
   - Payload: Multipart form-data with presentation file
   - Response: Upload confirmation, file URL, metadata (slide count, size)
   - Integration: AWS S3 with resumable upload support
   - Processing:
     - Generate PDF preview
     - Extract slide count
     - Create thumbnail previews
   - Max size: 50MB
   - Supported formats: PPTX, PDF, KEY, Google Slides link

2. **GET /api/v1/speakers/{speakerId}/presentations/{fileId}/upload-progress**
   - Triggered by: Polling during large file upload
   - Response: Upload progress percentage, estimated time remaining
   - Used for: Progress bar display during upload

### File Management

3. **DELETE /api/v1/speakers/{speakerId}/presentations/{fileId}**
   - Triggered by: [Delete] button
   - Response: Deletion confirmation
   - Side effect: Remove file from S3, archive in version history

4. **POST /api/v1/speakers/{speakerId}/presentations/{fileId}/replace**
   - Triggered by: [Replace] button
   - Opens: File picker for new file
   - Action: Uploads new version, archives old version
   - Maintains version history

5. **GET /api/v1/presentations/{fileId}/preview**
   - Triggered by: [Preview] button
   - Response: PDF preview URL or slide images
   - Opens: Preview modal with slide viewer

### Version Control

6. **POST /api/v1/speakers/{speakerId}/presentations/restore**
   - Triggered by: [Restore] button on version history item
   - Payload: `{ versionId }`
   - Response: Restored version details
   - Action: Makes selected version current, archives current as new version

7. **GET /api/v1/presentations/{fileId}/download**
   - Triggered by: Download link on version history
   - Response: Presigned S3 URL for file download
   - Used for: Downloading previous versions

### Supplementary Materials

8. **POST /api/v1/speakers/{speakerId}/events/{eventId}/supplementary/upload**
   - Triggered by: [+ Add Resource] button
   - Payload: Multipart form-data with resource file
   - Response: File upload confirmation, URL, metadata
   - Supported: Any file type (PDFs, ZIPs, MD, code files)
   - Max size: 25MB per file

9. **PUT /api/v1/speakers/{speakerId}/events/{eventId}/supplementary/{fileId}**
   - Triggered by: Updating resource metadata or download settings
   - Payload: `{ filename, description, availableAfterEvent }`
   - Response: Updated resource details

10. **DELETE /api/v1/speakers/{speakerId}/supplementary/{fileId}**
    - Triggered by: [Remove] button on supplementary material
    - Response: Deletion confirmation

11. **PUT /api/v1/speakers/{speakerId}/events/{eventId}/github-repo**
    - Triggered by: Entering/updating GitHub URL
    - Payload: `{ repoUrl }`
    - Response: Validation result, repo metadata (stars, description)
    - Validation: Check repo exists and is public

### Compliance & Validation

12. **POST /api/v1/presentations/{fileId}/validate-requirements**
    - Triggered by: Auto after upload or [Validate] button
    - Payload: `{ eventId }`
    - Response: Compliance check results
    - Returns: `{ requirements: [{ name, status: "pass|fail|warning", message }] }`
    - Validates: Aspect ratio, branding, page numbers, font sizes

### Submission

13. **POST /api/v1/speakers/{speakerId}/events/{eventId}/presentation/submit**
    - Triggered by: [Submit for Review] button
    - Payload: `{ presentationFileId, supplementaryMaterials: [], notes }`
    - Response: Submission confirmation, review timeline
    - Side effects:
      - Notifies organizer of submission
      - Updates speaker task status to "completed"
      - Locks current version for review
      - Triggers quality review workflow
    - State change: Presentation status → "under_review"

14. **POST /api/v1/speakers/{speakerId}/events/{eventId}/presentation/request-feedback**
    - Triggered by: [Request Feedback] button
    - Payload: `{ specificQuestions: [] }`
    - Response: Feedback request sent confirmation
    - Side effect: Notifies organizer, creates feedback task

### Progress Tracking

15. **PUT /api/v1/speakers/{speakerId}/events/{eventId}/presentation/progress**
    - Triggered by: [Save Progress] button or auto-save
    - Payload: All form data including checkboxes, URLs, settings
    - Response: Save confirmation, last saved timestamp
    - Used for: Persisting work-in-progress state

---

## Navigation Map

Screen transitions triggered by actions and events:

### Primary Navigation

1. **← Back Button**
   - **Target**: Speaker Dashboard (Story 3.3) or Material Submission Wizard (Story 3.3)
   - **Context**: Return to previous screen
   - **Warning**: If unsaved changes, prompt "Save progress?"

2. **[Download Template]**
   - **Action**: Downloads presentation template file
   - **No Navigation**: Remains on current screen
   - **Feedback**: File download initiated

3. **[View Guidelines]**
   - **Target**: Guidelines document viewer (modal or new tab)
   - **Type**: Modal overlay or PDF viewer
   - **Content**: Event presentation guidelines and best practices

### File Management Navigation

4. **[Browse Files]**
   - **Target**: System file picker dialog
   - **Return**: Remains on screen after file selection
   - **Action**: Initiates upload process

5. **[Preview]**
   - **Target**: Presentation preview modal
   - **Type**: Modal overlay with slide viewer
   - **Features**: Navigate slides, zoom, fullscreen
   - **Close**: Returns to current screen

6. **[Replace]**
   - **Action**: Opens file picker
   - **No Navigation**: Remains on screen
   - **Flow**: Upload new file → version history updated

7. **[Delete]**
   - **Confirmation**: "Are you sure?" dialog
   - **No Navigation**: Remains on screen
   - **Feedback**: File removed, version archived

8. **[Restore]** (version history)
   - **Confirmation**: "Restore this version?" dialog
   - **No Navigation**: Remains on screen
   - **Feedback**: Version restored, current version archived

### Supplementary Materials Navigation

9. **[+ Add Resource]**
   - **Target**: File upload dialog or URL input modal
   - **Type**: Modal overlay
   - **Options**: Upload file OR enter URL (GitHub, docs site)
   - **Return**: Closes modal, resource added to list

10. **[Remove]** (on resource)
    - **Confirmation**: "Remove this resource?" dialog
    - **No Navigation**: Remains on screen
    - **Feedback**: Resource removed from list

11. **GitHub Repository Link Click**
    - **Target**: GitHub repository (external)
    - **Type**: Opens in new tab
    - **Purpose**: Verify repo before submission

### Compliance & Quality Navigation

12. **Requirements Checklist Item Click**
    - **Target**: Requirement details tooltip or modal
    - **Type**: Tooltip or modal
    - **Content**: Explanation of requirement, how to fix
    - **Example**: "16:9 aspect ratio - Your slides must be 1920x1080 or 1280x720"

### Submission Flow

13. **[Save Progress]**
    - **Action**: Saves all current data
    - **No Navigation**: Remains on screen
    - **Feedback**: "Progress saved" toast notification

14. **[Submit for Review]**
    - **Validation**: Checks all requirements met
    - **If Valid**:
      - **Target**: Submission confirmation page
      - **Content**: "Materials submitted successfully. What happens next..."
      - **Actions**:
        - [Return to Dashboard] → Speaker Dashboard (Story 3.3)
        - [View Event] → Event Timeline (Story 3.5)
    - **If Invalid**:
      - **No Navigation**: Remains on screen
      - **Feedback**: Error modal showing missing requirements
      - **Highlight**: Invalid items in requirements checklist

15. **[Request Feedback]**
    - **Target**: Feedback request modal
    - **Type**: Modal overlay
    - **Content**: Text area for specific questions
    - **Submit**: Sends request to organizer
    - **Confirmation**: "Feedback request sent" toast
    - **Return**: Closes modal, remains on screen

### Event-Driven Navigation

16. **On Upload Complete**
    - **No Navigation**: Remains on screen
    - **Feedback**: Success toast, file details displayed
    - **Auto-actions**:
      - Validate requirements
      - Generate preview
    - **Updates**: Requirements checklist, version history

17. **On Upload Failure**
    - **No Navigation**: Remains on screen
    - **Feedback**: Error modal with details and retry option
    - **Actions**: [Retry], [Try Different File], [Contact Support]

18. **On Submission Accepted (by organizer)**
    - **Notification**: Via email and dashboard notification
    - **Entry**: Link from notification
    - **Feedback**: "Your presentation has been approved" banner
    - **Status**: Presentation status → "approved"

19. **On Revision Requested (by organizer)**
    - **Notification**: Via email and dashboard notification
    - **Entry**: Link from notification opens this screen
    - **Feedback**: Revision request banner with organizer feedback
    - **Highlight**: Specific issues to address
    - **Status**: Presentation status → "needs_revision"

### Deadline Management

20. **On Deadline Approaching** (< 7 days)
    - **Feedback**: Warning banner "Presentation due in 5 days"
    - **Urgency**: Red deadline indicator

21. **On Deadline Passed**
    - **Feedback**: Error banner "Presentation overdue"
    - **Action**: [Request Extension] button
    - **Restriction**: May block submission, require organizer approval

### Error States

22. **On File Too Large**
    - **No Navigation**: Remains on screen
    - **Feedback**: Error message "File exceeds 50MB limit"
    - **Suggestion**: "Compress images or split into multiple files"

23. **On Unsupported Format**
    - **No Navigation**: Remains on screen
    - **Feedback**: Error message "Unsupported file format"
    - **Supported**: List supported formats (PPTX, PDF, KEY)

24. **On Network Error During Upload**
    - **No Navigation**: Remains on screen
    - **Feedback**: "Upload paused - connection lost"
    - **Auto-resume**: When connection restored
    - **Action**: [Retry Now] button

### Mobile-Specific

25. **Mobile File Upload**
    - **Options**: Camera, Photo Library (for photos), Files app
    - **Optimization**: Upload progress visible in notification
    - **Background**: Support background uploads

26. **Mobile Preview**
    - **Target**: Full-screen slide viewer
    - **Gestures**: Swipe between slides, pinch to zoom
    - **Close**: Back button returns to screen

---
