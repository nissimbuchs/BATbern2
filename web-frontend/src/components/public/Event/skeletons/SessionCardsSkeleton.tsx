/**
 * SessionCardsSkeleton Component
 * Story 4.1.7: API Optimization & Performance - Task 5
 *
 * Loading skeleton for session cards while session data is being fetched.
 * Shows placeholder cards in a grid layout matching the expected session display.
 */

import { Skeleton } from '@/components/public/ui/ui/skeleton';

export const SessionCardsSkeleton = () => (
  <div className="py-12 px-4 md:px-8 bg-zinc-900">
    <div className="max-w-6xl mx-auto">
      {/* Section title */}
      <Skeleton className="h-8 w-56 mb-8 bg-zinc-800" />

      {/* Session cards grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {[...Array(6)].map((_, index) => (
          <div key={index} className="bg-zinc-950 p-6 rounded-lg border border-zinc-800 space-y-4">
            {/* Session time */}
            <div className="flex items-center gap-2">
              <Skeleton className="h-4 w-4 rounded bg-zinc-800" />
              <Skeleton className="h-4 w-32 bg-zinc-800" />
            </div>

            {/* Session title */}
            <Skeleton className="h-6 w-full bg-zinc-800" />

            {/* Session description */}
            <div className="space-y-2">
              <Skeleton className="h-4 w-full bg-zinc-800" />
              <Skeleton className="h-4 w-full bg-zinc-800" />
              <Skeleton className="h-4 w-3/4 bg-zinc-800" />
            </div>

            {/* Speaker info */}
            <div className="flex items-center gap-3 pt-4">
              <Skeleton className="h-10 w-10 rounded-full bg-zinc-800" />
              <div className="space-y-2">
                <Skeleton className="h-4 w-28 bg-zinc-800" />
                <Skeleton className="h-3 w-24 bg-zinc-800" />
              </div>
            </div>

            {/* Session room/capacity */}
            <div className="flex gap-4 pt-2">
              <Skeleton className="h-4 w-24 bg-zinc-800" />
              <Skeleton className="h-4 w-20 bg-zinc-800" />
            </div>
          </div>
        ))}
      </div>
    </div>
  </div>
);
