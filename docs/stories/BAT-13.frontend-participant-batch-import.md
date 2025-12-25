# BAT-13: Frontend - Participant Batch Import

**Linear**: [BAT-13](https://linear.app/batbern/issue/BAT-13)
**Status**: Blocked
**Epic**: Epic 3 - Historical Data Migration
**Project**: [Epic 3: Historical Data Migration](https://linear.app/batbern/project/epic-3-historical-data-migration-168670d74297)
**Created**: 2025-12-25

---

## Story

**As an** organizer,
**I want** a batch import interface for uploading participant CSV files,
**so that** I can efficiently import 2,307 historical participants with their event attendance records.

**Frontend Focus:** This story implements the UI/UX using MSW (Mock Service Worker) to mock the batch registration API defined in [BAT-12](https://linear.app/batbern/issue/BAT-12).

---

## Dependencies

**Blocked By:**
- ⚠️ [BAT-12](https://linear.app/batbern/issue/BAT-12) (API Contract) must be Done first
- Requires: OpenAPI specification
- Requires: TypeScript types generated from API contract (`BatchRegistrationRequest`, `BatchRegistrationResponse`)
- Requires: Mock data specifications

**Blocks:**
- [BAT-15](https://linear.app/batbern/issue/BAT-15) (Integration) is BLOCKED until this story is Done

---

## Domain Context

### User Role Context
**Role**: Organizer

**Access**: Authenticated users with ORGANIZER role only

### Component Location
```
web-frontend/src/
├── components/organizer/UserManagement/
│   └── ParticipantBatchImportModal.tsx
├── hooks/useParticipantBatchImport/
│   └── useParticipantBatchImport.ts
├── types/
│   └── participantImport.types.ts
└── utils/
    └── participantImportUtils.ts
```

### API Contract Dependencies
- **Story**: [BAT-12](https://linear.app/batbern/issue/BAT-12)
- **Endpoint**: `POST /api/v1/events/batch_registrations`
- **Generated Types**:
  - `BatchRegistrationRequest`
  - `BatchRegistrationResponse`
  - `BatchRegistrationItem`
  - `FailedRegistration`

---

## Component Specifications

### Component List

**New Components:**

1. **ParticipantBatchImportModal**
   - Location: `web-frontend/src/components/organizer/UserManagement/ParticipantBatchImportModal.tsx`
   - Type: Modal Dialog Component
   - State Management: Local State + React Query
   - Purpose: CSV upload, parsing, preview, and batch import for participants

**Modified Components:**
- `UserManagement.tsx` - Add "Import Participants" button to trigger modal

**Shared Components Used:**
- Material-UI Dialog, Table, Chip, LinearProgress, Alert, Box
- react-dropzone for file upload

### New Files Created

```
web-frontend/src/
├── components/organizer/UserManagement/
│   └── ParticipantBatchImportModal.tsx          # Modal UI (250 lines)
├── hooks/useParticipantBatchImport/
│   └── useParticipantBatchImport.ts             # Business logic (200 lines)
├── types/
│   └── participantImport.types.ts               # TypeScript interfaces (80 lines)
├── utils/
│   └── participantImportUtils.ts                # CSV parsing (150 lines)
└── mocks/handlers/
    └── eventBatchRegistrationHandlers.ts        # MSW mocks (100 lines)
```

### Dependencies to Install

```bash
cd web-frontend
npm install papaparse @types/papaparse
```

---

## Implementation Pattern

**Follow**: `docs/templates/frontend/batch-import-pattern.md`

This story implements the standard 4-file batch import pattern with CSV parsing (instead of JSON).

**Key Adaptations**:
1. **CSV Input** - Use Papa Parse instead of JSON.parse
2. **Row-to-Batch Conversion** - Transform CSV rows with event columns (1-57) into batch registration requests
3. **Batch API** - Use `POST /events/batch_registrations` instead of individual registration calls
4. **Synthetic Emails** - Generate emails for participants without email addresses

---

## Acceptance Criteria

1. **CSV Upload**
   - [ ] Organizer can upload CSV file via drag-and-drop
   - [ ] System validates CSV structure (62 columns expected)
   - [ ] System shows preview of participants with event counts
   - [ ] File validation errors display clearly

2. **CSV Parsing**
   - [ ] Papa Parse handles BOM character at file start
   - [ ] German characters (ä, ö, ü, ß) parsed correctly
   - [ ] Empty columns treated as "no attendance"
   - [ ] Missing emails trigger synthetic email generation

3. **Preview Table**
   - [ ] Shows participant name, email, and event count
   - [ ] Displays synthetic emails distinctly (e.g., badge/icon)
   - [ ] Shows import status during processing
   - [ ] Allows scrolling for large datasets

4. **Batch Import Processing**
   - [ ] Calls batch registration API for each participant
   - [ ] Displays progress bar with "X of Y" indicator
   - [ ] Updates status in real-time (importing → success/error/skipped)
   - [ ] Handles rate limiting (10 requests/second max)

5. **Progress & Feedback**
   - [ ] Shows import progress in real-time
   - [ ] Displays final summary (success, failed, skipped counts)
   - [ ] Lists failed participants with error messages
   - [ ] Allows exporting failed participants for retry

6. **Error Handling**
   - [ ] Displays validation errors clearly
   - [ ] Shows backend errors with context
   - [ ] Allows closing modal even if import fails
   - [ ] Doesn't block UI during long imports

7. **MSW Mocks**
   - [ ] Mock batch registration endpoint configured
   - [ ] Success response matches API contract
   - [ ] Error responses (400, 401, 403, 500) mocked
   - [ ] Delayed responses simulate network timing

---

## Tasks / Subtasks (TDD Workflow)

- [ ] Task 1: Install Dependencies
  - [ ] Install Papa Parse and types
  - [ ] Verify package.json updated

- [ ] Task 2: Setup MSW Mocks (RED Phase)
  - [ ] Create MSW handlers for `POST /events/batch_registrations`
  - [ ] Configure success response mock
  - [ ] Configure partial success response mock
  - [ ] Configure error response mocks (400, 401, 403, 500)
  - [ ] Add 100ms delay to simulate network
  - [ ] Test MSW setup works in test environment

- [ ] Task 3: Define Types (`participantImport.types.ts`)
  - [ ] Define `SourceParticipant` interface (CSV row structure)
  - [ ] Define `ParticipantImportCandidate` interface
  - [ ] Define `ImportStatus` type
  - [ ] Define `ParticipantBatchImportResult` interface
  - [ ] Define `ParticipantBatchImportModalProps` interface
  - [ ] Import generated API types (`BatchRegistrationRequest`, etc.)

- [ ] Task 4: Create CSV Parsing Utilities (RED Phase)
  - [ ] Write failing test for `parseParticipantCsv`
  - [ ] Write failing test for `convertParticipantToRegistrationRequest`
  - [ ] Write failing test for `generateSyntheticEmail`
  - [ ] Write failing test for `extractEventParticipation`
  - [ ] Verify utility tests fail appropriately

- [ ] Task 5: Implement CSV Parsing Utilities (GREEN Phase)
  - [ ] Implement `parseParticipantCsv` with Papa Parse
  - [ ] Implement `convertParticipantToRegistrationRequest`
  - [ ] Implement `generateSyntheticEmail` (firstname.lastname@batbern.ch)
  - [ ] Implement `extractEventParticipation` (columns 1-57)
  - [ ] Handle German character conversion (ä→ae, ö→oe, ü→ue, ß→ss)
  - [ ] Verify all utility tests pass

- [ ] Task 6: Create Business Logic Hook (RED Phase)
  - [ ] Write failing tests for `useParticipantBatchImport` hook
  - [ ] Write failing test for import progress tracking
  - [ ] Write failing test for batch API calls
  - [ ] Write failing test for error handling
  - [ ] Verify hook tests fail appropriately

- [ ] Task 7: Implement Business Logic Hook (GREEN Phase)
  - [ ] Implement `useParticipantBatchImport` hook
  - [ ] Implement sequential processing with progress updates
  - [ ] Call `POST /events/batch_registrations` for each participant
  - [ ] Update candidate status in real-time
  - [ ] Invalidate React Query cache after import
  - [ ] Verify all hook tests pass

- [ ] Task 8: Create Modal Component (RED Phase)
  - [ ] Write failing tests for modal rendering
  - [ ] Write failing tests for CSV dropzone
  - [ ] Write failing tests for preview table
  - [ ] Write failing tests for import button interaction
  - [ ] Write failing tests for progress display
  - [ ] Verify component tests fail appropriately

- [ ] Task 9: Implement Modal Component (GREEN Phase)
  - [ ] Create `ParticipantBatchImportModal.tsx`
  - [ ] Implement CSV dropzone with react-dropzone
  - [ ] Implement preview table with Material-UI Table
  - [ ] Implement progress bar with LinearProgress
  - [ ] Implement result summary with Alert
  - [ ] Implement status chips with color coding
  - [ ] Add "Import Participants" button to UserManagement
  - [ ] Verify all component tests pass

- [ ] Task 10: Refactor (REFACTOR Phase)
  - [ ] Extract reusable logic
  - [ ] Optimize performance (useMemo for computed values)
  - [ ] Improve error messages
  - [ ] Add JSDoc documentation
  - [ ] Verify tests still pass after refactoring

- [ ] Task 11: Accessibility & Responsive Design
  - [ ] Add ARIA labels to dropzone and buttons
  - [ ] Test keyboard navigation
  - [ ] Test screen reader compatibility
  - [ ] Test on mobile (responsive table)
  - [ ] Run Lighthouse accessibility audit

---

## Dev Notes - Implementation Guide

### CSV Structure

**File**: `/apps/BATspa-old/src/api/anmeldungen.csv`

**Columns** (62 total):
- `Name` - Full name (often "FirstName,FirstName,LastName" format)
- `FirstName` - First name
- `LastName` - Last name
- `BestMail` - Email address (missing for ~15% of participants)
- `companyKey` - Company identifier (not used for import)
- `1` through `57` - Event participation flags ("1" = attended, "" = not attended)

**Sample Row**:
```csv
Name,FirstName,LastName,BestMail,companyKey,1,2,3,...,57
A Röthlisberger,A,Röthlisberger,a.roethlisberger@alpiq.com,alpiq,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,1,,1,...
```

### CSV Parsing Implementation

**File**: `utils/participantImportUtils.ts`

```typescript
import Papa from 'papaparse';
import { BatchRegistrationRequest, BatchRegistrationItem } from '@/types/generated/api.types';

export interface SourceParticipant {
  Name: string;
  FirstName: string;
  LastName: string;
  BestMail: string;
  companyKey: string;
  [key: string]: string; // Event columns (1-57)
}

/**
 * Parse participant CSV file
 */
export function parseParticipantCsv(fileContent: string): SourceParticipant[] {
  // Remove BOM character if present
  const cleanContent = fileContent.replace(/^\uFEFF/, '');

  const { data, errors } = Papa.parse<SourceParticipant>(cleanContent, {
    header: true,
    skipEmptyLines: true,
    transformHeader: (header) => header.trim(),
  });

  if (errors.length > 0) {
    throw new Error(`CSV parsing errors: ${errors.map(e => e.message).join(', ')}`);
  }

  // Validate structure (should have 62 columns)
  if (data.length > 0) {
    const columns = Object.keys(data[0]);
    if (columns.length !== 62) {
      throw new Error(`Invalid CSV structure: expected 62 columns, got ${columns.length}`);
    }
  }

  return data;
}

/**
 * Generate synthetic email for participants without email
 * Format: firstname.lastname@batbern.ch
 * Handles German characters (ä→ae, ö→oe, ü→ue, ß→ss)
 */
export function generateSyntheticEmail(firstName: string, lastName: string): string {
  const normalize = (str: string) =>
    str
      .toLowerCase()
      .replace(/ä/g, 'ae')
      .replace(/ö/g, 'oe')
      .replace(/ü/g, 'ue')
      .replace(/ß/g, 'ss')
      .replace(/[^a-z0-9]/g, '');

  const normalizedFirst = normalize(firstName);
  const normalizedLast = normalize(lastName);

  return `${normalizedFirst}.${normalizedLast}@batbern.ch`;
}

/**
 * Extract event participation from CSV row
 * Returns array of event codes where participant attended
 */
export function extractEventParticipation(participant: SourceParticipant): string[] {
  const eventCodes: string[] = [];

  for (let i = 1; i <= 57; i++) {
    const columnValue = participant[i.toString()];
    if (columnValue === '1') {
      eventCodes.push(`BATbern${i}`);
    }
  }

  return eventCodes;
}

/**
 * Convert source participant to batch registration request
 */
export function convertParticipantToRegistrationRequest(
  participant: SourceParticipant
): BatchRegistrationRequest {
  const email = participant.BestMail?.trim() || generateSyntheticEmail(participant.FirstName, participant.LastName);
  const eventCodes = extractEventParticipation(participant);

  const registrations: BatchRegistrationItem[] = eventCodes.map(eventCode => ({
    eventCode,
    status: 'attended',
  }));

  return {
    participantEmail: email,
    firstName: participant.FirstName,
    lastName: participant.LastName,
    registrations,
  };
}
```

### Business Logic Hook

**File**: `hooks/useParticipantBatchImport/useParticipantBatchImport.ts`

```typescript
import { useState, useCallback } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { ParticipantImportCandidate, ParticipantBatchImportResult } from '@/types/participantImport.types';
import { BatchRegistrationRequest } from '@/types/generated/api.types';
import apiClient from '@/services/apiClient';

export function useParticipantBatchImport() {
  const queryClient = useQueryClient();
  const [isImporting, setIsImporting] = useState(false);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [candidates, setCandidates] = useState<ParticipantImportCandidate[]>([]);

  const updateCandidate = useCallback(
    (index: number, updates: Partial<ParticipantImportCandidate>) => {
      setCandidates((prev) =>
        prev.map((candidate, i) =>
          i === index ? { ...candidate, ...updates } : candidate
        )
      );
    },
    []
  );

  const importCandidates = useCallback(
    async (
      requests: BatchRegistrationRequest[],
      onProgress?: (current: number, total: number) => void
    ): Promise<ParticipantBatchImportResult> => {
      setIsImporting(true);

      // Create initial candidates
      const initialCandidates: ParticipantImportCandidate[] = requests.map(req => ({
        firstName: req.firstName,
        lastName: req.lastName,
        email: req.participantEmail,
        eventCount: req.registrations.length,
        isSyntheticEmail: req.participantEmail.endsWith('@batbern.ch'),
        importStatus: 'pending' as const,
      }));
      setCandidates(initialCandidates);
      setCurrentIndex(0);

      const result: ParticipantBatchImportResult = {
        total: requests.length,
        success: 0,
        failed: 0,
        skipped: 0,
      };

      // Sequential processing with rate limiting
      for (let i = 0; i < requests.length; i++) {
        const request = requests[i];
        setCurrentIndex(i);

        updateCandidate(i, { importStatus: 'importing' });
        onProgress?.(i + 1, requests.length);

        try {
          const response = await apiClient.post<BatchRegistrationResponse>(
            '/api/v1/events/batch_registrations',
            request
          );

          const data = response.data;

          if (data.failedRegistrations.length > 0) {
            // Partial success
            updateCandidate(i, {
              importStatus: 'success',
              errorMessage: `${data.successfulRegistrations}/${data.totalRegistrations} registrations succeeded`,
            });
          } else {
            // Full success
            updateCandidate(i, { importStatus: 'success' });
          }

          result.success++;
        } catch (error) {
          const errorMessage = error instanceof Error ? error.message : 'Unknown error';

          updateCandidate(i, {
            importStatus: 'error',
            errorMessage,
          });
          result.failed++;
        }

        // Rate limiting: 10 requests/second max
        if (i < requests.length - 1) {
          await new Promise(resolve => setTimeout(resolve, 100));
        }
      }

      // Invalidate cache
      await queryClient.invalidateQueries({ queryKey: ['users'] });
      await queryClient.invalidateQueries({ queryKey: ['registrations'] });

      setIsImporting(false);
      return result;
    },
    [queryClient, updateCandidate]
  );

  return {
    importCandidates,
    isImporting,
    currentIndex,
    candidates,
  };
}
```

### MSW Mock Handlers

**File**: `mocks/handlers/eventBatchRegistrationHandlers.ts`

```typescript
import { rest } from 'msw';
import { BatchRegistrationRequest, BatchRegistrationResponse } from '@/types/generated/api.types';

export const eventBatchRegistrationHandlers = [
  rest.post('/api/v1/events/batch_registrations', async (req, res, ctx) => {
    const body = await req.json<BatchRegistrationRequest>();

    // Simulate successful batch registration
    const response: BatchRegistrationResponse = {
      username: `${body.firstName.toLowerCase()}.${body.lastName.toLowerCase()}`,
      totalRegistrations: body.registrations.length,
      successfulRegistrations: body.registrations.length,
      failedRegistrations: [],
      errors: [],
    };

    return res(
      ctx.delay(100), // Simulate network delay
      ctx.status(200),
      ctx.json(response)
    );
  }),
];
```

---

## Definition of Done Checklist

### Development Complete
- [ ] All component tests written BEFORE implementation (TDD)
- [ ] All acceptance criteria have corresponding tests
- [ ] All acceptance criteria implemented
- [ ] Component tests passing (>90% coverage)
- [ ] Hook tests passing (>90% coverage)
- [ ] Utility tests passing (>95% coverage)
- [ ] MSW mocks properly configured and tested
- [ ] TypeScript types properly used (no 'any')
- [ ] Code follows React best practices
- [ ] ESLint passes with no errors

### UX Complete
- [ ] UI matches wireframe/mockup (if provided)
- [ ] Responsive design tested (mobile/tablet/desktop)
- [ ] CSV dropzone works correctly
- [ ] Preview table displays correctly
- [ ] Progress bar updates in real-time
- [ ] Error states implemented and tested
- [ ] Result summary displays correctly
- [ ] Material-UI theme applied consistently

### Accessibility Complete
- [ ] ARIA labels added to interactive elements
- [ ] Keyboard navigation works correctly
- [ ] Focus indicators visible
- [ ] Screen reader tested (basic)
- [ ] Color contrast meets WCAG 2.1 AA
- [ ] Lighthouse accessibility score >90

### Integration Ready
- [ ] MSW mocks match API contract from Story 3.2.1a exactly
- [ ] Generated TypeScript types used correctly
- [ ] API client services ready for backend integration
- [ ] Component ready to consume real API

---

## Integration Notes for Story 3.2.1d

- **MSW handler to remove**: `eventBatchRegistrationHandlers.ts`
- **Environment variable needed**: `REACT_APP_API_BASE_URL` (for real API calls)
- **Expected behavior**: Should work identically with real backend

---

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-12-25 | 0.1 | Initial frontend story creation | Bob (SM) |

---

## Dev Agent Record

_This section will be populated during implementation by the dev agent._

### Agent Model Used
_To be filled by dev agent_

### Implementation Approach
_To be filled by dev agent_

### File List
_To be filled by dev agent:_
- Created: [list of new files]
- Modified: [list of changed files]
- Deleted: [list of removed files]

### MSW Configuration Details
_To be filled by dev agent:_
- Handler file locations
- Mock data file locations
- How to run with mocks enabled
- How to disable mocks for integration

### Component Locations
_To be filled by dev agent:_
- ParticipantBatchImportModal → web-frontend/src/components/organizer/UserManagement/
- useParticipantBatchImport → web-frontend/src/hooks/use Participant BatchImport/
- participantImport.types → web-frontend/src/types/
- participantImportUtils → web-frontend/src/utils/
