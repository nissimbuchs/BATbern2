#!/bin/bash

# Monitor Parallel Development Progress
# Tracks progress of stories 2.8.1, 2.8.2, 2.8.3 across three worktrees

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Worktree paths
WORKTREE_1="/Users/nissim/dev/bat/BATbern-2.8.1"
WORKTREE_2="/Users/nissim/dev/bat/BATbern-2.8.2"
WORKTREE_3="/Users/nissim/dev/bat/BATbern-2.8.3"

# Story files
STORY_1="$WORKTREE_1/docs/stories/2.8.1.partner-directory.md"
STORY_2="$WORKTREE_2/docs/stories/2.8.2.partner-detail-view.md"
STORY_3="$WORKTREE_3/docs/stories/2.8.3.partner-create-edit-modal.md"

# Function to count tasks
count_tasks() {
    local story_file=$1
    # Count total tasks (both completed and uncompleted, with or without indentation)
    local uncompleted completed total
    uncompleted=$(grep -E "^[ ]*- \[ \]" "$story_file" 2>/dev/null | wc -l | tr -d ' ')
    completed=$(grep -E "^[ ]*- \[x\]" "$story_file" 2>/dev/null | wc -l | tr -d ' ')
    total=$((completed + uncompleted))
    echo "$completed $total"
}

# Function to get story status
get_story_status() {
    local story_file=$1
    grep "^## Status" "$story_file" -A 1 | tail -1
}

# Function to get current task
get_current_task() {
    local story_file=$1
    # Find first uncompleted task
    grep -A 1 "^- \[ \]" "$story_file" | head -1 | sed 's/^- \[ \] //'
}

# Function to get files changed
get_files_changed() {
    local worktree=$1
    cd "$worktree"
    git status --short | wc -l
}

# Function to get last commit
get_last_commit() {
    local worktree=$1
    cd "$worktree"
    git log -1 --pretty=format:"%h - %s" 2>/dev/null || echo "No commits yet"
}

# Function to calculate progress bar
progress_bar() {
    local completed=$1
    local total=$2
    local width=40

    if [ "$total" -eq 0 ]; then
        echo "[                                        ] 0%"
        return
    fi

    local percentage=$((completed * 100 / total))
    local filled=$((completed * width / total))
    local empty=$((width - filled))

    printf "["
    printf "%${filled}s" | tr ' ' '#'
    printf "%${empty}s" | tr ' ' '-'
    printf "] %d%%" "$percentage"
}

# Clear screen
clear

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE} Parallel Development Monitor ${NC}"
echo -e "${BLUE}================================${NC}"
echo ""

# Check if worktrees exist
if [ ! -d "$WORKTREE_1" ]; then
    echo -e "${RED}Error: Worktree 1 not found at $WORKTREE_1${NC}"
    exit 1
fi

if [ ! -d "$WORKTREE_2" ]; then
    echo -e "${RED}Error: Worktree 2 not found at $WORKTREE_2${NC}"
    exit 1
fi

if [ ! -d "$WORKTREE_3" ]; then
    echo -e "${RED}Error: Worktree 3 not found at $WORKTREE_3${NC}"
    exit 1
fi

# Monitor Story 2.8.1
echo -e "${GREEN}Story 2.8.1: Partner Directory${NC}"
echo -e "Branch: feature/story-2.8.1"
echo -e "Path: $WORKTREE_1"
if [ -f "$STORY_1" ]; then
    read completed1 total1 <<< $(count_tasks "$STORY_1")
    status1=$(get_story_status "$STORY_1")
    current1=$(get_current_task "$STORY_1")
    files1=$(get_files_changed "$WORKTREE_1")
    commit1=$(get_last_commit "$WORKTREE_1")

    echo -e "Status: ${YELLOW}$status1${NC}"
    echo -e "Progress: $(progress_bar $completed1 $total1) ($completed1/$total1 tasks)"
    echo -e "Current Task: ${BLUE}$current1${NC}"
    echo -e "Files Changed: $files1"
    echo -e "Last Commit: $commit1"
else
    echo -e "${RED}Story file not found${NC}"
fi
echo ""

# Monitor Story 2.8.2
echo -e "${GREEN}Story 2.8.2: Partner Detail View${NC}"
echo -e "Branch: feature/story-2.8.2"
echo -e "Path: $WORKTREE_2"
if [ -f "$STORY_2" ]; then
    read completed2 total2 <<< $(count_tasks "$STORY_2")
    status2=$(get_story_status "$STORY_2")
    current2=$(get_current_task "$STORY_2")
    files2=$(get_files_changed "$WORKTREE_2")
    commit2=$(get_last_commit "$WORKTREE_2")

    echo -e "Status: ${YELLOW}$status2${NC}"
    echo -e "Progress: $(progress_bar $completed2 $total2) ($completed2/$total2 tasks)"
    echo -e "Current Task: ${BLUE}$current2${NC}"
    echo -e "Files Changed: $files2"
    echo -e "Last Commit: $commit2"
else
    echo -e "${RED}Story file not found${NC}"
fi
echo ""

# Monitor Story 2.8.3
echo -e "${GREEN}Story 2.8.3: Partner Create/Edit Modal${NC}"
echo -e "Branch: feature/story-2.8.3"
echo -e "Path: $WORKTREE_3"
if [ -f "$STORY_3" ]; then
    read completed3 total3 <<< $(count_tasks "$STORY_3")
    status3=$(get_story_status "$STORY_3")
    current3=$(get_current_task "$STORY_3")
    files3=$(get_files_changed "$WORKTREE_3")
    commit3=$(get_last_commit "$WORKTREE_3")

    echo -e "Status: ${YELLOW}$status3${NC}"
    echo -e "Progress: $(progress_bar $completed3 $total3) ($completed3/$total3 tasks)"
    echo -e "Current Task: ${BLUE}$current3${NC}"
    echo -e "Files Changed: $files3"
    echo -e "Last Commit: $commit3"
else
    echo -e "${RED}Story file not found${NC}"
fi
echo ""

# Overall Progress
echo -e "${BLUE}================================${NC}"
echo -e "${BLUE} Overall Progress ${NC}"
echo -e "${BLUE}================================${NC}"
total_tasks=$((total1 + total2 + total3))
total_completed=$((completed1 + completed2 + completed3))
echo -e "Combined: $(progress_bar $total_completed $total_tasks) ($total_completed/$total_tasks tasks)"

# Check if all stories are complete
all_complete=true
if [ "$completed1" -ne "$total1" ]; then all_complete=false; fi
if [ "$completed2" -ne "$total2" ]; then all_complete=false; fi
if [ "$completed3" -ne "$total3" ]; then all_complete=false; fi

if [ "$all_complete" = true ]; then
    echo -e "${GREEN}✅ All stories complete! Ready to merge.${NC}"
else
    echo -e "${YELLOW}⏳ Development in progress...${NC}"
fi

echo ""
echo "Run this script again to refresh status, or use:"
echo "  watch -n 5 ./scripts/monitor-parallel-dev.sh"
