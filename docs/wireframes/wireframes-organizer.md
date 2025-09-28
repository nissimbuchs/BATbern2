# BATbern Organizer Portal - Complete Wireframes

## Overview
This document contains detailed wireframes for all organizer interfaces, covering the complete 16-step workflow and intelligent features (FR17-FR21).

---

## 1. Main Organizer Dashboard

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ BATbern Event Platform                            Sally O. ▼  [🔔 3] [?] [Settings] │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────────┐    │
│  │ Welcome back, Sally! Here's your event command center                       │    │
│  └─────────────────────────────────────────────────────────────────────────────┘    │
│                                                                                       │
│  ┌──────────────── ACTIVE EVENTS PIPELINE ─────────────────┬──── QUICK ACTIONS ───┐ │
│  │                                                          │                       │ │
│  │  Spring Conference 2025        ███████████░░░░░ 65%     │ [+ New Event]        │ │
│  │  ├─ Step 7/16: Content Review                           │ [📊 Analytics]       │ │
│  │  ├─ 3 speakers pending ⚠️                               │ [👥 Speakers]        │ │
│  │  └─ Publishing: March 15                                │ [🏢 Partners]        │ │
│  │                                                          │ [📅 Venues]          │ │
│  │  Summer Workshop 2025          ██░░░░░░░░░░░░░ 15%     │ [📧 Newsletter]      │ │
│  │  ├─ Step 2/16: Speaker Research                         │                       │ │
│  │  ├─ On track ✓                                          │ ─────────────────     │ │
│  │  └─ Publishing: May 30                                  │                       │ │
│  │                                                          │ AI SUGGESTIONS 🤖    │ │
│  │  Autumn Conference 2025        ░░░░░░░░░░░░░░░ Planning │                       │ │
│  │  ├─ Step 1/16: Topic Selection                          │ "Contact speaker     │ │
│  │  └─ Starts: April 1                                     │  John D. - overdue   │ │
│  └──────────────────────────────────────────────────────────┤  3 days"            │ │
│                                                              │                       │ │
│  ┌──────────── CRITICAL TASKS (3) ─────────────────────────┤ "Reserve venue for   │ │
│  │                                                          │  Q3 partner meeting" │ │
│  │  ⚠️ Speaker materials overdue: Dr. Smith (3 days)       │                       │ │
│  │     [Contact] [Extend Deadline] [Find Replacement]      │ "Review 5 pending    │ │
│  │                                                          │  abstracts"          │ │
│  │  🔴 Venue confirmation needed: Kursaal Bern             │                       │ │
│  │     [Confirm] [View Details] [Alternative Venues]       └───────────────────────┤ │
│  │                                                                                  │ │
│  │  📋 Moderate 5 pending abstracts                                                │ │
│  │     [Start Review] [Assign to Team] [Bulk Approve]                             │ │
│  └──────────────────────────────────────────────────────────────────────────────┘  │ │
│                                                                                       │
│  ┌─── TEAM ACTIVITY FEED ───────────┬─── PERFORMANCE METRICS ────────────────────┐  │
│  │                                   │                                             │  │
│  │ 10:45 Mark assigned to Spring    │  Avg. Planning Time    ⬇ 45% improved      │  │
│  │       speaker outreach            │  Speaker Accept Rate   ⬆ 78% (target: 80%) │  │
│  │ 10:12 Anna completed venue        │  Content Quality       ⬆ 92% compliance    │  │
│  │       booking for Summer          │  Publishing On-Time    ✓ 100% this quarter │  │
│  │ 09:30 System auto-sent reminder   │                                             │  │
│  │       to 3 pending speakers       │  [View Detailed Analytics →]                │  │
│  └───────────────────────────────────┴─────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Key Interactive Elements:
- **Progress bars**: Click to drill down into specific workflow steps
- **Warning indicators**: Hover for details, click for action options
- **Quick Actions**: One-click access to frequent tasks
- **AI Suggestions**: Context-aware recommendations updated in real-time
- **Team Feed**: Live updates with @mentions and notifications
- **Metrics**: Click any metric for detailed breakdown

