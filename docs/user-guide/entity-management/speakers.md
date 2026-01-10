# Speaker Management

> Manage speaker profiles and session assignments

<span class="feature-status in-progress">In Progress</span>

## Overview

Speakers are architecture professionals who present at BATbern conferences. Each speaker has a profile containing biographical information, expertise areas, and contact details. Speakers progress through defined status states from initial identification through confirmed participation.

## Speaker Profiles

### Profile Components

**Personal Information**:
- First Name, Last Name
- Professional headshot (photo upload)
- Email, Phone
- LinkedIn profile (optional)

**Professional Details**:
- Company affiliation
- Job title
- Years of experience
- Professional biography (≤500 characters)

**Expertise**:
- Primary topics (e.g., "Sustainable Building", "Digital Transformation")
- Secondary topics (related interests)
- Languages spoken (German, French, Italian, English)

**Presentation History**:
- Past BATbern presentations
- External speaking engagements
- Publications and awards

## Creating a Speaker

<div class="step" data-step="1">

**Navigate to Speakers**

Click **🎤 Speakers** in the left sidebar.
</div>

<div class="step" data-step="2">

**Click "Create New Speaker"**

Click the **+ Create New Speaker** button (top-right).
</div>

<div class="step" data-step="3">

**Fill Speaker Details**

Complete the speaker creation form:

**Basic Information**:
- **First Name*** - Given name
- **Last Name*** - Family name
- **Email*** - Contact email
- **Phone** - Contact number

**Professional Profile**:
- **Company** - Select from autocomplete (optional)
- **Job Title** - Current position
- **Biography** - Professional summary (≤500 characters)

**Expertise**:
- **Primary Topics*** - Main areas of expertise (select 1-3)
- **Secondary Topics** - Related interests (optional)
- **Languages** - Spoken languages for presentations

**Visual Assets**:
- **Headshot** - Professional photo (PNG, JPG, max 2MB)

</div>

<div class="step" data-step="4">

**Save**

Click **Save** to create the speaker profile.

Speaker is created with initial status: **identified**

Note: Speakers created outside workflow (via Speakers menu) start in "identified" state. Speakers added during Phase A workflow automatically link to the event.
</div>

## Speaker Status States

<span class="feature-status implemented">Implemented</span>

Each speaker progresses through their **own independent workflow** in parallel with other speakers. The speaker workflow operates independently from the event workflow, allowing flexible coordination.

### Status Flow

```mermaid
graph LR
    A[identified] --> B[contacted]
    B --> C[ready]
    C --> D[accepted]
    C --> E[declined]
    D --> F[content_submitted]
    F --> G[quality_reviewed]
    G --> H[confirmed]
    D --> I[overflow]
    D --> J[withdrew]
```

### Status Descriptions

| Status | Description | Organizer Actions |
|--------|-------------|-------------------|
| **identified** | Potential speaker brainstormed in Phase A | Send initial outreach |
| **contacted** | Outreach email sent, awaiting response | Follow up if no response |
| **ready** | Speaker ready to accept/decline invitation | Get acceptance confirmation |
| **accepted** | Speaker committed to presenting | Collect content submission |
| **declined** | Speaker not available | Contact backup speaker |
| **content_submitted** | Speaker submitted presentation details | Review content quality (Phase C) |
| **quality_reviewed** | Content approved by organizer | Assign time slot (Phase D) |
| **confirmed** | Quality reviewed AND slot assigned | Ready for agenda publication |
| **overflow** | Accepted but no slot available | Backup speaker for dropouts |
| **withdrew** | Speaker dropped out after accepting | Find replacement speaker |

### Critical: Auto-Confirmation

Speakers reach **confirmed** status automatically when **BOTH** conditions are met (in any order):
1. Speaker status = `quality_reviewed` (content approved in Phase C)
2. Session has `startTime` (slot assigned in Phase D)

**This means**:
- Quality review first → slot assigned later → auto-confirms when slot saved
- Slot assigned first → quality review later → auto-confirms when quality approved
- Order doesn't matter - confirmation happens when both complete

### Status Transitions

Speaker workflow progresses through phases:

- **Phase A: Setup** - identified (speakers brainstormed)
- **Phase B: Outreach** - identified → contacted → ready → accepted/declined
- **Phase B: Content Collection** - accepted → content_submitted
- **Phase C: Quality Review** - content_submitted → quality_reviewed
- **Phase D: Assignment** - quality_reviewed + slot → **confirmed** (automatic)
- **Any Phase** - accepted → withdrew (if speaker drops out)

**Parallel Progression**: All speakers move through states independently. Event can have speakers in different states simultaneously (e.g., Speaker A is "contacted", Speaker B is "content_submitted", Speaker C is "confirmed").

