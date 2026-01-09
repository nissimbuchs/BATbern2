# Phase A: Setup (Steps 1-3)

> Define event structure, select topics, and identify speaker candidates

<div class="workflow-phase phase-a">
<strong>Phase A: Setup</strong><br>
Status: <span class="feature-status implemented">Implemented</span><br>
Duration: 1-2 weeks<br>
State Transitions: CREATED → TOPIC_SELECTED → SPEAKERS_IDENTIFIED
</div>

## Overview

Phase A establishes the foundation for your event. You'll define the event format, select topics based on historical data, and brainstorm potential speakers.

**Key Deliverable**: Event configured with selected topics and identified speaker candidates

## Step 1: Event Type Definition

<span class="feature-status implemented">Implemented</span>

### Purpose

Define the event format which determines timeline, session count, and speaker requirements.

### Accessing Event Creation

Navigate to the Events dashboard and click **Create Event**.

![Event Dashboard](../assets/screenshots/workflow/phase-a-setup/a-01-event-dashboard.png)

The event creation modal will open with the event configuration form.

![Event Creation Modal](../assets/screenshots/workflow/phase-a-setup/a-02-event-creation-modal.png)

### Acceptance Criteria

- ✅ Event type selected (Full-Day, Afternoon, or Evening)
- ✅ Event date and time confirmed
- ✅ Venue identified
- ✅ Registration timeline defined

### Event Type Selection

Choose the appropriate format for your conference:

#### 🌞 Full-Day Conference

**When to Use**:
- Annual flagship BATbern event
- Broad audience appeal (200+ expected attendees)
- Multiple topics to cover
- Budget supports full-day venue rental

**Configuration**:
- **Duration**: 8+ hours (e.g., 9:00 - 18:00)
- **Sessions**: 8-12 presentations
- **Speakers Needed**: 10-15 speakers
- **Parallel Tracks**: 2-3 tracks (optional)
- **Lead Time**: 3-4 months recommended

#### 🌤️ Afternoon Workshop

**When to Use**:
- Specialized topic deep-dive
- Smaller audience (50-100 attendees)
- Interactive format with Q&A
- Half-day commitment from speakers

**Configuration**:
- **Duration**: 3-4 hours (e.g., 14:00 - 18:00)
- **Sessions**: 2-4 presentations
- **Speakers Needed**: 3-5 speakers
- **Parallel Tracks**: Single track
- **Lead Time**: 6-8 weeks recommended

#### 🌙 Evening Lecture

**When to Use**:
- Guest speaker or keynote presentation
- Networking-focused event
- Shorter commitment for attendees
- Lower budget / less formal

**Configuration**:
- **Duration**: 2-3 hours (e.g., 18:00 - 21:00)
- **Sessions**: 1-2 presentations
- **Speakers Needed**: 1-3 speakers
- **Parallel Tracks**: Single track
- **Lead Time**: 4-6 weeks recommended

### How to Complete

<div class="step" data-step="1">

**Select Event Type**

In the event creation/edit form, select the appropriate type:

```
Event Type *
[▼ Select type...              ]
   Full-Day Conference
   Afternoon Workshop
   Evening Lecture
```
</div>

<div class="step" data-step="2">

**Configure Timeline**

Set event date and registration windows:

```
Event Date *
[📅 2025-03-15]

Event Time *
[⏰ 09:00] to [⏰ 18:00]

Registration Opens *
[📅 2025-01-15]

Registration Closes *
[📅 2025-03-10]
```

Validation ensures logical timeline: Opens < Closes < Event Date
</div>

<div class="step" data-step="3">

**Specify Venue**

Enter venue details:

```
Venue Name
[Kursaal Bern]

Address
[Kornhausstrasse 3]

City *
[Bern]

Canton
[▼ BE]

Postal Code
[3013]
```
</div>

<div class="step" data-step="4">

**Save Configuration**

Click **Save & Continue to Step 2**.

Event state advances to: **CREATED** (ready for topic selection)
</div>

### Assigning Tasks to Organizers

After creating the event, you can assign tasks to team members to distribute the workload.

