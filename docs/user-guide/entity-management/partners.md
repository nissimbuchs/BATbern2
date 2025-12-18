# Partner Management

> Coordinate partner relationships and event collaboration

<span class="feature-status implemented">Implemented</span>

## Overview

Partners are organizations that collaborate with BATbern through sponsorship, resource sharing, or strategic partnerships. Partners receive benefits like event presence, networking opportunities, and brand visibility.

## Partner Tiers

<span class="feature-status implemented">Implemented</span>

Partners are classified into 5 tiers based on contribution level and benefits:

### 💎 Diamond

<span style="background: linear-gradient(135deg, #B8B8D0, #D0D0E0); padding: 4px 12px; border-radius: 12px; font-weight: 500; color: #333;">💎 DIAMOND</span>

**Highest tier** - Premier partnership with maximum visibility.

**Benefits**:
- ✅ Logo on all event materials (print, digital, venue)
- ✅ Speaking slot (30-45 minutes)
- ✅ Premium booth location
- ✅ Attendee list access (GDPR compliant)
- ✅ Pre-event networking reception hosting
- ✅ Dedicated organizer liaison

**Typical Partners**: Major architecture firms, technology providers

### 🥈 Platinum

<span style="background: #E5E4E2; padding: 4px 12px; border-radius: 12px; font-weight: 500; color: #333;">PLATINUM</span>

**Premium tier** - High visibility and significant benefits.

**Benefits**:
- ✅ Logo on event materials
- ✅ Speaking opportunity (15-20 minutes)
- ✅ Booth at event
- ✅ Attendee list access
- ✅ Networking reception presence

**Typical Partners**: Industry associations, larger suppliers

### 🥇 Gold

<span style="background: #FFD700; padding: 4px 12px; border-radius: 12px; font-weight: 500; color: #333;">GOLD</span>

**Standard sponsorship** - Good visibility and core benefits.

**Benefits**:
- ✅ Logo on website and event program
- ✅ Table at event venue
- ✅ Networking opportunities
- ✅ Social media mentions

**Typical Partners**: Material suppliers, software vendors

### 🥈 Silver

<span style="background: #C0C0C0; padding: 4px 12px; border-radius: 12px; font-weight: 500; color: #333;">SILVER</span>

**Supporting sponsorship** - Moderate visibility.

**Benefits**:
- ✅ Logo on website
- ✅ Mention in event program
- ✅ Networking access

**Typical Partners**: Local businesses, service providers

### 🥉 Bronze

<span style="background: #CD7F32; padding: 4px 12px; border-radius: 12px; font-weight: 500; color: white;">BRONZE</span>

**Basic sponsorship** - Recognition and support.

**Benefits**:
- ✅ Logo on website
- ✅ Name in event program

**Typical Partners**: Small businesses, startups

## Creating a Partner

<div class="step" data-step="1">

**Navigate to Partners**

Click **🤝 Partners** in the left sidebar.
</div>

<div class="step" data-step="2">

**Click "Create New Partner"**

Click the **+ Create New Partner** button (top-right).
</div>

<div class="step" data-step="3">

**Fill Partner Details**

Complete the partner creation form:

**Organization Information**:
- **Partner Name*** - Organization name
- **Partner Tier*** - Select tier (Diamond, Platinum, Gold, Silver, Bronze)
- **Website** - Partner website URL
- **Industry** - Business sector (e.g., "Building Materials", "Software")

**Contact Information**:
- **Primary Contact Name*** - Main liaison
- **Email*** - Contact email
- **Phone** - Contact number

**Description**:
- **About** - Partner description (displayed in directory)
- **Collaboration Focus** - Areas of interest (optional)

**Visual Assets**:
- **Logo** - Partner logo upload (PNG, JPG, SVG, max 2MB)
- **Banner** - Event banner image (optional, for Diamond/Platinum)

</div>

<div class="step" data-step="4">

**Save**

Click **Save** to create the partner record.

Success message: "Partner 'Sustainable Materials AG' created successfully"
</div>

## Partner Directory

<span class="feature-status implemented">Implemented</span>

The partner directory showcases all active partners, sorted by tier.