---

## 2. 16-Step Workflow Visualization (Event Detail View)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Dashboard        Spring Conference 2025 - Workflow Manager                 │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  Event: Cloud Native Architecture        Status: Active       Progress: 7/16 (44%)   │
│  Date: May 15, 2025                     Deadline: March 15   Team: Sally, Mark, Anna│
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

### Workflow States:
- **✓ Done**: Completed steps (green)
- **● Active**: Current step (blue, pulsing)
- **○ Pending**: Future steps (gray)
- **⚠️ Blocked**: Steps with issues (orange)
- **🔴 Overdue**: Past deadline (red)

---

## 3. Smart Topic Backlog Management (FR18)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Dashboard                Topic Backlog Manager                    [+ Add]  │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── FILTERS ───────────────────────┬─── VIEW OPTIONS ──────────────────────────┐  │
│  │                                   │                                            │  │
│  │ Category: [All Categories ▼]      │ Sort by: [Partner Priority ▼]             │  │
│  │ Status: [Available ▼]             │ Group by: [Category ▼]                     │  │
│  │ Last Used: [Any Time ▼]          │ View: [● Heat Map] [○ List] [○ Board]     │  │
│  │ Partner Interest: [High ▼]        │                                            │  │
│  └───────────────────────────────────┴────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── TOPIC HEAT MAP ───────────────────────────────────────────────────────────┐   │
│  │                                                                               │   │
│  │  Usage History (Darker = More Recent/Frequent)                               │   │
│  │                                                                               │   │
│  │        2020      2021      2022      2023      2024      2025                │   │
│  │  Q1 Q2 Q3 Q4 Q1 Q2 Q3 Q4 Q1 Q2 Q3 Q4 Q1 Q2 Q3 Q4 Q1 Q2 Q3 Q4 Q1 Q2          │   │
│  │                                                                               │   │
│  │  Cloud Native     ██ ░░ ░░ ░░ ██ ░░ ░░ ░░ ░░ ░░ ██ ░░ ░░ ░░ ░░ [SELECTED]  │   │
│  │  AI/ML            ░░ ░░ ░░ ░░ ░░ ░░ ██ ░░ ░░ ██ ██ ░░ ░░ ██ ░░ ░░          │   │
│  │  DevOps           ██ ██ ░░ ██ ░░ ██ ░░ ░░ ██ ░░ ░░ ██ ░░ ░░ ░░ ░░          │   │
│  │  Security         ░░ ░░ ██ ░░ ░░ ░░ ██ ░░ ░░ ██ ░░ ░░ ██ ░░ ░░ ░░          │   │
│  │  Blockchain       ██ ██ ██ ░░ ░░ ░░ ░░ ░░ ░░ ░░ ░░ ░░ ░░ ░░ ⚠️ Stale       │   │
│  │  IoT              ░░ ░░ ░░ ██ ░░ ░░ ░░ ██ ░░ ░░ ░░ ░░ ░░ ░░ ✨ Trending    │   │
│  │                                                                               │   │
│  └───────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                       │
│  ┌─── SELECTED TOPIC DETAILS ───────────────────────────────────────────────────┐   │
│  │                                                                               │   │
│  │  Topic: Cloud Native Architecture                    ID: TOP-2025-014         │   │
│  │  Category: Infrastructure                           Status: Available         │   │
│  │                                                                               │   │
│  │  ┌─── Performance ───┐  ┌─── Partner Interest ───┐  ┌─── AI Analysis ───┐   │   │
│  │  │                    │  │                        │  │                     │   │
│  │  │ Attendance: 187    │  │ Votes: 24 👍           │  │ Similarity Score:   │   │
│  │  │ Rating: 4.6/5      │  │ Priority: HIGH         │  │ 15% to "Kubernetes" │   │
│  │  │ Downloads: 1,247   │  │ Sponsors: UBS, Swiss Re│  │ 22% to "Microserv." │   │
│  │  │ Engagement: 89%    │  │                        │  │                     │   │
│  │  └────────────────────┘  └────────────────────────┘  └───────────────────┘   │   │
│  │                                                                               │   │
│  │  Last Used: March 2023 (Spring Conference)                                   │   │
│  │  Suggested Wait: 6+ months before reuse                                      │   │
│  │  Related Topics: Kubernetes, Microservices, Container Orchestration          │   │
│  │                                                                               │   │
│  │  Partner Comments:                                                            │   │
│  │  "Very relevant for our teams" - Credit Suisse                               │   │
│  │  "Would like deeper dive on security aspects" - Swiss Re                     │   │
│  │                                                                               │   │
│  │  [Select for Event] [View History] [Similar Topics] [Partner Feedback]       │   │
│  └───────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                       │
│  ┌─── AI SUGGESTIONS ─────────────────────────────────────────────────────────────┐  │
│  │ 🤖 Based on industry trends and partner interests:                             │  │
│  │                                                                                 │  │
│  │ • "Platform Engineering" - New trend, high search volume, no previous events   │  │
│  │ • "FinOps & Cloud Cost" - 3 partners requested, aligns with cost focus        │  │
│  │ • "Zero Trust Security" - Compliance deadline approaching for banks           │  │
│  │                                                           [See All →]          │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Intelligent Speaker Matching Interface (FR17)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back        Speaker Management - Spring Conference 2025              [Import CSV] │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  Event: Cloud Native Architecture          Slots: 8          Confirmed: 5/8         │
│                                                                                       │
│  ┌─── SPEAKER PIPELINE ──────────────────────────────────────────────────────────┐  │
│  │                                                                               │  │
│  │  Open     Contacted    Ready    Declined    Accepted    Assigned    Final     │  │
│  │   (12)       (8)        (3)       (2)         (5)         (5)        (0)      │  │
│  │                                                                               │  │
│  │  ┌─────┐   ┌─────┐   ┌─────┐   ┌─────┐    ┌─────┐    ┌─────┐    ┌─────┐    │  │
│  │  │John │   │Anna │   │Marc │   │Lisa │    │Peter│    │Peter│    │     │    │  │
│  │  │Smith│──▶│Wong │──▶│Baum │──▶│Chen │───▶│Mull │───▶│Slot2│───▶│     │    │  │
│  │  │Erik │   │Paul │   │Nina │   │Hans │    │Sara │    │Sara │    │     │    │  │
│  │  │+9...│   │+5...│   │     │   │     │    │Kim  │    │Slot1│    │     │    │  │
│  │  └─────┘   └─────┘   └─────┘   └─────┘    └─────┘    └─────┘    └─────┘    │  │
│  │                                                                               │  │
│  │  [Bulk Move →] [Send Reminder] [View Waitlist (3)]                          │  │
│  └───────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                       │
│  ┌─── SMART MATCHING ────────────────────┬─── SLOT ASSIGNMENT ─────────────────┐   │
│  │                                       │                                      │   │
│  │  For: Cloud Native Architecture       │  Morning (Technical)                │   │
│  │                                       │  ┌────────────────────────────┐     │   │
│  │  ┌── Best Matches ─────────────┐      │  │ 09:00 │ Sara Kim - Docker  │     │   │
│  │  │                              │      │  ├────────────────────────────┤     │   │
│  │  │ 95% Dr. Thomas Weber          │      │  │ 09:45 │ Peter Muller - K8s │     │   │
│  │  │     • 5 yrs K8s experience   │      │  ├────────────────────────────┤     │   │
│  │  │     • Swiss Re, Architecture │      │  │ 10:30 │ [Empty - Assign]   │     │   │
│  │  │     • Available ✓            │      │  ├────────────────────────────┤     │   │
│  │  │     [Invite] [View Profile]  │      │  │ 11:15 │ [Empty - Assign]   │     │   │
│  │  │                              │      │  └────────────────────────────┘     │   │
│  │  │ 89% Maria Santos             │      │                                      │   │
│  │  │     • CNCF Ambassador        │      │  Afternoon (Practical)              │   │
│  │  │     • 50+ conferences        │      │  ┌────────────────────────────┐     │   │
│  │  │     • Travel required ✈️     │      │  │ 13:30 │ [Empty - Assign]   │     │   │
│  │  │     [Invite] [View Profile]  │      │  ├────────────────────────────┤     │   │
│  │  │                              │      │  │ 14:15 │ [Empty - Assign]   │     │   │
│  │  │ 82% Local: Hans Gerber      │      │  ├────────────────────────────┤     │   │
│  │  │     • Swisscom, DevOps Lead │      │  │ 15:00 │ [Empty - Assign]   │     │   │
│  │  │     • First time speaker 🆕  │      │  ├────────────────────────────┤     │   │
│  │  │     [Invite] [View Profile]  │      │  │ 15:45 │ [Empty - Assign]   │     │   │
│  │  └──────────────────────────────┘      │  └────────────────────────────┘     │   │
│  │                                       │                                      │   │
│  │  Filter by:                           │  ⚠️ Overflow: 2 accepted speakers    │   │
│  │  □ Local speakers only                │     need slots                       │   │
│  │  ☑ Industry experience                │     [Start Voting] [Auto-Assign]    │   │
│  │  □ First-time speakers                │                                      │   │
│  └───────────────────────────────────────┴──────────────────────────────────────┘   │
│                                                                                       │
│  ┌─── TECHNICAL REQUIREMENTS ────────┬─── TEAM COLLABORATION ──────────────────┐   │
│  │                                   │                                          │   │
│  │ Speaker          Requirements     │  @sally: Thomas Weber looks perfect!    │   │
│  │ Sara Kim         ✓ HDMI, ✓ Mic   │  @mark: Agreed, sending invite now      │   │
│  │ Peter Muller     ✓ All standard   │  @anna: Maria needs travel budget       │   │
│  │ Marc Baum        ⚠️ Mac adapter    │  @system: 2 speakers auto-reminded      │   │
│  │                  ⚠️ Remote option  │                                          │   │
│  └───────────────────────────────────┴──────────────────────────────────────────┘   │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. Progressive Publishing Engine (FR19)

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
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Multi-Year Planning Dashboard (FR21)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                    Strategic Planning Dashboard                    [Export]   │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  View: [● Calendar] [○ Timeline] [○ Budget]    Years: [2024 ▼] [2025 ▼] [2026 ▼]   │
│                                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐ │
│  │                              2025 Planning Calendar                              │ │
│  │                                                                                   │ │
│  │     January          February         March            April            May       │ │
│  │  Mo Tu We Th Fr   Mo Tu We Th Fr   Mo Tu We Th Fr   Mo Tu We Th Fr   Mo Tu We  │ │
│  │        1  2  3                1    31  1  2  3  4       1  2  3  4             │ │
│  │   6  7  8  9 10    3  4  5  6  7    5  6  7  8  9    7  8  9 10 11    5  6  7  │ │
│  │  13 14 15 16 17   10 11 12 13 14   12 13 14 15 16   14 [P] 16 17 18   12 13 14  │ │
│  │  20 21 22 23 24   17 18 19 20 21   19 20 21 22 23   21 22 23 24 25   19 [E] 21 │ │
│  │  27 28 29 30 31   24 25 26 27 28   26 27 28 29 30   28 29 30         26 27 28  │ │
│  │                                                                                   │ │
│  │  Legend: [E] Event  [P] Partner Meeting  [V] Venue Booking  [D] Planning Deadline│ │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── VENUE RESERVATIONS ────────────┬─── PARTNER MEETINGS ──────────────────────┐  │
│  │                                   │                                            │  │
│  │  2025 Bookings:                   │  2025 Schedule:                           │  │
│  │  ┌─────────────────────────────┐  │  ┌──────────────────────────────────┐     │  │
│  │  │ Q1: Kursaal Bern      ✓     │  │  │ Apr 15: Spring Planning         │     │  │
│  │  │     Mar 20 - Confirmed      │  │  │   Attendees: 12 partners        │     │  │
│  │  │     Capacity: 250           │  │  │   Agenda: 2026 topics           │     │  │
│  │  │                             │  │  │   Status: Room booked ✓         │     │  │
│  │  │ Q2: UniS Bern         ⚠️    │  │  │                                  │     │  │
│  │  │     May 20 - Tentative     │  │  │ Oct 10: Autumn Review           │     │  │
│  │  │     Awaiting confirmation  │  │  │   Attendees: TBD                │     │  │
│  │  │                             │  │  │   Agenda: Budget & metrics      │     │  │
│  │  │ Q3: Tech Park         ○     │  │  │   Status: Planning              │     │  │
│  │  │     Sep - Not booked       │  │  └──────────────────────────────────┘     │  │
│  │  │     [Check Availability]   │  │                                            │  │
│  │  │                             │  │  Recurring Tasks:                         │  │
│  │  │ Q4: Casino Bern       ✓     │  │  • Quarterly budget review               │  │
│  │  │     Nov 15 - Confirmed     │  │  • Topic voting (30 days before)         │  │
│  │  └─────────────────────────────┘  │  • Sponsor reports (14 days before)      │  │
│  │                                   │                                            │  │
│  │  2026 Advance Bookings:           │  [Schedule Meeting] [View 2024 History]  │  │
│  │  • Q1: Negotiating with Kursaal   │                                            │  │
│  │  • Q2-Q4: Open for planning       │                                            │  │
│  └───────────────────────────────────┴────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── BUDGET TRACKING ────────────────────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  2025 Annual Budget: CHF 120,000        Allocated: CHF 89,000 (74%)           │  │
│  │                                                                                 │  │
│  │  Q1: CHF 30,000  ████████████████████████████░░░░░░  85% allocated            │  │
│  │  Q2: CHF 30,000  ████████████████████░░░░░░░░░░░░░░  60% allocated            │  │
│  │  Q3: CHF 30,000  ████████████░░░░░░░░░░░░░░░░░░░░░░  40% planning             │  │
│  │  Q4: CHF 30,000  ████████░░░░░░░░░░░░░░░░░░░░░░░░░░  25% committed            │  │
│  │                                                                                 │  │
│  │  Major Expenses:           Sponsors Committed:                                 │  │
│  │  • Venues: CHF 40,000     • UBS: CHF 25,000 ✓                                 │  │
│  │  • Catering: CHF 35,000   • Swiss Re: CHF 20,000 ✓                            │  │
│  │  • Speakers: CHF 15,000   • Credit Suisse: Pending                            │  │
│  │  • Marketing: CHF 10,000  • Swisscom: CHF 15,000 ✓                            │  │
│  │                                                                                 │  │
│  │  [Download Report] [Update Budget] [Forecast 2026]                            │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Cross-Role Collaboration Hub

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back               Collaboration Hub - Spring Conference 2025                      │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── ACTIVE PARTICIPANTS ─────────┬─── VISIBILITY MATRIX ───────────────────────┐  │
│  │                                 │                                              │  │
│  │  Organizers (3)                 │           View  Edit  Comment  Notify       │  │
│  │  ● Sally O. - Lead       Online │  Organizer  ✓    ✓      ✓        ✓         │  │
│  │  ● Mark T. - Speakers    Online │  Speaker    ✓    Own    ✓        ✓         │  │
│  │  ○ Anna M. - Content     Away   │  Partner    ✓    ✗      ✓        Summary   │  │
│  │                                 │  Attendee   ✓    ✗      ✗        Major      │  │
│  │  Speakers (8)                   │  Public     Part. ✗      ✗        ✗         │  │
│  │  ● Peter M.              Online │                                              │  │
│  │  ● Sara K.               Online │  [Configure Permissions →]                   │  │
│  │  ○ Thomas W.             Offline│                                              │  │
│  │  [+5 more]                      │                                              │  │
│  │                                 │                                              │  │
│  │  Partners (4)                   │                                              │  │
│  │  ● UBS Rep               Viewing│                                              │  │
│  │  ○ Swiss Re Rep          Offline│                                              │  │
│  │  [+2 more]                      │                                              │  │
│  └─────────────────────────────────┴──────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── ACTIVITY FEED ─────────────────────────────────────────────────────────────┐   │
│  │                                                                                │   │
│  │  ┌──────────────────────────────────────────────────────────────────────┐     │   │
│  │  │ 14:32  @sally mentioned @mark in Speaker assignments                 │     │   │
│  │  │        "Can you contact Thomas about his technical requirements?"    │     │   │
│  │  │        [Reply] [Resolve]                                            │     │   │
│  │  ├──────────────────────────────────────────────────────────────────────┤     │   │
│  │  │ 14:28  @system: Speaker Peter M. submitted abstract                 │     │   │
│  │  │        Abstract ready for review (924 characters) ✓                 │     │   │
│  │  │        [Review Now] [Assign Reviewer]                               │     │   │
│  │  ├──────────────────────────────────────────────────────────────────────┤     │   │
│  │  │ 14:15  @anna completed quality review for 3 abstracts              │     │   │
│  │  │        All passed validation checks                                 │     │   │
│  │  │        [View Details] [Approve Publishing]                          │     │   │
│  │  ├──────────────────────────────────────────────────────────────────────┤     │   │
│  │  │ 13:45  @partner-ubs requested topic for Q3                         │     │   │
│  │  │        "Interested in AI/ML security topics"                       │     │   │
│  │  │        [Add to Backlog] [Discuss]                                  │     │   │
│  │  ├──────────────────────────────────────────────────────────────────────┤     │   │
│  │  │ 13:30  @system: Deadline approaching                               │     │   │
│  │  │        Speaker materials due in 48 hours (3 pending)               │     │   │
│  │  │        [Send Reminders] [View Status]                              │     │   │
│  │  └──────────────────────────────────────────────────────────────────────┘     │   │
│  │                                                                                │   │
│  │  Filter: [All Roles ▼] [All Events ▼] [Last 24h ▼]      [Mark All Read]      │   │
│  └────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                       │
│  ┌─── SHARED DOCUMENTS ──────────┬─── QUICK COMMUNICATIONS ─────────────────────┐   │
│  │                               │                                               │   │
│  │ 📄 Event Planning Doc         │  New Message:                                │   │
│  │    Last edit: Sally, 2h ago   │  To: [@mark ▼] [Add more...]                 │   │
│  │    [Open] [History]           │                                               │   │
│  │                               │  ┌───────────────────────────────────┐        │   │
│  │ 📊 Speaker Matrix             │  │ Type your message here...         │        │   │
│  │    Shared by: Mark, Today     │  │                                   │        │   │
│  │    [View] [Download]          │  └───────────────────────────────────┘        │   │
│  │                               │                                               │   │
│  │ 📋 Venue Contract             │  Priority: [Normal ▼]                         │   │
│  │    Status: Under review       │  Notify via: ☑ Platform ☑ Email ☐ SMS        │   │
│  │    [Review] [Approve]         │                                               │   │
│  │                               │  [Send] [Save Draft] [Schedule]               │   │
│  └───────────────────────────────┴───────────────────────────────────────────────┘  │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Automated Notification Center

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                     Notification Center                         [Settings]    │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  Unread: 3     Total: 47     Active Rules: 12     Next Scheduled: 15:30            │
│                                                                                       │
│  ┌─── INBOX ────────────────────────┬─── AUTOMATION RULES ───────────────────────┐  │
│  │                                  │                                             │  │
│  │ [All] [Unread] [Critical] [Team] │  Active Rules (12)                         │  │
│  │                                  │                                             │  │
│  │ ┌────────────────────────────┐   │  ┌─────────────────────────────────────┐   │  │
│  │ │ 🔴 CRITICAL - 14:45        │   │  │ Speaker Deadline Reminder          │   │  │
│  │ │ Speaker dropout detected   │   │  │ Trigger: 72h before deadline       │   │  │
│  │ │ Dr. Weber cancelled talk   │   │  │ Action: Email + Platform notify    │   │  │
│  │ │ [Find Replacement]         │   │  │ Status: ✓ Active                   │   │  │
│  │ │ [Contact Waitlist]         │   │  │ Last run: Today 09:00              │   │  │
│  │ └────────────────────────────┘   │  │ [Edit] [Disable] [Test]            │   │  │
│  │                                  │  ├─────────────────────────────────────┤   │  │
│  │ ┌────────────────────────────┐   │  │ Abstract Quality Alert             │   │  │
│  │ │ ⚠️ WARNING - 14:30         │   │  │ Trigger: Failed validation         │   │  │
│  │ │ Abstract needs revision    │   │  │ Action: Notify moderator + speaker │   │  │
│  │ │ Marc B. - Too long (1250)  │   │  │ Status: ✓ Active                   │   │  │
│  │ │ [Review] [Contact Speaker] │   │  │ Fired: 23 times this month         │   │  │
│  │ └────────────────────────────┘   │  ├─────────────────────────────────────┤   │  │
│  │                                  │  │ Venue Confirmation Check           │   │  │
│  │ ┌────────────────────────────┐   │  │ Trigger: 30 days before event      │   │  │
│  │ │ ℹ️ INFO - 13:15            │   │  │ Action: Check status, escalate     │   │  │
│  │ │ Publishing milestone hit   │   │  │ Status: ✓ Active                   │   │  │
│  │ │ 5 speakers now confirmed   │   │  │ Next: March 15, 09:00              │   │  │
│  │ │ Ready for partial publish  │   │  ├─────────────────────────────────────┤   │  │
│  │ │ [Publish Now] [Review]     │   │  │ [+ Create New Rule]                │   │  │
│  │ └────────────────────────────┘   │  └─────────────────────────────────────┘   │  │
│  │                                  │                                             │  │
│  │ View older notifications...      │                                             │  │
│  └──────────────────────────────────┴─────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── ESCALATION WORKFLOWS ─────────────────────────────────────────────────────┐   │
│  │                                                                                │   │
│  │  Active Escalations:                                                          │   │
│  │                                                                                │   │
│  │  Speaker Non-Response → Day 1: Email → Day 3: Call → Day 5: Replace          │   │
│  │  ├─ Peter M: Day 3 (calling today)                                           │   │
│  │  └─ Lisa C: Day 1 (email sent)                                               │   │
│  │                                                                                │   │
│  │  Venue Issues → Hour 1: Notify team → Hour 4: Call venue → Day 1: Escalate   │   │
│  │  └─ All clear ✓                                                               │   │
│  │                                                                                │   │
│  │  Quality Problems → Immediate: Speaker → Day 1: Moderator → Day 2: Lead      │   │
│  │  └─ Marc B. abstract: Moderator notified                                     │   │
│  │                                                                                │   │
│  │  [Configure Escalations] [View History] [Pause All]                          │   │
│  └────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                       │
│  ┌─── NOTIFICATION PREFERENCES ──────────────────────────────────────────────────┐  │
│  │                                                                                │   │
│  │  My Preferences:              Team Preferences:          System Defaults:     │   │
│  │  ☑ Platform notifications     ☑ Daily digest 09:00      ☑ Critical: Always   │   │
│  │  ☑ Email (important only)    ☑ Weekly report Mon        ☑ Warnings: Batched  │   │
│  │  ☐ SMS (critical only)       ☐ Slack integration        ☑ Info: Platform     │   │
│  │  ☑ Browser push              ☑ @mentions immediate      ☐ Marketing: Never   │   │
│  │                                                                                │   │
│  │  Quiet Hours: 20:00 - 08:00  [Edit Preferences]                              │   │
│  └────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Interactive Elements & Behaviors

