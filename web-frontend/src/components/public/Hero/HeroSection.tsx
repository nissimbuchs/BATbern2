/**
 * HeroSection Component
 * From BATbern-public with React Router integration
 * Full-screen hero with Unicorn.studio interactive background
 */

import { useEffect, useState, ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '@/components/public/ui/button';
import { useTranslation } from 'react-i18next';
import { RegistrationWizard } from '@/components/public/Registration/RegistrationWizard';
import { CheckCircle2, Mail } from 'lucide-react';

interface HeroSectionProps {
  title: string;
  date?: string;
  location?: string;
  ctaText?: string;
  ctaLink: string;
  /** Optional event code for inline registration (Story 4.1.5) */
  eventCode?: string;
  unicornProjectId?: string;
  countdownTimer?: ReactNode;
}

export const HeroSection = ({
  title,
  date,
  location,
  ctaText,
  ctaLink,
  eventCode,
  unicornProjectId = 'jfzsiwProJi81qvb7uKX',
  countdownTimer,
}: HeroSectionProps) => {
  const { t } = useTranslation('common');
  const [isRegistrationExpanded, setIsRegistrationExpanded] = useState(false);
  const [pendingRegistration, setPendingRegistration] = useState<{
    email: string;
    eventCode: string;
    expiresAt: number;
  } | null>(null);

  // Check for pending registration in sessionStorage
  useEffect(() => {
    const stored = sessionStorage.getItem('pendingRegistration');
    if (stored && eventCode) {
      try {
        const data = JSON.parse(stored);
        // Check if registration is for this event and not expired
        if (data.eventCode === eventCode && Date.now() < data.expiresAt) {
          setPendingRegistration(data);
        } else if (Date.now() >= data.expiresAt) {
          // Clean up expired registration
          sessionStorage.removeItem('pendingRegistration');
        }
      } catch (err) {
        console.error('Failed to parse pending registration:', err);
        sessionStorage.removeItem('pendingRegistration');
      }
    }
  }, [eventCode]);

  // Format date for display
  const formattedDate = date
    ? new Date(date).toLocaleDateString('de-CH', {
        weekday: 'long',
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      })
    : null;
  // Extend Window interface for UnicornStudio
  interface WindowWithUnicorn extends Window {
    UnicornStudio?: {
      isInitialized: boolean;
      init: () => void;
    };
  }

  // Load Unicorn.studio script
  useEffect(() => {
    const win = window as WindowWithUnicorn;
    if (!win.UnicornStudio) {
      win.UnicornStudio = { isInitialized: false, init: () => {} };
      const script = document.createElement('script');
      script.src =
        'https://cdn.jsdelivr.net/gh/hiunicornstudio/unicornstudio.js@v1.4.34/dist/unicornStudio.umd.js';
      script.onload = function () {
        if (win.UnicornStudio && !win.UnicornStudio.isInitialized) {
          win.UnicornStudio.init();
          win.UnicornStudio.isInitialized = true;
        }
      };
      (document.head || document.body).appendChild(script);
    }
  }, []);

  // Continuously track wizard bottom during animation
  useEffect(() => {
    if (!isRegistrationExpanded) return;

    let animationFrame: number;
    let startTime: number | null = null;
    const animationDuration = 1500; // 1.5 seconds

    const trackWizardBottom = (timestamp: number) => {
      if (!startTime) startTime = timestamp;
      const elapsed = timestamp - startTime;

      const wizardSection = document.getElementById('registration-wizard-section');
      if (wizardSection && elapsed < animationDuration) {
        const rect = wizardSection.getBoundingClientRect();
        // Scroll so bottom of wizard aligns with bottom of viewport
        const scrollTarget = window.pageYOffset + rect.bottom - window.innerHeight;
        window.scrollTo({ top: scrollTarget, behavior: 'instant' });

        animationFrame = requestAnimationFrame(trackWizardBottom);
      }
    };

    animationFrame = requestAnimationFrame(trackWizardBottom);

    return () => {
      if (animationFrame) {
        cancelAnimationFrame(animationFrame);
      }
    };
  }, [isRegistrationExpanded]);

  return (
    <>
      <section
        className={`relative w-full overflow-hidden transition-all duration-500 ease-in-out min-h-screen`}
      >
        {/* Unicorn.studio Interactive Background - Scrolls with hero */}
        <div className="absolute inset-0 z-0 w-full h-full" aria-hidden="true">
          <div data-us-project={unicornProjectId} style={{ width: '100%', height: '100%' }} />
        </div>

        {/* Content */}
        <div className={`relative z-10 w-full flex items-end pb-16 md:pb-20 lg:pb-24 min-h-screen`}>
          <div className="container mx-auto px-4">
            <div className="max-w-4xl">
              <h1 className="text-4xl sm:text-5xl md:text-6xl lg:text-7xl xl:text-8xl font-bold text-foreground leading-tight mb-6">
                {title}
              </h1>

              {/* Date and Location Subtitle */}
              {(formattedDate || location) && (
                <div className="flex flex-wrap items-center gap-4 md:gap-6 text-foreground/90 mb-8">
                  {formattedDate && (
                    <div className="flex items-center gap-2">
                      <svg
                        className="w-5 h-5 md:w-6 md:h-6"
                        fill="none"
                        stroke="currentColor"
                        viewBox="0 0 24 24"
                        xmlns="http://www.w3.org/2000/svg"
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={2}
                          d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"
                        />
                      </svg>
                      <span className="text-base md:text-lg lg:text-xl font-medium">
                        {formattedDate}
                      </span>
                    </div>
                  )}
                  {location && (
                    <div className="flex items-center gap-2">
                      <svg
                        className="w-5 h-5 md:w-6 md:h-6"
                        fill="none"
                        stroke="currentColor"
                        viewBox="0 0 24 24"
                        xmlns="http://www.w3.org/2000/svg"
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={2}
                          d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z"
                        />
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={2}
                          d="M15 11a3 3 0 11-6 0 3 3 0 016 0z"
                        />
                      </svg>
                      <span className="text-base md:text-lg lg:text-xl font-medium">
                        {location}
                      </span>
                    </div>
                  )}
                </div>
              )}

              <div className="flex flex-col sm:flex-row items-start sm:items-center gap-4">
                {pendingRegistration ? (
                  // Show "already registered" message
                  <div className="bg-green-900/20 border border-green-800 rounded-lg p-4 max-w-md">
                    <div className="flex items-start gap-3">
                      <CheckCircle2 className="h-5 w-5 text-green-400 flex-shrink-0 mt-0.5" />
                      <div>
                        <p className="text-green-400 font-medium mb-1">
                          {t('registration.alreadyRegistered.title')}
                        </p>
                        <p className="text-sm text-zinc-300 mb-2">
                          {t('registration.alreadyRegistered.pending')}{' '}
                          <span className="font-mono text-green-400">
                            {pendingRegistration.email}
                          </span>
                        </p>
                        <div className="flex items-center gap-2 text-xs text-zinc-400">
                          <Mail className="h-3 w-3" />
                          <span>{t('registration.alreadyRegistered.checkEmail')}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                ) : eventCode ? (
                  // Inline registration mode: expand wizard below
                  <Button
                    size="lg"
                    className="text-base md:text-lg"
                    onClick={() => setIsRegistrationExpanded(true)}
                    disabled={isRegistrationExpanded}
                  >
                    {ctaText || t('public.register')}
                  </Button>
                ) : (
                  // Fallback to link mode (when eventCode not provided)
                  <Button size="lg" className="text-base md:text-lg" asChild>
                    <Link to={ctaLink}>{ctaText || t('public.register')}</Link>
                  </Button>
                )}
                {countdownTimer && <div className="ml-0 sm:ml-4">{countdownTimer}</div>}
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Inline Registration (Story 4.1.5) - Grows from bottom */}
      {isRegistrationExpanded && eventCode && (
        <section
          id="registration-wizard-section"
          className="relative z-20 overflow-hidden"
          style={{
            animation: 'slideUp 1.5s ease-out forwards',
          }}
        >
          <div className="container mx-auto px-4 py-16">
            <RegistrationWizard
              eventCode={eventCode}
              inline={true}
              onCancel={() => setIsRegistrationExpanded(false)}
            />
          </div>
        </section>
      )}
    </>
  );
};
