# Story 10.23: Event Description Section on Public Homepage

Status: done

## Story

As a **visitor**,
I want to read the event description on the public homepage right below the hero section,
so that I can understand what this BATbern event is about before deciding to register.

## Acceptance Criteria

1. **Homepage**: An `EventDescriptionSection` is rendered immediately after `HeroSection` when `activeEvent.description` is non-null and non-empty
2. **Homepage**: When description is null or empty the section is completely absent — no empty card, no whitespace, no placeholder text shown to anonymous visitors
3. Description text wraps correctly on mobile and desktop (responsive prose block)
4. **Archive Detail Page**: `ArchiveEventDetailPage` also shows `description` in a similar prose block (using `EventDescriptionSection`) if non-empty, positioned below event metadata and above the sessions list
5. No backend changes; no Flyway migrations; no OpenAPI changes; no type regeneration needed (`Event.description?: string` already present in the generated types)
6. i18n: `events.description.heading` key present in all 10 locales; `npm run type-check` passes; `npm run lint` passes
7. Unit test: `EventDescriptionSection.test.tsx` covers render/hidden behaviour

## Tasks / Subtasks

### Phase 1: Create EventDescriptionSection component (AC: 1, 2, 3)
- [x] Create `web-frontend/src/components/public/Event/EventDescriptionSection.tsx`
  - [x] Props: `{ description: string | null | undefined }`
  - [x] Return `null` when `description` is falsy (null, undefined, or empty string after trim)
  - [x] Render a styled prose block (section or div) using the site dark theme
    - Consistent with `EventLogistics` / `CapacityIndicator` card style
    - Use MUI `Paper` or `Box` with `sx` styling matching the site's dark theme (background `#1a1a1a` or `rgba(255,255,255,0.05)`, rounded corners, padding)
  - [x] Optional section heading: `<Typography variant="h6">{t('events:description.heading')}</Typography>`
  - [x] Description body: `<Typography sx={{ whiteSpace: 'pre-wrap' }}>{description}</Typography>` (preserves line breaks from `\n`)
  - [x] Use `useTranslation('events')` for the heading key

### Phase 2: Unit test for EventDescriptionSection (AC: 7)
- [x] Create `web-frontend/src/components/public/Event/EventDescriptionSection.test.tsx`
  - [x] `renders description text when description is a non-empty string`
  - [x] `renders nothing (null) when description is null`
  - [x] `renders nothing (null) when description is empty string`
  - [x] `renders nothing (null) when description is whitespace-only string`
  - [x] Mock `useTranslation` — key `'description.heading'` → `'About This Event'`

### Phase 3: Update HomePage (AC: 1, 2)
- [x] Edit `web-frontend/src/pages/public/HomePage.tsx`
  - [x] Import `EventDescriptionSection` from `'@/components/public/Event/EventDescriptionSection'`
  - [x] Insert `<EventDescriptionSection description={event?.description} />` immediately after `<HeroSection ... />` (and its associated inline RegistrationWizard expansion area) and before `EventLogistics`
  - [x] No guard needed beyond what `EventDescriptionSection` handles internally — pass `event?.description` directly

### Phase 4: Update ArchiveEventDetailPage (AC: 4)
- [x] Edit `web-frontend/src/pages/public/ArchiveEventDetailPage.tsx`
  - [x] Import `EventDescriptionSection`
  - [x] The archive page already renders description text inline inside the header metadata block — **replace or supplement** that inline display with `<EventDescriptionSection description={event.description} />` placed after the event header/metadata card and before the sessions section
  - [x] Remove the duplicate inline description text from the header if it already renders there (avoid double display)

