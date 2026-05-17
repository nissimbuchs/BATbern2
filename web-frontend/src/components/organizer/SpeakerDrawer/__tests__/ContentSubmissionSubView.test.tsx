/**
 * ContentSubmissionSubView Tests (Story 11.D.4 — AC10 cases 39–44)
 *
 * Coverage:
 *  39. Bio field renders with max length 5000 + character counter
 *  40. uploadProfilePictureForUser is called with the selected user id on portrait change
 *  41. The submit request body OMITS the legacy `username` field (AC11 invariant — 11.C.2
 *      backend reads the username from speaker_pool server-side).
 *  42. Non-empty bio is included in the request body.
 *  43. profilePictureUrl is included in the request body when portrait upload succeeds.
 *  44. An error Alert renders when the submit mutation rejects.
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
import { uploadProfilePictureForUser } from '@/services/api/userAccountApi';
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

vi.mock('@/services/api/userAccountApi', () => ({
  uploadProfilePictureForUser: vi.fn(),
}));

vi.mock('@/services/api/userManagementApi', () => ({
  searchUsers: vi.fn(),
  updateUserRoles: vi.fn(),
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
  // Title + abstract are required; bio is optional and left to the caller.
  await user.type(screen.getByTestId('presentation-title-field'), 'Cloud-native event sourcing');
  await user.type(
    screen.getByTestId('presentation-abstract-field'),
    'A talk about reactive architectures and CQRS at scale.'
  );
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('ContentSubmissionSubView — AC10 cases 39–44', () => {
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
    vi.mocked(uploadProfilePictureForUser).mockResolvedValue(
      'https://cdn.example.com/portraits/jane.doe.jpg'
    );
  });

  afterEach(() => {
    cleanup();
  });

  // Case 39 — Bio field rendered with max length 5000 + character counter.
  it('should_renderBioField_withMaxLength5000_andCharacterCounter', async () => {
    renderSubView();
    const bioField = await screen.findByTestId('speaker-bio-field');
    expect(bioField).toBeInTheDocument();
    // The component's MAX_BIO_LENGTH constant is 5000 — verify it surfaces in the
    // helper-text counter on initial render (remaining = 5000).
    expect(screen.getByText(/5000 \/ 5000 characters remaining/i)).toBeInTheDocument();
  });

  // Case 40 — Selecting a portrait file calls uploadProfilePictureForUser with the
  // selected user id (admin presigned-URL helper).
  it('should_callUploadProfilePictureForUser_withSelectedUserId_whenPortraitChanges', async () => {
    const user = userEvent.setup();
    renderSubView();
    await pickTestUser(user);

    const fileInput = screen.getByTestId('speaker-portrait-file-input') as HTMLInputElement;
    const file = new File(['fake-bytes'], 'portrait.jpg', { type: 'image/jpeg' });
    await user.upload(fileInput, file);

    await waitFor(() => {
      expect(uploadProfilePictureForUser).toHaveBeenCalledTimes(1);
    });
    const [calledUserId, calledFile] = vi.mocked(uploadProfilePictureForUser).mock.calls[0];
    expect(calledUserId).toBe(TEST_USER.id);
    expect((calledFile as File).name).toBe('portrait.jpg');
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

  // Case 42 — Non-empty bio is propagated into the request body.
  it('should_includeBioInRequestBody_whenBioIsNonEmpty', async () => {
    const user = userEvent.setup();
    renderSubView();
    await pickTestUser(user);
    await fillRequiredFields(user);

    const bioField = screen.getByTestId('speaker-bio-field');
    await user.type(bioField, 'Cloud architect & speaker since 2019.');

    await user.click(screen.getByTestId('submit-speaker-content-button'));

    await waitFor(() => {
      expect(speakerContentService.submitContent).toHaveBeenCalledTimes(1);
    });
    const requestBody = vi.mocked(speakerContentService.submitContent).mock.calls[0][2];
    expect(requestBody.bio).toBe('Cloud architect & speaker since 2019.');
  });

  // Case 43 — profilePictureUrl is captured into the request body after a successful
  // portrait upload (the admin presigned-URL helper resolves to the CDN URL).
  it('should_includeProfilePictureUrl_whenPortraitUploadSucceeds', async () => {
    const user = userEvent.setup();
    renderSubView();
    await pickTestUser(user);

    const fileInput = screen.getByTestId('speaker-portrait-file-input') as HTMLInputElement;
    const file = new File(['fake-bytes'], 'portrait.jpg', { type: 'image/jpeg' });
    await user.upload(fileInput, file);

    await waitFor(() => {
      expect(uploadProfilePictureForUser).toHaveBeenCalledTimes(1);
    });

    await fillRequiredFields(user);
    await user.click(screen.getByTestId('submit-speaker-content-button'));

    await waitFor(() => {
      expect(speakerContentService.submitContent).toHaveBeenCalledTimes(1);
    });
    const requestBody = vi.mocked(speakerContentService.submitContent).mock.calls[0][2];
    expect(requestBody.profilePictureUrl).toBe('https://cdn.example.com/portraits/jane.doe.jpg');
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

    // The Alert renders either the rejected error message OR the i18n fallback
    // `organizer:speakerContent.errors.submitFailed`. Both are acceptable per the
    // component's `error instanceof Error ? .message : t(...)` branch.
    const alert = await screen.findByRole('alert');
    expect(alert).toBeInTheDocument();
    expect(alert).toHaveTextContent(/Server rejected the payload|Failed to submit content/i);
  });
});
