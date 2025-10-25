package ch.batbern.companyuser.config;

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
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JITUserProvisioningInterceptor jitUserProvisioningInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Register JIT provisioning interceptor for all API endpoints
        registry.addInterceptor(jitUserProvisioningInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",           // Exclude auth endpoints
                        "/api/public/**",         // Exclude public endpoints
                        "/actuator/**",           // Exclude actuator endpoints
                        "/swagger-ui/**",         // Exclude Swagger UI
                        "/v3/api-docs/**"         // Exclude OpenAPI docs
                );
    }
}
