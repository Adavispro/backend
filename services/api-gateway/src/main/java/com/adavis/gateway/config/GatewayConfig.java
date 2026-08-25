package com.adavis.gateway.config;

import com.adavis.gateway.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class GatewayConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${gateway.routes.auth-service-uri:http://localhost:9081}")
    private String authServiceUri;

    @Value("${gateway.routes.license-service-uri:http://localhost:8082}")
    private String licenseServiceUri;

    @Value("${gateway.routes.audit-service-uri:http://localhost:8084}")
    private String auditServiceUri;

    @Value("${gateway.routes.mdm-service-uri:http://localhost:9083}")
    private String mdmServiceUri;

    @Value("${gateway.routes.iiot-service-uri:http://localhost:9085}")
    private String iiotServiceUri;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Auth Service - Public
                .route("auth-service", r -> r
                        .path("/api/v1/auth/**", "/api/auth/**")
                        .uri(authServiceUri))

                // License Service - Protected with JWT
                .route("license-service", r -> r
                        .path("/api/v1/mdm/license/**")
                        .filters(f -> f
                                .filter(jwtAuthenticationFilter))
                        .uri(licenseServiceUri))

                // Audit Service - Protected with JWT
                .route("audit-service", r -> r
                        .path("/api/v1/audit/**")
                        .filters(f -> f
                                .filter(jwtAuthenticationFilter))
                        .uri(auditServiceUri))
                
                // MDM Service - Protected with JWT
                .route("mdm-service", r -> r
                        .path("/api/v1/mdm/**", "/api/v1/dms/**")
                        .filters(f -> f
                                .filter(jwtAuthenticationFilter))
                        .uri(mdmServiceUri))

                // IIoT Service - Protected with JWT
                .route("iiot-service", r -> r
                        .path("/api/v1/iiot/**", "/api/v1/notifications/**")
                        .filters(f -> f
                                .filter(jwtAuthenticationFilter))
                        .uri(iiotServiceUri))
                .build();
    }
}