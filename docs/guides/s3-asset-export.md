# S3 Asset Export & Migration Guide

All media assets (company logos, speaker portraits, presentation materials, event images)
are stored in the AWS S3 content bucket. This guide explains how to export everything
for migration or backup purposes.

## Buckets

| Environment | Bucket                       |
|-------------|------------------------------|
| Staging     | `batbern-content-staging`    |
| Production  | `batbern-content-production` |
| Region      | `eu-central-1`               |

## Folder Structure

The bucket is organised into human-readable virtual folders:

```
batbern-content-production/
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

> **Note on filenames**: Individual filenames contain a UUID suffix (e.g. `logo-a2b3c4d5.png`),
> but the folder path always identifies the entity (company name, username, event code, session slug).
> The folder structure is sufficient to locate any asset manually.

## Bulk Export with AWS CLI

### 1. Prerequisites

- [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) installed
- IAM credentials with `s3:GetObject` + `s3:ListBucket` on the content bucket

### 2. Configure credentials (one-time)

```bash
aws configure --profile batbern-prod
# AWS Access Key ID: <your key>
# AWS Secret Access Key: <your secret>
# Default region: eu-central-1
# Default output format: json
```

### 3. Mirror the full bucket locally

```bash
aws s3 sync s3://batbern-content-production ./batbern-assets --profile batbern-prod
```

This downloads everything and preserves the folder structure under `./batbern-assets/`.
Re-running the command is safe and incremental — only new or changed files are downloaded.

For staging:

```bash
aws s3 sync s3://batbern-content-staging ./batbern-assets-staging --profile batbern-staging
```

### 4. Browse without downloading

```bash
# List all objects recursively
aws s3 ls s3://batbern-content-production --recursive --profile batbern-prod

# List a specific folder
aws s3 ls s3://batbern-content-production/logos/ --profile batbern-prod
```

## GUI Alternative: Cyberduck

[Cyberduck](https://cyberduck.io) (macOS / Windows, free) provides a Finder-like interface
for S3. Connect using your AWS credentials, navigate to the bucket, and drag-and-drop
download files or entire folders.
