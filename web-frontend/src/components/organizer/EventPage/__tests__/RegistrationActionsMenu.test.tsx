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
