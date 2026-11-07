package ch.demo;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = {DemoApp.class})
@Import(DemoConfig.class)
class MethodLevelIntegrationTest extends AbstractIntegrationTest {

    @Transactional
    void should_write_when_called() {
    }
}
