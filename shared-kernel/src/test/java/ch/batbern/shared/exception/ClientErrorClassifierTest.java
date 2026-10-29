package ch.batbern.shared.exception;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issues #1148, #1157, #1103: client mistakes that reached the catch-all handler answered
 * 500 CRITICAL. The classifier tells a catch-all which exceptions are really the client's.
 */
class ClientErrorClassifierTest {

    @Test
    void should_return400_when_requiredQueryParameterMissing() {
        Optional<ClientErrorClassifier.ClientError> result = ClientErrorClassifier.classify(
                new MissingServletRequestParameterException("scope", "String"));

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.get().message()).contains("scope");
    }

    @Test
    void should_return400_when_requiredHeaderMissing() throws Exception {
        MethodParameter parameter = new MethodParameter(
                ClientErrorClassifierTest.class.getDeclaredMethod("headerTarget", String.class), 0);

        Optional<ClientErrorClassifier.ClientError> result = ClientErrorClassifier.classify(
                new MissingRequestHeaderException("X-Attendee-Username", parameter));

        assertThat(result).map(ClientErrorClassifier.ClientError::status).contains(HttpStatus.BAD_REQUEST);
    }

    @Test
    void should_return415_when_mediaTypeNotSupported() {
        Optional<ClientErrorClassifier.ClientError> result = ClientErrorClassifier.classify(
                new HttpMediaTypeNotSupportedException(
                        MediaType.APPLICATION_FORM_URLENCODED, List.of(MediaType.APPLICATION_JSON)));

        assertThat(result).map(ClientErrorClassifier.ClientError::status)
                .contains(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void should_return404_when_noResourceFound() {
        Optional<ClientErrorClassifier.ClientError> result = ClientErrorClassifier.classify(
                new NoResourceFoundException(HttpMethod.GET, "/api/v1/users/x/nonexistent", "nonexistent"));

        assertThat(result).map(ClientErrorClassifier.ClientError::status).contains(HttpStatus.NOT_FOUND);
    }

    @Test
    void should_return405_when_methodNotSupported() {
        Optional<ClientErrorClassifier.ClientError> result = ClientErrorClassifier.classify(
                new HttpRequestMethodNotSupportedException("PATCH"));

        assertThat(result).map(ClientErrorClassifier.ClientError::status)
                .contains(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void should_return400_when_inputContainsNulByte() {
        SQLException nul = new SQLException(
                "ERROR: invalid byte sequence for encoding \"UTF8\": 0x00", "22021");
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "JDBC exception executing SQL", new RuntimeException("wrapped", nul));

        Optional<ClientErrorClassifier.ClientError> result = ClientErrorClassifier.classify(ex);

        assertThat(result).map(ClientErrorClassifier.ClientError::status).contains(HttpStatus.BAD_REQUEST);
        assertThat(result.get().message()).doesNotContain("UTF8").doesNotContain("SQL");
    }

    @Test
    void should_returnEmpty_when_dataIntegrityViolationIsAConstraint() {
        SQLException unique = new SQLException("duplicate key value", "23505");

        assertThat(ClientErrorClassifier.classify(
                new DataIntegrityViolationException("dup", unique))).isEmpty();
    }

    @Test
    void should_returnEmpty_when_serverError() {
        assertThat(ClientErrorClassifier.classify(new IllegalStateException("boom"))).isEmpty();
        assertThat(ClientErrorClassifier.classify(new NullPointerException())).isEmpty();
    }

    @Test
    void should_returnEmpty_when_springErrorResponseCarries5xx() {
        assertThat(ClientErrorClassifier.classify(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE))).isEmpty();
    }

    @SuppressWarnings("unused")
    private void headerTarget(String header) {
        // target for MethodParameter only
    }
}
