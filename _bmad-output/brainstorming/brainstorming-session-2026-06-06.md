---
stepsCompleted: [1, 2, 3, 4]
inputDocuments: ['docs/prd-enhanced.md']
session_active: false
workflow_completed: true
github_vote: 'nissimbuchs/BATbern2 issues #746-#766, label idea-vote'
session_topic: 'Attendee experience improvements for a small, low-frequency community'
session_goals: 'Generate right-sized, high-value ideas that make a login worthwhile and enrich the 3-touchpoints-per-year attendee journey — explicitly avoiding the over-ambitious Epic 7 feature set'
selected_approach: 'ai-recommended'
techniques_used: ['Assumption Reversal', 'Role Playing', 'Resource Constraints']
ideas_generated: []
context_file: 'docs/prd-enhanced.md'
---

# Brainstorming Session Results

**Facilitator:** Mary (Business Analyst)
**Participant:** Nissim
**Date:** 2026-06-06

## Session Overview

**Topic:** Improving the BATbern attendee experience

**Goals:** Generate ideas that deliver real added value for attendees, correctly sized for the actual community scale — and give attendees a reason to create (and use) a login.

### Context Guidance

**Community reality (the sizing constraint that killed the old ideas):**
- ~2,000 newsletter subscribers
- ~200 attendees per event
- 3 events per year, each with only 3–6 sessions
- Attendees touch the website ~3 times/year, triggered by the "registration is open" newsletter

**Current attendee experience (as-is):**
- Can register for an event WITHOUT a login
- Can create a login afterwards — but gains NO additional features
- Public frontend: current event home page, past events archive, edit profile. That's it.

**Prior art:**
- Earlier brainstorming (2026-02) produced ideas that proved far too ambitious for this community size
- Epic 7 (deferred): personal dashboard, bookmarks, PWA/offline, notification preferences, recommendation engine — the canonical example of over-scoping (a recommendation engine for 9–18 sessions/year)

### Session Setup

Key framing agreed for this session: ideas must respect a low-frequency engagement model (3 touchpoints/year is a feature, not a bug). Value levers likely lie in: making each touchpoint richer, making the login earn its existence, and leveraging the archive + community intimacy rather than building always-on engagement features.

## Technique Selection

**Approach:** AI-Recommended Techniques
**Analysis Context:** Attendee experience for a small, low-frequency community — with "make the login worth existing" as the core value question and over-ambition as the known failure mode.

**Recommended Techniques:**

- **Assumption Reversal (deep):** Flip the inherited "big platform" assumptions (3 touchpoints is too few; logins need features; more engagement = more value) to open fresh idea territories before generating.
- **Role Playing (collaborative):** Generate ideas through real BATbern attendee archetypes (veteran, first-timer, partner employee, newsletter lurker) — grounding every idea in a human need.
- **Resource Constraints (structured):** The anti-ambition vaccine — stress-test ideas against "one feature before the next event", "zero new backend services", "works for someone who forgot their password".

**AI Rationale:** The prior session failed on over-scoping; this sequence builds the corrective directly into the method — assumption-breaking first (avoid regenerating Epic 7), empathy-grounded generation second, ruthless right-sizing last.

## Technique Execution Results

### Technique 1: Assumption Reversal (completed)

