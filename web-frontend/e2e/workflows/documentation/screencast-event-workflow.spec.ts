/**
 * Screencast Training Video E2E Test
 *
 * This test executes the complete event management workflow as ONE continuous flow
 * for video recording purposes. All test phases are merged into a single test.
 *
 * Differences from complete-event-workflow.spec.ts:
 * - All 6 phases (A-E) merged into ONE continuous test
 * - NO screenshot captures (video recording only)
 * - Console timing markers for subtitle synchronization
 * - Strategic pauses for narration pacing
 *
 * Workflow Coverage:
 * - Phase A: Setup (Event creation, task assignment, topic selection, speaker brainstorming)
 * - Phase B: Outreach (Speaker contact, kanban workflow)
 * - Phase B.5: Content Submission
 * - Phase C: Quality Review
 * - Phase D: Slot Assignment & Publishing
 * - Phase E: Archival
 *
 * Run:
 *   npm run test:e2e:screencast
 *   npm run test:e2e:screencast:headed
 */

import { test, expect } from '@playwright/test';
import { testConfig, getPresentation } from './test-data.config';
import { cleanupAfterTests } from './helpers/cleanup-helpers';
import { EventWorkflowPage } from './page-objects/EventWorkflowPage';
import { SpeakerManagementPage } from './page-objects/SpeakerManagementPage';
import { TopicSelectionPage } from './page-objects/TopicSelectionPage';
import { waitForNarration, logNarration, startTimer } from './screencast/timing-helper';

/**
 * Continuous Event Workflow Screencast
 * Single test running all phases sequentially for video recording
 */
