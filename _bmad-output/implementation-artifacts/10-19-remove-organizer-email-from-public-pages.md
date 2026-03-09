# Story 10.19: Remove Organizer Email from Public Presenter & About Pages

Status: done

## Story

As an **organizer**,
I want our personal email addresses removed from the public-facing About page and the moderator presentation page,
so that we don't expose team members' private contact details to every event attendee.

## Acceptance Criteria

1. **AC1 — About page**: `/about` renders all organizer cards without any email address or `mailto:` link.
2. **AC2 — Presenter page**: `/present/:eventCode` (CommitteeSlide) renders organizer cards with no email address or `mailto:` link visible.
3. **AC3 — Contact section preserved**: The Contact section on `/about` still shows `info@berner-architekten-treffen.ch` (footer and contact section unchanged).
4. **AC4 — No schema/API change**: Organizer `email` field remains in DB and API; only the UI render is removed.
5. **AC5 — Quality gates**: `type-check` passes; lint passes; no test regressions.

---

## Tasks / Subtasks

### Phase 1: Audit (read before touching anything)

- [x] **T1 — Read OrganizerDisplay.tsx** (AC: #1, #2)
  - [x] T1.1 — Confirm exact location of the email block:
    ```
    web-frontend/src/components/public/About/OrganizerDisplay.tsx  lines 69–77
    ```
  - [x] T1.2 — Confirm `Mail` is imported from `lucide-react` (line 8) and is ONLY used for the email block.
  - [x] T1.3 — Confirm `Building2` is still used elsewhere in the file (line 65, 93) → keep it.

- [x] **T2 — Verify no email renders in presentation slides** (AC: #2)
  - [x] T2.1 — CommitteeSlide uses `OrganizerDisplay` with `showBio={false}`. Fixing OrganizerDisplay fixes CommitteeSlide automatically.
  - [x] T2.2 — SessionSlide → uses SpeakerCard / TwoSpeakerCard → confirmed no email render.
  - [x] T2.3 — No other presentation slide child components render `email` or `mailto`.

### Phase 2: Edit OrganizerDisplay.tsx

- [x] **T3 — Remove email block** (AC: #1, #2)
  - [x] T3.1 — Remove the `Mail` import from `lucide-react` (keep `Building2`):
    ```tsx
    // Before:
    import { Building2, Mail } from 'lucide-react';
    // After:
    import { Building2 } from 'lucide-react';
    ```
  - [x] T3.2 — Remove the entire email conditional block (lines 69–77):
    ```tsx
    // REMOVE this entire block:
    {organizer.email && (
      <a
        href={`mailto:${organizer.email}`}
        className="text-sm text-blue-400 hover:text-blue-300 transition-colors flex items-center gap-1.5"
      >
        <Mail className="h-3 w-3 flex-shrink-0" />
        <span className="truncate">{organizer.email}</span>
      </a>
    )}
    ```
  - [x] T3.3 — Verify `OrganizerDisplay.tsx` layout comment at lines 21–25 still makes sense; update the inline comment to remove "Email" mention (the layout comment says `[Email]` — remove that line).

### Phase 3: Quality gates

- [x] **T4 — Type-check** (AC: #5)
  - [x] T4.1 — `cd web-frontend && npx tsc --noEmit 2>&1 | tee /tmp/typecheck-10-19.log && grep -c "error" /tmp/typecheck-10-19.log`
  - [x] T4.2 — Expect 0 errors.

- [x] **T5 — Lint** (AC: #5)
  - [x] T5.1 — `cd web-frontend && npx eslint src/components/public/About/OrganizerDisplay.tsx 2>&1 | tee /tmp/lint-10-19.log`
  - [x] T5.2 — Expect 0 errors (removing an unused import satisfies ESLint `no-unused-vars`).

- [x] **T6 — Run existing tests** (AC: #5)
  - [x] T6.1 — `cd web-frontend && npx vitest run 2>&1 | tee /tmp/test-10-19.log && grep -E "PASS|FAIL|Tests" /tmp/test-10-19.log | tail -10`
  - [x] T6.2 — No test file exists for `OrganizerDisplay`; no test changes expected.

---

## Dev Notes

### Summary

This is a **one-file frontend change**: remove the `email` display block from `OrganizerDisplay.tsx`.

Because `OrganizerDisplay` is the single shared component used on both the `/about` page and the presenter page (`CommitteeSlide.tsx`), the fix to one file satisfies both ACs automatically.

### Exact Diff (OrganizerDisplay.tsx)

**Line 8 — import change:**
```tsx
// Before
import { Building2, Mail } from 'lucide-react';

// After
import { Building2 } from 'lucide-react';
```

**Lines 69–77 — remove block entirely:**
```tsx
{organizer.email && (
  <a
    href={`mailto:${organizer.email}`}
    className="text-sm text-blue-400 hover:text-blue-300 transition-colors flex items-center gap-1.5"
  >
    <Mail className="h-3 w-3 flex-shrink-0" />
    <span className="truncate">{organizer.email}</span>
  </a>
)}
```

**Layout comment at lines 21–25 (nice-to-have):**
Remove the `[Email]` line from the JSDoc comment block.

### File Impact Map

| File | Change | Notes |
|------|--------|-------|
| `web-frontend/src/components/public/About/OrganizerDisplay.tsx` | **EDIT** — remove email block + Mail import | Only file changed |
| `web-frontend/src/pages/presentation/slides/CommitteeSlide.tsx` | No change needed | Inherits fix via OrganizerDisplay |
| `web-frontend/src/pages/PresentationPage.tsx` | No change needed | Inherits fix via CommitteeSlide → OrganizerDisplay |

### What Is NOT Affected

- `web-frontend/src/components/public/Footer/PublicFooter.tsx:23` — `mailto:info@berner-architekten-treffen.ch` → preserved per AC3
- `web-frontend/src/components/public/Event/SocialSharing.tsx` — email share link is for sharing events, not organizer PII → unchanged
- All registration form email fields → functional forms, not organizer PII → unchanged
- Backend schema, API, DB → no changes per AC4

### Architecture Compliance

- Component follows existing project pattern: no `process.env` access, no direct HTTP calls
- lucide-react icons — always import only what is used; unused imports cause lint errors
- No new i18n keys needed; no test files to create or update
- Commit pattern: `fix(10.19): remove organizer email from public about and presenter pages`

### References

- OrganizerDisplay email block: [Source: web-frontend/src/components/public/About/OrganizerDisplay.tsx#L69-77]
- CommitteeSlide uses OrganizerDisplay: [Source: web-frontend/src/pages/presentation/slides/CommitteeSlide.tsx#L14,69]
- PresentationPage CommitteeSlide usage: [Source: web-frontend/src/pages/PresentationPage.tsx#L421]
- Public footer contact email (unchanged): [Source: web-frontend/src/components/public/Footer/PublicFooter.tsx#L23]
- Story spec: [Source: docs/prd/epic-10-additional-stories.md#L1364-1402]

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- `/tmp/typecheck-10-19.log` — 0 errors
- `/tmp/lint-10-19.log` — 0 errors
- `/tmp/test-10-19.log` — 3860 passed / 3 pre-existing failures in unrelated components (EventParticipantsTab, EventLogistics); 0 regressions from this story

### Completion Notes List

- Removed `Mail` import from `lucide-react` (line 8) — was the only usage; `Building2` kept (used at 2 locations)
- Removed 9-line email conditional block (`{organizer.email && (<a href={mailto:...}>...</a>)}`) from OrganizerDisplay render
- Removed `[Email]` line from JSDoc layout comment
- AC2 satisfied automatically: CommitteeSlide renders OrganizerDisplay; no separate change needed
- AC3 confirmed: `PublicFooter.tsx:23` `mailto:info@berner-architekten-treffen.ch` untouched
- AC4 confirmed: no DB/API/schema changes
- 3 pre-existing test failures in `EventParticipantsTab.test.tsx` and `EventLogistics.test.tsx` are unrelated to this story

### File List

- `web-frontend/src/components/public/About/OrganizerDisplay.tsx`

### Change Log

- 2026-03-03: Remove organizer email from public About and CommitteeSlide pages (Story 10.19)
