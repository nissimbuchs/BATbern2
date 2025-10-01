# Sprint Change Proposal: Remove FR04 & FR09 (Partner Analytics Features)

**Date:** 2025-10-01
**Trigger:** Shifted priorities
**Impact Level:** Major (Epic 6 scope reduction)
**Status:** ✅ APPROVED

---

## 1. Executive Summary

### Issue Identified
Remove partner analytics features (FR04, FR09) from BATbern platform due to shifted priorities, reducing Epic 6 scope from comprehensive analytics platform to focused partner coordination features.

### Impact Assessment
- **Epic 6 Impact**: Remove 3 of 5 stories (60% content reduction)
- **Timeline Impact**: Epic 6 duration reduces from 10 weeks to ~4 weeks
- **Architecture Impact**: Simplified infrastructure (remove QuickSight, analytics tables, complex dashboards)
- **Retained Value**: Partner coordination features (topic voting, meeting management) preserved

### Recommended Path
**Direct Adjustment** - Clean removal of unstarted Epic 6 features with systematic documentation updates. No code rollback required.

### Rationale
1. Epic 6 not yet started - no wasted implementation work
2. Clear scope boundaries between removed and retained features
3. Reduces architectural complexity
4. Core MVP value preserved (event management, speaker coordination, attendee experience)
5. Partner topic voting (FR8) and meeting coordination (FR21) retained

---

## 2. Detailed Changes by Artifact

### 2.1 PRD Changes

#### **File: `docs/prd-enhanced.md`**

**Change 1: Remove FR04**
```markdown
FROM (line 55):
**FR4**: Partners shall access real-time analytics dashboards showing employee attendance, brand exposure metrics, and ROI data

TO:
[REMOVE ENTIRE LINE]
```

**Change 2: Remove FR09**
```markdown
FROM (line 65):
**FR9**: The platform shall generate automated reports for partner meetings including attendance statistics, topic performance, and engagement metrics

TO:
[REMOVE ENTIRE LINE]
```

**Change 3: Update Epic 6 Reference**
```markdown
FROM (line 168):
- **Epic 6**: Weeks 47-56 (Partner & Analytics Platform)

TO:
- **Epic 6**: Weeks 47-50 (Partner Coordination)
```

**Change 4: Update Epic 6 Description**
```markdown
FROM (line 179):
- **[Epic 6: Partner & Analytics Platform](./prd/epic-6-partner-analytics-stories.md)** - ROI dashboards and strategic partnership tools (10 weeks)

TO:
- **[Epic 6: Partner Coordination](./prd/epic-6-partner-coordination-stories.md)** - Strategic partnership tools including topic voting and meeting coordination (4 weeks)
```

**Change 5: Update Epic 6 Success Metrics**
```markdown
FROM (line 206):
- **Epic 6**: Partner dashboard adoption by 100% of partners, ROI visible

TO:
- **Epic 6**: Partner topic voting adoption by 100% of partners, efficient meeting coordination
```

---

#### **File: `docs/prd/epic-6-partner-analytics-stories.md`**

**Change 1: Rename File**
```
FROM: docs/prd/epic-6-partner-analytics-stories.md
TO: docs/prd/epic-6-partner-coordination-stories.md
```

**Change 2: Update Epic Header and Overview**
```markdown
FROM (lines 1-16):
# Epic 6: Partner & Analytics Platform - Vertical Slice Stories

## Epic Overview

**Epic Goal**: Deliver comprehensive partner analytics dashboards with ROI tracking, strategic input tools, and partnership coordination features.

**Deliverable**: Partners can see ROI, track employee attendance, influence topics, and coordinate strategic meetings with organizers.

**Architecture Context**:
- **Core Service**: Partner Analytics Service (Java 21 + Spring Boot 3.2)
- **Analytics**: AWS QuickSight for dashboards
- **Database**: PostgreSQL with analytics views
- **Integration**: Event Management Service for topic voting

**Duration**: 10 weeks (Weeks 47-56)

TO:
# Epic 6: Partner Coordination - Vertical Slice Stories

## Epic Overview

**Epic Goal**: Deliver strategic partnership tools enabling partners to influence event topics and coordinate efficiently with organizers.

**Deliverable**: Partners can vote on topics, submit suggestions, and organizers can coordinate seasonal partner meetings with automated scheduling and materials.

**Architecture Context**:
- **Core Service**: Partner Coordination Service (Java 21 + Spring Boot 3.2)
- **Database**: PostgreSQL with topic voting and meeting management tables
- **Integration**: Event Management Service for topic voting and backlog integration

**Duration**: 4 weeks (Weeks 47-50)
```

