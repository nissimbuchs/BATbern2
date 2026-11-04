# Screenshot Index

**Last Updated**: 2026-11-04

**Total**: 57 screenshots referenced by the guide.

All screenshots in this guide, where they are used, and how they are made.

---

## How the screenshots are made

- **Source**: local dev running an anonymized copy of production (fake names, emails `@example.invalid`, mail bodies replaced by sample text). Main event **BATbern60** (state `AGENDA_PUBLISHED`, event day 6 November 2026); archived event **BATbern59** for the wrap-up and the public archive.
- **Spec**: `web-frontend/e2e/workflows/documentation/capture-user-guide.spec.ts`, config `web-frontend/playwright.user-guide.config.ts`. The spec is read-only: it only navigates and opens menus or dialogs, which it closes with Escape. Every write request to the local API is aborted, and the Live Control poll is sent without a token so it does not record organizer presence.
- **Run** (local dev up, role tokens in `~/.batbern/staging-{organizer,speaker,partner}.json`):

```bash
cd web-frontend
npx playwright test -c playwright.user-guide.config.ts --workers=1
ONLY=organizer npx playwright test -c playwright.user-guide.config.ts --workers=1   # one role
```

- **Format**: PNG, desktop 1440×900; phone shots 390×844 at 2× (shown at 300 px width). English UI.
- **Live Control** is captured with the browser clock set to 6 November 2026, 16:22, so a session is running.

---

## Getting started

| File | View | Used on |
|------|------|---------|
| `getting-started/top-bar.png` | Top bar for an organizer | [getting-started/dashboard.md](../getting-started/dashboard.md) |
| `getting-started/events-list.png` | Events page | [getting-started/dashboard.md](../getting-started/dashboard.md) |
| `getting-started/login-page.png` | Login page | [getting-started/login.md](../getting-started/login.md) |
| `getting-started/forgot-password.png` | Forgot password page | [getting-started/login.md](../getting-started/login.md) |

## Event page (BATbern60)

| File | View | Used on |
|------|------|---------|
| `event-page/wrapup-thank-you-notes.png` | Wrap-up tab, thank-you notes with the Featured switch | [attendee-experience/thank-the-organizers.md](../attendee-experience/thank-the-organizers.md), [entity-management/event-page.md](../entity-management/event-page.md) |
| `event-page/more-sheet-phone.png` | The More sheet on a phone | [entity-management/event-page.md](../entity-management/event-page.md) |
| `event-page/cockpit.png` | Cockpit tab of BATbern60 | [entity-management/event-page.md](../entity-management/event-page.md) |
| `event-page/speakers-pool.png` | Speakers & Agenda → Pool | [entity-management/event-page.md](../entity-management/event-page.md), [workflow/phase-a-setup.md](../workflow/phase-a-setup.md) |
| `event-page/registrations.png` | Registrations tab with capacity bar and filters | [entity-management/event-page.md](../entity-management/event-page.md) |
| `event-page/communications.png` | Communications tab | [entity-management/event-page.md](../entity-management/event-page.md), [features/event-mail.md](../features/event-mail.md) |
| `event-page/publishing.png` | Publishing tab | [entity-management/event-page.md](../entity-management/event-page.md), [workflow/phase-b-outreach.md](../workflow/phase-b-outreach.md), [workflow/phase-c-quality.md](../workflow/phase-c-quality.md) |
| `event-page/details-info.png` | Details → Info | [entity-management/event-page.md](../entity-management/event-page.md) |
| `event-page/details-tasks.png` | Details → Tasks | [entity-management/event-page.md](../entity-management/event-page.md), [workflow/phase-a-setup.md](../workflow/phase-a-setup.md) |
| `event-page/details-settings.png` | Details → Settings | [entity-management/event-page.md](../entity-management/event-page.md), [workflow/phase-e-publishing.md](../workflow/phase-e-publishing.md) |
| `event-page/cockpit-at-a-glance.png` | Cockpit: lifecycle spine, Needs your attention, At a glance with the rehearsal links | [features/cockpit-and-event-day.md](../features/cockpit-and-event-day.md) |
| `event-page/cockpit-phone.png` | Event page on a phone, with the bottom tab bar | [getting-started/navigation.md](../getting-started/navigation.md) |
| `event-page/speakers-agenda.png` | Speakers & Agenda → Agenda: sessions with materials status | [workflow/phase-c-quality.md](../workflow/phase-c-quality.md) |
| `event-page/speakers-slots-phone.png` | Slots view on a phone | [workflow/phase-d-assignment.md](../workflow/phase-d-assignment.md) |

