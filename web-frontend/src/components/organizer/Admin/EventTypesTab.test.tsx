/**
 * EventTypesTab Tests (Story 10.1 - Task 2)
 *
 * Tests:
 * - AC2: Renders event type cards for FULL_DAY, AFTERNOON, EVENING
 * - AC2: Edit button opens dialog
 * - AC2: Loading and error states
 */

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { EventTypesTab } from './EventTypesTab';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, fallback?: string) => fallback ?? key,
    i18n: { changeLanguage: vi.fn() },
  }),
}));

const mockUpdateMutateAsync = vi.fn();
let mockIsLoading = false;
let mockError: Error | null = null;
let mockEventTypes = [
  { type: 'FULL_DAY', morningSlots: 2, afternoonSlots: 3, eveningSlots: 1 },
  { type: 'AFTERNOON', morningSlots: 0, afternoonSlots: 4, eveningSlots: 0 },
  { type: 'EVENING', morningSlots: 0, afternoonSlots: 0, eveningSlots: 2 },
];

vi.mock('@/hooks/useEventTypes', () => ({
  useEventTypes: () => ({
    data: mockIsLoading ? undefined : mockEventTypes,
    isLoading: mockIsLoading,
    error: mockError,
  }),
  useUpdateEventType: () => ({
    mutateAsync: mockUpdateMutateAsync,
  }),
}));

vi.mock('@/components/organizer/EventTypeConfigurationForm/EventTypeConfigurationForm', () => ({
  EventTypeConfigurationForm: ({ onCancel }: { onCancel: () => void }) => (
    <div data-testid="event-type-config-form">
      <button onClick={onCancel}>Cancel</button>
    </div>
  ),
}));

vi.mock('@/components/organizer/SlotTemplatePreview/SlotTemplatePreview', () => ({
  SlotTemplatePreview: ({ eventType }: { eventType: string }) => (
    <div data-testid={`slot-preview-${eventType}`}>{eventType}</div>
  ),
}));

vi.mock('@components/shared/BATbernLoader', () => ({
  BATbernLoader: () => <div data-testid="loader" />,
}));

const createQueryClient = () => new QueryClient({ defaultOptions: { queries: { retry: false } } });

const renderTab = () =>
  render(
    <QueryClientProvider client={createQueryClient()}>
      <EventTypesTab />
    </QueryClientProvider>
  );

describe('EventTypesTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockIsLoading = false;
    mockError = null;
    mockEventTypes = [
      { type: 'FULL_DAY', morningSlots: 2, afternoonSlots: 3, eveningSlots: 1 },
      { type: 'AFTERNOON', morningSlots: 0, afternoonSlots: 4, eveningSlots: 0 },
      { type: 'EVENING', morningSlots: 0, afternoonSlots: 0, eveningSlots: 2 },
    ];
  });

  it('shows loader while loading', () => {
    mockIsLoading = true;
    renderTab();
    expect(screen.getByTestId('loader')).toBeInTheDocument();
  });

  it('shows error alert on fetch failure', () => {
    mockError = new Error('fetch failed');
    renderTab();
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('renders a card for each event type', () => {
    renderTab();
    expect(screen.getByTestId('slot-preview-FULL_DAY')).toBeInTheDocument();
    expect(screen.getByTestId('slot-preview-AFTERNOON')).toBeInTheDocument();
    expect(screen.getByTestId('slot-preview-EVENING')).toBeInTheDocument();
  });

  it('opens edit dialog when Edit button clicked', async () => {
    const user = userEvent.setup();
    renderTab();
    const editButtons = screen.getAllByRole('button', { name: /edit/i });
    await user.click(editButtons[0]);
    expect(screen.getByTestId('event-type-config-form')).toBeInTheDocument();
  });

  it('closes edit dialog when Cancel clicked', async () => {
    const user = userEvent.setup();
    renderTab();
    const editButtons = screen.getAllByRole('button', { name: /edit/i });
    await user.click(editButtons[0]);
    expect(screen.getByTestId('event-type-config-form')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /cancel/i }));
    await waitFor(() =>
      expect(screen.queryByTestId('event-type-config-form')).not.toBeInTheDocument()
    );
  });
});
