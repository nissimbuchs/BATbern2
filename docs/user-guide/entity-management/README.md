# Entity Management

> Manage companies, users, events, partners, and speakers

<span class="feature-status implemented">Implemented</span>

## Overview

Entity management is the foundation of the BATbern platform. As an **Organizer**, you can create, read, update, and delete (CRUD) all core entities needed for successful conference planning.

## Core Entities

BATbern organizes information into five core entity types:

### 🏢 Companies

Architectural firms participating in BATbern conferences.

**Key Features**:
- Swiss UID validation (CHE-xxx.xxx.xxx format)
- Company logo upload to S3
- Employee tracking
- Location and contact information
- Autocomplete search

**Status**: <span class="feature-status implemented">Implemented</span>

[Learn more →](companies.md)

### 👥 Users

Individuals with platform accounts (Organizers, Speakers, Attendees, Admins).

**Key Features**:
- 4 distinct roles with different permissions
- GDPR-compliant profile management
- Role promotion/demotion workflows
- Password reset and email verification
- User sync from Cognito to PostgreSQL

**Status**: <span class="feature-status implemented">Implemented</span>

[Learn more →](users.md)

### 📅 Events

BATbern conferences and their lifecycle management.

**Key Features**:
- 3 event types (Full-Day, Afternoon, Evening)
- Timeline management (dates, registration windows)
- Session tracking
- Speaker assignments
- 9-state event workflow, per-speaker workflow, task system

**Status**: <span class="feature-status implemented">Implemented</span>

[Learn more →](events.md)

### 🤝 Partners

Organizations collaborating with BATbern.

**Key Features**:
- Partner directory with tier badges (Diamond, Platinum, Gold, Silver, Bronze)
- Contact management
- Meeting coordination during events
- Engagement metrics
- Public-facing showcase

**Status**: <span class="feature-status implemented">Implemented</span>

[Learn more →](partners.md)

### 🎤 Speakers

Presenters for BATbern conference sessions.

**Key Features**:
- Speaker profile management
- Status tracking (IDENTIFIED → CONTACTED → INTERESTED → CONFIRMED)
- Content collection (≤1000 characters)
- Expertise and topic matching
- Session assignments

**Status**: <span class="feature-status in-progress">In Progress</span>

[Learn more →](speakers.md)

## Common Operations

All entities support standard CRUD operations:

### Create (C)

<div class="step" data-step="1">

**Navigate to Entity List**

Click the entity type in the left sidebar (e.g., "Companies").
</div>

<div class="step" data-step="2">

**Click "Create New"**

Click the "+ Create New Company" button (top-right).
</div>

<div class="step" data-step="3">

**Fill Form**

Complete all required fields (marked with *).
</div>

<div class="step" data-step="4">

**Save**

Click "Save" or "Save & Continue" to create the entity.
</div>

### Read (R)

**List View**: Browse all entities in paginated tables

```
🏢 Companies (237)
────────────────────────────────────────────
[🔍 Search companies...                   ]

Company Name          | Swiss UID    | Actions
───────────────────────────────────────────
Müller Architekten AG | CHE-123.456  | 👁️ 📝 🗑️
```

**Detail View**: View complete entity information

Click the **👁️ View** icon to see read-only details.

### Update (U)

<div class="step" data-step="1">

**Click Edit**

Click the **📝 Edit** icon in the table row.
</div>

<div class="step" data-step="2">

**Modify Fields**

Update any editable fields.
</div>

<div class="step" data-step="3">

**Save Changes**

Click "Save Changes" to persist updates.
</div>

### Delete (D)

<div class="step" data-step="1">

**Click Delete**

Click the **🗑️ Delete** icon in the table row.
</div>

<div class="step" data-step="2">

**Confirm**

A confirmation dialog appears:

> Are you sure you want to delete "Müller Architekten AG"?
> This action cannot be undone.

Click **Delete** (red button) to confirm.
</div>

### Search & Filter

<span class="feature-status implemented">Implemented</span>

All entity lists support powerful search and filtering:

**Simple Search**:
```
🔍 [Müller]
```
Searches across all text fields (name, email, description, etc.)

**Advanced Filters** (JSON syntax):
```
canton:ZH
employees:>100
verified:true
```

