package ch.batbern.shared.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpTest {

    @Test
    @DisplayName("should_ignoreSpoofedFirstHop_when_infrastructureAppendedPeer")
    void should_ignoreSpoofedFirstHop_when_infrastructureAppendedPeer() {
        // Client sent "X-Forwarded-For: 10.1.0.5"; the ALB appended the real peer.
        assertThat(ClientIp.resolve("10.1.0.5, 3.71.123.100", "10.1.2.3")).isEqualTo("3.71.123.100");
        assertThat(ClientIp.resolve("203.0.113.9, 3.71.123.100", "10.1.2.3")).isEqualTo("3.71.123.100");
    }

    @Test
    @DisplayName("should_skipInternalHops_when_proxiesAppendedPrivateAddresses")
    void should_skipInternalHops_when_proxiesAppendedPrivateAddresses() {
        assertThat(ClientIp.resolve("3.127.75.147, 10.1.4.20, 127.0.0.1", "127.0.0.1")).isEqualTo("3.127.75.147");
    }

    @Test
    @DisplayName("should_returnRemoteAddr_when_noPublicHop")
    void should_returnRemoteAddr_when_noPublicHop() {
        assertThat(ClientIp.resolve(null, "198.51.100.7")).isEqualTo("198.51.100.7");
        assertThat(ClientIp.resolve("", "198.51.100.7")).isEqualTo("198.51.100.7");
        assertThat(ClientIp.resolve(" , ", "198.51.100.7")).isEqualTo("198.51.100.7");
        assertThat(ClientIp.resolve("10.0.0.1, 192.168.1.1", "127.0.0.1")).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("should_ignoreGarbage_when_hopIsNotAnIpLiteral")
    void should_ignoreGarbage_when_hopIsNotAnIpLiteral() {
        assertThat(ClientIp.resolve("3.71.123.100, evil.example.com", "10.1.0.1")).isEqualTo("3.71.123.100");
    }

    @Test
    @DisplayName("should_handleIpv6_when_hopIsIpv6")
    void should_handleIpv6_when_hopIsIpv6() {
        assertThat(ClientIp.resolve("2001:db8::1, fd00::5", "::1")).isEqualTo("2001:db8::1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "example.com", "1.2.3", "not an ip", "10.1.0.5;rm"})
    @DisplayName("should_notResolveHostNames_when_parsingLiteral")
    void should_notResolveHostNames_when_parsingLiteral(String value) {
        assertThat(ClientIp.parseLiteral(value)).isEmpty();
    }

    @Test
    @DisplayName("should_splitAndTrimHops")
    void should_splitAndTrimHops() {
        assertThat(ClientIp.hops(" 1.2.3.4 ,, 5.6.7.8 ")).containsExactly("1.2.3.4", "5.6.7.8");
    }
}
