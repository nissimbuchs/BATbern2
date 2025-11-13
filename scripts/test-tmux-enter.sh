#!/bin/bash

# Test different methods of sending Enter to tmux panes

SESSION="parallel-dev-stories"
TEST_PANE="0.0"

echo "Testing different Enter methods in tmux..."
echo "Check pane $TEST_PANE to see results"
echo ""

# Clear the pane first
tmux send-keys -t "$SESSION:$TEST_PANE" "clear" C-m
sleep 1

echo "Method 1: C-m"
tmux send-keys -t "$SESSION:$TEST_PANE" "echo 'Method 1: C-m worked'" C-m
sleep 1

echo "Method 2: Enter keyword"
tmux send-keys -t "$SESSION:$TEST_PANE" "echo 'Method 2: Enter keyword worked'" Enter
sleep 1

echo "Method 3: Command + separate Enter"
tmux send-keys -t "$SESSION:$TEST_PANE" "echo 'Method 3: Separate Enter worked'"
sleep 0.2
tmux send-keys -t "$SESSION:$TEST_PANE" "Enter"
sleep 1

echo "Method 4: KPEnter (keypad enter)"
tmux send-keys -t "$SESSION:$TEST_PANE" "echo 'Method 4: KPEnter worked'" KPEnter
sleep 1

echo "Method 5: \\r (carriage return)"
tmux send-keys -t "$SESSION:$TEST_PANE" "echo 'Method 5: Carriage return worked'" $'\r'
sleep 1

echo ""
echo "Capturing pane output to show results:"
echo "========================================"
tmux capture-pane -t "$SESSION:$TEST_PANE" -p | tail -20
echo "========================================"
echo ""
echo "Which methods showed 'worked' messages?"
echo "Those are the methods that actually executed!"
