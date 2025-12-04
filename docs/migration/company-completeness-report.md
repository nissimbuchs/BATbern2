# Company Data Completeness Report

Generated: 2025-11-19T10:43:58Z

## Executive Summary

- **Total Companies Processed**: 70
- **Companies with Logos**: 51 (72.9%)
- **Companies with URLs**: 25 (35.7%)
- **Companies Complete (Logo + URL)**: 19 (27.1%)

## Data Collection Progress by Batch

### Batch 1: Top 5 Companies (10+ speakers) ✅ COMPLETE
**Status**: All 5 companies have logos and URLs

Companies:
1. **SBB CFF FFS** (36 speakers) - ✅ Complete
2. **Die Mobiliar** (22 speakers) - ✅ Complete
3. **Swisscom AG** (20 speakers) - ✅ Complete
4. **PostFinance AG** (17 speakers) - ✅ Complete
5. **ELCA Informatique SA** (10 speakers) - ✅ Complete

**Impact**: 105 speaker mentions covered (100% complete)

### Batch 2: Priority Companies (5-9 speakers) ✅ COMPLETE
**Status**: 4/5 with logos, all 5 with URLs

Companies:
1. **RTC Real-Time Center AG** (9 speakers) - ⚠️ No logo (company dissolved)
2. **BKW AG** (8 speakers) - ✅ Complete
3. **ipt** (5 speakers) - ⚠️ Logo exists, URL added
4. **mtrail GmbH** (5 speakers) - ✅ Complete
5. **PostFinance AG** (5 speakers) - ⚠️ Duplicate (logo-pf)

**Impact**: 32 speaker mentions covered (80% with full data)

### Batch 3: Standard Priority (2-4 speakers) ⚠️ PARTIAL
**Status**: 13/31 with logos, 5/31 with URLs

**Complete** (5 companies):
- ISC-EJPD (4 speakers)
- Die Schweizerische Post (3 speakers)
- Amazon Web Services/AWS (3 speakers)
- Microsoft (2 speakers)
- Raiffeisen Schweiz (2 speakers)

**Has Logo, Needs URL** (8 companies):
- ETH Zürich, EPFL, Dartfish, Finnova AG, IBM, Google Cloud, D ONE Solutions, others

**Needs Logo and URL** (18 companies):
- BLS AG, Puzzle ITC, Zühlke Engineering AG, BIT, HP, PostAuto, and others

### Batch 4: Low Priority (1 speaker each) ⏳ PENDING
**Status**: 29 companies with logos, all need URLs

These are companies with single speakers that already have logo files. URL collection is pending.

## Data Quality Issues Identified

### 1. File Extension in Company Names
**50 companies** have file extensions in the `company` field:
- Examples: "mobiliar.jpg", "sbb.jpg", "logo-pf.jpg"
- **Action Required**: Strip file extensions during migration
- Logo references should be in separate field

### 2. Duplicate Entries
**Identified duplicates**:
- PostFinance (postfinance vs logo-pf)
- AWS (aws vs aws-logo)
- Zühlke (zühlke vs zuehlke - encoding variation)

**Action Required**: Merge duplicates during consolidation

### 3. Unclear Company Names
**Needs clarification**:
- "universität" - Multiple universities, needs disambiguation
- "berner" - Unclear which organization
- Various generic names that need full official names

## Missing Data Summary

### High Priority Missing (5-9 speakers)
- **RTC Real-Time Center AG**: Logo not available (company dissolved 2018)

### Standard Priority Missing (2-4 speakers)
**Logos Needed** (14 companies):
1. BLS AG (4 speakers)
2. Universität/Universities (4 speakers - needs clarification)
3. BIT - Bundesamt für Informatik (3 speakers)
4. HP Hewlett Packard (3 speakers)
5. Puzzle ITC (3 speakers)
6. Zühlke Engineering AG (3 speakers)
7. PostAuto Schweiz AG (3 speakers)
8. Swiss Post Mail (2 speakers)
9. ISB Bund (2 speakers)
10-14. Various others (2 speakers each)

### URLs Needed
**45 companies** have logos but need URLs collected

## Recommendations

### Phase 1: Data Cleanup (Immediate)
1. Remove file extensions from company names in sessions.json
2. Merge duplicate company entries
3. Standardize company display names with official names
4. Clarify ambiguous entries (universität, berner, etc.)

### Phase 2: Logo Collection (Week 1)
**Priority Order**:
1. BLS AG (4 speakers) - swiss transport company
2. BIT (3 speakers) - swiss government
3. Puzzle ITC (3 speakers) - swiss consulting
4. Zühlke Engineering AG (3 speakers) - swiss consulting
5. PostAuto (3 speakers) - swiss transport

### Phase 3: URL Collection (Week 2)
1. Batch 3 companies with logos (8 companies)
2. Batch 4 companies (29 companies, all have logos)
3. Web search for official websites
4. Validate all URLs

### Phase 4: Migration Ready (Week 3)
- Estimated: 90% complete with logos
- Estimated: 95% complete with URLs
- Ready for database entity creation

## Migration Statistics

### Current State
- **Immediately migratable**: 19 companies (27%)
- **Needs URL only**: 32 companies (46%)
- **Needs logo + URL**: 19 companies (27%)

### Target State (After Phase 2-3)
- **Complete data**: ~65 companies (93%)
- **Missing logos only**: ~5 companies (7%)
- **Missing URLs only**: 0 companies (0%)

## Files Generated

1. `/docs/migration/companies.json` - Consolidated company data
2. `/tmp/companies-batch-1.json` - Top 5 companies (complete)
3. `/tmp/companies-batch-2.json` - Priority companies (complete)
4. `/tmp/companies-batch-3.json` - Standard priority (partial)
5. `/tmp/companies-batch-4.json` - Low priority (logos only)

## Next Actions

1. ✅ Review this completeness report
2. ⏳ Decide on logo collection priority/scope
3. ⏳ Run Phase 1 data cleanup
4. ⏳ Collect missing logos (14 high-priority companies)
5. ⏳ Collect missing URLs (45 companies)
6. ⏳ Final consolidation and validation
7. ⏳ Create database migration script

---

**Report generated by**: Claude Code
**Source data**: BAT Events 1-57 (sessions.json, archiv folders, OneDrive)
**Analysis date**: 2025-11-19
