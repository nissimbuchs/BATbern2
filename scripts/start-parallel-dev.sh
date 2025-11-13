#!/bin/bash

# Start Parallel Development with tmux
# Creates a tmux session with 3 panes, each in a different worktree

set -euo pipefail

SESSION_NAME="parallel-dev-stories"

# Check if tmux is installed
if ! command -v tmux &> /dev/null; then
    echo "Error: tmux is not installed. Install with: brew install tmux"
    exit 1
fi

# Check if session already exists
if tmux has-session -t "$SESSION_NAME" 2>/dev/null; then
    echo "Session $SESSION_NAME already exists. Attaching..."
    tmux attach-session -t "$SESSION_NAME"
    exit 0
fi

# Worktree paths
WORKTREE_1="/Users/nissim/dev/bat/BATbern-2.8.1"
WORKTREE_2="/Users/nissim/dev/bat/BATbern-2.8.2"
WORKTREE_3="/Users/nissim/dev/bat/BATbern-2.8.3"

echo "Creating tmux session: $SESSION_NAME"
echo "Layout: 3 horizontal panes for stories 2.8.1, 2.8.2, 2.8.3"
echo ""

# Create new session with first pane
tmux new-session -d -s "$SESSION_NAME" -c "$WORKTREE_1"
tmux send-keys -t "$SESSION_NAME:0.0" "clear" C-m
tmux send-keys -t "$SESSION_NAME:0.0" "echo 'Story 2.8.1: Partner Directory'" C-m
tmux send-keys -t "$SESSION_NAME:0.0" "echo 'Branch: feature/story-2.8.1'" C-m
tmux send-keys -t "$SESSION_NAME:0.0" "echo ''" C-m
tmux send-keys -t "$SESSION_NAME:0.0" "echo 'To start development:'" C-m
tmux send-keys -t "$SESSION_NAME:0.0" "echo '1. Launch Claude Code in this directory'" C-m
tmux send-keys -t "$SESSION_NAME:0.0" "echo '2. Run: /BMad:agents:dev'" C-m
tmux send-keys -t "$SESSION_NAME:0.0" "echo '3. Run: develop-story'" C-m
tmux send-keys -t "$SESSION_NAME:0.0" "echo ''" C-m

# Split horizontally for second pane
tmux split-window -h -t "$SESSION_NAME:0" -c "$WORKTREE_2"
tmux send-keys -t "$SESSION_NAME:0.1" "clear" C-m
tmux send-keys -t "$SESSION_NAME:0.1" "echo 'Story 2.8.2: Partner Detail View'" C-m
tmux send-keys -t "$SESSION_NAME:0.1" "echo 'Branch: feature/story-2.8.2'" C-m
tmux send-keys -t "$SESSION_NAME:0.1" "echo ''" C-m
tmux send-keys -t "$SESSION_NAME:0.1" "echo 'To start development:'" C-m
tmux send-keys -t "$SESSION_NAME:0.1" "echo '1. Launch Claude Code in this directory'" C-m
tmux send-keys -t "$SESSION_NAME:0.1" "echo '2. Run: /BMad:agents:dev'" C-m
tmux send-keys -t "$SESSION_NAME:0.1" "echo '3. Run: develop-story'" C-m
tmux send-keys -t "$SESSION_NAME:0.1" "echo ''" C-m

# Split horizontally for third pane
tmux split-window -h -t "$SESSION_NAME:0" -c "$WORKTREE_3"
tmux send-keys -t "$SESSION_NAME:0.2" "clear" C-m
tmux send-keys -t "$SESSION_NAME:0.2" "echo 'Story 2.8.3: Partner Create/Edit Modal'" C-m
tmux send-keys -t "$SESSION_NAME:0.2" "echo 'Branch: feature/story-2.8.3'" C-m
tmux send-keys -t "$SESSION_NAME:0.2" "echo ''" C-m
tmux send-keys -t "$SESSION_NAME:0.2" "echo 'To start development:'" C-m
tmux send-keys -t "$SESSION_NAME:0.2" "echo '1. Launch Claude Code in this directory'" C-m
tmux send-keys -t "$SESSION_NAME:0.2" "echo '2. Run: /BMad:agents:dev'" C-m
tmux send-keys -t "$SESSION_NAME:0.2" "echo '3. Run: develop-story'" C-m
tmux send-keys -t "$SESSION_NAME:0.2" "echo ''" C-m

# Set equal pane sizes
tmux select-layout -t "$SESSION_NAME:0" even-horizontal

echo "✅ tmux session '$SESSION_NAME' created!"
echo ""
echo "Attaching to session..."
echo ""
echo "tmux cheat sheet:"
echo "  Ctrl+b + arrows  - Navigate between panes"
echo "  Ctrl+b + d       - Detach from session"
echo "  Ctrl+b + z       - Toggle pane zoom"
echo "  Ctrl+b + [       - Enter scroll mode (q to exit)"
echo ""
echo "To monitor progress, run in another terminal:"
echo "  ./scripts/monitor-parallel-dev.sh"
echo "  watch -n 5 ./scripts/monitor-parallel-dev.sh"
echo ""

# Attach to session
tmux attach-session -t "$SESSION_NAME"
