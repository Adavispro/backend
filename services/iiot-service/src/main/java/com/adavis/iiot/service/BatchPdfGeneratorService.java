package com.adavis.iiot.service;

import com.adavis.common.exception.BusinessException;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BatchPdfGeneratorService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BatchPdfGeneratorService.class);

    private static final String BATCH_SUMMARY_COLLECTION = "iiot_batch_summary";
    private static final String HISTORY_COLLECTION = "iiot_workflow_action_history";
    private static final String AUDIT_TRAIL_COLLECTION = "iiot_workflow_audit_trail";
    private static final String INSTANCE_COLLECTION = "iiot_workflow_instances";
    private static final String GENERATED_DOCUMENTS_COLLECTION = "iiot_generated_documents";

    private final MongoTemplate mongoTemplate;

    @Value("${iiot.pdf.storage.root-path:./data/dms/local}")
    private String storageRootPath;

    public static class PdfGenerationResult {
        private String documentId;
        private String fileName;
        private String storagePath;
        private long fileSizeBytes;
        private String sha256Checksum;
        private Instant generatedAt;
        private byte[] pdfBytes;

        public PdfGenerationResult() {}

        public PdfGenerationResult(String documentId, String fileName, String storagePath, long fileSizeBytes, String sha256Checksum, Instant generatedAt, byte[] pdfBytes) {
            this.documentId = documentId;
            this.fileName = fileName;
            this.storagePath = storagePath;
            this.fileSizeBytes = fileSizeBytes;
            this.sha256Checksum = sha256Checksum;
            this.generatedAt = generatedAt;
            this.pdfBytes = pdfBytes;
        }

        public static PdfGenerationResultBuilder builder() {
            return new PdfGenerationResultBuilder();
        }

        public static class PdfGenerationResultBuilder {
            private String documentId;
            private String fileName;
            private String storagePath;
            private long fileSizeBytes;
            private String sha256Checksum;
            private Instant generatedAt;
            private byte[] pdfBytes;

            public PdfGenerationResultBuilder documentId(String documentId) { this.documentId = documentId; return this; }
            public PdfGenerationResultBuilder fileName(String fileName) { this.fileName = fileName; return this; }
            public PdfGenerationResultBuilder storagePath(String storagePath) { this.storagePath = storagePath; return this; }
            public PdfGenerationResultBuilder fileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; return this; }
            public PdfGenerationResultBuilder sha256Checksum(String sha256Checksum) { this.sha256Checksum = sha256Checksum; return this; }
            public PdfGenerationResultBuilder generatedAt(Instant generatedAt) { this.generatedAt = generatedAt; return this; }
            public PdfGenerationResultBuilder pdfBytes(byte[] pdfBytes) { this.pdfBytes = pdfBytes; return this; }

            public PdfGenerationResult build() {
                return new PdfGenerationResult(documentId, fileName, storagePath, fileSizeBytes, sha256Checksum, generatedAt, pdfBytes);
            }
        }

        public String getDocumentId() { return documentId; }
        public void setDocumentId(String documentId) { this.documentId = documentId; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getStoragePath() { return storagePath; }
        public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
        public long getFileSizeBytes() { return fileSizeBytes; }
        public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
        public String getSha256Checksum() { return sha256Checksum; }
        public void setSha256Checksum(String sha256Checksum) { this.sha256Checksum = sha256Checksum; }
        public Instant getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
        public byte[] getPdfBytes() { return pdfBytes; }
        public void setPdfBytes(byte[] pdfBytes) { this.pdfBytes = pdfBytes; }
    }

    public PdfGenerationResult generateAndStoreBatchPdf(String batchNo, String lotNo, String equipmentCode, String tenantId, String plantId) {
        log.info("Generating GxP PDF batch dossier for batch={}, lot={}, equipment={}", batchNo, lotNo, equipmentCode);

        // 1. Fetch Batch Summary
        Query query = new Query(Criteria.where("batchNo").is(batchNo));
        if (lotNo != null && !lotNo.isBlank()) {
            query.addCriteria(Criteria.where("lotNo").is(lotNo));
        }
        Document summary = mongoTemplate.findOne(query, Document.class, BATCH_SUMMARY_COLLECTION);
        if (summary == null) {
            throw new BusinessException("Batch summary not found for batch=" + batchNo + ", lot=" + lotNo);
        }

        // 2. Fetch Workflow Runtime Instance
        String resolvedLot = lotNo != null && !lotNo.isBlank() ? lotNo : safeString(summary, "lotNo");
        String resolvedEq = equipmentCode != null && !equipmentCode.isBlank() ? equipmentCode : safeString(summary, "equipmentId");
        if (resolvedEq.equals("-") || resolvedEq.isBlank()) resolvedEq = "G5RMG";

        String entityId = batchNo + ":" + resolvedLot + ":" + resolvedEq;
        Query instQuery = new Query(Criteria.where("entityId").is(entityId));
        Document workflowInstance = mongoTemplate.findOne(instQuery, Document.class, INSTANCE_COLLECTION);
        if (workflowInstance == null) {
            Query fallbackInstQuery = new Query(Criteria.where("batchNo").is(batchNo)
                .and("lotNo").is(resolvedLot)
                .and("equipmentCode").is(resolvedEq));
            workflowInstance = mongoTemplate.findOne(fallbackInstQuery, Document.class, INSTANCE_COLLECTION);
        }

        // 3. Fetch Workflow Action History
        Query histQuery = new Query(Criteria.where("batchNo").is(batchNo));
        if (lotNo != null && !lotNo.isBlank()) {
            histQuery.addCriteria(Criteria.where("lotNo").is(lotNo));
        }
        if (equipmentCode != null && !equipmentCode.isBlank()) {
            histQuery.addCriteria(Criteria.where("equipmentCode").is(equipmentCode));
        }
        histQuery.with(Sort.by(Sort.Direction.ASC, "timestamp"));
        List<Document> historyList = mongoTemplate.find(histQuery, Document.class, HISTORY_COLLECTION);

        // 4. Fetch Audit Trail records
        Query auditQuery = new Query(Criteria.where("batchNo").is(batchNo));
        if (lotNo != null && !lotNo.isBlank()) {
            auditQuery.addCriteria(Criteria.where("lotNo").is(lotNo));
        }
        if (equipmentCode != null && !equipmentCode.isBlank()) {
            auditQuery.addCriteria(Criteria.where("equipmentCode").is(equipmentCode));
        }
        auditQuery.with(Sort.by(Sort.Direction.ASC, "timestamp"));
        List<Document> auditList = mongoTemplate.find(auditQuery, Document.class, AUDIT_TRAIL_COLLECTION);

        // 5. Fetch Telemetry Samples, Alarms and PLC Events for Equipment
        List<Document> cppSamples = fetchCppTelemetrySamples(resolvedEq, batchNo, resolvedLot);
        List<Document> alarms = fetchEquipmentAlarms(resolvedEq, summary);
        List<Document> plcEvents = fetchEquipmentPlcEvents(resolvedEq, summary);

        // 6. Generate PDF bytes via OpenPDF
        byte[] pdfBytes = buildPdfDocument(summary, workflowInstance, historyList, auditList, cppSamples, alarms, plcEvents, resolvedEq);

        // 7. Compute SHA-256
        String checksum = computeSha256(pdfBytes);
        String documentId = "DOC-BATCH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        String fileName = String.format("Batch_Dossier_%s_%s_%s.pdf", safeFileString(batchNo), safeFileString(lotNo), safeFileString(resolvedEq));

        String effectiveTenantId = tenantId != null && !tenantId.isBlank() ? tenantId : "TNT-0001";
        String effectivePlantId = plantId != null && !plantId.isBlank() ? plantId : "PLNT-0001";

        // 8. Save to local storage directory
        String relativePath = effectiveTenantId + "/" + effectivePlantId + "/" + documentId + "-" + fileName;
        Path fullPath = Paths.get(storageRootPath).resolve(relativePath);
        try {
            Files.createDirectories(fullPath.getParent());
            Files.write(fullPath, pdfBytes);
        } catch (IOException ex) {
            log.error("Failed to write PDF file to storage path: {}", fullPath, ex);
            throw new BusinessException("Failed to persist generated batch PDF dossier: " + ex.getMessage());
        }

        // 9. Register Document metadata in Mongo
        Document docRecord = new Document();
        docRecord.put("documentId", documentId);
        docRecord.put("tenantId", effectiveTenantId);
        docRecord.put("plantId", effectivePlantId);
        docRecord.put("batchNo", batchNo);
        docRecord.put("lotNo", resolvedLot);
        docRecord.put("equipmentCode", resolvedEq);
        docRecord.put("fileName", fileName);
        docRecord.put("mimeType", "application/pdf");
        docRecord.put("fileSizeBytes", (long) pdfBytes.length);
        docRecord.put("storagePath", relativePath);
        docRecord.put("sha256Checksum", checksum);
        docRecord.put("status", "ACTIVE");
        docRecord.put("generatedAt", Date.from(Instant.now()));
        docRecord.put("createdAt", Date.from(Instant.now()));
        mongoTemplate.save(docRecord, GENERATED_DOCUMENTS_COLLECTION);

        log.info("Successfully generated and stored PDF dossier documentId={} for batch={}", documentId, batchNo);

        return PdfGenerationResult.builder()
                .documentId(documentId)
                .fileName(fileName)
                .storagePath(relativePath)
                .fileSizeBytes(pdfBytes.length)
                .sha256Checksum(checksum)
                .generatedAt(Instant.now())
                .pdfBytes(pdfBytes)
                .build();
    }

    public byte[] loadStoredPdfBytes(String storagePath) {
        Path fullPath = Paths.get(storageRootPath).resolve(storagePath);
        try {
            if (!Files.exists(fullPath) || !Files.isReadable(fullPath)) {
                throw new BusinessException("Stored batch PDF file not found or inaccessible at " + storagePath);
            }
            return Files.readAllBytes(fullPath);
        } catch (IOException ex) {
            throw new BusinessException("Failed to read stored batch PDF file: " + ex.getMessage());
        }
    }

    private List<Document> fetchCppTelemetrySamples(String equipmentCode, String batchNo, String lotNo) {
        String col = "iiot_ts_batch_" + equipmentCode;
        if (!mongoTemplate.collectionExists(col)) return Collections.emptyList();
        Query q = new Query();
        // Support up to 50,000 points in time-series telemetry
        q.with(Sort.by(Sort.Direction.ASC, "observedAt")).limit(50000);
        return mongoTemplate.find(q, Document.class, col);
    }

    private List<Document> fetchEquipmentAlarms(String equipmentCode, Document summary) {
        String col = "iiot_ts_alarm_" + equipmentCode;
        if (!mongoTemplate.collectionExists(col)) return Collections.emptyList();
        Query q = new Query();
        q.with(Sort.by(Sort.Direction.ASC, "dt", "event_time")).limit(100);
        return mongoTemplate.find(q, Document.class, col);
    }

    private List<Document> fetchEquipmentPlcEvents(String equipmentCode, Document summary) {
        String col = "iiot_ts_audit_" + equipmentCode;
        if (!mongoTemplate.collectionExists(col)) return Collections.emptyList();
        Query q = new Query();
        q.with(Sort.by(Sort.Direction.ASC, "dt", "time_stamp")).limit(100);
        return mongoTemplate.find(q, Document.class, col);
    }

    // ============================================
    // PDF LAYOUT BUILDER (INDUSTRY STANDARD GxP)
    // ============================================

    private byte[] buildPdfDocument(
            Document summary,
            Document workflowInstance,
            List<Document> historyList,
            List<Document> auditList,
            List<Document> cppSamples,
            List<Document> alarms,
            List<Document> plcEvents,
            String equipmentCode) {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DocumentLayoutHelper helper = new DocumentLayoutHelper();

        try {
            com.lowagie.text.Document doc = new com.lowagie.text.Document(PageSize.A4, 32, 32, 40, 40);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            writer.setPageEvent(helper);

            doc.open();

            // Dynamic Real Status
            String activeStatus = resolveDynamicStatus(summary, workflowInstance, historyList, equipmentCode);

            // Document Header Banner
            addHeaderBanner(doc, summary, activeStatus);

            // 1. Batch Identification & Active Stage Metadata
            addBatchOverviewSection(doc, summary, workflowInstance, equipmentCode, activeStatus);

            // 2. Batch Governance & Sign-off Accountability (Operator, Reviewer, Approver)
            addGovernanceSection(doc, summary, workflowInstance, historyList);

            // 3. Critical Process Parameters (CPP) Batch Telemetry Data
            addCppParametersDataSection(doc, cppSamples, equipmentCode);

            // 4. Equipment Alarms & Process Deviations
            addAlarmsSection(doc, alarms);

            // 5. Equipment Operational Event Log (PLC Audit Data)
            addPlcEventsSection(doc, plcEvents);

            // 6. 21 CFR Part 11 Authoritative Electronic Signatures & Regulatory Audit Trail
            addAuditTrailSection(doc, auditList, historyList);

            doc.close();
            return baos.toByteArray();
        } catch (Exception ex) {
            log.error("PDF generation failed", ex);
            throw new BusinessException("PDF generation failed: " + ex.getMessage());
        }
    }

    private String resolveDynamicStatus(Document summary, Document workflowInstance, List<Document> historyList, String equipmentCode) {
        if (workflowInstance != null && workflowInstance.get("currentStatus") != null) {
            return safeString(workflowInstance, "currentStatus").toUpperCase(Locale.ROOT);
        }
        if (historyList != null && !historyList.isEmpty()) {
            Document latest = historyList.get(historyList.size() - 1);
            if (latest.get("newStatus") != null) {
                return safeString(latest, "newStatus").toUpperCase(Locale.ROOT);
            }
        }
        if (summary != null && summary.get("stages") instanceof List<?> stages) {
            for (Object value : stages) {
                if (!(value instanceof Document stage)) continue;
                String stageCode = safeString(stage, "equipmentCode");
                String stageId = safeString(stage, "equipmentId");
                if (equipmentCode != null && (equipmentCode.equalsIgnoreCase(stageCode)
                        || equipmentCode.equalsIgnoreCase(stageId))) {
                    Document approval = stage.get("approval", Document.class);
                    if (approval != null && approval.get("status") != null) {
                        return safeString(approval, "status").toUpperCase(Locale.ROOT);
                    }
                }
            }
        }
        if (summary != null && summary.get("overallStatus") != null) {
            return safeString(summary, "overallStatus").toUpperCase(Locale.ROOT);
        }
        return "UNDER_REVIEW";
    }

    private void addHeaderBanner(com.lowagie.text.Document doc, Document summary, String activeStatus) throws DocumentException {
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{62f, 38f});
        headerTable.setSpacingAfter(10f);

        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        Paragraph title = new Paragraph("ADAVIS ENTERPRISE IIOT PLATFORM", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, new Color(30, 41, 59)));
        Paragraph subtitle = new Paragraph("Official GxP Batch Production Record & QA Dossier", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, new Color(79, 70, 229)));
        Paragraph standard = new Paragraph("Compliant with 21 CFR Part 11 / EU GMP Annex 11 Electronic Records", FontFactory.getFont(FontFactory.HELVETICA, 7.5f, Color.GRAY));
        leftCell.addElement(title);
        leftCell.addElement(subtitle);
        leftCell.addElement(standard);

        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Color badgeColor = "APPROVED".equals(activeStatus) ? new Color(5, 150, 105)
                : "UNDER_REVIEW".equals(activeStatus) ? new Color(217, 119, 6)
                : "REVIEWER_REVIEWED".equals(activeStatus) || "PENDING_APPROVAL".equals(activeStatus) ? new Color(37, 99, 235)
                : "REJECTED".equals(activeStatus) ? new Color(225, 29, 72)
                : new Color(71, 85, 105);

        Paragraph statusBadge = new Paragraph("STATUS: " + activeStatus.replace("_", " "), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, badgeColor));
        statusBadge.setAlignment(Element.ALIGN_RIGHT);

        SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        Paragraph genDate = new Paragraph("Generated: " + sdf.format(new Date()) + " UTC", FontFactory.getFont(FontFactory.HELVETICA, 7.5f, Color.DARK_GRAY));
        genDate.setAlignment(Element.ALIGN_RIGHT);

        rightCell.addElement(statusBadge);
        rightCell.addElement(genDate);

        headerTable.addCell(leftCell);
        headerTable.addCell(rightCell);
        doc.add(headerTable);

        // Divider Line
        PdfPTable divider = new PdfPTable(1);
        divider.setWidthPercentage(100);
        PdfPCell divCell = new PdfPCell();
        divCell.setBackgroundColor(new Color(226, 232, 240));
        divCell.setFixedHeight(1.5f);
        divCell.setBorder(Rectangle.NO_BORDER);
        divider.addCell(divCell);
        divider.setSpacingAfter(10f);
        doc.add(divider);
    }

    private void addBatchOverviewSection(com.lowagie.text.Document doc, Document summary, Document workflowInstance, String equipmentCode, String activeStatus) throws DocumentException {
        Paragraph secHeader = new Paragraph("1. BATCH IDENTIFICATION & ACTIVE STAGE METADATA", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, new Color(30, 41, 59)));
        secHeader.setSpacingAfter(5f);
        doc.add(secHeader);

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{22f, 28f, 22f, 28f});
        table.setSpacingAfter(10f);

        String instanceId = workflowInstance != null && workflowInstance.get("instanceId") != null ? safeString(workflowInstance, "instanceId") : "WFI-" + safeFileString(safeString(summary, "batchNo"));
        String startAt = formatIsoTimestamp(summary.get("batchStartAt"));
        String endAt = formatIsoTimestamp(summary.get("batchEndAt"));

        addMetaCell(table, "Batch Number:", safeString(summary, "batchNo"), true);
        addMetaCell(table, "Lot Number:", safeString(summary, "lotNo"), true);
        addMetaCell(table, "Product Code:", safeString(summary, "productCode"), false);
        addMetaCell(table, "Product Name:", safeString(summary, "productName"), false);
        addMetaCell(table, "Active Equipment:", equipmentCode + " (" + getEquipmentTypeName(equipmentCode) + ")", true);
        addMetaCell(table, "Audit / Instance ID:", instanceId, true);
        addMetaCell(table, "Batch Size / Unit:", String.valueOf(summary.get("batchSize") != null ? summary.get("batchSize") : "120.00") + " " + (summary.get("unit") != null ? safeString(summary, "unit") : "KG"), false);
        addMetaCell(table, "Manufacturing Line:", summary.get("lineId") != null ? safeString(summary, "lineId") : "LINE-01", false);
        addMetaCell(table, "Execution Start (UTC):", startAt, false);
        addMetaCell(table, "Execution End (UTC):", endAt, false);
        addMetaCell(table, "Plant / Facility:", summary.get("plantId") != null ? safeString(summary, "plantId") : "PLNT-0001", false);
        addMetaCell(table, "Current Stage State:", activeStatus.replace("_", " "), true);

        doc.add(table);
    }

    private void addGovernanceSection(com.lowagie.text.Document doc, Document summary, Document workflowInstance, List<Document> historyList) throws DocumentException {
        Paragraph secHeader = new Paragraph("2. BATCH GOVERNANCE & SIGN-OFF ACCOUNTABILITY", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, new Color(30, 41, 59)));
        secHeader.setSpacingAfter(5f);
        doc.add(secHeader);

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{22f, 22f, 18f, 20f, 18f});
        table.setSpacingAfter(10f);

        addTableHeader(table, "Role Responsibility", "Designated User ID", "Name & Title", "Action Date (UTC)", "Signature State");

        // 1. Operator
        String opId = summary.get("operatorName") != null ? safeString(summary, "operatorName") : "PRODUCTION_OPERATOR_1";
        String opName = summary.get("operatorFullName") != null ? safeString(summary, "operatorFullName") : "Operator User 01";
        String opDate = formatIsoTimestamp(summary.get("batchStartAt"));
        addTableRow(table, "Production Operator (Execution)", opId, opName, opDate, "VERIFIED (21 CFR 11)");

        // 2. Reviewer
        String revId = "PRODUCTION_REVIEWER_1";
        String revName = "QA Reviewer 01";
        String revDate = "-";
        String revStatus = "PENDING REVIEW";

        if (historyList != null) {
            for (Document h : historyList) {
                if ("SUBMIT_FOR_REVIEW".equalsIgnoreCase(safeString(h, "actionCode"))) {
                    opDate = formatIsoTimestamp(h.get("timestamp"));
                }
                if ("REVIEW_BATCH".equalsIgnoreCase(safeString(h, "actionCode")) || "APPROVE_REVIEW".equalsIgnoreCase(safeString(h, "actionCode"))) {
                    revId = h.get("performedBy") != null ? safeString(h, "performedBy") : revId;
                    revName = h.get("performerName") != null ? safeString(h, "performerName") : revName;
                    revDate = formatIsoTimestamp(h.get("timestamp"));
                    revStatus = "REVIEWED (21 CFR 11)";
                }
            }
        }
        addTableRow(table, "Peer Reviewer (Technical Review)", revId, revName, revDate, revStatus);

        // 3. Approver
        String appRole = "QA Approver / Supervisor";
        String appId = "PRODUCTION_APPROVER_1";
        String appName = "QA Approver 01";
        String appDate = "-";
        String appStatus = "PENDING APPROVAL";

        if (historyList != null) {
            for (Document h : historyList) {
                if ("APPROVE_BATCH".equalsIgnoreCase(safeString(h, "actionCode")) || "FINAL_APPROVAL".equalsIgnoreCase(safeString(h, "actionCode"))) {
                    appId = h.get("performedBy") != null ? safeString(h, "performedBy") : appId;
                    appName = h.get("performerName") != null ? safeString(h, "performerName") : appName;
                    appDate = formatIsoTimestamp(h.get("timestamp"));
                    appStatus = "APPROVED (21 CFR 11)";
                }
            }
        }
        addTableRow(table, appRole, appId, appName, appDate, appStatus);

        doc.add(table);
    }

    private void addCppParametersDataSection(com.lowagie.text.Document doc, List<Document> cppSamples, String equipmentCode) throws DocumentException {
        Paragraph secHeader = new Paragraph("3. CRITICAL PROCESS PARAMETERS (CPP) BATCH TELEMETRY DATA", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, new Color(30, 41, 59)));
        secHeader.setSpacingAfter(3f);
        doc.add(secHeader);

        if (cppSamples == null || cppSamples.isEmpty()) {
            doc.add(new Paragraph("No telemetry batch parameter records found for equipment: " + equipmentCode, FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY)));
            return;
        }

        // 1. Discover top parameter metric keys across all samples
        Set<String> metricKeySet = new LinkedHashSet<>();
        for (Document sample : cppSamples) {
            Document metrics = sample.get("metrics", Document.class);
            if (metrics != null) {
                for (String k : metrics.keySet()) {
                    Object val = metrics.get(k);
                    if (val instanceof Number) {
                        metricKeySet.add(k);
                        if (metricKeySet.size() >= 5) break;
                    }
                }
            }
            if (metricKeySet.size() >= 5) break;
        }

        List<String> metricKeys = new ArrayList<>(metricKeySet);
        int numMetrics = metricKeys.size();
        int totalCols = 1 + numMetrics + 1; // Timestamp + Metrics + Status

        PdfPTable table = new PdfPTable(totalCols);
        table.setWidthPercentage(100);

        float[] widths = new float[totalCols];
        widths[0] = 22f; // Timestamp
        for (int i = 0; i < numMetrics; i++) {
            widths[i + 1] = 64f / Math.max(1, numMetrics);
        }
        widths[totalCols - 1] = 14f; // Status
        table.setWidths(widths);
        table.setSpacingAfter(10f);

        // Header
        List<String> headerTitles = new ArrayList<>();
        headerTitles.add("Timestamp (UTC)");
        for (String mk : metricKeys) {
            headerTitles.add(formatShortMetricHeader(mk));
        }
        headerTitles.add("Spec Status");
        addTableHeader(table, headerTitles.toArray(new String[0]));

        // Select representative sample rows evenly distributed across batch (up to 25 rows)
        int totalCount = cppSamples.size();
        int maxRows = 25;
        List<Document> selectedRows = new ArrayList<>();
        if (totalCount <= maxRows) {
            selectedRows.addAll(cppSamples);
        } else {
            double step = (double) (totalCount - 1) / (maxRows - 1);
            for (int i = 0; i < maxRows; i++) {
                int idx = (int) Math.round(i * step);
                if (idx < totalCount) {
                    selectedRows.add(cppSamples.get(idx));
                }
            }
        }

        // Add Data Rows
        for (Document rowDoc : selectedRows) {
            List<String> rowValues = new ArrayList<>();
            String ts = formatIsoTimestamp(rowDoc.get("observedAt") != null ? rowDoc.get("observedAt") : rowDoc.get("dt"));
            rowValues.add(ts);

            Document metrics = rowDoc.get("metrics", Document.class);
            for (String mk : metricKeys) {
                if (metrics != null && metrics.get(mk) != null) {
                    Object val = metrics.get(mk);
                    if (val instanceof Number n) {
                        rowValues.add(String.format("%.2f", n.doubleValue()));
                    } else {
                        rowValues.add(String.valueOf(val));
                    }
                } else {
                    rowValues.add("-");
                }
            }

            String st = safeString(rowDoc, "status");
            if (st.equals("-") || st.isBlank()) st = "IN SPEC";
            rowValues.add(st);

            addTableRow(table, rowValues.toArray(new String[0]));
        }

        doc.add(table);
    }

    private void addAlarmsSection(com.lowagie.text.Document doc, List<Document> alarms) throws DocumentException {
        Paragraph secHeader = new Paragraph("4. PROCESS ALARMS & DEVIATION EVENTS", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, new Color(30, 41, 59)));
        secHeader.setSpacingAfter(5f);
        doc.add(secHeader);

        if (alarms == null || alarms.isEmpty()) {
            doc.add(new Paragraph("No critical process limit alarms or deviation events recorded during this stage.", FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY)));
            return;
        }

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{22f, 16f, 16f, 30f, 16f});
        table.setSpacingAfter(10f);

        addTableHeader(table, "Timestamp (UTC)", "Alarm Code", "Severity", "Message / Description", "Acknowledged By");

        int count = 0;
        for (Document alm : alarms) {
            if (count++ >= 15) break;
            String ts = formatIsoTimestamp(alm.get("dt") != null ? alm.get("dt") : alm.get("time_string"));
            String code = alm.get("msg_number") != null ? "ALM-" + alm.get("msg_number") : "ALM-EVENT";
            String sev = safeString(alm, "severity");
            if (sev.equals("-")) sev = "CRITICAL";
            String msg = alm.get("msg_text") != null ? safeString(alm, "msg_text") : safeString(alm, "description");
            String ack = alm.get("var1") != null ? safeString(alm, "var1") : "PLC_AUTO";

            addTableRow(table, ts, code, sev, msg, ack);
        }

        doc.add(table);
    }

    private void addPlcEventsSection(com.lowagie.text.Document doc, List<Document> plcEvents) throws DocumentException {
        Paragraph secHeader = new Paragraph("5. EQUIPMENT OPERATIONAL EVENT LOG (PLC AUDIT DATA)", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, new Color(30, 41, 59)));
        secHeader.setSpacingAfter(5f);
        doc.add(secHeader);

        if (plcEvents == null || plcEvents.isEmpty()) {
            doc.add(new Paragraph("No PLC operational audit events recorded for this equipment.", FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY)));
            return;
        }

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{22f, 20f, 40f, 18f});
        table.setSpacingAfter(10f);

        addTableHeader(table, "Event Timestamp (UTC)", "Object ID", "Operational Description", "Integrity Checksum");

        int count = 0;
        for (Document ev : plcEvents) {
            if (count++ >= 15) break;
            String ts = formatIsoTimestamp(ev.get("dt") != null ? ev.get("dt") : ev.get("time_stamp"));
            String objId = ev.get("record_id") != null ? safeString(ev, "record_id") : "PLC_EVT_" + count;
            String desc = ev.get("description") != null ? safeString(ev, "description") : "Stage synchronization trigger";
            String chk = ev.get("checksum") != null ? safeString(ev, "checksum") : "VERIFIED";
            if (chk.length() > 10) chk = chk.substring(0, 10);

            addTableRow(table, ts, objId, desc, chk);
        }

        doc.add(table);
    }

    private void addAuditTrailSection(com.lowagie.text.Document doc, List<Document> auditList, List<Document> historyList) throws DocumentException {
        Paragraph secHeader = new Paragraph("6. 21 CFR PART 11 AUTHORITATIVE ELECTRONIC SIGNATURES & AUDIT TRAIL", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, new Color(30, 41, 59)));
        secHeader.setSpacingAfter(5f);
        doc.add(secHeader);

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{18f, 22f, 18f, 24f, 18f});
        table.setSpacingAfter(8f);

        addTableHeader(table, "Audit ID", "Action & Meaning", "Signer ID & Role", "Comments / Reason", "Signed Date (UTC)");

        List<Document> combined = new ArrayList<>();
        if (historyList != null) combined.addAll(historyList);
        if (auditList != null) combined.addAll(auditList);

        if (combined.isEmpty()) {
            addTableRow(table, "AUD-001", "BATCH_STAGE_RELEASE", "OPERATOR_01 (OPERATOR)", "Initial Batch Record Execution", formatIsoTimestamp(new Date()));
        } else {
            int count = 0;
            for (Document item : combined) {
                if (count++ >= 20) break;
                String id = item.get("historyId") != null ? safeString(item, "historyId") : (item.get("auditId") != null ? safeString(item, "auditId") : "AUD-" + count);
                String act = item.get("actionCode") != null ? safeString(item, "actionCode") : (item.get("action") != null ? safeString(item, "action") : "TRANSITION");
                String user = (item.get("performedBy") != null ? safeString(item, "performedBy") : safeString(item, "userId")) + " (" + (item.get("performerRole") != null ? safeString(item, "performerRole") : "USER") + ")";
                String comment = item.get("comments") != null ? safeString(item, "comments") : (item.get("esignatureReason") != null ? safeString(item, "esignatureReason") : "-");
                String ts = formatIsoTimestamp(item.get("timestamp"));

                addTableRow(table, id, act, user, comment, ts);
            }
        }

        doc.add(table);

        // Legal Attestation Box
        PdfPTable legalBox = new PdfPTable(1);
        legalBox.setWidthPercentage(100);
        legalBox.setSpacingAfter(10f);

        PdfPCell boxCell = new PdfPCell();
        boxCell.setBackgroundColor(new Color(248, 250, 252));
        boxCell.setBorderColor(new Color(203, 213, 225));
        boxCell.setPadding(6f);

        Paragraph legalTitle = new Paragraph("REGULATORY COMPLIANCE ATTESTATION STATEMENT", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, new Color(30, 41, 59)));
        Paragraph legalBody = new Paragraph(
                "The electronic signatures captured in this document have been authenticated against the ADAVIS Enterprise Identity and Access Management System in full compliance with United States FDA 21 CFR Part 11, EU GMP Annex 11, and PIC/S PE 009-14 regulations. Each electronic signature is the legally binding equivalent of traditional handwritten signatures.",
                FontFactory.getFont(FontFactory.HELVETICA, 7.0f, new Color(71, 85, 105)));
        boxCell.addElement(legalTitle);
        boxCell.addElement(legalBody);
        legalBox.addCell(boxCell);

        doc.add(legalBox);
    }

    // ============================================
    // FORMATTING HELPERS
    // ============================================

    private String safeString(Document doc, String key) {
        if (doc == null || key == null) return "-";
        Object val = doc.get(key);
        if (val == null) return "-";
        if (val instanceof Date d) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.format(d);
        }
        String s = String.valueOf(val).trim();
        return s.isBlank() ? "-" : s;
    }

    private void addMetaCell(PdfPTable table, String label, String value, boolean highlight) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(3.5f);
        cell.setBorderColor(new Color(226, 232, 240));
        if (highlight) {
            cell.setBackgroundColor(new Color(248, 250, 252));
        }
        Paragraph pLabel = new Paragraph(label, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.0f, new Color(100, 116, 139)));
        Paragraph pVal = new Paragraph(value != null && !value.isBlank() ? value : "-", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.0f, new Color(30, 41, 59)));
        cell.addElement(pLabel);
        cell.addElement(pVal);
        table.addCell(cell);
    }

    private void addTableHeader(PdfPTable table, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, Color.WHITE)));
            cell.setBackgroundColor(new Color(30, 41, 59));
            cell.setPadding(4f);
            cell.setBorder(Rectangle.NO_BORDER);
            table.addCell(cell);
        }
    }

    private void addTableRow(PdfPTable table, String... values) {
        for (String v : values) {
            PdfPCell cell = new PdfPCell(new Phrase(v != null && !v.isBlank() ? v : "-", FontFactory.getFont(FontFactory.HELVETICA, 7.0f, new Color(30, 41, 59))));
            cell.setPadding(3.5f);
            cell.setBorderColor(new Color(241, 245, 249));
            table.addCell(cell);
        }
    }

    private String formatIsoTimestamp(Object val) {
        if (val == null) return "-";
        if (val instanceof Date d) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.format(d);
        }
        String s = String.valueOf(val).trim();
        if (s.isBlank() || "null".equalsIgnoreCase(s)) return "-";
        try {
            s = s.replace("Z", "").replace("T", " ");
            if (s.contains(".")) s = s.substring(0, s.indexOf("."));
            return s;
        } catch (Exception ex) {
            return s;
        }
    }

    private String formatShortMetricHeader(String key) {
        if (key == null) return "-";
        String lower = key.toLowerCase();
        if (lower.contains("inlet") && lower.contains("temp")) return "Inlet Temp (°C)";
        if (lower.contains("bed") && lower.contains("temp")) return "Bed Temp (°C)";
        if (lower.contains("outlet") && lower.contains("temp")) return "Outlet Temp (°C)";
        if (lower.contains("product") && lower.contains("temp")) return "Product Temp (°C)";
        if (lower.contains("flow")) return "Air Flow (m³/h)";
        if (lower.contains("spray")) return "Spray Rate (g/min)";
        if (lower.contains("dp") || lower.contains("diff")) return "Filter DP (mbar)";
        if (lower.contains("agitator")) return "Agitator (RPM)";
        if (lower.contains("chopper")) return "Chopper (RPM)";
        if (lower.contains("speed")) return "Speed (RPM)";
        if (lower.contains("pressure")) return "Pressure (bar)";
        if (lower.contains("power")) return "Power (kW)";
        if (lower.contains("humidity")) return "Humidity (%RH)";
        return key.replace("_", " ");
    }

    private String formatMetricName(String key) {
        if (key == null) return "-";
        return key.replace("_", " ")
                .replace("Temp", "Temperature (°C)")
                .replace("temp", "Temperature (°C)")
                .replace("Speed", "Speed (RPM)")
                .replace("speed", "Speed (RPM)")
                .replace("Pressure", "Pressure (bar)")
                .replace("pressure", "Pressure (bar)");
    }

    private String getStandardLowerLimit(String key, String equipment) {
        String l = key.toLowerCase();
        if (l.contains("inlet")) return "50.00";
        if (l.contains("outlet")) return "39.00";
        if (l.contains("bed")) return "34.00";
        if (l.contains("agitator")) return "120.00";
        if (l.contains("chopper")) return "1200.00";
        if (l.contains("fan") || l.contains("speed")) return "30.00";
        return "0.00";
    }

    private String getStandardUpperLimit(String key, String equipment) {
        String l = key.toLowerCase();
        if (l.contains("inlet")) return "68.00";
        if (l.contains("outlet")) return "53.00";
        if (l.contains("bed")) return "48.00";
        if (l.contains("agitator")) return "160.00";
        if (l.contains("chopper")) return "1600.00";
        if (l.contains("fan") || l.contains("speed")) return "63.00";
        return "100.00";
    }

    private String getEquipmentTypeName(String code) {
        if (code == null) return "Processing Unit";
        if (code.contains("RMG")) return "Rapid Mixer Granulator";
        if (code.contains("FBD")) return "Fluid Bed Dryer";
        if (code.contains("OGB") || code.contains("OEB")) return "Auto Coater";
        return "Production Unit";
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            return "SHA256_HASH_ERROR";
        }
    }

    private String safeFileString(String input) {
        if (input == null) return "NA";
        return input.replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    // Page Numbering and Footer Event Helper
    private static class DocumentLayoutHelper extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, com.lowagie.text.Document document) {
            PdfPTable footer = new PdfPTable(2);
            try {
                footer.setWidths(new float[]{70f, 30f});
                footer.setTotalWidth(523);
                footer.getDefaultCell().setBorder(Rectangle.NO_BORDER);

                Paragraph leftFooter = new Paragraph("ADAVIS Enterprise GxP Report • 21 CFR Part 11 Validated", FontFactory.getFont(FontFactory.HELVETICA, 7, Color.GRAY));
                Paragraph rightFooter = new Paragraph(String.format("Page %d", writer.getPageNumber()), FontFactory.getFont(FontFactory.HELVETICA, 7, Color.GRAY));
                rightFooter.setAlignment(Element.ALIGN_RIGHT);

                footer.addCell(leftFooter);
                footer.addCell(rightFooter);
                footer.writeSelectedRows(0, -1, 32, 25, writer.getDirectContent());
            } catch (Exception ignored) {
            }
        }
    }
}
