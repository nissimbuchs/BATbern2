#!/usr/bin/env python3
"""
Working Hours Analysis Tool for BATbern Project
Analyzes Claude session logs and git commits to calculate working hours.

Usage:
    python analyze_working_hours.py
    python analyze_working_hours.py --start-date 2025-12-01 --end-date 2026-01-03
    python analyze_working_hours.py --source claude
"""

import argparse
import subprocess
import re
from pathlib import Path
from datetime import datetime, timedelta
from dataclasses import dataclass
from enum import Enum
from typing import List, Dict, Optional
from collections import defaultdict


# ============================================================================
# Data Models
# ============================================================================

class ProjectType(Enum):
    FEATURE = "BATbern-feature"
    MAIN = "BATbern-main"
    DEVELOP = "BATbern-develop"
    UNKNOWN = "unknown"


class DataSource(Enum):
    CLAUDE_SESSION = "claude"
    GIT_COMMITS = "git"


@dataclass
class WorkSession:
    start: datetime
    end: datetime
    project: ProjectType
    source: DataSource

    @property
    def duration_hours(self) -> float:
        return (self.end - self.start).total_seconds() / 3600


@dataclass
class WeeklyStats:
    year: int
    week_num: int
    week_start: str
    week_end: str
    hours_by_project: Dict[ProjectType, float]
    total_hours: float
    session_count: int


# ============================================================================
# Claude Session Parser
# ============================================================================

def parse_claude_sessions(
    debug_dir: Path,
    start_date: datetime,
    end_date: datetime
) -> List[WorkSession]:
    """
    Parse Claude session logs to extract work sessions.

    Reads timestamps from log content and detects idle time (gaps > 1 hour).
    Only counts active work time.
    """
    sessions = []

    if not debug_dir.exists():
        print(f"Warning: Claude debug directory not found: {debug_dir}")
        return sessions

    for session_file in debug_dir.glob("*.txt"):
        try:
            # Get file modification time to filter by date range
            stat = session_file.stat()
            mod_time = datetime.fromtimestamp(stat.st_mtime)

            # Skip if outside date range
            if not (start_date <= mod_time <= end_date):
                continue

            # Extract project and timestamps from file content
            project = extract_project_from_file(session_file)
            active_periods = extract_active_periods_from_log(session_file)

            # Create a WorkSession for each active period
            for start, end in active_periods:
                if start >= end:
                    continue

                sessions.append(WorkSession(
                    start=start,
                    end=end,
                    project=project,
                    source=DataSource.CLAUDE_SESSION
                ))

        except Exception as e:
            print(f"Warning: Could not process {session_file.name}: {e}")
            continue

    return sessions


def extract_project_from_file(file_path: Path) -> ProjectType:
    """
    Extract project type from session log file.

    Looks for "Watching for changes" message containing project path.
    """
    try:
        with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
            # Read first 10KB (project info is early in the file)
            content = f.read(10000)

            if 'BATbern-feature' in content:
                return ProjectType.FEATURE
            elif 'BATbern-main' in content:
                return ProjectType.MAIN
            elif 'BATbern-develop' in content:
                return ProjectType.DEVELOP

    except Exception as e:
        pass

    return ProjectType.UNKNOWN


def extract_active_periods_from_log(file_path: Path) -> List[tuple]:
    """
    Extract active work periods from session log by parsing timestamps.

    Detects idle time: gaps > 1 hour are excluded from work time.
    Returns list of (start, end) tuples representing active periods.
    """
    IDLE_GAP_MINUTES = 60  # Gaps > 1 hour = idle time

    timestamps = []
    timestamp_pattern = re.compile(r'^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})\.\d{3}Z')

    try:
        with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
            for line in f:
                match = timestamp_pattern.match(line)
                if match:
                    try:
                        ts = datetime.strptime(match.group(1), '%Y-%m-%dT%H:%M:%S')
                        timestamps.append(ts)
                    except ValueError:
                        continue
    except Exception as e:
        # If we can't parse timestamps, fall back to file timestamps
        stat = file_path.stat()
        mod_time = datetime.fromtimestamp(stat.st_mtime)

        if hasattr(stat, 'st_birthtime'):
            start_time = datetime.fromtimestamp(stat.st_birthtime)
        else:
            start_time = mod_time - timedelta(hours=1)

        return [(start_time, mod_time)]

    if not timestamps:
        return []

    # Sort timestamps
    timestamps.sort()

    # Detect active periods (exclude gaps > IDLE_GAP_MINUTES)
    active_periods = []
    period_start = timestamps[0]
    last_timestamp = timestamps[0]

    for ts in timestamps[1:]:
        gap_minutes = (ts - last_timestamp).total_seconds() / 60

        if gap_minutes > IDLE_GAP_MINUTES:
            # End current active period
            if period_start < last_timestamp:
                active_periods.append((period_start, last_timestamp))

            # Start new active period
            period_start = ts

        last_timestamp = ts

    # Add final active period
    if period_start < last_timestamp:
        active_periods.append((period_start, last_timestamp))

    return active_periods


