/**
 * NewsletterSubscriberTable Tests
 * Story 10.28: Newsletter Subscriber Management Page
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { I18nextProvider } from 'react-i18next';
import i18n from '../../../../i18n/config';
import NewsletterSubscriberTable from '../NewsletterSubscriberTable';
import type { components } from '@/types/generated/event-newsletter-api.types';

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

const mockSuppressedSubscriber: SubscriberResponse = {
  id: 'sub-3',
  email: 'bounced@example.com',
  firstName: 'Carol',
  language: 'en',
  source: 'website',
  subscribedAt: '2025-11-01T08:00:00Z',
  unsubscribedAt: null,
  suppressedAt: '2026-03-01T08:00:00Z',
  bounceType: 'Permanent',
  bounceCount: 3,
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

/** Concatenate every emotion <style> rule that targets the element's css-* class. */
const cssForElement = (el: HTMLElement): string => {
  const cssClass = Array.from(el.classList).find((c) => c.startsWith('css-'));
  if (!cssClass) return '';
  let combined = '';
  document.querySelectorAll('style').forEach((styleEl) => {
    const css = styleEl.textContent ?? '';
    if (css.includes(`.${cssClass}`)) combined += css + '\n';
  });
  return combined;
};

/**
 * True when the element collapses to display:none at the xs base breakpoint.
 * MUI compiles `display: { xs: 'none', sm: 'table-cell' }` into per-breakpoint
 * `@media (min-width:…)` rules; the xs value lands behind `@media (min-width:0px)`,
 * which jsdom never applies — so we inspect the injected stylesheet directly.
 */
const isHiddenAtXs = (el: HTMLElement): boolean => {
  const css = cssForElement(el);
  return /@media\s*\(min-width:\s*0px\)\s*\{[^}]*display:\s*none[^}]*\}/.test(css);
};

