/**
 * Screenshot Helper Utilities
 *
 * Provides standardized screenshot capture with automatic organization,
 * retry logic, and consistent naming conventions.
 */

import { Page } from '@playwright/test';
import * as path from 'path';
import * as fs from 'fs';
import { fileURLToPath } from 'url';

export interface ScreenshotOptions {
  /** Screenshot file name (without extension) */
  name: string;

  /** Workflow phase folder (e.g., "phase-a-setup", "phase-b-outreach") */
  phase: string;

  /** Sequence number for ordering (e.g., 1, 2, 3) */
  sequence: number;

  /** Capture full page (default: true) */
  fullPage?: boolean;

  /** Delay before capturing (ms) - useful for animations */
  delay?: number;

  /** Maximum retries on failure */
  maxRetries?: number;

  /** Scroll to top before capturing (default: false) */
  scrollToTop?: boolean;

  /** Scroll modal content to top before capturing (default: false) */
  scrollModal?: boolean;

  /** Skip waiting for networkidle (useful for pages with continuous network activity like iframes) */
  skipNetworkIdle?: boolean;
}

/**
 * Base directory for all documentation screenshots
 * ES module compatible - uses import.meta.url instead of __dirname
 */
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const SCREENSHOTS_BASE_DIR = path.join(
  __dirname,
  '../../../../../docs/user-guide/assets/screenshots/workflow'
);

/**
 * Builds the full path for a screenshot based on options
 */
export function buildScreenshotPath(options: ScreenshotOptions): string {
  const { phase, sequence, name } = options;

  // Ensure phase directory exists
  const phaseDir = path.join(SCREENSHOTS_BASE_DIR, phase);
  if (!fs.existsSync(phaseDir)) {
    fs.mkdirSync(phaseDir, { recursive: true });
  }

  // Format: 01-event-dashboard.png, 02-topic-selection.png, etc.
  const sequenceStr = sequence.toString().padStart(2, '0');
  const fileName = `${sequenceStr}-${name}.png`;

  return path.join(phaseDir, fileName);
}

/**
 * Scrolls the page to the top (y=0)
 * Useful for ensuring modals and content start at the top of screenshots
 */
export async function scrollToTop(page: Page): Promise<void> {
  await page.evaluate(() => {
    window.scrollTo(0, 0);
  });
  await page.waitForTimeout(300); // Wait for scroll to complete
}

/**
 * Scrolls modal dialog content to the top
 * MUI Dialog uses DialogContent as the scrollable container
 */
export async function scrollModalToTop(
  page: Page,
  modalSelector: string = '[role="dialog"]'
): Promise<void> {
  const scrolled = await page.evaluate((selector) => {
    // Find the modal dialog
    const dialog = document.querySelector(selector);
    if (!dialog) {
      console.log('[scrollModalToTop] Dialog not found with selector:', selector);
      return false;
    }

    // MUI Dialog uses DialogContent as the scrollable container (see EventForm.tsx line 627)
    const dialogContent = dialog.querySelector('.MuiDialogContent-root');
    if (dialogContent) {
      console.log(
        '[scrollModalToTop] Found DialogContent, scrolling to top. Current scrollTop:',
        dialogContent.scrollTop
      );
      dialogContent.scrollTop = 0;
      console.log('[scrollModalToTop] After scroll, scrollTop:', dialogContent.scrollTop);
      return true;
    }

    // Fallback: try scrolling the dialog paper itself
    const paper = dialog.querySelector('.MuiDialog-paper');
    if (paper) {
      console.log('[scrollModalToTop] Fallback: Found Dialog paper, scrolling to top');
      paper.scrollTop = 0;
      return true;
    }

    console.log('[scrollModalToTop] No scrollable container found in dialog');
    return false;
  }, modalSelector);

  console.log(`[scrollModalToTop] Scroll completed: ${scrolled}`);
  await page.waitForTimeout(500); // Wait for scroll to complete and render
}

/**
 * Captures a workflow screenshot with retry logic
 *
 * @param page - Playwright Page object
 * @param options - Screenshot configuration
 * @returns Path to the captured screenshot
 */
export async function captureWorkflowScreenshot(
  page: Page,
  options: ScreenshotOptions
): Promise<string> {
  const maxRetries = options.maxRetries ?? 3;
  const fullPage = options.fullPage ?? true;
  const delay = options.delay ?? 500;
  const scrollToTopFirst = options.scrollToTop ?? false;
  const scrollModalFirst = options.scrollModal ?? false;
  const skipNetworkIdle = options.skipNetworkIdle ?? false;

  for (let attempt = 0; attempt < maxRetries; attempt++) {
    try {
      // Wait for page to be fully loaded (skip if page has continuous network activity)
      if (!skipNetworkIdle) {
        await page.waitForLoadState('networkidle', { timeout: 5000 });
      } else {
        // Just wait for DOM to be ready, don't wait for network
        await page.waitForLoadState('domcontentloaded', { timeout: 5000 });
      }

      // Scroll to top if requested
      if (scrollToTopFirst) {
        await scrollToTop(page);
      }

      // Scroll modal to top if requested
      if (scrollModalFirst) {
        await scrollModalToTop(page);
      }

      // Wait for any animations/transitions to complete
      if (delay > 0) {
        await page.waitForTimeout(delay);
      }

      // Hide dynamic content that changes between runs (timestamps, etc.)
      await hideDynamicContent(page);

      // Capture screenshot
      const screenshotPath = buildScreenshotPath(options);
      await page.screenshot({
        path: screenshotPath,
        fullPage: fullPage,
        animations: 'disabled', // Disable CSS animations for consistency
      });

      console.log(`✅ Screenshot captured: ${path.basename(screenshotPath)}`);
      return screenshotPath;
    } catch (error) {
      if (attempt === maxRetries - 1) {
        console.error(`❌ Failed to capture screenshot after ${maxRetries} attempts:`, error);
        throw error;
      }

      console.warn(`⚠️  Screenshot capture attempt ${attempt + 1} failed, retrying...`);
      await page.waitForTimeout(1000); // Wait before retry
    }
  }

  throw new Error('Screenshot capture failed after all retries');
}

