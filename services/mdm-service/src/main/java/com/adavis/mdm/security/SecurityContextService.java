package com.adavis.mdm.security;

import com.adavis.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityContextService {

    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";

    private static final String USER_PROFILES_COLLECTION = "mdm_user_profiles";
    private static final String USER_GROUP_ASSIGNMENTS_COLLECTION = "mdm_user_assignments_to_user_groups";
    private static final String ROLE_ASSIGNMENTS_COLLECTION = "mdm_role_assignments_to_user_groups";
    private static final String ROLES_COLLECTION = "mdm_roles";
    private static final String USER_GROUPS_COLLECTION = "mdm_user_groups";

    public static final Set<String> ADMIN_ROLE_CODES = Set.of(
            "SUPER_ADMIN",
            "PLATFORM_SUPER_ADMIN",
            "PLATFORM_ADMIN",
            "SYSTEM_ADMIN",
            "IT_ADMIN",
            "ADMIN"
    );

    @Value("${security.internal-auth-header:adavis-internal-auth-key}")
    private String internalAuthHeaderValue;

    private final MongoTemplate mongoTemplate;

    public String resolveActor(String currentUserId, HttpServletRequest request) {
        String internalAuth = request != null ? request.getHeader(INTERNAL_AUTH_HEADER) : null;
        if (internalAuthHeaderValue != null && internalAuthHeaderValue.equals(internalAuth) && StringUtils.hasText(currentUserId)) {
            return currentUserId.trim();
        }
        if (request != null) {
            Object attr = request.getAttribute("authenticatedUserId");
            if (attr != null && StringUtils.hasText(attr.toString())) {
                return attr.toString().trim();
            }
        }
        return "SYSTEM";
    }

    public boolean isSuperAdmin(String actorUserId) {
        if (!StringUtils.hasText(actorUserId)) {
            return false;
        }
        String normalized = actorUserId.trim();
        if ("SUPER_ADMIN".equalsIgnoreCase(normalized)) {
            return true;
        }
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("userId").regex("^" + Pattern.quote(normalized) + "$", "i"),
                Criteria.where("username").regex("^" + Pattern.quote(normalized) + "$", "i"),
                Criteria.where("email").regex("^" + Pattern.quote(normalized) + "$", "i")
        ));
        Document profile = mongoTemplate.findOne(query, Document.class, USER_PROFILES_COLLECTION);
        if (profile != null) {
            String title = profile.getString("title");
            if (title != null && (title.equalsIgnoreCase("Super Admin") || title.equalsIgnoreCase("Platform Super Administrator"))) {
                return true;
            }
            String uid = profile.getString("userId");
            if (uid != null) {
                return hasSuperAdminRole(uid);
            }
        }
        return false;
    }

    private boolean hasSuperAdminRole(String userId) {
        Query userAssignQuery = new Query(Criteria.where("userId").is(userId).and("isActive").is(true));
        List<Document> userAssignments = mongoTemplate.find(userAssignQuery, Document.class, USER_GROUP_ASSIGNMENTS_COLLECTION);
        Set<String> groupIds = new HashSet<>();
        for (Document doc : userAssignments) {
            String gid = doc.getString("groupId");
            if (gid != null) groupIds.add(gid);
        }
        if (groupIds.isEmpty()) return false;

        Query roleAssignQuery = new Query(Criteria.where("groupId").in(groupIds).and("isActive").is(true));
        List<Document> roleAssignments = mongoTemplate.find(roleAssignQuery, Document.class, ROLE_ASSIGNMENTS_COLLECTION);
        Set<String> roleIds = new HashSet<>();
        for (Document doc : roleAssignments) {
            String rid = doc.getString("roleId");
            if (rid != null) roleIds.add(rid);
        }
        if (roleIds.isEmpty()) return false;

        Query roleQuery = new Query(Criteria.where("roleId").in(roleIds).and("isActive").is(true));
        List<Document> roles = mongoTemplate.find(roleQuery, Document.class, ROLES_COLLECTION);
        for (Document r : roles) {
            String code = r.getString("roleCode");
            if (code != null && ("SUPER_ADMIN".equalsIgnoreCase(code) || "PLATFORM_SUPER_ADMIN".equalsIgnoreCase(code))) {
                return true;
            }
        }
        return false;
    }

    public String resolveEffectiveTenantId(String actorUserId, String requestedTenantId) {
        if (isSuperAdmin(actorUserId)) {
            return requestedTenantId;
        }
        if (!StringUtils.hasText(actorUserId) || "SYSTEM".equalsIgnoreCase(actorUserId.trim())) {
            return requestedTenantId;
        }
        Document profile = findProfile(actorUserId);
        if (profile != null) {
            String userTenantId = profile.getString("tenantId");
            if (StringUtils.hasText(requestedTenantId) && userTenantId != null && !userTenantId.equalsIgnoreCase(requestedTenantId.trim())) {
                throw new BusinessException("Access to tenant " + requestedTenantId + " is forbidden.", "FORBIDDEN");
            }
            return userTenantId != null ? userTenantId : requestedTenantId;
        }
        return requestedTenantId;
    }

    public void verifyTenantAccess(String actorUserId, String targetTenantId) {
        if (!StringUtils.hasText(actorUserId) || !StringUtils.hasText(targetTenantId) || "SYSTEM".equalsIgnoreCase(actorUserId.trim())) {
            return;
        }
        if (isSuperAdmin(actorUserId)) {
            return;
        }
        Document profile = findProfile(actorUserId);
        if (profile != null) {
            String userTenantId = profile.getString("tenantId");
            if (userTenantId != null && !userTenantId.equalsIgnoreCase(targetTenantId.trim())) {
                throw new BusinessException("User " + actorUserId + " does not have access to tenant " + targetTenantId, "FORBIDDEN");
            }
        }
    }

    public boolean isAdminRoleCode(String code) {
        return code != null && ADMIN_ROLE_CODES.contains(code.toUpperCase(Locale.ROOT));
    }

    private Document findProfile(String actorUserId) {
        String normalized = actorUserId.trim();
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("userId").regex("^" + Pattern.quote(normalized) + "$", "i"),
                Criteria.where("username").regex("^" + Pattern.quote(normalized) + "$", "i")
        ));
        return mongoTemplate.findOne(query, Document.class, USER_PROFILES_COLLECTION);
    }
}
