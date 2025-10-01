# Story 1.16: 16-Step Workflow Visualization - Wireframe

**Story**: Epic 1, Story 1.16 - Event Management Service
**Screen**: 16-Step Workflow Visualization (Event Detail View)
**User Role**: Organizer
**Related FR**: FR2 (16-Step Workflow Management), FR20 (Intelligent Notifications)

---

## 16-Step Workflow Visualization (Event Detail View)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Dashboard        Spring Conference 2025 - Workflow Manager                 │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  Event: Cloud Native Architecture        Status: Active       Progress: 7/16 (44%)   │
│  Date: May 15, 2025                     Deadline: March 15                           │
│                                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐ │
│  │                         16-STEP EVENT WORKFLOW                                   │ │
│  │                                                                                   │ │
│  │   Planning Phase          Speaker Phase           Publishing Phase    Final Phase│ │
│  │   ═══════════            ════════════            ═══════════════    ═══════════ │ │
│  │                                                                                   │ │
│  │   ┌───┐    ┌───┐    ┌───┐    ┌───┐    ┌───┐    ┌───┐    ┌───┐    ┌───┐        │ │
│  │   │ ✓ │───▶│ ✓ │───▶│ ✓ │───▶│ ✓ │───▶│ ✓ │───▶│ ✓ │───▶│ ● │───▶│   │ ...    │ │
│  │   └───┘    └───┘    └───┘    └───┘    └───┘    └───┘    └───┘    └───┘        │ │
│  │    1        2        3        4        5        6        7        8             │ │
│  │   Topic   Speaker  Assign  Outreach Status  Collect  Review  Threshold         │ │
│  │   Select  Research Contact           Track   Content  Quality  Check            │ │
│  │   ✓ Done  ✓ Done   ✓ Done  ✓ Done  ✓ Done  ✓ Done   ● Active  ○ Pending      │ │
│  │   Jan 5   Jan 12   Jan 15  Jan 20  Ongoing Feb 10   NOW      Mar 1            │ │
│  │                                                                                   │ │
│  │   ┌───┐    ┌───┐    ┌───┐    ┌───┐    ┌───┐    ┌───┐    ┌───┐    ┌───┐        │ │
│  │   │   │───▶│   │───▶│   │───▶│   │───▶│   │───▶│   │───▶│   │───▶│   │        │ │
│  │   └───┘    └───┘    └───┘    └───┘    └───┘    └───┘    └───┘    └───┘        │ │
│  │    9        10       11       12       13       14       15       16            │ │
│  │   Select   Assign   Publish  Finalize News    Assign   Catering Partner        │ │
│  │   Speaker  Slots    Progress  Agenda   letter  Moder.   & Venue  Meeting       │ │
│  │   ○ Wait   ○ Wait   ○ Wait   ○ Apr 1  ○ Apr 5 ○ Apr 10 ○ Done   ○ May 20      │ │
│  │                                                          2023                    │ │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── STEP 7: CONTENT QUALITY REVIEW (CURRENT) ────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │  Status: IN PROGRESS                    Started: Feb 10    Due: Feb 20           │ │
│  │  Owner: Anna M.                         Progress: 60%                            │ │
│  │                                                                                   │ │
│  │  Tasks:                                                                          │ │
│  │  ☑ Review abstract length (8/8 complete)                                        │ │
│  │  ☑ Verify lessons learned included (8/8 complete)                               │ │
│  │  ☐ Technical accuracy check (5/8 complete)                                      │ │
│  │  ☐ Final approval from moderator (0/8 complete)                                 │ │
│  │                                                                                   │ │
│  │  Blockers:                                                                       │ │
│  │  ⚠️ 3 abstracts need revision - awaiting speaker response                       │ │
│  │                                                                                   │ │
│  │  [View Details] [Reassign] [Mark Complete] [Skip Step] [Get Help]              │ │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── AUTOMATION STATUS ────────────────┬─── DEPENDENCIES ─────────────────────────┐ │
│  │                                       │                                          │ │
│  │  🤖 Auto-reminders: Active            │  Step 8 blocked by: Step 7 completion   │ │
│  │  📧 Email sequences: 3 sent           │  Step 11 requires: Min. 6 speakers      │ │
│  │  📊 Progress tracking: Real-time      │  Step 12 depends on: Venue confirmation │ │
│  │  🔄 Workflow rules: 12 active         │                                          │ │
│  │                                       │  [View Dependency Graph →]               │ │
│  └───────────────────────────────────────┴──────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Workflow Steps**: Click any step to view detailed status and tasks
- **Progress Indicators**: Visual states (✓ Done, ● Active, ○ Pending, ⚠️ Blocked, 🔴 Overdue)
- **Step Actions**: Inline buttons for common operations on current step
- **Automation Panel**: View and configure automated workflows
- **Dependency Graph**: Visual representation of step dependencies
- **Phase Grouping**: Steps organized by logical phases