# ============================================================================
# Git Commit Parser
# ============================================================================

def parse_git_commits(
    repo_paths: Dict[ProjectType, Path],
    start_date: datetime,
    end_date: datetime
) -> List[WorkSession]:
    """
    Parse git commits and detect work sessions.

    Uses commit clustering: commits within 30 minutes = same session.
    """
    sessions = []

    for project, repo_path in repo_paths.items():
        if not repo_path.exists():
            print(f"Warning: Repository not found: {repo_path}")
            continue

        try:
            commits = get_commits(repo_path, start_date, end_date)
            project_sessions = detect_sessions_from_commits(commits, project)
            sessions.extend(project_sessions)
        except Exception as e:
            print(f"Warning: Could not parse git commits for {project.value}: {e}")
            continue

    return sessions


def get_commits(repo_path: Path, start_date: datetime, end_date: datetime) -> List[Dict]:
    """
    Extract commits using git log.

    Returns list of {timestamp, hash} dicts sorted by timestamp.
    """
    cmd = [
        'git', '-C', str(repo_path), 'log', '--all',
        '--format=%ai|%H',
        f'--since={start_date.isoformat()}',
        f'--until={end_date.isoformat()}'
    ]

    result = subprocess.run(cmd, capture_output=True, text=True)

    if result.returncode != 0:
        raise RuntimeError(f"Git command failed: {result.stderr}")

    commits = []
    for line in result.stdout.strip().split('\n'):
        if not line:
            continue

        try:
            timestamp_str, commit_hash = line.split('|')
            # Parse: "2026-01-03 12:38:13 +0100"
            timestamp = datetime.strptime(timestamp_str[:19], "%Y-%m-%d %H:%M:%S")
            commits.append({
                'timestamp': timestamp,
                'hash': commit_hash
            })
        except Exception as e:
            continue

    return sorted(commits, key=lambda c: c['timestamp'])


def detect_sessions_from_commits(commits: List[Dict], project: ProjectType) -> List[WorkSession]:
    """
    Detect work sessions from commit clustering.

    Algorithm:
    - Gap > 30 minutes between commits = new session
    - Session end = last commit + 15 minute buffer
    """
    SESSION_GAP_MINUTES = 30
    COMMIT_BUFFER_MINUTES = 15

    if not commits:
        return []

    sessions = []
    session_start = commits[0]['timestamp']
    last_commit_time = commits[0]['timestamp']

    for commit in commits[1:]:
        gap_minutes = (commit['timestamp'] - last_commit_time).total_seconds() / 60

        if gap_minutes > SESSION_GAP_MINUTES:
            # End previous session
            session_end = last_commit_time + timedelta(minutes=COMMIT_BUFFER_MINUTES)
            sessions.append(WorkSession(
                start=session_start,
                end=session_end,
                project=project,
                source=DataSource.GIT_COMMITS
            ))

            # Start new session
            session_start = commit['timestamp']

        last_commit_time = commit['timestamp']

    # Add final session
    session_end = last_commit_time + timedelta(minutes=COMMIT_BUFFER_MINUTES)
    sessions.append(WorkSession(
        start=session_start,
        end=session_end,
        project=project,
        source=DataSource.GIT_COMMITS
    ))

    return sessions


# ============================================================================
# Deduplication
# ============================================================================