### Phase 5: i18n — add description.heading to all 10 locales (AC: 6)
- [x] `web-frontend/public/locales/de/events.json` → `"description": { "heading": "Über diese Veranstaltung" }`
- [x] `web-frontend/public/locales/en/events.json` → `"description": { "heading": "About This Event" }`
- [x] `web-frontend/public/locales/fr/events.json` → `"description": { "heading": "À propos de cet événement" }`
- [x] `web-frontend/public/locales/it/events.json` → `"description": { "heading": "Informazioni sull'evento" }`
- [x] `web-frontend/public/locales/rm/events.json` → `"description": { "heading": "Davart quest'eveniment" }`
- [x] `web-frontend/public/locales/es/events.json` → `"description": { "heading": "Acerca de este evento" }`
- [x] `web-frontend/public/locales/fi/events.json` → `"description": { "heading": "Tietoja tapahtumasta" }`
- [x] `web-frontend/public/locales/nl/events.json` → `"description": { "heading": "Over dit evenement" }`
- [x] `web-frontend/public/locales/ja/events.json` → `"description": { "heading": "このイベントについて" }`
- [x] `web-frontend/public/locales/gsw-BE/events.json` → `"description": { "heading": "Über disi Veranstautig" }`

### Phase 6: Validation
- [x] `cd web-frontend && npm run type-check` — zero errors
- [x] `cd web-frontend && npm run lint` — zero warnings/errors
- [x] `npx vitest run --reporter=verbose EventDescriptionSection` — 4/4 PASS (dumped to `/tmp/test-10-23-final.log`)
- [ ] Manual smoke: run `make dev-native-up`, open homepage with an event that has a description set — section appears; remove description from organizer settings — section disappears

## Dev Notes

### No backend work — description field already exists

`EventResponse.description` is a nullable TEXT column already mapped:
- DB: `description TEXT` in `events` table
- Java DTO: `EventResponse.java:37` — `String description;`
- Generated TypeScript: `web-frontend/src/types/generated/events-api.types.ts` → `Event.description?: string`

**No OpenAPI changes, no type regen, no migrations.**

### Where exactly to insert in HomePage

The `HomePage.tsx` component renders sections in this order:
1. `<HeroSection>` — full-screen hero with title, date, countdown, CTA
2. RegistrationStatusBanner (conditional — only for logged-in users in certain workflow states)
3. DeregistrationModal
4. **← Insert `<EventDescriptionSection description={event?.description} />` here**
5. EventLogistics (capacity, date details)
6. CapacityIndicator
7. EventProgram (agenda sessions timeline)
8. SessionCards
9. SpeakerGrid
10. VenueMap
11. SocialSharing
12. UpcomingEventsSection
13. TestimonialSection / PhotosMarquee
14. NewsletterSubscribeWidget

"Right below the hero" (story spec) = position #4 above, between HeroSection and EventLogistics.

Note: `event` may be `null` while loading (skeleton/loading state). Guard with `event?.description` — `EventDescriptionSection` handles null internally.

### ArchiveEventDetailPage — existing description in header

The exploration reveals the archive page already renders description inline inside the event header metadata block (as part of the title/date/description cluster).

**Strategy**: Replace that inline text with the dedicated `EventDescriptionSection` component positioned immediately after the header card and before the sessions section. This keeps the header cleaner (just title + date + topic badge) and gives description a proper visual section of its own consistent with the homepage.

If the inline rendering is minimal and hard to locate, just ensure `EventDescriptionSection` is inserted above the sessions list and the inline text is removed/commented to avoid duplication.

### Component styling — dark theme consistency

BATbern's public pages use a dark theme. Match the style of neighbouring event sections:

```tsx
// Pattern from EventLogistics.tsx / CapacityIndicator.tsx
<Box
  sx={{
    backgroundColor: 'rgba(255, 255, 255, 0.05)',
    borderRadius: 2,
    p: { xs: 2, md: 3 },
    mb: 3,
  }}
>
  <Typography variant="h6" sx={{ color: 'text.primary', mb: 1 }}>
    {t('description.heading')}
  </Typography>
  <Typography
    variant="body1"
    sx={{ color: 'text.secondary', whiteSpace: 'pre-wrap', lineHeight: 1.7 }}
  >
    {description}
  </Typography>
</Box>
```

Use `sx` props (MUI system), not a separate CSS file. No Framer Motion animations (pure display, same principle as 10.8a).

### EventDescriptionSection — null/empty guard pattern

