/**
 * Types for the vitest-axe matchers (`expect(results).toHaveNoViolations()`).
 *
 * vitest-axe declares them on the old global `Vi` namespace, which Vitest 4 no longer reads, so
 * tests that use the matcher did not type-check. This puts them on Vitest's own `Assertion`.
 */
import type { AxeMatchers } from 'vitest-axe/matchers';

declare module 'vitest' {
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  interface Assertion extends AxeMatchers {}
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  interface AsymmetricMatchersContaining extends AxeMatchers {}
}
