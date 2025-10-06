# API Consolidation Analysis Report

**Generated**: 2025-10-04 21:54:41

**Project**: BATbern Event Management Platform

---

## 1. Executive Summary

This report analyzes all API endpoints mentioned across 49 wireframe specifications for the BATbern platform. The analysis identifies consolidation opportunities to create a more RESTful, maintainable, and scalable API design.

### Overall Statistics

- **Total Unique API Endpoints**: 731
- **Total API Call Instances**: 853
- **Wireframes Analyzed**: 49
- **Resource Domains**: 49

### Method Distribution

- **GET**: 381 endpoints (52%)
- **POST**: 214 endpoints (29%)
- **PUT**: 90 endpoints (12%)
- **PATCH**: 4 endpoints (0%)
- **DELETE**: 42 endpoints (5%)

### Top 10 Resource Domains by Endpoint Count

1. **events**: 130 endpoints
2. **partners**: 109 endpoints
3. **speakers**: 70 endpoints
4. **content**: 63 endpoints
5. **organizers**: 53 endpoints
6. **topics**: 52 endpoints
7. **users**: 46 endpoints
8. **companies**: 42 endpoints
9. **auth**: 18 endpoints
10. **attendees**: 16 endpoints

### Consolidation Potential

Based on pattern analysis, the following consolidation is achievable:

- **Events Domain**: 130 → ~25 endpoints (81% reduction)
- **Partners Domain**: 109 → ~20 endpoints (82% reduction)
- **Speakers Domain**: 70 → ~18 endpoints (74% reduction)
- **Content Domain**: 63 → ~15 endpoints (76% reduction)
- **Topics Domain**: 52 → ~12 endpoints (77% reduction)

**Overall Estimated Reduction**: 731 → ~250 endpoints (**66% reduction**)

---

## 2. Current State Analysis

### 2.1 API Endpoints by Domain

| Rank | Resource Domain | Total Endpoints | GET | POST | PUT | PATCH | DELETE |
|------|----------------|----------------|-----|------|-----|-------|--------|
| 1 | events | 130 | 70 | 26 | 26 | 2 | 6 |
| 2 | partners | 109 | 59 | 34 | 11 | 0 | 5 |
| 3 | speakers | 70 | 32 | 25 | 8 | 0 | 5 |
| 4 | content | 63 | 28 | 26 | 6 | 0 | 3 |
| 5 | organizers | 53 | 33 | 10 | 8 | 0 | 2 |
| 6 | topics | 52 | 44 | 4 | 2 | 0 | 2 |
| 7 | users | 46 | 11 | 18 | 9 | 0 | 8 |
| 8 | companies | 42 | 18 | 13 | 7 | 0 | 4 |
| 9 | auth | 18 | 5 | 13 | 0 | 0 | 0 |
| 10 | attendees | 16 | 11 | 3 | 0 | 0 | 2 |
| 11 | admin | 13 | 4 | 3 | 3 | 2 | 1 |
| 12 | invitations | 13 | 4 | 6 | 2 | 0 | 1 |
| 13 | notifications | 11 | 5 | 4 | 1 | 0 | 1 |
| 14 | archive | 11 | 11 | 0 | 0 | 0 | 0 |
| 15 | files | 8 | 2 | 4 | 0 | 0 | 2 |
| 16 | planning | 8 | 5 | 2 | 1 | 0 | 0 |
| 17 | sessions | 6 | 2 | 2 | 2 | 0 | 0 |
| 18 | presentations | 5 | 4 | 1 | 0 | 0 | 0 |
| 19 | registrations | 5 | 4 | 0 | 1 | 0 | 0 |
| 20 | venues | 5 | 3 | 1 | 1 | 0 | 0 |

### 2.2 Identified Redundancy Patterns


#### Pattern 1: Over-Fragmented GET Endpoints
Multiple GET endpoints retrieving sub-resources that could use field selection:

```
# Example: Events Domain
GET /api/v1/events/{id}
GET /api/v1/events/{id}/venue
GET /api/v1/events/{id}/catering
GET /api/v1/events/{id}/speakers
GET /api/v1/events/{id}/sessions
GET /api/v1/events/{id}/attendees
GET /api/v1/events/{id}/workflow
# ... and 25+ more sub-resource endpoints
```

**Impact**: Events (30+ endpoints), Partners (25+ endpoints), Speakers (20+ endpoints)

#### Pattern 2: Redundant Search/Filter Endpoints
Multiple specialized search endpoints per resource:

```
# Example: Topics Domain
GET /api/v1/topics/search?query={text}
GET /api/v1/topics/popular
GET /api/v1/topics/trending
GET /api/v1/topics/backlog/filter
GET /api/v1/topics/community-trends
GET /api/v1/topics/industry-trends
```

**Impact**: Topics (8+ search variants), Content (6+ search variants), Events (5+ search variants)

#### Pattern 3: Inconsistent CRUD Patterns
CRUD operations with varying conventions:

```
# Mixing PUT for full updates and PATCH for partials
PUT /api/v1/events/{id}
PATCH /api/v1/events/{id}/title
PATCH /api/v1/events/{id}/description
PUT /api/v1/events/{id}/settings
PUT /api/v1/events/{id}/workflow/steps/{stepNumber}/complete
```

#### Pattern 4: Missing Bulk Operations
Individual operations that could be batched:

