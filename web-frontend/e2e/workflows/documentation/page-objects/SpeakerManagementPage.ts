/**
 * Speaker Management Page Object
 *
 * Encapsulates interactions with speaker brainstorming, outreach, and content management screens.
 *
 * Responsibilities:
 * - Add speaker candidates (brainstorming)
 * - Drive the BUTTON-BASED kanban workflow (ADR-009): log outreach, promote, accept on behalf,
 *   decline, enter content, review content — locator patterns mirror
 *   e2e/organizer/speaker-pool-golden-path.spec.ts (the hardened reference)
 * - Submit and approve speaker content
 *
 * Speaker cards are id-keyed (`speaker-card-{id}`) but screencast speakers are created through
 * the UI (no API), so ids are resolved from the card's data-testid attribute by visible name.
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

    // Speaker Outreach (language-independent testIds)
    this.contactMethodSelect = page.getByTestId('contact-method-select');
    this.contactNotesField = page.getByTestId('contact-notes-field');
    this.markAsContactedButton = page.getByTestId('mark-contacted-button');
    this.backdropClose = page.locator('.MuiBackdrop-root');

    // Speaker Status (language-independent testIds)
    this.changeStatusButton = page.getByTestId('status-change-confirm');
    this.kanbanViewButton = page.getByTestId('kanban-view-toggle');
    this.sessionsViewButton = page.getByTestId('sessions-view-toggle');

    // Content Submission (language-independent testIds)
    this.speakerSearchField = page.getByTestId('speaker-search-field');
    this.presentationTitleField = page.getByTestId('presentation-title-field');
    this.presentationAbstractField = page.getByTestId('presentation-abstract-field');
    this.submitContentButton = page.getByTestId('submit-speaker-content-button');

    // Content Approval (language-independent testIds)
    this.approveButton = page.getByTestId('approve-content-button');

    // Slot Assignment (language-independent testIds)
    this.manageSlotAssignmentsButton = page.getByTestId('manage-slot-assignments-button');
    this.dragHandle = page.getByTestId('drag-handle');
    this.backToEventButton = page.getByTestId('back-to-event-button');
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
   * Gets a kanban speaker card by a uniquely-identifying visible name fragment
   * (e.g. the brainstormed first name). Cards carry `data-testid="speaker-card-{id}"`.
   */
  getCardByName(name: string): Locator {
    return this.page.locator('[data-testid^="speaker-card-"]').filter({ hasText: name }).first();
  }

  /**
   * Resolves the speaker-pool id for a card by name (UI-only — read from the testid attribute).
   * Throws when the name fragment matches more than one card — a silent `.first()` on an
   * ambiguous match would drive the wrong speaker through the workflow.
   */
  async resolveSpeakerId(name: string): Promise<string> {
    const card = this.getCardByName(name);
    await card.waitFor({ state: 'visible', timeout: 10000 });
    const matches = await this.page
      .locator('[data-testid^="speaker-card-"]')
      .filter({ hasText: name })
      .count();
    if (matches > 1) {
      throw new Error(
        `Ambiguous speaker card name "${name}": ${matches} cards match — use a unique fragment`
      );
    }
    const testId = await card.getAttribute('data-testid');
    if (!testId) {
      throw new Error(`Speaker card for "${name}" has no data-testid`);
    }
    return testId.replace('speaker-card-', '');
  }

  /** Clicks the card's primary-action button (bottom of card, id-keyed). */
  private async clickPrimaryAction(name: string): Promise<void> {
    const id = await this.resolveSpeakerId(name);
    const button = this.page.getByTestId(`primary-action-button-${id}`);
    await button.scrollIntoViewIfNeeded();
    await button.click();
  }

  /**
   * UI-only success signal for workflow transitions: the card has moved into the target lane
   * (cards render inside the lane Paper carrying `status-lane-{status}`). Replaces the API
   * status polling the golden-path spec uses — the screencast must stay frontend-only.
   */
  private async expectCardInLane(name: string, laneStatus: string): Promise<void> {
    const card = this.page
      .getByTestId(`status-lane-${laneStatus}`)
      .locator('[data-testid^="speaker-card-"]')
      .filter({ hasText: name })
      .first();
    await card.waitFor({ state: 'visible', timeout: 20000 });
  }

  /**
   * IDENTIFIED → CONTACTED via the card primary action ("Kontakt erfassen") + MarkContactedModal.
   */
  async logOutreach(
    name: string,
    contactMethod: 'email' | 'phone' | 'in-person',
    notes: string
  ): Promise<void> {
    await this.clickPrimaryAction(name);
    const modal = this.page.getByTestId('mark-contacted-modal');
    await modal.waitFor({ state: 'visible', timeout: 10000 });

    await this.page.getByTestId('contact-method-select').click();
    await this.page.getByTestId(`contact-method-option-${contactMethod}`).click();
    await this.page.getByTestId('contact-notes').locator('textarea').first().fill(notes);

    await this.page.getByTestId('save-button').click();
    await modal.waitFor({ state: 'hidden', timeout: 15000 });
  }

  /**
   * CONTACTED → READY via the card primary action ("Zum Speaker befördern") → drawer promote
   * sub-view, creating a fresh SPEAKER user (provisions Cognito out-of-band — Pattern N).
   */
  async promoteCreatingNewUser(
    name: string,
    user: { firstName: string; lastName: string; email: string }
  ): Promise<void> {
    await this.clickPrimaryAction(name);
    const submit = this.page.getByTestId('promote-submit-button');
    await submit.waitFor({ state: 'visible', timeout: 10000 });

    await this.page.getByTestId('promote-create-new-speaker-button').click();
    const dialog = this.page.getByTestId('user-create-dialog');
    await dialog.waitFor({ state: 'visible', timeout: 10000 });
    await this.page.getByTestId('user-create-firstName').locator('input').fill(user.firstName);
    await this.page.getByTestId('user-create-lastName').locator('input').fill(user.lastName);
    await this.page.getByTestId('user-create-email').locator('input').fill(user.email);
    await this.page.getByTestId('user-create-role-SPEAKER').click();
    await this.page.getByTestId('user-create-submit').click();
    await dialog.waitFor({ state: 'hidden', timeout: 15000 });

    // Created user is now the selected speaker → promote.
    await submit.click();
    await submit.waitFor({ state: 'hidden', timeout: 20000 });
  }

  /**
   * Opens the drawer for a card (card click ≠ primary-action button) and runs a status-change
   * dialog action with the required reason. accept-on-behalf: READY → ACCEPTED (no invitation
   * email). decline: any state → DECLINED.
   */
  async drawerStatusChange(
    name: string,
    action: 'accept-on-behalf' | 'decline',
    reason: string
  ): Promise<void> {
    await this.getCardByName(name).click();
    const actionButton = this.page.getByTestId(`drawer-action-${action}`);
    await actionButton.waitFor({ state: 'visible', timeout: 10000 });
    await actionButton.click();

    const dialog = this.page.getByTestId('status-change-dialog');
    await dialog.waitFor({ state: 'visible', timeout: 10000 });
    await this.page.getByTestId('status-change-reason').locator('textarea').first().fill(reason);
    await this.page.getByTestId('status-change-confirm').click();
    await dialog.waitFor({ state: 'hidden', timeout: 15000 });

    // Close the drawer so the kanban board is fully visible again.
    await this.page.keyboard.press('Escape');
    await this.page.waitForTimeout(500);
  }

  /**
   * ACCEPTED → CONTENT_SUBMITTED via the card primary action ("Inhalt erfassen") → drawer
   * content form. The speaker is auto-resolved from the promoted user (no picker).
   */
  async enterContent(name: string, content: { title: string; abstract: string }): Promise<void> {
    await this.clickPrimaryAction(name);
    await this.presentationTitleField.waitFor({ state: 'visible', timeout: 10000 });
    await this.presentationTitleField.fill(content.title);
    await this.presentationAbstractField.fill(content.abstract);
    await this.submitContentButton.click();
    // The drawer form stays open after a successful submit (POST .../content → 201), so the
    // success signal is the card moving lanes — close the drawer and wait for containment.
    await this.page.waitForTimeout(1500);
    await this.page.keyboard.press('Escape');
    await this.expectCardInLane(name, 'content_submitted');
  }

  /**
   * CONTENT_SUBMITTED → QUALITY_REVIEWED via the card primary action ("Inhalt prüfen") →
   * quality-review view → approve.
   */
  async approveContent(name: string): Promise<void> {
    await this.clickPrimaryAction(name);
    await this.approveButton.waitFor({ state: 'visible', timeout: 10000 });
    await this.approveButton.click();
    await this.page.waitForTimeout(1500);
    // Close the drawer; the success signal is the card landing in QUALITY_REVIEWED.
    await this.page.keyboard.press('Escape');
    await this.expectCardInLane(name, 'quality_reviewed');
  }

  /**
   * @deprecated Pre-ADR-009 card-click contact dialog — superseded by {@link logOutreach}.
   * Kept only for the legacy complete-event-workflow.spec.ts; do not use in new code.
   */
  async contactSpeaker(
    displayName: string,
    contactMethod: 'phone' | 'email' | 'in_person',
    notes: string
  ): Promise<void> {
    await this.page.getByRole('button', { name: displayName }).click();
    await this.page.waitForTimeout(300);
    await this.contactMethodSelect.click();
    await this.page.waitForTimeout(300);
    await this.page.getByTestId(`contact-method-${contactMethod}`).click();
    await this.contactNotesField.fill(notes);
    await this.markAsContactedButton.click();
    await this.page.waitForTimeout(500);
    await this.backdropClose.click();
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