/**
 * Captures a screenshot of a modal dialog
 * Waits for modal to be visible before capturing
 * Automatically scrolls modal content to top
 */
export async function captureModalDialog(
  page: Page,
  options: ScreenshotOptions,
  modalSelector: string = '[role="dialog"]'
): Promise<string> {
  // Wait for modal to be visible
  await page.waitForSelector(modalSelector, { state: 'visible', timeout: 10000 });

  // Wait for modal animation to complete
  await page.waitForTimeout(300);

  // Scroll modal content to top (not the page)
  await scrollModalToTop(page, modalSelector);

  return captureWorkflowScreenshot(page, {
    ...options,
    fullPage: false, // Modals don't need full page
    scrollToTop: false, // Already scrolled modal content above
  });
}

/**
 * Captures multiple screenshots in sequence with automatic numbering
 *
 * Usage:
 * ```typescript
 * const capturer = createSequentialCapturer('phase-a-setup', 1);
 * await capturer(page, 'event-dashboard');
 * await capturer(page, 'create-button-clicked');
 * await capturer(page, 'event-form-filled');
 * ```
 */
export function createSequentialCapturer(phase: string, startSequence: number = 1) {
  let currentSequence = startSequence;

  // Return a callable function with extra methods attached
  const capturer = async (
    page: Page,
    name: string,
    customOptions: Partial<ScreenshotOptions> = {}
  ): Promise<string> => {
    const options: ScreenshotOptions = {
      phase,
      sequence: currentSequence++,
      name,
      ...customOptions,
    };

    return captureWorkflowScreenshot(page, options);
  };

  // Add utility methods
  capturer.getCurrentSequence = (): number => currentSequence;
  capturer.resetSequence = (newStart: number = 1): void => {
    currentSequence = newStart;
  };

  return capturer;
}

/**
 * Hides dynamic content that varies between test runs
 * (timestamps, user IDs, etc.) for consistent screenshots
 */
async function hideDynamicContent(page: Page): Promise<void> {
  await page.evaluate(() => {
    // Hide elements with timestamps
    document.querySelectorAll('[data-dynamic="timestamp"]').forEach((el) => {
      (el as HTMLElement).style.opacity = '0';
    });

    // Hide correlation IDs or request IDs
    document.querySelectorAll('[data-dynamic="correlation-id"]').forEach((el) => {
      (el as HTMLElement).style.opacity = '0';
    });

    // You can add more dynamic content hiding logic here as needed
  });
}

/**
 * Cleans up all screenshots in a phase directory
 * Useful for re-running tests to get fresh screenshots
 */
export function cleanupPhaseScreenshots(phase: string): void {
  const phaseDir = path.join(SCREENSHOTS_BASE_DIR, phase);

  if (fs.existsSync(phaseDir)) {
    const files = fs.readdirSync(phaseDir);
    files.forEach((file) => {
      if (file.endsWith('.png')) {
        fs.unlinkSync(path.join(phaseDir, file));
      }
    });
    console.log(`🧹 Cleaned up screenshots in ${phase}`);
  }
}

/**
 * Lists all screenshots in a phase directory
 */
export function listPhaseScreenshots(phase: string): string[] {
  const phaseDir = path.join(SCREENSHOTS_BASE_DIR, phase);

  if (!fs.existsSync(phaseDir)) {
    return [];
  }

  return fs
    .readdirSync(phaseDir)
    .filter((file) => file.endsWith('.png'))
    .sort();
}

/**
 * Gets the total screenshot count across all phases
 */
export function getTotalScreenshotCount(): number {
  if (!fs.existsSync(SCREENSHOTS_BASE_DIR)) {
    return 0;
  }

  let total = 0;
  const phases = fs.readdirSync(SCREENSHOTS_BASE_DIR);

  phases.forEach((phase) => {
    const phaseDir = path.join(SCREENSHOTS_BASE_DIR, phase);
    if (fs.statSync(phaseDir).isDirectory()) {
      const screenshots = fs.readdirSync(phaseDir).filter((f) => f.endsWith('.png'));
      total += screenshots.length;
    }
  });

  return total;
}
