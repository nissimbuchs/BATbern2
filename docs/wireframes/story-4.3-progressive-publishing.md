# Story 4.3: Progressive Publishing - Wireframe

**Story**: Epic 4, Story 4.3 - Enhanced Content & Publishing
**Screen**: Full Progressive Publishing (from Epic 4)
**User Role**: Organizer
**Related FR**: FR19 (Progressive Publishing - Complete), FR6 (Current Event Prominence)

---

## Progressive Publishing Engine - Full Version (FR19)

**Note**: This is the complete version from Epic 4 with advanced features. See story-2.3-basic-publishing-engine.md for the basic version from Epic 2.

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back              Publishing Control Center - Spring Conference                    │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── PUBLISHING TIMELINE ──────────────────────────────────────────────────────┐   │
│  │                                                                               │   │
│  │  Jan 1          Feb 1          Mar 1          Apr 1          May 15          │   │
│  │  ──┬─────────────┬──────────────┬──────────────┬──────────────┬──           │   │
│  │    ↓             ↓              ↓              ↓              ↓              │   │
│  │  Topic ✓    Speakers ✓    Agenda Draft    Final Agenda    Event Day         │   │
│  │  Published   Published      Mar 15          May 1                            │   │
│  │                                                                               │   │
│  │  Current Phase: SPEAKERS PUBLISHED - Next: Agenda Draft (12 days)            │   │
│  └───────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                       │
│  ┌─── CONTENT VALIDATION DASHBOARD ─────────────────────────────────────────────┐   │
│  │                                                                               │   │
│  │  Publishing Readiness: 73%  ████████████████████░░░░░░░                      │   │
│  │                                                                               │   │
│  │  ┌─── Required Items ──────────────────────────────────────────────────┐     │   │
│  │  │                                                                      │     │   │
│  │  │  Component              Status    Validation               Action   │     │   │
│  │  │  ─────────────────────────────────────────────────────────────────  │     │   │
│  │  │  ✓ Event Title          Ready     Passed all checks        [Edit]   │     │   │
│  │  │  ✓ Date & Venue         Ready     Venue confirmed          [View]   │     │   │
│  │  │  ✓ Topic Description    Ready     Within 500 chars         [Edit]   │     │   │
│  │  │  ⚠️ Speaker List        Partial   5/8 confirmed            [Manage] │     │   │
│  │  │  ⚠️ Abstracts           Partial   5/8 validated            [Review] │     │   │
│  │  │    └─ Length Check      Failed    3 exceed 1000 chars      [Fix]    │     │   │
│  │  │    └─ Lessons Learned  Passed    All included             ✓        │     │   │
│  │  │    └─ Quality Review   Pending   3 await moderation       [Go]     │     │   │
│  │  │  ✗ Speaker Photos      Missing   3/8 uploaded             [Upload] │     │   │
│  │  │  ✗ Agenda Times        Not Set   Slots unassigned         [Assign] │     │   │
│  │  │  ✓ Registration Link   Ready     Tested & working         [Test]   │     │   │
│  │  │                                                                      │     │   │
│  │  └──────────────────────────────────────────────────────────────────────┘     │   │
│  └───────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                       │
│  ┌─── LIVE PREVIEW ──────────────────┬─── PUBLISHING CONTROLS ──────────────────┐  │
│  │                                   │                                           │  │
│  │  ┌─────────────────────────┐      │  Publishing Mode:                        │  │
│  │  │ BATbern Spring 2025     │      │  ○ Draft (internal only)                 │  │
│  │  │                         │      │  ● Progressive (public, partial)         │  │
│  │  │ Cloud Native            │      │  ○ Complete (all content)                │  │
│  │  │ Architecture            │      │                                           │  │
│  │  │                         │      │  Auto-publish rules:                     │  │
│  │  │ May 15, 2025           │      │  ☑ Publish when validation >= 80%        │  │
│  │  │ Kursaal Bern           │      │  ☑ Update hourly if changes              │  │
│  │  │                         │      │  ☐ Require manual approval               │  │
│  │  │ Speakers:              │      │                                           │  │
│  │  │ • Sara Kim - Docker    │      │  Version Control:                        │  │
│  │  │ • Peter Muller - K8s   │      │  Current: v3 (Feb 28, 14:30)            │  │
│  │  │ • [3 more confirmed]   │      │  [View History] [Rollback]               │  │
│  │  │ • [3 slots available]  │      │                                           │  │
│  │  │                         │      │  Actions:                                │  │
│  │  │ [Register Now]         │      │  [Publish Now] [Schedule] [Preview]      │  │
│  │  └─────────────────────────┘      │                                           │  │
│  │                                   │  ⚠️ Warning: 3 validation errors         │  │
│  │  [Desktop] [Mobile] [Print]       │     Publishing will show partial content  │  │
│  └───────────────────────────────────┴───────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── ADVANCED FEATURES (Epic 4) ───────────────────────────────────────────────┐   │
│  │                                                                                │   │
│  │  A/B Testing:                             SEO Optimization:                   │   │
│  │  ☑ Test 2 hero images                     ✓ Meta tags optimized               │   │
│  │  ☐ Test CTA button text                   ✓ Schema.org markup                 │   │
│  │  Current winner: Version A (67% CTR)      ⚠️ Missing social preview image     │   │
│  │                                                                                │   │
│  │  Analytics Integration:                   Content Scheduling:                 │   │
│  │  Views: 1,234 (last 7 days)              • Speakers: Published                │   │
│  │  Bounce rate: 23% (⬇ 5%)                 • Full agenda: Mar 15, 09:00        │   │
│  │  Conversion: 18% (⬆ 3%)                  • Final details: May 1, 09:00       │   │
│  │  [View Full Analytics →]                  [Edit Schedule]                     │   │
│  │                                                                                │   │
│  └────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

