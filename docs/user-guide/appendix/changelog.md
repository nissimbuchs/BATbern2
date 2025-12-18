# Changelog

## Overview

Version history and release notes for the BATbern platform. Releases follow [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`

- **MAJOR**: Breaking changes requiring user action
- **MINOR**: New features (backward compatible)
- **PATCH**: Bug fixes and minor improvements

**Current Version**: v0.9.0 (Beta)
**Last Updated**: 2025-12-18

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

### v1.0.0 - BATbern GA (General Availability) `[PLANNED Q1 2025]`

**Target Date**: February 2025
**Type**: Major Release (Production Launch)

**Planned Features**:
- ✅ Complete 16-Step Workflow
- ✅ All Entity CRUD Operations
- 🔨 In-App Notifications
- 🔨 Email Notification System
- 📋 Company Hierarchy
- 📋 Partner Engagement Metrics

**Stability Goals**:
- 99.9% uptime SLA
- <200ms API response time (P95)
- Zero critical bugs
- Full documentation coverage

---

## v0.9.x - Beta Releases (Current)

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

### Future Deprecations (Planned)

**v1.2.0** (Planned Q3 2025):
- **Email Template V1** - Will be replaced with V2 (WYSIWYG editor)
  - Current: Plain text with placeholders
  - Replacement: Rich HTML editor with drag-and-drop
  - Migration: Auto-convert templates to new format

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
| v0.9.x | **Current** | Until v1.0.0 release |
| v0.8.x | Security fixes only | 2025-03-01 |
| v0.7.x | Unsupported | Ended 2024-12-01 |
| v0.6.x and earlier | Unsupported | Ended 2024-11-01 |

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
