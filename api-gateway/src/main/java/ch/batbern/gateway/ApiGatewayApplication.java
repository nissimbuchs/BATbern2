package ch.batbern.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway application entry point.
 *
 * Stateless gateway with no direct database access. The JDBC/JPA auto-config modules are NOT on
 * the gateway's COMPILE classpath (shared-kernel's spring-boot-starter-data-jpa is an implementation
 * dependency) but ARE on its RUNTIME classpath, so they activate at boot and DataSourceAutoConfiguration
 * fails trying to build a Hikari pool with no URL. They can't be referenced by class here (compile
 * error), so they're excluded by name via spring.autoconfigure.exclude in application.yml.
 */
// Build alignment: 2026-04-05
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}