/**
 * RegistrationStatusBanner Component
 * Story 10.10: Registration Status Indicator for Logged-in Users (T8, AC2, AC3, AC4)
 *
 * Shows a status banner below the hero section when an authenticated user has a
 * registration for the current event. Includes a loading skeleton to prevent CLS.
 *
 * Tailwind/shadcn + lucide (no MUI): this component renders eagerly on the public
 * HomePage, so it must not pull @mui/material into the public bundle. The colours
 * mirror the dark-theme status chips used elsewhere (see EventCard STATUS_CHIP_STYLES).
 */

import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { CheckCircle2, Hourglass, Layers, XCircle } from 'lucide-react';
import { Skeleton } from '@/components/public/ui/skeleton';

export type RegistrationStatus = 'REGISTERED' | 'CONFIRMED' | 'WAITLIST' | 'CANCELLED' | 'ATTENDED';

interface RegistrationStatusBannerProps {
  /** Registration status. null = not registered (render nothing). undefined = loading/no data. */
  status: RegistrationStatus | null | undefined;
  /** Event code used to build the manage-registration link */
  eventCode: string;
  /** When true, renders a skeleton placeholder instead of the banner */
  isLoading: boolean;
  /** AC13 (Story 10.11): Position on the waitlist (1-based). Only relevant when status=WAITLIST. */
  waitlistPosition?: number | null;
}

/**
 * RegistrationStatusBanner
 *
 * AC4: While loading, shows a skeleton div with the same dimensions as the banner.
 *      This prevents cumulative layout shift after the banner resolves.
 * AC3: Banner includes a "Manage Registration" link (or "Register again" for CANCELLED).
 */
export function RegistrationStatusBanner({
  status,
  eventCode,
  isLoading,
  waitlistPosition,
}: RegistrationStatusBannerProps) {
  const { t } = useTranslation('registration');

  // AC4: Loading skeleton — same height as the banner (56px), prevents CLS
  if (isLoading) {
    return (
      <Skeleton
        className="my-4 h-14 w-full rounded"
        data-testid="registration-status-banner-skeleton"
      />
    );
  }

  // No banner when not registered or no data
  if (status === null || status === undefined) {
    return null;
  }

  const manageLink = `/register/${eventCode}`;

  // AC2: CANCELLED = grey banner.
  if (status === 'CANCELLED') {
    return (
      <div
        data-testid="registration-status-banner"
        data-status="CANCELLED"
        role="status"
        className="my-4 flex items-center gap-2 rounded-md border border-zinc-600/30 bg-zinc-500/15 px-4 py-2.5 text-zinc-400"
      >
        <XCircle className="h-5 w-5 flex-shrink-0 text-zinc-500" />
        <span className="flex-1">{t('registrationStatusBanner.cancelled')}</span>
        <Link
          to={manageLink}
          data-testid="registration-manage-link"
          className="whitespace-nowrap text-inherit underline"
        >
          {t('registrationStatusBanner.registerAgain')}
        </Link>
      </div>
    );
  }

  const config: Record<
    Exclude<RegistrationStatus, 'CANCELLED'>,
    { className: string; icon: ReactNode; textKey: string; linkKey: string }
  > = {
    CONFIRMED: {
      className: 'border-green-400/30 bg-green-400/15 text-green-300',
      icon: <CheckCircle2 className="h-5 w-5 flex-shrink-0 text-green-400" />,
      textKey: 'registrationStatusBanner.confirmed',
      linkKey: 'registrationStatusBanner.manageLink',
    },
    REGISTERED: {
      className: 'border-amber-400/30 bg-amber-400/15 text-amber-300',
      icon: <Hourglass className="h-5 w-5 flex-shrink-0 text-amber-400" />,
      textKey: 'registrationStatusBanner.registered',
      linkKey: 'registrationStatusBanner.manageLink',
    },
    WAITLIST: {
      className: 'border-blue-400/30 bg-blue-400/15 text-blue-300',
      icon: <Layers className="h-5 w-5 flex-shrink-0 text-blue-400" />,
      textKey:
        waitlistPosition != null
          ? 'registrationStatusBanner.waitlistWithPosition'
          : 'registrationStatusBanner.waitlist',
      linkKey: 'registrationStatusBanner.manageLink',
    },
    ATTENDED: {
      className: 'border-green-400/30 bg-green-400/15 text-green-300',
      icon: <CheckCircle2 className="h-5 w-5 flex-shrink-0 text-green-400" />,
      textKey: 'registrationStatusBanner.attended',
      linkKey: 'registrationStatusBanner.manageLink',
    },
  };

  const { className, icon, textKey, linkKey } = config[status];

  // AC13: interpolate position for WAITLIST when available
  const bannerText =
    status === 'WAITLIST' && waitlistPosition != null
      ? t(textKey, { position: waitlistPosition })
      : t(textKey);

  return (
    <div
      data-testid="registration-status-banner"
      data-status={status}
      role="status"
      className={`my-4 flex items-center gap-2 rounded-md border px-4 py-2.5 ${className}`}
    >
      {icon}
      <span className="flex-1">{bannerText}</span>
      <Link
        to={manageLink}
        data-testid="registration-manage-link"
        className="whitespace-nowrap text-inherit underline"
      >
        {t(linkKey)}
      </Link>
    </div>
  );
}