All features from basic version (story-2.3), plus:

- **A/B Testing**: Test different content variations and track performance
- **SEO Optimization**: Built-in SEO checks and recommendations
- **Analytics Integration**: Real-time traffic and conversion tracking
- **Content Scheduling**: Automated release of content at specific times
- **Social Preview**: Optimize how content appears when shared

## Functional Requirements Met

- **FR19 (Complete)**: Full progressive publishing with advanced features
- **FR6**: Ensures current event prominence with SEO optimization
- **Content Validation**: Enhanced checks including SEO, social, accessibility
- **A/B Testing**: Data-driven content optimization
- **Analytics**: Integrated performance tracking
- **Scheduling**: Time-based automatic content releases

## User Interactions

1. **All Basic Interactions** from story-2.3-basic-publishing-engine.md
2. **Configure A/B Tests**: Set up content variations and track winners
3. **Optimize SEO**: Review and fix SEO issues
4. **Schedule Content**: Set automatic release times for content phases
5. **View Analytics**: Monitor traffic, engagement, and conversion metrics
6. **Preview Social Sharing**: See how content appears on LinkedIn, Twitter

## Technical Notes

- All features from basic version (story-2.3)
- A/B testing engine with statistical significance calculation
- SEO crawler integration for real-time optimization
- Google Analytics / Matomo integration
- Scheduled job system for timed content releases
- Social media preview generation (Open Graph, Twitter Cards)
- Accessibility validation (WCAG compliance)
- Performance monitoring (Core Web Vitals)

---

## API Requirements

APIs needed to load and display data for this screen:

### Initial Page Load

1. **GET /api/v1/events/{eventId}/publishing/dashboard**
   - Retrieve complete publishing dashboard data
   - Response includes: timeline phases, validation status, readiness percentage, warnings
   - Used for: Main dashboard overview and validation dashboard
   - Aggregated: Single call for complete publishing state

2. **GET /api/v1/events/{eventId}/publishing/timeline**
   - Retrieve publishing timeline with phases
   - Response includes: phases, dates, completion status, current phase, next phase
   - Used for: Publishing timeline visualization at top

3. **GET /api/v1/events/{eventId}/publishing/validation**
   - Retrieve detailed validation results for all components
   - Response includes: component status, validation checks, errors, warnings
   - Used for: Content validation dashboard section
   - Detailed: Each component has sub-checks (length, quality, etc.)

