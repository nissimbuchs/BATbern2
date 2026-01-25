# Company Management

> Manage architectural firms participating in BATbern conferences

<span class="feature-status implemented">Implemented</span>

## Overview

Companies represent architectural firms in Switzerland and internationally that participate in BATbern conferences. Each company has employees (users) who can register as speakers or attendees.

## Key Features

- ✅ **Swiss UID Validation** - Real-time validation of Swiss company identifiers
- ✅ **Logo Upload** - Direct S3 uploads using presigned URLs
- ✅ **Search & Autocomplete** - Fast company lookup by name or UID
- ✅ **Employee Tracking** - Link users to their companies
- ✅ **Location Management** - Canton, city, address information

## Creating a Company

<div class="step" data-step="1">

**Navigate to Companies**

Click **🏢 Companies** in the left sidebar.
</div>

<div class="step" data-step="2">

**Click "Create New Company"**

Click the **+ Create New Company** button (top-right).
</div>

<div class="step" data-step="3">

**Fill Required Fields**

Complete the company creation form:

**Basic Information**:
- **Name*** - Legal name (e.g., "Müller Architekten AG") - used as internal identifier
- **Display Name** - Optional public-facing name (defaults to Name if not provided)
- **Swiss UID*** - CHE-xxx.xxx.xxx format (Swiss companies only)
- **Country*** - Switzerland or international
- **Canton** - For Swiss companies (ZH, BE, VD, etc.)
- **City** - Headquarters location

**Contact Information**:
- **Email** - General company email
- **Phone** - Primary contact number
- **Website** - Company website URL

**Additional Details**:
- **Employee Count** - Approximate number of employees
- **Year Founded** - Company establishment year
- **Description** - Brief company overview (optional)

</div>

<div class="step" data-step="4">

**Upload Company Logo** (Optional)

Click **Upload Logo** to add a company logo:

1. Click **Choose File**
2. Select PNG, JPG, or SVG file (max 2MB)
3. Logo is uploaded directly to S3 via presigned URL
4. Thumbnail appears immediately after upload

</div>

<div class="step" data-step="5">

**Save**

Click **Save** to create the company.

Success message appears: "Company 'Müller Architekten AG' created successfully"
</div>

## Swiss UID Validation

<span class="feature-status implemented">Implemented</span>

Swiss companies must provide a valid **UID (Unternehmens-Identifikationsnummer)** - the official Swiss business identifier.

### Format Requirements

**Valid Format**: `CHE-XXX.XXX.XXX`

Where:
- `CHE` = Swiss prefix (constant)
- `XXX.XXX.XXX` = 9-digit number with dots

**Examples**:
- ✅ Valid: `CHE-123.456.789`
- ✅ Valid: `CHE-100.000.001`
- ❌ Invalid: `CHE123456789` (missing dots)
- ❌ Invalid: `CHE-123456789` (missing dots)
- ❌ Invalid: `ABC-123.456.789` (wrong prefix)
- ❌ Invalid: `CHE-12.345.678` (wrong digit count)

### Real-Time Validation

The Swiss UID field validates as you type:

```
Swiss UID *
[CHE-123.456.789    ] ✅ Valid Swiss UID
```

```
Swiss UID *
[CHE123456789       ] ❌ Invalid format. Use CHE-XXX.XXX.XXX
```

### UID Registry

<span class="feature-status planned">Planned</span>

Future releases will integrate with the Swiss UID Registry API to:
- Verify UID exists in official registry
- Auto-populate company name and address
- Check UID status (active, dissolved, etc.)

## Logo Upload

<span class="feature-status implemented">Implemented</span>

Companies can upload logos for branding and recognition.

### Upload Process

<div class="step" data-step="1">

**Request Upload URL**

System requests a presigned S3 URL from the backend.
</div>

<div class="step" data-step="2">

**Upload Directly to S3**

File is uploaded directly to S3 (not through backend), reducing server load.
</div>

<div class="step" data-step="3">

**Confirm Upload**

Backend confirms successful upload and saves logo URL to company record.
</div>

### File Requirements

- **Formats**: PNG, JPG, SVG
- **Max Size**: 2MB
- **Recommended Dimensions**: 400x400px (square)
- **Aspect Ratio**: 1:1 or 16:9

### Logo Display

