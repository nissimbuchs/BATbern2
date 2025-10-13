package ch.batbern.gateway.auth;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.security.interfaces.RSAPublicKey;

@Slf4j
public class DefaultCognitoJWKSProvider implements CognitoJWKSProvider {

    private final JwkProvider jwkProvider;

    public DefaultCognitoJWKSProvider(String jwksUrl) {
        try {
            this.jwkProvider = new UrlJwkProvider(new URL(jwksUrl));
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize JWKS provider", e);
        }
    }

    @Override
    public Algorithm getAlgorithm(DecodedJWT jwt) throws Exception {
        String keyId = jwt.getKeyId();
        if (keyId == null) {
            throw new IllegalArgumentException("Token doesn't have a key ID");
        }

        Jwk jwk = jwkProvider.get(keyId);
        return Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);
    }
}