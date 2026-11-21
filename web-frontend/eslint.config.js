import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import tseslint from 'typescript-eslint';
import setStateInEffectBaseline from './eslint.set-state-in-effect-baseline.js';

export default tseslint.config(
  {
    ignores: [
      'dist',
      '.env*',
      'node_modules',
      'build',
      'coverage',
      '*.config.js',
      '*.config.ts',
      '*.html',
      'public/**',
      'src/types/generated/**',
    ],
  },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      // React Compiler rules added in react-hooks v7. The project does not adopt the compiler
      // (#611, owner 2026-11-21), so the compiler-only rules stay off. The three that flag
      // genuinely fragile code are on: refs, purity, and set-state-in-effect (the last with a
      // shrinking baseline of old files, see the block below).
      'react-hooks/static-components': 'off',
      'react-hooks/use-memo': 'off',
      'react-hooks/component-hook-factories': 'off',
      'react-hooks/preserve-manual-memoization': 'off',
      'react-hooks/incompatible-library': 'off',
      'react-hooks/immutability': 'off',
      'react-hooks/globals': 'off',
      'react-hooks/refs': 'error',
      'react-hooks/set-state-in-effect': 'error',
      'react-hooks/error-boundaries': 'off',
      'react-hooks/purity': 'error',
      'react-hooks/set-state-in-render': 'off',
      'react-hooks/unsupported-syntax': 'off',
      'react-hooks/config': 'off',
      'react-hooks/gating': 'off',
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      // Temporarily downgrade to warnings for incremental development
      '@typescript-eslint/no-unused-vars': 'warn',
      '@typescript-eslint/no-explicit-any': 'warn',
    },
  },
  {
    // #611: files that still set state synchronously in an effect. Only shrinks.
    files: setStateInEffectBaseline,
    rules: {
      'react-hooks/set-state-in-effect': 'off',
    },
  },
  {
    // Test files: Allow 'any' for mocking and test assertions
    files: ['**/*.test.{ts,tsx}', '**/__tests__/**/*.{ts,tsx}'],
    rules: {
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/no-unused-vars': 'off',
    },
  }
);
