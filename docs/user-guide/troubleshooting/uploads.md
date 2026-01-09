# File Upload Troubleshooting

## Overview

This guide helps resolve file upload issues for company logos, speaker materials, and event assets. BATbern uses direct-to-S3 uploads with presigned URLs for security and performance.

See [File Uploads Feature Guide](../features/file-uploads.md) for complete upload documentation.

## Quick Diagnosis

```
Select file → Upload initiated?
│
├─ NO → [File Selection Issues](#cant-select-file)
│
└─ YES → Progress bar appears?
    │
    ├─ NO → [Upload Not Starting](#upload-not-starting)
    │
    └─ YES → Upload completes?
        │
        ├─ NO → [Upload Fails or Stalls](#upload-fails-or-stalls)
        │
        └─ YES → File appears in system?
            │
            ├─ NO → [Upload Completed but File Missing](#upload-completed-but-file-missing)
            │
            └─ YES → Quality/preview issues?
                │
                ├─ Poor quality → [Preview Quality Issues](#preview-quality-issues)
                └─ Wrong preview → [Incorrect Thumbnail](#incorrect-thumbnail)
```

---

## File Selection Issues

### Can't Select File

**Symptom**: File picker doesn't open or file selection doesn't register

**Possible Causes**:
1. Browser permissions not granted
2. File input element not responding
3. Browser extension blocking file access
4. Operating system file picker crash

**Solutions**:

**Step 1: Check Browser Permissions**
```
Chrome:
1. Click padlock icon (left of address bar)
2. Verify "Files" permission is "Allow"
3. If blocked, change to "Allow" and refresh page

Firefox:
1. Click shield icon (left of address bar)
2. Verify "Access files" is allowed
3. Allow if blocked, refresh page

Safari:
1. Safari → Preferences → Websites → Files
2. Find app.batbern.ch
3. Set to "Allow"
```

**Step 2: Try Different Selection Method**
```
If "Choose File" button doesn't work:
1. Try drag-and-drop instead
   - Open file explorer/finder
   - Drag file directly onto upload area
   - Drop when blue border appears

2. Try right-click approach
   - Right-click upload area
   - Select "Choose File" from context menu
```

**Step 3: Browser Troubleshooting**
```
1. Clear browser cache and cookies
2. Try incognito/private mode (disables extensions)
3. If works in incognito → disable extensions one-by-one to find conflict
4. Try different browser (Chrome recommended)
```

---

### File Type Not Accepted

**Symptom**: Error "Invalid file type" or file grayed out in picker

**Cause**: File format not supported for this upload category

**Supported Formats by Category**:

| Category | Accepted Formats | Max Size |
|----------|------------------|----------|
| Company Logos | PNG, JPG, JPEG, SVG | 5 MB |
| Speaker Presentations | PDF, PPTX, KEY | 25 MB |
| Handouts/Documents | PDF, DOCX `[PLANNED]` | 10 MB |
| Event Images | PNG, JPG, JPEG | 5 MB |

**Solutions**:

**For Images (logos, photos)**:
```
If you have:
- GIF → Convert to PNG (online: cloudconvert.com)
- BMP → Convert to JPG (online: convertio.co)
- WEBP → Convert to PNG (online: ezgif.com/webp-to-png)
- TIFF → Convert to JPG (Photoshop, Preview, or online tool)
```

**For Presentations**:
```
If you have:
- Google Slides → File → Download → PDF or PPTX
- Keynote → File → Export To → PDF or PowerPoint
- ODP (LibreOffice) → File → Export as PDF
```

**For Documents**:
```
If you have:
- Word DOCX → File → Save As → PDF (DOCX support planned)
- Google Docs → File → Download → PDF
- Pages → File → Export To → PDF
```

**Quick Conversion Tips**:
- Use online converters (no software install needed)
- Maintain original file as backup
- Compress after conversion if file size too large

---

## Upload Failures

### Upload Not Starting

**Symptom**: Click "Upload", file selected, but nothing happens (no progress bar)

**Possible Causes**:
1. Presigned URL request failed (network or permission issue)
2. File validation failed client-side (size or format)
3. JavaScript error blocking upload

**Solutions**:

**Step 1: Check Browser Console**
```
1. Press F12 to open Developer Tools
2. Click "Console" tab
3. Look for errors (red text) when clicking upload
4. Common errors:
   - "NetworkError" → Network issue, see Step 2
   - "Validation failed" → File size/format issue, see Step 3
   - "Permission denied" → Role permission issue, contact admin
```