![Task Assignment](../assets/screenshots/workflow/phase-a-setup/a-07-edit-modal-tasks-tab-initial.png)

Access the task list to view and manage assigned tasks.

![Task List View](../assets/screenshots/workflow/phase-a-setup/a-13-task-list-my-tasks-filter.png)

## Step 2: Topic Selection with Heat Map

<span class="feature-status implemented">Implemented</span>

### Purpose

Select session topics using historical data to inform decisions and avoid topic fatigue.

### Acceptance Criteria

- ✅ Minimum topics selected based on event type:
  - Full-Day: 8-12 topics
  - Afternoon: 2-4 topics
  - Evening: 1-2 topics
- ✅ Topics reviewed for balance (not all from same category)
- ✅ Historical context considered via heat map

### Topic Heat Map

<span class="feature-status implemented">Implemented</span>

The **Topic Heat Map** visualizes 20+ years of BATbern conference history, showing topic frequency over time.

#### How the Heat Map Works

```
Topic Heat Map (2000-2025)

Topic                        | 2000 | 2005 | 2010 | 2015 | 2020 | 2025
─────────────────────────────────────────────────────────────────────
Sustainable Building         | ████ | ████ | ████ | ████ | ████ | ⬜
Digital Transformation       | ⬜   | ░░░░ | ████ | ████ | ████ | ⬜
Urban Planning               | ████ | ████ | ░░░░ | ████ | ░░░░ | ⬜
Heritage Preservation        | ░░░░ | ████ | ████ | ░░░░ | ░░░░ | ⬜
Materials Innovation         | ⬜   | ⬜   | ░░░░ | ████ | ████ | ⬜

Legend:
████ Dark Blue - Presented frequently (4+ times in period)
░░░░ Light Blue - Presented occasionally (1-3 times)
⬜ White - Not presented (0 times)
```

**Color Coding**:
- **Dark Blue**: Frequent topic (presented 4+ times in 5-year period)
- **Light Blue**: Occasional topic (1-3 times)
- **White**: Never or rarely presented

#### Interpreting the Heat Map

**Popular Topics** (Dark Blue columns):
- ✅ Proven audience interest
- ⚠️ Risk of topic fatigue if presented too often
- Consider new angles or subtopics

**Emerging Topics** (White → Light Blue transition):
- ✅ Fresh content, likely high interest
- ✅ Opportunity to be ahead of trends
- Consider pairing with established speakers

**Declining Topics** (Dark → Light Blue transition):
- ⚠️ May indicate waning interest
- Consider retiring or significantly updating

**Consistently Popular** (Dark Blue across all periods):
- ✅ Safe, reliable topics
- Ensure fresh perspective each time
- Examples: Sustainable Building, Energy Efficiency

### Topic Categories

Topics are organized into categories:

**Design & Aesthetics**:
- Minimalist Design
- Swiss Architecture
- Color Theory in Architecture

**Technology & Innovation**:
- Digital Transformation
- BIM and Digital Twins
- Smart Buildings
- Computational Design

**Sustainability**:
- Sustainable Building Materials
- Energy Efficiency
- Circular Economy
- Net-Zero Buildings

**Urban & Regional**:
- Urban Planning
- Public Spaces
- Transportation Integration
- Regional Development

**Heritage & History**:
- Heritage Preservation
- Adaptive Reuse
- Historical Context

**Practice & Business**:
- Project Management
- Client Relations
- Practice Development

### How to Complete

<div class="step" data-step="1">

**Open Heat Map**

In Step 2 of the workflow, click **View Topic Heat Map**.

Interactive heat map opens showing historical data.

![Topic Heat Map](../assets/screenshots/workflow/phase-a-setup/a-16-topic-heatmap.png)

</div>

<div class="step" data-step="2">

**Explore Historical Trends**

Hover over cells to see details:

```
Sustainable Building - 2020
────────────────────────────
Presentations: 6
Speakers: 8 (2 panels)
Average Rating: 4.7/5
Attendance: 245 avg
```

Click year to see presentation details.
</div>

<div class="step" data-step="3">

**Select Topics**

