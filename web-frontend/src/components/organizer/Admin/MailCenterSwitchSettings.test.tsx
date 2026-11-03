/**
 * MailCenterSwitchSettings tests (Story 16.6 AC7): the switch `mailcenter.notify-instead-of-forward`
 * in Admin → Settings. One checkbox per alias group; saving writes the checked groups, nothing
 * checked writes an empty value (forwarding as before).
 */
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { MailCenterSwitchSettings } from './MailCenterSwitchSettings';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { changeLanguage: vi.fn() },
  }),
}));

const mockGetAdminSetting = vi.fn();
const mockUpdateAdminSetting = vi.fn();

vi.mock('@/services/adminSettingsService', () => ({
  getAdminSetting: (...args: unknown[]) => mockGetAdminSetting(...args),
  updateAdminSetting: (...args: unknown[]) => mockUpdateAdminSetting(...args),
}));

const KEY = 'mailcenter.notify-instead-of-forward';

const renderIt = () =>
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <MailCenterSwitchSettings />
    </QueryClientProvider>
  );

describe('MailCenterSwitchSettings', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUpdateAdminSetting.mockImplementation((key: string, value: string) =>
      Promise.resolve({ key, value })
    );
  });

  it('should show every group off when the setting is missing', async () => {
    mockGetAdminSetting.mockResolvedValue({ key: KEY, value: null });
    renderIt();

    for (const group of ['moderator', 'replies', 'contact']) {
      expect(await screen.findByTestId(`mail-switch-${group}`)).not.toBeChecked();
    }
    expect(mockGetAdminSetting).toHaveBeenCalledWith(KEY);
  });

  it('should read the stored value like the service does: case and separators do not matter', async () => {
    mockGetAdminSetting.mockResolvedValue({ key: KEY, value: ' Moderator,contact  ' });
    renderIt();

    expect(await screen.findByTestId('mail-switch-moderator')).toBeChecked();
    expect(screen.getByTestId('mail-switch-replies')).not.toBeChecked();
    expect(screen.getByTestId('mail-switch-contact')).toBeChecked();
  });

  it('should save the checked groups', async () => {
    mockGetAdminSetting.mockResolvedValue({ key: KEY, value: '' });
    renderIt();

    await userEvent.click(await screen.findByTestId('mail-switch-moderator'));
    await userEvent.click(screen.getByTestId('mail-switch-save'));

    await waitFor(() => expect(mockUpdateAdminSetting).toHaveBeenCalledWith(KEY, 'moderator'));
    expect(await screen.findByText('settings.saved')).toBeInTheDocument();
  });

  it('should save an empty value when every group is switched off', async () => {
    mockGetAdminSetting.mockResolvedValue({ key: KEY, value: 'moderator replies' });
    renderIt();

    await userEvent.click(await screen.findByTestId('mail-switch-moderator'));
    await userEvent.click(screen.getByTestId('mail-switch-replies'));
    await userEvent.click(screen.getByTestId('mail-switch-save'));

    await waitFor(() => expect(mockUpdateAdminSetting).toHaveBeenCalledWith(KEY, ''));
  });

  it('should keep the groups in a fixed order', async () => {
    mockGetAdminSetting.mockResolvedValue({ key: KEY, value: 'contact' });
    renderIt();

    await userEvent.click(await screen.findByTestId('mail-switch-moderator'));
    await userEvent.click(screen.getByTestId('mail-switch-save'));

    await waitFor(() =>
      expect(mockUpdateAdminSetting).toHaveBeenCalledWith(KEY, 'moderator,contact')
    );
  });

  it('should keep save disabled until something changed', async () => {
    mockGetAdminSetting.mockResolvedValue({ key: KEY, value: 'moderator' });
    renderIt();

    expect(await screen.findByTestId('mail-switch-save')).toBeDisabled();
    await userEvent.click(screen.getByTestId('mail-switch-replies'));
    expect(screen.getByTestId('mail-switch-save')).toBeEnabled();
  });

  it('should say so when saving fails', async () => {
    mockGetAdminSetting.mockResolvedValue({ key: KEY, value: '' });
    mockUpdateAdminSetting.mockRejectedValue(new Error('403'));
    renderIt();

    await userEvent.click(await screen.findByTestId('mail-switch-contact'));
    await userEvent.click(screen.getByTestId('mail-switch-save'));

    expect(await screen.findByText('settings.saveFailed')).toBeInTheDocument();
  });
});
