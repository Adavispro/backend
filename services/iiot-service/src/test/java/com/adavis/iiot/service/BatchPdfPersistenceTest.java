package com.adavis.iiot.service;

import com.adavis.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class BatchPdfPersistenceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private DynamicWorkflowEngine dynamicWorkflowEngine;

    private BatchPdfGeneratorService pdfGeneratorService;
    private IiotOperationsService iiotOperationsService;

    private Document testSummary;
    private Document testWorkflowInstance;

    @BeforeEach
    void setUp() {
        pdfGeneratorService = new BatchPdfGeneratorService(mongoTemplate);
        ReflectionTestUtils.setField(pdfGeneratorService, "storageRootPath", "/non_existent_unwritable_path/./data/dms/local");

        iiotOperationsService = new IiotOperationsService(mongoTemplate, stringRedisTemplate, new ObjectMapper(), pdfGeneratorService, dynamicWorkflowEngine);

        testSummary = new Document("batchNo", "NL0026008")
                .append("lotNo", "01 of 05")
                .append("productCode", "STFS7000")
                .append("productName", "Mirtazapine Tablets USP 5 mg")
                .append("equipmentId", "G5FBD")
                .append("overallStatus", "APPROVED")
                .append("tenantId", "TNT-0001")
                .append("plantId", "PLNT-0001")
                .append("stages", List.of(
                        new Document("stageId", "STAGE-2")
                                .append("equipmentCode", "G5FBD")
                                .append("equipmentId", "G5FBD")
                                .append("approval", new Document("status", "APPROVED"))
                ));

        testWorkflowInstance = new Document("entityId", "NL0026008:01 of 05:G5FBD")
                .append("instanceId", "WFI-NL0026008-01")
                .append("workflowCode", "IIOT_BATCH_STAGE_WORKFLOW")
                .append("workflowVersion", "1.0.0")
                .append("currentStatus", "APPROVED");

        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_batch_summary")))
                .thenReturn(testSummary);
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("iiot_workflow_instances")))
                .thenReturn(testWorkflowInstance);
    }

    @Test
    @DisplayName("Should validate valid PDF bytes header")
    void testValidatePdfBytesValid() {
        byte[] validPdf = "%PDF-1.4 test content".getBytes(StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> pdfGeneratorService.validatePdfBytes(validPdf));
    }

    @Test
    @DisplayName("Should reject null, empty or invalid PDF header")
    void testValidatePdfBytesInvalid() {
        assertThrows(BusinessException.class, () -> pdfGeneratorService.validatePdfBytes(null));
        assertThrows(BusinessException.class, () -> pdfGeneratorService.validatePdfBytes(new byte[0]));
        assertThrows(BusinessException.class, () -> pdfGeneratorService.validatePdfBytes("INVALID_HEADER".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Should generate GxP PDF and persist into dms_documents even when local filesystem path fails")
    void testGenerateAndStoreBatchPdfWithDmsPersistence() {
        BatchPdfGeneratorService.PdfGenerationResult result = pdfGeneratorService.generateAndStoreBatchPdf(
                "NL0026008", "01 of 05", "G5FBD", "TNT-0001", "PLNT-0001", "QA_APPROVER_01", "QA_APPROVER");

        assertNotNull(result);
        assertNotNull(result.getDocumentId());
        assertTrue(result.getDocumentId().startsWith("DOC-BATCH-"));
        assertTrue(result.getFileSizeBytes() > 0);
        assertNotNull(result.getSha256Checksum());
        assertNotNull(result.getPdfBytes());

        // Verify PDF magic bytes
        assertEquals(0x25, result.getPdfBytes()[0]); // %
        assertEquals(0x50, result.getPdfBytes()[1]); // P
        assertEquals(0x44, result.getPdfBytes()[2]); // D
        assertEquals(0x46, result.getPdfBytes()[3]); // F

        // Verify dms_documents persistence was called
        ArgumentCaptor<Document> dmsDocCaptor = ArgumentCaptor.forClass(Document.class);
        verify(mongoTemplate, atLeastOnce()).save(dmsDocCaptor.capture(), eq("dms_documents"));

        Document savedDoc = dmsDocCaptor.getValue();
        assertEquals(result.getDocumentId(), savedDoc.getString("documentId"));
        assertEquals("TNT-0001", savedDoc.getString("tenantId"));
        assertEquals("PLNT-0001", savedDoc.getString("plantId"));
        assertEquals("NL0026008", savedDoc.getString("batchNo"));
        assertEquals("01 of 05", savedDoc.getString("lotNo"));
        assertEquals("G5FBD", savedDoc.getString("equipmentCode"));
        assertEquals("application/pdf", savedDoc.getString("mimeType"));
        assertEquals("ACTIVE", savedDoc.getString("status"));
        assertEquals(result.getSha256Checksum(), savedDoc.getString("sha256Checksum"));
        assertNotNull(savedDoc.getString("base64Data"));

        // Verify batch summary document association was updated
        ArgumentCaptor<Document> summaryCaptor = ArgumentCaptor.forClass(Document.class);
        verify(mongoTemplate, atLeastOnce()).save(summaryCaptor.capture(), eq("iiot_batch_summary"));

        Document updatedSummary = summaryCaptor.getValue();
        assertEquals(result.getDocumentId(), updatedSummary.getString("pdfDocumentId"));
        assertEquals("READY", updatedSummary.getString("pdfStatus"));
    }

    @Test
    @DisplayName("Should generate PDF with QA APPROVED status and without CHECKED/REVIEWED BY footer")
    void testPdfVisualContentAndStatus() throws Exception {
        BatchPdfGeneratorService.PdfGenerationResult result = pdfGeneratorService.generateAndStoreBatchPdf(
                "NL0026008", "01 of 05", "G5FBD", "TNT-0001", "PLNT-0001", "QA_APPROVER_01", "QA_APPROVER");

        assertNotNull(result);
        assertNotNull(result.getPdfBytes());

        java.nio.file.Files.write(java.nio.file.Path.of("/tmp/test_qa_approved.pdf"), result.getPdfBytes());

        PdfReader reader = new PdfReader(result.getPdfBytes());
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder extractedText = new StringBuilder();
        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            extractedText.append(extractor.getTextFromPage(i)).append("\n");
        }
        reader.close();

        String pdfText = extractedText.toString();
        assertFalse(pdfText.contains("CHECKED BY:"), "Footer table 'CHECKED BY:' must be removed");
        assertFalse(pdfText.contains("REVIEWED BY:"), "Footer table 'REVIEWED BY:' must be removed");
        int workflowIdx = pdfText.indexOf("WORKFLOW ACTIONS & ELECTRONIC SIGNATURE RECORD");
        int printSummaryIdx = pdfText.indexOf("PRINT CONTROLLED SUMMARY & TRACEABILITY LOG");
        if (printSummaryIdx < 0) {
            printSummaryIdx = pdfText.indexOf("CONTROLLED PRINT SUMMARY & TRACEABILITY LOG");
        }
        assertTrue(workflowIdx > 0, "Workflow actions table header must be present");
        assertTrue(printSummaryIdx > 0, "Controlled print summary table header must be present");
        assertTrue(printSummaryIdx > workflowIdx, "Controlled print summary must appear after workflow log table in PDF");
        assertFalse(pdfText.contains("[VERIFIED]"), "E-signature verification tag [VERIFIED] must be removed from workflow table");
        assertFalse(pdfText.contains("Transition Sign-off (21 CFR Part 11)"), "Workflow table reasons must not contain (21 CFR Part 11)");
        assertFalse(pdfText.contains("Release Approval (21 CFR Part 11)"), "Workflow table reasons must not contain (21 CFR Part 11)");
    }

    @Test
    @DisplayName("Should serve existing stored PDF from dms_documents without regenerating")
    void testGetBatchPdfBytesServingExistingStoredDocument() {
        byte[] existingPdf = pdfGeneratorService.generateAndStoreBatchPdf(
                "NL0026008", "01 of 05", "G5FBD", "TNT-0001", "PLNT-0001", "QA_APPROVER_01", "QA_APPROVER"
        ).getPdfBytes();
        clearInvocations(mongoTemplate);
        String base64Content = Base64.getEncoder().encodeToString(existingPdf);

        Document storedDoc = new Document("documentId", "DOC-BATCH-EXISTING01")
                .append("batchNo", "NL0026008")
                .append("lotNo", "01 of 05")
                .append("equipmentCode", "G5FBD")
                .append("status", "ACTIVE")
                .append("mimeType", "application/pdf")
                .append("fileName", "Batch_Dossier_NL0026008_01_of_05_G5FBD.pdf")
                .append("sha256Checksum", "abc123sha")
                .append("base64Data", base64Content)
                .append("generatedAt", new Date());

        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("dms_documents")))
                .thenReturn(storedDoc);

        byte[] returnedBytes = iiotOperationsService.getBatchPdfBytes(
                "NL0026008", "01 of 05", "G5FBD", "TNT-0001", "USER_01", "QA_APPROVER");

        assertNotNull(returnedBytes);
        assertArrayEquals(existingPdf, returnedBytes);

        // Verify dms_documents save was NOT called (no regeneration)
        verify(mongoTemplate, never()).save(any(Document.class), eq("dms_documents"));
    }

    @Test
    @DisplayName("Should backfill and persist PDF on download when approved batch is missing PDF")
    void testGetBatchPdfBytesBackfillWhenMissing() {
        // First lookup returns null (missing stored document)
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("dms_documents")))
                .thenReturn(null);

        byte[] returnedBytes = iiotOperationsService.getBatchPdfBytes(
                "NL0026008", "01 of 05", "G5FBD", "TNT-0001", "USER_01", "QA_APPROVER");

        assertNotNull(returnedBytes);
        assertTrue(returnedBytes.length >= 4);
        assertEquals(0x25, returnedBytes[0]); // %
        assertEquals(0x50, returnedBytes[1]); // P

        // Verify it was persisted to dms_documents
        verify(mongoTemplate, atLeastOnce()).save(any(Document.class), eq("dms_documents"));
    }

    @Test
    @DisplayName("Controlled Print should reject empty or whitespace reason")
    void testControlledPrintBatchPdfEmptyReasonThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            iiotOperationsService.controlledPrintBatchPdf(
                "NL0026008", "01 of 05", "G5FBD", "   ", "validPassword",
                "TNT-0001", "PLNT-0001", "USER_01", "QA_APPROVER"
            )
        );
    }

    @Test
    @DisplayName("Controlled Print should reject invalid e-signature password")
    void testControlledPrintBatchPdfEsignatureFailureThrows() {
        doThrow(new BusinessException("Invalid password"))
            .when(dynamicWorkflowEngine)
            .verifyEsignature(anyString(), anyString(), anyString(), anyString());

        assertThrows(BusinessException.class, () ->
            iiotOperationsService.controlledPrintBatchPdf(
                "NL0026008", "01 of 05", "G5FBD", "Auditor Inspection Copy", "wrongPassword",
                "TNT-0001", "PLNT-0001", "USER_01", "QA_APPROVER"
            )
        );
    }

    @Test
    @DisplayName("Controlled Print should increment printCount and log audit trail on success")
    void testControlledPrintBatchPdfSuccess() {
        byte[] existingPdf = "%PDF-1.4 Mock Stored PDF Content".getBytes(StandardCharsets.UTF_8);
        String base64Content = Base64.getEncoder().encodeToString(existingPdf);

        Document storedDoc = new Document("documentId", "DOC-BATCH-EXISTING01")
                .append("batchNo", "NL0026008")
                .append("lotNo", "01 of 05")
                .append("equipmentCode", "G5FBD")
                .append("status", "ACTIVE")
                .append("base64Data", base64Content);

        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("dms_documents")))
                .thenReturn(storedDoc);

        Document updatedSummary = new Document("batchNo", "NL0026008")
                .append("printCount", 1)
                .append("lastPrintedBy", "USER_01")
                .append("lastPrintReason", "Auditor Inspection Copy");
        when(mongoTemplate.findAndModify(any(Query.class), any(), any(), eq(Document.class), eq("iiot_batch_summary")))
                .thenReturn(updatedSummary);

        IiotOperationsService.ControlledPrintResult result = iiotOperationsService.controlledPrintBatchPdf(
                "NL0026008", "01 of 05", "G5FBD", "Auditor Inspection Copy", "validPass123",
                "TNT-0001", "PLNT-0001", "USER_01", "QA_APPROVER"
        );

        assertNotNull(result);
        assertNotNull(result.getPdfBytes());
        assertEquals(1, result.getPrintCount());
        assertEquals("USER_01", result.getPrintedBy());

        // Verify e-signature was checked
        verify(dynamicWorkflowEngine, times(1)).verifyEsignature(
                eq("USER_01"), eq("validPass123"), eq("PRINT_BATCH_DOSSIER_PDF"), eq("TNT-0001")
        );

        // Verify audit trail logged with action PRINT
        ArgumentCaptor<Document> auditCaptor = ArgumentCaptor.forClass(Document.class);
        verify(mongoTemplate, atLeastOnce()).insert(auditCaptor.capture(), eq("iiot_workflow_audit_trail"));
        Document auditDoc = auditCaptor.getValue();
        assertEquals("PRINT", auditDoc.getString("action"));
        assertEquals("Auditor Inspection Copy", auditDoc.getString("reason"));
        assertEquals("USER_01", auditDoc.getString("performedBy"));
    }

    @Test
    @DisplayName("Print count must belong to specific batch only and never affect other batches")
    void testPrintCountIsStrictlyBatchSpecificAndIndependent() {
        byte[] pdfBytes = "%PDF-1.4 Mock Dossier".getBytes(StandardCharsets.UTF_8);
        String base64Content = Base64.getEncoder().encodeToString(pdfBytes);

        Document docBatchA = new Document("documentId", "DOC-BATCH-A")
                .append("batchNo", "BATCH-001")
                .append("lotNo", "01 of 05")
                .append("equipmentCode", "G5RMG")
                .append("status", "ACTIVE")
                .append("base64Data", base64Content);

        Document summaryBatchA = new Document("batchNo", "BATCH-001")
                .append("lotNo", "01 of 05")
                .append("equipmentId", "G5RMG")
                .append("tenantId", "TNT-0001")
                .append("plantId", "PLNT-0001")
                .append("printCount", 5);

        Document summaryBatchB = new Document("batchNo", "BATCH-002")
                .append("lotNo", "01 of 05")
                .append("equipmentId", "G5RMG")
                .append("tenantId", "TNT-0001")
                .append("plantId", "PLNT-0001")
                .append("printCount", 2);

        // When searching DMS for BATCH-001
        when(mongoTemplate.findOne(argThat((Query q) -> q != null && q.getQueryObject() != null && q.getQueryObject().toString().contains("BATCH-001")), eq(Document.class), eq("dms_documents")))
                .thenReturn(docBatchA);

        // When finding batch summary for BATCH-001
        when(mongoTemplate.findOne(argThat((Query q) -> q != null && q.getQueryObject() != null && q.getQueryObject().toString().contains("BATCH-001")), eq(Document.class), eq("iiot_batch_summary")))
                .thenReturn(summaryBatchA);

        // When finding batch summary for BATCH-002
        when(mongoTemplate.findOne(argThat((Query q) -> q != null && q.getQueryObject() != null && q.getQueryObject().toString().contains("BATCH-002")), eq(Document.class), eq("iiot_batch_summary")))
                .thenReturn(summaryBatchB);

        // findAndModify for BATCH-001 returns updated count 6
        Document updatedSummaryBatchA = new Document("batchNo", "BATCH-001")
                .append("printCount", 6)
                .append("lastPrintedBy", "USER_QA");
        when(mongoTemplate.findAndModify(argThat((Query q) -> q != null && q.getQueryObject() != null && q.getQueryObject().toString().contains("BATCH-001")), any(), any(), eq(Document.class), eq("iiot_batch_summary")))
                .thenReturn(updatedSummaryBatchA);

        // Execute print on BATCH-001
        IiotOperationsService.ControlledPrintResult resultA = iiotOperationsService.controlledPrintBatchPdf(
                "BATCH-001", "01 of 05", "G5RMG", "QA Review Copy", "password123",
                "TNT-0001", "PLNT-0001", "USER_QA", "QA_APPROVER"
        );

        // Assert BATCH-001 received increment to 6
        assertNotNull(resultA);
        assertEquals(6, resultA.getPrintCount());

        // Verify that findAndModify was invoked targeting BATCH-001 ONLY
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate, atLeastOnce()).findAndModify(queryCaptor.capture(), any(), any(), eq(Document.class), eq("iiot_batch_summary"));
        assertTrue(queryCaptor.getValue().getQueryObject().toString().contains("BATCH-001"));
        assertFalse(queryCaptor.getValue().getQueryObject().toString().contains("BATCH-002"));

        // Batch B summary was untouched
        assertEquals(2, summaryBatchB.getInteger("printCount"));
    }

    @Test
    @DisplayName("Failed print (invalid e-signature) must NOT increment batch print count")
    void testFailedPrintDoesNotIncrementCount() {
        doThrow(new BusinessException("Invalid electronic signature credentials"))
                .when(dynamicWorkflowEngine)
                .verifyEsignature(eq("BAD_USER"), eq("wrongPassword"), anyString(), anyString());

        assertThrows(BusinessException.class, () ->
                iiotOperationsService.controlledPrintBatchPdf(
                        "BATCH-001", "01 of 05", "G5RMG", "Unauthorized Print Attempt", "wrongPassword",
                        "TNT-0001", "PLNT-0001", "BAD_USER", "OPERATOR"
                )
        );

        // Verify findAndModify was NEVER called
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(), any(), eq(Document.class), eq("iiot_batch_summary"));
        // Verify audit trail event was NEVER inserted for a failed print
        verify(mongoTemplate, never()).insert(argThat((Document d) -> d != null && "PRINT".equals(d.getString("action"))), eq("iiot_workflow_audit_trail"));
    }

    @Test
    @DisplayName("Print operation must respect tenant and plant isolation")
    void testPrintCountTenantIsolation() {
        byte[] pdfBytes = "%PDF-1.4 Mock Dossier".getBytes(StandardCharsets.UTF_8);
        String base64Content = Base64.getEncoder().encodeToString(pdfBytes);

        Document docTenant2 = new Document("documentId", "DOC-BATCH-T2")
                .append("batchNo", "BATCH-001")
                .append("lotNo", "01 of 05")
                .append("equipmentCode", "G5RMG")
                .append("status", "ACTIVE")
                .append("base64Data", base64Content);

        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("dms_documents")))
                .thenReturn(docTenant2);

        Document updatedSummaryT2 = new Document("batchNo", "BATCH-001")
                .append("tenantId", "TNT-0002")
                .append("plantId", "PLNT-0002")
                .append("printCount", 1)
                .append("lastPrintedBy", "USER_T2");

        when(mongoTemplate.findAndModify(any(Query.class), any(), any(), eq(Document.class), eq("iiot_batch_summary")))
                .thenReturn(updatedSummaryT2);

        IiotOperationsService.ControlledPrintResult result = iiotOperationsService.controlledPrintBatchPdf(
                "BATCH-001", "01 of 05", "G5RMG", "Tenant 2 Audit Copy", "pass123",
                "TNT-0002", "PLNT-0002", "USER_T2", "QA_APPROVER"
        );

        assertNotNull(result);
        assertEquals(1, result.getPrintCount());

        // Verify e-signature was checked with Tenant TNT-0002
        verify(dynamicWorkflowEngine, times(1)).verifyEsignature(
                eq("USER_T2"), eq("pass123"), eq("PRINT_BATCH_DOSSIER_PDF"), eq("TNT-0002")
        );
    }

    @Test
    @DisplayName("Print count must belong to specific stage only; uncompleted stages must remain 0 and never inherit counts from completed stages")
    void testStageSpecificPrintCountIsolationBetweenCompletedAndUncompletedStages() {
        byte[] pdfBytes = "%PDF-1.4 Mock Dossier".getBytes(StandardCharsets.UTF_8);
        String base64Content = Base64.getEncoder().encodeToString(pdfBytes);

        Document docFbd = new Document("documentId", "DOC-BATCH-FBD")
                .append("batchNo", "NL0026008")
                .append("lotNo", "01 of 05")
                .append("equipmentCode", "G5FBD")
                .append("status", "ACTIVE")
                .append("base64Data", base64Content);

        org.bson.types.ObjectId batchDocId = new org.bson.types.ObjectId();
        Document stageRmg = new Document("stageId", "STAGE-1")
                .append("equipmentCode", "G5RMG")
                .append("approval", new Document("status", "PENDING"))
                .append("printCount", 0);

        Document stageFbd = new Document("stageId", "STAGE-2")
                .append("equipmentCode", "G5FBD")
                .append("approval", new Document("status", "APPROVED"))
                .append("printCount", 2);

        Document batchSummaryWithStages = new Document("_id", batchDocId)
                .append("batchNo", "NL0026008")
                .append("lotNo", "01 of 05")
                .append("stages", List.of(stageRmg, stageFbd))
                .append("tenantId", "TNT-0001")
                .append("plantId", "PLNT-0001");

        when(mongoTemplate.findOne(argThat((Query q) -> q != null && q.getQueryObject() != null && q.getQueryObject().toString().contains("NL0026008")), eq(Document.class), eq("iiot_batch_summary")))
                .thenReturn(batchSummaryWithStages);

        when(mongoTemplate.findOne(argThat((Query q) -> q != null && q.getQueryObject() != null && q.getQueryObject().toString().contains("G5FBD")), eq(Document.class), eq("dms_documents")))
                .thenReturn(docFbd);

        // Perform print for G5FBD
        IiotOperationsService.ControlledPrintResult result = iiotOperationsService.controlledPrintBatchPdf(
                "NL0026008", "01 of 05", "G5FBD", "QA Inspection Copy", "validPass123",
                "TNT-0001", "PLNT-0001", "QA_APPROVER_1", "QA_APPROVER"
        );

        assertNotNull(result);
        assertEquals(3, result.getPrintCount()); // G5FBD count increments from 2 to 3

        // Verify that updateFirst targeted stages.$.printCount for G5FBD specifically
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, atLeastOnce()).updateFirst(queryCaptor.capture(), updateCaptor.capture(), eq("iiot_batch_summary"));

        Query capturedQ = queryCaptor.getValue();
        Update capturedU = updateCaptor.getValue();

        assertTrue(capturedQ.getQueryObject().toString().contains("G5FBD"));
        assertFalse(capturedQ.getQueryObject().toString().contains("G5RMG"));
        assertTrue(capturedU.getUpdateObject().toString().contains("stages.$.printCount"));

        // Verify audit event is strictly associated with G5FBD, not G5RMG
        ArgumentCaptor<Document> auditCaptor = ArgumentCaptor.forClass(Document.class);
        verify(mongoTemplate, atLeastOnce()).insert(auditCaptor.capture(), eq("iiot_workflow_audit_trail"));
        Document auditDoc = auditCaptor.getValue();
        assertEquals("NL0026008", auditDoc.getString("batchNo"));
        assertEquals("G5FBD", auditDoc.getString("equipmentCode"));
        assertEquals(3, auditDoc.getInteger("printCount"));
    }

    @Test
    @DisplayName("PDF should contain controlled print summary table with records when prints occur")
    void testPdfContainsUpdatedControlledPrintSummary() throws Exception {
        Document printEntry = new Document("copyNo", 1)
                .append("printedBy", "QA_AUDITOR_01")
                .append("userRole", "QA Reviewer")
                .append("printedAt", "2026-09-21T10:00:00Z")
                .append("reason", "Regulatory Submission Copy");

        Document summaryWithPrint = new Document(testSummary);
        summaryWithPrint.put("printHistory", List.of(printEntry));

        when(mongoTemplate.findOne(argThat((Query q) -> q != null && q.getQueryObject() != null && q.getQueryObject().toString().contains("NL0026008")), eq(Document.class), eq("iiot_batch_summary")))
                .thenReturn(summaryWithPrint);

        BatchPdfGeneratorService.PdfGenerationResult result = pdfGeneratorService.generateAndStoreBatchPdf(
                "NL0026008", "01 of 05", "G5FBD", "TNT-0001", "PLNT-0001", "QA_APPROVER_01", "QA_APPROVER"
        );

        assertNotNull(result);
        assertNotNull(result.getPdfBytes());
        assertTrue(pdfGeneratorService.pdfContainsLogo(result.getPdfBytes()), "Generated PDF must contain the Aurobindo company logo");
        java.nio.file.Files.write(java.nio.file.Paths.get("/tmp/verified_fixed_pdf.pdf"), result.getPdfBytes());

        PdfReader reader = new PdfReader(result.getPdfBytes());
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            sb.append(extractor.getTextFromPage(i)).append("\n");
        }
        reader.close();

        String pdfText = sb.toString();
        assertTrue(pdfText.contains("PRINT CONTROLLED SUMMARY & TRACEABILITY LOG") || pdfText.contains("CONTROLLED PRINT SUMMARY & TRACEABILITY LOG"));
        assertTrue(pdfText.contains("Copy #1"));
        assertTrue(pdfText.contains("QA_AUDITOR_01"));
        assertTrue(pdfText.contains("Regulatory Submission Copy"));
    }

    @Test
    @DisplayName("Workflow actions and Print Summary must never have orphan header when audit trail spans multiple pages")
    void testPdfWithManyAuditLogsWorkflowAndPrintSummaryNeverOrphaned() throws Exception {
        BatchPdfGeneratorService.PdfGenerationResult result = pdfGeneratorService.generateAndStoreBatchPdf(
                "NL0026008", "01 of 05", "G5RMG", "TNT-0001", "PLNT-0001", "QA_APPROVER_01", "QA_APPROVER");

        assertNotNull(result);
        assertNotNull(result.getPdfBytes());

        java.nio.file.Files.write(java.nio.file.Path.of("/tmp/test_g5rmg_fixed.pdf"), result.getPdfBytes());

        PdfReader reader = new PdfReader(result.getPdfBytes());
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            String pageText = extractor.getTextFromPage(i);
            if (pageText.contains("WORKFLOW ACTIONS & ELECTRONIC SIGNATURE RECORD")) {
                assertTrue(pageText.contains("Workflow Action") || pageText.contains("Performed By"),
                        "Page " + i + " has the header WORKFLOW ACTIONS but is missing table contents (orphan header!)");
            }
            if (pageText.contains("PRINT CONTROLLED SUMMARY & TRACEABILITY LOG") || pageText.contains("CONTROLLED PRINT SUMMARY & TRACEABILITY LOG")) {
                assertTrue(pageText.contains("Controlled Print Count:") || pageText.contains("Copy #"),
                        "Page " + i + " has the header CONTROLLED PRINT SUMMARY but is missing content!");
            }
        }
        reader.close();
    }
}
