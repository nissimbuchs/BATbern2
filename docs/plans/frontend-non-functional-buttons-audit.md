# Frontend Non-Functional Buttons Audit

**Generated:** 2025-12-28
**Updated:** 2025-12-28 (comprehensive audit)
**Branch:** feature/5.7-slot-assignment-publishing
**Purpose:** Track buttons in the frontend that lack functionality or are marked as TBD for later stories.

---

## Summary

| Category | Count |
|----------|-------|
| **Buttons Without onClick Handlers** | 11 |
| **Buttons with console.log Only** | 9 |
| **Disabled Placeholder Buttons** | 4 |
| **Total Non-Functional Buttons** | 24 |
| **Files Affected** | 11 |

---

## Category 1: Buttons Without onClick Handlers

These buttons are rendered but have no onClick handler attached.

### 1. EventTeamTab.tsx - "Add Team Member" Button

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/EventPage/EventTeamTab.tsx` |
| **Line** | 128 |
| **Label** | Add Team Member |
| **Context** | Team Assignments Section header |

```tsx
<Button variant="outlined" startIcon={<AddIcon />} size="small">
  {t('eventPage.team.addMember', 'Add Team Member')}
</Button>
```

---

### 2. EventTeamTab.tsx - "Reassign Speakers" Button

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/EventPage/EventTeamTab.tsx` |
| **Line** | 257 |
| **Label** | Reassign Speakers |
| **Context** | Outreach Distribution Section |

```tsx
<Button variant="outlined" size="small">
  {t('eventPage.team.reassignSpeakers', 'Reassign Speakers')}
</Button>
```

---

### 3. EventTeamTab.tsx - Edit Team Member IconButton

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/EventPage/EventTeamTab.tsx` |
| **Line** | 169 |
| **Label** | Edit (IconButton) |
| **Context** | Team member row actions |

```tsx
<IconButton size="small">
  <EditIcon fontSize="small" />
</IconButton>
```

---

### 4. EventTeamTab.tsx - Delete Team Member IconButton

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/EventPage/EventTeamTab.tsx` |
| **Line** | 173 |
| **Label** | Delete (IconButton) |
| **Context** | Team member row actions (non-lead members) |

```tsx
<IconButton size="small" color="error">
  <DeleteIcon fontSize="small" />
</IconButton>
```

---

### 5. EventSettingsTab.tsx - Edit Notification Rule IconButton

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/EventPage/EventSettingsTab.tsx` |
| **Line** | 207 |
| **Label** | Edit (IconButton) |
| **Context** | Notification rules list item |

```tsx
<IconButton size="small">
  <EditIcon fontSize="small" />
</IconButton>
```

---

### 6. EventSettingsTab.tsx - "Manage All Notifications" Button

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/EventPage/EventSettingsTab.tsx` |
| **Line** | 216 |
| **Label** | Manage All Notifications |
| **Context** | Notifications section footer |

```tsx
<Button variant="text" sx={{ mt: 2 }}>
  {t('eventPage.settings.manageAll', 'Manage All Notifications')}
</Button>
```

---

### 7. EventOverviewTab.tsx - "Advance Workflow" Button

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/EventPage/EventOverviewTab.tsx` |
| **Line** | 155 |
| **Label** | Advance Workflow → |
| **Context** | Workflow Status Bar (early-stage events only) |

```tsx
<Button variant="outlined" size="small">
  {t('workflow.advanceWorkflow', 'Advance Workflow')} →
</Button>
```

---

### 8. EventPublishingTab.tsx - "Configure" Button

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/EventPage/EventPublishingTab.tsx` |
| **Line** | 170 |
| **Label** | Configure |
| **Context** | Publishing Status section header |

```tsx
<Button variant="outlined" startIcon={<SettingsIcon />} size="small">
  {t('eventPage.publishing.configure', 'Configure')}
</Button>
```

---

