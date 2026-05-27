package ch.batbern.companyuser.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidParameterException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResendConfirmationCodeResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;
import software.amazon.awssdk.services.cognitoidentityprovider.paginators.ListUsersIterable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CognitoConfirmationResendJob (daily nudge for unconfirmed Cognito sign-ups).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CognitoConfirmationResendJob Tests")
class CognitoConfirmationResendJobTest {

    @Mock
    private CognitoIdentityProviderClient cognitoClient;

    private CognitoConfirmationResendJob job;

    private static final String POOL = "eu-central-1_Test";
    private static final String CLIENT = "test-client-id";

    @BeforeEach
    void setUp() {
        // enabled, nudge after 48h, bounded window 48h -> eligible signup age in [48h, 96h)
        job = new CognitoConfirmationResendJob(cognitoClient, POOL, CLIENT, true, 48, 48);
    }

    @Test
    @DisplayName("Disabled: no Cognito interaction at all")
    void disabled_noop() {
        CognitoConfirmationResendJob disabled =
                new CognitoConfirmationResendJob(cognitoClient, POOL, CLIENT, false, 48, 48);
        disabled.resendUnconfirmedSignups();
        verifyNoInteractions(cognitoClient);
    }

    @Test
    @DisplayName("Missing client-id: safe no-op (no scan, no resend)")
    void blankClientId_noop() {
        CognitoConfirmationResendJob unconfigured =
                new CognitoConfirmationResendJob(cognitoClient, POOL, "", true, 48, 48);
        unconfigured.resendUnconfirmedSignups();
        verifyNoInteractions(cognitoClient);
    }

    @Test
    @DisplayName("Unconfirmed within window: fresh code resent via email alias + client-id")
    void unconfirmedInWindow_resends() {
        UserType u = user("sub-1", "stuck@example.ch", UserStatusType.UNCONFIRMED, hoursAgo(72));
        ListUsersIterable pages = paginator(u);
        when(cognitoClient.listUsersPaginator(any(ListUsersRequest.class))).thenReturn(pages);
        when(cognitoClient.resendConfirmationCode(any(ResendConfirmationCodeRequest.class)))
                .thenReturn(ResendConfirmationCodeResponse.builder().build());

        job.resendUnconfirmedSignups();

        ArgumentCaptor<ResendConfirmationCodeRequest> captor =
                ArgumentCaptor.forClass(ResendConfirmationCodeRequest.class);
        verify(cognitoClient).resendConfirmationCode(captor.capture());
        assertThat(captor.getValue().clientId()).isEqualTo(CLIENT);
        assertThat(captor.getValue().username()).isEqualTo("stuck@example.ch");
    }

    @Test
    @DisplayName("Confirmed accounts are never nudged")
    void confirmed_skipped() {
        UserType u = user("sub-2", "ok@example.ch", UserStatusType.CONFIRMED, hoursAgo(72));
        ListUsersIterable pages = paginator(u);
        when(cognitoClient.listUsersPaginator(any(ListUsersRequest.class))).thenReturn(pages);

        job.resendUnconfirmedSignups();

        verify(cognitoClient, never()).resendConfirmationCode(any(ResendConfirmationCodeRequest.class));
    }

    @Test
    @DisplayName("Unconfirmed but still inside the grace window (< after-hours) is not nudged yet")
    void tooNew_skipped() {
        UserType u = user("sub-3", "fresh@example.ch", UserStatusType.UNCONFIRMED, hoursAgo(12));
        ListUsersIterable pages = paginator(u);
        when(cognitoClient.listUsersPaginator(any(ListUsersRequest.class))).thenReturn(pages);

        job.resendUnconfirmedSignups();

        verify(cognitoClient, never()).resendConfirmationCode(any(ResendConfirmationCodeRequest.class));
    }

    @Test
    @DisplayName("Unconfirmed but past the bounded window is left alone")
    void tooOld_skipped() {
        UserType u = user("sub-4", "ancient@example.ch", UserStatusType.UNCONFIRMED, hoursAgo(200));
        ListUsersIterable pages = paginator(u);
        when(cognitoClient.listUsersPaginator(any(ListUsersRequest.class))).thenReturn(pages);

        job.resendUnconfirmedSignups();

        verify(cognitoClient, never()).resendConfirmationCode(any(ResendConfirmationCodeRequest.class));
    }

    @Test
    @DisplayName("A failing resend does not stop the rest of the batch")
    void continuesOnError() {
        UserType bad = user("sub-5", "bad@example.ch", UserStatusType.UNCONFIRMED, hoursAgo(60));
        UserType good = user("sub-6", "good@example.ch", UserStatusType.UNCONFIRMED, hoursAgo(60));
        ListUsersIterable pages = paginator(bad, good);
        when(cognitoClient.listUsersPaginator(any(ListUsersRequest.class))).thenReturn(pages);
        when(cognitoClient.resendConfirmationCode(any(ResendConfirmationCodeRequest.class)))
                .thenThrow(InvalidParameterException.builder().message("already confirmed").build())
                .thenReturn(ResendConfirmationCodeResponse.builder().build());

        job.resendUnconfirmedSignups();

        // Both users attempted despite the first throwing
        verify(cognitoClient, times(2)).resendConfirmationCode(any(ResendConfirmationCodeRequest.class));
    }

    // ---- helpers ----
    private static Instant hoursAgo(long h) {
        return Instant.now().minus(h, ChronoUnit.HOURS);
    }

    private UserType user(String username, String email, UserStatusType status, Instant created) {
        return UserType.builder()
                .username(username)
                .userStatus(status)
                .userCreateDate(created)
                .attributes(AttributeType.builder().name("email").value(email).build())
                .build();
    }

    private ListUsersIterable paginator(UserType... users) {
        ListUsersIterable paginator = mock(ListUsersIterable.class);
        ListUsersResponse response = ListUsersResponse.builder().users(users).build();
        when(paginator.iterator()).thenReturn(List.of(response).iterator());
        return paginator;
    }
}
