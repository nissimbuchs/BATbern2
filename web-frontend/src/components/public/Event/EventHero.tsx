/**
 * EventHero Component
 * Story 4.1.3: Event Landing Page Hero Section
 */

import { Link } from 'react-router-dom';
import { Button } from '@/components/public/ui/button';
import { ArrowRight } from 'lucide-react';
import type { EventDetail } from '@/types/event.types';

interface EventHeroProps {
  event: EventDetail;
}

export const EventHero = ({ event }: EventHeroProps) => {
  return (
    <div className="relative overflow-hidden rounded-lg bg-gradient-to-br from-zinc-900 to-zinc-950 p-8 md:p-12">
      <div className="relative z-10 grid gap-8 md:grid-cols-2">
        {/* Event Info */}
        <div>
          <h1 className="text-4xl font-light tracking-tight md:text-5xl">
            {event.title}
          </h1>
          {event.description && (
            <p className="mt-4 text-lg text-zinc-300 font-light leading-relaxed">
              {event.description}
            </p>
          )}
        </div>

        {/* CTA */}
        <div className="flex items-center justify-start md:justify-end">
          <Button
            asChild
            size="lg"
            className="group bg-blue-400 text-zinc-950 hover:bg-blue-500"
          >
            <Link to={`/register/${event.eventCode}`}>
              Register Now
              <ArrowRight className="ml-2 h-4 w-4 transition-transform group-hover:translate-x-1" />
            </Link>
          </Button>
        </div>
      </div>
    </div>
  );
};
