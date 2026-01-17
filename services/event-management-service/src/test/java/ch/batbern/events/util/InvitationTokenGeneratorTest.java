package ch.batbern.events.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for InvitationTokenGenerator - Story 6.1 AC3.
 *
 * Tests cryptographically secure 64-character token generation.
 */
class InvitationTokenGeneratorTest {

    private final InvitationTokenGenerator tokenGenerator = new InvitationTokenGenerator();

    @Test
    void should_generate64CharToken_when_invitationCreated() {
        // When
        String token = tokenGenerator.generateToken();

        // Then
        assertThat(token).hasSize(64);
    }

    @Test
    void should_generateAlphanumericToken_when_generated() {
        // When
        String token = tokenGenerator.generateToken();

        // Then - Token should only contain hex characters (0-9, a-f)
        assertThat(token).matches("^[a-f0-9]{64}$");
    }

    @RepeatedTest(100)
    void should_generateUniqueTokens_when_multipleInvitationsCreated() {
        // When - Generate 100 tokens
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String token = tokenGenerator.generateToken();
            assertThat(tokens.add(token))
                    .withFailMessage("Duplicate token generated: " + token)
                    .isTrue();
        }

        // Then - All tokens should be unique
        assertThat(tokens).hasSize(100);
    }

    @Test
    void should_generateCryptographicallySecureToken_when_generated() {
        // Given - Generate many tokens
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(tokenGenerator.generateToken());
        }

        // Then - All should be unique (probability of collision is astronomically low)
        assertThat(tokens).hasSize(1000);
    }

    @Test
    void should_generateDifferentTokensEachTime_when_calledMultipleTimes() {
        // When
        String token1 = tokenGenerator.generateToken();
        String token2 = tokenGenerator.generateToken();
        String token3 = tokenGenerator.generateToken();

        // Then
        assertThat(token1).isNotEqualTo(token2);
        assertThat(token2).isNotEqualTo(token3);
        assertThat(token1).isNotEqualTo(token3);
    }
}
