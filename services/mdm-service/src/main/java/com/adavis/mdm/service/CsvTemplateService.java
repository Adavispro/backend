package com.adavis.mdm.service;

import com.adavis.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.util.*;

@Slf4j
@Service
public class CsvTemplateService {

    public enum BulkType {
        TENANT,
        PLANT,
        DEPARTMENT,
        ROLE,
        USER,
        USER_GROUP,
        USER_GROUP_ASSIGNMENT,
        IIOT_MASTER
    }

    private static final Map<BulkType, List<String>> HEADERS = new LinkedHashMap<>();
    private static final Map<BulkType, List<List<String>>> SAMPLE_ROWS = new LinkedHashMap<>();

    static {
        // TENANT - Business fields only (tenantId is auto-generated)
        HEADERS.put(BulkType.TENANT, List.of("tenantCode", "tenantName", "status", "contactEmail", "contactPhone", "region", "domain"));
        SAMPLE_ROWS.put(BulkType.TENANT, List.of(
                List.of("ACME_PHARMA", "Acme Pharmaceuticals Ltd", "ACTIVE", "admin@acmepharma.com", "+1-555-0199", "North America", "https://acmepharma.com")
        ));

        // PLANT - Business fields only (plantId, blockId, areaId, roomId are auto-generated)
        HEADERS.put(BulkType.PLANT, List.of("plantCode", "plantName", "type", "isActive", "blockCode", "blockName", "areaCode", "areaName", "roomCode", "roomName"));
        SAMPLE_ROWS.put(BulkType.PLANT, List.of(
                List.of("BLR-01", "Formulation Plant - Bangalore", "Manufacturing", "true", "BLK-0002", "Granulation Block B", "AREA-0002", "Granulation Area 2", "ROOM-0002", "Granulation Suite 102")
        ));

        // DEPARTMENT - Business fields only (departmentId is auto-generated)
        HEADERS.put(BulkType.DEPARTMENT, List.of("departmentCode", "departmentName", "description", "plantCode", "parentDepartmentCode", "isActive"));
        SAMPLE_ROWS.put(BulkType.DEPARTMENT, List.of(
                List.of("QA-DEPT", "Quality Assurance", "QA and Compliance Department", "HYD-01", "", "true"),
                List.of("QC-DEPT", "Quality Control", "Analytical Testing & QC", "HYD-01", "QA-DEPT", "true")
        ));

        // ROLE - Business fields only (roleId is auto-generated)
        HEADERS.put(BulkType.ROLE, List.of("roleCode", "roleName", "description", "isActive"));
        SAMPLE_ROWS.put(BulkType.ROLE, List.of(
                List.of("QA_REVIEWER", "QA Reviewer", "Quality Assurance Batch Reviewer", "true")
        ));

        // USER - Business fields only (userId and userTrackId are auto-generated / derived)
        HEADERS.put(BulkType.USER, List.of("username", "email", "firstName", "lastName", "departmentCode", "designation", "initialPassword", "title", "userType", "empId", "isActive"));
        SAMPLE_ROWS.put(BulkType.USER, List.of(
                List.of("operator_new_03", "op03@adavis.com", "John", "Doe", "PROD", "Production Operator", "Password@123", "Mr.", "Internal", "EMP-00107", "true")
        ));

        // USER_GROUP - Business fields only (groupId is auto-generated)
        HEADERS.put(BulkType.USER_GROUP, List.of("groupCode", "groupName", "description", "isActive"));
        SAMPLE_ROWS.put(BulkType.USER_GROUP, List.of(
                List.of("QA_REVIEWERS", "QA Reviewers", "Quality assurance and batch reviewers", "true")
        ));

        // USER_GROUP_ASSIGNMENT - Business references (assignmentId is auto-generated)
        HEADERS.put(BulkType.USER_GROUP_ASSIGNMENT, List.of("username", "groupCode", "assignedBy", "reason", "isActive"));
        SAMPLE_ROWS.put(BulkType.USER_GROUP_ASSIGNMENT, List.of(
                List.of("operator_new_03", "QA_REVIEWERS", "SUPER_ADMIN", "Operator assignment", "true")
        ));

        // IIOT_MASTER - Business fields only (equipmentId is set to equipmentCode / auto-generated)
        HEADERS.put(BulkType.IIOT_MASTER, List.of("equipmentCode", "equipmentName", "equipmentType", "lineId", "roomCode", "status", "isActive"));
        SAMPLE_ROWS.put(BulkType.IIOT_MASTER, List.of(
                List.of("G8RMG", "Rapid Mixer Granulator G8", "RMG", "G8", "ROOM-0001", "ACTIVE", "true"),
                List.of("G8FBD", "Fluid Bed Dryer G8", "FBD", "G8", "ROOM-0001", "ACTIVE", "true")
        ));
    }

    public String generateTemplateCsv(String typeStr) {
        BulkType type;
        try {
            type = BulkType.valueOf(typeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Unsupported bulk upload type: " + typeStr + ". Valid types: " + Arrays.toString(BulkType.values()));
        }

        List<String> headers = HEADERS.get(type);
        List<List<String>> sampleRows = SAMPLE_ROWS.get(type);

        StringWriter sw = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setHeader(headers.toArray(new String[0])).build())) {
            if (sampleRows != null) {
                for (List<String> row : sampleRows) {
                    printer.printRecord(row);
                }
            }
            printer.flush();
            return sw.toString();
        } catch (Exception e) {
            throw new BusinessException("Failed to generate CSV template: " + e.getMessage());
        }
    }

    public List<String> getHeaders(BulkType type) {
        return HEADERS.getOrDefault(type, Collections.emptyList());
    }
}
