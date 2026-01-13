/**
 * SpeakerGridSkeleton Component
 * Story 4.1.7: API Optimization & Performance - Task 5
 *
 * Loading skeleton for the speaker grid while speaker data is being fetched.
 * Shows a grid of placeholder cards matching the expected speaker layout.
 */

import { Skeleton } from '@/components/public/ui/ui/skeleton';

export const SpeakerGridSkeleton = () => (
  <div className="py-12 px-4 md:px-8 bg-zinc-950">
    <div className="max-w-6xl mx-auto">
      {/* Section title */}
      <Skeleton className="h-8 w-48 mb-8 bg-zinc-800" />

      {/* Speaker cards grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
        {[...Array(8)].map((_, index) => (
          <div key={index} className="flex flex-col items-center space-y-3">
            {/* Speaker photo */}
            <Skeleton className="h-32 w-32 rounded-full bg-zinc-800" />

            {/* Speaker name */}
            <Skeleton className="h-5 w-32 bg-zinc-800" />

            {/* Speaker company */}
            <Skeleton className="h-4 w-28 bg-zinc-800" />

            {/* Speaker session title */}
            <Skeleton className="h-4 w-full bg-zinc-800" />
          </div>
        ))}
      </div>
    </div>
  </div>
);
