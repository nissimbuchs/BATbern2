#!/bin/bash

# Orchestrate Parallel Development
# Automatically manages 3 dev agents across worktrees using tmux

set -euo pipefail

# Debug mode: set to 1 to see detailed command sending
DEBUG=${DEBUG:-0}

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

SESSION_NAME="parallel-dev-stories"

# Worktree paths
WORKTREE_1="/Users/nissim/dev/bat/BATbern-2.8.1"
WORKTREE_2="/Users/nissim/dev/bat/BATbern-2.8.2"
WORKTREE_3="/Users/nissim/dev/bat/BATbern-2.8.3"

# Story files
STORY_1="$WORKTREE_1/docs/stories/2.8.1.partner-directory.md"
STORY_2="$WORKTREE_2/docs/stories/2.8.2.partner-detail-view.md"
STORY_3="$WORKTREE_3/docs/stories/2.8.3.partner-create-edit-modal.md"

# Log files
LOG_DIR="/tmp/parallel-dev-logs"
mkdir -p "$LOG_DIR"
LOG_1="$LOG_DIR/story-2.8.1.log"
LOG_2="$LOG_DIR/story-2.8.2.log"
LOG_3="$LOG_DIR/story-2.8.3.log"

# Track last task counts
LAST_COMPLETED_1=0
LAST_COMPLETED_2=0
LAST_COMPLETED_3=0

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE} Parallel Development Orchestrator ${NC}"
echo -e "${BLUE}================================${NC}"
echo ""

# Function to count completed tasks
count_completed() {
    local story_file=$1
    grep -E "^[ ]*- \[x\]" "$story_file" 2>/dev/null | wc -l | tr -d ' '
}

# Function to get total tasks
count_total() {
    local story_file=$1
    local uncompleted completed
    uncompleted=$(grep -E "^[ ]*- \[ \]" "$story_file" 2>/dev/null | wc -l | tr -d ' ')
    completed=$(grep -E "^[ ]*- \[x\]" "$story_file" 2>/dev/null | wc -l | tr -d ' ')
    echo $((completed + uncompleted))
}

# Function to check if story is complete
is_story_complete() {
    local story_file=$1
    local uncompleted
    uncompleted=$(grep -E "^[ ]*- \[ \]" "$story_file" 2>/dev/null | wc -l | tr -d ' ')
    [ "$uncompleted" -eq 0 ]
}

# Function to check if Claude is still running in a pane
is_claude_running() {
    local pane=$1
    local output
    output=$(tmux capture-pane -t "$SESSION_NAME:0.$pane" -p | tail -1)

    # Check if we see Claude's prompt (>) or if we see shell prompt (%)
    if echo "$output" | grep -q "^>"; then
        return 0  # Claude is running
    elif echo "$output" | grep -q "%"; then
        return 1  # Shell prompt - Claude has exited
    else
        return 0  # Unknown state, assume running
    fi
}

# Function to wait for Claude to exit
wait_for_claude_exit() {
    local pane=$1
    local story_name=$2
    local max_wait=300  # 5 minutes timeout
    local waited=0

    echo -e "${YELLOW}[Pane $pane - $story_name]${NC} Waiting for agent to exit..."

    while is_claude_running "$pane"; do
        sleep 2
        waited=$((waited + 2))

        if [ $waited -ge $max_wait ]; then
            echo -e "${RED}[Pane $pane - $story_name]${NC} Timeout waiting for agent to exit"
            return 1
        fi

        # Show progress every 10 seconds
        if [ $((waited % 10)) -eq 0 ]; then
            echo -e "${BLUE}[Pane $pane - $story_name]${NC} Still working... (${waited}s)"
        fi
    done

    echo -e "${GREEN}[Pane $pane - $story_name]${NC} Agent has exited"
    return 0
}

# Function to send command to tmux pane
send_to_pane() {
    local pane=$1
    local command=$2

    if [ "$DEBUG" -eq 1 ]; then
        echo -e "${BLUE}[DEBUG]${NC} Sending to pane $pane: '$command'"
    fi

    # Try multiple methods to ensure Enter is pressed
    # Method 1: Send command + Enter as separate keys
    tmux send-keys -t "$SESSION_NAME:0.$pane" "$command"
    sleep 0.2  # Small delay between command and Enter
    tmux send-keys -t "$SESSION_NAME:0.$pane" "Enter"

    echo -e "${GREEN}[Pane $pane]${NC} Sent: $command"

    if [ "$DEBUG" -eq 1 ]; then
        sleep 0.5
        echo -e "${BLUE}[DEBUG]${NC} Pane output after command:"
        tmux capture-pane -t "$SESSION_NAME:0.$pane" -p | tail -5
        echo ""
    fi
}

# Function to start agent in pane
start_agent() {
    local pane=$1
    local story_name=$2
    echo -e "${YELLOW}[Pane $pane - $story_name]${NC} Starting dev agent..."

    # Send each command separately with proper timing
    send_to_pane "$pane" "claude --permission-mode acceptEdits"
    sleep 3  # Wait for Claude to start

    send_to_pane "$pane" "/BMad:agents:dev"
    sleep 15  # Wait for dev agent to load

    # Tell the agent to implement one task cycle then exit
    send_to_pane "$pane" "develop-story $story_name implement the next uncompleted task (one red/green cycle), update the story tasklist, then type /exit"

    echo -e "${GREEN}[Pane $pane - $story_name]${NC} Agent started and working..."
}

