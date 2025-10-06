# API Design - Domain Overview

This document provides a high-level overview of the BATbern Event Management Platform API architecture, organized by domain-driven design principles. For detailed API specifications, refer to the domain-specific documentation.

## Architecture Overview

The BATbern API is organized into **five domain-specific APIs** plus **core API infrastructure**, following domain-driven design principles to ensure clear boundaries, independent scaling, and focused development.

## Domain APIs

### 1. [Core API Design](04-api-core.md)

Foundational API patterns, external integrations, and common components shared across all domains.

**Key Topics:**
- OpenAPI specification metadata
- External API integrations (AWS Cognito, SES, S3)
- API design principles (authentication, consistency, performance, error handling)
- Common schemas and security schemes
- Cross-cutting concerns (pagination, caching, rate limiting, i18n)

**Use this for:** Understanding base API patterns, security implementation, and shared components

---

### 2. [Event Management API](04-api-event-management.md)

Core event lifecycle management, organizer workflows, and comprehensive 16-step workflow automation.

**Key Endpoints:**
- `/api/v1/events` - Event CRUD operations
- `/api/v1/events/{id}/workflow` - 16-step workflow state management
- `/api/v1/events/{id}/slots` - Slot configuration and assignment
- `/api/v1/events/{id}/overflow` - Speaker overflow voting and waitlist
- `/api/v1/topics/backlog` - Topic backlog with ML similarity and staleness detection
- `/api/v1/users/{id}/roles` - User role management

**Core Workflows:**
- Event creation with intelligent automation
- Multi-speaker session assignment
- Overflow management and voting
- Topic backlog management with heat maps and duplicate detection

**Use this for:** Event planning, workflow orchestration, slot management, topic backlog management, and role administration

---

### 3. [Speaker Coordination API](04-api-speaker-coordination.md)

Enhanced speaker management with complex workflow states, slot preferences, and quality control.

**Key Endpoints:**
- `/api/v1/speakers` - Speaker profiles and expertise
- `/api/v1/speakers/{id}/preferences` - Slot preferences and requirements
- `/api/v1/sessions/{id}/quality-review` - Content quality review workflow
- `/api/v1/moderators/{id}/reviews` - Moderator review queue

**Core Workflows:**
- Speaker invitation and confirmation workflow
- 8-state speaker workflow progression
- Quality review and content validation

**Use this for:** Speaker coordination, preference collection, material submission, and quality review

---

### 4. [Partner Coordination API](04-api-partner-coordination.md)

Partner relationship management, strategic topic voting, and partnership analytics.

**Key Endpoints:**
- `/api/v1/notifications/templates` - Email template management
- `/api/v1/notifications/preferences` - User notification preferences
- `/api/v1/notifications/escalation-rules` - Escalation rule configuration

**Key Features:**
- Strategic topic voting
- Partner meeting coordination
- Partnership analytics and ROI tracking
- Multi-channel notification system

**Use this for:** Partner engagement, topic voting, meeting coordination, and analytics

---

### 5. [Attendee Experience API](04-api-attendee-experience.md)

Attendee registration, content discovery, and historical archive search across 20+ years of content.

**Key Endpoints:**
- `/api/v1/content/search` - Full-text content discovery

**Core Workflows:**
- Content discovery and search with caching
- Historical archive access

**Key Features:**
- PostgreSQL full-text search
- Search faceting and filtering
- Relevance ranking
- Multi-level caching strategy

**Use this for:** Content search, historical archive access, and attendee engagement

---

### 6. [Company Management API](04-api-company-management.md)

Centralized company entity management with logo storage, file management, and CDN integration.

**Key Endpoints:**
- `/api/v1/companies` - Company CRUD and search
- `/api/v1/files/presigned-upload-url` - Generate presigned upload URLs
- `/api/v1/files/{id}/confirm` - Confirm file upload completion
- `/api/v1/files/{id}/download-url` - Generate presigned download URLs
- `/api/v1/users/{id}/storage-quota` - Storage quota management
- `/api/v1/users/{id}/files` - User file listing

