package ch.batbern.migration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Migration Tool Application
 *
 * Spring Boot application for migrating historical BATbern data (20+ years)
 * from legacy Angular application to new microservices platform.
 *
 * Features:
 * - Spring Batch for parallel chunk-oriented processing
 * - PostgreSQL for progress tracking and ID mappings
 * - AWS S3 for file migrations
 * - REST API integration with target services
 *
 * Story: 3.2.1 - Migration Tool Implementation
 *
 * Note: @EnableBatchProcessing is NOT used in Spring Boot 3.x
 * as it disables auto-configuration. Batch is auto-configured.
 */
@SpringBootApplication
public class MigrationToolApplication {

    public static void main(String[] args) {
        SpringApplication.run(MigrationToolApplication.class, args);
    }
}
