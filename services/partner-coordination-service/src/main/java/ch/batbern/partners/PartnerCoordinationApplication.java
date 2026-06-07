package ch.batbern.partners;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Build alignment: 2026-04-05
@SpringBootApplication(scanBasePackages = {"ch.batbern.partners", "ch.batbern.shared"})
public class PartnerCoordinationApplication {

    public static void main(String[] args) {
        SpringApplication.run(PartnerCoordinationApplication.class, args);
    }
}
