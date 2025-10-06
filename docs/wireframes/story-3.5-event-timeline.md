# Story 3.5: Event Timeline - Wireframe

**Story**: Epic 3, Story 5 - Event Participation Timeline
**Screen**: Event Timeline View
**User Role**: Speaker
**Related FR**: FR5 (Speaker Management)

---

## 5. Event Participation Timeline

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back          Spring Conference 2025 - Your Timeline                    [Export]   │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  Event: Cloud Native Architecture       Date: May 15, 2025      Your Slot: 09:45 AM │
│                                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐ │
│  │                           YOUR EVENT TIMELINE                                    │ │
│  │                                                                                   │ │
│  │  March                 April                  May                                │ │
│  │  ─────┬─────────────────┬──────────────────────┬───────────────────────         │ │
│  │       │                 │                      │                                 │ │
│  │   Mar 3             Apr 1                 May 1              May 15              │ │
│  │     ↓                 ↓                     ↓                  ↓                 │ │
│  │   ✓ Accepted      ⚠️ Abstract Due      Final Slides      EVENT DAY              │ │
│  │   Invitation        Revision               Due                                   │ │
│  │                                                                                   │ │
│  │   ✓ Mar 5         ✓ Mar 20            ○ Apr 30           ○ May 10               │ │
│  │   Bio Submitted   Photo Uploaded      Slides Due         Tech Check              │ │
│  │                                                                                   │ │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── UPCOMING MILESTONES ─────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  ⚠️ OVERDUE - Abstract Revision                          Due: Apr 1 (2 days ago)│ │
│  │     Moderator feedback requires addressing 2 points                             │ │
│  │     [View Feedback] [Submit Revision]                                           │ │
│  │                                                                                  │ │
│  │  📊 Final Presentation Slides                           Due: Apr 30 (27 days)   │ │
│  │     PDF format, 16:9 aspect ratio, max 30 slides                              │ │
│  │     [Upload Slides] [View Guidelines]                                           │ │
│  │                                                                                  │ │
│  │  🎤 Tech Check Appointment                              May 10 (Time TBD)       │ │
│  │     Test AV equipment, screen sharing, backup plans                            │ │
│  │     [Schedule Time Slot] [Technical Requirements]                              │ │
│  │                                                                                  │ │
│  │  🍽️ Speaker Dinner RSVP                                May 14, 19:00           │ │
│  │     Restaurant Volkshaus, Zurich                                               │ │
│  │     [✓ Attending] [Dietary Restrictions]                                       │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── PREPARATION CHECKLIST ────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Pre-Event (Complete by May 10)          Event Day                             │ │
│  │  ☑ Submit bio and photo                  ☐ Arrive 45 min early                │ │
│  │  ☑ Provide abstract                      ☐ Bring laptop + adapter             │ │
│  │  ☐ Upload final slides                   ☐ Have backup on USB                 │ │
│  │  ☐ Complete tech check                   ☐ Test presentation mode              │ │
│  │  ☐ Review attendee questions             ☐ Bring business cards               │ │
│  │  ☐ Prepare backup demos                  ☐ Stay for networking                │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Visual Timeline**: Gantt-style timeline showing all milestones from invitation to event day
- **Status Indicators**: ✓ Completed, ⚠️ Overdue, ○ Pending
- **Milestone Cards**: Interactive cards with actions for each upcoming task
- **Preparation Checklist**: Pre-event and event-day checklists with progress tracking
- **Calendar Integration**: Export timeline to personal calendar

## Functional Requirements Met

- **FR5**: Speaker management with event participation tracking
- Complete visibility into event preparation timeline
- Milestone and deadline tracking
- Preparation checklist with progress indicators

## User Interactions

1. **View Timeline**: See all event milestones from acceptance to event day
2. **Track Progress**: Monitor completion of required tasks
3. **Act on Milestones**: Submit materials, RSVP, schedule appointments
4. **Export Calendar**: Add events and deadlines to personal calendar
5. **Complete Checklist**: Track preparation tasks

## Technical Notes

- Timeline visualization using timeline component library
- Real-time updates on milestone completion
- Calendar export using iCal format
- Integration with task management system
- Responsive design for mobile timeline view

---

## API Requirements

APIs needed to load and display data for this screen:

### Initial Page Load

1. **GET /api/v1/speakers/{speakerId}/events/{eventId}?include=timeline,materials,checklist**
   - **Consolidated API**: Replaces `/timeline`, `/milestones`, and `/checklist` endpoints
   - Retrieve complete event data with timeline, materials, and checklist included
   - Response includes:
     - Complete timeline with all milestones, deadlines, completion status
     - Event milestones with status (pending/overdue/completed)
     - Preparation checklist items with categories
     - Material submission status
   - Used for: Populating timeline visualization, milestone cards, and checklist in a single call
   - **Consolidation benefit**: 3 endpoints → 1 endpoint (67% reduction), atomic data consistency
   - Query filter for milestones: Can use `?filter={"milestoneStatus":"pending|overdue"}` within include

