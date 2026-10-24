/**
 * CockpitTab tests (Story 14.B.1 / FR7) — the three-region landing, no identity block.
 */
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { I18nextProvider } from 'react-i18next';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import i18n from '@/i18n/config';
import { CockpitTab } from '../CockpitTab';

let mockShowRehearsal = false;
vi.mock('../useCockpitCards', () => ({
  useCockpitCards: () => ({
    cards: [],
    isLoading: false,
    isError: false,
    refetch: vi.fn(),
    showRehearsal: mockShowRehearsal,
  }),
}));
vi.mock('@/hooks/useAuth', () => ({ useAuth: () => ({ user: { username: 'john.doe' } }) }));
let mockIsMobile = false;
vi.mock('@/hooks/useBreakpoints', () => ({
  useBreakpoints: () => ({ isMobile: mockIsMobile, isTablet: false, isDesktop: !mockIsMobile }),
}));
vi.mock('../LifecycleSpine', () => ({
  LifecycleSpine: ({ compact }: { compact?: boolean }) => (
    <div data-testid="cockpit-lifecycle-spine" data-compact={String(!!compact)} />
  ),
}));
vi.mock('@/components/organizer/Tasks/CustomTaskModal', () => ({
  CustomTaskModal: () => null,
}));

const event = {
  eventCode: 'BAT54',
  title: 'Spring Conference 2025',
  workflowState: 'SPEAKER_IDENTIFICATION',
  confirmedCount: 10,
  registrationCapacity: 100,
  confirmedSpeakersCount: 2,
  maxSpeakerSlots: 8,
  sessionsWithMaterialsCount: 0,
  totalSessionsCount: 4,
  sessions: [],
} as never;

const renderCockpit = (onNavigate = vi.fn()) => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <I18nextProvider i18n={i18n}>
        <CockpitTab event={event} eventCode="BAT54" onNavigate={onNavigate} />
      </I18nextProvider>
    </QueryClientProvider>
  );
};

describe('CockpitTab', () => {
  it('renders the three stacked regions (spine · attention · tiles)', () => {
    renderCockpit();
    expect(screen.getByTestId('cockpit-tab')).toBeInTheDocument();
    expect(screen.getByTestId('cockpit-lifecycle-spine')).toBeInTheDocument();
    expect(screen.getByTestId('cockpit-attention-region')).toBeInTheDocument();
    expect(screen.getByTestId('cockpit-metric-tiles')).toBeInTheDocument();
  });

  it('shows the state "what now" emphasis line', () => {
    renderCockpit();
    expect(screen.getByText(/Fill the speaker pool/i)).toBeInTheDocument();
  });

  it('does NOT render an event-identity block (identity lives in Details, FR7)', () => {
    renderCockpit();
    expect(screen.queryByText('Spring Conference 2025')).not.toBeInTheDocument();
  });

  it('renders the spine non-compact on desktop', () => {
    mockIsMobile = false;
    renderCockpit();
    expect(screen.getByTestId('cockpit-lifecycle-spine')).toHaveAttribute('data-compact', 'false');
  });

  it('collapses the spine to compact on mobile (14.G.2)', () => {
    mockIsMobile = true;
    renderCockpit();
    expect(screen.getByTestId('cockpit-lifecycle-spine')).toHaveAttribute('data-compact', 'true');
    mockIsMobile = false;
  });

  it('shows no rehearsal links when the hook says so (event day, or other states)', () => {
    mockShowRehearsal = false;
    renderCockpit();
    expect(screen.queryByTestId('cockpit-rehearsal')).not.toBeInTheDocument();
  });

  it('shows the rehearsal links before the event day and navigates to the live routes (T8)', async () => {
    mockShowRehearsal = true;
    const onNavigate = vi.fn();
    renderCockpit(onNavigate);
    expect(screen.getByTestId('cockpit-rehearsal')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /presentation/i }));
    expect(onNavigate).toHaveBeenCalledWith({
      kind: 'route',
      path: '/present/BAT54',
      newTab: true,
    });
    await userEvent.click(screen.getByRole('button', { name: /live control/i }));
    expect(onNavigate).toHaveBeenCalledWith({
      kind: 'route',
      path: '/organizer/events/BAT54/live-control',
    });
    mockShowRehearsal = false;
  });
});
