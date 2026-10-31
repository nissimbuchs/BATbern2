# Event mail page (Communications tab)

> **Last Updated:** 2026-10-30 · Epic 16, Stories 16.3 + 16.4

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

The menu offers **Newsletter, Registrants, Speakers, Venue & Catering, Partners**. Each opens the
compose screen you know at the top of the page (full screen on a phone). Sending, the preview and the
confirmation step are unchanged.

- After a send the compose screen closes, a message confirms it, and the mail appears on top of
  "What went out".
- A **test** newsletter keeps it open, so the real send can follow.
- If some speaker reminders fail, it stays open and lists them, with a retry.
- **Retry** of a newsletter that left recipients out is in the newsletter compose screen.
- **Partners** lists the event's partner meeting with its calendar invite. Without a meeting it
  links to the partner meetings page.

Esc closes the panel; the focus goes back to "+ New mail".
