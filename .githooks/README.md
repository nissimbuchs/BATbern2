# Git Hooks for TDD Enforcement

This directory contains git hooks that enforce Test-Driven Development practices for the BATbern Event Management Platform.

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
- Tests exist for all changed files
- Tests pass for changed files
- Code meets linting standards
- TDD workflow is followed (tests before implementation)

**Execution time**: ~10 seconds

### pre-push
Runs before pushing to remote to ensure:
- Full test suite passes
- Coverage thresholds are met (85% overall, 90% for business logic)
- No security issues (credentials, secrets)
- Branch naming conventions are followed

**Execution time**: ~2-5 minutes

## TDD Workflow Enforcement

The hooks enforce the following TDD workflow:

1. **Test Commit** (prefix: `test:`)
   - Should only contain test files
   - No implementation code

2. **Implementation Commit** (prefix: `feat:` or `fix:`)
   - Should have a preceding test commit
   - Implementation to make tests pass

3. **Refactor Commit** (prefix: `refactor:`)
   - Optional cleanup after tests pass
   - Tests must still pass

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

**Frontend (React/TypeScript)**:
- Test files adjacent to source files: `Component.tsx` → `Component.test.tsx`
- Test commands in package.json:
  - `test:unit` - Run unit tests
  - `test:integration` - Run integration tests
  - `test:related` - Run tests for specific files

**Backend (Java/Spring Boot)**:
- Test files in parallel structure: `src/main/java/` → `src/test/unit/`
- Gradle tasks:
  - `test` - Run unit tests
  - `integrationTest` - Run integration tests
  - `jacocoTestReport` - Generate coverage report

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

### Tests failing in hook but passing locally
```bash
# Run the exact command the hook uses
npm run test:related -- [your-file] --run --passWithNoTests

# Check Node/Java versions
node --version
java --version
```

### Hook takes too long
```bash
# Run only essential checks
QUICK_MODE=true git commit -m "your message"

# Or temporarily disable specific checks
SKIP_INTEGRATION_TESTS=true git push
```

## Best Practices

1. **Commit frequently** - Small, focused commits are easier to test
2. **Write tests first** - Follow TDD red-green-refactor cycle
3. **Keep tests fast** - Use mocks and stubs for external dependencies
4. **Fix failures immediately** - Don't accumulate technical debt

## Support

For issues or questions about the git hooks:
1. Check this README first
2. Review the [TDD Workflow Guide](../docs/architecture/tdd-workflow.md)
3. Ask in the #dev-help Slack channel
4. Create an issue in the repository

Remember: **These hooks help maintain code quality. Embrace them!**