### Global Interactions
- **Drag & Drop**: Speaker cards to slots, timeline events, document uploads
- **Hover States**: Show tooltips, preview content, reveal quick actions
- **Keyboard Shortcuts**:
  - `Cmd/Ctrl + K`: Quick search
  - `Cmd/Ctrl + N`: New event
  - `Cmd/Ctrl + S`: Save current work
  - `Tab`: Navigate through workflow steps
  - `Esc`: Close modals/overlays

### Smart Features
- **Auto-save**: Every 30 seconds with visual indicator
- **Undo/Redo**: Last 20 actions tracked
- **Bulk Operations**: Select multiple items with checkboxes
- **Real-time Updates**: WebSocket connections for live data
- **Offline Mode**: Queue actions when disconnected
- **Smart Defaults**: AI-powered field suggestions

### Responsive Behaviors
- **Mobile (<768px)**: Stack all panels vertically, hamburger menu
- **Tablet (768-1024px)**: 2-column layout, collapsible sidebars
- **Desktop (>1024px)**: Full multi-panel layout
- **Print View**: Optimized layout for reports

### Accessibility Features
- **Screen Reader**: ARIA labels on all interactive elements
- **Keyboard Nav**: Full keyboard accessibility
- **High Contrast**: Mode toggle in settings
- **Focus Indicators**: Clear visual focus states
- **Skip Links**: Jump to main content

