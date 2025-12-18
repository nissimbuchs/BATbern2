# Keyboard Shortcuts

## Overview

Keyboard shortcuts improve productivity by reducing reliance on mouse navigation. This guide covers all platform shortcuts, including:

- **Navigation** - Move between sections and pages
- **Editing** - Create, modify, and save entities
- **Workflow** - Advance steps and change status
- **Search & Filter** - Find information quickly
- **General** - Common actions and utilities

**Note**: Most advanced shortcuts are `[PLANNED]`. Currently implemented shortcuts are marked `[IMPLEMENTED]`.

---

## Notation

### Key Symbols

| Symbol | Meaning | Example |
|--------|---------|---------|
| `Ctrl` | Control key (Windows/Linux) | `Ctrl+S` |
| `Cmd` | Command key (macOS) | `Cmd+S` |
| `⌘` | Command key symbol | `⌘+S` |
| `Alt` | Alt/Option key | `Alt+N` |
| `Shift` | Shift key | `Shift+Enter` |
| `+` | Press keys together | `Ctrl+Shift+F` |
| `/` | Or (platform-specific) | `Ctrl/Cmd+S` |

### Platform Differences

Most shortcuts follow this pattern:
- **Windows/Linux**: Use `Ctrl`
- **macOS**: Use `Cmd` (⌘)

Examples:
- Save: `Ctrl+S` (Windows) / `Cmd+S` (Mac)
- New: `Ctrl+N` (Windows) / `Cmd+N` (Mac)

This guide uses `Ctrl/Cmd+Key` notation to indicate platform-specific shortcuts.

---

## Universal Shortcuts `[IMPLEMENTED]`

These work across the entire platform, regardless of current page.

### Browser Standard

| Shortcut | Action | Notes |
|----------|--------|-------|
| `Ctrl/Cmd+R` | Refresh page | Reload current view |
| `Ctrl/Cmd+Shift+R` | Hard refresh | Clear cache and reload |
| `Ctrl/Cmd+W` | Close tab | Close current browser tab |
| `Ctrl/Cmd+T` | New tab | Open new browser tab |
| `Ctrl/Cmd+L` | Focus address bar | Jump to URL field |
| `Ctrl/Cmd+F` | Find on page | Browser search (searches rendered HTML) |
| `F5` | Refresh | Alternative to `Ctrl+R` |
| `F12` | Developer tools | Open browser console |

### Platform-Specific `[IMPLEMENTED]`