## Features

| File | View | Used on |
|------|------|---------|
| `features/analytics.png` | Organizer analytics page | [features/analytics.md](../features/analytics.md) |
| `features/live-control-phone.png` | Live Control on a phone, a session running | [features/cockpit-and-event-day.md](../features/cockpit-and-event-day.md), [workflow/phase-e-publishing.md](../workflow/phase-e-publishing.md) |
| `features/live-control-adjust-time.png` | Session Zeit anpassen sheet | [features/cockpit-and-event-day.md](../features/cockpit-and-event-day.md) |
| `features/event-mail-new-mail.png` | The + New mail menu | [features/event-mail.md](../features/event-mail.md) |
| `features/mail-center.png` | Mail page | [features/event-mail.md](../features/event-mail.md) |
| `features/presentation-page.png` | Moderator presentation, title slide | [features/media-and-content.md](../features/media-and-content.md) |
| `features/task-board.png` | Task Board with the All Tasks filter | [workflow/phase-a-setup.md](../workflow/phase-a-setup.md), [workflow/phase-f-communication.md](../workflow/phase-f-communication.md) |

## Entity management

| File | View | Used on |
|------|------|---------|
| `entity-management/companies-list.png` | Company list | [entity-management/companies.md](../entity-management/companies.md) |
| `entity-management/partners-list.png` | Partner Directory | [entity-management/partners.md](../entity-management/partners.md) |
| `entity-management/users-list.png` | User Management list | [entity-management/users.md](../entity-management/users.md) |

## Administration

| File | View | Used on |
|------|------|---------|
| `administration/admin-presentation.png` | Administration, Presentation tab | [administration/README.md](../administration/README.md) |
| `administration/admin-ai-prompts.png` | Administration, AI Prompts tab | [administration/README.md](../administration/README.md) |
| `administration/admin-settings.png` | Administration, Settings tab | [administration/README.md](../administration/README.md) |
| `administration/admin-global-images.png` | Administration, Global Images tab | [administration/README.md](../administration/README.md) |
| `administration/admin-email-templates.png` | Administration, Email Templates tab | [administration/email-templates.md](../administration/email-templates.md) |
| `administration/admin-event-types.png` | Administration, Event Types tab | [administration/event-types.md](../administration/event-types.md) |
| `administration/admin-import-data.png` | Administration, Import Data tab | [administration/import-data.md](../administration/import-data.md) |
| `administration/admin-task-templates.png` | Administration, Task Templates tab | [administration/task-templates.md](../administration/task-templates.md) |

## Partner portal

| File | View | Used on |
|------|------|---------|
| `partner-portal/company.png` | Partner portal, My Company | [partner-portal/README.md](../partner-portal/README.md) |
| `partner-portal/analytics.png` | Partner attendance analytics | [partner-portal/analytics.md](../partner-portal/analytics.md) |
| `partner-portal/organizer-partner-meetings.png` | Partner Meetings list | [partner-portal/meetings.md](../partner-portal/meetings.md) |
| `partner-portal/topics.png` | Topic Suggestions as a partner sees them | [partner-portal/topic-voting.md](../partner-portal/topic-voting.md) |
| `partner-portal/organizer-partner-topics.png` | Partner topics, organizer view | [partner-portal/topic-voting.md](../partner-portal/topic-voting.md) |

## Speaker portal

| File | View | Used on |
|------|------|---------|
| `speaker-portal/profile.png` | Speaker profile page | [speaker-portal/dashboard.md](../speaker-portal/dashboard.md) |

## Attendee experience

| File | View | Used on |
|------|------|---------|
| `attendee-experience/event-history-dashboard.png` | Attendee dashboard of a signed-in user | [attendee-experience/event-history.md](../attendee-experience/event-history.md) |

## Public website

