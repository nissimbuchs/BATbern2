#!/bin/bash

# BATbern Platform - Git Hooks Installation Script
# Sets up TDD-enforcing git hooks for the project

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🔧 Installing BATbern TDD Git Hooks...${NC}"

# Get the git directory
GIT_DIR=$(git rev-parse --git-dir 2>/dev/null)

if [ -z "$GIT_DIR" ]; then
    echo "Error: Not in a git repository"
    exit 1
fi

# Create hooks directory if it doesn't exist
HOOKS_DIR="$GIT_DIR/hooks"
mkdir -p "$HOOKS_DIR"

# Path to custom hooks
CUSTOM_HOOKS_DIR=".githooks"

if [ ! -d "$CUSTOM_HOOKS_DIR" ]; then
    echo "Error: Custom hooks directory not found at $CUSTOM_HOOKS_DIR"
    exit 1
fi

# Function to install a hook
install_hook() {
    local HOOK_NAME=$1
    local SOURCE="$CUSTOM_HOOKS_DIR/$HOOK_NAME"
    local DEST="$HOOKS_DIR/$HOOK_NAME"

    if [ -f "$SOURCE" ]; then
        # Backup existing hook if it exists
        if [ -f "$DEST" ] && [ ! -L "$DEST" ]; then
            echo -e "${YELLOW}  Backing up existing $HOOK_NAME to $HOOK_NAME.backup${NC}"
            cp "$DEST" "$DEST.backup"
        fi

        # Create symlink to our hook
        echo "  Installing $HOOK_NAME..."
        ln -sf "../../$SOURCE" "$DEST"
        chmod +x "$SOURCE"
        echo -e "${GREEN}  ✓ $HOOK_NAME installed${NC}"
    else
        echo "  ⚠️  $HOOK_NAME not found in $CUSTOM_HOOKS_DIR"
    fi
}

# Install hooks
echo "Installing git hooks..."
install_hook "pre-commit"
install_hook "pre-push"

# Configure git to use our hooks directory (alternative method)
git config core.hooksPath "$CUSTOM_HOOKS_DIR"
echo -e "${GREEN}✓ Configured git to use $CUSTOM_HOOKS_DIR${NC}"

# Install npm packages needed for hooks (if package.json exists)
if [ -f "package.json" ]; then
    echo ""
    echo -e "${BLUE}📦 Installing npm dependencies for hooks...${NC}"
    npm install --save-dev eslint prettier husky lint-staged
fi

# Install gradle wrapper if needed
if [ -f "build.gradle" ] && [ ! -f "gradlew" ]; then
    echo ""
    echo -e "${BLUE}🔨 Installing Gradle wrapper...${NC}"
    gradle wrapper
fi

echo ""
echo -e "${GREEN}✅ Git hooks installation complete!${NC}"
echo ""
echo "Hooks installed:"
echo "  • pre-commit: Runs tests for changed files"
echo "  • pre-push: Runs full test suite before push"
echo ""
echo "TDD Workflow:"
echo "  1. Write failing tests (commit with 'test:' prefix)"
echo "  2. Write code to pass tests (commit with 'feat:' or 'fix:' prefix)"
echo "  3. Refactor if needed (commit with 'refactor:' prefix)"
echo ""
echo "To temporarily bypass hooks (not recommended):"
echo "  git commit --no-verify"
echo "  git push --no-verify"
echo ""
echo -e "${YELLOW}⚠️  Remember: No code without tests!${NC}"