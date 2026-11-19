/**
 * The calendar day (`YYYY-MM-DD`) of an instant in Bern. A machine key for comparing and grouping
 * days, never shown to a person: `en-CA` is used because it formats as ISO `YYYY-MM-DD`, it is not
 * a display locale (those come from `intlLocale`).
 */
const ZURICH_DAY = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Europe/Zurich',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

export function zurichDay(instant: Date | number | string): string {
  return ZURICH_DAY.format(instant instanceof Date ? instant : new Date(instant));
}