**Change 3: Remove Stories 6.1, 6.2, 6.3**
```markdown
[REMOVE lines 19-103]:
- Story 6.1: Partner Analytics Dashboard (entire section)
- Story 6.2: Brand Exposure Tracking (entire section)
- Story 6.3: ROI Reporting (entire section)
```

**Change 4: Renumber Remaining Stories**
```markdown
FROM:
## Story 6.4: Topic Voting Integration
## Story 6.5: Partner Meeting Coordination

TO:
## Story 6.1: Topic Voting Integration
## Story 6.2: Partner Meeting Coordination
```

**Change 5: Update Story 6.1 (formerly 6.4) Architecture Integration**
```markdown
FROM (line 112):
**Architecture Integration:**
- **Service**: Partner Analytics Service + Event Management Service

TO:
**Architecture Integration:**
- **Service**: Partner Coordination Service + Event Management Service
```

**Change 6: Update Epic 6 Success Metrics**
```markdown
FROM (lines 164-183):
## Epic 6 Success Metrics

**Functional Success:**
- ✅ 100% partners have dashboard access
- ✅ ROI clearly quantified and reported
- ✅ Topic voting influences event planning
- ✅ Partner meetings efficiently coordinated

**Technical Performance:**
- **Dashboard Load**: <3 seconds
- **Report Generation**: <30 seconds
- **Data Freshness**: Updated hourly
- **System Availability**: >99.5% uptime

**Business Value:**
- **Partner Retention**: >90% renewal rate
- **ROI Demonstration**: Clear value metrics
- **Strategic Alignment**: Topics match partner needs
- **Efficiency**: 50% reduction in coordination time

TO:
## Epic 6 Success Metrics

**Functional Success:**
- ✅ 100% partners actively voting on topics
- ✅ Topic voting influences event planning decisions
- ✅ Partner meetings efficiently coordinated with automation
- ✅ Meeting materials generated accurately and timely

**Technical Performance:**
- **Voting Interface Load**: <2 seconds
- **Meeting Scheduling**: Automated 2+ months ahead
- **System Availability**: >99.5% uptime

**Business Value:**
- **Partner Engagement**: >80% active participation in topic voting
- **Strategic Alignment**: Topics match partner strategic needs
- **Efficiency**: 50% reduction in meeting coordination time
- **Partner Satisfaction**: Improved partnership value perception
```

---

### 2.2 Architecture Document Changes

#### **File: `docs/architecture/01-system-overview.md`**

**Change 1: Update Directory Structure Comment**
```markdown
FROM (line 40):
├── partner-analytics-service/     # ROI tracking & strategic features

TO:
├── partner-coordination-service/  # Topic voting & meeting coordination
```

**Change 2: Update Service Name in Diagram**
```markdown
FROM (line 139):
            J[Partner Analytics Service<br/>Java 21 + Spring Boot<br/>ROI analytics & reporting]

TO:
            J[Partner Coordination Service<br/>Java 21 + Spring Boot<br/>Topic voting & meeting coordination]
```

**Change 3: Update Event Management Service Dependencies**
```markdown
FROM (line 319):
- Partner Analytics Service (partner involvement tracking)

TO:
- Partner Coordination Service (partner topic voting integration)
```

**Change 4: Update Service Section Header and Description**
```markdown
FROM (lines 358-375):
### Partner Analytics Service

**Responsibility:** Partner relationship management, ROI analytics, strategic input collection, and cross-event participation tracking for all partner companies.

**Key Interfaces:**
- `/api/v1/partners` - Partner profile and relationship management
- `/api/v1/partners/{id}/analytics` - ROI and engagement analytics
- `/api/v1/partners/{id}/topic-votes` - Strategic topic voting
- `/api/v1/reports` - Partner meeting reports and presentations

**Dependencies:**
- Attendee Experience Service (employee attendance data)
- Event Management Service (event participation data)
- Company Management Service (partner company data)
- Analytics Engine (data processing and insights)
- Shared Kernel (partner domain events)

**Technology Stack:** Java 21 + Spring Boot 3.2, PostgreSQL (analytics data), ElastiCache Redis (metrics caching), AWS QuickSight (dashboard generation)

TO:
### Partner Coordination Service

**Responsibility:** Partner relationship management, strategic topic voting, partner meeting coordination, and partnership lifecycle management.

**Key Interfaces:**
- `/api/v1/partners` - Partner profile and relationship management
- `/api/v1/partners/{id}/topic-votes` - Strategic topic voting and suggestions
- `/api/v1/partners/{id}/meetings` - Partner meeting scheduling and coordination
- `/api/v1/topics/voting` - Topic voting and prioritization

**Dependencies:**
- Event Management Service (topic backlog integration, event participation data)
- Company Management Service (partner company data)
- Notification Service (meeting reminders and voting notifications)
- Shared Kernel (partner domain events)

**Technology Stack:** Java 21 + Spring Boot 3.2, PostgreSQL (partnership and voting data), AWS SES (meeting notifications)
```

