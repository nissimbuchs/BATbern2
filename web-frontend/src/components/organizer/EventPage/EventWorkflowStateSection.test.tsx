/**
 * EventWorkflowStateSection tests.
 *
 * Restores the organizer's manual event-state change with validation override, which PR #788
 * (lifecycle-aware event page redesign) left unreachable from the event page.
 *
 * The real workflowService and apiClient run against the MSW harness, with the real i18next `en`
 * bundle (touch rule of Story 4.9b): the assertions read what `POST /events/{code}/transition`
 * received.
 */

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { EventWorkflowStateSection } from './EventWorkflowStateSection';

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
  it('should show the current state when rendered', () => {
    renderSection('SLOT_ASSIGNMENT');

    expect(screen.getByTestId('workflow-state-section')).toBeInTheDocument();
    expect(screen.getByRole('combobox')).toHaveTextContent('Slot Assignment');
  });

  it('should keep the apply button disabled when the selected state equals the current state', () => {
    renderSection('SLOT_ASSIGNMENT');

    expect(screen.getByTestId('apply-workflow-state-button')).toBeDisabled();
  });

  it('should transition without override when a different state is applied', async () => {
    const { invalidateSpy } = renderSection('SLOT_ASSIGNMENT');

    const user = await chooseState('workflow-state-option-AGENDA_PUBLISHED');
    await user.click(screen.getByTestId('apply-workflow-state-button'));

    await waitFor(() => expect(api.calls('transitionEventWorkflowState')).toHaveLength(1));
    const [call] = api.calls('transitionEventWorkflowState');
    expect(call.params.eventCode).toBe('BATbern60');
    expect(call.body).toEqual({ targetState: 'AGENDA_PUBLISHED', overrideValidation: false });
    await waitFor(() =>
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['event', 'BATbern60'] })
    );
    expect(await screen.findByText('Event state changed.')).toBeInTheDocument();
  });

  it('should send override and reason when override is checked', async () => {
    renderSection('SLOT_ASSIGNMENT');

    const user = await chooseState('workflow-state-option-SPEAKER_IDENTIFICATION');
    await user.click(screen.getByTestId('override-workflow-validation-checkbox'));
    await user.type(screen.getByTestId('override-reason-input'), 'Speaker dropped out');
    await user.click(screen.getByTestId('apply-workflow-state-button'));

    await waitFor(() => expect(api.calls('transitionEventWorkflowState')).toHaveLength(1));
    expect(api.calls('transitionEventWorkflowState')[0].body).toEqual({
      targetState: 'SPEAKER_IDENTIFICATION',
      overrideValidation: true,
      overrideReason: 'Speaker dropped out',
    });
  });

  it('should require a reason when override is checked', async () => {
    renderSection('SLOT_ASSIGNMENT');

    const user = await chooseState('workflow-state-option-CREATED');
    await user.click(screen.getByTestId('override-workflow-validation-checkbox'));

    expect(screen.getByTestId('apply-workflow-state-button')).toBeDisabled();
  });

  it('should show the server message when the transition is rejected', async () => {
    api.on('transitionEventWorkflowState', () =>
      HttpResponse.json(
        {
          timestamp: '2026-11-10T08:00:00Z',
          status: 422,
          error: 'Unprocessable Entity',
          message: 'Transition SLOT_ASSIGNMENT -> CREATED is not allowed',
        },
        { status: 422 }
      )
    );
    renderSection('SLOT_ASSIGNMENT');

    const user = await chooseState('workflow-state-option-CREATED');
    await user.click(screen.getByTestId('apply-workflow-state-button'));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Transition SLOT_ASSIGNMENT -> CREATED is not allowed'
    );
  });
});