2. **GET /api/v1/events/{eventId}**
   - Retrieve event details
   - Response includes: event name, date, location, topic, speaker slot time
   - Used for: Header information display

---

## Action APIs

APIs called by user interactions and actions:

### Milestone Actions

1. **GET /api/v1/speakers/{speakerId}/feedback?filter={"eventId":"{eventId}","milestoneId":"{milestoneId}"}&include=details**
   - **Consolidated API**: Uses standard feedback endpoint with filtering
   - Triggered by: [View Feedback] button
   - Response: Organizer feedback details, revision requests
   - Opens: Feedback modal or navigates to relevant submission screen
   - **Consolidation benefit**: Reuses existing feedback endpoint, consistent feedback access pattern

2. **POST /api/v1/speakers/{speakerId}/events/{eventId}/materials**
   - **Consolidated API**: Uses unified materials endpoint
   - Triggered by: [Submit Revision], [Upload Slides] buttons
   - Action: Redirects to appropriate submission interface
   - Navigation: Opens material submission wizard or upload screen
   - **Consolidation benefit**: All material submissions through single endpoint

3. **PUT /api/v1/speakers/{speakerId}/events/{eventId}/milestones/{milestoneId}/status**
   - Triggered by: Completing milestone action
   - Payload: `{ status: "completed", completionData: {} }`
   - Response: Updated milestone status
   - Updates: Timeline visualization and milestone list

### Scheduling

4. **GET /api/v1/events/{eventId}/tech-check/available-slots**
   - Triggered by: [Schedule Time Slot] button for tech check
   - Response: Available time slots for tech check appointments
   - Opens: Time slot selection modal

5. **POST /api/v1/speakers/{speakerId}/events/{eventId}/tech-check/schedule**
   - Triggered by: Selecting time slot in tech check scheduling
   - Payload: `{ timeSlot, requirements: [] }`
   - Response: Tech check appointment confirmation
   - Side effect: Sends confirmation email, adds to calendar

6. **GET /api/v1/events/{eventId}/technical-requirements**
   - Triggered by: [Technical Requirements] link
   - Response: Technical requirements form or document
   - Opens: Technical requirements page or PDF

### RSVP & Logistics

7. **POST /api/v1/speakers/{speakerId}/events/{eventId}/speaker-dinner/rsvp**
   - Triggered by: [✓ Attending] button for speaker dinner
   - Payload: `{ attending: true, dietaryRestrictions: "string" }`
   - Response: RSVP confirmation
   - Side effect: Updates event logistics, notifies organizer

8. **PUT /api/v1/speakers/{speakerId}/events/{eventId}/dietary-restrictions**
   - Triggered by: [Dietary Restrictions] link
   - Opens: Form modal for dietary restrictions
   - Payload: `{ restrictions: "string", allergies: [] }`
   - Response: Restrictions saved confirmation

### Guidelines & Documentation

9. **GET /api/v1/events/{eventId}/presentation/guidelines**
   - Triggered by: [View Guidelines] link
   - Response: Presentation guidelines document (PDF or markdown)
   - Opens: Guidelines viewer (modal or new tab)

### Calendar Integration

10. **POST /api/v1/speakers/{speakerId}/export**
    - **Consolidated API**: Uses speaker export endpoint with event context
    - Triggered by: [Export] or [Add to Calendar] buttons
    - Payload: `{ eventId, format: "ical|google|outlook", includeDeadlines: true }`
    - Response: iCal file or calendar service redirect
    - Downloads: .ics file with all milestones
    - **Consolidation benefit**: Unified export endpoint for all speaker data exports

### Checklist Management

11. **PATCH /api/v1/speakers/{speakerId}/events/{eventId}**
    - **Consolidated API**: Uses PATCH on event endpoint for checklist updates
    - Triggered by: Checking/unchecking checklist item
    - Payload: `{ checklist: { itemId: { completed: true } } }`
    - Response: Updated event data with checklist status
    - Updates: Checklist progress, completion percentage
    - **Consolidation benefit**: Checklist is part of event resource, no separate endpoint needed

12. **POST /api/v1/speakers/{speakerId}/events/{eventId}/checklist/reset**
    - Triggered by: Optional "Reset Checklist" action
    - Response: All items reset to incomplete
    - Used for: Testing or re-preparation

### Questions & Support

13. **GET /api/v1/speakers/{speakerId}/events/{eventId}/attendee-questions**
    - Triggered by: "Review attendee questions" checklist item
    - Response: Pre-submitted questions from attendees (if feature enabled)
    - Opens: Questions review interface

14. **POST /api/v1/speakers/{speakerId}/events/{eventId}/support-request**
    - Triggered by: "Need Help" or support button
    - Payload: `{ subject, message, urgency }`
    - Response: Support ticket created
    - Side effect: Notifies organizer team

---

## Navigation Map

Screen transitions triggered by actions and events:

### Primary Navigation

