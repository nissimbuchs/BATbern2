# Local Logo Import Workflow

This directory contains scripts to enable batch importing of companies with local logo files.

## Problem

The `companies.json` file contains references to local logo files via `logoFilePath`, but the batch import feature only supports external URLs (`logoUrl`). The browser cannot access arbitrary local file paths due to security restrictions.

## Solution

We run a local HTTP server to serve the logo files, then update the JSON to reference `localhost` URLs instead of file paths.

## Scripts

### 1. `serve-logos.js`
Simple HTTP server that serves logo files from the archiv directories.

- **Port**: 8888
- **Directories**:
  - `apps/BATspa-old/src/archiv/42/`
  - `apps/BATspa-old/src/archiv/43/`
- **Features**: CORS enabled, automatic MIME type detection, 404 handling

### 2. `update-logo-urls.js`
Updates `companies.json` to add `logoUrl` fields pointing to localhost.

- **Input**: `apps/BATspa-old/src/api/companies.json`
- **Output**: `apps/BATspa-old/src/api/companies-with-local-urls.json`
- **Logic**:
  - For each company with `logoFilePath`
  - Extract filename (e.g., `sbb.jpg`)
  - Add `logoUrl: "http://localhost:8888/sbb.jpg"`
  - Verify file exists before adding URL

## Usage Workflow

### Step 1: Update JSON file

```bash
node scripts/dev/update-logo-urls.js
```

This creates `apps/BATspa-old/src/api/companies-with-local-urls.json` with localhost URLs.

**Expected Output:**
```
============================================================
Summary
============================================================
Total companies:           56
Already have logoUrl:      8 (external URLs)
Have logoFilePath:         43 (local files)
  ✓ File exists:           12
  ✗ File missing:          31
Updated with localhost:    12
============================================================
```

### Step 2: Start logo server

In a separate terminal:

```bash
node scripts/dev/serve-logos.js
```

**Expected Output:**
```
============================================================
BATbern Logo Server
============================================================
Server running at http://localhost:8888/
```

Keep this terminal open while importing.

### Step 3: Batch import companies

1. Open the web frontend at http://localhost:3000
2. Navigate to Companies → Batch Import
3. Upload `apps/BATspa-old/src/api/companies-with-local-urls.json`
4. Review the preview (logos should load from localhost:8888)
5. Click "Import Companies"

The batch import will:
- Fetch logos from `http://localhost:8888/<filename>`
- Upload to S3 via presigned URLs (ADR-002)
- Create companies with logos attached

### Step 4: Stop the server

When import is complete, stop the logo server with `Ctrl+C`.

## Testing the Server

### Manual test in browser
Visit http://localhost:8888/ to see the server status page.

### Test a specific logo
Visit http://localhost:8888/sbb.jpg to view a logo file.

### Test with curl
```bash
curl -I http://localhost:8888/sbb.jpg
```

Should return:
```
HTTP/1.1 200 OK
Content-Type: image/jpeg
Access-Control-Allow-Origin: *
```

## Troubleshooting

### "File not found" errors
The script reports which files are referenced in JSON but don't exist in the archiv directories. This is expected - only 12 out of 43 local file references actually exist on disk.

### Port already in use
If port 8888 is already in use, you can modify the `PORT` constant in `serve-logos.js`.

### CORS issues
The server includes CORS headers (`Access-Control-Allow-Origin: *`) to allow the frontend to fetch logos. If you still see CORS errors, verify the server is running.

### Logo not displaying in preview
Check the browser console for errors. The logo should load from `http://localhost:8888/<filename>`.

## Architecture Notes

This solution follows the **backend proxy pattern** already implemented for external URLs:

1. **Frontend** requests logo from localhost:8888
2. **Backend** proxy endpoint (`/logos/fetch-from-url`) fetches the image
3. **Backend** validates content-type and size
4. **Frontend** receives image blob (no CORS issues)
5. **3-step upload** (presigned URL → S3 → confirm → company creation)

The local server eliminates the need for:
- Browser file system access
- Complex file upload UI
- Modifying batch import logic

## Files Created

- `scripts/dev/serve-logos.js` - HTTP server for logo files
- `scripts/dev/update-logo-urls.js` - JSON URL updater script
- `scripts/dev/README-logo-import.md` - This documentation
- `apps/BATspa-old/src/api/companies-with-local-urls.json` - Updated JSON (generated)

## Cleanup

The `companies-with-local-urls.json` file is temporary and can be deleted after import. The original `companies.json` is never modified.

---

# Staging Environment Workflow

For using the batch import on the **staging environment** (`https://staging.batbern.ch`), we upload logos to the staging CDN instead of running a local server.

## Prerequisites

- AWS CLI installed and configured
- AWS profile `batbern-staging` configured with access to S3
- Access to `batbern-content-staging` S3 bucket
- Node.js installed

## Staging Scripts

### 1. `upload-company-logos.sh`
Uploads logo files from local archiv directories to staging S3.

- **Location**: `scripts/staging/upload-company-logos.sh`
- **S3 Bucket**: `batbern-content-staging`
- **S3 Path**: `import-data/company-logos/<filename>`
- **CDN URL**: `https://cdn.staging.batbern.ch/import-data/company-logos/<filename>`
- **Features**: Automatic Content-Type detection, duplicate detection, dry-run mode

