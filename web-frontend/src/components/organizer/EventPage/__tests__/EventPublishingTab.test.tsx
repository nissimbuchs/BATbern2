/**
 * EventPublishingTab (Story 5.7, Story 4.9b)
 *
 * The real tab with its real children, hooks and services against the MSW harness (Epic 17 test
 * fidelity, touch rule): every request is checked against events-core-api and the publishing
 * resource answers with a typed override. Story 4.9b: status and auto-publish schedule are one
 * resource (`GET`/`PATCH /events/{eventCode}/publishing`), phases are published through
 * `POST /events/{eventCode}/phases/{phase}/publish`.
 */

import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { EventPublishingTab } from '../EventPublishingTab';
import type { Event, PublishingStatusResponse } from '@/types/event.types';
import { anEvent } from '@/test/fixtures';

const EVENT_CODE = 'BATbern61';
const DAY = 24 * 60 * 60 * 1000;

const event: Event = {
  ...anEvent(),
  eventCode: EVENT_CODE,
  title: 'BATbern61 Brünnimann-Abend',
  date: new Date(Date.now() + 60 * DAY).toISOString(),
  workflowState: 'SLOT_ASSIGNMENT',
  topicCode: 'cloud-architecture',
};

const publishing = (
  overrides: Partial<PublishingStatusResponse> = {}
): PublishingStatusResponse => ({
  currentPhase: 'topic',
  publishedPhases: ['topic'],
  topic: { isValid: true, errors: [] },
  speakers: { isValid: true, errors: [] },
  sessions: { isValid: true, errors: [], assignedCount: 6, totalCount: 6, unassignedSessions: [] },
  phase2Enabled: true,
  phase2DaysBeforeEvent: 30,
  phase2TriggerDate: new Date(Date.now() + 30 * DAY).toISOString(),
  phase3Enabled: true,
  phase3DaysBeforeEvent: 14,
  phase3TriggerDate: new Date(Date.now() + 46 * DAY).toISOString(),
  ...overrides,
});

describe('EventPublishingTab', () => {
  let queryClient: QueryClient;

  const renderTab = () =>
    render(
      <MemoryRouter>
        <QueryClientProvider client={queryClient}>
          <EventPublishingTab event={event} eventCode={EVENT_CODE} />
        </QueryClientProvider>
      </MemoryRouter>
    );

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
    });
    // useSlotAssignment: no session without timing
    api.on('getUnassignedSessions', () => []);
  });

  afterEach(() => {
    queryClient.clear();
  });

  it('shows a skeleton until the publishing resource is loaded', async () => {
    api.on('getPublishing', () => publishing());

    const { container } = renderTab();

    expect(container.querySelectorAll('.MuiSkeleton-root').length).toBeGreaterThan(0);
    expect(await screen.findByTestId('publish-topic-button')).toBeInTheDocument();
    expect(api.calls('getPublishing')[0].params.eventCode).toBe(EVENT_CODE);
  });

  it('shows the published phase in the controls and the timeline', async () => {
    api.on('getPublishing', () => publishing());

    renderTab();

    const topic = await screen.findByTestId('publish-topic-button');
    expect(topic).toBeDisabled();
    expect(topic).toHaveTextContent('Topic Published');
    expect(screen.getByTestId('publish-speakers-button')).toBeEnabled();
    expect(screen.getByTestId('icon-complete-topic')).toBeInTheDocument();
  });

  it('shows the stored schedule in the timeline, and no chip for a phase switched off', async () => {
    api.on('getPublishing', () => publishing({ phase3Enabled: false }));

    renderTab();

    expect(await screen.findByTestId('auto-publish-speakers')).toBeInTheDocument();
    const timeline = screen.getByTestId('publishing-timeline-container');
    expect(within(timeline).getByText(/Auto-publish in 30 days/)).toBeInTheDocument();
    expect(within(timeline).queryByText(/Auto-publish in 46 days/)).not.toBeInTheDocument();
  });

  it('saves only the changed schedule field and shows what EMS stored', async () => {
    const user = userEvent.setup();
    api.on('getPublishing', () => publishing());
    api.on('patchPublishing', ({ body }) => publishing({ ...body }));

    renderTab();

    const schedule = await screen.findByTestId('auto-publish-schedule');
    const save = within(schedule).getByTestId('save-auto-publish-schedule');
    expect(save).toBeDisabled();

    await user.click(
      within(schedule).getByRole('switch', { name: 'Publish speakers automatically' })
    );
    expect(save).toBeEnabled();
    await user.click(save);

    await waitFor(() => expect(api.calls('patchPublishing')).toHaveLength(1));
    expect(api.calls('patchPublishing')[0].body).toEqual({ phase2Enabled: false });
    expect(await within(schedule).findByText('Schedule saved.')).toBeInTheDocument();
    expect(
      within(schedule).getByRole('switch', { name: 'Publish speakers automatically' })
    ).not.toBeChecked();
  });

  it('refuses days outside 1 to 365 before sending anything', async () => {
    const user = userEvent.setup();
    api.on('getPublishing', () => publishing());

    renderTab();

    const schedule = await screen.findByTestId('auto-publish-schedule');
    const agendaDays = within(
      within(schedule).getByTestId('auto-publish-schedule-agenda')
    ).getByLabelText('Days before the event');
    await user.clear(agendaDays);
    await user.type(agendaDays, '0');

    expect(within(schedule).getByText('1 to 365 days')).toBeInTheDocument();
    expect(within(schedule).getByTestId('save-auto-publish-schedule')).toBeDisabled();
    expect(api.calls('patchPublishing')).toHaveLength(0);
  });

  it('publishes the speakers phase through POST …/phases/speakers/publish', async () => {
    const user = userEvent.setup();
    api.on('getPublishing', () => publishing());
    api.on('publishPhase', ({ params }) => ({
      phase: params.phase,
      published: true,
      publishedAt: '2026-11-10T10:00:00Z',
      cdnInvalidated: true,
    }));

    renderTab();

    await user.click(await screen.findByTestId('publish-speakers-button'));

    await waitFor(() => expect(api.calls('publishPhase')).toHaveLength(1));
    expect(api.calls('publishPhase')[0].params).toEqual({
      eventCode: EVENT_CODE,
      phase: 'speakers',
    });
    // the status is read again after the publish
    await waitFor(() => expect(api.calls('getPublishing').length).toBeGreaterThan(1));
  });

  it('lists every reason of a 422 when the agenda is not ready', async () => {
    const user = userEvent.setup();
    api.on('getPublishing', () =>
      publishing({ currentPhase: 'speakers', publishedPhases: ['topic', 'speakers'] })
    );
    api.on('publishPhase', () =>
      HttpResponse.json(
        {
          error: 'VALIDATION_ERROR',
          message: 'Event date must be in the future; All sessions must have timing assigned',
          validationErrors: [
            { sessionSlug: null, field: 'date', message: 'Event date must be in the future' },
            { sessionSlug: 'keynote', field: 'timing', message: 'Session has no timing assigned' },
          ],
        },
        { status: 422 }
      )
    );

    renderTab();

    await user.click(await screen.findByTestId('publish-agenda-button'));

    expect(await screen.findByText('• Event date must be in the future')).toBeInTheDocument();
    expect(screen.getByText('• Session has no timing assigned')).toBeInTheDocument();
  });
});
