# Story 3.2: Invitation Response Interface - Wireframe

**Story**: Epic 3, Story 3.2 - Speaker Management Service
**Screen**: Invitation Response Interface (From Email Link)
**User Role**: Speaker
**Related FR**: FR3 (Speaker Self-Service)

---

## Invitation Response Interface (From Email Link)

From wireframes-speaker.md Section 2:

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ BATbern Event Platform                                          [Login] [Register]   │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐ │
│  │                     You're Invited to Speak at BATbern!                          │ │
│  │                          Autumn Conference 2025                                   │ │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── EVENT DETAILS ─────────────────────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  🎯 Topic Request:  Cloud Security Trends & Best Practices                     │  │
│  │  📅 Date:          November 20, 2025                                          │  │
│  │  📍 Location:      Tech Park, Zurich                                          │  │
│  │  ⏱️ Format:        45-minute presentation + 15-min Q&A                        │  │
│  │  👥 Expected:      200+ IT professionals                                      │  │
│  │  🎟️ Your Role:     Main track speaker, afternoon session                     │  │
│  │                                                                                 │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── WHY WE CHOSE YOU ──────────────────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  "Your expertise in cloud security architecture and your recent work on        │  │
│  │  zero-trust implementations makes you the perfect speaker for this topic.      │  │
│  │  Your previous talk on Kubernetes security was highly rated (4.8/5) and        │  │
│  │  we believe our audience would greatly benefit from your insights."            │  │
│  │                                                                                 │  │
│  │  - Sally Organizer, Event Lead                                                 │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── YOUR RESPONSE ──────────────────────────────────────────────────────────────┐ │
│  │                                                                                 │  │
│  │  Please respond by: March 15, 2025 (10 days remaining)                        │  │
│  │                                                                                 │  │
│  │  ┌───────────────┐  ┌───────────────┐  ┌────────────────────┐                 │  │
│  │  │               │  │               │  │                    │                 │  │
│  │  │   ✓ ACCEPT    │  │   ✗ DECLINE   │  │   ? NEED MORE INFO │                 │  │
│  │  │               │  │               │  │                    │                 │  │
│  │  │ I'm excited   │  │ Unfortunately │  │  I'm interested    │                 │  │
│  │  │ to speak!     │  │ not available │  │  but need details  │                 │  │
│  │  │               │  │               │  │                    │                 │  │
│  │  └───────────────┘  └───────────────┘  └────────────────────┘                 │  │
│  │                                                                                 │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── IF ACCEPTING - QUICK PREFERENCES ──────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  Preferred Time Slot:                Technical Requirements:                  │  │
│  │  ○ Morning (09:00-12:00)            ☐ Mac adapter needed                      │  │
│  │  ● Afternoon (13:00-17:00)          ☐ Remote presentation option              │  │
│  │  ○ No preference                    ☐ Special audio/video needs               │  │
│  │                                                                                 │  │
│  │  Travel Requirements:                Initial Presentation Title:               │  │
│  │  ○ Local (no accommodation)         ┌────────────────────────────────┐        │  │
│  │  ○ Need accommodation                │ Zero-Trust Security in Cloud   │        │  │
│  │  ○ Virtual participation            │ Native Environments            │        │  │
│  │                                     └────────────────────────────────┘        │  │
│  │                                                                                 │  │
│  │  Comments for Organizer:                                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐          │  │
│  │  │ Happy to speak! Can we discuss covering specific compliance     │          │  │
│  │  │ requirements for Swiss financial sector?                        │          │  │
│  │  └─────────────────────────────────────────────────────────────────┘          │  │
│  │                                                                                 │  │
│  │                              [Submit Response →]                               │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── WHAT HAPPENS NEXT ──────────────────────────────────────────────────────────┐ │
│  │  1. Submit your response (you can update it later)                            │  │
│  │  2. We'll confirm your slot within 48 hours                                   │  │
│  │  3. You'll receive a speaker portal account                                   │  │
│  │  4. Submit materials at your convenience (deadline: Oct 20)                   │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Three-button response**: Accept, Decline, Need More Info
- **Conditional form**: Preferences shown only when accepting
- **Inline preferences**: Capture time, travel, technical needs upfront
- **Comments field**: Open communication with organizer
- **Process timeline**: Clear expectations of next steps

## Functional Requirements Met

- **FR3**: Self-service speaker invitation response
- **Quick Response**: One-click decision with optional details
- **Preferences Capture**: Technical and scheduling needs collected early
- **Communication**: Direct messaging to organizer

## User Interactions

1. **Select Response**: Click Accept, Decline, or Need More Info
2. **Set Preferences**: Choose time slot, specify requirements
3. **Add Title**: Provide initial presentation title
4. **Add Comments**: Communicate specific needs or questions
5. **Submit**: Send response to organizer

## Technical Notes

- Token-based link from email (no login required)
- Auto-save preferences as entered
- Creates speaker portal account upon acceptance
- Triggers organizer notification upon response
- Mobile-responsive design for on-the-go responses

---

## API Requirements

APIs needed to load and display data for this screen:

### Initial Page Load (Token-Based)

1. **GET /api/v1/invitations/token/{invitationToken}**
   - Retrieve invitation details using secure token from email link
   - Response includes: event details, session info, invitation message, response deadline
   - Used for: Populating all event details, "Why We Chose You" section
   - Security: Single-use or time-limited token validation

