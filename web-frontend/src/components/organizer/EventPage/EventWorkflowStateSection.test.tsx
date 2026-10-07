/**
 * EventWorkflowStateSection tests.
 *
 * Restores the organizer's manual event-state change with validation override, which PR #788
 * (lifecycle-aware event page redesign) left unreachable from the event page.
 */

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { EventWorkflowStateSection } from './EventWorkflowStateSection';

const mockTransition = vi.fn();

vi.mock('@/services/workflowService', () => ({
  workflowService: {
    transitionWorkflowState: (...args: unknown[]) => mockTransition(...args),
  },
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, fallback?: string | Record<string, unknown>) =>
      typeof fallback === 'string' ? fallback : key,
  }),
}));

const renderSection = (workflowState = 'SLOT_ASSIGNMENT') => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
  render(
    <QueryClientProvider client={queryClient}>
      <EventWorkflowStateSection eventCode="BATbern60" workflowState={workflowState} />
    </QueryClientProvider>
  );
  return { invalidateSpy };
};

const chooseState = async (stateTestId: string) => {
  const user = userEvent.setup();
  await user.click(screen.getByRole('combobox'));
  await user.click(await screen.findByTestId(stateTestId));
  return user;
};

describe('EventWorkflowStateSection', () => {
  beforeEach(() => {
    mockTransition.mockReset();
  });

  it('should show the current state when rendered', () => {
    renderSection('SLOT_ASSIGNMENT');

    expect(screen.getByTestId('workflow-state-section')).toBeInTheDocument();
    expect(screen.getByRole('combobox')).toHaveTextContent('workflow.states.slot_assignment');
  });

  it('should keep the apply button disabled when the selected state equals the current state', () => {
    renderSection('SLOT_ASSIGNMENT');

    expect(screen.getByTestId('apply-workflow-state-button')).toBeDisabled();
  });

  it('should transition without override when a different state is applied', async () => {
    mockTransition.mockResolvedValue({ workflowState: 'AGENDA_PUBLISHED' });
    const { invalidateSpy } = renderSection('SLOT_ASSIGNMENT');

    const user = await chooseState('workflow-state-option-AGENDA_PUBLISHED');
    await user.click(screen.getByTestId('apply-workflow-state-button'));

    await waitFor(() =>
      expect(mockTransition).toHaveBeenCalledWith('BATbern60', 'AGENDA_PUBLISHED', false, undefined)
    );
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event', 'BATbern60'] });
  });

  it('should send override and reason when override is checked', async () => {
    mockTransition.mockResolvedValue({ workflowState: 'SPEAKER_IDENTIFICATION' });
    renderSection('SLOT_ASSIGNMENT');

    const user = await chooseState('workflow-state-option-SPEAKER_IDENTIFICATION');
    await user.click(screen.getByTestId('override-workflow-validation-checkbox'));
    await user.type(screen.getByTestId('override-reason-input'), 'Speaker dropped out');
    await user.click(screen.getByTestId('apply-workflow-state-button'));

    await waitFor(() =>
      expect(mockTransition).toHaveBeenCalledWith(
        'BATbern60',
        'SPEAKER_IDENTIFICATION',
        true,
        'Speaker dropped out'
      )
    );
  });

  it('should require a reason when override is checked', async () => {
    renderSection('SLOT_ASSIGNMENT');

    const user = await chooseState('workflow-state-option-CREATED');
    await user.click(screen.getByTestId('override-workflow-validation-checkbox'));

    expect(screen.getByTestId('apply-workflow-state-button')).toBeDisabled();
  });

  it('should show the server message when the transition is rejected', async () => {
    mockTransition.mockRejectedValue({
      response: { data: { message: 'Transition SLOT_ASSIGNMENT -> CREATED is not allowed' } },
    });
    renderSection('SLOT_ASSIGNMENT');

    const user = await chooseState('workflow-state-option-CREATED');
    await user.click(screen.getByTestId('apply-workflow-state-button'));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Transition SLOT_ASSIGNMENT -> CREATED is not allowed'
    );
  });
});
