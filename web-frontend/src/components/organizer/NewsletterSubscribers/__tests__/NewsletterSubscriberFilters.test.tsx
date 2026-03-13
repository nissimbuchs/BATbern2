/**
 * NewsletterSubscriberFilters Tests
 * Story 10.28: Newsletter Subscriber Management Page
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { I18nextProvider } from 'react-i18next';
import i18n from '../../../../i18n/config';
import NewsletterSubscriberFilters from '../NewsletterSubscriberFilters';
import { useNewsletterSubscriberStore } from '@/stores/newsletterSubscriberStore';
import { act } from '@testing-library/react';

const renderComponent = () => {
  return render(
    <I18nextProvider i18n={i18n}>
      <NewsletterSubscriberFilters />
    </I18nextProvider>
  );
};

describe('NewsletterSubscriberFilters', () => {
  beforeEach(() => {
    const { reset } = useNewsletterSubscriberStore.getState();
    act(() => {
      reset();
    });
  });

  it('should_renderSearchInput_when_mounted', () => {
    renderComponent();

    expect(screen.getByTestId('subscriber-search-input')).toBeInTheDocument();
  });

  it('should_renderStatusRadios_when_mounted', () => {
    renderComponent();

    expect(screen.getByLabelText(/all/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/active/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/unsubscribed/i)).toBeInTheDocument();
  });

  it('should_renderClearButton_when_mounted', () => {
    renderComponent();

    expect(screen.getByTestId('subscriber-clear-filters')).toBeInTheDocument();
  });

  it('should_updateStoreSearchQuery_when_userTypesWithDebounce', async () => {
    const user = userEvent.setup();
    renderComponent();

    const searchInput = screen.getByTestId('subscriber-search-input').querySelector('input')!;
    await user.type(searchInput, 'test@example.com');

    // Debounced — wait for 300ms
    await waitFor(
      () => {
        const { filters } = useNewsletterSubscriberStore.getState();
        expect(filters.searchQuery).toBe('test@example.com');
      },
      { timeout: 500 }
    );
  });

  it('should_updateStatusFilter_when_radioClicked', async () => {
    const user = userEvent.setup();
    renderComponent();

    const activeRadio = screen.getByLabelText(/active/i);
    await user.click(activeRadio);

    const { filters } = useNewsletterSubscriberStore.getState();
    expect(filters.status).toBe('active');
  });

  it('should_resetFilters_when_clearButtonClicked', async () => {
    const user = userEvent.setup();

    // Set some filters first
    act(() => {
      useNewsletterSubscriberStore.getState().setFilters({ status: 'active' });
      useNewsletterSubscriberStore.getState().setSearchQuery('test');
    });

    renderComponent();

    const clearButton = screen.getByTestId('subscriber-clear-filters');
    await user.click(clearButton);

    const { filters } = useNewsletterSubscriberStore.getState();
    expect(filters.status).toBe('all');
    expect(filters.searchQuery).toBe('');
  });
});
