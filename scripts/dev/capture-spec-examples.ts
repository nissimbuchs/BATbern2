/**
 * Captures a response example for every JSON 2xx response in `docs/api/*.openapi.yml` that has
 * none (Story 17.0c1), from the LOCAL dev stack on the anonymized snapshot.
 *
 *   make dev-native-up                                   (gateway on localhost:8000)
 *   ./scripts/auth/refresh-token.sh staging              (organizer token; partner/speaker optional)
 *   cd web-frontend && node ../scripts/dev/capture-spec-examples.ts [--dry-run] [--only <key>]
 *
 * - Refuses any base URL that is not localhost: never run against staging/production.
 * - Only GETs are called. A GET's path parameters are resolved from the list responses already
 *   captured (the parent collection first, then any captured value of the same name). Lists are
 *   trimmed to 2 items.
 * - A write (POST/PUT/PATCH/DELETE) takes the captured example of a GET whose response has the same
 *   schema. Anything not capturable gets an example generated from the schema (its property
 *   examples, enums and formats), reported as `generated` so a person can review it.
 * - Every example is scrubbed: e-mail addresses become `@example.com`, usernames and person names
 *   synthetic, free text (bios, notes, descriptions, bodies) synthetic. The result is checked by
 *   VALUE against the database (every username, e-mail and full name in `user_profiles`) and
 *   against the preserved owner accounts; one hit stops the write.
 * - A `null` on an optional property the schema types non-nullable is dropped (the backend sends
 *   unset fields as `null`; omitting them says the same within the spec). Every example must then
 *   validate against its schema (`src/test/msw/openapi.ts`). A captured response that still does
 *   not is reported as DRIFT (a spec that disagrees with the real API) and replaced by a generated
 *   example; the DRIFT lines are findings for the spec owner.
 * - Responses that already have an example keep it. Generated examples reuse captured sub-objects
 *   of the same `$ref` schema wherever there is one.
 * - `--save-raw <file>` / `--from-raw <file>` keep the unscrubbed captures outside the repository,
 *   so the scrubbing can be re-run without the dev stack.
 * - The example is inserted as text (`examples: default: value:`) after the media object's last
 *   line, so comments and the rest of the file stay byte-identical.
 *
 * Plain TypeScript for Node >= 22.18 type stripping. Run it from `web-frontend/` so `yaml` resolves.
 */
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  listOperations,
  loadSpecFiles,
  mediaExample,
  resolveRef,
  validateAgainstSchema,
} from '../../web-frontend/src/test/msw/openapi.ts';
import type { SpecFile, SpecOperation } from '../../web-frontend/src/test/msw/openapi.ts';
import {
  Document,
  isMap,
  isPair,
  isScalar,
  parseDocument,
} from '../../web-frontend/node_modules/yaml/dist/index.js';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Json = any;

const here = dirname(fileURLToPath(import.meta.url));
const SPEC_DIR = join(here, '..', '..', 'docs', 'api');

// ---------------------------------------------------------------------------------------------
// Arguments
// ---------------------------------------------------------------------------------------------

const args = process.argv.slice(2);
const flag = (name: string) => args.includes(name);
const option = (name: string, fallback: string) => {
  const i = args.indexOf(name);
  return i >= 0 && args[i + 1] ? args[i + 1] : fallback;
};
const BASE_URL = option('--base-url', 'http://localhost:8000/api/v1');
const DRY_RUN = flag('--dry-run');
const ONLY = option('--only', '');
const DB_CONTAINER = option('--db-container', 'batbern-dev-postgres');
const DB_NAME = option('--db-name', 'batbern_development');
/**
 * The raw (unscrubbed) captures, kept locally so the scrubbing can be re-run without the dev
 * stack: `--save-raw` writes them after capturing, `--from-raw` reads them instead of calling the
 * API. Personal data of the local snapshot: keep the file outside the repository.
 */
const SAVE_RAW = option('--save-raw', '');
const FROM_RAW = option('--from-raw', '');

export function assertLocal(url: string): void {
  const host = new URL(url).hostname;
  if (!['localhost', '127.0.0.1', '::1', '[::1]'].includes(host)) {
    throw new Error(
      `Refusing to capture from ${url}: only the local dev stack (localhost) is allowed.`
    );
  }
}

// ---------------------------------------------------------------------------------------------
// Tokens
// ---------------------------------------------------------------------------------------------

function readToken(role: string): string | undefined {
  for (const name of [`staging-${role}.json`, `development-${role}.json`]) {
    try {
      const json = JSON.parse(readFileSync(join(homedir(), '.batbern', name), 'utf8'));
      const token: string | undefined = json.idToken;
      if (!token) continue;
      const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'));
      if (payload.exp * 1000 > Date.now() + 60_000) return token;
    } catch {
      // try the next file
    }
  }
  return undefined;
}

