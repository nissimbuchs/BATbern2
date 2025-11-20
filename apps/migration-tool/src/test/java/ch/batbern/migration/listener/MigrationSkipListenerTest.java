package ch.batbern.migration.listener;

import ch.batbern.migration.model.target.MigrationError;
import ch.batbern.migration.repository.MigrationErrorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.StepExecution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Migration Skip Listener Test
 *
 * Tests error logging to migration_errors table during skip events.
 *
 * Story: 3.2.1 - Task 8: Error Handling & Retry Logic
 */
@ExtendWith(MockitoExtension.class)
class MigrationSkipListenerTest {

    @Mock
    private MigrationErrorRepository errorRepository;

    @Mock
    private StepExecution stepExecution;

    @Captor
    private ArgumentCaptor<MigrationError> errorCaptor;

    private MigrationSkipListener<TestItem, TestItem> skipListener;

    @BeforeEach
    void setUp() {
        skipListener = new MigrationSkipListener<>(errorRepository);

        // Setup step execution mock
        when(stepExecution.getJobExecutionId()).thenReturn(123L);
        when(stepExecution.getStepName()).thenReturn("companyMigrationStep");

        skipListener.beforeStep(stepExecution);
    }

    @Test
    void should_logError_when_skipInProcess() {
        // Given: Item and exception
        TestItem item = new TestItem("test-id-001");
        IllegalArgumentException exception = new IllegalArgumentException("Validation failed");

        // When: Skip event occurs during processing
        skipListener.onSkipInProcess(item, exception);

        // Then: Error logged to repository
        verify(errorRepository).save(errorCaptor.capture());

        MigrationError savedError = errorCaptor.getValue();
        assertEquals(123L, savedError.getJobExecutionId());
        assertEquals("Company", savedError.getEntityType());
        assertEquals("test-id-001", savedError.getLegacyId());
        assertEquals("PROCESS", savedError.getPhase());
        assertEquals("Validation failed", savedError.getErrorMessage());
        assertNotNull(savedError.getStackTrace());
        assertFalse(savedError.getResolved());
    }

    @Test
    void should_logError_when_skipInWrite() {
        // Given: Item and exception
        TestItem item = new TestItem("test-id-002");
        RuntimeException exception = new RuntimeException("API call failed");

        // When: Skip event occurs during write
        skipListener.onSkipInWrite(item, exception);

        // Then: Error logged to repository
        verify(errorRepository).save(errorCaptor.capture());

        MigrationError savedError = errorCaptor.getValue();
        assertEquals(123L, savedError.getJobExecutionId());
        assertEquals("Company", savedError.getEntityType());
        assertEquals("test-id-002", savedError.getLegacyId());
        assertEquals("WRITE", savedError.getPhase());
        assertEquals("API call failed", savedError.getErrorMessage());
        assertFalse(savedError.getResolved());
    }

    @Test
    void should_logError_when_skipInRead() {
        // Given: Exception during read
        RuntimeException exception = new RuntimeException("File not found");

        // When: Skip event occurs during read
        skipListener.onSkipInRead(exception);

        // Then: Error logged to repository
        verify(errorRepository).save(errorCaptor.capture());

        MigrationError savedError = errorCaptor.getValue();
        assertEquals(123L, savedError.getJobExecutionId());
        assertEquals("Company", savedError.getEntityType());
        assertEquals("UNKNOWN", savedError.getLegacyId()); // No item available during read
        assertEquals("READ", savedError.getPhase());
        assertEquals("File not found", savedError.getErrorMessage());
        assertFalse(savedError.getResolved());
    }

    @Test
    void should_extractEntityType_when_differentStepNames() {
        // Test different step names
        when(stepExecution.getStepName()).thenReturn("eventMigrationStep");
        skipListener.beforeStep(stepExecution);
        skipListener.onSkipInProcess(new TestItem("e1"), new RuntimeException("test"));
        verify(errorRepository).save(argThat(error -> "Event".equals(error.getEntityType())));

        reset(errorRepository);

        when(stepExecution.getStepName()).thenReturn("userSpeakerMigrationStep");
        skipListener.beforeStep(stepExecution);
        skipListener.onSkipInProcess(new TestItem("u1"), new RuntimeException("test"));
        verify(errorRepository).save(argThat(error -> "User".equals(error.getEntityType())));
    }

    @Test
    void should_truncateStackTrace_when_tooLong() {
        // Given: Exception with long stack trace
        Exception deepException = new Exception("Deep error");
        StackTraceElement[] longTrace = new StackTraceElement[100];
        for (int i = 0; i < 100; i++) {
            longTrace[i] = new StackTraceElement("Class" + i, "method" + i, "File" + i + ".java", i);
        }
        deepException.setStackTrace(longTrace);

        // When: Skip event occurs
        skipListener.onSkipInProcess(new TestItem("test"), deepException);

        // Then: Stack trace is truncated
        verify(errorRepository).save(errorCaptor.capture());
        MigrationError savedError = errorCaptor.getValue();
        assertTrue(savedError.getStackTrace().length() <= 2500,
            "Stack trace should be truncated to reasonable length");
        assertTrue(savedError.getStackTrace().contains("(truncated)"),
            "Stack trace should indicate truncation");
    }

    /**
     * Test item class for skip listener tests
     */
    static class TestItem {
        private String id;

        public TestItem(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        @Override
        public String toString() {
            return "TestItem{id='" + id + "'}";
        }
    }
}
