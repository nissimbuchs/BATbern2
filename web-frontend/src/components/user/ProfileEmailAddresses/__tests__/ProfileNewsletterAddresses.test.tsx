/**
 * #1244 — the newsletter per address (owner decision 2026-11-07): one checkbox for the primary
 * and each confirmed additional address.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'de' } }),
}));

vi.mock('@/services/newsletterService', () => ({
  getMySubscriptions: vi.fn(),
  putMySubscription: vi.fn(),
}));

import { getMySubscriptions, putMySubscription } from '@/services/newsletterService';
import { ProfileNewsletterAddresses } from '../ProfileNewsletterAddresses';

function renderSection() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ProfileNewsletterAddresses />
    </QueryClientProvider>
  );
}

describe('ProfileNewsletterAddresses', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getMySubscriptions).mockResolvedValue({
      addresses: [
        { email: 'work@example.com', primary: true, subscribed: true },
        { email: 'private@example.com', primary: false, subscribed: false },
      ],
    });
  });

  it('shows one checkbox per address with its state', async () => {
    renderSection();

    expect(await screen.findByTestId('newsletter-address-work@example.com')).toBeChecked();
    expect(screen.getByTestId('newsletter-address-private@example.com')).not.toBeChecked();
  });

  it('subscribes exactly the clicked address', async () => {
    vi.mocked(putMySubscription).mockResolvedValue({
      addresses: [
        { email: 'work@example.com', primary: true, subscribed: true },
        { email: 'private@example.com', primary: false, subscribed: true },
      ],
    });
    const user = userEvent.setup();
    renderSection();

    await user.click(await screen.findByTestId('newsletter-address-private@example.com'));

    await waitFor(() =>
      expect(putMySubscription).toHaveBeenCalledWith('private@example.com', true, 'de')
    );
  });
});