---

#### **File: `docs/architecture/03-data-architecture.md`**

**Change 1: Update Service Schema Header**
```markdown
FROM (line 689):
### Partner Analytics Service Database Schema

TO:
### Partner Coordination Service Database Schema
```

**Change 2: Remove partner_analytics Table and Add New Tables**
```markdown
FROM (lines 706-718):
-- Partner analytics aggregations
CREATE TABLE partner_analytics (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    partner_id UUID NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    event_id UUID, -- NULL for cross-event analytics
    total_employee_attendance INTEGER DEFAULT 0,
    content_engagement_score DECIMAL(5,2) DEFAULT 0.00,
    brand_exposure_score DECIMAL(5,2) DEFAULT 0.00,
    roi_score DECIMAL(10,2) DEFAULT 0.00,
    topic_influence_score DECIMAL(5,2) DEFAULT 0.00,
    calculation_date TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(partner_id, event_id)
);

TO:
-- Topic voting
CREATE TABLE topic_votes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    topic_id UUID NOT NULL, -- References topic in Event Management Service
    partner_id UUID NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    vote_weight INTEGER DEFAULT 1, -- Based on partnership_level
    vote_value INTEGER NOT NULL CHECK (vote_value BETWEEN 1 AND 5),
    voted_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(topic_id, partner_id)
);

-- Partner topic suggestions
CREATE TABLE topic_suggestions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    partner_id UUID NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    suggested_topic VARCHAR(500) NOT NULL,
    description TEXT,
    business_justification TEXT,
    suggested_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    status VARCHAR(50) NOT NULL CHECK (status IN (
        'submitted', 'under_review', 'accepted', 'rejected', 'implemented'
    )) DEFAULT 'submitted',
    reviewed_at TIMESTAMP WITH TIME ZONE,
    reviewed_by UUID -- References organizer
);

-- Partner meetings
CREATE TABLE partner_meetings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    meeting_type VARCHAR(50) NOT NULL CHECK (meeting_type IN ('spring', 'autumn', 'ad_hoc')),
    scheduled_date DATE NOT NULL,
    location VARCHAR(255),
    agenda TEXT,
    materials_generated BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Partner meeting attendance
CREATE TABLE partner_meeting_attendance (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    meeting_id UUID NOT NULL REFERENCES partner_meetings(id) ON DELETE CASCADE,
    partner_id UUID NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    rsvp_status VARCHAR(50) NOT NULL CHECK (rsvp_status IN (
        'invited', 'accepted', 'declined', 'tentative', 'attended'
    )) DEFAULT 'invited',
    rsvp_at TIMESTAMP WITH TIME ZONE,
    attended BOOLEAN DEFAULT FALSE,
    UNIQUE(meeting_id, partner_id)
);
```

**Change 3: Update Indexes**
```markdown
FROM (lines 720-724):
-- Indexes
CREATE INDEX idx_partners_company_id ON partners(company_id);
CREATE INDEX idx_partners_active ON partners(is_active);
CREATE INDEX idx_partner_analytics_partner_id ON partner_analytics(partner_id);

TO:
-- Indexes
CREATE INDEX idx_partners_company_id ON partners(company_id);
CREATE INDEX idx_partners_active ON partners(is_active);
CREATE INDEX idx_topic_votes_partner_id ON topic_votes(partner_id);
CREATE INDEX idx_topic_votes_topic_id ON topic_votes(topic_id);
CREATE INDEX idx_topic_suggestions_partner_id ON topic_suggestions(partner_id);
CREATE INDEX idx_topic_suggestions_status ON topic_suggestions(status);
CREATE INDEX idx_partner_meetings_date ON partner_meetings(scheduled_date);
CREATE INDEX idx_partner_meeting_attendance_meeting_id ON partner_meeting_attendance(meeting_id);
CREATE INDEX idx_partner_meeting_attendance_partner_id ON partner_meeting_attendance(partner_id);
```

