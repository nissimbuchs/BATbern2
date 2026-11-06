/**
 * PlaceNumbersBlock (#1238)
 *
 * The one place the public event page shows registration numbers, in "Logistik":
 * total · vergeben (registered + confirmed) · frei · waitlist, so that total = vergeben + frei and
 * the waitlist comes on top. Without a capacity only "N vergeben". Under the numbers: "ausgebucht"
 * when full, "Anmeldung geschlossen" after the deadline and on the event day, no call to action.
 * Replaces the capacity badge and the occupancy figure that showed different numbers (G1–G3).
 *
 * Tailwind only: rendered eagerly on the public HomePage, so no @mui/material.
 */
import { useTranslation } from 'react-i18next';
import { Users } from 'lucide-react';
import type { PlaceNumbers } from '@/pages/public/homePagePhase';

interface PlaceNumbersBlockProps {
  places: PlaceNumbers;
}

function Figure({ value, label, testId }: { value: number; label: string; testId: string }) {
  return (
    <span className="whitespace-nowrap" data-testid={testId}>
      <span className="text-lg font-light text-zinc-100">{value}</span>{' '}
      <span className="text-sm text-zinc-400">{label}</span>
    </span>
  );
}

export function PlaceNumbersBlock({ places }: PlaceNumbersBlockProps) {
  const { t } = useTranslation('events');
  const separator = <span className="text-zinc-600">·</span>;

  return (
    <div className="flex items-start gap-3" data-testid="place-numbers">
      <Users className="h-5 w-5 text-primary mt-1 flex-shrink-0" />
      <div>
        <p className="text-sm text-zinc-400">{t('public.places.title')}</p>
        <p className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
          {places.total != null && (
            <>
              <Figure value={places.total} label={t('public.places.total')} testId="places-total" />
              {separator}
            </>
          )}
          <Figure value={places.taken} label={t('public.places.taken')} testId="places-taken" />
          {places.free != null && (
            <>
              {separator}
              <Figure value={places.free} label={t('public.places.free')} testId="places-free" />
            </>
          )}
          {places.total != null && (
            <>
              {separator}
              <Figure
                value={places.waitlist}
                label={t('public.places.waitlist')}
                testId="places-waitlist"
              />
            </>
          )}
        </p>
        {places.note && (
          <p
            className="mt-1 text-sm text-amber-300"
            data-testid="places-note"
            data-note={places.note}
          >
            {places.note === 'FULL' ? t('public.places.full') : t('public.places.closed')}
          </p>
        )}
      </div>
    </div>
  );
}
