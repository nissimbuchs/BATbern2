# Story: Strategic Planning Interface - Wireframe

**Story**: Epic 6, Story 4
**Screen**: Strategic Planning Interface
**User Role**: Partner
**Related FR**: FR8 (Planning)

---

## 6. Strategic Planning Interface

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                    Strategic Partnership Planning                             │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌──── 2025-2026 STRATEGIC ROADMAP ───────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  Your Strategic Goals                    Alignment with BATbern                │  │
│  │                                                                                 │  │
│  │  1. Digital Transformation               Topics: Cloud, DevOps, Platform Eng   │  │
│  │     Progress: ████████░░ 80%            Coverage: ████████░░ 80%              │  │
│  │                                                                                 │  │
│  │  2. Talent Development                   Topics: Training, Workshops           │  │
│  │     Progress: ██████░░░░ 60%            Coverage: ███████░░░ 70%              │  │
│  │                                                                                 │  │
│  │  3. Innovation Culture                   Topics: AI/ML, Emerging Tech          │  │
│  │     Progress: ████░░░░░░ 40%            Coverage: █████░░░░░ 50%              │  │
│  │                                                                                 │  │
│  │  [Edit Goals] [Add New Goal] [Export Roadmap]                                  │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── TALENT PIPELINE ─────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Skills Gap Analysis                          Training Impact                   │ │
│  │                                                                                  │ │
│  │  Critical Skills Needed:                      After BATbern Training:          │ │
│  │  • Kubernetes: 45 people      ████████        • Certified: 23 (+12)            │ │
│  │  • Security: 32 people        ██████          • Proficient: 67 (+25)           │ │
│  │  • AI/ML: 28 people          █████           • Aware: 124 (+45)                │ │
│  │  • Cloud Arch: 25 people      █████                                            │ │
│  │                                               ROI on Training:                  │ │
│  │  Hiring Alternative Cost:                    CHF 3.2M saved                    │ │
│  │  CHF 4.5M (45 people × 100K)                vs recruitment                     │ │
│  │                                                                                  │ │
│  │  [Request Custom Workshop] [View Certification Paths] [Download Skills Report]  │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── INNOVATION OPPORTUNITIES ────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  Collaboration Proposals                                                        ││
│  │                                                                                  ││
│  │  ┌──────────────────────────────────────────────────────────────────────────┐  ││
│  │  │ 💡 Co-Innovation Lab                                        Status: Draft │  ││
│  │  │                                                                            │  ││
│  │  │ Proposal: Establish quarterly innovation sessions where UBS teams         │  ││
│  │  │ present challenges and BATbern community provides solutions               │  ││
│  │  │                                                                            │  ││
│  │  │ Benefits:                          Next Steps:                            │  ││
│  │  │ • Direct problem solving           • Define first challenge               │  ││
│  │  │ • Community engagement             • Set Q2 2025 date                     │  ││
│  │  │ • Thought leadership               • Identify participants                │  ││
│  │  │                                                                            │  ││
│  │  │ [Submit Proposal] [Schedule Discussion] [View Similar Initiatives]        │  ││
│  │  └──────────────────────────────────────────────────────────────────────────┘  ││
│  │                                                                                  ││
│  │  Other Opportunities:                                                           ││
│  │  • Hackathon Sponsorship - Engage developer community                          ││
│  │  • Speaker Program - Position UBS experts as thought leaders                   ││
│  │  • Research Partnership - Joint whitepapers on Swiss FinTech                   ││
│  └──────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## API Requirements

### Initial Page Load APIs

When the Strategic Partnership Planning screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/partners/{partnerId}/strategic-goals**
   - Query params: timeframe (2025-2026)
   - Returns: List of strategic goals with descriptions, progress percentages, alignment metrics, related topics
   - Used for: Populate strategic roadmap section with goals and progress

