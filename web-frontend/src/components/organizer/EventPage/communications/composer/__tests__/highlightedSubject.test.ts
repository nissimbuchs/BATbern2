/** Story 16.5: the own-text preview subject is parsed, never rendered as HTML. */
import { describe, it, expect } from 'vitest';
import { parseHighlightedSubject, unescapeHtml } from '../highlightedSubject';

describe('parseHighlightedSubject', () => {
  it('splits the escaped subject into text and marked values (backend example)', () => {
    // Subject `<b>{{eventTitle}}</b>` with eventTitle `A&B`.
    const segments = parseHighlightedSubject(
      '&lt;b&gt;<mark data-var="eventTitle">A&amp;B</mark>&lt;/b&gt;'
    );
    expect(segments).toEqual([
      { text: '<b>', mark: null },
      { text: 'A&B', mark: { kind: 'var', name: 'eventTitle' } },
      { text: '</b>', mark: null },
    ]);
  });

  it('marks unknown placeholders', () => {
    expect(
      parseHighlightedSubject('Due <mark class="unknown">{{deadlin}}</mark> &quot;now&quot;')
    ).toEqual([
      { text: 'Due ', mark: null },
      { text: '{{deadlin}}', mark: { kind: 'unknown' } },
      { text: ' "now"', mark: null },
    ]);
  });

  it('a subject without marks is one plain segment; empty gives none', () => {
    expect(parseHighlightedSubject('Room change')).toEqual([{ text: 'Room change', mark: null }]);
    expect(parseHighlightedSubject('')).toEqual([]);
  });

  it('unescapes in one pass', () => {
    expect(unescapeHtml('&amp;lt; &lt;&gt;&quot;&amp;')).toBe('&lt; <>"&');
  });
});
