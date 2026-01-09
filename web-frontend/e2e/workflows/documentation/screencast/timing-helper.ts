/**
 * Timing Helper Functions for Screencast Synchronization
 *
 * These functions synchronize Playwright test execution with pre-recorded narration audio
 * by calculating cumulative timestamps and waiting until the correct moment.
 *
 * Usage:
 *   import { waitForNarration, logNarration, startTimer } from './screencast/timing-helper';
 *
 *   startTimer(); // Call once at the start of the test
 *   logNarration('NARRATION_01', 'Willkommen zur BATbern Event-Management-Plattform');
 *   // Perform action...
 *   await waitForNarration('NARRATION_01', page);
 */

import { Page } from '@playwright/test';
import { timingConfig } from './timing-config';

// Global timer to track script start time
let scriptStartTime: number | null = null;

/**
 * Start the global timer for narration synchronization.
 * Call this once at the beginning of the test.
 */
export function startTimer(): void {
  scriptStartTime = Date.now();
  console.log('🎬 Narration timer started');
}

/**
 * Calculate the cumulative timestamp when a narration should END.
 * This is the sum of all delayAfter values up to and including this marker.
 *
 * @param marker - The narration marker (e.g., "NARRATION_01")
 * @returns Cumulative milliseconds from start, or null if marker not found
 */
function getCumulativeEndTime(marker: string): number | null {
  const markerIndex = timingConfig.findIndex((s) => s.marker === marker);
  if (markerIndex === -1) return null;

  // Sum all delayAfter values up to and including this marker
  let cumulative = 0;
  for (let i = 0; i <= markerIndex; i++) {
    cumulative += timingConfig[i].delayAfter;
  }
  return cumulative;
}

/**
 * Wait for narration segment to complete, synchronized with script start time.
 *
 * Calculates how long to wait based on:
 * - When the script started (scriptStartTime)
 * - When this narration should END (cumulative sum of all previous narrations)
 * - Current elapsed time
 *
 * If we're already past the target time, this returns immediately (min wait = 0).
 * If we're ahead of schedule, we wait until we reach the target time (max wait = full narration duration).
 *
 * @param marker - The narration marker (e.g., "NARRATION_01")
 * @param page - The Playwright page object
 */
export async function waitForNarration(marker: string, page: Page): Promise<void> {
  if (!scriptStartTime) {
    console.warn('⚠️  Script timer not started! Call startTimer() at the beginning of the test.');
    console.warn('   Falling back to simple delay...');

    const segment = timingConfig.find((s) => s.marker === marker);
    if (segment) {
      await page.waitForTimeout(segment.delayAfter);
    } else {
      await page.waitForTimeout(3000);
    }
    return;
  }

  const targetEndTime = getCumulativeEndTime(marker);
  if (targetEndTime === null) {
    console.warn(`⚠️  No timing config for marker: ${marker}`);
    console.warn(`   Using default delay of 3 seconds`);
    await page.waitForTimeout(3000);
    return;
  }

  const currentElapsed = Date.now() - scriptStartTime;
  const timeToWait = Math.max(0, targetEndTime - currentElapsed);

  const segment = timingConfig.find((s) => s.marker === marker);
  const targetSeconds = (targetEndTime / 1000).toFixed(1);
  const waitSeconds = (timeToWait / 1000).toFixed(1);
  const elapsedSeconds = (currentElapsed / 1000).toFixed(1);

  console.log(
    `⏱️  [${marker}] Target: ${targetSeconds}s | Elapsed: ${elapsedSeconds}s | Wait: ${waitSeconds}s`
  );
  console.log(`    "${segment?.description}"`);

  if (timeToWait > 0) {
    await page.waitForTimeout(timeToWait);
  } else {
    console.log(`    ✓ Already past target time, continuing immediately`);
  }
}

/**
 * Log narration marker.
 *
 * Replaces direct console.log calls for narration markers to maintain consistency.
 *
 * @param marker - The narration marker (e.g., "NARRATION_00:00")
 * @param message - The narration message
 */
export function logNarration(marker: string, message: string): void {
  console.log(`[${marker}] ${message}`);
}

/**
 * Verify timing configuration totals.
 *
 * Checks that the sum of all delays is approximately equal to the audio duration.
 * Logs a warning if the difference exceeds 5% tolerance.
 *
 * This is called by the verify-timing.spec.ts test.
 */
export function verifyTiming(): void {
  const totalDelay = timingConfig.reduce((sum, s) => sum + s.delayAfter, 0);
  const totalSeconds = totalDelay / 1000;
  const targetDuration = 740.7; // Audio file duration in seconds

  console.log(`\n=== TIMING VERIFICATION ===`);
  console.log(`Configured segments: ${timingConfig.length}`);
  console.log(`Total delay: ${totalSeconds.toFixed(1)}s`);
  console.log(`Target duration: ${targetDuration}s`);

  const difference = Math.abs(targetDuration - totalSeconds);
  const percentDiff = ((difference / targetDuration) * 100).toFixed(1);

  console.log(`Difference: ${difference.toFixed(1)}s (${percentDiff}%)`);

  const tolerance = targetDuration * 0.05; // 5% tolerance

  if (difference > tolerance) {
    console.warn(
      `\n⚠️  WARNING: Timing difference (${difference.toFixed(1)}s) exceeds 5% tolerance (${tolerance.toFixed(1)}s)`
    );
    console.warn(`   Consider manually adjusting delays in timing-config.ts`);
    console.warn(`\nSegments with longest delays:`);

    // Show top 5 longest delays
    const sorted = [...timingConfig].sort((a, b) => b.delayAfter - a.delayAfter);
    sorted.slice(0, 5).forEach((seg) => {
      console.warn(`   ${seg.marker}: ${(seg.delayAfter / 1000).toFixed(1)}s`);
    });
  } else {
    console.log(`\n✅ Timing within acceptable range`);
  }

  console.log(`\nDetailed breakdown:`);
  timingConfig.forEach((seg) => {
    console.log(
      `  ${seg.marker.padEnd(18)} ${((seg.delayAfter / 1000).toFixed(1) + 's').padStart(6)} - ${seg.description}`
    );
  });
}

/**
 * Get the delay duration for a specific marker.
 *
 * @param marker - The narration marker
 * @returns The delay in milliseconds, or 0 if marker not found
 */
export function getDelayForMarker(marker: string): number {
  return timingConfig.find((s) => s.marker === marker)?.delayAfter || 0;
}

/**
 * Get timing statistics.
 *
 * @returns Object with timing statistics
 */
export function getTimingStats() {
  const totalDelay = timingConfig.reduce((sum, s) => sum + s.delayAfter, 0);
  const avgDelay = totalDelay / timingConfig.length;
  const minDelay = Math.min(...timingConfig.map((s) => s.delayAfter));
  const maxDelay = Math.max(...timingConfig.map((s) => s.delayAfter));

  return {
    segments: timingConfig.length,
    totalDelay: totalDelay / 1000, // in seconds
    avgDelay: avgDelay / 1000,
    minDelay: minDelay / 1000,
    maxDelay: maxDelay / 1000,
    targetDuration: 740.7,
    difference: Math.abs(740.7 - totalDelay / 1000),
  };
}
