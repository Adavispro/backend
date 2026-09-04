package com.adavis.mdm.service;

import com.adavis.common.exception.BusinessException;
import com.adavis.common.exception.UnauthorizedException;
import com.adavis.mdm.model.entity.GroupRoleAssignment;
import com.adavis.mdm.model.entity.Role;
import com.adavis.mdm.model.entity.UserGroupAssignment;
import com.adavis.mdm.model.entity.UserProfile;
import com.adavis.mdm.repository.*;
import com.adavis.mdm.security.SecurityContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private DmsDocumentRepository dmsDocumentRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserGroupAssignmentRepository userGroupAssignmentRepository;

    @Mock
    private GroupRoleAssignmentRepository groupRoleAssignmentRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private MetadataCatalogService metadataCatalogService;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private BusinessIdGeneratorService businessIdGeneratorService;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private SecurityContextService securityContextService;

    @Mock
    private TopologyEsignatureService topologyEsignatureService;

    @InjectMocks
    private UserService userService;

    private UserProfile sampleUser;
    private static final String ADMIN_ACTOR = "ADMIN-001";
    private static final String TARGET_USER_ID = "USER-TEST-01";
    private static final String TENANT_ID = "TNT-0001";
    private static final String REMARKS = "Authorizing user management action";
    private static final String PASSWORD = "AdminPassword123!";

    @BeforeEach
    void setUp() {
        sampleUser = UserProfile.builder()
                .userId(TARGET_USER_ID)
                .username("testuser")
                .email("testuser@adavis.com")
                .firstName("Test")
                .lastName("User")
                .tenantId(TENANT_ID)
                .departmentId("DEP-0001")
                .title("QA Specialist")
                .userType("STANDARD")
                .lifecycleStatus("ACTIVE")
                .empId("EMP-1234")
                .isActive(true)
                .isBlocked(false)
                .build();
    }

    private void mockAdminPrivileges() {
        UserProfile admin = UserProfile.builder()
                .userId(ADMIN_ACTOR)
                .tenantId(TENANT_ID)
                .isActive(true)
                .build();
        when(userProfileRepository.findByUserId(ADMIN_ACTOR)).thenReturn(Optional.of(admin));

        UserGroupAssignment uga = UserGroupAssignment.builder()
                .userId(ADMIN_ACTOR)
                .groupId("GRP-ADMIN")
                .isActive(true)
                .build();
        when(userGroupAssignmentRepository.findByUserIdAndIsActiveTrue(ADMIN_ACTOR)).thenReturn(List.of(uga));

        GroupRoleAssignment gra = GroupRoleAssignment.builder()
                .groupId("GRP-ADMIN")
                .roleId("ROLE-IT-ADMIN")
                .isActive(true)
                .build();
        when(groupRoleAssignmentRepository.findByGroupIdInAndIsActiveTrue(List.of("GRP-ADMIN"))).thenReturn(List.of(gra));

        Role adminRole = Role.builder()
                .roleId("ROLE-IT-ADMIN")
                .roleCode("IT_ADMIN")
                .tenantId(TENANT_ID)
                .isActive(true)
                .build();
        when(roleRepository.findByRoleId("ROLE-IT-ADMIN")).thenReturn(Optional.of(adminRole));
    }

    @Test
    @DisplayName("createUser throws AUTH_CONTEXT_REQUIRED if actor is null or SYSTEM")
    void createUser_missingActor_throwsUnauthorizedException() {
        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () ->
                userService.createUser(sampleUser, "Password123!", null, null, List.of(), List.of(), null, null, REMARKS, PASSWORD));
        assertEquals("AUTH_CONTEXT_REQUIRED", ex.getErrorCode());
    }

    @Test
    @DisplayName("createUser throws TENANT_ID_REQUIRED if tenantId is missing")
    void createUser_missingTenantId_throwsBusinessException() {
        sampleUser.setTenantId(null);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.createUser(sampleUser, "Password123!", ADMIN_ACTOR, ADMIN_ACTOR, List.of(), List.of(), null, null, REMARKS, PASSWORD));
        assertEquals("TENANT_ID_REQUIRED", ex.getErrorCode());
    }

    @Test
    @DisplayName("createUser delegates remarks and e-signature validation to TopologyEsignatureService")
    void createUser_delegatesEsignatureValidation() {
        mockAdminPrivileges();
        doThrow(new BusinessException("Remarks are required for controlled action.", "REMARKS_REQUIRED"))
                .when(topologyEsignatureService).validateRemarks(any());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.createUser(sampleUser, "Password123!", ADMIN_ACTOR, ADMIN_ACTOR, List.of(), List.of(), null, null, null, PASSWORD));
        assertEquals("REMARKS_REQUIRED", ex.getErrorCode());
    }

    @Test
    @DisplayName("createUser on success captures sanitized after snapshot and publishes audit with authentic actor")
    void createUser_success_publishesAuditWithAuthenticActor() {
        mockAdminPrivileges();
        when(businessIdGeneratorService.nextId("mdm_user_profiles", "userTrackId", "USR-", 4)).thenReturn("USR-0099");
        when(userProfileRepository.existsByUserId(TARGET_USER_ID)).thenReturn(false);
        when(userProfileRepository.existsByUserTrackId("USR-0099")).thenReturn(false);
        when(userProfileRepository.findByEmail("testuser@adavis.com")).thenReturn(Optional.empty());
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfile created = userService.createUser(sampleUser, "Password123!", ADMIN_ACTOR, ADMIN_ACTOR,
                List.of(), List.of(), null, null, REMARKS, PASSWORD);

        assertNotNull(created);
        verify(topologyEsignatureService).validateRemarks(REMARKS);
        verify(topologyEsignatureService).verifyEsignature(eq(ADMIN_ACTOR), eq(PASSWORD), anyString(), eq(TENANT_ID));

        ArgumentCaptor<String> actorCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> entityCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> entityIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> beforeCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> afterCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> metaCaptor = ArgumentCaptor.forClass(Map.class);

        verify(auditEventPublisher).publish(
                actorCaptor.capture(),
                actionCaptor.capture(),
                entityCaptor.capture(),
                entityIdCaptor.capture(),
                statusCaptor.capture(),
                beforeCaptor.capture(),
                afterCaptor.capture(),
                metaCaptor.capture()
        );

        // Verify authentic actor attribution (NOT the newly created user)
        assertEquals(ADMIN_ACTOR, actorCaptor.getValue());
        assertEquals("USER_CREATED", actionCaptor.getValue());
        assertEquals("MDM_USER", entityCaptor.getValue());
        assertEquals(TARGET_USER_ID, entityIdCaptor.getValue());
        assertEquals("SUCCESS", statusCaptor.getValue());

        // Verify Part 11 metadata
        Map<String, Object> meta = metaCaptor.getValue();
        assertEquals(REMARKS, meta.get("remarks"));
        assertEquals(true, meta.get("esignatureVerified"));
        assertEquals("21_CFR_PART_11", meta.get("complianceStandard"));

        // Verify after snapshot does not contain sensitive passwords
        Map<String, Object> after = afterCaptor.getValue();
        assertNotNull(after);
        assertEquals(TARGET_USER_ID, after.get("userId"));
        assertFalse(after.containsKey("password"));
        assertFalse(after.containsKey("initialPassword"));
        assertFalse(after.containsKey("esignPassword"));
        assertNull(beforeCaptor.getValue());
    }

    @Test
    @DisplayName("updateUser throws FORBIDDEN when non-super-admin attempts to modify super admin")
    void updateUser_modifySuperAdmin_throwsForbidden() {
        when(userProfileRepository.findByUserId(TARGET_USER_ID)).thenReturn(Optional.of(sampleUser));
        when(securityContextService.isSuperAdmin(TARGET_USER_ID)).thenReturn(true);
        when(securityContextService.isSuperAdmin(ADMIN_ACTOR)).thenReturn(false);

        UserProfile updated = UserProfile.builder().firstName("Modified").build();
        BusinessException ex = assertThrows(BusinessException.class, () ->
                userService.updateUser(TARGET_USER_ID, updated, ADMIN_ACTOR, REMARKS, PASSWORD));
        assertEquals("FORBIDDEN", ex.getErrorCode());
    }

    @Test
    @DisplayName("updateUser records sanitized before and after snapshots with authentic actor")
    void updateUser_success_recordsBeforeAndAfterAudit() {
        when(userProfileRepository.findByUserId(TARGET_USER_ID)).thenReturn(Optional.of(sampleUser));
        when(securityContextService.isSuperAdmin(TARGET_USER_ID)).thenReturn(false);
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfile updatedProfile = UserProfile.builder()
                .firstName("UpdatedFirst")
                .lastName("UpdatedLast")
                .title("Senior QA Specialist")
                .build();

        UserProfile result = userService.updateUser(TARGET_USER_ID, updatedProfile, ADMIN_ACTOR, REMARKS, PASSWORD);
        assertNotNull(result);

        ArgumentCaptor<String> actorCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> beforeCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> afterCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> metaCaptor = ArgumentCaptor.forClass(Map.class);

        verify(auditEventPublisher).publish(
                actorCaptor.capture(),
                actionCaptor.capture(),
                eq("MDM_USER"),
                eq(TARGET_USER_ID),
                eq("SUCCESS"),
                beforeCaptor.capture(),
                afterCaptor.capture(),
                metaCaptor.capture()
        );

        assertEquals(ADMIN_ACTOR, actorCaptor.getValue());
        assertEquals("USER_UPDATED", actionCaptor.getValue());

        Map<String, Object> before = beforeCaptor.getValue();
        Map<String, Object> after = afterCaptor.getValue();
        assertNotNull(before);
        assertNotNull(after);
        assertEquals("Test", before.get("firstName"));
        assertEquals("UpdatedFirst", after.get("firstName"));

        // Verify zero credential leakage
        assertFalse(before.containsKey("password"));
        assertFalse(after.containsKey("password"));
        assertFalse(before.containsKey("esignPassword"));
        assertFalse(after.containsKey("esignPassword"));
    }

    @Test
    @DisplayName("deleteUser deactivates account, verifies e-signature, and publishes audit with authentic actor")
    void deleteUser_success_publishesAudit() {
        when(userProfileRepository.findByUserId(TARGET_USER_ID)).thenReturn(Optional.of(sampleUser));
        when(securityContextService.isSuperAdmin(TARGET_USER_ID)).thenReturn(false);
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.deleteUser(TARGET_USER_ID, ADMIN_ACTOR, REMARKS, PASSWORD);

        verify(topologyEsignatureService).validateRemarks(REMARKS);
        verify(topologyEsignatureService).verifyEsignature(eq(ADMIN_ACTOR), eq(PASSWORD), anyString(), eq(TENANT_ID));

        ArgumentCaptor<String> actorCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> beforeCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> afterCaptor = ArgumentCaptor.forClass(Map.class);

        verify(auditEventPublisher).publish(
                actorCaptor.capture(),
                actionCaptor.capture(),
                eq("MDM_USER"),
                eq(TARGET_USER_ID),
                eq("SUCCESS"),
                beforeCaptor.capture(),
                afterCaptor.capture(),
                any()
        );

        assertEquals(ADMIN_ACTOR, actorCaptor.getValue());
        assertEquals("USER_DELETED", actionCaptor.getValue());
        assertEquals(true, beforeCaptor.getValue().get("isActive"));
        assertEquals(false, afterCaptor.getValue().get("isActive"));
        assertEquals("DELETED", afterCaptor.getValue().get("lifecycleStatus"));
    }

    @Test
    @DisplayName("updateLifecycle validates action and verifies e-signature")
    void updateLifecycle_validAction_publishesAudit() {
        when(userProfileRepository.findByUserId(TARGET_USER_ID)).thenReturn(Optional.of(sampleUser));
        when(securityContextService.isSuperAdmin(TARGET_USER_ID)).thenReturn(false);
        when(userProfileRepository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.updateLifecycle(TARGET_USER_ID, "deactivate", ADMIN_ACTOR,
                List.of(), List.of(), null, null, REMARKS, PASSWORD);

        verify(topologyEsignatureService).validateRemarks(REMARKS);
        verify(topologyEsignatureService).verifyEsignature(eq(ADMIN_ACTOR), eq(PASSWORD), anyString(), eq(TENANT_ID));

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditEventPublisher).publish(
                eq(ADMIN_ACTOR),
                actionCaptor.capture(),
                eq("MDM_USER"),
                eq(TARGET_USER_ID),
                eq("SUCCESS"),
                any(),
                any(),
                any()
        );
        assertEquals("USER_DEACTIVATED", actionCaptor.getValue());
    }
}
