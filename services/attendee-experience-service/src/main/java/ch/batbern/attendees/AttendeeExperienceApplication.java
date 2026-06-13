package ch.batbern.attendees;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;

// Build alignment: 2026-04-05
// SB4: the Flyway auto-config module (spring-boot-flyway) is not on the classpath, so there
// is nothing to exclude for Flyway; DataSource/HibernateJpa come via starter-data-jpa and are
// still excluded (this service runs without a configured datasource).
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
public class AttendeeExperienceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AttendeeExperienceApplication.class, args);
    }
}
