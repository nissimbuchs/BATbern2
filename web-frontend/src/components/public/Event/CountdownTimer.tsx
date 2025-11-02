/**
 * CountdownTimer Component
 * Story 4.1.3: Event Landing Page Hero Section
 */

import { differenceInDays } from 'date-fns';

interface CountdownTimerProps {
  eventDate: Date;
}

export const CountdownTimer = ({ eventDate }: CountdownTimerProps) => {
  const daysUntil = differenceInDays(eventDate, new Date());

  // Only show if event is within 30 days
  if (daysUntil < 0 || daysUntil > 30) {
    return null;
  }

  return (
    <div className="mb-8 flex items-center gap-3">
      {/* Pulsing blue dot */}
      <div className="relative">
        <div className="h-3 w-3 rounded-full bg-blue-400 animate-pulse" />
        <div className="absolute inset-0 h-3 w-3 rounded-full bg-blue-400 opacity-75 animate-ping" />
      </div>

      {/* Badge */}
      <div className="text-sm font-light text-zinc-300">
        <span className="text-blue-400 font-medium">Next Event</span>
        {' • '}
        {daysUntil === 0
          ? 'Today!'
          : `${daysUntil} day${daysUntil === 1 ? '' : 's'} until event`}
      </div>
    </div>
  );
};
