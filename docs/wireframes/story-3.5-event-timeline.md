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
│  ┌─── EVENT DAY SCHEDULE ───────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Your Presentation Day: May 15, 2025                                            │ │
│  │                                                                                  │ │
│  │  08:30  Speaker arrival & check-in                                              │ │
│  │  08:45  Tech setup in main hall                                                 │ │
│  │  09:00  Morning session begins                                                  │ │
│  │  09:45  YOUR PRESENTATION (45 min)  ← You are here                             │ │
│  │  10:30  Q&A Session (15 min)                                                    │ │
│  │  10:45  Coffee break & networking                                               │ │
│  │  11:00  Next speaker                                                            │ │
│  │                                                                                  │ │
│  │  [View Full Agenda] [Download Schedule] [Add to Calendar]                       │ │
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
- **Event Day Schedule**: Hour-by-hour breakdown of presentation day
- **Preparation Checklist**: Pre-event and event-day checklists with progress tracking
- **Calendar Integration**: Export timeline to personal calendar

## Functional Requirements Met

- **FR5**: Speaker management with event participation tracking
- Complete visibility into event preparation timeline
- Milestone and deadline tracking
- Event day schedule and logistics
- Preparation checklist with progress indicators

## User Interactions

1. **View Timeline**: See all event milestones from acceptance to event day
2. **Track Progress**: Monitor completion of required tasks
3. **Act on Milestones**: Submit materials, RSVP, schedule appointments
4. **View Schedule**: See event day timeline and personal presentation slot
5. **Export Calendar**: Add events and deadlines to personal calendar
6. **Complete Checklist**: Track preparation tasks

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

1. **GET /api/v1/speakers/{speakerId}/events/{eventId}/timeline**
   - Retrieve complete event timeline data
   - Response includes: all milestones, deadlines, completion status, event day schedule
   - Used for: Populating timeline visualization and milestone cards
   - Aggregated: Single call for complete timeline

2. **GET /api/v1/speakers/{speakerId}/events/{eventId}/milestones**
   - Retrieve all event milestones with status
   - Response includes: milestone type, due date, status, actions, dependencies
   - Used for: "Upcoming Milestones" section
   - Filter: `status=pending|overdue`, `limit=5`

3. **GET /api/v1/events/{eventId}/schedule**
   - Retrieve full event day schedule
   - Response includes: time slots, sessions, speakers, locations
   - Used for: "Event Day Schedule" section
   - Highlight: Speaker's own presentation slot

4. **GET /api/v1/speakers/{speakerId}/events/{eventId}/checklist**
   - Retrieve preparation checklist items
   - Response includes: checklist items, completion status, categories (pre-event, event-day)
   - Used for: "Preparation Checklist" section

5. **GET /api/v1/events/{eventId}/details**
   - Retrieve event details
   - Response includes: event name, date, location, topic, speaker slot time
   - Used for: Header information display

---

## Action APIs

APIs called by user interactions and actions:

### Milestone Actions

1. **GET /api/v1/speakers/{speakerId}/events/{eventId}/milestones/{milestoneId}/feedback**
   - Triggered by: [View Feedback] button
   - Response: Organizer feedback details, revision requests
   - Opens: Feedback modal or navigates to relevant submission screen

2. **POST /api/v1/speakers/{speakerId}/events/{eventId}/milestones/{milestoneId}/submit**
   - Triggered by: [Submit Revision], [Upload Slides] buttons
   - Action: Redirects to appropriate submission interface
   - Navigation: Opens material submission wizard or upload screen

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

10. **GET /api/v1/events/{eventId}/agenda**
    - Triggered by: [View Full Agenda] button
    - Response: Complete event agenda with all sessions
    - Opens: Full agenda page

### Calendar Integration

11. **GET /api/v1/speakers/{speakerId}/events/{eventId}/calendar/export**
    - Triggered by: [Export] or [Add to Calendar] buttons
    - Query params: `format=ical|google|outlook`, `includeDeadlines=true`
    - Response: iCal file or calendar service redirect
    - Downloads: .ics file with all milestones and event day schedule

12. **GET /api/v1/events/{eventId}/schedule/download**
    - Triggered by: [Download Schedule] button
    - Response: PDF or iCal file of event day schedule
    - Downloads: Event schedule document

### Checklist Management

13. **PUT /api/v1/speakers/{speakerId}/events/{eventId}/checklist/{itemId}**
    - Triggered by: Checking/unchecking checklist item
    - Payload: `{ completed: true }`
    - Response: Updated checklist item status
    - Updates: Checklist progress, completion percentage

