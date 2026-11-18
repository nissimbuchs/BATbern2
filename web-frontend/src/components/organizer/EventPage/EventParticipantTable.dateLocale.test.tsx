/**
 * The registration date on the event's Registrations tab follows the UI language (#1222 item 6):
 * it was formatted with a fixed `de-CH`, so the English UI showed "22. Okt. 2026". Real
 * i18next, network at the wire (MSW), no own-code mocks.
 */
import { describe, it, expect, afterEach } from 'vitest';
import { screen } from '@testing-library/react';
import i18n from '@/i18n/config';
import { renderWithProviders } from '@/test/test-utils';
import type { EventParticipant } from '@/types/eventParticipant.types';
import EventParticipantTable from './EventParticipantTable';

const participant: EventParticipant = {
  registrationCode: 'BATbern60-reg-ab12cd',
  eventCode: 'BATbern60',
  attendeeUsername: 'anna.keller',
  firstName: 'Anna',
  lastName: 'Keller',
  email: 'anna.keller@example.com',
  status: 'CONFIRMED',
  registrationDate: '2026-10-22T10:30:00Z',
};

const renderTable = () =>
  renderWithProviders(<EventParticipantTable participants={[participant]} isLoading={false} />);

describe('EventParticipantTable registration date follows the UI language', () => {
  afterEach(async () => {
    await i18n.changeLanguage('en');
  });

  it('should_showOct22_when_uiIsEnglish', async () => {
    await i18n.changeLanguage('en');
    renderTable();
    expect(await screen.findByText('Oct 22, 2026')).toBeInTheDocument();
    expect(screen.queryByText('22. Okt. 2026')).not.toBeInTheDocument();
  });

  it('should_showTheGermanDate_when_uiIsGerman', async () => {
    await i18n.changeLanguage('de');
    renderTable();
    expect(await screen.findByText('22. Okt. 2026')).toBeInTheDocument();
  });

  it('should_showTheGermanDate_when_uiIsSwissGerman', async () => {
    await i18n.changeLanguage('gsw-BE');
    renderTable();
    expect(await screen.findByText('22. Okt. 2026')).toBeInTheDocument();
  });
});
