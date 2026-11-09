/**
 * The production source files of `web-frontend/src` the frontend checks scan: no tests, stories,
 * mocks, fixtures, declaration files or generated types. Shared by check-api-paths.ts (Story
 * 17.3) and check-api-type-copies.ts (Story 17.3c). Plain TypeScript for Node 22 type stripping.
 */
import { readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';

/** Paths relative to the frontend root, `/` separators. */
export const EXCLUDED: readonly RegExp[] = [
  /\.test\.[jt]sx?$/,
  /\.spec\.[jt]sx?$/,
  /\.stories\.[jt]sx?$/,
  /\.d\.ts$/,
  /(^|\/)__tests__\//,
  /(^|\/)__mocks__\//,
  /(^|\/)__fixtures__\//,
  /^src\/mocks\//,
  /^src\/test\//,
  /^src\/types\/generated\//,
];

/** Lists the source files to scan, as paths relative to `root` with `/` separators. */
export function listSourceFiles(root: string, dir = 'src'): string[] {
  const files: string[] = [];
  const walk = (abs: string) => {
    for (const name of readdirSync(abs)) {
      const full = join(abs, name);
      if (statSync(full).isDirectory()) {
        if (name !== 'node_modules') walk(full);
      } else if (/\.(ts|tsx)$/.test(name)) {
        const rel = relative(root, full).split(sep).join('/');
        if (!EXCLUDED.some((re) => re.test(rel))) files.push(rel);
      }
    }
  };
  walk(join(root, dir));
  return files.sort();
}
