package ch.batbern.companyuser.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Strong random password generator used by Cognito provisioning.
 *
 * <p>Story 11.E.2 (AC4). Generates passwords that satisfy the Cognito User Pool
 * password policy from Story 11.E.1:
 * <ul>
 *   <li>minLength: 8 (we default to 16)</li>
 *   <li>requires lowercase, uppercase, digit, and symbol — at least one each</li>
 *   <li>tempPasswordValidity: 14 days (set on the Cognito side; not the generator's concern)</li>
 * </ul>
 *
 * <p>The symbol set excludes ambiguous characters (none of {@code 0/O/1/l/I}) and any
 * Cognito-rejected characters; the {@code !@#$%&?+-=_*.} set is known to round-trip
 * cleanly through email + clipboard + Cognito's first-login challenge.
 *
 * <p>Used at two call sites:
 * <ol>
 *   <li>{@link UserService#provisionUserWithRole} — throwaway password for the
 *       {@code AdminCreateUser} shell at CONTACTED → READY (speaker never sees it).</li>
 *   <li>{@link UserService#issueInvitationCredentials} — fresh password issued at
 *       READY → INVITED via {@code AdminSetUserPassword(Permanent=false)} and embedded
 *       in the invitation email.</li>
 * </ol>
 */
@Component
public class PasswordGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] LOWERCASE = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final char[] UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final char[] DIGITS = "0123456789".toCharArray();
    // Cognito accepts these symbols cleanly; no copy-paste pitfalls (no quotes, slashes, backticks).
    private static final char[] SYMBOLS = "!@#$%&?+-=_*.".toCharArray();

    public String generate() {
        return generate(16);
    }

    public String generate(int length) {
        if (length < 8) {
            throw new IllegalArgumentException(
                    "Length must be >= 8 to satisfy Cognito password policy");
        }
        char[] result = new char[length];
        // Guarantee one of each character class is present.
        result[0] = LOWERCASE[RANDOM.nextInt(LOWERCASE.length)];
        result[1] = UPPERCASE[RANDOM.nextInt(UPPERCASE.length)];
        result[2] = DIGITS[RANDOM.nextInt(DIGITS.length)];
        result[3] = SYMBOLS[RANDOM.nextInt(SYMBOLS.length)];
        char[] all = (new String(LOWERCASE) + new String(UPPERCASE)
                + new String(DIGITS) + new String(SYMBOLS)).toCharArray();
        for (int i = 4; i < length; i++) {
            result[i] = all[RANDOM.nextInt(all.length)];
        }
        // Fisher-Yates shuffle so the first four positions aren't predictable.
        for (int i = result.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = result[i];
            result[i] = result[j];
            result[j] = tmp;
        }
        return new String(result);
    }
}
