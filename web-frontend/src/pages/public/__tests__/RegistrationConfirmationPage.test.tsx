/**
 * RegistrationConfirmationPage Component Tests (Story 4.1.6)
 *
 * The real page, eventApiClient, companyApiClient and useAuth run against the MSW harness
 * (docs/api specs); the visitor's sign-in state comes from src/test/auth.tsx. Only
 * canvas-confetti (a canvas animation jsdom cannot draw) is replaced.
 */

import { describe, test, expect, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import confetti from 'canvas-confetti';
import RegistrationConfirmationPage from '../RegistrationConfirmationPage';
import { api } from '@/test/msw/api';
import { renderWithAuth, signedInAs, signedOut } from '@/test/auth';
import type { UseAuthReturn } from '@/contexts/AuthContext';
import type { Registration } from '@/types/event.types';

vi.mock('canvas-confetti', () => ({ default: vi.fn() }));

const CODE = 'BATbern142-reg-A3X9K2';

/** What the public lookup returns: the attendee's address masked (GHSA-qhqv). */
const registration: Registration = {
  registrationCode: CODE,
  eventCode: 'BATbern142',
  eventTitle: 'BATbern 142 Architektur im Wandel',
  eventDate: '2026-06-15T16:00:00Z',
  attendeeUsername: 'hong.an.nguyen',
  attendeeFirstName: 'Hông Ân',
  attendeeLastName: 'Nguyễn',
  attendeeEmail: 'h***@example.com',
  attendeeCompany: 'bruennimann',
  status: 'CONFIRMED',
  registrationDate: '2026-05-14T16:00:00Z',
  createdAt: '2026-05-14T16:00:00Z',
  updatedAt: '2026-05-14T16:00:00Z',
};

const renderPage = (code: string = CODE, auth: UseAuthReturn = signedOut()) =>
  renderWithAuth(
    <Routes>
      <Route
        path="/registration-confirmation/:confirmationCode"
        element={<RegistrationConfirmationPage />}
      />
      <Route path="/" element={<div>Home Page</div>} />
    </Routes>,
    { auth, initialEntries: [`/registration-confirmation/${code}`] }
  );

const answerWith = (body: Registration = registration) => api.on('getRegistration', () => body);

describe('RegistrationConfirmationPage', () => {
  describe('Data fetching', () => {
    test('should_lookUpTheRegistrationInItsEvent_when_codeIsWhatEmsCreates', async () => {
      answerWith();

      renderPage();

      await screen.findByText(CODE);
      expect(api.calls('getRegistration')[0].params).toMatchObject({
        eventCode: 'BATbern142',
        registrationCode: CODE,
      });
    });

    test('should_lookUpTheEventOfTheDataLoad_when_codeIsAnExistCode', async () => {
      answerWith({ ...registration, registrationCode: 'BAT60-EXIST-17', eventCode: 'BATbern60' });

      renderPage('BAT60-EXIST-17');

      await screen.findByText('BAT60-EXIST-17');
      expect(api.calls('getRegistration')[0].params.eventCode).toBe('BATbern60');
    });

    test('should_showNotFoundWithoutARequest_when_codeHasNoKnownShape', async () => {
      // The page used to guess `BATbern{YY}` from a `BAT-YYYY-NNNNNN` code no registration has.
      renderPage('BAT-2025-000123');

      expect(await screen.findByText(/registration not found/i)).toBeInTheDocument();
      expect(api.calls('getRegistration')).toHaveLength(0);
    });

    test('should_renderLoadingState_when_fetchingData', () => {
      api.on('getRegistration', () => new Promise<Response>(() => {}));

      renderPage();

      expect(screen.getByText(/loading/i)).toBeInTheDocument();
    });

    test('should_renderNotFound_when_codeIsUnknown', async () => {
      api.on('getRegistration', () =>
        HttpResponse.json(
          { status: 404, error: 'Not Found', message: 'Registration not found' },
          { status: 404 }
        )
      );

      renderPage();

      expect(await screen.findByText(/registration not found/i)).toBeInTheDocument();
    });
  });

  describe('Registration summary', () => {
    test('should_showNameEventAndMaskedAddress_when_registrationLoaded', async () => {
      answerWith();

      renderPage();

      expect(await screen.findByText(/Hông Ân Nguyễn/)).toBeInTheDocument();
      expect(screen.getAllByText('BATbern 142 Architektur im Wandel').length).toBeGreaterThan(0);
      expect(screen.getAllByText(/h\*\*\*@example\.com/).length).toBeGreaterThan(0);
    });

    test('should_showTheCompanysDisplayName_when_attendeeHasACompany', async () => {
      answerWith();
      api.on('getCompany', ({ params }) => ({
        name: params.name,
        displayName: 'Brünnimann & Söhne AG',
        isVerified: true,
        createdAt: '2026-01-15T09:00:00Z',
        updatedAt: '2026-01-15T09:00:00Z',
      }));

      renderPage();

      expect(await screen.findByText('Brünnimann & Söhne AG')).toBeInTheDocument();
      expect(api.calls('getCompany')[0].params.name).toBe('bruennimann');
      // attendeeCompany is the company key, never shown itself.
      expect(screen.queryByText('bruennimann')).not.toBeInTheDocument();
    });

    test('should_showNoCompanyLine_when_attendeeHasNoCompany', async () => {
      answerWith({ ...registration, attendeeCompany: null });

      renderPage();

      // Positive control: the details are there, only the company line is not.
      expect(await screen.findByText(/Hông Ân Nguyễn/)).toBeInTheDocument();
      expect(screen.queryByText(/company:/i)).not.toBeInTheDocument();
      expect(api.calls('getCompany')).toHaveLength(0);
    });

    test('should_showNoCompanyKey_when_companyIsGone', async () => {
      answerWith();
      api.on('getCompany', () =>
        HttpResponse.json({ status: 404, error: 'Not Found', message: 'gone' }, { status: 404 })
      );

      renderPage();

      expect(await screen.findByText(/Hông Ân Nguyễn/)).toBeInTheDocument();
      await waitFor(() => expect(api.calls('getCompany')).toHaveLength(1));
      expect(screen.queryByText('bruennimann')).not.toBeInTheDocument();
      expect(screen.queryByText(/company:/i)).not.toBeInTheDocument();
    });

    test('should_neitherFetchNorShowAQrCode_when_registrationLoaded', async () => {
      answerWith();

      renderPage();

      await screen.findByText(CODE);
      expect(screen.queryByAltText(/qr code/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/present this qr code.*check-in/i)).not.toBeInTheDocument();
      expect(api.allCalls().map((call) => call.key)).not.toContain('getRegistrationQR');
    });
  });

  describe('Actions and links', () => {
    test('should_offerCalendarSharingAndEventLinks_when_registrationLoaded', async () => {
      answerWith();

      renderPage();

      expect(
        await screen.findByRole('button', { name: /calendar|add to calendar/i })
      ).toBeInTheDocument();
      expect(screen.getByTestId('social-sharing')).toBeInTheDocument();
      expect(screen.getByRole('link', { name: /register another/i })).toHaveAttribute(
        'href',
        '/register/BATbern142'
      );
      expect(screen.getByRole('link', { name: /view event|event details/i })).toBeInTheDocument();
    });

    test('should_triggerConfetti_when_registrationLoaded', async () => {
      answerWith();

      renderPage();

      await waitFor(() => expect(confetti).toHaveBeenCalled());
    });
  });

  describe('Account linking CTA (AC #11)', () => {
    test('should_offerAnAccount_when_visitorIsSignedOut', async () => {
      answerWith();

      renderPage();

      const cta = await screen.findByTestId('account-linking-cta');
      expect(cta).toHaveTextContent('h***@example.com');
      expect(cta).toHaveTextContent(/Create an account to manage your registrations/);
      expect(screen.getByText(/automatically linked/i)).toBeInTheDocument();
      // The lookup returns the address masked (GHSA-qhqv), so the link does not prefill it.
      expect(screen.getByRole('link', { name: /create account/i })).toHaveAttribute(
        'href',
        '/auth/register'
      );
    });

    test('should_hideTheAccountOffer_when_visitorIsSignedIn', async () => {
      answerWith();

      renderPage(CODE, signedInAs('attendee'));

      expect(await screen.findByText(/Hông Ân Nguyễn/)).toBeInTheDocument();
      expect(screen.queryByTestId('account-linking-cta')).not.toBeInTheDocument();
    });
  });
});