**Change 4: Update Domain Model (if exists at line 66)**
```markdown
FROM (line 66):
- analytics: PartnerAnalytics - ROI and engagement metrics across all events
- strategicInput: PartnerInput - Topic voting and suggestions

TO:
- topicVotes: TopicVote[] - Historical voting records
- topicSuggestions: TopicSuggestion[] - Partner-submitted topic ideas
- meetingAttendance: PartnerMeetingAttendance[] - Meeting participation history
```

---

#### **File: `docs/architecture/04-api-design.md`**

**Change 1: Remove Analytics Endpoint**
```markdown
FROM (line 643+):
  # Partner Analytics Domain
  /api/v1/partners/{partnerId}/analytics:
    get:
      tags: [Partners]
      summary: Get partner analytics
      [... full endpoint definition ...]

TO:
[REMOVE ENTIRE ENDPOINT SECTION - search for and delete all lines related to the analytics endpoint]
```

---

#### **File: `docs/architecture/05-frontend-architecture.md`**

**Change 1: Remove Analytics from State**
```markdown
FROM (line 67):
  partners: {
    analytics: PartnerAnalytics | null;
    topicVotes: TopicVote[];
  };

TO:
  partners: {
    topicVotes: TopicVote[];
    meetings: PartnerMeeting[];
  };
```

**Change 2: Remove PartnerAnalyticsDashboard Component**
```markdown
FROM (lines 754-806):
// Partner Analytics Dashboard
interface PartnerAnalyticsDashboardProps {
  partner: Partner;
  analytics: PartnerAnalytics;
  timeRange: TimeRange;
  onTimeRangeChange: (range: TimeRange) => void;
}

const PartnerAnalyticsDashboard: React.FC<PartnerAnalyticsDashboardProps> = ({
  [... full component ...]
});

TO:
[REMOVE ENTIRE COMPONENT SECTION]
```

**Change 3: Remove Analytics Route**
```markdown
FROM (line 1045):
/partner/analytics            # ROI analytics dashboard

TO:
[REMOVE LINE]
```

**Change 4: Update Partner Dashboard Route Description**
```markdown
FROM (line 1044):
/partner/dashboard            # Partner main dashboard

TO:
/partner/dashboard            # Partner main dashboard (voting, meetings, profile)
```

---

#### **Additional Architecture Files**

Search and update the following files if they contain references:

**File: `docs/architecture/02-infrastructure-deployment.md`**
- Remove all "QuickSight" references
- Remove "ElastiCache Redis" references if only used for analytics

**File: `docs/architecture/06-backend-architecture.md`**
- Replace "Partner Analytics Service" → "Partner Coordination Service"
- Remove analytics calculation references

**File: `docs/architecture/index.md`** (if exists)
- Update service list and descriptions

---

### 2.3 Other Changes

#### **Directory Structure (Future)**
```
When Epic 6 implementation begins, use:
partner-coordination-service/  (not partner-analytics-service/)
```

#### **Epic Timeline Update**
```markdown
Epic 6 Duration:
FROM: 10 weeks (Weeks 47-56)
TO: 4 weeks (Weeks 47-50)

Overall Project Timeline:
FROM: 62 weeks total
TO: 56 weeks total (6 weeks saved)
```

---

## 3. Implementation Checklist

### PRD Updates
- [ ] Update `docs/prd-enhanced.md` - Remove FR04 (line 55)
- [ ] Update `docs/prd-enhanced.md` - Remove FR09 (line 65)
- [ ] Update `docs/prd-enhanced.md` - Update Epic 6 timeline reference (line 168)
- [ ] Update `docs/prd-enhanced.md` - Update Epic 6 description (line 179)
- [ ] Update `docs/prd-enhanced.md` - Update Epic 6 success metrics (line 206)
- [ ] Rename `docs/prd/epic-6-partner-analytics-stories.md` → `epic-6-partner-coordination-stories.md`
- [ ] Update Epic 6 file - Replace header and overview (lines 1-16)
- [ ] Update Epic 6 file - Remove Stories 6.1, 6.2, 6.3 (lines 19-103)
- [ ] Update Epic 6 file - Renumber Stories 6.4→6.1, 6.5→6.2
- [ ] Update Epic 6 file - Update architecture integration references
- [ ] Update Epic 6 file - Update success metrics (lines 164-183)

