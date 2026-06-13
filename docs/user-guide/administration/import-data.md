# Import Data

> <span class="feature-status implemented">Implemented</span> — Batch import (Tab 1, Story 10.1) and legacy BAT-format export/import (Tab 4, Story 10.20) are both live. Historical CSV/JSON batch import composes on the Epic 3 migration tooling.

**Last Updated:** 2026-06-13

## Overview

The **Import Data** tab consolidates all five batch import modals into a single, organised screen. Previously, import buttons were scattered across the event management dashboard, company management screen, and user list — they have been moved here and removed from those pages.

A second, related surface — the **Export / Import** tab (Tab 4) — handles full data round-trips in the legacy BAT JSON format. See [Legacy BAT-Format Export & Import](#legacy-bat-format-export--import) below.

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

## Legacy BAT-Format Export & Import

<span class="feature-status implemented">Implemented</span> (Story 10.20)

Separate from the per-entity batch importers above, the **Export / Import** tab (Tab 4 of the Administration page) handles full data round-trips in the **legacy BAT JSON format**. This is used to migrate data between system versions and to interoperate with the old BATspa platform.

> The per-entity CSV/JSON importers (Tab 1) are for *backfilling* historical records; the legacy BAT export/import (Tab 4) is for *whole-dataset* round-trips. They are complementary.

### What you can export

| Export | Endpoint | Contents |
|--------|----------|----------|
| **Legacy data** | `GET /api/v1/admin/export/legacy` | A single JSON file with the envelope `{ version, exportedAt, events[], companies[], speakers[], attendees[] }` |
| **Asset manifest** | `GET /api/v1/admin/export/assets` | A JSON manifest of presigned download URLs (`{ exportedAt, assetCount, assets[] }`); each URL is valid for 1 hour |

Both exports are **organizer-only** (other roles receive `403`).

### What you can import

| Import | Endpoint | Behaviour |
|--------|----------|-----------|
| **Legacy data** | `POST /api/v1/admin/import/legacy` (multipart JSON `file`) | Upserts events, sessions, speakers, companies, and attendees; returns `{ imported, skipped, errors }`. **Idempotent** — importing the same file twice has no side effects. Invalid JSON returns `400` with structured errors. |
| **Assets** | `POST /api/v1/admin/import/assets` (multipart ZIP `file`) | Unpacks the ZIP to S3 under an `imports/{timestamp}/` prefix and links each asset to its entity by filename convention. |

### Using the tab

1. Navigate to **Administration → Export / Import**
2. To **export**: click the export button — the JSON (or asset manifest) downloads as `batbern-export-{date}.json`
3. To **import**: pick the JSON (or asset ZIP) file, then confirm in the **confirmation dialog** that appears before any import runs
4. After import, a **result summary** shows per-entity counts of records imported, skipped, and any errors

Because the legacy import is idempotent, it is safe to re-run after fixing a partial dataset.

---

## Related

- **[Partners: Attendance Analytics](../partner-portal/analytics.md)** — why participant import matters for partner reporting
- **[Email Templates](email-templates.md)** — content/layout template management
- **[Administration Overview](README.md)** — back to admin hub
