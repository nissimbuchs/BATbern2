package ch.batbern.shared.test.async;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Knows when the {@code @Async} work of a test context is done (17.0 deferred work, 2026-11-09).
 *
 * <p>The real {@code EmailService} sends on the {@code @Async} executor, so a mail started by one
 * integration test can reach the {@code SesRecorder} after the next test's {@code reset()} and break
 * its assertions. Registered as a {@code static @Bean} in a service's {@code TestAwsConfig}, this
 * post-processor tracks every {@link ThreadPoolTaskExecutor} of the context, and
 * {@link #awaitIdle()} returns once none of them has a task left. The {@code SesRecorder} calls it
 * before every reset.
 *
 * <p>An executor with a {@link TaskDecorator} (the services' {@code AsyncConfig}) is tracked exactly:
 * the decorator is wrapped so a task counts from its submission until it has finished, which also
 * covers a task submitted by a running task. {@code ThreadPoolTaskExecutor} reads its decorator on
 * every {@code execute}, which {@code AsyncTaskTrackerTest} proves on the Spring version in use. An
 * executor without a decorator (Spring applies a decorator set after {@code initialize()} only when
 * one was there before) is idle when its pool has no active task and an empty queue for three
 * polls in a row.
 */
public final class AsyncTaskTracker implements BeanPostProcessor {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final long POLL_MILLIS = 10;
    private static final int QUIET_POLLS = 3;

    private final Duration timeout;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final List<ThreadPoolTaskExecutor> executors = new CopyOnWriteArrayList<>();

    public AsyncTaskTracker() {
        this(DEFAULT_TIMEOUT);
    }

    public AsyncTaskTracker(Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof ThreadPoolTaskExecutor executor) {
            TaskDecorator original = (TaskDecorator) ReflectionTestUtils.getField(executor, "taskDecorator");
            if (original != null) {
                executor.setTaskDecorator(runnable -> counted(original.decorate(runnable)));
            }
            executors.add(executor);
        }
        return bean;
    }

    private Runnable counted(Runnable task) {
        inFlight.incrementAndGet();
        return () -> {
            try {
                task.run();
            } finally {
                inFlight.decrementAndGet();
            }
        };
    }

    /**
     * Blocks until no tracked executor has a task left.
     *
     * @throws AssertionError when tasks are still running after the timeout (30 s by default)
     */
    public void awaitIdle() {
        long deadline = System.nanoTime() + timeout.toNanos();
        int quiet = 0;
        while (quiet < QUIET_POLLS) {
            quiet = idle() ? quiet + 1 : 0;
            if (quiet < QUIET_POLLS) {
                if (System.nanoTime() > deadline) {
                    throw new AssertionError("@Async tasks still running after " + timeout.toMillis()
                            + " ms: " + inFlight.get() + " tracked, " + activeOrQueued() + " active or queued");
                }
                sleep();
            }
        }
    }

    private boolean idle() {
        return inFlight.get() == 0 && activeOrQueued() == 0;
    }

    private int activeOrQueued() {
        int busy = 0;
        for (ThreadPoolTaskExecutor executor : executors) {
            ThreadPoolExecutor pool;
            try {
                pool = executor.getThreadPoolExecutor();
            } catch (IllegalStateException notInitialized) {
                continue;
            }
            busy += pool.getActiveCount() + pool.getQueue().size();
        }
        return busy;
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for @Async tasks", e);
        }
    }
}
