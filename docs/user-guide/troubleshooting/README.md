# Troubleshooting Guide

## Overview

This section provides solutions to common issues organizers encounter while using the BATbern platform. For each problem, you'll find:

- **Symptoms** - How the problem manifests
- **Possible Causes** - Why it's happening
- **Solutions** - Step-by-step fixes (ordered by likelihood)
- **Prevention** - How to avoid the issue in the future

If you don't find a solution here, contact support at **support@batbern.ch** or use the in-app help chat.

## Quick Reference

### By Issue Category

| Category | Common Issues | Documentation |
|----------|---------------|---------------|
| **Authentication** | Login failures, token expiry, password reset | [View →](authentication.md) |
| **File Uploads** | Upload failures, size limits, format errors | [View →](uploads.md) |
| **Workflow** | State transition blocks, validation errors | [View →](workflow.md) |
| **Performance** | Slow loading, timeouts, browser issues | [Performance Issues](#performance-issues) |
| **Data** | Missing records, sync errors | [Data Issues](#data-issues) |

### By Urgency

#### 🔴 Critical (Can't work at all)
- [Can't log in](#cant-log-in) → [Authentication Guide](authentication.md#login-failures)
- [Complete system outage](#system-unavailable) → Check [Status Page](https://status.batbern.ch)
- [Data loss or corruption](#data-loss) → Contact support immediately

#### 🟠 High (Blocks primary workflow)
- [Can't upload files](#upload-failures) → [Upload Guide](uploads.md)
- [Workflow stuck](#workflow-blocked) → [Workflow Guide](workflow.md)
- [Can't access critical features](#feature-unavailable) → Verify role permissions

#### 🟡 Medium (Inconvenient but workaround exists)
- [Slow performance](#performance-issues) → Clear cache, try different browser
- [Minor display issues](#display-problems) → Refresh page, check browser compatibility
- [Missing historical data](#missing-data) → Request backfill from admin

#### 🔵 Low (Cosmetic or minor)
- [UI styling issues](#cosmetic-issues) → Report but doesn't block work
- [Typos in labels](#content-errors) → Report for future fix

## Common Issues (Quick Fixes)

### Can't Log In

**Quick Fix**: Reset password at [login page](https://app.batbern.ch/reset-password)

**See**: [Authentication Troubleshooting](authentication.md#login-failures) for detailed solutions

---

### Page Won't Load / Blank Screen

**Quick Fixes**:
1. Hard refresh: `Ctrl+Shift+R` (Windows) or `Cmd+Shift+R` (Mac)
2. Clear browser cache: Settings → Privacy → Clear Browsing Data
3. Try incognito/private window
4. Try different browser (Chrome recommended)

**If Still Not Working**:
- Check internet connection
- Verify [Status Page](https://status.batbern.ch) for outages
- Contact support with screenshot and browser console errors

---

### File Upload Fails

**Quick Fixes**:
1. Check file size (max 5 MB for logos, 25 MB for presentations)
2. Verify file format (PNG/JPG for logos, PDF/PPTX for presentations)
3. Try smaller file or different format
4. Clear browser cache and retry

**See**: [Upload Troubleshooting](uploads.md) for detailed solutions

---

### Workflow Transition Blocked

**Quick Fixes**:
1. Review validation messages (red error text)
2. Complete all required fields/actions
3. Verify you have correct role permissions
4. Check if previous step is truly complete

**See**: [Workflow Troubleshooting](workflow.md) for detailed solutions

---

### Data Not Showing / Missing Records

**Quick Fixes**:
1. Refresh page
2. Check filters (may be hiding records)
3. Verify date range selection
4. Clear "Recently Deleted" if applicable

**If Still Missing**:
- Verify you have permission to view the data
- Check if data is archived (view Archive section)
- Contact admin if data was recently created by someone else

---

### Slow Performance

**Quick Fixes**:
1. Close unnecessary browser tabs (reduces memory usage)
2. Clear browser cache and cookies
3. Disable browser extensions temporarily
4. Use wired connection instead of WiFi

**Long-Term Solutions**:
- Upgrade browser to latest version
- Increase computer RAM (if frequently slow)
- Use Chrome for best performance

---

## Performance Issues

### Symptoms

- Pages take >5 seconds to load
- Actions (clicks, form submissions) are sluggish
- Browser freezes or becomes unresponsive

### Possible Causes

1. **Browser Issues**
   - Outdated browser version
   - Too many open tabs/windows
   - Conflicting extensions (ad blockers, privacy tools)
   - Low memory availability

2. **Network Issues**
   - Slow internet connection
   - High network latency
   - Corporate firewall/proxy interference

3. **System Issues**
   - Heavy platform usage (many organizers online)
   - Database maintenance window
   - Scheduled deployments

### Solutions

**Browser Optimization**:
```
1. Update browser to latest version
   - Chrome: Settings → About Chrome → Update
   - Firefox: Menu → Help → About Firefox

2. Close unused tabs/windows
   - Keep only 5-10 tabs open at once
   - Use bookmarks instead of keeping tabs open

3. Disable extensions temporarily
   - Open in incognito mode (extensions usually disabled)
   - If faster, identify problematic extension
   - Whitelist batbern.ch in ad blockers

4. Clear cache and cookies
   - Chrome: Settings → Privacy → Clear Browsing Data
   - Select "Cached images and files" + "Cookies"
   - Choose "Last 7 days" or "All time"

5. Restart browser
   - Completely quit (not just close window)
   - Reopen and test performance
```

**Network Optimization**:
```
1. Test connection speed
   - Visit fast.com or speedtest.net
   - Minimum recommended: 10 Mbps download, 5 Mbps upload
   - Contact IT if consistently slower

2. Use wired connection
   - Ethernet cable instead of WiFi (more stable)
   - Reduces latency by 50-70%

3. Bypass corporate proxy (if allowed)
   - Contact IT to whitelist app.batbern.ch
   - May require VPN exception

4. Try different time
   - Off-peak hours (early morning, late evening)
   - Avoid Monday mornings and Friday afternoons
```

**If Still Slow**:
- Check [Status Page](https://status.batbern.ch) for known issues
- Report to support with:
  - Browser and version
  - Internet speed test results
  - Specific pages/actions that are slow
  - Browser console errors (F12 → Console tab)

---

## Data Issues

### Missing Historical Data

**Symptom**: Expected records (events, companies, speakers) not appearing

**Possible Causes**:
- Data not yet digitized (pre-2020 events)
- Archived records (hidden by default)
- Filter settings hiding data
- Permission restrictions

**Solutions**:
1. Check archive: Toggle "Show Archived" in filter panel
2. Clear all filters: Click "Reset Filters" button
3. Expand date range: Set "All Time" in date picker
4. Verify permissions: Contact admin if you should have access
5. Request backfill: Email admin team to digitize historical records

### Sync Errors

**Symptom**: "Data sync failed" error message or stale data

**Possible Causes**:
- Network interruption during sync
- Browser storage full (IndexedDB quota exceeded)
- Concurrent edits conflict

**Solutions**:
1. Retry sync: Click "Retry" button in error message
2. Hard refresh: `Ctrl+Shift+R` to force reload
3. Clear browser storage:
   - F12 (Dev Tools) → Application → Clear Storage
   - Check "Local Storage" and "IndexedDB"
   - Click "Clear Site Data"
4. Report persistent sync issues to support

---

## Browser Compatibility

### Supported Browsers (Recommended)

| Browser | Minimum Version | Recommended Version | Notes |
|---------|----------------|---------------------|-------|
| **Chrome** | 90+ | Latest (130+) | Best performance, all features supported |
| **Firefox** | 88+ | Latest (120+) | Fully supported, good alternative |
| **Safari** | 14+ | Latest (17+) | macOS only, minor styling differences |
| **Edge** | 90+ | Latest (130+) | Chromium-based, equivalent to Chrome |

### Unsupported Browsers

- Internet Explorer (all versions) - Not supported
- Opera Mini - Not supported
- Old Android browsers (<90) - Not supported

### Browser-Specific Issues

**Safari (macOS)**:
- Date pickers may look different (native Safari styling)
- File uploads require explicit click (drag-drop less reliable)
- Workaround: Use Chrome for best experience

**Firefox**:
- PDF preview may not work inline (opens in new tab instead)
- Autofill behavior differs from Chrome
- Workaround: Update to latest Firefox version

---

## Getting Additional Help

### Before Contacting Support

1. **Check This Guide**: Review relevant troubleshooting pages
2. **Try Quick Fixes**: Follow the step-by-step solutions above
3. **Gather Information**:
   - What were you trying to do?
   - What happened instead (exact error message)?
   - When did it start (specific date/time)?
   - Browser and version (Help → About)
   - Screenshot of error (if applicable)

### Contacting Support

**Email**: support@batbern.ch

**In-App Help Chat**: Click "?" icon → "Chat with Support" (bottom-right corner)

**Include in Your Request**:
- Your name and email
- Event number (if issue is event-specific)
- Steps to reproduce the problem
- Screenshot or error message
- Browser and version
- Urgency level (Critical, High, Medium, Low)

**Response Times**:
- **Critical** (can't work): 2-4 hours during business hours
- **High** (major inconvenience): 8-12 hours
- **Medium/Low**: 1-2 business days

### Emergency Support

For critical production issues outside business hours:

**Emergency Hotline**: +41 31 XXX XXXX (organizers only, requires authorization code)

**Use Only For**:
- Complete system outage during event
- Data loss or corruption
- Security incidents (unauthorized access)

---

## Related Resources

- **[Authentication Guide](authentication.md)** - Login, password, and session issues
- **[Upload Guide](uploads.md)** - File upload problems and solutions
- **[Workflow Guide](workflow.md)** - Workflow state and transition issues
- **[Feature Status](../appendix/feature-status.md)** - Check if feature is implemented or planned
- **[Getting Started](../getting-started/)** - Platform basics and orientation

---

**Next**: Explore specific troubleshooting guides for [Authentication →](authentication.md)
