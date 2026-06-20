/**
 * EventEditionLabel — the "BATbern <n>" edition kicker shown above an event title.
 *
 * Post-event-#2 feedback #15: the edition number should appear before/above the title everywhere
 * the event is shown (public homepage, archive, event detail, speaker "My Sessions", attendee
 * "My Past Events"). Derives the number from `eventNumber` when present, else from the trailing
 * digits of `eventCode` (e.g. "BATbern59" → "BATbern 59"). Renders nothing when neither is usable.
 *
 * "BATbern <n>" is a brand string — intentionally NOT translated (no i18n key). MUI-free so it is
 * safe to use on the Tailwind-only public bundle.
 */
import { type JSX } from 'react';

interface EventEditionLabelProps {
  eventCode?: string | null;
  eventNumber?: number | null;
  className?: string;
  'data-testid'?: string;
}

export function EventEditionLabel({
  eventCode,
  eventNumber,
  className,
  'data-testid': testId = 'event-edition-label',
}: EventEditionLabelProps): JSX.Element | null {
  const parsed =
    eventNumber ?? (eventCode ? Number.parseInt(eventCode.replace(/\D/g, ''), 10) : NaN);
  const label = Number.isFinite(parsed) && parsed > 0 ? `BATbern ${parsed}` : (eventCode ?? '');
  if (!label) return null;
  return (
    <span className={className} data-testid={testId}>
      {label}
    </span>
  );
}

export default EventEditionLabel;