```tsx
export function EventDescriptionSection({
  description,
}: {
  description?: string | null;
}): JSX.Element | null {
  const { t } = useTranslation('events');
  if (!description?.trim()) return null;

  return (
    <Box sx={{ ... }}>
      <Typography variant="h6">{t('description.heading')}</Typography>
      <Typography sx={{ whiteSpace: 'pre-wrap' }}>{description}</Typography>
    </Box>
  );
}
```

**Critical**: Return `null` (not `undefined`) for React reconciler compatibility.

### i18n key placement in events.json

The `events` namespace is large (774 lines in en/events.json). The `description` key should be added at the top-level (not nested under `public.` or `eventPage.`) since it will be used by the component directly via `t('description.heading')`.

**Do NOT** add under `events.public.*` — `EventDescriptionSection` is a standalone component that uses `useTranslation('events')` directly and expects `description.heading` as a top-level key within that namespace.

Check whether a `"description"` key already exists in events.json (e.g., for form labels). If so, add `"heading"` as a sub-key inside the existing `"description"` object. If not, create a new top-level `"description"` object.

### Test pattern for EventDescriptionSection

```tsx
// EventDescriptionSection.test.tsx
import { render, screen } from '@testing-library/react';
import { EventDescriptionSection } from './EventDescriptionSection';

jest.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => (key === 'description.heading' ? 'About This Event' : key),
  }),
}));

describe('EventDescriptionSection', () => {
  it('renders description text when non-empty', () => {
    render(<EventDescriptionSection description="Join us for great talks." />);
    expect(screen.getByText('Join us for great talks.')).toBeInTheDocument();
    expect(screen.getByText('About This Event')).toBeInTheDocument();
  });

  it('renders nothing when description is null', () => {
    const { container } = render(<EventDescriptionSection description={null} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders nothing when description is empty string', () => {
    const { container } = render(<EventDescriptionSection description="" />);
    expect(container.firstChild).toBeNull();
  });

  it('renders nothing when description is whitespace only', () => {
    const { container } = render(<EventDescriptionSection description="   " />);
    expect(container.firstChild).toBeNull();
  });
});
```

### Test output pattern (CLAUDE.md mandate)

```bash
cd web-frontend && npm run test -- --testPathPattern="EventDescriptionSection" 2>&1 | tee /tmp/test-10-23.log
grep -E "PASS|FAIL|✓|✗|Tests:" /tmp/test-10-23.log
```

### Checking ArchiveEventDetailPage description rendering

Before editing, read `ArchiveEventDetailPage.tsx` to confirm where `event.description` is currently rendered. The exploration noted it appears in the "Event Header" block as a description text. Locate the exact JSX and remove it before adding `EventDescriptionSection` to avoid double display.

### Recent relevant commits

```
8809524c fix(10.16): replace Mockito star import with explicit imports  (most recent)
619ba1ad fix(10.16): correct double /api/v1 prefix and gateway routing
99e34fac feat(10.16): AI-assisted event content creation
```

Story 10.16 established the `AiAssistController` + `BatbernAiService` pattern (RestClient + OpenAI). This story has no backend relevance — purely frontend component.

### Project Structure Notes

**New file:**
```
web-frontend/src/components/public/Event/EventDescriptionSection.tsx     (NEW component)
web-frontend/src/components/public/Event/EventDescriptionSection.test.tsx (NEW test)
```

**Modified files:**
```
web-frontend/src/pages/public/HomePage.tsx                               (add section after HeroSection)
web-frontend/src/pages/public/ArchiveEventDetailPage.tsx                 (add section before sessions, remove inline)
web-frontend/public/locales/de/events.json                               (description.heading DE)
web-frontend/public/locales/en/events.json                               (description.heading EN)
web-frontend/public/locales/fr/events.json                               (description.heading FR)
web-frontend/public/locales/it/events.json                               (description.heading IT)
web-frontend/public/locales/rm/events.json                               (description.heading RM)
web-frontend/public/locales/es/events.json                               (description.heading ES)
web-frontend/public/locales/fi/events.json                               (description.heading FI)
web-frontend/public/locales/nl/events.json                               (description.heading NL)
web-frontend/public/locales/ja/events.json                               (description.heading JA)
web-frontend/public/locales/gsw-BE/events.json                           (description.heading gsw-BE)
```