2. **GET /api/v1/speakers/{speakerId}/invitation-history**
   - Retrieve speaker's past speaking history (for context)
   - Query params: `invitationToken` (for anonymous access)
   - Response includes: previous event ratings, topics covered
   - Used for: Building speaker confidence, showing track record
   - Optional: May not load if speaker is new

---

## Action APIs

APIs called by user interactions and actions:

### Response Submission

1. **POST /api/v1/invitations/{invitationId}/respond**
   - Triggered by: [Submit Response →] button
   - Payload:
     ```json
     {
       "response": "accepted|declined|need_more_info",
       "preferences": {
         "timeSlot": "morning|afternoon|no_preference",
         "travelRequirements": "local|accommodation|virtual",
         "technicalRequirements": ["mac_adapter", "remote_option", "special_av"],
         "presentationTitle": "string",
         "comments": "string"
       }
     }
     ```
   - Response: Updated invitation status, confirmation details
   - Side effects:
     - If accepted: Creates speaker portal account, sends confirmation email
     - If declined: Updates speaker pipeline status, sends organizer notification
     - If need_more_info: Triggers organizer alert, opens communication channel

2. **POST /api/v1/speakers/register-from-invitation**
   - Triggered by: Accepting invitation (auto-called after response)
   - Payload: `{ invitationToken, email, basicProfile }`
   - Response: Speaker account created, authentication token
   - Side effect: Sends welcome email with portal access credentials

### Auto-Save (Optional)

3. **PUT /api/v1/invitations/{invitationId}/draft-preferences**
   - Triggered by: Field changes (debounced, every 2 seconds)
   - Payload: Partial preferences object
   - Response: Draft saved confirmation
   - Used for: Preventing data loss if user navigates away

### Additional Actions

4. **POST /api/v1/invitations/{invitationId}/request-info**
   - Triggered by: [? NEED MORE INFO] button
   - Payload: `{ questions: "string", specificConcerns: [] }`
   - Response: Info request sent to organizer
   - Side effect: Creates task for organizer to respond

5. **GET /api/v1/events/{eventId}/public-details**
   - Triggered by: Optional "Learn more about this event" link
   - Response: Public event information, past event examples
   - Opens: Event details modal or link to public page

---

## Navigation Map

Screen transitions triggered by actions and events:

### Primary Navigation

1. **[Login] Header Button**
   - **Target**: Login page
   - **Context**: If speaker already has account, can login to full portal
   - **Type**: Standard login flow

2. **[Register] Header Button**
   - **Target**: Speaker registration page
   - **Context**: Create account before responding to invitation
   - **Type**: Standard registration flow

3. **On Response Submitted - ACCEPTED**
   - **Target**: Speaker Dashboard (Story 3.3)
   - **Type**: Automatic redirect after 3-second success message
   - **Context**: Account auto-created, user logged in automatically
   - **Welcome Flow**: Show onboarding tour on first login

4. **On Response Submitted - DECLINED**
   - **Target**: Thank You page
   - **Type**: Static page
   - **Context**: "Thank you for considering. We hope to work with you in the future."
   - **Options**:
     - Link to join mailing list for future events
     - Button to "Update to Maybe/Interested" (changes response to need_more_info)

5. **On Response Submitted - NEED MORE INFO**
   - **Target**: Communication Interface (inline or modal)
   - **Type**: Modal overlay
   - **Context**: Open message thread with organizer
   - **Follow-up**: Option to later accept/decline after getting info

### Secondary Navigation

6. **"What Happens Next" - Step 3 Link**
   - **Target**: Speaker Portal info page
   - **Type**: Informational modal or external page
   - **Context**: Preview what the speaker portal offers

7. **"Previous Talk Rating" Link** (in "Why We Chose You")
   - **Target**: Past Event Details page
   - **Type**: Modal or new tab
   - **Context**: Show speaker their previous presentation metrics

8. **Event Details - Location Link**
   - **Target**: Venue information page or Google Maps
   - **Type**: External link
   - **Context**: Help speaker plan travel/accommodation

### Event-Driven Navigation

9. **On Token Expired**
   - **Target**: Error page with contact form
   - **Feedback**: "This invitation link has expired. Please contact us."
   - **Action**: Provide email/contact form to request new invitation

10. **On Token Invalid**
    - **Target**: 404 or Error page
    - **Feedback**: "Invalid invitation link"
    - **Action**: Redirect to homepage or contact support

11. **On Already Responded**
    - **Target**: Response confirmation page
    - **Feedback**: "You already responded to this invitation on [date]"
    - **Action**: Show previous response with option to update
    - **Navigation**: Button to "Update My Response" or "Go to Speaker Dashboard"

### Auto-Save & Recovery

12. **On Page Reload with Unsaved Draft**
    - **No Navigation**: Remains on same page
    - **Feedback**: "We've saved your progress" banner
    - **Action**: Restore draft preferences from auto-save

13. **On Submit Error**
    - **No Navigation**: Remains on same page
    - **Feedback**: Error message with specific issue
    - **Action**: Allow user to correct and resubmit

### Mobile-Specific

14. **On Small Screen - Response Buttons**
    - **No Navigation**: Inline form expansion
    - **Context**: Preferences section slides in below button
    - **UX**: Smooth scroll to expanded preferences

### Post-Acceptance Flow

15. **After Acceptance - "Complete Your Profile" Prompt**
    - **Target**: Speaker Profile Management (Story 7.1)
    - **Type**: Optional next step
    - **Context**: Encourage speaker to fill complete profile
    - **Timing**: Can be delayed with "Do This Later" option