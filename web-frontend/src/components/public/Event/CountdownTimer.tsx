/**
 * CountdownTimer Component (Story 4.1.3)
 * Displays countdown to upcoming event if within 30 days
 */

import { differenceInDays } from 'date-fns';

interface CountdownTimerProps {
  eventDate: Date;
}

export const CountdownTimer = ({ eventDate }: CountdownTimerProps) => {
  const daysUntil = differenceInDays(eventDate, new Date());

  // Only show if event is within 30 days and in the future
  if (daysUntil < 0 || daysUntil > 30) {
    return null;
  }

  return (
    <div className="flex items-center gap-3 animate-in fade-in slide-in-from-top-2 duration-500">
      {/* Pulsing blue dot */}
      <div className="relative">
        <div className="h-3 w-3 rounded-full bg-primary animate-pulse" />
        <div className="absolute inset-0 h-3 w-3 rounded-full bg-primary opacity-75 animate-ping" />
      </div>

      {/* Badge */}
      <div className="text-sm font-light text-zinc-300">
        <span className="text-primary font-medium">Next Event</span>
        {' • '}
        {daysUntil === 0 ? (
          <span className="text-green-400 font-medium">Today!</span>
        ) : daysUntil === 1 ? (
          <span className="text-orange-400 font-medium">Tomorrow!</span>
        ) : (
          <span>{daysUntil} days until event</span>
        )}
      </div>
    </div>
  );
};
