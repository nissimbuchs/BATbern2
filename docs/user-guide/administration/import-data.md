# Import Data

> 🔨 **IN PROGRESS** — Part of Story 10.1 (Administration Page). This documentation describes the intended functionality once delivered.

## Overview

The **Import Data** tab consolidates all five batch import modals into a single, organised screen. Previously, import buttons were scattered across the event management dashboard, company management screen, and user list — they have been moved here and removed from those pages.

**Navigation**: Administration → Tab 1 (Import Data)

---

## Import Types

Each import type is presented as a card with a description and a trigger button.

### Events

Import historical event records in bulk.

**Use case**: Backfilling past BATbern events (e.g., the 57-event historical archive) for reporting and the public archive browser.

**Fields**: event number, title, date, venue, type, topic

### Sessions

Import session/presentation data for historical or upcoming events.

**Use case**: Populating session details for events that were managed outside the platform.

**Fields**: event reference, session title, speaker name, session type, time slot, abstract

### Companies

Import company records in bulk.

**Use case**: Initial data load from spreadsheets or a previous system.

**Fields**: company name, Swiss UID (CHE-XXX.XXX.XXX), tier, website, address

### Speakers

Import speaker profiles.

**Use case**: Migrating a speaker database from a previous CRM or spreadsheet.

**Fields**: name, email, company, bio, expertise areas, LinkedIn URL

### Participants / Attendees

Import historical attendance data.

**Use case**: Backfilling attendance records for past events to enable partner analytics and attendance tracking.

**Fields**: event reference, attendee name, email, company name, attended (boolean)

---

## Import Process

Each import modal follows the same multi-step flow:

```
1. Choose file  →  2. Validate  →  3. Preview  →  4. Import  →  5. Result
```

### Step 1: Choose File

- Accepted formats: **CSV** and **JSON**
- File size limit: 10 MB
- Drag-and-drop or file browser

### Step 2: Validation

After uploading, the platform validates every row before importing anything:

- **Required fields** — missing mandatory values are flagged
- **Format checks** — Swiss UID format, date format, valid email syntax
- **Duplicate detection** — records that already exist in the database are identified

If validation finds errors, a summary is shown and no data is imported. You can download an **error export** (CSV) listing each invalid row and the reason.

### Step 3: Preview

A paginated preview table shows the first 50 rows that will be imported, with colour-coded indicators:

| Colour | Meaning |
|--------|---------|
| Green | New record — will be created |
| Yellow | Duplicate — will be skipped (or optionally updated) |
| Red | Invalid — will not be imported |

### Step 4: Import

Click **Start Import** to begin. A progress indicator shows:

- Records processed
- Records created
- Records skipped (duplicates)
- Records failed

The import runs in the background — you can navigate away and return to check progress.

### Step 5: Result

A summary report shows final counts. You can download:

- **Success report** (CSV) — all imported record IDs
- **Error report** (CSV) — rows that could not be imported with reasons

---

## Tips

**Prepare your file first**: Review column headers against the expected field names. The import modal shows a template download link for each entity type.

**Start small**: For large imports (1,000+ rows), test with a 10-row sample first to verify column mapping before running the full file.

**Historical participant data**: When importing participants for analytics purposes, the `company name` field is used directly for partner attendance aggregation — ensure it exactly matches the company names already in the system.

---

## Related

- **[Partners: Attendance Analytics](../partner-portal/analytics.md)** — why participant import matters for partner reporting
- **[Administration Overview](README.md)** — back to admin hub
