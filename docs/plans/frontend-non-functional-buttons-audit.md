# Frontend Non-Functional Buttons Audit

**Generated:** 2025-12-28
**Branch:** feature/5.7-slot-assignment-publishing
**Purpose:** Track buttons in the frontend that lack functionality or are marked as TBD for later stories.

---

## Summary

| Metric | Count |
|--------|-------|
| **Buttons Without Handlers** | 10 |
| **Disabled Placeholder Buttons** | 1 |
| **Files Affected** | 8 |
| **TODO Comments Near Buttons** | 5 |

---

## Critical Issues: Buttons Without onClick Handlers

### 1. PublishingControls.tsx - "Send Test" Email Button

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/Publishing/PublishingControls/PublishingControls.tsx` |
| **Line** | 239 |
| **Label** | Send Test (Newsletter) |
| **Status** | NO onClick handler |
| **Context** | Newsletter Preview Dialog |

```tsx
<Button variant="contained">{t('publishing.controls.newsletterDialog.sendTest')}</Button>
```

**Issue:** Send Test button appears in the Newsletter Preview Modal but has no handler to send test emails.

---

### 2. TeamActivityFeed.tsx - "View All" Button

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/EventManagement/TeamActivityFeed.tsx` |
| **Line** | 215 |
| **Label** | View All |
| **Status** | NO onClick handler |
| **Context** | Team Activity Feed expansion |

```tsx
<Button variant="outlined" size="small">{t('dashboard.viewAll')}</Button>
```

**Issue:** "View All" button for expanded activity feed has no navigation or handler. Conditionally shown when activities exceed limit (`hasMore`).

---

### 3. EventSettingsTab.tsx - "Manage All Notifications" Button

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/EventPage/EventSettingsTab.tsx` |
| **Line** | 216 |
| **Label** | Manage All Notifications |
| **Status** | NO onClick handler |
| **Context** | Event Settings - Notifications Section |

```tsx
<Button variant="text" sx={{ mt: 2 }}>
  {t('eventPage.settings.manageAll', 'Manage All Notifications')}
</Button>
```

**Issue:** No handler to open notification management modal/page.

---

### 4. EventSettingsTab.tsx - Notification Rules Edit IconButton

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/EventPage/EventSettingsTab.tsx` |
| **Line** | 207 |
| **Label** | Edit (IconButton) |
| **Status** | NO onClick handler |
| **Context** | Notification Rules list item |

```tsx
<IconButton size="small"><EditIcon fontSize="small" /></IconButton>
```

**Issue:** Edit button for notification rules has no handler. Rendered within notification list items.

---

### 5. EventOverviewTab.tsx - "Advance Workflow" Button

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/EventPage/EventOverviewTab.tsx` |
| **Line** | 155 |
| **Label** | Advance Workflow → |
| **Status** | NO onClick handler |
| **Context** | Workflow Status Bar (early-stage events) |

```tsx
<Button variant="outlined" size="small">
  {t('workflow.advanceWorkflow', 'Advance Workflow')} →
</Button>
```

**Issue:** Button appears conditionally when event is in early stage but has no handler.

---

### 6. EventTeamTab.tsx - "Add Team Member" Button

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/EventPage/EventTeamTab.tsx` |
| **Line** | 128 |
| **Label** | Add Team Member |
| **Icon** | PersonAdd (AddIcon) |
| **Status** | NO onClick handler |
| **Context** | Team Assignments Section header |

```tsx
<Button variant="outlined" startIcon={<AddIcon />} size="small">
  {t('eventPage.team.addMember', 'Add Team Member')}
</Button>
```

**Issue:** No handler to open team member creation modal.

---

### 7. EventTeamTab.tsx - "Reassign Speakers" Button

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/EventPage/EventTeamTab.tsx` |
| **Line** | 257 |
| **Label** | Reassign Speakers |
| **Status** | NO onClick handler |
| **Context** | Outreach Distribution Section |

```tsx
<Button variant="outlined" size="small">
  {t('eventPage.team.reassignSpeakers', 'Reassign Speakers')}
</Button>
```

**Issue:** No handler to trigger speaker reassignment workflow.

---

### 8. PartnerDetailHeader.tsx - "Add Note" Button

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/PartnerManagement/PartnerDetailHeader.tsx` |
| **Line** | 225 |
| **Label** | Add Note |
| **Icon** | NoteAdd |
| **Status** | NO onClick handler |
| **Context** | Partner Detail Header action buttons |

```tsx
<Button variant="outlined" startIcon={<NoteAdd />} size="small">
  {t('detail.notesTab.addNote')}
</Button>
```

**Issue:** No handler to open note creation dialog.

---

### 9. PartnerOverviewTab.tsx - "View Full Analytics" Button (Disabled)

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/PartnerManagement/PartnerOverviewTab.tsx` |
| **Line** | 189 |
| **Label** | View Full Analytics → |
| **Icon** | TrendingUp |
| **Status** | NO onClick handler + DISABLED |
| **Context** | Engagement Metrics Panel |

```tsx
<Button variant="text" endIcon={<TrendingUp />} disabled sx={{ mt: 2 }}>
  View Full Analytics →
</Button>
```

**Issue:** Disabled placeholder button with no handler. Part of future Epic 8 features.

---

### 10. UserSettingsTab.tsx - "Change Password" Button

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/user/UserSettingsTab/UserSettingsTab.tsx` |
| **Line** | 116 |
| **Label** | Change Password |
| **Status** | NO onClick handler |
| **Context** | Account Settings Panel |

```tsx
<Button variant="outlined" sx={{ mt: 2 }} data-testid="change-password-button">
  Change Password
</Button>
```

**Issue:** No handler to open password change modal/form.

---

## TODO Comments Near Button Logic

### 1. EventOverviewTab.tsx - handleSendNotification

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/EventPage/EventOverviewTab.tsx` |
| **Line** | 112 |
| **Comment** | `// TODO: Open notification modal` |

The `handleSendNotification()` function needs proper implementation.

---

### 2. EventPublishingTab.tsx - Multiple TODOs

| Line | Comment |
|------|---------|
| 25 | `// TODO: Replace with real API call to get publishing status` |
| 27 | `// TODO: Get from event.currentPublishedPhase` |
| 30 | `// TODO: Replace with real validation data from API` |
| 52 | `// TODO: Replace with real data from event` |

Multiple mock/incomplete data indicators affecting publishing controls.

---

## Recommendations

Each button should be addressed by one of these approaches:

1. **Implement Handler** - Add proper `onClick` handler with business logic
2. **Remove from UI** - If feature is not yet required, remove the button
3. **Mark as Coming Soon** - Explicitly disable with "Coming Soon" tooltip/message

---

## Related Epics/Stories

| Button | Likely Epic/Story |
|--------|-------------------|
| Send Test Email | Epic 5 - Enhanced Organizer Workflows |
| View All (Activity) | Epic 5 - Enhanced Organizer Workflows |
| Manage Notifications | Epic 5 - Enhanced Organizer Workflows |
| Advance Workflow | Story 5.7 - Slot Assignment & Publishing |
| Add Team Member | Epic 5 - Enhanced Organizer Workflows |
| Reassign Speakers | Epic 6 - Speaker Portal Support |
| Add Note (Partner) | Epic 8 - Partner Coordination |
| View Full Analytics | Epic 8 - Partner Coordination |
| Change Password | Core - User Management |

---

## Tracking

- [ ] All 10 buttons reviewed and assigned to stories
- [ ] Non-essential buttons removed or disabled with messaging
- [ ] Critical buttons implemented with handlers
