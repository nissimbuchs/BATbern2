# Changelog

## Overview

Version history and release notes for the BATbern platform. Releases follow [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`

- **MAJOR**: Breaking changes requiring user action
- **MINOR**: New features (backward compatible)
- **PATCH**: Bug fixes and minor improvements

**Current Version**: v1.4.0
**Last Updated**: 2026-11-04

---

## Release Format

Each release includes:
- **Version Number** & Release Date
- **Type** (Major / Minor / Patch)
- **New Features** - Functional additions
- **Improvements** - Enhancements to existing features
- **Bug Fixes** - Resolved issues
- **Breaking Changes** - Actions required by users (if any)
- **Known Issues** - Limitations to be addressed

---

## Upcoming

Only items that are not live yet. Everything else is listed under its release below.

- 📋 Epic 16 Mail Center: HTML in Java mails moved to templates (16.12); one mail value for the email service (16.13)
- 📋 Epic 13 close-out: Jackson 3 switch-over, dependency versions handed back to the Spring Boot BOM
- 📋 Epic 15: LinkedIn post drafts (Story 15.8)
- 📋 Apple / generic OIDC SSO (Story 12-10) — extends "Continue with Google" with additional providers
- 📋 Epic 3: production import of the historical data (pending a user trigger)

---

## v1.4.x - Current Release

### Unreleased — after BATbern60

**Improvement — No e-mail address in links, one get-or-create shape (4.10d)**:
- ✅ Changing, removing, confirming again or making primary one of your further e-mail addresses no longer puts the address in the web address, so it does not end up in server logs or the browser history. Nothing changes on the page.
- ⚠️ API, breaking for scripts: the four operations on one further address take its `id` from `GET /api/v1/users/me` (`additionalEmails[].id`): `PATCH`, `DELETE /api/v1/users/me/additional-emails/{emailId}`, `POST …/{emailId}/make-primary`, `POST …/{emailId}/resend-verification` (was the URL-encoded address; that now answers 400). Making an address primary gives the addresses new ids. Get-or-create of a company is `POST /api/v1/companies/get-or-create` (was `POST /companies:get-or-create`, now 404) and answers 201 when it created the company, 200 when it found it.

**Improvement — Own notifications only, fewer verb paths (4.10b)**:
- ✅ Notifications are each organizer's own: marking read, deleting, the list and the unread count only reach the signed-in organizer's notifications. Before, an organizer could read, mark and delete another organizer's notifications by their ID.
- ✅ A user's event history (organizer view of a user) lists every event, also for people with a long history; it is read page by page.
- ⚠️ API, breaking for scripts: `PATCH /api/v1/notifications/{id}` and `PATCH /api/v1/notifications` with `status: READ` mark notifications read (was `PUT …/{id}/read`, `…/batch-read`); `GET /notifications` and `/count` take no `username`; `DELETE /notifications/batch-delete` and `GET /notifications/history` are gone. The AI prompt reset is `DELETE /api/v1/ai-prompts/{key}/override` (was `POST …/reset`). The session import is `POST /api/v1/events/{eventCode}/sessions` with `imports` (was `…/sessions/batch-import`), and a single create answers `{session}`. Event types are `/api/v1/event-types` (was `/events/types`), the homepage photos `GET /api/v1/public/event-photos` (was `/events/recent-photos`), a user's event history `GET /api/v1/registrations?filter={"attendeeUsername":…}` (was `GET /events/registrations` with a header).

**Improvement — Participants and newsletter, one way each (4.10c)**:
- ✅ The participant CSV import (Administration → Import data) sends each event's people together. A person who attended several events shows **Success** only when every event worked, otherwise the events that failed and why. One person that cannot be imported no longer stops the others.
- ✅ Account settings show the newsletter per address, like the profile page. Before, the switch there covered the login address only.
- ✅ A signed-in visitor who receives the newsletter on any of their addresses no longer sees the subscribe form on the homepage.
- ⚠️ API, breaking for scripts: participants are created only with `POST /api/v1/events/{eventCode}/participants` and exactly one of `username` (single add), `people` (import of up to 100 people for that event) or `stakeholders: true`; `POST /events/batch_registrations` and `…/enroll-stakeholders` are gone. Subscribing is `POST /api/v1/newsletter/subscribers` (was `/newsletter/subscribe`); a user's newsletter is `GET`/`PATCH /api/v1/newsletter/my-subscriptions` per address; `/newsletter/my-subscription` and the PUT are gone.

**Improvement — One address per profile, picture and speaker (4.10)**:
- ✅ A speaker of one event can no longer be read or changed through another event's page or link. Before, most speaker actions (status, review, outreach, history, content) checked only the speaker's ID, so an organizer working in one event could act on a speaker of another one by mistake.
- ✅ Outreach notes record the organizer's user name. Before, they recorded the organizer's e-mail address (notes written before keep it).
- ✅ The profile page saves with the user name read from the profile, so the first save after the first sign-in works as well.
- ⚠️ API, breaking for scripts: a profile is `PATCH /api/v1/users/{username}` and its picture `/api/v1/users/{username}/picture*`, for the user themself and for organizers; `PATCH /users/me`, `PUT /users/{username}` and `/users/me/picture*` are gone. A speaker is `/api/v1/events/{eventCode}/speakers/{speakerId}` (the pool entry ID); `…/speakers/pool*`, `…/speaker-pool/{id}/reminders` (now the field `remindersDisabled` of the PATCH), `…/speakers/invite` and `…/speakers/{username}/send-invitation` are gone; a self-nomination is `POST …/speaker-nominations`.

**Improvement — Auto-publish schedule per event, one way to change the event state (4.9b)**:
- ✅ The Publishing tab has an **Auto-publish schedule**: speakers and agenda can each be switched off for one event, and the number of days before the event changed (1 to 365). The daily job follows it. Before, the job always published 30 and 14 days before, whatever was set.
- ✅ The phase timeline shows the scheduled auto-publish date of each phase from that schedule.
- ✅ Publishing the agenda lists every reason it is not ready (no date, a date in the past, sessions without a time) instead of the first one only. Before, the reasons of a refused publish were not shown at all.
- ⚠️ API, breaking for scripts: the event state changes only through `POST /api/v1/events/{eventCode}/transition`; phases are `POST …/phases/{phase}/publish` and `…/unpublish`; status and schedule are `GET`/`PATCH …/publishing`. The former `…/publish`, `…/publish/{phase}`, `…/unpublish/{phase}`, `…/publish/schedule`, `…/publish/status` and `…/workflow/transition` answer 404, and a `workflowState` in an event PUT or PATCH answers 400.

**Improvement — One way to send mail to an audience (16.8c)**:
- ✅ Newsletter, registrant notices (for example "the slides are online"), speaker reminders and venue and catering mail go out only from **+ New mail** on the event's Communications tab. The old Newsletter, Registrant notices and Venue sections and the reminder button per speaker were already hidden since 16.8b; their code and server endpoints are now removed. A newsletter's progress and **Retry** are on its row in the Mail Center.
- ✅ The venue and catering contacts in Administration now say that their mails are sent from the Communications tab.

**Improvement — No-shows (after BATbern60)**:
- ✅ A registration can be marked **No-show**: registered for the event and did not come. The participant list has a *No-show* filter and chip, and the attendee's own event history says so.
- ✅ No-shows do not count as attendance: the analytics count confirmed and attended registrations only, so after an event the people who came are set to *Attended* and the others to *No-show*.
- ✅ Mail Center: own text to the registrants can go to chosen statuses, for example the no-shows only. A template still goes to registered, confirmed and attended.

**Bug Fix — Creating an event says what is missing (17.0)**:
- ✅ The "Create event" form marks venue name, venue address and registration deadline as required and shows an error under each one left empty. Before, a missing venue blocked the save without any message, and a missing deadline was sent empty and refused by the server.
- ✅ "Save as draft" checks the fields an event cannot be stored without (title, date, type, venue name, address and capacity, registration deadline) instead of sending a draft the server would refuse.

**Improvement — Every company has a short ID (17.0)**:
- ✅ A company created in the company list gets the same kind of short ID as a company named during a registration: at most 12 letters and digits, from its name ("Gamma Innovations" → "gammainnovat"). The name as typed stays the display name. Before, the ID was the name exactly as typed, spaces and all, and such a company could not become a partner or be the company of a user.
- ✅ Existing companies with a longer or spaced ID got a short one, and everything that pointed to them (logos, logo requests, partners, registrations) points to the new ID. A company created a second time with the same name is refused, as before.

**Bug Fix — Topic heat map shows real attendance (17.0)**:
- ✅ The topic heat maps (topic backlog and topic selection) show for each use of a topic the attendance of its event: the confirmed and attended registrations, counted as on the analytics page, once the event has taken place. Before, the value was never filled in, so the tooltip read "null attendees" or 0. An event still to come shows no attendance. The "Avg Engagement" line is gone: the platform never measured engagement (older entries showed an estimate derived from the event's state), so it could show nothing true.

**Bug Fix — "Sort by" in the partner directory works (17.0)**:
- ✅ The organizers' partner directory sorts by **Company Name** (A to Z) or **Partnership Tier** (Strategic first, then Platinum, Gold, Silver, Bronze; equal tiers by name), across all pages. The control never had an effect: the list always came sorted by company name. The options "Engagement Score" and "Last Event" are gone: the platform records neither for a partner, so they could not sort anything.

**Bug Fix — Partner meeting invites show the event's real times (17.0)**:
- ✅ The BATbern event entry in a partner meeting invite now has the same start and end as the registration confirmation and the event page. Before the agenda was published it used fixed times of its own (18:00–22:00 for an evening event) instead of the times set for the event.

**Improvement — Search finds what you mean, everywhere (Story 17.17)**:
- ✅ Every search box (users, participants, newsletter subscribers, companies, partners, topics, events and the archive) searches all records, not only the ones on the current page. The partner list and the topic picker used to search only the rows already loaded.
- ✅ Spelling variants find each other: "Müller", "Mueller" and "Muller", "Zürich" and "Zurich", "Strasse" and "Straße". Capital letters and accents do not matter.
- ✅ Several words can be typed in any order: "muster hans" finds "Hans Peter Muster". Each word may be part of a word: "Archi" finds "Architektur", "56" finds BATbern56.
- ✅ Users are also found by an additional e-mail address; participants by their company; newsletter subscribers by their last name; events by the speakers' names and the talks' titles.
- ✅ In the company pickers the exact company is always at the top (it could be missing among many similar names). The company list shows real page numbers when searching.
- ✅ Characters like `(`, `%` or `'` no longer cause an error in the event search.

**Bug Fixes — Registration pages and waitlist order (Story 17.0c2)**:
- ✅ The page a participant sees after registering showed an empty name and e-mail under "Details". It now shows the name, the masked address the confirmation went to, and the name of the participant's company.
- ✅ The old confirmation page (`/registration-confirmation/<code>`) is gone, and with it the public lookup of a registration by its code (`GET /events/{eventCode}/registrations/{registrationCode}`). No page, link or mail pointed to it; registering and confirming run through `/registration-success` and the confirmation link in the mail (owner decision 2026-11-09).
- ✅ The organizer's waitlist view numbers and orders people by their place on the waitlist. The position was never sent to the page, so the list was ordered by name and numbered by row.

**Bug Fix — Requesting a revision of a talk without an abstract (Story 17.0b3)**:
- ✅ Asking a speaker to revise a talk whose content the organizers had entered themselves, with a title and no abstract yet, failed with an error and was not saved. It is now stored, with an empty abstract.

**Bug Fixes — Mail notices and name badges (#1235, #1236)**:
- ✅ (#1235) The Mail Center notice for a mail that belongs to no event showed raw tags in subject and text ("Neue Mail{{#eventCode}} für …"). It now reads "Neue Mail von …". Every automatic mail to one person now drops a section whose value is missing instead of printing its tags, and the log names the missing value.
- ✅ (#1236) Name badges (Word) and the participants list (XLSX) label partners "Partner" and list them after the organizers and speakers. A partner is everyone with the partner role who is registered for the event; someone with two roles gets one badge with the higher one (organizer, speaker, partner, participant).

**Bug Fixes — Registration on the public event page (#1238)**:
- ✅ After the registration deadline the public page no longer says "Plätze verfügbar" or offers to register or join the waitlist: the numbers stay, with "Anmeldung geschlossen". On BATbern60 the page still read "8 Plätze verfügbar" a day after the deadline.
- ✅ The registration numbers appear once, under "Logistik": total, vergeben, frei and the waitlist, with total = vergeben + frei. They are hidden before publication and after the event. The separate capacity badge is gone.
- ✅ Your own registration status appears once, in the header (it appeared up to three times). After the deadline you can still unregister there until the day before the event.
- ✅ When the event is full the button reads "Auf die Warteliste". A direct link to the registration page says "Anmeldung geschlossen" after the deadline.
- ✅ Unregistering is possible until the end of the day before the event, for every way of doing it; from the event day on it is refused. A cancellation after the registration deadline no longer moves anyone up from the waitlist (the name badges are printed by then).

**New — Combine an earlier profile without access to its address (#1237)**:
- ✅ Participants who cannot read the address of an earlier profile any more (for example a former employer's) can ask the organizers to combine it with their account, with a short note. The request appears on the Mail page; any organizer combines or declines it there, with a reason. The person is told by mail either way. The old address is not added to the account.

**Improvement — One "next dates" section on the homepage**:
- ✅ The homepage showed upcoming events twice: "Nächste BAT-Termine" (the next three, date and title) and "Weitere kommende Veranstaltungen" (full cards, published events only). There is now one section, "Nächste BAT-Termine", with the next three events: full cards for published events, date and title for planned ones. It also appears on archive pages.

**Bug Fixes — Theme image and event type (2026-11-07)**:
- ✅ "Generate with AI" for the theme image still failed after the model change: an image now takes about 40 seconds, and every request to the platform ends after 30 seconds, so the finished image never reached the page. The image is now generated in the background and the page waits for it (up to two and a half minutes).
- ✅ The event type (full day, afternoon, evening) can be changed again on the event page; saving it failed with an error since the API consolidation in October.

**Bug Fix — "Generate with AI" theme image works again**:
- ✅ The theme image could not be generated: OpenAI had retired the image model the platform used. All AI functions (theme image, event description, abstract review, trending topics) now use OpenAI's current model automatically, so a retired model no longer breaks them. The trending topics suggestions had also fallen back to a fixed list several times a week; they now read the answer correctly.

**New — Your e-mail addresses on your profile (#1244)**:
- ✅ Participants manage further e-mail addresses on their profile (**Profil → E-Mail-Adressen**): add, confirm by link, remove. A confirmed address helps us recognise you, for example after a job change, and lets you log in with Google if it is a Google address.
- ✅ Event mails (registration, reminders, invitations) go to your primary address. A further address gets a copy only if you tick "Kopie der Event-Mails" for it. Until now every further address got a copy, confirmed or not.
- ✅ The newsletter can be switched on and off for each confirmed address separately (**Profil → Einwilligung & Newsletter**).
- ✅ An earlier profile from the historical import (placeholder address `vorname.nachname@batbern.ch`) now offers only "Ask the organizers": the confirmation link would have reached nobody.
- ✅ A confirmed further address can become your primary address (**"Als Hauptadresse verwenden"**). You then log in with it and receive the event mails there; the old primary address stays as a confirmed address.
- ✅ Two accounts of one person, both with a login (for example first with the company address, later with a private one), can be combined by the person: add the other address on your profile, answer "Ist es Ihres?", and open the link we send to that address while you are logged in. The other account's registrations and events move to this one, its address stays as a confirmed address, and its login is deleted.

**New — Possible duplicates for organizers**:
- ✅ **Users → Mögliche Duplikate** lists profile pairs that probably belong to one person (same name, swapped first and last name, same address name at another domain), with how likely it is and which profile would stay. One click opens the merge preview with the pair chosen; "Kein Duplikat" hides namesakes for good. About 300 pairs were found on 2026-11-07.

**New — Request a new company logo (#1246)**:
- ✅ On your profile you now see your company's logo. **"Logo-Änderung beantragen"** lets you upload a new one (PNG, JPG or SVG, at most 5 MB) with an optional note. The request appears on the Mail page with the current and the new logo side by side; any organizer accepts it (the company gets the new logo everywhere) or declines it, optionally with a reason. You are told by mail either way.

### v1.4.0 - Event Page, Spring Boot 4, Mail Center & BATbern60 Fixes `[2026-11-04]`

**Type**: Minor Release (Epics 13, 14, 15, 16)

Covers everything that went live between v1.3.1 (2026-06-15) and 2026-11-04. The platform deploys continuously; each entry carries the date it went live, newest first.

**Bug Fix — Raising the Capacity Moves the Waitlist Up (2026-11-04)**:
- ✅ (#1208) Saving the registration capacity now gives every free place to the waitlist, in waitlist order, and each promoted person gets the usual "you are registered" mail. On 4 November BATbern60 went from 220 to 250 places and nobody of the 42 people waiting moved up: until now the waitlist only moved by one place when someone cancelled. Saving the same capacity again also fills places that are already free. A cancellation promotes only while there is a free place, so a participant added over the capacity by an organizer no longer pulls someone in. Removing the capacity promotes everyone waiting.

**Improvements and Bug Fixes — Presenter, Templates, Q&A (2026-11-04)**:
- ✅ The moderator presentation is made for the big screen: darker background, all text white, type and pictures about 1.5 to 2 times larger, the agenda and the speaker cards sized to their content. Session slides now sit beside the agenda sidebar instead of under it.
- ✅ Saving a mail as a template works with a normal name. "Save as template" in the mail composer and "New Template" in Administration accepted only a key in lowercase with hyphens: the composer kept Save disabled for "Partner Info", Administration sent it and showed only "could not be saved". The name is now turned into the key, which is shown before saving.
- ✅ Visitors who are not logged in see on an open session Q&A without questions "Anmelden, um eine Frage zu stellen oder mitzudiskutieren", with a link to the login.
- ✅ After login, the platform returns to the page that asked for it instead of always opening the dashboard. This also keeps the confirmation link for an earlier profile (#1183) working for someone who was not logged in yet.

**Bug Fix — Top menu fits on laptop screens (2026-11-03)**:
- ✅ On screens between 1200 and 1440 pixels wide, the organizer menu was wider than the window and the page scrolled sideways (bell and profile cut off). There the menu now shows icons only; hovering an icon shows its name. From 1440 pixels the names show as before.

**New — Two profiles of the same person become one (2026-11-03)**:
- ✅ Organizers: on a user's page, "Merge profile" folds another profile of the same person into this one: registrations, talks, history, roles and addresses move over, and the other profile is deleted. A preview shows both profiles and what moves before anything changes. Only a profile without a login can be merged in; the dialog offers to swap the direction.
- ✅ Participants: if an earlier profile exists under your name, for example from a registration without an account, your dashboard and your account settings show it with a masked address. "This is me" sends a confirmation link to that address; open it while logged in and confirm, and the earlier registrations and events appear in your account.

**Improvement — Registrations under your other addresses (2026-11-03)**:
- ✅ Your dashboard and an event's registration status now also show registrations made under another of your addresses, for example a guest registration with your work address, once that address is confirmed in your profile (Profile → additional e-mail addresses).
- ✅ "Cancel registration" by e-mail also finds the registration when it was made under another of your addresses or profiles. The link goes to the address you typed in.

**Improvement — Easier to read conversations (2026-11-03)**:
- ✅ A conversation shows the newest mail first and open. Older mails are one line each and open with a click, or all at once with "Show all".
- ✅ "+ New mail" moved to the top right of the communication tab, outside "Needs an answer". It writes a new mail and is not related to the open conversations.
- ✅ Answers from the Mail Center now come from `info@batbern.ch` instead of `noreply@batbern.ch`. Replies still land in the same conversation.

**Improvement — Mail Center switch in the settings (2026-11-03)**:
- ✅ Administration → Settings now has "Mail Center: notice instead of forwarding". Per group (speaker mail, replies, general mail) you choose whether the mail is still forwarded to the inbox or whether a short notice points to the Mail Center, where it is answered.

**Bug Fix — Amazon notice no longer forwarded (2026-11-03)**:
- ✅ When the mail server's rules change, Amazon writes a short setup notice. It went to every organizer's inbox and into the Mail page. It is now ignored.

**New Feature — Mail Page Across Events, Spam Filter, Mail Tab per Person (2026-11-02)**:
- ✅ **Mail** in the top navigation, after Events: everything that needs an answer across all events, the mail to `info@`, `events@` and `support@` that belongs to no event (put it on an event with "Assign to event"), the spam, and what went out across all events. The number on the entry counts the open conversations.
- ✅ The mail server now scans incoming mail. Mail with a virus is neither kept nor forwarded. Spam is no longer forwarded to anyone's inbox; it waits 30 days in the Spam list, where "Not spam" moves it to where it belongs.
- ✅ A **Mail** tab on a user's page and on a partner's page lists every mail sent to them and every mail they sent, across events and all their addresses, with each mail's delivery status.

**New Feature — Speaker Replies in the Mail Page (2026-11-02)**:
- ✅ The event's mail page has a new first section, **Needs an answer**: what speakers wrote to the event's moderator address, grouped per speaker, with a status (open, waiting, done) and the organizer who answers. The lead organizer is assigned at first; "I'll take it" makes it yours. Forwarding to the moderator's inbox is unchanged.
- ✅ **Handled automatically** now also counts out-of-office replies, bounce mails, calendar acceptances and declines, keyword replies that acted, and unsubscribes, without keeping their text.
- ✅ A mail an organizer sends to `batbern{N}-speaker@` now appears under "What went out".
- ✅ Replies to registration mails and newsletters, and mail to `info@`, `events@` and `support@`, become conversations like the speakers' mails: on the event they belong to, or kept for the coming Mail page when they belong to none. An organizer's mail to `batbern{N}-participants@` or `partner@` now appears under "What went out".
- ✅ Attachments of incoming mail up to 25 MB are kept with the conversation and open with a click; they are deleted together with the conversation.
- ✅ Organizers answer a conversation directly from the mail page. The answer goes out as "{name} via BATbern", the speaker's reply comes back into the same conversation, and every organizer sees who answered what. Handing a conversation to another organizer sends them one short mail with a link.

**Bug Fixes — Clearer Registration Status on the Event Page (2026-11-02)**:
- ✅ A logged-in visitor who is not registered no longer sees "Anmeldung stornieren". The page says instead under which address they are not registered, with a link for a registration made with another address. On 2 November a participant asked four times for a cancellation link and waited for it, although there was no registration to cancel. Registered visitors cancel directly in the event header, as before. Visitors who are not logged in still get the cancellation link by e-mail; the dialog now asks for the address they registered with and says that no mail means no registration under that address (#1183).

**Bug Fixes — No Automatic Mail to All Registrants (2026-11-02)**:
- ✅ The platform no longer sends mail to all registrants of an event on its own. On 2 November an automatic "Registration closes in 2 days" reminder went to all 267 BATbern60 registrations, including people who had cancelled or were on the waitlist. It would have repeated on the next two days. The reminder is removed, and so is the automatic "Event is now published" mail, which went to the same list when the agenda was published. A mail to many people now only goes out when an organizer sends it from the event's Mail page. Automatic mails still go to one person about their own case: confirmation links, waitlist moves, speaker deadlines and organizer tasks (#1179).

**New Feature — Own Text and Partner Mails (2026-11-02)**:
- ✅ Every audience in "+ New mail" can now get a text you write yourself: subject and text with placeholders inserted by click (name, talk title, deadline …), a preview that marks each filled value and refuses unknown placeholders, a template as starting point, and saving the text as a new template. Speakers are chosen by status. Registrants get a mail with the same subject only once unless you confirm a resend.
- ✅ Partners: a plain mail to all partners from a template of the new category "Partner", and partner meeting invites and cancellations now appear in "What went out", also under "Mails of this meeting" in Partner meetings.
- ✅ Partner meeting invites, cancellations and partner mails go to each recipient on their own. Before, every partner saw the addresses of all others in To: and in the calendar entry.

**Improvements — One Way to Render Every Mail (2026-11-02)**:
- ✅ Every BATbern mail is now built the same way, from one shared list of variables. A template can use any variable its mail has data for, and the old names (`venue`, `eventDetailLink`, `deregistrationLink`) keep working. The variable list is in [Email Templates](../administration/email-templates.md).
- ✅ The event date reads the same in every mail: "Freitag, 5. März 2027" or "Friday, 5 March 2027". Registration, speaker, venue and other mails used to show "05.03.2027".
- ✅ A newsletter send and its retry use the template as it was when the send started. Editing the template during a send no longer changes the mail for the remaining subscribers.
- ✅ Deregistration and waitlist mails show the BATbern logo and the current year again; the unsubscribe confirmation has the BATbern layout.
- ✅ Group mails no longer write the recipients' addresses into the server log.

**Bug Fixes — Partner Meeting Invites (2026-11-01)**:
- ✅ Partner meeting invites and cancellations are sent again. Since 24 October every one of them failed silently: the mail goes to all partners and organizers in one To: line, and the address check introduced with #1104 read that whole line as one invalid address. The organizer saw no error. An address that cannot be delivered is now left out with a warning, and the others still get the mail.
- ✅ The meeting shows "invite sent" only when the invite really went out. Before, it was marked as sent as soon as the button was pressed, even when sending then failed (BATbern60, 30 October). A failed send now shows an error to the organizer and leaves the meeting unmarked, so it can simply be sent again.

**New Feature — One Composer for Every Audience (2026-11-01)**:
- ✅ "+ New mail" on the event's Communications tab opens one composer for newsletter, registrants, speakers and venue: template, language, the recipients with who is left out and why, a preview per recipient, a test send to the organizers and the real send with a confirmation (#1174, Stories 16.8a + 16.8b).
- ✅ "What went out" marks a mail that is still sending, offers "Retry failed" for a newsletter that did not reach everyone, and hides test sends unless "Show test sends" is on.

**New Feature — Mail Page on the Event (2026-10-31)**:
- ✅ The event's Communications tab becomes the mail page (#1166, Epic 16). "What went out" lists every mail the platform sent for the event, with filters per audience and delivered / failed counts; automatic mails are grouped per template and day. "Handled automatically" counts what the platform dealt with on its own.
- ✅ Every outgoing mail is recorded, with one-click links, codes and passwords removed, and kept 12 months after the event.
- ✅ Delivery status per recipient: bounces, complaints and rejections mark a recipient as failed, deliveries as delivered. A bounce of a single mail never unsubscribes anyone from the newsletter.
- ✅ Deleting an event with newsletter sends no longer fails with a server error (#1158).

**Bug Fixes — Replies and Calendar Declines (2026-10-30)**:
- ✅ Replies to platform mails that are not a keyword are forwarded to the organizers instead of being discarded (#1164, Story 16.0).
- ✅ A keyword reply ("cancel") or a declined calendar invite no longer cancels a registration at once: the attendee gets the deregistration link and the place is released only when they click it (#1165).

**Security — Public Pages Show Only Public Data (2026-10-28)**:
- ✅ Visitors who are not logged in no longer see organizer planning data: the topic list on the archive page shows only title, category and how often a topic ran; event pages no longer show which organizer runs or last edited an event.
- ✅ The About page no longer exposes the organizers' email addresses; contact stays info@batbern.ch. The partner list no longer shows when a partnership ends; "partner since" stays.
- ✅ The registration confirmation page shows the email address masked (n***@example.com). "Create account" no longer fills in the address; you type it once.
- ✅ After every deploy, an automated check calls every non-public API operation without login and fails the deploy if one answers. The weekly security scan now covers all 19 API areas instead of 13.

**Bug Fixes — Newsletter Sending, Paged Lists and Request Origin (2026-10-28)**:
- ✅ A newsletter send no longer skips subscribers when others unsubscribe while it runs. For BATbern60, 13 of 1060 were missed this way. "Retry" no longer mails addresses that were deleted or unsubscribed in the meantime.
- ✅ Paged lists show every entry exactly once. The newsletter subscriber list showed some subscribers twice and hid about 337 of them; the same fix applies to every paged list.
- ✅ The platform no longer trusts an IP address that a caller can set in a request header. This affected an internal access rule and the protection against too many requests.
- ✅ Automated tests now check the access rules exactly as they run in production, and the API tests for distribution lists and participant exports run on every deploy.

**Bug Fixes — Missing Translations (2026-10-28)**:
- ✅ French, Italian, Romansh, Spanish, Finnish, Dutch, Japanese and Bernese German no longer show "[MISSING]" texts. This covers the registration status on the event page, cancelling a registration, the waitlist step of the registration form, the status chips on event cards, and the organizer's waitlist table.
- ✅ A test now fails the build if a "[MISSING]" placeholder is added to any language file again.

**Bug Fixes — Waitlist Mail and Capacity Badge (2026-10-28)**:
- ✅ Logged-in users who register for a full event now get the waitlist mail, like everyone who registers through the form. Before, they only saw the message on the page.
- ✅ On the homepage, a visitor who is already registered for a full event sees "You're registered!" in the event details, as in the hero card, instead of "Full — join waitlist". Someone on the waitlist sees "You're on the waitlist".
- ✅ The capacity badge ("spots remaining", "fully booked", "on waitlist") is translated into all ten languages. Eight of them showed an English placeholder.

**Security — User Directory for Organizers Only (2026-10-28)**:
- ✅ The user list, the user search and other people's user records can only be read by organizers. Everyone reads their own profile under "My Profile" as before.
- ✅ On their company page, speakers see the company's details and sessions. The lists of colleagues and speakers of the company are shown to organizers only.
- ✅ Creating, changing, publishing and deleting events, sessions and speaker-pool entries, topic selection and the notification centre are reserved for organizers.
- ✅ Unknown API addresses now answer "not found" instead of a server error.

**Bug Fixes — Account Confirmation and Registration Changes (2026-10-27)**:
- ✅ "Resend confirmation code" now actually sends the mail. Before, it showed success but nothing was sent, so people who had not confirmed their new account right away could neither confirm it nor reset the password (#1141).
- ✅ Login, "Forgot password" and "Create account" recognise an account that is not confirmed yet and offer to send a new confirmation link, instead of "unknown error", "no account for this e-mail" or "account already exists".
- ✅ Changing the status of a registration or deleting it is reserved for organizers. Attendees keep cancelling their own registration through the link in their confirmation mail.

**Bug Fixes — Email Aliases Work Again (2026-10-27)**:
- ✅ Mail to `ok@`, `info@`, `events@`, `partner@`, `support@` and the per-event aliases `batbern{N}-speaker@`, `batbern{N}-moderator@`, `batbern{N}-participants@` is forwarded again. The organizer-only aliases had refused every sender, organizers included (#1130).
- ✅ The organizer-only aliases now also check that the sender's mail provider vouches for the address (DMARC, or a DKIM signature of the sender's domain). A forged organizer address can no longer mail all speakers or participants. Organizers sending from a mailbox that does neither get a rejection and should send from an address whose provider does.
- ✅ Each mail is forwarded at most once, even if delivery is retried.
- ✅ Out-of-office replies and replies to `noreply@` are dropped quietly instead of raising the abuse alarm.

**Bug Fixes — Apéro Session Type and Material Status (2026-10-26)**:
- ✅ The apéro is stored as one session type again (`networking`, still shown as "Apéro"). A second value, `aperitif`, had been added in June for the same slot; the Apple Watch app 2.3 does not know it and could not load the event at all (#1123).
- ✅ Speakers & Sessions table: a session shows its material as complete as soon as one file is uploaded, whatever its type. The "Pending" state for a session with only a PDF and no slide deck is gone (#1123).

**Security — Organizer-only Participant Data (2026-10-26)**:
- ✅ Registration lists, the per-event mailing-list addresses and admin settings can only be read by organizers.
- ⚠️ Until the email forwarding moves into the backend (follow-up release), mail to `support@` and `batbern{N}-moderator@` is not forwarded. Incoming mail is kept for 7 days and re-delivered once the follow-up ships.

**New Feature — Next BAT Dates on the Homepage (2026-10-25)**:
- ✅ Below the venue map, the homepage shows the next three BAT dates after the featured event: one card each with date and title (#1110). The list includes events that are planned but not yet published.

**Bug Fixes — BATbern60 Website Batch (2026-10-25)**:
- ✅ Presenter view: the agenda slide and the session slides are filled again. The presenter read the event through the organizer endpoint, which since 7 October returns no sessions to a visitor who is not logged in. It now uses the public event, so it shows exactly what the public agenda shows (#1112).
- ✅ Presenter agenda: each break row carries its own title, as on the public agenda. Before, the coffee break and the apéro both read "Pause" (#1117).
- ✅ Newsletter: "Weitere BAT-Termine" lists only the next three events, nearest first, instead of every planned event in random order (#1111).
- ✅ Past events: session cards no longer show when a speaker presented (#1114), and a long material file name wraps inside the card instead of making it wider than a phone screen (#1113).
- ✅ Partner row at the bottom of the website: all logos load right away. Before, each logo only started loading once it scrolled into view, so the row filled in over tens of seconds (#1115).

**Bug Fixes — Logged-in Registration Sends the Confirmation and Calendar Invite (2026-10-25)**:
- ✅ Attendees who register with one click while logged in now receive the "Anmeldung bestätigt / Registration confirmed" mail with the calendar invite, like everyone else. Before, they got no mail and no calendar entry (#1097). A waitlisted registration still gets no mail.

**Bug Fixes — Notifications: No Test Noise, and Who Did It (2026-10-25)**:
- ✅ Notifications from automated test runs no longer pile up in organizers' lists (#1068). Deleting an event now deletes its notifications, and the nightly test run removes notifications of all test events, including the backlog from earlier runs (6,365 for the test organizer alone).
- ✅ Every notification names who made the change, for example "nissim.buchs moved BATbern60 to Agenda Published" or "nissim.buchs moved a speaker of BATbern60 from Contacted to Ready". Slot changes showed the placeholder "organizer" instead of the real person; the slot audit history now records the real person too.

**Cleanup — Company Swiss UID Removed, Stale Placeholders Gone (2026-10-25)**:
- 🗑️ Companies no longer have a Swiss UID field (#1119). It was never used (0 of 691 companies had one), so the field, its format check and the `GET /companies/validate-uid` endpoint are gone, and the database column is dropped. Company verification by an organizer is unchanged.
- 🗑️ The company `?include=statistics` expansion is gone: it always returned zeros and no screen showed it.
- 🧹 Code cleanup with no behaviour change: placeholder code that was never called, outdated TODO notes, and tests that had been switched off. Tests that still made sense were repaired and switched back on.

**Bug Fixes — Photo Row Back on the Homepage (2026-10-24)**:
- ✅ The homepage shows the photo row from recent events again. It took its photos from the five events with the latest date, and once BATbern61–66 had been created ahead of time those were all future events without photos, so the row disappeared. Only events that have already taken place count now.

**Bug Fixes — Speaker Cards Show the Assigned Slot (2026-10-24)**:
- ✅ In the speaker pool, a confirmed speaker who already has a slot now shows the slot time instead of "⚠ Needs a slot". The card looked the session up by an identifier the agenda data does not carry, so every confirmed speaker appeared unslotted, even with the agenda published (BATbern60: 3 speakers).

**Bug Fixes — Email Addresses Are Matched Regardless of Capitalisation (2026-10-24)**:
- ✅ `Jane.Doe@x.ch` and `jane.doe@x.ch` are now always the same person. Previously a different capitalisation created a second user (5 people were affected and have been merged), a second newsletter subscription (one person received every newsletter twice), or a second partner-meeting RSVP.
- ✅ Unsubscribing, deregistering or confirming by email reply, and SES bounce/complaint handling, now work even when the address is capitalised differently. Before, they silently did nothing.
- ✅ New addresses are stored lower-case; the database now rejects case-duplicates for users and newsletter subscribers.

**Bug Fixes — Organizer Tasks Appear on Time (2026-10-24)**:
- ✅ Tasks now become active when the event reaches their trigger, however the event got there. Until now the automatic agenda publication, changing the status in the event settings, and finishing the last session on the Watch skipped this step, so tasks such as Catering and the Final Agenda newsletter stayed "pending" (BATbern60, 23 October).
- ✅ A task also becomes active a set number of days before it is due (14 by default), even if its trigger has not happened yet, so no task can be born overdue. A daily check at 05:30 applies both rules.
- ✅ The Speaker Lineup newsletter task now starts when the speakers are published, not when the agenda is.
- ✅ Newsletter tasks that a later announcement has replaced are closed automatically: Topic Announcement once the speakers or the agenda are public, Speaker Lineup once the agenda is public.
- ✅ Catering is now due **5 days** before the event (final headcount), and two new standard tasks exist: **Namensschilder** (2 days before) and **Wein** (event day). For an existing event, add them from the event's task templates.

**New Feature — Cockpit Shows the Right Work at the Right Time (2026-10-24)**:
- ✅ The attention cards on the event Cockpit hide completed, cancelled and not-yet-due tasks. A pending task that is already overdue shows "Trigger not reached yet" (#1105).
- ✅ The event-day cards ("Start the presentation", "Open Live Control") show only on the event day, with rehearsal links before that.
- ✅ The registrations card shows only from 90 % capacity or when someone is on the waitlist. The presentations card reads "n of m" and is due on the event day.
- ✅ A new card lists speakers whose content is not yet quality-reviewed, once the speakers are public. The name-badge task links to the badge export.
- ✅ The "my open tasks" widget lists all of my open tasks by the same rules. The task-side changes are listed under "Organizer Tasks Appear on Time".

**Bug Fixes — Any Name and Every Deliverable Email Address Can Register (2026-10-23)**:
- ✅ Names with a space in the first or last name (`Hông Ân`, `van der Berg`, `Maria José`) or in a non-Latin script no longer fail registration with "Server Error". One attendee had to misspell their own name to get through on 2026-10-22 (#1102).
- ✅ Account sign-up accepts every name: typographic apostrophes (`O’Neill`, as typed on iPhone), single-character surnames (`王`), Devanagari, decomposed Vietnamese, digits and parentheses.
- ✅ Email addresses are accepted whenever our mail service can deliver to them, including internationalised domains (`name@müller.ch`), long domain endings and the full set of RFC characters before the `@`. An address with accented characters **before** the `@` (`jürg@…`) is refused with an explanation, because no confirmation mail could ever reach it.
- ✅ Usernames stay readable ASCII and are now derived the same way on every path (`Jürg Müller → juerg.mueller`, previously sometimes `jrg.mller`).

**Bug Fixes — Registration with an Additional Email Address (2026-10-18)**:
- ✅ Registering for an event with an address that is stored as another account's **additional email** no longer fails with "Server Error". A verified address registers the owning account; an unverified one shows "this address is linked to an existing account, please sign in" (all 10 languages).

**Bug Fixes — Registration Confirmation Mails (2026-10-18)**:
- ✅ The "please confirm your registration" email no longer attaches the calendar invite, and its subject now reads **"Bitte bestätigen: Ihre Anmeldung …"** / **"Please confirm your registration …"** instead of "Registrierungsbestätigung". At the last event about 30 attendees took the attached invite (with its "Accept" button) for the confirmation, never clicked the link, and lost their registration.
- ✅ New **"Anmeldung bestätigt" / "Registration confirmed"** email, sent as soon as the attendee confirms (link or reply). It carries the calendar invite and the cancel link.
- ✅ Automatic confirmation reminders are unchanged (same schedule); they send the updated "please confirm" email.

**Bug Fixes — Organizer Partner Settings (2026-08-12)**:
- ✅ The **Active partnership** toggle (Partners → open a partner → Settings) now works. It previously appeared to switch but saved nothing — the control was rendered without a handler, so every flip was silently discarded. Deactivating ends the partnership as of today; re-activating restores it to open-ended.
- ⚠️ The **Auto-renewal** switch and the "renewal date" line have been **removed** from that tab. Neither field has ever existed in the partner API, so the switch could never save and the date was always blank — it only looked like a setting. Nothing was lost, because nothing was ever stored. If automatic renewal is wanted as a real feature, it needs an API and a database column first.

**New Feature — Stable Slot Assignment: Insert, Swap, Mobile Tap (2026-06-21)**:
- ✅ Dropping a pool speaker onto an occupied slot inserts the talk and moves the later ones down, with their times recalculated; dropping a scheduled talk onto another swaps the two. A live preview shows the result while dragging (#806, Story 15.3).
- ✅ ✕ "Remove from slot" sends a session back to the speaker pool.
- ✅ On phones and tablets: tap a session, then tap a highlighted slot.
- ✅ An insert that would overflow a full agenda is refused instead of losing a talk.

**New Feature — Per-Event Agenda Shape (2026-06-21)**:
- ✅ "Edit event type" on the slot assignment page changes slot count and duration, breaks, lunch and apéro for one event only, with a live schedule preview (#805, Story 15.2). The first save creates a per-event copy; events without a copy keep following the event-type template in Administration.
- ✅ The apéro can be switched on as a structural slot at the end of the agenda.

**Platform — Spring Boot 4 (2026-06-19)**:
- ✅ All Java services run on Spring Boot 4 (#797, Epic 13). No visible change for organizers. Close-out (Jackson 3 switch-over, dependency-pin cleanup) is listed under Upcoming.

**New Feature — Organizer "Add participant" (2026-06-16)**:
- ✅ Organizers can manually add an existing user to an event **directly as a confirmed participant** from the Registrations tab (4th action button) — for VIPs, late phone/email requests, or colleagues — skipping the self-registration + email-confirmation step.
- ✅ Existing-users-only picker (the shared `UserAutocomplete`); capacity respected with an explicit "add anyway" override; counts as a real attendee.
- ✅ The added participant receives a dedicated **"you're registered — your place is confirmed"** email (event details + calendar invite + self-service cancel link) — not a "please confirm" or waitlist message.

**Improvements — Organizer Communications (Epic 14, 2026-06-15)**:
- ✅ Two "deregistration call" registrant-notice templates (DE + EN) — ask registrants of a fully-booked event to free their seat; selectable in Communications → Event registrants:
  - `registrant-notice-deregistration-call` — one-click cancel via a per-recipient `{{deregistrationUrl}}` (requires the send-side injection below; available once the backend deploys)
  - `registrant-notice-deregistration-call-selfservice` — points to the already-live self-service ("Cancel my registration" on the event page → enter email → confirm link); works with no deploy
- ✅ Registrant-notice sends now inject a per-recipient one-click `{{deregistrationUrl}}` (self-service cancel link, no login) — "Way 1"
- ✅ New `batbern{N}-participants@batbern.ch` mailing alias fans out to every active registrant — "Way 2" (same audience as Way 1)
- ✅ Consolidated the older bare `batbern{N}@batbern.ch` alias: now **deprecated** and forwards to the same participants resolution as `batbern{N}-participants@` (clearer name, matches `-speaker@`/`-moderator@`); logs a deprecation warning, sender auth unchanged (organizers only)

**New Feature — Lifecycle-Aware Event Page (Epic 14, 2026-06-15)**:
- ✅ The event page has new tabs that follow the event's lifecycle: **Cockpit** (lifecycle spine, attention cards, metric tiles, event-day controls), **Speakers & Agenda** (pool, agenda, slots, 4-phase kanban, slot assignment in the tab), **Registrations** (status filter including the waitlist, counter), **Communications**, **Publishing**, **Wrap-up** (photos and thank-you notes) and **Details** (Info / Tasks / Settings) (#788).
- ✅ Full mobile navigation: four main tabs in a bottom bar, the others under "More".

---

## v1.3.x - Previous Releases

### v1.3.1 - Q&A, Email Forwarding & Registrant-Notice Fixes `[2026-06-15]`

**Type**: Patch Release

**Improvements**:
- ✅ Speaker email alias (`batbernNN-speaker@`) now resolves to the primary speaker **and** co-speakers, and is delivered as a single visible message — all speakers in `To:`, the event organizer in `Cc:` — so the recipient list is verifiable at a glance. Forwarded mail now has dedicated SES delivery/bounce tracking, separate from newsletter list hygiene.
- ✅ Early Epic 7 attendee touchpoints shipped incrementally: the attendee dashboard greets by profile first name; per-session Q&A is visible pre-event (from when speakers are published); and Thank-the-Organizers moved to a prominent navigation button.
- ✅ Curated thank-you marquee (Story 7.7, #785, 2026-06-13): organizers feature selected thank-you notes, which then appear in the public homepage marquee. Anonymous notes cannot be featured. This completes Epic 7.

**Bug Fixes**:
- ✅ Removed Q&A posts are now dropped entirely from the thread instead of shown as tombstones; removing a question also drops its answers (posts stay soft-deleted in the database for audit).
- ✅ The post-event "slides are online" email now reaches registrants who **attended** the event — audience is `registered` + `confirmed` + `attended` (`waitlist`/`cancelled` excluded) — matching the mail's post-event timing.
- ✅ Company-logo lookup reads the denormalised `logo_url` column (fixes missing logos); company logos render with CDN `fit=inside` to avoid black bars.

### v1.3.0 - Google SSO, Unified Speaker Workflow & Organizer/Admin Cluster `[2026-06-13]`

**Type**: Minor Release (Epics 10, 11, 12 + Epic 7)

> **Note**: The former "v1.3.0 — Speaker Authentication Unification (Epic 9)" plan has shipped in a
> different form. Epic 9's JWT magic-link approach was **superseded** by Epic 11 (unified Cognito
> speaker workflow) and Epic 12 (Google SSO), both released in v1.3.0 below.

**New Features — Authentication (Epic 12)**:
- ✅ "Continue with Google" single sign-on — Google OIDC federation at `auth.batbern.ch`
- ✅ Transparent account linking (links a Google identity to an existing account by email)
- ✅ Just-in-time (JIT) user provisioning on first SSO login
- ✅ Terms-of-Service consent gate + federated onboarding completion (consent / company / newsletter)
- ✅ Google profile-avatar import; runtime kill-switch (`FEATURES_SSO_ENABLED`)

**New Features — Unified Speaker Workflow (Epic 11)**:
- ✅ Speaker workflow reduced to an **8-state model**; `SpeakerWorkflowService` is the sole status writer
- ✅ Workflow unified into the event-management service (standalone `speakers` table + speaker-coordination refs dropped)
- ✅ Speaker portal migrated to **AWS Cognito** authentication — the original magic-link login was fully torn down
- ✅ Cognito user provisioning at the `READY` transition; kanban drawer redesign + guided drag; speaker company self-service

**New Features — Admin Tools, Newsletter & Public Site (Epic 10)**:
- ✅ Administration page (`/organizer/admin`) — Event Types, Import Data, Task Templates, Email Template management (TinyMCE for content, Monaco for layout); task deadline reminder emails; blob/heat-map topic selector
- ✅ Newsletter — subscription, sending, template selection, subscriber management; email-reply unsubscribe/deregistration; SES forwarding/distribution lists; SES bounce processing + list hygiene
- ✅ Registration lifecycle — registration status indicator; venue capacity enforcement + **waitlist** management; self-service deregistration
- ✅ Public site — event photos gallery; event teaser image; event description on the homepage; **moderator presentation page** (+ animations); organizer analytics dashboard
- ✅ **Turnstile** bot protection on public submit flows; additional user emails ("receive at" + "send-as" aliases); legacy BAT-format data export/import; AI-assisted event content creation

**New Features — Attendee Contribution (Epic 7, initial)**:
- ✅ Topics From the Floor — logged-in attendees suggest future topics (tagged `source = community`); organizers triage in the topic-suggestion admin UI
- ✅ "I Could Speak on That" — logged-in attendees self-nominate as speakers (session title + abstract); creates a `speaker_pool` entry at `IDENTIFIED` for organizer triage (no auto-provisioning)

**New Features — Attendee Contribution (Epic 7, rest of the epic, #776, 2026-06-12)**:
- ✅ "The Slides Are Online" mail — auto-created organizer task after the event; one click mails the active registrants (DE + EN)
- ✅ Thank-the-Organizers — one-click post-event thank-you with optional note and a public appreciation counter
- ✅ The Apéro Continues — time-boxed per-session Q&A that freezes onto the session archive page; settings per event
- ✅ Attendee Event History — logged-in dashboard of the events the attendee registered for or attended

**Also in this release (April – June 2026)**:
- ✅ One-click registration for logged-in attendees (2026-05-06): register and unregister in a panel in the homepage event hero, without the registration wizard. (The confirmation mail with calendar invite for this path followed on 2026-10-25, see v1.4.0.)
- ✅ Unconfirmed attendees can recover (#683, 2026-05-28): registration confirmation links are valid for 4 days instead of 48 hours, and a daily job resends the confirmation (fresh link, at most twice) to registrations and new accounts that are still unconfirmed
- ✅ Speakers are registered as participants automatically when they commit to an event (#686, 2026-05-29)
- ✅ Per-event mail aliases `batbern{N}-speaker@` and `batbern{N}-moderator@` (#686, 2026-05-29)
- ✅ Name badges: XLSX export (first name, name, company, role) (#686) and printable Word badges for Avery L4784 sheets with the BAT logo (#688), both 2026-05-29
- ✅ Venue coordination (#690, 2026-05-29): Administration → Venue & Catering holds the venue, catering and coordinator contacts; the event sends timetable and catering-offer mails from templates with a preview. The speaker drawer's history also shows rejected abstracts
- ✅ Additional email addresses are verified by mail (Verified / Unverified, Resend), and "Continue with Google" with a verified additional address signs in to the existing account instead of creating a second one (#745, 2026-06-07)

**Improvements**:
- ✅ Company picker rework — the company field on the profile and registration forms is now a
  selection-locked combobox: it shows each company's **display name** (not the internal slug),
  locks the chosen company into a removable chip (no more "Firmen-ID muss aus 1-12 Buchstaben…"
  format error from editing the slug), and offers an explicit **"Create new company"** action
  instead of silently creating duplicates. Company search now matches both the internal name and
  the display name, so existing companies surface even when you type their spaced/cased name.
- ✅ Event deletion now ignores programmatic registrations. Every new event auto-enrols all
  organizers and partners as participants, which previously left the **Delete Event** button
  permanently disabled. Deletion is now blocked only when an event has *real* (self-registered)
  attendees; events with only auto-enrolled stakeholders can be deleted. The API returns `409`
  if real attendees exist, and the event detail exposes a `realAttendeeCount` field.
- ✅ Post-event 14-day homepage window: the public homepage shows the most recent event for 14 days
  after it ends (archive-style view — timetable and speakers visible, no registration/logistics).
  After 14 days a nightly scheduler auto-archives the event.
- ✅ Legacy structural entries in historical events — the "Moderation" slots (28 rows, BATbern11–48)
  and the "Programmheft" entries (41 rows, BATbern1–41) — are reclassified from content talks to
  technical sessions (`session_type = moderation`), matching how moderation slots are modelled in
  new events (BATbern57+). Applied directly to the production database (no migration).

**Breaking Changes**:
- Speaker magic-link login URLs are **retired**. Speakers now log in via AWS Cognito
  (email/password or "Continue with Google"). Existing speakers were migrated; old magic-link
  emails no longer authenticate.

---

## v1.2.x - Previous Releases

### v1.2.2 - Email & Public Event Page Fixes `[2026-04-07]`

**Type**: Patch Release

**Bug Fixes**:
- ✅ Registration confirmation emails and ICS calendar attachments now use the correct, resolved event start and end times (previously used placeholder/incorrect values)
- ✅ Email forwarding Lambda now correctly forwards CC addresses as mailing-list recipients, and recognises all `batbernNN@batbern.ch` address variants
- ✅ Public event page now shows resolved start **and** end times (not just the event date)
- ✅ Newsletter subscriber list: registered-user icon corrected; subscriber display names improved for readability
- ✅ TypeScript 7 compatibility: deprecated `baseUrl`-only `tsconfig.json` options resolved; removed stale lucide icon imports

---

### v1.2.1 - Speaker JWT Magic Link Authentication `[2026-02-23]`

**Type**: Patch Release (Epic 9, Story 9.1)

**New Features**:
- ✅ JWT-based magic link authentication for speakers — RS256 signed tokens, HTTP-only cookie, 30-day reusable sessions
- ✅ Speaker portal auto-login on link click (no username/password required)
- ✅ Organizer-controlled invitation emails trigger JWT generation per speaker/event
- ✅ Backward compatible with Epic 6 staging token system

**Architecture**:
- JWT signed with private RSA key (RS256); public key exposed at `/api/v1/speaker-auth/jwks.json`
- Token audience: `batbern-speaker-portal`; issued by `batbern-speaker-coordination`
- Magic links reusable within 30-day session window; new token issued on next invite

---

### v1.2.0 - Partner Coordination Complete `[2026-02-22]`

**Type**: Minor Release (Epic 8)

**New Features**:
- ✅ Partner Attendance Analytics Dashboard — per-company attendance table (last 5 years / full history toggle), cost-per-attendee KPI, XLSX export via Apache POI
- ✅ Topic Suggestions & Voting — partners suggest topics + toggle-vote (one per company); organizers set status (Selected/Declined) with optional "planned for event" note; sorted by votes descending
- ✅ Partner Meeting Coordination — meeting record linked to BATbern event by event code; free-text agenda; RFC 5545 ICS calendar invite sent async via AWS SES to all partner contacts; post-meeting notes

**Architecture**:
- Analytics data queried on demand from `event-management-service`; Caffeine-cached 15 min
- No materialized views, no nightly batch (max ~60 events ever)
- Standard ICS (no Microsoft Graph / Outlook dependency)
- Role-based access enforced at API level (PARTNER sees own company only; ORGANIZER sees all)

---

### v1.1.0 - Speaker Self-Service Portal Complete `[2026-02-16]`

**Type**: Minor Release (Epic 6)

**New Features**:
- ✅ Automated Speaker Invitations — magic link emails via AWS SES; bulk send; tracking (sent/responded)
- ✅ Self-Service Response Portal — Accept/Decline via unique link (no account required); auto-updates organizer speaker status; organizer manual override available
- ✅ Speaker Material Self-Submission — multi-step wizard for title, abstract (1000-char limit), CV, photo, presentation; drag-and-drop upload via S3 presigned URLs; draft auto-save
- ✅ Speaker Dashboard — view upcoming/past events, material submission status, event details; WCAG 2.1 AA accessible; i18n EN/DE; 30-day magic link session
- ✅ Automated Deadline Reminders — configurable 3-tier escalation (friendly → urgent → organizer escalation); deduplication (no reminder if materials already submitted); full reminder log

**Multi-Role E2E Test Auth**:
- ✅ `make setup-test-users` — authenticate all roles at once
- ✅ Per-role token storage: `~/.batbern/staging-{organizer,speaker,partner}.json`
- ✅ Playwright `speaker` and `partner` projects activated by env var

---

## v0.9.x - Prior Releases

### v0.9.0 - Workflow Phase F & Documentation `[2025-12-18]`

### v0.9.0 - Workflow Phase F & Documentation `[2025-12-18]`

**Type**: Minor Release

**New Features**:
- ✅ Workflow Step 14: Moderator Assignment
- ✅ Workflow Step 16: Partner Meeting Coordination
- ✅ Comprehensive User Guide (40 pages across 6 sections)
- ✅ Troubleshooting guides for authentication, uploads, workflow
- ✅ Glossary with 80+ terms
- ✅ Keyboard shortcuts reference (planned features documented)

**Improvements**:
- Enhanced workflow validation messages (more specific error details)
- Improved file upload progress indicators (show speed, time remaining)
- Partner directory performance optimized (50% faster load time)
- Drag-and-drop slot assignment UX refined (visual feedback improved)

**Bug Fixes**:
- Fixed: Speaker status not updating after content submission (Issue #142)
- Fixed: Company logo preview not showing after upload (Issue #145)
- Fixed: Workflow validation incorrectly blocking Step 8 advancement (Issue #148)
- Fixed: Search autocomplete showing archived companies (Issue #151)

**Known Issues**:
- Step 13 (Newsletter Creation): Template system incomplete (60% done)
- Step 15 (Catering Coordination): Dietary restrictions UI in progress (70% done)
- File upload: PPTX → PDF auto-conversion not yet implemented

---

### v0.8.0 - Workflow Phase E & Publishing `[2024-11-15]`

**Type**: Minor Release

**New Features**:
- ✅ Workflow Step 11: Progressive Topic Publishing
- ✅ Workflow Step 12: Speaker Publishing & Finalization
- ✅ Dropout handling workflow (speaker withdrawals after acceptance)
- ✅ Public event preview mode (attendee-facing view)

**Improvements**:
- Workflow dashboard now shows completion percentage per phase
- Event timeline visualization improved (Gantt chart style)
- Speaker profile templates added (faster bio creation)

**Bug Fixes**:
- Fixed: Published topics reverting to draft after edit (Issue #128)
- Fixed: Speaker photos not displaying in public view (Issue #131)
- Fixed: Timezone handling for event dates (now UTC-based) (Issue #134)

---

### v0.7.0 - Workflow Phase D & Assignment `[2024-10-01]`

**Type**: Minor Release

**New Features**:
- ✅ Workflow Step 9: Overflow Management with voting
- ✅ Workflow Step 10: Drag-and-drop slot assignment
- ✅ Conflict detection (speaker availability, double-booking)
- ✅ Time slot templates by event type

**Improvements**:
- Slot assignment grid redesigned (better UX for 10+ speakers)
- Speaker cards now show expertise tags for easier matching
- Conflict warnings display in real-time during drag operations

**Bug Fixes**:
- Fixed: Drag-and-drop not working in Firefox (Issue #118)
- Fixed: Slot times not respecting venue timezone (Issue #121)
- Fixed: Overflow voting counts incorrectly calculated (Issue #124)

---

### v0.6.0 - Workflow Phase C & Quality Review `[2024-08-20]`

**Type**: Minor Release

**New Features**:
- ✅ Workflow Step 7: Quality Review with Approve/Revise/Reject
- ✅ Workflow Step 8: Minimum Threshold Validation
- ✅ Review queue with prioritization (deadline-based)
- ✅ Content preview modal (view slides inline)

**Improvements**:
- Review comments now support rich text formatting
- Reviewers can attach files (feedback documents, marked-up slides)
- Email notifications for revision requests (manual trigger)

**Bug Fixes**:
- Fixed: Quality threshold not accounting for event type (Issue #105)
- Fixed: Review comments not saving correctly (Issue #108)
- Fixed: Content preview failing for large PDFs (Issue #111)

---

## v0.5.x - Phase B Releases

### v0.5.0 - Workflow Phase B & Outreach `[2024-07-10]`

**Type**: Minor Release

**New Features**:
- ✅ Workflow Step 4: Speaker outreach with email invitations
- ✅ Workflow Step 5: Status management (Accepted/Rejected/Pending tracking)
- ✅ Workflow Step 6: Content collection with upload interface
- ✅ Email template system (basic customization)

**Improvements**:
- Speaker invitation emails now include event details automatically
- Status dashboard shows response rate and time-to-response metrics
- File upload supports drag-and-drop (previously button-only)

**Bug Fixes**:
- Fixed: Invitation emails containing incorrect event date (Issue #92)
- Fixed: Content upload not validating file size correctly (Issue #95)
- Fixed: Status filters not persisting after page refresh (Issue #98)

---

## v0.4.x - Phase A & Core Features

### v0.4.0 - Workflow Phase A & Setup `[2024-05-25]`

**Type**: Minor Release

**New Features**:
- ✅ Workflow Step 1: Event setup with type/date/venue
- ✅ Workflow Step 2: Topic selection with heat map integration
- ✅ Workflow Step 3: Speaker brainstorming with minimum threshold
- ✅ Topic heat map visualization (20+ years historical data)

**Improvements**:
- Heat map now supports filtering by time range and category
- Speaker brainstorming UI redesigned (card-based layout)
- Event setup wizard added (guided 3-step process)

**Bug Fixes**:
- Fixed: Heat map colors not matching legend (Issue #78)
- Fixed: Topic backlog search case-sensitivity (Issue #81)
- Fixed: Event duplication creating corrupted records (Issue #84)

---

### v0.3.0 - Entity Management Complete `[2024-04-10]`

**Type**: Minor Release

**New Features**:
- ✅ Partner management CRUD (directory, contacts, meetings)
- ✅ Speaker management CRUD (profiles, status tracking)
- ✅ Company-speaker associations
- ✅ Partner tier badges (Platinum, Gold, Silver, Bronze)

**Improvements**:
- Partner directory now sortable by tier, engagement, recent activity
- Speaker search improved (fuzzy matching, partial name matching)
- Company logos now support SVG format (in addition to PNG/JPG)

**Bug Fixes**:
- Fixed: Partner contacts not associating with company correctly (Issue #65)
- Fixed: Speaker bio character limit not enforced (Issue #68)
- Fixed: Duplicate partner detection false positives (Issue #71)

---

### v0.2.0 - Core Entity Management `[2024-03-01]`

**Type**: Minor Release

**New Features**:
- ✅ Company management CRUD
- ✅ User management CRUD with role-based access
- ✅ Event management CRUD (3 event types)
- ✅ Swiss UID validation with check digit algorithm
- ✅ Company logo upload via presigned S3 URLs

**Improvements**:
- Search performance improved (added full-text indexes)
- Role promotion/demotion now requires confirmation dialog
- Event list now paginated (50 per page, previously all-at-once)

**Bug Fixes**:
- Fixed: User email validation too strict (blocked valid domains) (Issue #42)
- Fixed: Company logo upload hanging at 99% (Issue #45)
- Fixed: Event date picker not showing correct timezone (Issue #48)
- Fixed: GDPR data export missing user activity logs (Issue #51)

---

## v0.1.x - Foundation Releases

### v0.1.0 - Foundation & Authentication `[2024-01-15]`

**Type**: Initial Beta Release

**New Features**:
- ✅ AWS Cognito authentication (email/password)
- ✅ Password reset flow
- ✅ Session management (8-hour sessions, 2-hour idle timeout)
- ✅ Role-based access control (4 roles)
- ✅ PostgreSQL database (RDS Single-AZ)
- ✅ S3 file storage with CloudFront CDN
- ✅ AWS ECS Fargate deployment
- ✅ CloudWatch monitoring and logging

**Initial Limitations**:
- Only ADMIN role can create users (no self-registration)
- Single-language only (English)
- No mobile optimization yet
- Limited analytics (basic usage metrics only)

**Known Issues**:
- Session timeout not showing warning before logout (Issue #12)
- File upload progress bar jumps from 0% to 100% instantly (Issue #15)
- Some error messages too technical (not user-friendly) (Issue #18)

---

## Breaking Changes History

### v0.9.0 Breaking Changes

**None** - All changes backward compatible

### v0.8.0 Breaking Changes

**Timezone Handling** (Issue #134):
- **What Changed**: Event dates now stored in UTC, displayed in user's timezone
- **Impact**: Existing event dates may appear shifted if created in different timezone
- **Action Required**: Review all future events, adjust dates if needed
- **Migration**: Auto-migration applied on upgrade (no manual action for most users)

### v0.6.0 Breaking Changes

**None** - All changes backward compatible

### v0.4.0 Breaking Changes

**None** - All changes backward compatible

### v0.2.0 Breaking Changes

**User Email Validation** (Issue #42):
- **What Changed**: Email validation regex relaxed to support more TLDs
- **Impact**: Previously rejected emails now accepted
- **Action Required**: None (improvement only)

---

## Security Updates

### High Priority Security Fixes

**v0.9.0 Security Updates**:
- Updated AWS SDK to patch credential leak vulnerability (CVE-2024-XXXXX)
- Enhanced CORS policy to prevent cross-origin attacks
- Added rate limiting to authentication endpoints (prevent brute force)

**v0.8.0 Security Updates**:
- Upgraded PostgreSQL driver to fix SQL injection vulnerability (CVE-2024-YYYYY)
- Strengthened password requirements (now requires special character)
- Implemented session token rotation (reduces session hijacking risk)

**v0.5.0 Security Updates**:
- Presigned URL expiry reduced from 60 minutes to 15 minutes
- Added virus scanning for uploaded files (ClamAV integration planned)
- Implemented CSRF protection on all POST/PUT/DELETE endpoints

---

## Performance Improvements

### Notable Performance Gains

| Release | Improvement | Metric | Details |
|---------|-------------|--------|---------|
| v0.9.0 | Partner directory load time | 50% faster | Implemented caching, reduced DB queries |
| v0.8.0 | Event list pagination | 70% faster | Added database indexes on common filters |
| v0.7.0 | Drag-and-drop responsiveness | 3x faster | Optimized React re-renders, debouncing |
| v0.6.0 | Content preview loading | 60% faster | Lazy load PDF renderer, progressive rendering |
| v0.4.0 | Heat map rendering | 40% faster | Client-side caching, compressed data transfer |
| v0.2.0 | Search autocomplete | 80% faster | Full-text indexes, query optimization |

---

## Deprecation Notices

### Current Deprecations

**None** - No features currently deprecated

### Completed Deprecations

**Speaker Magic-Link Login** (removed in v1.3.0 — Epic 11):
- **What changed**: The JWT magic-link speaker login was fully torn down.
- **Replacement**: AWS Cognito authentication (email/password or "Continue with Google").
- **Migration**: Existing speakers were migrated to Cognito; old magic-link emails no longer authenticate.

### Future Deprecations (Planned)

**None currently scheduled.**

---

## Feedback & Bug Reports

### How to Report Issues

**Bug Reports**: [GitHub Issues](https://github.com/batbern/platform/issues) or support@batbern.ch

**Include**:
- Version number (Settings → About)
- Steps to reproduce
- Expected behavior
- Actual behavior
- Screenshot (if visual issue)
- Browser and OS

**Priority Definitions**:
- **Critical**: Platform down or data loss
- **High**: Major feature broken, no workaround
- **Medium**: Feature broken, workaround exists
- **Low**: Minor inconvenience or cosmetic issue

**Response Times**:
- Critical: <2 hours
- High: <8 hours
- Medium: 1-2 business days
- Low: Next sprint (2 weeks)

---

## Version Support Policy

### Supported Versions

| Version | Status | Support End Date |
|---------|--------|------------------|
| v1.4.0 | **Current** | Until the next release |
| v1.3.x | Superseded | Ended with v1.4.0 (2026-11-04) |
| v1.2.x | Superseded | Ended with v1.4.0 (2026-11-04) |
| v1.1.x | Unsupported | Ended 2026-05-01 |
| v0.9.x | Unsupported | Ended 2026-01-01 |
| v0.8.x and earlier | Unsupported | Ended 2025-06-01 |

**Support Includes**:
- Security patches (critical vulnerabilities)
- Data integrity bug fixes (corruption, loss)
- Documentation updates

**Not Included in Support**:
- New feature backports
- Performance improvements
- Cosmetic bug fixes

### Upgrade Recommendations

- **Always upgrade to latest**: Cumulative improvements, security patches
- **Test in staging first**: Verify compatibility with your workflows
- **Review breaking changes**: Check changelog for required actions
- **Contact support before upgrade**: If running custom integrations

---

## Related Resources

- **[Feature Status](feature-status.md)** - Current implementation status and roadmap
- **[Glossary](glossary.md)** - Platform terminology
- **[Documentation Home](../README.md)** - Full user guide

---

**Questions?** Contact support@batbern.ch or check [Status Page](https://status.batbern.ch)

**Back to Main**: Return to [Documentation Home](../README.md) →
