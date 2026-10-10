package com.adavis.iiot.service;

import com.adavis.common.exception.BusinessException;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.bson.Document;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.*;
import java.util.List;

/** Source-based final batch dossier; engineering values are never replaced with sample defaults. */
final class CompressionBatchPdfRenderer {
    private static final Font LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.2f, new Color(30, 41, 59));
    private static final Font VALUE = FontFactory.getFont(FontFactory.HELVETICA, 7.2f, new Color(30, 41, 59));

    byte[] render(String batchNo, String equipmentCode, List<BatchPdfGeneratorService.CompressionLot> lots,
                  List<Document> printHistory, String actor, String role, byte[] logo) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        com.lowagie.text.Document pdf = new com.lowagie.text.Document(PageSize.A4, 36, 36, 36, 42);
        Instant generatedAt = Instant.now();
        try {
            PdfWriter writer = PdfWriter.getInstance(pdf, out);
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter w, com.lowagie.text.Document d) {
                    ColumnText.showTextAligned(w.getDirectContent(), Element.ALIGN_LEFT,
                            new Phrase(batchNo + " / " + equipmentCode + " | QA APPROVED | Batch Print", VALUE), d.left(), 24, 0);
                    ColumnText.showTextAligned(w.getDirectContent(), Element.ALIGN_RIGHT,
                            new Phrase("Page " + w.getPageNumber(), VALUE), d.right(), 24, 0);
                }
            });
            pdf.open();
            pdf.addTitle(BatchPdfGeneratorService.compressionFileName(batchNo, equipmentCode).replaceFirst("\\.pdf$", ""));
            for (BatchPdfGeneratorService.CompressionLot lot : lots) {
                if (writer.getPageNumber() > 0) pdf.newPage();
                Document details = child(lot.sample(), "compression_details");
                Document info = child(details, "batchInfo");
                Document recipe = child(details, "recipeSettings");
                Document counters = child(details, "tabletCounters");
                header(pdf, logo, batchNo, lot.lotNo(), "PRODUCTION REPORT (1/2)", generatedAt);
                heading(pdf, "Product Information");
                fields(pdf, List.of(
                        pair("Station No.", text(info, "stationNo")), pair("Product Name", text(info, "productName")),
                        pair("Machine Name", text(info, "machineName")), pair("Batch No.", batchNo),
                        pair("Derived Lot No.", lot.lotNo()), pair("Recipe Name", text(lot.summary(), "recipeName")),
                        pair("User ID", text(info, "userId")), pair("Operator", text(info, "operatorName")),
                        pair("Print Interval", unit(info, "printInterval", "Tabs")), pair("Running Time", text(info, "runningTime")),
                        pair("Total Counter", unit(counters, "totalCounter", "Tabs")), pair("Total Running Time", text(info, "totalRunningTime")),
                        pair("Report Time", text(details, "metadata.reportTimestamp")), pair("Source File", text(details, "metadata.sourceFile"))));
                heading(pdf, "Setting Value");
                List<String[]> settings = new ArrayList<>(List.of(
                        pair("Feeder Auto", unit(recipe, "feeder.autoPercent", "%")), pair("Feeder Manual", unit(recipe, "feeder.manualRpm", "RPM")),
                        pair("Filling Cam", text(recipe, "fillingCam")), pair("Target Quantity", unit(recipe, "targetQuantity", "Tabs")),
                        pair("Air Pressure Low Limit", unit(recipe, "airPressureLowLimitKpa", "kPa")),
                        pair("Hydraulic High Limit", unit(recipe, "hydraulicPressureLimits.highLimitMpa", "MPa")),
                        pair("Hydraulic Low Limit", unit(recipe, "hydraulicPressureLimits.lowLimitMpa", "MPa")),
                        pair("Powder Supply Time", unit(recipe, "powderSupplyTimeSec", "Sec")),
                        pair("Initial Reject Time", unit(recipe, "initialRejectTimeSec", "Sec"))));
                for (String[] oil : List.of(pair("upperPunchS1", "Upper Punch S1"), pair("lowerPunchS2", "Lower Punch S2"), pair("lowerHeadS3", "Lower Head S3"))) {
                    settings.add(pair(oil[1] + " Interval", unit(recipe, "oilLubrication." + oil[0] + ".intervalMin", "Min")));
                    settings.add(pair(oil[1] + " Supply", unit(recipe, "oilLubrication." + oil[0] + ".supplySec", "Sec")));
                }
                settings.add(pair("Upper Punch Tightness High", text(recipe, "upperPunchTightnessHigh")));
                settings.add(pair("Lower Punch Tightness High", text(recipe, "lowerPunchTightnessHigh")));
                settings.add(pair("Ejecting Force High", text(recipe, "ejectingForceHigh")));
                fields(pdf, settings);
                heading(pdf, "Control Limits & Stop Conditions");
                PdfPTable limits = table("Parameter", "% Setting", "kN Limit", "Stop Condition");
                for (String[] limit : List.of(pair("hsp", "HSP"), pair("hep", "HEP"), pair("hcp", "HCP"), pair("ref", "Reference"),
                        pair("lcp", "LCP"), pair("lep", "LEP"), pair("lsp", "LSP"), pair("sdLimit", "SD Limit"), pair("preHsp", "Pre HSP"))) {
                    Document value = child(child(recipe, "controlLimits"), limit[0]);
                    String condition = text(value, "stop");
                    if (!"-".equals(condition)) condition = "Stop: " + condition;
                    else if (value.containsKey("rot")) condition = unit(value, "rot", "Rot") + " / " + unit(value, "tabs", "Tabs");
                    else if (value.containsKey("times")) condition = unit(value, "times", "Times");
                    row(limits, limit[1], unit(value, "percent", "%"), unit(value, "kn", "kN"), condition);
                }
                pdf.add(limits);
                fields(pdf, List.of(pair("Mean Calculation Range", unit(recipe, "controlLimits.meanCalculationRangeRot", "Rot")),
                        pair("Calculation Pass", unit(recipe, "controlLimits.calculationPassRot", "Rot")),
                        pair("Empty Punch No.", text(recipe, "controlLimits.emptyPunchNo"))));
                heading(pdf, "Pressure Data");
                PdfPTable pressure = table("Section", "Mean (kN)", "SD (%)", "Min (kN) [Punch]", "Max (kN) [Punch]");
                for (String[] p : List.of(pair("prePressure", "Pre Pressure"), pair("mainPressure", "Main Pressure"))) {
                    Document value = child(child(details, "pressureData"), p[0]);
                    row(pressure, p[1], text(value, "meanKn"), text(value, "sdPercent"),
                            text(value, "minKn") + " [#" + text(value, "minPunchNo") + "]",
                            text(value, "maxKn") + " [#" + text(value, "maxPunchNo") + "]");
                }
                row(pressure, "Filling Depth Adjustments", "Increase: " + text(details, "pressureData.fillingDepthAdjustments.increaseTimes"),
                        "Decrease: " + text(details, "pressureData.fillingDepthAdjustments.decreaseTimes"), "-", "-");
                pdf.add(pressure);

                pdf.newPage();
                header(pdf, logo, batchNo, lot.lotNo(), "PRODUCTION REPORT - OPERATIONS & TABLET DATA (2/2)", generatedAt);
                heading(pdf, "Operation Value");
                Document op = child(details, "operationValues");
                List<String[]> operations = new ArrayList<>(List.of(
                        pair("Disk Speed", unit(op, "diskSpeedRpm", "RPM")), pair("Capacity", unit(op, "capacityTabsPerHour", "Tabs/hour")),
                        pair("Feeder Status", text(op, "feeder.status")), pair("Feeder Speed", unit(op, "feeder.speedRpm", "RPM"))));
                for (String[] p : List.of(pair("prePressure", "Pre Pressure"), pair("mainPressure", "Main Pressure"))) {
                    operations.add(pair(p[1] + " Thickness", unit(op, p[0] + ".thicknessMm", "mm")));
                    operations.add(pair(p[1] + " Lower Punch Position", unit(op, p[0] + ".lowerPunchPositionMm", "mm")));
                    operations.add(pair(p[1] + " Penetration Depth", unit(op, p[0] + ".penetrationDepthMm", "mm")));
                }
                operations.addAll(List.of(pair("Filling Depth", unit(op, "fillingDepthMm", "mm")), pair("Current Cam", text(op, "currentCam")),
                        pair("Main Air Pressure", unit(op, "mainAirPressureKpa", "kPa")), pair("Hydraulic Pressure", unit(op, "hydraulicPressureMpa", "MPa"))));
                for (String[] oil : List.of(pair("upperPunchS1", "Oil S1 Remaining"), pair("lowerPunchS2", "Oil S2 Remaining"), pair("lowerHeadS3", "Oil S3 Remaining")))
                    operations.add(pair(oil[1], unit(op, "lubricationRemainingMin." + oil[0], "Min")));
                for (String[] aux : List.of(pair("powderStatus", "Powder Status"), pair("dustCollector", "Dust Collector"), pair("initialReject", "Initial Reject"), pair("buzzer", "Buzzer")))
                    operations.add(pair(aux[1], text(op, "auxiliaryStatus." + aux[0])));
                fields(pdf, operations);
                heading(pdf, "Tightness");
                PdfPTable tightness = table("Section", "Average (kN)", "SD (%)", "Max (kN)", "Max Punch No.");
                for (String[] t : List.of(pair("upperPunch", "Upper Punch"), pair("lowerPunch", "Lower Punch"), pair("ejectionForce", "Ejection Force"))) {
                    Document value = child(child(details, "tightness"), t[0]);
                    row(tightness, t[1], text(value, "averageKn"), text(value, "sdPercent"), text(value, "maxKn"), text(value, "maxPunch"));
                }
                pdf.add(tightness);
                heading(pdf, "Tablet Checker");
                PdfPTable checker = table("Measurement", "Average", "Max", "Min", "SD (%)");
                for (String[] t : List.of(pair("weightMg", "Weight (mg)"), pair("thicknessMm", "Thickness (mm)"), pair("diameterMm", "Diameter (mm)"), pair("hardnessN", "Hardness (N)"))) {
                    Document value = child(child(details, "tabletChecker"), t[0]);
                    row(checker, t[1], text(value, "average"), text(value, "max"), text(value, "min"), text(value, "sdPercent"));
                }
                pdf.add(checker);
                heading(pdf, "Tablet Data & Production Counters");
                PdfPTable tablets = table("Metric", "Count (Tabs)", "Source Details");
                row(tablets, "Total Counter", text(counters, "totalCounter"), "-");
                row(tablets, "A.W.C. Counter", text(counters, "awcCounter"), "-");
                for (String key : List.of("hep", "lep", "good"))
                    row(tablets, key.toUpperCase(Locale.ROOT), text(counters, key + ".count"), text(counters, key + ".raw"));
                pdf.add(tablets);
                heading(pdf, "Lot QA Approval");
                PdfPTable approvals = table("Lot", "Status", "QA Approver", "QA Approved At");
                Document approval = approval(lot, equipmentCode);
                row(approvals, lot.lotNo(), lot.status(), text(approval, "approvedBy"), text(approval, "approvedAt"));
                pdf.add(approvals);
                fields(pdf, List.of(pair("Source Operator", text(details, "signatures.operatorName")),
                        pair("Source Report Date", text(details, "signatures.reportDate"))));
            }
            addHistories(pdf, logo, batchNo, lots, generatedAt);
            pdf.newPage();
            header(pdf, logo, batchNo, "ALL LOTS", "WORKFLOW CHANGES SUMMARY", generatedAt);
            heading(pdf, "Consolidated Derived Lots Summary");
            PdfPTable overview = table("Lot", "Station", "Source Report Time", "QA Status", "QA Approver", "QA Approved At");
            for (BatchPdfGeneratorService.CompressionLot lot : lots) {
                Document details = child(lot.sample(), "compression_details");
                Document qa = approval(lot, equipmentCode);
                row(overview, lot.lotNo(), text(details, "batchInfo.stationNo"), text(details, "metadata.reportTimestamp"),
                        lot.status(), text(qa, "approvedBy"), text(qa, "approvedAt"));
            }
            pdf.add(overview);
            heading(pdf, "Workflow Changes Summary");
            PdfPTable workflow = table("Lot", "Action", "Previous / New Status", "User / Role", "Timestamp", "Remarks");
            int transitions = 0;
            for (BatchPdfGeneratorService.CompressionLot lot : lots) {
                for (Document event : lot.history()) {
                    row(workflow, lot.lotNo(), first(event, "actionName", "actionCode"),
                            text(event, "previousStatus") + " / " + text(event, "newStatus"),
                            first(event, "performerName", "performedBy") + " / " + first(event, "performerRole", "userRole"),
                            text(event, "timestamp"), first(event, "comments", "justification"));
                    transitions++;
                }
            }
            if (transitions == 0) row(workflow, "ALL LOTS", "No workflow changes recorded", "-", "-", "-", "-");
            pdf.add(workflow);
            heading(pdf, "Controlled Print Summary");
            fields(pdf, List.of(pair("Approval Status", "QA APPROVED - ALL " + lots.size() + " LOTS"),
                    pair("Generated / Downloaded By", actor == null || actor.isBlank() ? "-" : actor),
                    pair("Role", role == null || role.isBlank() ? "-" : role), pair("Generated At (UTC)", generatedAt.toString())));
            PdfPTable prints = table("Copy / Event", "Lot / Scope", "Printed / Downloaded By", "Timestamp", "Reason");
            for (Document print : printHistory) {
                String copy = text(print, "printCount");
                row(prints, "-".equals(copy) ? first(print, "action", "actionCode") : "Copy #" + copy,
                        text(print, "lotNo"), first(print, "userName", "performedBy", "userId"),
                        first(print, "timestamp", "createdAt"), first(print, "reason", "comments"));
            }
            if (printHistory.isEmpty()) row(prints, "No controlled print events recorded", "CONSOLIDATED", "-", "-", "-");
            pdf.add(prints);
            pdf.close();
            return out.toByteArray();
        } catch (DocumentException | java.io.IOException ex) {
            throw new BusinessException("Consolidated compression PDF generation failed: " + ex.getMessage(), "COMPRESSION_PDF_FAILED");
        }
    }

    private void addHistories(com.lowagie.text.Document pdf, byte[] logo, String batchNo,
                              List<BatchPdfGeneratorService.CompressionLot> lots, Instant generatedAt) throws DocumentException, java.io.IOException {
        for (String[] section : List.of(pair("alarm_history", "ALARM HISTORY"), pair("operation_history", "OPERATING HISTORY / AUDIT TRAIL"),
                pair("login_history", "USER LOGIN / LOGOUT HISTORY"))) {
            pdf.newPage();
            header(pdf, logo, batchNo, "ALL LOTS", section[1], generatedAt);
            heading(pdf, section[1]);
            Map<String, Document> events = new LinkedHashMap<>();
            // Lots are iterated in lot order and each periodic report repeats earlier events,
            // so the first lot whose report contains an event is the lot it occurred in.
            Map<String, String> owningLot = new LinkedHashMap<>();
            for (BatchPdfGeneratorService.CompressionLot lot : lots) {
                Object embedded = child(lot.sample(), "compression_details").get(section[0]);
                if (!(embedded instanceof List<?> values)) continue;
                for (Object value : values) {
                    if (!(value instanceof Document event)) continue;
                    String eventBatch = first(event, "batchNo", "batchNumber", "bNo");
                    if (!"-".equals(eventBatch) && !batchNo.equalsIgnoreCase(eventBatch)) continue;
                    Map<String, Object> identity = new TreeMap<>(event);
                    for (String key : List.of("_id", "auditId", "alarmId", "loginId", "no", "ingestedAt")) identity.remove(key);
                    String id = identity.toString();
                    events.putIfAbsent(id, event);
                    owningLot.putIfAbsent(id, lot.lotNo());
                }
            }
            boolean login = "login_history".equals(section[0]);
            boolean audit = "operation_history".equals(section[0]);
            PdfPTable table = login ? table("Lot", "Station", "Timestamp", "User", "Login / Logout", "Remarks")
                    : audit ? table("Lot", "Station", "Timestamp", "Action", "Previous", "New", "Remarks")
                    : table("Lot", "Station", "Timestamp", "Alarm", "Severity / Remarks");
            List<Map.Entry<String, Document>> ordered = new ArrayList<>(events.entrySet());
            ordered.sort(Comparator.comparing(entry -> first(entry.getValue(), "timestamp", "time", "eventAt", "dt", "occurred_time")));
            for (Map.Entry<String, Document> entry : ordered) {
                Document event = entry.getValue();
                String attribution = first(event, "derivedLotNo", "lotNo");
                if ("-".equals(attribution)) attribution = owningLot.get(entry.getKey());
                String station = first(event, "stationNo", "station");
                String time = first(event, "timestamp", "time", "eventAt", "dt", "occurred_time");
                if (login) row(table, attribution, station, time, first(event, "userId", "user_id", "u", "userName", "operatorName"),
                        first(event, "action", "act", "eventType"), first(event, "remark", "rem", "remarks"));
                else if (audit) row(table, attribution, station, time, first(event, "action", "description", "message"),
                        first(event, "previousValue", "previous"), first(event, "newValue", "new"), first(event, "remark", "comments"));
                else row(table, attribution, station, time, first(event, "alarmName", "alarm_name", "message", "description"),
                            first(event, "severity", "remark"));
            }
            if (events.isEmpty()) {
                String[] empty = new String[table.getNumberOfColumns()];
                Arrays.fill(empty, "-");
                empty[0] = "No records available for this batch";
                row(table, empty);
            }
            pdf.add(table);
        }
    }

    static Document approval(BatchPdfGeneratorService.CompressionLot lot, String equipmentCode) {
        Document approval = new Document();
        if (lot.summary().get("stages") instanceof List<?> stages) {
            for (Object value : stages) {
                if (value instanceof Document stage && (equipmentCode.equalsIgnoreCase(text(stage, "equipmentCode"))
                        || equipmentCode.equalsIgnoreCase(text(stage, "equipmentId")))) approval = new Document(child(stage, "approval"));
            }
        }
        for (Document event : lot.history()) {
            if (List.of("APPROVED", "QA_APPROVED").contains(text(event, "newStatus").toUpperCase(Locale.ROOT))) {
                if ("-".equals(text(approval, "approvedBy"))) approval.put("approvedBy", first(event, "performedBy", "performerName"));
                if ("-".equals(text(approval, "approvedAt"))) approval.put("approvedAt", event.get("timestamp"));
            }
        }
        return approval;
    }

    private void header(com.lowagie.text.Document pdf, byte[] logo, String batch, String lot, String title, Instant time) throws DocumentException, java.io.IOException {
        PdfPTable header = new PdfPTable(new float[]{18, 57, 25});
        header.setWidthPercentage(100);
        PdfPCell imageCell = new PdfPCell();
        imageCell.setBorder(Rectangle.NO_BORDER);
        if (logo != null && logo.length > 0) {
            Image image = Image.getInstance(logo);
            image.scaleToFit(70, 34);
            imageCell.addElement(image);
        }
        header.addCell(imageCell);
        PdfPCell titleCell = new PdfPCell(new Phrase("AUROBINDO PHARMA LTD\nSEJONG TABLET PRESS (COMPRESSION MACHINE)\n" + title
                + "\nBatch " + batch + " / " + lot, LABEL));
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        header.addCell(titleCell);
        PdfPCell status = new PdfPCell(new Phrase("QA APPROVED\nCONSOLIDATED\nGenerated (UTC):\n" + time, VALUE));
        status.setBorder(Rectangle.NO_BORDER);
        header.addCell(status);
        pdf.add(header);
    }

    private void heading(com.lowagie.text.Document pdf, String text) throws DocumentException {
        Paragraph p = new Paragraph(text, LABEL);
        p.setSpacingBefore(6);
        p.setSpacingAfter(3);
        pdf.add(p);
    }

    private static PdfPTable table(String... headers) {
        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        table.setSpacingAfter(5);
        for (String header : headers) table.addCell(cell(header, true));
        return table;
    }

    private static PdfPCell cell(String text, boolean label) {
        PdfPCell cell = new PdfPCell(new Phrase(text, label ? LABEL : VALUE));
        cell.setBorderColor(new Color(226, 232, 240));
        cell.setPadding(3);
        if (label) cell.setBackgroundColor(new Color(248, 250, 252));
        return cell;
    }

    private static void row(PdfPTable table, String... values) {
        for (String value : values) table.addCell(cell(value, false));
    }

    private void fields(com.lowagie.text.Document pdf, List<String[]> fields) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{23, 27, 23, 27});
        table.setWidthPercentage(100);
        table.setSpacingAfter(4);
        for (String[] field : fields) {
            table.addCell(cell(field[0], true));
            table.addCell(cell(field[1], false));
        }
        if (fields.size() % 2 != 0) {
            table.addCell(cell("", true));
            table.addCell(cell("", false));
        }
        pdf.add(table);
    }

    private static String[] pair(String key, String value) { return new String[]{key, value}; }
    private static Document child(Document parent, String key) {
        return parent != null && parent.get(key) instanceof Document d ? d : new Document();
    }

    static String text(Document doc, String path) {
        Object value = doc;
        for (String part : path.split("\\.")) value = value instanceof Document d ? d.get(part) : null;
        if (value == null || String.valueOf(value).isBlank()) return "-";
        return value instanceof Date date ? date.toInstant().toString() : String.valueOf(value);
    }

    private static String first(Document doc, String... paths) {
        for (String path : paths) {
            String value = text(doc, path);
            if (!"-".equals(value)) return value;
        }
        return "-";
    }

    private static String unit(Document doc, String path, String unit) {
        String value = text(doc, path);
        return "-".equals(value) ? "-" : value + " " + unit;
    }
}