```
# Current: Individual operations
POST /api/v1/partners/{partnerId}/topics/votes/add
DELETE /api/v1/partners/{partnerId}/topics/votes/{topicId}

# Could be: Batch operations
PATCH /api/v1/partners/{partnerId}/topics/votes (with array of changes)
```

---

## 3. Consolidated API Design by Domain


### 3.1 EVENTS Domain

**Current Endpoints**: 130
**Proposed Endpoints**: 25
**Reduction**: 80% (130 → 25)

#### Proposed Consolidated APIs

```
GET /api/v1/events?filter={}&sort={}&page={}&include={}
GET /api/v1/events/{id}?include=venue,catering,speakers,sessions,attendees,workflow,metrics
POST /api/v1/events
PUT /api/v1/events/{id}
PATCH /api/v1/events/{id}
DELETE /api/v1/events/{id}
POST /api/v1/events/{id}/publish
POST /api/v1/events/{id}/unpublish
GET /api/v1/events/{id}/workflow
PUT /api/v1/events/{id}/workflow
POST /api/v1/events/{id}/workflow/advance
GET /api/v1/events/{id}/registrations?filter={}&page={}
POST /api/v1/events/{id}/registrations
PUT /api/v1/events/{id}/registrations/{regId}
DELETE /api/v1/events/{id}/registrations/{regId}
GET /api/v1/events/{id}/invitations?filter={}&page={}
POST /api/v1/events/{id}/invitations
PUT /api/v1/events/{id}/invitations/{invId}
DELETE /api/v1/events/{id}/invitations/{invId}
GET /api/v1/events/{id}/notifications?filter={}
POST /api/v1/events/{id}/notifications
GET /api/v1/events/{id}/archive
POST /api/v1/events/{id}/export
GET /api/v1/events/{id}/calendar.ics
GET /api/v1/events/{id}/public
```

#### Sample Endpoints Being Replaced

```
GET /api/v1/events/{id}/venue
GET /api/v1/events/{id}/catering
GET /api/v1/events/{id}/speakers
GET /api/v1/events/{id}/sessions
GET /api/v1/events/{id}/attendees
GET /api/v1/events/{id}/workflow/status
... and 100+ more endpoints
```

#### Affected Wireframes

- `story-1.16-event-detail-edit.md`
- `story-1.16-event-management-dashboard.md`
- `story-2.3-basic-publishing-engine.md`
- `story-2.4-current-event-landing.md`
- `story-1.18-historical-archive.md`

#### Migration Notes

- **Sub-resource access**: Use `?include=venue,catering,speakers` parameter
- **Filtering**: Use `?filter={"status":"published","year":2025}` JSON filter
- **Pagination**: Standard `?page=1&limit=50` parameters
- **Workflow actions**: Consolidate into `/workflow/advance` with state machine
- **No breaking changes**: Old endpoints can be deprecated gradually

### 3.2 PARTNERS Domain

**Current Endpoints**: 109
**Proposed Endpoints**: 20
**Reduction**: 81% (109 → 20)

#### Proposed Consolidated APIs

```
GET /api/v1/partners?filter={}&sort={}&page={}
GET /api/v1/partners/{id}?include=analytics,settings,meetings,votes,employees
POST /api/v1/partners
PUT /api/v1/partners/{id}
PATCH /api/v1/partners/{id}
DELETE /api/v1/partners/{id}
GET /api/v1/partners/{id}/analytics?metrics={}&timeframe={}
POST /api/v1/partners/{id}/analytics/export
GET /api/v1/partners/{id}/meetings?filter={}&page={}
POST /api/v1/partners/{id}/meetings
PUT /api/v1/partners/{id}/meetings/{meetingId}
GET /api/v1/partners/{id}/topics/votes?sessionId={}
PUT /api/v1/partners/{id}/topics/votes
PATCH /api/v1/partners/{id}/topics/votes
GET /api/v1/partners/{id}/employees?filter={}&page={}
GET /api/v1/partners/{id}/notifications?filter={}
PUT /api/v1/partners/{id}/notifications/read
POST /api/v1/partners/{id}/export
GET /api/v1/partners/{id}/reports?type={}
POST /api/v1/partners/{id}/reports
```

#### Sample Endpoints Being Replaced

```
GET /api/v1/partners/{id}/analytics/summary
GET /api/v1/partners/{id}/analytics/detailed
GET /api/v1/partners/{id}/analytics/attendance-trends
GET /api/v1/partners/{id}/meetings/upcoming
GET /api/v1/partners/{id}/meetings/history
... and 85+ more endpoints
```

#### Affected Wireframes

- `story-6.1-partner-analytics-dashboard.md`
- `story-6.2-partner-meetings.md`
- `story-6.3-partner-directory.md`
- `story-6.4-topic-voting.md`

#### Migration Notes

- **Analytics consolidation**: Single endpoint with `?metrics=attendance,engagement&timeframe=month`
- **Voting**: Batch updates via PATCH with array of vote changes
- **Meetings**: Standard list/detail pattern with filtering
- **Employee data**: Use `?include=employees` on partner detail endpoint

### 3.3 SPEAKERS Domain

**Current Endpoints**: 70
**Proposed Endpoints**: 18
**Reduction**: 74% (70 → 18)

#### Proposed Consolidated APIs

