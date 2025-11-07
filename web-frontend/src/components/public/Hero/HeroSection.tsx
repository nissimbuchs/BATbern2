/**
 * HeroSection Component
 * From BATbern-public with React Router integration
 * Full-screen hero with Unicorn.studio interactive background
 */

import { useEffect, ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Button } from "@/components/public/ui/button";
import { useTranslation } from 'react-i18next';

interface HeroSectionProps {
  title: string;
  date?: string;
  location?: string;
  ctaText?: string;
  ctaLink: string;
  unicornProjectId?: string;
  countdownTimer?: ReactNode;
}

export const HeroSection = ({
  title,
  date,
  location,
  ctaText,
  ctaLink,
  unicornProjectId = 'jfzsiwProJi81qvb7uKX',
  countdownTimer
}: HeroSectionProps) => {
  const { t } = useTranslation('common');
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
      const script = document.createElement("script");
      script.src = "https://cdn.jsdelivr.net/gh/hiunicornstudio/unicornstudio.js@v1.4.34/dist/unicornStudio.umd.js";
      script.onload = function() {
        if (win.UnicornStudio && !win.UnicornStudio.isInitialized) {
          win.UnicornStudio.init();
          win.UnicornStudio.isInitialized = true;
        }
      };
      (document.head || document.body).appendChild(script);
    }
  }, []);

  return (
    <section className="relative h-screen w-full overflow-hidden">
      {/* Unicorn.studio Interactive Background */}
      <div
        className="absolute inset-0 z-0 w-full h-full"
        aria-hidden="true"
      >
        <div
          data-us-project={unicornProjectId}
          style={{ width: '100%', height: '100%' }}
        />
      </div>

      {/* Content */}
      <div className="relative z-10 w-full h-full flex items-end pb-16 md:pb-20 lg:pb-24">
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
            <Button size="lg" className="text-base md:text-lg" asChild>
              <Link to={ctaLink}>{ctaText || t('public.register')}</Link>
            </Button>
            {countdownTimer && (
              <div className="ml-0 sm:ml-4">
                {countdownTimer}
              </div>
            )}
          </div>
        </div>
        </div>
      </div>
    </section>
  );
};
