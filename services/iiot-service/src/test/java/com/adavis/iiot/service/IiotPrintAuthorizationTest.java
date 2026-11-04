package com.adavis.iiot.service;

import com.adavis.common.exception.BusinessException;
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

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class IiotPrintAuthorizationTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private PrintAuthorizationEvaluator evaluator;

    private static final String TENANT_ID = "TNT-0001";
    private static final String PLANT_ID = "PLNT-0001";
    private static final String QA_USER = "QA_APPROVER_01";
    private static final String OPERATOR_USER = "OPERATOR_01";

    @Test
    @DisplayName("Super Admin is granted universal access for any print action")
    void checkAccess_superAdmin_granted() {
        PrintAuthorizationEvaluator.AuthorizationResult result = evaluator.checkAndAuditAccess(
                TENANT_ID, PLANT_ID, "SUPER_ADMIN", "Super Administrator",
                "IIOT", "BATCH_DOSSIER", "PRINT", "BATCH-001", 1, "Testing print"
        );

        assertTrue(result.isAllowed());
        assertEquals("PRINT", result.getAction());
        assertEquals("SUPER_ADMIN_UNIVERSAL", result.getSource());

        ArgumentCaptor<Document> auditCaptor = ArgumentCaptor.forClass(Document.class);
        verify(mongoTemplate).insert(auditCaptor.capture(), eq("mdm_print_audit_logs"));
        Document audit = auditCaptor.getValue();
        assertEquals("AUTHORIZED", audit.getString("status"));
        assertEquals("SUPER_ADMIN", audit.getString("userId"));
    }

    @Test
    @DisplayName("User with role permission for VIEW is allowed to preview/download PDF")
    void checkAccess_rolePermissionView_allowed() {
        // Mock user assignment to group and role
        Document userAssign = new Document("userId", QA_USER).append("groupId", "GRP-0017").append("isActive", true);
        doReturn(List.of(userAssign)).when(mongoTemplate).find(
                any(Query.class), eq(Document.class), eq("mdm_user_assignments_to_user_groups"));

        Document roleAssign = new Document("groupId", "GRP-0017").append("roleId", "ROLE-0017").append("isActive", true);
        doReturn(List.of(roleAssign)).when(mongoTemplate).find(
                any(Query.class), eq(Document.class), eq("mdm_role_assignments_to_user_groups"));

        // No user override
        doReturn(Collections.emptyList()).when(mongoTemplate).find(
                any(Query.class), eq(Document.class), eq("mdm_print_permissions"));

        // Role permission allows VIEW
        Document rolePermDoc = new Document("tenantId", TENANT_ID)
                .append("targetType", "ROLE")
                .append("targetId", "ROLE-0017")
                .append("module", "IIOT")
                .append("reportType", "BATCH_DOSSIER")
                .append("allowedActions", List.of("VIEW", "PRINT"))
                .append("isActive", true);

        doReturn(List.of(rolePermDoc)).when(mongoTemplate).find(
                argThat(q -> q != null && q.toString().contains("ROLE")), eq(Document.class), eq("mdm_print_permissions"));

        PrintAuthorizationEvaluator.AuthorizationResult result = evaluator.checkAndAuditAccess(
                TENANT_ID, PLANT_ID, QA_USER, "QA Approver",
                "IIOT", "BATCH_DOSSIER", "VIEW", "BATCH-001", null, "Preview report"
        );

        assertTrue(result.isAllowed());
        assertEquals("VIEW", result.getAction());
    }

    @Test
    @DisplayName("User lacking REPRINT permission is DENIED when reprinting Copy #2+")
    void checkAccess_missingReprint_denied() {
        // Mock user assignment to group and role
        Document userAssign = new Document("userId", OPERATOR_USER).append("groupId", "GRP-0011").append("isActive", true);
        doReturn(List.of(userAssign)).when(mongoTemplate).find(
                any(Query.class), eq(Document.class), eq("mdm_user_assignments_to_user_groups"));

        Document roleAssign = new Document("groupId", "GRP-0011").append("roleId", "ROLE-0008").append("isActive", true);
        doReturn(List.of(roleAssign)).when(mongoTemplate).find(
                any(Query.class), eq(Document.class), eq("mdm_role_assignments_to_user_groups"));

        // Role only has VIEW and PRINT (initial copy), NO REPRINT
        Document rolePermDoc = new Document("tenantId", TENANT_ID)
                .append("targetType", "ROLE")
                .append("targetId", "ROLE-0008")
                .append("module", "IIOT")
                .append("reportType", "BATCH_DOSSIER")
                .append("allowedActions", List.of("VIEW", "PRINT"))
                .append("isActive", true);

        doReturn(List.of(rolePermDoc)).when(mongoTemplate).find(
                argThat(q -> q != null && q.toString().contains("ROLE")), eq(Document.class), eq("mdm_print_permissions"));

        doReturn(Collections.emptyList()).when(mongoTemplate).find(
                argThat(q -> q != null && q.toString().contains("USER")), eq(Document.class), eq("mdm_print_permissions"));

        PrintAuthorizationEvaluator.AuthorizationResult result = evaluator.checkAndAuditAccess(
                TENANT_ID, PLANT_ID, OPERATOR_USER, "Production Operator",
                "IIOT", "BATCH_DOSSIER", "REPRINT", "BATCH-001", 2, "Duplicate copy needed"
        );

        assertFalse(result.isAllowed(), "REPRINT should be denied because role lacks REPRINT action");
        assertTrue(result.getMessage().contains("Access denied"));

        ArgumentCaptor<Document> auditCaptor = ArgumentCaptor.forClass(Document.class);
        verify(mongoTemplate).insert(auditCaptor.capture(), eq("mdm_print_audit_logs"));
        Document audit = auditCaptor.getValue();
        assertEquals("DENIED", audit.getString("status"));
        assertEquals("REPRINT_ATTEMPT", audit.getString("action"));
        assertEquals(OPERATOR_USER, audit.getString("userId"));
        assertNotNull(audit.getString("denialReason"));
    }

    @Test
    @DisplayName("User with explicit user override REVOKE is denied even if role allows it")
    void checkAccess_userOverrideRevoke_denied() {
        lenient().doReturn(Collections.emptyList()).when(mongoTemplate).find(
                any(Query.class), eq(Document.class), eq("mdm_user_assignments_to_user_groups"));

        // User has explicit deny on PRINT
        Document userOverride = new Document("tenantId", TENANT_ID)
                .append("targetType", "USER")
                .append("targetId", QA_USER)
                .append("module", "IIOT")
                .append("reportType", "BATCH_DOSSIER")
                .append("allowedActions", List.of("VIEW"))
                .append("deniedActions", List.of("PRINT", "REPRINT")) // Revoked!
                .append("isActive", true);

        doReturn(List.of(userOverride)).when(mongoTemplate).find(
                argThat(q -> q != null && q.toString().contains("USER")), eq(Document.class), eq("mdm_print_permissions"));

        PrintAuthorizationEvaluator.AuthorizationResult result = evaluator.checkAndAuditAccess(
                TENANT_ID, PLANT_ID, QA_USER, "QA Approver",
                "IIOT", "BATCH_DOSSIER", "PRINT", "BATCH-001", 1, "Print attempt"
        );

        assertFalse(result.isAllowed(), "PRINT should be DENIED due to explicit user override revocation");
        assertEquals("USER_OVERRIDE_REVOKE", result.getSource());
    }
}
