/**
 * Playwright Global Teardown (plan §A3)
 * docs/plans/playwright-staging-hardening.md
 *
 * The belt-and-suspenders final sweep — the role Bruno's `99-posttest-cleanup.bru` plays.
 * After the whole run finishes, this deletes any leftover canonical-prefix test rows on
 * staging (= production) that a spec failed to clean up itself (e.g. it crashed before its
 * `afterAll`). Per-spec teardown is still mandatory (plan §"Quality bar" #4); this is the
 * safety net, not the primary cleanup.
 *
 * Auth: cleanup requires ROLE_ORGANIZER, so we resolve the ORGANIZER idToken from the same
 * `~/.batbern/{env}-organizer.json` (or legacy `{env}.json`) file `global-setup.ts` reads.
 * No organizer token → log + skip (the run still passes; nothing was cleaned).
 *
 * Note: server-generated `eventCode`/`sessionSlug` rows (UI-created events/sessions) are
 * NOT reachable by this prefix sweep — those are torn down by per-spec `cleanupByCode`.
 * See test-fixtures-cleanup.ts.
 */

import { sweepAllPrefixes } from './helpers/test-fixtures-cleanup';

async function globalTeardown(): Promise<void> {
  const testEnv = process.env.TEST_ENV || 'development';
  console.log(`[Global Teardown] Environment: ${testEnv} — running final prefix sweep`);

  const fs = await import('fs');
  const os = await import('os');
  const path = await import('path');

  const batbernDir = path.join(os.homedir(), '.batbern');
  const candidates = [
    path.join(batbernDir, `${testEnv}-organizer.json`),
    path.join(batbernDir, `${testEnv}.json`), // legacy organizer file
  ];

  const tokenFile = candidates.find((f) => fs.existsSync(f));
  if (!tokenFile) {
    console.warn(
      `[Global Teardown] ⚠️  No organizer token file (${candidates.join(' | ')}) — skipping sweep`
    );
    return;
  }

  let idToken: string;
  try {
    idToken = JSON.parse(fs.readFileSync(tokenFile, 'utf8')).idToken || '';
  } catch (error) {
    console.warn(
      `[Global Teardown] ⚠️  Could not read ${tokenFile}:`,
      error instanceof Error ? error.message : String(error)
    );
    return;
  }

  // Always tolerant: never let a cleanup hiccup fail the run.
  await sweepAllPrefixes(idToken);
  console.log('[Global Teardown] ✓ Final sweep finished');
}

export default globalTeardown;