**Step 2: Network Troubleshooting**
```
1. Test connection: open google.com in new tab
2. Check status page: https://status.batbern.ch
3. Disable VPN temporarily (may block AWS S3 requests)
4. Try different network (mobile hotspot vs WiFi)
5. Contact IT if on corporate network (may need to whitelist S3 domains)
```

**Step 3: File Validation**
```
1. Check file size:
   - Right-click file → Properties (Windows) / Get Info (Mac)
   - If over limit, compress before uploading

2. Check file format:
   - Verify extension matches actual type
   - Rename if needed (e.g., image.PNG → image.png)

3. Try smaller test file:
   - Upload 100KB file to verify system works
   - If succeeds, original file may be corrupted
```

**Step 4: Browser Reset**
```
1. Hard refresh: Ctrl+Shift+R (Cmd+Shift+R on Mac)
2. Clear cache and cookies
3. Log out and log back in
4. Try different browser if still failing
```

---

### Upload Fails or Stalls

**Symptom**: Progress bar appears but stops at X% or shows error

**Possible Causes**:
1. Network interruption
2. Presigned URL expired (15-minute limit)
3. AWS S3 service disruption (rare)
4. File size too large for connection speed

**Solutions**:

**Stalls at 0-10% (Initialization Failure)**:
```
Cause: Presigned URL issue
Solution:
1. Wait 10 seconds (may be slow network)
2. Cancel and retry upload (generates new presigned URL)
3. Check AWS status: https://status.aws.amazon.com (eu-central-1 region)
```

**Stalls at 50-90% (Network Interruption)**:
```
Cause: Connection dropped during upload
Solution:
1. Check network stability (WiFi signal, VPN connection)
2. Use wired Ethernet connection (more reliable)
3. Upload during off-peak hours (less network congestion)
4. Compress file to reduce size (faster upload)
```

**"Upload Failed" Error After Progress**:
```
Common error messages:

"Upload expired" → Took >15 minutes
- Retry upload (new presigned URL)
- Compress file for faster upload
- Use wired connection

"Network error" → Connection lost
- Verify internet connection
- Retry upload

"Access denied" → Permission issue
- Verify you're logged in
- Check role permissions (ORGANIZER or ADMIN)
- Contact admin if permissions correct

"Storage quota exceeded" [PLANNED]
- Contact admin to increase org quota
- Delete old unused files
```

---

### Upload Completed but File Missing

**Symptom**: Progress bar reaches 100%, success message, but file doesn't appear

**Possible Causes**:
1. Backend confirmation failed (S3 upload succeeded but database update failed)
2. Page refreshed too early (before backend processed)
3. File processing still in progress (large files take time)
4. Filter hiding the uploaded file

**Solutions**:

**Step 1: Wait and Refresh**
```
1. Wait 30 seconds (backend may still be processing)
2. Refresh page (F5 or Ctrl+R)
3. Check if file now appears
```

**Step 2: Check Recent Uploads**
```
1. Go to Settings → File Manager → Recent Uploads
2. Look for your file in list (shows last 50 uploads)
3. If listed but not associated with entity:
   - Click "Associate" button
   - Select correct entity (company, speaker, event)
   - File will now appear in entity record
```

**Step 3: Check Filters**
```
1. Clear any active filters (Search box, Date range, Status)
2. Click "Show All" or "Reset Filters" button
3. Verify "Include Archived" is unchecked (unless expecting archived)
```

**Step 4: Verify Browser Didn't Block**
```
1. Check browser console (F12 → Console)
2. Look for errors after upload completed
3. Common issue: browser blocked S3 response due to CORS
   - Disable browser extensions temporarily
   - Try incognito mode
   - Clear cache and retry
```

**If Still Missing**:
```
Contact support with:
- File name and size
- Upload timestamp (approximate time)
- Entity you were trying to associate (company name, speaker name, event #)
- Screenshot of "success" message
- Browser console log (F12 → Console → screenshot)

Support can locate file in S3 and manually associate it.
```

---

## File Size Issues

### File Too Large

**Symptom**: Error "File exceeds maximum size" or "File too large"

**Size Limits**:
- Company Logos: 5 MB
- Presentations: 25 MB
- Documents: 10 MB
- Event Images: 5 MB

**Solutions**:

