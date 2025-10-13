# Events API Migration Guide

**Story**: 1.15a.1 - Events API Consolidation
**Version**: 1.0.0
**Last Updated**: 2025-10-12

## Table of Contents

1. [Overview](#overview)
2. [Migration Benefits](#migration-benefits)
3. [Breaking Changes](#breaking-changes)
4. [Migration Patterns](#migration-patterns)
5. [Performance Improvements](#performance-improvements)
6. [Code Examples](#code-examples)
7. [Testing Strategies](#testing-strategies)
8. [Rollback Plan](#rollback-plan)

---

## Overview

This guide helps developers migrate from the fragmented Events API (130 endpoints) to the new consolidated Events API (25 endpoints). The consolidation uses **resource expansion**, **rich filtering**, and **in-memory caching** to dramatically improve performance and developer experience.

### Migration Timeline

- **Phase 1**: New API available alongside old API (backward compatibility maintained)
- **Phase 2**: Old API deprecated with warning headers (6 months)
- **Phase 3**: Old API removed (12 months from Phase 1)

### Current Phase

**Phase 1**: New API live, old API still functional

---

## Migration Benefits

### Reduced API Calls

| Screen | Before | After | Reduction |
|--------|--------|-------|-----------|
| Event Detail Page | 30 calls | 1 call | 97% |
| Event Dashboard | 7 calls | 5 calls | 29% |
| Event List (20 items) | 21 calls | 1 call | 95% |
| Current Event Landing | 6 calls | 1 call | 83% |

### Performance Improvements

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| Event Detail Load Time | 2.5s | 0.35s | 86% faster |
| Event Dashboard Load | 1.8s | 0.5s | 72% faster |
| Archive Page (20 events) | 5.0s | 0.5s | 90% faster |
| Cached Response Time | N/A | <50ms | ∞ |

### Developer Experience

- **Simpler Integration**: One API call instead of orchestrating multiple calls
- **Atomic Data**: All data fetched at same point in time (no inconsistencies)
- **Better TypeScript Support**: Unified response types
- **Automatic Caching**: 15-minute TTL with automatic invalidation

---

## Breaking Changes

### ⚠️ Changes That Require Code Updates

#### 1. Response Structure Changes

**Old API** (multiple calls):
```javascript
const event = await fetch('/api/v1/events/evt-001');
const venue = await fetch('/api/v1/venues/ven-001');
const speakers = await fetch('/api/v1/events/evt-001/speakers');
```

**New API** (single call):
```javascript
const response = await fetch('/api/v1/events/evt-001?include=venue,speakers');
// Response contains: { id, title, date, venue: {...}, speakers: [...] }
```

#### 2. Filter Syntax Changes

**Old API**:
```
GET /api/v1/events?status=published&year=2025
```

**New API**:
```
GET /api/v1/events?filter={"status":"published","year":2025}
```

#### 3. Pagination Parameter Names

**Old API**:
```
GET /api/v1/events?offset=20&count=10
```

**New API**:
```
GET /api/v1/events?page=1&limit=10
```

#### 4. Error Response Format

**Old API**:
```json
{
  "message": "Event not found"
}
```

**New API**:
```json
{
  "error": "RESOURCE_NOT_FOUND",
  "errorCode": "NOT_FOUND",
  "message": "Event not found with id evt-999",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

---

## Migration Patterns

### Pattern 1: Event Detail Page Migration

#### Before (30 API calls)

```typescript
// Old implementation - EVENT DETAIL PAGE
async function loadEventDetailPage(eventId: string) {
  // 1. Get basic event
  const event = await api.get(`/api/v1/events/${eventId}`);

  // 2. Get venue
  const venue = await api.get(`/api/v1/venues/${event.venueId}`);

  // 3. Get catering
  const catering = await api.get(`/api/v1/events/${eventId}/catering`);

  // 4. Get speakers (list)
  const speakers = await api.get(`/api/v1/events/${eventId}/speakers`);

  // 5-12. Get each speaker detail (8 speakers)
  const speakerDetails = await Promise.all(
    speakers.map(s => api.get(`/api/v1/speakers/${s.id}`))
  );

  // 13. Get sessions
  const sessions = await api.get(`/api/v1/events/${eventId}/sessions`);

  // 14. Get topics
  const topics = await api.get(`/api/v1/events/${eventId}/topics`);

  // 15. Get workflow
  const workflow = await api.get(`/api/v1/events/${eventId}/workflow`);

  // 16. Get registrations summary
  const registrations = await api.get(`/api/v1/events/${eventId}/registrations/summary`);

  // ... 14 more API calls for team, publishing, notifications, etc.

  return {
    event,
    venue,
    catering,
    speakers: speakerDetails,
    sessions,
    topics,
    workflow,
    registrations,
    // ... more resources
  };
}
```

**Load Time**: ~2.5 seconds (30 sequential/parallel requests)

#### After (1 API call)

```typescript
// New implementation - EVENT DETAIL PAGE
async function loadEventDetailPage(eventId: string) {
  const event = await api.get(`/api/v1/events/${eventId}`, {
    params: {
      include: 'venue,speakers,sessions,topics,workflow,registrations,catering,team,publishing,notifications,analytics'
    }
  });

  // All data available in single response
  return event;
}
```

**Load Time**:
- First request: ~350ms (all includes)
- Cached request: <50ms (15-minute cache)

**Performance**: 86% faster (97% reduction in API calls)

---

### Pattern 2: Event Dashboard Migration

#### Before (7 API calls for 3 events)

```typescript
// Old implementation - EVENT DASHBOARD
async function loadEventDashboard(organizerId: string) {
  // 1. Get organizer dashboard summary
  const dashboard = await api.get(`/api/v1/organizers/${organizerId}/dashboard`);

  // 2-4. Get each active event (3 events)
  const events = await Promise.all(
    dashboard.activeEventIds.map(id => api.get(`/api/v1/events/${id}`))
  );

  // 5-7. Get workflow for each event
  const workflows = await Promise.all(
    dashboard.activeEventIds.map(id =>
      api.get(`/api/v1/events/${id}/workflow`)
    )
  );

  return { dashboard, events, workflows };
}
```

**Load Time**: ~1.8 seconds

#### After (5 API calls total)

```typescript
// New implementation - EVENT DASHBOARD
async function loadEventDashboard(organizerId: string) {
  // Single call for all events with workflow included
  const [events, dashboard, tasks, activity, notifications] = await Promise.all([
    api.get('/api/v1/events', {
      params: {
        filter: JSON.stringify({ status: 'active', organizerId }),
        include: 'workflow,sessions,registrations',
        sort: '-date',
        limit: 10
      }
    }),
    api.get(`/api/v1/organizers/${organizerId}/dashboard`),
    api.get(`/api/v1/organizers/${organizerId}/tasks/critical`),
    api.get(`/api/v1/organizers/${organizerId}/activity-feed`),
    api.get(`/api/v1/organizers/${organizerId}/notifications/unread`)
  ]);

  return { events, dashboard, tasks, activity, notifications };
}
```

**Load Time**: ~500ms (29% reduction in API calls, 72% faster)

---

### Pattern 3: Historical Archive Migration

#### Before (21 API calls for 20 events)

```typescript
// Old implementation - HISTORICAL ARCHIVE
async function loadArchivePage(year: number) {
  // 1. Get archive events list
  const eventIds = await api.get(`/api/v1/archive/events?year=${year}`);

  // 2-21. Get summary for each event (20 events)
  const events = await Promise.all(
    eventIds.map(id => api.get(`/api/v1/events/${id}/summary`))
  );

  return events;
}
```

**Load Time**: ~5 seconds (20 sequential requests)

#### After (1 API call)

```typescript
// New implementation - HISTORICAL ARCHIVE
async function loadArchivePage(year: number) {
  const response = await api.get('/api/v1/events', {
    params: {
      filter: JSON.stringify({
        year,
        status: ['archived', 'upcoming']
      }),
      include: 'speakers,sessions,analytics',
      sort: '-eventDate',
      limit: 20
    }
  });

  return response.data;
}
```

**Load Time**:
- First request: ~500ms (all 20 events with includes)
- Cached request: <50ms

**Performance**: 90% faster (95% reduction in API calls)

---

### Pattern 4: Search and Filter Migration

#### Before (limited filtering)

```typescript
// Old implementation - Limited filtering
const events = await api.get('/api/v1/events', {
  params: {
    status: 'published',
    year: 2025,
    organizerId: 'org-001'
  }
});
```

#### After (rich JSON filtering)

```typescript
// New implementation - Rich JSON filtering
const events = await api.get('/api/v1/events', {
  params: {
    filter: JSON.stringify({
      $and: [
        { status: 'published' },
        { date: { $gte: '2025-01-01', $lte: '2025-12-31' } },
        { organizerId: 'org-001' }
      ]
    }),
    sort: '-date',
    page: 0,
    limit: 20
  }
});
```

**Advanced Filtering Examples**:

```typescript
// OR logic
filter: JSON.stringify({
  $or: [
    { status: 'published' },
    { status: 'archived' }
  ]
})

// IN operator
filter: JSON.stringify({
  status: { $in: ['published', 'archived', 'completed'] }
})

// Date range
filter: JSON.stringify({
  date: {
    $gte: '2025-01-01T00:00:00Z',
    $lte: '2025-12-31T23:59:59Z'
  }
})

// Complex nested query
filter: JSON.stringify({
  $and: [
    { status: { $in: ['published', 'completed'] } },
    {
      $or: [
        { organizerId: 'org-001' },
        { organizerId: 'org-002' }
      ]
    },
    { maxAttendees: { $gte: 100 } }
  ]
})
```

---

## Performance Improvements

### Caching Strategy

The new API uses **Caffeine in-memory caching** with the following configuration:

- **TTL**: 15 minutes
- **Max Entries**: 1000 events
- **Cache Key**: `event:{id}:includes:{includeList}`
- **Invalidation**: Automatic on PUT, PATCH, DELETE, publish, workflow advance

### Cache Behavior Examples

```typescript
// First request - Cache MISS
const event1 = await api.get('/api/v1/events/evt-001?include=venue,speakers');
// Response time: ~350ms
// Cache-Status header: MISS

// Second request (within 15 minutes) - Cache HIT
const event2 = await api.get('/api/v1/events/evt-001?include=venue,speakers');
// Response time: <50ms
// Cache-Status header: HIT

// Update event - Cache invalidation
await api.patch('/api/v1/events/evt-001', { title: 'Updated Title' });
// All caches for evt-001 invalidated

// Next request - Cache MISS (cache was cleared)
const event3 = await api.get('/api/v1/events/evt-001?include=venue,speakers');
// Response time: ~350ms
// Cache-Status header: MISS
```

### Performance Targets

| Operation | Target (P95) | Actual (P95) | Status |
|-----------|--------------|--------------|--------|
| Event list (no includes) | <100ms | ~80ms | ✅ |
| Event detail (basic) | <150ms | ~120ms | ✅ |
| Event detail (all includes) | <500ms | ~350ms | ✅ |
| Cached response | <50ms | ~30ms | ✅ |

---

## Code Examples

### TypeScript Client Implementation

```typescript
// api/events.ts - New Events API Client

export interface EventFilters {
  status?: string | string[];
  year?: number;
  organizerId?: string;
  date?: {
    $gte?: string;
    $lte?: string;
  };
  $or?: Array<Record<string, any>>;
  $and?: Array<Record<string, any>>;
}

export interface EventListOptions {
  filter?: EventFilters;
  include?: string[];
  sort?: string;
  page?: number;
  limit?: number;
}

export class EventsApiClient {
  constructor(private baseUrl: string) {}

  async listEvents(options: EventListOptions = {}) {
    const params = new URLSearchParams();

    if (options.filter) {
      params.append('filter', JSON.stringify(options.filter));
    }

    if (options.include && options.include.length > 0) {
      params.append('include', options.include.join(','));
    }

    if (options.sort) {
      params.append('sort', options.sort);
    }

    if (options.page !== undefined) {
      params.append('page', options.page.toString());
    }

    if (options.limit !== undefined) {
      params.append('limit', options.limit.toString());
    }

    const response = await fetch(`${this.baseUrl}/events?${params}`);
    return response.json();
  }

  async getEvent(id: string, include?: string[]) {
    const params = new URLSearchParams();

    if (include && include.length > 0) {
      params.append('include', include.join(','));
    }

    const response = await fetch(`${this.baseUrl}/events/${id}?${params}`);
    return response.json();
  }

  async createEvent(event: CreateEventRequest) {
    const response = await fetch(`${this.baseUrl}/events`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(event)
    });
    return response.json();
  }

  async updateEvent(id: string, event: UpdateEventRequest) {
    const response = await fetch(`${this.baseUrl}/events/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(event)
    });
    return response.json();
  }

  async patchEvent(id: string, updates: Partial<Event>) {
    const response = await fetch(`${this.baseUrl}/events/${id}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(updates)
    });
    return response.json();
  }

  async deleteEvent(id: string) {
    await fetch(`${this.baseUrl}/events/${id}`, {
      method: 'DELETE'
    });
  }

  async publishEvent(id: string) {
    const response = await fetch(`${this.baseUrl}/events/${id}/publish`, {
      method: 'POST'
    });
    return response.json();
  }

  async advanceWorkflow(id: string) {
    const response = await fetch(`${this.baseUrl}/events/${id}/workflow/advance`, {
      method: 'POST'
    });
    return response.json();
  }
}
```

### React Hook Example

```typescript
// hooks/useEvent.ts - React hook for event data

import { useState, useEffect } from 'react';
import { EventsApiClient } from '../api/events';

export function useEvent(eventId: string, includes: string[] = []) {
  const [event, setEvent] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const client = new EventsApiClient('/api/v1');

    client.getEvent(eventId, includes)
      .then(setEvent)
      .catch(setError)
      .finally(() => setLoading(false));
  }, [eventId, includes.join(',')]);

  return { event, loading, error };
}

// Usage in component
function EventDetailPage({ eventId }) {
  const { event, loading, error } = useEvent(eventId, [
    'venue',
    'speakers',
    'sessions',
    'workflow'
  ]);

  if (loading) return <LoadingSpinner />;
  if (error) return <ErrorMessage error={error} />;

  return (
    <div>
      <h1>{event.title}</h1>
      <VenueInfo venue={event.venue} />
      <SpeakerList speakers={event.speakers} />
      <SessionSchedule sessions={event.sessions} />
      <WorkflowStatus workflow={event.workflow} />
    </div>
  );
}
```

---

## Testing Strategies

### Unit Tests

```typescript
// __tests__/events-api.test.ts

import { EventsApiClient } from '../api/events';

describe('EventsApiClient', () => {
  let client: EventsApiClient;

  beforeEach(() => {
    client = new EventsApiClient('http://localhost:8080/api/v1');
  });

  it('should build correct filter query', async () => {
    const mockFetch = jest.spyOn(global, 'fetch').mockResolvedValue({
      json: async () => ({ data: [] })
    });

    await client.listEvents({
      filter: { status: 'published', year: 2025 },
      include: ['venue', 'speakers'],
      sort: '-date',
      page: 0,
      limit: 20
    });

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('filter=%7B%22status%22%3A%22published%22%2C%22year%22%3A2025%7D')
    );
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('include=venue%2Cspeakers')
    );
  });
});
```

### Integration Tests

```typescript
// __tests__/event-detail-page.integration.test.ts

import { render, screen } from '@testing-library/react';
import { EventDetailPage } from '../pages/EventDetailPage';

describe('EventDetailPage - API Integration', () => {
  it('should load event with all includes in single API call', async () => {
    const mockFetch = jest.spyOn(global, 'fetch');

    render(<EventDetailPage eventId="evt-001" />);

    // Wait for data to load
    await screen.findByText('Spring Conference 2025');

    // Verify only 1 API call was made (not 30)
    expect(mockFetch).toHaveBeenCalledTimes(1);
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/v1/events/evt-001?include=')
    );
  });
});
```

### Performance Tests

```typescript
// __tests__/performance.test.ts

describe('Events API Performance', () => {
  it('should load event detail with all includes under 500ms', async () => {
    const startTime = Date.now();

    const response = await fetch('/api/v1/events/evt-001?include=venue,speakers,sessions,topics,workflow,registrations,catering,team,publishing,notifications,analytics');

    const endTime = Date.now();
    const duration = endTime - startTime;

    expect(duration).toBeLessThan(500);
  });

  it('should return cached response under 50ms', async () => {
    // Prime cache
    await fetch('/api/v1/events/evt-001?include=venue,speakers');

    // Test cached response
    const startTime = Date.now();
    const response = await fetch('/api/v1/events/evt-001?include=venue,speakers');
    const endTime = Date.now();

    const duration = endTime - startTime;
    const cacheStatus = response.headers.get('X-Cache-Status');

    expect(cacheStatus).toBe('HIT');
    expect(duration).toBeLessThan(50);
  });
});
```

---

## Rollback Plan

### Phase 1: Detection

If issues are detected with the new API:

1. **Monitor CloudWatch Metrics**:
   - API error rate > 5%
   - P95 latency > 1000ms
   - Cache hit rate < 50%

2. **Check Application Logs**:
   - Search for ERROR level logs
   - Review exception patterns

### Phase 2: Immediate Mitigation

```typescript
// Feature flag to switch between old and new API
export const USE_NEW_EVENTS_API = process.env.REACT_APP_USE_NEW_EVENTS_API === 'true';

export class EventsService {
  async getEvent(id: string) {
    if (USE_NEW_EVENTS_API) {
      return this.getEventNew(id);
    } else {
      return this.getEventLegacy(id);
    }
  }

  private async getEventNew(id: string) {
    // New consolidated API
    return fetch(`/api/v1/events/${id}?include=venue,speakers`);
  }

  private async getEventLegacy(id: string) {
    // Old fragmented API
    const event = await fetch(`/api/v1/events/${id}`);
    const venue = await fetch(`/api/v1/venues/${event.venueId}`);
    const speakers = await fetch(`/api/v1/events/${id}/speakers`);
    return { ...event, venue, speakers };
  }
}
```

### Phase 3: Rollback Execution

1. **Update Environment Variable**:
   ```bash
   # Disable new API via environment variable
   aws ssm put-parameter \
     --name /batbern/frontend/USE_NEW_EVENTS_API \
     --value "false" \
     --overwrite
   ```

2. **Deploy Frontend**:
   ```bash
   # Frontend will use old API after restart
   aws ecs update-service \
     --cluster batbern-prod \
     --service web-frontend \
     --force-new-deployment
   ```

3. **Verify Rollback**:
   - Check error rates return to normal
   - Verify old API endpoints receiving traffic
   - Monitor user experience metrics

---

## Support and Resources

### Documentation

- **OpenAPI Spec**: `docs/api/events-api.openapi.yml`
- **Story Documentation**: `docs/stories/1.15a.1.events-api-consolidation.md`
- **Wireframe Updates**: `docs/wireframes/story-*.md`

### Contact

- **Platform Team**: platform@batbern.ch
- **Slack Channel**: #batbern-api-migration
- **Issue Tracker**: https://github.com/batbern/platform/issues

### FAQ

**Q: Can I use both old and new APIs during migration?**
A: Yes, both APIs are available during Phase 1. Use feature flags to gradually migrate.

**Q: How long will the old API be supported?**
A: The old API will be deprecated 6 months after Phase 1 and removed after 12 months.

**Q: What happens if I don't migrate before the deadline?**
A: Your application will break when the old API is removed. Migrate during Phase 1 or Phase 2.

**Q: How do I test the new API in development?**
A: Use the local development server at `http://localhost:8080/api/v1` or the dev environment at `https://dev.api.batbern.ch/api/v1`.

**Q: Is caching automatic?**
A: Yes, caching is handled automatically by the server. You don't need to implement client-side caching.

**Q: How do I clear the cache for an event?**
A: The cache is automatically cleared when you update the event (PUT, PATCH, DELETE, publish, workflow advance). You cannot manually clear the cache.

---

**Last Updated**: 2025-10-12
**Version**: 1.0.0
**Story**: 1.15a.1 - Events API Consolidation
