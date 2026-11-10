/**
 * TopicSelectionOverlay search: the typed term goes to GET /topics?search=, which
 * matches title and description over all topics before sorting and paging, and the overlay shows
 * what the backend returns. It used to filter the first 50 loaded topics by title in the browser.
 * Real hooks and services; the MSW harness (src/test/msw) answers every HTTP call.
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { api } from '@/test/msw/api';
import { TopicSelectionOverlay } from '../TopicSelectionOverlay';
import { aTopic } from '@/test/fixtures';

const topic = (topicCode: string, title: string, description: string) => ({
  ...aTopic(),
  topicCode,
  title,
  description,
  category: 'technical',
  stalenessScore: 90,
  colorZone: 'green' as const,
  usageCount: 0,
  similarityScores: [],
  active: true,
});

const ALL = [
  topic('cloud-native', 'Cloud Native', 'Plattformen'),
  topic('ai-ops', 'AI Ops', 'Betrieb mit Künstlicher Intelligenz'),
];
// The backend matched "kunstliche" in the description, which the old title-only browser
// filter could never have found.
const MATCHED = [ALL[1]];

beforeEach(() => {
  api.on('topics-api/listTopics', ({ query }) => {
    const data = query.get('search') ? MATCHED : ALL;
    return {
      data,
      pagination: {
        page: 1,
        limit: 50,
        totalItems: data.length,
        totalPages: 1,
        hasNext: false,
        hasPrev: false,
      },
    };
  });
});

const topicSearches = () => api.calls('topics-api/listTopics').map((c) => c.query);

const renderOverlay = () =>
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <MemoryRouter>
        <TopicSelectionOverlay open eventCode="BAT54" onClose={() => {}} />
      </MemoryRouter>
    </QueryClientProvider>
  );

describe('TopicSelectionOverlay search', () => {
  it('sends the typed term to the backend and shows the topics it matched', async () => {
    renderOverlay();
    expect(await screen.findByTestId('topic-card-cloud-native')).toBeInTheDocument();
    expect(topicSearches()[0].has('search')).toBe(false);

    fireEvent.change(screen.getByTestId('topic-overlay-search'), {
      target: { value: '  kunstliche ' },
    });

    await waitFor(() =>
      expect(topicSearches().some((q) => q.get('search') === 'kunstliche')).toBe(true)
    );
    expect(await screen.findByTestId('topic-card-ai-ops')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByTestId('topic-card-cloud-native')).not.toBeInTheDocument()
    );
  });
});
