package com.adavis.mdm.security;

import com.adavis.security.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class MdmAuthorizationFilter extends OncePerRequestFilter {

    private final MongoTemplate mongoTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${security.internal-auth-header:adavis-internal-auth-key}")
    private String internalAuthHeaderValue;

    private static final String USER_PROFILES_COLLECTION = "mdm_user_profiles";
    private static final String USER_GROUP_ASSIGNMENTS_COLLECTION = "mdm_user_assignments_to_user_groups";
    private static final String ROLE_ASSIGNMENTS_COLLECTION = "mdm_role_assignments_to_user_groups";
    private static final String ROLES_COLLECTION = "mdm_roles";
    private static final String USER_GROUPS_COLLECTION = "mdm_user_groups";

    private static final Set<String> ADMIN_ROLE_CODES = Set.of(
            "SUPER_ADMIN",
            "PLATFORM_SUPER_ADMIN",
            "PLATFORM_ADMIN",
            "SYSTEM_ADMIN",
            "IT_ADMIN",
            "ADMIN"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // 1. Actuator and public endpoints
        if (path.startsWith("/actuator/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Resolve acting user ID
        String userId = resolveUserId(request);

        // 3. Pure internal service-to-service requests (only without user context)
        String internalAuth = request.getHeader("X-Internal-Auth");
        if ((userId == null || userId.isBlank()) && internalAuthHeaderValue != null && internalAuthHeaderValue.equals(internalAuth)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (userId == null || userId.isBlank()) {
            sendUnauthorizedResponse(response, "Authentication required for Master Management.");
            return;
        }

        // 4. User self-service / context endpoints required for login and plant context
        if (isSelfServiceContextPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 5. Allow self profile query: GET /api/v1/mdm/users/{userId} when requested by that user
        if (isSelfProfileQuery(path, request.getMethod(), userId)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Read-only topology is needed to scope equipment views for every authenticated role.
        if (isReadOnlyTopologyQuery(path, request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // 6. Dynamic Authoritative Admin Verification
        if (isUserAuthorizedAdmin(userId)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 7. Non-admin user attempting to access Master Management -> Return HTTP 403 Forbidden
        log.warn("Access denied: User '{}' is not authorized for Master Management endpoint {} {}",
                userId, request.getMethod(), path);
        sendForbiddenResponse(response);
    }

    private boolean isSelfServiceContextPath(String path) {
        return path.matches("^/api/v1/mdm/users/[^/]+/login-context$")
                || path.matches("^/api/v1/mdm/users/[^/]+/select-plant$");
    }

    private boolean isSelfProfileQuery(String path, String method, String userId) {
        if (!"GET".equalsIgnoreCase(method)) return false;
        String prefix = "/api/v1/mdm/users/";
        if (path.startsWith(prefix)) {
            String targetUserId = path.substring(prefix.length());
            if (!targetUserId.contains("/")) {
                return targetUserId.equalsIgnoreCase(userId);
            }
        }
        return false;
    }

    private boolean isReadOnlyTopologyQuery(String path, String method) {
        if (!"GET".equalsIgnoreCase(method)) return false;
        return path.matches("^/api/v1/mdm/(plants|blocks|areas|rooms)$");
    }

    private String resolveUserId(HttpServletRequest request) {
        String headerUserId = request.getHeader("X-User-Id");
        if (headerUserId != null && !headerUserId.isBlank()) {
            return headerUserId.trim();
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            try {
                if (jwtTokenProvider.validateToken(token)) {
                    String uid = jwtTokenProvider.getUserIdFromToken(token);
                    if (uid != null && !uid.isBlank()) return uid.trim();
                    String username = jwtTokenProvider.getUsernameFromToken(token);
                    if (username != null && !username.isBlank()) return username.trim();
                }
            } catch (Exception e) {
                log.debug("JWT token validation in MdmAuthorizationFilter failed: {}", e.getMessage());
            }
        }
        return null;
    }

    private boolean isUserAuthorizedAdmin(String userId) {
        String normalized = userId.trim();
        if ("SUPER_ADMIN".equalsIgnoreCase(normalized)) {
            return true;
        }

        try {
            // Check direct profile
            Query profileQuery = new Query(new Criteria().orOperator(
                    Criteria.where("userId").regex("^" + normalized + "$", "i"),
                    Criteria.where("username").regex("^" + normalized + "$", "i"),
                    Criteria.where("email").regex("^" + normalized + "$", "i")
            ));
            Document profile = mongoTemplate.findOne(profileQuery, Document.class, USER_PROFILES_COLLECTION);
            if (profile != null) {
                String title = profile.getString("title");
                if (title != null && (title.equalsIgnoreCase("Super Admin") || title.equalsIgnoreCase("System Admin"))) {
                    return true;
                }
            }

            // Check group assignments
            Query userAssignQuery = new Query(new Criteria().orOperator(
                    Criteria.where("userId").regex("^" + normalized + "$", "i"),
                    Criteria.where("userId").is(normalized)
            ).and("isActive").is(true));

            List<Document> userAssignments = mongoTemplate.find(userAssignQuery, Document.class, USER_GROUP_ASSIGNMENTS_COLLECTION);
            Set<String> groupIds = new HashSet<>();
            for (Document doc : userAssignments) {
                String gid = doc.getString("groupId");
                if (gid != null) groupIds.add(gid);
            }

            if (groupIds.isEmpty()) {
                return false;
            }

            // Check if any group itself is an admin group
            Query groupQuery = new Query(Criteria.where("groupId").in(groupIds).and("isActive").is(true));
            List<Document> groups = mongoTemplate.find(groupQuery, Document.class, USER_GROUPS_COLLECTION);
            for (Document g : groups) {
                String code = g.getString("groupCode");
                if (code != null && ADMIN_ROLE_CODES.contains(code.toUpperCase(Locale.ROOT))) {
                    return true;
                }
            }

            // Check role assignments for these groups
            Query roleAssignQuery = new Query(Criteria.where("groupId").in(groupIds).and("isActive").is(true));
            List<Document> roleAssignments = mongoTemplate.find(roleAssignQuery, Document.class, ROLE_ASSIGNMENTS_COLLECTION);
            Set<String> roleIds = new HashSet<>();
            for (Document doc : roleAssignments) {
                String rid = doc.getString("roleId");
                if (rid != null) roleIds.add(rid);
            }

            if (roleIds.isEmpty()) {
                return false;
            }

            // Check roles
            Query roleQuery = new Query(Criteria.where("roleId").in(roleIds).and("isActive").is(true));
            List<Document> roles = mongoTemplate.find(roleQuery, Document.class, ROLES_COLLECTION);
            for (Document r : roles) {
                String code = r.getString("roleCode");
                if (code != null && ADMIN_ROLE_CODES.contains(code.toUpperCase(Locale.ROOT))) {
                    return true;
                }
            }

        } catch (Exception e) {
            log.error("Error evaluating admin authority for userId {}: {}", userId, e.getMessage(), e);
        }

        return false;
    }

    private void sendForbiddenResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String json = "{\"success\":false,\"message\":\"Access denied: Master Management is restricted to system administrators.\",\"errorCode\":\"FORBIDDEN\",\"data\":null,\"timestamp\":\""
                + Instant.now().toString() + "\"}";
        response.getWriter().write(json);
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String json = "{\"success\":false,\"message\":\"" + message + "\",\"errorCode\":\"UNAUTHORIZED\",\"data\":null,\"timestamp\":\""
                + Instant.now().toString() + "\"}";
        response.getWriter().write(json);
    }
}