```
GET /api/v1/speakers?filter={}&sort={}&page={}
GET /api/v1/speakers/{id}?include=profile,events,materials,timeline,feedback
POST /api/v1/speakers
PUT /api/v1/speakers/{id}
PATCH /api/v1/speakers/{id}
DELETE /api/v1/speakers/{id}
GET /api/v1/speakers/{id}/events?filter={}&page={}
GET /api/v1/speakers/{id}/events/{eventId}?include=timeline,materials,checklist
POST /api/v1/speakers/{id}/events/{eventId}/materials
PUT /api/v1/speakers/{id}/events/{eventId}/materials/{materialId}
DELETE /api/v1/speakers/{id}/events/{eventId}/materials/{materialId}
GET /api/v1/speakers/{id}/feedback?filter={}
POST /api/v1/speakers/{id}/ratings
GET /api/v1/speakers/{id}/availability
PUT /api/v1/speakers/{id}/availability
GET /api/v1/speakers/{id}/history?page={}
POST /api/v1/speakers/{id}/export
GET /api/v1/speakers/directory?filter={}&page={}
```

#### Sample Endpoints Being Replaced

```
GET /api/v1/speakers/{id}/profile
GET /api/v1/speakers/{id}/events/{eventId}/timeline
GET /api/v1/speakers/{id}/events/{eventId}/materials
GET /api/v1/speakers/{id}/events/{eventId}/checklist
GET /api/v1/speakers/{id}/speaking-history
... and 50+ more endpoints
```

#### Affected Wireframes

- `story-3.1-speaker-matching-interface.md`
- `story-3.3-presentation-upload.md`
- `story-3.5-event-timeline.md`
- `story-7.1-speaker-profile-management.md`

#### Migration Notes

- **Event materials**: Nested under `/events/{eventId}/materials` resource
- **Timeline integration**: Use `?include=timeline` parameter
- **Material versioning**: Standard version management pattern
- **Profile expansion**: Single profile endpoint with optional inclusions

### 3.4 CONTENT Domain

**Current Endpoints**: 63
**Proposed Endpoints**: 15
**Reduction**: 76% (63 → 15)

#### Proposed Consolidated APIs

```
GET /api/v1/content?filter={}&sort={}&page={}&facets={}
GET /api/v1/content/{id}?include=analytics,reviews,versions,related
POST /api/v1/content
PUT /api/v1/content/{id}
PATCH /api/v1/content/{id}
DELETE /api/v1/content/{id}
GET /api/v1/content/{id}/download
GET /api/v1/content/{id}/preview
GET /api/v1/content/{id}/versions?page={}
POST /api/v1/content/{id}/versions
POST /api/v1/content/{id}/reviews
GET /api/v1/content/{id}/analytics?metrics={}
POST /api/v1/content/bulk
GET /api/v1/content/recommendations?userId={}
GET /api/v1/content/discovery?context={}
```

#### Sample Endpoints Being Replaced

```
GET /api/v1/content/{id}/analytics
GET /api/v1/content/{id}/reviews
GET /api/v1/content/{id}/versions
GET /api/v1/content/{id}/related
GET /api/v1/content/search
GET /api/v1/content/search/advanced
... and 45+ more endpoints
```

#### Affected Wireframes

- `story-3.3-content-library-repository.md`
- `story-5.1-content-discovery.md`
- `story-5.1-content-viewer.md`

#### Migration Notes

- **Discovery**: Single `/content?filter={}` with rich filtering
- **Analytics**: Use `?include=analytics` or dedicated analytics endpoint with metrics selection
- **Versioning**: Standard REST versioning under `/versions` sub-resource
- **Bulk operations**: Single `/content/bulk` endpoint for batch actions

### 3.5 TOPICS Domain

**Current Endpoints**: 52
**Proposed Endpoints**: 12
**Reduction**: 76% (52 → 12)

#### Proposed Consolidated APIs

```
GET /api/v1/topics?filter={}&sort={}&page={}&analytics={}
GET /api/v1/topics/{id}?include=details,history,feedback,insights,similarity
POST /api/v1/topics
PUT /api/v1/topics/{id}
PATCH /api/v1/topics/{id}
DELETE /api/v1/topics/{id}
GET /api/v1/topics/{id}/analytics?metrics={}
GET /api/v1/topics/voting-sessions/{sessionId}?include=topics,results
PUT /api/v1/topics/voting-sessions/{sessionId}/votes
POST /api/v1/topics/compare
POST /api/v1/topics/{id}/export
GET /api/v1/topics/trends?type={}&timeframe={}
```

#### Sample Endpoints Being Replaced

```
GET /api/v1/topics/{id}/details
GET /api/v1/topics/{id}/history
GET /api/v1/topics/{id}/ai-insights
GET /api/v1/topics/{id}/similarity
GET /api/v1/topics/popular
GET /api/v1/topics/trending
GET /api/v1/topics/community-trends
... and 35+ more endpoints
```

#### Affected Wireframes

- `story-2.2-topic-backlog-management.md`
- `story-6.4-topic-voting.md`
- `story-6.1-all-topics-browser.md`

#### Migration Notes

- **Trend analysis**: Single `/topics/trends?type=community|industry&timeframe=month`
- **AI insights**: Use `?include=insights` parameter
- **Voting**: Consolidate into voting-session resource
- **History tracking**: Standard pagination on history endpoint

