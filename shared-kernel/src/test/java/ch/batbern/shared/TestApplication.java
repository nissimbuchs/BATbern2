package ch.batbern.shared;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
    basePackages = "ch.batbern.shared",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "ch\\.batbern\\.shared\\.testing\\..*"
    )
)
public class TestApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}