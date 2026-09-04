package com.adavis.mdm.service;

import com.adavis.common.exception.BusinessException;
import com.adavis.common.exception.UnauthorizedException;
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

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PlantTopologyServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private BusinessIdGeneratorService businessIdGeneratorService;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private TopologyEsignatureService topologyEsignatureService;

    @InjectMocks
    private PlantTopologyService plantTopologyService;

    private Map<String, Object> samplePlantRequest;

    @BeforeEach
    void setUp() {
        samplePlantRequest = new HashMap<>();
        samplePlantRequest.put("plantCode", "PLNT-HYD-01");
        samplePlantRequest.put("plantName", "Hyderabad Facility 1");
        samplePlantRequest.put("tenantId", "TNT-0001");
        samplePlantRequest.put("remarks", "Creating primary manufacturing facility");
        samplePlantRequest.put("password", "Secret123!");
    }

    @Test
    @DisplayName("createPlant enforces e-signature and emits controlled audit")
    void createPlant_valid_succeeds() {
        when(businessIdGeneratorService.nextId(eq("mdm_plants"), eq("plantId"), eq("PLNT-"), eq(4)))
                .thenReturn("PLNT-0001");
        when(mongoTemplate.exists(any(Query.class), eq("mdm_plants"))).thenReturn(false);
        when(mongoTemplate.insert(any(Document.class), eq("mdm_plants"))).thenAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            return doc;
        });

        Map<String, Object> result = plantTopologyService.createPlant(samplePlantRequest, "USER-0001");

        assertNotNull(result);
        assertEquals("PLNT-0001", result.get("plantId"));
        assertEquals("PLNT-HYD-01", result.get("plantCode"));
        // Ensure sensitive password was stripped from saved document
        assertNull(result.get("password"));

        // Verify e-sign enforcement was called
        verify(topologyEsignatureService).validateRemarks("Creating primary manufacturing facility");
        verify(topologyEsignatureService).verifyEsignature(eq("USER-0001"), eq("Secret123!"), contains("PLANT_CREATED"), eq("TNT-0001"));

        // Verify audit publishing with compliance metadata
        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditEventPublisher).publish(
                eq("USER-0001"),
                eq("PLANT_CREATED"),
                eq("MDM_PLANT"),
                eq("PLNT-0001"),
                eq("SUCCESS"),
                anyMap(),
                anyMap(),
                metadataCaptor.capture()
        );

        Map<String, Object> capturedMeta = metadataCaptor.getValue();
        assertEquals(Boolean.TRUE, capturedMeta.get("esignatureVerified"));
        assertEquals("21_CFR_PART_11", capturedMeta.get("complianceStandard"));
        assertEquals("Creating primary manufacturing facility", capturedMeta.get("remarks"));
    }

    @Test
    @DisplayName("createPlant fails when e-signature verification fails")
    void createPlant_invalidEsign_fails() {
        doThrow(new UnauthorizedException("Invalid password", "INVALID_ESIGN_CREDENTIALS"))
                .when(topologyEsignatureService).verifyEsignature(anyString(), anyString(), anyString(), anyString());

        assertThrows(UnauthorizedException.class, () ->
                plantTopologyService.createPlant(samplePlantRequest, "USER-0001"));

        verify(mongoTemplate, never()).insert(any(Document.class), anyString());
    }

    @Test
    @DisplayName("deletePlant (deactivate) enforces e-signature")
    void deletePlant_valid_succeeds() {
        Document existing = new Document("plantId", "PLNT-0001")
                .append("plantCode", "PLNT-HYD-01")
                .append("tenantId", "TNT-0001")
                .append("isActive", true);

        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_plants")))
                .thenReturn(existing);

        Map<String, Object> deactivateReq = Map.of(
                "remarks", "Decommissioning unit",
                "password", "Secret123!"
        );

        plantTopologyService.deletePlant("PLNT-0001", deactivateReq, "USER-0001");

        verify(topologyEsignatureService).validateRemarks("Decommissioning unit");
        verify(topologyEsignatureService).verifyEsignature(eq("USER-0001"), eq("Secret123!"), contains("PLANT_DEACTIVATED"), eq("TNT-0001"));
        verify(mongoTemplate).save(any(Document.class), eq("mdm_plants"));
    }

    @Test
    @DisplayName("reactivatePlant enforces e-signature")
    void reactivatePlant_valid_succeeds() {
        Document existing = new Document("plantId", "PLNT-0001")
                .append("plantCode", "PLNT-HYD-01")
                .append("tenantId", "TNT-0001")
                .append("isActive", false);

        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("mdm_plants")))
                .thenReturn(existing);

        Map<String, Object> reactivateReq = Map.of(
                "remarks", "Re-activating facility for new line",
                "password", "Secret123!"
        );

        plantTopologyService.reactivatePlant("PLNT-0001", reactivateReq, "USER-0001");

        verify(topologyEsignatureService).validateRemarks("Re-activating facility for new line");
        verify(topologyEsignatureService).verifyEsignature(eq("USER-0001"), eq("Secret123!"), contains("PLANT_REACTIVATED"), eq("TNT-0001"));
        verify(mongoTemplate).save(any(Document.class), eq("mdm_plants"));
    }
}
