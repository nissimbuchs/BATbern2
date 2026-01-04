# Claude Code Linear Integration Hooks

This directory contains hooks that automatically integrate the dev agent workflow with Linear issue tracking.

## Hooks Overview

### 1. `linear-start.sh` - User Prompt Submit Hook
**Trigger**: When you submit `develop-story {story-name}` command
**Action**: Updates Linear issue status to "In Progress"

**How it works**:
1. Detects `develop-story` command in your prompt
2. Extracts story reference (e.g., `2.5.3` or `BAT-123`) from the command
3. Finds the story file in `docs/stories/` using the reference
4. Extracts Linear issue ID from the story file or git branch
5. Calls Linear GraphQL API to update issue state to "In Progress"
6. Uses BATbern team ID from `core-config.yaml`

### 2. `linear-stop.sh` - Session Stop Hook
**Trigger**: When Claude finishes responding
**Action**: Posts dev notes as a Linear comment

**How it works**:
1. Finds Linear issue ID (same logic as start hook)
2. Extracts "Dev Agent Record" section from story file
3. Posts extracted dev notes as a Linear comment
4. Formats comment with markdown for readability

## Setup Instructions

### 1. Set LINEAR_API_KEY Environment Variable

You need a Linear API key for the hooks to work.

**Get your API key**:
1. Go to Linear → Settings → API
2. Create a new Personal API Key
3. Copy the key (starts with `lin_api_`)

**Set the environment variable**:

```bash
# Add to your ~/.zshrc or ~/.bashrc
export LINEAR_API_KEY="lin_api_xxxxxxxxxxxxx"

# Or set it in your Claude Code config
```

### 2. Verify Hook Configuration

Check that hooks are registered:

```bash
# This should show SessionStart and Stop hooks
claude /hooks
```

### 3. Test the Hooks

**Test start hook**:
1. Start Claude and activate dev agent: `/dev`
2. Tell dev agent to work on a story: `develop-story 2.5.3` (or any story with a Linear issue)
3. Check Linear - the associated issue should be updated to "In Progress"
4. Verify hook output in terminal (should show "✅ Updated Linear issue BAT-XXX to 'In Progress'")

**Test stop hook**:
1. Work on a story file in `docs/stories/`
2. Ensure the story has a Linear issue link: `**Linear Issue**: [BAT-123](url)`
3. Add some dev notes to the "Dev Agent Record" section
4. End the Claude session
5. Check Linear - issue should have a new comment with dev notes

## Hook Behavior

### Non-Blocking
- Hooks fail silently if:
  - No Linear issue ID found
  - LINEAR_API_KEY not set
  - Linear API request fails
- Development workflow continues uninterrupted

### Issue ID Detection Priority
1. Git branch name (e.g., `feature/BAT-123-description`)
2. Story file Linear issue link (e.g., `**Linear Issue**: [BAT-123](url)`)
3. If neither found, hook exits silently

### Linear API Integration
- Uses Linear GraphQL API directly
- Team ID: `978bcf9f-669c-464b-a0a4-a162072e575c` (BATbern)
- State mapping:
  - Start hook → "In Progress"
  - Stop hook → Adds comment (no state change)

## Troubleshooting

### Hooks not running
```bash
# Check if hooks are configured
cat .claude/settings.json

# Verify hook scripts are executable
ls -la .claude/hooks/

# Enable debug mode
claude --debug
```

### LINEAR_API_KEY issues
```bash
# Verify key is set
echo $LINEAR_API_KEY

# Test Linear API access
curl -X POST https://api.linear.app/graphql \
  -H "Authorization: $LINEAR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"query":"{ viewer { id name } }"}'
```

### No Linear issue ID found
- Ensure git branch includes issue ID: `feature/BAT-123-description`
- OR ensure story file has Linear issue link in header
- Check hook output: hooks log to stderr

### Dev notes not posting
- Verify story file has "Dev Agent Record" section
- Check section has sufficient content (>50 characters)
- Review Linear API response for errors

## Files

- `.claude/settings.json` - Hook configuration (committed to git)
- `.claude/hooks/linear-start.sh` - Start hook script
- `.claude/hooks/linear-stop.sh` - Stop hook script
- `.claude/hooks/README.md` - This file

## Integration with BMAD Workflow

These hooks integrate seamlessly with the BMAD dev agent workflow:

1. **Start Claude**: Launch Claude Code
2. **Activate Dev Agent**: Run `/dev` command
3. **Assign Story**: Run `develop-story {story-ref}` (e.g., `develop-story 2.5.3`)
4. **Start Hook Fires**: Linear issue automatically updated to "In Progress"
5. **Development**: Dev agent implements story tasks
6. **Dev Notes**: Agent updates "Dev Agent Record" section
7. **Stop Hook Fires**: When Claude stops, dev notes automatically posted to Linear
8. **Review**: Product owner sees progress and notes in Linear

This ensures Linear stays synchronized with development progress without manual updates.

### Workflow Example

```bash
# Terminal 1: Start Claude and dev agent
$ claude
Claude> /dev

# Dev agent activates
James: (shows help menu)

# Start working on a story
You> develop-story 2.5.3

# Hook fires automatically → Linear issue BAT-42 updated to "In Progress"
# Dev agent reads story and begins implementation...
# When done, stop hook posts dev notes to Linear
```
