package ch.demo;

import ch.demo.client.BarClient;
import ch.demo.service.Foo;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@MockitoBean(types = {Foo.class, BarClient.class})
class SomeUnitTest {
}
