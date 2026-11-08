package ch.demo;

import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public class ReadOnlyIT {
}
