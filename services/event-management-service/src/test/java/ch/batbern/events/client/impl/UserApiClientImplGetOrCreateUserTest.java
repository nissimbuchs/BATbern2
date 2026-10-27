package ch.batbern.events.client.impl;

import ch.batbern.events.dto.generated.users.GetOrCreateUserRequest;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.exception.UserServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link UserApiClientImpl#getOrCreateUser} error mapping. CUMS answers 409
 * {@code EMAIL_LINKED_TO_ACCOUNT} when the email is another user's unverified additional
 * email; the code must survive into the event-management error so the registration form can
 * tell the visitor to sign in instead of showing a generic error.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserApiClientImpl.getOrCreateUser(request) error mapping")
class UserApiClientImplGetOrCreateUserTest {

    private static final String BASE_URL = "http://cums";

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private CacheManager cacheManager;

    private UserApiClientImpl client;

    @BeforeEach
    void setUp() {
        client = new UserApiClientImpl(restTemplate, new ObjectMapper(), cacheManager);
        ReflectionTestUtils.setField(client, "userServiceBaseUrl", BASE_URL);
    }

    private void cumsResponds(HttpStatus status, String body) {
        when(restTemplate.exchange(
                eq(BASE_URL + "/internal/users/get-or-create"),
                eq(HttpMethod.POST), any(HttpEntity.class), eq(GetOrCreateUserResponse.class)))
                .thenThrow(HttpClientErrorException.create(status, status.getReasonPhrase(),
                        new HttpHeaders(), body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
    }

    private GetOrCreateUserRequest request() {
        return new GetOrCreateUserRequest()
                .email("linked@example.com").firstName("A").lastName("B").createIfMissing(true);
    }

    @Test
    @DisplayName("409 EMAIL_LINKED_TO_ACCOUNT keeps status 409 and the error code in the message")
    void should_keepEmailLinkedCode_when_cumsReturns409() {
        cumsResponds(HttpStatus.CONFLICT,
                "{\"status\":409,\"errorCode\":\"EMAIL_LINKED_TO_ACCOUNT\",\"message\":\"linked\"}");

        assertThatThrownBy(() -> client.getOrCreateUser(request()))
                .isInstanceOf(UserServiceException.class)
                .hasMessageStartingWith("EMAIL_LINKED_TO_ACCOUNT")
                .satisfies(e -> assertThat(((UserServiceException) e).getStatusCode()).isEqualTo(409));
    }

    @Test
    @DisplayName("other 4xx keep the generic client-error message")
    void should_keepGenericMessage_when_otherClientError() {
        cumsResponds(HttpStatus.BAD_REQUEST, "{\"status\":400,\"message\":\"bad\"}");

        assertThatThrownBy(() -> client.getOrCreateUser(request()))
                .isInstanceOf(UserServiceException.class)
                .hasMessageStartingWith("Client error getting/creating user");
    }
}