## Workflow States

- **✓ Done**: Completed steps (green)
- **● Active**: Current step (blue, pulsing)
- **○ Pending**: Future steps (gray)
- **⚠️ Blocked**: Steps with issues (orange)
- **🔴 Overdue**: Past deadline (red)

## Functional Requirements Met

- **FR2**: Complete 16-step workflow visualization with progress tracking
- **FR20**: Automated notifications and reminders integrated into workflow
- **Dependency Management**: Clear visualization of step dependencies
- **Team Collaboration**: Owner assignment and task distribution
- **Workflow Rules**: Configurable automation rules

## User Interactions

1. **Click Step**: Drill down into detailed view of any workflow step
2. **Reassign Tasks**: Change step ownership
3. **Mark Complete**: Manually complete steps when ready
4. **Skip Step**: Override workflow with proper authorization
5. **View Dependency Graph**: Understand step relationships
6. **Configure Automation**: Set up rules and notifications

## Technical Notes

- Real-time progress updates via WebSocket
- Workflow state machine enforces dependencies
- Audit trail tracks all state changes
- Configurable workflow templates per event type
- Role-based permissions for step actions

---

## API Requirements

### Initial Page Load APIs

When the 16-Step Workflow Visualization screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/events/{eventId}**
   - Returns: Event basic info (title, date, status, venue, deadline)
   - Used for: Populate event header information

2. **GET /api/v1/events/{eventId}/workflow**
   - Returns: Complete 16-step workflow state with step details, completion status, owners, dates, blockers, dependencies
   - Used for: Render workflow visualization with all step statuses

3. **GET /api/v1/events/{eventId}/workflow/steps/{stepNumber}**
   - Query params: includeTaskDetails (true)
   - Returns: Detailed step information with task list, completion status, owner info, blockers, progress percentage
   - Used for: Display current step details panel (Step 7 in example)

4. **GET /api/v1/events/{eventId}/workflow/dependencies**
   - Returns: Step dependency map with blocking relationships, required conditions, warnings
   - Used for: Populate dependencies section and validate workflow progression

5. **GET /api/v1/events/{eventId}/workflow/automation**
   - Returns: Automation status (auto-reminders, email sequences, progress tracking, active workflow rules)
   - Used for: Display automation status panel

6. **GET /api/v1/events/{eventId}/team/assignments**
   - Returns: Team member assignments with availability, current workload, roles
   - Used for: Display team info and enable reassignment options

7. **GET /api/v1/events/{eventId}/workflow/history**
   - Query params: limit (20)
   - Returns: Audit trail of workflow changes with timestamps, users, actions taken
   - Used for: Track workflow progress history

8. **WebSocket /ws/events/{eventId}/workflow**
   - Real-time updates: Step completion, task updates, blocker changes, team assignments, automation triggers
   - Used for: Live workflow progress updates

---

## Action APIs

### Workflow Step Management

1. **PUT /api/v1/events/{eventId}/workflow/steps/{stepNumber}/complete**
   - Payload: `{ completedBy, completionNotes, overrideWarnings: boolean }`
   - Response: Updated workflow state, next step activated, automation triggered
   - Used for: Mark current step as complete from [Mark Complete] button

2. **PUT /api/v1/events/{eventId}/workflow/steps/{stepNumber}/reassign**
   - Payload: `{ newOwnerId, reassignReason, notifyPreviousOwner: boolean }`
   - Response: Updated assignment, notification sent, updated workflow state
   - Used for: Reassign step ownership from [Reassign] button

3. **PUT /api/v1/events/{eventId}/workflow/steps/{stepNumber}/skip**
   - Payload: `{ skipReason, authorizationCode, skipType: "temporary|permanent" }`
   - Response: Step skipped confirmation, workflow advanced, audit log entry
   - Used for: Skip workflow step with authorization from [Skip Step] button

4. **PUT /api/v1/events/{eventId}/workflow/steps/{stepNumber}/reopen**
   - Payload: `{ reopenReason, affectedSteps: [] }`
   - Response: Step reopened, dependent steps updated, notifications sent
   - Used for: Reopen completed step if issues found

5. **POST /api/v1/events/{eventId}/workflow/steps/{stepNumber}/blocker**
   - Payload: `{ blockerType, description, severity: "low|medium|high", estimatedResolution }`
   - Response: Blocker created, notifications sent, workflow status updated
   - Used for: Report blocker preventing step completion

