/**
 * EventHeroSkeleton Component
 * Story 4.1.7: API Optimization & Performance - Task 5
 *
 * Loading skeleton for the event hero section while event data is being fetched.
 * Improves perceived performance by showing structure before content loads.
 */

import { Skeleton } from '@/components/public/ui/ui/skeleton';

export const EventHeroSkeleton = () => (
  <div className="relative min-h-[500px] bg-gradient-to-br from-zinc-900 via-zinc-950 to-black p-8 md:p-12">
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Event title skeleton */}
      <Skeleton className="h-12 md:h-16 w-3/4 bg-zinc-800" />

      {/* Event date and location */}
      <div className="flex flex-wrap gap-4">
        <Skeleton className="h-6 w-48 bg-zinc-800" />
        <Skeleton className="h-6 w-56 bg-zinc-800" />
      </div>

      {/* Event description */}
      <div className="space-y-2 mt-6">
        <Skeleton className="h-4 w-full bg-zinc-800" />
        <Skeleton className="h-4 w-full bg-zinc-800" />
        <Skeleton className="h-4 w-2/3 bg-zinc-800" />
      </div>

      {/* Call-to-action button */}
      <div className="mt-8">
        <Skeleton className="h-12 w-40 bg-zinc-800" />
      </div>
    </div>
  </div>
);
