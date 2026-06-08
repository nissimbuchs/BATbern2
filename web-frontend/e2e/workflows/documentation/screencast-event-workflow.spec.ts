/**
 * Screencast Training Video E2E Test
 *
 * This test executes the complete event management workflow as ONE continuous flow
 * for video recording purposes. All test phases are merged into a single test.
 *
 * Differences from complete-event-workflow.spec.ts:
 * - All 6 phases (A-E) merged into ONE continuous test
 * - NO screenshot captures (video recording only)
 * - Narration synchronized via per-segment audio durations (timing-helper v2)
 * - Strategic pauses for narration pacing
 *
 * Speaker workflow (ADR-009, button-driven — NO drag-and-drop):
 * - IDENTIFIED → CONTACTED   card primary action "Kontakt erfassen" (MarkContactedModal)
 * - CONTACTED → READY        card primary action "Zum Speaker befördern" (drawer promote,
 *                            creates SPEAKER user — provisions Cognito out-of-band)
 * - READY → ACCEPTED         drawer "Zusage im Namen erfassen" (reason; NO invitation email)
 * - CONTACTED → DECLINED     drawer "Mit Grund absagen" (Daniel — not promoted, no orphan session)
 * - ACCEPTED → CONTENT_SUBMITTED   card primary action "Inhalt erfassen"
 * - CONTENT_SUBMITTED → QUALITY_REVIEWED   card primary action "Inhalt prüfen" → approve
 *
 * Narration pipeline (see screencast/README.md):
 *   1. npm run screencast:narration [-- --fake]   generate per-segment audio + timings
 *   2. npm run test:e2e:screencast                record video; writes narration-timeline JSON
 *   3. npm run screencast:assemble                place audio at recorded offsets + SRT
 *
 * Narration texts live in screencast/narration-manifest-de.json (single source of truth);
 * the comments above each marker are convenience copies.
 *
 * Run:
 *   npm run test:e2e:screencast
 *   npm run test:e2e:screencast:headed
 */

import { test, expect } from '@playwright/test';
import { testConfig } from './test-data.config';
import { cleanupAfterTests } from './helpers/cleanup-helpers';
import { EventWorkflowPage } from './page-objects/EventWorkflowPage';
import { SpeakerManagementPage } from './page-objects/SpeakerManagementPage';
import { TopicSelectionPage } from './page-objects/TopicSelectionPage';
import { email as factoryEmail } from '../../helpers/test-data-factory';
import {
  assertNarrationTimings,
  waitForNarration,
  logNarration,
  paceWithinNarration,
  startTimer,
  flushTimeline,
  SCREENCAST_LANG,
} from './screencast/timing-helper';

/**
 * Continuous Event Workflow Screencast
 * Single test running all phases sequentially for video recording
 */