Click topics to add to your event:

```
Selected Topics (4 of 8 required)
──────────────────────────────────
✅ Sustainable Building Materials    [Remove]
✅ Digital Transformation            [Remove]
✅ Urban Planning Innovations        [Remove]
✅ Heritage Adaptive Reuse           [Remove]

[+ Add Another Topic]
```

Add minimum required for your event type.
</div>

<div class="step" data-step="4">

**Review Balance**

Ensure topic diversity:

- ✅ Mix of popular and emerging topics
- ✅ Balance across categories (not all Technology)
- ✅ Variety in presentation formats (case studies, theory, practice)

</div>

<div class="step" data-step="5">

**Confirm Selection**

Click **Confirm Topic Selection**.

Event state advances to: **TOPIC_SELECTED**
</div>

### Topic Selection Tips

**Aim for Variety**:
```
✅ Good Balance:
- Sustainable Materials (Sustainability)
- Digital Transformation (Technology)
- Urban Planning (Urban & Regional)
- Heritage Reuse (Heritage)

❌ Poor Balance:
- BIM Implementation (Technology)
- Smart Buildings (Technology)
- Digital Twins (Technology)
- Computational Design (Technology)
```

**Consider Event Type**:
- **Full-Day**: Mix of broad appeal and specialized topics
- **Afternoon**: Focused theme with related topics
- **Evening**: Single compelling topic or contrasting pair

**Leverage Heat Map Insights**:
- Avoid topics presented last year (unless new angle)
- Identify 5+ year gaps as re-introduction opportunities
- Note which topics consistently draw high attendance

## Step 3: Speaker Brainstorming

<span class="feature-status implemented">Implemented</span>

### Purpose

Identify potential speakers for each selected topic, creating a pool of candidates for outreach in Phase B.

### Acceptance Criteria

- ✅ Each topic has 2-3 candidate speakers identified
- ✅ Total candidates = 2× needed speakers (e.g., 20 candidates for 10 speaker slots)
- ✅ Candidate diversity considered (companies, experience levels, demographics)
- ✅ Contact information captured (email minimum)

### Candidate Sources

**Past Speakers**:
- Review historical speaker database
- Filter by topic expertise
- Check availability (not presented recently)

**Company Referrals**:
- Ask partner companies for recommendations
- Leverage user/company database
- Contact industry associations

**Industry Experts**:
- LinkedIn searches
- Industry publications and blogs
- Conference speaker rosters (other events)

**Internal Knowledge**:
- Organizer team recommendations
- Board member suggestions
- Partner company employees

### How to Complete

<div class="step" data-step="1">

**Select Topic**

In Step 3, choose a topic to brainstorm speakers:

```
Topics for BATbern 2025
───────────────────────────────────
[📋 Sustainable Materials]     0 speakers ⚠️
[📋 Digital Transformation]    3 speakers ✅
[📋 Urban Planning]            2 speakers ⚠️
[📋 Heritage Reuse]            0 speakers ⚠️
```

Click topic to add speaker candidates.
</div>

<div class="step" data-step="2">

**Add Candidate**

Click **+ Add Speaker Candidate** to open the brainstorming form.

![Speaker Brainstorming Form](../assets/screenshots/workflow/phase-a-setup/a-19-speaker-brainstorming-form.png)

Fill in the candidate details:

```
Add Speaker Candidate
────────────────────────────────────
First Name *
[Hans]

Last Name *
[Müller]

Email *
[hans.mueller@example.com]

Company
[🔍 Müller Architekten AG     ] (autocomplete)

Expertise Match
[▼ High]  High | Medium | Low

Notes (optional)
[Presented on sustainable materials at ETH 2023.
Strong technical background, engaging presenter.]

Source
[▼ Past Speaker]
   Past Speaker | Company Referral | Industry Expert | Internal

[Cancel]  [Add Candidate]
```

Candidate is created with status: **IDENTIFIED**
</div>

<div class="step" data-step="3">

**Repeat for All Topics**

Add 2-3 candidates per topic:

