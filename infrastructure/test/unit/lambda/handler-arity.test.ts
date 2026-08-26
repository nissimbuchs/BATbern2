/**
 * #883: no Cognito trigger handler may be callback-based.
 *
 * ── Why this test exists ────────────────────────────────────────────────────────────────
 * AWS Lambda removed callback-based handlers in Node.js 24, and it classifies a handler by its
 * ARITY — `handler.length === 3` means callback-style — not by whether the function is `async`
 * or returns a Promise. `pre-authentication.ts` was an `async` function that merely DECLARED a
 * third `callback` parameter, and bumping the runtime to nodejs24.x therefore failed EVERY
 * sign-in in production with:
 *
 *   UserLambdaValidationException: PreAuthentication failed with error
 *   ERROR: AWS Lambda has removed support for callback-based function handlers starting with
 *   Node.js 24.
 *
 * ── Why the existing tests could not catch it ──────────────────────────────────────────
 * Two blind spots, and both matter:
 *
 *   1. CDK `Template.fromStack()` assertions check the CloudFormation Runtime property. A
 *      declared runtime says nothing about whether the handler's signature is compatible with it.
 *   2. The per-handler tests import the module and CALL the handler directly. In Jest a 3-arity
 *      async function is perfectly callable — only the Lambda runtime rejects it. So the module
 *      loaded, the handler ran, and every test passed while production sign-in was broken.
 *
 * Arity is the one property that distinguishes the two, and nothing asserted it. Hence this file.
 */
import * as fs from 'fs';
import * as path from 'path';

const TRIGGER_DIR = path.join(__dirname, '../../../lib/lambda/triggers');

const triggerFiles = fs
  .readdirSync(TRIGGER_DIR)
  .filter((f) => f.endsWith('.ts') && !f.endsWith('.d.ts'));

describe('Cognito trigger handler arity (#883)', () => {
  test('should_findTriggerSources_when_directoryScanned', () => {
    // Guard against the suite silently passing because the glob matched nothing.
    expect(triggerFiles.length).toBeGreaterThan(0);
  });

  test.each(triggerFiles)(
    '%s should export an async handler with at most 2 parameters',
    async (file) => {
      const mod = await import(path.join(TRIGGER_DIR, file));
      expect(typeof mod.handler).toBe('function');

      // 3 parameters === callback-based === rejected by the nodejs24 runtime.
      expect(mod.handler.length).toBeLessThanOrEqual(2);
    }
  );

  test.each(triggerFiles)('%s source must not declare a `callback` parameter', (file) => {
    const src = fs.readFileSync(path.join(TRIGGER_DIR, file), 'utf8');

    // Strip comments so the explanatory prose in these files (which necessarily says
    // "callback") cannot fail the assertion — only real declarations count.
    const code = src
      .split('\n')
      .filter((l) => !l.trim().startsWith('//') && !l.trim().startsWith('*'))
      .join('\n')
      // Unrelated Lambda context property, not a handler parameter.
      .replace(/callbackWaitsForEmptyEventLoop/g, '');

    expect(code).not.toMatch(/^\s*callback\s*[,)]/m);
    expect(code).not.toMatch(/callback\s*\(/);
  });
});
