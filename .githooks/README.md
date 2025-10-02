# Git Hooks for Code Quality

This directory contains git hooks that enforce code quality standards for the BATbern Event Management Platform.

## Installation

Run the installation script to set up the hooks:

```bash
chmod +x .githooks/install-hooks.sh
./.githooks/install-hooks.sh
```

Or manually configure git to use this hooks directory:

```bash
git config core.hooksPath .githooks
```

## Available Hooks

### pre-commit
Runs before each commit to ensure:
- Frontend: ESLint and Prettier checks on changed TypeScript/JavaScript files
- Backend: Checkstyle checks on changed Java files
- Code meets formatting and linting standards

**Execution time**: ~5-10 seconds

### commit-msg
Validates commit message format:
- Must follow conventional commits format: `<type>(<scope>): <description>`
- Valid types: feat, fix, docs, style, refactor, test, chore, perf, ci, build, revert

### pre-push
Runs before pushing to remote to ensure:
- Full test suite passes for frontend and backend
- Code is ready for review

**Execution time**: ~1-3 minutes

## Commit Message Format

All commits must follow conventional commits format:

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

**Examples:**
```
feat(auth): add Google OAuth integration
fix(api): resolve timeout in event creation endpoint
test(user): add unit tests for user service
docs(readme): update installation instructions
```

## Bypassing Hooks

**NOT RECOMMENDED** - Only use in emergency situations:

```bash
# Skip pre-commit hook
git commit --no-verify -m "emergency: fix production issue"

# Skip pre-push hook
git push --no-verify
```

## Configuration

### Environment Variables

- `RUN_E2E_ON_PUSH=true` - Enable E2E tests during pre-push (disabled by default)
- `SKIP_COVERAGE_CHECK=true` - Skip coverage threshold checks (not recommended)

### Project Requirements

The hooks expect the following project structure:

**Frontend (web-frontend/)**:
- ESLint configuration for TypeScript/React
- Prettier configuration for code formatting
- Test command in package.json: `test:run`

**Backend (shared-kernel/, services/*/)**:
- Gradle build configuration
- Checkstyle configuration for Java code style
- Test tasks: `test`, `integrationTest`

## Troubleshooting

### Hook not executing
```bash
# Check hook permissions
ls -la .git/hooks/

# Make hooks executable
chmod +x .githooks/*

# Verify hooks path
git config core.hooksPath
```

### Linting/Formatting failures
```bash
# Auto-fix ESLint issues
cd web-frontend
npm run lint:fix

# Auto-format code with Prettier
npm run format

# Check Node/Java versions
node --version
java --version
```

### Commit message validation failures
Make sure your commit message follows the format:
```
<type>(<scope>): <description>
```

Valid types: feat, fix, docs, style, refactor, test, chore, perf, ci, build, revert

## Best Practices

1. **Commit frequently** - Small, focused commits are easier to review
2. **Write clear commit messages** - Follow conventional commits format
3. **Fix lint/format issues immediately** - Don't bypass hooks
4. **Run tests locally before pushing** - Saves time in CI/CD

## Support

For issues or questions about the git hooks:
1. Check this README first
2. Review the [Developer Workflow Guide](../docs/guides/developer-workflow.md)
3. Ask in the #dev-help Slack channel
4. Create an issue in the repository

Remember: **These hooks help maintain code quality. Embrace them!**