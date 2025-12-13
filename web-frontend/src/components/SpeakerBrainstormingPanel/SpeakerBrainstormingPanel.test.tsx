/**
 * SpeakerBrainstormingPanel Tests - Focused component tests for speaker brainstorming
 */
import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SpeakerBrainstormingPanel } from './SpeakerBrainstormingPanel';
import { useSpeakerPool } from '@/hooks/useSpeakerPool';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string, def?: string) => def || key }),
}));

vi.mock('@/hooks/useSpeakerPool', () => ({
  useSpeakerPool: vi.fn(() => ({
    data: [
      {
        id: '1',
        speakerName: 'Dr. Jane Smith',
        company: 'TechCorp',
        status: 'identified',
        createdAt: '2025-12-13',
        updatedAt: '2025-12-13',
      },
      {
        id: '2',
        speakerName: 'Prof. Bob Johnson',
        company: 'University',
        status: 'contacted',
        createdAt: '2025-12-13',
        updatedAt: '2025-12-13',
      },
    ],
    isLoading: false,
  })),
  useAddSpeakerToPool: () => ({ mutate: vi.fn(), isLoading: false }),
}));

describe('SpeakerBrainstormingPanel', () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });

  const renderComponent = (props = {}) =>
    render(
      <QueryClientProvider client={queryClient}>
        <SpeakerBrainstormingPanel eventId="event-123" {...props} />
      </QueryClientProvider>
    );

  it('should render speaker pool list', () => {
    renderComponent();
    expect(screen.getByText('Dr. Jane Smith')).toBeInTheDocument();
    expect(screen.getByText('Prof. Bob Johnson')).toBeInTheDocument();
  });

  it('should display add speaker form', () => {
    renderComponent();
    expect(screen.getByLabelText(/Speaker Name/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Company/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Expertise/i)).toBeInTheDocument();
  });

  it('should allow adding a new speaker', async () => {
    const user = userEvent.setup();
    renderComponent();

    await user.type(screen.getByLabelText(/Speaker Name/i), 'New Speaker');
    await user.type(screen.getByLabelText(/Company/i), 'ACME Inc');
    await user.click(screen.getByRole('button', { name: /Add Speaker/i }));

    // Verify form was submitted (mutation would be called in real implementation)
    expect(screen.getByLabelText(/Speaker Name/i)).toHaveValue('');
  });

  it('should display speaker status badges', () => {
    renderComponent();
    expect(screen.getByText('identified')).toBeInTheDocument();
    expect(screen.getByText('contacted')).toBeInTheDocument();
  });

  it('should allow assigning speakers to organizers', async () => {
    const user = userEvent.setup();
    renderComponent();

    const assignButton = screen.getAllByRole('button', { name: /Assign/i })[0];
    await user.click(assignButton);

    expect(screen.getByRole('dialog')).toBeInTheDocument(); // Assumes dialog opens
  });

  it('should handle empty speaker pool', () => {
    vi.mocked(useSpeakerPool).mockReturnValueOnce({
      data: [],
      isLoading: false,
    });

    renderComponent();
    expect(screen.getByText(/No speakers in pool yet/i)).toBeInTheDocument();
  });
});
