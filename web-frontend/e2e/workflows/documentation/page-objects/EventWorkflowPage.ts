/**
 * Event Workflow Page Object
 *
 * Encapsulates interactions with the event management workflow screens.
 * Selectors extracted from playwright-recording.ts
 *
 * Responsibilities:
 * - Navigate to event dashboard
 * - Create new events
 * - Navigate through workflow steps
 * - Verify workflow state transitions
 */

import { Page, Locator } from '@playwright/test';
import type { ScreenshotOptions } from '../helpers/screenshot-helpers.js';

export class EventWorkflowPage {
  readonly page: Page;

  // Authentication
  readonly loginLink: Locator;
  readonly emailField: Locator;
  readonly passwordField: Locator;
  readonly rememberMeCheckbox: Locator;
  readonly loginButton: Locator;

  // Event Dashboard
  readonly createEventButton: Locator;

  // Event Creation Form
  readonly eventNumberField: Locator;
  readonly eventTitleField: Locator;
  readonly eventDescriptionField: Locator;
  readonly eventDateField: Locator;
  readonly registrationDeadlineField: Locator;
  readonly venueNameField: Locator;
  readonly venueAddressField: Locator;
  readonly venueCapacityField: Locator;
  readonly fileDropzone: Locator;
  readonly saveAndCreateButton: Locator;

  // Workflow Navigation
  readonly overviewTab: Locator;
  readonly speakersTab: Locator;
  readonly publishingTab: Locator;
  readonly settingsTab: Locator;

  // Workflow Actions
  readonly editButton: Locator;
  readonly workflowStatusSelect: Locator;
  readonly overrideValidationCheckbox: Locator;
  readonly saveButton: Locator;
  readonly deleteEventButton: Locator;
  readonly confirmDeleteButton: Locator;

  constructor(page: Page) {
    this.page = page;

    // Authentication - NOT USED (global setup handles auth via localStorage)
    this.loginLink = page.getByTestId('login-link');
    this.emailField = page.getByTestId('email-field');
    this.passwordField = page.getByTestId('password-field');
    this.rememberMeCheckbox = page.getByTestId('remember-me-checkbox');
    this.loginButton = page.getByTestId('login-button');

    // Event Dashboard - Language-independent selectors
    this.createEventButton = page.getByTestId('new-event-button');

    // Event Creation Form - Language-independent selectors (target input inside TextField)
    this.eventNumberField = page.getByTestId('event-number-field').locator('input');
    this.eventTitleField = page.getByTestId('event-title-field').locator('input');
    this.eventDescriptionField = page
      .getByTestId('event-description-field')
      .locator('textarea')
      .first();
    this.eventDateField = page.getByTestId('event-date-field').locator('input');
    this.registrationDeadlineField = page
      .getByTestId('registration-deadline-field')
      .locator('input');
    this.venueNameField = page.getByTestId('venue-name-field').locator('input');
    this.venueAddressField = page.getByTestId('venue-address-field').locator('input');
    this.venueCapacityField = page.getByTestId('venue-capacity-field').locator('input');
    this.fileDropzone = page.getByTestId('file-dropzone');
    this.saveAndCreateButton = page.getByTestId('save-create-event-button');

    // Workflow Navigation Tabs - Language-independent selectors
    this.overviewTab = page.getByTestId('event-tab-overview');
    this.speakersTab = page.getByTestId('event-tab-speakers');
    this.publishingTab = page.getByTestId('event-tab-publishing');
    this.settingsTab = page.getByTestId('event-tab-settings');

    // Workflow State Management - Language-independent selectors
    this.editButton = page.getByTestId('edit-event-button');
    this.workflowStatusSelect = page.getByTestId('event-status-select');
    this.overrideValidationCheckbox = page.getByTestId('override-workflow-validation-checkbox');
    this.saveButton = page.getByTestId('save-event-button');

    // Event Deletion - Language-independent selectors
    this.deleteEventButton = page.getByTestId('delete-event-button');
    this.confirmDeleteButton = page.getByTestId('confirm-delete-button');
  }

  /**
   * Navigates to the event management dashboard
   */
  async navigateToDashboard(): Promise<void> {
    await this.page.goto('http://localhost:8100/organizer/events?includeArchived=false');
    await this.page.waitForLoadState('networkidle');
  }

  /**
   * Performs login - injects auth token from environment
   * @param _email - Unused (kept for API compatibility)
   * @param _password - Unused (kept for API compatibility)
   */
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  async login(_email?: string, _password?: string): Promise<void> {
    // Get auth token from environment
    const authToken = process.env.AUTH_TOKEN;

    if (!authToken) {
      console.warn('⚠️  No AUTH_TOKEN found - login may fail');
    }

    // Navigate to the app first
    await this.page.goto('http://localhost:8100/');
    await this.page.waitForLoadState('networkidle');

    // Inject auth token into localStorage
    if (authToken) {
      await this.page.evaluate((token) => {
        localStorage.setItem('authToken', token);
        localStorage.setItem('idToken', token);
        // Also set as cookie for good measure
        document.cookie = `authToken=${token}; path=/; max-age=86400`;
      }, authToken);

      console.log('✓ Auth token injected into browser');
    }

    // Wait a moment for token to be set
    await this.page.waitForTimeout(500);
  }