14. **POST /api/v1/speakers/{speakerId}/events/{eventId}/checklist/reset**
    - Triggered by: Optional "Reset Checklist" action
    - Response: All items reset to incomplete
    - Used for: Testing or re-preparation

### Questions & Support

15. **GET /api/v1/speakers/{speakerId}/events/{eventId}/attendee-questions**
    - Triggered by: "Review attendee questions" checklist item
    - Response: Pre-submitted questions from attendees (if feature enabled)
    - Opens: Questions review interface

16. **POST /api/v1/speakers/{speakerId}/events/{eventId}/support-request**
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

### Schedule Navigation

11. **[View Full Agenda]**
    - **Target**: Complete event agenda page
    - **Type**: Full page or modal
    - **Content**: All sessions, speakers, times, locations
    - **Features**: Filter by track, search sessions, speaker bios

12. **[Download Schedule]**
    - **Action**: Downloads event schedule PDF or iCal
    - **No Navigation**: Remains on screen
    - **Feedback**: File download initiated

13. **[Add to Calendar]**
    - **Action**: Exports to calendar service
    - **Options**: Google Calendar, Outlook, Apple Calendar, iCal file
    - **Flow**: Select service → Redirect or download

### Checklist Navigation

14. **Checklist Item Click**
    - **Behavior**: Toggle checked/unchecked
    - **No Navigation**: Remains on screen
    - **Updates**: Progress percentage, completion status
    - **Auto-save**: Checkbox state persisted immediately

15. **"Review attendee questions" Item**
    - **If Clicked**: Opens attendee questions interface
    - **Target**: Questions review page or modal
    - **Type**: Modal or dedicated page
    - **Content**: Pre-submitted questions from registered attendees

### Event Day Navigation

16. **"Your Presentation" Slot Click** (in event day schedule)
    - **Target**: Presentation details modal
    - **Type**: Modal overlay
    - **Content**: Slot details, location, equipment, organizer contact
    - **Actions**: [View Location Map], [Contact Organizer]

17. **[View Location Map]**
    - **Target**: Venue map or Google Maps link
    - **Type**: New tab or inline map widget
    - **Content**: Venue layout, presentation room, facilities

### Timeline Interaction

18. **Timeline Milestone Click**
    - **Target**: Milestone details modal
    - **Type**: Modal overlay
    - **Content**: Milestone description, requirements, actions
    - **Close**: Returns to timeline view

19. **Overdue Milestone**
    - **Visual**: Red color, ⚠️ icon, "OVERDUE" label
    - **Click**: Opens resolution modal
    - **Actions**: [Complete Now], [Request Extension], [Contact Organizer]

### Event-Driven Navigation

20. **On Milestone Completed** (via other screens)
    - **Entry**: Returns to timeline from submission screen
    - **Update**: Timeline refreshes, milestone marked complete
    - **Feedback**: Success toast, visual update (✓ icon)
    - **Progress**: Next milestone highlighted

21. **On Deadline Approaching** (< 7 days)
    - **Notification**: Push notification or email
    - **Entry**: Link opens timeline, scrolls to relevant milestone
    - **Highlight**: Milestone pulsing or highlighted

22. **On Tech Check Scheduled**
    - **Update**: Calendar event added to timeline
    - **Feedback**: "Tech check scheduled for May 10, 14:00" toast
    - **Milestone**: Tech check milestone marked in progress

23. **On Event Day - 1 Day Before**
    - **Highlight**: Event day schedule becomes prominent
    - **Checklist**: Event-day items highlighted
    - **Reminder**: "Your presentation is tomorrow at 09:45" banner

24. **On Event Day**
    - **Mode**: "Event Day Mode" - simplified view
    - **Focus**: Event day schedule and logistics only
    - **Features**: Real-time updates, location info, organizer contact

### Error & Support

25. **On Overdue Item - Extension Needed**
    - **Action**: [Request Extension] button
    - **Target**: Extension request form modal
    - **Payload**: Reason, requested new deadline
    - **Submit**: Sends request to organizer

26. **[Contact Organizer]** (various locations)
    - **Target**: Communication Hub (Story 7.3) or message composer
    - **Type**: Modal or navigation
    - **Context**: Pre-filled with event and context info

27. **On Network Error**
    - **No Navigation**: Remains on screen
    - **Feedback**: "Unable to load timeline" message
    - **Action**: [Retry] button

### Mobile-Specific

28. **Mobile Timeline View**
    - **Layout**: Vertical timeline (not horizontal)
    - **Gestures**: Scroll to navigate, tap to expand
    - **Simplified**: Fewer visual elements, focus on next actions

29. **Mobile Event Day View**
    - **Mode**: Single column layout
    - **Quick Actions**: Call venue, get directions, view slides
    - **Sticky**: Current time indicator

---