### 9. PartnerDetailHeader.tsx - "Add Note" Button

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/PartnerManagement/PartnerDetailHeader.tsx` |
| **Line** | 225 |
| **Label** | Add Note |
| **Icon** | NoteAdd |
| **Context** | Partner detail action buttons |

```tsx
<Button variant="outlined" startIcon={<NoteAdd />} size="small">
  {t('detail.notesTab.addNote')}
</Button>
```

---

### 10. TeamActivityFeed.tsx - "View All" Button

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/EventManagement/TeamActivityFeed.tsx` |
| **Line** | 215 |
| **Label** | View All |
| **Context** | Activity feed footer (shown when hasMore) |

```tsx
<Button variant="outlined" size="small">
  {t('dashboard.viewAll')}
</Button>
```

---

### 11. UserSettingsTab.tsx - "Change Password" Button

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/user/UserSettingsTab/UserSettingsTab.tsx` |
| **Line** | 116 |
| **Label** | Change Password |
| **Context** | Account Settings panel |

```tsx
<Button variant="outlined" sx={{ mt: 2 }} data-testid="change-password-button">
  Change Password
</Button>
```

---

## Category 2: Buttons with console.log Only Handlers

These buttons have onClick handlers that only log to console without actual functionality.

### 12-15. EventSpeakersTab.tsx - Session Action Handlers

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/EventPage/EventSpeakersTab.tsx` |
| **Lines** | 135-161 |
| **Handlers** | handleViewDetails, handleEditSlot, handleViewMaterials, handleAutoAssignSpeakers |

```tsx
const handleViewDetails = (sessionId: string) => {
  console.log('View details:', sessionId);
};

const handleEditSlot = (sessionId: string) => {
  console.log('Edit slot:', sessionId);
};

const handleViewMaterials = (sessionId: string) => {
  console.log('View materials:', sessionId);
};

const handleAutoAssignSpeakers = (code: string) => {
  console.log('Auto-assign speakers for:', code);
};
```

**Affected UI Elements:**
- View Details button on each session row
- Edit Slot button on each session row
- Materials button on each session row
- Auto-Assign Speakers button in header

---

### 16. EventOverviewTab.tsx - handleSendNotification

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/EventPage/EventOverviewTab.tsx` |
| **Line** | 111-114 |
| **Handler** | handleSendNotification |
| **Comment** | `// TODO: Open notification modal` |

```tsx
const handleSendNotification = () => {
  // TODO: Open notification modal
  console.log('Send notification for:', eventCode);
};
```

**Affected UI:** "Send Notification" quick action button

---

### 17-18. EventPublishingTab.tsx - Publishing Actions

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/EventPage/EventPublishingTab.tsx` |
| **Lines** | 138-144 |
| **Handlers** | handleRepublish, handleNotifyAttendees |

```tsx
const handleRepublish = () => {
  console.log('Republish event:', eventCode);
};

const handleNotifyAttendees = () => {
  console.log('Notify attendees:', eventCode);
};
```

**Affected UI:**
- "Republish Event" button
- "Notify Attendees" button

---

### 19-20. EventSettingsTab.tsx - Danger Zone Actions

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/EventPage/EventSettingsTab.tsx` |
| **Lines** | 95-104 |
| **Handlers** | handleDeleteEvent, handleCancelEvent |

```tsx
const handleDeleteEvent = () => {
  console.log('Delete event:', eventCode);
  setDeleteDialogOpen(false);
  navigate('/organizer/events');
};

const handleCancelEvent = () => {
  console.log('Cancel event:', eventCode);
  setCancelDialogOpen(false);
};
```

**Note:** These navigate/close dialogs but don't call backend APIs.

---

## Category 3: Disabled Placeholder Buttons (Marked as Coming Soon)

These buttons are intentionally disabled with "Coming Soon" messaging.

### 21. PartnerOverviewTab.tsx - "View Full Analytics" Button

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/PartnerManagement/PartnerOverviewTab.tsx` |
| **Line** | 189 |
| **Label** | View Full Analytics → |
| **Status** | Disabled |
| **Epic** | Epic 8 - Partner Coordination |

```tsx
<Button variant="text" endIcon={<TrendingUp />} disabled sx={{ mt: 2 }}>
  View Full Analytics →
