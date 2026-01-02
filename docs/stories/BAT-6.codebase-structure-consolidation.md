# BAT-6: Codebase Structure Consolidation

⚠️ **IMPORTANT: Story Content Location**

This file contains **implementation details only** (Dev Agent Record). The full **product view** (User Story, Acceptance Criteria, Tasks, Definition of Done) is maintained in Linear for stakeholder visibility.

**Linear Issue (Product View)**: [BAT-6 - Codebase Structure Consolidation](https://linear.app/batbern/issue/BAT-6/codebase-structure-consolidation)

**Legacy Story ID**: 1.18

---

## Dev Agent Record

### Status
Draft

### Agent Model Used
- Created: N/A (story not yet implemented)

### Story-Specific Implementation

**REFACTORING STORY** - No new features, only structural reorganization.

**Key Principles**:
- Use `git mv` to preserve history
- No functionality changes
- Verify tests pass after each phase
- Update all references systematically

### Migration Path Reference

**Service Directory Mappings**:
```
OLD PATH                                    NEW PATH
services/event-management-service/       → services/event-management/
services/speaker-coordination-service/   → services/speaker-coordination/
services/partner-coordination-service/   → services/partner-coordination/
services/attendee-experience-service/    → services/attendee-experience/
services/company-user-management-service/ → services/company-management/
```

**Test Directory Mappings**:
```
OLD PATH                          NEW PATH
bruno-tests/                   → e2e-tests/api/
web-frontend/e2e/              → e2e-tests/workflows/
infrastructure/test/e2e/       → e2e-tests/infrastructure/
```

**Infrastructure Stack Mappings**:
```
OLD PATH                                NEW PATH
infrastructure/lib/stacks/*.ts       → infrastructure/lib/stacks/{category}/*.ts
                                        (category = global|core|domain|frontend)
```

### References to Update

**Gradle Configuration**:
- `settings.gradle` - project names
- Service `build.gradle` files - dependencies

**Docker Compose**:
- `docker-compose.yml` - service names
- `docker-compose-dev.yml` - service names
- Volume mappings

**CDK Infrastructure**:
- `bin/batbern-infrastructure.ts` - stack imports
- All stack files - cross-stack references
- Lambda function paths
- ECS service definitions

**CI/CD Workflows**:
- `.github/workflows/*.yml` - service paths
- Test paths
- Build paths

**Documentation**:
- `docs/architecture/source-tree.md` - primary update
- `README.md` - if references old structure
- Developer onboarding docs
- Contribution guidelines

### Phase-by-Phase Validation

**After Phase 1 (Service Rename)**:
```bash
./gradlew clean build
# All services must compile
# No broken imports
```

**After Phase 2 (Test Consolidation)**:
```bash
# API tests
cd e2e-tests/api && bruno run

# Frontend e2e
cd e2e-tests/workflows && npm run test:e2e

# Infrastructure tests
cd e2e-tests/infrastructure && npm test
```

**After Phase 3 (Infrastructure Reorganization)**:
```bash
cd infrastructure
npx cdk synth
# All stacks must synthesize without errors
```

**Final Validation**:
```bash
# Complete test suite
./gradlew test           # Unit tests
make test               # All tests

# Verify builds
./gradlew build         # Java services
npm run build           # Frontend
npx cdk synth           # Infrastructure

# Git hooks
git commit -m "test: verify hooks work"
```

### Risk Mitigation Commands

**Backup before starting**:
```bash
git checkout -b backup-before-structure-consolidation
git push origin backup-before-structure-consolidation
git checkout feature/story-1.18-structure-consolidation
```

**Rollback if needed**:
```bash
git reset --hard HEAD~N  # Where N = number of commits to undo
# Or restore from backup branch
```

### File List

**Created Files**:
- e2e-tests/README.md (test documentation)
- e2e-tests/.env.example (test configuration template)
- e2e-tests/fixtures/ (shared test data)
- infrastructure/lib/stacks/global/ (directory)
- infrastructure/lib/stacks/core/ (directory)
- infrastructure/lib/stacks/domain/ (directory)
- infrastructure/lib/stacks/frontend/ (directory)
- Migration guide document

**Modified Files**:
- settings.gradle (project names)
- docker-compose.yml (service names)
- docker-compose-dev.yml (service names)
- All CDK stack files (imports)
- bin/batbern-infrastructure.ts (imports)
- .github/workflows/*.yml (paths)
- docs/architecture/source-tree.md (structure documentation)
- package.json (test scripts)
- scripts/test/*.sh (test paths)
- README.md (if applicable)

**Moved Files** (via git mv):
- 5 service directories (renamed)
- All bruno-tests/* → e2e-tests/api/
- All web-frontend/e2e/* → e2e-tests/workflows/
- All infrastructure/test/e2e/* → e2e-tests/infrastructure/
- 19 infrastructure stacks → categorized subdirectories

**Deleted Files**:
- Empty bruno-tests/ directory
- Empty web-frontend/e2e/ directory
- Empty infrastructure/test/e2e/ directory

### Debug Log References

- (No debug logs yet - story not yet implemented)

### Completion Notes

**Validation Checklist**:
- [ ] All baseline tests still passing
- [ ] All builds succeed (Gradle, npm, CDK)
- [ ] Git hooks functional
- [ ] CI/CD pipelines pass
- [ ] No broken imports or references
- [ ] Migration guide complete

**Success Criteria**:
- Zero functionality changes (pure refactoring)
- Git history preserved (all moves via git mv)
- All tests green
- Documentation accurate

### Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-11-01 | 1.0 | Initial story creation (legacy format) | Winston (Architect) |
| 2025-12-21 | 2.0 | Migrated to Linear-first format | James (Dev) |
