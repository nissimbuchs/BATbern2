---
stepsCompleted: ['step-01-init', 'step-02-discovery', 'step-02b-vision', 'step-02c-executive-summary', 'step-03-success', 'step-04-journeys', 'step-05-domain', 'step-06-innovation', 'step-07-project-type', 'step-08-scoping', 'step-09-functional', 'step-10-nonfunctional', 'step-11-polish', 'step-12-complete']
completedAt: '2026-04-04'
status: complete
inputDocuments:
  - 'docs/prd-enhanced.md'
  - 'docs/prd/epic-8-partner-coordination.md'
  - '_bmad-output/project-context.md'
  - '_bmad-output/brainstorming/brainstorming-session-2026-02-24.md'
  - '_bmad-output/brainstorming/brainstorming-session-2026-02-25.md'
workflowType: 'prd'
classification:
  projectType: saas_b2b
  domain: event_management
  complexity: medium
  projectContext: brownfield
---

# Product Requirements Document — BATbern Partner Management Enhancements

**Author:** Nissim
**Date:** 2026-04-04

## Executive Summary

BATbern Partner Management Enhancements extend the existing BATbern event management platform with two capability layers that eliminate the remaining analog gaps in the partner relationship lifecycle. Target users: BATbern organizers (managing 5–10 active sponsoring companies) and partner contacts (company representatives who sponsor and attend BATbern events).

Two problems are solved: (1) invoice lifecycle management is entirely manual — organizers create invoices by hand, track payment status without tooling, and have no structured view of outstanding receivables; (2) partner communication artifacts (meeting notes, action items) exist only in email, leaving partners without a self-service record of their relationship with the association.

BATbern is purpose-built for small professional associations that cannot justify a Bexio + Salesforce + events platform stack. These enhancements make BATbern the single authoritative system for the full partner lifecycle — from first meeting invite through invoice settlement. The platform already holds all required data (partnership cost, contacts, meeting records) — this work surfaces it as structured, actionable workflows. The differentiator moment is bilateral: partners open their portal and see meeting notes, action items, and their current invoice in one place; organizers open the partner dashboard and see a real-time financial status board with zero manual tracking.

## Project Classification

- **Project Type:** SaaS B2B — multi-tenant platform with role-based access (organizer / partner), dashboards, enterprise feature set
- **Domain:** Event and association management — general business domain, no regulated industry constraints
- **Complexity:** Medium — established domain patterns; business logic complexity in invoice lifecycle state machine and PDF generation; GDPR-compliant data handling already in place
- **Project Context:** Brownfield — extending a production-ready platform (Epics 1–8 complete); new features add to `partner-coordination-service` and the partner portal frontend; all existing patterns (ADR-003, async SES email, React Query, MUI) apply

## Success Criteria

### User Success

**Organizer:**
- Issues a PDF invoice to any active partner in under 2 minutes without an external tool or PDF editor
- Invoice status board provides real-time view of all partners (DRAFT / SENT / PAID / OVERDUE) — no spreadsheet required
- Overdue invoices surface immediately; reminder sent in one click
- Meeting notes and action items published to a partner in one click after any meeting

**Partner:**
- Finds current and past invoices with download links in the portal without contacting the organizer
- Views published meeting notes and action items for every meeting where the organizer chose to share them
- Maintains own billing information (address, contact, PO number) without organizer involvement
- RSVP status visible in the portal — no back-channel "did you get our invite?" emails

### Business Success

- 100% of annual partnership invoices issued from within BATbern within the first billing cycle after launch
- All active partner companies complete billing info within 4 weeks of feature availability
- Zero overdue invoices undetected for more than 1 day (same-day cron detection)
- Organizer invoice administration time: under 5 minutes/year per partner after initial setup

### Measurable Outcomes

| Outcome | Target | Timeframe |
|---------|--------|-----------|
| Invoices issued via platform | 100% of active partners | First billing cycle |
| Partner billing info completion | 100% of active partners | 4 weeks post-launch |
| Organizer invoice admin time | < 5 min/year per partner | Ongoing |
| Overdue detection lag | 0 days (same-day cron) | Ongoing |
| Partner self-service rate | Invoice/notes found without emailing organizer | Ongoing |

## Project Scoping

### MVP — Minimum Viable Product

Problem-Solving MVP: eliminate both analog gaps completely. Each cluster (invoice management, partner communication) is all-or-nothing — partial delivery does not achieve the stated goals.

