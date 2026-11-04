package com.adavis.mdm.service;

import com.adavis.common.exception.BusinessException;
import com.adavis.mdm.model.dto.*;
import com.adavis.mdm.model.entity.*;
import com.adavis.mdm.repository.*;
import com.adavis.mdm.security.SecurityContextService;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PrintPermissionServiceTest {

    @Mock
    private PrintPermissionRepository printPermissionRepository;

    @Mock
    private PrintDelegationRepository printDelegationRepository;

    @Mock
    private PrintAuditLogRepository printAuditLogRepository;

    @Mock
    private SecurityContextService securityContextService;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private PrintPermissionService printPermissionService;

    private static final String TENANT_ID = "TNT-0001";
    private static final String SUPER_ADMIN_USER = "SUPER_ADMIN";
    private static final String ADMIN_USER = "IT_ADMIN";
    private static final String QA_USER = "QA_APPROVER_01";
    private static final String QA_ROLE_ID = "ROLE-0017";

    @BeforeEach
    void setUp() {
        lenient().when(securityContextService.resolveEffectiveTenantId(anyString(), anyString())).thenReturn(TENANT_ID);
    }

    @Test
    @DisplayName("Super Admin can grant role print permissions without delegation")
    void saveRolePermission_superAdmin_success() {
        when(securityContextService.isSuperAdmin(SUPER_ADMIN_USER)).thenReturn(true);
        when(printPermissionRepository.findByTenantIdAndTargetTypeAndTargetIdAndModuleAndReportTypeAndIsActiveTrue(
                eq(TENANT_ID), eq("ROLE"), eq(QA_ROLE_ID), eq("IIOT"), eq("BATCH_DOSSIER")
        )).thenReturn(Optional.empty());

        PrintPermission savedEntity = PrintPermission.builder()
                .id("PERM-001")
                .tenantId(TENANT_ID)
                .targetType("ROLE")
                .targetId(QA_ROLE_ID)
                .module("IIOT")
                .reportType("BATCH_DOSSIER")
                .allowedActions(Set.of("VIEW", "PRINT", "REPRINT"))
                .isActive(true)
                .build();
        when(printPermissionRepository.save(any(PrintPermission.class))).thenReturn(savedEntity);

        PrintPermissionRequest request = PrintPermissionRequest.builder()
                .tenantId(TENANT_ID)
                .targetType("ROLE")
                .targetId(QA_ROLE_ID)
                .targetName("QA Approver")
                .module("IIOT")
                .reportType("BATCH_DOSSIER")
                .allowedActions(Set.of("VIEW", "PRINT", "REPRINT"))
                .remarks("Granted QA Approver batch dossier print access")
                .build();

        PrintPermission result = printPermissionService.savePrintPermission(request, SUPER_ADMIN_USER);

        assertNotNull(result);
        assertEquals(QA_ROLE_ID, result.getTargetId());
        assertTrue(result.getAllowedActions().contains("PRINT"));

        // Verify audit log emitted
        ArgumentCaptor<PrintAuditLog> auditCaptor = ArgumentCaptor.forClass(PrintAuditLog.class);
        verify(printAuditLogRepository).save(auditCaptor.capture());
        PrintAuditLog audit = auditCaptor.getValue();
        assertEquals("PERMISSION_CHANGE", audit.getEventCategory());
        assertEquals("AUTHORIZED", audit.getStatus());
        assertEquals(SUPER_ADMIN_USER, audit.getUserId());
    }

    @Test
    @DisplayName("Non-delegated Admin cannot manage print permissions (Default Deny)")
    void saveRolePermission_nonDelegatedAdmin_forbidden() {
        when(securityContextService.isSuperAdmin(ADMIN_USER)).thenReturn(false);
        when(printDelegationRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                .thenReturn(Collections.emptyList());

        PrintPermissionRequest request = PrintPermissionRequest.builder()
                .tenantId(TENANT_ID)
                .targetType("ROLE")
                .targetId(QA_ROLE_ID)
                .module("IIOT")
                .reportType("BATCH_DOSSIER")
                .allowedActions(Set.of("VIEW", "PRINT"))
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                printPermissionService.savePrintPermission(request, ADMIN_USER)
        );

        assertEquals("FORBIDDEN", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Administrative delegation required"));
        verify(printPermissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Super Admin can delegate print management to Admin with module scope")
    void delegateAdmin_superAdmin_success() {
        when(securityContextService.isSuperAdmin(SUPER_ADMIN_USER)).thenReturn(true);

        PrintDelegation delegationEntity = PrintDelegation.builder()
                .id("DEL-001")
                .tenantId(TENANT_ID)
                .delegateeType("USER")
                .delegateeId(ADMIN_USER)
                .delegateeName("IT Administrator")
                .allowedModules(Set.of("IIOT"))
                .delegatedBy(SUPER_ADMIN_USER)
                .isActive(true)
                .build();
        when(printDelegationRepository.save(any(PrintDelegation.class))).thenReturn(delegationEntity);

        PrintDelegationRequest request = PrintDelegationRequest.builder()
                .tenantId(TENANT_ID)
                .delegateeType("USER")
                .delegateeId(ADMIN_USER)
                .delegateeName("IT Administrator")
                .allowedModules(Set.of("IIOT"))
                .remarks("Delegating IIOT print management")
                .build();

        PrintDelegation created = printPermissionService.delegateAdmin(request, SUPER_ADMIN_USER);

        assertNotNull(created);
        assertEquals(ADMIN_USER, created.getDelegateeId());
        assertTrue(created.getAllowedModules().contains("IIOT"));
        assertFalse(created.getCanSubDelegate());
    }

    @Test
    @DisplayName("Delegated Admin can manage permissions within their delegated module")
    void saveRolePermission_delegatedAdmin_success() {
        when(securityContextService.isSuperAdmin(ADMIN_USER)).thenReturn(false);

        PrintDelegation activeDelegation = PrintDelegation.builder()
                .id("DEL-001")
                .tenantId(TENANT_ID)
                .delegateeType("USER")
                .delegateeId(ADMIN_USER)
                .allowedModules(Set.of("IIOT"))
                .isActive(true)
                .build();
        when(printDelegationRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                .thenReturn(List.of(activeDelegation));

        PrintPermission savedEntity = PrintPermission.builder()
                .id("PERM-002")
                .tenantId(TENANT_ID)
                .targetType("ROLE")
                .targetId(QA_ROLE_ID)
                .module("IIOT")
                .reportType("BATCH_DOSSIER")
                .allowedActions(Set.of("VIEW"))
                .build();
        when(printPermissionRepository.save(any(PrintPermission.class))).thenReturn(savedEntity);

        PrintPermissionRequest request = PrintPermissionRequest.builder()
                .tenantId(TENANT_ID)
                .targetType("ROLE")
                .targetId(QA_ROLE_ID)
                .module("IIOT")
                .reportType("BATCH_DOSSIER")
                .allowedActions(Set.of("VIEW"))
                .remarks("Delegated admin authorized configuration")
                .build();

        PrintPermission result = printPermissionService.savePrintPermission(request, ADMIN_USER);
        assertNotNull(result);
        verify(printPermissionRepository).save(any());
    }

    @Test
    @DisplayName("Delegated Admin attempting to manage out-of-scope module throws FORBIDDEN")
    void saveRolePermission_delegatedAdmin_scopeExceeded_forbidden() {
        when(securityContextService.isSuperAdmin(ADMIN_USER)).thenReturn(false);

        PrintDelegation activeDelegation = PrintDelegation.builder()
                .id("DEL-001")
                .tenantId(TENANT_ID)
                .delegateeType("USER")
                .delegateeId(ADMIN_USER)
                .allowedModules(Set.of("MDM")) // Delegated for MDM only, not L2_INTEGRATION / IIOT
                .isActive(true)
                .build();
        when(printDelegationRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                .thenReturn(List.of(activeDelegation));

        PrintPermissionRequest request = PrintPermissionRequest.builder()
                .tenantId(TENANT_ID)
                .targetType("ROLE")
                .targetId(QA_ROLE_ID)
                .module("L2_INTEGRATION") // Out of scope
                .reportType("L2_RECIPE_REPORT")
                .allowedActions(Set.of("VIEW"))
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                printPermissionService.savePrintPermission(request, ADMIN_USER)
        );

        assertEquals("FORBIDDEN", ex.getErrorCode());
        verify(printPermissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Privilege Escalation Prevention: Delegated Admin cannot modify administrative role")
    void saveRolePermission_privilegeEscalation_forbidden() {
        when(securityContextService.isSuperAdmin(ADMIN_USER)).thenReturn(false);

        PrintDelegation activeDelegation = PrintDelegation.builder()
                .id("DEL-001")
                .tenantId(TENANT_ID)
                .delegateeType("USER")
                .delegateeId(ADMIN_USER)
                .allowedModules(Set.of("*"))
                .isActive(true)
                .build();
        when(printDelegationRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                .thenReturn(List.of(activeDelegation));

        when(securityContextService.isAdminRoleCode("SUPER_ADMIN")).thenReturn(true);
        Document adminRoleDoc = new Document("roleId", "ROLE-0001").append("roleCode", "SUPER_ADMIN");
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_roles")))
                .thenReturn(adminRoleDoc);

        PrintPermissionRequest request = PrintPermissionRequest.builder()
                .tenantId(TENANT_ID)
                .targetType("ROLE")
                .targetId("ROLE-0001")
                .module("IIOT")
                .reportType("BATCH_DOSSIER")
                .allowedActions(Set.of("VIEW", "PRINT"))
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () ->
                printPermissionService.savePrintPermission(request, ADMIN_USER)
        );

        assertEquals("FORBIDDEN", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Privilege escalation prevented"));
    }

    @Test
    @DisplayName("Effective Access: User inherits role permissions and applies user-level revoke override")
    void calculateEffectiveAccess_roleInheritance_and_userRevoke() {
        when(securityContextService.isSuperAdmin(QA_USER)).thenReturn(false);

        // Mock User's assigned group and role
        Document userAssign = new Document("userId", QA_USER).append("groupId", "GRP-0017").append("isActive", true);
        doReturn(List.of(userAssign)).when(mongoTemplate).find(
                any(Query.class), eq(Document.class), eq("mdm_user_assignments_to_user_groups"));

        Document roleAssign = new Document("groupId", "GRP-0017").append("roleId", QA_ROLE_ID).append("isActive", true);
        doReturn(List.of(roleAssign)).when(mongoTemplate).find(
                any(Query.class), eq(Document.class), eq("mdm_role_assignments_to_user_groups"));

        // Role has VIEW, PRINT, REPRINT
        PrintPermission rolePerm = PrintPermission.builder()
                .tenantId(TENANT_ID)
                .targetType("ROLE")
                .targetId(QA_ROLE_ID)
                .module("IIOT")
                .reportType("BATCH_DOSSIER")
                .allowedActions(Set.of("VIEW", "PRINT", "REPRINT"))
                .isActive(true)
                .build();
        when(printPermissionRepository.findByTenantIdAndTargetTypeAndTargetIdInAndIsActiveTrue(
                eq(TENANT_ID), eq("ROLE"), anyList()
        )).thenReturn(List.of(rolePerm));

        // User override explicitly REVOKES "REPRINT"
        PrintPermission userOverride = PrintPermission.builder()
                .tenantId(TENANT_ID)
                .targetType("USER")
                .targetId(QA_USER)
                .module("IIOT")
                .reportType("BATCH_DOSSIER")
                .allowedActions(Set.of("VIEW"))
                .deniedActions(Set.of("REPRINT")) // Explicit deny!
                .isActive(true)
                .build();
        when(printPermissionRepository.findByTenantIdAndTargetTypeAndTargetIdAndIsActiveTrue(
                eq(TENANT_ID), eq("USER"), eq(QA_USER)
        )).thenReturn(List.of(userOverride));

        EffectivePrintAccessResponse access = printPermissionService.calculateEffectiveAccess(
                TENANT_ID, QA_USER, "IIOT", "BATCH_DOSSIER");

        assertNotNull(access);
        assertTrue(access.canView(), "VIEW should be allowed");
        assertTrue(access.canPrint(), "PRINT should be inherited from role");
        assertFalse(access.canReprint(), "REPRINT should be DENIED due to user override");
        assertTrue(access.getDeniedActions().contains("REPRINT"));
    }

    @Test
    @DisplayName("VerifyPrintAccess records AUTHORIZED attempt when permission is present")
    void verifyPrintAccess_authorized_success() {
        when(securityContextService.isSuperAdmin(QA_USER)).thenReturn(true);

        PrintAccessCheckRequest checkReq = PrintAccessCheckRequest.builder()
                .tenantId(TENANT_ID)
                .userId(QA_USER)
                .module("IIOT")
                .reportType("BATCH_DOSSIER")
                .action("PRINT")
                .documentId("BATCH-12345")
                .copyNumber(1)
                .reason("Batch final QA signoff print")
                .build();

        PrintAccessCheckResponse resp = printPermissionService.verifyPrintAccess(checkReq);

        assertTrue(resp.isAllowed());
        assertEquals("PRINT", resp.getAction());

        ArgumentCaptor<PrintAuditLog> auditCaptor = ArgumentCaptor.forClass(PrintAuditLog.class);
        verify(printAuditLogRepository).save(auditCaptor.capture());
        PrintAuditLog logEntry = auditCaptor.getValue();
        assertEquals("PRINT_ATTEMPT", logEntry.getEventCategory());
        assertEquals("AUTHORIZED", logEntry.getStatus());
        assertEquals("BATCH-12345", logEntry.getDocumentId());
    }

    @Test
    @DisplayName("VerifyPrintAccess records DENIED attempt and returns false when permission is missing")
    void verifyPrintAccess_denied_recordsAudit() {
        when(securityContextService.isSuperAdmin("UNKNOWN_USER")).thenReturn(false);
        when(printDelegationRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                .thenReturn(Collections.emptyList());

        PrintAccessCheckRequest checkReq = PrintAccessCheckRequest.builder()
                .tenantId(TENANT_ID)
                .userId("UNKNOWN_USER")
                .module("IIOT")
                .reportType("BATCH_DOSSIER")
                .action("REPRINT")
                .documentId("BATCH-12345")
                .copyNumber(2)
                .reason("Unapproved reprint attempt")
                .build();

        PrintAccessCheckResponse resp = printPermissionService.verifyPrintAccess(checkReq);

        assertFalse(resp.isAllowed());
        assertEquals("REPRINT", resp.getAction());
        assertTrue(resp.getMessage().contains("Access denied"));

        ArgumentCaptor<PrintAuditLog> auditCaptor = ArgumentCaptor.forClass(PrintAuditLog.class);
        verify(printAuditLogRepository).save(auditCaptor.capture());
        PrintAuditLog logEntry = auditCaptor.getValue();
        assertEquals("PRINT_ATTEMPT", logEntry.getEventCategory());
        assertEquals("DENIED", logEntry.getStatus());
        assertNotNull(logEntry.getDenialReason());
    }
}