// ---------------------------------------------------------------------------------------------
// Personal data: value sets from the local database, synthetic replacements
// ---------------------------------------------------------------------------------------------

/** Accounts the anonymization keeps as they are; they must never appear in an example. */
const PRESERVED_FRAGMENTS = ['nissim', 'buchs', 'berner-architekten-treffen.ch'];

function psql(sql: string): string[] {
  const out = execFileSync(
    'docker',
    ['exec', DB_CONTAINER, 'psql', '-U', 'postgres', '-d', DB_NAME, '-At', '-c', sql],
    { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }
  );
  return out.split('\n').filter((l) => l.length > 0);
}

interface PiiSets {
  usernames: Set<string>;
  /** lower(email) -> username, so an address and a username of one person map alike. */
  userByEmail: Map<string, string>;
  emails: Set<string>;
  fullNames: Set<string>;
}

function loadPii(): PiiSets {
  const usernames = new Set(psql('SELECT lower(username) FROM user_profiles'));
  const emails = new Set(
    psql(
      `SELECT lower(email) FROM user_profiles WHERE email IS NOT NULL
       UNION SELECT lower(attendee_email) FROM registrations WHERE attendee_email IS NOT NULL`
    )
  );
  const fullNames = new Set(
    psql(
      `SELECT lower(first_name || ' ' || last_name) FROM user_profiles
       WHERE length(first_name) > 1 AND length(last_name) > 1`
    )
  );
  const userByEmail = new Map<string, string>();
  for (const row of psql(
    "SELECT lower(email) || '\t' || lower(username) FROM user_profiles WHERE email IS NOT NULL"
  )) {
    const [email, username] = row.split('\t');
    userByEmail.set(email, username);
  }
  return { usernames, userByEmail, emails, fullNames };
}

const FIRST = ['Anna', 'Ben', 'Carla', 'David', 'Elena', 'Felix', 'Gina', 'Hugo', 'Ines', 'Jonas'];
const LAST = ['Muster', 'Beispiel', 'Probe', 'Vorlage', 'Modell', 'Entwurf', 'Skizze', 'Test'];

class Scrubber {
  private readonly names = new Map<string, number>();
  private readonly pii: PiiSets;

  constructor(pii: PiiSets) {
    this.pii = pii;
  }

  private person(key: string): {
    first: string;
    last: string;
    username: string;
  } {
    let n = this.names.get(key);
    if (n === undefined) {
      n = this.names.size;
      this.names.set(key, n);
    }
    const first = FIRST[n % FIRST.length];
    const last = LAST[Math.floor(n / FIRST.length) % LAST.length];
    return { first, last, username: `${first}.${last}`.toLowerCase() };
  }

  /** One identity per person: an e-mail of a known user resolves to the username. */
  private canonical(value: string): string {
    const lower = value.toLowerCase().trim();
    return this.pii.userByEmail.get(lower) ?? lower;
  }

  email(value: string): string {
    return `${this.person(this.canonical(value)).username}@example.com`;
  }

  username(value: string): string {
    return this.person(this.canonical(value)).username;
  }

  /** Replaces DB usernames and e-mail addresses inside a longer string. */
  inline(value: string): string {
    let out = value.replace(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g, (m) =>
      m.toLowerCase().endsWith('@example.com') && !this.pii.emails.has(m.toLowerCase())
        ? m
        : this.email(m)
    );
    // Session slugs are built from the speaker's name (`batbern60-first-last`).
    out = out.replace(/\b(batbern\d+)-(?!reg-)[a-z]+-[a-z]+(?:-\d+)?\b/g, '$1-anna-muster');
    out = out.replace(/\b[a-z]+\.[a-z]+(?:\.\d+)?\b/gi, (m) =>
      this.pii.usernames.has(m.toLowerCase()) ? this.username(m) : m
    );
    return out;
  }

  /** The scrubbed copy of a JSON value. `key` is the property name the value sits under. */
  scrub(value: Json, key = ''): Json {
    if (Array.isArray(value)) return value.map((v) => this.scrub(v, key));
    if (value && typeof value === 'object') {
      const out: Json = {};
      // Names are replaced as a pair so first/last/full stay consistent within one object.
      const id =
        typeof value.id === 'string' && this.pii.usernames.has(value.id.toLowerCase())
          ? value.id
          : undefined;
      const who =
        typeof value.username === 'string'
          ? value.username
          : (id ??
            (typeof value.email === 'string'
              ? value.email
              : `${value.firstName ?? ''} ${value.lastName ?? ''}`));
      for (const [k, v] of Object.entries(value)) out[k] = this.field(k, v, who);
      return out;
    }
    if (value === null || typeof value !== 'string') return value;
    return this.field(key, value, value);
  }

