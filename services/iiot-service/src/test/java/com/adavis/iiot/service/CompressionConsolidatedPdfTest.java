package com.adavis.iiot.service;

import com.adavis.common.exception.BusinessException;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CompressionConsolidatedPdfTest {
    private MongoTemplate mongo;
    private BatchPdfGeneratorService service;
    private List<Document> summaries;
    private List<Document> samples;

    @BeforeEach
    void setup() {
        mongo = mock(MongoTemplate.class);
        service = new BatchPdfGeneratorService(mongo);
        ReflectionTestUtils.setField(service, "storageRootPath", "");
        summaries = new ArrayList<>();
        samples = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            String lot = "Lot-0" + i;
            summaries.add(summary(lot, "APPROVED"));
            samples.add(sample(lot, i));
        }
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("iiot_batch_summary"))).thenAnswer(inv -> summaries.get(0));
        when(mongo.find(any(Query.class), eq(Document.class), eq("iiot_batch_summary"))).thenAnswer(inv -> summaries);
        when(mongo.collectionExists("iiot_ts_batch_MC081")).thenReturn(true);
        when(mongo.find(any(Query.class), eq(Document.class), eq("iiot_ts_batch_MC081"))).thenAnswer(inv -> samples);
    }

    @Test
    void finalDossierContainsThreeFullReportsAndConsolidatedHistories() throws Exception {
        Document shared = new Document("batchNo", "BATCH-COMP").append("timestamp", "2026-09-30T12:00:00")
                .append("alarmName", "Shared batch alarm");
        for (Document sample : samples) {
            Document details = sample.get("compression_details", Document.class);
            details.put("alarm_history", List.of(shared, new Document("batchNo", "OTHER").append("alarmName", "Exclude this alarm")));
        }
        when(mongo.find(any(Query.class), eq(Document.class), eq("iiot_workflow_action_history"))).thenReturn(List.of(
                new Document("lotNo", "Lot-01").append("newStatus", "APPROVED").append("actionName", "QA approval")
                        .append("performedBy", "QA-01").append("timestamp", new Date())));
        when(mongo.find(any(Query.class), eq(Document.class), eq("iiot_workflow_audit_trail"))).thenReturn(List.of(
                new Document("action", "PRINT").append("userId", "actual-printer").append("printCount", 2)
                        .append("timestamp", new Date()).append("lotNo", "Lot-03")));

        var result = service.generateAndStoreBatchPdf("BATCH-COMP", "Lot-03", "MC081", "TNT-0001", "PLNT-0001", "download-user", "QA");
        PdfReader reader = new PdfReader(result.getPdfBytes());
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder text = new StringBuilder();
        for (int page = 1; page <= reader.getNumberOfPages(); page++) text.append(extractor.getTextFromPage(page)).append("\n");
        assertTrue(reader.getNumberOfPages() >= 10);
        reader.close();
        String output = text.toString();
        for (int i = 1; i <= 3; i++) {
            assertTrue(output.contains("Lot-0" + i));
            assertTrue(output.contains("Product-" + i));
            assertTrue(output.contains("Source-" + i + ".xls"));
            assertTrue(output.contains("Action-" + i));
            assertTrue(output.contains("login-user-" + i));
        }
        assertEquals(3, occurrences(output, "PRODUCTION REPORT (1/2)"));
        assertEquals(3, occurrences(output, "OPERATIONS & TABLET DATA (2/2)"));
        assertEquals(1, occurrences(output, "Shared batch alarm"));
        assertFalse(output.contains("Exclude this alarm"));
        for (String section : List.of("Tightness", "Tablet Checker", "WORKFLOW CHANGES SUMMARY", "Controlled Print Summary",
                "actual-printer", "download-user", "QA APPROVED", "QA Approved At")) assertTrue(output.contains(section), section);
        ArgumentCaptor<Document> saved = ArgumentCaptor.forClass(Document.class);
        verify(mongo).save(saved.capture(), eq("dms_documents"));
        assertEquals("CONSOLIDATED", saved.getValue().get("lotNo"));
        assertEquals("COMPRESSION_BATCH", saved.getValue().get("reportScope"));
        assertEquals(List.of("Lot-01", "Lot-02", "Lot-03"), saved.getValue().get("includedLots"));
        assertEquals("Compression_Consolidated_Production_Report_BATCH-COMP_MC081_QA_Approved.pdf", result.getFileName());
    }

    @Test
    void consolidationIsExclusiveToMc081() {
        assertTrue(service.isCompressionEquipment("MC081"));
        for (String equipment : List.of("MB003", "MB004", "MB005", "MB041", "MB040", "MC082", "COMP", "SEJONG")) {
            assertFalse(service.isCompressionEquipment(equipment), equipment);
        }
    }

    @Test
    void pendingLotBlocksGenerationAndRetrievalWithoutPersistence() {
        summaries.set(1, summary("Lot-02", "UNDER_REVIEW"));
        Document readiness = service.compressionPdfReadiness("BATCH-COMP", "MC081", "TNT-0001", "PLNT-0001");
        assertEquals(false, readiness.get("ready"));
        assertEquals(List.of("Lot-02"), readiness.get("pendingLots"));
        assertThrows(BusinessException.class, () -> service.generateAndStoreBatchPdf("BATCH-COMP", "Lot-03", "MC081", "TNT-0001", "PLNT-0001"));
        assertThrows(BusinessException.class, () -> service.findStoredBatchPdf("BATCH-COMP", "Lot-03", "MC081", "TNT-0001", "PLNT-0001"));
        verify(mongo, never()).save(any(Document.class), eq("dms_documents"));
    }

    @Test
    void ingestedCompletedStatusIsNotQaApproval() {
        for (Document summary : summaries) {
            summary.remove("stages");
            summary.put("overallStatus", "COMPLETED");
        }
        assertEquals(false, service.compressionPdfReadiness("BATCH-COMP", "MC081", "TNT-0001", "PLNT-0001").get("ready"));
    }

    @Test
    void approvedSummaryWithoutProductionReportIsNotReady() {
        samples.remove(1);
        assertEquals(List.of("Lot-02"), service.compressionPdfReadiness("BATCH-COMP", "MC081", "TNT-0001", "PLNT-0001").get("pendingLots"));
    }

    @Test
    void separateExecutionsCannotBeSilentlyMergedUnderOneLot() {
        samples.add(sample("Lot-01", 4));
        assertThrows(BusinessException.class, () -> service.compressionPdfReadiness("BATCH-COMP", "MC081", "TNT-0001", "PLNT-0001"));
    }

    @Test
    void storedLookupRequiresConsolidatedScopeAndLayoutVersion() {
        assertNull(service.findStoredBatchPdf("BATCH-COMP", "Lot-03", "MC081", "TNT-0001", "PLNT-0001"));
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).findOne(query.capture(), eq(Document.class), eq("dms_documents"));
        assertEquals("CONSOLIDATED", query.getValue().getQueryObject().get("lotNo"));
        assertEquals("COMPRESSION_BATCH", query.getValue().getQueryObject().get("reportScope"));
        assertEquals(2, query.getValue().getQueryObject().get("reportLayoutVersion"));
    }

    private static Document summary(String lot, String status) {
        return new Document("batchNo", "BATCH-COMP").append("lotNo", lot).append("equipmentId", "MC081")
                .append("tenantId", "TNT-0001").append("plantId", "PLNT-0001")
                .append("stages", List.of(new Document("equipmentCode", "MC081")
                        .append("approval", new Document("status", status).append("approvedBy", "qa-" + lot).append("approvedAt", new Date()))));
    }

    private static Document sample(String lot, int n) {
        return new Document("meta", new Document("batchNo", "BATCH-COMP").append("derivedLotNo", lot).append("lotNo", lot))
                .append("compression_details", new Document("metadata", new Document("sourceFile", "Source-" + n + ".xls"))
                        .append("batchInfo", new Document("productName", "Product-" + n).append("stationNo", "Station " + n))
                        .append("operation_history", List.of(new Document("batchNo", "BATCH-COMP").append("action", "Action-" + n).append("timestamp", "2026-09-30T13:00:00")))
                        .append("login_history", List.of(new Document("userId", "login-user-" + n).append("action", "LOGIN").append("timestamp", "2026-09-30T12:00:00"))));
    }

    private static int occurrences(String text, String value) {
        return (text.length() - text.replace(value, "").length()) / value.length();
    }
}