See [Workflow System Documentation](../workflow/README.md) for complete details on the 3 workflow systems.

## Content Collection

<span class="feature-status in-progress">In Progress</span>

Speakers submit presentation content during **Phase B: Outreach**.

### Content Requirements

**Mandatory Fields**:
- **Presentation Title*** - Specific session title (≤100 characters)
- **Abstract*** - Session description (≤1000 characters)
- **Learning Objectives** - What attendees will learn (3-5 bullets)

**Optional Fields**:
- **Supporting Materials** - Slides, handouts, references (PDF upload)
- **Prerequisites** - Required attendee knowledge
- **Target Audience** - Who should attend (Beginners, Intermediate, Advanced)

### Character Limit Validation

Abstract field enforces 1000-character limit:

```
Abstract *
[Your presentation description here...     ]

Characters: 287 / 1000 ✅
```

```
Abstract *
[Very long text that exceeds the limit... ]

Characters: 1042 / 1000 ❌
Error: Abstract must be 1000 characters or less
```

### Content Review

Content is reviewed during **Phase C: Quality Review**:

1. **Organizer reviews content** for:
   - Relevance to event theme
   - Quality and clarity
   - Originality
   - Audience fit

2. **Feedback provided** if revisions needed:
   - Organizer clicks "Request Revisions" in quality drawer
   - Speaker receives comments
   - Speaker revises and resubmits
   - Status remains content_submitted until approved

3. **Content approved**:
   - Organizer clicks "Approve" in quality drawer
   - Status advances to quality_reviewed
   - Speaker auto-confirms when slot also assigned (in either order)

See [Phase C: Quality Review](../workflow/phase-c-quality.md) for details.

## Outreach Tracking

<span class="feature-status implemented">Implemented</span>

Track all speaker communications during outreach phase.

### Contact History

```
Contact History - Hans Müller
────────────────────────────────────
2025-02-15 14:23 | Email Sent
Subject: "Invitation to speak at BATbern 2025"
Status: contacted

2025-02-18 09:45 | Response Received
"Yes, interested in presenting on sustainable materials"
Status: ready

2025-02-19 10:15 | Acceptance Confirmed
Speaker accepted invitation
Status: accepted

2025-02-20 16:30 | Content Request Sent
Subject: "Content submission deadline: March 1"

2025-02-28 11:15 | Content Received
Title: "Innovations in Sustainable Building Materials"
Status: content_submitted

2025-03-02 14:30 | Quality Review Completed
Content approved by organizer
Status: quality_reviewed

2025-03-05 09:00 | Slot Assigned
Assigned to 10:00-10:45 AM slot
Status: confirmed (auto-confirmed)
```

### Outreach Templates

<span class="feature-status planned">Planned</span>

Pre-built email templates for common communications:

**Initial Invitation**:
```
Subject: Invitation to speak at BATbern 2025

Dear {{speaker_name}},

We are planning BATbern 2025 and would like to invite you
to present on "{{topic}}".

Event Details:
- Date: {{event_date}}
- Type: {{event_type}}
- Audience: {{expected_attendees}} architects

Are you interested? Please let us know by {{response_deadline}}.

Best regards,
{{organizer_name}}
```

**Content Request**:
```
Subject: Content submission for BATbern 2025

Dear {{speaker_name}},

Thank you for confirming your participation! Please submit:

- Presentation title (≤100 characters)
- Abstract (≤1000 characters)
- 3-5 learning objectives

Deadline: {{content_deadline}}

Submit via: {{submission_link}}

Looking forward to your presentation!
```

See [Phase B: Outreach](../workflow/phase-b-outreach.md) for outreach management details.

## Session Assignment

<span class="feature-status planned">Planned</span>

Confirmed speakers are assigned to event time slots during **Phase D: Assignment**.

### Slot Assignment Interface

Drag-and-drop interface for assigning speakers to slots:

```
BATbern 2025 - March 15, 2025
──────────────────────────────────────────

Morning Track A         | Morning Track B
────────────────────────────────────────
09:00-09:45            | 09:00-09:45
[Drag speaker here]     | [Hans Müller]
                        | Sustainable Materials

10:00-10:45            | 10:00-10:45
[Anna Schmidt]         | [Drag speaker here]
Digital Architecture    |
```

### Assignment Constraints

System enforces constraints:
- ✅ **No double-booking**: Speaker can't be in two slots simultaneously
- ✅ **Break enforcement**: Minimum 15-minute gap between speaker's sessions
- ✅ **Availability check**: Speakers can mark unavailable time slots
- ❌ **Conflict warnings**: Flag potential scheduling conflicts