  private field(key: string, value: Json, who: string): Json {
    if (value === null || value === undefined) return value;
    if (typeof value !== 'string') {
      return typeof value === 'object' ? this.scrub(value, key) : value;
    }
    const k = key.toLowerCase();
    const p = this.person(this.canonical(String(who)));
    if (/e-?mail/.test(k) && value.includes('@')) {
      // The object's own address follows its person; another address (a contact's) its own.
      return this.canonical(value) === this.canonical(String(who))
        ? `${p.username}@example.com`
        : this.email(value);
    }
    if (/^(first_?name|given_?name)$/.test(k) || /firstname$/.test(k)) return p.first;
    if (/^(last_?name|family_?name|surname)$/.test(k) || /lastname$/.test(k)) return p.last;
    if (
      /^(full_?name|speakername|authorname|attendeename|organizername|contactname|personname|recipientname|sendername|fromname|username_display)$/.test(
        k
      ) ||
      (k === 'displayname' && this.pii.fullNames.has(value.toLowerCase())) ||
      (k === 'name' && this.pii.fullNames.has(value.toLowerCase()))
    ) {
      return `${p.first} ${p.last}`;
    }
    if (this.pii.usernames.has(value.toLowerCase())) return this.username(value);
    if (/^(phone|phonenumber|mobile|telephone)$/.test(k)) return '+41 31 000 00 00';
    // An enum-like code (`CONFIRMED`, `NO_RECIPIENTS`) is never personal and must stay valid.
    // Checked after the name, username and e-mail rules, so `lastName: "MEIER"` is still replaced.
    if (/^[A-Z][A-Z0-9_]*$/.test(value)) return value;
    if (
      /^(bio|biography|notes?|comments?|commentary|message|body|htmlbody|textbody|bodyhtml|bodytext|html|text|content|answer|question|reason|feedback|abstract|description|remarks?|minutes|agenda|snippet|preview|plaintext|htmlcontent|textcontent|rawbody|quote)$/.test(
        k
      )
    ) {
      return value.length === 0 ? value : syntheticText(k);
    }
    if (this.pii.fullNames.has(value.toLowerCase())) return `${p.first} ${p.last}`;
    return this.inline(value);
  }