---

## 4. Wireframe Impact Analysis

### 4.1 High-Impact Wireframes (Requiring Most Changes)


| Wireframe | Current API Calls | Estimated Post-Consolidation | Change Required |
|-----------|------------------|------------------------------|-----------------|

---

## 5. API Design Standards & Conventions


### 5.1 Resource Naming Conventions

- Use plural nouns for collections: `/events`, `/speakers`, `/topics`
- Use kebab-case for multi-word resources: `/partner-meetings`, `/voting-sessions`
- Avoid verbs in URLs except for actions that aren't CRUD: `/publish`, `/advance`, `/export`

### 5.2 Query Parameter Standards

```
# Filtering
GET /api/v1/resource?filter={"field":"value","field2":{"$gt":100}}

# Sorting
GET /api/v1/resource?sort=-createdAt,name  # - prefix for descending

# Pagination
GET /api/v1/resource?page=1&limit=50
# Response includes: {data: [...], pagination: {page, limit, total, hasMore}}

# Field Selection/Inclusion
GET /api/v1/resource/{id}?include=subresource1,subresource2
GET /api/v1/resource/{id}?fields=field1,field2  # Sparse fieldsets

# Expansion
GET /api/v1/resource?expand=relation1,relation2  # Expand referenced resources
```

### 5.3 HTTP Method Usage

- **GET**: Retrieve resource(s), idempotent, cacheable
- **POST**: Create new resource or trigger action
- **PUT**: Full replacement of resource (requires all fields)
- **PATCH**: Partial update of resource (only changed fields)
- **DELETE**: Remove resource

### 5.4 Standard Response Format

```json
{
  "success": true,
  "data": { /* resource data */ },
  "meta": {
    "requestId": "uuid",
    "timestamp": "2025-01-15T10:30:00Z"
  }
}
```

