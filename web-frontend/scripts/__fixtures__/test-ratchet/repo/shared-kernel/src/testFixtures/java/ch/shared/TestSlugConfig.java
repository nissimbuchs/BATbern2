package ch.shared;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

public class TestSlugConfig {
    @MockitoBean
    protected SlugGenerationService slugGenerationService;
}