def deduplicate_sessions(sessions: List[WorkSession]) -> List[WorkSession]:
    """
    Remove overlapping sessions with two-phase deduplication:

    Phase 1: Remove overlap between data sources (Claude vs git)
    Phase 2: Merge overlapping sessions across projects (parallel work)
    """
    # Phase 1: Deduplicate by data source (Claude over git)
    claude_sessions = [s for s in sessions if s.source == DataSource.CLAUDE_SESSION]
    git_sessions = [s for s in sessions if s.source == DataSource.GIT_COMMITS]

    # Keep all Claude sessions
    deduplicated = claude_sessions.copy()

    # Only add git sessions that don't overlap with Claude sessions
    for git_session in git_sessions:
        has_overlap = False

        for claude_session in claude_sessions:
            if sessions_overlap(git_session, claude_session):
                has_overlap = True
                break

        if not has_overlap:
            deduplicated.append(git_session)

    # Phase 2: Merge overlapping sessions across projects
    deduplicated = merge_parallel_project_sessions(deduplicated)

    return deduplicated


def merge_parallel_project_sessions(sessions: List[WorkSession]) -> List[WorkSession]:
    """
    Merge overlapping sessions across different projects.

    When working on multiple projects simultaneously, count the time only once
    but track all projects involved.

    Returns merged sessions with combined project information.
    """
    if not sessions:
        return []

    # Sort sessions by start time
    sorted_sessions = sorted(sessions, key=lambda s: s.start)

    merged = []
    current_start = sorted_sessions[0].start
    current_end = sorted_sessions[0].end
    current_projects = {sorted_sessions[0].project}
    current_source = sorted_sessions[0].source

    for session in sorted_sessions[1:]:
        # Check if this session overlaps with current merged session
        if session.start < current_end:
            # Overlapping - extend the current session and add project
            current_end = max(current_end, session.end)
            current_projects.add(session.project)
            # Prefer Claude source if available
            if session.source == DataSource.CLAUDE_SESSION:
                current_source = DataSource.CLAUDE_SESSION
        else:
            # No overlap - save current session and start new one
            # Use the primary project (first alphabetically for consistency)
            primary_project = sorted(current_projects, key=lambda p: p.value)[0]
            merged.append(WorkSession(
                start=current_start,
                end=current_end,
                project=primary_project,
                source=current_source
            ))

            # Start new session
            current_start = session.start
            current_end = session.end
            current_projects = {session.project}
            current_source = session.source

    # Add final session
    primary_project = sorted(current_projects, key=lambda p: p.value)[0]
    merged.append(WorkSession(
        start=current_start,
        end=current_end,
        project=primary_project,
        source=current_source
    ))

    return merged


def sessions_overlap(session1: WorkSession, session2: WorkSession) -> bool:
    """
    Check if two sessions overlap in time.

    Sessions overlap if:
    - session1 starts before session2 ends, AND
    - session2 starts before session1 ends
    """
    return (session1.start < session2.end and session2.start < session1.end)


# ============================================================================
# Analysis & Aggregation
# ============================================================================

def aggregate_by_week(sessions: List[WorkSession]) -> List[WeeklyStats]:
    """
    Aggregate sessions by ISO week.
    """
    weekly_data = defaultdict(lambda: {
        'hours_by_project': defaultdict(float),
        'sessions': []
    })

    for session in sessions:
        # Get ISO week
        iso_year, iso_week, _ = session.start.isocalendar()
        week_key = (iso_year, iso_week)

        weekly_data[week_key]['hours_by_project'][session.project] += session.duration_hours
        weekly_data[week_key]['sessions'].append(session)

    # Convert to WeeklyStats objects
    weekly_stats = []
    for (year, week_num), data in sorted(weekly_data.items()):
        # Calculate week start/end dates
        week_start = datetime.strptime(f'{year}-W{week_num:02d}-1', '%G-W%V-%u')
        week_end = week_start + timedelta(days=6)

        total_hours = sum(data['hours_by_project'].values())

        weekly_stats.append(WeeklyStats(
            year=year,
            week_num=week_num,
            week_start=week_start.strftime('%Y-%m-%d'),
            week_end=week_end.strftime('%Y-%m-%d'),
            hours_by_project=dict(data['hours_by_project']),
            total_hours=total_hours,
            session_count=len(data['sessions'])
        ))

    return weekly_stats


