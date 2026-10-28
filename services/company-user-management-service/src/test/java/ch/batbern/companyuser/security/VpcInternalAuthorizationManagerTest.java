package ch.batbern.companyuser.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import static org.assertj.core.api.Assertions.assertThat;

class VpcInternalAuthorizationManagerTest {

    private final VpcInternalAuthorizationManager manager = new VpcInternalAuthorizationManager("10.1.0.0/16");

    private static final Authentication ANONYMOUS = new AnonymousAuthenticationToken(
            "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    private boolean granted(String remoteAddr, String xForwardedFor, Authentication auth) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/john.doe");
        request.setRemoteAddr(remoteAddr);
        if (xForwardedFor != null) {
            request.addHeader("X-Forwarded-For", xForwardedFor);
        }
        return manager.authorize(() -> auth, new RequestAuthorizationContext(request)).isGranted();
    }

    @Test
    @DisplayName("should_allow_when_serviceCallsThroughServiceConnect")
    void should_allow_when_serviceCallsThroughServiceConnect() {
        assertThat(granted("127.0.0.1", null, ANONYMOUS)).isTrue();
        assertThat(granted("10.1.3.7", null, ANONYMOUS)).isTrue();
    }

    @Test
    @DisplayName("should_allow_when_everyHopIsInternal")
    void should_allow_when_everyHopIsInternal() {
        assertThat(granted("127.0.0.1", "10.1.4.20", ANONYMOUS)).isTrue();
    }

    @Test
    @DisplayName("should_deny_when_anonymousClientSpoofsVpcFirstHop")
    void should_deny_when_anonymousClientSpoofsVpcFirstHop() {
        // #1132: client sends "X-Forwarded-For: 10.1.0.5", the ALB appends the real peer,
        // and the gateway reaches CUMS through Service Connect (remote address 127.0.0.1).
        assertThat(granted("127.0.0.1", "10.1.0.5, 3.71.123.100", ANONYMOUS)).isFalse();
        assertThat(granted("127.0.0.1", "127.0.0.1, 203.0.113.9", ANONYMOUS)).isFalse();
    }

    @Test
    @DisplayName("should_deny_when_anonymousRequestCameThroughGateway")
    void should_deny_when_anonymousRequestCameThroughGateway() {
        assertThat(granted("127.0.0.1", "3.71.123.100", ANONYMOUS)).isFalse();
    }

    @Test
    @DisplayName("should_deny_when_hopIsNotAnIpLiteral")
    void should_deny_when_hopIsNotAnIpLiteral() {
        assertThat(granted("127.0.0.1", "localhost", ANONYMOUS)).isFalse();
    }

    @Test
    @DisplayName("should_deny_when_remoteAddressIsPublic")
    void should_deny_when_remoteAddressIsPublic() {
        assertThat(granted("203.0.113.9", null, ANONYMOUS)).isFalse();
    }

    @Test
    @DisplayName("should_allow_when_externalCallerIsAuthenticated")
    void should_allow_when_externalCallerIsAuthenticated() {
        Authentication user = new TestingAuthenticationToken("john.doe", "n/a", "ROLE_ATTENDEE");
        user.setAuthenticated(true);
        assertThat(granted("127.0.0.1", "10.1.0.5, 3.71.123.100", user)).isTrue();
    }
}
