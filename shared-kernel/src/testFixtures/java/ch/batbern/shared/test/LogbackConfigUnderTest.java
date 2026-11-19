package ch.batbern.shared.test;

import ch.batbern.shared.logging.EmailMaskingMessageConverter;
import ch.batbern.shared.logging.EmailMaskingThrowableConverter;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.pattern.CompositeConverter;
import ch.qos.logback.core.pattern.Converter;
import ch.qos.logback.core.pattern.PatternLayoutBase;
import ch.qos.logback.core.status.Status;

import org.slf4j.spi.MDCAdapter;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * A service's real logback configuration, loaded into a fresh {@link LoggerContext} of its own, with
 * every console appender writing into a buffer instead of stdout (#1202).
 *
 * <p>Loads the named classpath resource with logback's {@link JoranConfigurator}, so the encoder,
 * conversion rules and maskers are the production ones; nothing of logback is replaced. The
 * application's global logger context is not touched. {@code <springProperty>} elements need a
 * Spring environment and are skipped by plain Joran; pass their values as {@code contextProperties},
 * together with {@link #springLoggingProperties(Path)} (the {@code logging.pattern.*} the service
 * really runs with). Any other configuration error (a class that does not load, a bad element) fails
 * the load, so a broken production file cannot pass a test that loaded only half of it. So does a
 * pattern layout whose converter chain renders the message or an exception with a converter that
 * does not mask: a pattern without a throwable word gets logback's plain
 * {@code ThrowableProxyConverter} appended, which leaves stack traces in clear text.
 */
public final class LogbackConfigUnderTest implements AutoCloseable {

    private final LoggerContext context = new LoggerContext();
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final URL source;

    private LogbackConfigUnderTest(String resource, Map<String, String> contextProperties) {
        source = Thread.currentThread().getContextClassLoader().getResource(resource);
        if (source == null) {
            throw new IllegalArgumentException("not on the classpath: " + resource);
        }
        context.setName("under-test:" + resource);
        context.setMDCAdapter(new LogbackMDCAdapter());
        contextProperties.forEach(context::putProperty);
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        try {
            configurator.doConfigure(source);
        } catch (JoranException e) {
            throw new IllegalStateException("could not load " + source, e);
        }
        failOnProblems(Status.WARN, "loading");
        redirectAppenders();
    }

    /** Status texts of the Spring Boot elements plain Joran does not know; nothing else is tolerated. */
    private static final Pattern SPRING_ELEMENT_STATUS = Pattern.compile(
            "Ignoring unknown property \\[(springProperty|springProfile)\\]"
                    + " in \\[ch\\.qos\\.logback\\.classic\\.LoggerContext\\]");

    /**
     * @param resource e.g. {@code logback-spring.xml}
     * @param contextProperties values for the file's {@code <springProperty>} names
     */
    public static LogbackConfigUnderTest load(String resource, Map<String, String> contextProperties) {
        return new LogbackConfigUnderTest(resource, contextProperties);
    }

    /** Spring Boot's {@code logging.*} keys and the logback properties it sets from them. */
    private static final Map<String, String> SPRING_LOGGING_KEYS = Map.of(
            "logging.pattern.console", "CONSOLE_LOG_PATTERN",
            "logging.pattern.file", "FILE_LOG_PATTERN",
            "logging.pattern.level", "LOG_LEVEL_PATTERN",
            "logging.pattern.dateformat", "LOG_DATEFORMAT_PATTERN",
            "logging.pattern.correlation", "LOG_CORRELATION_PATTERN",
            "logging.exception-conversion-word", "LOG_EXCEPTION_CONVERSION_WORD");

    /**
     * The logback properties Spring Boot derives from a service's {@code logging.pattern.*} and
     * {@code logging.exception-conversion-word}, read from its real {@code application.properties}
     * and {@code application.yml}: the base document plus the {@code staging} document, which is what
     * production runs. Pass the result to {@link #load} so the test renders the production pattern,
     * not the default one.
     *
     * @param resources e.g. {@code src/main/resources} of the service under test
     */
    public static Map<String, String> springLoggingProperties(Path resources) {
        Map<String, String> found = new LinkedHashMap<>();
        try {
            Path properties = resources.resolve("application.properties");
            if (Files.exists(properties)) {
                Properties loaded = new Properties();
                try (Reader reader = Files.newBufferedReader(properties)) {
                    loaded.load(reader);
                }
                loaded.stringPropertyNames().forEach(key -> collect(key, loaded.getProperty(key), found));
            }
            Path yml = resources.resolve("application.yml");
            if (Files.exists(yml)) {
                try (Reader reader = Files.newBufferedReader(yml)) {
                    for (Object document : new Yaml().loadAll(reader)) {
                        if (document instanceof Map<?, ?> map && appliesInProduction(map)) {
                            flatten("", map, found);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return found;
    }

    private static boolean appliesInProduction(Map<?, ?> document) {
        Object profile = path(document, "spring", "config", "activate", "on-profile");
        return profile == null || "staging".equals(profile);
    }

    private static Object path(Map<?, ?> map, String... keys) {
        Object current = map;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> m)) {
                return null;
            }
            current = m.get(key);
        }
        return current;
    }

    private static void flatten(String prefix, Map<?, ?> map, Map<String, String> found) {
        map.forEach((key, value) -> {
            String name = prefix.isEmpty() ? String.valueOf(key) : prefix + "." + key;
            if (value instanceof Map<?, ?> nested) {
                flatten(name, nested, found);
            } else if (value != null) {
                collect(name, String.valueOf(value), found);
            }
        });
    }

    private static void collect(String key, String value, Map<String, String> found) {
        String logbackName = SPRING_LOGGING_KEYS.get(key);
        if (logbackName != null) {
            found.put(logbackName, value);
        }
    }

    public Logger logger(String name) {
        return context.getLogger(name);
    }

    /** The MDC of this context (the static {@code org.slf4j.MDC} belongs to the global one). */
    public MDCAdapter mdc() {
        return context.getMDCAdapter();
    }

    /** Where the configuration was read from (proves which file was loaded). */
    public URL source() {
        return source;
    }

    /**
     * Everything the redirected appenders wrote so far. Fails if an appender reported an error (a
     * line that failed to render is not a line without an address).
     */
    public String output() {
        failOnProblems(Status.ERROR, "appending");
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        context.stop();
    }

    private void failOnProblems(int level, String phase) {
        List<String> problems = new ArrayList<>();
        for (Status status : context.getStatusManager().getCopyOfStatusList()) {
            if (status.getEffectiveLevel() >= level && !aboutSpringElement(status)) {
                problems.add(status.toString());
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("logback reported problems " + phase + " " + source + ": " + problems);
        }
    }

    private static boolean aboutSpringElement(Status status) {
        return SPRING_ELEMENT_STATUS.matcher(String.valueOf(status.getMessage())).matches();
    }

    private void failOnUnmaskedConverters(OutputStreamAppender<ILoggingEvent> appender) {
        Encoder<ILoggingEvent> encoder = appender.getEncoder();
        if (encoder instanceof LayoutWrappingEncoder<ILoggingEvent> wrapping
                && wrapping.getLayout() instanceof PatternLayoutBase<ILoggingEvent> layout) {
            List<String> unmasked = new ArrayList<>();
            collectUnmasked(head(layout), unmasked);
            if (!unmasked.isEmpty()) {
                throw new IllegalStateException(source + ": appender " + appender.getName() + " renders with "
                        + unmasked + ", which do not mask e-mail addresses (pattern '" + layout.getPattern()
                        + "'); a pattern needs a throwable word (%wEx) and the masking conversion rules");
            }
        }
    }

    private static void collectUnmasked(Converter<ILoggingEvent> converter, List<String> unmasked) {
        for (Converter<ILoggingEvent> c = converter; c != null; c = c.getNext()) {
            if (c instanceof ThrowableProxyConverter && !(c instanceof EmailMaskingThrowableConverter)
                    || c instanceof MessageConverter && !(c instanceof EmailMaskingMessageConverter)) {
                unmasked.add(c.getClass().getName());
            }
            if (c instanceof CompositeConverter<ILoggingEvent> composite) {
                collectUnmasked(composite.getChildConverter(), unmasked);
            }
        }
    }

    /** logback keeps the compiled chain in a package-private field; no public accessor exists. */
    @SuppressWarnings("unchecked")
    private static Converter<ILoggingEvent> head(PatternLayoutBase<ILoggingEvent> layout) {
        try {
            Field head = PatternLayoutBase.class.getDeclaredField("head");
            head.setAccessible(true);
            return (Converter<ILoggingEvent>) head.get(layout);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("logback changed PatternLayoutBase; adapt the converter check", e);
        }
    }

    private void redirectAppenders() {
        int redirected = 0;
        for (Logger logger : context.getLoggerList()) {
            Iterator<Appender<ILoggingEvent>> appenders = logger.iteratorForAppenders();
            while (appenders.hasNext()) {
                if (appenders.next() instanceof OutputStreamAppender<ILoggingEvent> stream) {
                    failOnUnmaskedConverters(stream);
                    stream.setOutputStream(buffer);
                    redirected++;
                }
            }
        }
        if (redirected == 0) {
            throw new IllegalStateException(source + " defines no output stream appender to capture");
        }
    }
}