4. **GET /api/v1/events/{eventId}/publishing/preview**
   - Retrieve rendered preview of public event page
   - Query params: `version=draft|progressive|complete`, `device=desktop|mobile`
   - Response: HTML preview or rendering data
   - Used for: Live preview panel

5. **GET /api/v1/events/{eventId}/publishing/config**
   - Retrieve publishing configuration and rules
   - Response includes: publishing mode, auto-publish rules, schedule
   - Used for: Publishing controls panel

6. **GET /api/v1/events/{eventId}/publishing/versions**
   - Retrieve version history
   - Response includes: version list with timestamps, authors, changes
   - Used for: Version control section

### Advanced Features (Epic 4)

7. **GET /api/v1/events/{eventId}/ab-tests**
   - Retrieve active A/B tests
   - Response includes: test configurations, variants, performance metrics, winner
   - Used for: A/B testing section

8. **GET /api/v1/events/{eventId}/seo/analysis**
   - Retrieve SEO optimization status
   - Response includes: meta tags, schema markup, issues, recommendations
   - Used for: SEO optimization section

9. **GET /api/v1/events/{eventId}/analytics/summary**
   - Retrieve analytics summary
   - Query params: `period=7d|30d|90d`
   - Response includes: views, bounce rate, conversion rate, trends
   - Used for: Analytics integration section

10. **GET /api/v1/events/{eventId}/publishing/schedule**
    - Retrieve content release schedule
    - Response includes: scheduled phases, release times, auto-publish settings
    - Used for: Content scheduling section

---

## Action APIs

APIs called by user interactions and actions:

### Publishing Control

1. **PUT /api/v1/events/{eventId}/publishing/mode**
   - Triggered by: Publishing mode radio button selection
   - Payload: `{ mode: "draft|progressive|complete" }`
   - Response: Updated publishing mode, visibility changes
   - Side effects:
     - Updates public event page
     - Triggers cache invalidation
     - Sends notifications if moving to public modes

2. **PUT /api/v1/events/{eventId}/publishing/auto-rules**
   - Triggered by: Auto-publish rule checkboxes
   - Payload: `{ publishAtThreshold: boolean, thresholdPercent: number, updateHourly: boolean, requireApproval: boolean }`
   - Response: Updated auto-publish configuration
   - Side effects: Schedules or cancels automated publishing jobs

3. **POST /api/v1/events/{eventId}/publishing/publish**
   - Triggered by: [Publish Now] button
   - Payload: `{ force: boolean, mode: "progressive|complete" }`
   - Response: Publishing result, version created, public URL
   - Validation: Checks validation threshold before publishing
   - Side effects:
     - Creates new version
     - Updates public pages
     - Invalidates CDN cache
     - Sends notification emails (if configured)
     - Triggers analytics tracking

4. **POST /api/v1/events/{eventId}/publishing/schedule**
   - Triggered by: [Schedule] button
   - Opens: Scheduling modal
   - Payload: `{ publishAt: datetime, mode: string }`
   - Response: Scheduled job created
   - Side effect: Creates scheduled publishing job

### Validation & Quality

5. **POST /api/v1/events/{eventId}/publishing/validate**
   - Triggered by: Auto-refresh or manual [Validate] button
   - Response: Updated validation results for all components
   - Processing: Runs all validation checks (length, quality, completeness)
   - Updates: Validation dashboard and readiness percentage

6. **GET /api/v1/events/{eventId}/validation/{componentId}/details**
   - Triggered by: Clicking on component validation row
   - Response: Detailed validation report with specific issues
   - Opens: Validation details modal

7. **POST /api/v1/events/{eventId}/abstracts/{abstractId}/quality-review**
   - Triggered by: [Go] button on quality review pending
   - Opens: Quality review interface
   - Navigation: Moderator review workflow

### Component Management

8. **GET /api/v1/events/{eventId}/speakers**
   - Triggered by: [Manage] button on speaker list
   - Opens: Speaker Matching Interface (Story 3.1)
   - Navigation: Full speaker management screen