  /** Every personal value still in `value`, by path; empty when clean. */
  leaks(value: Json, path = '$'): string[] {
    if (Array.isArray(value)) return value.flatMap((v, i) => this.leaks(v, `${path}[${i}]`));
    if (value && typeof value === 'object') {
      return Object.entries(value).flatMap(([k, v]) => this.leaks(v, `${path}.${k}`));
    }
    if (typeof value !== 'string') return [];
    const lower = value.toLowerCase();
    const found: string[] = [];
    for (const m of lower.match(/[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/g) ?? []) {
      if (!m.endsWith('@example.com') || this.pii.emails.has(m)) found.push(`${path}: e-mail ${m}`);
    }
    for (const m of lower.match(/\b[a-z]+\.[a-z]+(?:\.\d+)?\b/g) ?? []) {
      if (this.pii.usernames.has(m) && !this.isSynthetic(m)) found.push(`${path}: username ${m}`);
    }
    if (this.pii.fullNames.has(lower) && !this.isSyntheticName(lower)) {
      found.push(`${path}: person name ${value}`);
    }
    for (const fragment of PRESERVED_FRAGMENTS) {
      if (lower.includes(fragment)) found.push(`${path}: preserved account '${fragment}'`);
    }
    return found;
  }

  private isSynthetic(username: string): boolean {
    const [first, last] = username.split('.');
    return (
      FIRST.some((f) => f.toLowerCase() === first) && LAST.some((l) => l.toLowerCase() === last)
    );
  }

  private isSyntheticName(name: string): boolean {
    return this.isSynthetic(name.replace(' ', '.'));
  }
}

function syntheticText(key: string): string {
  if (key.includes('html')) return '<p>Sample text for the API example.</p>';
  if (key === 'bio' || key === 'biography') return 'Software architect with a focus on APIs.';
  if (key === 'abstract' || key === 'description') return 'Sample description for the API example.';
  return 'Sample text for the API example.';
}

// ---------------------------------------------------------------------------------------------
// Fitting captured values to the schema, and the bank of captured sub-values
// ---------------------------------------------------------------------------------------------

function isNullable(files: Map<string, SpecFile>, file: string, schema: Json): boolean {
  const s = resolveRef(files, file, schema).node;
  if (!s || typeof s !== 'object') return true;
  if (s.nullable === true) return true;
  if (Array.isArray(s.type) && s.type.includes('null')) return true;
  if (!s.type && !s.$ref && !s.properties && !s.items && !s.allOf && !s.oneOf && !s.anyOf)
    return true;
  return false;
}

/**
 * Drops a `null` the schema does not allow on a property that is not required: the backend
 * serialises unset optional fields as `null` where the spec types them non-nullable (a known gap,
 * see the report). Omitting the property says the same thing within the spec.
 */
export function fitNulls(
  files: Map<string, SpecFile>,
  file: string,
  schema: Json,
  value: Json
): Json {
  const r = resolveRef(files, file, schema);
  const s = r.node;
  if (!s || typeof s !== 'object' || value === null || typeof value !== 'object') return value;
  if (Array.isArray(value)) {
    return s.items ? value.map((v) => fitNulls(files, r.file, s.items, v)) : value;
  }
  const props: Record<string, Json> = {};
  const required = new Set<string>();
  const collect = (node: Json, f: string) => {
    const n = resolveRef(files, f, node);
    if (!n.node || typeof n.node !== 'object') return;
    for (const [k, v] of Object.entries<Json>(n.node.properties ?? {}))
      props[k] = { schema: v, file: n.file };
    for (const k of n.node.required ?? []) required.add(k);
    for (const sub of n.node.allOf ?? []) collect(sub, n.file);
  };
  collect(schema, file);
  const out: Json = {};
  for (const [k, v] of Object.entries(value)) {
    const prop = props[k];
    if (v === null && prop && !required.has(k) && !isNullable(files, prop.file, prop.schema))
      continue;
    out[k] = prop ? fitNulls(files, prop.file, prop.schema, v) : v;
  }
  return out;
}

/** Captured sub-values by the schema `$ref` they were found under (`file#/pointer`). */
export const refBank = new Map<string, Json>();

function refKey(file: string, ref: string): string {
  const [target, pointer] = ref.split('#');
  return `${target ? target.replace(/^\.\//, '') : file}#${pointer ?? ''}`;
}

function collectRefs(
  files: Map<string, SpecFile>,
  file: string,
  schema: Json,
  value: Json,
  depth = 0
): void {
  if (!schema || typeof schema !== 'object' || value === null || value === undefined || depth > 12)
    return;
  if (typeof schema.$ref === 'string') {
    const key = refKey(file, schema.$ref);
    if (!refBank.has(key) && validateAgainstSchema(files, file, schema, value).length === 0) {
      refBank.set(key, value);
    }
  }
  const r = resolveRef(files, file, schema);
  const s = r.node;
  if (!s || typeof s !== 'object') return;
  for (const sub of s.allOf ?? []) collectRefs(files, r.file, sub, value, depth + 1);
  if (Array.isArray(value)) {
    if (s.items) for (const v of value) collectRefs(files, r.file, s.items, v, depth + 1);
    return;
  }
  if (typeof value === 'object') {
    for (const [k, v] of Object.entries<Json>(value)) {
      if (s.properties?.[k]) collectRefs(files, r.file, s.properties[k], v, depth + 1);
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Trimming and generation
// ---------------------------------------------------------------------------------------------

export function trim(value: Json): Json {
  if (Array.isArray(value)) return value.slice(0, 2).map(trim);
  if (value && typeof value === 'object') {
    const out: Json = {};
    for (const [k, v] of Object.entries(value)) out[k] = trim(v);
    return out;
  }
  if (typeof value === 'string' && value.length > 400) return `${value.slice(0, 400)}`;
  return value;
}

const FORMAT_SAMPLES: Record<string, string> = {
  'date-time': '2026-05-14T16:00:00Z',
  date: '2026-05-14',
  time: '16:00:00',
  uuid: '3f2b8c1e-5d4a-4e6b-9a7c-1b2d3e4f5a6b',
  email: 'anna.muster@example.com',
  uri: 'https://www.batbern.ch/',
  url: 'https://www.batbern.ch/',
  hostname: 'www.batbern.ch',
  binary: '',
  byte: '',
};

/** An example generated from the schema: property examples, enums, formats, minimums. */
export function generate(
  files: Map<string, SpecFile>,
  file: string,
  schema: Json,
  depth = 0,
  key = ''
): Json {
  if (schema && typeof schema.$ref === 'string') {
    const banked = refBank.get(refKey(file, schema.$ref));
    if (banked !== undefined) return banked;
  }
  const r = resolveRef(files, file, schema);
  const s = r.node;
  const f = r.file;
  if (!s || typeof s !== 'object' || depth > 8) return null;
  if (s.example !== undefined) return s.example;
  if (Array.isArray(s.examples) && s.examples.length > 0) return s.examples[0];
  if (s.default !== undefined) return s.default;
  if (Array.isArray(s.enum)) return s.enum.find((e: unknown) => e !== null) ?? s.enum[0];
  if (s.const !== undefined) return s.const;
  if (Array.isArray(s.allOf)) {
    const merged: Json = {};
    for (const sub of s.allOf) {
      const part = generate(files, f, sub, depth + 1, key);
      if (part && typeof part === 'object' && !Array.isArray(part)) Object.assign(merged, part);
    }
    return merged;
  }
  const choice = (s.oneOf ?? s.anyOf)?.find((sub: Json) => {
    const target = resolveRef(files, f, sub).node;
    return target && target.type !== 'null';
  });
  if (choice) return generate(files, f, choice, depth + 1, key);
  const types: string[] = Array.isArray(s.type) ? s.type : s.type ? [s.type] : [];
  const type =
    types.find((t) => t !== 'null') ?? (s.properties ? 'object' : s.items ? 'array' : 'string');
  switch (type) {
    case 'object': {
      const out: Json = {};
      for (const [name, prop] of Object.entries<Json>(s.properties ?? {})) {
        out[name] = generate(files, f, prop, depth + 1, name);
      }
      if (!s.properties && s.additionalProperties && typeof s.additionalProperties === 'object') {
        out.key = generate(files, f, s.additionalProperties, depth + 1, 'key');
      }
      return out;
    }
    case 'array':
      return s.items ? [generate(files, f, s.items, depth + 1, key)] : [];
    case 'integer':
      return typeof s.minimum === 'number' ? Math.max(s.minimum, 1) : 1;
    case 'number':
      return typeof s.minimum === 'number' ? Math.max(s.minimum, 1.5) : 1.5;
    case 'boolean':
      return true;
    default: {
      if (s.format && FORMAT_SAMPLES[s.format] !== undefined) return FORMAT_SAMPLES[s.format];
      if (s.pattern === '^\\d{6}$') return '482910';
      const k = key.toLowerCase();
      if (k.includes('email')) return 'anna.muster@example.com';
      if (k === 'username' || k.endsWith('username') || k.endsWith('by')) return 'anna.muster';
      if (k === 'eventcode') return 'BATbern57';
      if (k.endsWith('url')) return 'https://www.batbern.ch/';
      if (k === 'firstname') return 'Anna';
      if (k === 'lastname') return 'Muster';
      if (k === 'id' || k.endsWith('id')) return FORMAT_SAMPLES.uuid;
      if (k.endsWith('s3key') || k === 'key' || k.endsWith('filekey'))
        return 'uploads/2026/sample/file.png';
      if (k.includes('extension')) return 'png';
      if (k.includes('contenttype') || k.includes('mimetype')) return 'image/png';
      if (k.includes('locale') || k.includes('language')) return 'de';
      if (k.includes('slug')) return 'sample-slug';
      if (k.includes('title') || k.includes('subject')) return 'Sample title';
      if (k.includes('name')) return 'Sample name';
      if (k.includes('message') || k.includes('description') || k.includes('text')) {
        return 'Sample text for the API example.';
      }
      return typeof s.minLength === 'number' && s.minLength > 6
        ? 'x'.repeat(s.minLength)
        : 'sample';
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Capture
// ---------------------------------------------------------------------------------------------

interface Captured {
  op: SpecOperation;
  status: number;
  /** The concrete path that answered, e.g. `/events/BATbern57`. */
  url: string;
  body: Json;
}

/** Values seen in captured responses, by property name. */
class Pool {
  readonly byKey = new Map<string, Set<string>>();

  add(value: Json): void {
    if (Array.isArray(value)) value.forEach((v) => this.add(v));
    else if (value && typeof value === 'object') {
      for (const [k, v] of Object.entries(value)) {
        if (typeof v === 'string' || typeof v === 'number') {
          if (!this.byKey.has(k)) this.byKey.set(k, new Set());
          const set = this.byKey.get(k)!;
          if (set.size < 20) set.add(String(v));
        } else this.add(v);
      }
    }
  }

  get(key: string): string[] {
    return [...(this.byKey.get(key) ?? [])];
  }
}

/** The items of a list response: the array itself, or `data` / `items` / `content`. */
function itemsOf(body: Json): Json[] {
  if (Array.isArray(body)) return body;
  for (const k of ['data', 'items', 'content', 'results']) {
    if (body && Array.isArray(body[k])) return body[k];
  }
  return [];
}

const ALIASES: Record<string, string[]> = {
  eventCode: ['eventCode', 'code'],
  code: ['code', 'eventCode'],
  name: ['name', 'companyName'],
  companyName: ['companyName', 'name'],
  sessionSlug: ['sessionSlug', 'slug'],
  username: ['username', 'speakerUsername', 'attendeeUsername'],
  topicCode: ['topicCode', 'code'],
};

/** Candidate values for path parameter `param`, the parent collection's items first. */
function candidates(param: string, parentBodies: Json[], pool: Pool): string[] {
  const keys = [...(ALIASES[param] ?? [param]), param.endsWith('Id') ? 'id' : ''].filter(Boolean);
  const out: string[] = [];
  for (const body of parentBodies) {
    for (const item of itemsOf(body)) {
      for (const k of keys)
        if (item && item[k] !== undefined && item[k] !== null) out.push(String(item[k]));
    }
  }
  for (const k of keys) out.push(...pool.get(k));
  return [...new Set(out)].slice(0, 4);
}

function queryFor(files: Map<string, SpecFile>, op: SpecOperation): string {
  const doc = files.get(op.file)!.doc;
  const item = doc.paths[op.path];
  const params = [...(item.parameters ?? []), ...(item[op.method.toLowerCase()].parameters ?? [])];
  const parts: string[] = [];
  for (const raw of params) {
    const p = resolveRef(files, op.file, raw).node;
    if (!p || p.in !== 'query') continue;
    const schema = resolveRef(files, op.file, p.schema).node ?? {};
    if (['limit', 'size', 'pageSize'].includes(p.name)) {
      const min = typeof schema.minimum === 'number' ? schema.minimum : 1;
      parts.push(`${p.name}=${Math.max(min, 2)}`);
      continue;
    }
    if (!p.required) continue;
    const value =
      p.example ??
      schema.example ??
      schema.default ??
      (Array.isArray(schema.enum) ? schema.enum[0] : undefined) ??
      generate(files, op.file, p.schema, 0, p.name);
    parts.push(`${encodeURIComponent(p.name)}=${encodeURIComponent(String(value))}`);
  }
  return parts.length ? `?${parts.join('&')}` : '';
}

async function call(url: string, tokens: string[]): Promise<{ status: number; body: Json } | null> {
  for (const token of tokens) {
    const res = await fetch(url, {
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: 'application/json',
        'Accept-Language': 'en',
      },
    });
    const text = await res.text();
    if (res.status === 401 || res.status === 403) continue;
    if (!res.ok) return { status: res.status, body: text.slice(0, 200) };
    try {
      return { status: res.status, body: text ? JSON.parse(text) : null };
    } catch {
      return { status: res.status, body: undefined };
    }
  }
  return null;
}

/** Why a GET could not be captured, by key (the last attempt). */
export const failures = new Map<string, string>();

async function captureGets(
  files: Map<string, SpecFile>,
  ops: SpecOperation[],
  tokens: string[]
): Promise<Map<string, Captured>> {
  const captured = new Map<string, Captured>();
  const pool = new Pool();
  const pending = ops.filter((o) => o.method === 'GET' && o.success.some((r) => r.media));
  // Parameterless paths first, then deeper ones, several passes so values flow down.
  pending.sort((a, b) => a.path.split('/').length - b.path.split('/').length);
  for (let pass = 0; pass < 4; pass++) {
    let progress = false;
    for (const op of pending) {
      if (captured.has(op.key)) continue;
      const segments = op.path.split('/');
      let urls = [''];
      for (let i = 0; i < segments.length; i++) {
        const seg = segments[i];
        const m = /^\{(.+)\}$/.exec(seg);
        if (!m) {
          urls = urls.map((u) => (seg ? `${u}/${seg}` : u));
          continue;
        }
        const next: string[] = [];
        for (const u of urls) {
          const parents = [...captured.values()].filter((c) => c.url === u).map((c) => c.body);
          for (const v of candidates(m[1], parents, pool))
            next.push(`${u}/${encodeURIComponent(v)}`);
        }
        urls = next.slice(0, 6);
      }
      if (urls.length === 0) failures.set(op.key, `${op.path}: no value for a path parameter`);
      for (const u of urls) {
        const result = await call(`${BASE_URL}${u}${queryFor(files, op)}`, tokens);
        if (!result || result.status >= 300) {
          failures.set(
            op.key,
            `${u}${queryFor(files, op)} -> ${result ? `${result.status} ${JSON.stringify(result.body).slice(0, 120)}` : '401/403 for every token'}`
          );
        }
        if (result && result.status >= 200 && result.status < 300 && result.body !== undefined) {
          captured.set(op.key, {
            op,
            status: result.status,
            url: u,
            body: result.body,
          });
          pool.add(result.body);
          progress = true;
          break;
        }
      }
    }
    if (!progress) break;
  }
  return captured;
}

// ---------------------------------------------------------------------------------------------
// Writing: text insertion after the media object's last line
// ---------------------------------------------------------------------------------------------

function exampleBlock(value: Json, indent: number, source: string): string {
  const pad = ' '.repeat(indent);
  // YAML 1.1 output quotes every string a 1.1 parser (SnakeYAML in the Java contract tests) would
  // read as something else: timestamps, yes/no, octal-looking numbers.
  const yaml = new Document(value, { version: '1.1' })
    .toString({ lineWidth: 0 })
    .replace(/\n$/, '');
  const body = yaml
    .split('\n')
    .map((l) => `${pad}      ${l}`)
    .join('\n');
  return `\n${pad}examples:\n${pad}  default:\n${pad}    summary: ${source}\n${pad}    value:\n${body}`;
}

/** Inserts the examples into one spec file's text; returns the new text. */
function insertExamples(
  text: string,
  entries: Array<{
    path: string;
    method: string;
    status: number;
    value: Json;
    source: string;
  }>
): string {
  const doc = parseDocument(text, { keepSourceTokens: true });
  const inserts: Array<{ at: number; text: string }> = [];
  for (const e of entries) {
    const media = doc.getIn([
      'paths',
      e.path,
      e.method.toLowerCase(),
      'responses',
      String(e.status),
      'content',
      'application/json',
    ]);
    const mediaAlt =
      media ??
      doc.getIn([
        'paths',
        e.path,
        e.method.toLowerCase(),
        'responses',
        e.status,
        'content',
        'application/json',
      ]);
    if (!isMap(mediaAlt) || mediaAlt.flow || !mediaAlt.range) {
      throw new Error(`Cannot insert into ${e.method} ${e.path} ${e.status}: not a block mapping`);
    }
    const firstKey = mediaAlt.items[0];
    if (!isPair(firstKey) || !isScalar(firstKey.key) || !firstKey.key.range) {
      throw new Error(`Cannot find the first key of ${e.method} ${e.path} ${e.status}`);
    }
    const keyStart = firstKey.key.range[0];
    const lineStart = text.lastIndexOf('\n', keyStart - 1) + 1;
    const indent = keyStart - lineStart;
    let end = mediaAlt.range[1];
    while (end > 0 && /\s/.test(text[end - 1])) end--;
    const eol = text.indexOf('\n', end);
    inserts.push({
      at: eol < 0 ? text.length : eol,
      text: exampleBlock(e.value, indent, e.source),
    });
  }
  inserts.sort((a, b) => b.at - a.at);
  let out = text;
  for (const i of inserts) out = out.slice(0, i.at) + i.text + out.slice(i.at);
  return out;
}

// ---------------------------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------------------------

function schemaKey(files: Map<string, SpecFile>, file: string, schema: Json): string | undefined {
  if (!schema || typeof schema !== 'object') return undefined;
  if (typeof schema.$ref === 'string') {
    const target = resolveRef(files, file, {
      $ref: schema.$ref.split('#')[0] ? schema.$ref : `${file}${schema.$ref}`,
    });
    return `${target.file}#${schema.$ref.split('#')[1]}`;
  }
  if (schema.type === 'array' && schema.items) {
    const inner = schemaKey(files, file, schema.items);
    return inner ? `array:${inner}` : undefined;
  }
  return undefined;
}

async function main(): Promise<void> {
  assertLocal(BASE_URL);
  const tokens = ['organizer', 'partner', 'speaker'].map(readToken).filter((t): t is string => !!t);
  if (tokens.length === 0 && !FROM_RAW) {
    throw new Error('No valid token in ~/.batbern: run ./scripts/auth/refresh-token.sh staging');
  }
  const files = loadSpecFiles(SPEC_DIR);
  const ops = listOperations(files).filter((o) => !ONLY || o.key === ONLY);
  const scrubber = new Scrubber(loadPii());

  let captured: Map<string, Captured>;
  if (FROM_RAW) {
    const raw: Array<{ key: string; status: number; url: string; body: Json }> = JSON.parse(
      readFileSync(FROM_RAW, 'utf8')
    );
    const byKey = new Map(ops.map((o) => [o.key, o]));
    captured = new Map(
      raw.filter((r) => byKey.has(r.key)).map((r) => [r.key, { ...r, op: byKey.get(r.key)! }])
    );
  } else {
    captured = await captureGets(files, ops, tokens);
  }
  if (SAVE_RAW) {
    if (resolve(SAVE_RAW).startsWith(resolve(here, '..', '..') + '/')) {
      throw new Error('--save-raw must be outside the repository (personal data)');
    }
    writeFileSync(
      SAVE_RAW,
      JSON.stringify(
        [...captured.values()].map((c) => ({
          key: c.op.key,
          status: c.status,
          url: c.url,
          body: c.body,
        }))
      )
    );
  }
  const bySchema = new Map<string, Json>();
  const values = new Map<string, { value: Json; source: string }>();
  for (const c of captured.values()) {
    const media = c.op.success.find((r) => r.status === c.status)?.media;
    const value = fitNulls(files, c.op.file, media?.schema, scrubber.scrub(trim(c.body)));
    collectRefs(files, c.op.file, media?.schema, value);
    values.set(`${c.op.key} ${c.status}`, {
      value,
      source: 'captured from the local anonymized database',
    });
    const key = schemaKey(files, c.op.file, media?.schema);
    if (key && !bySchema.has(key)) bySchema.set(key, value);
  }

  const report = {
    captured: 0,
    reused: 0,
    generated: 0,
    drift: [] as string[],
    leaks: [] as string[],
  };
  const perFile = new Map<
    string,
    Array<{
      path: string;
      method: string;
      status: number;
      value: Json;
      source: string;
    }>
  >();
  for (const op of ops) {
    for (const r of op.success) {
      if (!r.media || mediaExample(r.media).found) continue;
      const id = `${op.key} ${r.status}`;
      let entry = values.get(id);
      let kind: 'captured' | 'reused' | 'generated' = 'captured';
      if (entry) {
        const errors = validateAgainstSchema(files, op.file, r.media.schema, entry.value);
        if (errors.length > 0) {
          report.drift.push(
            `${op.method} ${op.path} ${r.status}: ${errors.slice(0, 4).join('; ')}`
          );
          entry = undefined;
        }
      }
      if (!entry) {
        const key = schemaKey(files, op.file, r.media.schema);
        const reused = key ? bySchema.get(key) : undefined;
        if (reused && validateAgainstSchema(files, op.file, r.media.schema, reused).length === 0) {
          entry = {
            value: reused,
            source: 'captured from the local anonymized database (same schema as a GET)',
          };
          kind = 'reused';
        } else {
          entry = {
            value: scrubber.scrub(generate(files, op.file, r.media.schema)),
            source: 'generated from the schema and captured sub-values',
          };
          kind = 'generated';
          const errors = validateAgainstSchema(files, op.file, r.media.schema, entry.value);
          if (errors.length > 0) {
            report.drift.push(
              `GENERATED ${op.method} ${op.path} ${r.status}: ${errors.slice(0, 4).join('; ')}`
            );
          }
        }
      }
      const leaks = scrubber.leaks(entry.value);
      if (leaks.length > 0) report.leaks.push(`${op.method} ${op.path}: ${leaks.join(', ')}`);
      report[kind]++;
      if (!perFile.has(op.file)) perFile.set(op.file, []);
      perFile.get(op.file)!.push({ path: op.path, method: op.method, status: r.status, ...entry });
    }
  }

  console.log(
    `captured ${report.captured}, reused ${report.reused}, generated ${report.generated}, ` +
      `drift ${report.drift.length}, leaks ${report.leaks.length}`
  );
  for (const [key, why] of failures) if (!captured.has(key)) console.log(`MISS   ${key}: ${why}`);
  for (const d of report.drift) console.log(`DRIFT  ${d}`);
  for (const l of report.leaks) console.log(`LEAK   ${l}`);
  for (const [file, entries] of perFile) {
    for (const e of entries) {
      const kind = e.source.startsWith('generated') ? 'generated' : 'captured';
      console.log(`${kind.padEnd(9)}  ${file}  ${e.method} ${e.path} ${e.status}`);
    }
  }
  if (report.leaks.length > 0) {
    console.error('Personal data found in an example: nothing written.');
    process.exit(1);
  }
  if (DRY_RUN) return;
  for (const [file, entries] of perFile) {
    const path = join(SPEC_DIR, file);
    writeFileSync(path, insertExamples(readFileSync(path, 'utf8'), entries));
  }
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  main().catch((e) => {
    console.error(e instanceof Error ? e.message : e);
    process.exit(1);
  });
}
