package ch.batbern.attendees;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;

// Build alignment: 2026-04-05
// SB4: this service runs without a configured datasource, so DataSource/HibernateJpa (via
// starter-data-jpa) are excluded. Epic 13 added the spring-boot-flyway auto-config module
// estate-wide for the DB services, so Flyway auto-config is now on attendee's classpath too —
// exclude it explicitly here (no datasource, zero migrations).
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    FlywayAutoConfiguration.class
})
public class AttendeeExperienceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AttendeeExperienceApplication.class, args);
    }
}
