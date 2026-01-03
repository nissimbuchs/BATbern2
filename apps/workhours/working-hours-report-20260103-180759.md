# BATbern Working Hours Analysis

**Analysis Period**: 2025-10-01 to 2026-01-03
**Generated**: 2026-01-03 18:08:06

## Executive Summary

- **Total Hours**: 589.1 hours
- **Total Sessions**: 476
- **Average Session**: 1.2 hours
- **Average Weekly Hours**: 42.1 hours/week

## Hours by Project

| Project | Hours | Percentage |
|---------|-------|------------|
| BATbern-feature | 459.1 | 77.9% |
| BATbern-develop | 80.6 | 13.7% |
| unknown | 31.6 | 5.4% |
| BATbern-main | 17.8 | 3.0% |

## Weekly Breakdown

| Week | Dates | Feature | Main | Develop | Total |
|------|-------|---------|------|---------|-------|
| 2025-W40 | 2025-09-29 to 2025-10-05 | 13.2h | 0.0h | 3.0h | **16.2h** |
| 2025-W41 | 2025-10-06 to 2025-10-12 | 2.9h | 0.0h | 12.8h | **15.7h** |
| 2025-W42 | 2025-10-13 to 2025-10-19 | 2.9h | 2.4h | 15.2h | **20.5h** |
| 2025-W43 | 2025-10-20 to 2025-10-26 | 15.6h | 0.0h | 11.7h | **27.4h** |
| 2025-W44 | 2025-10-27 to 2025-11-02 | 7.8h | 0.2h | 6.2h | **14.2h** |
| 2025-W45 | 2025-11-03 to 2025-11-09 | 0.0h | 8.8h | 9.0h | **17.8h** |
| 2025-W46 | 2025-11-10 to 2025-11-16 | 2.3h | 3.3h | 13.3h | **18.9h** |
| 2025-W47 | 2025-11-17 to 2025-11-23 | 42.9h | 0.1h | 0.6h | **43.6h** |
| 2025-W48 | 2025-11-24 to 2025-11-30 | 88.2h | 0.0h | 0.9h | **89.1h** |
| 2025-W49 | 2025-12-01 to 2025-12-07 | 23.3h | 2.9h | 2.6h | **31.9h** |
| 2025-W50 | 2025-12-08 to 2025-12-14 | 58.9h | 0.0h | 2.4h | **89.8h** |
| 2025-W51 | 2025-12-15 to 2025-12-21 | 57.3h | 0.0h | 2.9h | **60.2h** |
| 2025-W52 | 2025-12-22 to 2025-12-28 | 81.0h | 0.0h | 0.0h | **81.0h** |
| 2026-W01 | 2025-12-29 to 2026-01-04 | 62.9h | 0.0h | 0.0h | **62.9h** |

## Data Sources

- **git**: 147.3 hours (25.0%)
- **claude**: 441.8 hours (75.0%)

## Methodology

- **Primary Source**: Claude session logs (`~/.claude/debug/`)
  - Session duration from file creation to modification time
  - Project extracted from working directory path
- **Fallback Source**: Git commit history
  - Sessions detected via commit clustering (30+ min gap = new session)
  - Each session gets 15-minute buffer after last commit