def calculate_summary(sessions: List[WorkSession]) -> Dict:
    """Calculate overall summary statistics."""
    total_hours = sum(s.duration_hours for s in sessions)
    hours_by_project = defaultdict(float)
    hours_by_source = defaultdict(float)

    for session in sessions:
        hours_by_project[session.project] += session.duration_hours
        hours_by_source[session.source] += session.duration_hours

    return {
        'total_hours': total_hours,
        'total_sessions': len(sessions),
        'hours_by_project': dict(hours_by_project),
        'hours_by_source': dict(hours_by_source),
        'date_range': (
            min(s.start for s in sessions),
            max(s.end for s in sessions)
        ) if sessions else (None, None)
    }


# ============================================================================
# Report Generation
# ============================================================================

def generate_markdown_report(
    weekly_stats: List[WeeklyStats],
    summary: Dict,
    output_file: Path
) -> None:
    """
    Generate comprehensive markdown report.
    """
    report = []

    # Header
    report.append("# BATbern Working Hours Analysis\n")
    if summary['date_range'][0]:
        report.append(f"**Analysis Period**: {summary['date_range'][0].strftime('%Y-%m-%d')} to {summary['date_range'][1].strftime('%Y-%m-%d')}")
    report.append(f"**Generated**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")

    # Executive Summary
    report.append("## Executive Summary\n")
    report.append(f"- **Total Hours**: {summary['total_hours']:.1f} hours")
    report.append(f"- **Total Sessions**: {summary['total_sessions']}")
    if summary['total_sessions'] > 0:
        report.append(f"- **Average Session**: {summary['total_hours'] / summary['total_sessions']:.1f} hours")
    if len(weekly_stats) > 0:
        report.append(f"- **Average Weekly Hours**: {summary['total_hours'] / len(weekly_stats):.1f} hours/week")
    report.append("")

    # Project Breakdown
    report.append("## Hours by Project\n")
    report.append("| Project | Hours | Percentage |")
    report.append("|---------|-------|------------|")
    for project, hours in sorted(
        summary['hours_by_project'].items(),
        key=lambda x: x[1],
        reverse=True
    ):
        pct = (hours / summary['total_hours']) * 100 if summary['total_hours'] > 0 else 0
        report.append(f"| {project.value} | {hours:.1f} | {pct:.1f}% |")
    report.append("")

    # Weekly Breakdown
    report.append("## Weekly Breakdown\n")
    report.append("| Week | Dates | Feature | Main | Develop | Total |")
    report.append("|------|-------|---------|------|---------|-------|")

    for week in weekly_stats:
        feature_hrs = week.hours_by_project.get(ProjectType.FEATURE, 0)
        main_hrs = week.hours_by_project.get(ProjectType.MAIN, 0)
        develop_hrs = week.hours_by_project.get(ProjectType.DEVELOP, 0)

        report.append(
            f"| {week.year}-W{week.week_num:02d} | "
            f"{week.week_start} to {week.week_end} | "
            f"{feature_hrs:.1f}h | {main_hrs:.1f}h | {develop_hrs:.1f}h | "
            f"**{week.total_hours:.1f}h** |"
        )
    report.append("")

    # Data Sources
    report.append("## Data Sources\n")
    for source, hours in summary['hours_by_source'].items():
        pct = (hours / summary['total_hours']) * 100 if summary['total_hours'] > 0 else 0
        report.append(f"- **{source.value}**: {hours:.1f} hours ({pct:.1f}%)")
    report.append("")

    # Methodology
    report.append("## Methodology\n")
    report.append("- **Primary Source**: Claude session logs (`~/.claude/debug/`)")
    report.append("  - Session duration from file creation to modification time")
    report.append("  - Project extracted from working directory path")
    report.append("- **Fallback Source**: Git commit history")
    report.append("  - Sessions detected via commit clustering (30+ min gap = new session)")
    report.append("  - Each session gets 15-minute buffer after last commit")
    report.append("")

    # Write report
    output_file.parent.mkdir(parents=True, exist_ok=True)
    with open(output_file, 'w') as f:
        f.write('\n'.join(report))

    print(f"\n✅ Report generated: {output_file}")


