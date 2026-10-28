/**
 * CapacityIndicator Component
 * Story 10.11: Venue Capacity Enforcement & Waitlist Management (AC7)
 *
 * Displays a capacity badge on public event pages:
 * - When registrationCapacity is null (unlimited): renders nothing
 * - When spotsRemaining > 0: green badge "X spots remaining"
 * - When spotsRemaining === 0: amber badge "Full — join waitlist". A visitor who already holds a
 *   registration sees their status instead, in the hero card's words: "You're registered!" or
 *   "You're on the waitlist".
 */

import { useTranslation } from 'react-i18next';
import { Badge } from '@/components/public/ui/badge';
import type { RegistrationStatus } from '@/components/public/RegistrationStatusBanner';

interface CapacityIndicatorProps {
  registrationCapacity?: number | null;
  spotsRemaining?: number | null;
  waitlistCount?: number | null;
  /** The visitor's own registration for this event, if any */
  registrationStatus?: RegistrationStatus | null;
}

export function CapacityIndicator({
  registrationCapacity,
  spotsRemaining,
  waitlistCount,
  registrationStatus,
}: CapacityIndicatorProps) {
  const { t } = useTranslation(['events', 'common']);

  // No capacity limit configured → render nothing
  if (registrationCapacity == null) {
    return null;
  }

  if (spotsRemaining == null || spotsRemaining > 0) {
    // Spots available
    const count = spotsRemaining ?? 0;
    return (
      <Badge className="bg-green-900/60 text-green-300 border border-green-700 px-3 py-1 text-sm">
        {t('capacity.spotsRemaining', { count })}
      </Badge>
    );
  }

  // Event is full, but the visitor already has a seat: say so, as the hero card does
  if (registrationStatus === 'CONFIRMED' || registrationStatus === 'REGISTERED') {
    return (
      <Badge className="bg-green-900/60 text-green-300 border border-green-700 px-3 py-1 text-sm">
        {t('common:registration.alreadyRegistered.title')}
      </Badge>
    );
  }

  if (registrationStatus === 'WAITLIST') {
    return (
      <Badge className="bg-amber-900/60 text-amber-300 border border-amber-700 px-3 py-1 text-sm">
        {t('capacity.onWaitlist')}
      </Badge>
    );
  }

  // Event is full
  return (
    <div className="flex items-center gap-2 flex-wrap">
      <Badge className="bg-amber-900/60 text-amber-300 border border-amber-700 px-3 py-1 text-sm">
        {t('capacity.fullJoinWaitlist')}
      </Badge>
      {waitlistCount != null && waitlistCount > 0 && (
        <Badge className="bg-zinc-800 text-zinc-400 border border-zinc-700 px-3 py-1 text-sm">
          {t('capacity.waitlistCount', { count: waitlistCount })}
        </Badge>
      )}
    </div>
  );
}
