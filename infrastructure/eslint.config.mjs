import js from '@eslint/js';
import globals from 'globals';
import tseslint from 'typescript-eslint';

/**
 * ESLint for the CDK estate (#975).
 *
 * Until #1007 this directory had no formatting or lint check of any kind, which made the code
 * with production write access the least-checked code in the repo. #1007 landed prettier;
 * this is the other half.
 *
 * WHY NON-TYPE-AWARE, since that is the interesting decision. Measured on 2026-08-26 across
 * all 93 tracked files, `recommendedTypeChecked` reported 1609 problems — and of the rules
 * that catch actual bugs it reported NOTHING:
 *
 *     no-floating-promises     0
 *     no-misused-promises      0
 *     await-thenable           0
 *     no-base-to-string        0   (in lib/+bin/)
 *
 * Verified those rules were genuinely evaluated at severity 2 via `--print-config`, rather
 * than silently skipped. The 1609 were almost entirely `no-unsafe-*` cascading off `any`-typed
 * Lambda mock events in tests. Type-aware linting would cost a second tsconfig and ~5x the
 * runtime to buy 17 `no-unnecessary-type-assertion` hits and zero bugs. Revisit if async code
 * lands here — CDK stack construction is synchronous, which is exactly why the promise rules
 * find nothing today.
 */
export default tseslint.config(
  {
    // Prettier needs .prettierignore because it globs the filesystem; ESLint needs this for
    // the same reason. cdk.out alone holds ~1500 synthesised asset files.
    ignores: [
      'node_modules/**',
      'cdk.out/**',
      'dist/**',
      // Vendored third-party bundles — image-resize ships its own sharp install.
      'lib/lambda/*/node_modules/**',
      'coverage/**',
      '**/*.d.ts',
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    // test/global-setup.js and global-teardown.js are plain CommonJS Node scripts. Without
    // this, js.configs.recommended's `no-undef` flags `require`, `module`, `process` and
    // `console` as undefined — 13 errors that are purely a missing environment declaration.
    // The .ts files do not need it: typescript-eslint disables no-undef for TypeScript, where
    // the compiler already answers the question, and tsconfig declares the node types.
    files: ['**/*.js'],
    languageOptions: {
      sourceType: 'commonjs',
      globals: { ...globals.node },
    },
    rules: {
      '@typescript-eslint/no-require-imports': 'off',
    },
  },
  {
    files: ['**/*.ts'],
    rules: {
      // tsconfig.json sets noUnusedLocals/noUnusedParameters to FALSE, so tsc cannot see
      // these at all — this rule is the only thing that catches them, and it found 18 real
      // dead imports and parameters in lib/+bin/ on adoption. Underscore-prefixed args are
      // the conventional opt-out for a signature you must keep but do not use.
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^_' },
      ],
    },
  },
  {
    // Test files legitimately cast mock Cognito/SNS/CloudFront events as `any`: constructing a
    // fully-typed trigger event adds no coverage and obscures the assertion. 268 of the 274
    // no-explicit-any hits were here, against 6 in lib/+bin/. Enforcing it would mean either
    // ~270 inline disables or ~270 pointless full type literals, so the rule is off rather
    // than warned — a warning nobody can act on is the noise this repo already has too much of.
    //
    // Everything else still applies to tests, including no-unused-vars.
    files: ['test/**/*.ts'],
    rules: {
      '@typescript-eslint/no-explicit-any': 'off',
      // ts-jest resolves fixtures and Lambda bundles with require() in a few places where the
      // import would be hoisted above a jest.mock() call.
      '@typescript-eslint/no-require-imports': 'off',
    },
  },
  {
    // Lambda handler sources are bundled by esbuild at synth time, not compiled by the CDK
    // tsc run, and a couple of them require() optional native deps behind a try/catch so a
    // missing module degrades instead of crashing the handler at cold start.
    files: ['lib/lambda/**/*.ts', 'lambda/**/*.ts'],
    rules: {
      '@typescript-eslint/no-require-imports': 'off',
    },
  }
);
