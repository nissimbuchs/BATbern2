# Parallel Development Guide: Stories 2.8.1, 2.8.2, 2.8.3

## Overview

This guide explains how to develop three stories in parallel using git worktrees and separate Claude Code sessions.

## Preparation Complete ✅

1. ✅ Created `partnerApi.ts` skeleton with all method signatures
2. ✅ Generated TypeScript types from OpenAPI specs
3. ✅ Committed skeleton to `feature/story-2.7-split-guides`
4. ✅ Created 3 git worktrees:
   - `/Users/nissim/dev/bat/BATbern-2.8.1` → Story 2.8.1 (Partner Directory)
   - `/Users/nissim/dev/bat/BATbern-2.8.2` → Story 2.8.2 (Partner Detail View)
   - `/Users/nissim/dev/bat/BATbern-2.8.3` → Story 2.8.3 (Partner Create/Edit Modal)

## Starting Parallel Development

### Option 1: Fully Automated (Recommended) 🤖

**Two-step process:**

1. **Start tmux session** (creates 3 panes, one per worktree):
```bash
./scripts/start-parallel-dev.sh
```

2. **Launch orchestrator** (in a separate terminal):
```bash
./scripts/orchestrate-parallel-dev.sh
```

**What the orchestrator does:**
- Automatically sends `/BMad:agents:dev` and `develop-story` to all 3 panes
- Monitors story files for task completion every 5 seconds
- Auto-restarts agents when tasks complete
- Shows real-time progress dashboard
- Alerts when all stories are complete

**To detach from tmux while orchestrator runs:**
- Press `Ctrl+b` then `d` (tmux session keeps running)
- Orchestrator continues monitoring in background

### Option 2: Manual Control

Start tmux and manually control each pane:

```bash
./scripts/start-parallel-dev.sh
```

Then in each pane, manually run:
```
/BMad:agents:dev
develop-story
```

**tmux navigation:**
- `Ctrl+b` → Arrow keys - Switch between panes
- `Ctrl+b` → `z` - Toggle pane fullscreen

## Monitoring Progress

### Built-in Monitoring (with Orchestrator)

The orchestrator includes built-in monitoring - it automatically displays:
- Task completion percentage for each story
- Real-time progress updates
- Notifications when tasks complete
- Overall progress across all stories

### Standalone Monitoring (without Orchestrator)

If using manual mode, run the monitoring script:

```bash
./scripts/monitor-parallel-dev.sh

# Or watch continuously:
watch -n 5 ./scripts/monitor-parallel-dev.sh
```

This displays:
- Task completion percentage for each story
- Current task being worked on
- Files changed
- Last commit for each story

## Expected Workflow Per Story

Each dev agent will:
1. Read the next uncompleted task from the story file
2. Implement the task following TDD (Red-Green-Refactor)
3. Run tests and verify they pass
4. Update the story file checkbox: `- [ ]` → `- [x]`
5. Update the "Files Changed" section
6. Commit changes
7. Move to the next task

## Handling Conflicts

Since all three stories share `partnerApi.ts`, conflicts are minimized by the skeleton:
- Each story implements different methods
- Story 2.8.1: `listPartners`, `getPartnerStatistics`
- Story 2.8.2: `getPartnerDetail`, `getPartnerVotes`, `getPartnerMeetings`, `getPartnerActivity`, notes CRUD
- Story 2.8.3: `createPartner`, `updatePartner`

## Merging Back to Main

After all 3 stories complete (Status: "Ready for Review"), merge in order:

```bash
# Merge 2.8.1 first (foundation)
git checkout main
git merge feature/story-2.8.1
git push

# Merge 2.8.2 (depends on 2.8.1)
git merge feature/story-2.8.2
# Resolve any conflicts (should be minimal)
git push

# Merge 2.8.3 (mutations layer)
git merge feature/story-2.8.3
# Resolve any conflicts
git push

# Run integration tests
cd web-frontend && npm run test
npm run test:e2e

# Cleanup worktrees
git worktree remove /Users/nissim/dev/bat/BATbern-2.8.1
git worktree remove /Users/nissim/dev/bat/BATbern-2.8.2
git worktree remove /Users/nissim/dev/bat/BATbern-2.8.3
git branch -d feature/story-2.8.1
git branch -d feature/story-2.8.2
git branch -d feature/story-2.8.3
```

## Troubleshooting

### Worktree Issues

```bash
# List all worktrees
git worktree list

# Remove a worktree
git worktree remove /path/to/worktree

# Recreate if needed
git worktree add /path/to/worktree -b branch-name
```

### Merge Conflicts

If you encounter conflicts in `partnerApi.ts`:
1. The skeleton should have prevented most conflicts
2. Conflicts are likely in method implementations only
3. Keep all implementations from all branches
4. Test thoroughly after merge

### Agent Stuck or Failed

If a dev agent gets stuck:
1. Check the Debug Log (`.ai/debug-log.md`)
2. Review the last commit
3. Manually fix the issue
4. Restart the agent

## Success Criteria

All stories are complete when:
- ✅ All tasks marked `[x]` in story files
- ✅ All tests passing (unit, integration, E2E)
- ✅ Code coverage >85%
- ✅ All ESLint/Prettier checks pass
- ✅ Story status: "Ready for Review"
- ✅ Files Changed section complete
- ✅ No merge conflicts

## Estimated Timeline

With 3 agents running in parallel:
- **Story 2.8.1**: ~2-3 hours (16 tasks)
- **Story 2.8.2**: ~3-4 hours (18 tasks)
- **Story 2.8.3**: ~2-3 hours (14 tasks)
- **Total parallel time**: ~4 hours (vs ~9 hours sequential)
- **Merge & testing**: ~30 minutes

**Total**: ~4.5 hours (50% time savings)