### Directory Display

```
BATbern Partners
────────────────────────────────────────

💎 Diamond Partners
┌──────────────────────────────────┐
│  [Logo]  Sustainable Materials AG │
│  Building materials innovation    │
│  🔗 sustainablematerials.ch       │
└──────────────────────────────────┘

🥇 Gold Partners
┌────────────────┐  ┌────────────────┐
│ [Logo] Tech AG │  │ [Logo] Plan AG │
│ Architecture   │  │ Software       │
└────────────────┘  └────────────────┘
```

### Public Access

The partner directory is **publicly accessible** (no login required) at:
- **Production**: https://www.batbern.ch/partners
- **Staging**: https://staging.batbern.ch/partners
- **Local**: http://localhost:3000/partners

Partners are displayed in tier order with logos and descriptions.

## Partner Contacts

<span class="feature-status implemented">Implemented</span>

Each partner can have **multiple contacts** for different purposes.

### Contact Types

**Primary Contact**:
- Main liaison for partnership matters
- Receives partnership updates
- Coordinates meetings and logistics

**Marketing Contact**:
- Handles promotional materials
- Provides logos and content
- Approves marketing communications

**Technical Contact**:
- Coordinates booth setup
- Handles technical requirements
- Manages equipment and presentations

### Managing Contacts

**Add Contact**:
```
[+ Add Contact]

Contact Name: Hans Müller
Email: hans.mueller@partner.ch
Phone: +41 31 123 45 67
Type: [▼ Marketing Contact]

[Save]
```

**Edit/Delete**:
- Click **📝 Edit** to modify contact details
- Click **🗑️ Delete** to remove contact

## Meeting Coordination

<span class="feature-status implemented">Implemented</span>

Organizers can schedule partner meetings during events.

### Creating a Meeting

<div class="step" data-step="1">

**Navigate to Partner Details**

Open the partner record you want to meet with.
</div>

<div class="step" data-step="2">

**Click "Schedule Meeting"**

In the partner detail view, click **Schedule Meeting** button.
</div>

<div class="step" data-step="3">

**Fill Meeting Details**

**Meeting Information**:
- **Purpose** - Meeting objective (e.g., "Discuss sponsorship renewal")
- **Date & Time** - Meeting schedule
- **Duration** - Expected length (15, 30, 60 minutes)
- **Location** - Room or booth number

**Attendees**:
- **Partner Contacts** - Select from partner contacts
- **Organizers** - Select BATbern organizers

**Agenda** (optional):
- Discussion topics
- Desired outcomes

</div>

<div class="step" data-step="4">

**Send Invitations**

Click **Send Meeting Invitations**.

Calendar invitations sent to all attendees via email (ICS format).
</div>

### Meeting Status

Track meeting status throughout the event:

| Status | Description |
|--------|-------------|
| **SCHEDULED** | Meeting confirmed, invitations sent |
| **CONFIRMED** | All parties confirmed attendance |
| **IN_PROGRESS** | Meeting currently happening |
| **COMPLETED** | Meeting finished |
| **CANCELLED** | Meeting cancelled |
| **NO_SHOW** | Partner didn't attend |

### Meeting Notes

<span class="feature-status planned">Planned</span>

After meetings, organizers can add notes:

```
Meeting Notes (2025-03-15)
──────────────────────────────
Attendees: Hans Müller (Partner), Anna Schmidt (Organizer)
Duration: 30 minutes

Discussion:
- Reviewed 2025 partnership benefits
- Discussed booth location preferences
- Agreed on speaking slot (20 minutes)

Action Items:
- [ ] Send booth layout diagram (Anna)
- [ ] Provide speaker bio and headshot (Hans)
- [ ] Schedule follow-up in 2 weeks

Next Steps:
- Confirm speaking slot by March 20
- Finalize booth setup by March 25
```

## Engagement Metrics

<span class="feature-status planned">Planned</span>

Track partner engagement over time:

### Metrics Dashboard

```
Sustainable Materials AG
📊 Engagement Metrics
────────────────────────────────────
Events Sponsored: 8
Total Contribution: CHF 50,000
Speaking Sessions: 12
Booth Attendance: ~500 visitors/year
Partnership Duration: 3 years
```

