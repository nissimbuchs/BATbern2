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
