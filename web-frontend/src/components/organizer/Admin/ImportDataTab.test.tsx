/**
 * ImportDataTab Tests (Story 10.1 - Task 3)
 *
 * Tests:
 * - AC3: Renders 5 import cards (Events, Sessions, Companies, Speakers, Participants)
 * - AC3: Clicking each button opens the corresponding modal
 */

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { ImportDataTab } from './ImportDataTab';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, fallback?: string) => fallback ?? key,
  }),
}));

vi.mock('@/components/shared/Event/EventBatchImportModal', () => ({
  EventBatchImportModal: ({ open }: { open: boolean }) =>
    open ? <div data-testid="event-batch-import-modal" /> : null,
}));

vi.mock('@/components/shared/Session/SessionBatchImportModal', () => ({
  SessionBatchImportModal: ({ open }: { open: boolean }) =>
    open ? <div data-testid="session-batch-import-modal" /> : null,
}));

vi.mock('@/components/shared/Company/CompanyBatchImportModal', () => ({
  CompanyBatchImportModal: ({ open }: { open: boolean }) =>
    open ? <div data-testid="company-batch-import-modal" /> : null,
}));

vi.mock('@/components/organizer/UserManagement/SpeakerBatchImportModal', () => ({
  default: ({ open }: { open: boolean }) =>
    open ? <div data-testid="speaker-batch-import-modal" /> : null,
}));

vi.mock('@/components/organizer/UserManagement/ParticipantBatchImportModal', () => ({
  ParticipantBatchImportModal: ({ open }: { open: boolean }) =>
    open ? <div data-testid="participant-batch-import-modal" /> : null,
}));

const createQueryClient = () => new QueryClient({ defaultOptions: { queries: { retry: false } } });

const renderTab = () =>
  render(
    <QueryClientProvider client={createQueryClient()}>
      <ImportDataTab />
    </QueryClientProvider>
  );

describe('ImportDataTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders 5 import cards', () => {
    renderTab();
    expect(screen.getByText('Events')).toBeInTheDocument();
    expect(screen.getByText('Sessions')).toBeInTheDocument();
    expect(screen.getByText('Companies')).toBeInTheDocument();
    expect(screen.getByText('Speakers')).toBeInTheDocument();
    expect(screen.getByText('Participants')).toBeInTheDocument();
  });

  it('opens EventBatchImportModal when Import Events clicked', async () => {
    const user = userEvent.setup();
    renderTab();
    await user.click(screen.getByTestId('import-events-btn'));
    expect(screen.getByTestId('event-batch-import-modal')).toBeInTheDocument();
  });

  it('opens SessionBatchImportModal when Import Sessions clicked', async () => {
    const user = userEvent.setup();
    renderTab();
    await user.click(screen.getByTestId('import-sessions-btn'));
    expect(screen.getByTestId('session-batch-import-modal')).toBeInTheDocument();
  });

  it('opens CompanyBatchImportModal when Import Companies clicked', async () => {
    const user = userEvent.setup();
    renderTab();
    await user.click(screen.getByTestId('import-companies-btn'));
    expect(screen.getByTestId('company-batch-import-modal')).toBeInTheDocument();
  });

  it('opens SpeakerBatchImportModal when Import Speakers clicked', async () => {
    const user = userEvent.setup();
    renderTab();
    await user.click(screen.getByTestId('import-speakers-btn'));
    expect(screen.getByTestId('speaker-batch-import-modal')).toBeInTheDocument();
  });

  it('opens ParticipantBatchImportModal when Import Participants clicked', async () => {
    const user = userEvent.setup();
    renderTab();
    await user.click(screen.getByTestId('import-participants-btn'));
    expect(screen.getByTestId('participant-batch-import-modal')).toBeInTheDocument();
  });

  it('modals start closed', () => {
    renderTab();
    expect(screen.queryByTestId('event-batch-import-modal')).not.toBeInTheDocument();
    expect(screen.queryByTestId('session-batch-import-modal')).not.toBeInTheDocument();
    expect(screen.queryByTestId('company-batch-import-modal')).not.toBeInTheDocument();
    expect(screen.queryByTestId('speaker-batch-import-modal')).not.toBeInTheDocument();
    expect(screen.queryByTestId('participant-batch-import-modal')).not.toBeInTheDocument();
  });
});