**For Images (Logos, Photos)**:
```
Method 1: Reduce Dimensions
1. Open in image editor (Preview on Mac, Paint on Windows, or online: pixlr.com)
2. Resize to target dimensions:
   - Logos: 800x800px (sufficient for all uses)
   - Photos: 1920x1080px (HD quality)
3. Save as PNG (lossless) or JPG (smaller file)

Method 2: Compress Image
1. Use online compression tool:
   - TinyPNG.com (PNG files, often 60-80% reduction)
   - Compressor.io (JPG files, maintain quality)
   - Squoosh.app (advanced options, works offline)
2. Upload compressed version

Method 3: Convert Format
- PNG → JPG (usually 50-70% smaller, but no transparency)
- JPG → Progressive JPG (loads faster, slightly smaller)
```

**For Presentations**:
```
Method 1: Export as PDF
1. Open in PowerPoint/Keynote
2. File → Export as PDF (not "Save As")
3. PDF typically 40-60% smaller than PPTX

Method 2: Compress Images Within Slides
PowerPoint:
1. Click any image → Picture Format → Compress Pictures
2. Select "Web (150 PPI)" quality
3. Check "Apply to all pictures"
4. Save file

Keynote:
1. File → Reduce File Size
2. Select quality level (Medium recommended)
3. Save reduced version

Method 3: Remove Unused Content
- Delete unused slides
- Remove embedded videos (link to YouTube instead)
- Remove embedded fonts (use web-safe fonts)
```

**For PDF Documents**:
```
Method 1: Online PDF Compressor
1. Visit Smallpdf.com or ilovepdf.com
2. Upload PDF
3. Select compression level (High compression for heavily illustrated PDFs)
4. Download compressed version

Method 2: Print to PDF (macOS)
1. Open PDF in Preview
2. File → Print
3. PDF dropdown → Save as PDF
4. Select "Reduce File Size" from Quartz Filter
5. Save (usually 50-70% smaller)

Method 3: Optimize in Acrobat (if available)
1. File → Save As Other → Reduced Size PDF
2. Select compatibility (Acrobat 10.0 or later)
3. Save optimized version
```

---

### Slow Upload Speed

**Symptom**: Upload taking very long time (>2 minutes for 5 MB file)

**Expected Upload Times** (typical broadband):
- 1 MB: 5-10 seconds
- 5 MB: 20-40 seconds
- 10 MB: 40-80 seconds
- 25 MB: 2-4 minutes

**If Significantly Slower**:

**Test Your Connection Speed**:
```
1. Visit fast.com or speedtest.net
2. Note "Upload Speed" (not download)
3. Minimum recommended: 5 Mbps upload
4. If < 5 Mbps, contact ISP or use better connection
```

**Optimize Upload Speed**:
```
1. Use Wired Connection
   - Ethernet cable (not WiFi)
   - Typically 2-3x faster and more reliable

2. Close Bandwidth-Heavy Apps
   - Streaming services (Netflix, YouTube)
   - Video calls (Zoom, Teams)
   - Cloud sync (Dropbox, OneDrive)
   - Downloads in progress

3. Upload During Off-Peak Hours
   - Early morning (6-8 AM)
   - Late evening (after 8 PM)
   - Avoid Monday mornings and Friday afternoons

4. Compress File Before Uploading
   - Smaller file = faster upload
   - See [File Too Large](#file-too-large) for compression tips
```

**Corporate Network Issues**:
```
If on company network and uploads very slow:
1. Contact IT - may be traffic shaping/throttling
2. Ask IT to whitelist these domains:
   - *.s3.eu-central-1.amazonaws.com
   - *.amazonaws.com
3. Try mobile hotspot as temporary workaround
```

---

## Quality and Preview Issues

### Preview Quality Poor

**Symptom**: Uploaded logo/image appears blurry or pixelated in preview

**Possible Causes**:
1. Original file resolution too low
2. Browser scaling artifact
3. Thumbnail generation compressed too aggressively
4. Viewing on high-DPI display (Retina, 4K)

**Solutions**:

**Check Original File Quality**:
```
1. View original file on your computer
2. Zoom to 100% (actual size)
3. If blurry at 100%, image is low resolution:
   - Find higher-res version
   - For logos: request vector (SVG) from designer
   - For photos: use original camera file (not social media version)
```

**Upload Higher Resolution Version**:
```
Recommended minimums:
- Company logos: 800x800px (200 DPI ideal)
- Event photos: 1920x1080px (HD)
- Speaker headshots: 600x600px

Optimal formats:
- Logos: PNG (transparency) or SVG (vector, scales perfectly)
- Photos: JPG (smaller file size)
```

**Workaround for High-DPI Displays**:
```
If image looks fine on standard display but blurry on Retina/4K:
1. Upload 2x resolution version (e.g., 1600x1600px instead of 800x800px)
2. Platform will downsample for standard displays
3. High-DPI displays will use full resolution
```