9. **GET /api/v1/events/{eventId}/abstracts**
   - Triggered by: [Review] button on abstracts
   - Opens: Abstract review interface
   - Navigation: Content review and moderation screen

10. **POST /api/v1/events/{eventId}/speakers/{speakerId}/photo-request**
    - Triggered by: [Upload] button on speaker photos
    - Action: Sends reminder to speaker or opens upload interface
    - Side effect: Email sent to speaker

11. **PUT /api/v1/events/{eventId}/agenda/slots**
    - Triggered by: [Assign] button on agenda times
    - Opens: Slot assignment interface
    - Navigation: Agenda management screen

### Preview & Testing

12. **GET /api/v1/events/{eventId}/publishing/preview**
    - Triggered by: [Desktop], [Mobile], [Print] buttons
    - Query params: `device=desktop|mobile|print`, `mode=current`
    - Response: Rendered preview HTML
    - Updates: Live preview panel

13. **POST /api/v1/events/{eventId}/publishing/preview/refresh**
    - Triggered by: Auto-refresh or manual refresh
    - Response: Updated preview content
    - Side effect: Re-renders preview with latest data

14. **GET /api/v1/events/{eventId}/publishing/test-registration**
    - Triggered by: [Test] button on registration link
    - Response: Test result, registration flow validation
    - Opens: Test result modal

### Version Control

15. **GET /api/v1/events/{eventId}/publishing/versions/{versionId}**
    - Triggered by: [View History] or clicking version in history
    - Response: Full version details, diff from current
    - Opens: Version comparison modal

16. **POST /api/v1/events/{eventId}/publishing/rollback**
    - Triggered by: [Rollback] button
    - Payload: `{ versionId }`
    - Confirmation: "Rollback to version X?" dialog
    - Response: Rollback result, new version created
    - Side effects:
      - Creates new version from old state
      - Updates public pages
      - Logs rollback action

### A/B Testing (Epic 4)

17. **POST /api/v1/events/{eventId}/ab-tests**
    - Triggered by: Creating new A/B test
    - Payload: `{ name, variants: [], trafficSplit: {}, metric: "ctr|conversion" }`
    - Response: A/B test created, tracking enabled
    - Side effect: Starts serving variants to users

18. **PUT /api/v1/events/{eventId}/ab-tests/{testId}**
    - Triggered by: Toggling A/B test checkboxes
    - Payload: `{ active: boolean }`
    - Response: Test status updated
    - Side effect: Enables/disables variant serving

19. **POST /api/v1/events/{eventId}/ab-tests/{testId}/declare-winner**
    - Triggered by: Manually declaring winner or auto when significant
    - Payload: `{ winningVariant }`
    - Response: Winner set, test concluded
    - Side effect: All users see winning variant

### SEO Optimization (Epic 4)

20. **GET /api/v1/events/{eventId}/seo/recommendations**
    - Triggered by: Viewing SEO section or [Optimize] button
    - Response: SEO recommendations, priority issues
    - Opens: SEO recommendations modal

21. **PUT /api/v1/events/{eventId}/seo/meta-tags**
    - Triggered by: Editing meta tags in SEO optimization
    - Payload: `{ title, description, keywords, ogImage }`
    - Response: Meta tags updated
    - Side effect: Updates public page meta tags

22. **POST /api/v1/events/{eventId}/seo/social-preview/generate**
    - Triggered by: [Generate] button for missing social preview
    - Response: Generated social preview image URL
    - Processing: Creates Open Graph image from event data

23. **POST /api/v1/events/{eventId}/seo/validate**
    - Triggered by: [Validate SEO] button
    - Response: SEO validation report
    - Checks: Meta tags, schema markup, performance, accessibility

### Analytics (Epic 4)

24. **GET /api/v1/events/{eventId}/analytics/detailed**
    - Triggered by: [View Full Analytics →] link
    - Opens: Full analytics dashboard
    - Navigation: Dedicated analytics page

25. **GET /api/v1/events/{eventId}/analytics/realtime**
    - Triggered by: Auto-refresh in analytics section
    - Query params: `metric=views|conversions|bounces`
    - Response: Real-time analytics data
    - Used for: Live dashboard updates