6. **DELETE /api/v1/events/{eventId}/workflow/steps/{stepNumber}/blocker/{blockerId}**
   - Payload: `{ resolutionNotes }`
   - Response: Blocker removed, workflow unblocked, notifications sent
   - Used for: Remove resolved blocker

### Task Management Within Steps

7. **PUT /api/v1/events/{eventId}/workflow/steps/{stepNumber}/tasks/{taskId}/complete**
   - Payload: `{ completedBy, notes }`
   - Response: Task marked complete, step progress updated
   - Used for: Complete individual task within step (e.g., "Review abstract length")

8. **POST /api/v1/events/{eventId}/workflow/steps/{stepNumber}/tasks**
   - Payload: `{ taskTitle, description, assigneeId, dueDate }`
   - Response: Task created, notifications sent
   - Used for: Add custom task to workflow step

9. **DELETE /api/v1/events/{eventId}/workflow/steps/{stepNumber}/tasks/{taskId}**
   - Payload: `{ deletionReason }`
   - Response: Task deleted, step progress recalculated
   - Used for: Remove task from step

### Automation Management

10. **GET /api/v1/events/{eventId}/workflow/automation/rules**
    - Returns: All automation rules with conditions, actions, status
    - Used for: View automation configuration

11. **PUT /api/v1/events/{eventId}/workflow/automation/rules/{ruleId}/toggle**
    - Payload: `{ enabled: boolean }`
    - Response: Rule status updated
    - Used for: Enable/disable automation rules

12. **POST /api/v1/events/{eventId}/workflow/automation/rules**
    - Payload: `{ trigger, conditions: [], actions: [], schedule }`
    - Response: Rule created, activated
    - Used for: Create new automation rule

13. **PUT /api/v1/events/{eventId}/workflow/automation/reminders**
    - Payload: `{ stepNumber, reminderSchedule: [], recipients: [] }`
    - Response: Reminder configuration updated
    - Used for: Configure auto-reminders for step

### Dependency Management

14. **GET /api/v1/events/{eventId}/workflow/dependencies/graph**
    - Query params: format (json|svg|png)
    - Returns: Dependency graph data or visualization
    - Used for: Generate and display dependency graph

15. **PUT /api/v1/events/{eventId}/workflow/dependencies**
    - Payload: `{ stepNumber, dependsOn: [], conditions: [] }`
    - Response: Dependencies updated, workflow validated
    - Used for: Update step dependencies

16. **POST /api/v1/events/{eventId}/workflow/dependencies/validate**
    - Returns: Validation results with warnings, errors, suggestions
    - Used for: Validate workflow before advancing steps

### Workflow Help & Support

17. **GET /api/v1/workflow/help/step/{stepNumber}**
    - Returns: Help documentation for specific step, best practices, common issues, tips
    - Used for: Display context-sensitive help from [Get Help] button

18. **POST /api/v1/events/{eventId}/workflow/support-request**
    - Payload: `{ stepNumber, issueDescription, urgency: "low|medium|high" }`
    - Response: Support ticket created, estimated response time
    - Used for: Request help from support team

### History & Audit

19. **GET /api/v1/events/{eventId}/workflow/audit-trail**
    - Query params: stepNumber (optional), userId (optional), dateRange, limit (50)
    - Returns: Detailed audit trail with all workflow changes, timestamps, users, actions
    - Used for: View complete workflow history

20. **GET /api/v1/events/{eventId}/workflow/export**
    - Query params: format (pdf|csv|json), includeHistory (true)
    - Returns: Download URL for workflow export
    - Used for: Export workflow state and history

---

## Navigation Map

### Primary Navigation Actions

1. **← Back to Dashboard button** → Navigate to `Event Management Dashboard` (story-1.16-event-management-dashboard.md)
   - Returns to main dashboard
   - Shows all active events
   - Preserves workflow state

2. **Event title (Spring Conference 2025)** → Navigate to `Event Detail/Edit Screen`
   - Full event information
   - Edit event details
   - Team management
   - Timeline view

3. **Workflow step click (Step 1-16)** → Expands step detail panel
   - Shows step details in bottom section
   - Displays tasks and progress
   - Reveals blockers
   - Shows action buttons
   - No screen navigation

4. **Current step indicator (● Active)** → Automatically expanded
   - Step 7 in example
   - Already showing details
   - Focus on current work
   - No additional action needed

5. **Completed step indicator (✓ Done)** → Click to view historical details
   - Shows completion date
   - Displays who completed it
   - Shows completion notes
   - Review mode (read-only)
   - No screen navigation