Logos appear in:
- Company directory listings
- Event partner showcases
- Speaker profile pages (showing speaker's company)
- Email communications <span class="feature-status planned">Planned</span>

## Searching Companies

<span class="feature-status implemented">Implemented</span>

### Quick Search

Use the search box at the top of the company list:

```
🔍 [Müller]
```

Searches across:
- Company name (both internal name and display name)
- Swiss UID
- City
- Canton

### Advanced Filters

Use JSON-based filters for complex queries:

**Filter by Canton**:
```
canton:ZH
```

**Filter by Employee Count**:
```
employees:>100
```

**Multiple Filters**:
```
canton:ZH
employees:>50
verified:true
```

### Autocomplete

<span class="feature-status implemented">Implemented</span>

When creating users or speakers, company fields provide autocomplete:

```
Company
[Mül____________]
   Müller Architekten AG (CHE-123.456.789)
   Müller & Partner GmbH (CHE-987.654.321)
```

Type 2+ characters to trigger suggestions.

## Editing a Company

<div class="step" data-step="1">

**Find the Company**

Use search or browse the company list.
</div>

<div class="step" data-step="2">

**Click Edit**

Click the **📝 Edit** icon in the company row.
</div>

<div class="step" data-step="3">

**Modify Fields**

Update any editable fields. Note:
- Swiss UID cannot be changed once set (prevents fraud)
- Logo can be replaced (old logo deleted from S3)

</div>

<div class="step" data-step="4">

**Save Changes**

Click **Save Changes** to persist updates.
</div>

## Deleting a Company

<div class="alert warning">
⚠️ <strong>Warning:</strong> Deleting a company does NOT delete associated users. Users will have their company reference cleared.
</div>

<div class="step" data-step="1">

**Click Delete**

Click the **🗑️ Delete** icon in the company row.
</div>

<div class="step" data-step="2">

**Confirm Deletion**

A confirmation dialog appears:

```
Delete Company?
────────────────────────────────────
Are you sure you want to delete
"Müller Architekten AG"?

This will:
- Remove the company from the directory
- Clear company association for 45 employees
- Delete the company logo from S3

This action cannot be undone.

[Cancel]           [Delete]
```

Click **Delete** (red button) to confirm.
</div>

<div class="step" data-step="3">

**Cascade Effects**

After deletion:
- Company record is removed
- Company logo is deleted from S3
- User company references are set to NULL
- Event partnerships remain (historical record)

</div>

## Company Employees

### Viewing Employees

Company detail view shows linked employees:

```
Employees (45)
──────────────────────────────────────
Name                  | Role       | Actions
──────────────────────────────────────
Hans Müller          | ORGANIZER  | 👁️ 📝
Anna Schmidt         | SPEAKER    | 👁️ 📝
Peter Meier          | ATTENDEE   | 👁️ 📝
```

Click **View All Employees** to see the complete list.

### Linking Users to Companies

Users are linked to companies during:
1. **User Creation** - Select company from autocomplete dropdown
2. **User Profile Update** - Change company affiliation
3. **User Sync** - Automatic sync from Cognito user attributes

See [User Management](users.md) for details.

## Company Statistics

<span class="feature-status planned">Planned</span>

Company detail view will show:
- **Total Employees**: Active users linked to company
- **Speakers**: Employees registered as speakers
- **Attendees**: Employees registered for events
- **Event Participation**: Historical attendance by year
- **Topics Presented**: Topics covered by company speakers

## Export & Import

<span class="feature-status planned">Planned</span>

### Export Companies

Export company directory to CSV/Excel:

```
[📥 Export]
   ├─ Export All (237 companies)
   ├─ Export Filtered (42 companies)
   └─ Export Selected (5 companies)
```

### Import Companies

Bulk import from CSV:

```
[📤 Import Companies]

Upload CSV file with columns:
- name, swiss_uid, country, canton, city, email

Validation:
- Duplicate UIDs rejected
- Invalid formats flagged
- Success rate shown (e.g., "185/200 imported")
```

## Common Issues

### "Invalid Swiss UID format"

**Problem**: UID doesn't match CHE-XXX.XXX.XXX format.

**Solution**:
- Ensure dots are included: `CHE-123.456.789`
- Verify 9 digits total (3 groups of 3)
- Use uppercase CHE prefix

### "Swiss UID already exists"

**Problem**: Another company has the same UID.

**Solution**:
- UIDs must be unique in the system
- Check if company already exists (use search)
- Contact support if you believe this is an error

### "Logo upload failed"

**Problem**: Logo couldn't be uploaded to S3.

**Solution**:
- Check file size (must be ≤ 2MB)
- Verify file format (PNG, JPG, SVG only)
- Try again (presigned URL may have expired)
- Check network connectivity

See [File Upload Troubleshooting](../troubleshooting/uploads.md) for more details.

## Related Topics

- [User Management →](users.md) - Link employees to companies
- [File Uploads Feature →](../features/file-uploads.md) - How presigned URLs work
- [Partner Management →](partners.md) - Companies as event partners

## API Reference

### Endpoints

```
POST   /api/companies              Create company
GET    /api/companies              List companies (paginated)
GET    /api/companies/{id}         Get company by ID
PUT    /api/companies/{id}         Update company
DELETE /api/companies/{id}         Delete company
POST   /api/companies/{id}/logo    Request presigned URL for logo upload
```

### Resource Expansion

```
GET /api/companies/123?expand=employees

{
  "id": "123",
  "name": "Müller Architekten AG",
  "swissUid": "CHE-123.456.789",
  "employees": [
    { "id": "456", "name": "Hans Müller", "role": "ORGANIZER" },
    { "id": "789", "name": "Anna Schmidt", "role": "SPEAKER" }
  ]
}
```

See [API Documentation](../../api/) for complete specifications.
