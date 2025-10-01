# BATbern Speaker Portal - Wireframes Index

## Overview
This document serves as an index for all speaker interface wireframes, covering the complete speaker journey from invitation to post-event content management.

**Individual wireframe files have been separated by story for better organization and development workflow alignment.**

---

## Purpose

The speaker portal provides a seamless, self-service experience for speakers to:
- **Respond to Invitations**: Quick accept/decline with availability
- **Submit Materials**: Guided wizard for abstracts and presentations
- **Manage Profile**: Professional profile with expertise and history
- **Track Events**: Timeline view of all speaking engagements
- **Communicate**: Direct channel with organizers and other speakers
- **Build Community**: Network with fellow speakers and access resources

---

## Wireframe Files by Story

### Epic 3: Speaker Management

**Story 3.2 - Speaker Response**
- `story-3.2-invitation-response.md` - Invitation Response Interface

**Story 3.3 - Material Submission**
- `story-3.3-speaker-dashboard.md` - Speaker Portal Dashboard
- `story-3.3-material-submission-wizard.md` - Material Submission Wizard
- `story-3.3-presentation-upload.md` - Presentation Upload & Management

**Story 3.5 - Speaker Outreach**
- `story-3.5-event-timeline.md` - Event Participation Timeline

### Epic 7: Enhanced Features

**Story 7.1 - Speaker Dashboard**
- `story-7.1-speaker-profile-management.md` - Speaker Profile Management
- `story-7.1-speaker-community.md` - Speaker Community & Networking

**Story 7.3 - Communication Hub**
- `story-7.3-communication-hub.md` - Speaker Communication Hub

---

## Key Features Covered

### FR3: Automated Speaker Workflows
Self-service portal with automated reminders and status tracking.

### FR10: Speaker Self-Service Portal
Complete material submission, agenda viewing, and profile management.

---

## Speaker Journey Stages

### 1. Invitation (Story 3.2)
- Receive invitation email with portal link
- Review event details and topic
- Accept/decline with one click
- Provide availability and preferences

### 2. Onboarding (Story 3.3)
- Access speaker dashboard
- Complete profile (if new speaker)
- Review submission requirements
- Access helpful resources

### 3. Content Submission (Story 3.3)
- Abstract submission wizard
- Presentation upload
- Supporting materials
- Preview and review

### 4. Pre-Event (Story 3.5 & 7.3)
- Track event timeline
- Communicate with organizers
- Review logistics
- Access venue information

### 5. Post-Event (Story 7.1)
- View attendance metrics
- Access recordings
- Receive feedback
- Download materials

### 6. Community (Story 7.1 & 7.3)
- Network with other speakers
- Share knowledge
- Participate in discussions
- Build reputation

---

## User Roles

### Invited Speaker
- Invitation response only
- Limited profile access
- No dashboard access

### Confirmed Speaker
- Full dashboard access
- Material submission
- Communication hub
- Event timeline

### Alumni Speaker
- Historical event access
- Community features
- Profile management
- Mentoring opportunities

---

## Design Principles

1. **Simplicity First**: Minimal friction for busy professionals
2. **Clear Progress**: Always show what's next
3. **Guided Workflows**: Step-by-step wizards for complex tasks
4. **Mobile-Friendly**: Responsive design for on-the-go access
5. **Professional Tone**: Respectful of speaker expertise

---

## Navigation Structure

```
Speaker Portal
├── Dashboard (Story 3.3)
│   ├── Upcoming Events
│   ├── Action Items
│   ├── Recent Activity
│   └── Quick Links
│
├── My Events
│   ├── Event Timeline (Story 3.5)
│   ├── Material Submission (Story 3.3)
│   └── Presentation Upload (Story 3.3)
│
├── Profile (Story 7.1)
│   ├── Personal Information
│   ├── Expertise & Topics
│   ├── Speaking History
│   └── Media & Photos
│
├── Communication (Story 7.3)
│   ├── Messages with Organizers
│   ├── Speaker Discussions
│   └── Notifications
│
└── Community (Story 7.1)
    ├── Speaker Directory
    ├── Discussion Forums
    └── Resources & Tips
```

---

## Technical Considerations

### File Uploads
- Drag-and-drop interface
- Multiple file format support
- Progress indicators
- Resume interrupted uploads

### Email Integration
- Invitation emails with magic links
- Automated reminders
- Status update notifications
- Calendar invites

### Mobile Experience
- Touch-optimized controls
- Offline content access
- Camera integration for photos
- Native app-like experience

### Security
- Secure file storage
- Access control per event
- Email verification
- Session management

---

## Key User Interactions

### Invitation Response
- One-click accept/decline
- Conditional fields based on response
- Immediate confirmation
- Automated organizer notification

### Material Submission
- Multi-step wizard with validation
- Auto-save drafts
- Preview before submit
- Change tracking

### Profile Management
- In-place editing
- Image cropping
- Tag selection with autocomplete
- Social media integration

### Communication
- Real-time messaging
- Thread organization
- File attachment
- Read receipts

---

## Validation & Quality Control

### Abstract Requirements
- Minimum/maximum length
- Required sections (problem, solution, lessons)
- Technical level indication
- Target audience

### Presentation Requirements
- Accepted formats (PDF, PPTX, KEY)
- File size limits
- Preview generation
- Version management

### Profile Completeness
- Required fields highlighted
- Completion percentage
- Profile strength indicators
- Improvement suggestions

---

## Related Documentation

- **PRD**: See `docs/prd/epic-3-speaker-management-stories.md` and `epic-7-enhanced-features-stories.md`
- **User Stories**: Individual story files in PRD folder
- **Coverage Report**: `wireframes-coverage-report.md`

---

## Notes

All detailed wireframes have been extracted into individual story-specific files for:
- Better alignment with development workflow
- Easier version control and reviews
- Clearer traceability to user stories
- Independent updates per feature

Each wireframe file is self-contained with complete ASCII art, interaction descriptions, and technical notes.