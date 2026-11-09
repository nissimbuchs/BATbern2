/**
 * Production-shaped people (Story 17.0d): the shared frontend set every test uses by default.
 *
 * Production has `Hông Ân`, `O’Neill`, `王`, mixed-case e-mail addresses, internationalised
 * domains, namesakes and more than 20 people called "Matthias". Test data that is ASCII, lower
 * case, unique and small hid #1034 (umlaut search, 32 "Matthias"), #1104 ("Hông Ân" → 500) and
 * #1107 (mixed-case e-mails). The MSW harness answers `listUsers` and `searchUsers` from this set
 * when a test sets no override (`src/test/msw/fixtureDefaults.ts`); `fixtures.test.ts` guards that
 * every shape stays in it. The backend twin is shared-kernel `ProductionShapedPeople`.
 *
 * Invented names only. E-mail addresses use `example.com` or an IDN under the
 * reserved `.example` TLD (ASCII local part: SES has no SMTPUTF8). Usernames are what
 * `SlugGenerationService` generates (namesakes get `.2`), so they match `chk_username_format`.
 *
 * Typed from `src/types/generated/`: a field the API does not send fails `npm run type-check`.
 */
import type { components } from '../../types/generated/user-api.types';

export type UserResponse = components['schemas']['UserResponse'];
export type UserSearchResponse = components['schemas']['UserSearchResponse'];
type Role = UserResponse['roles'][number];

/** One timestamp for many rows on purpose: a sort on `createdAt` has ties, as in production. */
export const FIXTURE_CREATED_AT = '2025-03-01T09:00:00Z';

/** A user as `GET /users` returns it. Defaults to an ordinary attendee; override what matters. */
export function aUser(overrides: Partial<UserResponse> = {}): UserResponse {
  return {
    id: 'anna.keller',
    email: 'Anna.Keller@Example.COM',
    firstName: 'Anna',
    lastName: 'Keller',
    roles: ['ATTENDEE'],
    active: true,
    hasCognitoAccount: true,
    additionalEmails: [],
    createdAt: FIXTURE_CREATED_AT,
    updatedAt: FIXTURE_CREATED_AT,
    ...overrides,
  };
}

/** The search projection of a user, as `GET /users/search` returns it. */
export function toSearchResult(user: UserResponse): UserSearchResponse {
  return {
    id: user.id,
    email: user.email,
    firstName: user.firstName,
    lastName: user.lastName,
    ...(user.companyId ? { companyId: user.companyId } : {}),
    roles: [...user.roles],
    ...(user.profilePictureUrl ? { profilePictureUrl: user.profilePictureUrl } : {}),
  };
}

const person = (
  id: string,
  firstName: string,
  lastName: string,
  email: string,
  roles: Role[] = ['ATTENDEE'],
  companyId?: string
): UserResponse =>
  aUser({
    id,
    firstName: firstName.normalize('NFC'),
    lastName: lastName.normalize('NFC'),
    email,
    roles,
    ...(companyId ? { companyId } : {}),
  });

/** The named shapes, so a test can say which one it means. */
export const shapes = {
  /** Vietnamese diacritics, stored NFC. Generated `hong.an.meier` once and broke the constraint. */
  vietnamese: person('hongan.meier', 'Hông Ân', 'Meier', 'hongan.meier@example.com', ['SPEAKER']),
  /** Typographic apostrophe (U+2019), as phones type it. */
  apostrophe: person('jeanluc.oneill', 'Jean-Luc', 'O’Neill', 'jeanluc.oneill@example.com', [
    'SPEAKER',
    'ATTENDEE',
  ]),
  /** A CJK surname: the username comes from the e-mail. */
  cjk: person('fang.wang', '芳', '王', 'fang.wang@example.com'),
  /** Capitals in the stored address (#1107: 58 production rows). */
  mixedCaseEmail: person('anna.keller', 'Anna', 'Keller', 'Anna.Keller@Example.COM', ['ORGANIZER']),
  /** An internationalised domain; the local part stays ASCII. */
  idnEmail: person(
    'juerg.brunner',
    'Jürg',
    'Brunner',
    'juerg.brunner@bücherstube.example',
    ['PARTNER'],
    'buecherstube'
  ),
  /** The #1034 target: an umlaut surname among many "Matthias". */
  bruennimann: person(
    'matthias.bruennimann',
    'Matthias',
    'Brünnimann',
    'matthias.bruennimann@example.com',
    ['SPEAKER']
  ),
  /** Two people with the identical full name; the second username gets `.2`. */
  identicalNames: [
    person('matthias.keller', 'Matthias', 'Keller', 'matthias.keller@example.com'),
    person('matthias.keller.2', 'Matthias', 'Keller', 'm.keller@example.com', ['SPEAKER']),
  ] as const,
};

/** Further "Matthias", so the namesakes outnumber the default page limit of 20. */
const MORE_MATTHIAS = [
  'Aebischer',
  'Bachmann',
  'Christen',
  'Dällenbach',
  'Egger',
  'Fankhauser',
  'Gfeller',
  'Hänni',
  'Ingold',
  'Jaggi',
  'Kämpf',
  'Lehmann',
  'Moser',
  'Nussbaum',
  'Oberli',
  'Probst',
  'Rüfenacht',
  'Schenk',
  'Trachsel',
  'Untersee',
  'Vögeli',
  'Wüthrich',
  'Zaugg',
  'Zürcher',
  'Bärtschi',
];

const fold = (s: string) =>
  s
    .toLowerCase()
    .replace(/ä/g, 'ae')
    .replace(/ö/g, 'oe')
    .replace(/ü/g, 'ue')
    .normalize('NFD')
    .replace(/\p{M}/gu, '');

const moreMatthias = MORE_MATTHIAS.map((lastName, i) =>
  person(
    `matthias.${fold(lastName)}`,
    'Matthias',
    lastName,
    `matthias.${fold(lastName)}@example.com`,
    i % 3 === 0 ? ['SPEAKER'] : ['ATTENDEE']
  )
);

/** Everyone called "Matthias": more than the default page limit (20). */
export const namesakes: readonly UserResponse[] = [
  shapes.bruennimann,
  ...shapes.identicalNames,
  ...moreMatthias,
];

/** The whole set, in a fixed order (insertion order, as an unsorted query would return it). */
export const people: readonly UserResponse[] = [
  shapes.mixedCaseEmail,
  shapes.vietnamese,
  shapes.apostrophe,
  shapes.cjk,
  shapes.idnEmail,
  ...namesakes,
];

/** Every distinct first or last name of the set that is not plain ASCII (`Hông Ân`, `O’Neill`, `王` ...). */
export const nonAsciiNames: readonly string[] = [
  ...new Set(people.flatMap((u) => [u.firstName, u.lastName])),
  // eslint-disable-next-line no-control-regex
].filter((name) => /[^\x00-\x7F]/.test(name));

/** Every e-mail address of the set, the mixed-case and the IDN one included. */
export const emails: readonly string[] = people.map((u) => u.email);
