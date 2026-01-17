package ch.batbern.events.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates cryptographically secure 64-character tokens for speaker invitations - Story 6.1 AC3.
 *
 * Tokens are used for passwordless speaker responses to invitations.
 * Uses SecureRandom for cryptographic security.
 *
 * Token format: 64 lowercase hexadecimal characters (256 bits of entropy)
 */
@Component
public class InvitationTokenGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32; // 32 bytes = 64 hex characters = 256 bits

    /**
     * Generate a unique, cryptographically secure 64-character token.
     *
     * @return 64-character lowercase hexadecimal token
     */
    public String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    /**
     * Convert byte array to lowercase hexadecimal string.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
