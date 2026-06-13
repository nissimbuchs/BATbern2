# User Management

> Manage organizers, speakers, attendees, partners, and admins

<span class="feature-status implemented">Implemented</span>

> **Last Updated:** 2026-06-13

## Overview

Users are individuals with BATbern accounts. Each user has one or more **roles** that determine their permissions and platform capabilities. Multi-role users get a single session with grouped navigation (section dividers per role) — no separate logins per role.

Users sign in with **email + password** or **"Continue with Google"** (see [Continue with Google (SSO)](#continue-with-google-sso) below).

## User Roles

BATbern supports 5 roles:

### 🔵 Organizer

<span style="background: #2C5F7C; color: white; padding: 4px 8px; border-radius: 12px; font-size: 0.75rem; font-weight: 500;">ORGANIZER</span>

**Event planning and coordination** - Manage all aspects of conference organization.

**Permissions**:
- ✅ Create and manage events
- ✅ CRUD all entities (companies, users, events, partners, speakers)
- ✅ Manage event workflow (9 event states, per-speaker workflow, tasks)
- ✅ Coordinate speaker outreach
- ✅ Manage partner relationships
- ✅ View analytics and reports
- ✅ Full system access

**Typical Users**: BATbern event coordinators, conference planners

**You are an Organizer** - this documentation is written for your role.

### 🟡 Speaker

<span style="background: #4A90B8; color: white; padding: 4px 8px; border-radius: 12px; font-size: 0.75rem; font-weight: 500;">SPEAKER</span>

**Speaker profile management** - Manage personal speaker profile and session assignments.

**Permissions**:
- ✅ View and edit own speaker profile
- ✅ Submit session content (≤1000 characters)
- ✅ View assigned sessions
- ✅ Update availability
- ❌ Cannot access entity management
- ❌ Cannot access workflow

**Typical Users**: Architecture professionals invited to speak at BATbern events

### 🟢 Attendee

<span style="background: #3498DB; color: white; padding: 4px 8px; border-radius: 12px; font-size: 0.75rem; font-weight: 500;">ATTENDEE</span>

**Event registration and attendance** - Register for events and access materials.

**Permissions**:
- ✅ Register for events
- ✅ View event schedules
- ✅ View public partner directory
- ✅ Access session materials
- ❌ Cannot create or edit entities
- ❌ Cannot access organizer tools

**Typical Users**: Architects attending BATbern conferences

### 🟣 Partner

<span style="background: #9B59B6; color: white; padding: 4px 8px; border-radius: 12px; font-size: 0.75rem; font-weight: 500;">PARTNER</span>

**Partner portal access** - View sponsorship analytics, participate in topic selection, and receive meeting coordination.

**Permissions**:
- ✅ View attendance analytics for own company (event participation, cost-per-attendee)
- ✅ Export attendance data to XLSX
- ✅ Suggest topics for future events
- ✅ Vote on proposed topics (one vote per topic)
- ✅ Receive meeting calendar invites (.ics) for Spring/Autumn partner meetings
- ❌ Cannot view other partners' data
- ❌ Cannot access entity management or organizer tools
- ❌ Cannot access event workflow

**Typical Users**: Sponsoring companies and collaborating organizations (Diamond through Bronze tier)

### ⚫ Admin

<span style="background: #2C3E50; color: white; padding: 4px 8px; border-radius: 12px; font-size: 0.75rem; font-weight: 500;">ADMIN</span>

**Platform administration** - Full system access, including administrative configuration not exposed to regular organizers.

**Permissions**:
- ✅ All Organizer permissions
- ✅ Platform configuration and administrative tooling
- ✅ Manage other users' roles

**Typical Users**: BATbern platform administrators

## Continue with Google (SSO)

<span class="feature-status implemented">Implemented</span> — Epic 12

BATbern supports federated login via **"Continue with Google"** at `auth.batbern.ch`. SSO is live in production.

### How it works

- **Account linking** — when a user signs in with Google using an email that already exists as a BATbern account, the federated identity is transparently linked to the existing profile (no duplicate account is created).
- **JIT (just-in-time) provisioning** — a brand-new Google sign-in creates a profile on first login with the default `ATTENDEE` role; organizers can promote later.
- **Terms-of-Service consent gate** — first-time federated users complete a consent step before reaching the app.
- **Federated onboarding completion** — first login collects consent, company, and newsletter preference.
- **Google avatar import** — the user's Google profile photo is imported as their avatar.
- **Runtime kill-switch** — SSO can be disabled at runtime via the `FEATURES_SSO_ENABLED` flag without a redeploy.

<div class="alert info">
ℹ️ <strong>Note:</strong> Apple / generic OIDC providers are not yet available (deferred — Story 12.10). Google is the only federated provider live today.
</div>

## Creating a User

<div class="alert info">
ℹ️ <strong>Note:</strong> User creation is typically done through the registration flow. Organizers can also create users manually for administrative purposes.
</div>

<div class="step" data-step="1">

**Navigate to Users**

Click **👥 Users** in the left sidebar.
</div>

<div class="step" data-step="2">

**Click "Create New User"**

Click the **+ Create New User** button (top-right).
</div>

<div class="step" data-step="3">

**Fill User Details**

Complete the user creation form:

**Personal Information**:
- **First Name*** - Given name
- **Last Name*** - Family name
- **Email*** - Unique email address (used for login)
- **Phone** - Contact number (optional)

**Profile Information**:
- **Role*** - Select one: Organizer, Speaker, Attendee, Partner, Admin
- **Company** - Select from autocomplete dropdown (optional)
- **Job Title** - Position at company (optional)
- **Bio** - Short biography (for speakers)
- **Cognito User ID** - Automatically populated from AWS Cognito authentication (read-only)

**Account Settings**:
- **Send Welcome Email** - Check to send invitation email
- **Temporary Password** - Auto-generated (sent via email)

</div>

<div class="step" data-step="4">

**Save**

Click **Save** to create the user account.

User receives welcome email with login instructions and temporary password.
</div>

## Role Management

### Promoting Users

<span class="feature-status implemented">Implemented</span>

Increase a user's role level:

**Allowed Promotions** (by Organizers):
- ✅ Attendee → Speaker
- ✅ Attendee → Organizer
- ✅ Attendee → Partner
- ✅ Speaker → Organizer
- ✅ Partner → Organizer

<div class="step" data-step="1">

**Edit User**

Click **📝 Edit** on the user row.
</div>

<div class="step" data-step="2">

**Change Role**

Select new role from **Role** dropdown.
</div>

<div class="step" data-step="3">

**Confirm Promotion**

A confirmation dialog appears:

```
Promote User?
────────────────────────────────────
Promote "Hans Müller" from SPEAKER to ORGANIZER?

This will grant additional permissions:
- Create and manage events
- Manage event workflow system
- Manage all entities

[Cancel]           [Promote]
```

Click **Promote** to confirm.
</div>

<div class="step" data-step="4">

**Notification**

User receives email notification of role change.
</div>

### Demoting Users

Decrease a user's role level:

**Allowed Demotions** (by Organizers):
- ✅ Organizer → Speaker
- ✅ Organizer → Attendee
- ✅ Organizer → Partner
- ✅ Speaker → Attendee
- ✅ Partner → Attendee

Process is the same as promotion, with appropriate confirmation dialog.

### Role Restrictions

<div class="alert info">
ℹ️ <strong>Note:</strong> All role changes are performed by Organizers who have full system access.
</div>

## User Sync from Cognito

<span class="feature-status implemented">Implemented</span>

BATbern uses **AWS Cognito** for authentication but stores user profiles in **PostgreSQL** for application data.

### How Sync Works

**Automatic Sync** (production):
1. User logs in via Cognito (email/password or "Continue with Google")
2. JWT token contains user attributes
3. API Gateway extracts user info from token
4. Backend creates/updates the user record in PostgreSQL (JIT provisioning for first-time federated logins — default role `ATTENDEE`)
5. User can now interact with the application

**Manual Sync** (local development — mirrors staging users into the local DB):
```bash
# Sync all users from staging Cognito to local PostgreSQL
./scripts/dev/sync-users-from-cognito.sh

# Output:
# Syncing users from Cognito...
# ✅ Created: organizer@batbern.ch (ORGANIZER)
# ✅ Updated: speaker@batbern.ch (SPEAKER)
# ✅ Synced 42 users
```

### Sync Behavior

- **New Users**: Created in PostgreSQL with Cognito ID
- **Existing Users**: Profile updated with latest Cognito data
- **Role Assignment**: Default role is ATTENDEE (can be promoted)
- **Deleted Cognito Users**: Flagged but not auto-deleted (data retention)

## Additional Email Addresses

<span class="feature-status implemented">Implemented</span> — Story 10-32

Any user (primarily organizers) can register **additional email addresses** on their profile, on the Account sub-tab of the user-settings page. These are authorised aliases that work in two directions:

- **Receive-at**: mail BATbern would have sent to the user's primary email also reaches every additional address — e.g. the `ok@batbern.ch` organizer fan-out, the `partner@batbern.ch` partner fan-out, `batbern{N}@…` registrant fan-out, and event-registration confirmations.
- **Send-as**: mail the user sends to BATbern from any registered additional address is treated as coming from an authorised sender. For organizers this means the email-forwarder no longer silently drops a message sent from a legacy/shared/personal mailbox (e.g. a Hostpoint shared inbox) instead of the primary address.

**Key details**:
- The **primary email stays the login identifier** — adding aliases does NOT change the Cognito-linked primary address.
- Each address can carry an optional free-text **label** (e.g. "Hostpoint shared") as a reminder.
- Up to **5** additional addresses per user.
- Addresses must be **globally unique** across all users' primary and additional emails; a collision is rejected.

<div class="alert info">
ℹ️ <strong>Note:</strong> v1 does not verify ownership of an added address (no confirmation email yet). Newsletter delivery to additional addresses is also out of scope (the newsletter keeps its own subscriber list).
</div>

## GDPR Compliance

<span class="feature-status implemented">Implemented</span>

BATbern complies with the General Data Protection Regulation (GDPR) for user privacy.

### User Rights

#### Right to Access

Users can view all personal data held by the platform:

```
[📄 Download My Data]
```

Downloads JSON file containing:
- Profile information
- Event registrations
- Session assignments (for speakers)
- Activity logs (anonymized IDs for other users)

#### Right to Rectification

Users can update incorrect or incomplete data:

- ✅ Edit profile at any time
- ✅ Correct email, name, phone, bio
- ✅ Update company affiliation
- ✅ Changes reflected immediately

#### Right to Erasure ("Right to be Forgotten")

Users can request account deletion:

<div class="step" data-step="1">

**User Requests Deletion**

User clicks **Delete My Account** in profile settings.
</div>

<div class="step" data-step="2">

**Confirmation**

User must confirm with password and acknowledge data loss.
</div>

<div class="step" data-step="3">

**Grace Period**

30-day grace period allows account recovery.
</div>

<div class="step" data-step="4">

**Permanent Deletion**

After 30 days:
- User profile deleted
- Personal data erased
- Event registrations anonymized
- Cognito account deleted
- Audit logs retained (anonymized)

</div>

### Data Retention

| Data Type | Retention Period | Notes |
|-----------|------------------|-------|
| **Active Users** | Indefinite | While account active |
| **Deleted Users** | 30 days | Grace period for recovery |
| **Personal Data** | 30 days post-deletion | Then permanently erased |
| **Audit Logs** | 7 years | Anonymized after user deletion |
| **Event Participation** | Indefinite | Anonymized (speaker became "Anonymous Speaker") |

### Consent Management

<span class="feature-status planned">Planned</span>

Future releases will include:
- **Cookie Consent**: Banner with accept/reject options
- **Marketing Communications**: Opt-in for newsletters
- **Data Processing**: Granular consent for different data uses

### Data Processing

**Legal Basis**:
- **Contract Performance**: Processing necessary for conference registration and participation
- **Legitimate Interest**: Event planning and coordination
- **Consent**: Marketing communications (when implemented)

**Data Processors**:
- AWS (hosting, storage)
- AWS SES (email delivery)
- AWS Cognito (authentication)

## Searching Users

<span class="feature-status implemented">Implemented</span>

### Quick Search

```
🔍 [Hans Müller]
```

Searches across:
- First name
- Last name
- Email address
- Company name

### Advanced Filters

**Filter by Role**:
```
role:ORGANIZER
```

**Filter by Company**:
```
company:Müller Architekten AG
```

**Multiple Filters**:
```
role:SPEAKER
company:Müller Architekten AG
verified:true
```

## Editing a User

<div class="step" data-step="1">

**Find the User**

Search or browse the user list.
</div>

<div class="step" data-step="2">

**Click Edit**

Click **📝 Edit** icon in user row.
</div>

<div class="step" data-step="3">

**Modify Fields**

Update profile information. Note:
- Email cannot be changed (login identifier)
- Role changes require confirmation
- Company can be changed

</div>

<div class="step" data-step="4">

**Save Changes**

Click **Save Changes** to persist updates.
</div>

## Deleting a User

<div class="alert error">
❌ <strong>Caution:</strong> User deletion is permanent after 30-day grace period. This action cannot be undone.
</div>

<div class="step" data-step="1">

**Click Delete**

Click **🗑️ Delete** icon in user row.
</div>

<div class="step" data-step="2">

**Confirm Deletion**

```
Delete User?
────────────────────────────────────
Delete "Hans Müller" (organizer@batbern.ch)?

This will:
- Deactivate the account immediately
- Delete Cognito login access
- Erase personal data after 30 days
- Anonymize event participation history

[Cancel]           [Delete]
```

Click **Delete** to confirm.
</div>

<div class="step" data-step="3">

**Cascade Effects**

After deletion:
- User cannot log in
- Event registrations preserved (anonymized)
- Speaker sessions show "Anonymous Speaker"
- Audit logs retained (anonymized)

</div>

## User Statistics

<span class="feature-status planned">Planned</span>

User list view shows statistics:

```
Total Users: 237
├─ Organizers: 8
├─ Speakers: 45
├─ Attendees: 172
└─ Partners: 12
```

User detail view shows activity:
- **Events Organized**: 5 (for Organizers)
- **Sessions Presented**: 12 (for Speakers)
- **Events Attended**: 8 (for Attendees)
- **Last Login**: 2025-03-15 14:23 UTC

## Password Management

### Reset Password (by Organizer)

<span class="feature-status implemented">Implemented</span>

Organizers can trigger password reset for users:

<div class="step" data-step="1">

**Click "Reset Password"**

In user detail view, click **Reset Password** button.
</div>

<div class="step" data-step="2">

**Cognito Sends Email**

AWS Cognito sends password reset email to user with verification code.
</div>

<div class="step" data-step="3">

**User Resets Password**

User follows email instructions to set new password.
</div>

See [Login & Authentication](../getting-started/login.md) for user-facing password reset flow.

## Export & Import

<span class="feature-status planned">Planned</span>

### Export Users

Export user directory to CSV:

```
[📥 Export]
   ├─ Export All Users (237)
   ├─ Export by Role (SPEAKER: 45)
   └─ Export Selected (5 users)
```

CSV columns: email, first_name, last_name, role, company, phone

### Import Users

Bulk import from CSV:

```
[📤 Import Users]

Upload CSV with columns:
- email, first_name, last_name, role, company

Behavior:
- Existing users updated
- New users created in Cognito + PostgreSQL
- Welcome emails sent
- Validation errors reported
```

## Common Issues

### "Email already exists"

**Problem**: Email address already registered.

**Solution**:
- Each email can only be used once
- Check if user exists (search by email)
- User may need to use a different email

### "Role change not allowed"

**Problem**: System prevents certain role changes.

**Solution**:
- Verify you have Organizer permissions
- Some role changes may have business logic restrictions
- Check user's current assignments (e.g., active speaker cannot be demoted during event)

### "User sync failed"

**Problem**: User exists in Cognito but not in PostgreSQL.

**Solution**:
- Run manual sync: `./scripts/dev/sync-users-from-cognito.sh`
- Check database connectivity
- Verify Cognito credentials

## Related Topics

- [Login & Authentication →](../getting-started/login.md) - How users access the platform
- [Company Management →](companies.md) - Link users to companies
- [Speaker Management →](speakers.md) - Speaker-specific features

## API Reference

### Endpoints

```
POST   /api/users                  Create user
GET    /api/users                  List users (paginated)
GET    /api/users/{id}             Get user by ID
PUT    /api/users/{id}             Update user
DELETE /api/users/{id}             Delete user (soft delete)
POST   /api/users/{id}/promote     Promote user role
POST   /api/users/{id}/demote      Demote user role
GET    /api/users/{id}/data-export Export user's personal data (GDPR)
GET    /api/users/me               Current user (includes additionalEmails)
POST   /api/users/me/additional-emails        Add an additional email alias
DELETE /api/users/me/additional-emails/{email} Remove an additional email alias
```

See [API Documentation](../../api/) for complete specifications.
