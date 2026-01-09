/**
 * Topic Selection Page Object
 *
 * Encapsulates interactions with the topic selection and heat map screens.
 * Selectors extracted from playwright-recording.ts (lines 36-39)
 *
 * Responsibilities:
 * - Navigate to topic selection
 * - Select topics using heat map
 * - Confirm topic selection
 */

import { Page, Locator } from '@playwright/test';

export class TopicSelectionPage {
  readonly page: Page;

  readonly selectTopicButton: Locator;
  readonly heatmapButton: Locator;
  readonly confirmSelectionButton: Locator;

  constructor(page: Page) {
    this.page = page;

    // Topic selection - using test IDs instead of translated text for language independence
    this.selectTopicButton = page.getByTestId('select-topic-button');
    this.heatmapButton = page.getByTestId('view-mode-heatmap');
    this.confirmSelectionButton = page.getByTestId('confirm-topic-selection-button');
  }

  /**
   * Opens topic selection for the event
   */
  async openTopicSelection(): Promise<void> {
    await this.selectTopicButton.click();
    await this.page.waitForTimeout(500);
  }

  /**
   * Opens the heatmap view
   */
  async openHeatmap(): Promise<void> {
    await this.heatmapButton.click();
    await this.page.waitForTimeout(1000); // Wait for heatmap to load
  }

  /**
   * Selects a topic from the heatmap by clicking a specific cell
   * @param row - Row number (1-indexed)
   * @param column - Column number (1-indexed)
   */
  async selectTopicFromHeatmap(row: number, column: number): Promise<void> {
    // Click heatmap cell (line 38 in recording uses nth-child selectors)
    await this.page.locator(`div:nth-child(${row}) > div:nth-child(${column})`).click();
    await this.page.waitForTimeout(500);
  }

  /**
   * Confirms topic selection
   */
  async confirmSelection(): Promise<void> {
    await this.confirmSelectionButton.click();
    await this.page.waitForTimeout(1000);
  }

  /**
   * Complete workflow: Open topic selection, use heatmap, and confirm
   */
  async selectTopicViaHeatmap(row: number, column: number): Promise<void> {
    await this.openTopicSelection();
    await this.openHeatmap();
    await this.selectTopicFromHeatmap(row, column);
    await this.confirmSelection();
  }
}
