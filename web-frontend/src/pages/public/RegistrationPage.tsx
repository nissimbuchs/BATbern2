/**
 * RegistrationPage Component (Story 4.1.5 - Task 12)
 *
 * Dedicated page for event registration (secondary UX to inline registration).
 * Displays event header and RegistrationWizard in dedicated page mode.
 */

import { useParams, Navigate } from 'react-router-dom';
import { PublicLayout } from '@/components/public/PublicLayout';
import { RegistrationWizard } from '@/components/public/Registration/RegistrationWizard';
import { eventApiClient } from '@/services/eventApiClient';
import { useQuery } from '@tanstack/react-query';
import { BATbernLoader } from '@components/shared/BATbernLoader';
import { useTranslation } from 'react-i18next';

const RegistrationPage = () => {
  const { t } = useTranslation('events');
  const { eventCode } = useParams<{ eventCode: string }>();

  // Fetch event details for header
  const {
    data: event,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['event', eventCode],
    queryFn: () =>
      eventCode ? eventApiClient.getEvent(eventCode) : Promise.reject('No event code'),
    enabled: !!eventCode,
  });

  // No event code in URL - redirect to homepage
  if (!eventCode) {
    return <Navigate to="/" replace />;
  }

  return (
    <PublicLayout>
      <div className="container mx-auto px-4 py-12 min-h-screen">
        {/* Loading State */}
        {isLoading && (
          <div
            className="flex justify-center py-24"
            role="status"
            aria-label="Loading event details"
          >
            <BATbernLoader size={96} />
          </div>
        )}

        {/* Error State */}
        {error && (
          <div className="max-w-2xl mx-auto text-center py-24">
            <h2 className="text-2xl font-light text-zinc-300 mb-4">
              {t('public.errors.loadFailed')}
            </h2>
            <p className="text-zinc-400">{t('public.errors.checkBackLater')}</p>
          </div>
        )}

        {/* Registration Content */}
        {!isLoading && !error && event && (
          <div className="max-w-4xl mx-auto">
            {/* Event Header */}
            <div className="mb-12 text-center">
              <h1 className="text-3xl md:text-4xl font-light text-zinc-100 mb-4">
                {t('public.registerFor')} {event.title}
              </h1>
              {event.date && (
                <p className="text-lg text-zinc-400">
                  {new Date(event.date).toLocaleDateString('de-CH', {
                    weekday: 'long',
                    year: 'numeric',
                    month: 'long',
                    day: 'numeric',
                  })}
                  {event.venueName && ` • ${event.venueName}`}
                </p>
              )}
            </div>

            {/* Registration Wizard */}
            <RegistrationWizard eventCode={eventCode} inline={false} />
          </div>
        )}
      </div>
    </PublicLayout>
  );
};

export default RegistrationPage;
