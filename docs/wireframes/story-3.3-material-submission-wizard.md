# Material Submission Wizard - Wireframe

**Story**: Epic 3, Story 3.3
**Screen**: Material Submission Wizard
**User Role**: Speaker
**Related FR**: FR3 (Speaker Self-Service)

---

## Material Submission Wizard (Multi-Step)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Dashboard            Submit Speaker Materials                    [Save Draft]│
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  Spring Conference 2025 - Kubernetes Best Practices                                  │
│                                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐ │
│  │  Step 1         Step 2         Step 3         Step 4         Step 5    Review   │ │
│  │    ●━━━━━━━━━━━━━○━━━━━━━━━━━━━○━━━━━━━━━━━━━○━━━━━━━━━━━━━○━━━━━━━━━━○       │ │
│  │  Basic Info    Abstract      Biography       Photo         Presentation         │ │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── STEP 1: BASIC INFORMATION ─────────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  Presentation Title * (max 100 characters)                                     │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐          │  │
│  │  │ Kubernetes Best Practices for Production Environments           │ 58/100    │  │
│  │  └─────────────────────────────────────────────────────────────────┘          │  │
│  │                                                                                 │  │
│  │  Presentation Language *                                                       │  │
│  │  ● English    ○ German    ○ German with English slides                        │  │
│  │                                                                                 │  │
│  │  Session Format Preference                                                     │  │
│  │  ● Presentation only (45 min)                                                 │  │
│  │  ○ Presentation (30 min) + Demo (15 min)                                      │  │
│  │  ○ Workshop style (interactive)                                                │  │
│  │                                                                                 │  │
│  │  Special Requirements (optional)                                               │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐          │  │
│  │  │ Live demo planned - will need internet connection and backup    │          │  │
│  │  │ plan. May require audience participation.                        │          │  │
│  │  └─────────────────────────────────────────────────────────────────┘          │  │
│  │                                                                                 │  │
│  │                     [← Previous]  [Save Draft]  [Next Step →]                 │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── PROGRESS & TIPS ────────────────────────────────────────────────────────────┐ │
│  │  ✓ Auto-saved 30 seconds ago                                                   │  │
│  │  💡 Tip: Clear titles help attendees choose the right sessions                 │  │
│  │  📊 20% complete                                                                │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘

[STEP 2: ABSTRACT]
┌─────────────────────────────────────────────────────────────────────────────────────┐
│  ┌──── STEP 2: PRESENTATION ABSTRACT ─────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  Abstract * (max 1000 characters)                                              │  │
│  │  Your abstract should include:                                                 │  │
│  │  • What attendees will learn                                                   │  │
│  │  • Key takeaways                                                               │  │
│  │  • ⚠️ Must include "Lessons Learned" from real experience                     │  │
│  │                                                                                 │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐          │  │
│  │  │ This talk explores production-ready Kubernetes practices based   │          │  │
│  │  │ on 3 years of managing 50+ clusters at scale.                   │          │  │
│  │  │                                                                   │          │  │
│  │  │ You'll learn:                                                    │          │  │
│  │  │ • Cluster architecture patterns that scale                       │          │  │
│  │  │ • Security hardening beyond defaults                             │          │  │
│  │  │ • Monitoring strategies that actually work                       │          │  │
│  │  │ • Cost optimization techniques                                   │          │  │
│  │  │                                                                   │          │  │
│  │  │ Lessons learned: We'll share real failures including our         │          │  │
│  │  │ 2-hour production outage, DNS mysteries, and how we reduced     │          │  │
│  │  │ our cloud costs by 40% while improving reliability.             │          │  │
│  │  └─────────────────────────────────────────────────────────────────┘          │  │
│  │                                                              789/1000          │  │
│  │                                                                                 │  │
│  │  ✓ Length requirement met                                                      │  │
│  │  ✓ Lessons learned included                                                    │  │
│  │  ⚠️ Consider adding specific technologies/tools                               │  │
│  │                                                                                 │  │
│  │                                                                                 │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────┘