# Check if tmux session exists
if ! tmux has-session -t "$SESSION_NAME" 2>/dev/null; then
    echo -e "${RED}Error: tmux session '$SESSION_NAME' not found${NC}"
    echo "Please run: ./scripts/start-parallel-dev.sh first"
    exit 1
fi

echo -e "${GREEN}✅ Found tmux session: $SESSION_NAME${NC}"
echo ""

# Initialize: Start all three agents
echo -e "${BLUE}Step 1: Starting dev agents in all 3 panes...${NC}"
echo ""

start_agent 0 "Story 2.8.1"
sleep 2
start_agent 1 "Story 2.8.2"
sleep 2
start_agent 2 "Story 2.8.3"

echo ""
echo -e "${BLUE}Step 2: Monitoring progress and auto-restarting on task completion...${NC}"
echo ""
echo "Press Ctrl+C to stop monitoring (agents will continue running)"
echo ""

# Monitoring loop
ITERATION=0
while true; do
    ITERATION=$((ITERATION + 1))

    # Get current task counts
    completed_1=$(count_completed "$STORY_1")
    completed_2=$(count_completed "$STORY_2")
    completed_3=$(count_completed "$STORY_3")

    total_1=$(count_total "$STORY_1")
    total_2=$(count_total "$STORY_2")
    total_3=$(count_total "$STORY_3")

    # Clear screen and show status
    if [ $((ITERATION % 6)) -eq 0 ]; then  # Update display every 30 seconds
        clear
        echo -e "${BLUE}================================${NC}"
        echo -e "${BLUE} Orchestrator Status ${NC}"
        echo -e "${BLUE}================================${NC}"
        echo ""
        echo -e "${GREEN}Story 2.8.1:${NC} $completed_1/$total_1 tasks ($(( completed_1 * 100 / total_1 ))%)"
        echo -e "${GREEN}Story 2.8.2:${NC} $completed_2/$total_2 tasks ($(( completed_2 * 100 / total_2 ))%)"
        echo -e "${GREEN}Story 2.8.3:${NC} $completed_3/$total_3 tasks ($(( completed_3 * 100 / total_3 ))%)"
        echo ""
        total_tasks=$((total_1 + total_2 + total_3))
        total_completed=$((completed_1 + completed_2 + completed_3))
        echo -e "${YELLOW}Overall:${NC} $total_completed/$total_tasks tasks ($(( total_completed * 100 / total_tasks ))%)"
        echo ""
        echo "Monitoring... (checks every 5 seconds)"
    fi

    # Check if any story made progress (completed a task)
    if [ "$completed_1" -gt "$LAST_COMPLETED_1" ]; then
        echo -e "${GREEN}[Story 2.8.1]${NC} Task completed! ($completed_1/$total_1)"
        LAST_COMPLETED_1=$completed_1

        # If not complete, wait for Claude to exit then restart agent
        if ! is_story_complete "$STORY_1"; then
            # Wait for Claude to exit before restarting
            wait_for_claude_exit 0 "Story 2.8.1"
            echo -e "${YELLOW}[Story 2.8.1]${NC} Restarting agent for next task..."
            start_agent 0 "Story 2.8.1"
        else
            echo -e "${GREEN}[Story 2.8.1]${NC} ✅ All tasks complete!"
        fi
    fi

    if [ "$completed_2" -gt "$LAST_COMPLETED_2" ]; then
        echo -e "${GREEN}[Story 2.8.2]${NC} Task completed! ($completed_2/$total_2)"
        LAST_COMPLETED_2=$completed_2

        if ! is_story_complete "$STORY_2"; then
            # Wait for Claude to exit before restarting
            wait_for_claude_exit 1 "Story 2.8.2"
            echo -e "${YELLOW}[Story 2.8.2]${NC} Restarting agent for next task..."
            start_agent 1 "Story 2.8.2"
        else
            echo -e "${GREEN}[Story 2.8.2]${NC} ✅ All tasks complete!"
        fi
    fi

    if [ "$completed_3" -gt "$LAST_COMPLETED_3" ]; then
        echo -e "${GREEN}[Story 2.8.3]${NC} Task completed! ($completed_3/$total_3)"
        LAST_COMPLETED_3=$completed_3

        if ! is_story_complete "$STORY_3"; then
            # Wait for Claude to exit before restarting
            wait_for_claude_exit 2 "Story 2.8.3"
            echo -e "${YELLOW}[Story 2.8.3]${NC} Restarting agent for next task..."
            start_agent 2 "Story 2.8.3"
        else
            echo -e "${GREEN}[Story 2.8.3]${NC} ✅ All tasks complete!"
        fi
    fi

    # Check if all stories are complete
    if is_story_complete "$STORY_1" && is_story_complete "$STORY_2" && is_story_complete "$STORY_3"; then
        echo ""
        echo -e "${GREEN}================================${NC}"
        echo -e "${GREEN} 🎉 ALL STORIES COMPLETE! 🎉 ${NC}"
        echo -e "${GREEN}================================${NC}"
        echo ""
        echo "Next steps:"
        echo "1. Review the code in each worktree"
        echo "2. Run integration tests"
        echo "3. Merge stories sequentially (2.8.1 → 2.8.2 → 2.8.3)"
        echo ""
        echo "See: scripts/PARALLEL-DEV-GUIDE.md for merge instructions"
        exit 0
    fi

    # Wait 5 seconds before next check
    sleep 5
done