**Must-Have Capabilities:**
- `PartnerInvoice` entity + full status lifecycle (DRAFT→SENT→PAID→OVERDUE→CANCELLED)
- Annual fee table with inline editing + bulk "Create All Drafts" per billing year
- Single invoice creation (for mid-year partner additions)
- PDF invoice generation with BATbern branding (library TBD — architect ADR required before sprint 1)
- Send invoice via SES email; manual overdue reminder with PDF re-attached
- Auto-overdue detection via Spring `@Scheduled` cron (daily)
- Mark-as-paid with payment date + optional reference
- `PartnerBillingInfo` self-service tab in partner portal (address, contact, VAT, PO number)
- Partner invoice history + PDF download in portal
- Admin-configurable invoice settings (association name, address, IBAN, invoice number prefix, default due date offset, default PDF language)
- Publish / unpublish meeting notes + action items to partner
- Partner view of published meeting notes + structured action items
- RSVP status visible in partner portal
- Attachment upload (organizer) + download (organizer + partner, own company)

### Phase 2 — Growth (Post-MVP)

- Automated overdue reminder emails (currently manual trigger only)
- Bulk send all DRAFT invoices for a year in one action
- Revenue dashboard: total billed / collected / outstanding per year
- Invoice number format customization

### Phase 3 — Expansion (Future)

- Swiss QR-Rechnung (ISO 20022) for one-scan bank transfer
- Payment link integration (Stripe / TWINT)
- Automatic bank reconciliation
- Partner health score (attendance + engagement + payment history)

### Risk Mitigation

| Risk | Mitigation |
|------|-----------|
| PDF library selection (technical) | Architect ADR required before sprint 1; fallback: HTML → headless Chrome render |
| Market validation | None needed — solves confirmed internal pain for known user base |
| Scope reduction | Attachments are most deferrable; invoice + notes sharing are non-negotiable |

## User Journeys

### Journey 1: Organizer — Invoice Season (Happy Path)

*Lukas, BATbern organizer. Mid-January. Last year: afternoon hunting billing addresses in email, hand-crafting PDFs in InDesign, attaching one by one. This year: BATbern.*

He opens **Partners → Fees & Invoices**. The fee table shows all 7 active partners with tier and annual fee. He clicks **"Create All Drafts for 2026"**. Seven DRAFT invoices appear, pre-filled with amount, partner name, and 31 March due date. He updates two fees inline and hits Enter. He selects all and clicks **Send Invoices**. Seven branded PDFs land in each partner's billing contact inbox.

Three weeks later: five PAID, one SENT, one OVERDUE. He clicks the overdue partner, sends a reminder. Following week: all seven PAID. *4:30 PM. First invoicing day Lukas leaves on time.*

**Capabilities:** fee table, bulk draft creation, PDF generation, send invoice email, invoice status board, manual reminder, mark-as-paid.

---

### Journey 2: Organizer — After the Partner Meeting (Happy Path)

*Day after the spring partner meeting. Lukas has his notes.*

He opens the Acme AG meeting detail, pastes minutes, adds a structured **Action Items** section (three items with owner + due date each), clicks **Publish to Partner**. Notes are visible in Acme AG's portal immediately. He uploads the signed sponsorship contract via the new **Attachments** tab.

**Capabilities:** publish meeting notes toggle, action items structure, partner-visible notes, attachment upload.

---

### Journey 3: Partner — First Login After the Meeting (Happy Path)

*Sandra, head of community at Acme AG. Attended last week's partner meeting. Needs to brief her CMO.*

She opens the **Meetings** tab. A green "Notes Published" indicator on the spring meeting entry. She opens it: full minutes, action items with owners and dates. She downloads the notes PDF, forwards to her CMO.

She opens **Billing Info** — not yet filled in. She enters billing address, her name, and the internal PO number mandatory for their accounts payable. She saves. Two days later, the 2026 invoice arrives pre-addressed. She forwards it to accounts payable.

**Capabilities:** partner meeting notes view, action items display, billing info self-service, invoice receipt via email.

---

### Journey 4: Partner — Invoice Confusion (Edge Case)

*Sandra's accounts payable paid with a different reference number. Call: "Is this paid?"*

She opens the portal: invoice status **SENT**. She emails Lukas. Lukas finds the payment, opens the invoice, clicks **Mark as Paid**, enters date and bank reference. Invoice flips to **PAID**. Sandra refreshes — green. *One action. Resolved.*

**Capabilities:** partner invoice status visibility, mark-as-paid with reference, organizer reconciliation.

---

### Journey 5: Organizer — New Partner Onboarding (Admin/Config Path)

*New GOLD-tier sponsor joins mid-year. Lukas creates the partner record, sets a pro-rated fee, creates one DRAFT invoice manually. Partner fills in billing info; invoice sent within the week.*

*Separately: association moves offices. Lukas updates the address in **Admin → Invoice Settings** — one change, reflected in all future PDFs.*

**Capabilities:** single invoice creation, admin-configurable association settings.

