/**
 * Story 17.1: outreach history is typed from event-speakers-api (OutreachHistoryResponse),
 * where `notes` is nullable. An entry without notes must render without error.
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import OutreachHistoryTimeline from './OutreachHistoryTimeline';
import type { OutreachHistoryResponse } from '@/types/speakerOutreach.types';
import { useSpeakerOutreachHistory } from '../../../hooks/useSpeakerOutreach';

vi.mock('../../../hooks/useSpeakerOutreach', () => ({
  useSpeakerOutreachHistory: vi.fn(),
}));

const entry = (overrides: Partial<OutreachHistoryResponse>): OutreachHistoryResponse => ({
  id: '550e8400-e29b-41d4-a716-446655440000',
  speakerPoolId: '123e4567-e89b-12d3-a456-426614174000',
  contactDate: '2025-12-14T14:30:00Z',
  contactMethod: 'email',
  notes: null,
  organizerUsername: 'john.doe',
  createdAt: '2025-12-14T14:30:00Z',
  updatedAt: '2025-12-14T14:30:00Z',
  ...overrides,
});

describe('OutreachHistoryTimeline', () => {
  it('should_renderEntry_when_notesAreNull', () => {
    vi.mocked(useSpeakerOutreachHistory).mockReturnValue({
      data: [entry({ notes: null })],
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useSpeakerOutreachHistory>);

    render(<OutreachHistoryTimeline eventCode="BATbern142" speakerId="speaker-1" />);

    expect(screen.getByTestId('outreach-timeline')).toBeInTheDocument();
    expect(screen.getByText(/john\.doe/)).toBeInTheDocument();
  });

  it('should_renderNotes_when_present', () => {
    vi.mocked(useSpeakerOutreachHistory).mockReturnValue({
      data: [entry({ id: 'b', notes: 'Follow up next Tuesday', contactMethod: 'phone' })],
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useSpeakerOutreachHistory>);

    render(<OutreachHistoryTimeline eventCode="BATbern142" speakerId="speaker-1" />);

    expect(screen.getByText('Follow up next Tuesday')).toBeInTheDocument();
  });
});
