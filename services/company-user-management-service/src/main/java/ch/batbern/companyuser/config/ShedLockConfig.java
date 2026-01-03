package ch.batbern.companyuser.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * ShedLock configuration for distributed scheduled task coordination
 *
 * Purpose: Ensures that scheduled tasks (cron jobs) execute only ONCE across multiple ECS instances.
 * Without ShedLock, if 3 ECS tasks are running, each scheduled job would run 3 times simultaneously,
 * causing duplicate operations, race conditions, and wasted resources.
 *
 * How it works:
 * 1. Before executing a @Scheduled method with @SchedulerLock, ShedLock tries to acquire a database lock
 * 2. Only ONE instance successfully acquires the lock
 * 3. That instance executes the job while others skip it
 * 4. Lock is released after execution (or expires after lockAtMostFor duration)
 *
 * Database table: shedlock (created by V12__Add_shedlock_table.sql)
 *
 * Production scenario:
 * - Development: 1-2 ECS tasks → ShedLock ensures only 1 executes
 * - Production: 2-8 ECS tasks → ShedLock ensures only 1 executes
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m") // Failsafe: release lock after 10 minutes even if instance crashes
public class ShedLockConfig {

    /**
     * Creates LockProvider backed by PostgreSQL
     *
     * LockProvider uses the 'shedlock' table to coordinate locks across instances
     * Each scheduled job has a unique lock name (e.g., "userReconciliationTask")
     *
     * @param dataSource PostgreSQL datasource (auto-injected by Spring)
     * @return LockProvider for JDBC-based locking
     */
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime() // Use database time for consistency across instances
                        .build()
        );
    }
}
