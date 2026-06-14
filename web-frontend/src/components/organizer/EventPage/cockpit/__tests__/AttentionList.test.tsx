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

vi.mock('@/components/shared/OrganizerSelect', () => ({
  useOrganizers: () => ({
    organizers: [{ id: 'sandra.keller', name: 'Sandra Keller' }],
    isLoading: false,
  }),
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

  it('renders open cards and deep-links by clicking the whole card', () => {
    const { onNavigate } = renderList({ cards: [card] });
    // The whole card is the button — clicking it navigates (no nested button).
    fireEvent.click(screen.getByTestId('cockpit-attention-card'));
    expect(onNavigate).toHaveBeenCalledWith({ kind: 'tab', tab: 'speakers', view: 'slots' });
  });

  it('shows the assignee avatar on a task-backed card', () => {
    const taskCard: CockpitCard = {
      id: 'task:42',
      labelKey: '',
      labelVars: { name: 'Confirm caterer headcount' },
      target: { kind: 'tab', tab: 'communications' },
      severity: 'overdue',
      dueDays: -2,
      assignee: 'sandra.keller',
      taskBacked: true,
    };
    renderList({ cards: [taskCard] });
    // Username resolves to the organizer's display name (id === username), like the kanban chip.
    const chip = screen.getByTestId('cockpit-attention-assignee-task:42');
    expect(chip).toHaveTextContent('Sandra Keller');
    expect(chip).toHaveTextContent('SK'); // avatar initials
  });

  it('lays cards out in a responsive auto-fill grid, not one per row (UX-DR16, prototype .attn)', () => {
    renderList({ cards: [card] });
    const grid = screen.getByTestId('cockpit-attention-grid');
    expect(grid.style.display).toBe('grid');
    expect(grid.style.gridTemplateColumns).toBe('repeat(auto-fill, minmax(290px, 1fr))');
    // the card lives inside the grid container
    expect(grid).toContainElement(screen.getByTestId('cockpit-attention-card'));
  });

  it('opens the CustomTaskModal from "Add task"', () => {
    renderList({ cards: [] });
    expect(screen.queryByTestId('custom-task-modal-mock')).not.toBeInTheDocument();
    fireEvent.click(screen.getByTestId('cockpit-add-task'));
    expect(screen.getByTestId('custom-task-modal-mock')).toBeInTheDocument();
  });
});
