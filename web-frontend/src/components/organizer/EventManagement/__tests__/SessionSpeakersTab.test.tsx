/**
 * SessionSpeakersTab Unit Tests
 *
 * Coverage:
 * - Renders speaker list from session.speakers
 * - Shows "no speakers" empty state
 * - "Set Primary" disabled for current primary, enabled for others
 * - Remove calls removeSpeaker mutation
 * - Set Primary calls removeSpeaker then assignSpeaker
 * - Add form: disabled Add button when no user selected
 * - Add form: calls assignSpeaker with correct username + role, clears form on success
 * - Add form: shows inline error on mutation failure
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders as render } from '@/test/test-utils';
import { SessionSpeakersTab } from '../SessionSpeakersTab';
import type { SessionUI, SessionSpeaker } from '@/types/event.types';

// ─── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, fallback?: unknown) => {
      if (typeof fallback === 'string') return fallback;
      if (
        typeof fallback === 'object' &&
        fallback !== null &&
        'defaultValue' in (fallback as object)
      )
        return (fallback as { defaultValue: string }).defaultValue;
      return key;
    },
  }),
}));

const mockAssignMutateAsync = vi.fn().mockResolvedValue({});
const mockRemoveMutateAsync = vi.fn().mockResolvedValue({});

vi.mock('@/hooks/useSessionSpeakers', () => ({
  useAssignSpeaker: () => ({
    mutateAsync: mockAssignMutateAsync,
    isPending: false,
  }),
  useRemoveSpeaker: () => ({
    mutateAsync: mockRemoveMutateAsync,
    isPending: false,
  }),
}));

vi.mock('@/components/shared/UserAutocomplete', () => ({
  UserAutocomplete: ({
    onChange,
    label,
  }: {
    onChange: (user: { id: string; firstName: string; lastName: string } | null) => void;
    label?: string;
  }) => (
    <button
      data-testid="user-autocomplete"
      onClick={() => onChange({ id: 'jane.doe', firstName: 'Jane', lastName: 'Doe' })}
    >
      {label ?? 'Search User'}
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

// ─── Test Fixtures ─────────────────────────────────────────────────────────────

const primarySpeaker: SessionSpeaker = {
  username: 'john.doe',
  firstName: 'John',
  lastName: 'Doe',
  company: 'ACME',
  speakerRole: 'PRIMARY_SPEAKER',
  isConfirmed: true,
};

const coSpeaker: SessionSpeaker = {
  username: 'jane.smith',
  firstName: 'Jane',
  lastName: 'Smith',
  company: 'SwissRe',
  speakerRole: 'CO_SPEAKER',
  isConfirmed: false,
};

function makeSession(speakers: SessionSpeaker[] = []): SessionUI {
  return {
    sessionSlug: 'cloud-talk',
    eventCode: 'BATbern99',
    title: 'Cloud Talk',
    language: 'de',
    speakers,
  };
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('SessionSpeakersTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should_showEmptyState_when_noSpeakersAssigned', () => {
    render(<SessionSpeakersTab session={makeSession([])} />);
    expect(screen.getByText('No speakers assigned yet')).toBeInTheDocument();
  });

  it('should_renderSpeakers_when_sessionHasSpeakers', () => {
    render(<SessionSpeakersTab session={makeSession([primarySpeaker, coSpeaker])} />);
    expect(screen.getByText('John Doe')).toBeInTheDocument();
    expect(screen.getByText('Jane Smith')).toBeInTheDocument();
  });

  it('should_disableSetPrimary_when_speakerIsAlreadyPrimary', () => {
    render(<SessionSpeakersTab session={makeSession([primarySpeaker, coSpeaker])} />);
    // Primary speaker row should not have a Set Primary button
    const speakerRows = screen.getAllByRole('button', { name: 'Set Primary' });
    // Only the co-speaker should have the Set Primary button
    expect(speakerRows).toHaveLength(1);
  });

  it('should_callRemoveSpeaker_when_removeButtonClicked', async () => {
    const user = userEvent.setup();
    render(<SessionSpeakersTab session={makeSession([primarySpeaker, coSpeaker])} />);

    const removeButtons = screen.getAllByTitle('Remove');
    await user.click(removeButtons[0]); // Remove first speaker (John Doe)

    expect(mockRemoveMutateAsync).toHaveBeenCalledWith({
      eventCode: 'BATbern99',
      sessionSlug: 'cloud-talk',
      username: 'john.doe',
    });
  });

  it('should_callRemoveThenAssign_when_setPrimaryClicked', async () => {
    const user = userEvent.setup();
    render(<SessionSpeakersTab session={makeSession([primarySpeaker, coSpeaker])} />);

    const setPrimaryButton = screen.getByTitle('Set Primary');
    await user.click(setPrimaryButton); // Set Jane Smith as primary

    expect(mockRemoveMutateAsync).toHaveBeenCalledWith({
      eventCode: 'BATbern99',
      sessionSlug: 'cloud-talk',
      username: 'jane.smith',
    });
    expect(mockAssignMutateAsync).toHaveBeenCalledWith({
      eventCode: 'BATbern99',
      sessionSlug: 'cloud-talk',
      request: { username: 'jane.smith', speakerRole: 'PRIMARY_SPEAKER' },
    });
  });

  it('should_disableAddButton_when_noUserSelected', () => {
    render(<SessionSpeakersTab session={makeSession([])} />);
    const addButton = screen.getByRole('button', { name: 'Add Speaker' });
    expect(addButton).toBeDisabled();
  });

  it('should_callAssignSpeaker_when_addFormSubmitted', async () => {
    const user = userEvent.setup();
    render(<SessionSpeakersTab session={makeSession([])} />);

    // Select a user via the mocked autocomplete
    await user.click(screen.getByTestId('user-autocomplete'));

    // Add button should be enabled now
    const addButton = screen.getByRole('button', { name: 'Add Speaker' });
    expect(addButton).not.toBeDisabled();

    await user.click(addButton);

    expect(mockAssignMutateAsync).toHaveBeenCalledWith({
      eventCode: 'BATbern99',
      sessionSlug: 'cloud-talk',
      request: { username: 'jane.doe', speakerRole: 'PRIMARY_SPEAKER' }, // no speakers → PRIMARY_SPEAKER default
    });
  });

  it('should_showInlineError_when_assignMutationFails', async () => {
    mockAssignMutateAsync.mockRejectedValueOnce(new Error('User already assigned'));
    const user = userEvent.setup();
    render(<SessionSpeakersTab session={makeSession([])} />);

    await user.click(screen.getByTestId('user-autocomplete'));
    await user.click(screen.getByRole('button', { name: 'Add Speaker' }));

    await waitFor(() => {
      expect(screen.getByText('User already assigned')).toBeInTheDocument();
    });
  });

  it('should_clearForm_when_addSucceeds', async () => {
    const user = userEvent.setup();
    render(<SessionSpeakersTab session={makeSession([])} />);

    await user.click(screen.getByTestId('user-autocomplete'));
    // Verify the selected user preview appears
    expect(screen.getByText('Jane Doe')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Add Speaker' }));

    await waitFor(() => {
      // After success, the selected user preview is gone (form cleared)
      expect(screen.queryByTestId('user-avatar')).not.toBeInTheDocument();
    });
  });
});
