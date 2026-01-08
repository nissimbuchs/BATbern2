/**
 * Cleanup Helper Functions
 *
 * Utilities for cleaning up test data and state after test execution
 */

import { cleanupTestEvent, cleanupOrphanedTestEvents } from './api-helpers';
import { cleanupPhaseScreenshots } from './screenshot-helpers';

export interface CleanupOptions {
  /** Delete test event */
  deleteEvent?: boolean;

  /** Clean up orphaned E2E events */
  cleanupOrphaned?: boolean;

  /** Clean up screenshots for specific phase */
  cleanupScreenshots?: string | string[];

  /** Auth token for API calls */
  authToken: string;

  /** Event code to clean up */
  eventCode?: string;
}

/**
 * Comprehensive cleanup after test execution
 *
 * @param options - Cleanup configuration
 */
export async function performCleanup(options: CleanupOptions): Promise<void> {
  const {
    deleteEvent = true,
    cleanupOrphaned = true,
    cleanupScreenshots,
    authToken,
    eventCode,
  } = options;

  console.log('\n🧹 Starting cleanup...\n');

  // 1. Delete specific test event
  if (deleteEvent && eventCode) {
    try {
      await cleanupTestEvent(authToken, eventCode);
    } catch (error) {
      console.error('Error deleting test event:', error);
    }
  }

  // 2. Clean up orphaned events
  if (cleanupOrphaned) {
    try {
      await cleanupOrphanedTestEvents(authToken);
    } catch (error) {
      console.error('Error cleaning up orphaned events:', error);
    }
  }

  // 3. Clean up screenshots if requested
  if (cleanupScreenshots) {
    try {
      const phases = Array.isArray(cleanupScreenshots) ? cleanupScreenshots : [cleanupScreenshots];

      phases.forEach((phase) => {
        cleanupPhaseScreenshots(phase);
      });
    } catch (error) {
      console.error('Error cleaning up screenshots:', error);
    }
  }

  console.log('\n✅ Cleanup complete\n');
}

/**
 * Cleanup function to use in test.afterAll()
 *
 * Usage:
 * ```typescript
 * test.afterAll(async () => {
 *   await cleanupAfterTests(authToken, testEventCode);
 * });
 * ```
 */
export async function cleanupAfterTests(authToken: string, eventCode?: string): Promise<void> {
  await performCleanup({
    authToken,
    eventCode,
    deleteEvent: true,
    cleanupOrphaned: true,
  });
}

/**
 * Cleanup function for fresh screenshot regeneration
 * Deletes old screenshots before running test
 *
 * Usage:
 * ```typescript
 * test.beforeAll(async () => {
 *   cleanupBeforeScreenshots(['phase-a-setup', 'phase-b-outreach']);
 * });
 * ```
 */
export function cleanupBeforeScreenshots(phases: string[]): void {
  console.log('\n🧹 Cleaning up old screenshots before test run...\n');

  phases.forEach((phase) => {
    try {
      cleanupPhaseScreenshots(phase);
    } catch (error) {
      console.warn(`Warning: Could not clean up screenshots for ${phase}:`, error);
    }
  });

  console.log('\n✅ Screenshot cleanup complete\n');
}
