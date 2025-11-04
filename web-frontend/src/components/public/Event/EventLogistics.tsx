/**
 * EventLogistics Component (Story 4.1.3)
 * Displays event logistics information with icons
 */

import { Calendar, Clock, MapPin, Users } from 'lucide-react';
import { format } from 'date-fns';
import type { EventDetail } from '@/types/event.types';
import { useTranslation } from 'react-i18next';

interface EventLogisticsProps {
  event: EventDetail;
}

export const EventLogistics = ({ event }: EventLogisticsProps) => {
  const { t } = useTranslation('events');
  // Parse date string to Date object
  const eventDate = event.date ? new Date(event.date) : null;

  // Extract time from date if available (assuming format includes time)
  const startTime = eventDate ? format(eventDate, 'HH:mm') : null;
  const endTime = '18:30'; // Default end time for now

  return (
    <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
      {/* Date */}
      {eventDate && (
        <div className="flex items-start gap-3">
          <Calendar className="h-5 w-5 text-primary mt-1 flex-shrink-0" />
          <div>
            <p className="text-sm text-zinc-400">{t('public.logistics.date')}</p>
            <p className="text-lg font-light text-zinc-100">
              {format(eventDate, 'PPP')}
            </p>
          </div>
        </div>
      )}

      {/* Time */}
      {startTime && (
        <div className="flex items-start gap-3">
          <Clock className="h-5 w-5 text-primary mt-1 flex-shrink-0" />
          <div>
            <p className="text-sm text-zinc-400">{t('public.logistics.time')}</p>
            <p className="text-lg font-light text-zinc-100">
              {startTime}h - {endTime}h
            </p>
          </div>
        </div>
      )}

      {/* Location */}
      {event.venueName && (
        <div className="flex items-start gap-3">
          <MapPin className="h-5 w-5 text-primary mt-1 flex-shrink-0" />
          <div>
            <p className="text-sm text-zinc-400">{t('public.logistics.location')}</p>
            <p className="text-lg font-light text-zinc-100">{event.venueName}</p>
            {event.venueAddress && (
              <p className="text-sm text-zinc-500 mt-1">{event.venueAddress.split(',')[0]}</p>
            )}
          </div>
        </div>
      )}

      {/* Capacity */}
      {event.venueCapacity && (
        <div className="flex items-start gap-3">
          <Users className="h-5 w-5 text-primary mt-1 flex-shrink-0" />
          <div>
            <p className="text-sm text-zinc-400">{t('public.logistics.capacity')}</p>
            <p className="text-lg font-light text-zinc-100">
              {event.currentAttendeeCount || 0} / {event.venueCapacity}
            </p>
          </div>
        </div>
      )}
    </div>
  );
};
