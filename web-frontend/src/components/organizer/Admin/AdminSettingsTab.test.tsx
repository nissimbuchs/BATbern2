/**
 * AdminSettingsTab Tests (Story 10.26 - T3.7)
 *
 * Tests:
 * - Renders Email Forwarding section with text field
 * - Loads current setting value on mount
 * - Save button calls API with correct value
 * - Shows success/error toast
 * - Loading state
 */

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { AdminSettingsTab } from './AdminSettingsTab';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, fallback?: string) => fallback ?? key,
    i18n: { changeLanguage: vi.fn() },
  }),
}));

const mockGetAdminSetting = vi.fn();
const mockUpdateAdminSetting = vi.fn();

vi.mock('@/services/adminSettingsService', () => ({
  getAdminSetting: (...args: unknown[]) => mockGetAdminSetting(...args),
  updateAdminSetting: (...args: unknown[]) => mockUpdateAdminSetting(...args),
}));

const createQueryClient = () => new QueryClient({ defaultOptions: { queries: { retry: false } } });

const renderTab = () =>
  render(
    <QueryClientProvider client={createQueryClient()}>
      <AdminSettingsTab />
    </QueryClientProvider>
  );

describe('AdminSettingsTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetAdminSetting.mockResolvedValue({
      key: 'email-forwarding.support-contacts',
      value: 'alice@test.ch, bob@test.ch',
    });
    mockUpdateAdminSetting.mockResolvedValue({
      key: 'email-forwarding.support-contacts',
      value: 'alice@test.ch, bob@test.ch',
    });
  });

  it('should render email forwarding section title', async () => {
    renderTab();
    await waitFor(() => {
      expect(screen.getByText('Email Forwarding')).toBeInTheDocument();
    });
  });

  it('should load current setting value on mount', async () => {
    renderTab();
    await waitFor(() => {
      const input = screen.getByLabelText('support contacts');
      expect(input).toHaveValue('alice@test.ch, bob@test.ch');
    });
    expect(mockGetAdminSetting).toHaveBeenCalledWith('email-forwarding.support-contacts');
  });

  it('should render empty value when no setting exists', async () => {
    mockGetAdminSetting.mockResolvedValue({
      key: 'email-forwarding.support-contacts',
      value: null,
    });
    renderTab();
    await waitFor(() => {
      const input = screen.getByLabelText('support contacts');
      expect(input).toHaveValue('');
    });
  });

  it('should call API on save with current input value', async () => {
    const user = userEvent.setup();
    renderTab();

    await waitFor(() => {
      expect(screen.getByLabelText('support contacts')).toHaveValue('alice@test.ch, bob@test.ch');
    });

    const input = screen.getByLabelText('support contacts');
    await user.clear(input);
    await user.type(input, 'new@test.ch');

    const saveButton = screen.getByRole('button', { name: /save/i });
    await user.click(saveButton);

    await waitFor(() => {
      expect(mockUpdateAdminSetting).toHaveBeenCalledWith(
        'email-forwarding.support-contacts',
        'new@test.ch'
      );
    });
  });

  it('should show success toast on successful save', async () => {
    const user = userEvent.setup();
    renderTab();

    await waitFor(() => {
      expect(screen.getByLabelText('support contacts')).toBeInTheDocument();
    });

    const saveButton = screen.getByRole('button', { name: /save/i });
    await user.click(saveButton);

    await waitFor(() => {
      expect(screen.getByText('Settings saved.')).toBeInTheDocument();
    });
  });

  it('should show error toast on save failure', async () => {
    mockUpdateAdminSetting.mockRejectedValue(new Error('Network error'));
    const user = userEvent.setup();
    renderTab();

    await waitFor(() => {
      expect(screen.getByLabelText('support contacts')).toBeInTheDocument();
    });

    const saveButton = screen.getByRole('button', { name: /save/i });
    await user.click(saveButton);

    await waitFor(() => {
      expect(screen.getByText('Save failed. Please try again.')).toBeInTheDocument();
    });
  });

  it('should show loading spinner while fetching', () => {
    mockGetAdminSetting.mockReturnValue(new Promise(() => {})); // Never resolves
    renderTab();
    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });
});
