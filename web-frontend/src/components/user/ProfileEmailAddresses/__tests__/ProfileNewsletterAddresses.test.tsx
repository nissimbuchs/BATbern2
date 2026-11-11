/**
 * #1244 — the newsletter per address (owner decision 2026-11-07): one checkbox for the primary
 * and each confirmed additional address. The real hooks and service against the MSW harness
 * (4.10c: `PATCH /newsletter/my-subscriptions`, partial), real i18next with `en`.
 */

import { describe, it, expect } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { api } from '@/test/msw/api';
import { ProfileNewsletterAddresses } from '../ProfileNewsletterAddresses';

const before = {
  addresses: [
    { email: 'work@example.com', primary: true, subscribed: true, language: 'de' },
    { email: 'private@example.com', primary: false, subscribed: false, language: null },
    { email: 'old@example.com', primary: false, subscribed: false, language: 'en' },
  ],
};

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
  it('shows one checkbox per address with its state', async () => {
    api.on('getMyNewsletterSubscriptions', () => before);
    renderSection();

    expect(await screen.findByTestId('newsletter-address-work@example.com')).toBeChecked();
    expect(screen.getByTestId('newsletter-address-private@example.com')).not.toBeChecked();
  });

  it('subscribes exactly the clicked address, with the UI language for a new subscription', async () => {
    api.on('getMyNewsletterSubscriptions', () => before);
    api.on('patchMyNewsletterSubscriptions', () => ({
      addresses: [
        before.addresses[0],
        { email: 'private@example.com', primary: false, subscribed: true, language: 'en' },
        before.addresses[2],
      ],
    }));
    const user = userEvent.setup();
    renderSection();

    await user.click(await screen.findByTestId('newsletter-address-private@example.com'));

    await waitFor(() => expect(api.calls('patchMyNewsletterSubscriptions')).toHaveLength(1));
    expect(api.calls('patchMyNewsletterSubscriptions')[0].body).toEqual({
      email: 'private@example.com',
      subscribed: true,
      language: 'en',
    });
    await waitFor(() =>
      expect(screen.getByTestId('newsletter-address-private@example.com')).toBeChecked()
    );
  });

  it('keeps the stored language when an address that had one is subscribed again (partial)', async () => {
    api.on('getMyNewsletterSubscriptions', () => before);
    api.on('patchMyNewsletterSubscriptions', () => before);
    const user = userEvent.setup();
    renderSection();

    await user.click(await screen.findByTestId('newsletter-address-old@example.com'));

    await waitFor(() => expect(api.calls('patchMyNewsletterSubscriptions')).toHaveLength(1));
    expect(api.calls('patchMyNewsletterSubscriptions')[0].body).toEqual({
      email: 'old@example.com',
      subscribed: true,
    });
  });
});
