/**
 * UUID generation that works outside a secure context.
 *
 * `crypto.randomUUID()` is only defined in a SECURE CONTEXT — an HTTPS origin, or
 * localhost. Production is HTTPS so it is always available there. A dev server reached
 * over plain http:// by LAN IP or machine name (e.g. `make dev-native-up` on a remote
 * host, browsed from another machine) has `crypto.randomUUID === undefined`, and calling
 * it throws `TypeError: crypto.randomUUID is not a function`.
 *
 * `crypto.getRandomValues()` has no secure-context requirement, so it covers that case
 * with the same randomness quality. The Math.random path exists only for exotic
 * environments with no Web Crypto at all and is NOT suitable for anything security
 * sensitive — this helper is for correlation IDs and cache-busting seeds, not tokens.
 */
export function safeRandomUUID(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }

  const bytes = new Uint8Array(16);
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    crypto.getRandomValues(bytes);
  } else {
    for (let i = 0; i < bytes.length; i++) {
      bytes[i] = Math.floor(Math.random() * 256);
    }
  }

  // RFC 4122 §4.4: version 4 in the high nibble of byte 6, variant 10xx in byte 8.
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;

  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
