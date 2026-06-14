/**
 * EventInfoTab tests (Epic 14, Story 14.F.2)
 *
 * The inline identity editor: renders fields from the event, Saves only changed
 * fields via useUpdateEvent, navigates on Change topic, and never shows the
 * removed Preview-public / Enrol affordances (FR38/FR39).
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { EventInfoTab } from '../EventInfoTab';
import type { Event } from '@/types/event.types';

const mutateAsync = vi.fn();
const navigateMock = vi.fn();

vi.mock('@/hooks/useEvents', () => ({
  useUpdateEvent: () => ({ mutateAsync, isPending: false }),
}));
vi.mock('@/hooks/useFeatureFlags', () => ({
  useFeatureFlags: () => ({ aiContentEnabled: false }),
}));
vi.mock('react-router-dom', () => ({ useNavigate: () => navigateMock }));
vi.mock('@/services/topicService', () => ({
  topicService: { getTopicById: vi.fn().mockResolvedValue({ title: 'Cloud Native' }) },
}));
vi.mock('../AiAssistDrawer', () => ({ AiAssistDrawer: () => null }));
vi.mock('@/components/shared/FileUpload/FileUpload', () => ({
  FileUpload: () => <div data-testid="file-upload" />,
}));
vi.mock('@/components/organizer/EventTypeSelector/EventTypeSelector', () => ({
  EventTypeSelector: () => <div data-testid="event-type-selector" />,
}));
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: string | Record<string, unknown>) =>
      typeof opts === 'string' ? opts : ((opts?.defaultValue as string) ?? key),
  }),
}));

const event = {
  eventId: 'uuid-1',
  eventCode: 'BAT54',
  eventNumber: 54,
  title: 'Cloud Native Architecture',
  description: 'A deep dive into platform engineering.',
  date: '2026-03-15T09:00:00Z',
  registrationDeadline: '2026-03-10T23:59:59Z',
  venueName: 'Kursaal Bern',
  venueAddress: 'Kornhausstrasse 3',
  venueCapacity: 200,
  workflowState: 'TOPIC_SELECTION',
  eventType: 'FULL_DAY',
  topicCode: 'cloud-native',
  themeImageUrl: 'https://cdn/x.jpg',
} as unknown as Event;

beforeEach(() => {
  vi.clearAllMocks();
  mutateAsync.mockResolvedValue({});
});

function renderTab() {
  return render(<EventInfoTab event={event} eventCode="BAT54" />);
}

describe('EventInfoTab', () => {
  it('renders the identity fields from the event', () => {
    renderTab();
    expect(screen.getByTestId('info-title-field')).toHaveValue('Cloud Native Architecture');
    expect(screen.getByTestId('info-venue-name-field')).toHaveValue('Kursaal Bern');
    expect(screen.getByTestId('file-upload')).toBeInTheDocument();
  });

  it('Saves only the changed field via useUpdateEvent', async () => {
    renderTab();
    fireEvent.change(screen.getByTestId('info-title-field'), {
      target: { value: 'Cloud Native Architecture 2026' },
    });
    fireEvent.click(screen.getByTestId('info-save-button'));
    await waitFor(() => expect(mutateAsync).toHaveBeenCalledTimes(1));
    expect(mutateAsync).toHaveBeenCalledWith({
      eventCode: 'BAT54',
      data: { title: 'Cloud Native Architecture 2026' },
    });
    expect(await screen.findByTestId('info-saved')).toBeInTheDocument();
  });

  it('does not save when nothing changed (Save disabled)', () => {
    renderTab();
    expect(screen.getByTestId('info-save-button')).toBeDisabled();
  });

  it('navigates to the topics route on Change topic (interim)', () => {
    renderTab();
    fireEvent.click(screen.getByTestId('info-change-topic'));
    expect(navigateMock).toHaveBeenCalledWith('/organizer/topics?eventCode=BAT54');
  });

  it('does not show Preview-public or Enrol affordances (FR39)', () => {
    renderTab();
    expect(screen.queryByText(/preview public/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/enrol/i)).not.toBeInTheDocument();
  });
});
