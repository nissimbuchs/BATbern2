package ch.batbern.shared.unit.utils;

import ch.batbern.shared.utils.LoggingUtils;
import ch.batbern.shared.utils.LogContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.slf4j.MDC;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingUtilsTest {

    @Nested
    @DisplayName("Structured Logging")
    class StructuredLogging {

        @Test
        void should_formatStructuredLogMessage_when_contextProvided() {
            Map<String, Object> context = Map.of(
                "eventId", "123",
                "userId", "456",
                "action", "createEvent"
            );

            String message = LoggingUtils.formatStructuredMessage("Event created", context);

            assertThat(message)
                .contains("Event created")
                .contains("eventId=\"123\"")
                .contains("userId=\"456\"")
                .contains("action=\"createEvent\"");
        }

        @Test
        void should_escapeSpecialCharacters_when_loggingValues() {
            Map<String, Object> context = Map.of(
                "message", "User said: \"Hello\"",
                "data", "Line1\nLine2"
            );

            String message = LoggingUtils.formatStructuredMessage("Processing", context);

            assertThat(message)
                .contains("\\\"Hello\\\"")
                .contains("\\n");
        }

        @Test
        void should_handleNullValues_when_formattingMessage() {
            Map<String, Object> context = Map.of(
                "eventId", "123",
                "speakerId", "null"
            );
            context = new java.util.HashMap<>(context);
            context.put("companyId", null);

            String message = LoggingUtils.formatStructuredMessage("Processing", context);

            assertThat(message)
                .contains("eventId=\"123\"")
                .contains("companyId=null");
        }

        @Test
        void should_formatAsJSON_when_jsonModeEnabled() {
            Map<String, Object> context = Map.of(
                "eventId", "123",
                "action", "create"
            );

            String json = LoggingUtils.formatAsJson("Event created", context);

            assertThat(json)
                .contains("\"message\":\"Event created\"")
                .contains("\"eventId\":\"123\"")
                .contains("\"action\":\"create\"")
                .contains("\"timestamp\":");
        }
    }

    @Nested
    @DisplayName("MDC Context Management")
    class MDCContextManagement {

        @Test
        void should_setMDCContext_when_traceIdProvided() {
            String traceId = "trace-123";
            String correlationId = "corr-456";

            LoggingUtils.setTraceContext(traceId, correlationId);

            assertThat(MDC.get("traceId")).isEqualTo(traceId);
            assertThat(MDC.get("correlationId")).isEqualTo(correlationId);

            MDC.clear();
        }

        @Test
        void should_clearMDCContext_when_contextCleared() {
            MDC.put("traceId", "test-trace");
            MDC.put("correlationId", "test-correlation");

            LoggingUtils.clearContext();

            assertThat(MDC.get("traceId")).isNull();
            assertThat(MDC.get("correlationId")).isNull();
        }

        @Test
        void should_preserveOtherMDCValues_when_settingTraceContext() {
            MDC.put("existingKey", "existingValue");

            LoggingUtils.setTraceContext("trace-123", "corr-456");

            assertThat(MDC.get("existingKey")).isEqualTo("existingValue");
            assertThat(MDC.get("traceId")).isEqualTo("trace-123");

            MDC.clear();
        }

        @Test
        void should_executeWithContext_when_usingContextualLogging() {
            String result = LoggingUtils.withContext(Map.of("requestId", "req-123"), () -> {
                assertThat(MDC.get("requestId")).isEqualTo("req-123");
                return "success";
            });

            assertThat(result).isEqualTo("success");
            assertThat(MDC.get("requestId")).isNull();
        }
    }

    @Nested
    @DisplayName("Log Context Builder")
    class LogContextBuilder {

        @Test
        void should_buildLogContext_when_addingFields() {
            LogContext context = LoggingUtils.context()
                .add("eventId", "123")
                .add("userId", "456")
                .add("action", "create")
                .build();

            assertThat(context.getFields())
                .containsEntry("eventId", "123")
                .containsEntry("userId", "456")
                .containsEntry("action", "create");
        }

        @Test
        void should_chainContextBuilding_when_addingMultipleFields() {
            LogContext context = LoggingUtils.context()
                .add("field1", "value1")
                .addAll(Map.of("field2", "value2", "field3", "value3"))
                .build();

            assertThat(context.getFields()).hasSize(3);
            assertThat(context.getFields().get("field1")).isEqualTo("value1");
            assertThat(context.getFields().get("field2")).isEqualTo("value2");
        }

        @Test
        void should_overwriteExistingField_when_duplicateKeyAdded() {
            LogContext context = LoggingUtils.context()
                .add("key", "value1")
                .add("key", "value2")
                .build();

            assertThat(context.getFields().get("key")).isEqualTo("value2");
        }
    }

    @Nested
    @DisplayName("Performance Logging")
    class PerformanceLogging {

        @Test
        void should_measureExecutionTime_when_timedOperation() throws InterruptedException {
            long duration = LoggingUtils.measureTime(() -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

            assertThat(duration).isGreaterThanOrEqualTo(50);
        }

        @Test
        void should_logPerformanceMetrics_when_operationCompletes() {
            Map<String, Object> metrics = LoggingUtils.capturePerformanceMetrics(() -> {
                // No return needed for Runnable
            });

            assertThat(metrics).containsKeys("duration", "success", "timestamp");
            assertThat(metrics.get("success")).isEqualTo(true);
        }

        @Test
        void should_captureFailureMetrics_when_operationFails() {
            Map<String, Object> metrics = LoggingUtils.capturePerformanceMetrics(() -> {
                throw new RuntimeException("Test failure");
            });

            assertThat(metrics).containsKeys("duration", "success", "error", "timestamp");
            assertThat(metrics.get("success")).isEqualTo(false);
            assertThat(metrics.get("error")).isEqualTo("Test failure");
        }
    }

    @Nested
    @DisplayName("Audit Logging")
    class AuditLogging {

        @Test
        void should_createAuditLogEntry_when_actionPerformed() {
            String auditEntry = LoggingUtils.createAuditEntry(
                "USER_LOGIN",
                "user123",
                Map.of("ip", "192.168.1.1", "browser", "Chrome")
            );

            assertThat(auditEntry)
                .contains("USER_LOGIN")
                .contains("user123")
                .contains("192.168.1.1")
                .contains("Chrome")
                .contains("timestamp");
        }

        @Test
        void should_includeBeforeAfterValues_when_auditingChanges() {
            Map<String, Object> before = Map.of("status", "DRAFT", "title", "Old Title");
            Map<String, Object> after = Map.of("status", "PUBLISHED", "title", "New Title");

            String auditEntry = LoggingUtils.createChangeAuditEntry(
                "EVENT_UPDATE",
                "user123",
                before,
                after
            );

            assertThat(auditEntry)
                .contains("EVENT_UPDATE")
                .contains("DRAFT")
                .contains("PUBLISHED")
                .contains("Old Title")
                .contains("New Title");
        }
    }

    @Nested
    @DisplayName("Sensitive Data Masking")
    class SensitiveDataMasking {

        @Test
        void should_maskSensitiveData_when_loggingPersonalInfo() {
            Map<String, Object> data = Map.of(
                "email", "user@example.com",
                "password", "secret123",
                "creditCard", "1234-5678-9012-3456"
            );

            Map<String, Object> masked = LoggingUtils.maskSensitiveData(data);

            assertThat(masked.get("email")).isEqualTo("u***@example.com");
            assertThat(masked.get("password")).isEqualTo("***");
            assertThat(masked.get("creditCard")).isEqualTo("1234-****-****-3456");
        }

        @Test
        void should_preserveNonSensitiveData_when_masking() {
            Map<String, Object> data = Map.of(
                "eventId", "123",
                "email", "test@test.com",
                "status", "ACTIVE"
            );

            Map<String, Object> masked = LoggingUtils.maskSensitiveData(data);

            assertThat(masked.get("eventId")).isEqualTo("123");
            assertThat(masked.get("status")).isEqualTo("ACTIVE");
            assertThat(masked.get("email")).isNotEqualTo("test@test.com");
        }
    }
}