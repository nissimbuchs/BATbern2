# Partner Portal

> Analytics, topic input, and meeting coordination — all in one place for BATbern partner companies

<span class="feature-status implemented">Implemented</span> — Epic 8 (Stories 8.0–8.4, 2026-02-22)

## Overview

The Partner Portal gives sponsoring companies three ways to engage with BATbern:

1. **Attendance Analytics** — See how many employees attended past events and what your partnership costs per attendee
2. **Topic Voting** — Suggest topics for future events and vote on other partners' suggestions
3. **Meeting Coordination** — Receive calendar invites for the annual Spring and Autumn partner meetings

Partners log in with standard AWS Cognito credentials (role: PARTNER). Each partner sees only their own company's data.

## Access

Partners access the portal at the same URL as other users, but see a partner-specific navigation once logged in:

| Environment | URL |
|-------------|-----|
| Production | https://www.batbern.ch |
| Staging | https://staging.batbern.ch |

After login, partners are routed to their company dashboard showing all three portal sections.

## Data Visibility Rules

| Data | Partner Sees | Organiser Sees |
|------|-------------|---------------|
| Attendance analytics | Own company only | Any company |
| Topics & votes | All topics (global) | All topics + voting data |
| Meetings | Receive .ics calendar invite via email only | All meetings + management tools |
| Partner notes | **Hidden entirely** | All notes for all partners |
| Other partners' data | Not visible | All partners |

## Portal Sections

| Section | What Partners Can Do |
|---------|---------------------|
| [Attendance Analytics](analytics.md) | View attendance table, export to XLSX, see cost-per-attendee |
| [Topic Voting](topic-voting.md) | Suggest topics, vote on proposals, view status of selected topics |
| [Meeting Coordination](meetings.md) | Receive .ics calendar invites for partner meetings |

## Constraints

- **Partner company names** are limited to **12 characters** (meaningful short identifiers per ADR-003). Names exceeding 12 characters are rejected with an `IllegalArgumentException`.

## For Organisers

Organisers interact with partner features from the **Partner Management** screens (`/organizer/partners`). The same analytics, topics, and meetings data is visible to organisers with full management capabilities.

In addition, organisers have access to **Partner Notes** (completely hidden from partners) — a private scratchpad for recording context about each partner relationship. See [Partner Management →](../entity-management/partners.md).

## Related

- [Partner Management →](../entity-management/partners.md) — Create and manage partner records, contacts, and notes
- [Entity Management Overview →](../entity-management/README.md)
