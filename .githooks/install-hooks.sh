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
install_hook "commit-msg"
install_hook "pre-push"

# Configure git to use our hooks directory (alternative method)
git config core.hooksPath "$CUSTOM_HOOKS_DIR"
echo -e "${GREEN}✓ Configured git to use $CUSTOM_HOOKS_DIR${NC}"

# Check the npm tooling the frontend hooks need.
#
# This used to run:
#   npm install --save-dev prettier eslint @commitlint/cli @commitlint/config-conventional
#
# Those are unpinned package names with --save-dev, so npm resolved them to the LATEST
# versions and rewrote web-frontend/package.json AND package-lock.json. Installing git hooks
# silently bumped two devDependencies for every developer who followed README.md — observed
# 2026-08-09: eslint ^10.2.1 -> ^10.8.1 and prettier ^3.8.3 -> ^3.9.6, as an uncommitted
# working-tree change nobody asked for.
#
# All four packages are already devDependencies of web-frontend. Setting up hooks must not
# change what the project depends on, so this now only VERIFIES and tells you what to run.
if [ -d "web-frontend" ] && [ -f "web-frontend/package.json" ]; then
    echo ""
    echo -e "${BLUE}📦 Checking npm tooling for the frontend hooks...${NC}"
    MISSING=""
    for pkg in prettier eslint @commitlint/cli @commitlint/config-conventional; do
        if [ ! -d "web-frontend/node_modules/$pkg" ]; then
            MISSING="$MISSING $pkg"
        fi
    done

    if [ -n "$MISSING" ]; then
        echo -e "${YELLOW}  ⚠ Missing:${MISSING}${NC}"
        echo -e "${YELLOW}    Run: cd web-frontend && npm ci${NC}"
        echo -e "${YELLOW}    (npm ci installs the pinned versions; it will not alter package.json)${NC}"
    else
        echo -e "${GREEN}  ✓ prettier, eslint and commitlint present${NC}"
    fi
fi

# Ensure shared-kernel has gradle wrapper
if [ -d "shared-kernel" ] && [ ! -f "shared-kernel/gradlew" ]; then
    echo ""
    echo -e "${BLUE}🔨 Ensuring Gradle wrapper exists...${NC}"
    cd shared-kernel
    if [ -f "build.gradle" ]; then
        gradle wrapper || echo "Gradle wrapper already exists"
    fi
    cd ..
fi

echo ""
echo -e "${GREEN}✅ Git hooks installation complete!${NC}"
echo ""
echo "Hooks installed:"
echo "  • pre-commit: Runs linting and formatting checks on changed files"
echo "  • commit-msg: Validates commit message format (conventional commits)"
echo "  • pre-push: Runs full test suite before push"
echo ""
echo "Installed hooks:"
ls -la .githooks/ | grep -E '(pre-commit|commit-msg|pre-push)' | awk '{print "  ✓", $9}'
echo ""
echo "Commit Message Format:"
echo "  <type>(<scope>): <description>"
echo "  Types: feat, fix, docs, style, refactor, test, chore, perf, ci, build, revert"
echo ""
echo "To temporarily bypass hooks (not recommended):"
echo "  git commit --no-verify"
echo "  git push --no-verify"
echo ""
echo -e "${YELLOW}⚠️  Hooks help maintain code quality - embrace them!${NC}"