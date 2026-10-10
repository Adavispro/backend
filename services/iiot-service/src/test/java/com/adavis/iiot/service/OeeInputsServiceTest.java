package com.adavis.iiot.service;

import com.adavis.common.exception.BusinessException;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OeeInputsServiceTest {
    @Mock MongoTemplate mongo;
    OeeInputsService service;

    @BeforeEach
    void setup() {
        service = new OeeInputsService(mongo);
    }

    private OeeInputsService.SettingsRequest settings(Map<String, Double> ideals, boolean complete) {
        return new OeeInputsService.SettingsRequest("T1", "P1", "MC081", "2026-10-01", "2026-10-07",
                List.of("Shift 1", "Shift 3"), List.of(1, 2, 3, 4, 5), ideals, complete, "Asia/Kolkata");
    }

    private OeeInputsService.DowntimeRequest downtime(String start, String end, String classification, String category) {
        return new OeeInputsService.DowntimeRequest("T1", "P1", "MC081", "2026-10-01",
                start, end, classification, category, "Validated maintenance", "", "Asia/Kolkata");
    }

    @Test
    void savesScopedInputsAndAuditsTheAuthenticatedActor() {
        when(mongo.exists(any(Query.class), eq("iiot_equipment_master"))).thenReturn(true);
        when(mongo.save(any(Document.class), eq("iiot_oee_batch_settings"))).thenAnswer(i -> i.getArgument(0));
        Map<String, Object> saved = service.saveSettings(settings(Map.of("prod", 1.25), true), "USER1");
        assertEquals("T1|P1|MC081", saved.get("id"));
        assertEquals("USER1", saved.get("updatedBy"));
        assertEquals(Map.of("PROD", 1.25), saved.get("idealBatchHours"));
        assertEquals(true, saved.get("downtimeComplete"));
        assertEquals("Asia/Kolkata", saved.get("timeZone"));
        ArgumentCaptor<Document> audit = ArgumentCaptor.forClass(Document.class);
        verify(mongo).insert(audit.capture(), eq("iiot_oee_input_audit"));
        assertEquals("USER1", audit.getValue().get("actor"));
        assertNotNull(audit.getValue().get("current"));
    }

    @Test
    void storesDottedProductCodesAsListEntriesAndReturnsThemAsAMap() {
        when(mongo.exists(any(Query.class), eq("iiot_equipment_master"))).thenReturn(true);
        ArgumentCaptor<Document> stored = ArgumentCaptor.forClass(Document.class);
        when(mongo.save(stored.capture(), eq("iiot_oee_batch_settings"))).thenAnswer(i -> i.getArgument(0));
        Map<String, Object> saved = service.saveSettings(settings(Map.of("Carvedilol 12.5mg", 7.58), true), "USER1");
        assertEquals(List.of(new Document("productCode", "CARVEDILOL 12.5MG").append("hours", 7.58)),
                stored.getValue().get("idealBatchHours"));
        assertEquals(Map.of("CARVEDILOL 12.5MG", 7.58), saved.get("idealBatchHours"));
    }

    @Test
    void rejectsCrossPlantEquipmentAndMissingScope() {
        assertThrows(BusinessException.class, () -> service.saveSettings(settings(Map.of(), false), "USER1"));
        verify(mongo, never()).save(any(Document.class), anyString());
        var missing = new OeeInputsService.SettingsRequest("", "P1", "MC081", "2026-10-01", "2026-10-07",
                List.of("Shift 1"), List.of(1), Map.of(), false, "UTC");
        assertThrows(BusinessException.class, () -> service.saveSettings(missing, "USER1"));
    }

    @Test
    void rejectsInvalidIdealDurationsAndUnknownShifts() {
        when(mongo.exists(any(Query.class), eq("iiot_equipment_master"))).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.saveSettings(settings(Map.of("PROD", 0.0), true), "USER1"));
        assertThrows(BusinessException.class, () -> service.saveSettings(settings(Map.of("PROD", Double.NaN), true), "USER1"));
        var badShift = new OeeInputsService.SettingsRequest("T1", "P1", "MC081", "2026-10-01", "2026-10-07",
                List.of("Shift 4"), List.of(1), Map.of(), false, "UTC");
        assertThrows(BusinessException.class, () -> service.saveSettings(badShift, "USER1"));
    }

    @Test
    void validatesDateRangeAndTimezone() {
        when(mongo.exists(any(Query.class), eq("iiot_equipment_master"))).thenReturn(true);
        var reversed = new OeeInputsService.SettingsRequest("T1", "P1", "MC081", "2026-10-07", "2026-10-01",
                List.of("Shift 1"), List.of(1), Map.of(), false, "UTC");
        assertThrows(BusinessException.class, () -> service.saveSettings(reversed, "USER1"));
        var badZone = new OeeInputsService.SettingsRequest("T1", "P1", "MC081", "2026-10-01", "2026-10-07",
                List.of("Shift 1"), List.of(1), Map.of(), false, "INVALID");
        assertThrows(BusinessException.class, () -> service.saveSettings(badZone, "USER1"));
    }

    @Test
    void savesOvernightDowntimeWithServerCalculatedDuration() {
        when(mongo.exists(any(Query.class), eq("iiot_equipment_master"))).thenReturn(true);
        when(mongo.insert(any(Document.class), eq("iiot_oee_downtime"))).thenAnswer(i -> i.getArgument(0));
        Map<String, Object> saved = service.addDowntime(downtime("22:00", "02:00", "UNPLANNED", "Equipment Failure"), "USER1");
        assertEquals(4.0, saved.get("durationHours"));
        assertEquals("USER1", saved.get("createdBy"));
        assertNotNull(saved.get("id"));
    }

    @Test
    void rejectsEqualTimesAndMismatchedClassification() {
        when(mongo.exists(any(Query.class), eq("iiot_equipment_master"))).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.addDowntime(downtime("08:00", "08:00", "PLANNED", "Cleaning"), "USER1"));
        assertThrows(BusinessException.class, () -> service.addDowntime(downtime("08:00", "09:00", "UNPLANNED", "Cleaning"), "USER1"));
        verify(mongo, never()).insert(any(Document.class), eq("iiot_oee_downtime"));
    }

    @Test
    void updatesDowntimePreservingCreatorAndAudits() {
        when(mongo.exists(any(Query.class), eq("iiot_equipment_master"))).thenReturn(true);
        when(mongo.findById("D1", Document.class, "iiot_oee_downtime"))
                .thenReturn(new Document("_id", "D1").append("createdBy", "USER0").append("createdAt", "2026-10-01T00:00:00Z"));
        when(mongo.save(any(Document.class), eq("iiot_oee_downtime"))).thenAnswer(i -> i.getArgument(0));
        Map<String, Object> saved = service.updateDowntime("D1", downtime("08:00", "09:30", "PLANNED", "Cleaning"), "USER1");
        assertEquals("D1", saved.get("id"));
        assertEquals(1.5, saved.get("durationHours"));
        assertEquals("USER0", saved.get("createdBy"));
        assertEquals("USER1", saved.get("updatedBy"));
        verify(mongo).insert(argThat((Document d) -> "UPDATE_DOWNTIME".equals(d.get("action"))), eq("iiot_oee_input_audit"));
    }

    @Test
    void deletesExistingDowntimeAndRejectsUnknownIds() {
        when(mongo.findById("D1", Document.class, "iiot_oee_downtime")).thenReturn(new Document("_id", "D1"));
        assertEquals(true, service.deleteDowntime("D1", "USER1").get("deleted"));
        verify(mongo).remove(any(Query.class), eq("iiot_oee_downtime"));
        verify(mongo).insert(argThat((Document d) -> "DELETE_DOWNTIME".equals(d.get("action"))), eq("iiot_oee_input_audit"));
        assertThrows(BusinessException.class, () -> service.deleteDowntime("MISSING", "USER1"));
    }

    @Test
    void deletesExistingSettingsAndRejectsUnknownScope() {
        when(mongo.findById("T1|P1|MC081", Document.class, "iiot_oee_batch_settings")).thenReturn(new Document("_id", "T1|P1|MC081"));
        assertEquals(true, service.deleteSettings("T1", "P1", "MC081", "USER1").get("deleted"));
        verify(mongo).remove(any(Query.class), eq("iiot_oee_batch_settings"));
        verify(mongo).insert(argThat((Document d) -> "DELETE_SETTINGS".equals(d.get("action"))), eq("iiot_oee_input_audit"));
        assertThrows(BusinessException.class, () -> service.deleteSettings("T1", "P1", "MISSING", "USER1"));
    }

    @Test
    void importsAllRowsOnlyWhenEveryRowIsValid() {
        when(mongo.exists(any(Query.class), eq("iiot_equipment_master"))).thenReturn(true);
        var good = downtime("08:00", "09:00", "PLANNED", "Cleaning");
        var bad = downtime("08:00", "09:00", "UNPLANNED", "Cleaning");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.importDowntime(List.of(good, bad), "USER1"));
        assertTrue(ex.getMessage().contains("Row 3"));
        verify(mongo, never()).insert(anyCollection(), eq("iiot_oee_downtime"));
        Map<String, Object> result = service.importDowntime(List.of(good, good), "USER1");
        assertEquals(2, result.get("imported"));
        verify(mongo).insert(anyCollection(), eq("iiot_oee_downtime"));
    }

    @Test
    void readsOnlyTheRequestedTenantAndPlant() {
        when(mongo.find(any(Query.class), eq(Document.class), anyString())).thenReturn(List.of());
        service.getInputs("T1", "P1");
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(), eq(Document.class), eq("iiot_oee_batch_settings"));
        assertEquals("T1", query.getValue().getQueryObject().get("tenantId"));
        assertEquals("P1", query.getValue().getQueryObject().get("plantId"));
    }
}
