/**
 * Speaker Management Page Object
 *
 * Encapsulates interactions with speaker brainstorming, outreach, and content management screens.
 * Selectors extracted from playwright-recording.ts
 *
 * Responsibilities:
 * - Add speaker candidates (brainstorming)
 * - Track speaker outreach and contact
 * - Manage speaker status via kanban
 * - Submit and approve speaker content
 */

import { Page, Locator } from '@playwright/test';

export class SpeakerManagementPage {
  readonly page: Page;

  // Speaker Brainstorming
  readonly addSpeakersButton: Locator;
  readonly speakerNameField: Locator;
  readonly companyField: Locator;
  readonly expertiseField: Locator;
  readonly assignedUserSelect: Locator;
  readonly addToPoolButton: Locator;
  readonly proceedToOutreachButton: Locator;

  // Speaker Outreach
  readonly contactMethodSelect: Locator;
  readonly contactNotesField: Locator;
  readonly markAsContactedButton: Locator;
  readonly backdropClose: Locator;

  // Speaker Status Management
  readonly changeStatusButton: Locator;
  readonly kanbanViewButton: Locator;
  readonly sessionsViewButton: Locator;

  // Content Submission
  readonly speakerSearchField: Locator;
  readonly presentationTitleField: Locator;
  readonly presentationAbstractField: Locator;
  readonly submitContentButton: Locator;

  // Content Approval
  readonly approveButton: Locator;

  // Slot Assignment
  readonly manageSlotAssignmentsButton: Locator;
  readonly dragHandle: Locator;
  readonly backToEventButton: Locator;

  constructor(page: Page) {
    this.page = page;

    // Speaker Brainstorming - using test IDs for language independence
    this.addSpeakersButton = page.getByRole('button', { name: 'Add Speakers' });
    this.speakerNameField = page.getByTestId('speaker-name-field');
    this.companyField = page.getByTestId('speaker-company-field');
    this.expertiseField = page.getByTestId('speaker-expertise-field');
    this.assignedUserSelect = page.getByTestId('speaker-organizer-select');
    this.addToPoolButton = page.getByTestId('add-to-pool-button');
    this.proceedToOutreachButton = page.getByTestId('proceed-to-outreach-button');

    // Speaker Outreach (lines 90-123)
    this.contactMethodSelect = page.getByRole('combobox');
    this.contactNotesField = page.getByRole('textbox', { name: 'Notizen' });
    this.markAsContactedButton = page.getByRole('button', { name: 'Als kontaktiert markieren' });
    this.backdropClose = page.locator('.MuiBackdrop-root');

    // Speaker Status (lines 171, 189-191)
    this.changeStatusButton = page.getByRole('button', { name: 'Status ändern' });
    this.kanbanViewButton = page.getByRole('button', { name: 'Kanban' });
    this.sessionsViewButton = page.getByRole('button', { name: 'Sessions view' });

    // Content Submission (lines 195-232)
    this.speakerSearchField = page.getByRole('combobox', { name: 'Sprecher suchen' });
    this.presentationTitleField = page.getByRole('textbox', { name: 'Präsentationstitel' });
    this.presentationAbstractField = page.getByRole('textbox', {
      name: 'Präsentationszusammenfassung',
    });
    this.submitContentButton = page.getByRole('button', { name: 'Sprecher-Inhalt einreichen' });

    // Content Approval (lines 237-241)
    this.approveButton = page.getByRole('button', { name: 'Genehmigen' });

    // Slot Assignment (lines 243-245)
    this.manageSlotAssignmentsButton = page.getByRole('button', {
      name: 'Slot-Zuweisungen verwalten',
    });
    this.dragHandle = page.getByTestId('drag-handle');
    this.backToEventButton = page.getByRole('button', {
      name: 'Zurück zur Veranstaltung',
      exact: true,
    });
  }

  /**
   * Adds a speaker candidate to the pool
   */
  async addSpeakerCandidate(candidate: {
    firstName: string;
    company: string;
    expertise: string;
    assignedUserName: string;
  }): Promise<void> {
    await this.speakerNameField.fill(candidate.firstName);
    await this.companyField.fill(candidate.company);
    await this.expertiseField.fill(candidate.expertise);

    // Select assigned user from dropdown
    // CRITICAL: MUI Select needs special handling to avoid scroll-into-view blocking
    // 1. Scroll the element into view first to prevent interference
    await this.assignedUserSelect.scrollIntoViewIfNeeded();
    await this.page.waitForTimeout(300); // Wait for scroll to complete

    // 2. Use force: true to bypass actionability checks (MUI Select has overlapping divs)
    await this.assignedUserSelect.click({ force: true });

    // 3. Wait for dropdown menu to be visible and stable
    await this.page.waitForTimeout(500); // Allow dropdown animation to complete

    // 4. Wait for the option to be visible in the dropdown
    const optionLocator = this.page.getByRole('option', { name: candidate.assignedUserName });
    await optionLocator.waitFor({ state: 'visible', timeout: 5000 });

    // 5. Click the option (force: true not needed here, option is fully actionable)
    await optionLocator.click();

    // 6. Wait for dropdown to close
    await this.page.waitForTimeout(300);

    await this.addToPoolButton.click();
    await this.page.waitForTimeout(500); // Wait for speaker to be added
  }

