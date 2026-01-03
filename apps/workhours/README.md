# Working Hours Analysis Tool

Analyzes your working hours on the BATbern project by parsing Claude session logs and git commit history.

## Usage

```bash
# Basic usage - analyze all available data (Oct 2025 to today)
python analyze_working_hours.py

# Custom date range
python analyze_working_hours.py --start-date 2025-12-01 --end-date 2026-01-03

# Use only Claude sessions (no git fallback)
python analyze_working_hours.py --source claude

# Use only git commits
python analyze_working_hours.py --source git

# Custom output location
python analyze_working_hours.py --output ../my-report.md
```

## Output

Generates a timestamped markdown report with:
- Total working hours
- Breakdown by project (BATbern-feature/main/develop)
- Weekly breakdown (by ISO week)
- Data source summary (Claude sessions vs git commits)

Example: `working-hours-report-20260103-175230.md`

## Data Sources

### Primary: Claude Session Logs
- Location: `~/.claude/debug/*.txt`
- Accuracy: High - exact session start/end times
- Coverage: October 2025 onwards

### Fallback: Git Commit History
- Locations: BATbern-feature, BATbern-main, BATbern-develop
- Accuracy: Medium - estimates sessions from commit clustering
- Algorithm: 30+ minute gap between commits = new session

## Requirements

- Python 3.7+
- Git repositories at standard locations
- Access to `~/.claude/debug/` directory

## Notes

- Session durations > 6 hours trigger warnings (may include system sleep time)
- Each run creates a new timestamped report (no overwrites)
- First run may take 10-15 seconds to parse all data
