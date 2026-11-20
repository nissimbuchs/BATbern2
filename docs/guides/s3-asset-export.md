# S3 Asset Export & Migration Guide

All media assets (company logos, speaker portraits, presentation materials, event images)
are stored in the AWS S3 content bucket. This guide explains how to export everything
for migration or backup purposes.

## Buckets

| Environment | Bucket                       |
|-------------|------------------------------|
| Production  | `batbern-content-staging` (account 188701360969, profile `batbern-staging`) |
| Region      | `eu-central-1`               |

The bucket name follows `batbern-content-${envName}` (`infrastructure/lib/stacks/storage-stack.ts`);
production runs with `envName` `staging`. There is no `batbern-content-production` bucket in use: the
former production account is decommissioned.

## Folder Structure

The bucket is organised into human-readable virtual folders:

```
batbern-content-staging/
├── logos/
│   └── {year}/{ENTITY_TYPE}/{entity-name}/                          ← company logos
├── profile-pictures/
│   └── {year}/{username}/                                           ← user profile photos
├── speaker-profiles/
│   └── {year}/{username}/                                           ← speaker portal photos
├── materials/
│   └── {year}/events/{eventCode}/sessions/{sessionSlug}/            ← presentation files
└── events/
    ├── {eventCode}/photos/                                          ← event gallery
    └── {eventCode}/teaser/                                          ← event banner images
```

> **Uploads since Story 17.11** use `logos/companies/{companyName}/logo-{uploadId}.{ext}` and
> `logos/events/{eventCode}/theme-{uploadId}.{ext}` (temporary uploads under `logos/temp/`). Older
> objects keep the layout above; which share of the bucket uses which was not checked.

> **Note on filenames**: Individual filenames contain a UUID suffix (e.g. `logo-a2b3c4d5.png`),
> but the folder path always identifies the entity (company name, username, event code, session slug).
> The folder structure is sufficient to locate any asset manually.

## Bulk Export with AWS CLI

### 1. Prerequisites

- [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) installed
- IAM credentials with `s3:GetObject` + `s3:ListBucket` on the content bucket

### 2. Configure credentials (one-time)

```bash
aws configure --profile batbern-staging
# Default region: eu-central-1
```

### 3. Mirror the full bucket locally

```bash
aws s3 sync s3://batbern-content-staging ./batbern-assets --profile batbern-staging
```

This downloads everything and preserves the folder structure under `./batbern-assets/`.
Re-running the command is safe and incremental — only new or changed files are downloaded.

### 4. Browse without downloading

```bash
# List all objects recursively
aws s3 ls s3://batbern-content-staging --recursive --profile batbern-staging

# List a specific folder
aws s3 ls s3://batbern-content-staging/logos/ --profile batbern-staging
```

## GUI Alternative: Cyberduck

[Cyberduck](https://cyberduck.io) (macOS / Windows, free) provides a Finder-like interface
for S3. Connect using your AWS credentials, navigate to the bucket, and drag-and-drop
download files or entire folders.
