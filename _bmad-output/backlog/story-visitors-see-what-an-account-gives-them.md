---
id: 1
type: story
title: "Visitors see what a BATbern account gives them before they create one"
parent: none
covers: []
sources: []
companions: []
after: []
refined: false
hitl: true
risk: low
---

# Visitors see what a BATbern account gives them before they create one

## Description

People who register for an event without an account are told today, in the confirmation mail and in the registration wizard, that an account lets them "manage registrations", "access exclusive content" or get "personalized event recommendations". That is too vague, and part of it is not true. Replace it with three or four concrete things an account gives them today, and add one public page that lists them, linked from both places. A person (the owner) chooses the features and approves the German and English wording before the build ends.

## Requirements

Verify: in local dev, register for an event without an account; the confirmation mail (in `/dev/mails`, DE and EN) and the wizard's last step show the chosen benefits and link to the public page. That page renders in all 10 locales, is reachable without a login, and links to account creation. Every benefit named exists in the product.

## Boundaries

- Must not change: the confirmation link, the 48-hour confirmation rule and the calendar-entry text in the confirmation mail.
- Must not change: registration itself (the wizard's steps, fields and validation).
- Touch point: mail templates are DE and EN only. Other locales fall back to EN (CLAUDE.md, Localization).
- Touch point: the new page and wizard texts need keys in all 10 locales.

## References

- decision — owner, 2026-11-13, in session: the "What's new" approach (teaser for people without a login, card for logged-in users)
- sibling — _bmad-output/backlog/story-logged-in-users-see-whats-new.md
- standard — CLAUDE.md, Localization section

## Notes

- Decision: 2026-11-13, owner: the benefits are, in this order: Q&A and comments on sessions (two weeks before to two weeks after the event), suggest topics, profile and history, company logo. Final wording is approved at the build.
- Assumption: (proposed 2026-11-13, owner to confirm) "profile and history" includes the profile merge (#1183 D): all past BATbern visits in one place, including registrations made before the account existed.
- Decision: 2026-11-13, owner: no teaser for anonymous visitors on the general website. It appears only where they register (mail, wizard) and on the linked page.
- Parked: event-window hint (Q&A open ±2 weeks) on the event page and in the pre- and post-event mails. This is a separate follow-up story.
- Parked: a one-off "What's new at BATbern" newsletter. The owner sends it himself; an agent only drafts it.