**Assumptions surfaced in the framing:**
1. "3 touchpoints/year is a weakness" → **flipped:** it's the perfect cadence; each touchpoint is too thin
2. "A login must be justified by features" → **flipped:** gate participation, not consumption (see Meta #10)
3. "The experience happens on the website 3×/year" → **flipped:** the archive is a need-driven 4th touchpoint ("did BATbern cover X? what were the lessons learned?")
4. "The relationship is with BATbern the organization" (held for later)
5. "Value is delivered before/during registration" → **flipped:** the real product is what remains afterwards — answered questions, lessons learned, relationships
6. "The 1,800 non-attending subscribers are failed conversions" → **flipped:** they're a valid distinct audience

**Audience insight (Nissim):** ~50% of attendees are near-always regulars; ~50% come depending on the topic; a tail of newsletter readers just want to know what's up in the community. Registration moment = "short moment of joy"; apéro = relationships, not content follow-up; open questions after events have nowhere to live; organizers (volunteers, 20 years, free, ad-free) never receive structured thanks.

**Ideas Generated (Phase 1):**

**[Post-Event #1]**: The Apéro Continues
_Concept_: Time-boxed (~2 weeks) per-session Q&A after each event; attendees post open questions, speakers/attendees answer async; then it freezes and attaches to the session in the archive permanently.
_Novelty_: Not a forum (would die at this scale) — a time-boxed afterglow whose output enriches the archive forever.

**[Gratitude #2]**: Thank-the-Organizers Button
_Concept_: One-click thank-you / short note to the volunteer organizers after each event; visible as counter or appreciation wall.
_Novelty_: Near-zero build cost; addresses a need the attendee stated verbatim ("too bad I cannot thank them").

**[Archive #3]**: Lessons-Learned Search as First-Class Feature
_Concept_: Position archive search around "has BATbern covered X and what were the lessons learned?", with a curated key-takeaways line per session.
_Novelty_: Reframes archive from "past events list" to the community's institutional memory.

**[Identity #4]**: Open Question → Asked at Registration
_Concept_: Registration asks "What question would you like this event to answer?" Speakers see questions pre-event; some get woven into talks/moderation.
_Novelty_: Enriches the registration touchpoint itself and connects attendees to speakers before the event.

**[Community #5]**: Topics From the Floor
_Concept_: Attendees suggest future event topics, flowing into the same topic pool partners feed via topic voting (source: "community").
_Novelty_: Reuses existing partner topic-voting machinery; 200 practitioners become a sensing network.

**[Community #6]**: "I Could Speak on That"
_Concept_: When the next event's topic is announced, attendees can self-nominate: title + abstract → speaker_pool at READY; organizers triage as usual.
_Novelty_: Pull→push speaker acquisition on the existing 8-state workflow; zero new state machine.

**[Post-Event #7]**: "The Slides Are Online" Moment
_Concept_: Per-event notification the day slides land — the email the attendee said he'd actually open. A 4th newsletter per event.
_Novelty_: Cheapest touchpoint enrichment: 3 visits/year → 6 without demanding more commitment.

**[Networking #8]**: Apéro Follow-Up Handshake
_Concept_: Post-event, mark "I'd like to follow up with [name]"; contacts exchanged only on mutual opt-in/approval.
_Novelty_: Digitizes what already happens physically at the apéro; double-opt-in defuses the privacy problem.

**[Networking #9]**: ~~"Who's Coming" Opt-In List~~ — **KILLED by user decision** (not wanted).

**[Meta #10]**: The Login Is For Contributing, Not Consuming — **SESSION THESIS**
_Concept_: Every surviving idea (ask, thank, suggest, volunteer, handshake) requires identity. The login was pointless because it gated consumption (content is rightly free). Gate participation instead.
_Novelty_: Inverts freemium logic — anonymous users lose nothing; logged-in users gain a voice, not content.

### Technique 2: Role Playing (completed — Tom & Rita explored; Walter & Fiona deferred)

**Persona cast (from Nissim's audience data):** Rita the Regular (~50%, ritual + people), Tom the Topic-Picker (~50%, decides by topic), Walter the Watcher (newsletter-only ~1,800), Fiona the First-Timer.

**Tom's decision scan:** companies presenting → use cases → industries → vendor-smell check (BAT's 20-year no-vendor-pitch rule is a trust asset Tom re-verifies every time). Existing newsletter cadence: topic announcement → timetable update → 2-week reminder with full lineup.

**Rita findings:** registers regardless of topic; loves that topics "hit the current wave" without being hype; is a PROMOTER (tells others) but NOT a host (rejected welcome-committee duties); sacred: the apéro, topic relevance, the crowd continuing to come.

**Ideas Generated (Phase 2):**

**[Touchpoint #11]**: The 90-Second Use-Case Card
_Concept_: Each session rendered as a scannable card answering Tom's algorithm: company + industry + lesson-learned-in-one-sentence + tech topics. No marketing prose.
_Novelty_: Designs the event page around the decision scan of 50% of the audience instead of chronology.

**[Trust #12]**: "No Vendor Pitches — Since 2005" Badge
_Concept_: Make the implicit rule explicit: a visible trust marker on event pages and session cards ("practitioner lessons learned, no sales talks — our 20-year rule").
_Novelty_: Pre-answers Tom's #1 hesitation at zero cost; displays the moat.

**[Community #13]**: Attendee Topic Voting — Close the Loop
_Concept_: Extend partner topic voting to logged-in attendees. When a topic Tom voted for becomes the next event → "the topic YOU voted for is happening — registration open."
_Novelty_: Converts passive topic-filtering into active topic-shaping; the most personally relevant email BATbern could send. Reuses existing voting machinery (builds on #5).

**[Archive #14]**: "We've Covered This Before" Strip
_Concept_: Event page shows past BATbern sessions on this topic from the archive.
_Novelty_: Welds the event-page touchpoint to the archive touchpoint; shows 20 years of depth at the credibility-evaluation moment.

**[Funnel #15]**: Register-on-Topic, Enriched Later
_Concept_: Free event → one-click registration at topic announcement; the later lineup newsletters become confirmation/retention rather than conversion. Easy self-service cancel.
_Novelty_: Moves commitment to the peak-joy moment; reframes the existing 3-newsletter cadence.

**[Promoter #16]**: One-Tap Forwarding Kit
_Concept_: Pre-written forward-email, LinkedIn-ready post with clean preview card per event, per-session deep links.
_Novelty_: Rita shares a recommendation, not a URL — the kit writes it for her (10 seconds instead of composing).

**[Promoter #17]**: "Bring Someone New" Nudge at Registration
_Concept_: One soft prompt at Rita's registration moment: "Know someone who'd love this topic? Send them the invite." No tracking, no gamification.
_Novelty_: Converts regulars' loyalty into the first-timer pipeline at the emotional peak, in BATbern style.

**[Guardrail #18]**: The Sacred Three — DO-NOT-BREAK List
_Concept_: (1) Apéro stays unstructured — no app intrusion into the physical ritual; (2) topics stay lessons-learned-driven, never hype/vendor; (3) the crowd keeps coming — features that change the event's character are rejected by default.
_Novelty_: An anti-feature contract — the corrective the over-ambitious prior session lacked.

**[Rejected in persona testing]**: Rita as first-timer host / welcome committee — regulars promote outward, they don't want assigned social duties inward.

**User-injected territory before Phase 3 (channels & next generation):**

**[Channels #19]**: Meet Them Where They Are — LinkedIn-Native BATbern
_Concept_: Treat LinkedIn (and Instagram for the younger crowd) as first-class channels, not link-dumps: event announcements as native posts, post-event lessons-learned snippets, session-takeaway carousels from the slides.
_Novelty_: The webpage stops being the only stage; content goes to where the audience already scrolls. Synergy with #16's share kit.

**[NextGen #20]**: Bring-Your-Junior
_Concept_: Aim the #17 nudge at the next generation explicitly: "bring a junior architect from your team." Regulars become the bridge to the younger community.
_Novelty_: Solves the aging-community problem through the existing community's strongest asset — its promoters — rather than through marketing spend.

**[NextGen #21]**: Young Voices on Stage
_Concept_: Reserve one session slot (or a lightning-talk slot) per year for an under-35 practitioner — sourced via the "I Could Speak on That" self-nomination (#6).
_Novelty_: Attracts the young by SHOWING them on stage, not by advertising at them; composes two ideas already in the pool.

### Technique 3: Resource Constraints (completed)

**Constraint Round 1 — Scarcest Resource Test:** Confirmed by Nissim: the binding constraint is **volunteer organizer attention** ("we don't have time to work a lot beside the organization — everything must be automatic"). Every idea must run on (near-)zero recurring organizer effort.

- Pass (self-running once built): #2, #5, #6, #7 (auto-publishing exists), #12 (one-time), #13, #14 (if auto-matched), #15, #16, #17, #20
- Wounded → rescued: #3 & #11 via #22 (speaker supplies the takeaway), #19-LinkedIn via #23 (agent drafts, humans review)
- Wounded → watch list: #1 (needs agent-chasing of speakers to be safe), #4 (needs agent clustering/filtering)

**Constraint Round 2 — One-Feature Test:** Asked to pick ONE, Nissim picked four — revealing two natural bundles:
- **Bundle A — Richer Touchpoints (no login):** #7 Slides-Online Mail + #11 Use-Case Cards (named twice — strongest gut pull; brings dependency #22)
- **Bundle B — Contribution Loop (login-gated):** #5 Topics From the Floor + #6 "I Could Speak on That" (one feature wearing two hats: same surface, same gate, same triage, feeding existing topic pool + speaker_pool)

**Ideas Generated (Phase 3):**

**[Guardrail #24]**: The Cadence-Match Principle
_Concept_: Only play on channels that tolerate ~6-9 quality posts/year. LinkedIn: yes. Instagram/TikTok as community channels: no — they punish silence with death; a 3×/year feed looks abandoned and damages the brand. Reach the young via people (#20/#21), not feeds.
_Novelty_: Channel selection derived from the community's own metabolism, not from "where the young people are."

**[Automation #22]**: Speaker-Sourced Takeaways
_Concept_: "Lessons learned in one sentence" + industry/tech tags become required fields in the existing speaker material submission. One structured field feeds use-case cards (#11), archive takeaway lines (#3), and lessons-learned search.
_Novelty_: Effort lands on the person with most skin in the game, at a moment they're already in the portal. Rescues #3 and #11 fully.

**[Automation #23]**: The Curation Agent — Draft Everything, Review Only
_Concept_: Extend the existing abstract-quality agent: drafts homepage session cards, LinkedIn post per event, slides-online announcement, takeaway polish. Organizers get a review-and-approve queue.
_Novelty_: Flips the organizer role from producer to editor — the only sustainable role for a volunteer committee. Rescues #19-LinkedIn; softens #1 and #4.

**[Decision #25]**: Login-Gated Contributions — Because Identity Enables Dialogue
_Concept_: ALL contribution features (#5, #6, future #13/#1) sit behind login — not for gatekeeping but for the return channel: organizers can reply ("why do you suggest that topic?"), a suggestion can become a conversation, a conversation can become a speaker. Anonymous suggestions are dead ends. Friction resolved by "Continue with Google" (Epic 12, live).
_Novelty_: Upgrades thesis #10 — the login is not just for contributing, it is for **being reachable**. Future-proofs community features on accounts that exist for a felt reason.

**Emergent architecture (the session's structural insight):** a three-layer engine — **attendees contribute (#10/#25) → speakers structure (#22) → agent curates, humans approve (#23)**. Nobody works more; everything compounds on existing machinery (topic pool, speaker_pool 8-state workflow, speaker portal, auto-publishing, abstract-quality agent, Google SSO).

### Creative Facilitation Narrative

Nissim arrived with a humility most product owners lack: "our last ideas were too ambitious for our community." The session's engine was that humility made methodical — assumptions flipped first, personas grounded in real audience data (50% regulars / 50% topic-pickers), constraints applied without mercy. The breakthrough moments were user-driven: the secret 4th touchpoint (need-driven archive visits), the gratitude gap stated verbatim, the pull→push speaker pipeline mapped instantly onto the existing 8-state workflow, and the final reframe of the login as a *return channel*. Two ideas were killed deliberately (who's-coming list, welcome committee) — and the kills sharpened the survivors.

### Session Highlights

**User Creative Strengths:** Instant feasibility-mapping onto existing machinery; decisive killing of unwanted ideas; persona empathy (answered as Tom/Rita, not as organizer).
**AI Facilitation Approach:** Assumption-flipping → persona inhabitation → constraint stress-testing; domain pivots every ~10 ideas to fight clustering.
**Breakthrough Moments:** The contribution thesis (#10) and its upgrade (#25); the three-layer automation engine (#22+#23); the cadence-match channel principle (#24).
**Energy Flow:** Convergent by nature — Nissim generates best when reacting to concrete provocations, and converges early toward buildable bundles.

## Idea Organization and Prioritization

**Thematic Organization (5 themes):**
1. **🎯 Contribution Loop** — #5, #6, #13, #2, #8 + thesis #10/#25 (the login earns its existence)
2. **📬 Richer Touchpoints** — #7, #11, #12, #14, #15, #4, #3 (same 3 visits/year, 10× thicker)
3. **🤖 Automation Engine** — #22, #23, #1 (attendees contribute → speakers structure → agent curates → organizers approve)
4. **📣 Promoters & Next Generation** — #16, #17, #20, #21, #19 (growth through people, not feeds)
5. **🛡️ Guardrails** — #18 Sacred Three, #24 Cadence-Match (+ kills: #9, Rita-as-host)

**Prioritization Results (Nissim's One-Feature-Test picks):**
- **Bundle B — Contribution Loop MVP:** #5 + #6 behind login (per #25) — one surface, two hands raised, feeding existing topic pool + speaker_pool
- **Bundle A — Touchpoint Pack:** #7 + #11 with #22 as enabler
- **Quick wins:** #12 (an afternoon), #2, #16/#17
- **Later:** #13, #23, #14, #21 · **Watch list:** #1, #4 (only with agent support)

**Decision: prioritization delegated to the organizer committee via GitHub vote.**

## Organizer Vote on GitHub (executed 2026-06-06)

All 20 living feature ideas published as GitHub issues in `nissimbuchs/BATbern2`, label `idea-vote`; organizers vote with 👍 reactions.

- **Umbrella issue:** [#766](https://github.com/nissimbuchs/BATbern2/issues/766) — instructions, themes, guardrails
- **Idea issues:** #746–#765 (session idea → issue): 01→746, 02→747, 03→748, 04→749, 05→750, 06→751, 07→752, 08→753, 11→754, 12→755, 13→756, 14→757, 15→758, 16→759, 17→760, 19→761, 20→762, 21→763, 22→764, 23→765
- **Results view:** [issues sorted by 👍](https://github.com/nissimbuchs/BATbern2/issues?q=is%3Aissue+is%3Aopen+label%3Aidea-vote+sort%3Areactions-%2B1-desc)

## Session Summary and Insights

**Key Achievements:**
- 25 captured items: 21 living ideas, 2 deliberate kills, 2 guardrails — plus the three-layer automation architecture
- The login question answered: gate participation, not consumption; identity = reachability for dialogue
- Every priority idea composes onto existing machinery (topic pool, speaker_pool 8-state workflow, speaker portal, auto-publishing, abstract-quality agent, Google SSO) — the anti-Epic-7 outcome the session was designed for
- Prioritization democratized: the organizer committee votes on GitHub instead of one person deciding

**Session Reflections:**
The constraint phase (zero recurring organizer effort) did more shaping than the generation phases — it converted "content features" into "automation patterns" and revealed that the platform's next evolution is organizational (contributor pipelines + agent curation), not feature-quantity. The kills (#9 who's-coming, Rita-as-host) and the channel rejection (Instagram/TikTok) protect the event's character as effectively as the new ideas enhance it.
