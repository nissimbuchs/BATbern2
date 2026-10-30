package ch.batbern.events.mailcenter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 16.1a AC5: every EMS send through the shared {@code EmailService} passes a
 * {@code MailContext}, so a new send path cannot silently skip the record. Plain source scan, no
 * ArchUnit: for each class that declares an {@code EmailService} field, every
 * {@code field.send...(} call must take the context as its first argument.
 *
 * <p>The first argument counts as a context when it is a variable or call whose name contains
 * "context" or "ctx" (case-insensitive), or a {@code MailContext.} factory call.
 */
@DisplayName("Every EMS send carries a MailContext (Story 16.1a AC5)")
class OutboundMailInventoryGuardTest {

    private static final Path SOURCES = Path.of("src/main/java/ch/batbern/events");

    /** Classes allowed to send without a record, with the reason. */
    private static final Map<String, String> ALLOW_LIST = Map.of(
            "EmailForwardingService.java", "forwards inbound mail unchanged; recorded by Story 16.2 as inbound",
            "InboundEmailConfirmationEmailService.java",
            "newsletter unsubscribe acknowledgement: no event, asked for by the recipient");

    private static final Pattern EMAIL_SERVICE_FIELD = Pattern.compile("\\bEmailService\\s+(\\w+)\\s*[;=,)]");
    private static final Pattern CONTEXT_ARGUMENT = Pattern.compile("(?i)^(mailcontext\\.|[\\w.]*(context|ctx)[\\w]*)");

    @Test
    @DisplayName("no unrecorded send outside the allow-list")
    void everySendCarriesContext() throws IOException {
        List<String> violations = new ArrayList<>();
        int sendCalls = 0;
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (!source.contains("import ch.batbern.shared.service.EmailService;")) {
                    continue;
                }
                Matcher field = EMAIL_SERVICE_FIELD.matcher(source);
                while (field.find()) {
                    Matcher call = Pattern.compile("\\b" + field.group(1) + "\\.(send\\w*)\\s*\\(").matcher(source);
                    while (call.find()) {
                        sendCalls++;
                        String firstArgument = firstArgument(source, call.end());
                        if (!CONTEXT_ARGUMENT.matcher(firstArgument).find()
                                && !ALLOW_LIST.containsKey(file.getFileName().toString())) {
                            violations.add(file.getFileName() + ":" + lineOf(source, call.start()) + " "
                                    + call.group(1) + "(" + firstArgument + ", ...)");
                        }
                    }
                }
            }
        }

        assertThat(sendCalls).as("the scan found the send calls at all").isGreaterThan(15);
        assertThat(violations)
                .as("Pass a MailContext as first argument (see Story 16.1a), or allow-list the class with a reason")
                .isEmpty();
    }

    private static String firstArgument(String source, int from) {
        int depth = 0;
        StringBuilder argument = new StringBuilder();
        for (int i = from; i < source.length(); i++) {
            char c = source.charAt(i);
            if ((c == ',' || c == ')') && depth == 0) {
                break;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            argument.append(c);
        }
        return argument.toString().strip();
    }

    private static int lineOf(String source, int index) {
        return (int) source.substring(0, index).chars().filter(c -> c == '\n').count() + 1;
    }
}