---

### Journey Requirements Traceability

| Capability | Journey |
|-----------|---------|
| Fee table + bulk draft creation | J1 |
| PDF generation + send via email | J1 |
| Invoice status board (all partners) | J1 |
| Manual overdue reminder | J1 |
| Mark as paid + payment reference | J1, J4 |
| Publish meeting notes to partner | J2 |
| Action items structure in notes | J2 |
| Attachment upload/download | J2 |
| Partner: meeting notes + action items view | J3 |
| Partner: billing info self-service | J3 |
| Partner: invoice history + status | J3, J4 |
| Partner: RSVP status visibility | MVP scope |
| Single invoice creation | J5 |
| Admin-configurable invoice settings | J5 |

## SaaS B2B Architecture Requirements

This section captures how the new features integrate with BATbern's existing multi-tenant SaaS B2B architecture. No new architectural paradigms are introduced — all patterns extend existing decisions.

### Tenant Model

BATbern uses **company-scoped tenancy** (ADR-003): partner data isolated by `companyName` (String, 12-char max), no FK to other services. Cross-tenant isolation enforced at the API layer via `PartnerSecurityService`.

New entities follow the same model:
- `PartnerInvoice.companyName` — scopes invoice records per partner company
- `PartnerBillingInfo.companyName` (PK) — one billing profile per company, partner-owned
- `PartnerAttachment.companyName` — scopes files per partner company

### RBAC Matrix

| Feature | ORGANIZER | PARTNER |
|---------|-----------|---------|
| View fee table (all partners) | ✅ | ❌ |
| Create / edit annual fee | ✅ | ❌ |
| Create / send / cancel invoice | ✅ | ❌ |
| Mark invoice as paid | ✅ | ❌ |
| Send overdue reminder | ✅ | ❌ |
| View all partners' invoices | ✅ | ❌ |
| View own invoices + download PDF | ✅ | ✅ Own only |
| View any partner's billing info | ✅ | ❌ |
| Edit own billing info | ❌ (view only) | ✅ Own only |
| Upload attachments | ✅ | ❌ |
| Download attachments | ✅ | ✅ Own company only |
| Publish / unpublish meeting notes | ✅ | ❌ |
| View published meeting notes | ✅ | ✅ Own only |
| View RSVP status | ✅ All partners | ✅ Own only |
| Configure invoice admin settings | ✅ | ❌ |

### Integration Contracts

All integrations reuse existing platform patterns — no new external services:

| Integration | Pattern | Source |
|------------|---------|--------|
| PDF generation | Server-side Java library (ADR TBD) | New — architect decision required |
| Email delivery | AWS SES async (202 Accepted) | Reused from Stories 6.5, 8.3 |
| File storage | AWS S3 presigned URL upload | Reused from Epic 6 |
| Overdue detection | Spring `@Scheduled` cron | Reused from Epic 5 auto-publish |

### Compliance

- **GDPR:** `PartnerBillingInfo` contains personal data (contact name + email). Stored for legitimate B2B invoicing purpose only; deletable on partnership end; invoice records retained separately per Swiss 10-year accounting obligation (no cascade delete on partner removal).
- **PDF storage:** S3 objects are private; access via presigned URL with 15-minute TTL.

### Implementation Constraints

- All new controllers use existing `@PreAuthorize` + `PartnerSecurityService.isCurrentUserCompany()` — no new security infrastructure
- OpenAPI spec first; Spring Boot interface generated; TypeScript types generated for frontend
- `PartnerInvoice` state transitions are server-side only — clients trigger named actions (sendInvoice, markAsPaid, sendReminder, cancel), never set status directly
- Invoice number format: `BAT-{year}-{companyName}-{seq}` — DB sequence per (year, companyName) pair guarantees uniqueness
- **🏗️ Architect Decision Required:** Select PDF generation library. Candidates: iText 7 (AGPL — check license compatibility), Apache PDFBox (Apache 2.0), OpenPDF (LGPL). Criteria: license, template flexibility, Fargate memory footprint, font/image embedding. Document as ADR before sprint 1.

## Functional Requirements

### Invoice Management

- **FR1:** Organizer can set and update the annual sponsorship fee for each active partner
- **FR2:** Organizer can create a single invoice for a specific partner for a given billing year
- **FR3:** Organizer can bulk-create draft invoices for all active partners with a fee set for a given billing year
- **FR4:** Organizer can review and edit a draft invoice (amount, due date, description) before sending
- **FR5:** Organizer can send an invoice to a partner, triggering PDF generation and email delivery to the partner's billing contact
- **FR6:** Organizer can send a payment reminder for an overdue invoice, re-attaching the PDF
- **FR7:** Organizer can mark an invoice as paid, recording payment date and an optional payment reference
- **FR8:** Organizer can cancel an invoice from any non-terminal status
- **FR9:** Organizer can view all partners' invoices in a consolidated view, filterable by billing year and status
- **FR10:** System transitions invoices from SENT to OVERDUE when due date passes without payment
- **FR11:** Partner can view their own company's invoice history including status and billing year
- **FR12:** Partner can download a PDF copy of any of their own invoices

