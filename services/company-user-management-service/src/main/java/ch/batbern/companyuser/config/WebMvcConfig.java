package ch.batbern.companyuser.config;

import ch.batbern.companyuser.interceptor.FederatedAvatarImportInterceptor;
import ch.batbern.companyuser.interceptor.JITUserProvisioningInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC Configuration
 * <p>
 * Story 1.2.5: User Sync and Reconciliation Implementation
 * Registers JIT User Provisioning Interceptor for automatic user creation
 * <p>
 * Story 12.12: registers the federated avatar import interceptor AFTER JIT, so on the
 * very first federated request the JIT-created user row already exists when the avatar
 * hook runs (interceptor order = registration order).
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JITUserProvisioningInterceptor jitUserProvisioningInterceptor;
    private final FederatedAvatarImportInterceptor federatedAvatarImportInterceptor;

    private static final String[] INTERCEPTOR_EXCLUDED_PATHS = {
        "/api/auth/**",           // Exclude auth endpoints
        "/api/public/**",         // Exclude public endpoints
        "/actuator/**",           // Exclude actuator endpoints
        "/swagger-ui/**",         // Exclude Swagger UI
        "/v3/api-docs/**"         // Exclude OpenAPI docs
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Register JIT provisioning interceptor for all API endpoints
        registry.addInterceptor(jitUserProvisioningInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(INTERCEPTOR_EXCLUDED_PATHS);

        // Story 12.12: one-time Google avatar import — MUST come after JIT (see class doc)
        registry.addInterceptor(federatedAvatarImportInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(INTERCEPTOR_EXCLUDED_PATHS);
    }
}
