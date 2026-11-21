# Developer Workflow Guide

## Quick Start

This guide covers the essential developer workflow for the BATbern Event Management Platform, focusing on code quality checks and commit practices.

## One-Time Setup

### 1. Install Git Hooks

After cloning the repository, install the git hooks:

```bash
make install-hooks      # also part of make install
```

This runs `.githooks/install-hooks.sh`, which sets `core.hooksPath` to `.githooks` and activates
the pre-commit, commit-msg and pre-push hooks. It installs no npm packages; the frontend hooks use
the tooling from `npm ci` in `web-frontend/`.

### 2. Verify Installation

```bash
# Check hooks are configured
git config core.hooksPath
# Should output: .githooks

# Verify hooks are executable
ls -la .githooks/
# Should show executable permissions (rwxr-xr-x)
```

## Daily Development Workflow

### Step 1: Make Your Changes

Work on your feature or bug fix as normal:

```bash
# Create a feature branch
git checkout -b feature/your-feature-name

# Make your code changes
# Edit files, write code, etc.
```

### Step 2: Pre-Commit (Automatic)

When you try to commit, the **pre-commit hook** runs automatically:

```bash
git add .
git commit -m "feat(auth): add Google OAuth"
```

The hook (`.githooks/pre-commit`) will:
- Run lint-staged (ESLint and Prettier, may amend and re-stage files) when anything under
  `web-frontend/` is staged, and for staged `infrastructure/**/*.ts`
- Check that the generated API types are up to date when a spec or generated file is staged
- Run Checkstyle when Java files are staged

The commit message is checked separately by the commit-msg hook (next step).

#### If Checks Fail:

```bash
# Fix linting issues automatically
cd web-frontend
npm run lint:fix

# Format code automatically
npm run format

# Then try committing again
git commit -m "feat(auth): add Google OAuth"
```

### Step 3: Commit Message Validation (Automatic)

The **commit-msg hook** validates your commit message format:

#### Valid Format:

```
<type>(<scope>): <description>
```

**Types:** `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`, `ci`, `build`, `revert`

#### Examples:

```bash
# ✅ Good commit messages
git commit -m "feat(auth): add Google OAuth integration"
git commit -m "fix(api): resolve timeout in event creation"
git commit -m "test(user): add unit tests for user service"
git commit -m "docs(readme): update installation instructions"
git commit -m "refactor(auth): extract validation logic"

# ❌ Bad commit messages
git commit -m "added feature"           # Missing type/scope
git commit -m "WIP"                     # Not descriptive
git commit -m "fixed bug"               # Too vague
```

### Step 4: Pre-Push (Automatic)

When you push to remote, the **pre-push hook** runs:

```bash
git push origin feature/your-feature-name
```

The hook (`.githooks/pre-push`) runs the tests of the components the push changes, not
everything: Vitest when the frontend changed, the Gradle tests (unit and integration, so Docker is
needed) of the changed Java modules and of the services a changed `docs/api` spec belongs to, and
the CICD permission test when a workflow changed.

#### If Tests Fail:

Fix the failing tests before pushing:

```bash
# Run frontend tests locally
cd web-frontend
npm run test

# Run backend tests locally
cd shared-kernel
./gradlew test

# Fix failing tests, then try pushing again
git push origin feature/your-feature-name
```

## Bypassing Hooks (Emergency Only)

⚠️ **NOT RECOMMENDED** - Only use in emergency situations:

```bash
# Skip pre-commit hook
git commit --no-verify -m "emergency: fix production issue"

# Skip pre-push hook
git push --no-verify
```

**When to bypass:**
- Critical production hotfix
- Hooks are broken and need fixing
- Temporary workaround (must fix in follow-up commit)

**Never bypass for:**
- "I'll fix it later"
- Tests are failing
- Just to save time

## Common Workflows

### Making a Quick Fix

```bash
# 1. Create branch
git checkout -b fix/quick-issue

# 2. Make changes
# ... edit files ...

# 3. Commit (hooks run automatically)
git commit -m "fix(api): resolve timeout issue"

# 4. Push (tests run automatically)
git push origin fix/quick-issue

# 5. Create pull request
gh pr create --title "Fix: Resolve timeout issue" --body "..."
```

### Working on a Feature

```bash
# 1. Create feature branch
git checkout -b feature/new-dashboard

# 2. Make incremental commits
git add src/components/Dashboard.tsx
git commit -m "feat(dashboard): add dashboard layout"

git add src/components/DashboardWidget.tsx
git commit -m "feat(dashboard): add widget component"

# 3. Push when ready
git push origin feature/new-dashboard

# 4. Create pull request
gh pr create --title "Feature: New Dashboard" --body "..."
```

### Fixing Linting Issues

```bash
# Check what's wrong
cd web-frontend
npm run lint

# Auto-fix most issues
npm run lint:fix

# Format all files
npm run format

# Verify fixes
npm run lint
npm run format:check

# Commit the fixes
git commit -m "style(frontend): fix linting and formatting"
```

## Tips for Success

### 1. Commit Frequently
Make small, focused commits that pass all checks:
- ✅ One logical change per commit
- ✅ Descriptive commit messages
- ✅ All hooks pass before committing

### 2. Run Checks Locally First
Don't wait for hooks to fail:
```bash
# Before committing
npm run lint           # Check linting
npm run format:check   # Check formatting
npm run test          # Run tests

# Fix issues
npm run lint:fix      # Auto-fix linting
npm run format        # Auto-format code
```

### 3. Use Meaningful Commit Messages
Follow conventional commits format:
- **feat**: New feature
- **fix**: Bug fix
- **docs**: Documentation only
- **style**: Formatting, missing semicolons, etc.
- **refactor**: Code restructuring
- **test**: Adding or updating tests
- **chore**: Maintenance tasks

### 4. Keep Tests Passing
- Write tests for new features
- Fix failing tests immediately
- Don't bypass pre-push hook

## Troubleshooting

### Hooks Not Running

```bash
# Check configuration
git config core.hooksPath
# Should output: .githooks

# Reconfigure if needed
git config core.hooksPath .githooks

# Verify hooks are executable
chmod +x .githooks/*
```

### "Permission Denied" Errors

```bash
# Make hooks executable
chmod +x .githooks/pre-commit
chmod +x .githooks/commit-msg
chmod +x .githooks/pre-push
```

### Linting Failures

```bash
# Auto-fix ESLint issues
cd web-frontend
npm run lint:fix

# Format with Prettier
npm run format

# If issues persist, fix manually and commit
```

### Test Failures

```bash
# Run tests with details
npm run test           # Frontend
./gradlew test --info  # Backend

# Fix failing tests
# Update test files or implementation

# Verify fixes
npm run test
```

### Commit Message Rejected

```bash
# Error: Invalid commit message format

# Fix: Use conventional commits format
git commit --amend -m "feat(scope): proper description"
```

## Getting Help

1. **Read this guide** - Most common issues are covered here
2. **Check .githooks/README.md** - Detailed hook documentation
3. **Ask the team** - #dev-help Slack channel
4. **Create an issue** - For bugs or improvements

## Summary

**Daily workflow:**
1. Create feature branch
2. Make changes
3. Commit (hooks run automatically)
4. Fix any issues
5. Push (tests run automatically)
6. Create pull request

**Remember:**
- Hooks help maintain code quality
- Fix issues immediately
- Don't bypass hooks unless emergency
- Write clear commit messages

Happy coding! 🚀
