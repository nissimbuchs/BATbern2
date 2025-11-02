/**
 * EventLogistics Component
 * Story 4.1.3: Event Landing Page Hero Section
 */

import { Calendar, Clock, MapPin, Users } from 'lucide-react';
import { format } from 'date-fns';
import type { EventDetail } from '@/types/event.types';

interface EventLogisticsProps {
  event: EventDetail;
}

export const EventLogistics = ({ event }: EventLogisticsProps) => {
  return (
    <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
      {/* Date */}
      <div className="flex items-start gap-3">
        <Calendar className="h-5 w-5 text-blue-400 mt-1" />
        <div>
          <p className="text-sm text-zinc-400">Date</p>
          <p className="text-lg font-light">
            {format(new Date(event.date), 'PPP')}
          </p>
        </div>
      </div>

      {/* Time */}
      <div className="flex items-start gap-3">
        <Clock className="h-5 w-5 text-blue-400 mt-1" />
        <div>
          <p className="text-sm text-zinc-400">Time</p>
          <p className="text-lg font-light">
            {format(new Date(event.date), 'p')}
          </p>
        </div>
      </div>

      {/* Location */}
      <div className="flex items-start gap-3">
        <MapPin className="h-5 w-5 text-blue-400 mt-1" />
        <div>
          <p className="text-sm text-zinc-400">Location</p>
          <p className="text-lg font-light">{event.venueName}</p>
          {event.venueAddress && (
            <p className="text-sm text-zinc-500">{event.venueAddress}</p>
          )}
        </div>
      </div>

      {/* Capacity */}
      <div className="flex items-start gap-3">
        <Users className="h-5 w-5 text-blue-400 mt-1" />
        <div>
          <p className="text-sm text-zinc-400">Attendees</p>
          <p className="text-lg font-light">
            {event.currentAttendeeCount} / {event.venueCapacity}
          </p>
        </div>
      </div>
    </div>
  );
};
