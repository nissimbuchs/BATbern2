package ch.batbern.shared.test.async;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The tracker waits for every task an {@code @Async} executor accepted, including tasks those tasks
 * submit, so a late send of one test cannot land in the next test's recorder.
 */
@DisplayName("AsyncTaskTracker")
class AsyncTaskTrackerTest {

    private final List<ThreadPoolTaskExecutor> executors = new ArrayList<>();

    @AfterEach
    void shutDown() {
        executors.forEach(ThreadPoolTaskExecutor::shutdown);
    }

    @Test
    @DisplayName("waits for a running task of a decorated executor (the services' AsyncConfig)")
    void should_waitForRunningTask_when_executorHasDecorator() {
        AsyncTaskTracker tracker = new AsyncTaskTracker();
        ThreadPoolTaskExecutor executor = tracked(tracker, true);
        AtomicInteger done = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);

        executor.execute(() -> {
            started.countDown();
            sleep(300);
            done.incrementAndGet();
        });
        await(started);
        assertThat(done).as("positive control: still running when the wait starts").hasValue(0);

        tracker.awaitIdle();

        assertThat(done).hasValue(1);
    }

    @Test
    @DisplayName("waits for a task submitted by a running task (a listener that sends mail async)")
    void should_waitForChainedTask_when_taskSubmitsAnother() {
        AsyncTaskTracker tracker = new AsyncTaskTracker();
        ThreadPoolTaskExecutor executor = tracked(tracker, true);
        AtomicInteger done = new AtomicInteger();

        executor.execute(() -> {
            sleep(100);
            executor.execute(() -> {
                sleep(200);
                done.incrementAndGet();
            });
        });

        tracker.awaitIdle();

        assertThat(done).hasValue(1);
    }

    @Test
    @DisplayName("waits for queued tasks of an executor without decorator too")
    void should_waitForQueuedTasks_when_executorHasNoDecorator() {
        AsyncTaskTracker tracker = new AsyncTaskTracker();
        ThreadPoolTaskExecutor executor = tracked(tracker, false);
        AtomicInteger done = new AtomicInteger();

        for (int i = 0; i < 5; i++) {
            executor.execute(() -> {
                sleep(80);
                done.incrementAndGet();
            });
        }

        tracker.awaitIdle();

        assertThat(done).hasValue(5);
    }

    @Test
    @DisplayName("fails naming the count when a task does not finish in time")
    void should_fail_when_taskOutlivesTimeout() {
        AsyncTaskTracker tracker = new AsyncTaskTracker(Duration.ofMillis(200));
        ThreadPoolTaskExecutor executor = tracked(tracker, true);
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> await(release));
        try {
            assertThatThrownBy(tracker::awaitIdle)
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("still running");
        } finally {
            release.countDown();
        }
    }

    @Test
    @DisplayName("leaves beans other than a ThreadPoolTaskExecutor alone")
    void should_returnBeanUnchanged_when_notAnExecutor() {
        Object bean = new Object();
        assertThat(new AsyncTaskTracker().postProcessAfterInitialization(bean, "other")).isSameAs(bean);
    }

    private ThreadPoolTaskExecutor tracked(AsyncTaskTracker tracker, boolean decorated) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        if (decorated) {
            executor.setTaskDecorator(runnable -> runnable);
        }
        executor.initialize();
        executors.add(executor);
        return (ThreadPoolTaskExecutor) tracker.postProcessAfterInitialization(executor, "taskExecutor");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
