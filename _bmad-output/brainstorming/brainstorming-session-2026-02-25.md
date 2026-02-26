---
stepsCompleted: [1, 2, 3]
inputDocuments: ['_bmad/bmm/data/project-context-template.md']
session_topic: 'Analytics Dashboard — BATbern partner-facing event statistics'
session_goals: 'Brainstorm a compelling, data-rich analytics page for the BATbern web frontend — primarily used in partner meetings to showcase event stats, speaker contributions, attendee trends, and company engagement'
selected_approach: 'Collaborative facilitation — Mary (Analyst) with Nissim ideas as seed'
techniques_used: ['requirements elicitation', 'constraint-driven refinement', 'layout architecture']
ideas_generated: ['KPI hero cards', 'event cadence timeline', 'attendees per event bar chart', 'label toggle buttons', 'trend line overlay', 'returning vs new attendees stacked bar', 'events per category bar chart', 'topic vs attendee scatter plot', 'attendees per company stacked bar', 'sessions per company bar + unique speaker indicator', 'attendee distribution pie', 'partner auto-highlight', 'own company pinned + Top N toggle', 'collapsible data table per chart', 'global time range filter', 'empty state per chart', 'tab-based navigation', 'BATbern palette consistency']
context_file: '_bmad/bmm/data/project-context-template.md'
---

# Brainstorming Session Results

**Facilitator:** Nissim
**Date:** 2026-02-25

---

## Session Overview

**Topic:** Analytics Dashboard — BATbern partner-facing event statistics
**Goals:** Design a compelling, data-rich analytics page primarily used in partner meetings to showcase attendee trends, speaker contributions, topic distribution, and company engagement across 58+ events

### Seed Ideas (from Nissim)

1. **Attendees per event** — bar chart across all ~58 events, with event title + category labels, configurable label options
2. **Events per topic** — bar chart showing how many events were held per topic
3. **Attendees per company per event** — bar chart filtered to last 2 or last 5 years
4. **Sessions per company** — bar/pie chart of how many speaker sessions each company has contributed in total
5. **Attendee distribution per company** — pie chart of company representation at events, filterable by event

---

## Final Design — Analytics Dashboard

### Page Shell

```
┌─────────────────────────────────────────────────────┐
│  ANALYTICS                          [All Time ▾]    │
│  [Overview] [Attendance] [Topics] [Companies]        │
│  ────────────────────────────────────────────────── │
│  ... tab content (full width chart + ▼ data table)  │
└─────────────────────────────────────────────────────┘
```

- **Global time filter** (All Time / Last 5yr / Last 2yr) — top-right, affects all time-sensitive charts across all tabs
- **Tab navigation** — Overview is default; tabs maximize chart real estate
- **Per-chart layout** — chart on top, `▼ Show data table` collapsible below
- **Empty states** — friendly "No data for this period" per chart when no data exists
- **Colors** — BATbern brand palette, consistent across all charts
- **Library** — Recharts

---

### Tab 1 — Overview *(default)*

| Element | Details |
|---------|---------|
| Hero KPI cards | 58 Events · X Total Attendees · Y Companies represented · Z Speaker sessions |
| Event cadence timeline | All events as colored dots/bars on horizontal time axis; colored by category (BATbern palette) |

---

### Tab 2 — Attendance

| Chart | Type | Details |
|-------|------|---------|
| Attendees per event | Bar | Label toggle buttons (event title / category / both); trend line overlay; ▼ data table |
| Returning vs. New attendees per event | Stacked bar | Warm = returning, cool = first-timers; ▼ data table |

---

### Tab 3 — Topics

| Chart | Type | Details |
|-------|------|---------|
| Events per category | Bar | Uses existing category field; ▼ data table |
| Topic popularity vs. attendee count | Scatter | X = #events on topic, Y = avg attendees; reveals over/underperforming topics; ▼ data table |

---

### Tab 4 — Companies *(partner auto-highlight)*

**Partner identity**: logged-in partner's `user.company` is used to auto-highlight their company across all three charts. Their company is always shown (pinned), even if outside Top N. Highlighted with distinct BATbern color/border.

| Chart | Type | Details |
|-------|------|---------|
| Attendees per company over time | Stacked bar | Own company pinned + highlighted; Top N toggle (5/10/all); ▼ data table |
| Sessions per company | Bar | Own company pinned + highlighted; secondary indicator = unique speakers count; Top N toggle; ▼ data table |
| Attendee distribution per company | Pie | Own company slice highlighted; per-event filter; ▼ data table |

**Session counting rule**: each speaker contribution counts to their company. Two speakers from the same company at the same event = 2 sessions for that company.

---

### Cross-Cutting Decisions

| Concern | Decision |
|---------|----------|
| Colors | BATbern brand palette — consistent across all charts and categories |
| Layout | Chart on top, collapsible data table below each chart |
| Empty states | Friendly "No data for this period" per chart (not a broken chart) |
| Export | None — not needed |
| Refresh | None automated — 3 events/year; data updates naturally after each event |
| Partner identity | `user.company` → auto-highlight in Tab 4 |
| Individual data | Never shown — aggregates only |
| Library | Recharts |
| Visibility | Organizers see everything; partners see everything but own company is highlighted |

---

## Brainstorming Ideas (all generated)

| # | Idea | Status |
|---|------|--------|
| 1 | Attendees per event bar chart (label toggle, trend line) | ✅ IN |
| 2 | Events per category bar chart | ✅ IN |
| 3 | Attendees per company over time (stacked bar, 2yr/5yr/all) | ✅ IN |
| 4 | Sessions per company (bar, unique speaker secondary indicator) | ✅ IN |
| 5 | Attendee distribution per company pie (per-event filter) | ✅ IN |
| 6 | Speaker geography heatmap | ❌ OUT — no location data, Bern-only event |
| 7 | Returning vs. New attendee ratio per event | ✅ IN |
| 8 | Partner impact score card | ❌ OUT — no attendees-sent data |
| 9 | Topic popularity vs. attendee count scatter | ✅ IN |
| 10 | Event cadence timeline | ✅ IN |
| 11 | Speaker repeat rate | ❌ OUT |
| 12 | Waitlist / overflow rate | ❌ OUT |
| 13 | Hero KPI summary cards (inside Overview tab) | ✅ IN |
| 14 | Global time range filter | ✅ IN |
| 15 | Company spotlight / drill-down | ✅ IN — as auto-highlight + pinning in Tab 4 |
| 16 | Categories over time (stacked area) | ❌ OUT |
| 17 | Top N toggle on company charts | ✅ IN |
| 18 | Tab-level export button | ❌ OUT |
| 19 | Manual refresh button + timestamp | ❌ OUT |
| 20 | Define new color palette as part of this feature | ❌ OUT — use existing BATbern colors |
| 21 | Empty state per chart | ✅ IN |

