package ch.batbern.gateway.auth;

import com.auth0.jwt.algorithms.Algorithm;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Test utility to generate RSA key pairs for JWT testing.
 * This replaces the insecure Algorithm.none() approach.
 */
public class TestKeyPairGenerator {

    private final KeyPair keyPair;
    private final Algorithm algorithm;

    public TestKeyPairGenerator() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            this.keyPair = keyGen.generateKeyPair();
            this.algorithm = Algorithm.RSA256(
                (RSAPublicKey) keyPair.getPublic(),
                (RSAPrivateKey) keyPair.getPrivate()
            );
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate test RSA key pair", e);
        }
    }

    public Algorithm getAlgorithm() {
        return algorithm;
    }

    public RSAPublicKey getPublicKey() {
        return (RSAPublicKey) keyPair.getPublic();
    }

    public RSAPrivateKey getPrivateKey() {
        return (RSAPrivateKey) keyPair.getPrivate();
    }

    /**
     * Creates an Algorithm for mocking JWKS provider.
     * This returns an algorithm with only the public key (for verification).
     */
    public Algorithm getVerificationAlgorithm() {
        return Algorithm.RSA256((RSAPublicKey) keyPair.getPublic(), null);
    }
}