### 2. `generate-staging-companies-json.js`
Transforms `companies.json` to use staging CDN URLs.

- **Location**: `scripts/staging/generate-staging-companies-json.js`
- **Input**: `apps/BATspa-old/src/api/companies.json`
- **Output**: `apps/BATspa-old/src/api/companies-with-staging-urls.json`
- **Logic**:
  - For each company with `logoFilePath`: Extracts filename and sets `logoUrl` to CDN URL
  - Companies with existing `logoUrl` (external URLs) are kept unchanged
  - Preserves `logoFilePath` for reference

## Staging Usage Workflow

### Step 1: Upload logos to staging S3

```bash
./scripts/staging/upload-company-logos.sh
```

This uploads logo files from local archiv directories to S3.

**Expected Output:**
```
============================================================
BATbern - Upload Company Logos to Staging S3
============================================================
✓ AWS credentials valid and bucket accessible
✓ Found 43 logo files

Uploading logo files to S3...
  ✓ sbb.jpg → s3://batbern-content-staging/import-data/company-logos/sbb.jpg
  ✓ swisscom.jpg → s3://batbern-content-staging/import-data/company-logos/swisscom.jpg
  ...

============================================================
Summary
============================================================
Total logo files found:    43
Successfully uploaded:     43
Already existed (skipped): 0
Failed:                    0
============================================================
```

**Optional dry-run mode:**
```bash
./scripts/staging/upload-company-logos.sh --dry-run
```

### Step 2: Generate staging JSON

```bash
node scripts/staging/generate-staging-companies-json.js
```

This creates `companies-with-staging-urls.json` with CDN URLs.

**Expected Output:**
```
============================================================
BATbern Staging Companies JSON Generator
============================================================
Reading: apps/BATspa-old/src/api/companies.json
Loaded 56 companies

  [0] sbb: sbb.jpg → https://cdn.staging.batbern.ch/import-data/company-logos/sbb.jpg
  [1] swisscom: swisscom.jpg → https://cdn.staging.batbern.ch/import-data/company-logos/swisscom.jpg
  ...

============================================================
Summary
============================================================
Total companies:           56
Already have logoUrl:      8 (kept unchanged)
Have logoFilePath:         43
  ✓ Transformed to CDN:    43
  ✗ Errors:                0
No logo data:              5
============================================================
```

### Step 3: Batch import on staging

1. Open **https://staging.batbern.ch**
2. Navigate to **Companies → Batch Import**
3. Upload `apps/BATspa-old/src/api/companies-with-staging-urls.json`
4. Review the preview (logos should load from CDN)
5. Click **"Import Companies"**

The batch import will:
- Fetch logos from `https://cdn.staging.batbern.ch/import-data/company-logos/<filename>`
- Upload to final S3 location via backend (server-side)
- Create companies with logos attached
- Generate final CloudFront URLs for each company

### Step 4: Verify import

Check that companies were created successfully:
- Navigate to Companies list on staging
- Verify logos are displayed correctly
- Check that company count increased as expected

## Verification Commands

**Test CDN access:**
```bash
curl -I https://cdn.staging.batbern.ch/import-data/company-logos/sbb.jpg
```

Should return:
```
HTTP/2 200
Content-Type: image/jpeg
Cache-Control: max-age=31536000, public
```

**List uploaded logos:**
```bash
aws s3 ls s3://batbern-content-staging/import-data/company-logos/ --profile batbern-staging
```

## Troubleshooting

### AWS credentials error
If you see "Cannot access S3 bucket", ensure:
```bash
aws configure list --profile batbern-staging
```

### Logos not accessible via CDN
- Check S3 upload was successful
- Verify file has `public-read` ACL
- Check CloudFront distribution is working
- Wait 1-2 minutes for CDN propagation

### Batch import fails
- Verify logos are accessible (use curl command above)
- Check browser console for CORS errors
- Ensure staging backend is running
- Check backend logs for logo fetch errors

## Cleanup (Optional)

After successful import, you can optionally delete the temporary logos from S3:

```bash
aws s3 rm s3://batbern-content-staging/import-data/company-logos/ --recursive --profile batbern-staging
```

**Note**: Storage cost is minimal (~3MB, <$0.01/month), so it's safe to leave them for future imports.

The generated JSON file can also be deleted:
```bash
rm apps/BATspa-old/src/api/companies-with-staging-urls.json
```

## Architecture Notes

The staging workflow follows the same **backend proxy pattern** as local development:

1. **Frontend** requests logo from CDN URL
2. **Backend** `/logos/upload-from-url` endpoint fetches the image
3. **Backend** validates content-type and size
4. **Backend** uploads directly to S3 (avoids binary corruption)
5. **3-step process**: CDN → Backend → S3 final location → Company creation

This eliminates the need for:
- Browser CORS issues
- Local logo server for staging
- Manual logo uploads

## Files Created (Staging)

- `scripts/staging/upload-company-logos.sh` - S3 upload script
- `scripts/staging/generate-staging-companies-json.js` - CDN URL transformer
- `apps/BATspa-old/src/api/companies-with-staging-urls.json` - Generated JSON (temporary)