  /**
   * Adds multiple speaker candidates
   */
  async addMultipleSpeakers(
    candidates: Array<{
      firstName: string;
      company: string;
      expertise: string;
      assignedUserName: string;
    }>
  ): Promise<void> {
    // The form stays visible after adding a speaker, no button click needed
    // Just fill and submit for each candidate
    for (let i = 0; i < candidates.length; i++) {
      await this.addSpeakerCandidate(candidates[i]);
    }
  }

  /**
   * Proceeds from brainstorming to outreach phase
   */
  async proceedToOutreach(): Promise<void> {
    await this.proceedToOutreachButton.click();
    await this.page.waitForTimeout(1000);
  }

  /**
   * Gets speaker card by name
   */
  getSpeakerCard(displayName: string): Locator {
    return this.page.getByRole('button', { name: displayName });
  }

  /**
   * Contacts a speaker and records outreach
   */
  async contactSpeaker(
    displayName: string,
    contactMethod: 'Telefon' | 'E-Mail' | 'Persönlich',
    notes: string
  ): Promise<void> {
    // Click speaker card to open contact dialog
    await this.getSpeakerCard(displayName).click();
    await this.page.waitForTimeout(300);

    // Select contact method
    await this.contactMethodSelect.click();
    await this.page.getByRole('option', { name: contactMethod }).click();

    // Add notes
    await this.contactNotesField.fill(notes);

    // Mark as contacted
    await this.markAsContactedButton.click();
    await this.page.waitForTimeout(500);

    // Close dialog
    await this.backdropClose.click();
  }

  /**
   * Drags speaker from one kanban column to another
   */
  async dragSpeakerToColumn(
    speakerDisplayName: string,
    targetColumnName: 'Ready' | 'Bereit' | 'Accepted' | 'Akzeptiert'
  ): Promise<void> {
    const speakerCard = this.getSpeakerCard(speakerDisplayName);
    const targetColumn = this.page
      .getByRole('heading', { name: new RegExp(targetColumnName, 'i') })
      .locator('..');

    await speakerCard.dragTo(targetColumn);
    await this.page.waitForTimeout(500);
  }

  /**
   * Changes speaker status using the status button
   */
  async changeStatusBatch(): Promise<void> {
    await this.changeStatusButton.click();
    await this.page.waitForTimeout(500);
  }

  /**
   * Switches to kanban view
   */
  async switchToKanbanView(): Promise<void> {
    await this.kanbanViewButton.click();
    await this.page.waitForTimeout(500);
  }

  /**
   * Switches to sessions view
   */
  async switchToSessionsView(): Promise<void> {
    await this.sessionsViewButton.click();
    await this.page.waitForTimeout(500);
  }

  /**
   * Submits speaker content (presentation)
   */
  async submitSpeakerContent(
    speakerDisplayName: string,
    content: {
      speakerSearchTerm?: string; // Search term to find speaker in dropdown
      title: string;
      abstract: string;
    }
  ): Promise<void> {
    // Click speaker card to open content submission dialog
    await this.getSpeakerCard(speakerDisplayName).click();
    await this.page.waitForTimeout(300);

    // Search for and select speaker if needed
    if (content.speakerSearchTerm) {
      await this.speakerSearchField.click();
      await this.speakerSearchField.fill(content.speakerSearchTerm);
      // Select first matching option
      await this.page.waitForTimeout(300);
      await this.page.keyboard.press('Enter');
    }

    // Fill presentation details
    await this.presentationTitleField.fill(content.title);
    await this.presentationAbstractField.fill(content.abstract);

    // Submit content
    await this.submitContentButton.click();
    await this.page.waitForTimeout(500);
  }

  /**
   * Approves a presentation by clicking on it and then approve button
   */
  async approvePresentation(presentationTitle: string): Promise<void> {
    // Click presentation to open approval dialog
    await this.page.getByRole('button', { name: new RegExp(presentationTitle) }).click();
    await this.page.waitForTimeout(300);

    // Approve
    await this.approveButton.click();
    await this.page.waitForTimeout(500);
  }

  /**
   * Opens slot assignment management
   */
  async openSlotAssignments(): Promise<void> {
    await this.manageSlotAssignmentsButton.click();
    await this.page.waitForTimeout(1000);
  }

  /**
   * Returns to event overview from slot assignments
   */
  async returnToEvent(): Promise<void> {
    await this.backToEventButton.click();
    await this.page.waitForTimeout(500);
  }
}
