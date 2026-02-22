# BATbern Working Hours Analysis

**Analysis Period**: 2025-10-01 to 2026-02-22
**Generated**: 2026-02-22 15:57:06

## Executive Summary

- **Total Hours**: 584.8 hours
- **Total Sessions**: 626
- **Average Session**: 0.9 hours
- **Average Weekly Hours**: 27.8 hours/week

## Hours by Project

| Project | Hours | Percentage |
|---------|-------|------------|
| BATbern-feature | 378.4 | 64.7% |
| BATbern-develop | 176.2 | 30.1% |
| BATbern-main | 25.3 | 4.3% |
| unknown | 5.0 | 0.9% |

## Weekly Breakdown

| Week | Dates | Feature | Main | Develop | Total |
|------|-------|---------|------|---------|-------|
| 2025-W40 | 2025-09-29 to 2025-10-05 | 0.0h | 0.5h | 0.2h | **0.8h** |
| 2025-W41 | 2025-10-06 to 2025-10-12 | 2.9h | 0.0h | 12.4h | **15.3h** |
| 2025-W42 | 2025-10-13 to 2025-10-19 | 2.9h | 2.4h | 15.2h | **20.5h** |
| 2025-W43 | 2025-10-20 to 2025-10-26 | 15.6h | 0.0h | 11.7h | **27.4h** |
| 2025-W44 | 2025-10-27 to 2025-11-02 | 7.8h | 0.2h | 6.2h | **14.2h** |
| 2025-W45 | 2025-11-03 to 2025-11-09 | 0.0h | 8.8h | 9.0h | **17.8h** |
| 2025-W46 | 2025-11-10 to 2025-11-16 | 0.8h | 3.3h | 8.0h | **12.1h** |
| 2025-W47 | 2025-11-17 to 2025-11-23 | 10.2h | 0.0h | 0.9h | **11.1h** |
| 2025-W48 | 2025-11-24 to 2025-11-30 | 5.1h | 4.1h | 4.1h | **13.3h** |
| 2025-W49 | 2025-12-01 to 2025-12-07 | 0.7h | 3.5h | 7.6h | **11.8h** |
| 2025-W50 | 2025-12-08 to 2025-12-14 | 0.3h | 0.0h | 7.4h | **7.7h** |
| 2025-W51 | 2025-12-15 to 2025-12-21 | 11.2h | 0.0h | 17.2h | **28.5h** |
| 2025-W52 | 2025-12-22 to 2025-12-28 | 19.2h | 0.0h | 0.2h | **19.4h** |
| 2026-W01 | 2025-12-29 to 2026-01-04 | 17.2h | 0.0h | 10.7h | **27.9h** |
| 2026-W02 | 2026-01-05 to 2026-01-11 | 3.0h | 0.0h | 18.5h | **21.5h** |
| 2026-W03 | 2026-01-12 to 2026-01-18 | 8.1h | 0.0h | 17.0h | **25.1h** |
| 2026-W04 | 2026-01-19 to 2026-01-25 | 33.4h | 0.4h | 8.9h | **42.7h** |
| 2026-W05 | 2026-01-26 to 2026-02-01 | 2.1h | 0.0h | 10.7h | **16.4h** |
| 2026-W06 | 2026-02-02 to 2026-02-08 | 35.5h | 0.0h | 9.7h | **45.2h** |
| 2026-W07 | 2026-02-09 to 2026-02-15 | 117.1h | 0.3h | 0.2h | **118.3h** |
| 2026-W08 | 2026-02-16 to 2026-02-22 | 85.3h | 1.7h | 0.0h | **87.7h** |

## Data Sources

- **git**: 307.9 hours (52.6%)
- **claude**: 277.0 hours (47.4%)

## Methodology

- **Primary Source**: Claude session logs (`~/.claude/debug/`)
  - Session duration from file creation to modification time
  - Project extracted from working directory path
- **Fallback Source**: Git commit history
  - Sessions detected via commit clustering (30+ min gap = new session)
  - Each session gets 15-minute buffer after last commit