2. **GET /api/v1/partners/{partnerId}/strategic-goals/alignment**
   - Query params: goalIds
   - Returns: For each goal, shows alignment with BATbern topics, coverage percentages, recommended content
   - Used for: Display topic alignment and coverage bars for each goal

3. **GET /api/v1/partners/{partnerId}/skills-gap-analysis**
   - Returns: Critical skills needed with demand counts, current skill levels, gap analysis
   - Used for: Populate skills gap analysis section

4. **GET /api/v1/partners/{partnerId}/training-impact**
   - Returns: Training effectiveness metrics (certified, proficient, aware counts), before/after comparisons, ROI calculations
   - Used for: Display training impact and ROI metrics

5. **GET /api/v1/partners/{partnerId}/innovation-proposals**
   - Query params: status (draft, submitted, approved)
   - Returns: List of innovation collaboration proposals with status, descriptions, benefits, next steps
   - Used for: Populate innovation opportunities section with proposals

6. **GET /api/v1/partners/{partnerId}/innovation-opportunities/suggested**
   - Returns: Suggested collaboration opportunities based on partner profile, industry trends, past engagement
   - Used for: Display "Other Opportunities" section

---

## Action APIs

### Strategic Goals Management

1. **POST /api/v1/partners/{partnerId}/strategic-goals**
   - Payload: `{ title, description, targetCompletion, keyMetrics: [], relatedTopics: [] }`
   - Response: Goal ID, initial alignment analysis
   - Used for: Add new strategic goal

2. **PUT /api/v1/partners/{partnerId}/strategic-goals/{goalId}**
   - Payload: `{ title, description, progress, targetCompletion, keyMetrics: [] }`
   - Response: Updated goal, recalculated alignment
   - Used for: Edit existing strategic goal

3. **DELETE /api/v1/partners/{partnerId}/strategic-goals/{goalId}**
   - Response: Deletion confirmation
   - Used for: Remove strategic goal

4. **POST /api/v1/partners/{partnerId}/strategic-goals/export**
   - Payload: `{ format: "pdf|pptx|excel", includeAlignment: boolean, includeMetrics: boolean }`
   - Response: Export task ID, estimated completion time
   - Used for: Export strategic roadmap

5. **GET /api/v1/partners/{partnerId}/strategic-goals/export/{taskId}/download**
   - Returns: Download URL, expiration timestamp
   - Used for: Download exported roadmap

### Skills & Training Management

6. **POST /api/v1/partners/{partnerId}/skills-gap-analysis/update**
   - Payload: `{ skills: [{ skillName, demandCount, priority: "critical|high|medium|low" }] }`
   - Response: Updated analysis, training recommendations
   - Used for: Update skills gap analysis

7. **POST /api/v1/partners/{partnerId}/workshops/custom-request**
   - Payload: `{ title, targetSkills: [], targetAudience, expectedAttendees, preferredDates: [], duration, objectives }`
   - Response: Request ID, review status, expected response timeline
   - Used for: Request custom workshop for specific skills

8. **GET /api/v1/certifications/paths**
   - Query params: skills
   - Returns: Available certification paths, requirements, timelines, costs, BATbern offerings
   - Used for: Navigate to certification paths view

9. **POST /api/v1/partners/{partnerId}/training/skills-report/generate**
   - Payload: `{ format: "pdf|excel", includeGaps: boolean, includeROI: boolean, includeRecommendations: boolean }`
   - Response: Report generation task ID
   - Used for: Generate comprehensive skills report

10. **GET /api/v1/partners/{partnerId}/training/skills-report/{taskId}/download**
    - Returns: Download URL, expiration timestamp
    - Used for: Download skills report

### Innovation & Collaboration

11. **POST /api/v1/partners/{partnerId}/innovation-proposals/create**
    - Payload: `{ title, type: "co-innovation|hackathon|speaker-program|research", description, benefits: [], resources, timeline }`
    - Response: Proposal ID, draft saved confirmation
    - Used for: Create new innovation proposal

