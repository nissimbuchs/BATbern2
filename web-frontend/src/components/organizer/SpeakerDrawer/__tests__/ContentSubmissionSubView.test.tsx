/**
 * ContentSubmissionSubView Tests (Story 11.D.4 — AC10 cases 39–44;
 * Epic 11 bug fix 2026-05-19 removed bio + portrait override cases 39/40/42/43).
 *
 * Coverage:
 *  41. The submit request body OMITS the legacy `username` field (AC11 invariant —
 *      11.C.2 backend reads the username from speaker_pool server-side).
 *  44. An error Alert renders when the submit mutation rejects.
 *
 * Removed cases — bio + portrait are user-level attributes managed by the user-edit
 * modal (the "Edit speaker profile" button), not by the per-event content-submission
 * form. The form fields, state, and the related request-body keys (`bio`,
 * `profilePictureUrl`) were dropped from ContentSubmissionSubView; the corresponding
 * tests are now stale and have been removed.
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { I18nextProvider } from 'react-i18next';
import i18n from '@/i18n/config';
import { ContentSubmissionSubView } from '../ContentSubmissionSubView';
import { speakerContentService } from '@/services/speakerContentService';
import { searchUsers, updateUserRoles } from '@/services/api/userManagementApi';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';
import type { UserSearchResponse } from '@/types/user.types';

// ─── Mocks ────────────────────────────────────────────────────────────────────

const TEST_USER: UserSearchResponse = {
  id: 'jane.doe',
  email: 'jane@example.com',
  firstName: 'Jane',
  lastName: 'Doe',
  roles: ['SPEAKER'],
};

vi.mock('@/services/speakerContentService', () => ({
  speakerContentService: {
    submitContent: vi.fn(),
  },
}));

vi.mock('@/services/api/userManagementApi', () => ({
  searchUsers: vi.fn(),
  updateUserRoles: vi.fn(),
  getUserByUsername: vi.fn(),
}));

// Stub the autocomplete to a deterministic button that injects the test user. This
// bypasses the debounced query lifecycle while keeping the form contract intact.
vi.mock('@/components/shared/UserAutocomplete', () => ({
  UserAutocomplete: ({
    value,
    onChange,
    label,
  }: {
    value: UserSearchResponse | null;
    onChange: (u: UserSearchResponse | null) => void;
    label?: string;
  }) => (
    <button type="button" data-testid="user-autocomplete-stub" onClick={() => onChange(TEST_USER)}>
      {value ? value.id : (label ?? 'Pick user')}
    </button>
  ),
}));

vi.mock('@/components/shared/UserAvatar', () => ({
  UserAvatar: ({ firstName, lastName }: { firstName: string; lastName: string }) => (
    <span data-testid="user-avatar">{`${firstName} ${lastName}`}</span>
  ),
}));

vi.mock('@/components/organizer/UserManagement/UserCreateEditModal', () => ({
  default: () => null,
}));

// ─── Fixtures + render helper ─────────────────────────────────────────────────

const makeSpeaker = (overrides: Partial<SpeakerPoolEntry> = {}): SpeakerPoolEntry => ({
  id: 'speaker-pool-1',
  eventId: 'event-1',
  speakerName: 'Jane Doe',
  status: 'ACCEPTED',
  createdAt: '2026-05-01T00:00:00Z',
  updatedAt: '2026-05-15T00:00:00Z',
  ...overrides,
});

const renderSubView = (
  speaker: SpeakerPoolEntry = makeSpeaker(),
  props: Partial<React.ComponentProps<typeof ContentSubmissionSubView>> = {}
) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider client={queryClient}>
        <ContentSubmissionSubView
          speaker={speaker}
          eventCode="BATbern56"
          onBack={props.onBack ?? vi.fn()}
          onClose={props.onClose ?? vi.fn()}
        />
      </QueryClientProvider>
    </I18nextProvider>
  );
};

// Pick the speaker from the stubbed autocomplete + return the same userEvent
// instance so callers can chain further interactions.
async function pickTestUser(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByTestId('user-autocomplete-stub'));
  await waitFor(() =>
    expect(screen.getByTestId('user-autocomplete-stub')).toHaveTextContent(TEST_USER.id)
  );
}

async function fillRequiredFields(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByTestId('presentation-title-field'), 'Cloud-native event sourcing');
  await user.type(
    screen.getByTestId('presentation-abstract-field'),
    'A talk about reactive architectures and CQRS at scale.'
  );
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('ContentSubmissionSubView — AC10 cases 41 + 44 (post Epic 11 bio/portrait removal)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // The prefill effect runs searchUsers on mount; make it resolve to an empty list
    // so the deterministic stub-driven flow stays in control.
    vi.mocked(searchUsers).mockResolvedValue([]);
    vi.mocked(updateUserRoles).mockResolvedValue({} as never);
    vi.mocked(speakerContentService.submitContent).mockResolvedValue({
      poolId: 'speaker-pool-1',
      status: 'CONTENT_SUBMITTED',
    });
  });

  afterEach(() => {
    cleanup();
  });

  // Case 41 — Regression guard: the request body must NOT include `username`. The
  // 11.C.2 backend rejects unknown properties (@JsonIgnoreProperties(ignoreUnknown=false)).
  it('should_omitUsernameField_fromSubmitContentRequestBody', async () => {
    const user = userEvent.setup();
    renderSubView();
    await pickTestUser(user);
    await fillRequiredFields(user);

    await user.click(screen.getByTestId('submit-speaker-content-button'));

    await waitFor(() => {
      expect(speakerContentService.submitContent).toHaveBeenCalledTimes(1);
    });
    const calls = vi.mocked(speakerContentService.submitContent).mock.calls;
    const requestBody = calls[0][2] as Record<string, unknown>;
    expect(Object.keys(requestBody)).not.toContain('username');
  });

  // Epic 11 bug fix 2026-05-19 — bio + profilePictureUrl have been removed from the
  // form, so the request body must NOT carry them. Keeps the contract narrow: the
  // form is the per-event submission, user-level fields are edited elsewhere.
  it('should_omitBioAndProfilePictureUrl_fromSubmitContentRequestBody', async () => {
    const user = userEvent.setup();
    renderSubView();
    await pickTestUser(user);
    await fillRequiredFields(user);

    await user.click(screen.getByTestId('submit-speaker-content-button'));

    await waitFor(() => {
      expect(speakerContentService.submitContent).toHaveBeenCalledTimes(1);
    });
    const calls = vi.mocked(speakerContentService.submitContent).mock.calls;
    const requestBody = calls[0][2] as Record<string, unknown>;
    expect(Object.keys(requestBody)).not.toContain('bio');
    expect(Object.keys(requestBody)).not.toContain('profilePictureUrl');
  });

  // Case 44 — Submit mutation rejection surfaces an error Alert.
  it('should_renderErrorAlert_whenSubmitMutationRejects', async () => {
    vi.mocked(speakerContentService.submitContent).mockRejectedValueOnce(
      new Error('Server rejected the payload')
    );

    const user = userEvent.setup();
    renderSubView();
    await pickTestUser(user);
    await fillRequiredFields(user);

    await user.click(screen.getByTestId('submit-speaker-content-button'));

    await waitFor(() => {
      expect(screen.getByText('Server rejected the payload')).toBeInTheDocument();
    });
  });
});
