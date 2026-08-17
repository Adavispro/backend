package com.adavis.gateway.filter;

import com.adavis.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GatewayFilter, Ordered {

    private final JwtTokenProvider jwtTokenProvider;
    private final WebClient.Builder webClientBuilder;
    private final ReactiveStringRedisTemplate redisTemplate;

    private static final String SUPER_ADMIN_USER_ID = "SUPER_ADMIN";
    private static final String BLACKLIST_PREFIX = "blacklist:";

    @Value("${services.mdm.base-url:http://mdm-service:9083}")
    private String mdmServiceBaseUrl;

    @Value("${services.license.base-url:http://license-service:8082}")
    private String licenseServiceBaseUrl;

    @Value("${security.internal-auth-header:adavis-internal-auth-key}")
    private String internalAuthHeaderValue;

    private static final List<String> PUBLIC_PATHS = List.of(
        "/api/auth/login",
        "/api/auth/refresh",
        "/api/v1/auth/",
        "/actuator/health",
        "/actuator/info"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().toString();
        log.info("Gateway received {} {}", request.getMethod(), path);

        // Allow public paths
        if (isPublicPath(path)) {
            log.info("Public path allowed without JWT: {}", path);
            return chain.filter(exchange);
        }

        // Extract and validate token
        String token = extractToken(request.getHeaders().getFirst("Authorization"));

        if (token == null || !jwtTokenProvider.validateToken(token)) {
            log.warn("Invalid or missing token for path: {}", path);
            return unauthorized(exchange.getResponse());
        }

        return isTokenBlacklisted(token)
                .flatMap(isBlacklisted -> {
                    if (isBlacklisted) {
                        log.warn("Blacklisted token rejected for path: {}", path);
                        return unauthorized(exchange.getResponse());
                    }

                    // Extract user info and add to headers
                    String userId = jwtTokenProvider.getUserIdFromToken(token);
                    String username = jwtTokenProvider.getUsernameFromToken(token);
                    String sessionId = jwtTokenProvider.getSessionIdFromToken(token);

                    if (isLicenseBypassPath(path) || SUPER_ADMIN_USER_ID.equalsIgnoreCase(userId)) {
                        ServerHttpRequest bypassRequest = buildTrustedRequestHeaders(request, userId, username, sessionId, null);
                        log.info("Gateway bypassing license check for path={} userId={}", path, userId);
                        return chain.filter(exchange.mutate().request(bypassRequest).build());
                    }

                    return resolveTenantId(userId)
                            .flatMap(tenantId -> validateTenantLicense(tenantId)
                                    .flatMap(valid -> {
                                        if (!valid) {
                                            return licenseExpired(exchange.getResponse());
                                        }
                                        ServerHttpRequest mutatedRequest = buildTrustedRequestHeaders(request, userId, username, sessionId, tenantId);
                                        log.info("Authenticated user: {} for path: {} tenantId={}", username, path, tenantId);
                                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                                    }))
                            .onErrorResume(ex -> {
                                log.warn("License guard failed for user {} on {}: {}", userId, path, ex.getMessage());
                                return serviceUnavailable(exchange.getResponse());
                            });
                });
    }

    private Mono<Boolean> isTokenBlacklisted(String token) {
        return redisTemplate.hasKey(BLACKLIST_PREFIX + token)
                .onErrorResume(ex -> {
                    log.warn("Failed to check token blacklist state: {}", ex.getMessage());
                    return Mono.just(false);
                });
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private boolean isLicenseBypassPath(String path) {
        return path.startsWith("/api/v1/mdm/license/")
                || path.startsWith("/api/v1/auth/")
                || path.matches("^/api/v1/mdm/users/[^/]+/login-context$")
                || path.matches("^/api/v1/mdm/users/[^/]+/select-plant$");
    }

    private ServerHttpRequest buildTrustedRequestHeaders(ServerHttpRequest request,
                                                         String userId,
                                                         String username,
                                                         String sessionId,
                                                         String tenantId) {
        return request.mutate()
            .headers(headers -> {
                // Remove client-supplied identity headers to prevent header spoofing.
                headers.remove("X-User-Id");
                headers.remove("X-Username");
                headers.remove("X-Tenant-Id");
                headers.remove("X-Session-Id");
                headers.remove("X-Internal-Auth");
                headers.add("X-User-Id", userId);
                headers.add("X-Username", username);
                if (StringUtils.hasText(sessionId)) {
                    headers.add("X-Session-Id", sessionId);
                }
                headers.add("X-Internal-Auth", internalAuthHeaderValue);
                if (StringUtils.hasText(tenantId)) {
                    headers.add("X-Tenant-Id", tenantId);
                }
            })
            .build();
    }

    private Mono<String> resolveTenantId(String userId) {
        return tryResolveTenantIdForUserId(userId)
                .switchIfEmpty(Mono.defer(() -> {
                    String upper = userId == null ? null : userId.toUpperCase(Locale.ROOT);
                    if (StringUtils.hasText(upper) && !upper.equals(userId)) {
                        return tryResolveTenantIdForUserId(upper);
                    }
                    return Mono.empty();
                }))
                .switchIfEmpty(Mono.defer(() -> {
                    String lower = userId == null ? null : userId.toLowerCase(Locale.ROOT);
                    if (StringUtils.hasText(lower) && !lower.equals(userId)) {
                        return tryResolveTenantIdForUserId(lower);
                    }
                    return Mono.empty();
                }))
                .switchIfEmpty(Mono.error(new IllegalStateException("Tenant context missing")));
    }

    private Mono<String> tryResolveTenantIdForUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return Mono.empty();
        }

        String url = mdmServiceBaseUrl + "/api/v1/mdm/users/" + userId;
        return webClientBuilder.build()
                .get()
                .uri(url)
                .header("X-Internal-Auth", internalAuthHeaderValue)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(body -> {
                    Object data = body.get("data");
                    if (!(data instanceof Map<?, ?> dataMap)) {
                        return Mono.empty();
                    }
                    Object tenantId = dataMap.get("tenantId");
                    String tenantValue = tenantId == null ? null : String.valueOf(tenantId).trim();
                    if (!StringUtils.hasText(tenantValue)) {
                        return Mono.empty();
                    }
                    return Mono.just(tenantValue);
                })
                .onErrorResume(ex -> {
                    log.warn("Tenant lookup failed for userId {}: {}", userId, ex.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Boolean> validateTenantLicense(String tenantId) {
        String url = licenseServiceBaseUrl + "/internal/v1/mdm/license/tenant/" + tenantId + "/validate";
        return webClientBuilder.build()
                .post()
                .uri(url)
                .header("X-Internal-Auth", internalAuthHeaderValue)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of())
                .retrieve()
                .bodyToMono(Map.class)
                .map(body -> {
                    Object data = body.get("data");
                    return data instanceof Boolean value && value;
                });
    }

    private Mono<Void> unauthorized(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        String body = "{\"success\":false,\"message\":\"Unauthorized\",\"errorCode\":\"UNAUTHORIZED\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private Mono<Void> licenseExpired(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        String body = "{\"success\":false,\"message\":\"License expired or inactive\",\"errorCode\":\"LICENSE_EXPIRED\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private Mono<Void> serviceUnavailable(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        String body = "{\"success\":false,\"message\":\"License validation service unavailable\",\"errorCode\":\"LICENSE_GUARD_UNAVAILABLE\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}