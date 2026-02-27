# Topic Voting

> Partners can suggest topics for future BATbern events and vote on proposals from other companies

<span class="feature-status implemented">Implemented</span> — Epic 8.2

## Overview

The topic voting system gives partner companies a voice in shaping the content of future BATbern events. Partners can:

- **Browse** all proposed topics with their current vote counts and status
- **Vote** for topics they'd like to see at a future event (one vote per topic, toggled on/off)
- **Suggest** new topics that haven't been proposed yet

Organisers review all suggestions, select topics for upcoming events, and update each topic's status so all partners can see the outcome.

## The Topic List

Topics are displayed sorted by vote count (most voted first):

```
┌─────────────────────────────────────────────────────────────────────┐
│  Topic Suggestions                          [+ Suggest a Topic]     │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  🟢 SELECTED  Planned for: BATbern 57                       │   │
│  │  Digital Twins in Construction                              │   │
│  │  Suggested by: Tech Innovations AG  ·  ▲ 14 votes          │   │
│  │  How digital twins are transforming project delivery...     │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  ⬜ PROPOSED                            [✅ You voted]       │   │
│  │  Circular Economy in Architecture                           │   │
│  │  Suggested by: Sustainable Materials AG  ·  ▲ 9 votes      │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  ⬜ PROPOSED                            [☐ Vote]            │   │
│  │  AI in Structural Engineering                               │   │
│  │  Suggested by: BIM Solutions GmbH  ·  ▲ 6 votes           │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  🔴 DECLINED                                                │   │
│  │  Traditional Swiss Building Techniques                      │   │
│  │  Suggested by: Alpine Architekten  ·  ▲ 2 votes            │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### Topic Status Badges

| Badge | Status | Meaning |
|-------|--------|---------|
| ⬜ **PROPOSED** | Default | Under consideration, voting open |
| 🟢 **SELECTED** | Organiser chose it | Will be featured at a future event |
| 🔴 **DECLINED** | Organiser declined | Not suitable for BATbern at this time |

When a topic is **SELECTED**, organisers optionally add a "Planned for" note (e.g., "Planned for: BATbern 57") so partners know when to expect it.

## Voting

Voting is a simple on/off toggle. Each partner company gets **one vote per topic**.

<div class="step" data-step="1">

**Find a topic you support**

Browse the list or use the browser's page search (Ctrl+F / Cmd+F).
</div>

<div class="step" data-step="2">

**Click the vote button**

```
[☐ Vote]       → Click to add your vote
[✅ You voted]  → Click again to remove your vote
```

The vote count updates immediately. Your vote is attributed to your company, not your individual user account.
</div>

<div class="alert info">
ℹ️ <strong>One vote per company per topic.</strong> If multiple users from the same company vote, only one vote is counted (last action wins).
</div>

## Suggesting a New Topic

<div class="step" data-step="1">

**Click "+ Suggest a Topic"**

The suggestion dialog opens.
</div>

<div class="step" data-step="2">

**Fill in the topic details**

```
┌─────────────────────────────────────────────┐
│  Suggest a Topic                             │
│                                              │
│  Title *                  [ 0 / 255 chars ] │
│  ┌─────────────────────────────────────────┐ │
│  │                                         │ │
│  └─────────────────────────────────────────┘ │
│                                              │
│  Description (optional)   [ 0 / 500 chars ] │
│  ┌─────────────────────────────────────────┐ │
│  │                                         │ │
│  └─────────────────────────────────────────┘ │
│                                              │
│  [Cancel]           [Submit Suggestion]      │
└─────────────────────────────────────────────┘
```

| Field | Required | Limit |
|-------|----------|-------|
| **Title** | Yes | 255 characters (minimum 5) |
| **Description** | No | 500 characters |
</div>

<div class="step" data-step="3">

**Submit**

Your suggestion appears at the bottom of the list with status **PROPOSED** and 0 votes. Your company name is shown as the suggester.
</div>

## What Happens After Suggestion

1. Your topic appears in the list immediately (status: PROPOSED)
2. Other partners can see and vote on it straight away
3. The organising team reviews suggestions when planning the next event
4. The organiser updates the status to **SELECTED** or **DECLINED**
5. If selected, they may add a "Planned for" note — you'll see it in the list

## For Organisers

Organisers can see all topics and voting data. To update a topic's status:

1. Go to **Partners → Topics** (or the topic voting view)
2. Click the **⋯** menu on a topic card
3. Select **Set Status → Selected** or **Declined**
4. Optionally add a **Planned Event** note (e.g., "BATbern 57")
5. Save — all partners see the updated status immediately

Organisers can also suggest topics on behalf of a partner, or delete spam/duplicate suggestions.

## Troubleshooting

### "My vote isn't showing"

Votes are attributed to your company (not individual user). If a colleague from the same company voted and then unvoted, your company's vote will show as unvoted. Toggle the vote again to reapply it.

### "I submitted a topic but it's not appearing"

Refresh the page. If still missing after a few minutes, contact the organising team — there may be a validation issue with the title.