</Button>
```

---

### 22-24. PartnerDetailHeader.tsx - Epic 8 Action Buttons

| Field | Value |
|-------|-------|
| **File** | `web-frontend/src/components/organizer/PartnerManagement/PartnerDetailHeader.tsx` |
| **Lines** | 198-224 |
| **Buttons** | Send Email, Schedule Meeting, Export Data |
| **Status** | All disabled with "Coming Soon" tooltip |
| **Epic** | Epic 8 - Partner Coordination |

```tsx
<Button variant="outlined" startIcon={<Email />} disabled size="small"
  title={t('detail.header.comingSoon')}>
  {t('detail.header.sendEmail')}
</Button>
<Button variant="outlined" startIcon={<CalendarMonth />} disabled size="small"
  title={t('detail.header.comingSoon')}>
  {t('detail.header.scheduleMeeting')}
</Button>
<Button variant="outlined" startIcon={<Analytics />} disabled size="small"
  title={t('detail.header.comingSoon')}>
  {t('detail.header.exportData')}
</Button>
```

---

## Files Summary

| File | Issues | Categories |
|------|--------|------------|
| `EventTeamTab.tsx` | 4 | No handler |
| `EventSpeakersTab.tsx` | 4 | console.log only |
| `EventSettingsTab.tsx` | 4 | No handler (2), console.log (2) |
| `EventOverviewTab.tsx` | 2 | No handler (1), console.log (1) |
| `EventPublishingTab.tsx` | 3 | No handler (1), console.log (2) |
| `PartnerDetailHeader.tsx` | 4 | No handler (1), Disabled (3) |
| `PartnerOverviewTab.tsx` | 1 | Disabled |
| `TeamActivityFeed.tsx` | 1 | No handler |
| `UserSettingsTab.tsx` | 1 | No handler |

---

## Recommendations

### Priority 1: Remove or Disable Non-Functional Buttons
Buttons that appear functional but do nothing should either:
- Be removed until implemented
- Be disabled with "Coming Soon" tooltip

**Candidates:**
- Session action buttons (View Details, Edit Slot, Materials)
- Team management buttons (Add Member, Edit, Delete, Reassign)
- Notification management buttons (Edit rule, Manage All)

### Priority 2: Implement Core Functionality
These buttons are critical for user workflows:

| Button | Impact | Story |
|--------|--------|-------|
| Auto-Assign Speakers | High | Story 5.7 |
| Advance Workflow | High | Story 5.7 |
| View/Edit Session Details | High | Story 5.7 |
| Send Notification | Medium | Epic 5 |
| Change Password | Medium | Core Auth |
| Delete/Cancel Event | Medium | Core |

### Priority 3: Already Properly Handled
These are correctly marked as coming soon with Epic 8:
- Partner analytics and engagement features
- Partner communication features (Email, Schedule, Export)

---

## Related Epics/Stories

| Button Group | Epic/Story |
|--------------|------------|
| Session Actions (View, Edit, Materials) | Story 5.7 - Slot Assignment |
| Auto-Assign Speakers | Story 5.7 - Slot Assignment |
| Advance Workflow | Story 5.7 - Workflow Progression |
| Team Management | Epic 5 - Enhanced Organizer Workflows |
| Notification Management | Epic 5 - Enhanced Organizer Workflows |
| Publishing Actions | Epic 5 - Enhanced Organizer Workflows |
| Partner Analytics | Epic 8 - Partner Coordination |
| Partner Communications | Epic 8 - Partner Coordination |
| Change Password | Core - User Management |

---

## Tracking

- [ ] Review all 24 non-functional buttons
- [ ] Remove or disable Category 1 buttons (no handler) - 11 items
- [ ] Implement Category 2 handlers or remove buttons - 9 items
- [ ] Category 3 is correctly handled (disabled with messaging) - 4 items
