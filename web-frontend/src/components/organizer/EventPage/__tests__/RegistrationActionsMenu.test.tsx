/**
 * RegistrationActionsMenu tests — focus on the 14.G.4 ⋯ overflow variant.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { EventParticipant } from '../../../../types/eventParticipant.types';
import RegistrationActionsMenu from '../RegistrationActionsMenu';

vi.mock('../../../../services/api/eventRegistrationService', () => ({
  cancelRegistration: vi.fn().mockResolvedValue({}),
  deleteRegistration: vi.fn().mockResolvedValue({}),
  resendConfirmationEmail: vi.fn().mockResolvedValue({}),
}));
vi.mock('@/hooks/useBreakpoints', () => ({
  useBreakpoints: () => ({ isMobile: true, isTablet: false, isDesktop: false }),
}));
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: string | Record<string, unknown>) =>
      typeof opts === 'string' ? opts : ((opts?.defaultValue as string) ?? key),
  }),
}));

const participant = {
  eventCode: 'BAT54',
  registrationCode: 'REG-1',
  firstName: 'Ada',
  lastName: 'Lovelace',
  status: 'REGISTERED',
} as unknown as EventParticipant;

const renderMenu = () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <RegistrationActionsMenu participant={participant} variant="overflow" />
    </QueryClientProvider>
  );
};

beforeEach(() => vi.clearAllMocks());

describe('RegistrationActionsMenu — overflow variant (14.G.4)', () => {
  it('renders a single ⋯ button (not inline icons)', () => {
    renderMenu();
    expect(screen.getByTestId('registration-actions-overflow')).toBeInTheDocument();
  });

  it('opens a menu exposing Resend / Cancel / Delete', () => {
    renderMenu();
    fireEvent.click(screen.getByTestId('registration-actions-overflow'));
    expect(
      screen.getByText('eventPage.participantTable.actions.resendConfirmation')
    ).toBeInTheDocument();
    expect(screen.getByText('eventPage.participantTable.actions.cancel')).toBeInTheDocument();
    expect(screen.getByText('eventPage.participantTable.actions.delete')).toBeInTheDocument();
  });

  it('Delete from the menu opens the confirm dialog', () => {
    renderMenu();
    fireEvent.click(screen.getByTestId('registration-actions-overflow'));
    fireEvent.click(screen.getByText('eventPage.participantTable.actions.delete'));
    expect(screen.getByText('eventPage.participantTable.deleteDialog.title')).toBeInTheDocument();
  });
});

/**
 * Inline variant (desktop table rows) — issue #955.
 *
 * The nightly renders the organizer UI in German (i18n `fallbackLng: 'de'`, and the detector
 * order is ['localStorage','htmlTag'] with no `navigator`, so a fresh browser context always
 * falls back). The E2E spec located these controls by the English accessible name
 * `getByLabel('Cancel Registration')`, which cannot resolve there — 41 consecutive red
 * nightlies. These tests pin the locale-neutral testids the spec now uses, so the E2E
 * locators cannot silently drift again.
 */
describe('RegistrationActionsMenu — inline variant (#955 locale-neutral testids)', () => {
  const renderInline = (status = 'REGISTERED') => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
      <QueryClientProvider client={qc}>
        <RegistrationActionsMenu
          participant={{ ...participant, status } as unknown as EventParticipant}
        />
      </QueryClientProvider>
    );
  };

  it('exposes stable testids for resend, cancel and delete', () => {
    renderInline();
    expect(screen.getByTestId('registration-action-resend')).toBeInTheDocument();
    expect(screen.getByTestId('registration-action-cancel')).toBeInTheDocument();
    expect(screen.getByTestId('registration-action-delete')).toBeInTheDocument();
  });

  it('hides the cancel action for an already-cancelled registration', () => {
    renderInline('CANCELLED');
    expect(screen.queryByTestId('registration-action-cancel')).not.toBeInTheDocument();
    expect(screen.getByTestId('registration-action-delete')).toBeInTheDocument();
  });

  it('exposes a testid on the delete confirm button', () => {
    renderInline();
    fireEvent.click(screen.getByTestId('registration-action-delete'));
    expect(screen.getByTestId('registration-delete-confirm')).toBeInTheDocument();
    expect(screen.getByTestId('registration-delete-dismiss')).toBeInTheDocument();
  });
});
