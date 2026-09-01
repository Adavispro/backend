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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

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

    private BatchPdfGeneratorService pdfGeneratorService;
    private IiotOperationsService iiotOperationsService;

    private Document testSummary;
    private Document testWorkflowInstance;

    @BeforeEach
    void setUp() {
        pdfGeneratorService = new BatchPdfGeneratorService(mongoTemplate);
        ReflectionTestUtils.setField(pdfGeneratorService, "storageRootPath", "/non_existent_unwritable_path/./data/dms/local");

        iiotOperationsService = new IiotOperationsService(mongoTemplate, stringRedisTemplate, new ObjectMapper(), pdfGeneratorService);

        testSummary = new Document("batchNo", "NL0026008")
                .append("lotNo", "01 of 05")
                .append("productCode", "STFS7000")
                .append("productName", "Finasteride USP 5 mg")
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
        assertEquals(result.getSha256Checksum(), updatedSummary.getString("pdfSha256Checksum"));
    }

    @Test
    @DisplayName("Should serve existing stored PDF from dms_documents without regenerating")
    void testGetBatchPdfBytesServingExistingStoredDocument() {
        byte[] existingPdf = "%PDF-1.4 Mock Stored PDF Content".getBytes(StandardCharsets.UTF_8);
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
}
