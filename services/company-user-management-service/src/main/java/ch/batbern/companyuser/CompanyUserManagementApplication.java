package ch.batbern.companyuser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"ch.batbern.companyuser", "ch.batbern.shared"})
public class CompanyUserManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(CompanyUserManagementApplication.class, args);
    }
}