### Partner Billing Information

- **FR13:** Partner can create and update their company's billing information (contact name, email, postal address, VAT number, purchase order number)
- **FR14:** Organizer can view any partner's billing information
- **FR15:** System pre-populates generated invoice PDFs from the partner's billing information
- **FR16:** Organizer can configure association invoice settings (name, address, IBAN, invoice number prefix, default due date offset, default PDF language) from the admin section
- **FR17:** System uses the latest admin-configured association details in all generated invoice PDFs

### Invoice PDF Generation

- **FR18:** System generates a branded PDF invoice from partner billing info, invoice record, and admin-configured association details
- **FR19:** Generated invoice PDFs are stored and retrievable by time-limited link for organizers and the owning partner company

### Meeting Notes & Action Items

- **FR20:** Organizer can publish meeting notes and action items to a partner, making them visible in the partner portal
- **FR21:** Organizer can unpublish previously shared meeting notes, removing partner portal visibility
- **FR22:** Partner can view published meeting notes and action items for meetings involving their company
- **FR23:** Organizer can structure action items within meeting notes with owner name and due date per item

### Attachments

- **FR24:** Organizer can upload one or more files to a partner's record (contracts, logos, sponsorship agreements)
- **FR25:** Organizer can delete attachments from a partner's record
- **FR26:** Partner can view and download files attached to their own company's record

### Meeting RSVP Visibility

- **FR27:** Partner can view the RSVP status of meeting invites sent to their company's contacts
- **FR28:** Organizer can view RSVP status per partner per meeting

### Admin Configuration

- **FR29:** Organizer can manage invoice configuration settings (association name, address, IBAN, invoice number prefix, default due date offset, default PDF language)
- **FR30:** System applies the latest admin-configured association settings to all newly generated invoice PDFs

## Non-Functional Requirements

### Performance

- **NFR1:** Invoice PDF generation completes within 3 seconds p95; generated file immediately available for download or email attachment
- **NFR2:** Invoice status board (all partners, single year, up to 20 records) loads within 2 seconds p95
- **NFR3:** Partner portal billing tab (invoice history + billing info form) loads within 2 seconds p95
- **NFR4:** Attachment presigned URL request responds within 1 second; upload goes directly to S3

### Security

- **NFR5:** `PartnerBillingInfo` and `PartnerInvoice` records accessible only to the owning partner company and organizers — no cross-tenant leakage at any API endpoint
- **NFR6:** Invoice PDFs in S3 are private; download requires a presigned URL with max 15-minute TTL
- **NFR7:** Attachment files in S3 are partner-scoped; presigned download URLs expire within 15 minutes
- **NFR8:** All new endpoints enforce existing `@PreAuthorize` + `PartnerSecurityService` role checks — no new security patterns
- **NFR9:** Billing contact personal data (name, email) stored for invoice delivery purpose only; deletable on partnership end without cascade-deleting invoice records

### Accessibility

- **NFR10:** All new frontend components meet WCAG 2.1 AA, consistent with the platform baseline from Epic 6
- **NFR11:** Invoice status indicators use colour plus text label — not colour alone

### Integration & Reliability

- **NFR12:** Invoice email dispatch via SES uses async pattern (202 Accepted); SES failure is logged and invoice status remains SENT — organizer can resend
- **NFR13:** Auto-overdue cron runs daily; missed executions recover on next run without duplicate transitions
- **NFR14:** PDF generation is idempotent — same invoice always produces the same PDF; stored PDFs are not regenerated on every download
- **NFR15:** Attachment upload uses presigned S3 URL pattern — backend never proxies file bytes

### Internationalisation (i18n)

- **NFR16:** All new frontend UI strings externalised in existing `public/locales/{de,en}/` files — no hardcoded display strings in components
- **NFR17:** German (`de`) is the primary locale; English (`en`) is the fallback — consistent with existing platform convention
- **NFR18:** Invoice PDF language is configurable per partner (DE / EN); admin invoice settings define the default; organizer can override per invoice before sending
- **NFR19:** All invoice PDF text (labels, headings, line items, footer) driven by locale strings — no hardcoded language in the PDF template
- **NFR20:** Invoice email subjects and bodies (initial send + overdue reminder) sent in the configured invoice language for that partner
