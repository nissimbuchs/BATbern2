/**
 * The own-text preview subject (Story 16.5). The server sends it HTML-escaped (`&amp; &lt; &gt;
 * &quot;`) with every filled value in `<mark data-var="name">value</mark>` and every unknown
 * placeholder in `<mark class="unknown">{{x}}</mark>`. It is parsed here into plain segments, never
 * rendered as HTML, so neither the subject nor a value can inject markup.
 */
export interface SubjectSegment {
  text: string;
  /** null for plain text; the variable name for a filled value; 'unknown' for an unknown one. */
  mark: null | { kind: 'var'; name: string } | { kind: 'unknown' };
}

const MARK = /<mark\s+(?:data-var="([^"]*)"|class="unknown")\s*>([\s\S]*?)<\/mark>/g;

const ENTITIES: Record<string, string> = { '&amp;': '&', '&lt;': '<', '&gt;': '>', '&quot;': '"' };

/** Undo the server's escaping in one pass, so `&amp;lt;` becomes `&lt;`, not `<`. */
export function unescapeHtml(text: string): string {
  return text.replace(/&(?:amp|lt|gt|quot);/g, (entity) => ENTITIES[entity]);
}

export function parseHighlightedSubject(highlighted: string): SubjectSegment[] {
  const segments: SubjectSegment[] = [];
  let last = 0;
  for (const match of highlighted.matchAll(MARK)) {
    const index = match.index ?? 0;
    if (index > last) {
      segments.push({ text: unescapeHtml(highlighted.slice(last, index)), mark: null });
    }
    const name = match[1];
    segments.push({
      text: unescapeHtml(match[2]),
      mark: name !== undefined ? { kind: 'var', name: unescapeHtml(name) } : { kind: 'unknown' },
    });
    last = index + match[0].length;
  }
  if (last < highlighted.length) {
    segments.push({ text: unescapeHtml(highlighted.slice(last)), mark: null });
  }
  return segments;
}
