---
id: 3
type: story
title: "The attendee dashboard shows the next three events, registered or not"
parent: none
covers: []
sources: []
companions: []
after: []
refined: false
hitl: false
risk: low
---

# The attendee dashboard shows the next three events, registered or not

## Description

The "upcoming" list on the attendee dashboard shows only events the user has registered for, so it is usually empty. It shows three events instead: every upcoming event the user is registered for, then the next upcoming events they are not registered for, until there are three. The cards look like the homepage's upcoming-event cards. A published event links to its event page, where the user registers. An event not yet published shows code, title and date only, without a link. There is no register button on the card (owner 2026-11-13).

## Requirements

Verify: on local dev, a user with no registration sees the next three events. A user registered for one sees that event first, marked as registered, then the next two. Every published card links to its event page. An unpublished one shows only code, title and date. When the public list fails, the registered events still show.

## Boundaries

- Must not change: the "past" list and the topic-suggestion panel on the dashboard.
- Must not change: the homepage's upcoming section (reuse it, don't fork it).
- Must not show unpublished event details beyond code, title and date (2026-06-11 leak).

## References

- decision — owner, 2026-11-13, in session: three cards, registered first, link to the event page for registration
- sibling — _bmad-output/backlog/story-logged-in-users-see-whats-new.md (same page)

## Notes

- Decision: 2026-11-13, owner: always three cards in total, including the ones the user is registered for. Registration happens on the event page, not on the card.
- Assumption: a user registered for more than three upcoming events sees all of them and no unregistered ones.
