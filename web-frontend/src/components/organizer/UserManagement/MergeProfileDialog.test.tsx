/**
 * #1183 C — organizer merge dialog: pick, preview, confirm; swap when the picked profile has the login.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { I18nextProvider } from 'react-i18next';
import i18n from '../../../i18n/config';
import { MergeProfileDialog } from './MergeProfileDialog';
import * as api from '@/services/api/userManagementApi';

vi.mock('@/services/api/userManagementApi', () => ({
  getUserMergePreview: vi.fn(),
  mergeUserProfile: vi.fn(),
}));

// The real autocomplete searches the API; here one click picks the other profile.
vi.mock('@/components/shared/UserAutocomplete', () => ({
  UserAutocomplete: ({ onChange }: { onChange: (u: unknown) => void }) => (
    <button
      data-testid="pick"
      onClick={() =>
        onChange({
          id: 'anna.muster',
          firstName: 'Anna',
          lastName: 'Muster',
          email: 'a@example.com',
        })
      }
    >
      pick
    </button>
  ),
}));

const navigate = vi.fn();
vi.mock('react-router-dom', async (orig) => ({
  ...(await orig<typeof import('react-router-dom')>()),
  useNavigate: () => navigate,
}));

const summary = (username: string, hasLogin: boolean) => ({
  username,
  firstName: 'Anna',
  lastName: 'Muster',
  email: `${username}@example.com`,
  hasLogin,
  roles: ['ATTENDEE'],
  additionalEmails: [],
});

const preview = (canMerge: boolean, reasons: string[] = []) => ({
  target: summary('anna.muster.2', true),
  source: summary('anna.muster', false),
  moves: { 'registrations.attendee_username': 3, 'notifications.recipient_username': 0 },
  conflicts: { registrations: 1 },
  blockers: [],
  canMerge,
  reasons,
});

const renderDialog = (username = 'anna.muster.2') =>
  render(
    <QueryClientProvider
      client={
        new QueryClient({
          defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
        })
      }
    >
      <I18nextProvider i18n={i18n}>
        <MemoryRouter>
          <MergeProfileDialog open onClose={vi.fn()} username={username} />
        </MemoryRouter>
      </I18nextProvider>
    </QueryClientProvider>
  );

describe('MergeProfileDialog (#1183 C)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should_previewAndMerge_when_otherProfilePicked', async () => {
    vi.mocked(api.getUserMergePreview).mockResolvedValue(preview(true));
    vi.mocked(api.mergeUserProfile).mockResolvedValue({
      targetUsername: 'anna.muster.2',
      sourceUsername: 'anna.muster',
      moved: {},
      removed: {},
    });
    renderDialog();

    await userEvent.click(screen.getByTestId('pick'));

    expect(await screen.findByTestId('merge-source')).toHaveTextContent('anna.muster');
    expect(api.getUserMergePreview).toHaveBeenCalledWith('anna.muster.2', 'anna.muster');
    // rows with 0 are not listed
    expect(screen.getByTestId('merge-moves').querySelectorAll('li')).toHaveLength(1);
    expect(screen.getByTestId('merge-conflicts')).toBeInTheDocument();

    await userEvent.click(screen.getByTestId('merge-confirm'));

    expect(await screen.findByTestId('merge-success')).toBeInTheDocument();
    expect(api.mergeUserProfile).toHaveBeenCalledWith('anna.muster.2', 'anna.muster');
    expect(navigate).not.toHaveBeenCalled();
    // the merged-away profile no longer exists: its preview must not be fetched again (404 in prod)
    await new Promise((r) => setTimeout(r, 50));
    expect(api.getUserMergePreview).toHaveBeenCalledTimes(1);
  });

  it('should_disableConfirm_and_offerSwap_when_pickedProfileIsTheOneWithoutLogin', async () => {
    // page of the guest profile; the picked one has the login
    vi.mocked(api.getUserMergePreview).mockResolvedValueOnce(preview(false, ['SOURCE_HAS_LOGIN']));
    vi.mocked(api.getUserMergePreview).mockResolvedValueOnce(preview(true));
    vi.mocked(api.mergeUserProfile).mockResolvedValue({
      targetUsername: 'anna.muster',
      sourceUsername: 'anna.muster.2',
      moved: {},
      removed: {},
    });
    renderDialog('anna.muster.2');

    await userEvent.click(screen.getByTestId('pick'));
    await screen.findByTestId('merge-source');
    expect(screen.getByTestId('merge-confirm')).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: /swap|tauschen|merge\.swap/i }));

    await waitFor(() =>
      expect(api.getUserMergePreview).toHaveBeenLastCalledWith('anna.muster', 'anna.muster.2')
    );
    await waitFor(() => expect(screen.getByTestId('merge-confirm')).toBeEnabled());
    await userEvent.click(screen.getByTestId('merge-confirm'));
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/organizer/users/anna.muster'));
  });

  it('should_previewThePresetPair_without_picking_and_stayOnPage_when_openedFromDuplicates', async () => {
    vi.mocked(api.getUserMergePreview).mockResolvedValue(preview(true));
    vi.mocked(api.mergeUserProfile).mockResolvedValue({
      targetUsername: 'anna.muster.2',
      sourceUsername: 'anna.muster',
      moved: {},
      removed: {},
    });
    const onMerged = vi.fn();
    render(
      <QueryClientProvider
        client={
          new QueryClient({
            defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
          })
        }
      >
        <I18nextProvider i18n={i18n}>
          <MemoryRouter>
            <MergeProfileDialog
              open
              onClose={vi.fn()}
              username="anna.muster.2"
              otherUsername="anna.muster"
              stayOnPage
              onMerged={onMerged}
            />
          </MemoryRouter>
        </I18nextProvider>
      </QueryClientProvider>
    );

    expect(screen.queryByTestId('pick')).not.toBeInTheDocument();
    expect(await screen.findByTestId('merge-source')).toHaveTextContent('anna.muster');
    expect(api.getUserMergePreview).toHaveBeenCalledWith('anna.muster.2', 'anna.muster');

    // swapping still works, and no navigation happens afterwards
    vi.mocked(api.getUserMergePreview).mockResolvedValue(preview(true));
    await userEvent.click(screen.getByTestId('merge-confirm'));
    await waitFor(() => expect(onMerged).toHaveBeenCalled());
    expect(navigate).not.toHaveBeenCalled();
  });
});