### Architecture Updates
- [ ] Update `docs/architecture/01-system-overview.md` - Directory structure (line 40)
- [ ] Update `docs/architecture/01-system-overview.md` - Service diagram (line 139)
- [ ] Update `docs/architecture/01-system-overview.md` - Dependencies (line 319)
- [ ] Update `docs/architecture/01-system-overview.md` - Service section (lines 358-375)
- [ ] Update `docs/architecture/03-data-architecture.md` - Schema header (line 689)
- [ ] Update `docs/architecture/03-data-architecture.md` - Replace tables (lines 706-718)
- [ ] Update `docs/architecture/03-data-architecture.md` - Update indexes (lines 720-724)
- [ ] Update `docs/architecture/03-data-architecture.md` - Update domain model (line 66)
- [ ] Update `docs/architecture/04-api-design.md` - Remove analytics endpoint (line 643+)
- [ ] Update `docs/architecture/05-frontend-architecture.md` - Remove analytics state (line 67)
- [ ] Update `docs/architecture/05-frontend-architecture.md` - Remove component (lines 754-806)
- [ ] Update `docs/architecture/05-frontend-architecture.md` - Remove route (line 1045)
- [ ] Update `docs/architecture/05-frontend-architecture.md` - Update dashboard description (line 1044)
- [ ] Review `docs/architecture/02-infrastructure-deployment.md` - Remove QuickSight
- [ ] Review `docs/architecture/06-backend-architecture.md` - Update service references

### Verification
- [ ] Grep search for "FR04" - ensure all removed
- [ ] Grep search for "FR4" - ensure all removed
- [ ] Grep search for "FR09" - ensure all removed
- [ ] Grep search for "FR9" - ensure all removed
- [ ] Grep search for "Partner Analytics Service" - ensure all replaced
- [ ] Grep search for "partner-analytics-service" - ensure all replaced
- [ ] Grep search for "QuickSight" - ensure all removed
- [ ] Grep search for "ROI" in context of partner features - verify removal
- [ ] Grep search for "analytics dashboard" - verify partner context removed
- [ ] Verify Epic 6 stories 6.1-6.2 (new) have complete acceptance criteria

---

## 4. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Missed analytics references in docs | Medium | Low | Systematic grep search per verification checklist |
| Future partner expectations for analytics | Low | Medium | Clear communication; features can be added post-MVP |
| Incomplete topic voting specifications | Medium | Medium | Review Stories 6.1-6.2 for completeness before Epic 6 |
| Service renaming causes confusion | Low | Low | Systematic updates; clear documentation |

---

## 5. Next Steps & Agent Handoff

### Immediate Actions (This Session)
1. ✅ Sprint Change Proposal approved
2. ➡️ Execute all artifact updates systematically
3. ➡️ Run verification grep searches
4. ➡️ Confirm all changes complete

### Future Actions (Pre-Epic 6)
- **Architect Review**: Verify architecture consistency post-changes
- **PM Review**: Ensure Stories 6.1-6.2 (topic voting, meetings) have complete specs
- **PO Review**: Validate Epic 6 scope aligns with partner needs

### Agent Handoff Points
- **Back to Dev Agent**: Apply all changes, run verification
- **To Architect**: Architecture consistency review
- **To PM**: Epic 6 story completeness validation

---

## 6. Success Criteria

✅ **Documentation Updated:**
- All 8+ documents updated with no analytics references
- Service renamed throughout (Partner Analytics → Partner Coordination)
- Database schemas updated (analytics table removed, voting/meeting tables added)

✅ **Scope Clarified:**
- Epic 6 properly scoped to 4 weeks
- 2 focused stories (topic voting, meeting coordination)
- FR8 and FR21 retained and specified

✅ **Architecture Simplified:**
- No QuickSight infrastructure
- No complex analytics processing
- Streamlined partner coordination focus

✅ **Timeline Adjusted:**
- Epic 6: 10 weeks → 4 weeks
- Overall project: 62 weeks → 56 weeks

---

**Status: APPROVED - Ready for Implementation**

**Approved By:** User
**Approval Date:** 2025-10-01
**Implementation Start:** Immediate
