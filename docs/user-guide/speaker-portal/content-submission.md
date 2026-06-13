# Content Submission

> Speakers submit their presentation title, abstract, and materials through a guided portal

<span class="feature-status implemented">Implemented</span> — Epic 6.3, refactored to Cognito auth in Epic 11.E.3

**Last Updated:** 2026-06-13

## Overview

After accepting an invitation, speakers submit their presentation content through the self-service portal. The organiser receives the submission automatically, reviews it in Phase C (Quality Review), and approves or requests revisions.

Content submission happens behind the speaker's **Cognito-authenticated session** — the speaker signs in to BATbern (email + password, or "Continue with Google") and opens the submission page for the relevant event. The page identifies the event by its `eventCode` in the URL path (e.g. `/speaker-portal/events/BATbern57/content`); there are no magic-link tokens.

## Accessing the Submission Portal

From the [Speaker Dashboard](dashboard.md), the speaker clicks **Submit Content** on the event card. The acceptance confirmation email and deadline reminder emails also link straight to the login page — after signing in the speaker lands on the dashboard and picks the event.

Because the portal is Cognito-secured, a speaker can only submit content for events they were invited to: the backend resolves the speaker's pool entry from `(authenticated username, eventCode)` and returns **403 Forbidden** if no matching invitation exists.

## Submission Wizard

The submission form has three main sections:

### 1 — Presentation Details

```
┌─────────────────────────────────────────────────────┐
│  Presentation Details                                │
│                                                      │
│  Title *                          [ 0 / 200 chars ] │
│  ┌─────────────────────────────────────────────┐    │
│  │                                             │    │
│  └─────────────────────────────────────────────┘    │
│                                                      │
│  Abstract *                      [ 0 / 1000 chars ] │
│  ┌─────────────────────────────────────────────┐    │
│  │                                             │    │
│  │  💡 Tips: Include what attendees will       │    │
│  │     learn, key takeaways, and lessons.      │    │
│  └─────────────────────────────────────────────┘    │
│  ⚠️  Abstract is under 200 characters — consider    │
│      adding more detail for attendees.               │
└─────────────────────────────────────────────────────┘
```

**Field rules:**

| Field | Required | Limit | Notes |
|-------|----------|-------|-------|
| **Title** | Yes | 200 characters | Real-time character counter |
| **Abstract** | Yes | 1000 characters | Warning shown if under 200 chars |

### 2 — Presentation File (Optional)

```
┌─────────────────────────────────────────────────────┐
│  Presentation File (Optional)                        │
│                                                      │
│  ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐  │
│  │   Drag and drop your file here, or           │  │
│  │        [  Browse Files  ]                    │  │
│  │                                               │  │
│  │   Accepted formats: PPTX, PDF, KEY           │  │
│  │   Maximum size: 50 MB                        │  │
│  └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘  │
└─────────────────────────────────────────────────────┘
```

Files are uploaded directly to S3 via presigned URL — they are never sent through the backend. The speaker's Cognito Bearer token authorises the request; the presigned-URL endpoint is scoped to the event's `eventCode`. A progress bar is shown during upload.

### 3 — Review & Submit

The speaker reviews all entered details before submitting:

```
┌─────────────────────────────────────────────────────┐
│  Review Your Submission                              │
│                                                      │
│  Title:    Digital Transformation in Practice       │
│  Abstract: 487 / 1000 chars ✅                      │
│  File:     presentation-v3.pptx (2.3 MB) ✅        │
│                                                      │
│  [  ← Back  ]                  [  Submit  ]         │
└─────────────────────────────────────────────────────┘
```

## Draft Auto-Save

The portal **automatically saves a draft every 30 seconds**. If the speaker closes the browser and signs back in, the draft is restored from the server (keyed to the speaker's identity + the event).

```
Last auto-saved: 30 seconds ago  [  Save Now  ]
```

Manual saving is also available at any time.

## After Submitting

When the speaker submits:

1. **Speaker status** transitions: **ACCEPTED → CONTENT_SUBMITTED** (recorded in `speaker_status_history` with `changed_by_username` = the speaker's Cognito username)
2. **Organiser notified** automatically (domain event triggers in-app notification)
3. **Speaker sees** a success page with the content deadline and a link back to their dashboard:

```
┌─────────────────────────────────────────────────────┐
│  ✅ Your submission has been received!               │
│                                                      │
│  The organising team will review your content       │
│  and get back to you if any changes are needed.     │
│                                                      │
│  You can track the review status on your dashboard. │
│                                                      │
│  [  Go to Dashboard  ]                              │
└─────────────────────────────────────────────────────┘
```

## Revisions

If the organiser requests changes (Phase C Quality Review), the speaker receives a **revision request email** containing:

- The reviewer's feedback
- A link to sign in and reopen the content submission page for that event

The portal shows the feedback at the top of the form:

```
┌─────────────────────────────────────────────────────┐
│  📝 Revision Requested                               │
│                                                      │
│  Feedback from organiser:                           │
│  "Please shorten the abstract to focus on the key   │
│   takeaway for the audience. The current version    │
│   is a good start but reads as too technical."      │
└─────────────────────────────────────────────────────┘
```

The speaker edits the form and resubmits. Each resubmission increments the version number (v1, v2, v3…), and all versions are stored for the organiser.

## Submission Status

| Status | Meaning | Workflow state |
|--------|---------|----------------|
| **Not submitted** | Speaker has not yet submitted content | `ACCEPTED` |
| **Under review** | Content submitted, awaiting moderator review | `CONTENT_SUBMITTED` |
| **Approved** | Moderator approved the content | `QUALITY_REVIEWED` |
| **Revision needed** | Organiser requested changes — speaker must resubmit | `CONTENT_SUBMITTED` + revision flag |

The speaker can see their current status on the [Speaker Dashboard](dashboard.md). Once content is `QUALITY_REVIEWED` **and** a slot is assigned, the speaker is publishable (the legacy `CONFIRMED` state, removed in Epic 11, is replaced by this derived `is_publishable` condition).

## Organiser: Uploading on Behalf of a Speaker

Organisers can also submit content on behalf of a speaker directly from the Phase B: Outreach screen (hybrid workflow). This is useful when a speaker is not comfortable with online submissions or has provided content by email or phone. Both the speaker-self and organizer-on-behalf paths traverse the same `ContentSubmissionService`.

See [Phase B: Outreach →](../workflow/phase-b-outreach.md) for the organiser-side content collection flow.

## Troubleshooting

### Speaker can't open the submission page

The page requires a signed-in speaker session. If the speaker sees a login redirect, they should sign in with their BATbern account (the credentials from the invitation email, or "Continue with Google"). If they reach the page but get a 403, confirm they were actually invited to that event — the page is scoped per event.

### File upload fails

Common causes:
- File exceeds 50 MB — ask the speaker to compress or split the file
- Unsupported format — only PPTX, PDF, and KEY are accepted
- Browser timeout on slow connection — try a smaller file or a faster connection

### Speaker submitted the wrong file

The speaker can resubmit from the portal — each submission creates a new version. The organiser sees all versions and can mark the correct one for review.