6. **Pending step indicator (○ Pending)** → Click to view upcoming step
   - Shows dependencies
   - Displays prerequisites
   - Shows estimated start date
   - Preview mode
   - No screen navigation

7. **Blocked step indicator (⚠️ Blocked)** → Click to view blocker details
   - Shows blocking issues
   - Displays resolution actions
   - Shows affected timeline
   - Resolution options
   - No screen navigation

8. **Team member name click (Anna M.)** → Navigate to `User Profile Screen`
   - User details
   - Current assignments
   - Availability calendar
   - Contact options

### Step Action Buttons

9. **[View Details] button** → Expands full step details
   - Shows all tasks
   - Displays full description
   - Shows related documents
   - Reveals additional actions
   - No screen navigation

10. **[Reassign] button** → Opens reassignment modal
    - Team member selector
    - Load balancing info
    - Reason input field
    - Notification options
    - Submits via API
    - No screen navigation

11. **[Mark Complete] button** → Opens completion confirmation dialog
    - Completion checklist
    - Notes input
    - Override warnings toggle
    - Submits completion
    - Advances workflow
    - Updates visualization
    - No screen navigation

12. **[Skip Step] button** → Opens authorization dialog
    - Requires authorization code
    - Skip reason required
    - Impact warning shown
    - Permanent/temporary option
    - Audit trail entry
    - No screen navigation

13. **[Get Help] button** → Opens help modal
    - Step-specific documentation
    - Best practices
    - Common issues
    - Support request option
    - Video tutorials
    - No screen navigation

### Task Interactions

14. **Task checkbox (☑/☐)** → Toggles task completion
    - Marks task complete/incomplete
    - Updates step progress
    - Saves via API
    - Updates percentage
    - No screen navigation

15. **Task text click** → Shows task details tooltip
    - Task description
    - Assigned person
    - Due date
    - Related resources
    - No navigation

### Automation Panel Actions

16. **Auto-reminders status** → Click to configure
    - Opens reminder settings
    - Schedule configuration
    - Recipient selection
    - Template selection
    - No screen navigation

17. **Email sequences count** → Click to view details
    - Lists sent emails
    - Shows recipients
    - Displays open/click rates
    - Template preview
    - No screen navigation

18. **Workflow rules count** → Click to manage rules
    - Opens rules configuration
    - Enable/disable rules
    - Create new rules
    - Edit existing rules
    - No screen navigation

### Dependencies Panel Actions

19. **Dependency line click** → Highlights related steps
    - Shows blocking relationship
    - Displays condition
    - Shows impact
    - Visual highlight
    - No screen navigation

20. **[View Dependency Graph →] button** → Opens dependency graph modal
    - Full-screen graph view
    - Interactive visualization
    - Zoom and pan
    - Highlight paths
    - Export options
    - No permanent screen navigation (modal)

### Phase Grouping Interactions

21. **Phase header click (Planning Phase)** → Collapses/expands phase
    - Shows/hides phase steps
    - Accordion behavior
    - Quick navigation
    - No screen navigation

22. **Phase progress indicator** → Shows phase summary
    - Completed steps count
    - Phase duration
    - Phase status
    - Tooltip display
    - No navigation

### Event-Driven Navigation

23. **Step completed (WebSocket)** → Updates workflow visualization
    - Moves progress indicator
    - Updates step status icon
    - Activates next step
    - Updates progress percentage
    - May show celebration animation
    - No automatic navigation

24. **Blocker added (WebSocket)** → Updates step status
    - Changes step icon to ⚠️
    - Shows blocker notification
    - Updates dependencies panel
    - Highlights affected steps
    - No automatic navigation

25. **Task completed by team member (WebSocket)** → Updates task list
    - Checks task checkbox
    - Updates step progress
    - Shows notification
    - Updates percentage
    - No automatic navigation

26. **Deadline approaching** → Shows warning indicator
    - Changes step color
    - Shows countdown timer
    - May trigger notification
    - Updates urgency indicator
    - No automatic navigation

27. **Step overdue** → Shows overdue indicator
    - Changes step icon to 🔴
    - Shows overdue warning
    - Triggers escalation notification
    - Updates dashboard alerts
    - No automatic navigation

28. **Reassignment completed** → Updates step owner
    - Changes owner name
    - Shows reassignment note
    - Sends notifications
    - Updates team panel
    - No automatic navigation

29. **Automation rule triggered** → Shows automation activity
    - Updates automation panel
    - Shows triggered action
    - May show notification
    - Updates affected steps
    - No automatic navigation

30. **Dependency satisfied** → Unblocks dependent step
    - Removes blocker warning
    - Updates dependency panel
    - Changes step status
    - Enables step actions
    - No automatic navigation

---