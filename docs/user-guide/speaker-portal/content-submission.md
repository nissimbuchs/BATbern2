# Content Submission

> Speakers submit their presentation title, abstract, and materials through a guided portal

<span class="feature-status implemented">Implemented</span> — Epic 6.3

## Overview

After accepting an invitation, speakers submit their presentation content through the self-service portal. The organiser receives the submission automatically, reviews it in Phase C (Quality Review), and approves or requests revisions.

Speakers access the content submission portal via the **VIEW magic link** included in their acceptance confirmation email. This link is **reusable** (not single-use) and valid for 30 days, so speakers can return multiple times.

## Accessing the Submission Portal

Speakers receive a **"Submit Content"** link in their acceptance confirmation email. This link opens the content submission portal directly. No login is required.

If the link has expired (30 days), the speaker should contact the organiser to request a new link. Organisers can resend from the Phase B: Outreach screen.

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

Files are uploaded directly to S3 via presigned URL — they are never sent through the backend. A progress bar is shown during upload.

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

The portal **automatically saves a draft every 30 seconds**. If the speaker closes the browser and returns via their link, the draft is restored from the server.

```
Last auto-saved: 30 seconds ago  [  Save Now  ]
```

Manual saving is also available at any time.

## After Submitting

When the speaker submits:

1. **Speaker status** transitions: **ACCEPTED → CONTENT_SUBMITTED**
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
- A direct link to the content submission portal (fresh 30-day VIEW token)

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

| Status | Meaning |
|--------|---------|
| **Not submitted** | Speaker has not yet submitted content |
| **Under review** | Content submitted, awaiting organiser review |
| **Approved** | Organiser approved the content |
| **Revision needed** | Organiser requested changes — speaker must resubmit |

The speaker can see their current status on the [Speaker Dashboard](dashboard.md).

## Organiser: Uploading on Behalf of a Speaker

Organisers can also submit content on behalf of a speaker directly from the Phase B: Outreach screen (hybrid workflow). This is useful when a speaker is not comfortable with online submissions or has provided content by email or phone.

See [Phase B: Outreach →](../workflow/phase-b-outreach.md) for the organiser-side content collection flow.

## Troubleshooting

### Speaker's link is expired

The content submission link is a 30-day VIEW token. If expired, the organiser can resend from Phase B: Outreach — this generates a new token and sends a reminder email.

### File upload fails

Common causes:
- File exceeds 50 MB — ask the speaker to compress or split the file
- Unsupported format — only PPTX, PDF, and KEY are accepted
- Browser timeout on slow connection — try a smaller file or a faster connection

### Speaker submitted the wrong file

The speaker can resubmit from the portal — each submission creates a new version. The organiser sees all versions and can mark the correct one for review.
