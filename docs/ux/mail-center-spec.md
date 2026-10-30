# Mail Center — UX spec (Epic 16)

**Prototype:** [`mail-center-prototype.html`](mail-center-prototype.html). Open it in a browser; it has no build step and no backend.
**Status:** approved by the owner on 2026-10-30 (v4), plus the Partners view and shared composer (v5) added the same day.
**Epic:** [`docs/prd/epic-16-mail-center.md`](../prd/epic-16-mail-center.md)

## What the prototype is and is not

- **It is the layout and flow reference.** It defines the sections, the order of things, wording, empty states and the composer steps.
- **It is not a component source.** It is Tailwind; the organizer area is MUI with theme primary `#2C5F7C`. Build with MUI.
- **All person names and mail texts are synthetic.** The event data (BATbern60, 6.11.2026, Zentrum Paul Klee) and the counts were real on 2026-10-30.
- **Template texts in the prototype are illustrative.** The real texts come from the admin template store (e.g. the six `speaker-reminder-*` templates).

## Event › Kommunikation

One column, max width about 900 px, three sections in this order:

1. **Braucht eine Antwort** (Story 16.2). Human mail for this event with status and assignee. The count badge shows the open ones only. Empty state: one line ("Nichts offen."), never an empty table. Done items stay visible, greyed.
2. **Was rausging** (Story 16.3).
   - Filter chips: Alle · Newsletter · Angemeldete · Speaker · Venue & Catering · Partner.
   - Each row: date · sender → audience · "Vorlage" or "eigener Text" on line 1, the subject on line 2, and delivery stats on the right ("1049 zugestellt · 11 fehlgeschlagen"; for partner invites, the RSVP counts).
   - A click opens the side panel with the mail exactly as sent, the recipients, and what came back.
   - Automatic transactional mails (confirmations, invitations) are collapsed per template ("Anmeldebestätigungen · laufend").
   - A filter chip with nothing to show displays "Nichts für diese Gruppe.".
3. **Automatisch verarbeitet** (16.3 / 16.2). Collapsed. Counts only: accepts, declines, bounces, out-of-office replies, calendar RSVPs.

The **"+ Neue Mail"** button sits in the header of section 1. Its menu lists, each with a one-line hint (count, or who answers):
- Newsletter
- Angemeldete
- Speaker ("Antworten an {moderator}")
- Venue & Catering
- Partner

## Composer (side panel, Stories 16.4 → 16.8 → 16.5)

The same steps for every audience:

1. **[Vorlage | Eigener Text]** toggle. Own text arrives with 16.5.
2. **Vorlage:** a dropdown of this audience's templates.
   - For speakers, all six reminder templates are listed, and the auto-detected one is preselected with a hint line ("Stufe automatisch erkannt: Inhalte, Stufe 2 …").
   - Below it is the link "Diese Vorlage als Ausgangspunkt nehmen und anpassen →". It is not offered for the partner invite with calendar.
3. **Eigener Text:** subject and body, with placeholder chips above the body. Only this audience's variables are offered, with plain labels (Name, Frist, Vortragstitel …). A click inserts `{{var}}` at the cursor. For the newsletter, a note says the unsubscribe link is added automatically.
4. **Empfänger:** the count and up to 8 name chips, all resolved by the server. Audiences without personal fields say so ("keine persönlichen Felder").
5. **Vorschau:** the mail in the BATbern layout for one real recipient.
   - ‹ › steps through the recipients, showing "(3/8)".
   - Substituted values are highlighted yellow.
   - An unknown placeholder is shown red, with a message, and disables both send buttons.
6. **Reply note:** where answers go ("Antworten gehen an {moderator} und erscheinen hier unter „Braucht eine Antwort“").
7. **Footer:**
   - "Als Vorlage speichern" (own text only);
   - **[An Organisatoren (Test)]**, an outlined button: the current preview goes to all organizers with `[TEST]` in the subject, and the toast says so;
   - **[An {N Empfänger} senden]**, the primary button, followed by an inline confirm ("Wirklich an … senden?" · Zurück · Ja, senden).
8. **After sending:** the panel closes, a toast confirms, and the new row appears on top of "Was rausging", highlighted, first showing "wird gesendet …" and then the counts.

**Partner:**
- Template 1, "Einladung Partner-Meeting mit Kalender", is sent by the partner service (ICS, RSVP tracking). Its text is still hard-coded, as the hint says.
- Template 2, "Partner-Info", is a normal template in the admin store (new category PARTNER).

## Global "Mail" (top nav, Story 16.7)

- The badge shows the open count across all events.
- Three lists: open items across events (each row shows its event), mail without an event (`info@`, `support@`) with an "Event zuordnen" control, and spam (collapsed, kept 30 days).

## Mobile (phone width)

- Single column, no horizontal scroll. The top nav hides secondary items.
- The composer is full screen; the footer buttons stay visible; the filter chips wrap.

## Language

The prototype is German. UI keys go into all 10 locales (EN and DE first-class). Mail templates are DE and EN only.
