/**
 * Comment masking for JavaScript/TypeScript sources, shared by the frontend checks
 * (check-api-paths.ts, Story 17.3; test-ratchet.ts, Story 17.0a). No npm dependencies: plain
 * TypeScript for Node 22 type stripping.
 */

/** Replaces comments with spaces (newlines kept), leaving strings and templates intact. */
export function maskComments(src: string): string {
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
      i = skipQuoted(src, i);
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
