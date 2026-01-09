/**
 * Publishing Page Object
 *
 * Encapsulates interactions with the progressive publishing workflow screens.
 * Selectors extracted from playwright-recording.ts (lines 184-249)
 *
 * Responsibilities:
 * - Progressive publishing (topics, speakers, agenda)
 * - Navigate publishing tab
 * - Manage publish states
 */

import { Page, Locator } from '@playwright/test';

export class PublishingPage {
  readonly page: Page;

  readonly publishingTab: Locator;
  readonly publishTopicButton: Locator;
  readonly publishSpeakersButton: Locator;
  readonly publishAgendaButton: Locator;

  constructor(page: Page) {
    this.page = page;

    // Publishing controls (lines 185-249 in recording)
    this.publishingTab = page.getByRole('tab', { name: 'Veröffentlichung' });
    this.publishTopicButton = page.getByTestId('publish-topic-button');
    this.publishSpeakersButton = page.getByTestId('publish-speakers-button');
    this.publishAgendaButton = page.getByTestId('publish-agenda-button');
  }

  /**
   * Navigates to the publishing tab
   */
  async navigateToPublishing(): Promise<void> {
    await this.publishingTab.click();
    await this.page.waitForTimeout(500);
  }

  /**
   * Publishes the event topic
   */
  async publishTopic(): Promise<void> {
    await this.navigateToPublishing();
    await this.publishTopicButton.click();
    await this.page.waitForTimeout(1000);
  }

  /**
   * Publishes the speaker list
   */
  async publishSpeakers(): Promise<void> {
    await this.navigateToPublishing();
    await this.publishSpeakersButton.click();
    await this.page.waitForTimeout(1000);
  }

  /**
   * Publishes the final agenda
   */
  async publishAgenda(): Promise<void> {
    await this.navigateToPublishing();
    await this.publishAgendaButton.click();
    await this.page.waitForTimeout(1000);
  }

  /**
   * Completes all publishing steps (topics → speakers → agenda)
   */
  async publishAll(): Promise<void> {
    await this.publishTopic();
    await this.publishSpeakers();
    await this.publishAgenda();
  }
}