12. **PUT /api/v1/partners/{partnerId}/innovation-proposals/{proposalId}**
    - Payload: Updated proposal data
    - Response: Update confirmation
    - Used for: Edit draft innovation proposal

13. **POST /api/v1/partners/{partnerId}/innovation-proposals/{proposalId}/submit**
    - Response: Submission confirmation, review timeline, next steps
    - Used for: Submit innovation proposal for review

14. **DELETE /api/v1/partners/{partnerId}/innovation-proposals/{proposalId}**
    - Response: Deletion confirmation
    - Used for: Delete draft proposal

15. **POST /api/v1/partners/{partnerId}/innovation-proposals/{proposalId}/meeting/schedule**
    - Payload: `{ proposedDates: [], attendees: [], agenda }`
    - Response: Meeting request ID, scheduling status
    - Used for: Schedule discussion about innovation proposal

16. **GET /api/v1/innovation-opportunities/similar**
    - Query params: proposalType, industry
    - Returns: Similar initiatives from other partners or industry, case studies, best practices
    - Used for: View similar initiatives for inspiration

17. **GET /api/v1/innovation-opportunities/catalog**
    - Returns: Full catalog of available collaboration types with descriptions, requirements, examples
    - Used for: Browse innovation opportunity types

### Analytics & Insights

18. **GET /api/v1/partners/{partnerId}/strategic-planning/insights**
    - Returns: AI-generated insights on goal progress, skills development, innovation opportunities, recommendations
    - Used for: Get strategic recommendations based on current state

19. **GET /api/v1/partners/{partnerId}/strategic-planning/roi-analysis**
    - Query params: timeframe
    - Returns: Comprehensive ROI analysis across goals, training, engagement, brand exposure
    - Used for: View detailed ROI breakdown

20. **GET /api/v1/partners/{partnerId}/strategic-planning/benchmark**
    - Returns: Comparison with similar partners on goals progress, skills development, innovation engagement
    - Used for: Navigate to strategic planning benchmark view

---

## Navigation Map

### Primary Navigation Actions

1. **← Back button** → Navigate back to `Partner Analytics Dashboard`
   - Returns to main partner dashboard

2. **Strategic goal card click** → Navigate to `Goal Details Screen`
   - Detailed goal information
   - Progress tracking over time
   - Related events and content
   - Team members involved
   - Milestone management

3. **[Edit Goals] button** → Navigate to `Goals Management Screen`
   - Edit existing goals
   - Update progress
   - Adjust timelines
   - Manage goal metrics

4. **[Add New Goal] button** → Navigate to `New Goal Creation Screen`
   - Goal definition wizard
   - Topic alignment selector
   - Metrics definition
   - Timeline setting

5. **[Export Roadmap] button** → Triggers roadmap export
   - Select export format
   - Choose sections to include
   - Generate strategic roadmap document
   - No screen navigation

6. **Progress bar click (goal)** → Opens progress details modal
   - Progress timeline
   - Milestones achieved
   - Upcoming milestones
   - Contributors
   - No screen navigation

7. **Coverage bar click (alignment)** → Opens alignment details modal
   - Shows matching topics
   - Content recommendations
   - Upcoming relevant events
   - Gap areas
   - No screen navigation

8. **Skill item click** → Navigate to `Skill Development Path Screen`
   - Available training for that skill
   - Certification options
   - Employee skill levels
   - Recommended events

9. **[Request Custom Workshop] button** → Navigate to `Custom Workshop Request Screen`
   - Workshop specification form
   - Skill targeting
   - Audience definition
   - Date preferences

10. **[View Certification Paths] button** → Navigate to `Certification Paths Browser Screen`
    - Available certifications
    - Requirements and timelines
    - BATbern support for each path
    - Success rates

11. **[Download Skills Report] button** → Triggers skills report generation
    - Select report options
    - Generate comprehensive report
    - Download when ready
    - No screen navigation

