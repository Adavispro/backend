package com.adavis.mdm.service;

import com.adavis.common.exception.BusinessException;
import com.adavis.mdm.model.dto.DashboardSummaryResponse;
import com.adavis.mdm.model.dto.DashboardUserTilesResponse;
import com.adavis.mdm.model.dto.GroupActivityDto;
import com.adavis.mdm.model.dto.RoleUserCountDto;
import com.adavis.mdm.model.entity.Group;
import com.adavis.mdm.model.entity.GroupRoleAssignment;
import com.adavis.mdm.model.entity.Role;
import com.adavis.mdm.model.entity.UserGroupAssignment;
import com.adavis.mdm.model.entity.UserProfile;
import com.adavis.mdm.repository.GroupRepository;
import com.adavis.mdm.repository.GroupRoleAssignmentRepository;
import com.adavis.mdm.repository.RoleRepository;
import com.adavis.mdm.repository.UserGroupAssignmentRepository;
import com.adavis.mdm.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserProfileRepository userProfileRepository;
    private final RoleRepository roleRepository;
    private final GroupRepository groupRepository;
    private final UserGroupAssignmentRepository userGroupAssignmentRepository;
    private final GroupRoleAssignmentRepository groupRoleAssignmentRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${services.auth.base-url:http://auth-service:9081}")
    private String authServiceBaseUrl;

    @Transactional(readOnly = true)
    public DashboardUserTilesResponse getUserTiles(String tenantId) {
        long totalUsersCount = StringUtils.hasText(tenantId)
                ? userProfileRepository.countByTenantIdAndIsActiveTrueAndIsBlockedFalse(tenantId)
                : userProfileRepository.countByIsActiveTrueAndIsBlockedFalse();

        long configuredRolesCount = StringUtils.hasText(tenantId)
                ? roleRepository.countByTenantIdAndIsActiveTrue(tenantId)
                : roleRepository.countByIsActiveTrue();

        long configuredGroupsCount = StringUtils.hasText(tenantId)
                ? groupRepository.countByTenantIdAndIsActiveTrue(tenantId)
                : groupRepository.countByIsActiveTrue();

        Map<String, Object> presenceSummary = fetchPresenceSummary(tenantId);

        long activeUsersCount = toLong(presenceSummary.get("activeUsersCount"));
        long idleUsersCount = toLong(presenceSummary.get("idleUsersCount"));
        long totalOnlineUsersCount = activeUsersCount + idleUsersCount;

        return DashboardUserTilesResponse.builder()
                .tenantId(tenantId)
                .totalUsersCount(totalUsersCount)
                .activeUsersCount(activeUsersCount)
                .idleUsersCount(idleUsersCount)
                .totalOnlineUsersCount(totalOnlineUsersCount)
                .configuredRolesCount(configuredRolesCount)
                .configuredGroupsCount(configuredGroupsCount)
                .idleThresholdMinutes(String.valueOf(presenceSummary.getOrDefault("idleThresholdMinutes", "10")))
                .asOf(String.valueOf(presenceSummary.getOrDefault("asOf", Instant.now().toString())))
                .build();
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getDashboardSummary(String tenantId) {
        DashboardUserTilesResponse userTiles = getUserTiles(tenantId);
        Map<String, Object> presenceSummary = fetchPresenceSummary(tenantId);

        @SuppressWarnings("unchecked")
        List<String> rawActiveUserIds = (List<String>) presenceSummary.get("activeUserIds");
        Set<String> activeUserIds = rawActiveUserIds != null ? new HashSet<>(rawActiveUserIds) : Collections.emptySet();

        List<UserProfile> activeUsers = StringUtils.hasText(tenantId)
                ? userProfileRepository.findByTenantIdAndIsActiveTrue(tenantId)
                : userProfileRepository.findByIsActiveTrue();

        Set<String> validUserIds = activeUsers.stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()) && !Boolean.TRUE.equals(u.getIsBlocked()))
                .map(UserProfile::getUserId)
                .collect(Collectors.toSet());

        // 1. Authoritative Users by Role
        List<Role> roles = StringUtils.hasText(tenantId)
                ? roleRepository.findByTenantIdAndIsActiveTrue(tenantId)
                : roleRepository.findByIsActiveTrue();

        List<RoleUserCountDto> usersByRole = roles.stream()
                .map(role -> {
                    List<GroupRoleAssignment> groupAssignments = groupRoleAssignmentRepository.findByRoleIdAndIsActiveTrue(role.getRoleId());
                    List<String> groupIds = groupAssignments.stream().map(GroupRoleAssignment::getGroupId).toList();

                    long uniqueCount = 0;
                    if (!groupIds.isEmpty()) {
                        List<UserGroupAssignment> userAssignments = userGroupAssignmentRepository.findByGroupIdInAndIsActiveTrue(groupIds);
                        uniqueCount = userAssignments.stream()
                                .map(UserGroupAssignment::getUserId)
                                .filter(validUserIds::contains)
                                .distinct()
                                .count();
                    }

                    String label = StringUtils.hasText(role.getRoleName()) ? role.getRoleName() : (StringUtils.hasText(role.getName()) ? role.getName() : role.getRoleCode());
                    return RoleUserCountDto.builder()
                            .roleId(role.getRoleId())
                            .roleCode(role.getRoleCode())
                            .label(label)
                            .value(uniqueCount)
                            .build();
                })
                .sorted((a, b) -> {
                    int cmp = Long.compare(b.getValue(), a.getValue());
                    return cmp != 0 ? cmp : a.getLabel().compareToIgnoreCase(b.getLabel());
                })
                .collect(Collectors.toList());

        // 2. Authoritative Team Activity
        List<Group> groups = StringUtils.hasText(tenantId)
                ? groupRepository.findByTenantIdAndIsActiveTrue(tenantId)
                : groupRepository.findByIsActiveTrue();

        List<GroupActivityDto> teamActivity = groups.stream()
                .map(group -> {
                    List<UserGroupAssignment> assignments = userGroupAssignmentRepository.findByGroupIdAndIsActiveTrue(group.getGroupId());
                    List<String> groupUserIds = assignments.stream()
                            .map(UserGroupAssignment::getUserId)
                            .filter(validUserIds::contains)
                            .distinct()
                            .toList();

                    long totalMembers = groupUserIds.size();
                    long activeMembers = groupUserIds.stream().filter(activeUserIds::contains).count();

                    String label = StringUtils.hasText(group.getGroupName()) ? group.getGroupName() : (StringUtils.hasText(group.getName()) ? group.getName() : group.getGroupCode());
                    return GroupActivityDto.builder()
                            .groupId(group.getGroupId())
                            .label(label)
                            .total(totalMembers)
                            .value(activeMembers)
                            .build();
                })
                .sorted((a, b) -> {
                    int cmp = Long.compare(b.getTotal(), a.getTotal());
                    return cmp != 0 ? cmp : a.getLabel().compareToIgnoreCase(b.getLabel());
                })
                .collect(Collectors.toList());

        // 3. Authoritative Recent Users
        Pageable pageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UserProfile> recentPage = StringUtils.hasText(tenantId)
                ? userProfileRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable)
                : userProfileRepository.findAllByOrderByCreatedAtDesc(pageable);

        List<Map<String, Object>> recentUsers = recentPage.getContent().stream()
                .map(u -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("userId", u.getUserId());
                    map.put("username", u.getUsername());
                    map.put("email", u.getEmail());
                    map.put("designation", u.getDesignation());
                    map.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "");
                    return map;
                })
                .collect(Collectors.toList());

        return DashboardSummaryResponse.builder()
                .userTiles(userTiles)
                .usersByRole(usersByRole)
                .teamActivity(teamActivity)
                .recentUsers(recentUsers)
                .build();
    }

    private Map<String, Object> fetchPresenceSummary(String tenantId) {
        String url = authServiceBaseUrl + "/internal/v1/auth/users/session-presence-summary";
        if (StringUtils.hasText(tenantId)) {
            url += "?tenantId=" + tenantId;
        }

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Map.of();
            }

            Object data = response.getBody().get("data");
            if (data instanceof Map<?, ?> payload) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : payload.entrySet()) {
                    if (entry.getKey() != null) {
                        result.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                return result;
            }
        } catch (RestClientException ex) {
            log.warn("Failed to fetch session presence summary for tenantId {}: {}", tenantId, ex.getMessage());
        }

        // Return empty presence on transient failure rather than failing the entire dashboard
        return Map.of(
                "activeUsersCount", 0L,
                "idleUsersCount", 0L,
                "totalOnlineUsersCount", 0L,
                "idleThresholdMinutes", 10,
                "asOf", Instant.now().toString()
        );
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0L;
    }
}