```
Sustainable Materials (3 candidates ✅)
────────────────────────────────────
Hans Müller | High Match | hans.mueller@example.com
Anna Schmidt | Medium Match | anna.schmidt@example.com
Peter Weber | High Match | peter.weber@example.com

[+ Add Candidate]
```
</div>

<div class="step" data-step="4">

**Review Candidate Pool**

View all candidates across topics:

```
Speaker Candidate Summary
────────────────────────────────────
Total Candidates: 24
Speakers Needed: 12
Ratio: 2:1 ✅

By Company:
- Müller Architekten AG: 3
- Schmidt & Partner: 2
- [Other companies]: 19

By Experience:
- High Match: 16
- Medium Match: 6
- Low Match: 2

Warnings:
⚠️ 3 candidates from same company (Müller Architekten AG)
   → Consider diversity
```
</div>

<div class="step" data-step="5">

**Confirm Brainstorming Complete**

Once minimum candidates identified, click **Complete Brainstorming**.

Event state advances to: **SPEAKERS_IDENTIFIED**

Phase A is complete! ✅
</div>

### Brainstorming Tips

**Target 2:1 Ratio**:
- Identify 2× candidates per needed speaker
- Provides backup if speakers decline
- Allows selectivity based on responses

**Consider Diversity**:
- **Company**: Avoid 3+ speakers from same firm
- **Experience**: Mix established and emerging voices
- **Gender**: Aim for balanced representation
- **Geography**: Include speakers from different regions

**Prioritize Candidates**:
```
High Priority (contact first):
- Past speakers with excellent ratings
- Industry thought leaders
- Strong topic-expertise match

Medium Priority (contact if needed):
- New speakers with good credentials
- Medium topic match
- Company referrals

Low Priority (backup):
- Emerging speakers (unproven)
- Low topic match
- Last-resort options
```

**Capture Useful Notes**:
- Previous presentation history
- Presentation style (technical, storytelling, visual)
- Availability constraints (e.g., "Not available Fridays")
- Special requirements (e.g., "Needs translator")

## Phase A Completion

### Success Criteria

Before advancing to Phase B, confirm:

- ✅ Event type defined with timeline
- ✅ Topics selected using heat map data
- ✅ Speaker candidates identified (2:1 ratio)
- ✅ Event state = **SPEAKERS_IDENTIFIED**

### What Happens Next

**Phase B: Outreach** begins automatically:
- Candidates ready for contact (Step 4)
- Outreach tracking system active
- Email templates available

See [Phase B: Outreach →](phase-b-outreach.md) to continue.

## Troubleshooting Phase A

### "Heat map shows no data"

**Problem**: Heat map displays empty cells.

**Solution**:
- Historical data may not be imported yet (Epic 3 in progress)
- Proceed with topic selection based on organizer knowledge
- Contact support to import historical data

### "Can't find enough speaker candidates"

**Problem**: Insufficient candidates for 2:1 ratio.

**Solution**:
- Expand search to adjacent topics
- Consider emerging speakers (lower experience)
- Extend outreach timeline to allow more research
- Consider reducing event scope (fewer sessions)

### "Topics too similar"

**Problem**: Selected topics lack diversity.

**Solution**:
- Review heat map for adjacent categories
- Consult topic category list
- Replace 1-2 topics with different categories
- Ensure balance across Design, Technology, Sustainability

## Related Topics

- [Event Management →](../entity-management/events.md) - Event configuration
- [Topic Heat Map →](../features/heat-maps.md) - Detailed heat map guide
- [Speaker Management →](../entity-management/speakers.md) - Speaker profiles
- [Phase B: Outreach →](phase-b-outreach.md) - Next phase

## API Reference

### Workflow Endpoints

```
POST /api/events/{id}/workflow/step-1     Complete Step 1 (Event Type)
POST /api/events/{id}/workflow/step-2     Complete Step 2 (Topic Selection)
POST /api/events/{id}/workflow/step-3     Complete Step 3 (Brainstorming)
GET  /api/topics/heatmap                   Get heat map data
POST /api/speakers                         Create speaker candidate
```

See [API Documentation](../../api/) for complete specifications.