### 5.5 Error Response Format

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid input data",
    "details": [
      {
        "field": "email",
        "message": "Invalid email format",
        "code": "INVALID_FORMAT"
      }
    ]
  },
  "meta": {
    "requestId": "uuid",
    "timestamp": "2025-01-15T10:30:00Z"
  }
}
```

### 5.6 Pagination Standards

**Cursor-based for large datasets**:
```json
{
  "data": [...],
  "pagination": {
    "cursor": "opaque-cursor-string",
    "hasMore": true,
    "total": 1500  // Optional, can be expensive
  }
}
```

**Offset-based for smaller datasets**:
```json
{
  "data": [...],
  "pagination": {
    "page": 1,
    "limit": 50,
    "total": 150,
    "totalPages": 3,
    "hasMore": true
  }
}
```

### 5.7 Versioning Strategy

- **URL versioning**: `/api/v1/`, `/api/v2/`
- Version in URL path for major breaking changes
- Use content negotiation headers for minor versions: `Accept: application/vnd.batbern.v1.2+json`
- Maintain at least 2 major versions during migration period
- Deprecation notices in response headers: `Sunset: Sat, 01 Jan 2026 00:00:00 GMT`

---

## 6. Implementation Roadmap


### Phase 1: Foundation (Weeks 1-2)

**Objectives**: Establish API standards and core infrastructure

**Tasks**:
1. Define comprehensive API specification (OpenAPI 3.0)
2. Implement standard middleware:
   - Request validation
   - Error handling
   - Response formatting
   - Pagination helpers
3. Set up API versioning infrastructure
4. Create query parameter parsing utilities
   - Filter parser (JSON filter syntax)
   - Sort parser
   - Include/expand parser
5. Establish API documentation framework

**Deliverables**:
- API standards document (this document, finalized)
- OpenAPI 3.0 specification (initial)
- Core middleware implementation
- Query parameter utilities

### Phase 2: Core Resources (Weeks 3-6)

**Objectives**: Implement consolidated APIs for top 5 resources

**Priority Order**:
1. **Events** (Week 3-4)
   - Consolidate 130 → 25 endpoints
   - Implement `?include=` for sub-resources
   - Migrate event management dashboard
   - Migrate event detail/edit screens

2. **Partners** (Week 4-5)
   - Consolidate 109 → 20 endpoints
   - Implement analytics consolidation
   - Migrate partner dashboard
   - Migrate meeting management

3. **Speakers** (Week 5)
   - Consolidate 70 → 18 endpoints
   - Implement event materials nesting
   - Migrate speaker profile screens

4. **Content** (Week 6)
   - Consolidate 63 → 15 endpoints
   - Implement discovery/search consolidation
   - Migrate content library

5. **Topics** (Week 6)
   - Consolidate 52 → 12 endpoints
   - Implement voting consolidation
   - Migrate topic management

**Deliverables**:
- Consolidated APIs for top 5 resources
- Updated wireframes with new API calls
- Frontend adapters/services updated
- Unit tests for all new endpoints
- Integration tests

### Phase 3: Supporting Resources (Weeks 7-9)

**Objectives**: Consolidate remaining resources

**Resources**:
- Users (46 → 12 endpoints)
- Companies (42 → 10 endpoints)
- Organizers (53 → 15 endpoints)
- Attendees (16 → 8 endpoints)
- Notifications (20 → 6 endpoints)
- Auth (18 → 8 endpoints)
- Other resources (remaining)

**Deliverables**:
- All resources consolidated
- Complete API documentation
- Migration guides for frontend teams
- Performance benchmarks

### Phase 4: Migration & Deprecation (Weeks 10-12)

**Objectives**: Migrate all wireframes, deprecate old endpoints

**Tasks**:
1. Update all 49 wireframes with new API calls
2. Frontend migration:
   - Update API clients/services
   - Update data fetching hooks
   - Update forms and mutations
3. Add deprecation headers to old endpoints
4. Monitor usage of deprecated endpoints
5. Create migration tracking dashboard

**Deliverables**:
- All wireframes updated
- All frontend code migrated
- Deprecation notices active
- Migration completion report

### Phase 5: Optimization & Cleanup (Weeks 13-14)

**Objectives**: Optimize performance, remove deprecated code

**Tasks**:
1. Performance optimization:
   - Add caching for frequently accessed resources
   - Optimize database queries (N+1 queries)
   - Add indices for common filters
2. Remove deprecated endpoints
3. Final documentation review
4. Security audit
5. Load testing

**Deliverables**:
- Optimized API performance
- No deprecated endpoints
- Complete API documentation
- Security audit report
- Load test results

---

## 7. Summary Statistics


### Before and After Comparison

| Metric | Before | After | Change |
|--------|--------|-------|--------|
| Total Unique Endpoints | 731 | ~250 | -66% |
| GET Endpoints | 381 | ~150 | -60% |
| POST Endpoints | 214 | ~60 | -65% |
| PUT Endpoints | 90 | ~25 | -70% |
| PATCH Endpoints | 4 | ~8 | +100% |
| DELETE Endpoints | 42 | ~15 | -50% |

### Consolidation by Domain

| Domain | Current | Proposed | Reduction |
|--------|---------|----------|-----------|
| Events | 130 | 25 | 80% |
| Partners | 109 | 20 | 81% |
| Speakers | 70 | 18 | 74% |
| Content | 63 | 15 | 76% |
| Topics | 52 | 12 | 76% |
| Other Resources | 307 | 160 | 47% |
| **TOTAL** | **731** | **~250** | **66%** |

### Key Benefits

1. **Reduced Complexity**: 66% fewer endpoints to maintain and document
2. **Improved Consistency**: Standardized patterns across all resources
3. **Better Performance**: Fewer HTTP requests via `?include=` parameter
4. **Enhanced Flexibility**: Rich filtering/sorting without new endpoints
5. **Easier Evolution**: Adding new resources follows established patterns
6. **Better Developer Experience**: Predictable, intuitive API design
7. **Reduced Frontend Code**: Reusable API client utilities
8. **Lower Maintenance**: Fewer endpoints = fewer potential bugs

### Implementation Risks & Mitigation

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Breaking changes impact in-progress work | High | Medium | Maintain old endpoints during migration |
| Performance degradation with `?include=` | Medium | Low | Implement efficient query optimization, DataLoader pattern |
| Complex filter syntax learning curve | Low | Medium | Provide excellent documentation, examples |
| Frontend migration effort underestimated | High | Medium | Incremental migration, create adapter layer |
| Regression in existing functionality | High | Low | Comprehensive test coverage, gradual rollout |

---

## 8. Conclusion


This analysis of 731 API endpoints across 49 wireframes reveals significant consolidation opportunities. By implementing RESTful design principles, standardized query patterns, and resource-oriented architecture, we can reduce the API surface by 66% while improving consistency, maintainability, and developer experience.

The proposed consolidation maintains all required functionality while providing:
- **Simpler API**: 250 endpoints vs 731 (66% reduction)
- **Consistent patterns**: Standard CRUD, filtering, pagination across all resources
- **Better performance**: Fewer HTTP requests via smart field inclusion
- **Future-proof design**: Easy to extend without endpoint proliferation

The 14-week implementation roadmap provides a structured approach to migration with minimal disruption to ongoing development. By following the phased approach and maintaining backward compatibility during migration, we can achieve these benefits while managing risk effectively.
---

## Appendix A: Concrete Consolidation Examples

### Example 1: Events Domain - Sub-Resource Consolidation

**Wireframe**: `story-1.16-event-detail-edit.md` (Event Detail/Edit Screen)

**Current Fragmented Approach** (10+ separate API calls on page load):
```
GET /api/v1/events/{id}                    # Basic event info
GET /api/v1/events/{id}/venue              # Venue details
GET /api/v1/events/{id}/catering           # Catering info
GET /api/v1/events/{id}/speakers           # Speaker list
GET /api/v1/events/{id}/sessions           # Session list
GET /api/v1/events/{id}/attendees          # Attendee count
GET /api/v1/events/{id}/workflow           # Workflow status
GET /api/v1/events/{id}/metrics            # Key metrics
GET /api/v1/events/{id}/topics             # Assigned topics
GET /api/v1/events/{id}/settings           # Event settings
```

**Consolidated Approach** (1-2 API calls):
```
# Single call with all required data
GET /api/v1/events/{id}?include=venue,catering,speakers,sessions,workflow,metrics,topics

