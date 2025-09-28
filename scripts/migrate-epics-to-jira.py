#!/usr/bin/env python3
"""
One-time migration script to transfer BATbern epics from markdown to Jira.

This script is designed for a single migration event - after migration,
all epic and story management happens directly in Jira (single source of truth).

Usage:
    python migrate-epics-to-jira.py

Prerequisites:
    - Jira instance set up with BATbern project (key: BAT)
    - Atlassian Remote MCP server configured
    - Claude Code with MCP access to Jira
"""

import re
import json
from pathlib import Path
from typing import Dict, List, Optional
from dataclasses import dataclass


@dataclass
class Story:
    """Represents a user story extracted from epic markdown."""
    title: str
    description: str
    acceptance_criteria: List[str]
    estimated_points: int
    labels: List[str]


@dataclass
class Epic:
    """Represents an epic extracted from markdown."""
    title: str
    description: str
    architecture_context: str
    business_value: str
    stories: List[Story]
    source_file: str


class EpicParser:
    """Parses BATbern epic markdown files and extracts structured data."""

    def __init__(self):
        self.complexity_indicators = {
            'setup': 5, 'infrastructure': 8, 'integration': 5,
            'api': 3, 'frontend': 3, 'database': 5,
            'authentication': 8, 'microservice': 8,
            'deployment': 5, 'testing': 3, 'migration': 8
        }

    def parse_epic_file(self, file_path: Path) -> Epic:
        """Parse a single epic markdown file."""
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()

        return Epic(
            title=self._extract_epic_title(content),
            description=self._extract_epic_overview(content),
            architecture_context=self._extract_architecture_context(content),
            business_value=self._extract_business_value(content),
            stories=self._extract_stories(content),
            source_file=str(file_path)
        )

    def _extract_epic_title(self, content: str) -> str:
        """Extract epic title from markdown."""
        # Look for "Epic Goal" or main header
        match = re.search(r'\*\*Epic Goal\*\*:\s*(.+)', content)
        if match:
            return match.group(1).strip()

        # Fallback to first H1 header
        match = re.search(r'^# (.+)', content, re.MULTILINE)
        if match:
            return match.group(1).strip()

        return "Untitled Epic"

    def _extract_epic_overview(self, content: str) -> str:
        """Extract epic overview/description."""
        # Look for Epic Overview section
        match = re.search(
            r'## Epic Overview\s*\n\n(.+?)(?=\n##|\n---|\Z)',
            content,
            re.DOTALL
        )
        if match:
            return match.group(1).strip()

        return "No overview provided"

    def _extract_architecture_context(self, content: str) -> str:
        """Extract architecture context section."""
        match = re.search(
            r'\*\*Architecture Context\*\*:\s*\n(.+?)(?=\n\*\*|\n---|\n##|\Z)',
            content,
            re.DOTALL
        )
        if match:
            return match.group(1).strip()

        return "No architecture context provided"

    def _extract_business_value(self, content: str) -> str:
        """Extract business value or epic goal."""
        match = re.search(r'\*\*Epic Goal\*\*:\s*(.+)', content)
        if match:
            return match.group(1).strip()

        return "Business value to be defined"

    def _extract_stories(self, content: str) -> List[Story]:
        """Extract user stories from epic content."""
        stories = []

        # Pattern to match user stories
        story_pattern = r'### Story (\d+\.\d+): (.+?)\n\n\*\*User Story:\*\*\s*\n(.+?)\n\n\*\*Acceptance Criteria:\*\*\s*\n((?:- .+?\n)*)'

        matches = re.finditer(story_pattern, content, re.MULTILINE | re.DOTALL)

        for match in matches:
            story_num = match.group(1)
            title = match.group(2).strip()
            user_story = match.group(3).strip()
            criteria_text = match.group(4).strip()

            # Parse acceptance criteria
            acceptance_criteria = []
            if criteria_text:
                criteria_lines = criteria_text.split('\n')
                for line in criteria_lines:
                    line = line.strip()
                    if line.startswith('- '):
                        acceptance_criteria.append(line[2:].strip())

            story = Story(
                title=f"{story_num}: {title}",
                description=user_story,
                acceptance_criteria=acceptance_criteria,
                estimated_points=self._estimate_story_points(title, user_story),
                labels=self._extract_labels(title, user_story)
            )
            stories.append(story)

        return stories

    def _estimate_story_points(self, title: str, description: str) -> int:
        """Estimate story points based on complexity indicators."""
        text = (title + ' ' + description).lower()
        total_points = 0

        for indicator, points in self.complexity_indicators.items():
            if indicator in text:
                total_points += points

        # Apply reasonable bounds
        if total_points == 0:
            return 2  # Minimum for any story
        elif total_points <= 5:
            return 3
        elif total_points <= 10:
            return 5
        elif total_points <= 15:
            return 8
        else:
            return 13  # Cap at 13 points

    def _extract_labels(self, title: str, description: str) -> List[str]:
        """Extract relevant labels from story content."""
        labels = ['BATbern', 'Platform-Rewrite']
        text = (title + ' ' + description).lower()

        # Technical component labels
        if any(term in text for term in ['frontend', 'react', 'ui', 'component']):
            labels.append('Frontend')
        if any(term in text for term in ['backend', 'api', 'service', 'microservice']):
            labels.append('Backend')
        if any(term in text for term in ['database', 'migration', 'data']):
            labels.append('Database')
        if any(term in text for term in ['auth', 'login', 'cognito']):
            labels.append('Authentication')
        if any(term in text for term in ['deploy', 'infrastructure', 'aws']):
            labels.append('Infrastructure')

        return labels


