/**
 * Timing Verification Test
 *
 * This test verifies that the timing configuration generated from ElevenLabs
 * alignment data is within acceptable bounds for the screencast recording.
 *
 * Run:
 *   npm run test:e2e -- verify-timing.spec.ts
 */

import { test, expect } from '@playwright/test';
import { verifyTiming, getTimingStats } from './timing-helper';

test.describe('Screencast Timing Verification', () => {
  test('should have timing configuration within 5% tolerance of audio duration', async () => {
    console.log('\n🎬 Verifying screencast timing configuration...\n');

    // Run verification (outputs detailed logs)
    verifyTiming();

    // Get timing statistics
    const stats = getTimingStats();

    console.log('\n📊 Timing Statistics:');
    console.log(`   Total segments: ${stats.segments}`);
    console.log(`   Total delay: ${stats.totalDelay.toFixed(1)}s`);
    console.log(`   Average delay: ${stats.avgDelay.toFixed(1)}s`);
    console.log(`   Min delay: ${stats.minDelay.toFixed(1)}s`);
    console.log(`   Max delay: ${stats.maxDelay.toFixed(1)}s`);
    console.log(`   Target duration: ${stats.targetDuration}s`);
    console.log(`   Difference: ${stats.difference.toFixed(1)}s`);

    // Verify timing is within 5% tolerance
    const tolerance = stats.targetDuration * 0.05;
    const percentDiff = (stats.difference / stats.targetDuration) * 100;

    expect(stats.difference).toBeLessThan(tolerance);

    console.log(`\n✅ Timing verification passed (${percentDiff.toFixed(1)}% difference)`);
  });

  test('should have at least 25 narration segments configured', async () => {
    const stats = getTimingStats();

    expect(stats.segments).toBeGreaterThanOrEqual(25);

    console.log(`✅ Found ${stats.segments} narration segments`);
  });

  test('should have no delays shorter than 2 seconds (too rushed)', async () => {
    const stats = getTimingStats();

    expect(stats.minDelay).toBeGreaterThanOrEqual(2.0);

    console.log(`✅ Minimum delay is ${stats.minDelay.toFixed(1)}s (acceptable)`);
  });

  test('should have no delays longer than 60 seconds (too slow)', async () => {
    const stats = getTimingStats();

    expect(stats.maxDelay).toBeLessThanOrEqual(60.0);

    console.log(`✅ Maximum delay is ${stats.maxDelay.toFixed(1)}s (acceptable)`);
  });
});
