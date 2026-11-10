/**
 * Story 17.0c1 guard: the MSW harness answers every operation with its spec example, so every
 * JSON 2xx response in `docs/api/*.openapi.yml` must have one, it must be valid against the
 * response schema, and it must carry no personal data. New examples come from the local
 * anonymized database: `scripts/dev/capture-spec-examples.ts`.
 */
import { describe, expect, it } from 'vitest';
import { QUALIFIED_KEYS } from './api';
import { listOperations, loadSpecFiles, mediaExamples, validateAgainstSchema } from './openapi';
import { SPEC_DIR } from './server';

const files = loadSpecFiles(SPEC_DIR);
const operations = listOperations(files);
const jsonResponses = operations.flatMap((op) =>
  op.success.filter((r) => r.media).map((r) => ({ op, response: r }))
);

/** BATbern's own role mailboxes (no person behind them), e.g. the per-event moderator alias. */
const ROLE_ALIAS =
  /^(batbern\d+-(moderator|speaker|participants)|info|events|support|replies|partner|ok)@batbern\.ch$/;

function strings(value: unknown): string[] {
  if (typeof value === 'string') return [value];
  if (Array.isArray(value)) return value.flatMap(strings);
  if (value && typeof value === 'object') return Object.values(value).flatMap(strings);
  return [];
}

describe('spec examples (MSW harness defaults)', () => {
  it('reads all public specs (positive control)', () => {
    expect(operations.length).toBeGreaterThan(290);
    expect(jsonResponses.length).toBeGreaterThan(250);
  });

  it('every JSON 2xx response has an example', () => {
    const missing = jsonResponses
      .filter(({ response }) => mediaExamples(response.media).length === 0)
      .map(({ op, response }) => `${op.file} ${op.method} ${op.path} ${response.status}`);
    expect(missing).toEqual([]);
  });

  it('every example is valid against its response schema', () => {
    const invalid = jsonResponses.flatMap(({ op, response }) =>
      mediaExamples(response.media).flatMap(({ name, value }) =>
        validateAgainstSchema(files, op.file, response.media.schema, value).map(
          (error) => `${op.file} ${op.method} ${op.path} ${response.status} ${name}: ${error}`
        )
      )
    );
    expect(invalid).toEqual([]);
  });

  it('the validator rejects a wrong example (positive control)', () => {
    const { op, response } = jsonResponses.find(({ op }) => op.key === 'listCompanies')!;
    expect(validateAgainstSchema(files, op.file, response.media.schema, { data: 'x' })).toEqual([
      "$: missing required property 'pagination'",
      '$.data: expected array, got string',
    ]);
  });

  it('no example carries an e-mail address outside @example.com or a preserved account', () => {
    const leaks = jsonResponses.flatMap(({ op, response }) =>
      mediaExamples(response.media).flatMap(({ value }) =>
        strings(value)
          .flatMap((s) => s.toLowerCase().match(/[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/g) ?? [])
          .filter((email) => !email.endsWith('@example.com') && !ROLE_ALIAS.test(email))
          .map((email) => `${op.file} ${op.method} ${op.path}: ${email}`)
      )
    );
    expect(leaks).toEqual([]);
    const preserved = jsonResponses.filter(({ response }) =>
      /nissim|buchs/i.test(JSON.stringify(mediaExamples(response.media)))
    );
    expect(preserved.map(({ op }) => op.key)).toEqual([]);
  });

  it('qualifies exactly the operationIds two specs share (api.ts QUALIFIED_KEYS)', () => {
    const qualified = operations.filter((op) => op.key !== op.operationId).map((op) => op.key);
    expect(qualified.sort()).toEqual([...QUALIFIED_KEYS].sort());
  });
});
