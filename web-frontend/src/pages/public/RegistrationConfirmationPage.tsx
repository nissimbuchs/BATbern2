/**
 * RegistrationConfirmationPage Component (Story 4.1.6)
 *
 * Displays registration confirmation with QR code, calendar export, and social sharing.
 * Features:
 * - Registration summary with confirmation code
 * - QR code for event check-in
 * - Calendar export (.ics download)
 * - Confetti celebration animation
 * - Social sharing buttons
 * - Account linking CTA for anonymous users
 */

import { useEffect, useState } from 'react';
import { useParams, Navigate, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import confetti from 'canvas-confetti';
import { createEvent, EventAttributes } from 'ics';
import { PublicLayout } from '@/components/public/PublicLayout';
import { Button } from '@/components/public/ui/button';
import { Card } from '@/components/public/ui/card';
import { CheckCircle2, Calendar, Share2, Mail, Copy, ArrowLeft, UserPlus } from 'lucide-react';
import { eventApiClient } from '@/services/eventApiClient';
import { useAuth } from '@/hooks/useAuth/useAuth';
import { useCompany } from '@/hooks/useCompany/useCompany';
import { eventCodeOfRegistrationCode } from '@/utils/registrationCode';

const RegistrationConfirmationPage = () => {
  const { t } = useTranslation('registration');
  const params = useParams<{ confirmationCode: string; eventCode?: string }>();
  const confirmationCode = params.confirmationCode;
  const [qrCodeUrl, setQrCodeUrl] = useState<string | null>(null);
  const { isAuthenticated } = useAuth();

  // URL: /registration-confirmation/:confirmationCode. Every registration code starts with
  // its event, so the code alone finds the registration (an explicit :eventCode wins).
  const eventCode =
    params.eventCode || (confirmationCode ? eventCodeOfRegistrationCode(confirmationCode) : null);

  const {
    data: registration,
    isLoading,
    error,
  } = useQuery({
    queryKey: ['registration', eventCode, confirmationCode],
    queryFn: async () => {
      if (!confirmationCode || !eventCode) throw new Error('No confirmation code or event code');
      return eventApiClient.getRegistration(eventCode, confirmationCode);
    },
    enabled: !!confirmationCode && !!eventCode,
  });

  // attendeeCompany is the company key (user_profiles.company_id); the name shown is the
  // company's display name, read from the public GET /companies/{name}. Nothing is shown
  // while it loads or when the company cannot be read: the key is not a name.
  const { data: company } = useCompany(registration?.attendeeCompany ?? '');
  const companyDisplayName = registration?.attendeeCompany ? company?.displayName : undefined;
  const notFound = !!error || !eventCode;

  // QR code feature removed - users now confirm via email
  // This page is deprecated and kept for backwards compatibility only
  // New registrations use /registration-success and /confirm-registration flow
  useEffect(() => {
    // QR code generation removed
    setQrCodeUrl(null);
  }, [confirmationCode, eventCode]);

  // Trigger confetti on successful registration load
  useEffect(() => {
    if (registration) {
      confetti({
        particleCount: 100,
        spread: 70,
        origin: { y: 0.6 },
      });
    }
  }, [registration]);

  // Handle calendar export
  const handleDownloadCalendar = () => {
    if (!registration) return;

    // Parse event date
    const eventDate = registration.eventDate ? new Date(registration.eventDate) : new Date();

    const event: EventAttributes = {
      start: [
        eventDate.getFullYear(),
        eventDate.getMonth() + 1, // ics uses 1-indexed months
        eventDate.getDate(),
        eventDate.getHours(),
        eventDate.getMinutes(),
      ],
      duration: { hours: 3 }, // Default duration
      title: registration.eventTitle || 'BATbern Event',
      description: `Registration confirmed! Your confirmation code: ${registration.registrationCode}`,
      location: 'Kornhausforum, Kornhausplatz 18, 3011 Bern',
      status: 'CONFIRMED',
      busyStatus: 'BUSY',
    };

    createEvent(event, (error, value) => {
      if (error) {
        console.error('Failed to create calendar event:', error);
        return;
      }

      const blob = new Blob([value], { type: 'text/calendar;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `batbern-registration-${registration.registrationCode}.ics`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    });
  };

  // Handle copy confirmation code
  const handleCopyCode = () => {
    if (registration?.registrationCode) {
      navigator.clipboard.writeText(registration.registrationCode);
    }
  };

  // Handle social sharing
  const handleLinkedInShare = () => {
    const text = encodeURIComponent(`Excited to attend ${registration?.eventTitle || 'BATbern'}!`);
    const url = window.location.href;
    window.open(
      `https://www.linkedin.com/sharing/share-offsite/?url=${url}&summary=${text}`,
      '_blank'
    );
  };

  const handleTwitterShare = () => {
    const text = encodeURIComponent(
      `Registered for ${registration?.eventTitle || 'BATbern'}! #BATbern`
    );
    window.open(`https://twitter.com/intent/tweet?text=${text}`, '_blank');
  };

  const handleEmailShare = () => {
    const subject = encodeURIComponent(`I'm attending ${registration?.eventTitle || 'BATbern'}!`);
    const body = encodeURIComponent(
      `I've registered for ${registration?.eventTitle || 'BATbern'}.\n\nCheck out the event: ${window.location.origin}/events/${eventCode}`
    );
    window.location.href = `mailto:?subject=${subject}&body=${body}`;
  };

  // No confirmation code in URL - redirect to homepage
  if (!confirmationCode) {
    return <Navigate to="/" replace />;
  }

  return (
    <PublicLayout>
      <div className="container mx-auto px-4 py-12 min-h-screen max-w-3xl">
        {/* Loading State */}
        {isLoading && (
          <div className="flex justify-center py-24">
            <p className="text-zinc-400">{t('confirmationPage.loading')}</p>
          </div>
        )}

        {/* Error State: lookup failed, or a code of no known shape */}
        {notFound && (
          <div className="text-center py-24">
            <h2 className="text-2xl font-light text-zinc-300 mb-4">
              {t('confirmationPage.notFound.title')}
            </h2>
            <p className="text-zinc-400 mb-8">{t('confirmationPage.notFound.message')}</p>
            <Button asChild variant="outline">
              <Link to="/">
                <ArrowLeft className="h-4 w-4 mr-2" />
                Back to Home
              </Link>
            </Button>
          </div>
        )}

        {/* Success Content */}
        {!isLoading && !notFound && registration && (
          <>
            {/* Success Header */}
            <div className="text-center mb-8">
              <CheckCircle2 className="h-16 w-16 text-green-400 mx-auto mb-4" />
              <h1 className="text-4xl font-light mb-2">{t('confirmation.success.title')}</h1>
              <div className="flex items-center justify-center gap-2 mt-4">
                <p className="text-xl text-zinc-300 font-mono">{registration.registrationCode}</p>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={handleCopyCode}
                  className="h-8 w-8 p-0"
                  aria-label={t('confirmationPage.copyCode')}
                >
                  <Copy className="h-4 w-4" />
                </Button>
              </div>
            </div>

            {/* QR Code Card */}
            {qrCodeUrl && (
              <Card className="p-8 mb-8 text-center">
                <h2 className="text-xl font-light mb-4">{t('confirmationPage.qrCodeTitle')}</h2>
                <div className="inline-block p-4 bg-white rounded-lg mb-4">
                  <img
                    src={qrCodeUrl}
                    alt={t('confirmationPage.qrCodeAlt')}
                    className="w-64 h-64"
                  />
                </div>
                <p className="text-sm text-zinc-400">
                  Present this QR code at the event for quick check-in
                </p>
              </Card>
            )}

            {/* Registration Details Card */}
            <Card className="p-6 mb-8">
              <h2 className="text-xl font-light mb-4">{t('confirmationPage.detailsTitle')}</h2>
              <div className="space-y-2 text-sm">
                <div className="flex justify-between">
                  <span className="text-zinc-400">{t('common:labels.name')}:</span>
                  <span className="text-zinc-100">
                    {registration.attendeeFirstName} {registration.attendeeLastName}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-zinc-400">{t('common:labels.email')}:</span>
                  <span className="text-zinc-100">{registration.attendeeEmail}</span>
                </div>
                {companyDisplayName && (
                  <div className="flex justify-between">
                    <span className="text-zinc-400">{t('common:labels.company')}:</span>
                    <span className="text-zinc-100">{companyDisplayName}</span>
                  </div>
                )}
                <div className="flex justify-between">
                  <span className="text-zinc-400">{t('common:labels.event')}:</span>
                  <span className="text-zinc-100">{registration.eventTitle}</span>
                </div>
                {registration.eventDate && (
                  <div className="flex justify-between">
                    <span className="text-zinc-400">{t('common:labels.date')}:</span>
                    <span className="text-zinc-100">
                      {new Date(registration.eventDate).toLocaleDateString('de-CH', {
                        weekday: 'long',
                        year: 'numeric',
                        month: 'long',
                        day: 'numeric',
                      })}
                    </span>
                  </div>
                )}
              </div>
            </Card>

            {/* Account Linking CTA for Anonymous Users (AC #11) */}
            {!isAuthenticated && (
              <Card
                className="p-6 mb-8 bg-blue-950/30 border-blue-500/30"
                data-testid="account-linking-cta"
              >
                <div className="flex items-start gap-3">
                  <UserPlus className="h-5 w-5 text-blue-400 flex-shrink-0 mt-0.5" />
                  <div className="flex-1">
                    <h3 className="text-lg font-light mb-2">
                      Create an account to manage your registrations
                    </h3>
                    <p className="text-sm text-zinc-400 mb-4">
                      Your registration will be automatically linked when you create an account with{' '}
                      {registration.attendeeEmail}
                    </p>
                    <Button asChild variant="outline" size="sm">
                      {/* The lookup returns the address masked (GHSA-qhqv), so it cannot prefill */}
                      <Link to="/auth/register">
                        <UserPlus className="h-4 w-4 mr-2" />
                        Create Account
                      </Link>
                    </Button>
                  </div>
                </div>
              </Card>
            )}

            {/* Action Buttons */}
            <div className="space-y-4 mb-8">
              <Button onClick={handleDownloadCalendar} size="lg" className="w-full">
                <Calendar className="h-4 w-4 mr-2" />
                Add to Calendar
              </Button>

              {/* Social Sharing */}
              <div className="space-y-3" data-testid="social-sharing">
                <p className="text-sm text-zinc-400 text-center">
                  {t('confirmationPage.shareLabel')}
                </p>
                <div className="grid grid-cols-3 gap-3">
                  <Button variant="outline" onClick={handleLinkedInShare} className="flex-1">
                    <Share2 className="h-4 w-4 mr-2" />
                    LinkedIn
                  </Button>
                  <Button variant="outline" onClick={handleTwitterShare} className="flex-1">
                    <Share2 className="h-4 w-4 mr-2" />
                    Twitter
                  </Button>
                  <Button variant="outline" onClick={handleEmailShare} className="flex-1">
                    <Mail className="h-4 w-4 mr-2" />
                    Email
                  </Button>
                </div>
              </div>
            </div>

            {/* Navigation Links */}
            <div className="grid grid-cols-2 gap-4 mb-8">
              <Button asChild variant="outline">
                <Link to={`/register/${eventCode}`}>{t('confirmationPage.registerAnother')}</Link>
              </Button>
              <Button asChild variant="outline">
                <Link to={`/events/${eventCode}`}>{t('confirmationPage.viewEventDetails')}</Link>
              </Button>
            </div>

            {/* Email Confirmation Notice */}
            <p className="text-center text-sm text-zinc-400">
              A confirmation email has been sent to {registration.attendeeEmail}
            </p>
          </>
        )}
      </div>
    </PublicLayout>
  );
};

export default RegistrationConfirmationPage;
