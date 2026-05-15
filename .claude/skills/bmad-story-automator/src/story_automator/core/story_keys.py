from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

from .utils import file_exists, read_text

# Story ID formats supported:
#   - 2-part numeric (e.g. Epic 10):  "10.1", "10.30"        -> kebab "10-1", "10-30"
#   - 3-part with phase letter (e.g. Epic 11): "11.A.1"       -> kebab "11-a-1"
STORY_ID_RE = re.compile(r"^(\d+)\.([A-Z]\.)?(\d+)$")
STORY_PREFIX_RE = re.compile(r"^(\d+)-([a-z]-)?(\d+)$")
STORY_KEY_WITH_SLUG_RE = re.compile(r"^(\d+)-([a-z]-)?(\d+)-.+$")


def story_id_to_prefix(story_id: str) -> str:
    """Convert ``11.A.1`` -> ``11-a-1`` and ``10.1`` -> ``10-1``."""
    return story_id.replace(".", "-").lower()


def prefix_to_story_id(prefix: str) -> str:
    """Convert ``11-a-1`` -> ``11.A.1`` and ``10-1`` -> ``10.1``."""
    parts = prefix.split("-")
    return ".".join(part.upper() if len(part) == 1 and part.isalpha() else part for part in parts)


def story_id_sort_key(value: str) -> tuple:
    """Sort key that handles ``10.1``, ``11.A.1``, and kebab forms with optional slug.

    Accepts ``10-9``, ``10-9-i18n-cleanup``, ``11-a-1``, ``11-a-1-align-...`` etc.

    Letter phase sorts after empty (so 2-part IDs sort before 3-part IDs of the same epic;
    epics in practice use one form or the other).
    """
    match = STORY_ID_RE.match(value)
    if match:
        epic = int(match.group(1))
        phase = (match.group(2) or "").rstrip(".").upper()
        story_num = int(match.group(3))
    else:
        prefix_match = STORY_PREFIX_RE.match(value) or STORY_KEY_WITH_SLUG_RE.match(value)
        if not prefix_match:
            return (0, "", 0, 0)
        epic = int(prefix_match.group(1))
        phase = (prefix_match.group(2) or "").rstrip("-").upper()
        story_num = int(prefix_match.group(3))
    return (epic, phase, 1 if phase else 0, story_num)


@dataclass(frozen=True)
class StoryKey:
    id: str
    prefix: str
    key: str


def sprint_status_file(project_root: str) -> str:
    preferred = Path(project_root) / "_bmad-output" / "implementation-artifacts" / "sprint-status.yaml"
    if preferred.is_file():
        return str(preferred)
    legacy = Path(project_root) / "_bmad-output" / "sprint-status.yaml"
    if legacy.is_file():
        return str(legacy)
    return str(preferred)


def normalize_story_key(project_root: str, value: str) -> StoryKey | None:
    if STORY_ID_RE.match(value):
        story_id = value
        prefix = story_id_to_prefix(value)
        key = ""
    elif STORY_PREFIX_RE.match(value):
        prefix = value
        story_id = prefix_to_story_id(value)
        key = ""
    elif STORY_KEY_WITH_SLUG_RE.match(value):
        key = value
        match = STORY_KEY_WITH_SLUG_RE.match(value)
        epic, phase, story = match.group(1), (match.group(2) or "").rstrip("-"), match.group(3)
        prefix = "-".join(part for part in (epic, phase, story) if part)
        story_id = prefix_to_story_id(prefix)
    else:
        return None

    artifacts = Path(project_root) / "_bmad-output" / "implementation-artifacts"
    if not key:
        matches = sorted(artifacts.glob(f"{prefix}-*.md"))
        if matches:
            key = matches[0].stem
    if not key:
        status_file = sprint_status_file(project_root)
        if file_exists(status_file):
            content = read_text(status_file)
            match = re.search(rf"(?m)^\s*({re.escape(prefix)}-[^:\s]+)\s*:", content)
            if match:
                key = match.group(1).strip()
    if not key:
        key = prefix
    return StoryKey(id=story_id, prefix=prefix, key=key)
