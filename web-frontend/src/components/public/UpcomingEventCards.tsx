/**
 * The upcoming-event cards of the homepage section "Nächste BAT-Termine", shared with the attendee
 * dashboard (story 3, 2026-11-13). Which events get which card: `useUpcomingEventCards`.
 *
 * - `PublishedEventCard`: an event in the Public Events read model. The full EventCard with link,
 *   topic, lineup or agenda, the visitor's registration chip and self-nomination.
 * - `PlannedEventCard`: an event without a published phase. Code, title and date in the same card
 *   style, no link. Nothing else of an unpublished event is shown (2026-06-11: unpublished details
 *   leaked).
 */

import { useMyRegistration } from '@/hooks/useMyRegistration';
import { EventCard } from '@/components/public/EventCard';
import { Card, CardHeader, CardTitle } from '@/components/public/ui/card';
import { Badge } from '@/components/public/ui/badge';
import type { EventDetailUI } from '@/types/event.types';

/**
 * Per-card wrapper that calls useMyRegistration individually (rules of hooks).
 * Story 10.10, AC5 / T10.5.
 */
export function PublishedEventCard({ event }: { event: EventDetailUI }) {
  const { data: myReg } = useMyRegistration(event.eventCode);
  return (
    <div className="min-w-0" data-testid="upcoming-event-card">
      <EventCard
        event={event}
        viewMode="grid"
        linkPrefix="/events/"
        myRegistrationStatus={myReg?.status}
        // Story 7.2 "I Could Speak on That": attendees can self-nominate from upcoming-event cards.
        enableSelfNomination
      />
    </div>
  );
}

/** An event without a published phase: code, title and date in the EventCard style, no link. */
export function PlannedEventCard({
  eventCode,
  title,
  date,
}: {
  eventCode: string;
  title: string;
  date: string;
}) {
  const formattedDate = new Date(date).toLocaleDateString('de-CH', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });
  return (
    <div className="min-w-0" data-testid="upcoming-event-date-card">
      <Card className="bg-zinc-700 border-zinc-600 h-full">
        <CardHeader>
          <div className="mb-2">
            <Badge
              variant="outline"
              className="bg-blue-400/10 text-blue-400 border-blue-400/20 font-mono"
            >
              {eventCode}
            </Badge>
          </div>
          <CardTitle className="font-light text-xl text-zinc-100 wrap-anywhere">{title}</CardTitle>
          <p className="text-sm text-zinc-400 mt-2">{formattedDate}</p>
        </CardHeader>
      </Card>
    </div>
  );
}
