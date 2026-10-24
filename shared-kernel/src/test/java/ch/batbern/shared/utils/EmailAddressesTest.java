package ch.batbern.shared.utils;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * #1102: one rule for "is this an address we can accept", shared by every service.
 *
 * <p>Accept every address SES can deliver to: any RFC 5322 dot-atom local part in ASCII, any
 * domain including internationalised ones (sent as Punycode). Reject only what SES cannot
 * deliver (Unicode local parts, no SMTPUTF8 support) and what is not an address at all.
 */
class EmailAddressesTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "john.doe@example.com",
        "John.Doe@Example.COM",
        "o'neill@example.ie",
        "first+batbern@example.com",
        "a!#$%&*/=?^_`{|}~-@example.ch",
        "x@sub.domain.example.photography",
        "x@example.engineering",
        "x@xn--mller-kva.ch",
        "x@müller.ch",
        "x@例え.テスト",
        "x@example.xn--p1ai",
        "1234567890@example.com",
    })
    void should_accept_when_sesCanDeliverToAddress(String email) {
        assertThat(EmailAddresses.isAcceptable(email)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        " ",
        "plainaddress",
        "@example.com",
        "x@",
        "x@localhost",
        "x@@example.com",
        "x y@example.com",
        ".x@example.com",
        "x.@example.com",
        "x..y@example.com",
        "x@example..com",
        "x@-example.com",
        "x@example-.com",
        "x@[192.168.0.1]",
        "\"quoted\"@example.com",
        "x@example.com\n",
    })
    void should_reject_when_notAnAddress(String email) {
        assertThat(EmailAddresses.isAcceptable(email)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"jürg@example.ch", "δοκιμή@example.gr", "用户@例子.广告"})
    void should_reject_when_localPartIsNotAscii(String email) {
        assertThat(EmailAddresses.isAcceptable(email)).isFalse();
        assertThat(EmailAddresses.hasNonAsciiLocalPart(email)).isTrue();
    }

    @Test
    void should_rejectOverlongAddresses() {
        assertThat(EmailAddresses.isAcceptable("x".repeat(64) + "@example.com")).isTrue();
        assertThat(EmailAddresses.isAcceptable("x".repeat(65) + "@example.com")).isFalse();
        String label = "a".repeat(63);
        String longDomain = label + "." + label + "." + label + "." + "a".repeat(61) + ".ch"; // 256 > 253
        assertThat(EmailAddresses.isAcceptable("x@" + longDomain)).isFalse();
        assertThat(EmailAddresses.isAcceptable("x@" + "a".repeat(64) + ".ch")).isFalse();
    }

    @Test
    void should_convertDomainToPunycode_when_preparingForDelivery() {
        assertThat(EmailAddresses.toDeliveryForm("Max@Müller.ch")).isEqualTo("Max@xn--mller-kva.ch");
        assertThat(EmailAddresses.toDeliveryForm(" john.doe@example.com ")).isEqualTo("john.doe@example.com");
    }

    @Test
    void should_refuseDeliveryForm_when_addressNotAcceptable() {
        assertThatThrownBy(() -> EmailAddresses.toDeliveryForm("jürg@example.ch"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("local part");
    }

    @Test
    void should_matchOpenApiPattern_when_addressAcceptable() {
        // The OpenAPI specs carry the same rule as a regex so it is enforced at the API edge by the
        // generated @Pattern. Length and Punycode conversion cannot be expressed there; the regex
        // must therefore never reject an address isAcceptable() accepts.
        String[] accepted = {"john.doe@example.com", "o'neill@example.ie", "x@müller.ch", "x@example.xn--p1ai",
            "a!#$%&*/=?^_`{|}~-@example.ch"};
        for (String email : accepted) {
            assertThat(email).matches(EmailAddresses.OPENAPI_PATTERN);
        }
        assertThat("jürg@example.ch").doesNotMatch(EmailAddresses.OPENAPI_PATTERN);
        assertThat("x y@example.com").doesNotMatch(EmailAddresses.OPENAPI_PATTERN);
    }

    @Test
    void should_carrySameRegexInEveryOpenApiSpec() throws IOException {
        // Drift guard: every email pattern in docs/api must be exactly OPENAPI_PATTERN.
        Path apiDir = Path.of("..", "docs", "api");
        Pattern yamlPattern = Pattern.compile("pattern: '(\\^\\[A-Za-z0-9!.*)'$", Pattern.MULTILINE);
        List<Path> specs;
        try (Stream<Path> files = Files.list(apiDir)) {
            specs = files.filter(p -> p.toString().endsWith(".openapi.yml")).toList();
        }
        int found = 0;
        for (Path spec : specs) {
            Matcher m = yamlPattern.matcher(Files.readString(spec));
            while (m.find()) {
                assertThat(m.group(1).replace("''", "'")).as(spec.toString()).isEqualTo(EmailAddresses.OPENAPI_PATTERN);
                found++;
            }
        }
        assertThat(found).as("request email fields carrying the rule").isEqualTo(14);
    }
}