test.describe('Event Workflow Screencast for Training Video', () => {
  let authToken: string;
  let testEventCode: string;

  test.beforeAll(async () => {
    console.log('\n🎬 Starting Event Workflow Screencast Recording\n');
    // Fail fast when the narration audio has not been generated yet.
    assertNarrationTimings();
    authToken = process.env.AUTH_TOKEN || '';
  });

  test.afterAll(async () => {
    // Flush the narration timeline even when the run fails mid-way — a partial timeline
    // still lets screencast:assemble produce audio for the recorded part.
    flushTimeline();
    console.log('\n🧹 Cleaning up test data...\n');
    if (authToken) {
      // Promoted speakers use REAL names (for the video) but their emails are factory
      // `@e2e.batbern.invalid` addresses — Playwright global-teardown's
      // `cums/users_by_email prefix=@e2e.batbern.invalid` sweep deletes their CUMS rows
      // regardless of name (verified: 3 user_profiles removed). So no per-user delete is
      // needed here; just delete the event (cascades pool + sessions). The Cognito accounts
      // still leak (CUMS delete is DB-only) — documented manual sweep.
      await cleanupAfterTests(authToken, testEventCode);
    }
    console.log('\n✅ Screencast Recording Complete\n');
  });

  /**
   * Vollständiger Event-Workflow von Erstellung bis Archivierung
   * Complete Event Workflow from Creation to Archival
   */
  test('Vollständiger Event-Workflow von Erstellung bis Archivierung', async ({ page }) => {
    test.setTimeout(60 * 60 * 1000); // 60 minute timeout for full workflow

    const eventPage = new EventWorkflowPage(page);
    const topicPage = new TopicSelectionPage(page);
    const speakerPage = new SpeakerManagementPage(page);

    // Enable network logging for debugging
    page.on('request', (request) => {
      console.log(`→ ${request.method()} ${request.url()}`);
    });
    page.on('response', (response) => {
      const status = response.status();
      const url = response.url();
      if (status >= 400 || url.includes('/api/')) {
        console.log(`← ${status} ${url}`);
      }
    });

    try {
      // Start the narration synchronization timer
      startTimer();

      /*
       * NARRATION_01: [excited] Willkommen zur BATbern Event-Management-Plattform! [playful] Heute zeige ich Ihnen, wie man ein Event plant, ohne dabei den Verstand zu verlieren. [chuckling] Wir durchlaufen den kompletten Event-Lebenszyklus, von "Oh Gott, wir brauchen ein Event" bis zu "Endlich vorbei, ab ins Archiv damit!" [pause] Sie sehen in diesem Video alle wichtigen Schritte, die ein Organisator durchführt, um ein Berner Architekten Treffen zu planen, ohne dabei in Panik zu geraten.
       */
      logNarration('NARRATION_01', 'Willkommen zur BATbern Event-Management-Plattform');
      // Show public homepage first (display while NARRATION_01 plays).
      // NOTE: never wait for 'networkidle' here — Cloudflare Turnstile polls continuously on
      // the public homepage, so networkidle NEVER fires (observed 30-minute hang).
      console.log('\n🌐 Navigating to public homepage...\n');
      await page.goto('https://www.batbern.ch/', { waitUntil: 'load' });
      await page.waitForTimeout(2000);
      await waitForNarration('NARRATION_01', page);
      console.log('    ✓ Homepage displayed\n');

      // ========================================
      // PHASE A: EVENT SETUP
      // ========================================
      console.log('\n📋 Phase A: Event-Einrichtung\n');

      /*
       * NARRATION_02: [cheerful] Wir beginnen am Event-Dashboard. [playful] Das ist sozusagen Ihre Kommandozentrale, von der aus Sie den Überblick über alle Events behalten, die Sie jemals organisiert haben oder noch organisieren werden. [pause] Die Authentifizierung über AWS Cognito ist bereits erledigt. [satisfied] Sie sehen oben rechts Ihren Benutzernamen, und damit haben Sie die Macht, alle Funktionen als Organisator zu nutzen. [dramatic] Mit großer Macht kommt große Verantwortung!
       */
      logNarration('NARRATION_02', 'Dashboard und Authentifizierung');
      await eventPage.navigateToDashboard();
      await page.waitForLoadState('domcontentloaded');
      await expect(eventPage.createEventButton).toBeVisible({ timeout: 10000 });
      console.log('    ✓ Dashboard loaded - authentication successful');
      await waitForNarration('NARRATION_02', page);

      // Close any leftover event-form dialog via stable testid (language-agnostic — works in
      // English UI too, where the button reads "Cancel" not "ABBRECHEN").
      const leftoverCancel = page.getByTestId('close-edit-modal-button');
      if (await leftoverCancel.isVisible().catch(() => false)) {
        await leftoverCancel.click();
        await page.waitForTimeout(500);
      }

      /*
       * NARRATION_02L (idea #1): Language showcase. Open the user menu → language selector to
       * reveal all 10 supported locales, then switch the whole UI to the screencast's language
       * (English for the EN run; German stays German). The rest of the recording runs in that
       * language; the switch persists to localStorage + the user's backend preference.
       */
      logNarration('NARRATION_02L', 'Sprachauswahl — alle 10 Sprachen / language showcase');
      await page.getByTestId('user-menu-button').click();
      await page.waitForTimeout(500);
      await page.getByTestId('language-select').click();
      // Menu open ⇢ the locale options are visible (the option testids are language-agnostic).
      await expect(page.getByTestId(`language-option-${SCREENCAST_LANG}`)).toBeVisible({
        timeout: 5000,
      });
      // Hold the open list on screen for most of the narration so the viewer sees all locales,
      // then switch language near the end of the segment.
      await paceWithinNarration('NARRATION_02L', 0, 1, page);
      await page.getByTestId(`language-option-${SCREENCAST_LANG}`).click();
      await page.waitForTimeout(800);
      await page.keyboard.press('Escape'); // close the user menu
      await page.waitForTimeout(500);
      console.log(`    ✓ UI language set to ${SCREENCAST_LANG}`);
      await waitForNarration('NARRATION_02L', page);

      /*
       * NARRATION_03: [enthusiastic] Jetzt erstellen wir ein brandneues Event! [excited] Klicken Sie auf den Button "Neue Veranstaltung" oben rechts. [pause] Boom! Ein modales Formular erscheint. [playful] Keine Sorge, es sieht nach viel aus, aber wir füllen das gemeinsam aus.
       */
      logNarration('NARRATION_03', 'Neues Event erstellen');
      await eventPage.clickCreateEvent();
      await waitForNarration('NARRATION_03', page);

      const uniqueEventNumber = testConfig.event.eventNumber + Math.floor(Math.random() * 1000);
      console.log(`    → Creating event #${uniqueEventNumber}`);

      /*
       * NARRATION_04: [professional] Event-Nummer. [casual] Eine eindeutige Kennung für dieses Event. Das System verwendet intern das Format "BATbern" gefolgt von der Nummer. [playful] Sozusagen die Geburtsurkunde Ihres Events. Dies dient der Identifikation in der Datenbank und in URLs.
       */
      logNarration('NARRATION_04', 'Event-Nummer eingeben');
      await eventPage.eventNumberField.fill(uniqueEventNumber.toString());
      await waitForNarration('NARRATION_04', page);

      /*
       * NARRATION_05: [short pause] Titel. [cheerful] Hier kommt der Name, der Ihre Teilnehmer begeistern soll! [playful] "Langweiliges Architektur-Event Nummer 47" wäre zwar ehrlich, aber vielleicht nicht die beste Wahl. [chuckling] Wählen Sie etwas Aussagekräftiges, das die Leute auf der öffentlichen Website sehen werden.
       */
      logNarration('NARRATION_05', 'Titel eingeben');
      await eventPage.eventTitleField.fill(testConfig.event.title);
      await waitForNarration('NARRATION_05', page);

      /*
       * NARRATION_06: [short pause] Beschreibung. [casual] Eine kurze Erläuterung des Event-Themas. [helpful] Diese Information hilft Interessenten zu verstehen, warum sie sich unbedingt anmelden sollten. [playful] Oder zumindest, worum es geht.
       */
      logNarration('NARRATION_06', 'Beschreibung eingeben');
      await eventPage.eventDescriptionField.fill(testConfig.event.description);
      await waitForNarration('NARRATION_06', page);

      /*
       * NARRATION_07: [pause] Event-Typ. [professional] Wählen Sie zwischen drei Formaten: [clear] "Abend" für Feierabend-Events, [cheerful] bei denen man nach der Arbeit noch ein bisschen netzwerken kann. "Nachmittag" für Nachmittagsveranstaltungen, [playful] perfekt für alle, die abends lieber auf dem Sofa sitzen. [pause] Oder "Ganztag" für ganztägige Konferenzen. [dramatic] Da brauchen Sie dann viel Kaffee! [short pause] Dies beeinflusst die Zeitplanung und Slot-Verwaltung.
       */
      logNarration('NARRATION_07', 'Event-Typ auswählen');
      await eventPage.selectEventType(
        testConfig.event.eventType as 'EVENING' | 'AFTERNOON' | 'FULL_DAY'
      );
      await waitForNarration('NARRATION_07', page);

      /*
       * NARRATION_08: [professional] Datum und Anmeldefrist. [clear] Das Event-Datum legt fest, wann die Veranstaltung stattfindet. [playful] Bitte wählen Sie ein Datum in der Zukunft, Zeitreisen unterstützen wir noch nicht. [chuckling] Die Anmeldefrist ist wichtig für die Teilnehmer-Verwaltung.
       */
      logNarration('NARRATION_08', 'Datum und Anmeldefrist');
      await eventPage.eventDateField.fill(testConfig.event.date);
      await eventPage.registrationDeadlineField.fill(testConfig.event.registrationDeadline);
      await waitForNarration('NARRATION_08', page);

      /*
       * NARRATION_09: [short pause] Veranstaltungsort. [casual] Name und Adresse des Veranstaltungsortes. [playful] Also nicht "bei mir im Keller", sondern ein richtiger Ort mit Adresse. [cheerful] Diese Informationen werden auf der öffentlichen Website angezeigt, damit die Leute auch wirklich hingehen können.
       */
      logNarration('NARRATION_09', 'Veranstaltungsort eingeben');
      await eventPage.venueNameField.fill(testConfig.event.venue.name);
      await eventPage.venueAddressField.fill(testConfig.event.venue.address);
      await waitForNarration('NARRATION_09', page);

      /*
       * NARRATION_10: [satisfied] Wir klicken auf "Speichern" und das Event wird erstellt. [excited] Tada! [pause] Das System kehrt automatisch zum Dashboard zurück, und da ist es! Ihr brandneues Event in der Liste. [playful] Ihr Baby ist geboren!
       */
      logNarration('NARRATION_10', 'Event erfolgreich erstellt');
      await page.waitForTimeout(1000);
      await eventPage.submitEventForm();

      testEventCode = `BATbern${uniqueEventNumber}`;
      console.log(`    → Waiting for event creation to complete...`);
      await page.waitForLoadState('domcontentloaded');
      await page.waitForTimeout(2000);

      const modalStillOpen = await eventPage.eventNumberField
        .isVisible({ timeout: 1000 })
        .catch(() => false);
      if (modalStillOpen) {
        const errorText = await page
          .locator('text=/error|fehler|ungültig/i')
          .first()
          .textContent({ timeout: 1000 })
          .catch(() => 'No error text found');
        throw new Error(`Event creation failed: ${errorText}`);
      }

      console.log(`    ✓ Event created: ${testEventCode}`);
      await waitForNarration('NARRATION_10', page);

      const eventUrl = `http://localhost:8100/organizer/events/${testEventCode}`;
      const kanbanUrl = `${eventUrl}?tab=speakers&view=kanban`;

      /*
       * NARRATION_11: [pause] Nach der Event-Erstellung navigieren wir zur Event-Detailseite. [professional] Hier können wir Aufgaben an Teammitglieder zuweisen. [playful] Denn warum sollten Sie alles alleine machen, wenn Sie ein ganzes Team haben? [chuckling] Klicken Sie auf "Bearbeiten" um das Event-Formular erneut zu öffnen.
       */
      logNarration('NARRATION_11', 'Zur Event-Detailseite navigieren');
      await page.goto(eventUrl);
      await page.waitForLoadState('domcontentloaded');
      await page.waitForTimeout(1500);

      const editButton = page.getByTestId('edit-event-button');
      await editButton.click();
      await page.waitForTimeout(500);

      await expect(page.getByRole('dialog')).toBeVisible({ timeout: 5000 });
      await page.waitForTimeout(500);
      await waitForNarration('NARRATION_11', page);

      /*
       * NARRATION_12: [clear] Wechseln Sie zum Tab "Aufgaben". [professional] Hier sehen Sie eine vordefinierte Liste von Standard-Aufgaben, die bei jedem Event anfallen. [playful] Das System kennt sich aus, es weiß, was alles zu tun ist. [pause] Für jede Aufgabe wählen wir einen verantwortlichen Organisator aus dem Dropdown-Menü. [helpful] Dies stellt sicher, dass alle wichtigen Tätigkeiten klar zugeordnet sind und nichts vergessen wird. [dramatic] Denn vergessene Aufgaben führen zu Chaos, und Chaos führt zu... naja, mehr Chaos.
       */
      logNarration('NARRATION_12', 'Aufgaben zuweisen');
      const tasksTab = page.getByTestId('tasks-tab');
      await tasksTab.click();
      await page.waitForTimeout(800);

      const taskAssignments = [
        { taskName: 'Venue Booking', assignee: 'Nissim Buchs' },
        { taskName: 'Partner Meeting', assignee: 'Daniel Kühni' },
        { taskName: 'Moderator Assignment', assignee: 'Vanessa Deubel' },
        { taskName: 'Newsletter: Topic', assignee: 'Baltisar Oswald' },
        { taskName: 'Newsletter: Speaker', assignee: 'Andreas Grütter' },
        { taskName: 'Newsletter: Final', assignee: 'Vanessa Deubel' },
      ];

      for (let i = 0; i < taskAssignments.length; i++) {
        const { taskName, assignee } = taskAssignments[i];
        await paceWithinNarration('NARRATION_12', i, taskAssignments.length, page);
        console.log(`    → Assigning "${taskName}" to ${assignee}`);

        const taskRow = page.getByRole('listitem').filter({ hasText: taskName });
        const assigneeSelect = taskRow.getByRole('combobox');
        await assigneeSelect.scrollIntoViewIfNeeded();
        await assigneeSelect.click();

        // Wait for the MUI menu, then scroll the target option into view INSIDE the listbox
        // before clicking. Adding Vanessa lengthened the assignee list, so options below the
        // fold are not actionable until scrolled — a plain click would otherwise wait forever.
        const listbox = page.getByRole('listbox');
        await expect(listbox).toBeVisible();
        const option = listbox.getByRole('option', { name: assignee }).first();
        await option.scrollIntoViewIfNeeded();
        await option.click();
        await expect(listbox).toBeHidden();
        await page.waitForTimeout(300);
      }

      console.log(`    ✓ All ${taskAssignments.length} tasks assigned`);
      await waitForNarration('NARRATION_12', page);

      /*
       * NARRATION_13: [confident] Nach der Zuweisung klicken wir auf "Speichern". [satisfied] Das System speichert alle Aufgaben und kehrt zur Event-Detailseite zurück. [cheerful] Perfekt!
       */
      logNarration('NARRATION_13', 'Aufgaben speichern');
      const saveButton = page.getByTestId('save-event-button');
      await saveButton.click();
      await page.waitForTimeout(1500);

      await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 5000 });
      console.log('    ✓ Tasks saved');
      await waitForNarration('NARRATION_13', page);

      /*
       * NARRATION_14: [pause] Um die Aufgaben zu überprüfen, navigieren wir zur Aufgabenliste. [informative] Zunächst sehen Sie die Standard-Filterung "Meine Aufgaben", die nur Ihre eigenen Aufgaben anzeigt. [playful] Aber wir sind neugierig und wollen wissen, was die anderen so machen. [chuckling] Wir ändern den Filter auf "Alle Aufgaben", um alle Zuweisungen zu sehen. Dies gibt einen Überblick über die Verantwortlichkeiten im gesamten Team. [whispers] Und wer vielleicht gerade nichts zu tun hat.
       */
      logNarration('NARRATION_14', 'Aufgabenliste überprüfen');
      const tasksButton = page.getByTestId('tasks-button');
      await tasksButton.click();
      await page.waitForLoadState('domcontentloaded');
      await page.waitForTimeout(1000);

      const filterCombobox = page.getByRole('combobox', {
        name: /Filter.*Meine Aufgaben|My Tasks/i,
      });
      await filterCombobox.click();
      await page.waitForTimeout(400);

      await page.getByRole('option', { name: /Alle Aufgaben|All Tasks/i }).click();
      // Stay on the task list until the narration about it has finished — navigating away
      // mid-segment would show the event page while the voice still describes the tasks.
      await waitForNarration('NARRATION_14', page);

      await page.goto(eventUrl);
      await page.waitForLoadState('domcontentloaded');
      await page.waitForTimeout(1000);

      const topicButton = page.getByTestId('select-topic-button');
      await topicButton.scrollIntoViewIfNeeded({ timeout: 10000 });
      await expect(topicButton).toBeVisible({ timeout: 5000 });

      /*
       * NARRATION_15: [excited] Zurück auf der Event-Detailseite beginnen wir mit der inhaltlichen Planung! [enthusiastic] Der erste Schritt ist die Themenauswahl. Klicken Sie auf "Thema auswählen".
       */
      logNarration('NARRATION_15', 'Themenauswahl beginnen');
      await topicPage.openTopicSelection();
      await expect(topicPage.heatmapButton).toBeVisible({ timeout: 5000 });
      await page.waitForTimeout(1000);
      await waitForNarration('NARRATION_15', page);

      /*
       * NARRATION_16: [cheerful] Es öffnet sich eine Ansicht mit verschiedenen Themen-Kategorien. Die Standardansicht zeigt eine Liste, [excited] aber wir nutzen die Heat Map für eine bessere Übersicht. [playful] Denn wer liebt nicht eine gute Heat Map?
       */
      logNarration('NARRATION_16', 'Heat Map öffnen');
      await topicPage.openHeatmap();
      await page.waitForTimeout(1500);
      await waitForNarration('NARRATION_16', page);

      /*
       * NARRATION_17: [curious] Die Heat Map ist eine Zwei-Dimensionale Matrix, die Themen nach ihrer Popularität und Aktualität visualisiert. [excited] Helle Farben zeigen beliebte Themen, [casual] sozusagen die Rockstars unter den Architektur-Themen. [pause] Dunklere Farben zeigen weniger häufig gewählte Themen. [playful] Die Außenseiter, die auch eine Chance verdienen. [helpful] Dies hilft bei der strategischen Themenplanung basierend auf den Interessen der Teilnehmer.
       */
      logNarration('NARRATION_17', 'Heat Map erklären');
      await waitForNarration('NARRATION_17', page);

      /*
       * NARRATION_18: [instructional] Wir wählen ein Thema aus der Heat Map, indem wir auf eine Zelle klicken. [satisfied] Das System markiert die Auswahl und zeigt Details an. [excited] Nach der Bestätigung speichert das System das gewählte Thema und öffnet automatisch die Referenten-Brainstorming-Ansicht. [playful] Das System ist wie ein guter Assistent, es weiß immer, was als Nächstes kommt!
       */
      logNarration('NARRATION_18', 'Thema auswählen und bestätigen');
      const { row, column } = testConfig.topics.heatmapSelection;
      await topicPage.selectTopicFromHeatmap(row, column);
      await page.waitForTimeout(500);

      await topicPage.confirmSelection();
      await page.waitForLoadState('domcontentloaded');
      await page.waitForTimeout(1000);

      await expect(page.getByTestId('speaker-name-field')).toBeVisible({
        timeout: 10000,
      });
      console.log(`    ✓ Topic selected (row ${row}, col ${column})`);
      await waitForNarration('NARRATION_18', page);

      /*
       * NARRATION_19: [enthusiastic] Jetzt sammeln wir potenzielle Referenten für das gewählte Thema! [professional] In dieser Phase erstellen wir einen Pool von Kandidaten, die wir später kontaktieren werden. [pause] Wir fügen vier Referenten-Kandidaten hinzu. [strategic] Dies gibt uns ausreichend Optionen für die Kontaktaufnahme, falls nicht alle zusagen. [playful] Denn Referenten sind wie Katzen, manchmal sagen sie einfach nein, ohne Grund.
       */
      logNarration('NARRATION_19', 'Referenten-Kandidaten hinzufügen');
      const candidates = testConfig.speakerCandidates.map((c) => ({
        firstName: c.firstName,
        company: c.company,
        expertise: c.expertise,
        assignedUserName: c.assignedUserName,
      }));

      console.log(`    → Adding ${candidates.length} speaker candidates`);
      for (const [i, candidate] of candidates.entries()) {
        await paceWithinNarration('NARRATION_19', i, candidates.length, page);
        await speakerPage.addSpeakerCandidate(candidate);
      }
      await page.waitForTimeout(1000);
      console.log(`    ✓ All ${candidates.length} speakers added to pool`);
      await waitForNarration('NARRATION_19', page);

      /*
       * NARRATION_20: [confident] Nach dem Hinzufügen aller Kandidaten klicken wir auf "Weiter zur Kontaktierung". [satisfied] Das System wechselt automatisch zur Kanban-Ansicht, wo wir den Kontaktstatus verfolgen können. [excited] Kanban! Das klingt wichtig und organisiert!
       */
      logNarration('NARRATION_20', 'Zur Kontaktierung übergehen');
      await expect(speakerPage.proceedToOutreachButton).toBeVisible({ timeout: 5000 });
      await speakerPage.proceedToOutreach();
      await page.waitForLoadState('domcontentloaded');
      await page.waitForTimeout(1500);

      await expect(speakerPage.getCardByName('Nissim')).toBeVisible({ timeout: 10000 });
      console.log('    ✓ Proceeded to outreach phase');
      console.log('\n✅ Phase A Complete\n');
      await waitForNarration('NARRATION_20', page);

      // ========================================
      // PHASE B: SPEAKER OUTREACH (button-driven kanban, ADR-009)
      // ========================================
      console.log('\n📋 Phase B: Referenten-Kontaktierung\n');

      /*
       * NARRATION_21: [professional] Wir befinden uns jetzt in der Referenten-Kontaktierungs-Phase. [informative] Das Kanban-Board zeigt den Workflow in Spalten — von "Identifiziert" über "Kontaktiert" und "Bereit" bis "Zugesagt". [playful] Von "Wer ist das?" bis "Hurra, zugesagt!" [helpful] Und das Beste: Jede Karte hat einen Aktions-Button, der Ihnen immer genau den nächsten sinnvollen Schritt anbietet. [satisfied] Kein Rätselraten mehr, was als Nächstes zu tun ist.
       */
      logNarration('NARRATION_21', 'Kanban-Board mit Aktions-Buttons');
      await expect(page.getByTestId('status-lane-identified')).toBeVisible({ timeout: 10000 });
      await waitForNarration('NARRATION_21', page);

      /*
       * NARRATION_22: [methodical] Jetzt kontaktieren wir systematisch alle Referenten-Kandidaten. [instructional] Für jeden Kandidaten klicken wir auf der Karte auf "Kontakt erfassen". [professional] Im Dialog wählen wir die Kontaktmethode, zum Beispiel E-Mail oder Telefon, und halten Notizen zur Antwort fest. [playful] Sonst fragt nächste Woche jemand: "Haben wir den schon kontaktiert?" Und niemand weiß es. [chuckling] Chaos vermieden! [satisfied] Die Karten wandern dabei automatisch in die Spalte "Kontaktiert".
       */
      logNarration('NARRATION_22', 'Referenten kontaktieren (Kontakt erfassen)');
      for (const [i, contact] of testConfig.speakerOutreach.entries()) {
        await paceWithinNarration('NARRATION_22', i, testConfig.speakerOutreach.length, page);
        console.log(`    → Logging outreach for ${contact.cardName} (${contact.contactMethod})`);
        await speakerPage.logOutreach(
          contact.cardName,
          contact.contactMethod === 'in_person' ? 'in-person' : contact.contactMethod,
          contact.notes
        );
        await page.waitForTimeout(500);
        console.log(`    ✓ ${contact.cardName} contacted`);
      }
      console.log(`    ✓ All ${testConfig.speakerOutreach.length} contacts recorded`);
      await waitForNarration('NARRATION_22', page);

      /*
       * NARRATION_23: [positive] Drei Kandidaten haben positiv reagiert. [excited] Zeit, sie zu richtigen Referenten zu befördern! [instructional] Ein Klick auf "Zum Speaker befördern" öffnet die Detail-Ansicht. Dort legen wir für jeden Referenten ein Benutzerkonto mit Name und E-Mail-Adresse an. [informative] Damit erhält der Referent später Zugang zum Referenten-Portal. [playful] Das System erledigt die ganze Bürokratie im Hintergrund. [satisfied] Die Karten wandern in die Spalte "Bereit".
       */
      logNarration('NARRATION_23', 'Referenten befördern (CONTACTED → READY)');
      for (const [i, promoted] of testConfig.promotedSpeakers.entries()) {
        await paceWithinNarration('NARRATION_23', i, testConfig.promotedSpeakers.length, page);
        console.log(`    → Promoting ${promoted.cardName} to READY`);
        await page.goto(kanbanUrl);
        await page.waitForLoadState('domcontentloaded');
        await speakerPage.promoteCreatingNewUser(promoted.cardName, {
          ...promoted.user,
          email: factoryEmail(),
        });
        console.log(`    ✓ ${promoted.cardName} promoted (SPEAKER user created)`);
      }
      await page.waitForTimeout(1000);
      await waitForNarration('NARRATION_23', page);

      /*
       * Each accept gets its OWN narration segment (NARRATION_24/24B/24C) so the voice talks
       * through every accept — a single segment left ~55s of silent UI while accepts 2-3 ran.
       * NARRATION_24:  [confident] Jetzt erfassen wir die Zusagen. [informative] Normalerweise erhalten Referenten eine Einladung und antworten selbst im Referenten-Portal. [casual] Haben sie aber bereits mündlich zugesagt, erfassen wir das direkt. [instructional] Wir öffnen Nissims Karte, wählen "Zusage im Namen erfassen" und geben eine kurze Begründung an. [satisfied] Nissim ist dabei!
       * NARRATION_24B: [cheerful] Weiter mit Baltisar von Galenica. [instructional] Auch seine Karte öffnen wir, wählen erneut "Zusage im Namen erfassen" und bestätigen mit einer kurzen Notiz. [playful] Zwei von drei — läuft wie geschmiert!
       * NARRATION_24C: [enthusiastic] Und Andreas von der Mobiliar? [satisfied] Ebenfalls mit an Bord! [instructional] Auch seine Zusage erfassen wir im Namen, wieder mit kurzer Begründung. [excited] Damit haben wir drei zugesagte Referenten — genug, um den Abend zu füllen.
       */
      for (const promoted of testConfig.promotedSpeakers) {
        logNarration(promoted.marker, `Zusage erfassen: ${promoted.cardName}`);
        console.log(`    → Accepting on behalf: ${promoted.cardName}`);
        await page.goto(kanbanUrl);
        await page.waitForLoadState('domcontentloaded');
        await speakerPage.drawerStatusChange(
          promoted.cardName,
          'accept-on-behalf',
          testConfig.acceptOnBehalfReason
        );
        console.log(`    ✓ ${promoted.cardName} ACCEPTED`);
        await waitForNarration(promoted.marker, page);
      }

      /*
       * NARRATION_24D: [playful] Und Daniel? [dramatic] Daniel hat leider keine Zeit. [casual] Ihn lehnen wir mit "Mit Grund absagen" ab — natürlich ebenfalls mit Begründung. [informative] Vanessa lassen wir bewusst noch als Kandidatin im Pool, falls wir später doch noch jemanden brauchen. [chuckling] Referenten sind eben wie Katzen.
       */
      logNarration('NARRATION_24D', 'Daniels Absage + Vanessa bleibt im Pool');
      console.log(`    → Declining ${testConfig.declinedSpeaker.cardName}`);
      await page.goto(kanbanUrl);
      await page.waitForLoadState('domcontentloaded');
      await speakerPage.drawerStatusChange(
        testConfig.declinedSpeaker.cardName,
        'decline',
        testConfig.declinedSpeaker.reason
      );
      console.log(
        `    ✓ ${testConfig.declinedSpeaker.cardName} DECLINED (Vanessa stays CONTACTED)`
      );
      console.log('\n✅ Phase B Complete\n');
      await waitForNarration('NARRATION_24D', page);

      // ========================================
      // PHASE B.5: CONTENT SUBMISSION
      // ========================================
      console.log('\n📋 Phase B.5: Inhaltseinreichung\n');

      /*
       * NARRATION_25: [important] Bevor Referenten ihre Inhalte einreichen können, müssen wir das Thema veröffentlichen. [instructional] Wir navigieren zum Tab "Veröffentlichung" und klicken auf "Thema veröffentlichen". [informative] Im unteren Bereich ist ein Preview des Events auf der öffentlichen Seite ersichtlich. [satisfied] Schön, oder?
       */
      logNarration('NARRATION_25', 'Thema veröffentlichen');
      await page.goto(`${eventUrl}?tab=publishing`);
      await page.waitForLoadState('domcontentloaded');
      await page.waitForTimeout(500);

      await page.getByTestId('publish-topic-button').click();
      await page.waitForTimeout(2000);
      console.log('    ✓ Topic published');
      await waitForNarration('NARRATION_25', page);

      /*
       * NARRATION_26: [professional] Zurück im Kanban erfassen wir nun für jeden zugesagten Referenten die Präsentations-Inhalte. [instructional] Der Aktions-Button heißt jetzt "Inhalt erfassen". [casual] Titel, Abstract, die üblichen Verdächtigen. Der Referent ist bereits automatisch verknüpft. [methodical] Wir wiederholen diesen Prozess für alle drei Referenten. [playful] Copy, paste, repeat. [chuckling] Nein, Spaß, jeder Referent hat natürlich einzigartige Inhalte!
       */
      logNarration('NARRATION_26', 'Präsentations-Inhalte einreichen');
      for (const [i, presentation] of testConfig.presentations.entries()) {
        await paceWithinNarration('NARRATION_26', i, testConfig.presentations.length, page);
        console.log(`    → Submitting content for ${presentation.cardName}`);
        await page.goto(kanbanUrl);
        await page.waitForLoadState('domcontentloaded');
        await speakerPage.enterContent(presentation.cardName, {
          title: presentation.title,
          abstract: presentation.abstract,
        });
        console.log(`    ✓ Content submitted for ${presentation.cardName}`);
      }
      console.log('\n✅ Phase B.5 Complete\n');
      await waitForNarration('NARRATION_26', page);

      // ========================================
      // PHASE C: QUALITY REVIEW
      // ========================================
      console.log('\n📋 Phase C: Qualitätsprüfung\n');

      /*
       * NARRATION_27: [pause] Nach der Inhaltseinreichung folgt die Qualitätsprüfung. [professional] Da wir jetzt die Inhalte haben, können wir die Referenten veröffentlichen. [instructional] Wir klicken auf "Referenten veröffentlichen". [excited] Und jetzt kommt's! [satisfied] Nun sind auf der öffentlichen Webseite nicht nur das Thema, sondern auch die zugesagten Referenten mit ihrem Thema ersichtlich. [cheerful] Die Welt kann es sehen!
       */
      logNarration('NARRATION_27', 'Referenten veröffentlichen');
      await page.goto(`${eventUrl}?tab=publishing`);
      await page.waitForLoadState('domcontentloaded');
      await page.waitForTimeout(500);

      await page.getByTestId('publish-speakers-button').click();
      await page.waitForTimeout(1000);
      console.log('    ✓ Speakers published');
      await waitForNarration('NARRATION_27', page);

      /*
       * NARRATION_28: [professional] Zurück im Kanban prüfen wir nun jede eingereichte Präsentation. [instructional] Der Aktions-Button heißt diesmal "Inhalt prüfen". [playful] Wir spielen jetzt Qualitätskontrolle. [pause] Sieht gut aus, sieht gut aus, das auch. [confident] Wir genehmigen alle drei Präsentationen. [satisfied] Grünes Licht für alle!
       */
      logNarration('NARRATION_28', 'Präsentationen prüfen und genehmigen');
      for (const [i, presentation] of testConfig.presentations.entries()) {
        await paceWithinNarration('NARRATION_28', i, testConfig.presentations.length, page);
        console.log(`    → Approving content of ${presentation.cardName}`);
        await page.goto(kanbanUrl);
        await page.waitForLoadState('domcontentloaded');
        await speakerPage.approveContent(presentation.cardName);
        console.log(`    ✓ Content approved for ${presentation.cardName}`);
      }
      console.log('\n✅ Phase C Complete\n');
      await waitForNarration('NARRATION_28', page);

      // ========================================
      // PHASE D: SLOT ASSIGNMENT & PUBLISHING
      // ========================================
      console.log('\n📋 Phase D: Slot-Zuweisung und Veröffentlichung\n');

      await page.goto(eventUrl);
      await page.waitForTimeout(1000);

      /*
       * NARRATION_29: [instructional] Für die Slot-Zuweisung wechseln wir zur Sessions-Ansicht. Klicken Sie auf "Slot-Zuweisungen verwalten". [pause] Jetzt wird's zeitlich!
       */
      logNarration('NARRATION_29', 'Zur Sessions-Ansicht wechseln');
      await page.getByTestId('event-tab-speakers').click();
      await page.waitForTimeout(500);

      await page.getByTestId('sessions-view-toggle').click();
      await page.waitForTimeout(1000);
      console.log('    ✓ Sessions view loaded');

      await page.getByTestId('manage-slot-assignments-button').click();
      await page.waitForTimeout(1500);
      console.log('    ✓ Slot Assignment page opened');
      await waitForNarration('NARRATION_29', page);

      /*
       * NARRATION_30: [enthusiastic] Für eine schnelle initiale Planung nutzen wir die Auto-Assign-Funktion. [excited] Das System führt die automatische Zuweisung durch. [playful] Magie! Das System übernimmt die Arbeit. [informative] Durch Drag-und-Drop ist hier auch eine manuelle Zuweisung möglich. [casual] Falls Sie dem Computer nicht vertrauen oder einfach gerne Dinge herumschieben.
       */
      logNarration('NARRATION_30', 'Referenten automatisch zuweisen');
      await page.waitForTimeout(1000);

      const autoAssignButton = page.getByTestId('auto-assign-button');
      await expect(autoAssignButton).toBeVisible({ timeout: 5000 });
      await autoAssignButton.click();
      await page.waitForTimeout(500);

      const autoAssignModal = page.getByTestId('auto-assign-modal');
      await expect(autoAssignModal).toBeVisible({ timeout: 3000 });

      const confirmButton = page.getByTestId('auto-assign-confirm');
      await confirmButton.click();
      await page.waitForTimeout(3000);

      console.log('    ✓ Speakers auto-assigned to slots');
      await waitForNarration('NARRATION_30', page);

      /*
       * NARRATION_31: [calm] Nach der Slot-Zuweisung klicken wir auf "Zurück zur Veranstaltung".
       */
      logNarration('NARRATION_31', 'Zurück zur Veranstaltung');
      const backButton = page.getByRole('button', {
        name: /Zurück zur Veranstaltung|Back to Event/i,
      });
      if (await backButton.isVisible({ timeout: 2000 }).catch(() => false)) {
        await backButton.click();
        await page.waitForTimeout(2000);
      } else {
        await page.goto(eventUrl);
        await page.waitForTimeout(2000);
      }

      await expect(page.getByRole('tab', { name: /Übersicht|Overview/i })).toBeVisible({
        timeout: 5000,
      });
      await waitForNarration('NARRATION_31', page);

      /*
       * NARRATION_32: [dramatic] Der finale Schritt ist die Agenda-Veröffentlichung! [enthusiastic] Das große Finale! [instructional] Klicken Sie auf "Agenda veröffentlichen". [triumphant] Und... [excited] Der Event ist jetzt vollständig geplant und öffentlich inklusive detaillierter Agenda zugänglich! [cheerful] Konfetti! Feuerwerk! Okay, vielleicht nur in Gedanken, aber trotzdem! [satisfied] Wir haben es geschafft!
       */
      logNarration('NARRATION_32', 'Agenda veröffentlichen');
      await page.getByRole('tab', { name: /Veröffentlichung|Publishing/i }).click();
      await page.waitForTimeout(3000);

      await expect(page.getByTestId('publish-agenda-button')).toBeVisible({ timeout: 10000 });

      await page.getByTestId('publish-agenda-button').click();
      await page.waitForTimeout(2000);

      console.log('    ✓ Agenda published');
      console.log('\n✅ Phase D Complete\n');
      await waitForNarration('NARRATION_32', page);

      /*
       * NARRATION_32B (idea #3): the event is now LIVE on the public website. Open the public
       * event page and slowly scroll down so the viewer sees the published result — hero, speakers,
       * and the agenda/timeline — exactly as a visitor would.
       */
      logNarration('NARRATION_32B', 'Öffentliche Event-Seite — live');
      await page.goto(`http://localhost:8100/events/${testEventCode}`, {
        waitUntil: 'domcontentloaded',
      });
      await page.waitForTimeout(2500); // let the hero + sections render
      // Smooth slow scroll spread across the narration window (~5s of gentle downward scroll).
      const scrollSteps = 12;
      for (let i = 0; i < scrollSteps; i++) {
        await page.evaluate(
          (frac) => {
            const max = document.documentElement.scrollHeight - window.innerHeight;
            window.scrollTo({ top: max * frac, behavior: 'smooth' });
          },
          (i + 1) / scrollSteps
        );
        await paceWithinNarration('NARRATION_32B', i, scrollSteps, page);
      }
      console.log('    ✓ Public event page shown (slow scroll)');
      await waitForNarration('NARRATION_32B', page);

      // ========================================
      // PHASE E: ARCHIVAL
      // ========================================
      console.log('\n📋 Phase E: Archivierung\n');

      /*
       * NARRATION_33: [pause] Nach der Durchführung des Events archivieren wir es für die Historie. [professional] Wechseln Sie zum Tab "Übersicht" und klicken Sie auf "Bearbeiten". [playful] Zeit, das Event in Rente zu schicken.
       */
      logNarration('NARRATION_33', 'Event archivieren');
      await page.goto(eventUrl);
      await page.waitForTimeout(2000);

      await page.getByRole('tab', { name: /Übersicht|Overview/i }).click();
      await page.waitForTimeout(1000);

      const editButtonFinal = page.getByTestId('edit-event-button');
      await editButtonFinal.waitFor({ state: 'visible', timeout: 5000 });
      await editButtonFinal.click();
      await page.waitForTimeout(1500);

      const modalTitle = page
        .locator('.MuiDialog-root')
        .getByText(/Veranstaltung bearbeiten|Edit Event/i);
      await modalTitle.waitFor({ state: 'visible', timeout: 5000 });
      console.log('    ✓ Edit modal opened');
      await waitForNarration('NARRATION_33', page);

      /*
       * NARRATION_34: [clear] Im Formular wählen Sie "ARCHIVIERT" als Status. [casual] Das Event war toll, aber jetzt ist es Geschichte.
       */
      logNarration('NARRATION_34', 'Status auf ARCHIVIERT ändern');
      const statusSelect = page.getByTestId('event-status-select');
      await statusSelect.waitFor({ state: 'visible', timeout: 5000 });
      await statusSelect.click();
      await page.waitForTimeout(500);

      await page.getByRole('option', { name: /Archiviert|Archived/i }).click();
      await page.waitForTimeout(500);
      console.log('    ✓ Status changed to ARCHIVED');

      const saveButtonFinal = page.getByTestId('save-event-button');
      await saveButtonFinal.click();
      await page.waitForTimeout(1500);

      await modalTitle.waitFor({ state: 'visible', timeout: 3000 });
      console.log('    ✓ Validation error triggered (expected)');
      await waitForNarration('NARRATION_34', page);

      /*
       * NARRATION_35: [informative] Für Test-Zwecke können Sie die Workflow-Validierung überschreiben. [instructional] Aktivieren Sie die Checkbox "Workflow-Validierung überschreiben" und klicken Sie auf "Speichern". [playful] Das ist sozusagen der Notausgang, falls mal was nicht nach Plan läuft.
       */
      logNarration('NARRATION_35', 'Workflow-Validierung überschreiben');
      const overrideCheckbox = page.getByTestId('override-workflow-validation-checkbox');
      await overrideCheckbox.waitFor({ state: 'visible', timeout: 5000 });
      // Let the voice introduce the override ("Aktivieren Sie die Checkbox …") BEFORE we tick
      // it, then leave the checked box on screen a moment so the viewer connects word to action.
      await page.waitForTimeout(5000);
      await overrideCheckbox.check();
      console.log('    ✓ Override checkbox enabled');
      await page.waitForTimeout(2500);

      // Let the rest of NARRATION_35 (the "Notausgang" remark) play with the checked box still
      // visible, THEN save — so the modal closes only after the explanation is complete.
      await waitForNarration('NARRATION_35', page);
      await saveButtonFinal.click();
      await page.waitForTimeout(2000);

      console.log('    ✓ Event archived successfully');

      const archivedBadge = page.locator('text=/Archiviert|Archived/i').first();
      await archivedBadge.waitFor({ state: 'visible', timeout: 5000 });
      console.log('    ✓ ARCHIVED badge visible');

      console.log('\n✅ Phase E Complete: Event archived successfully\n');
      await page.waitForTimeout(1500);

      /*
       * NARRATION_36: [triumphant] Damit ist der vollständige Event-Workflow abgeschlossen! [excited] Von der ersten Idee bis zum Archiv, wir haben die ganze Reise gemeinsam gemacht! [satisfied] Sie sind jetzt ein Event-Management-Profi! [cheerful] Vielen Dank für Ihre Aufmerksamkeit! [playful] Und denken Sie daran: Events planen macht Spaß, [chuckling] zumindest mit der richtigen Software! [laughing] Tschüss!
       */
      logNarration('NARRATION_36', 'Workflow abgeschlossen - Vielen Dank');
      await waitForNarration('NARRATION_36', page);
    } catch (error) {
      console.error('\n❌ Screencast recording failed:', error);

      await page.screenshot({
        path: `docs/user-guide/assets/screenshots/workflow/screencast-ERROR-${Date.now()}.png`,
        fullPage: true,
      });

      throw error;
    }
  });
});