### Historical Participation

```
Partnership History
────────────────────────────────────
2025: Diamond - Speaking + Booth
2024: Platinum - Speaking + Booth
2023: Gold - Booth only
2022: Silver - Logo placement
```

## Partner Benefits Tracking

<span class="feature-status planned">Planned</span>

Track delivery of tier benefits:

```
BATbern 2025 - Benefits Checklist
────────────────────────────────────
✅ Logo on website
✅ Logo in event program
✅ Booth reserved (Location: A12)
✅ Speaking slot scheduled (March 15, 14:30)
⏳ Attendee list sent (post-event)
⏳ Social media mentions (ongoing)
```

## Editing a Partner

<div class="step" data-step="1">

**Find the Partner**

Search or browse the partner list.
</div>

<div class="step" data-step="2">

**Click Edit**

Click **📝 Edit** icon in partner row.
</div>

<div class="step" data-step="3">

**Modify Fields**

Update partner information. Note:
- Tier can be changed (upgrade/downgrade)
- Logo can be replaced
- Contacts can be added/removed

</div>

<div class="step" data-step="4">

**Save Changes**

Click **Save Changes** to persist updates.
</div>

## Deleting a Partner

<div class="alert warning">
⚠️ <strong>Warning:</strong> Deleting a partner removes them from the directory but preserves historical event participation records.
</div>

<div class="step" data-step="1">

**Click Delete**

Click **🗑️ Delete** icon in partner row.
</div>

<div class="step" data-step="2">

**Confirm Deletion**

```
Delete Partner?
────────────────────────────────────
Delete "Sustainable Materials AG"?

This will:
- Remove from partner directory
- Cancel scheduled meetings
- Preserve historical participation

Historical records retained:
- 8 past event sponsorships
- 12 speaking sessions delivered

[Cancel]           [Delete]
```

Click **Delete** to confirm.
</div>

## Searching Partners

### Quick Search

```
🔍 [Sustainable]
```

Searches across:
- Partner name
- Industry
- Contact names

### Filter by Tier

```
tier:DIAMOND
```

```
tier:GOLD,PLATINUM
```

## Export & Report

<span class="feature-status planned">Planned</span>

### Export Partner List

Export partner directory to CSV:

```
[📥 Export]
   ├─ Export All Partners (42)
   ├─ Export by Tier (Diamond: 3)
   └─ Export with Contacts (detailed)
```

### Partnership Report

Generate partnership summary:

```
[📊 Generate Report]

Partnership Report - 2025
─────────────────────────────────
Total Partners: 42
├─ Diamond: 3
├─ Platinum: 6
├─ Gold: 12
├─ Silver: 15
└─ Bronze: 6

Total Contribution: CHF 250,000
Speaking Slots: 18
Booths: 21
```

## Common Issues

### "Partner tier changed but benefits not updated"

**Problem**: Tier upgrade/downgrade doesn't auto-update benefit checkboxes.

**Solution**:
- Manually review and update benefit checklist
- Contact partner to communicate new benefits
- Update event materials with new logo placement

### "Partner logo not displaying in directory"

**Problem**: Logo uploaded but not visible publicly.

**Solution**:
- Verify logo uploaded successfully (check S3)
- Confirm partner record is active (not draft)
- Clear browser cache
- Check file format (PNG, JPG, SVG only)

## Related Topics

- [Event Management →](events.md) - Link partners to events
- [File Uploads →](../features/file-uploads.md) - Upload partner logos
- [Company Management →](companies.md) - Similar organization management

## API Reference

### Endpoints

```
POST   /api/partners               Create partner
GET    /api/partners               List partners (paginated)
GET    /api/partners/{id}          Get partner by ID
PUT    /api/partners/{id}          Update partner
DELETE /api/partners/{id}          Delete partner
POST   /api/partners/{id}/contacts Add contact
POST   /api/partners/{id}/meetings Schedule meeting
POST   /api/partners/{id}/logo     Request presigned URL for logo upload
```

See [API Documentation](../../api/) for complete specifications.
