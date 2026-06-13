# Changelog

## Overview

Version history and release notes for the BATbern platform. Releases follow [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`

- **MAJOR**: Breaking changes requiring user action
- **MINOR**: New features (backward compatible)
- **PATCH**: Bug fixes and minor improvements

**Current Version**: v1.3.0
**Last Updated**: 2026-06-13

---

## Release Format

Each release includes:
- **Version Number** & Release Date
- **Type** (Major / Minor / Patch)
- **New Features** - Functional additions
- **Improvements** - Enhancements to existing features
- **Bug Fixes** - Resolved issues
- **Breaking Changes** - Actions required by users (if any)
- **Known Issues** - Limitations to be addressed

---

## Upcoming Releases

### v1.4.0 - Attendee Contribution + Spring Boot 4 `[PLANNED]`

**Type**: Minor Release (Epic 7 finalization + Epic 13 groundwork)

**Planned Features — Epic 7 (Attendee Experience, in review)**:
- 📋 "The Slides Are Online" mail — auto-created organizer task (~1 day post-event) emailing active registrants
- 📋 Thank-the-Organizers — one-click post-event thank-you with optional note + public appreciation counter
- 📋 The Apéro Continues — time-boxed per-session post-event Q&A that freezes onto the session archive
- 📋 Attendee Event History — logged-in dashboard of events the attendee registered for / attended
- 📋 Curated Thank-You Marquee — feature selected thank-you notes in the public homepage marquee

**Planned Features — Platform**:
- 📋 Epic 13: Spring Boot 4 migration across all backend services (SB 3.5 OSS support ends 2026-06-30)
- 📋 Apple / generic OIDC SSO (Story 12-10) — extends "Continue with Google" with additional providers

> **Note**: The former "v1.3.0 — Speaker Authentication Unification (Epic 9)" plan has shipped in a
> different form. Epic 9's JWT magic-link approach was **superseded** by Epic 11 (unified Cognito
> speaker workflow) and Epic 12 (Google SSO), both released in v1.3.0 below.

---

## v1.3.x - Current Release

### v1.3.0 - Google SSO, Unified Speaker Workflow & Organizer/Admin Cluster `[2026-06-13]`

**Type**: Minor Release (Epics 10, 11, 12 + partial Epic 7)

**New Features — Authentication (Epic 12)**:
- ✅ "Continue with Google" single sign-on — Google OIDC federation at `auth.batbern.ch`
- ✅ Transparent account linking (links a Google identity to an existing account by email)
- ✅ Just-in-time (JIT) user provisioning on first SSO login
- ✅ Terms-of-Service consent gate + federated onboarding completion (consent / company / newsletter)
- ✅ Google profile-avatar import; runtime kill-switch (`FEATURES_SSO_ENABLED`)

**New Features — Unified Speaker Workflow (Epic 11)**:
- ✅ Speaker workflow reduced to an **8-state model**; `SpeakerWorkflowService` is the sole status writer
- ✅ Workflow unified into the event-management service (standalone `speakers` table + speaker-coordination refs dropped)
- ✅ Speaker portal migrated to **AWS Cognito** authentication — the original magic-link login was fully torn down
- ✅ Cognito user provisioning at the `READY` transition; kanban drawer redesign + guided drag; speaker company self-service

**New Features — Admin Tools, Newsletter & Public Site (Epic 10)**:
- ✅ Administration page (`/organizer/admin`) — Event Types, Import Data, Task Templates, Email Template management (TinyMCE for content, Monaco for layout); task deadline reminder emails; blob/heat-map topic selector
- ✅ Newsletter — subscription, sending, template selection, subscriber management; email-reply unsubscribe/deregistration; SES forwarding/distribution lists; SES bounce processing + list hygiene
- ✅ Registration lifecycle — registration status indicator; venue capacity enforcement + **waitlist** management; self-service deregistration
- ✅ Public site — event photos gallery; event teaser image; event description on the homepage; **moderator presentation page** (+ animations); organizer analytics dashboard
- ✅ **Turnstile** bot protection on public submit flows; additional user emails ("receive at" + "send-as" aliases); legacy BAT-format data export/import; AI-assisted event content creation

**New Features — Attendee Contribution (Epic 7, initial)**:
- ✅ Topics From the Floor — logged-in attendees suggest future topics (tagged `source = community`); organizers triage in the topic-suggestion admin UI
- ✅ "I Could Speak on That" — logged-in attendees self-nominate as speakers (session title + abstract); creates a `speaker_pool` entry at `IDENTIFIED` for organizer triage (no auto-provisioning)

**Improvements**:
- ✅ Company picker rework — the company field on the profile and registration forms is now a
  selection-locked combobox: it shows each company's **display name** (not the internal slug),
  locks the chosen company into a removable chip (no more "Firmen-ID muss aus 1-12 Buchstaben…"
  format error from editing the slug), and offers an explicit **"Create new company"** action
  instead of silently creating duplicates. Company search now matches both the internal name and
  the display name, so existing companies surface even when you type their spaced/cased name.
- ✅ Event deletion now ignores programmatic registrations. Every new event auto-enrols all
  organizers and partners as participants, which previously left the **Delete Event** button
  permanently disabled. Deletion is now blocked only when an event has *real* (self-registered)
  attendees; events with only auto-enrolled stakeholders can be deleted. The API returns `409`
  if real attendees exist, and the event detail exposes a `realAttendeeCount` field.
- ✅ Post-event 14-day homepage window: the public homepage shows the most recent event for 14 days
  after it ends (archive-style view — timetable and speakers visible, no registration/logistics).
  After 14 days a nightly scheduler auto-archives the event.
- ✅ Legacy structural entries in historical events — the "Moderation" slots (28 rows, BATbern11–48)
  and the "Programmheft" entries (41 rows, BATbern1–41) — are reclassified from content talks to
  technical sessions (`session_type = moderation`), matching how moderation slots are modelled in
  new events (BATbern57+). Applied directly to the production database (no migration).

**Breaking Changes**:
- Speaker magic-link login URLs are **retired**. Speakers now log in via AWS Cognito
  (email/password or "Continue with Google"). Existing speakers were migrated; old magic-link
  emails no longer authenticate.

---

## v1.2.x - Current Release

### v1.2.2 - Email & Public Event Page Fixes `[2026-04-07]`

**Type**: Patch Release

**Bug Fixes**:
- ✅ Registration confirmation emails and ICS calendar attachments now use the correct, resolved event start and end times (previously used placeholder/incorrect values)
- ✅ Email forwarding Lambda now correctly forwards CC addresses as mailing-list recipients, and recognises all `batbernNN@batbern.ch` address variants
- ✅ Public event page now shows resolved start **and** end times (not just the event date)
- ✅ Newsletter subscriber list: registered-user icon corrected; subscriber display names improved for readability
- ✅ TypeScript 7 compatibility: deprecated `baseUrl`-only `tsconfig.json` options resolved; removed stale lucide icon imports

---

### v1.2.1 - Speaker JWT Magic Link Authentication `[2026-02-23]`

**Type**: Patch Release (Epic 9, Story 9.1)

**New Features**:
- ✅ JWT-based magic link authentication for speakers — RS256 signed tokens, HTTP-only cookie, 30-day reusable sessions
- ✅ Speaker portal auto-login on link click (no username/password required)
- ✅ Organizer-controlled invitation emails trigger JWT generation per speaker/event
- ✅ Backward compatible with Epic 6 staging token system

**Architecture**:
- JWT signed with private RSA key (RS256); public key exposed at `/api/v1/speaker-auth/jwks.json`
- Token audience: `batbern-speaker-portal`; issued by `batbern-speaker-coordination`
- Magic links reusable within 30-day session window; new token issued on next invite

---

### v1.2.0 - Partner Coordination Complete `[2026-02-22]`

**Type**: Minor Release (Epic 8)

**New Features**:
- ✅ Partner Attendance Analytics Dashboard — per-company attendance table (last 5 years / full history toggle), cost-per-attendee KPI, XLSX export via Apache POI
- ✅ Topic Suggestions & Voting — partners suggest topics + toggle-vote (one per company); organizers set status (Selected/Declined) with optional "planned for event" note; sorted by votes descending
- ✅ Partner Meeting Coordination — meeting record linked to BATbern event by event code; free-text agenda; RFC 5545 ICS calendar invite sent async via AWS SES to all partner contacts; post-meeting notes

**Architecture**:
- Analytics data queried on demand from `event-management-service`; Caffeine-cached 15 min
- No materialized views, no nightly batch (max ~60 events ever)
- Standard ICS (no Microsoft Graph / Outlook dependency)
- Role-based access enforced at API level (PARTNER sees own company only; ORGANIZER sees all)

---

### v1.1.0 - Speaker Self-Service Portal Complete `[2026-02-16]`

**Type**: Minor Release (Epic 6)

**New Features**:
- ✅ Automated Speaker Invitations — magic link emails via AWS SES; bulk send; tracking (sent/responded)
- ✅ Self-Service Response Portal — Accept/Decline via unique link (no account required); auto-updates organizer speaker status; organizer manual override available
- ✅ Speaker Material Self-Submission — multi-step wizard for title, abstract (1000-char limit), CV, photo, presentation; drag-and-drop upload via S3 presigned URLs; draft auto-save
- ✅ Speaker Dashboard — view upcoming/past events, material submission status, event details; WCAG 2.1 AA accessible; i18n EN/DE; 30-day magic link session
- ✅ Automated Deadline Reminders — configurable 3-tier escalation (friendly → urgent → organizer escalation); deduplication (no reminder if materials already submitted); full reminder log

**Multi-Role E2E Test Auth**:
- ✅ `make setup-test-users` — authenticate all roles at once
- ✅ Per-role token storage: `~/.batbern/staging-{organizer,speaker,partner}.json`
- ✅ Playwright `speaker` and `partner` projects activated by env var

---

## v0.9.x - Prior Releases

### v0.9.0 - Workflow Phase F & Documentation `[2025-12-18]`

### v0.9.0 - Workflow Phase F & Documentation `[2025-12-18]`

**Type**: Minor Release

**New Features**:
- ✅ Workflow Step 14: Moderator Assignment
- ✅ Workflow Step 16: Partner Meeting Coordination
- ✅ Comprehensive User Guide (40 pages across 6 sections)
- ✅ Troubleshooting guides for authentication, uploads, workflow
- ✅ Glossary with 80+ terms
- ✅ Keyboard shortcuts reference (planned features documented)

**Improvements**:
- Enhanced workflow validation messages (more specific error details)
- Improved file upload progress indicators (show speed, time remaining)
- Partner directory performance optimized (50% faster load time)
- Drag-and-drop slot assignment UX refined (visual feedback improved)

**Bug Fixes**:
- Fixed: Speaker status not updating after content submission (Issue #142)
- Fixed: Company logo preview not showing after upload (Issue #145)
- Fixed: Workflow validation incorrectly blocking Step 8 advancement (Issue #148)
- Fixed: Search autocomplete showing archived companies (Issue #151)

**Known Issues**:
- Step 13 (Newsletter Creation): Template system incomplete (60% done)
- Step 15 (Catering Coordination): Dietary restrictions UI in progress (70% done)
- File upload: PPTX → PDF auto-conversion not yet implemented

---

### v0.8.0 - Workflow Phase E & Publishing `[2024-11-15]`

**Type**: Minor Release

**New Features**:
- ✅ Workflow Step 11: Progressive Topic Publishing
- ✅ Workflow Step 12: Speaker Publishing & Finalization
- ✅ Dropout handling workflow (speaker withdrawals after acceptance)
- ✅ Public event preview mode (attendee-facing view)

**Improvements**:
- Workflow dashboard now shows completion percentage per phase
- Event timeline visualization improved (Gantt chart style)
- Speaker profile templates added (faster bio creation)

**Bug Fixes**:
- Fixed: Published topics reverting to draft after edit (Issue #128)
- Fixed: Speaker photos not displaying in public view (Issue #131)
- Fixed: Timezone handling for event dates (now UTC-based) (Issue #134)

---

### v0.7.0 - Workflow Phase D & Assignment `[2024-10-01]`

**Type**: Minor Release

**New Features**:
- ✅ Workflow Step 9: Overflow Management with voting
- ✅ Workflow Step 10: Drag-and-drop slot assignment
- ✅ Conflict detection (speaker availability, double-booking)
- ✅ Time slot templates by event type

**Improvements**:
- Slot assignment grid redesigned (better UX for 10+ speakers)
- Speaker cards now show expertise tags for easier matching
- Conflict warnings display in real-time during drag operations

**Bug Fixes**:
- Fixed: Drag-and-drop not working in Firefox (Issue #118)
- Fixed: Slot times not respecting venue timezone (Issue #121)
- Fixed: Overflow voting counts incorrectly calculated (Issue #124)

---

### v0.6.0 - Workflow Phase C & Quality Review `[2024-08-20]`

**Type**: Minor Release

**New Features**:
- ✅ Workflow Step 7: Quality Review with Approve/Revise/Reject
- ✅ Workflow Step 8: Minimum Threshold Validation
- ✅ Review queue with prioritization (deadline-based)
- ✅ Content preview modal (view slides inline)

**Improvements**:
- Review comments now support rich text formatting
- Reviewers can attach files (feedback documents, marked-up slides)
- Email notifications for revision requests (manual trigger)

**Bug Fixes**:
- Fixed: Quality threshold not accounting for event type (Issue #105)
- Fixed: Review comments not saving correctly (Issue #108)
- Fixed: Content preview failing for large PDFs (Issue #111)

---

## v0.5.x - Phase B Releases

### v0.5.0 - Workflow Phase B & Outreach `[2024-07-10]`

**Type**: Minor Release

**New Features**:
- ✅ Workflow Step 4: Speaker outreach with email invitations
- ✅ Workflow Step 5: Status management (Accepted/Rejected/Pending tracking)
- ✅ Workflow Step 6: Content collection with upload interface
- ✅ Email template system (basic customization)

**Improvements**:
- Speaker invitation emails now include event details automatically
- Status dashboard shows response rate and time-to-response metrics
- File upload supports drag-and-drop (previously button-only)

**Bug Fixes**:
- Fixed: Invitation emails containing incorrect event date (Issue #92)
- Fixed: Content upload not validating file size correctly (Issue #95)
- Fixed: Status filters not persisting after page refresh (Issue #98)

---

## v0.4.x - Phase A & Core Features

### v0.4.0 - Workflow Phase A & Setup `[2024-05-25]`

**Type**: Minor Release

**New Features**:
- ✅ Workflow Step 1: Event setup with type/date/venue
- ✅ Workflow Step 2: Topic selection with heat map integration
- ✅ Workflow Step 3: Speaker brainstorming with minimum threshold
- ✅ Topic heat map visualization (20+ years historical data)

**Improvements**:
- Heat map now supports filtering by time range and category
- Speaker brainstorming UI redesigned (card-based layout)
- Event setup wizard added (guided 3-step process)

**Bug Fixes**:
- Fixed: Heat map colors not matching legend (Issue #78)
- Fixed: Topic backlog search case-sensitivity (Issue #81)
- Fixed: Event duplication creating corrupted records (Issue #84)

---

### v0.3.0 - Entity Management Complete `[2024-04-10]`

**Type**: Minor Release

**New Features**:
- ✅ Partner management CRUD (directory, contacts, meetings)
- ✅ Speaker management CRUD (profiles, status tracking)
- ✅ Company-speaker associations
- ✅ Partner tier badges (Platinum, Gold, Silver, Bronze)

**Improvements**:
- Partner directory now sortable by tier, engagement, recent activity
- Speaker search improved (fuzzy matching, partial name matching)
- Company logos now support SVG format (in addition to PNG/JPG)

**Bug Fixes**:
- Fixed: Partner contacts not associating with company correctly (Issue #65)
- Fixed: Speaker bio character limit not enforced (Issue #68)
- Fixed: Duplicate partner detection false positives (Issue #71)

---

### v0.2.0 - Core Entity Management `[2024-03-01]`

**Type**: Minor Release

**New Features**:
- ✅ Company management CRUD
- ✅ User management CRUD with role-based access
- ✅ Event management CRUD (3 event types)
- ✅ Swiss UID validation with check digit algorithm
- ✅ Company logo upload via presigned S3 URLs

**Improvements**:
- Search performance improved (added full-text indexes)
- Role promotion/demotion now requires confirmation dialog
- Event list now paginated (50 per page, previously all-at-once)

**Bug Fixes**:
- Fixed: User email validation too strict (blocked valid domains) (Issue #42)
- Fixed: Company logo upload hanging at 99% (Issue #45)
- Fixed: Event date picker not showing correct timezone (Issue #48)
- Fixed: GDPR data export missing user activity logs (Issue #51)

---

## v0.1.x - Foundation Releases

### v0.1.0 - Foundation & Authentication `[2024-01-15]`

**Type**: Initial Beta Release

**New Features**:
- ✅ AWS Cognito authentication (email/password)
- ✅ Password reset flow
- ✅ Session management (8-hour sessions, 2-hour idle timeout)
- ✅ Role-based access control (4 roles)
- ✅ PostgreSQL database (RDS Single-AZ)
- ✅ S3 file storage with CloudFront CDN
- ✅ AWS ECS Fargate deployment
- ✅ CloudWatch monitoring and logging

**Initial Limitations**:
- Only ADMIN role can create users (no self-registration)
- Single-language only (English)
- No mobile optimization yet
- Limited analytics (basic usage metrics only)

**Known Issues**:
- Session timeout not showing warning before logout (Issue #12)
- File upload progress bar jumps from 0% to 100% instantly (Issue #15)
- Some error messages too technical (not user-friendly) (Issue #18)

---

## Breaking Changes History

### v0.9.0 Breaking Changes

**None** - All changes backward compatible

### v0.8.0 Breaking Changes

**Timezone Handling** (Issue #134):
- **What Changed**: Event dates now stored in UTC, displayed in user's timezone
- **Impact**: Existing event dates may appear shifted if created in different timezone
- **Action Required**: Review all future events, adjust dates if needed
- **Migration**: Auto-migration applied on upgrade (no manual action for most users)

### v0.6.0 Breaking Changes

**None** - All changes backward compatible

### v0.4.0 Breaking Changes

**None** - All changes backward compatible

### v0.2.0 Breaking Changes

**User Email Validation** (Issue #42):
- **What Changed**: Email validation regex relaxed to support more TLDs
- **Impact**: Previously rejected emails now accepted
- **Action Required**: None (improvement only)

---

## Security Updates

### High Priority Security Fixes

**v0.9.0 Security Updates**:
- Updated AWS SDK to patch credential leak vulnerability (CVE-2024-XXXXX)
- Enhanced CORS policy to prevent cross-origin attacks
- Added rate limiting to authentication endpoints (prevent brute force)

**v0.8.0 Security Updates**:
- Upgraded PostgreSQL driver to fix SQL injection vulnerability (CVE-2024-YYYYY)
- Strengthened password requirements (now requires special character)
- Implemented session token rotation (reduces session hijacking risk)

**v0.5.0 Security Updates**:
- Presigned URL expiry reduced from 60 minutes to 15 minutes
- Added virus scanning for uploaded files (ClamAV integration planned)
- Implemented CSRF protection on all POST/PUT/DELETE endpoints

---

## Performance Improvements

### Notable Performance Gains

| Release | Improvement | Metric | Details |
|---------|-------------|--------|---------|
| v0.9.0 | Partner directory load time | 50% faster | Implemented caching, reduced DB queries |
| v0.8.0 | Event list pagination | 70% faster | Added database indexes on common filters |
| v0.7.0 | Drag-and-drop responsiveness | 3x faster | Optimized React re-renders, debouncing |
| v0.6.0 | Content preview loading | 60% faster | Lazy load PDF renderer, progressive rendering |
| v0.4.0 | Heat map rendering | 40% faster | Client-side caching, compressed data transfer |
| v0.2.0 | Search autocomplete | 80% faster | Full-text indexes, query optimization |

---

## Deprecation Notices

### Current Deprecations

**None** - No features currently deprecated

### Completed Deprecations

**Speaker Magic-Link Login** (removed in v1.3.0 — Epic 11):
- **What changed**: The JWT magic-link speaker login was fully torn down.
- **Replacement**: AWS Cognito authentication (email/password or "Continue with Google").
- **Migration**: Existing speakers were migrated to Cognito; old magic-link emails no longer authenticate.

### Future Deprecations (Planned)

**None currently scheduled.**

---

## Feedback & Bug Reports

### How to Report Issues

**Bug Reports**: [GitHub Issues](https://github.com/batbern/platform/issues) or support@batbern.ch

**Include**:
- Version number (Settings → About)
- Steps to reproduce
- Expected behavior
- Actual behavior
- Screenshot (if visual issue)
- Browser and OS

**Priority Definitions**:
- **Critical**: Platform down or data loss
- **High**: Major feature broken, no workaround
- **Medium**: Feature broken, workaround exists
- **Low**: Minor inconvenience or cosmetic issue

**Response Times**:
- Critical: <2 hours
- High: <8 hours
- Medium: 1-2 business days
- Low: Next sprint (2 weeks)

---

## Version Support Policy

### Supported Versions

| Version | Status | Support End Date |
|---------|--------|------------------|
| v1.3.0 | **Current** | Until v1.4.0 release |
| v1.2.x | Security fixes only | Until v1.4.0 release |
| v1.1.x | Unsupported | Ended 2026-05-01 |
| v0.9.x | Unsupported | Ended 2026-01-01 |
| v0.8.x and earlier | Unsupported | Ended 2025-06-01 |

**Support Includes**:
- Security patches (critical vulnerabilities)
- Data integrity bug fixes (corruption, loss)
- Documentation updates

**Not Included in Support**:
- New feature backports
- Performance improvements
- Cosmetic bug fixes

### Upgrade Recommendations

- **Always upgrade to latest**: Cumulative improvements, security patches
- **Test in staging first**: Verify compatibility with your workflows
- **Review breaking changes**: Check changelog for required actions
- **Contact support before upgrade**: If running custom integrations

---

## Related Resources

- **[Feature Status](feature-status.md)** - Current implementation status and roadmap
- **[Glossary](glossary.md)** - Platform terminology
- **[Documentation Home](../README.md)** - Full user guide

---

**Questions?** Contact support@batbern.ch or check [Status Page](https://status.batbern.ch)

**Back to Main**: Return to [Documentation Home](../README.md) →
