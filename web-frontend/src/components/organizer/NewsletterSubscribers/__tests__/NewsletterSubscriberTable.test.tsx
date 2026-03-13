/**
 * NewsletterSubscriberTable Tests
 * Story 10.28: Newsletter Subscriber Management Page
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { I18nextProvider } from 'react-i18next';
import i18n from '../../../../i18n/config';
import NewsletterSubscriberTable from '../NewsletterSubscriberTable';
import type { components } from '@/types/generated/events-api.types';

type SubscriberResponse = components['schemas']['SubscriberResponse'];

const mockActiveSubscriber: SubscriberResponse = {
  id: 'sub-1',
  email: 'active@example.com',
  firstName: 'Alice',
  language: 'en',
  source: 'website',
  subscribedAt: '2026-01-15T10:00:00Z',
  unsubscribedAt: null,
};

const mockUnsubscribedSubscriber: SubscriberResponse = {
  id: 'sub-2',
  email: 'inactive@example.com',
  firstName: 'Bob',
  language: 'de',
  source: 'import',
  subscribedAt: '2025-12-01T08:00:00Z',
  unsubscribedAt: '2026-02-01T12:00:00Z',
};

const renderComponent = (
  props: Partial<React.ComponentProps<typeof NewsletterSubscriberTable>> = {}
) => {
  const defaultProps = {
    subscribers: [mockActiveSubscriber, mockUnsubscribedSubscriber],
    sortBy: 'subscribedAt',
    sortDir: 'desc' as const,
    onSortChange: vi.fn(),
    onAction: vi.fn(),
    ...props,
  };

  return {
    ...render(
      <I18nextProvider i18n={i18n}>
        <NewsletterSubscriberTable {...defaultProps} />
      </I18nextProvider>
    ),
    props: defaultProps,
  };
};

describe('NewsletterSubscriberTable', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should_renderTable_when_subscribersProvided', () => {
    renderComponent();

    expect(screen.getByTestId('subscriber-table')).toBeInTheDocument();
    expect(screen.getByText('active@example.com')).toBeInTheDocument();
    expect(screen.getByText('inactive@example.com')).toBeInTheDocument();
  });

  it('should_renderEmptyState_when_noSubscribers', () => {
    renderComponent({ subscribers: [] });

    expect(screen.queryByTestId('subscriber-table')).not.toBeInTheDocument();
    expect(screen.getByText(/no subscribers found/i)).toBeInTheDocument();
  });

  it('should_renderColumnHeaders_when_tableShown', () => {
    renderComponent();

    // Use data-testid for sortable columns to avoid ambiguity with data cells
    expect(screen.getByTestId('sort-email')).toBeInTheDocument();
    expect(screen.getByTestId('sort-firstName')).toBeInTheDocument();
    expect(screen.getByTestId('sort-language')).toBeInTheDocument();
    expect(screen.getByTestId('sort-source')).toBeInTheDocument();
    expect(screen.getByTestId('sort-subscribedAt')).toBeInTheDocument();
  });

  it('should_callOnSortChange_when_sortHeaderClicked', async () => {
    const user = userEvent.setup();
    const { props } = renderComponent();

    const emailSort = screen.getByTestId('sort-email');
    await user.click(emailSort);

    expect(props.onSortChange).toHaveBeenCalledWith('email', 'asc');
  });

  it('should_toggleDirection_when_activeSortColumnClicked', async () => {
    const user = userEvent.setup();
    const { props } = renderComponent({ sortBy: 'email', sortDir: 'asc' });

    const emailSort = screen.getByTestId('sort-email');
    await user.click(emailSort);

    expect(props.onSortChange).toHaveBeenCalledWith('email', 'desc');
  });

  it('should_showUnsubscribeAndDelete_when_activeSubscriberMenuOpened', async () => {
    const user = userEvent.setup();
    const { props } = renderComponent();

    const menuButton = screen.getByTestId('actions-sub-1');
    await user.click(menuButton);

    expect(screen.getByTestId('action-unsubscribe')).toBeInTheDocument();
    expect(screen.getByTestId('action-delete')).toBeInTheDocument();
    expect(screen.queryByTestId('action-resubscribe')).not.toBeInTheDocument();
  });

  it('should_showResubscribeAndDelete_when_unsubscribedSubscriberMenuOpened', async () => {
    const user = userEvent.setup();
    const { props } = renderComponent();

    const menuButton = screen.getByTestId('actions-sub-2');
    await user.click(menuButton);

    expect(screen.getByTestId('action-resubscribe')).toBeInTheDocument();
    expect(screen.getByTestId('action-delete')).toBeInTheDocument();
    expect(screen.queryByTestId('action-unsubscribe')).not.toBeInTheDocument();
  });

  it('should_callOnAction_when_menuItemClicked', async () => {
    const user = userEvent.setup();
    const { props } = renderComponent();

    const menuButton = screen.getByTestId('actions-sub-1');
    await user.click(menuButton);

    const unsubscribeItem = screen.getByTestId('action-unsubscribe');
    await user.click(unsubscribeItem);

    expect(props.onAction).toHaveBeenCalledWith('unsubscribe', mockActiveSubscriber);
  });

  it('should_displayStatusChips_when_subscribersRendered', () => {
    renderComponent();

    const chips = screen.getAllByText(/active|unsubscribed/i);
    expect(chips.length).toBeGreaterThanOrEqual(2);
  });
});