class JiraMigrator:
    """Handles migration of epics and stories to Jira via MCP."""

    def __init__(self):
        self.project_key = "BAT"
        self.migrated_epics = []

    def migrate_epic(self, epic: Epic) -> Dict:
        """
        Migrate a single epic to Jira.

        Note: This method outputs Claude MCP commands that need to be executed.
        In a full implementation, this would use the MCP client directly.
        """

        print(f"\n=== Migrating Epic: {epic.title} ===")

        # Generate epic creation command for Claude
        epic_command = f"""
*jira create-epic \\
  --project={self.project_key} \\
  --title="{epic.title}" \\
  --description="{self._format_epic_description(epic)}" \\
  --labels="BATbern,Platform-Rewrite"
"""

        print("Execute this command in Claude:")
        print(epic_command)

        # Generate story creation commands
        print(f"\nCreate {len(epic.stories)} stories for this epic:")

        for i, story in enumerate(epic.stories, 1):
            story_command = f"""
*jira create-story \\
  --epic="{epic.title}" \\
  --title="{story.title}" \\
  --description="{story.description}" \\
  --acceptance-criteria="{'; '.join(story.acceptance_criteria)}" \\
  --story-points={story.estimated_points} \\
  --labels="{','.join(story.labels)}"
"""
            print(f"\nStory {i}:")
            print(story_command)

        # Track migration
        migration_record = {
            'epic_title': epic.title,
            'source_file': epic.source_file,
            'story_count': len(epic.stories),
            'estimated_total_points': sum(s.estimated_points for s in epic.stories)
        }

        self.migrated_epics.append(migration_record)
        return migration_record

    def _format_epic_description(self, epic: Epic) -> str:
        """Format epic description for Jira."""
        description = f"{epic.description}\n\n"
        description += f"**Architecture Context:**\n{epic.architecture_context}\n\n"
        description += f"**Business Value:**\n{epic.business_value}\n\n"
        description += f"**Source:** {epic.source_file}\n"
        description += f"**Stories:** {len(epic.stories)} user stories"

        return description

    def generate_migration_summary(self) -> Dict:
        """Generate summary of migration process."""
        total_stories = sum(epic['story_count'] for epic in self.migrated_epics)
        total_points = sum(epic['estimated_total_points'] for epic in self.migrated_epics)

        return {
            'total_epics': len(self.migrated_epics),
            'total_stories': total_stories,
            'total_estimated_points': total_points,
            'epics': self.migrated_epics
        }


def main():
    """Main migration function."""
    print("BATbern Epic Migration to Jira")
    print("=" * 40)

    # Initialize components
    parser = EpicParser()
    migrator = JiraMigrator()

    # Find epic files
    docs_path = Path('docs/prd')
    epic_files = list(docs_path.glob('epic-*.md'))

    if not epic_files:
        print("No epic files found in docs/prd/")
        return

    print(f"Found {len(epic_files)} epic files to migrate:")
    for file in epic_files:
        print(f"  - {file.name}")

    print("\nParsing epics...")

    # Parse and migrate each epic
    for epic_file in sorted(epic_files):
        try:
            epic = parser.parse_epic_file(epic_file)
            migration_record = migrator.migrate_epic(epic)

            print(f"✓ Parsed {epic.title}: {len(epic.stories)} stories, "
                  f"{sum(s.estimated_points for s in epic.stories)} estimated points")

        except Exception as e:
            print(f"✗ Error parsing {epic_file.name}: {e}")

    # Generate summary
    summary = migrator.generate_migration_summary()

    print("\n" + "=" * 50)
    print("MIGRATION SUMMARY")
    print("=" * 50)
    print(f"Total Epics: {summary['total_epics']}")
    print(f"Total Stories: {summary['total_stories']}")
    print(f"Total Estimated Points: {summary['total_estimated_points']}")

    # Save summary
    with open('migration_summary.json', 'w') as f:
        json.dump(summary, f, indent=2)

    print("\nMigration commands generated!")
    print("Execute the above commands in Claude to complete the migration.")
    print("After migration, consider archiving the markdown epic files.")


if __name__ == "__main__":
    main()