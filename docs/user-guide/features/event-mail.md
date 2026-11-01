# Event mail page (Communications tab)

> **Last Updated:** 2026-11-01 · Epic 16, Stories 16.3, 16.4, 16.8a, 16.8b

The **Communications** tab of an event is its mail page. It answers "did the speakers get the
slides mail?" without asking the team.

## What went out

Every mail sent for the event, newest first, the newsletter included.

- **Each row:** date · sender → audience · *Template* or *Own text*, the subject, and on the right
  how many were delivered and how many failed. "automatic" means no organizer sent it (a
  scheduler or a registration).
- **Filters:** All · Newsletter · Registrants · Speakers · Venue & Catering · Partners.
- **Automatic mails** (confirmations, reminders) are collapsed per template and day
  ("registration-confirmation · 14 mails"). A click expands them into single mails.
- **A click on a mail** opens it below its row, exactly as it was sent, with its recipients beside
  it (on a phone: full screen). A second click closes it. Failed recipients
  come first, with the reason: the bounce type (`Permanent/NoEmail`), `complaint`, or the reason
  SES refused it.
- **Links and codes are not shown.** One-click links, registration codes and temporary passwords
  were replaced by `[redacted]` when the mail was recorded.
- **A newsletter that is still sending** shows its progress and refreshes by itself.
- **A newsletter that left recipients out** (ended partial or failed) has **Retry** on its row: it
  sends again to the failed recipients only.
- **Test sends are hidden.** A test to the organizers is recorded with a TEST mark but hidden from
  the list; switch on **Show test sends** to see them.

Limits:
- Mails sent before 30 October 2026 are not listed, except newsletters and registrant notices,
  which come from the newsletter history. Their text was not recorded, so the mail says so.
- For the newsletter, "delivered" means sent and not bounced: SES reports no deliveries for the
  newsletter. For all other mail it is SES's delivery report.
- Mail is kept for 12 months after the event (see the privacy rules in
  `docs/architecture/08-operations-security.md`).

## Handled automatically

Collapsed, counts only. Today: failed deliveries for the event (bounces, complaints, rejects, sends
SES refused). Accepts, declines and out-of-office replies follow with Story 16.2.

## + New mail

The menu offers **Newsletter, Registrants, Speakers, Venue & Catering, Partners**. The first four
open the same composer at the top of the page (full screen on a phone), with the same steps:

1. **Template.** *Own text* is shown but not available yet (Story 16.5).
   - Newsletter: `newsletter-event` is preselected; *Send as reminder* adds the reminder prefix.
   - Registrants: a registrant notice (for example `slides-online`).
   - Speakers: *Automatic* or one of the six reminder templates. Automatic picks each speaker's
     tier from that speaker's deadline, so one send can mix tiers; choose which speakers (invited
     without an answer, or accepted without content). A chosen template decides that itself.
   - Venue & Catering: the template, the roles (venue, catering: the first is To, the other Cc) and
     optional notes.
2. **Language.** Newsletter and venue: DE or EN for everyone. Registrants and speakers each get
   their own language; the preview shows the language of the recipient shown.
3. **Recipients**, counted by the server, with up to eight names, and who is left out and why
   (unsubscribed, reminders turned off, no address, duplicate).
4. **Preview** of the mail exactly as one real recipient gets it. Registrants and speakers step
   through the recipients with ‹ › ("2/14"). Links and codes are shown as `[redacted]`. An unknown
   placeholder is shown in red and blocks sending.
5. **Where replies go.**
6. **Send to organizers (test)** sends the mail shown to every organizer, subject `[TEST] `, with
   dummy links. The composer stays open. **Send to N recipients** asks once more ("Really send to
   …?"), then sends.

After a real send the composer closes, a message confirms it, and the new mail is highlighted on top
of "What went out" ("sending …" first, then the counts). If the send cannot happen (the registrant
notice was already sent, a newsletter is still running), the composer says why.

**Partners** still lists the event's partner meeting with its calendar invite. Without a meeting it
links to the partner meetings page.

Esc closes the composer; the focus goes back to "+ New mail".
