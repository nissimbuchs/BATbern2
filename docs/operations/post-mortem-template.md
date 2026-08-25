# Post-Mortem Template

Copy this file, or open a **Post-mortem** issue from the GitHub issue template.

**When one is required:** any incident with user-visible impact, or any incident that took more
than an hour to resolve. Silent near-misses caught by an alarm before users noticed are worth one
too, when the near-miss revealed a missing signal.

**Blameless.** The unit of analysis is the system that allowed the mistake, not the person who
made it. "A green deploy is not evidence" is a system finding; "X did not check" is not.

**Not done until the action items are in the backlog.** A post-mortem whose follow-ups live only
inside the post-mortem is a document, not a fix.

---

## Incident summary

One paragraph, readable by someone who was not there. What broke, who was affected, how long,
how it ended.

- **Detected:** `<UTC>` — by which alarm, or by whom if no alarm caught it
- **Resolved:** `<UTC>`
- **Duration of user impact:** `<...>`
- **Severity:** `<user-visible outage | degraded | internal only | near-miss>`
- **Incident issue:** `#<n>`

## Timeline

All times UTC. Include the moments where the diagnosis was **wrong**, not only the ones that led
to the fix. The wrong turns are usually where the missing signal is.

| Time | Event |
|---|---|
| | |

## Root cause

The mechanism, not the trigger. "Deploy X went out" is a trigger; "an applied Flyway migration was
edited by a repo-wide sed, so the checksum changed and ECS rolled back to a stale image" is a
mechanism.

State explicitly which parts were **measured** and which are **inferred**. If a command was not
run, say so.

## Resolution

What was actually changed to end the incident, and whether it was a fix or a mitigation.

## Detection

- Did an alarm catch this? Which one, and how long after onset?
- If not: what signal would have, and does it exist now?
- Did any alarm fire and reach nobody? (See #1005 for why that is a real category here.)

## Contributing factors

Things that made it worse, longer, or harder to diagnose. Missing runbook, misleading log level,
a test that could not fail, a doc that described intent rather than the estate.

## Action items

Every row needs an owner and a tracking issue. Rows without one do not survive the week.

| Action | Type | Issue | Owner |
|---|---|---|---|
| | prevent / detect / mitigate / document | | |

## Lessons learned

What generalises beyond this incident. Prefer the sentence a future reader could apply to a
different subsystem, and add it to `CLAUDE.md` or the agent memory if it is the kind of thing that
would otherwise be rediscovered.