[STEP 3: BIOGRAPHY]
┌─────────────────────────────────────────────────────────────────────────────────────┐
│  ┌──── STEP 3: SPEAKER BIOGRAPHY ─────────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  Professional Bio * (max 500 characters)                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐          │  │
│  │  │ Peter Muller is a Principal Cloud Architect at TechCorp with    │          │  │
│  │  │ 10+ years in cloud native technologies. He leads the platform   │          │  │
│  │  │ team managing Kubernetes infrastructure for 500+ microservices.  │          │  │
│  │  │ Peter is a CNCF contributor and speaks regularly at DevOps      │          │  │
│  │  │ conferences across Europe. He holds certifications in CKA,      │          │  │
│  │  │ CKAD, and AWS Solutions Architect Professional.                 │          │  │
│  │  └─────────────────────────────────────────────────────────────────┘          │  │
│  │                                                              420/500          │  │
│  │                                                                                 │  │
│  │  Company/Organization *                        Job Title *                     │  │
│  │  ┌──────────────────────┐                     ┌──────────────────────┐       │  │
│  │  │ TechCorp AG          │                     │ Principal Architect  │       │  │
│  │  └──────────────────────┘                     └──────────────────────┘       │  │
│  │                                                                                 │  │
│  │  Social/Professional Links (optional)                                          │  │
│  │  LinkedIn:  [linkedin.com/in/pmuller_____________________________]            │  │
│  │  Twitter/X: [@pmuller_devops_____________________________________]            │  │
│  │  GitHub:    [github.com/pmuller__________________________________]            │  │
│  │  Website:   [https://pmuller.tech________________________________]            │  │
│  │                                                                                 │  │
│  │  Previous Speaking Experience                                                  │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐          │  │
│  │  │ • KubeCon EU 2024 - "Scaling Kubernetes"                       │          │  │
│  │  │ • DevOps Days Zurich 2023 - "GitOps Best Practices"           │          │  │
│  │  │ • BATbern Spring 2023 - "Container Security" (Rating: 4.8/5)   │          │  │
│  │  └─────────────────────────────────────────────────────────────────┘          │  │
│  │                                                                                 │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────┘

[STEP 4: PHOTO UPLOAD]
┌─────────────────────────────────────────────────────────────────────────────────────┐
│  ┌──── STEP 4: SPEAKER PHOTO ─────────────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  Upload Professional Photo *                                                   │  │
│  │                                                                                 │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐          │  │
│  │  │                                                                   │          │  │
│  │  │                      ┌─────────────────┐                         │          │  │
│  │  │                      │                 │                         │          │  │
│  │  │                      │                 │                         │          │  │
│  │  │                      │   📷 Drop       │                         │          │  │
│  │  │                      │   Photo Here    │                         │          │  │
│  │  │                      │                 │                         │          │  │
│  │  │                      │   or Browse     │                         │          │  │
│  │  │                      └─────────────────┘                         │          │  │
│  │  │                                                                   │          │  │
│  │  │              [Browse Files]  or drag and drop                    │          │  │
│  │  │                                                                   │          │  │
│  │  └─────────────────────────────────────────────────────────────────┘          │  │
│  │                                                                                 │  │
│  │  Requirements:                      Current Photo:                            │  │
│  │  • Minimum 400x400px               ┌──────────┐                               │  │
│  │  • Maximum 5MB                     │ [Photo   │                               │  │
│  │  • JPG or PNG format               │  Preview │                               │  │
│  │  • Professional headshot           │  Here]   │                               │  │
│  │  • Good lighting                   └──────────┘                               │  │
│  │  • Recent (within 2 years)         peter-muller.jpg                           │  │
│  │                                     2.3MB • 800x800px                         │  │
│  │                                     [Remove] [Replace]                        │  │
│  │                                                                                 │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Progress indicator**: Visual step-by-step wizard showing current position
- **Auto-save**: Every 30 seconds to prevent data loss
- **Drag & drop**: Upload photo and presentation files
- **Real-time validation**: Character counts, required field checking
- **Smart tips**: Contextual guidance for each step
- **Step navigation**: Previous/Next buttons with save draft option
- **Progress estimation**: Shows percentage complete and time remaining

## Functional Requirements Met

- **FR3**: Speaker self-service capabilities for content submission
- Multi-step wizard reduces cognitive load
- Auto-save prevents data loss
- Clear validation and feedback
- Support for all required speaker materials

## User Interactions

1. **Step 1 - Basic Info**: Enter presentation title, language, format preferences, special requirements
2. **Step 2 - Abstract**: Provide detailed abstract with lessons learned
3. **Step 3 - Biography**: Professional bio, company info, social links, speaking experience
4. **Step 4 - Photo**: Upload professional headshot with requirements guidance
5. **Step 5 - Presentation**: Upload presentation slides and supplementary materials
6. **Review**: Final review before submission

## Technical Notes

- React Hook Form for form state management
- Resumable file uploads with progress tracking
- IndexedDB for auto-save and offline support
- Validation engine with real-time feedback
- Component hierarchy: MaterialWizard.tsx → StepComponents (BasicInfo, Abstract, Biography, PhotoUpload, Presentation)
- Support for drag-and-drop file uploads
- Image preview with crop/resize functionality

---

## API Requirements

APIs needed to load and display data for this screen:

### Initial Load

1. **GET /api/v1/speakers/{speakerId}/events/{eventId}/materials/draft**
   - Retrieve existing draft or previously submitted materials
   - Response includes: all form data from previous steps, file metadata, submission status
   - Used for: Pre-populating form fields with saved/existing data
   - Auto-recovery: If session interrupted, resume from last saved state

2. **GET /api/v1/speakers/{speakerId}/profile**
   - Retrieve speaker profile data
   - Response includes: bio, photo, company, social links, speaking history
   - Used for: Pre-filling biography step (Step 3) with existing profile data
   - Optimization: Reduce duplicate data entry

3. **GET /api/v1/events/{eventId}/submission-requirements**
   - Retrieve event-specific submission requirements and guidelines
   - Response includes: required fields, optional fields, format specifications, deadlines
   - Used for: Dynamic form validation and requirement display
   - Context: Different events may have different requirements

### Reference Data

4. **GET /api/v1/reference/languages**
   - Retrieve supported presentation languages
   - Response: Array of language options
   - Used for: Language selection (Step 1)

---

## Action APIs

APIs called by user interactions and actions:

### Auto-Save

1. **PUT /api/v1/speakers/{speakerId}/events/{eventId}/materials/draft**
   - Triggered by: Auto-save (every 30 seconds) or field blur
   - Payload: Partial material data (current step + all previous steps)
   - Response: Draft saved timestamp, draft ID
   - Debounced: 30-second interval to reduce API calls
   - Offline support: Queue in IndexedDB, sync when online

### Step Navigation

2. **POST /api/v1/speakers/{speakerId}/events/{eventId}/materials/validate-step**
   - Triggered by: [Next Step →] button
   - Payload: `{ step: number, data: object }`
   - Response: Validation result, errors if any
   - Used for: Server-side validation before allowing progression
   - Returns: `{ valid: boolean, errors: [], warnings: [] }`

### File Uploads

3. **POST /api/v1/speakers/{speakerId}/upload/photo**
   - Triggered by: Photo file drop or browse (Step 4)
   - Payload: Multipart form-data with image file
   - Response: File URL, CDN path, image dimensions, file size
   - Integration: AWS S3 with presigned upload URL
   - Processing: Server-side image optimization and validation
   - Max size: 5MB

4. **POST /api/v1/speakers/{speakerId}/upload/presentation**
   - Triggered by: Presentation file drop or browse (Step 5)
   - Payload: Multipart form-data with presentation file
   - Response: File URL, CDN path, page count (for PDFs), file size
   - Integration: AWS S3 with resumable upload support
   - Supported formats: PDF, PPTX, KEY, Google Slides link
   - Max size: 50MB
   - Chunked upload: For large files, support resume capability

5. **DELETE /api/v1/speakers/{speakerId}/files/{fileId}**
   - Triggered by: [Remove] or [Replace] button on uploaded files
   - Response: File deleted confirmation
   - Side effect: Remove file from S3 storage

### Submission

6. **POST /api/v1/speakers/{speakerId}/events/{eventId}/materials/submit**
   - Triggered by: [Submit Materials] button on Review step
   - Payload: Complete material submission data (all steps)
   - Response: Submission confirmation, submission ID, next steps
   - Side effects:
     - Notifies organizer of submission
     - Updates speaker dashboard task status
     - Triggers quality review workflow (if configured)
   - State change: Material status → "submitted", speaker task → "completed"

### Draft Management

7. **GET /api/v1/speakers/{speakerId}/events/{eventId}/materials/versions**
   - Triggered by: Optional "View Previous Versions" link
   - Response: List of material versions with timestamps
   - Used for: Viewing submission history, reverting to previous versions

8. **POST /api/v1/speakers/{speakerId}/events/{eventId}/materials/revert**
   - Triggered by: "Revert to Version" action
   - Payload: `{ versionId }`
   - Response: Reverted material data
   - Used for: Recovering from mistakes or changes

### Profile Integration

9. **POST /api/v1/speakers/{speakerId}/profile/save-from-submission**
   - Triggered by: Checkbox "Save bio to my profile" (Step 3)
   - Payload: Bio, photo, company, social links
   - Response: Profile updated confirmation
   - Optimization: Allow speakers to update profile while submitting materials

### Validation & Feedback

11. **POST /api/v1/materials/check-quality**
    - Triggered by: Real-time as user types in abstract field
    - Payload: `{ abstract }`
    - Response: Quality score, suggestions, requirement check
    - Used for: Real-time feedback on abstract quality
    - Returns: `{ score: number, suggestions: [], requirementsMet: {} }`

---

## Navigation Map

Screen transitions triggered by actions and events:

### Primary Navigation

1. **← Back to Dashboard**
   - **Target**: Speaker Dashboard (Story 3.3)
   - **Context**: Exit wizard, return to dashboard
   - **Warning**: If unsaved changes, show "Save draft?" confirmation dialog

2. **[Save Draft]** (top-right button)
   - **Action**: Saves current progress
   - **No Navigation**: Remains on current step
   - **Feedback**: "Draft saved" toast notification
   - **Update**: Last saved timestamp displayed

### Step Navigation

3. **[Next Step →]**
   - **Target**: Next wizard step (Step 2, 3, 4, 5, or Review)
   - **Validation**: Validates current step before progression
   - **Error Handling**: If validation fails, show errors inline, don't progress
   - **Auto-save**: Triggers auto-save before moving to next step

4. **[← Previous]**
   - **Target**: Previous wizard step
   - **No Validation**: Allow backward navigation without validation
   - **Context**: Review or edit previous step data

5. **Progress Indicator Click** (Step circles at top)
   - **Target**: Clicked step (if already visited)
   - **Restriction**: Can't skip ahead to unvisited steps
   - **Use case**: Quick navigation to previously completed steps

### Submission Flow

6. **[Submit Materials]** (on Review step)
   - **Action**: POST final submission
   - **Target**: Submission Confirmation page
   - **Feedback**: Success message with next steps
   - **Navigation Options**:
     - "Return to Dashboard" button
     - "View Event Details" button → Event Timeline (Story 3.5)
   - **Notification**: Organizer notified of submission

7. **On Submission Success**
   - **Target**: Submission Success page
   - **Content**: Confirmation message, what happens next, timeline
   - **Actions**:
     - [Return to Dashboard] → Speaker Dashboard (Story 3.3)
     - [View Event] → Event Timeline (Story 3.5)
     - [Update Materials] → Returns to wizard in edit mode

### File Upload Navigation

8. **Photo Upload - Browse Files**
   - **Target**: System file picker dialog
   - **Return**: Remains on Step 4 after file selection
   - **Feedback**: Image preview, upload progress, validation status

9. **Photo Upload - Drag & Drop**
   - **No Navigation**: Inline file processing
   - **Feedback**: Drop zone highlight, upload progress, preview

10. **[Remove] or [Replace] File**
    - **Action**: Deletes file, optionally opens file picker
    - **No Navigation**: Remains on current step
    - **Feedback**: Confirmation dialog before deletion

### Error States & Recovery

11. **On Upload Failure**
    - **No Navigation**: Remains on current step
    - **Feedback**: Error message with retry option
    - **Action**: [Retry Upload] button or drag file again

12. **On Validation Error**
    - **No Navigation**: Remains on current step
    - **Feedback**: Inline error messages on invalid fields
    - **Focus**: Auto-scroll to first error field

13. **On Session Timeout**
    - **Target**: Login page
    - **Protection**: Draft saved to IndexedDB before redirect
    - **Recovery**: After re-login, prompt "Resume previous submission?"

14. **On Network Error**
    - **No Navigation**: Remains on current step
    - **Feedback**: "Offline mode" banner
    - **Auto-save**: Queued locally, sync when connection restored

### Auto-Save & Recovery

15. **On Page Refresh/Close with Unsaved Changes**
    - **Browser Warning**: "You have unsaved changes. Are you sure?"
    - **Recovery**: On return, load from IndexedDB or last server draft
    - **Prompt**: "Would you like to continue your previous submission?"

16. **On Auto-Save Success**
    - **No Navigation**: Remains on current step
    - **Feedback**: Small checkmark icon, "Saved 5 seconds ago" text

17. **On Auto-Save Failure**
    - **No Navigation**: Remains on current step
    - **Feedback**: Warning icon, "Unable to auto-save" message
    - **Action**: [Save Now] button to retry

### Profile Integration

18. **"Use Profile Data" Button** (Step 3)
    - **Action**: GET profile data and populate bio fields
    - **No Navigation**: Remains on Step 3
    - **Feedback**: Fields auto-filled with profile data
    - **Option**: "Save to profile" checkbox to update profile with changes

19. **"View My Profile" Link** (Step 3)
    - **Target**: Speaker Profile Management (Story 7.1)
    - **Type**: Open in new tab/window
    - **Context**: Review current profile without losing wizard progress

### Contextual Help

20. **💡 Tip Links**
    - **Target**: Help modal or tooltip expansion
    - **Type**: Inline modal overlay
    - **Content**: Expanded guidance and examples
    - **Close**: Remains on current step after reading

21. **"See Example" Link** (on Abstract step)
    - **Target**: Example abstract modal
    - **Type**: Modal overlay
    - **Content**: Sample high-quality abstracts
    - **Inspiration**: Helps speakers improve their content

### Event-Driven Navigation

22. **On Deadline Approaching** (< 7 days)
    - **Feedback**: Warning banner "Submission due in 5 days"
    - **Urgency**: Red deadline indicator in progress section

23. **On Deadline Passed**
    - **Feedback**: Error banner "Submission overdue"
    - **Action**: Prompt to contact organizer
    - **Option**: "Request Extension" button

24. **On Organizer Revision Request**
    - **Entry**: Link from dashboard notification or email
    - **Target**: Wizard opens to relevant step needing revision
    - **Context**: Organizer feedback displayed inline
    - **Mode**: Edit mode with feedback visible

### Mobile-Specific

25. **Mobile Step Navigation**
    - **UI**: Bottom sticky navigation buttons
    - **Steps**: One step fully visible at a time
    - **Progress**: Compact progress indicator at top

26. **Mobile File Upload**
    - **Options**: Camera, Photo Library, or Files
    - **Native**: Use device file picker/camera
    - **Optimization**: Image compression before upload on mobile