# Response structure:
{
  "data": {
    "id": "evt_123",
    "title": "Spring Conference 2025",
    "description": "A comprehensive Spring conference...",
    "eventNumber": 54,
    "eventDate": "2025-03-15",
    "status": "published",
    
    // Included sub-resources
    "venue": {
      "id": "ven_456",
      "name": "Kursaal Bern",
      "address": "Kornhausstrasse 3, 3013 Bern",
      "capacity": 200,
      "bookingStatus": "confirmed"
    },
    
    "catering": {
      "provider": null,
      "dietaryRequirements": {
        "vegetarian": 5,
        "vegan": 2,
        "glutenFree": 3
      }
    },
    
    "workflow": {
      "currentStep": 12,
      "totalSteps": 16,
      "progress": 80,
      "status": "Agenda Finalization"
    },
    
    "metrics": {
      "speakersConfirmed": 12,
      "speakersTotal": 12,
      "topicsAssigned": 3,
      "materialsPending": 2,
      "registrations": 87,
      "capacity": 150
    },
    
    "speakers": [...],
    "topics": [...]
  }
}
```

**Benefits**:
- 90% reduction in HTTP requests (10 → 1)
- Single loading state for entire page
- Atomic data consistency
- Reduced latency (fewer round trips)

---

### Example 2: Topics Domain - Search Consolidation

**Wireframes**: `story-2.2-topic-backlog-management.md`, `story-6.1-all-topics-browser.md`

**Current Fragmented Approach** (8+ specialized endpoints):
```
GET /api/v1/topics/search?query={text}           # Text search
GET /api/v1/topics/popular                        # Popular topics
GET /api/v1/topics/trending                       # Trending topics
GET /api/v1/topics/community-trends               # Community trends
GET /api/v1/topics/industry-trends                # Industry trends
GET /api/v1/topics/backlog/filter                 # Backlog filtering
GET /api/v1/topics/voting-session/{id}/all-topics # Voting topics
GET /api/v1/topics/voting-session/{id}/trending   # Session trending
```

**Consolidated Approach** (1 flexible endpoint):
```
# All searches through single endpoint with rich filtering

# Text search
GET /api/v1/topics?filter={"title":{"$contains":"microservices"}}

# Popular topics (by votes)
GET /api/v1/topics?sort=-votes&limit=10

# Trending topics (recent vote increase)
GET /api/v1/topics?filter={"trending":true}&sort=-trendScore

# Community trends (aggregate view)
GET /api/v1/topics/trends?type=community&timeframe=month

# Industry trends (aggregate view)
GET /api/v1/topics/trends?type=industry&timeframe=year

# Backlog filtering
GET /api/v1/topics?filter={"status":"backlog","category":"cloud"}&sort=-lastUsed

# Voting session topics
GET /api/v1/topics?filter={"votingSessionId":"sess_123"}&include=votes

# Complex filter example
GET /api/v1/topics?filter={
  "$and": [
    {"category": "architecture"},
    {"votes": {"$gte": 10}},
    {"lastUsed": {"$lte": "2023-01-01"}}
  ]
}&sort=-votes&page=1&limit=20
```

**Benefits**:
- Single endpoint handles all use cases
- No new endpoints needed for new filter combinations
- Consistent query syntax across platform
- Easy to add new filter criteria without API changes

---

### Example 3: Partners Domain - Analytics Consolidation

**Wireframe**: `story-6.1-partner-analytics-dashboard.md`

**Current Fragmented Approach** (6+ analytics endpoints):
```
GET /api/v1/partners/{id}/analytics/summary
GET /api/v1/partners/{id}/analytics/detailed
GET /api/v1/partners/{id}/analytics/attendance-trends
GET /api/v1/partners/{id}/analytics/employee-breakdown
GET /api/v1/partners/{id}/employees/analytics/overview
GET /api/v1/partners/{id}/employees/analytics/events
GET /api/v1/partners/{id}/employees/analytics/funnel
```

**Consolidated Approach** (1 flexible analytics endpoint):
```
# Single analytics endpoint with metric selection

# Dashboard summary
GET /api/v1/partners/{id}/analytics?metrics=summary

# Detailed analytics with multiple metrics
GET /api/v1/partners/{id}/analytics?metrics=attendance,employees,engagement&timeframe=year

# Attendance trends over time
GET /api/v1/partners/{id}/analytics?metrics=attendance&groupBy=month&timeframe=year

# Employee breakdown
GET /api/v1/partners/{id}/analytics?metrics=employees&breakdown=role,engagement

# Response structure:
{
  "data": {
    "partnerId": "partner_123",
    "timeframe": "year",
    "metrics": {
      "attendance": {
        "total": 156,
        "byMonth": [...],
        "trend": "+12%"
      },
      "employees": {
        "total": 45,
        "active": 38,
        "byRole": {...},
        "engagement": {...}
      },
      "engagement": {
        "score": 85,
        "topicVotes": 234,
        "eventAttendance": 156
      }
    }
  }
}
```

**Benefits**:
- Flexible metric selection
- Single endpoint for all analytics needs
- Easy to add new metrics without new endpoints
- Consistent response format

---

### Example 4: Speakers Domain - Event Materials Nesting

**Wireframe**: `story-3.3-presentation-upload.md`, `story-3.5-event-timeline.md`

**Current Fragmented Approach** (multiple parallel hierarchies):
```
GET /api/v1/speakers/{speakerId}/events/{eventId}/timeline
GET /api/v1/speakers/{speakerId}/events/{eventId}/materials/draft
GET /api/v1/speakers/{speakerId}/events/{eventId}/presentation
GET /api/v1/speakers/{speakerId}/events/{eventId}/checklist
GET /api/v1/speakers/{speakerId}/events/{eventId}/supplementary-materials
POST /api/v1/speakers/{speakerId}/events/{eventId}/presentation/upload
PUT /api/v1/speakers/{speakerId}/events/{eventId}/materials/draft
```

**Consolidated Approach** (clean resource nesting):
```
# Get event details with optional inclusions
GET /api/v1/speakers/{id}/events/{eventId}?include=timeline,materials,checklist

