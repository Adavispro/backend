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
        // TENANT
        HEADERS.put(BulkType.TENANT, List.of("tenantId", "tenantCode", "tenantName", "status", "contactEmail", "contactPhone", "region"));
        SAMPLE_ROWS.put(BulkType.TENANT, List.of(
                List.of("TNT-0002", "ACME_PHARMA", "Acme Pharmaceuticals Ltd", "ACTIVE", "admin@acmepharma.com", "+1-555-0199", "North America")
        ));

        // PLANT
        HEADERS.put(BulkType.PLANT, List.of("plantId", "tenantId", "plantCode", "plantName", "type", "isActive", "blockId", "blockName", "areaId", "areaName", "roomId", "roomName"));
        SAMPLE_ROWS.put(BulkType.PLANT, List.of(
                List.of("PLNT-0002", "TNT-0001", "BLR-01", "Formulation Plant - Bangalore", "Manufacturing", "true", "BLK-0002", "Granulation Block B", "AREA-0002", "Granulation Area 2", "ROOM-0002", "Granulation Suite 102")
        ));

        // DEPARTMENT
        HEADERS.put(BulkType.DEPARTMENT, List.of("departmentId", "tenantId", "departmentCode", "departmentName", "description", "isActive"));
        SAMPLE_ROWS.put(BulkType.DEPARTMENT, List.of(
                List.of("DEP-0003", "TNT-0001", "QA-DEPT", "Quality Assurance", "QA and Compliance Department", "true"),
                List.of("DEP-0004", "TNT-0001", "QC-DEPT", "Quality Control", "Analytical Testing & QC", "true")
        ));

        // ROLE
        HEADERS.put(BulkType.ROLE, List.of("roleId", "tenantId", "roleCode", "roleName", "description", "isActive"));
        SAMPLE_ROWS.put(BulkType.ROLE, List.of(
                List.of("ROLE-0010", "TNT-0001", "QA_REVIEWER", "QA Reviewer", "Quality Assurance Batch Reviewer", "true")
        ));

        // USER
        HEADERS.put(BulkType.USER, List.of("userId", "tenantId", "username", "email", "firstName", "lastName", "departmentId", "designation", "initialPassword", "title", "userType", "empId", "isActive"));
        SAMPLE_ROWS.put(BulkType.USER, List.of(
                List.of("OPERATOR_NEW_03", "TNT-0001", "operator_new_03", "op03@adavis.com", "John", "Doe", "DEP-0002", "Production Operator", "Password@123", "Mr.", "Internal", "EMP-00107", "true")
        ));

        // USER_GROUP
        HEADERS.put(BulkType.USER_GROUP, List.of("groupId", "tenantId", "groupCode", "groupName", "description", "isActive"));
        SAMPLE_ROWS.put(BulkType.USER_GROUP, List.of(
                List.of("GRP-0013", "TNT-0001", "QA_REVIEWERS", "QA Reviewers", "Quality assurance and batch reviewers", "true")
        ));

        // USER_GROUP_ASSIGNMENT
        HEADERS.put(BulkType.USER_GROUP_ASSIGNMENT, List.of("userId", "groupId", "assignedBy", "reason", "isActive"));
        SAMPLE_ROWS.put(BulkType.USER_GROUP_ASSIGNMENT, List.of(
                List.of("OPERATOR_NEW_03", "GRP-0011", "SUPER_ADMIN", "Operator assignment", "true")
        ));

        // IIOT_MASTER
        HEADERS.put(BulkType.IIOT_MASTER, List.of("equipmentId", "tenantId", "plantId", "equipmentCode", "equipmentName", "equipmentType", "lineId", "roomId", "status", "isActive"));
        SAMPLE_ROWS.put(BulkType.IIOT_MASTER, List.of(
                List.of("G8RMG", "TNT-0001", "PLNT-0001", "G8RMG", "Rapid Mixer Granulator G8", "RMG", "G8", "ROOM-0001", "ACTIVE", "true"),
                List.of("G8FBD", "TNT-0001", "PLNT-0001", "G8FBD", "Fluid Bed Dryer G8", "FBD", "G8", "ROOM-0001", "ACTIVE", "true")
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
