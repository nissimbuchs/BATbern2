package ch.batbern.events;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"ch.batbern.events", "ch.batbern.shared"})
@EnableJpaRepositories(basePackages = {"ch.batbern.events.repository", "ch.batbern.events.notification"})
@EntityScan(basePackages = {"ch.batbern.events.domain", "ch.batbern.events.entity", "ch.batbern.events.notification"})
public class EventManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventManagementApplication.class, args);
    }
}