---

## Technical Implementation Notes

### Component Architecture
```
OrganierDashboard/
├── DashboardLayout.tsx
├── EventPipeline/
│   ├── PipelineView.tsx
│   ├── EventCard.tsx
│   └── ProgressBar.tsx
├── WorkflowManager/
│   ├── SixteenStepFlow.tsx
│   ├── StepDetails.tsx
│   └── Dependencies.tsx
├── SpeakerManagement/
│   ├── SpeakerPipeline.tsx
│   ├── SmartMatching.tsx
│   └── SlotAssignment.tsx
├── PublishingEngine/
│   ├── ValidationDashboard.tsx
│   ├── LivePreview.tsx
│   └── PublishControls.tsx
└── shared/
    ├── NotificationCenter.tsx
    ├── CollaborationHub.tsx
    └── AIAssistant.tsx
```

### State Management
- **Global State**: Zustand for cross-component state
- **Server State**: React Query for API data
- **Local State**: Component-level useState
- **Form State**: React Hook Form
- **Real-time**: WebSocket with automatic reconnection

### Performance Optimizations
- **Code Splitting**: Lazy load heavy components
- **Virtual Scrolling**: For long lists (react-window)
- **Memoization**: React.memo for pure components
- **Debouncing**: Search and auto-save operations
- **Progressive Loading**: Skeleton screens during load

---

## Next Steps

With these comprehensive wireframes, you can now:

1. **Create high-fidelity mockups** in Figma with actual brand colors and typography
2. **Build interactive prototypes** for user testing with stakeholders
3. **Start component development** following the epic timeline
4. **Define API contracts** based on the data requirements shown
5. **Plan usability testing** sessions with actual organizers

These wireframes provide complete coverage of the organizer portal functionality, ensuring all 16 workflow steps and intelligent features are properly visualized and ready for implementation.