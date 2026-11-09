/**
 * Comment masking for JavaScript/TypeScript sources, shared by the frontend checks
 * (check-api-paths.ts, Story 17.3; test-ratchet.ts, Story 17.0a; check-api-type-copies.ts, Story
 * 17.3c, which also masks strings). No npm dependencies: plain
 * TypeScript for Node 22 type stripping.
 */

export interface MaskOptions {
  /**
   * Also blank the contents of string literals and the text of template literals (quotes,
   * backticks and `${...}` expressions stay). For checks that look for declarations, not for
   * string values: check-api-type-copies.ts.
   */
  strings?: boolean;
}

/**
 * Replaces comments with spaces (newlines kept). Strings and templates stay intact unless
 * `options.strings` is set.
 */
export function maskComments(src: string, options: MaskOptions = {}): string {
  const out = src.split('');
  let i = 0;
  // Stack of brace depths for `${` inside templates; top < 0 means "inside template text".
  const stack: number[] = [];
  let lastSignificant = '';
  const blank = (from: number, to: number) => {
    for (let k = from; k < to; k++) if (out[k] !== '\n') out[k] = ' ';
  };
  while (i < src.length) {
    const inTemplateText = stack.length > 0 && stack[stack.length - 1] < 0;
    const c = src[i];
    if (inTemplateText) {
      if (c === '\\') {
        if (options.strings) blank(i, i + 2);
        i += 2;
      } else if (c === '`') {
        stack.pop();
        i++;
        lastSignificant = '`';
      } else if (c === '$' && src[i + 1] === '{') {
        stack[stack.length - 1] = 0; // now in expression, depth 0
        i += 2;
        lastSignificant = '{';
      } else {
        if (options.strings) blank(i, i + 1);
        i++;
      }
      continue;
    }
    if (c === '/' && src[i + 1] === '/') {
      const end = src.indexOf('\n', i);
      const stop = end < 0 ? src.length : end;
      blank(i, stop);
      i = stop;
      continue;
    }
    if (c === '/' && src[i + 1] === '*') {
      const end = src.indexOf('*/', i + 2);
      const stop = end < 0 ? src.length : end + 2;
      blank(i, stop);
      i = stop;
      continue;
    }
    if (c === '"' || c === "'") {
      const end = skipQuoted(src, i);
      if (options.strings) blank(i + 1, end - 1);
      i = end;
      lastSignificant = c;
      continue;
    }
    if (c === '`') {
      stack.push(-1);
      i++;
      continue;
    }
    if (c === '/' && (lastSignificant === '' || '(,=:[!&|?{};+-*%<>~^'.includes(lastSignificant))) {
      i = skipRegex(src, i);
      lastSignificant = '/';
      continue;
    }
    if (stack.length > 0) {
      if (c === '{') stack[stack.length - 1]++;
      if (c === '}') {
        if (stack[stack.length - 1] === 0) {
          stack[stack.length - 1] = -1; // back to template text
          i++;
          continue;
        }
        stack[stack.length - 1]--;
      }
    }
    if (!/\s/.test(c)) lastSignificant = /[\w$)\]]/.test(c) ? 'x' : c;
    i++;
  }
  return out.join('');
}

export function skipQuoted(src: string, i: number): number {
  const q = src[i];
  i++;
  while (i < src.length && src[i] !== q && src[i] !== '\n') {
    i += src[i] === '\\' ? 2 : 1;
  }
  return i + 1;
}

function skipRegex(src: string, i: number): number {
  i++;
  let inClass = false;
  while (i < src.length && src[i] !== '\n') {
    const c = src[i];
    if (c === '\\') {
      i += 2;
      continue;
    }
    if (c === '[') inClass = true;
    else if (c === ']') inClass = false;
    else if (c === '/' && !inClass) {
      i++;
      while (/[a-z]/i.test(src[i] ?? '')) i++;
      return i;
    }
    i++;
  }
  return i;
}