# Manage materials under unified endpoint
GET /api/v1/speakers/{id}/events/{eventId}/materials
POST /api/v1/speakers/{id}/events/{eventId}/materials
PUT /api/v1/speakers/{id}/events/{eventId}/materials/{materialId}
DELETE /api/v1/speakers/{id}/events/{eventId}/materials/{materialId}

# Response includes all material types
{
  "data": {
    "presentation": {
      "id": "mat_123",
      "type": "presentation",
      "status": "submitted",
      "versions": [...]
    },
    "supplementary": [
      {
        "id": "mat_124",
        "type": "supplementary",
        "filename": "demo-code.zip"
      }
    ],
    "checklist": {
      "completed": 8,
      "total": 10,
      "items": [...]
    }
  }
}
```

**Benefits**:
- Clear resource hierarchy
- Standard CRUD operations
- Single material management interface
- Unified versioning across material types

---

## Appendix B: Filter Syntax Reference

### JSON Filter Syntax

The consolidated API uses MongoDB-style JSON filter syntax for maximum flexibility:

```javascript
// Basic equality
{"status": "published"}

// Comparison operators
{"votes": {"$gte": 10}}              // Greater than or equal
{"eventDate": {"$lte": "2025-12-31"}} // Less than or equal
{"registrations": {"$gt": 50}}        // Greater than
{"capacity": {"$lt": 200}}            // Less than

// String operators
{"title": {"$contains": "cloud"}}     // Case-insensitive substring
{"email": {"$startsWith": "john"}}    // Starts with
{"tag": {"$in": ["tech", "cloud"]}}   // In array

// Logical operators
{
  "$and": [
    {"status": "published"},
    {"votes": {"$gte": 10}}
  ]
}

{
  "$or": [
    {"category": "cloud"},
    {"category": "architecture"}
  ]
}

{"status": {"$not": "archived"}}

// Nested field access
{"venue.capacity": {"$gte": 100}}
{"speaker.company.name": "Accenture"}

// Array operations
{"tags": {"$contains": "microservices"}} // Array contains value
{"speakers": {"$size": 3}}               // Array length

// Date operations
{"createdAt": {"$between": ["2025-01-01", "2025-12-31"]}}
{"eventDate": {"$isNull": false}}
```

### Sort Syntax

```
# Single field ascending
?sort=createdAt

# Single field descending (use - prefix)
?sort=-votes

# Multiple fields
?sort=-votes,createdAt

# Nested fields
?sort=venue.capacity,-eventDate
```

### Include/Expand Syntax

```
# Include sub-resources (server-side join/expansion)
?include=venue,speakers,topics

# Limit fields returned (sparse fieldsets)
?fields=id,title,eventDate,status

# Expand references (populate foreign keys)
?expand=organizerId,categoryId

# Combine
?include=venue&fields=id,title,venue&expand=organizerId
```

---

## Appendix C: Implementation Code Examples

### Backend: Filter Parser Middleware

```typescript
// Example Express middleware for parsing filter parameter

import { Request, Response, NextFunction } from 'express';

interface FilterOperators {
  $eq?: any;
  $ne?: any;
  $gt?: any;
  $gte?: any;
  $lt?: any;
  $lte?: any;
  $in?: any[];
  $nin?: any[];
  $contains?: string;
  $startsWith?: string;
  $endsWith?: string;
  $between?: [any, any];
  $isNull?: boolean;
}

export function parseFilterMiddleware(req: Request, res: Response, next: NextFunction) {
  try {
    // Parse filter parameter
    const filterParam = req.query.filter as string;
    
    if (filterParam) {
      // Parse JSON filter
      const filter = JSON.parse(filterParam);
      
      // Validate and sanitize filter
      req.parsedFilter = validateFilter(filter);
    }
    
    // Parse sort parameter
    const sortParam = req.query.sort as string;
    if (sortParam) {
      req.parsedSort = parseSort(sortParam);
    }
    
    // Parse include parameter
    const includeParam = req.query.include as string;
    if (includeParam) {
      req.parsedInclude = includeParam.split(',').map(s => s.trim());
    }
    
    next();
  } catch (error) {
    res.status(400).json({
      success: false,
      error: {
        code: 'INVALID_FILTER',
        message: 'Invalid filter syntax',
        details: error.message
      }
    });
  }
}

function validateFilter(filter: any): any {
  // Implement filter validation and sanitization
  // - Check for allowed fields
  // - Validate operators
  // - Prevent injection attacks
  // - Convert to database-specific query format
  
  // Example: Convert to Prisma where clause
  return convertToWhereClause(filter);
}

