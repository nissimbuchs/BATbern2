/**
 * AttentionList tests (Story 14.B.2 / FR9–FR11).
 */
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { I18nextProvider } from 'react-i18next';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import i18n from '@/i18n/config';
import { AttentionList } from '../AttentionList';
import type { CockpitCard } from '../cockpitCards';

vi.mock('@/components/organizer/Tasks/CustomTaskModal', () => ({
  CustomTaskModal: ({ open }: { open: boolean }) =>
    open ? <div data-testid="custom-task-modal-mock" /> : null,
}));

const renderList = (props: Partial<React.ComponentProps<typeof AttentionList>> = {}) => {
  const onNavigate = vi.fn();
  const onRetry = vi.fn();
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <I18nextProvider i18n={i18n}>
        <AttentionList
          cards={[]}
          isLoading={false}
          isError={false}
          onRetry={onRetry}
          onNavigate={onNavigate}
          eventCode="BAT54"
          organizerUsername="john.doe"
          {...props}
        />
      </I18nextProvider>
    </QueryClientProvider>
  );
  return { onNavigate, onRetry };
};

const card: CockpitCard = {
  id: 'needs-slot',
  labelKey: 'needsSlot',
  labelVars: { count: 2 },
  target: { kind: 'tab', tab: 'speakers', view: 'slots' },
  severity: 'dueSoon',
};

describe('AttentionList', () => {
  it('shows the friendly empty state when no cards are open', () => {
    renderList({ cards: [] });
    expect(screen.getByTestId('cockpit-attention-empty')).toBeInTheDocument();
  });

  it('shows an inline error + retry when the task fetch fails', () => {
    const { onRetry } = renderList({ isError: true });
    fireEvent.click(screen.getByTestId('cockpit-attention-retry'));
    expect(onRetry).toHaveBeenCalledOnce();
    expect(screen.queryByTestId('cockpit-attention-empty')).not.toBeInTheDocument();
  });

  it('renders open cards and deep-links via their button', () => {
    const { onNavigate } = renderList({ cards: [card] });
    expect(screen.getByTestId('cockpit-attention-card')).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('cockpit-attention-deeplink-needs-slot'));
    expect(onNavigate).toHaveBeenCalledWith({ kind: 'tab', tab: 'speakers', view: 'slots' });
  });

  it('opens the CustomTaskModal from "Add task"', () => {
    renderList({ cards: [] });
    expect(screen.queryByTestId('custom-task-modal-mock')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId('cockpit-add-task'));
    expect(screen.getByTestId('custom-task-modal-mock')).toBeInTheDocument();
  });
});
