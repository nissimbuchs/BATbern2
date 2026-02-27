# Attendance Analytics

> Track how many employees attended BATbern events and calculate your cost-per-attendee

<span class="feature-status implemented">Implemented</span> — Epic 8.1

## Overview

The Attendance Analytics dashboard shows a partner company's attendance at every BATbern event, alongside total event attendance, so partners can measure their engagement over time and calculate the ROI of their partnership.

Data is loaded on demand with a 15-minute cache, so the first load per session may take up to 5 seconds; subsequent loads are near-instant.

## The Analytics Table

The main view is a table sorted by event date (most recent first):

```
┌─────────────────────────────────────────────────────────────────────┐
│  Attendance Analytics — Sustainable Materials AG                     │
│                                                                      │
│  Time period:  [● Last 5 Years]  [○ Full History]   [Export XLSX]  │
│                                                                      │
│  Event           Date          Your      Total     %                │
│  ─────────────────────────────────────────────────────              │
│  BATbern 57      15 Mar 2026   8         124       6.5%             │
│  BATbern 56      12 Sep 2025   12        143       8.4%             │
│  BATbern 55      14 Mar 2025   6         118       5.1%             │
│  BATbern 54      10 Sep 2024   9         131       6.9%             │
│  BATbern 53      15 Mar 2024   11        127       8.7%             │
│  …                                                                   │
│                                                                      │
│  Cost per attendee (last 5 years):  CHF 416.67                     │
└─────────────────────────────────────────────────────────────────────┘
```

### Table Columns

| Column | Description |
|--------|-------------|
| **Event** | BATbern event name and code |
| **Date** | Full event date |
| **Your Attendees** | Number of confirmed attendees from this partner company |
| **Total Attendees** | Overall confirmed attendees at that event |
| **%** | Your attendees ÷ total attendees × 100 |

### Time Period Toggle

| Option | Period | Typical Rows |
|--------|--------|-------------|
| **Last 5 Years** (default) | Current year minus 5 | ~15 events |
| **Full History** | Up to 20 years back | ~60 events |

### Cost-Per-Attendee Metric

```
Cost per attendee = Partnership cost ÷ Total company attendees
                    (over the selected time period)
```

This metric tells you how much BATbern membership costs per person-visit. A lower number means more employees are getting value from the partnership.

- If no employees attended in the selected period, this shows as **N/A**
- Partnership cost is set by the organiser on the partner record (field: `partnershipCost`)

## Exporting to Excel

Click **Export XLSX** to download the current table view as an Excel file.

The exported file contains:
- All rows matching the selected time period
- The same five columns as the table (Event, Date, Your Attendees, Total Attendees, %)
- A footer row with column totals and the cost-per-attendee metric

```
Export: partner-analytics-sustainable-materials-ag-2026-02-27.xlsx
```

The file is generated server-side using Apache POI and downloads immediately.

## Performance

| Action | Target |
|--------|--------|
| Dashboard load (first visit) | < 5 seconds |
| Dashboard load (cached, 15 min) | < 50 ms |
| XLSX export | < 5 seconds |

## For Organisers

Organisers can view any partner's analytics by navigating to the partner's detail page and selecting the **Analytics** tab. The same table and export functionality is available with full cross-partner visibility.

To set or update a partner's partnership cost:
1. Go to **Partners** → find the partner
2. Click **Edit**
3. Update the **Partnership Cost (CHF)** field
4. Save — the cost-per-attendee metric updates automatically on next load

## Troubleshooting

### "My attendees count looks wrong"

Attendance is counted from confirmed event registrations where the attendee's company matches the partner company name. Check that:
- Employees registered using their company email or selected the correct company during registration
- The partner company name in the system matches exactly what employees selected

If you believe the count is incorrect, contact the BATbern organising team.

### "Cost per attendee shows N/A"

Either:
- No employees attended in the selected time period (try switching to **Full History**)
- The partnership cost has not been set — ask the organising team to add it to the partner record