| Shortcut | Action | Context |
|----------|--------|---------|
| `Escape` | Close dialog/modal | Dismisses overlays without saving |
| `Enter` | Submit form | When input field focused |
| `Tab` | Next field | Move to next form field |
| `Shift+Tab` | Previous field | Move to previous form field |
| `Ctrl/Cmd+Z` | Undo | In text fields only (platform doesn't track entity undo) |
| `Ctrl/Cmd+Y` | Redo | In text fields only |

---

## Navigation Shortcuts

### Global Navigation `[PLANNED]`

| Shortcut | Action | Destination |
|----------|--------|-------------|
| `G` then `D` | Go to Dashboard | Main organizer dashboard |
| `G` then `E` | Go to Events | Events list |
| `G` then `S` | Go to Speakers | Speakers list |
| `G` then `C` | Go to Companies | Companies list |
| `G` then `P` | Go to Partners | Partner directory |
| `G` then `A` | Go to Analytics | Analytics dashboard |
| `G` then `?` | Go to Help | Open help documentation |

**Usage**: Press `G`, release, then press destination key (not held together).

### Within Page Navigation `[PLANNED]`

| Shortcut | Action | Notes |
|----------|--------|-------|
| `J` | Next item | Move down in lists |
| `K` | Previous item | Move up in lists |
| `O` or `Enter` | Open item | Open selected list item |
| `U` | Go back | Return to previous page (browser back) |
| `Ctrl/Cmd+↑` | Scroll to top | Jump to page top |
| `Ctrl/Cmd+↓` | Scroll to bottom | Jump to page bottom |
| `Space` | Page down | Scroll one viewport down |
| `Shift+Space` | Page up | Scroll one viewport up |

---

## Entity Management Shortcuts

### CRUD Operations `[PLANNED]`

| Shortcut | Action | Context |
|----------|--------|---------|
| `Ctrl/Cmd+N` | New entity | Create new (context-aware: event, speaker, company) |
| `Ctrl/Cmd+E` | Edit entity | Open edit form for selected entity |
| `Ctrl/Cmd+S` | Save | Save current form |
| `Ctrl/Cmd+Shift+S` | Save & Close | Save and return to list |
| `Ctrl/Cmd+D` | Duplicate | Clone selected entity |
| `Delete` | Delete entity | Delete selected (confirmation required) |
| `Escape` | Cancel edit | Close form without saving (confirmation if changes) |

### Entity-Specific `[PLANNED]`

**When viewing Event**:

| Shortcut | Action |
|----------|--------|
| `W` | View Workflow | Jump to workflow tab |
| `S` | View Speakers | Jump to speakers tab |
| `T` | View Topics | Jump to topics tab |
| `D` | View Details | Jump to details tab |

**When viewing Speaker**:

| Shortcut | Action |
|----------|--------|
| `E` | Edit speaker | Open edit form |
| `U` | Upload material | Open file upload dialog |
| `M` | Send email | Compose email to speaker |

---

## Workflow Shortcuts

### Workflow Navigation `[PLANNED]`

| Shortcut | Action | Notes |
|----------|--------|-------|
| `→` or `N` | Next step | Advance to next workflow step (if validated) |
| `←` or `P` | Previous step | View previous step (admin override required to revert) |
| `Ctrl/Cmd+Enter` | Advance workflow | Same as clicking "Advance to Next Step" button |
| `1` - `9` | Jump to step | Jump to specific step (1-9) |
| `Shift+1` - `Shift+7` | Jump to step 10-16 | For steps 10-16 (e.g., `Shift+1` = Step 10) |

### Status Changes `[PLANNED]`

| Shortcut | Action | Context |
|----------|--------|---------|
| `A` | Approve | Approve selected item (speaker, content) |
| `R` | Reject | Reject selected item |
| `I` | Invite | Send invitation to selected speaker |
| `Shift+I` | Bulk invite | Invite all selected speakers |
| `?` | View details | Show details panel for selected item |

---

## Search & Filter Shortcuts

### Search `[PLANNED]`

| Shortcut | Action | Notes |
|----------|--------|-------|
| `/` | Focus search | Jump to search box |
| `Ctrl/Cmd+K` | Command palette | Quick search/actions (like VS Code) |
| `Ctrl/Cmd+F` | Find on page | Browser find (searches rendered text) |
| `F3` or `Ctrl/Cmd+G` | Next result | Jump to next search result |
| `Shift+F3` or `Ctrl/Cmd+Shift+G` | Previous result | Jump to previous search result |
| `Escape` | Clear search | Exit search, clear filters |

### Filtering `[PLANNED]`

| Shortcut | Action | Notes |
|----------|--------|-------|
| `F` | Open filters | Show filter panel |
| `Ctrl/Cmd+Shift+F` | Clear filters | Reset all filters |
| `Alt+1` - `Alt+5` | Status filter | Filter by status (1=All, 2=Active, 3=Pending, 4=Completed, 5=Archived) |
| `T` then filter key | Filter by type | Event type, speaker status, etc. |

---

## Bulk Actions `[PLANNED]`

| Shortcut | Action | Context |
|----------|--------|---------|
| `Ctrl/Cmd+A` | Select all | Select all items in current list |
| `Ctrl/Cmd+Click` | Multi-select | Toggle selection (add/remove from selection) |
| `Shift+Click` | Range select | Select from last selected to clicked item |
| `Ctrl/Cmd+Shift+A` | Deselect all | Clear selection |
| `Shift+A` | Bulk approve | Approve all selected items |
| `Shift+R` | Bulk reject | Reject all selected items |
| `Shift+D` | Bulk delete | Delete all selected items (confirmation required) |
| `Shift+E` | Bulk export | Export selected items to CSV |

---

## Form Editing Shortcuts

### Text Editing `[IMPLEMENTED]`

Standard text editing shortcuts work in all input fields:

| Shortcut | Action |
|----------|--------|
| `Ctrl/Cmd+C` | Copy |
| `Ctrl/Cmd+X` | Cut |
| `Ctrl/Cmd+V` | Paste |
| `Ctrl/Cmd+A` | Select all text in field |
| `Ctrl/Cmd+Z` | Undo |
| `Ctrl/Cmd+Y` or `Ctrl/Cmd+Shift+Z` | Redo |
| `Ctrl/Cmd+B` | Bold (in rich text fields) `[PLANNED]` |
| `Ctrl/Cmd+I` | Italic (in rich text fields) `[PLANNED]` |
| `Ctrl/Cmd+U` | Underline (in rich text fields) `[PLANNED]` |

### Form Navigation `[IMPLEMENTED]`

| Shortcut | Action | Notes |
|----------|--------|-------|
| `Tab` | Next field | Move to next input |
| `Shift+Tab` | Previous field | Move to previous input |
| `Enter` | Submit form | Submits form if no multi-line text focused |
| `Shift+Enter` | New line | In multi-line text areas |
| `Escape` | Cancel | Close form without saving |

---

## File Upload Shortcuts `[PLANNED]`

| Shortcut | Action | Context |
|----------|--------|---------|
| `Ctrl/Cmd+U` | Upload file | Open file picker |
| `Ctrl/Cmd+V` | Paste image | Paste from clipboard (images only) |
| `Drag & Drop` | Upload | Drag file onto upload area |
| `Enter` | Confirm upload | After file selected, start upload |
| `Escape` | Cancel upload | Abort in-progress upload |

---

## Accessibility Shortcuts `[PLANNED]`

| Shortcut | Action | Notes |
|----------|--------|-------|
| `Alt+1` | Skip to main content | Bypass navigation |
| `Alt+2` | Skip to navigation | Jump to sidebar |
| `Alt+3` | Skip to search | Jump to search box |
| `Alt+0` | Accessibility menu | Show accessibility options |
| `Ctrl/Cmd++` | Zoom in | Increase text size |
| `Ctrl/Cmd+-` | Zoom out | Decrease text size |
| `Ctrl/Cmd+0` | Reset zoom | Return to 100% |
| `?` | Show shortcuts | Display this help |

---

## Developer / Debug Shortcuts

### Browser Developer Tools `[IMPLEMENTED]`

| Shortcut | Action | Purpose |
|----------|--------|---------|
| `F12` | Toggle DevTools | Open/close developer console |
| `Ctrl/Cmd+Shift+I` | Toggle DevTools | Alternative to F12 |
| `Ctrl/Cmd+Shift+J` | Open Console | Jump to Console tab |
| `Ctrl/Cmd+Shift+C` | Element inspector | Select element to inspect |
| `Ctrl/Cmd+Shift+Delete` | Clear cache | Open clear browsing data dialog |

### Platform Debug `[PLANNED]`

| Shortcut | Action | Purpose |
|----------|--------|---------|
| `Ctrl/Cmd+Shift+D` | Debug mode | Show debug panel (admin only) |
| `Ctrl/Cmd+Shift+L` | View logs | Show application logs |
| `Ctrl/Cmd+Shift+N` | Network monitor | Show API request log |

---

## Customizing Shortcuts `[PLANNED]`

**Feature Status**: Keyboard shortcut customization is planned for a future release.

**Planned Functionality**:
1. Settings → Keyboard Shortcuts
2. Search for action (e.g., "Save")
3. Click current shortcut
4. Press new key combination
5. Conflicts shown in real-time
6. Save custom keymap
7. Export/import keymaps (share with team)

**Presets** (planned):
- Default (BATbern standard)
- VS Code style (for developers)
- Gmail style (for email-heavy users)
- Vim style (for power users)

---

## Shortcuts Cheat Sheet

### Most Used (Quick Reference)

**Navigation**:
- `G` + `D/E/S/C/P` - Go to section `[PLANNED]`
- `J/K` - Next/Previous item `[PLANNED]`
- `/` - Search `[PLANNED]`

**Entity Management**:
- `Ctrl/Cmd+N` - New `[PLANNED]`
- `Ctrl/Cmd+S` - Save `[PLANNED]`
- `Ctrl/Cmd+E` - Edit `[PLANNED]`

**Workflow**:
- `Ctrl/Cmd+Enter` - Advance step `[PLANNED]`
- `A/R` - Approve/Reject `[PLANNED]`
- `1-9` - Jump to step `[PLANNED]`

**Universal**:
- `Escape` - Cancel/Close `[IMPLEMENTED]`
- `Enter` - Submit/Confirm `[IMPLEMENTED]`
- `Tab` - Next field `[IMPLEMENTED]`
- `?` - Show help `[PLANNED]`

---

## Browser-Specific Notes

### Chrome / Edge (Chromium)

- All shortcuts work as documented
- Recommended browser for best experience
- Dev Tools: `F12` or `Ctrl/Cmd+Shift+I`

### Firefox

- All shortcuts work as documented
- Some shortcuts may conflict with Firefox add-ons
- Disable conflicting add-ons: Add-ons → [Extension] → Permissions

### Safari (macOS)

- Use `Cmd` instead of `Ctrl`
- Some shortcuts may conflict with macOS system shortcuts
- To customize: System Settings → Keyboard → Shortcuts
- Known conflicts:
  - `Cmd+,` (Preferences) - overrides platform settings shortcut
  - `Cmd+H` (Hide) - macOS system shortcut takes precedence

### Mobile Browsers

- Keyboard shortcuts not applicable (touchscreen devices)
- Alternative: Gesture navigation (planned)
  - Swipe right: Back
  - Swipe down: Refresh
  - Long press: Context menu

---

## Tips & Best Practices

### Learning Shortcuts

1. **Start with 5 Most Used**
   - Learn `Ctrl/Cmd+S` (Save)
   - Then `Ctrl/Cmd+N` (New)
   - Then navigation shortcuts (`G` + letter)
   - Then search (`/`)
   - Then workflow (`Ctrl/Cmd+Enter`)

2. **Use Visual Cues**
   - Hover over buttons to see shortcut tooltips
   - Look for underlined letters (Access Keys)
   - Check help menu for shortcuts list

3. **Practice Regularly**
   - Use shortcuts even when slower at first
   - Muscle memory develops after ~2 weeks
   - Productivity improves 20-30% once mastered

### Avoiding Conflicts

**Browser Extensions**:
- Some extensions intercept shortcuts (e.g., Grammarly, password managers)
- Disable extensions if shortcuts not working
- Whitelist BATbern domain in extension settings

**OS Shortcuts**:
- Some OS shortcuts override browser (e.g., `Cmd+Tab` on macOS)
- Can't be overridden by web apps (browser security)
- Use alternative shortcuts if conflicts occur

**Accessibility Tools**:
- Screen readers have their own shortcuts
- Platform shortcuts designed to not conflict
- Contact support if conflicts found

---

## Printable Cheat Sheet

**Download**: [keyboard-shortcuts-cheatsheet.pdf](../assets/keyboard-shortcuts-cheatsheet.pdf) `[PLANNED]`

**One-Page Reference**:
```
┌─────────────────────────────────────────────────────────────┐
│             BATbern Keyboard Shortcuts (v1.0)               │
├─────────────────────────────────────────────────────────────┤
│ NAVIGATION              │ ENTITY MANAGEMENT                 │
│ G+D  Dashboard          │ Ctrl+N  New entity                │
│ G+E  Events             │ Ctrl+E  Edit entity               │
│ G+S  Speakers           │ Ctrl+S  Save                      │
│ J/K  Next/Prev item     │ Ctrl+D  Duplicate                 │
│ /    Search             │ Delete  Delete entity             │
├─────────────────────────┼───────────────────────────────────┤
│ WORKFLOW                │ UNIVERSAL                         │
│ Ctrl+Enter  Advance     │ Esc     Cancel/Close              │
│ A/R  Approve/Reject     │ Enter   Submit/Confirm            │
│ 1-9  Jump to step       │ Tab     Next field                │
│ W    View workflow      │ ?       Show help                 │
└─────────────────────────────────────────────────────────────┘

Note: Use Cmd instead of Ctrl on macOS
Most shortcuts marked [PLANNED] - coming in future release
```

---

## Related Resources

- **[Accessibility Features](../getting-started/navigation.md#accessibility)** - Screen reader support, keyboard navigation
- **[Feature Status](feature-status.md)** - Implementation status of planned shortcuts
- **[Glossary](glossary.md)** - Term definitions

---

**Back to Appendix**: Return to [Documentation Home](../README.md) →