See [Phase D: Assignment](../workflow/phase-d-assignment.md) for details.

## Speaker Directory

<span class="feature-status planned">Planned</span>

Public-facing speaker directory showcases confirmed speakers.

### Directory Display

```
BATbern 2025 Speakers
────────────────────────────────────────

[Photo]  Hans Müller
         Müller Architekten AG
         Senior Architect

         "Innovations in Sustainable Building Materials"

         Expert in sustainable design with 15 years experience.
         Previous speaker at BATbern 2022, 2023.

         [View Session Details]

────────────────────────────────────────

[Photo]  Anna Schmidt
         Schmidt & Partner
         Digital Innovation Lead

         "Digital Transformation in Architecture"

         Leading digital initiatives in architectural practice.

         [View Session Details]
```

## Editing a Speaker

<div class="step" data-step="1">

**Find the Speaker**

Search or browse the speaker list.
</div>

<div class="step" data-step="2">

**Click Edit**

Click **📝 Edit** icon in speaker row.
</div>

<div class="step" data-step="3">

**Modify Fields**

Update speaker information. Note:
- Email cannot be changed (linked to user account)
- Status should be changed through workflow UI (Phase B Kanban, Phase C Quality drawer)
- Content can be edited at any time
- Manual status override available via "Override workflow validation" checkbox (use sparingly)

</div>

<div class="step" data-step="4">

**Save Changes**

Click **Save Changes** to persist updates.
</div>

## Handling Speaker Dropouts

<span class="feature-status planned">Planned</span>

If a speaker withdraws participation:

<div class="step" data-step="1">

**Mark as Withdrew**

Change speaker status to **withdrew** via:
- Phase B Kanban board: Drag speaker to "Withdrew" column
- Or speaker edit modal with workflow override
</div>

<div class="step" data-step="2">

**Document Reason**

Add note explaining withdrawal:
- Schedule conflict
- Personal reasons
- Company policy
- Health issues
</div>

<div class="step" data-step="3">

**Find Replacement**

Options:
- **Promote backup speaker** from identified list
- **Reassign session** to confirmed speaker (if compatible topic)
- **Cancel session** and adjust agenda

</div>

<div class="step" data-step="4">

**Update Agenda**

If event already published:
- Update public agenda
- Send notification to registered attendees
- Update printed materials if time permits

</div>

See [Phase B: Outreach](../workflow/phase-b-outreach.md) for speaker state management and dropout handling.

## Searching Speakers

### Quick Search

```
🔍 [Müller]
```

Searches across:
- First name, Last name
- Company name
- Topics/expertise

### Filter by Status

```
status:confirmed
```

```
status:contacted,accepted
```

### Filter by Topic

```
topic:Sustainable Building
```

## Common Issues

### "Speaker stuck in contacted status"

**Problem**: Speaker contacted but no response received.

**Solution**:
- Send follow-up reminder (after 3-5 days)
- Try alternative contact method (phone instead of email)
- Move to backup speaker if deadline approaching
- Update status to declined if no response after 2 reminders (via Kanban drag-and-drop)

### "Content exceeds 1000 characters"

**Problem**: Speaker submitted abstract longer than limit.

**Solution**:
- Request speaker to revise and shorten
- Provide editing suggestions
- Emphasize need for concise, focused abstracts
- Offer to help edit if language barrier

### "Speaker has scheduling conflict after confirmation"

**Problem**: Confirmed speaker can't attend assigned time slot.

**Solution**:
- Check if alternative slot available in Phase D
- Swap with another speaker if possible (drag-and-drop slot reassignment)
- If no alternatives, may need to mark as withdrew
- Communicate changes promptly
- Update published agenda if event already in AGENDA_PUBLISHED state

## Related Topics

- [Phase B: Outreach →](../workflow/phase-b-outreach.md) - Speaker outreach process
- [Phase C: Quality →](../workflow/phase-c-quality.md) - Content review
- [Phase D: Assignment →](../workflow/phase-d-assignment.md) - Slot assignment
- [User Management →](users.md) - Link speakers to user accounts
- [Event Management →](events.md) - Event sessions and speakers

## API Reference

### Endpoints

```
POST   /api/speakers               Create speaker
GET    /api/speakers               List speakers (paginated)
GET    /api/speakers/{id}          Get speaker by ID
PUT    /api/speakers/{id}          Update speaker
DELETE /api/speakers/{id}          Delete speaker
PUT    /api/speakers/{id}/status   Update speaker status
POST   /api/speakers/{id}/content  Submit presentation content
POST   /api/speakers/{id}/photo    Request presigned URL for headshot upload
```

See [API Documentation](../../api/) for complete specifications.
