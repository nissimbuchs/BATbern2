/**
 * TopicBacklogManager Component Tests (Story 5.2 - Frontend Tests)
 *
 * The real tree runs against the MSW harness (17.0c2): useTopics/topicService (`GET /topics`),
 * useEvent (`GET /events/{eventCode}`), useTopic (`GET /topics/{topicCode}`), useUserList
 * (`GET /users`, fixtures), useEvents (`GET /events`, fixtures), the real TopicFilterPanel,
 * TopicList, TopicDetailsPanel (`/similar`, `/usage-history`, `POST /events/{code}/topics`),
 * MultiTopicHeatMap, CreateTopicModal and SpeakerBrainstormingPanel (`GET .../speaker-pool`),
 * and real English i18n. Every request is checked against the OpenAPI specs; the tests read
 * what went over the wire with `api.calls`.
 *
 * Tests main topic selection interface:
 * - Rendering with loading/error states
 * - Filter panel integration
 * - Topic list display and selection
 * - Topic details panel interaction
 * - Event topic selection callback
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import type { ResponseBody } from '@/test/msw/api';
import { aTopic, futureEvent } from '@/test/fixtures';
import { renderWithProviders } from '@/test/test-utils';
import { TopicBacklogManager } from './TopicBacklogManager';
import type { TopicBacklogManagerProps } from './TopicBacklogManager';

type TopicList = ResponseBody<'topics-api/listTopics', 200>;
type Topic = TopicList['data'][number];

const bestand: Topic = {
  ...aTopic(),
  topicCode: 'bauen-im-bestand-zuerich',
  title: 'Bauen im Bestand – Zürich',
  description: 'Umbau statt Neubau: Erfahrungen aus Zürich und Genève.',
  category: 'technical',
  createdDate: '2025-12-18T19:13:20.082653+01:00',
  lastUsedDate: '2013-03-15T16:00:00+01:00',
  usageCount: 2,
  stalenessScore: 100,
  colorZone: 'green',
  status: 'AVAILABLE',
  similarityScores: [],
  active: true,
};

const fuehrung: Topic = {
  ...aTopic(),
  topicCode: 'fuehrung-in-agilen-teams',
  title: 'Führung in agilen Teams',
  description: 'Management-Praktiken für selbstorganisierte Teams.',
  category: 'management',
  createdDate: '2026-03-06T14:42:40.504045+01:00',
  lastUsedDate: '2025-06-01T18:00:00+02:00',
  usageCount: 5,
  stalenessScore: 50,
  colorZone: 'yellow',
  status: 'CAUTION',
  similarityScores: [],
  active: true,
};

const topicList = (
  data: Topic[] = [bestand, fuehrung],
  pagination: Partial<TopicList['pagination']> = {}
): TopicList => ({
  data,
  pagination: {
    page: 1,
    limit: 20,
    totalItems: data.length,
    totalPages: 1,
    hasNext: false,
    hasPrev: false,
    ...pagination,
  },
});

type EventDetail = ResponseBody<'getEvent', 200>;

/** An event in topic selection, without a topic yet: no topic is preselected. */
const eventWithoutTopic = (overrides: Partial<EventDetail> = {}): EventDetail => ({
  ...futureEvent(40, {
    eventCode: 'BATbern56',
    eventNumber: 56,
    workflowState: 'TOPIC_SELECTION',
    topicCode: null,
  }),
  sessions: [],
  ...overrides,
});

const renderComponent = (props: TopicBacklogManagerProps = {}) =>
  renderWithProviders(<TopicBacklogManager {...props} />);

/** The details panel: the Paper that holds the always-present "Edit Topic" action. */
const detailsPanel = async () => {
  const edit = await screen.findByRole('button', { name: 'Edit Topic' });
  const panel = edit.closest('.MuiPaper-root');
  if (!(panel instanceof HTMLElement)) throw new Error('details panel not found');
  return panel;
};

const lastTopicsQuery = () => {
  const calls = api.calls('topics-api/listTopics');
  return calls[calls.length - 1].query;
};

