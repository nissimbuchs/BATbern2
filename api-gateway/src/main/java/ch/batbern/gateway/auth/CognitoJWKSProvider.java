package ch.batbern.gateway.auth;

import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

public interface CognitoJWKSProvider {
    Algorithm getAlgorithm(DecodedJWT jwt) throws Exception;
}