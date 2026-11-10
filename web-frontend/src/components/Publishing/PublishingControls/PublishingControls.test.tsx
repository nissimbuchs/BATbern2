/**
 * PublishingControls with its real usePublishing hook, publishingService and apiClient against the
 * MSW harness, and the real i18next `en` bundle (Epic 17 test fidelity, touch rule of Story 4.9b).
 * The assertions read what went over the wire: `POST /events/{code}/phases/{phase}/publish` and
 * `…/unpublish`.
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { HttpResponse, delay } from 'msw';
import { api } from '@/test/msw/api';
import type { PublishingStatusResponse } from '@/types/event.types';
import { PublishingControls } from './PublishingControls';

const EVENT = 'BATbern142';

const status = (overrides: Partial<PublishingStatusResponse> = {}): PublishingStatusResponse => ({
  currentPhase: null,
  publishedPhases: [],
  topic: { isValid: true, errors: [] },
  speakers: { isValid: true, errors: [] },
  sessions: { isValid: true, errors: [], assignedCount: 6, totalCount: 6, unassignedSessions: [] },
  phase2Enabled: true,
  phase2DaysBeforeEvent: 30,
  phase2TriggerDate: '2027-02-12T08:00:00Z',
  phase3Enabled: true,
  phase3DaysBeforeEvent: 14,
  phase3TriggerDate: '2027-02-28T08:00:00Z',
  ...overrides,
});

const published = (phase: string) => ({
  phase,
  published: true,
  publishedAt: '2026-11-10T10:00:00Z',
  cdnInvalidated: true,
});

describe('PublishingControls', () => {
  let queryClient: QueryClient;

  const renderControls = async () => {
    render(
      <QueryClientProvider client={queryClient}>
        <PublishingControls eventCode={EVENT} currentPhase="topic" />
      </QueryClientProvider>
    );
    await waitFor(() => expect(api.calls('getPublishing')).toHaveLength(1));
    // the buttons render enabled or disabled only once the status is there
    await waitFor(() => expect(screen.getByTestId('publish-topic-button')).toBeEnabled());
  };

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: 0 }, mutations: { retry: false } },
    });
    api.on('getPublishing', () => status());
  });

  afterEach(() => {
    queryClient.clear();
  });

  describe('Publish buttons', () => {
    it('renders one button per phase', async () => {
      await renderControls();

      expect(screen.getByTestId('publish-topic-button')).toHaveTextContent('Publish Topic');
      expect(screen.getByTestId('publish-speakers-button')).toBeInTheDocument();
      expect(screen.getByTestId('publish-agenda-button')).toBeInTheDocument();
    });

    it('publishes the phase through POST …/phases/{phase}/publish', async () => {
      const user = userEvent.setup();
      api.on('publishPhase', ({ params }) => published(params.phase));
      await renderControls();

      await user.click(screen.getByTestId('publish-topic-button'));

      await waitFor(() => expect(api.calls('publishPhase')).toHaveLength(1));
      expect(api.calls('publishPhase')[0].params).toEqual({ eventCode: EVENT, phase: 'topic' });
      expect(api.calls('publishPhase')[0].url.pathname).toContain(
        `/events/${EVENT}/phases/topic/publish`
      );
    });

    it('disables the buttons while a publish is in flight', async () => {
      const user = userEvent.setup();
      api.on('publishPhase', async ({ params }) => {
        await delay(200);
        return published(params.phase);
      });
      await renderControls();

      await user.click(screen.getByTestId('publish-topic-button'));

      await waitFor(() => expect(screen.getByTestId('publish-topic-button')).toBeDisabled());
      expect(screen.getByTestId('publish-topic-button')).toHaveTextContent('Publishing...');
    });

    it('disables a phase whose content is not ready', async () => {
      api.on('getPublishing', () =>
        status({ topic: { isValid: false, errors: ['Event topic must be defined'] } })
      );
      render(
        <QueryClientProvider client={queryClient}>
          <PublishingControls eventCode={EVENT} currentPhase="topic" />
        </QueryClientProvider>
      );

      await waitFor(() => expect(api.calls('getPublishing')).toHaveLength(1));
      expect(screen.getByTestId('publish-topic-button')).toBeDisabled();
    });
  });

  describe('Progressive phase ordering', () => {
    it('keeps speakers disabled until the topic is published', async () => {
      await renderControls();

      expect(screen.getByTestId('publish-speakers-button')).toBeDisabled();
    });

    it('enables speakers once the topic is published, and keeps agenda disabled', async () => {
      api.on('getPublishing', () => status({ currentPhase: 'topic', publishedPhases: ['topic'] }));
      render(
        <QueryClientProvider client={queryClient}>
          <PublishingControls eventCode={EVENT} currentPhase="topic" />
        </QueryClientProvider>
      );

      await waitFor(() => expect(screen.getByTestId('publish-speakers-button')).toBeEnabled());
      expect(screen.getByTestId('publish-topic-button')).toHaveTextContent('Topic Published');
      expect(screen.getByTestId('publish-agenda-button')).toBeDisabled();
    });
  });

  describe('Validation errors', () => {
    it('lists every reason of the 422 its own publish received', async () => {
      const user = userEvent.setup();
      api.on('getPublishing', () =>
        status({ currentPhase: 'speakers', publishedPhases: ['topic', 'speakers'] })
      );
      api.on('publishPhase', () =>
        HttpResponse.json(
          {
            error: 'VALIDATION_ERROR',
            message: 'Event date must be in the future; All sessions must have timing assigned',
            validationErrors: [
              { sessionSlug: null, field: 'date', message: 'Event date must be in the future' },
              {
                sessionSlug: 'keynote-bruennimann',
                field: 'timing',
                message: 'Session has no timing assigned',
              },
            ],
          },
          { status: 422 }
        )
      );
      render(
        <QueryClientProvider client={queryClient}>
          <PublishingControls eventCode={EVENT} currentPhase="speakers" />
        </QueryClientProvider>
      );
      await waitFor(() => expect(screen.getByTestId('publish-agenda-button')).toBeEnabled());

      await user.click(screen.getByTestId('publish-agenda-button'));

      expect(await screen.findByText('Validation Errors:')).toBeInTheDocument();
      expect(screen.getByText('• Event date must be in the future')).toBeInTheDocument();
      expect(screen.getByText('• Session has no timing assigned')).toBeInTheDocument();
      expect(screen.getByTestId('validation-error-icon')).toBeInTheDocument();
    });
  });

  describe('Revert phase', () => {
    it('offers no revert when nothing is published', async () => {
      await renderControls();

      expect(screen.queryByTestId('revert-phase-button')).not.toBeInTheDocument();
    });

    it('unpublishes the latest phase only after the organizer confirms', async () => {
      const user = userEvent.setup();
      api.on('getPublishing', () =>
        status({ currentPhase: 'speakers', publishedPhases: ['topic', 'speakers'] })
      );
      api.on('unpublishPhase', ({ params }) => ({
        phase: params.phase,
        published: false,
        unpublishedAt: '2026-11-10T11:00:00Z',
        cdnInvalidated: true,
      }));
      render(
        <QueryClientProvider client={queryClient}>
          <PublishingControls eventCode={EVENT} currentPhase="speakers" />
        </QueryClientProvider>
      );

      const revert = await screen.findByTestId('revert-phase-button');
      expect(revert).toHaveTextContent('Revert Speakers publication');
      await user.click(revert);
      expect(api.calls('unpublishPhase')).toHaveLength(0);

      await user.click(screen.getByTestId('confirm-revert-phase-button'));

      await waitFor(() => expect(api.calls('unpublishPhase')).toHaveLength(1));
      expect(api.calls('unpublishPhase')[0].params).toEqual({
        eventCode: EVENT,
        phase: 'speakers',
      });
    });

    it('does not unpublish when the organizer cancels', async () => {
      const user = userEvent.setup();
      api.on('getPublishing', () => status({ currentPhase: 'topic', publishedPhases: ['topic'] }));
      render(
        <QueryClientProvider client={queryClient}>
          <PublishingControls eventCode={EVENT} currentPhase="topic" />
        </QueryClientProvider>
      );

      await user.click(await screen.findByTestId('revert-phase-button'));
      await user.click(screen.getByTestId('cancel-revert-phase-button'));

      expect(api.calls('unpublishPhase')).toHaveLength(0);
    });
  });

  describe('Accessibility', () => {
    it('announces the publish to screen readers', async () => {
      const user = userEvent.setup();
      api.on('publishPhase', async ({ params }) => {
        await delay(100);
        return published(params.phase);
      });
      await renderControls();

      await user.click(screen.getByTestId('publish-topic-button'));

      expect(await screen.findByRole('status')).toHaveTextContent('Publishing topic');
    });
  });
});
