package com.adavis.mdm.service;

import com.adavis.mdm.model.dto.DashboardSummaryResponse;
import com.adavis.mdm.model.dto.DashboardUserTilesResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserGroupAssignmentRepository userGroupAssignmentRepository;

    @Mock
    private GroupRoleAssignmentRepository groupRoleAssignmentRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dashboardService, "authServiceBaseUrl", "http://auth-service:9081");
    }

    @Test
    @DisplayName("getUserTiles returns counts for tenant")
    void testGetUserTiles() {
        when(userProfileRepository.countByTenantIdAndIsActiveTrueAndIsBlockedFalse("TNT-0001")).thenReturn(11L);
        when(roleRepository.countByTenantIdAndIsActiveTrue("TNT-0001")).thenReturn(17L);
        when(groupRepository.countByTenantIdAndIsActiveTrue("TNT-0001")).thenReturn(14L);

        DashboardUserTilesResponse tiles = dashboardService.getUserTiles("TNT-0001");

        assertNotNull(tiles);
        assertEquals("TNT-0001", tiles.getTenantId());
        assertEquals(11L, tiles.getTotalUsersCount());
        assertEquals(17L, tiles.getConfiguredRolesCount());
        assertEquals(14L, tiles.getConfiguredGroupsCount());
    }

    @Test
    @DisplayName("getDashboardSummary aggregates unique role counts and team activity")
    void testGetDashboardSummary() {
        String tenantId = "TNT-0001";
        UserProfile u1 = UserProfile.builder().userId("USR-1").isActive(true).isBlocked(false).build();
        UserProfile u2 = UserProfile.builder().userId("USR-2").isActive(true).isBlocked(false).build();
        UserProfile u3 = UserProfile.builder().userId("USR-3").isActive(true).isBlocked(false).build();

        when(userProfileRepository.countByTenantIdAndIsActiveTrueAndIsBlockedFalse(tenantId)).thenReturn(3L);
        when(roleRepository.countByTenantIdAndIsActiveTrue(tenantId)).thenReturn(2L);
        when(groupRepository.countByTenantIdAndIsActiveTrue(tenantId)).thenReturn(1L);

        when(userProfileRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(u1, u2, u3));

        Role role1 = Role.builder().roleId("ROLE-1").roleCode("OP").roleName("Operator").build();
        Role role2 = Role.builder().roleId("ROLE-2").roleCode("REV").roleName("Reviewer").build();
        when(roleRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(role1, role2));

        Group group1 = Group.builder().groupId("GRP-1").groupCode("GRP_OP").groupName("Operator Group").build();
        when(groupRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(group1));

        // Group-Role assignment: GRP-1 assigned to ROLE-1
        GroupRoleAssignment gra = GroupRoleAssignment.builder().groupId("GRP-1").roleId("ROLE-1").isActive(true).build();
        when(groupRoleAssignmentRepository.findByRoleIdAndIsActiveTrue("ROLE-1")).thenReturn(List.of(gra));
        when(groupRoleAssignmentRepository.findByRoleIdAndIsActiveTrue("ROLE-2")).thenReturn(List.of());

        // User-Group assignments: USR-1, USR-2, USR-3 assigned to GRP-1
        UserGroupAssignment uga1 = UserGroupAssignment.builder().userId("USR-1").groupId("GRP-1").isActive(true).build();
        UserGroupAssignment uga2 = UserGroupAssignment.builder().userId("USR-2").groupId("GRP-1").isActive(true).build();
        UserGroupAssignment uga3 = UserGroupAssignment.builder().userId("USR-3").groupId("GRP-1").isActive(true).build();
        when(userGroupAssignmentRepository.findByGroupIdInAndIsActiveTrue(List.of("GRP-1"))).thenReturn(List.of(uga1, uga2, uga3));
        when(userGroupAssignmentRepository.findByGroupIdAndIsActiveTrue("GRP-1")).thenReturn(List.of(uga1, uga2, uga3));

        when(userProfileRepository.findByTenantIdOrderByCreatedAtDesc(eq(tenantId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(u1, u2, u3)));

        DashboardSummaryResponse summary = dashboardService.getDashboardSummary(tenantId);

        assertNotNull(summary);
        assertNotNull(summary.getUserTiles());
        assertEquals(3L, summary.getUserTiles().getTotalUsersCount());

        // Check Users by Role
        assertNotNull(summary.getUsersByRole());
        assertEquals(2, summary.getUsersByRole().size());
        assertEquals("ROLE-1", summary.getUsersByRole().get(0).getRoleId());
        assertEquals(3L, summary.getUsersByRole().get(0).getValue());
        assertEquals("ROLE-2", summary.getUsersByRole().get(1).getRoleId());
        assertEquals(0L, summary.getUsersByRole().get(1).getValue());

        // Check Team Activity
        assertNotNull(summary.getTeamActivity());
        assertEquals(1, summary.getTeamActivity().size());
        assertEquals("GRP-1", summary.getTeamActivity().get(0).getGroupId());
        assertEquals(3L, summary.getTeamActivity().get(0).getTotal());
    }
}