describe('NewsletterSubscriberTable', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should_hideLanguageSourceAndSubscribedAtColumns_when_xsViewport', () => {
    renderComponent();

    const langHeader = screen.getByTestId('sort-language').closest('th') as HTMLElement;
    const sourceHeader = screen.getByTestId('sort-source').closest('th') as HTMLElement;
    const subscribedAtHeader = screen.getByTestId('sort-subscribedAt').closest('th') as HTMLElement;
    const emailHeader = screen.getByTestId('sort-email').closest('th') as HTMLElement;

    // Language / Source / SubscribedAt collapse to display:none at the xs base breakpoint…
    expect(isHiddenAtXs(langHeader)).toBe(true);
    expect(isHiddenAtXs(sourceHeader)).toBe(true);
    expect(isHiddenAtXs(subscribedAtHeader)).toBe(true);
    // …while the Email column stays visible.
    expect(isHiddenAtXs(emailHeader)).toBe(false);
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

  it('should_showVisibleUnsubscribeAndDeleteButtons_when_activeSubscriber', () => {
    renderComponent();

    // No kebab menu anywhere.
    expect(screen.queryByRole('menu')).not.toBeInTheDocument();

    // Visible, status-conditional buttons (no clicking to reveal them).
    expect(screen.getByTestId('action-unsubscribe-sub-1')).toBeInTheDocument();
    expect(screen.getByTestId('action-delete-sub-1')).toBeInTheDocument();
    expect(screen.queryByTestId('action-resubscribe-sub-1')).not.toBeInTheDocument();
    expect(screen.queryByTestId('action-unsuppress-sub-1')).not.toBeInTheDocument();
  });

  it('should_showVisibleResubscribeAndDeleteButtons_when_unsubscribedSubscriber', () => {
    renderComponent();

    expect(screen.getByTestId('action-resubscribe-sub-2')).toBeInTheDocument();
    expect(screen.getByTestId('action-delete-sub-2')).toBeInTheDocument();
    expect(screen.queryByTestId('action-unsubscribe-sub-2')).not.toBeInTheDocument();
    expect(screen.queryByTestId('action-unsuppress-sub-2')).not.toBeInTheDocument();
  });

  it('should_callOnAction_when_visibleActionButtonClicked', async () => {
    const user = userEvent.setup();
    const { props } = renderComponent();

    await user.click(screen.getByTestId('action-unsubscribe-sub-1'));

    expect(props.onAction).toHaveBeenCalledWith('unsubscribe', mockActiveSubscriber);
  });

  it('should_displayStatusChips_when_subscribersRendered', () => {
    renderComponent();

    const chips = screen.getAllByText(/active|unsubscribed/i);
    expect(chips.length).toBeGreaterThanOrEqual(2);
  });

  it('should_renderSuppressedChip_when_subscriberHasSuppressedAt', () => {
    renderComponent({
      subscribers: [mockActiveSubscriber, mockSuppressedSubscriber],
    });

    expect(screen.getByTestId('suppressed-chip-sub-3')).toBeInTheDocument();
  });

  it('should_showVisibleUnsuppressButton_when_suppressedSubscriber', () => {
    renderComponent({
      subscribers: [mockActiveSubscriber, mockSuppressedSubscriber],
    });

    expect(screen.getByTestId('action-unsuppress-sub-3')).toBeInTheDocument();
    expect(screen.getByTestId('action-delete-sub-3')).toBeInTheDocument();
    expect(screen.queryByTestId('action-unsubscribe-sub-3')).not.toBeInTheDocument();
    expect(screen.queryByTestId('action-resubscribe-sub-3')).not.toBeInTheDocument();
  });

  describe('Mobile card view', () => {
    const originalMatchMedia = window.matchMedia;

    const installMobileMatchMedia = (viewportWidth: number) => {
      window.matchMedia = vi.fn((query: string) => {
        const maxMatch = /max-width:\s*([\d.]+)px/.exec(query);
        const minMatch = /min-width:\s*([\d.]+)px/.exec(query);
        let matches = false;
        if (maxMatch) matches = viewportWidth <= parseFloat(maxMatch[1]);
        else if (minMatch) matches = viewportWidth >= parseFloat(minMatch[1]);
        return {
          matches,
          media: query,
          onchange: null,
          addListener: vi.fn(),
          removeListener: vi.fn(),
          addEventListener: vi.fn(),
          removeEventListener: vi.fn(),
          dispatchEvent: vi.fn(),
        };
      }) as unknown as typeof window.matchMedia;
    };

    afterEach(() => {
      window.matchMedia = originalMatchMedia;
    });

    it('should_renderCards_when_mobileViewport', () => {
      installMobileMatchMedia(375);
      renderComponent({ subscribers: [mockActiveSubscriber, mockSuppressedSubscriber] });

      expect(screen.getByTestId('subscriber-cards')).toBeInTheDocument();
      expect(screen.queryByRole('table')).not.toBeInTheDocument();
      expect(screen.getByTestId('subscriber-card-sub-1')).toBeInTheDocument();
      expect(screen.getByText('active@example.com')).toBeInTheDocument();
      // Suppressed-state chip still rendered inside the card.
      expect(screen.getByTestId('suppressed-chip-sub-3')).toBeInTheDocument();
    });

    it('should_renderTable_when_desktopViewport', () => {
      installMobileMatchMedia(1280);
      renderComponent();

      expect(screen.getByRole('table')).toBeInTheDocument();
      expect(screen.queryByTestId('subscriber-cards')).not.toBeInTheDocument();
    });

    it('should_showVisibleActionButtonsInCard_when_mobileViewport', async () => {
      installMobileMatchMedia(375);
      const user = userEvent.setup();
      const { props } = renderComponent({
        subscribers: [mockActiveSubscriber, mockSuppressedSubscriber],
      });

      // No menu in the card view either.
      expect(screen.queryByRole('menu')).not.toBeInTheDocument();

      // Active subscriber: unsubscribe + delete visible directly inside the card.
      expect(screen.getByTestId('action-unsubscribe-sub-1')).toBeInTheDocument();
      expect(screen.getByTestId('action-delete-sub-1')).toBeInTheDocument();
      // Suppressed subscriber: unsuppress + delete.
      expect(screen.getByTestId('action-unsuppress-sub-3')).toBeInTheDocument();
      expect(screen.getByTestId('action-delete-sub-3')).toBeInTheDocument();

      await user.click(screen.getByTestId('action-unsuppress-sub-3'));
      expect(props.onAction).toHaveBeenCalledWith('unsuppress', mockSuppressedSubscriber);
    });
  });
});
