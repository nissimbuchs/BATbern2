/**
 * RegistrationStatusBanner Component
 * Story 10.10: Registration Status Indicator for Logged-in Users (T8, AC2, AC3, AC4)
 *
 * Shows a status banner below the hero section when an authenticated user has a
 * registration for the current event. Includes a loading skeleton to prevent CLS.
 */

import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Alert from '@mui/material/Alert';
import Skeleton from '@mui/material/Skeleton';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import HourglassTopIcon from '@mui/icons-material/HourglassTop';
import QueueIcon from '@mui/icons-material/Queue';
import CancelIcon from '@mui/icons-material/Cancel';

export type RegistrationStatus = 'REGISTERED' | 'CONFIRMED' | 'WAITLIST' | 'CANCELLED';

interface RegistrationStatusBannerProps {
  /** Registration status. null = not registered (render nothing). undefined = loading/no data. */
  status: RegistrationStatus | null | undefined;
  /** Event code used to build the manage-registration link */
  eventCode: string;
  /** When true, renders a skeleton placeholder instead of the banner */
  isLoading: boolean;
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
}: RegistrationStatusBannerProps) {
  const { t } = useTranslation('registration');

  // AC4: Loading skeleton — same height as the banner, prevents CLS
  if (isLoading) {
    return (
      <Skeleton
        variant="rectangular"
        height={56}
        sx={{ borderRadius: 1, my: 2 }}
        data-testid="registration-status-banner-skeleton"
      />
    );
  }

  // No banner when not registered or no data
  if (status === null || status === undefined) {
    return null;
  }

  const manageLink = `/register/${eventCode}`;

  const config: Record<
    RegistrationStatus,
    {
      severity: 'success' | 'warning' | 'info' | 'error';
      icon: React.ReactElement;
      textKey: string;
      linkKey: string;
    }
  > = {
    CONFIRMED: {
      severity: 'success',
      icon: <CheckCircleIcon fontSize="small" />,
      textKey: 'registrationStatusBanner.confirmed',
      linkKey: 'registrationStatusBanner.manageLink',
    },
    REGISTERED: {
      severity: 'warning',
      icon: <HourglassTopIcon fontSize="small" />,
      textKey: 'registrationStatusBanner.registered',
      linkKey: 'registrationStatusBanner.manageLink',
    },
    WAITLIST: {
      severity: 'info',
      icon: <QueueIcon fontSize="small" />,
      textKey: 'registrationStatusBanner.waitlist',
      linkKey: 'registrationStatusBanner.manageLink',
    },
    CANCELLED: {
      severity: 'error',
      icon: <CancelIcon fontSize="small" />,
      textKey: 'registrationStatusBanner.cancelled',
      linkKey: 'registrationStatusBanner.registerAgain',
    },
  };

  const { severity, icon, textKey, linkKey } = config[status];

  return (
    <Alert
      severity={severity}
      icon={icon}
      data-testid="registration-status-banner"
      sx={{ my: 2 }}
      action={
        <Link
          to={manageLink}
          style={{ color: 'inherit', textDecoration: 'underline', whiteSpace: 'nowrap' }}
        >
          {t(linkKey)}
        </Link>
      }
    >
      {t(textKey)}
    </Alert>
  );
}