| File | View | Used on |
|------|------|---------|
| `public/homepage.png` | Public homepage with the next event | [attendee-experience/README.md](../attendee-experience/README.md) |
| `public/archive-event-sessions.png` | Sessions of an archived event on the public site, with the Q&A links | [attendee-experience/post-event-qna.md](../attendee-experience/post-event-qna.md) |

## Workflow phases (legacy file names, current views)

| File | View | Used on |
|------|------|---------|
| `workflow/phase-a-setup/a-02-event-creation-modal.png` | Create New Event dialog | [entity-management/events.md](../entity-management/events.md), [workflow/phase-a-setup.md](../workflow/phase-a-setup.md) |
| `workflow/phase-a-setup/a-17-topic-heatmap.png` | Topic Backlog Manager, heat map view | [features/heat-maps.md](../features/heat-maps.md), [workflow/phase-a-setup.md](../workflow/phase-a-setup.md) |
| `workflow/phase-a-setup/a-01-event-dashboard.png` | Events page with the New Event button | [workflow/phase-a-setup.md](../workflow/phase-a-setup.md) |
| `workflow/phase-a-setup/a-03-event-type-dropdown-open.png` | Event type selection in the Create New Event dialog | [workflow/phase-a-setup.md](../workflow/phase-a-setup.md) |
| `workflow/phase-a-setup/a-16-topic-selection-opened.png` | Topic & speakers dialog, opened with Change topic | [workflow/phase-a-setup.md](../workflow/phase-a-setup.md) |
| `workflow/phase-a-setup/a-20-speaker-brainstorming-form.png` | Add Speakers to Pool drawer | [workflow/phase-a-setup.md](../workflow/phase-a-setup.md) |
| `workflow/phase-b-outreach/b-01-outreach-view-ready.png` | Speaker kanban with the status lanes Sourcing, Inviting, Content, Confirmed | [workflow/phase-b-outreach.md](../workflow/phase-b-outreach.md) |
| `workflow/phase-b5-content/b5-04-content-submission-drawer-1-opened.png` | Edit Session dialog, Materials tab (opened from Agenda → Materials) | [workflow/phase-b-outreach.md](../workflow/phase-b-outreach.md) |
| `workflow/phase-c-quality/c-04-quality-review-1-opened.png` | Speaker drawer, opened from a kanban card (tabs Details, Content, History) | [workflow/phase-c-quality.md](../workflow/phase-c-quality.md) |
| `workflow/phase-d-publishing/d-02-sessions-view-loaded.png` | Speakers & Agenda → Agenda | [workflow/phase-d-assignment.md](../workflow/phase-d-assignment.md) |
| `workflow/phase-d-publishing/d-03-slot-assignment-page-loaded.png` | Speakers & Agenda → Slots: speaker pool and session timeline | [workflow/phase-d-assignment.md](../workflow/phase-d-assignment.md) |
| `workflow/phase-d-publishing/d-10-agenda-published.png` | Publishing tab after all three phases are published | [workflow/phase-d-assignment.md](../workflow/phase-d-assignment.md) |

---

## Legacy workflow screenshots (not used)

64 files under `assets/screenshots/workflow/` date from January 2026 (the screenshot run of `complete-event-workflow.spec.ts`, before the Epic 11 speaker kanban and the Epic 14 event page). No page references them any more. They were left in place; delete them when the folder is next cleaned up.

<details><summary>List</summary>