1. **← Back Button**
   - **Target**: Speaker Dashboard (Story 3.3)
   - **Context**: Return to main dashboard

2. **[Export]** (top-right)
   - **Action**: Downloads calendar file
   - **No Navigation**: Remains on current screen
   - **Feedback**: "Calendar file downloaded" or redirects to calendar service

### Milestone Actions Navigation

3. **[View Feedback]** (on overdue abstract revision)
   - **Target**: Feedback modal or revision interface
   - **Type**: Modal overlay showing organizer feedback
   - **Actions**: [Close], [Submit Revision]

4. **[Submit Revision]**
   - **Target**: Material Submission Wizard (Story 3.3)
   - **Context**: Opens to specific step needing revision (e.g., Abstract step)
   - **Pre-fill**: Existing data with feedback annotations

5. **[Upload Slides]**
   - **Target**: Presentation Upload & Management (Story 3.3 - Presentation Upload)
   - **Type**: Full page navigation
   - **Context**: Direct link to presentation upload interface

6. **[View Guidelines]**
   - **Target**: Guidelines document viewer
   - **Type**: Modal, new tab, or PDF viewer
   - **Content**: Presentation guidelines and best practices

### Scheduling Navigation

7. **[Schedule Time Slot]** (tech check)
   - **Target**: Time slot selection modal
   - **Type**: Modal overlay with calendar picker
   - **Flow**: Select time → Confirm → Appointment created
   - **Return**: Closes modal, milestone updated

8. **[Technical Requirements]**
   - **Target**: Technical requirements form or document
   - **Type**: Modal or dedicated page
   - **Content**: Form to specify AV needs, equipment, special requests
   - **Submit**: Updates speaker requirements

### RSVP Navigation

9. **[✓ Attending]** (speaker dinner)
   - **Action**: POST RSVP
   - **Feedback**: Checkmark changes to "✓ Confirmed"
   - **No Navigation**: Remains on screen
   - **Optional**: Opens dietary restrictions form

10. **[Dietary Restrictions]**
    - **Target**: Dietary restrictions form modal
    - **Type**: Modal overlay with form fields
    - **Submit**: Saves restrictions
    - **Return**: Closes modal, RSVP updated

### Calendar Integration

11. **[Add to Calendar]**
    - **Action**: Exports to calendar service
    - **Options**: Google Calendar, Outlook, Apple Calendar, iCal file
    - **Flow**: Select service → Redirect or download

### Checklist Navigation

12. **Checklist Item Click**
    - **Behavior**: Toggle checked/unchecked
    - **No Navigation**: Remains on screen
    - **Updates**: Progress percentage, completion status
    - **Auto-save**: Checkbox state persisted immediately

13. **"Review attendee questions" Item**
    - **If Clicked**: Opens attendee questions interface
    - **Target**: Questions review page or modal
    - **Type**: Modal or dedicated page
    - **Content**: Pre-submitted questions from registered attendees

### Timeline Interaction

14. **Timeline Milestone Click**
    - **Target**: Milestone details modal
    - **Type**: Modal overlay
    - **Content**: Milestone description, requirements, actions
    - **Close**: Returns to timeline view

15. **Overdue Milestone**
    - **Visual**: Red color, ⚠️ icon, "OVERDUE" label
    - **Click**: Opens resolution modal
    - **Actions**: [Complete Now], [Request Extension], [Contact Organizer]

### Event-Driven Navigation

16. **On Milestone Completed** (via other screens)
    - **Entry**: Returns to timeline from submission screen
    - **Update**: Timeline refreshes, milestone marked complete
    - **Feedback**: Success toast, visual update (✓ icon)
    - **Progress**: Next milestone highlighted

17. **On Deadline Approaching** (< 7 days)
    - **Notification**: Push notification or email
    - **Entry**: Link opens timeline, scrolls to relevant milestone
    - **Highlight**: Milestone pulsing or highlighted

18. **On Tech Check Scheduled**
    - **Update**: Calendar event added to timeline
    - **Feedback**: "Tech check scheduled for May 10, 14:00" toast
    - **Milestone**: Tech check milestone marked in progress

### Error & Support

19. **On Overdue Item - Extension Needed**
    - **Action**: [Request Extension] button
    - **Target**: Extension request form modal
    - **Payload**: Reason, requested new deadline
    - **Submit**: Sends request to organizer

20. **[Contact Organizer]** (various locations)
    - **Target**: Communication Hub (Story 7.3) or message composer
    - **Type**: Modal or navigation
    - **Context**: Pre-filled with event and context info

21. **On Network Error**
    - **No Navigation**: Remains on screen
    - **Feedback**: "Unable to load timeline" message
    - **Action**: [Retry] button

### Mobile-Specific

22. **Mobile Timeline View**
    - **Layout**: Vertical timeline (not horizontal)
    - **Gestures**: Scroll to navigate, tap to expand
    - **Simplified**: Fewer visual elements, focus on next actions

---