describe('TopicBacklogManager', () => {
  beforeEach(() => {
    api.on('topics-api/listTopics', () => topicList());
    api.on('getEvent', () => eventWithoutTopic());
    api.on('getSimilarTopics', () => []);
    api.on('getTopicUsageHistory', () => []);
    api.on('getSpeakerPool', () => []);
  });

  it('should render with title', async () => {
    renderComponent();

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Topic Backlog Manager');
    // The subtitle is only shown in event selection mode
    expect(
      screen.queryByText(
        'Select topics from the backlog with intelligent suggestions and staleness detection'
      )
    ).not.toBeInTheDocument();
    expect(await screen.findByText('Bauen im Bestand – Zürich')).toBeInTheDocument();
  });

  it('should show the topic selection title and subtitle when an event is loaded', async () => {
    renderComponent({ eventCode: 'BATbern56' });

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Topic Selection' })
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'Select topics from the backlog with intelligent suggestions and staleness detection'
      )
    ).toBeInTheDocument();
    expect(api.calls('getEvent')[0].params.eventCode).toBe('BATbern56');
  });

  it('should display loading state', async () => {
    let release: () => void = () => {};
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    api.on('topics-api/listTopics', async () => {
      await pending;
      return topicList();
    });

    renderComponent();

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
    expect(screen.queryByTestId('topic-list')).not.toBeInTheDocument();

    release();
    expect(await screen.findByTestId('topic-list')).toBeInTheDocument();
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
  });

  it('should display error state', async () => {
    api.on('topics-api/listTopics', () =>
      HttpResponse.json(
        { status: 500, title: 'Internal Server Error', detail: 'Topic query failed' },
        { status: 500 }
      )
    );

    renderComponent();

    const message = await screen.findByText(/Failed to load topics/);
    expect(message.closest('.MuiAlert-root')).toHaveClass('MuiAlert-standardError');
    expect(screen.queryByTestId('topic-list')).not.toBeInTheDocument();
  });

  it('should render filter panel, topic list, and details placeholder', async () => {
    renderComponent();

    expect(screen.getByRole('heading', { name: 'Filters' })).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Category' })).toBeInTheDocument();
    expect(await screen.findByTestId('topic-list')).toBeInTheDocument();
    expect(screen.getByText('Select a topic to view details')).toBeInTheDocument();
  });

  it('should display topics in the list', async () => {
    renderComponent();

    const first = await screen.findByTestId('topic-card-bauen-im-bestand-zuerich');
    expect(first).toHaveTextContent('Bauen im Bestand – Zürich');
    expect(first).toHaveTextContent('100%');
    expect(first).toHaveTextContent('Technical');
    expect(screen.getByTestId('topic-card-fuehrung-in-agilen-teams')).toHaveTextContent(
      'Führung in agilen Teams'
    );
  });

  it('should show topic details when topic is selected', async () => {
    const user = userEvent.setup();
    renderComponent();

    await user.click(
      within(await screen.findByTestId('topic-card-bauen-im-bestand-zuerich')).getByRole('button')
    );

    const panel = await detailsPanel();
    expect(
      within(panel).getByRole('heading', { name: 'Bauen im Bestand – Zürich' })
    ).toBeInTheDocument();
    expect(
      within(panel).getByText('Umbau statt Neubau: Erfahrungen aus Zürich und Genève.')
    ).toBeInTheDocument();
    expect(
      within(panel).getByText('Safe to reuse - last used over 20 months ago')
    ).toBeInTheDocument();
    expect(screen.queryByText('Select a topic to view details')).not.toBeInTheDocument();
    await waitFor(() => {
      expect(api.calls('getSimilarTopics')[0]?.params.topicCode).toBe('bauen-im-bestand-zuerich');
      expect(api.calls('getTopicUsageHistory')[0]?.params.topicCode).toBe(
        'bauen-im-bestand-zuerich'
      );
    });
  });

  it('should call onTopicSelected callback when topic is confirmed', async () => {
    const user = userEvent.setup();
    const onTopicSelected = vi.fn();
    renderComponent({ eventCode: 'BATbern56', onTopicSelected });

    await user.click(
      within(await screen.findByTestId('topic-card-bauen-im-bestand-zuerich')).getByRole('button')
    );
    await user.click(await screen.findByTestId('confirm-topic-selection-button'));

    await waitFor(() => expect(onTopicSelected).toHaveBeenCalledWith('bauen-im-bestand-zuerich'));
    const [select] = api.calls('selectTopicForEvent');
    expect(select.params.eventCode).toBe('BATbern56');
    expect(select.body).toEqual({ topicCode: 'bauen-im-bestand-zuerich' });
  });

  it('should not call onTopicSelected when selecting the topic fails', async () => {
    const user = userEvent.setup();
    const onTopicSelected = vi.fn();
    api.on('selectTopicForEvent', () =>
      HttpResponse.json(
        { status: 409, title: 'Conflict', detail: 'Event is not in TOPIC_SELECTION' },
        { status: 409 }
      )
    );
    renderComponent({ eventCode: 'BATbern56', onTopicSelected });

    await user.click(
      within(await screen.findByTestId('topic-card-bauen-im-bestand-zuerich')).getByRole('button')
    );
    await user.click(await screen.findByTestId('confirm-topic-selection-button'));

    await waitFor(() => expect(api.calls('selectTopicForEvent')).toHaveLength(1));
    await waitFor(() =>
      expect(screen.getByTestId('confirm-topic-selection-button')).not.toBeDisabled()
    );
    expect(onTopicSelected).not.toHaveBeenCalled();
    expect(screen.queryByText('Speaker Brainstorming')).not.toBeInTheDocument();
  });

  it('should update filters when filter panel changes', async () => {
    const user = userEvent.setup();
    api.on('topics-api/listTopics', ({ query }) =>
      topicList([bestand, fuehrung], {
        page: Number(query.get('page') ?? 1),
        totalItems: 45,
        totalPages: 3,
        hasNext: true,
      })
    );
    renderComponent();

    // Go to page 2 first, so the reset to page 1 on a filter change is visible on the wire
    const pagination = await screen.findByTestId('topic-pagination');
    await user.click(within(pagination).getByRole('button', { name: /page 2/i }));
    await waitFor(() => expect(lastTopicsQuery().get('page')).toBe('2'));
    expect(lastTopicsQuery().get('filter')).toBeNull();

    await user.click(screen.getByRole('combobox', { name: 'Category' }));
    await user.click(await screen.findByRole('option', { name: 'Technical' }));

    await waitFor(() => expect(lastTopicsQuery().get('filter')).not.toBeNull());
    const query = lastTopicsQuery();
    expect(JSON.parse(query.get('filter') ?? '{}')).toEqual({ category: 'technical' });
    expect(query.get('page')).toBe('1'); // Reset to page 1 on filter change
    expect(query.get('sort')).toBe('-stalenessScore');
    expect(query.get('limit')).toBe('20');
  });

  it('should initialize with default filters (sort by staleness descending)', async () => {
    renderComponent();

    await waitFor(() => expect(api.calls('topics-api/listTopics')).toHaveLength(1));
    const { query } = api.calls('topics-api/listTopics')[0];
    expect(query.get('page')).toBe('1');
    expect(query.get('limit')).toBe('20');
    expect(query.get('sort')).toBe('-stalenessScore');
    expect(query.get('include')).toBe('history');
    expect(query.get('filter')).toBeNull();
  });

  it('should not show topic selection button when eventCode is not provided', async () => {
    const user = userEvent.setup();
    renderComponent(); // No eventCode

    await user.click(
      within(await screen.findByTestId('topic-card-bauen-im-bestand-zuerich')).getByRole('button')
    );

    // Positive control: the details panel is open with its always-present actions
    expect(await screen.findByRole('button', { name: 'Edit Topic' })).toBeInTheDocument();
    expect(screen.queryByTestId('confirm-topic-selection-button')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Select for Event' })).not.toBeInTheDocument();
    expect(api.calls('getEvent')).toHaveLength(0);
  });

  it('should handle empty topic list', async () => {
    api.on('topics-api/listTopics', () => topicList([]));
    renderComponent();

    expect(await screen.findByText('No topics found matching your filters')).toBeInTheDocument();
    expect(screen.queryByTestId(/^topic-card-/)).not.toBeInTheDocument();
    expect(screen.queryByText('Bauen im Bestand – Zürich')).not.toBeInTheDocument();
  });

  it('should show blob selector button when eventCode is provided', async () => {
    renderComponent({ eventCode: 'BATbern56' });

    expect(screen.getByTestId('blob-selector-button')).toHaveTextContent('Blob Selector');
    await screen.findByTestId('topic-list');
  });

  it('should not show blob selector button when eventCode is not provided', async () => {
    renderComponent();

    expect(screen.getByTestId('new-topic-button')).toBeInTheDocument();
    expect(screen.queryByTestId('blob-selector-button')).not.toBeInTheDocument();
    await screen.findByTestId('topic-list');
  });

  it('should show new topic button', async () => {
    renderComponent();

    expect(screen.getByTestId('new-topic-button')).toHaveTextContent('Create New Topic');
    await screen.findByTestId('topic-list');
  });

  it('should open create topic modal when new topic button is clicked', async () => {
    const user = userEvent.setup();
    renderComponent();
    await screen.findByTestId('topic-list');

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await user.click(screen.getByTestId('new-topic-button'));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('Create New Topic')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Create Topic' })).toBeInTheDocument();
  });

  it('should switch to heatmap view when heatmap toggle is clicked', async () => {
    const user = userEvent.setup();
    renderComponent();

    // Default is list view
    expect(await screen.findByTestId('topic-list')).toBeInTheDocument();
    expect(screen.queryByText('Topic Usage Heat Map')).not.toBeInTheDocument();

    await user.click(screen.getByTestId('view-mode-heatmap'));

    expect(await screen.findByText('Topic Usage Heat Map')).toBeInTheDocument();
    expect(screen.queryByTestId('topic-list')).not.toBeInTheDocument();
  });

  it('should switch back to list view from heatmap', async () => {
    const user = userEvent.setup();
    renderComponent();
    await screen.findByTestId('topic-list');

    await user.click(screen.getByTestId('view-mode-heatmap'));
    expect(await screen.findByText('Topic Usage Heat Map')).toBeInTheDocument();
    await user.click(screen.getByTestId('view-mode-list'));

    expect(screen.getByTestId('topic-list')).toBeInTheDocument();
    expect(screen.queryByText('Topic Usage Heat Map')).not.toBeInTheDocument();
  });

  it('should show speaker brainstorming panel after topic confirmed with eventCode', async () => {
    const user = userEvent.setup();
    renderComponent({ eventCode: 'BATbern56' });

    await user.click(
      within(await screen.findByTestId('topic-card-bauen-im-bestand-zuerich')).getByRole('button')
    );
    expect(screen.queryByText('Speaker Brainstorming')).not.toBeInTheDocument();

    await user.click(await screen.findByTestId('confirm-topic-selection-button'));

    expect(await screen.findByText('Speaker Brainstorming')).toBeInTheDocument();
    expect(screen.getByTestId('add-to-pool-button')).toBeInTheDocument();
    await waitFor(() => expect(api.calls('getSpeakerPool')[0]?.params.eventCode).toBe('BATbern56'));
  });

  it('should preselect the topic of an event that already has one', async () => {
    api.on('getEvent', () => eventWithoutTopic({ topicCode: 'fuehrung-in-agilen-teams' }));
    api.on('getTopicByCode', () => fuehrung);
    renderComponent({ eventCode: 'BATbern56' });

    const panel = await detailsPanel();
    expect(
      within(panel).getByRole('heading', { name: 'Führung in agilen Teams' })
    ).toBeInTheDocument();
    expect(await screen.findByText('Speaker Brainstorming')).toBeInTheDocument();
    const [byCode] = api.calls('getTopicByCode');
    expect(byCode.params.topicCode).toBe('fuehrung-in-agilen-teams');
    expect(byCode.query.get('include')).toBe('history');
    expect(api.calls('selectTopicForEvent')).toHaveLength(0);
  });
});