- `workflow/phase-a-setup/a-05-event-created-back-to-dashboard.png`
- `workflow/phase-a-setup/a-06-event-detail-page-initial.png`
- `workflow/phase-a-setup/a-07-edit-modal-info-tab.png`
- `workflow/phase-a-setup/a-09-tasks-assigned-1.png`
- `workflow/phase-a-setup/a-10-tasks-assigned-2.png`
- `workflow/phase-a-setup/a-11-tasks-assigned-3.png`
- `workflow/phase-a-setup/a-12-all-tasks-assigned.png`
- `workflow/phase-a-setup/a-13-event-detail-after-task-assignment.png`
- `workflow/phase-a-setup/a-15-task-list-all-tasks-filter.png`
- `workflow/phase-b-outreach/b-04-before-contact-speaker-2.png`
- `workflow/phase-b-outreach/b-05-after-contact-speaker-2.png`
- `workflow/phase-b-outreach/b-06-before-contact-speaker-3.png`
- `workflow/phase-b-outreach/b-07-after-contact-speaker-3.png`
- `workflow/phase-b-outreach/b-08-before-contact-speaker-4.png`
- `workflow/phase-b-outreach/b-09-after-contact-speaker-4.png`
- `workflow/phase-b-outreach/b-10-before-contact-speaker-5.png`
- `workflow/phase-b-outreach/b-11-after-contact-speaker-5.png`
- `workflow/phase-b5-content/b5-01-sessions-view-initial.png`
- `workflow/phase-b5-content/b5-02-publish-tab-before-topic.png`
- `workflow/phase-b5-content/b5-02-topic-published.png`
- `workflow/phase-b5-content/b5-03-speakers-tab-after-publish-topic.png`
- `workflow/phase-b5-content/b5-03-topic-published.png`
- `workflow/phase-b5-content/b5-04-speakers-tab-after-publish-topic.png`
- `workflow/phase-b5-content/b5-05-content-submission-1-speaker-selected.png`
- `workflow/phase-b5-content/b5-05-content-submission-drawer-1-opened.png`
- `workflow/phase-b5-content/b5-06-content-submission-1-filled.png`
- `workflow/phase-b5-content/b5-06-content-submission-1-speaker-selected.png`
- `workflow/phase-b5-content/b5-06-content-submitted-1.png`
- `workflow/phase-b5-content/b5-07-content-submission-1-filled.png`
- `workflow/phase-b5-content/b5-07-content-submission-drawer-2-opened.png`
- `workflow/phase-b5-content/b5-07-content-submitted-1.png`
- `workflow/phase-b5-content/b5-08-content-submission-2-filled.png`
- `workflow/phase-b5-content/b5-08-content-submission-drawer-2-opened.png`
- `workflow/phase-b5-content/b5-08-content-submitted-1.png`
- `workflow/phase-b5-content/b5-09-content-submission-2-filled.png`
- `workflow/phase-b5-content/b5-09-content-submitted-2.png`
- `workflow/phase-b5-content/b5-10-content-submission-drawer-3-opened.png`
- `workflow/phase-b5-content/b5-10-content-submitted-2.png`
- `workflow/phase-b5-content/b5-11-content-submission-3-speaker-selected.png`
- `workflow/phase-b5-content/b5-11-content-submission-drawer-3-opened.png`
- `workflow/phase-b5-content/b5-12-content-submission-3-filled.png`
- `workflow/phase-b5-content/b5-12-content-submission-3-speaker-selected.png`
- `workflow/phase-b5-content/b5-13-content-submission-3-filled.png`
- `workflow/phase-b5-content/b5-13-content-submitted-3.png`
- `workflow/phase-b5-content/b5-14-content-submitted-3.png`
- `workflow/phase-c-quality/c-06-quality-review-2-opened.png`
- `workflow/phase-c-quality/c-07-content-approved-2.png`
- `workflow/phase-c-quality/c-08-quality-review-3-opened.png`
- `workflow/phase-c-quality/c-09-content-approved-3.png`
- `workflow/phase-d-publishing/d-01-speakers-tab-initial.png`
- `workflow/phase-d-publishing/d-04-before-drag-assignment.png`
- `workflow/phase-d-publishing/d-05-after-drag-assignment.png`
- `workflow/phase-d-publishing/d-06-back-to-event-page.png`
- `workflow/phase-d-publishing/d-07-publishing-tab-loaded.png`
- `workflow/phase-d-publishing/d-08-before-publish-agenda.png`
- `workflow/phase-d-publishing/d-09-agenda-published.png`
- `workflow/phase-e-archival/e-01-overview-tab-before-archival.png`
- `workflow/phase-e-archival/e-02-edit-modal-opened.png`
- `workflow/phase-e-archival/e-03-status-dropdown-opened.png`
- `workflow/phase-e-archival/e-04-status-changed-to-archived.png`
- `workflow/phase-e-archival/e-05-validation-error-shown.png`
- `workflow/phase-e-archival/e-06-override-checkbox-checked.png`
- `workflow/phase-e-archival/e-07-event-archived-successfully.png`
- `workflow/phase-e-archival/e-08-archived-badge-visible.png`

</details>
