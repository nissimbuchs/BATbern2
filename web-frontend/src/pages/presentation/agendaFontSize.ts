/**
 * Center-stage font size: as large as the row count allows on a 16:9 screen (owner 2026-11-04:
 * "use a lot of the available estate"). 6 rows get the 2.6vw cap, 12 rows about 1.8vw. The vh
 * bound keeps a 4:3 or ultrawide screen from overflowing.
 */
export function centerFontSize(rowCount: number): string {
  const vw = Math.min(2.6, Math.max(1.5, 22 / Math.max(rowCount, 1)));
  const rounded = Math.round(vw * 100) / 100;
  return `min(${rounded}vw, ${Math.round(rounded * (16 / 9) * 100) / 100}vh)`;
}
