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
        List<Document> validDocuments = new ArrayList<>();
        List<Document> authUserDocuments = new ArrayList<>();
        List<Document> credentialDocuments = new ArrayList<>();
        int totalRows = 0;

        try (Reader reader = new InputStreamReader(csvInputStream, StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).build())) {

            List<String> expectedHeaders = csvTemplateService.getHeaders(type);
            Map<String, Integer> headerMap = parser.getHeaderMap();

            // Validate header presence
            for (String h : expectedHeaders) {
                if (!headerMap.containsKey(h)) {
                    errors.add(Map.of(
                            "rowNumber", 0,
                            "column", h,
                            "error", "Missing required header column: " + h
                    ));
                }
            }

            if (!errors.isEmpty()) {
                return buildResult(type, mode, 0, 0, errors.size(), errors, "Header validation failed");
            }

            Set<String> seenIdentifiers = new HashSet<>();
            int rowNum = 1; // 1-indexed data rows
            Date now = Date.from(Instant.now());

            for (CSVRecord record : parser) {
                totalRows++;
                rowNum++;
                List<String> rowErrors = validateRow(type, record, seenIdentifiers, tenantId);

                if (!rowErrors.isEmpty()) {
                    for (String err : rowErrors) {
                        errors.add(Map.of(
                                "rowNumber", rowNum,
                                "column", err.split(":")[0],
                                "error", err
                        ));
                    }
                } else {
                    Document doc = buildDocument(type, record, tenantId, performedBy, now);
                    validDocuments.add(doc);

                    // For USER type, also build auth_users and credentials documents
                    if (type == BulkType.USER) {
                        String userId = doc.getString("userId");
                        String username = doc.getString("username");
                        String email = doc.getString("email");
                        String rawPassword = record.isSet("initialPassword") && !record.get("initialPassword").isBlank()
                                ? record.get("initialPassword")
                                : "Password@123";

                        Document authUser = new Document();
                        authUser.put("userId", userId);
                        authUser.put("username", username);
                        authUser.put("email", email);
                        authUser.put("status", "ACTIVE");
                        authUser.put("isLocked", false);
                        authUser.put("failedAttempts", 0);
                        authUser.put("createdAt", now);
                        authUser.put("updatedAt", now);
                        authUserDocuments.add(authUser);

                        Document credDoc = new Document();
                        credDoc.put("userId", userId);
                        credDoc.put("email", email);
                        credDoc.put("passwordHash", PasswordEncoderConfig.encode(rawPassword));
                        credDoc.put("mustChangePassword", false);
                        credDoc.put("passwordUpdatedAt", now);
                        credDoc.put("updatedAt", now);
                        credDoc.put("createdAt", now);
                        credentialDocuments.add(credDoc);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse CSV: {}", e.getMessage(), e);
            throw new BusinessException("Failed to parse CSV file: " + e.getMessage());
        }

        // If validation errors exist, do NOT write to database
        if (!errors.isEmpty()) {
            return buildResult(type, mode, totalRows, 0, errors.size(), errors, "Validation failed for " + errors.size() + " records");
        }

        // Execute Database Persist
        int savedCount = 0;
        try {
            String collectionName = resolveCollectionName(type);

            if (mode == BulkMode.TRUNCATE_AND_LOAD) {
                // Safely clear tenant-scoped collection
                Query deleteQuery = new Query();
                if (tenantId != null && !tenantId.isBlank() && type != BulkType.TENANT) {
                    deleteQuery.addCriteria(Criteria.where("tenantId").is(tenantId));
                }
                mongoTemplate.remove(deleteQuery, collectionName);
                log.info("Truncated collection {} for tenant {}", collectionName, tenantId);
            }

            int currentMaxTrackSuffix = 0;
            if (type == BulkType.USER) {
                currentMaxTrackSuffix = getMaxNumericSuffix("mdm_user_profiles", "userTrackId", "USR-");
            }

            for (Document doc : validDocuments) {
                String primaryKey = resolvePrimaryKey(type);
                Object pkValue = doc.get(primaryKey);

                Query upsertQuery = new Query(Criteria.where(primaryKey).is(pkValue));
                if (tenantId != null && !tenantId.isBlank() && doc.containsKey("tenantId")) {
                    upsertQuery.addCriteria(Criteria.where("tenantId").is(doc.getString("tenantId")));
                }

                Document existing = mongoTemplate.findOne(upsertQuery, Document.class, collectionName);
                if (existing != null) {
                    doc.put("_id", existing.get("_id"));
                    doc.put("createdAt", existing.get("createdAt"));
                    if (existing.containsKey("userTrackId") && existing.get("userTrackId") != null && !String.valueOf(existing.get("userTrackId")).isBlank()) {
                        doc.put("userTrackId", existing.get("userTrackId"));
                    } else if (type == BulkType.USER) {
                        currentMaxTrackSuffix++;
                        doc.put("userTrackId", "USR-" + String.format("%04d", currentMaxTrackSuffix));
                    }
                } else if (type == BulkType.USER) {
                    if (!doc.containsKey("userTrackId") || doc.getString("userTrackId") == null || doc.getString("userTrackId").isBlank()) {
                        currentMaxTrackSuffix++;
                        doc.put("userTrackId", "USR-" + String.format("%04d", currentMaxTrackSuffix));
                    }
                }
                mongoTemplate.save(doc, collectionName);
                savedCount++;
            }

            // Save secondary documents if user type
            if (type == BulkType.USER) {
                for (Document authUser : authUserDocuments) {
                    Query authQ = new Query(Criteria.where("userId").is(authUser.getString("userId")));
                    Document existingAuth = mongoTemplate.findOne(authQ, Document.class, "auth_users");
                    if (existingAuth != null) {
                        authUser.put("_id", existingAuth.get("_id"));
                    }
                    mongoTemplate.save(authUser, "auth_users");
                }

                for (Document cred : credentialDocuments) {
                    Query credQ = new Query(Criteria.where("userId").is(cred.getString("userId")));
                    Document existingCred = mongoTemplate.findOne(credQ, Document.class, "mdm_user_auth_credentials");
                    if (existingCred != null) {
                        cred.put("_id", existingCred.get("_id"));
                    }
                    mongoTemplate.save(cred, "mdm_user_auth_credentials");
                }
            }

            // Record audit log
            emitBulkAudit(type, mode, tenantId, performedBy, totalRows, savedCount);

        } catch (Exception e) {
            log.error("Database persistence failed during bulk upload: {}", e.getMessage(), e);
            throw new BusinessException("Database persistence failed: " + e.getMessage());
        }

        return buildResult(type, mode, totalRows, savedCount, 0, Collections.emptyList(), "Successfully processed " + savedCount + " records");
    }

    private List<String> validateRow(BulkType type, CSVRecord record, Set<String> seenIds, String tenantId) {
        List<String> errs = new ArrayList<>();

        switch (type) {
            case TENANT:
                require(record, "tenantId", errs);
                require(record, "tenantCode", errs);
                require(record, "tenantName", errs);
                checkUnique(record, "tenantId", seenIds, errs);
                break;
            case PLANT:
                require(record, "plantId", errs);
                require(record, "plantCode", errs);
                require(record, "plantName", errs);
                checkUnique(record, "plantId", seenIds, errs);
                break;
            case DEPARTMENT:
                require(record, "departmentId", errs);
                require(record, "departmentCode", errs);
                require(record, "departmentName", errs);
                checkUnique(record, "departmentId", seenIds, errs);
                break;
            case ROLE:
                require(record, "roleId", errs);
                require(record, "roleCode", errs);
                require(record, "roleName", errs);
                checkUnique(record, "roleId", seenIds, errs);
                break;
            case USER:
                require(record, "userId", errs);
                require(record, "username", errs);
                require(record, "email", errs);
                validateEmail(record, "email", errs);
                checkUnique(record, "userId", seenIds, errs);
                checkUnique(record, "email", seenIds, errs);
                break;
            case USER_GROUP:
                require(record, "groupId", errs);
                require(record, "groupCode", errs);
                require(record, "groupName", errs);
                checkUnique(record, "groupId", seenIds, errs);
                break;
            case USER_GROUP_ASSIGNMENT:
                require(record, "userId", errs);
                require(record, "groupId", errs);
                break;
            case IIOT_MASTER:
                require(record, "equipmentId", errs);
                require(record, "equipmentCode", errs);
                require(record, "equipmentName", errs);
                checkUnique(record, "equipmentId", seenIds, errs);
                break;
        }

        return errs;
    }

    private void require(CSVRecord r, String col, List<String> errs) {
        if (!r.isSet(col) || r.get(col) == null || r.get(col).trim().isEmpty()) {
            errs.add(col + ": Mandatory field '" + col + "' cannot be empty");
        }
    }

    private void validateEmail(CSVRecord r, String col, List<String> errs) {
        if (r.isSet(col)) {
            String val = r.get(col);
            if (val != null && !val.trim().isEmpty() && !val.contains("@")) {
                errs.add(col + ": Invalid email address format '" + val + "'");
            }
        }
    }

    private void checkUnique(CSVRecord r, String col, Set<String> seenIds, List<String> errs) {
        if (r.isSet(col)) {
            String val = r.get(col).trim().toUpperCase(Locale.ROOT);
            if (!val.isEmpty()) {
                if (seenIds.contains(val)) {
                    errs.add(col + ": Duplicate identifier '" + r.get(col) + "' found within the CSV file");
                } else {
                    seenIds.add(val);
                }
            }
        }
    }

    private Document buildDocument(BulkType type, CSVRecord r, String tenantId, String performedBy, Date now) {
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

        if (!doc.containsKey("tenantId") && tenantId != null && !tenantId.isBlank()) {
            doc.put("tenantId", tenantId);
        }
        if (!doc.containsKey("isActive")) {
            doc.put("isActive", true);
        }

        if (type == BulkType.USER) {
            if (!doc.containsKey("isBlocked")) {
                doc.put("isBlocked", false);
            }
            if (!doc.containsKey("isExternal")) {
                doc.put("isExternal", false);
            }
            if (!doc.containsKey("userType")) {
                doc.put("userType", "Internal");
            }
            if (!doc.containsKey("lifecycleStatus")) {
                doc.put("lifecycleStatus", "Active");
            }
        } else if (type == BulkType.DEPARTMENT) {
            if (!doc.containsKey("plantId") || doc.getString("plantId") == null || doc.getString("plantId").isBlank()) {
                doc.put("plantId", "PLNT-0001");
            }
            if (!doc.containsKey("path") || doc.getString("path") == null || doc.getString("path").isBlank()) {
                String plant = doc.getString("plantId");
                String code = doc.getString("departmentCode");
                doc.put("path", "/" + plant + "/" + (code != null ? code : "DEPT"));
            }
            if (!doc.containsKey("description")) {
                doc.put("description", doc.getOrDefault("departmentName", "Department"));
            }
        } else if (type == BulkType.PLANT) {
            if (!doc.containsKey("type")) doc.put("type", "Manufacturing");
            if (!doc.containsKey("blockId")) doc.put("blockId", "BLK-0001");
            if (!doc.containsKey("blockName")) doc.put("blockName", "Main Block");
            if (!doc.containsKey("areaId")) doc.put("areaId", "AREA-0001");
            if (!doc.containsKey("areaName")) doc.put("areaName", "Production Area");
            if (!doc.containsKey("roomId")) doc.put("roomId", "ROOM-0001");
            if (!doc.containsKey("roomName")) doc.put("roomName", "Production Room");
        } else if (type == BulkType.USER_GROUP_ASSIGNMENT) {
            if (!doc.containsKey("assignmentId")) {
                doc.put("assignmentId", "UGA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
            }
            if (!doc.containsKey("assignedBy")) {
                doc.put("assignedBy", performedBy != null ? performedBy : "SUPER_ADMIN");
            }
            if (!doc.containsKey("assignedAt")) {
                doc.put("assignedAt", now);
            }
        } else if (type == BulkType.IIOT_MASTER) {
            if (!doc.containsKey("plantId")) doc.put("plantId", "PLNT-0001");
            if (!doc.containsKey("status")) doc.put("status", "ACTIVE");
        }

        doc.put("createdAt", now);
        doc.put("updatedAt", now);
        doc.put("createdBy", performedBy != null ? performedBy : "BULK_UPLOAD");

        return doc;
    }

    private int getMaxNumericSuffix(String collectionName, String fieldName, String prefix) {
        int max = 0;
        try {
            for (Document document : mongoTemplate.findAll(Document.class, collectionName)) {
                Object rawValue = document.get(fieldName);
                if (rawValue == null) continue;
                String val = String.valueOf(rawValue);
                if (val.startsWith(prefix)) {
                    String suffix = val.substring(prefix.length());
                    if (suffix.matches("\\d+")) {
                        try {
                            int num = Integer.parseInt(suffix);
                            if (num > max) max = num;
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to find max suffix for {}.{}: {}", collectionName, fieldName, e.getMessage());
        }
        return max;
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
            case USER_GROUP_ASSIGNMENT -> "userId";
            case IIOT_MASTER -> "equipmentId";
        };
    }

    private void emitBulkAudit(BulkType type, BulkMode mode, String tenantId, String performedBy, int total, int saved) {
        try {
            Document audit = new Document();
            audit.put("tenantId", tenantId != null ? tenantId : "TNT-0001");
            audit.put("action", "BULK_UPLOAD_" + type.name());
            audit.put("mode", mode.name());
            audit.put("entity", type.name());
            audit.put("totalRows", total);
            audit.put("processedRows", saved);
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
            int success,
            int errorCount,
            List<Map<String, Object>> errors,
            String message) {

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("type", type.name());
        res.put("mode", mode.name());
        res.put("totalRows", total);
        res.put("successCount", success);
        res.put("errorCount", errorCount);
        res.put("status", errorCount == 0 ? "SUCCESS" : "FAILED");
        res.put("message", message);
        res.put("errors", errors);
        res.put("timestamp", Instant.now().toString());
        return res;
    }
}