See entity-specific documentation for available filter fields.

### Resource Expansion

<span class="feature-status implemented">Implemented</span>

Optimize API requests by expanding related entities:

```
GET /api/events/123?expand=speakers,partners
```

Returns event with embedded speaker and partner data in a single request, reducing round trips.

**Available expansions** (entity-specific):
- Events: `expand=speakers,partners,sessions`
- Companies: `expand=employees,events`
- Speakers: `expand=company,sessions`

## Data Validation

All entities enforce validation rules:

### Swiss UID Format

<span class="feature-status implemented">Implemented</span>

Companies must provide a valid Swiss UID:

- **Format**: `CHE-123.456.789`
- **Validation**: Real-time format checking
- **Required**: Yes for Swiss companies

**Example**:
- ✅ Valid: `CHE-123.456.789`
- ❌ Invalid: `CHE123456789` (missing dots)
- ❌ Invalid: `ABC-123.456.789` (wrong prefix)

### Email Validation

All email fields validate format:

- **Format**: `user@domain.tld`
- **Required**: Yes for user accounts
- **Unique**: Email addresses must be unique per user

### File Upload Validation

<span class="feature-status implemented">Implemented</span>

File uploads (logos, attachments) enforce limits:

- **Max Size**: 2MB
- **Allowed Types**: PNG, JPG, SVG (images); PDF (documents)
- **Upload Method**: Presigned S3 URLs (no backend proxying)

See [File Uploads Feature](../features/file-uploads.md) for details.

## Data Privacy (GDPR)

<span class="feature-status implemented">Implemented</span>

BATbern complies with GDPR requirements:

### User Rights

Users can:
- ✅ **Access**: View all personal data held by the platform
- ✅ **Rectify**: Update incorrect or incomplete data
- ✅ **Erase**: Request deletion of their account and data ("Right to be Forgotten")
- ✅ **Export**: Download personal data in machine-readable format (JSON)
- ✅ **Object**: Opt-out of non-essential data processing

### Data Retention

- **Active Users**: Data retained indefinitely while account is active
- **Deleted Users**: Personal data erased within 30 days of deletion request
- **Audit Logs**: Retained for 7 years for compliance (anonymized after user deletion)

### Consent Management

<span class="feature-status planned">Planned</span>

Future releases will include:
- Granular consent preferences
- Cookie consent management
- Marketing communication opt-in/out

## Audit Logging

<span class="feature-status planned">Planned</span>

All entity changes are logged for accountability:

```
2025-03-15 14:23:45 | UPDATED | User: organizer@batbern.ch
  Company "Müller Architekten AG"
  Changed: employees (42 → 45)
```

Audit logs include:
- **Timestamp**: When the change occurred
- **Action**: CREATE, UPDATE, DELETE
- **User**: Who made the change
- **Entity**: What was changed
- **Changes**: Field-level diff

## Performance Considerations

### Pagination

All list views paginate results:

- **Default**: 25 items per page
- **Options**: 10, 25, 50, 100
- **Performance**: Large result sets (1000+) may take longer to load

### Caching

<span class="feature-status implemented">Implemented</span>

API responses are cached for performance:

- **Cache Duration**: 5 minutes for read operations
- **Cache Invalidation**: Automatic on UPDATE/DELETE operations
- **Cache Provider**: Caffeine in-memory cache (local dev), ElastiCache (AWS)

### Bulk Operations

<span class="feature-status planned">Planned</span>

Future releases will support:
- Bulk user import from CSV
- Bulk speaker invitation
- Bulk session assignment

## Related Topics

- [Companies Management →](companies.md) - Swiss UID validation, logo upload
- [Users Management →](users.md) - Roles, permissions, GDPR
- [Events Management →](events.md) - Event types, timeline, workflow
- [Partners Management →](partners.md) - Partner directory and coordination
- [Speakers Management →](speakers.md) - Speaker profiles and tracking

## What's Next?

Choose an entity type to learn more:

1. **New to BATbern?** Start with [Companies](companies.md) to register architectural firms
2. **Planning an event?** Jump to [Events](events.md) to create your first conference
3. **Managing speakers?** See [Speakers](speakers.md) for outreach and coordination
