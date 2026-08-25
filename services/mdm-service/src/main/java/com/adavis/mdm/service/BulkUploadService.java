package com.adavis.mdm.service;

import com.adavis.common.exception.BusinessException;
import com.adavis.mdm.service.CsvTemplateService.BulkType;
import com.adavis.security.PasswordEncoderConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkUploadService {

    private final MongoTemplate mongoTemplate;
    private final CsvTemplateService csvTemplateService;
    private final BusinessIdGeneratorService businessIdGeneratorService;

    public enum BulkMode {
        UPDATE,
        TRUNCATE_AND_LOAD
    }

    public Map<String, Object> processBulkUpload(
            String typeStr,
            String modeStr,
            String tenantId,
            String performedBy,
            InputStream csvInputStream) {

        BulkType type;
        try {
            type = BulkType.valueOf(typeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Unsupported bulk upload type: " + typeStr);
        }

        BulkMode mode = BulkMode.UPDATE;
        if (modeStr != null && !modeStr.isBlank()) {
            try {
                mode = BulkMode.valueOf(modeStr.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Unsupported bulk upload mode: " + modeStr + ". Valid modes: UPDATE, TRUNCATE_AND_LOAD");
            }
        }

        List<Map<String, Object>> errors = new ArrayList<>();
        List<CSVRecord> validRecords = new ArrayList<>();
        int totalRows = 0;

        try (Reader reader = new InputStreamReader(csvInputStream, StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build())) {

            List<String> expectedHeaders = csvTemplateService.getHeaders(type);
            Map<String, Integer> headerMap = parser.getHeaderMap();

            // Validate business header presence (case-insensitive)
            for (String h : expectedHeaders) {
                boolean found = false;
                for (String key : headerMap.keySet()) {
                    if (key.equalsIgnoreCase(h)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    errors.add(Map.of(
                            "rowNumber", 0,
                            "column", h,
                            "error", "Missing required header column: " + h
                    ));
                }
            }

            if (!errors.isEmpty()) {
                return buildResult(type, mode, 0, 0, 0, errors.size(), errors, Collections.emptyList(), "Header validation failed");
            }

            Set<String> seenBusinessKeys = new HashSet<>();
            int rowNum = 1;

            for (CSVRecord record : parser) {
                totalRows++;
                rowNum++;
                List<String> rowErrors = validateRow(type, record, seenBusinessKeys, tenantId);

                if (!rowErrors.isEmpty()) {
                    for (String err : rowErrors) {
                        errors.add(Map.of(
                                "rowNumber", rowNum,
                                "column", err.contains(":") ? err.split(":")[0] : "DATA",
                                "error", err
                        ));
                    }
                } else {
                    validRecords.add(record);
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse CSV: {}", e.getMessage(), e);
            throw new BusinessException("Failed to parse CSV file: " + e.getMessage());
        }

        // If validation errors exist, do NOT write to database
        if (!errors.isEmpty()) {
            return buildResult(type, mode, totalRows, 0, 0, errors.size(), errors, Collections.emptyList(), "Validation failed for " + errors.size() + " records");
        }

        // Execute Database Persist with automatic sequence generation
        int createdCount = 0;
        int updatedCount = 0;
        List<Map<String, Object>> processedItems = new ArrayList<>();
        Date now = Date.from(Instant.now());

        try {
            String collectionName = resolveCollectionName(type);

            if (mode == BulkMode.TRUNCATE_AND_LOAD) {
                Query deleteQuery = new Query();
                if (tenantId != null && !tenantId.isBlank() && type != BulkType.TENANT) {
                    deleteQuery.addCriteria(Criteria.where("tenantId").is(tenantId));
                }
                mongoTemplate.remove(deleteQuery, collectionName);
                log.info("Truncated collection {} for tenant {}", collectionName, tenantId);
            }

            int rowIdx = 1;
            for (CSVRecord record : validRecords) {
                rowIdx++;
                String effectiveTenantId = resolveEffectiveTenantId(record, tenantId);
                String businessKey = extractBusinessKey(type, record);

                Document existing = findExistingDocument(type, record, effectiveTenantId, collectionName);
                boolean isNew = (existing == null);

                Document doc = buildDocument(type, record, effectiveTenantId, performedBy, now, existing);

                if (isNew) {
                    // Automatically generate authoritative sequence ID
                    assignAutoGeneratedId(type, doc, effectiveTenantId);
                    mongoTemplate.insert(doc, collectionName);
                    createdCount++;
                    String generatedId = extractDocumentId(type, doc);
                    processedItems.add(Map.of(
                            "rowNumber", rowIdx,
                            "businessKey", businessKey,
                            "action", "CREATED",
                            "id", generatedId != null ? generatedId : "-"
                    ));
                } else {
                    // Update existing record preserving existing internal ID and _id
                    preserveExistingIdentities(type, doc, existing);
                    mongoTemplate.save(doc, collectionName);
                    updatedCount++;
                    String existingId = extractDocumentId(type, doc);
                    processedItems.add(Map.of(
                            "rowNumber", rowIdx,
                            "businessKey", businessKey,
                            "action", "UPDATED",
                            "id", existingId != null ? existingId : "-"
                    ));
                }

                // Handle secondary auth documents for USER
                if (type == BulkType.USER) {
                    saveUserAuthCredentials(doc, record, now, isNew);
                }
            }

            // Record audit log
            emitBulkAudit(type, mode, tenantId, performedBy, totalRows, createdCount, updatedCount);

        } catch (Exception e) {
            log.error("Database persistence failed during bulk upload: {}", e.getMessage(), e);
            throw new BusinessException("Database persistence failed: " + e.getMessage());
        }

        int successCount = createdCount + updatedCount;
        return buildResult(type, mode, totalRows, createdCount, updatedCount, 0, Collections.emptyList(), processedItems,
                "Successfully processed " + successCount + " records (Created: " + createdCount + ", Updated: " + updatedCount + ")");
    }

    private List<String> validateRow(BulkType type, CSVRecord record, Set<String> seenKeys, String tenantId) {
        List<String> errs = new ArrayList<>();

        switch (type) {
            case TENANT:
                require(record, "tenantCode", errs);
                require(record, "tenantName", errs);
                checkUnique(record, "tenantCode", seenKeys, errs);
                break;
            case PLANT:
                require(record, "plantCode", errs);
                require(record, "plantName", errs);
                checkUnique(record, "plantCode", seenKeys, errs);
                break;
            case DEPARTMENT:
                require(record, "departmentCode", errs);
                require(record, "departmentName", errs);
                checkUnique(record, "departmentCode", seenKeys, errs);
                break;
            case ROLE:
                require(record, "roleCode", errs);
                require(record, "roleName", errs);
                checkUnique(record, "roleCode", seenKeys, errs);
                break;
            case USER:
                require(record, "username", errs);
                require(record, "email", errs);
                validateEmail(record, "email", errs);
                checkUnique(record, "username", seenKeys, errs);
                checkUnique(record, "email", seenKeys, errs);
                break;
            case USER_GROUP:
                require(record, "groupCode", errs);
                require(record, "groupName", errs);
                checkUnique(record, "groupCode", seenKeys, errs);
                break;
            case USER_GROUP_ASSIGNMENT:
                require(record, "username", errs);
                require(record, "groupCode", errs);
                String compositeKey = getField(record, "username") + "::" + getField(record, "groupCode");
                if (seenKeys.contains(compositeKey.toUpperCase(Locale.ROOT))) {
                    errs.add("username: Duplicate assignment for user '" + getField(record, "username") + "' and group '" + getField(record, "groupCode") + "' found in file");
                } else {
                    seenKeys.add(compositeKey.toUpperCase(Locale.ROOT));
                }
                break;
            case IIOT_MASTER:
                require(record, "equipmentCode", errs);
                require(record, "equipmentName", errs);
                checkUnique(record, "equipmentCode", seenKeys, errs);
                break;
        }

        return errs;
    }

    private void require(CSVRecord r, String col, List<String> errs) {
        String val = getField(r, col);
        if (val == null || val.trim().isEmpty()) {
            errs.add(col + ": Mandatory field '" + col + "' cannot be empty");
        }
    }

    private void validateEmail(CSVRecord r, String col, List<String> errs) {
        String val = getField(r, col);
        if (val != null && !val.trim().isEmpty() && !val.contains("@")) {
            errs.add(col + ": Invalid email address format '" + val + "'");
        }
    }

    private void checkUnique(CSVRecord r, String col, Set<String> seenKeys, List<String> errs) {
        String val = getField(r, col);
        if (val != null && !val.trim().isEmpty()) {
            String normalized = val.trim().toUpperCase(Locale.ROOT);
            if (seenKeys.contains(normalized)) {
                errs.add(col + ": Duplicate identifier '" + val + "' found within the CSV file");
            } else {
                seenKeys.add(normalized);
            }
        }
    }

    private String getField(CSVRecord r, String col) {
        if (r.isSet(col)) {
            return r.get(col);
        }
        for (String header : r.getParser().getHeaderNames()) {
            if (header.equalsIgnoreCase(col) && r.isSet(header)) {
                return r.get(header);
            }
        }
        return null;
    }

    private String resolveEffectiveTenantId(CSVRecord r, String tenantId) {
        String fromRow = getField(r, "tenantId");
        if (fromRow != null && !fromRow.isBlank()) {
            return fromRow.trim();
        }
        return (tenantId != null && !tenantId.isBlank()) ? tenantId.trim() : "TNT-0001";
    }

    private String extractBusinessKey(BulkType type, CSVRecord r) {
        return switch (type) {
            case TENANT -> getField(r, "tenantCode");
            case PLANT -> getField(r, "plantCode");
            case DEPARTMENT -> getField(r, "departmentCode");
            case ROLE -> getField(r, "roleCode");
            case USER -> getField(r, "username");
            case USER_GROUP -> getField(r, "groupCode");
            case USER_GROUP_ASSIGNMENT -> getField(r, "username") + " -> " + getField(r, "groupCode");
            case IIOT_MASTER -> getField(r, "equipmentCode");
        };
    }

    private Document findExistingDocument(BulkType type, CSVRecord r, String tenantId, String collectionName) {
        Query q = new Query();
        switch (type) {
            case TENANT -> {
                String code = getField(r, "tenantCode");
                q.addCriteria(new Criteria().orOperator(
                        Criteria.where("companyCode").is(code),
                        Criteria.where("tenantCode").is(code)
                ));
            }
            case PLANT -> {
                String code = getField(r, "plantCode");
                q.addCriteria(Criteria.where("plantCode").is(code));
                if (tenantId != null && !tenantId.isBlank()) {
                    q.addCriteria(Criteria.where("tenantId").is(tenantId));
                }
            }
            case DEPARTMENT -> {
                String code = getField(r, "departmentCode");
                q.addCriteria(Criteria.where("departmentCode").is(code));
                if (tenantId != null && !tenantId.isBlank()) {
                    q.addCriteria(Criteria.where("tenantId").is(tenantId));
                }
            }
            case ROLE -> {
                String code = getField(r, "roleCode");
                q.addCriteria(Criteria.where("roleCode").is(code));
                if (tenantId != null && !tenantId.isBlank()) {
                    q.addCriteria(Criteria.where("tenantId").is(tenantId));
                }
            }
            case USER -> {
                String username = getField(r, "username");
                String email = getField(r, "email");
                List<Criteria> userCriteria = new ArrayList<>();
                if (username != null && !username.isBlank()) {
                    userCriteria.add(Criteria.where("username").is(username));
                    userCriteria.add(Criteria.where("userId").is(username.toUpperCase(Locale.ROOT)));
                }
                if (email != null && !email.isBlank()) {
                    userCriteria.add(Criteria.where("email").is(email));
                }
                if (!userCriteria.isEmpty()) {
                    q.addCriteria(new Criteria().orOperator(userCriteria.toArray(new Criteria[0])));
                }
            }
            case USER_GROUP -> {
                String code = getField(r, "groupCode");
                q.addCriteria(Criteria.where("groupCode").is(code));
                if (tenantId != null && !tenantId.isBlank()) {
                    q.addCriteria(Criteria.where("tenantId").is(tenantId));
                }
            }
            case USER_GROUP_ASSIGNMENT -> {
                String user = getField(r, "username");
                String group = getField(r, "groupCode");
                q.addCriteria(Criteria.where("userId").is(user != null ? user.toUpperCase(Locale.ROOT) : ""));
                q.addCriteria(Criteria.where("groupId").is(group));
            }
            case IIOT_MASTER -> {
                String code = getField(r, "equipmentCode");
                q.addCriteria(Criteria.where("equipmentCode").is(code));
            }
        }

        return mongoTemplate.findOne(q, Document.class, collectionName);
    }

    private Document buildDocument(BulkType type, CSVRecord r, String tenantId, String performedBy, Date now, Document existing) {
        Document doc = new Document();

        for (String header : r.getParser().getHeaderNames()) {
            if (r.isSet(header)) {
                String val = r.get(header);
                if ("isActive".equalsIgnoreCase(header) || "isBlocked".equalsIgnoreCase(header) || "isExternal".equalsIgnoreCase(header)) {
                    doc.put(header, Boolean.parseBoolean(val));
                } else {
                    doc.put(header, val);
                }
            }
        }

        // Set common tenant, plant, and timestamps
        if (!doc.containsKey("tenantId") && tenantId != null && !tenantId.isBlank()) {
            doc.put("tenantId", tenantId);
        }
        if (!doc.containsKey("isActive")) {
            doc.put("isActive", true);
        }

        switch (type) {
            case TENANT -> {
                String code = getField(r, "tenantCode");
                String name = getField(r, "tenantName");
                doc.put("companyCode", code);
                doc.put("tenantCode", code);
                doc.put("companyName", name);
                doc.put("tenantName", name);
            }
            case PLANT -> {
                if (!doc.containsKey("type")) doc.put("type", "Manufacturing");
            }
            case DEPARTMENT -> {
                String plantCode = getField(r, "plantCode");
                if (plantCode != null && !plantCode.isBlank()) {
                    doc.put("plantId", plantCode.startsWith("PLNT-") ? plantCode : "PLNT-0001");
                } else if (!doc.containsKey("plantId")) {
                    doc.put("plantId", "PLNT-0001");
                }
                String code = getField(r, "departmentCode");
                String plant = doc.getString("plantId");
                if (!doc.containsKey("path") || doc.getString("path") == null || doc.getString("path").isBlank()) {
                    doc.put("path", "/" + plant + "/" + (code != null ? code : "DEPT"));
                }
                if (!doc.containsKey("description")) {
                    doc.put("description", doc.getOrDefault("departmentName", "Department"));
                }
                String name = getField(r, "departmentName");
                doc.put("departmentName", name);
                doc.put("name", name);
            }
            case ROLE -> {
                String code = getField(r, "roleCode");
                String name = getField(r, "roleName");
                doc.put("roleCode", code);
                doc.put("roleName", name);
                doc.put("name", name);
                if (!doc.containsKey("description")) {
                    doc.put("description", name);
                }
            }
            case USER -> {
                String username = getField(r, "username");
                if (username != null) {
                    doc.put("username", username.trim());
                    doc.put("userId", username.trim().toUpperCase(Locale.ROOT));
                }
                if (!doc.containsKey("isBlocked")) doc.put("isBlocked", false);
                if (!doc.containsKey("isExternal")) doc.put("isExternal", false);
                if (!doc.containsKey("userType")) doc.put("userType", "Internal");
                if (!doc.containsKey("lifecycleStatus")) doc.put("lifecycleStatus", "Active");
            }
            case USER_GROUP -> {
                String code = getField(r, "groupCode");
                String name = getField(r, "groupName");
                doc.put("groupCode", code);
                doc.put("groupName", name);
                doc.put("name", name);
                if (!doc.containsKey("description")) {
                    doc.put("description", name);
                }
            }
            case USER_GROUP_ASSIGNMENT -> {
                String user = getField(r, "username");
                String group = getField(r, "groupCode");
                doc.put("userId", user != null ? user.trim().toUpperCase(Locale.ROOT) : "");
                doc.put("groupId", group != null ? group.trim() : "");
                if (!doc.containsKey("assignedBy")) {
                    doc.put("assignedBy", performedBy != null ? performedBy : "SUPER_ADMIN");
                }
                if (!doc.containsKey("assignedAt")) {
                    doc.put("assignedAt", now);
                }
            }
            case IIOT_MASTER -> {
                String code = getField(r, "equipmentCode");
                doc.put("equipmentId", code);
                if (!doc.containsKey("plantId")) doc.put("plantId", "PLNT-0001");
                if (!doc.containsKey("status")) doc.put("status", "ACTIVE");
            }
        }

        if (existing != null) {
            doc.put("_id", existing.get("_id"));
            doc.put("createdAt", existing.get("createdAt"));
        } else {
            doc.put("createdAt", now);
        }
        doc.put("updatedAt", now);
        doc.put("createdBy", performedBy != null ? performedBy : "BULK_UPLOAD");

        return doc;
    }

    private void assignAutoGeneratedId(BulkType type, Document doc, String tenantId) {
        switch (type) {
            case TENANT -> {
                String tenantIdVal = businessIdGeneratorService.nextId("mdm_tenants", "tenantId", "TNT-", 4);
                doc.put("tenantId", tenantIdVal);
            }
            case PLANT -> {
                String plantIdVal = businessIdGeneratorService.nextId("mdm_plants", "plantId", "PLNT-", 4);
                doc.put("plantId", plantIdVal);
                if (doc.containsKey("blockCode") || doc.containsKey("blockName")) {
                    doc.put("blockId", businessIdGeneratorService.nextId("mdm_blocks", "blockId", "BLK-", 4));
                }
                if (doc.containsKey("areaCode") || doc.containsKey("areaName")) {
                    doc.put("areaId", businessIdGeneratorService.nextId("mdm_areas", "areaId", "AREA-", 4));
                }
                if (doc.containsKey("roomCode") || doc.containsKey("roomName")) {
                    doc.put("roomId", businessIdGeneratorService.nextId("mdm_rooms", "roomId", "ROOM-", 4));
                }
            }
            case DEPARTMENT -> {
                String deptIdVal = businessIdGeneratorService.nextId("mdm_departments", "departmentId", "DEP-", 4);
                doc.put("departmentId", deptIdVal);
            }
            case ROLE -> {
                String roleIdVal = businessIdGeneratorService.nextId("mdm_roles", "roleId", "ROLE-", 4);
                doc.put("roleId", roleIdVal);
            }
            case USER -> {
                String userTrackIdVal = businessIdGeneratorService.nextId("mdm_user_profiles", "userTrackId", "USR-", 4);
                doc.put("userTrackId", userTrackIdVal);
            }
            case USER_GROUP -> {
                String groupIdVal = businessIdGeneratorService.nextId("mdm_user_groups", "groupId", "GRP-", 4);
                doc.put("groupId", groupIdVal);
            }
            case USER_GROUP_ASSIGNMENT -> {
                String asgIdVal = businessIdGeneratorService.nextId("mdm_user_context_assignments", "assignmentId", "ASG-", 4);
                doc.put("assignmentId", asgIdVal);
            }
            case IIOT_MASTER -> {
                if (!doc.containsKey("equipmentId") || doc.getString("equipmentId") == null) {
                    doc.put("equipmentId", doc.getString("equipmentCode"));
                }
            }
        }
    }

    private void preserveExistingIdentities(BulkType type, Document doc, Document existing) {
        String pk = resolvePrimaryKey(type);
        if (existing.containsKey(pk)) {
            doc.put(pk, existing.get(pk));
        }
        if (type == BulkType.USER && existing.containsKey("userTrackId")) {
            doc.put("userTrackId", existing.get("userTrackId"));
        }
        if (existing.containsKey("tenantId") && !doc.containsKey("tenantId")) {
            doc.put("tenantId", existing.get("tenantId"));
        }
    }

    private String extractDocumentId(BulkType type, Document doc) {
        return switch (type) {
            case TENANT -> doc.getString("tenantId");
            case PLANT -> doc.getString("plantId");
            case DEPARTMENT -> doc.getString("departmentId");
            case ROLE -> doc.getString("roleId");
            case USER -> doc.getString("userTrackId") != null ? doc.getString("userTrackId") + " (" + doc.getString("userId") + ")" : doc.getString("userId");
            case USER_GROUP -> doc.getString("groupId");
            case USER_GROUP_ASSIGNMENT -> doc.getString("assignmentId");
            case IIOT_MASTER -> doc.getString("equipmentId");
        };
    }

    private void saveUserAuthCredentials(Document doc, CSVRecord record, Date now, boolean isNew) {
        String userId = doc.getString("userId");
        String username = doc.getString("username");
        String email = doc.getString("email");
        String rawPassword = getField(record, "initialPassword");
        if (rawPassword == null || rawPassword.isBlank()) {
            rawPassword = "Password@123";
        }

        // Save auth_users
        Query authQ = new Query(Criteria.where("userId").is(userId));
        Document existingAuth = mongoTemplate.findOne(authQ, Document.class, "auth_users");
        Document authUser = existingAuth != null ? existingAuth : new Document();
        authUser.put("userId", userId);
        authUser.put("username", username);
        authUser.put("email", email);
        authUser.put("status", "ACTIVE");
        authUser.put("isLocked", false);
        authUser.put("failedAttempts", 0);
        authUser.put("updatedAt", now);
        if (isNew || existingAuth == null) {
            authUser.put("createdAt", now);
            mongoTemplate.insert(authUser, "auth_users");
        } else {
            mongoTemplate.save(authUser, "auth_users");
        }

        // Save mdm_user_auth_credentials
        Query credQ = new Query(Criteria.where("userId").is(userId));
        Document existingCred = mongoTemplate.findOne(credQ, Document.class, "mdm_user_auth_credentials");
        Document credDoc = existingCred != null ? existingCred : new Document();
        credDoc.put("userId", userId);
        credDoc.put("email", email);
        credDoc.put("passwordHash", PasswordEncoderConfig.encode(rawPassword));
        credDoc.put("mustChangePassword", false);
        credDoc.put("passwordUpdatedAt", now);
        credDoc.put("updatedAt", now);
        if (isNew || existingCred == null) {
            credDoc.put("createdAt", now);
            mongoTemplate.insert(credDoc, "mdm_user_auth_credentials");
        } else {
            mongoTemplate.save(credDoc, "mdm_user_auth_credentials");
        }
    }

    private String resolveCollectionName(BulkType type) {
        return switch (type) {
            case TENANT -> "mdm_tenants";
            case PLANT -> "mdm_plants";
            case DEPARTMENT -> "mdm_departments";
            case ROLE -> "mdm_roles";
            case USER -> "mdm_user_profiles";
            case USER_GROUP -> "mdm_user_groups";
            case USER_GROUP_ASSIGNMENT -> "mdm_user_assignments_to_user_groups";
            case IIOT_MASTER -> "iiot_equipment_master";
        };
    }

    private String resolvePrimaryKey(BulkType type) {
        return switch (type) {
            case TENANT -> "tenantId";
            case PLANT -> "plantId";
            case DEPARTMENT -> "departmentId";
            case ROLE -> "roleId";
            case USER -> "userId";
            case USER_GROUP -> "groupId";
            case USER_GROUP_ASSIGNMENT -> "assignmentId";
            case IIOT_MASTER -> "equipmentId";
        };
    }

    private void emitBulkAudit(BulkType type, BulkMode mode, String tenantId, String performedBy, int total, int created, int updated) {
        try {
            Document audit = new Document();
            audit.put("tenantId", tenantId != null ? tenantId : "TNT-0001");
            audit.put("action", "BULK_UPLOAD_" + type.name());
            audit.put("mode", mode.name());
            audit.put("entity", type.name());
            audit.put("totalRows", total);
            audit.put("createdRows", created);
            audit.put("updatedRows", updated);
            audit.put("processedRows", created + updated);
            audit.put("performedBy", performedBy != null ? performedBy : "SUPER_ADMIN");
            audit.put("timestamp", Date.from(Instant.now()));
            mongoTemplate.insert(audit, "mdm_audit_trails");
        } catch (Exception e) {
            log.error("Failed to emit bulk upload audit trail: {}", e.getMessage());
        }
    }

    private Map<String, Object> buildResult(
            BulkType type,
            BulkMode mode,
            int total,
            int created,
            int updated,
            int errorCount,
            List<Map<String, Object>> errors,
            List<Map<String, Object>> items,
            String message) {

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("type", type.name());
        res.put("mode", mode.name());
        res.put("totalRows", total);
        res.put("createdCount", created);
        res.put("updatedCount", updated);
        res.put("successCount", created + updated);
        res.put("errorCount", errorCount);
        res.put("status", errorCount == 0 ? "SUCCESS" : "FAILED");
        res.put("message", message);
        res.put("items", items);
        res.put("errors", errors);
        res.put("timestamp", Instant.now().toString());
        return res;
    }
}