**Core Features:**
- Presigned URL-based file uploads
- Direct browser-to-S3 uploads
- CloudFront CDN integration
- Storage quota enforcement
- Content type validation

**Use this for:** Company management, file uploads, storage management, and CDN access

---

## API Organization Principles

### Domain-Driven Design

Each API domain represents a **bounded context** with:
- **Clear boundaries**: Well-defined responsibilities and interfaces
- **Independent evolution**: Domains can evolve at different paces
- **Separate deployment**: Individual services can be deployed independently
- **Technology flexibility**: Each domain can use appropriate technology stacks

### Cross-Domain Communication

Domains communicate through:
- **Domain Events**: Published via AWS EventBridge for async communication
- **API Gateway**: Unified entry point for all domain APIs
- **Shared Kernel**: Common types, events, and utilities
- **REST APIs**: Synchronous cross-domain requests when necessary

### Consistency Patterns

- **Strong consistency**: Within a single domain service
- **Eventual consistency**: Across domain boundaries via events
- **Compensating transactions**: For distributed operation rollback
- **Saga pattern**: For multi-domain workflows (e.g., event creation workflow)

## Quick Reference

### By User Role

**Organizers** primarily use:
- [Event Management API](04-api-event-management.md) - Event planning and workflow
- [Speaker Coordination API](04-api-speaker-coordination.md) - Speaker management
- [Partner Coordination API](04-api-partner-coordination.md) - Partner coordination

**Speakers** primarily use:
- [Speaker Coordination API](04-api-speaker-coordination.md) - Preferences and materials
- [Company Management API](04-api-company-management.md) - File uploads

**Partners** primarily use:
- [Partner Coordination API](04-api-partner-coordination.md) - Topic voting and meetings
- [Company Management API](04-api-company-management.md) - Company profiles

**Attendees** primarily use:
- [Attendee Experience API](04-api-attendee-experience.md) - Content discovery
- [Company Management API](04-api-company-management.md) - File downloads

### By Feature

**Event Planning:**
→ [Event Management API](04-api-event-management.md)

**Speaker Management:**
→ [Speaker Coordination API](04-api-speaker-coordination.md)

**File Uploads/Downloads:**
→ [Company Management API](04-api-company-management.md)

**Content Search:**
→ [Attendee Experience API](04-api-attendee-experience.md)

**Notifications:**
→ [Partner Coordination API](04-api-partner-coordination.md)

**Authentication/Security:**
→ [Core API Design](04-api-core.md)

## API Standards

All domain APIs follow consistent standards defined in [Core API Design](04-api-core.md):

- ✅ JWT bearer token authentication
- ✅ Role-based access control (RBAC)
- ✅ Consistent error response format
- ✅ Standard pagination (limit/offset or cursor)
- ✅ Rate limiting per user role
- ✅ Request correlation IDs
- ✅ Multi-language support (en, de)
- ✅ OpenAPI 3.0 specification

## Evolution Strategy

### Versioning

All APIs use URL-based versioning: `/api/v{version}/...`

- Current version: **v1**
- Breaking changes → New major version
- Backward-compatible additions → Same version
- Deprecated endpoints → Maintained for 6 months minimum

### Deprecation Process

1. **Announcement**: Deprecation announced 6 months in advance
2. **Headers**: `Deprecation` and `Sunset` headers added to responses
3. **Documentation**: Marked as deprecated in API docs
4. **Migration Guide**: Provided for affected endpoints
5. **Removal**: Only after sunset date

## Getting Started

1. **Understand Core Patterns**: Start with [Core API Design](04-api-core.md)
2. **Choose Your Domain**: Select relevant domain API documentation
3. **Review Schemas**: Understand data models and relationships
4. **Test Workflows**: Follow workflow diagrams for integration
5. **Implement Features**: Use endpoint specifications for implementation

## Additional Resources

- [System Overview](01-system-overview.md) - Architecture context
- [Backend Architecture](06-backend-architecture.md) - Service implementation details
- [Frontend Architecture](05-frontend-architecture.md) - Client integration patterns
- [Data Architecture](03-data-architecture.md) - Database schemas and relationships
