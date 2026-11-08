/**
 * TopicSelectionOverlay search: the typed term goes to GET /topics?search=, which
 * matches title and description over all topics before sorting and paging, and the overlay shows
 * what the backend returns. It used to filter the first 50 loaded topics by title in the browser.
 * Real hooks and services; MSW answers every HTTP call.
 */
import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';
import { TopicSelectionOverlay } from '../TopicSelectionOverlay';

const topic = (topicCode: string, title: string, description: string) => ({
  topicCode,
  title,
  description,
  category: 'technical',
  stalenessScore: 90,
  colorZone: 'green',
  usageCount: 0,
  similarityScores: [],
});

const ALL = [
  topic('cloud-native', 'Cloud Native', 'Plattformen'),
  topic('ai-ops', 'AI Ops', 'Betrieb mit Künstlicher Intelligenz'),
];
// The backend matched "kunstliche" in the description, which the old title-only browser
// filter could never have found.
const MATCHED = [ALL[1]];

let topicRequests: URL[] = [];

const server = setupServer(
  http.get('*/topics', ({ request }) => {
    const url = new URL(request.url);
    topicRequests.push(url);
    const data = url.searchParams.get('search') ? MATCHED : ALL;
    return HttpResponse.json({
      data,
      pagination: {
        page: 1,
        limit: 50,
        totalItems: data.length,
        totalPages: 1,
        hasNext: false,
        hasPrev: false,
      },
    });
  }),
  http.get('*', () =>
    HttpResponse.json({
      data: [],
      pagination: {
        page: 1,
        limit: 100,
        totalItems: 0,
        totalPages: 0,
        hasNext: false,
        hasPrev: false,
      },
    })
  )
);

beforeAll(() => server.listen({ onUnhandledRequest: 'bypass' }));
afterEach(() => {
  server.resetHandlers();
  topicRequests = [];
});
afterAll(() => server.close());

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
    expect(topicRequests[0].searchParams.has('search')).toBe(false);

    fireEvent.change(screen.getByTestId('topic-overlay-search'), {
      target: { value: '  kunstliche ' },
    });

    await waitFor(() =>
      expect(topicRequests.some((r) => r.searchParams.get('search') === 'kunstliche')).toBe(true)
    );
    expect(await screen.findByTestId('topic-card-ai-ops')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByTestId('topic-card-cloud-native')).not.toBeInTheDocument()
    );
  });
});
