package ch.batbern.partners;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"ch.batbern.partners", "ch.batbern.shared"})
public class PartnerCoordinationApplication {

    public static void main(String[] args) {
        SpringApplication.run(PartnerCoordinationApplication.class, args);
    }
}