  /**
   * Clicks the Create Event button
   */
  async clickCreateEvent(): Promise<void> {
    // Close any existing dialogs first
    const closeButtons = this.page.locator(
      'button:has-text("ABBRECHEN"), button:has-text("Schließen")'
    );
    if ((await closeButtons.count()) > 0) {
      await closeButtons.first().click();
      await this.page.waitForTimeout(300);
    }

    // Wait for button to be ready and visible
    await this.createEventButton.waitFor({ state: 'visible', timeout: 5000 });

    // Scroll button into view to ensure it's clickable
    await this.createEventButton.scrollIntoViewIfNeeded();
    await this.page.waitForTimeout(200);

    // Click the button
    await this.createEventButton.click({ force: false });
    await this.page.waitForTimeout(800); // Wait for modal to open
  }

  /**
   * Fills the event creation form
   */
  async fillEventForm(
    eventData: {
      eventNumber: number;
      title: string;
      description: string;
      date: string;
      registrationDeadline: string;
      eventType: 'EVENING' | 'AFTERNOON' | 'FULL_DAY';
      venueName: string;
      venueAddress: string;
      venueCapacity: number;
      venueImagePath?: string;
    },
    options?: {
      captureDropdownScreenshot?: (
        page: Page,
        selector: string,
        name: string,
        options?: Partial<ScreenshotOptions>
      ) => Promise<string>;
    }
  ): Promise<void> {
    // Fill basic event details
    console.log('    → Filling event number');
    await this.eventNumberField.fill(eventData.eventNumber.toString());
    console.log('    → Filling title');
    await this.eventTitleField.fill(eventData.title);
    console.log('    → Filling description');
    await this.eventDescriptionField.fill(eventData.description);
    console.log('    → Filling date');
    await this.eventDateField.fill(eventData.date);
    console.log('    → Filling registration deadline');
    await this.registrationDeadlineField.fill(eventData.registrationDeadline);

    // Select event type
    console.log('    → Selecting event type:', eventData.eventType);
    await this.selectEventType(eventData.eventType, options?.captureDropdownScreenshot);
    console.log('    → Event type selected');

    // Fill venue details
    console.log('    → Filling venue name');
    await this.venueNameField.fill(eventData.venueName);
    console.log('    → Filling venue address');
    await this.venueAddressField.fill(eventData.venueAddress);
    console.log('    → Filling venue capacity');
    await this.venueCapacityField.fill(eventData.venueCapacity.toString());

    // Upload venue image if provided
    if (eventData.venueImagePath) {
      console.log('    → Uploading venue image');
      await this.fileDropzone.setInputFiles(eventData.venueImagePath);
    }
    console.log('    → Form filling complete');
  }

  /**
   * Selects event type (Evening, Afternoon, Full Day)
   * Uses data-testid for stable selection instead of text matching
   */
  async selectEventType(
    eventType: 'EVENING' | 'AFTERNOON' | 'FULL_DAY',
    captureDropdownScreenshot?: (
      page: Page,
      selector: string,
      name: string,
      options?: Partial<ScreenshotOptions>
    ) => Promise<string>
  ): Promise<void> {
    console.log(`      → Selecting event type: ${eventType}`);

    // Click the event type selector dropdown (uses data-testid from EventTypeSelector.tsx)
    const selector = this.page.getByTestId('event-type-selector');
    await selector.waitFor({ state: 'visible', timeout: 5000 });
    await selector.click();
    await this.page.waitForTimeout(500); // Wait for dropdown menu to fully open

    // Capture screenshot of opened dropdown if capturer provided
    if (captureDropdownScreenshot) {
      try {
        console.log('      → Capturing event type dropdown screenshot');
        await captureDropdownScreenshot(
          this.page,
          '[role="listbox"], [role="menu"], .MuiMenu-paper',
          'event-type-dropdown-open',
          { delay: 300 }
        );
        console.log('      ✓ Event type dropdown screenshot captured');
      } catch (error) {
        console.log('      ⚠️  Could not capture dropdown screenshot:', error.message);
      }
    }

    // Select the MenuItem by its data-value attribute (more reliable than text matching)
    // MUI Select renders MenuItems with data-value matching the value prop
    const option = this.page.locator(`li[role="option"][data-value="${eventType}"]`);
    await option.waitFor({ state: 'visible', timeout: 5000 });
    await option.click();

    console.log(`      → Event type ${eventType} selected`);
  }

  /**
   * Submits the event creation form
   */
  async submitEventForm(): Promise<void> {
    await this.saveAndCreateButton.click();
    await this.page.waitForLoadState('networkidle');
    await this.page.waitForTimeout(1000); // Wait for event to be created
  }

  /**
   * Navigates to a specific tab
   */
  async navigateToTab(tab: 'overview' | 'speakers' | 'publishing' | 'settings'): Promise<void> {
    const tabMap = {
      overview: this.overviewTab,
      speakers: this.speakersTab,
      publishing: this.publishingTab,
      settings: this.settingsTab,
    };

    await tabMap[tab].click();
    await this.page.waitForTimeout(500);
  }

  /**
   * Changes workflow status (with validation override)
   */
  async changeWorkflowStatus(status: string, overrideValidation: boolean = true): Promise<void> {
    await this.navigateToTab('overview');
    await this.editButton.click();
    await this.workflowStatusSelect.click();
    await this.page.getByRole('option', { name: status }).click();

    if (overrideValidation) {
      await this.overrideValidationCheckbox.check();
    }

    await this.saveButton.click();
    await this.page.waitForTimeout(500);
  }

  /**
   * Archives the event
   */
  async archiveEvent(): Promise<void> {
    await this.changeWorkflowStatus('Archiviert', true);
  }

  /**
   * Deletes the event
   */
  async deleteEvent(): Promise<void> {
    await this.navigateToTab('settings');
    await this.deleteEventButton.click();
    await this.confirmDeleteButton.click();
    await this.page.waitForLoadState('networkidle');
  }
}