12. **Training impact metric click** → Opens training details modal
    - Shows training history
    - Employee progression
    - Certification achievements
    - Event attendance correlation
    - No screen navigation

13. **ROI metric click** → Navigate to `ROI Breakdown Screen`
    - Detailed ROI calculation
    - Methodology explanation
    - Comparison with hiring costs
    - Historical ROI trends

14. **Innovation proposal card** → Navigate to `Proposal Details Screen`
    - Full proposal content
    - Benefits analysis
    - Resource requirements
    - Timeline and milestones
    - Edit capabilities (if draft)

15. **[Submit Proposal] button** → Triggers proposal submission
    - Validation of proposal
    - Confirmation modal
    - Submit to BATbern team
    - Shows success notification
    - No screen navigation

16. **[Schedule Discussion] button** → Navigate to `Meeting Scheduler Screen`
    - Calendar view
    - Attendee selection
    - Agenda builder
    - Proposal context included

17. **[View Similar Initiatives] button** → Navigate to `Similar Initiatives Browser Screen`
    - Case studies from other partners
    - Industry best practices
    - Success metrics
    - Lessons learned

18. **Other opportunity item click** → Navigate to `Opportunity Details Screen`
    - Full opportunity description
    - Requirements and commitments
    - Benefits and ROI
    - How to get started

### Secondary Navigation (Data Interactions)

19. **Strategic goal title click** → Expands goal details inline
    - Shows full description
    - Related metrics
    - Team assignments
    - No full screen navigation

20. **Related topics link (alignment)** → Navigate to `Topic Browser Screen`
    - Filtered to show related topics
    - Upcoming events for topics
    - Content library for topics
    - Voting opportunities

21. **Hiring Alternative Cost click** → Opens cost calculation modal
    - Explains calculation methodology
    - Industry benchmarks
    - Comparison scenarios
    - No screen navigation

22. **Training certification count click** → Navigate to `Employee Certification List Screen`
    - List of certified employees
    - Certification details
    - Achievement dates
    - Upcoming certifications

### Event-Driven Navigation

23. **New strategic goal added** → Shows success notification
    - Goal added to roadmap
    - Alignment analysis complete
    - Recommended actions
    - No automatic navigation

24. **Goal progress updated** → Updates progress bars
    - Animated progress change
    - Milestone notifications if applicable
    - No screen navigation

25. **Roadmap export complete** → Shows notification with download link
    - Download available
    - Preview option
    - Share with team
    - No automatic navigation

26. **Custom workshop request submitted** → Shows confirmation notification
    - Request ID provided
    - Expected response timeline
    - Next steps outlined
    - No automatic navigation

27. **Skills report ready** → Shows notification with download link
    - Download available
    - Preview option
    - No automatic navigation

28. **Innovation proposal submitted** → Shows confirmation notification
    - Submission ID
    - Review process timeline
    - Expected feedback date
    - Links to proposal tracking
    - No automatic navigation

29. **Proposal approved** → Shows success notification
    - Approval confirmation
    - Next steps for implementation
    - Links to implementation planning
    - No automatic navigation

30. **Proposal needs revision** → Shows notification with feedback
    - Revision requests explained
    - Suggestions for improvement
    - Links back to edit proposal
    - No automatic navigation

31. **Meeting scheduled** → Shows confirmation notification
    - Calendar invite sent
    - Meeting details
    - Add to calendar option
    - No automatic navigation

32. **Skills gap analysis updated** → Refreshes skills section
    - Updated demand counts
    - New recommendations
    - Training suggestions
    - No screen navigation

33. **New training completed by employees** → Updates training impact metrics
    - Increments certification/proficiency counts
    - Recalculates ROI
    - Subtle animation on changes
    - No screen navigation

34. **Strategic planning insights generated** → Shows insights banner
    - Key recommendations highlighted
    - Links to detailed insights
    - Action items suggested
    - No automatic navigation

---
