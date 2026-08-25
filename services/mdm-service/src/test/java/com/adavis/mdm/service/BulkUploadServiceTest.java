package com.adavis.mdm.service;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BulkUploadServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private BusinessIdGeneratorService businessIdGeneratorService;

    private CsvTemplateService csvTemplateService;
    private BulkUploadService bulkUploadService;

    @BeforeEach
    void setUp() {
        csvTemplateService = new CsvTemplateService();
        bulkUploadService = new BulkUploadService(mongoTemplate, csvTemplateService, businessIdGeneratorService);
    }

    @Test
    @DisplayName("1. Verify templates for all entity types contain only business fields and no database IDs")
    void testTemplatesDoNotContainDatabaseIds() {
        for (CsvTemplateService.BulkType type : CsvTemplateService.BulkType.values()) {
            List<String> headers = csvTemplateService.getHeaders(type);
            assertNotNull(headers);
            assertFalse(headers.isEmpty());

            // Verify no entity-level internal primary database ID is present in headers
            switch (type) {
                case TENANT -> assertFalse(headers.contains("tenantId"), "TENANT template must not contain tenantId");
                case PLANT -> {
                    assertFalse(headers.contains("plantId"), "PLANT template must not contain plantId");
                    assertFalse(headers.contains("blockId"), "PLANT template must not contain blockId");
                    assertFalse(headers.contains("areaId"), "PLANT template must not contain areaId");
                    assertFalse(headers.contains("roomId"), "PLANT template must not contain roomId");
                }
                case DEPARTMENT -> assertFalse(headers.contains("departmentId"), "DEPARTMENT template must not contain departmentId");
                case ROLE -> assertFalse(headers.contains("roleId"), "ROLE template must not contain roleId");
                case USER -> {
                    assertFalse(headers.contains("userId"), "USER template must not contain userId");
                    assertFalse(headers.contains("userTrackId"), "USER template must not contain userTrackId");
                }
                case USER_GROUP -> assertFalse(headers.contains("groupId"), "USER_GROUP template must not contain groupId");
                case IIOT_MASTER -> assertFalse(headers.contains("equipmentId"), "IIOT_MASTER template must not contain equipmentId");
            }
        }
    }

    @Test
    @DisplayName("2. Bulk upload new departments without ID automatically generates sequential DEP- IDs")
    void testDepartmentUploadGeneratesSequenceIds() {
        String csv = """
                departmentCode,departmentName,description,plantCode,parentDepartmentCode,isActive
                QA-NEW,Quality Assurance New,QA Dept,HYD-01,,true
                QC-NEW,Quality Control New,QC Dept,HYD-01,QA-NEW,true
                """;
        InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        when(businessIdGeneratorService.nextId(eq("mdm_departments"), eq("departmentId"), eq("DEP-"), eq(4)))
                .thenReturn("DEP-0017", "DEP-0018");
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_departments")))
                .thenReturn(null);

        Map<String, Object> result = bulkUploadService.processBulkUpload(
                "DEPARTMENT", "UPDATE", "TNT-0001", "SUPER_ADMIN", stream);

        assertEquals("SUCCESS", result.get("status"));
        assertEquals(2, result.get("totalRows"));
        assertEquals(2, result.get("createdCount"));
        assertEquals(0, result.get("updatedCount"));
        assertEquals(0, result.get("errorCount"));

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(mongoTemplate, times(2)).insert(docCaptor.capture(), eq("mdm_departments"));

        List<Document> saved = docCaptor.getAllValues();
        assertEquals("DEP-0017", saved.get(0).getString("departmentId"));
        assertEquals("QA-NEW", saved.get(0).getString("departmentCode"));
        assertEquals("DEP-0018", saved.get(1).getString("departmentId"));
        assertEquals("QC-NEW", saved.get(1).getString("departmentCode"));
    }

    @Test
    @DisplayName("3. Bulk upload existing department updates record and preserves original internal ID")
    void testDepartmentUploadUpdatesExistingRecord() {
        String csv = """
                departmentCode,departmentName,description,plantCode,parentDepartmentCode,isActive
                PROD,Production Updated,Updated Department,HYD-01,,true
                """;
        InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        Document existingDoc = new Document();
        existingDoc.put("_id", "mongo-obj-id-123");
        existingDoc.put("departmentId", "DEP-0002");
        existingDoc.put("departmentCode", "PROD");
        existingDoc.put("tenantId", "TNT-0001");
        existingDoc.put("createdAt", new java.util.Date());

        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_departments")))
                .thenReturn(existingDoc);

        Map<String, Object> result = bulkUploadService.processBulkUpload(
                "DEPARTMENT", "UPDATE", "TNT-0001", "SUPER_ADMIN", stream);

        assertEquals("SUCCESS", result.get("status"));
        assertEquals(1, result.get("totalRows"));
        assertEquals(0, result.get("createdCount"));
        assertEquals(1, result.get("updatedCount"));

        ArgumentCaptor<Document> docCaptor = ArgumentCaptor.forClass(Document.class);
        verify(mongoTemplate, times(1)).save(docCaptor.capture(), eq("mdm_departments"));

        Document updated = docCaptor.getValue();
        assertEquals("DEP-0002", updated.getString("departmentId"));
        assertEquals("Production Updated", updated.getString("departmentName"));
        assertEquals("mongo-obj-id-123", updated.get("_id"));
    }

    @Test
    @DisplayName("4. In-file duplicate business key is detected and rejected without persisting")
    void testInFileDuplicateDetection() {
        String csv = """
                roleCode,roleName,description,isActive
                OPERATOR,Operator Role 1,Desc 1,true
                OPERATOR,Operator Role 2,Desc 2,true
                """;
        InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> result = bulkUploadService.processBulkUpload(
                "ROLE", "UPDATE", "TNT-0001", "SUPER_ADMIN", stream);

        assertEquals("FAILED", result.get("status"));
        assertEquals(2, result.get("totalRows"));
        assertEquals(0, result.get("createdCount"));
        assertEquals(1, result.get("errorCount"));

        verify(mongoTemplate, never()).insert(any(Document.class), anyString());
        verify(mongoTemplate, never()).save(any(Document.class), anyString());
    }

    @Test
    @DisplayName("5. Bulk upload new users generates USR- sequence and creates auth documents")
    void testUserUploadGeneratesUserTrackIdAndAuth() {
        String csv = """
                username,email,firstName,lastName,departmentCode,designation,initialPassword,title,userType,empId,isActive
                new_op_01,new_op_01@adavis.com,Alice,Smith,PROD,Operator,Password@123,Ms.,Internal,EMP-00501,true
                """;
        InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        when(businessIdGeneratorService.nextId(eq("mdm_user_profiles"), eq("userTrackId"), eq("USR-"), eq(4)))
                .thenReturn("USR-0012");
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_user_profiles")))
                .thenReturn(null);

        Map<String, Object> result = bulkUploadService.processBulkUpload(
                "USER", "UPDATE", "TNT-0001", "SUPER_ADMIN", stream);

        assertEquals("SUCCESS", result.get("status"));
        assertEquals(1, result.get("createdCount"));

        ArgumentCaptor<Document> userCaptor = ArgumentCaptor.forClass(Document.class);
        verify(mongoTemplate, times(1)).insert(userCaptor.capture(), eq("mdm_user_profiles"));
        assertEquals("USR-0012", userCaptor.getValue().getString("userTrackId"));
        assertEquals("NEW_OP_01", userCaptor.getValue().getString("userId"));

        verify(mongoTemplate, times(1)).insert(any(Document.class), eq("auth_users"));
        verify(mongoTemplate, times(1)).insert(any(Document.class), eq("mdm_user_auth_credentials"));
    }

    @Test
    @DisplayName("6. Truncate and load mode removes existing tenant records before inserting new with auto IDs")
    void testTruncateAndLoadMode() {
        String csv = """
                groupCode,groupName,description,isActive
                GRP_NEW_01,New Group 1,Description 1,true
                """;
        InputStream stream = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        when(businessIdGeneratorService.nextId(eq("mdm_user_groups"), eq("groupId"), eq("GRP-"), eq(4)))
                .thenReturn("GRP-0015");

        Map<String, Object> result = bulkUploadService.processBulkUpload(
                "USER_GROUP", "TRUNCATE_AND_LOAD", "TNT-0001", "SUPER_ADMIN", stream);

        assertEquals("SUCCESS", result.get("status"));
        verify(mongoTemplate, times(1)).remove(any(Query.class), eq("mdm_user_groups"));
        verify(mongoTemplate, times(1)).insert(any(Document.class), eq("mdm_user_groups"));
    }
}