---

### Incorrect Thumbnail

**Symptom**: Thumbnail shows wrong image or corrupted preview

**Possible Causes**:
1. Thumbnail generation still processing
2. File format issue (corrupted metadata)
3. Browser cache showing old thumbnail
4. File type not fully supported

**Solutions**:

**Wait for Processing**:
```
1. Thumbnail generation can take 10-30 seconds
2. Refresh page after 1 minute
3. If still incorrect, proceed to next step
```

**Clear Browser Cache**:
```
1. Hard refresh: Ctrl+Shift+R (Cmd+Shift+R on Mac)
2. If still wrong, clear cache:
   - Chrome: Settings → Privacy → Clear browsing data
   - Select "Cached images and files"
   - Choose "Last 24 hours"
3. Refresh page after clearing
```

**Re-Upload File**:
```
1. Delete current file (if allowed)
2. Verify original file opens correctly on your computer
3. Convert to most compatible format:
   - Images: PNG or JPG (not TIFF, BMP, WEBP)
   - Presentations: PDF (not PPTX or KEY)
4. Upload converted version
```

**For SVG Files (Logos)**:
```
SVG thumbnails sometimes fail if file contains:
- External references
- Embedded scripts
- Complex filters/effects

Solution:
1. Open SVG in Inkscape or vector editor
2. File → Save As → "Plain SVG" or "Optimized SVG"
3. Upload simplified version

Or:
1. Export SVG as PNG (800x800px, 200 DPI)
2. Upload PNG instead (works reliably)
```

---

## Error Messages Reference

| Error Message | Meaning | Solution |
|---------------|---------|----------|
| `UPLOAD_001: File too large` | Exceeds category size limit | Compress or split file |
| `UPLOAD_002: Invalid file type` | Format not supported | Convert to supported format |
| `UPLOAD_003: Upload expired` | Took >15 minutes | Retry with smaller/compressed file |
| `UPLOAD_004: Network error` | Connection interrupted | Check network, retry upload |
| `UPLOAD_005: Access denied` | Permission issue | Verify role, contact admin |
| `UPLOAD_006: Storage quota exceeded` `[PLANNED]` | Org storage limit reached | Contact admin to increase quota |
| `UPLOAD_007: File corrupted` | File damaged or incomplete | Try different file or repair original |
| `UPLOAD_008: Virus detected` `[PLANNED]` | Security scan failed | Scan file locally, try different file |

---

## Prevention Best Practices

### File Preparation

1. **Optimize Before Uploading**
   - Compress images (TinyPNG, Compressor.io)
   - Export presentations as PDF
   - Use web-optimized formats (PNG, JPG, PDF)

2. **Use Correct Dimensions**
   - Logos: 800x800px minimum
   - Photos: 1920x1080px (HD standard)
   - Don't upload overly large files (reduces upload time)

3. **Choose Right Format**
   - Transparency needed → PNG
   - Photos/screenshots → JPG
   - Documents → PDF (most compatible)
   - Vectors → SVG (logos only, with PNG fallback)

### Upload Process

1. **Stable Connection**
   - Use wired Ethernet for large files
   - Avoid uploads during video calls or streaming
   - Don't close browser or tab during upload

2. **Verify Before Confirming**
   - Check thumbnail preview before saving
   - Verify file name is correct
   - Confirm file size is as expected

3. **Keep Originals**
   - Save original files before compression
   - Maintain backup of important documents
   - Version control for presentation files

---

## Getting Additional Help

### Before Contacting Support

1. Try solutions in this guide
2. Test with smaller file (to isolate if file-specific issue)
3. Try different browser
4. Gather error details (screenshots, console logs)

### Contact Support

**Email**: support@batbern.ch

**Include**:
- File name, type, and size
- Upload category (logo, presentation, etc.)
- Error message screenshot
- Browser console log (F12 → Console)
- Steps already tried
- Urgency level

**Response Times**:
- Critical (blocking workflow): 4-8 hours
- High priority: 12-24 hours
- Standard: 1-2 business days

---

## Related Resources

- **[File Uploads Feature Guide](../features/file-uploads.md)** - Complete upload documentation
- **[Company Management](../entity-management/companies.md#logo-upload)** - Logo upload workflow
- **[Speaker Materials](../workflow/phase-b-outreach.md#content-collection)** - Presentation upload
- **[Troubleshooting Overview](README.md)** - Other common issues

---

**Next**: Explore [Workflow Troubleshooting →](workflow.md)
