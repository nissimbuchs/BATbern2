/**
 * The event a registration code belongs to.
 *
 * Every stored code starts with its event (docs/api/event-registrations-api.openapi.yml,
 * `getRegistration`): `{eventCode}-reg-{suffix}` for every code EMS creates (and the V4
 * backfill), `BAT{n}-EXIST-{m}` / `BAT{n}-NEW-{m}` for the 2026-03 data load, whose event is
 * `BATbern{n}`. Checked against the anonymized local database on 2026-11-09: all 8190 codes
 * map to their own event this way.
 *
 * @returns the event code, or null for a code of no known shape
 */
export function eventCodeOfRegistrationCode(code: string): string | null {
  const created = /^(BATbern[0-9]+)-reg-[A-Za-z0-9]+$/.exec(code);
  if (created) return created[1];
  const loaded = /^BAT([0-9]+)-(?:EXIST|NEW)-[0-9]+$/.exec(code);
  if (loaded) return `BATbern${loaded[1]}`;
  return null;
}
