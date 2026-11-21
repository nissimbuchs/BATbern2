/**
 * useNow: the current time as render input (#611).
 *
 * `Date.now()` in a component body is impure (React's purity rule): two renders of the same
 * props can disagree. This hook reads the clock once, when the component mounts, and, with
 * `refreshMs`, again on an interval, so a page left open (the organizer cockpit across a day
 * boundary) still moves on.
 */
import { useEffect, useState } from 'react';

export function useNow(refreshMs?: number): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!refreshMs) return undefined;
    const id = setInterval(() => setNow(Date.now()), refreshMs);
    return () => clearInterval(id);
  }, [refreshMs]);
  return now;
}
