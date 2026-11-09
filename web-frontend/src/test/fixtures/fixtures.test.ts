/**
 * Guard of the production-shaped fixture set (Story 17.0d): every shape that production has and
 * ASCII-only test data hid stays in the set. One test per shape, so a removed shape fails by name.
 * The backend twin is shared-kernel `ProductionShapedPeopleTest`.
 */
import { describe, expect, it, vi } from 'vitest';
import {
  aUser,
  events,
  namesakes,
  people,
  REGISTRATION_STATUSES,
  registrations,
  shapes,
  type Event,
  type Registration,
  type UserResponse,
} from './index';

/** The username rule of the backend (`SlugGenerationService.USERNAME_PATTERN`, `chk_username_format`). */
const USERNAME_PATTERN = /^[a-z]+\.[a-z]+(\.[0-9]+)?$/;
const DEFAULT_PAGE_LIMIT = 20;

const fullName = (u: UserResponse) => `${u.firstName} ${u.lastName}`;
const domainOf = (email: string) => email.slice(email.lastIndexOf('@') + 1);
const localPartOf = (email: string) => email.slice(0, email.lastIndexOf('@'));

interface FixtureSet {
  people: readonly UserResponse[];
  events: Event[];
  registrations: Registration[];
}

const SHAPES: Array<[shape: string, present: (set: FixtureSet) => boolean]> = [
  [
    'a Vietnamese name with precomposed diacritics (Hông Ân, NFC)',
    ({ people }) =>
      people.some(
        (u) => /[ôÂâÔ]/.test(u.firstName) && u.firstName === u.firstName.normalize('NFC')
      ),
  ],
  ['a typographic apostrophe (O’Neill)', ({ people }) => people.some((u) => /’/.test(fullName(u)))],
  ['a CJK character (王)', ({ people }) => people.some((u) => /\p{Script=Han}/u.test(fullName(u)))],
  [
    'an umlaut in a surname (Brünnimann)',
    ({ people }) => people.some((u) => /[äöü]/.test(u.lastName)),
  ],
  [
    'a mixed-case e-mail address',
    ({ people }) => people.some((u) => u.email !== u.email.toLowerCase()),
  ],
  [
    'an internationalised e-mail domain with an ASCII local part',
    ({ people }) =>
      people.some(
        (u) =>
          // eslint-disable-next-line no-control-regex
          /[^\x00-\x7F]/.test(domainOf(u.email)) && /^[\x00-\x7F]+$/.test(localPartOf(u.email))
      ),
  ],
  [
    'two people with the identical full name',
    ({ people }) => new Set(people.map(fullName)).size < people.length,
  ],
  [
    `more than ${DEFAULT_PAGE_LIMIT} people called "Matthias" (more than one page)`,
    ({ people }) => people.filter((u) => u.firstName === 'Matthias').length > DEFAULT_PAGE_LIMIT,
  ],
  [
    'a tie in the sort key (same last name, different people)',
    ({ people }) => new Set(people.map((u) => u.lastName)).size < people.length,
  ],
  [
    'a tie in createdAt',
    ({ people }) => new Set(people.map((u) => u.createdAt)).size < people.length,
  ],
  ['a past event', ({ events }) => events.some((e) => Date.parse(e.date) < Date.now())],
  ['a future event', ({ events }) => events.some((e) => Date.parse(e.date) > Date.now())],
  [
    'every registration status of the generated union',
    ({ registrations }) =>
      REGISTRATION_STATUSES.every((s) => registrations.some((r) => r.status === s)),
  ],
];

const fixtureSet = (): FixtureSet => ({ people, events: events(), registrations: registrations() });

describe('production-shaped fixtures', () => {
  it.each(SHAPES)('contains %s', (shape, present) => {
    expect(present(fixtureSet()), `the fixture set lost: ${shape}`).toBe(true);
  });

  it('positive control: the guard rejects ASCII-only, unique, small data', () => {
    const typical: FixtureSet = {
      people: [
        aUser({
          id: 'john.doe',
          firstName: 'John',
          lastName: 'Doe',
          email: 'john.doe@example.com',
        }),
      ],
      events: [],
      registrations: [],
    };
    const missing = SHAPES.filter(([, present]) => !present(typical)).map(([shape]) => shape);
    expect(missing).toHaveLength(SHAPES.length);
  });

  it('has unique usernames and unique e-mail addresses ignoring case', () => {
    expect(new Set(people.map((u) => u.id)).size).toBe(people.length);
    expect(new Set(people.map((u) => u.email.toLowerCase())).size).toBe(people.length);
  });

  it('has usernames the backend accepts, the namesake with a numeric suffix', () => {
    expect(people.filter((u) => !USERNAME_PATTERN.test(u.id)).map((u) => u.id)).toEqual([]);
    expect(shapes.identicalNames.map((u) => u.id)).toEqual([
      'matthias.keller',
      'matthias.keller.2',
    ]);
  });

  it('fits the backend columns (company_id is VARCHAR(12))', () => {
    expect(people.filter((u) => (u.companyId ?? '').length > 12).map((u) => u.id)).toEqual([]);
  });

  it('stores every name in NFC, as the backend does', () => {
    expect(
      people.filter((u) => fullName(u) !== fullName(u).normalize('NFC')).map((u) => u.id)
    ).toEqual([]);
  });

  it('keeps personal data out: example domains only', () => {
    expect(
      people
        .map((u) => domainOf(u.email).toLowerCase())
        .filter((d) => !/^(example\.com|[^.]+\.example)$/.test(d))
    ).toEqual([]);
  });

  it('exposes the namesakes as a subset of the people', () => {
    expect(namesakes.every((n) => people.includes(n))).toBe(true);
  });

  it('dates events relative to the clock, not to a calendar date', () => {
    const before = events().map((e) => e.date);
    const later = Date.now() + 400 * 24 * 60 * 60 * 1000;
    vi.useFakeTimers({ toFake: ['Date'] });
    try {
      vi.setSystemTime(later);
      expect(events().map((e) => e.date)).not.toEqual(before);
      expect(events().some((e) => Date.parse(e.date) > later)).toBe(true);
      expect(events().some((e) => Date.parse(e.date) < later)).toBe(true);
    } finally {
      vi.useRealTimers();
    }
  });
});