### Content Scheduling (Epic 4)

26. **POST /api/v1/events/{eventId}/publishing/schedule-phase**
    - Triggered by: [Edit Schedule] button
    - Opens: Scheduling modal
    - Payload: `{ phase: "speakers|agenda|final", publishAt: datetime, autoPublish: boolean }`
    - Response: Schedule created
    - Side effect: Creates scheduled job for automatic content release

27. **DELETE /api/v1/events/{eventId}/publishing/schedule/{scheduleId}**
    - Triggered by: Canceling scheduled publish
    - Response: Schedule canceled
    - Side effect: Removes scheduled job

---

## Navigation Map

Screen transitions triggered by actions and events:

### Primary Navigation

1. **← Back Button**
   - **Target**: Event Management Dashboard (Story 1.16)
   - **Context**: Return to main event overview

2. **[Edit]** (on event title/topic)
   - **Target**: Event edit form (inline or modal)
   - **Type**: Modal overlay or inline edit
   - **Content**: Edit event basic info

3. **[View]** (on date & venue)
   - **Target**: Venue details page or modal
   - **Type**: Modal with venue info, map, capacity
   - **Content**: Venue details, logistics

### Component Management Navigation

4. **[Manage]** (on speaker list)
   - **Target**: Speaker Matching Interface (Story 3.1)
   - **Type**: Full page navigation
   - **Context**: Manage speaker invitations, assignments

5. **[Review]** (on abstracts)
   - **Target**: Abstract review and moderation interface
   - **Type**: Full page or modal
   - **Context**: Review and approve/reject abstracts

6. **[Fix]** (on validation errors)
   - **Target**: Context-specific to error type
   - **Examples**:
     - Abstract length → Abstract editing interface
     - Missing photos → Speaker photo upload
   - **Type**: Direct to relevant editing screen

7. **[Go]** (on quality review pending)
   - **Target**: Quality review moderator interface
   - **Type**: Full page navigation
   - **Context**: Moderator reviews pending content

8. **[Upload]** (on speaker photos)
   - **Target**: Bulk photo upload interface or speaker list
   - **Type**: Modal or navigation to speaker management
   - **Action**: Upload missing speaker photos

9. **[Assign]** (on agenda times)
   - **Target**: Slot assignment interface
   - **Type**: Full page or modal
   - **Context**: Assign speakers to time slots

10. **[Test]** (on registration link)
    - **Target**: Test result modal
    - **Type**: Modal overlay
    - **Content**: Registration flow test results, issues found

### Publishing Actions Navigation

11. **[Publish Now]**
    - **Validation Check**: If validation < threshold, show warning
    - **Confirmation**: "Publish with X% readiness?" dialog
    - **Target**: Remains on screen after publish
    - **Feedback**: Success toast with public URL
    - **Updates**: Version history, timeline phase, preview

12. **[Schedule]**
    - **Target**: Scheduling modal
    - **Type**: Modal overlay with date/time picker
    - **Submit**: Creates scheduled publish job
    - **Feedback**: "Publish scheduled for [date/time]" confirmation

13. **[Preview]**
    - **Target**: Full preview in new tab or modal
    - **Type**: New browser tab with public URL (preview token)
    - **Content**: Exact rendering of public event page

### Preview Navigation

14. **[Desktop] / [Mobile] / [Print]** buttons
    - **Action**: Changes preview rendering mode
    - **No Navigation**: Remains on screen
    - **Updates**: Live preview panel re-renders

15. **Live Preview Click** (on preview panel)
    - **Target**: Full preview in new tab
    - **Type**: New browser tab
    - **Content**: Full-size preview of public page

### Version Control Navigation

16. **[View History]**
    - **Target**: Version history modal or panel
    - **Type**: Modal overlay or side panel
    - **Content**: List of all versions with timestamps, authors, changes
    - **Actions**: [View Diff], [Rollback]

17. **[Rollback]**
    - **Confirmation**: "Rollback to version X? This will create a new version." dialog
    - **Action**: POST rollback
    - **Target**: Remains on screen
    - **Feedback**: "Rolled back to version X" toast
    - **Updates**: All panels refresh with rolled-back state

