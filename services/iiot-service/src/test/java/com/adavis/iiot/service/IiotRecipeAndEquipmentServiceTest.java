package com.adavis.iiot.service;

import com.adavis.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IiotRecipeAndEquipmentServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private BatchPdfGeneratorService batchPdfGeneratorService;

    private IiotOperationsService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_plants")))
                .thenReturn(new Document("plantId", "PLNT-0001").append("tenantId", "TNT-0001"));
        when(mongoTemplate.exists(any(Query.class), eq("mdm_plants"))).thenReturn(true);
        service = new IiotOperationsService(mongoTemplate, stringRedisTemplate, new ObjectMapper(), batchPdfGeneratorService);
    }

    @Test
    void testEquipmentUpdate_UnrelatedFieldPreservesTopology() {
        Document existingEq = new Document("equipmentId", "EQ-001")
                .append("equipmentCode", "RMG-01")
                .append("equipmentName", "Old Name")
                .append("tenantId", "TNT-0001")
                .append("plantId", "PLNT-0001")
                .append("blockId", "BLK-0001")
                .append("areaId", "AREA-0001")
                .append("roomId", "ROOM-0001")
                .append("isActive", true);

        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_equipment_master")))
                .thenReturn(existingEq);
        when(mongoTemplate.exists(any(Query.class), eq("mdm_blocks"))).thenReturn(true);
        when(mongoTemplate.exists(any(Query.class), eq("mdm_areas"))).thenReturn(true);
        when(mongoTemplate.exists(any(Query.class), eq("mdm_rooms"))).thenReturn(true);
        when(mongoTemplate.save(any(Document.class), eq("iiot_equipment_master")))
                .thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> updateReq = new HashMap<>();
        updateReq.put("equipmentName", "Updated Equipment Name");

        Map<String, Object> result = service.updateEquipmentMaster("EQ-001", updateReq);

        assertNotNull(result);
        assertEquals("Updated Equipment Name", result.get("equipmentName"));
        assertEquals("ROOM-0001", result.get("roomId"));
        assertEquals("AREA-0001", result.get("areaId"));
        assertEquals("BLK-0001", result.get("blockId"));
        assertEquals("PLNT-0001", result.get("plantId"));
        assertEquals("TNT-0001", result.get("tenantId"));
    }

    @Test
    void testEquipmentUpdate_UnchangedRoomWithLegacyMismatchSucceeds() {
        Document existingEq = new Document("equipmentId", "EQ-002")
                .append("equipmentCode", "FBD-01")
                .append("tenantId", "TNT-0001")
                .append("plantId", "PLNT-0001")
                .append("blockId", "LEGACY-BLK")
                .append("areaId", "LEGACY-AREA")
                .append("roomId", "LEGACY-ROOM")
                .append("isActive", true);

        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_equipment_master")))
                .thenReturn(existingEq);
        // Room exists in MDM, but wouldn't match area if checked strictly
        when(mongoTemplate.exists(any(Query.class), eq("mdm_rooms"))).thenReturn(true);
        when(mongoTemplate.save(any(Document.class), eq("iiot_equipment_master")))
                .thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> updateReq = new HashMap<>();
        updateReq.put("equipmentName", "Renamed FBD");
        // Room is identical to existing
        updateReq.put("roomId", "LEGACY-ROOM");

        Map<String, Object> result = service.updateEquipmentMaster("EQ-002", updateReq);
        assertNotNull(result);
        assertEquals("Renamed FBD", result.get("equipmentName"));
    }

    @Test
    void testEquipmentUpdate_RoomChangeDerivesTopology() {
        Document existingEq = new Document("equipmentId", "EQ-003")
                .append("equipmentCode", "COAT-01")
                .append("tenantId", "TNT-0001")
                .append("plantId", "PLNT-0001")
                .append("blockId", "BLK-OLD")
                .append("areaId", "AREA-OLD")
                .append("roomId", "ROOM-OLD")
                .append("isActive", true);

        Document newRoom = new Document("roomId", "ROOM-NEW")
                .append("tenantId", "TNT-0001")
                .append("plantId", "PLNT-0001")
                .append("blockId", "BLK-NEW")
                .append("areaId", "AREA-NEW");

        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_equipment_master")))
                .thenReturn(existingEq);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_rooms")))
                .thenReturn(newRoom);
        when(mongoTemplate.exists(any(Query.class), eq("mdm_plants"))).thenReturn(true);
        when(mongoTemplate.exists(any(Query.class), eq("mdm_blocks"))).thenReturn(true);
        when(mongoTemplate.exists(any(Query.class), eq("mdm_areas"))).thenReturn(true);
        when(mongoTemplate.exists(any(Query.class), eq("mdm_rooms"))).thenReturn(true);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_plants")))
                .thenReturn(new Document("plantId", "PLNT-0001"));
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_blocks")))
                .thenReturn(new Document("blockId", "BLK-NEW"));
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_areas")))
                .thenReturn(new Document("areaId", "AREA-NEW"));
        when(mongoTemplate.save(any(Document.class), eq("iiot_equipment_master")))
                .thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> updateReq = new HashMap<>();
        updateReq.put("roomId", "ROOM-NEW");

        Map<String, Object> result = service.updateEquipmentMaster("EQ-003", updateReq);
        assertNotNull(result);
        assertEquals("ROOM-NEW", result.get("roomId"));
        assertEquals("AREA-NEW", result.get("areaId"));
        assertEquals("BLK-NEW", result.get("blockId"));
    }

    @Test
    void testEquipmentUpdate_InvalidRoomRejects() {
        Document existingEq = new Document("equipmentId", "EQ-004")
                .append("roomId", "ROOM-OLD");

        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_equipment_master")))
                .thenReturn(existingEq);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_rooms")))
                .thenReturn(null);

        Map<String, Object> updateReq = new HashMap<>();
        updateReq.put("roomId", "NON-EXISTENT-ROOM");

        assertThrows(BusinessException.class, () -> service.updateEquipmentMaster("EQ-004", updateReq));
    }

    @Test
    void testRecipeMaster_CreateAndDuplicatePrevention() {
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_product_master")))
                .thenReturn(new Document("productId", "PRD-0001").append("productCode", "STFS7000").append("productName", "Mirtazapine"));
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_recipe_master")))
                .thenReturn(null);
        when(mongoTemplate.insert(any(Document.class), eq("iiot_recipe_master")))
                .thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> req = new HashMap<>();
        req.put("recipeCode", "RCP-MIRT-01");
        req.put("recipeName", "Mirtazapine Granulation");
        req.put("productId", "PRD-0001");
        req.put("associatedBatchSizes", List.of("900.000 Kg", "1000 kg"));

        Map<String, Object> created = service.createRecipeMaster(req);
        assertNotNull(created);
        assertEquals("RCP-MIRT-01", created.get("recipeCode"));
        assertEquals("STFS7000", created.get("productCode"));
        assertTrue((Boolean) created.get("isActive"));

        // Duplicate test
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_recipe_master")))
                .thenReturn(new Document("recipeCode", "RCP-MIRT-01"));
        assertThrows(BusinessException.class, () -> service.createRecipeMaster(req));
    }

    @Test
    void testRecipeManagement_CreateValidationAndLimitsCheck() {
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_product_master")))
                .thenReturn(new Document("productId", "PRD-A").append("productCode", "PRD-A"));
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_recipe_master")))
                .thenReturn(new Document("recipeId", "RCP-A").append("recipeCode", "RCP-A"));
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_equipment_master")))
                .thenReturn(new Document("equipmentId", "RMG-01").append("equipmentCode", "RMG-01"));
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_equipment_critical_parameters")))
                .thenReturn(new Document("parameterCode", "AG_SPEED").append("parameterName", "Impeller Speed").append("unitOfMeasure", "RPM"));
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_recipe_management")))
                .thenReturn(null);
        when(mongoTemplate.insert(any(Document.class), eq("iiot_recipe_management")))
                .thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> validReq = new HashMap<>();
        validReq.put("productId", "PRD-A");
        validReq.put("recipeId", "RCP-A");
        validReq.put("batchSize", "1000 kg");
        validReq.put("equipmentId", "RMG-01");
        validReq.put("parameterCode", "AG_SPEED");
        validReq.put("targetSetpoint", 100.0);
        validReq.put("lowLimit", 80.0);
        validReq.put("highLimit", 120.0);

        Map<String, Object> created = service.createRecipeManagement(validReq);
        assertNotNull(created);
        assertEquals(100.0, created.get("targetSetpoint"));
        assertEquals(80.0, created.get("lowLimit"));
        assertEquals(120.0, created.get("highLimit"));

        // Invalid limits test: Target < Low
        Map<String, Object> invalidReq1 = new HashMap<>(validReq);
        invalidReq1.put("targetSetpoint", 70.0);
        assertThrows(BusinessException.class, () -> service.createRecipeManagement(invalidReq1));

        // Invalid limits test: Target > High
        Map<String, Object> invalidReq2 = new HashMap<>(validReq);
        invalidReq2.put("targetSetpoint", 130.0);
        assertThrows(BusinessException.class, () -> service.createRecipeManagement(invalidReq2));

        // Invalid limits test: Low > High
        Map<String, Object> invalidReq3 = new HashMap<>(validReq);
        invalidReq3.put("lowLimit", 150.0);
        assertThrows(BusinessException.class, () -> service.createRecipeManagement(invalidReq3));
    }

    @Test
    void testRuntimeLimitResolution_DeterministicExample() {
        Document rcmDoc = new Document("productId", "PRD-A")
                .append("recipeId", "RCP-A")
                .append("batchSize", "1000 kg")
                .append("equipmentId", "RMG-01")
                .append("parameterCode", "AG_SPEED")
                .append("parameterName", "Impeller Speed")
                .append("unitOfMeasure", "RPM")
                .append("targetSetpoint", 100.0)
                .append("lowLimit", 80.0)
                .append("highLimit", 120.0)
                .append("isActive", true);

        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("iiot_recipe_management")))
                .thenReturn(List.of(rcmDoc));

        List<Map<String, Object>> limits = service.getEffectiveLimits(
                "TNT-0001", "PLNT-0001", "PRD-A", "RCP-A", "1000 kg", "RMG-01");

        assertFalse(limits.isEmpty());
        Map<String, Object> config = limits.get(0);
        assertEquals(100.0, config.get("targetSetpoint"));
        assertEquals(80.0, config.get("lowLimit"));
        assertEquals(120.0, config.get("highLimit"));

        // Verify Set / Actual test case: Actual 98 vs Target 100
        double actual = 98.0;
        double target = (Double) config.get("targetSetpoint");
        double low = (Double) config.get("lowLimit");
        double high = (Double) config.get("highLimit");

        assertTrue(actual >= low && actual <= high, "Actual 98 should be within limits [80, 120]");
        assertEquals("100 / 98", String.format("%.0f / %.0f", target, actual));

        // Verify Deviation test case: Actual 125 > High (120)
        double devActual = 125.0;
        assertTrue(devActual > high, "Actual 125 should trigger upper limit deviation");
    }

    @Test
    void testUploadRecipeToHmi_SuccessAndAuditPersisted() {
        Document rcpDoc = new Document("recipeId", "RCP-A")
                .append("recipeCode", "RCP-MIRT-01")
                .append("productCode", "STFS7000")
                .append("isActive", true);

        Document rcmDoc = new Document("productId", "PRD-A")
                .append("recipeId", "RCP-A")
                .append("batchSize", "1000 kg")
                .append("equipmentId", "RMG-01")
                .append("parameterCode", "AG_SPEED")
                .append("targetSetpoint", 100.0)
                .append("lowLimit", 80.0)
                .append("highLimit", 120.0)
                .append("isActive", true);

        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_recipe_master")))
                .thenReturn(rcpDoc);
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("iiot_recipe_management")))
                .thenReturn(List.of(rcmDoc));

        Map<String, Object> uploadReq = new HashMap<>();
        uploadReq.put("productId", "PRD-A");
        uploadReq.put("recipeId", "RCP-A");
        uploadReq.put("batchSize", "1000 kg");
        uploadReq.put("equipmentId", "RMG-01");
        uploadReq.put("userId", "OPERATOR_01");

        Map<String, Object> response = service.uploadRecipeToHmi(uploadReq);

        assertNotNull(response);
        assertEquals("SUCCESS", response.get("status"));
        assertEquals(1, response.get("parametersDispatched"));

        // Verify audit event captured
        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(mongoTemplate).insert(captor.capture(), eq("iiot_workflow_audit_trail"));
        Document auditEvent = captor.getValue();
        assertEquals("UPLOAD_TO_HMI", auditEvent.get("action"));
        assertEquals("HMI_DISPATCH", auditEvent.get("actionCode"));
        assertEquals("OPERATOR_01", auditEvent.get("userId"));
        assertEquals("SUCCESS", auditEvent.get("status"));
    }

    @Test
    void testRecipeBatchSizeAssociation_AddRemoveAndDuplicatePrevention() {
        Document existingRecipe = new Document("recipeId", "RCP-0001")
                .append("recipeCode", "RCP-MIRT-01")
                .append("recipeName", "Mirtazapine")
                .append("tenantId", "TNT-0001")
                .append("plantId", "PLNT-0001")
                .append("associatedBatchSizes", new java.util.ArrayList<>(List.of("1000 KG", "2000 KG")))
                .append("isActive", true);

        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_recipe_master")))
                .thenReturn(existingRecipe);
        when(mongoTemplate.save(any(Document.class), eq("iiot_recipe_master")))
                .thenAnswer(inv -> inv.getArgument(0));

        // 1. Get batch sizes
        List<String> currentBatches = service.getRecipeBatchSizes("RCP-0001");
        assertEquals(2, currentBatches.size());
        assertTrue(currentBatches.contains("1000 KG"));
        assertTrue(currentBatches.contains("2000 KG"));

        // 2. Add valid new batch size with normalization ("5000 kg" -> "5000 KG")
        Map<String, Object> addReq = new HashMap<>();
        addReq.put("batchSize", "5000 kg");
        Map<String, Object> updated = service.addRecipeBatchSize("RCP-0001", addReq);
        assertNotNull(updated);
        @SuppressWarnings("unchecked")
        List<String> updatedBatches = (List<String>) updated.get("associatedBatchSizes");
        assertEquals(3, updatedBatches.size());
        assertTrue(updatedBatches.contains("5000 KG"));

        // 3. Duplicate check - "1000.0 KG" or "1000 kg" should be rejected as duplicate
        Map<String, Object> dupReq = new HashMap<>();
        dupReq.put("batchSize", "1000.0 KG");
        assertThrows(BusinessException.class, () -> service.addRecipeBatchSize("RCP-0001", dupReq));

        // 4. Remove batch size
        Map<String, Object> afterRemove = service.removeRecipeBatchSize("RCP-0001", "2000 kg");
        @SuppressWarnings("unchecked")
        List<String> afterRemoveBatches = (List<String>) afterRemove.get("associatedBatchSizes");
        assertFalse(afterRemoveBatches.contains("2000 KG"));
    }

    @Test
    void testRecipeManagementBatchSave_Success() {
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_product_master")))
                .thenReturn(new Document("productId", "PRD-0001").append("productCode", "STFS7000"));
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_recipe_master")))
                .thenReturn(new Document("recipeId", "RCP-0001").append("recipeCode", "RCP-MIRT-01"));
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_equipment_master")))
                .thenReturn(new Document("equipmentId", "RMG-01").append("equipmentCode", "RMG-01"));
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_equipment_critical_parameters")))
                .thenReturn(new Document("parameterCode", "agSpeed").append("parameterName", "Agitator Speed").append("unitOfMeasure", "RPM"));
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_recipe_management")))
                .thenReturn(null);
        when(mongoTemplate.insert(any(Document.class), eq("iiot_recipe_management")))
                .thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> batchReq = new HashMap<>();
        batchReq.put("productId", "PRD-0001");
        batchReq.put("recipeId", "RCP-0001");
        batchReq.put("batchSize", "1000 kg");
        batchReq.put("equipmentId", "RMG-01");
        batchReq.put("parameters", List.of(
                Map.of("parameterCode", "agSpeed", "parameterName", "Agitator Speed", "targetSetpoint", 140.0, "lowLimit", 100.0, "highLimit", 160.0)
        ));

        List<Map<String, Object>> saved = service.saveRecipeManagementBatch(batchReq);
        assertNotNull(saved);
        assertEquals(1, saved.size());
        assertEquals("1000 KG", saved.get(0).get("batchSize"));
        assertEquals(140.0, saved.get(0).get("targetSetpoint"));
    }
}