**No changes to:**
- `docs/api/events-api.openapi.yml` (no API changes)
- `web-frontend/src/types/generated/events-api.types.ts` (no type regen needed)
- Any backend Java files
- Any Flyway migration files

### References

- Epic 10 story definition: [Source: docs/prd/epic-10-additional-stories.md#story-1023 (line 1624)]
- EventResponse.java description field: [Source: services/event-management-service/src/main/java/ch/batbern/events/dto/EventResponse.java:37]
- Generated TypeScript Event type: [Source: web-frontend/src/types/generated/events-api.types.ts — `Event.description?: string`]
- HomePage.tsx (insertion point): [Source: web-frontend/src/pages/public/HomePage.tsx]
- ArchiveEventDetailPage.tsx (sessions location): [Source: web-frontend/src/pages/public/ArchiveEventDetailPage.tsx]
- EventLogistics.tsx (styling reference): [Source: web-frontend/src/components/public/Event/EventLogistics.tsx]
- CapacityIndicator.tsx (styling reference): [Source: web-frontend/src/components/public/Event/CapacityIndicator.tsx]
- i18n multi-namespace pattern: [Source: web-frontend/src/i18n/config.ts]
- Coding standards (TDD, test patterns): [Source: docs/architecture/coding-standards.md]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- `/tmp/test-10-23.log` — initial test run (4/4 PASS after fixing `jest.mock` → `vi.mock`)
- `/tmp/test-10-23-final.log` — final component test run (4/4 PASS)
- `/tmp/typecheck-10-23.log` — type-check: zero errors (fixed by removing explicit `JSX.Element` return type)
- `/tmp/lint-10-23.log` — lint: zero warnings/errors
- `/tmp/fulltest-10-23.log` — full regression: 3864 pass, 3 pre-existing failures in `EventParticipantsTab.test.tsx` and `EventLogistics.test.tsx` (not in changeset)

### Completion Notes List

- Created `EventDescriptionSection` component using MUI `Box`+`Typography` with dark theme `rgba(255,255,255,0.05)` background, matching `EventLogistics`/`CapacityIndicator` style
- Component returns `null` for falsy/whitespace-only description (AC2 — no empty card, no whitespace)
- HomePage: inserted in separate container div immediately after `HeroSection` and before the main content container, before `EventLogistics` (AC1/AC2)
- ArchiveEventDetailPage: replaced inline `<p className="text-gray-700 leading-relaxed">{event.description}</p>` with `<EventDescriptionSection>` positioned after header block and before sessions (AC4, no duplication)
- Fixed: Vitest project uses `vi.mock` not `jest.mock`; removed explicit `JSX.Element` return type (not exported by TS config)
- All 10 locale files updated via Python `json.load`/`json.dump` — clean 3-line diff per file

### File List

- `web-frontend/src/components/public/Event/EventDescriptionSection.tsx` (NEW)
- `web-frontend/src/components/public/Event/EventDescriptionSection.test.tsx` (NEW)
- `web-frontend/src/pages/public/HomePage.tsx` (MODIFIED)
- `web-frontend/src/pages/public/ArchiveEventDetailPage.tsx` (MODIFIED)
- `web-frontend/public/locales/de/events.json` (MODIFIED)
- `web-frontend/public/locales/en/events.json` (MODIFIED)
- `web-frontend/public/locales/fr/events.json` (MODIFIED)
- `web-frontend/public/locales/it/events.json` (MODIFIED)
- `web-frontend/public/locales/rm/events.json` (MODIFIED)
- `web-frontend/public/locales/es/events.json` (MODIFIED)
- `web-frontend/public/locales/fi/events.json` (MODIFIED)
- `web-frontend/public/locales/nl/events.json` (MODIFIED)
- `web-frontend/public/locales/ja/events.json` (MODIFIED)
- `web-frontend/public/locales/gsw-BE/events.json` (MODIFIED)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (MODIFIED)
