/** An organizer's initials for the assignee avatar (Story 16.2a): "werner.graf" → "WG". */
export function initials(username: string): string {
  return username
    .split(/[.\-_\s]+/)
    .filter((part) => part && !/^\d+$/.test(part))
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('');
}