test.describe('Event Workflow Screencast for Training Video', () => {
  let authToken: string;
  let testEventCode: string;

  test.beforeAll(async () => {
    console.log('\n🎬 Starting Event Workflow Screencast Recording\n');
    authToken = process.env.AUTH_TOKEN || '';
  });

  test.afterAll(async () => {
    console.log('\n🧹 Cleaning up test data...\n');
    if (authToken) {
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
      // Show public homepage first (display while NARRATION_01 plays)
      console.log('\n🌐 Navigating to public homepage...\n');
      await page.goto('https://staging.batbern.ch/');
      await page.waitForLoadState('networkidle');
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
      await page.waitForLoadState('networkidle');
      await expect(eventPage.createEventButton).toBeVisible({ timeout: 10000 });
      console.log('    ✓ Dashboard loaded - authentication successful');
      await waitForNarration('NARRATION_02', page);

      const abbrechen = page.locator('button:has-text("ABBRECHEN")');
      if (await abbrechen.isVisible()) {
        await abbrechen.click();
        await page.waitForTimeout(500);
      }

      /*
       * NARRATION_03: [enthusiastic] Jetzt erstellen wir ein brandneues Event! [excited] Klicken Sie auf den Button "Neue Veranstaltung" oben rechts. [pause] Boom! Ein modales Formular erscheint. [playful] Keine Sorge, es sieht nach viel aus, aber wir füllen das gemeinsam aus.
       */
      logNarration('NARRATION_03', 'Neues Event erstellen');
      await eventPage.clickCreateEvent();
      // await expect(eventPage.eventNumberField).toBeVisible({ timeout: 10000 });
      // await page.waitForTimeout(1000);
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
      await page.waitForLoadState('networkidle');
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

      // await expect(eventPage.createEventButton).toBeVisible({ timeout: 5000 });
      console.log(`    ✓ Event created: ${testEventCode}`);
      await waitForNarration('NARRATION_10', page);

      /*
       * NARRATION_11: [pause] Nach der Event-Erstellung navigieren wir zur Event-Detailseite. [professional] Hier können wir Aufgaben an Teammitglieder zuweisen. [playful] Denn warum sollten Sie alles alleine machen, wenn Sie ein ganzes Team haben? [chuckling] Klicken Sie auf "Bearbeiten" um das Event-Formular erneut zu öffnen.
       */
      logNarration('NARRATION_11', 'Zur Event-Detailseite navigieren');
      const eventUrl = `http://localhost:8100/organizer/events/${testEventCode}`;
      await page.goto(eventUrl);
      await page.waitForLoadState('networkidle');
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
        { taskName: 'Moderator Assignment', assignee: 'Andreas Grütter' },
        { taskName: 'Newsletter: Topic', assignee: 'Baltisar Oswald' },
        { taskName: 'Newsletter: Speaker', assignee: 'Baltisar Oswald' },
        { taskName: 'Newsletter: Final', assignee: 'Baltisar Oswald' },
      ];

      for (let i = 0; i < taskAssignments.length; i++) {
        const { taskName, assignee } = taskAssignments[i];
        console.log(`    → Assigning "${taskName}" to ${assignee}`);

        const taskRow = page.getByRole('listitem').filter({ hasText: taskName });
        const assigneeSelect = taskRow.getByRole('combobox');
        await assigneeSelect.scrollIntoViewIfNeeded();
        await assigneeSelect.click();
        await page.waitForTimeout(400);

        await page.getByRole('option', { name: assignee }).first().click();
        await page.waitForTimeout(400);
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
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(1000);

      const filterCombobox = page.getByRole('combobox', {
        name: /Filter.*Meine Aufgaben|My Tasks/i,
      });
      await filterCombobox.click();
      await page.waitForTimeout(400);

      await page.getByRole('option', { name: /Alle Aufgaben|All Tasks/i }).click();
      await page.waitForTimeout(5000);

      await page.goto(eventUrl);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(1000);
      await waitForNarration('NARRATION_14', page);

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
      await page.waitForLoadState('networkidle');
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
      await speakerPage.addMultipleSpeakers(candidates);
      await page.waitForTimeout(1000);
      console.log(`    ✓ All ${candidates.length} speakers added to pool`);
      await waitForNarration('NARRATION_19', page);

      /*
       * NARRATION_20: [confident] Nach dem Hinzufügen aller Kandidaten klicken wir auf "Weiter zur Kontaktierung". [satisfied] Das System wechselt automatisch zur Kanban-Ansicht, wo wir den Kontaktstatus verfolgen können. [excited] Kanban! Das klingt wichtig und organisiert!
       */
      logNarration('NARRATION_20', 'Zur Kontaktierung übergehen');
      await expect(speakerPage.proceedToOutreachButton).toBeVisible({ timeout: 5000 });
      await speakerPage.proceedToOutreach();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(1500);

      await expect(page.locator('text=/N.*Nissim.*ELCA/i').first()).toBeVisible({ timeout: 10000 });
      console.log('    ✓ Proceeded to outreach phase');
      console.log('\n✅ Phase A Complete\n');
      await waitForNarration('NARRATION_20', page);

      // ========================================
      // PHASE B: SPEAKER OUTREACH
      // ========================================
      console.log('\n📋 Phase B: Referenten-Kontaktierung\n');

      /*
       * NARRATION_21: [professional] Wir befinden uns jetzt in der Referenten-Kontaktierungs-Phase. [informative] Das Kanban-Board zeigt mehrere Spalten, die den Workflow abbilden. [playful] Von "Wer ist das?" über "Angefragt" bis "Hurra, zugesagt!" [helpful] Diese Visualisierung gibt einen schnellen Überblick über den Fortschritt der Referenten-Gewinnung.
       */
      logNarration('NARRATION_21', 'Kanban-Board für Referenten-Kontaktierung');
      await waitForNarration('NARRATION_21', page);

      /*
       * NARRATION_22: [methodical] Jetzt kontaktieren wir systematisch alle Referenten-Kandidaten. [instructional] Für jeden Kandidaten klicken wir auf die Karte und öffnen den Kontakt-Dialog. [professional] Diese Dokumentation ist wichtig für die Nachverfolgung und für andere Teammitglieder, die den Status einsehen müssen. [playful] Sonst fragt nächste Woche jemand: "Haben wir den schon kontaktiert?" Und niemand weiß es. [chuckling] Chaos vermieden!
       */
      logNarration('NARRATION_22', 'Referenten kontaktieren');
      for (let i = 0; i < testConfig.speakerOutreach.length; i++) {
        const contact = testConfig.speakerOutreach[i];
        console.log(`    → Contacting speaker ${i + 1}: ${contact.displayName}`);

        await speakerPage.contactSpeaker(contact.displayName, contact.contactMethod, contact.notes);
        await page.waitForTimeout(500);
        console.log(`    ✓ Speaker ${i + 1} contacted via ${contact.contactMethod}`);
      }

      console.log(`    ✓ All ${testConfig.speakerOutreach.length} contacts recorded`);
      await waitForNarration('NARRATION_22', page);

      /*
       * NARRATION_23: [positive] Nachdem wir positive Rückmeldungen erhalten haben, [excited] verschieben wir Referenten durch den Workflow! [playful] Das ist wie Tetris, nur mit Menschen. [instructional] Wir nutzen Drag-and-Drop, um Karten zwischen den Spalten zu verschieben. [clear] Wir verschieben vier Referenten in die "READY"-Spalte.
       */
      logNarration('NARRATION_23', 'Referenten zu READY verschieben');
      await page.waitForTimeout(1000);

      const readyColumn = page.getByTestId('status-lane-READY');
      const speakersToMove = [
        { name: 'N Nissim ELCA AI', label: 'Nissim' },
        { name: 'B Balti Galenica AI', label: 'Balti' },
        { name: 'A Andreas Mobiliar AI', label: 'Andreas' },
        { name: 'D Daniel BKW AI', label: 'Daniel' },
      ];

      for (const speaker of speakersToMove) {
        console.log(`    → Dragging ${speaker.label} to READY`);
        const speakerCard = page.getByRole('button', { name: speaker.name });

        await expect(speakerCard).toBeVisible({ timeout: 5000 });
        await page.waitForTimeout(200);

        const cardBox = await speakerCard.boundingBox();
        const columnBox = await readyColumn.boundingBox();

        if (!cardBox || !columnBox) continue;

        await page.mouse.move(cardBox.x + cardBox.width / 2, cardBox.y + cardBox.height / 2);
        await page.mouse.down();
        await page.waitForTimeout(100);

        await page.mouse.move(
          columnBox.x + columnBox.width / 2,
          columnBox.y + columnBox.height / 2,
          {
            steps: 10,
          }
        );
        await page.waitForTimeout(100);

        await page.mouse.up();
        await page.waitForTimeout(200);

        const confirmButton = page.getByTestId('status-change-confirm');
        if (await confirmButton.isVisible({ timeout: 2000 }).catch(() => false)) {
          await confirmButton.click();
          await page.waitForTimeout(200);
        }
      }

      await page.waitForTimeout(2000);
      console.log('    ✓ All speakers moved to READY');
      await waitForNarration('NARRATION_23', page);

      /*
       * NARRATION_24: [confident] Im nächsten Schritt verschieben wir Referenten von "READY" zu "ACCEPTED". [satisfied] Alle vier READY-Referenten werden zu ACCEPTED verschoben. [excited] Das läuft wie geschmiert!
       */
      logNarration('NARRATION_24', 'Referenten zu ACCEPTED verschieben');
      await page.waitForTimeout(200);

      const acceptedColumn = page.getByTestId('status-lane-ACCEPTED');
      await expect(acceptedColumn).toBeVisible({ timeout: 5000 });

      for (const speaker of speakersToMove) {
        console.log(`    → Dragging ${speaker.label} to ACCEPTED`);
        await page.waitForTimeout(500);

        const speakerCard = page.getByRole('button', { name: speaker.name });
        await expect(speakerCard).toBeVisible({ timeout: 5000 });
        await page.waitForTimeout(500);

        const cardBox = await speakerCard.boundingBox();
        const columnBox = await acceptedColumn.boundingBox();

        if (!cardBox || !columnBox) continue;

        await page.mouse.move(cardBox.x + cardBox.width / 2, cardBox.y + cardBox.height / 2);
        await page.mouse.down();
        await page.waitForTimeout(100);

        await page.mouse.move(
          columnBox.x + columnBox.width / 2,
          columnBox.y + columnBox.height / 2,
          {
            steps: 10,
          }
        );
        await page.waitForTimeout(100);

        await page.mouse.up();
        await page.waitForTimeout(300);

        const confirmButton = page.getByTestId('status-change-confirm');
        if (await confirmButton.isVisible({ timeout: 2000 }).catch(() => false)) {
          await confirmButton.click();
          await page.waitForTimeout(800);
        }
      }

      await page.waitForTimeout(200);
      console.log('    ✓ All speakers moved to ACCEPTED');
      console.log('\n✅ Phase B Complete\n');
      await waitForNarration('NARRATION_24', page);

      // ========================================
      // PHASE B.5: CONTENT SUBMISSION
      // ========================================
      console.log('\n📋 Phase B.5: Inhaltseinreichung\n');

      /*
       * NARRATION_25: [important] Bevor Referenten ihre Inhalte einreichen können, müssen wir das Thema veröffentlichen. [instructional] Wir navigieren zum Tab "Veröffentlichung" und klicken auf "Thema veröffentlichen". [informative] Im unteren Bereich ist ein Preview des Events auf der öffentlichen Seite ersichtlich. [satisfied] Schön, oder?
       */
      logNarration('NARRATION_25', 'Thema veröffentlichen');
      await page.goto(`http://localhost:8100/organizer/events/${testEventCode}?tab=publishing`);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);

      await page.getByTestId('publish-topic-button').click();
      await page.waitForTimeout(2000);
      console.log('    ✓ Topic published');

      await waitForNarration('NARRATION_25', page);

      await page.getByTestId('event-tab-speakers').click();
      await page.waitForTimeout(1000);

      /*
       * NARRATION_26: [professional] Zurück im Referenten-Tab reichen wir nun für jeden Referenten die Präsentations-Inhalte ein. [casual] Titel, Abstract, die üblichen Verdächtigen. [methodical] Wir wiederholen diesen Prozess für alle drei Referenten mit zugesagten Präsentationen. [playful] Copy, paste, repeat. [chuckling] Nein, Spaß, jeder Referent hat natürlich einzigartige Inhalte!
       */
      logNarration('NARRATION_26', 'Präsentations-Inhalte einreichen');
      for (let i = 0; i < testConfig.presentations.length; i++) {
        const presentation = getPresentation(i);
        const speakerCandidate = testConfig.speakerCandidates[presentation.speakerIndex];

        console.log(`    → Submitting content for ${speakerCandidate.firstName}`);

        const cardPattern = new RegExp(
          `${speakerCandidate.firstName.charAt(0)} ${speakerCandidate.firstName}.*${speakerCandidate.company}`,
          'i'
        );

        const speakerCard = page.getByRole('button', { name: cardPattern });
        await speakerCard.waitFor({ state: 'visible', timeout: 5000 });
        await speakerCard.click();
        await page.waitForTimeout(1000);

        if (presentation.speakerSearchTerm) {
          const searchField = page.getByTestId('speaker-search-field');
          await searchField.click();
          await searchField.fill(presentation.speakerSearchTerm);
          await page.waitForTimeout(1000);

          await page.getByText(presentation.actualSpeakerName).first().click();
          await page.waitForTimeout(500);
        }

        await page.getByTestId('presentation-title-field').click();
        await page.getByTestId('presentation-title-field').fill(presentation.title);

        await page.getByTestId('presentation-abstract-field').click();
        await page.getByTestId('presentation-abstract-field').fill(presentation.abstract);

        await page.getByTestId('submit-speaker-content-button').click();
        await page.waitForTimeout(2000);
        await page.waitForLoadState('networkidle');

        console.log(`    ✓ Content submitted for ${speakerCandidate.firstName}`);
      }

      console.log('\n✅ Phase B.5 Complete\n');
      await waitForNarration('NARRATION_26', page);

      // ========================================
      // PHASE C: QUALITY REVIEW
      // ========================================
      console.log('\n📋 Phase C: Qualitätsprüfung\n');

      await page.goto(`http://localhost:8100/organizer/events/${testEventCode}`);
      await page.waitForLoadState('networkidle');

      await page.getByRole('tab', { name: /Veröffentlichung|Publishing/i }).click();
      await page.waitForTimeout(500);

      /*
       * NARRATION_27: [pause] Nach der Inhaltseinreichung folgt die Qualitätsprüfung. [professional] Da wir jetzt die Inhalte haben, können wir die Referenten veröffentlichen. [instructional] Wir klicken auf "Referenten veröffentlichen". [excited] Und jetzt kommt's! [satisfied] Nun sind auf der öffentlichen Webseite nicht nur das Thema, sondern auch die zugesagten Referenten mit ihrem Thema ersichtlich. [cheerful] Die Welt kann es sehen!
       */
      logNarration('NARRATION_27', 'Referenten veröffentlichen');
      await page.getByTestId('publish-speakers-button').click();
      await page.waitForTimeout(1000);
      console.log('    ✓ Speakers published');

      await page.getByTestId('event-tab-speakers').click();
      await page.waitForTimeout(500);
      await waitForNarration('NARRATION_27', page);

      /*
       * NARRATION_28: [professional] Zurück im Referenten-Tab prüfen und genehmigen wir nun jede eingereichte Präsentation. [playful] Wir spielen jetzt Qualitätskontrolle. [pause] Sieht gut aus, sieht gut aus, das auch. [confident] Wir genehmigen alle drei Präsentationen. [satisfied] Grünes Licht für alle!
       */
      logNarration('NARRATION_28', 'Präsentationen genehmigen');
      for (let i = 0; i < testConfig.presentations.length; i++) {
        const presentation = getPresentation(i);
        console.log(`    → Approving ${presentation.title}`);

        const presentationCard = page.getByRole('button', {
          name: new RegExp(presentation.title),
        });
        await presentationCard.click();
        await page.waitForTimeout(500);

        await page.getByTestId('approve-content-button').click();
        await page.waitForTimeout(1500);

        console.log(`    ✓ Content approved`);
      }

      console.log('\n✅ Phase C Complete\n');
      await waitForNarration('NARRATION_28', page);

      // ========================================
      // PHASE D: SLOT ASSIGNMENT & PUBLISHING
      // ========================================
      console.log('\n📋 Phase D: Slot-Zuweisung und Veröffentlichung\n');

      await page.goto(`http://localhost:8100/organizer/events/${testEventCode}`);
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
        await page.goto(`http://localhost:8100/organizer/events/${testEventCode}`);
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

      // ========================================
      // PHASE E: ARCHIVAL
      // ========================================
      console.log('\n📋 Phase E: Archivierung\n');

      /*
       * NARRATION_33: [pause] Nach der Durchführung des Events archivieren wir es für die Historie. [professional] Wechseln Sie zum Tab "Übersicht" und klicken Sie auf "Bearbeiten". [playful] Zeit, das Event in Rente zu schicken.
       */
      logNarration('NARRATION_33', 'Event archivieren');
      await page.goto(`http://localhost:8100/organizer/events/${testEventCode}`);
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
      await overrideCheckbox.check();
      await page.waitForTimeout(500);
      console.log('    ✓ Override checkbox enabled');

      await saveButtonFinal.click();
      await page.waitForTimeout(2000);

      // Wait for modal to close
      await page.waitForTimeout(2000);

      console.log('    ✓ Event archived successfully');

      const archivedBadge = page.locator('text=/Archiviert|Archived/i').first();
      await archivedBadge.waitFor({ state: 'visible', timeout: 5000 });
      console.log('    ✓ ARCHIVED badge visible');

      console.log('\n✅ Phase E Complete: Event archived successfully\n');
      await page.waitForTimeout(2000);
      await waitForNarration('NARRATION_35', page);

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
