# Story BAT-17: Session Edit Modal Enhancements

**Epic**: 5 - Event Publishing & Session Management
**Status**: Ready for Review
**Linear Issue**: [BAT-17](https://linear.app/batbern/issue/BAT-17/session-edit-modal-enhancements)
**Priority**: Medium
**Story Points**: 5
**Assignee**: Dev Agent

---

## Story

As an event organizer, I want to edit session details (title, description, times) directly from the speakers/sessions table view, so that I can quickly manage session information without navigating to multiple pages.

---

## Acceptance Criteria

1. **AC1**: Clicking a session row opens a modal with editable fields for:
   - Session title (required)
   - Session description/abstract (optional, max 1000 chars)
   - Start time (HH:mm time picker)
   - End time (HH:mm time picker)
   - Duration in minutes (auto-calculates endTime when changed)

2. **AC2**: Time field behavior:
   - Changing startTime auto-recalculates endTime based on current duration
   - Changing duration auto-recalculates endTime based on current startTime
   - Validation prevents endTime before startTime
   - Minimum session duration: 15 minutes
   - Maximum session duration: 480 minutes (8 hours)

3. **AC3**: Save button triggers two API calls:
   - PATCH `/events/{eventCode}/sessions/{sessionSlug}` for title/description/duration
   - PATCH `/events/{eventCode}/sessions/{sessionSlug}/timing` for startTime/endTime (with conflict detection)

4. **AC4**: Conflict detection displays 409 error messages:
   - Show speaker conflicts (same speaker, overlapping times)
   - Show room conflicts (same room, overlapping times)
   - Display conflict details in user-friendly format

5. **AC5**: Button cleanup:
   - Remove "View Details" button (redundant with row click)
   - Remove "Edit Slot" button (redundant with row click)
   - Keep "View Materials" button (placeholder for Phase 2)

6. **AC6**: After successful save:
   - Modal closes
   - Table refreshes with updated session data
   - Toast notification (optional)

---

## Technical Implementation

### Files Modified

1. **SessionEditModal.tsx** (`web-frontend/src/components/organizer/EventManagement/SessionEditModal.tsx`)
   - Add `eventDate` prop (for ISO 8601 conversion)
   - Add startTime/endTime TextField inputs (type="time")
   - Add helper functions: `extractTimeFromISO()`, `combineEventDateAndTime()`
   - Update validation to check time constraints
   - Handle 409 conflict errors with friendly messages
   - Add AxiosError import for error handling

2. **EventSpeakersTab.tsx** (`web-frontend/src/components/organizer/EventPage/EventSpeakersTab.tsx`)
   - Add imports: `slotAssignmentService`, `sessionApiClient`, types
   - Implement `handleSessionUpdate()` with dual API calls
   - Remove `handleViewDetails()` and `handleEditSlot()` functions
   - Pass `event?.date` to SessionEditModal
   - Keep `handleViewMaterials()` as placeholder

3. **SpeakersSessionsTable.tsx** (`web-frontend/src/components/organizer/EventManagement/SpeakersSessionsTable.tsx`)
   - Remove `onViewDetails` and `onEditSlot` props
   - Remove "View Details" button (desktop + mobile)
   - Remove "Edit Slot" button (desktop + mobile)
   - Remove unused imports: `VisibilityIcon`, `EditIcon`
   - Pass `eventDate` prop to SessionEditModal

### API Endpoints Used

- **PATCH** `/events/{eventCode}/sessions/{sessionSlug}` - Update title/description/duration
- **PATCH** `/events/{eventCode}/sessions/{sessionSlug}/timing` - Update times (with conflict validation)

### Helper Functions

```typescript
// Extract HH:mm from ISO 8601 timestamp
const extractTimeFromISO = (isoString: string | null | undefined): string => {
  if (!isoString) return '';
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return '';
    return date.toTimeString().substring(0, 5); // "14:30"
  } catch {
    return '';
  }
};

// Combine event date + HH:mm time → ISO 8601 timestamp
const combineEventDateAndTime = (eventDate: string, timeString: string): string => {
  const date = new Date(eventDate);
  const [hours, minutes] = timeString.split(':').map(Number);
  date.setHours(hours, minutes, 0, 0);
  return date.toISOString();
};
```

---

## Testing Strategy

### Unit Tests
- [ ] Time validation (endTime > startTime, minimum 15 min, maximum 480 min)
- [ ] Helper functions (extractTimeFromISO, combineEventDateAndTime)
- [ ] Error handling (409 conflicts, malformed times)

### Integration Tests
- [ ] Save with title/description updates calls correct endpoint
- [ ] Save with timing updates calls `/timing` endpoint
- [ ] 409 conflict errors display properly
- [ ] Cache invalidation triggers table refresh

### Manual Testing
- [ ] Open modal by clicking session row
- [ ] Edit all fields (title, description, times, duration)
- [ ] Verify auto-calculation (change startTime → endTime updates, change duration → endTime updates)
- [ ] Test validation (empty title, endTime before startTime, etc.)
- [ ] Create timing conflict, verify 409 error displays
- [ ] Verify only "Materials" button visible (no "View Details" or "Edit Slot")

---

## Known Limitations

1. **Dual API Calls**: Updates to both timing AND title/description trigger two separate API calls (could be optimized in future)
2. **No Optimistic Locking**: Concurrent edits may overwrite each other
3. **Basic Timezone Support**: Assumes all events in same timezone (Europe/Zurich)
4. **Partial Update Failure**: If title/description update succeeds but timing update fails (409 conflict), first update is not rolled back

---

## Dependencies

- **Backend**: Session update endpoints must be deployed
- **Frontend**: `slotAssignmentService` and `sessionApiClient` already exist
- **Types**: `SessionTimingRequest` and `SessionUpdateData` interfaces defined

---

## Definition of Done

- [ ] All AC implemented and passing tests
- [ ] Unit tests written and passing (80%+ coverage)
- [ ] Integration tests written and passing
- [ ] Manual testing completed
- [ ] Code reviewed and approved
- [ ] Documentation updated (if needed)
- [ ] Deployed to staging and verified
- [ ] No critical bugs or regressions

---

## Dev Notes

**Implementation Plan**: See `/Users/nissim/.claude/plans/atomic-kindling-matsumoto.md` for detailed step-by-step implementation instructions.

**Duration Auto-Update Logic**:
- Changing startTime → recalculate endTime = startTime + duration
- Changing duration → recalculate endTime = startTime + new duration
- Changing endTime manually → duration does NOT auto-update (manual override)

**Timezone Handling**:
- Store: ISO 8601 in UTC (backend requirement)
- Display: Browser local time via TextField type="time"
- Convert: `combineEventDateAndTime()` creates UTC timestamp from event date + HH:mm input

---

## Dev Agent Record

### Status
- **Current Status**: Ready for Review
- **Agent Model Used**: Claude Sonnet 4.5
- **Started**: 2026-01-03
- **Last Updated**: 2026-01-03

### Tasks
- [ ] Update SessionEditModal component with time fields
- [ ] Wire onSessionUpdate handler in EventSpeakersTab
- [ ] Remove redundant buttons from SpeakersSessionsTable
- [ ] Remove unused handler functions
- [ ] Add unit tests for time validation
- [ ] Add integration tests for API calls
- [ ] Manual testing and bug fixes

### Debug Log References
None yet

### Completion Notes
None yet

### File List
- `web-frontend/src/components/organizer/EventManagement/SessionEditModal.tsx`
- `web-frontend/src/components/organizer/EventPage/EventSpeakersTab.tsx`
- `web-frontend/src/components/organizer/EventManagement/SpeakersSessionsTable.tsx`

### Change Log
- 2026-01-03: Story created with implementation plan