# ============================================================================
# Main & CLI
# ============================================================================

def main():
    parser = argparse.ArgumentParser(
        description='Analyze working hours from Claude sessions and git commits'
    )
    parser.add_argument(
        '--start-date',
        type=lambda s: datetime.fromisoformat(s),
        default=datetime(2025, 10, 1),
        help='Start date (YYYY-MM-DD)'
    )
    parser.add_argument(
        '--end-date',
        type=lambda s: datetime.fromisoformat(s),
        default=datetime.now(),
        help='End date (YYYY-MM-DD)'
    )
    parser.add_argument(
        '--claude-debug-dir',
        type=Path,
        default=Path.home() / '.claude' / 'debug',
        help='Claude debug directory'
    )
    parser.add_argument(
        '--repos',
        nargs=3,
        default=[
            '/Users/nissim/dev/bat/BATbern-feature',
            '/Users/nissim/dev/bat/BATbern-main',
            '/Users/nissim/dev/bat/BATbern-develop'
        ],
        help='Repository paths (feature, main, develop)'
    )
    parser.add_argument(
        '--output',
        type=Path,
        default=None,
        help='Output report file (default: timestamped in current directory)'
    )
    parser.add_argument(
        '--source',
        choices=['claude', 'git', 'both'],
        default='both',
        help='Data source to use'
    )

    args = parser.parse_args()

    # Set default output with timestamp
    if args.output is None:
        timestamp = datetime.now().strftime('%Y%m%d-%H%M%S')
        args.output = Path(__file__).parent / f'working-hours-report-{timestamp}.md'

    print("=" * 70)
    print("BATbern Working Hours Analysis")
    print("=" * 70)
    print(f"Date Range: {args.start_date.strftime('%Y-%m-%d')} to {args.end_date.strftime('%Y-%m-%d')}")
    print(f"Data Source: {args.source}")
    print("")

    # Parse data sources
    sessions = []

    if args.source in ['claude', 'both']:
        print(f"Parsing Claude sessions from {args.claude_debug_dir}...")
        claude_sessions = parse_claude_sessions(
            args.claude_debug_dir,
            args.start_date,
            args.end_date
        )
        sessions.extend(claude_sessions)
        print(f"  ✓ Found {len(claude_sessions)} Claude sessions")

    if args.source in ['git', 'both']:
        print("Parsing git commits...")
        repo_paths = {
            ProjectType.FEATURE: Path(args.repos[0]),
            ProjectType.MAIN: Path(args.repos[1]),
            ProjectType.DEVELOP: Path(args.repos[2])
        }
        git_sessions = parse_git_commits(
            repo_paths,
            args.start_date,
            args.end_date
        )
        sessions.extend(git_sessions)
        print(f"  ✓ Found {len(git_sessions)} git-based sessions")

    if not sessions:
        print("\n❌ No data found for the specified date range.")
        return 1

    # Deduplicate overlapping sessions
    print(f"\nDeduplicating overlapping sessions...")
    print(f"  Before: {len(sessions)} sessions")
    sessions = deduplicate_sessions(sessions)
    print(f"  After: {len(sessions)} sessions (removed {len([s for s in sessions if s.source == DataSource.CLAUDE_SESSION])} duplicates)")

    # Analyze
    print("\nAnalyzing sessions...")
    weekly_stats = aggregate_by_week(sessions)
    summary = calculate_summary(sessions)

    # Generate report
    print("Generating report...")
    generate_markdown_report(weekly_stats, summary, args.output)

    # Print summary to console
    print("\n" + "=" * 70)
    print("SUMMARY")
    print("=" * 70)
    print(f"Total Hours: {summary['total_hours']:.1f}")
    print(f"Total Sessions: {summary['total_sessions']}")
    print(f"\nBy Project:")
    for project, hours in sorted(
        summary['hours_by_project'].items(),
        key=lambda x: x[1],
        reverse=True
    ):
        pct = (hours / summary['total_hours']) * 100 if summary['total_hours'] > 0 else 0
        print(f"  {project.value}: {hours:.1f}h ({pct:.0f}%)")
    print("=" * 70)

    return 0


if __name__ == '__main__':
    exit(main())