function parseSort(sort: string): Array<{field: string, direction: 'asc' | 'desc'}> {
  return sort.split(',').map(field => {
    const trimmed = field.trim();
    const descending = trimmed.startsWith('-');
    return {
      field: descending ? trimmed.substring(1) : trimmed,
      direction: descending ? 'desc' : 'asc'
    };
  });
}
```

### Backend: Include/Expansion Handler

```typescript
// Example Prisma query builder with include support

interface IncludeOptions {
  include?: string[];
  fields?: string[];
  expand?: string[];
}

export async function getEventWithIncludes(
  eventId: string,
  options: IncludeOptions
) {
  const { include, fields, expand } = options;
  
  // Build Prisma include object
  const prismaInclude: any = {};
  
  if (include) {
    for (const relation of include) {
      switch (relation) {
        case 'venue':
          prismaInclude.venue = true;
          break;
        case 'speakers':
          prismaInclude.speakers = {
            include: {
              user: true // Expand speaker details
            }
          };
          break;
        case 'sessions':
          prismaInclude.sessions = {
            include: {
              speaker: true,
              topic: true
            }
          };
          break;
        case 'workflow':
          prismaInclude.workflowSteps = {
            orderBy: { stepNumber: 'asc' }
          };
          break;
        case 'metrics':
          // Computed field, handled after query
          break;
      }
    }
  }
  
  // Build select for sparse fieldsets
  const select = fields ? buildSelectObject(fields) : undefined;
  
  // Execute query
  const event = await prisma.event.findUnique({
    where: { id: eventId },
    include: prismaInclude,
    select
  });
  
  if (!event) {
    throw new Error('Event not found');
  }
  
  // Add computed fields
  if (include?.includes('metrics')) {
    event.metrics = await computeEventMetrics(eventId);
  }
  
  return event;
}

async function computeEventMetrics(eventId: string) {
  const [speakers, registrations, topics] = await Promise.all([
    prisma.eventSpeaker.count({ where: { eventId } }),
    prisma.registration.count({ where: { eventId } }),
    prisma.eventTopic.count({ where: { eventId } })
  ]);
  
  return {
    speakersConfirmed: speakers,
    registrations,
    topicsAssigned: topics
  };
}
```

### Frontend: React Query Hook with Include Support

```typescript
// Example React Query hook for consolidated API

import { useQuery } from '@tanstack/react-query';

interface UseEventOptions {
  include?: string[];
  enabled?: boolean;
}

export function useEvent(eventId: string, options: UseEventOptions = {}) {
  const { include = [], enabled = true } = options;
  
  return useQuery({
    queryKey: ['events', eventId, { include }],
    queryFn: async () => {
      const params = new URLSearchParams();
      
      if (include.length > 0) {
        params.set('include', include.join(','));
      }
      
      const response = await fetch(
        `/api/v1/events/${eventId}?${params.toString()}`,
        {
          headers: {
            'Authorization': `Bearer ${getAuthToken()}`,
            'Accept': 'application/json'
          }
        }
      );
      
      if (!response.ok) {
        throw new Error('Failed to fetch event');
      }
      
      const result = await response.json();
      return result.data;
    },
    enabled
  });
}

// Usage in component
function EventDetailPage({ eventId }: { eventId: string }) {
  const { data: event, isLoading, error } = useEvent(eventId, {
    include: ['venue', 'speakers', 'sessions', 'workflow', 'metrics']
  });
  
  if (isLoading) return <LoadingSpinner />;
  if (error) return <ErrorMessage error={error} />;
  
  return (
    <div>
      <h1>{event.title}</h1>
      
      {/* All data loaded in single request */}
      <VenueSection venue={event.venue} />
      <WorkflowProgress workflow={event.workflow} />
      <MetricsDisplay metrics={event.metrics} />
      <SpeakersList speakers={event.speakers} />
      <SessionsTable sessions={event.sessions} />
    </div>
  );
}
```

### Frontend: Flexible Filter Hook

```typescript
// Example filter hook for consolidated search

import { useQuery } from '@tanstack/react-query';

interface FilterOptions {
  filter?: Record<string, any>;
  sort?: string;
  page?: number;
  limit?: number;
}

export function useTopicsSearch(options: FilterOptions = {}) {
  const { filter = {}, sort = '-votes', page = 1, limit = 20 } = options;
  
  return useQuery({
    queryKey: ['topics', 'search', { filter, sort, page, limit }],
    queryFn: async () => {
      const params = new URLSearchParams();
      
      if (Object.keys(filter).length > 0) {
        params.set('filter', JSON.stringify(filter));
      }
      
      params.set('sort', sort);
      params.set('page', page.toString());
      params.set('limit', limit.toString());
      
      const response = await fetch(
        `/api/v1/topics?${params.toString()}`
      );
      
      const result = await response.json();
      return result;
    }
  });
}

// Usage examples
function TopicsPage() {
  // Popular topics
  const popular = useTopicsSearch({
    sort: '-votes',
    limit: 10
  });
  
  // Trending cloud topics
  const trending = useTopicsSearch({
    filter: {
      $and: [
        { category: 'cloud' },
        { trending: true }
      ]
    },
    sort: '-trendScore'
  });
  
  // Backlog topics needing refresh
  const stale = useTopicsSearch({
    filter: {
      status: 'backlog',
      lastUsed: { $lte: '2023-01-01' }
    },
    sort: '-votes'
  });
}
```

---

**End of Report**