### A/B Testing Navigation (Epic 4)

18. **A/B Test Checkbox Toggle**
    - **No Navigation**: Remains on screen
    - **Feedback**: "Test enabled/disabled" toast
    - **Updates**: Live changes to public page

19. **"Current winner" Link**
    - **Target**: A/B test detailed results modal
    - **Type**: Modal overlay
    - **Content**: Full test results, statistical significance, variant performance
    - **Actions**: [Declare Winner], [End Test], [View Analytics]

### SEO Optimization Navigation (Epic 4)

20. **⚠️ Missing social preview image**
    - **Target**: Social preview generator modal
    - **Type**: Modal with image upload or auto-generate option
    - **Actions**: [Upload Image], [Auto-Generate], [Skip]

21. **SEO Optimization Section Click**
    - **Target**: SEO optimization modal
    - **Type**: Modal overlay
    - **Content**: Detailed SEO report, recommendations, editing interface
    - **Submit**: Updates meta tags, schema markup

### Analytics Navigation (Epic 4)

22. **[View Full Analytics →]**
    - **Target**: Full analytics dashboard
    - **Type**: Full page navigation or new tab
    - **Content**: Complete analytics with charts, segments, cohorts
    - **Integration**: May link to Google Analytics or internal dashboard

23. **Analytics Metrics Click**
    - **Target**: Detailed metric modal
    - **Type**: Modal overlay
    - **Content**: Drill-down on specific metric (views, bounce rate, conversion)
    - **Charts**: Time series, segments, funnels

### Content Scheduling Navigation (Epic 4)

24. **[Edit Schedule]**
    - **Target**: Content scheduling modal
    - **Type**: Modal overlay with calendar interface
    - **Content**: Set publish times for each phase
    - **Submit**: Updates scheduled publish jobs

25. **Scheduled Phase Link**
    - **Target**: Schedule edit modal for that phase
    - **Type**: Modal overlay
    - **Actions**: [Edit Time], [Cancel Schedule], [Publish Now]

### Event-Driven Navigation

26. **On Validation Complete**
    - **No Navigation**: Remains on screen
    - **Updates**: Validation dashboard refreshes
    - **Feedback**: Readiness percentage updates
    - **Highlight**: New errors or warnings highlighted

27. **On Publish Success**
    - **No Navigation**: Remains on screen
    - **Feedback**: Success toast with public URL link
    - **Updates**: Timeline phase updated, version history, preview
    - **Action**: [View Public Page] link in toast

28. **On Scheduled Publish Executes**
    - **Notification**: Email or dashboard notification
    - **Entry**: Link opens publishing dashboard
    - **Feedback**: "Scheduled publish completed" banner
    - **Updates**: Timeline, version history, validation status

29. **On Validation Error Threshold**
    - **Feedback**: Warning banner "Validation below 80%. Auto-publish disabled."
    - **Action**: [Fix Errors] button
    - **Restriction**: Publish button disabled or shows warning

30. **On A/B Test Winner Determined**
    - **Feedback**: Success toast "Test complete. Version A won (67% CTR)"
    - **Update**: Winner automatically applied to all users
    - **Action**: [View Results] link in toast

### Error States

31. **On Publish Failure**
    - **No Navigation**: Remains on screen
    - **Feedback**: Error modal with details
    - **Actions**: [Retry], [View Logs], [Contact Support]

32. **On Version Rollback Failure**
    - **No Navigation**: Remains on screen
    - **Feedback**: Error message explaining issue
    - **Action**: [Retry] or [Contact Support]

33. **On Validation Check Failure**
    - **No Navigation**: Remains on screen
    - **Feedback**: "Unable to validate content" error
    - **Action**: [Retry Validation] button

### Mobile-Specific

34. **Mobile Publishing Dashboard**
    - **Layout**: Stacked sections, simplified controls
    - **Priority**: Show validation status and quick actions first
    - **Preview**: Thumbnail with link to full preview

35. **Mobile Preview**
    - **Native**: Mobile preview is default
    - **Toggle**: Desktop preview in landscape mode

---