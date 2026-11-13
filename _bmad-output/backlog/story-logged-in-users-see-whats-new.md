---
id: 2
type: story
title: "Logged-in users see the features added since they last looked"
parent: none
covers: []
sources: []
companions: []
after: []
refined: false
hitl: true
risk: medium
---

# Logged-in users see the features added since they last looked

## Description

Attendee features built in the last months are hard to find, because the site shows mostly the next event. A dismissible "What's new" card at the top of the attendee dashboard lists the features released since the user last dismissed it, at most three. Each item has a title, one sentence and a link to the feature. A small dot on the avatar menu says something is new. The feature list lives in the code with texts in all 10 locales, and every later feature adds its entry in the same PR. The user's "last seen" time is stored on the user profile, so the card behaves the same on every device. On a first login the user sees the three most important features. A person (the owner) chooses and orders the first list before the build ends.

## Requirements

Verify:
- A user who has not seen the latest entries sees the card with at most three items and the dot.
- Dismissing the card hides it and the dot on this device and on another device (same account, fresh browser).
- Adding a newer entry to the list makes the card come back with only that entry.
- A first-time user sees the three entries marked most important.
- A user who is not logged in never sees the card.
- An entry can be limited to users it applies to. The first such entry is the profile merge: it shows, ranked first, only to a user for whom an earlier profile under the same name exists, and links to the existing offer. A user without one never sees it.
- Shown on local dev with an integration test against PostgreSQL for the stored "last seen" time.

## Boundaries

- Must not change: the rest of the attendee dashboard (next event, registrations, history).
- Must not change: login, onboarding and the terms-of-service gate. The card never blocks or opens on its own (no modal, no tour).
- Touch point: CUMS user profile (a new field and an endpoint to read and set it). The endpoint is self-service only.
- Touch point: frontend texts in all 10 locales.

## References

- decision — owner, 2026-11-13, in session: the "What's new" approach
- sibling — _bmad-output/backlog/story-visitors-see-what-an-account-gives-them.md
- standard — docs/architecture/coding-standards.md, Test fidelity rules
- standard — CLAUDE.md, Localization section and "@PreAuthorize on every endpoint that writes"

## Notes

- Decision: 2026-11-13, owner: first list: session Q&A and comments (two weeks before to two weeks after the event), suggest topics, login and profile, company logo request, thank the organizers, event history, self-nomination. The three shown at a first login: Q&A, suggest topics, company logo.
- Decision: 2026-11-13, owner: every logged-in user sees every entry. Partners and organizers are attendees too, so there are no per-role entries.
- Assumption: (proposed 2026-11-13, owner to confirm) add the profile merge (#1183 D), which so far shows only as a dashboard banner and in Settings. It is a conditional entry, shown only where it applies.
- Assumption: an empty list or an unreadable stored time shows no card rather than an error.
- Check beyond the criteria (risk medium, every logged-in user sees it): click through on beta.batbern.ch is not possible for the backend part, so walk through it on local dev as an attendee, a speaker and an organizer before the PR.
