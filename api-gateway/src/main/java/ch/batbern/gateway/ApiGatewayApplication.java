package ch.batbern.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway application entry point.
 *
 * Stateless gateway with no direct database access. Under Spring Boot 4's modularised
 * auto-configuration, the JDBC/JPA modules are simply absent from the classpath, so there
 * is nothing to exclude (the SB 3.x exclude of DataSource/HibernateJpa auto-config is moot).
 */
// Build alignment: 2026-04-05
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}