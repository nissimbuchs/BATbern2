/**
 * SpeakerMagicLoginPage Component (Story 9.1)
 *
 * Landing page for JWT magic link authentication.
 * Accessed via: /speaker-portal/magic-login?jwt={jwtToken}
 *
 * Flow:
 * 1. Extract ?jwt= param from URL
 * 2. POST to /api/v1/auth/speaker-magic-login
 * 3. On success: redirect to /speaker-portal/dashboard?token={sessionToken}
 * 4. On error: show error message with organizer contact info
 *
 * Uses session bridge pattern (Story 9.1 Dev Notes):
 * JWT validates identity → backend issues opaque VIEW token → redirect to existing dashboard.
 * SpeakerDashboardPage.tsx is NOT changed in this story.
 */

import { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { PublicLayout } from '@/components/public/PublicLayout';
import { Card } from '@/components/public/ui/card';
import { Loader2, AlertCircle, Mail } from 'lucide-react';
import { speakerAuthService } from '@/services/speakerAuthService';

type PageState = 'loading' | 'redirecting' | 'error';

const SpeakerMagicLoginPage = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const jwt = searchParams.get('jwt');

  const [pageState, setPageState] = useState<PageState>('loading');
  const [errorMessage, setErrorMessage] = useState<string>(
    'Dieser Link ist nicht mehr gültig. Bitte kontaktiere den Organisator.'
  );

  useEffect(() => {
    if (!jwt) {
      setPageState('error');
      return;
    }

    speakerAuthService
      .validateMagicLink(jwt)
      .then((response) => {
        setPageState('redirecting');
        navigate(`/speaker-portal/dashboard?token=${response.sessionToken}`);
      })
      .catch((error) => {
        const message =
          error?.response?.data?.message ||
          'Dieser Link ist nicht mehr gültig. Bitte kontaktiere den Organisator.';
        setErrorMessage(message);
        setPageState('error');
      });
  }, [jwt, navigate]);

  return (
    <PublicLayout>
      <div className="min-h-screen flex items-center justify-center p-4">
        <Card className="max-w-md w-full p-8 text-center">
          {(pageState === 'loading' || pageState === 'redirecting') && (
            <div>
              <Loader2 className="h-12 w-12 animate-spin mx-auto text-blue-600 mb-4" />
              <p className="text-gray-600">
                {pageState === 'loading'
                  ? 'Anmeldung wird verarbeitet...'
                  : 'Weiterleitung zum Dashboard...'}
              </p>
            </div>
          )}
          {pageState === 'error' && (
            <div>
              <AlertCircle className="h-12 w-12 mx-auto text-red-500 mb-4" />
              <h2 className="text-xl font-semibold text-gray-900 mb-2">Link ungültig</h2>
              <p className="text-gray-600 mb-6">{errorMessage}</p>
              <div className="flex items-center justify-center gap-2 text-sm text-gray-500">
                <Mail className="h-4 w-4" />
                <a href="mailto:events@batbern.ch" className="text-blue-600 hover:underline">
                  events@batbern.ch
                </a>
              </div>
            </div>
          )}
        </Card>
      </div>
    </PublicLayout>
  );
};

export default SpeakerMagicLoginPage;
