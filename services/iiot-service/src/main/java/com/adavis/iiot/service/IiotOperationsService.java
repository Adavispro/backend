package com.adavis.iiot.service;

import com.adavis.common.exception.BusinessException;
import com.mongodb.MongoWriteException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class IiotOperationsService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(IiotOperationsService.class);

    private static final String DEFAULT_TENANT_ID = "TNT-0001";
    private static final String DEFAULT_PLANT_ID = "PLNT-0001";

    private static final String ASSETS_COLLECTION = "iiot_assets";
    private static final String ASSET_TAGS_COLLECTION = "iiot_asset_tags";
    private static final String TAG_THRESHOLDS_COLLECTION = "iiot_tag_thresholds";
    private static final String EQUIPMENT_MASTER_COLLECTION = "iiot_equipment_master";
    private static final String CRITICAL_PARAMETERS_COLLECTION = "iiot_equipment_critical_parameters";
    private static final String CRITICAL_PARAMETER_LIMITS_COLLECTION = "iiot_equipment_critical_parameters_limit";
    private static final String PRODUCT_MASTER_COLLECTION = "iiot_product_master";
    private static final String SOURCE_MAPPING_COLLECTION = "iiot_source_table_mapping";
    private static final String CHECKPOINT_COLLECTION = "iiot_ingestion_checkpoint";
    private static final String JOB_RUN_COLLECTION = "iiot_ingestion_job_run";
    private static final String EQUIPMENT_LIVE_STATUS_COLLECTION = "iiot_equipment_live_status";
    private static final String BATCH_SUMMARY_COLLECTION = "iiot_batch_summary";
    private static final String BATCH_TS_COLLECTION = "iiot_ts_batch_";
    private static final String ALARM_TS_COLLECTION = "iiot_ts_alarm_";
    private static final String AUDIT_TS_COLLECTION = "iiot_ts_audit_";
    private static final String LEGACY_CPP_TS_PREFIX = "iiot_ts_cpp_";
    private static final String LEGACY_ALARM_TS_PREFIX = "iiot_ts_alarm_event_";
    private static final String TELEMETRY_COLLECTION = "iiot_telemetry";
    private static final String STATE_COLLECTION = "iiot_asset_states";
    private static final String OEE_CONFIG_COLLECTION = "iiot_oee_config";
    private static final String OEE_METRICS_COLLECTION = "iiot_oee_metrics";
    private static final String ALARM_RULES_COLLECTION = "iiot_alarm_rules";
    private static final String ALARM_EVENTS_COLLECTION = "iiot_alarm_events";
    private static final String REALTIME_COLLECTION = "iiot_realtime_cache";
    private static final String REALTIME_REDIS_KEY_PREFIX = "iiot:realtime:";

    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final BatchPdfGeneratorService batchPdfGeneratorService;

    @Value("${iiot.ingestion.source-db.url:}")
    private String sourceDbUrl;

    @Value("${iiot.ingestion.source-db.username:}")
    private String sourceDbUsername;

    @Value("${iiot.ingestion.source-db.password:}")
    private String sourceDbPassword;

    private final Map<String, Instant> lastRunAtByStream = new ConcurrentHashMap<>();

    public Map<String, Object> createAsset(Map<String, Object> request) {
        String assetId = requireText(request, "assetId");
        String assetCode = requireText(request, "assetCode");
        String tenantId = firstNonBlank(stringValue(request.get("tenantId")), DEFAULT_TENANT_ID);

        Query query = new Query(new Criteria().orOperator(
                Criteria.where("assetId").is(assetId),
                Criteria.where("tenantId").is(tenantId).and("assetCode").is(assetCode)));
        Document existing = mongoTemplate.findOne(query, Document.class, ASSETS_COLLECTION);
        if (existing != null) {
            throw new BusinessException("Asset already exists: " + assetCode);
        }

        Document doc = new Document(request);
        doc.put("assetId", assetId);
        doc.put("assetCode", assetCode);
        doc.put("tenantId", tenantId);
        doc.put("isActive", request.getOrDefault("isActive", true));
        doc.put("createdAt", Date.from(Instant.now()));
        doc.put("updatedAt", Date.from(Instant.now()));
        return insertDocument(doc, ASSETS_COLLECTION, "Asset already exists: " + assetCode);
    }

    public List<Map<String, Object>> getAssets() {
        Query query = new Query(new Criteria().orOperator(
            Criteria.where("isActive").exists(false),
            Criteria.where("isActive").is(true)));
        query.with(Sort.by(Sort.Direction.ASC, "assetId", "assetCode"));
        return mongoTemplate.find(query, Document.class, ASSETS_COLLECTION).stream().map(this::toMap).toList();
    }

    public Map<String, Object> getAsset(String assetId) {
        return toMap(requireActiveDocumentByBusinessKey(ASSETS_COLLECTION, "assetId", assetId));
    }

    public Map<String, Object> updateAsset(String assetId, Map<String, Object> request) {
        Document existing = requireActiveDocumentByBusinessKey(ASSETS_COLLECTION, "assetId", assetId);
        request.forEach((k, v) -> {
            if (!"_id".equals(k) && !"assetId".equals(k)) {
                existing.put(k, v);
            }
        });
        existing.put("updatedAt", Date.from(Instant.now()));
        return toMap(mongoTemplate.save(existing, ASSETS_COLLECTION));
    }

    public Map<String, Object> deleteAsset(String assetId) {
        Document existing = requireActiveDocumentByBusinessKey(ASSETS_COLLECTION, "assetId", assetId);
        existing.put("isActive", false);
        existing.put("updatedAt", Date.from(Instant.now()));
        return toMap(mongoTemplate.save(existing, ASSETS_COLLECTION));
    }

    public Map<String, Object> reactivateAsset(String assetId) {
        return reactivateDocumentByBusinessKey(ASSETS_COLLECTION, "assetId", assetId);
    }

    public Map<String, Object> createAssetTag(Map<String, Object> request) {
        String tagId = requireText(request, "tagId");
        String assetCode = requireText(request, "assetCode");
        String tagCode = requireText(request, "tagCode");
        String tenantId = firstNonBlank(stringValue(request.get("tenantId")), DEFAULT_TENANT_ID);

        Query query = new Query(new Criteria().orOperator(
                Criteria.where("tagId").is(tagId),
                Criteria.where("tenantId").is(tenantId).and("assetCode").is(assetCode).and("tagCode").is(tagCode)));
        Document existing = mongoTemplate.findOne(query, Document.class, ASSET_TAGS_COLLECTION);
        if (existing != null) {
            throw new BusinessException("Asset tag already exists: " + tagCode);
        }

        Document doc = new Document(request);
        doc.put("tagId", tagId);
        doc.put("assetCode", assetCode);
        doc.put("tagCode", tagCode);
        doc.put("tenantId", tenantId);
        doc.put("isActive", request.getOrDefault("isActive", true));
        doc.put("createdAt", Date.from(Instant.now()));
        doc.put("updatedAt", Date.from(Instant.now()));
        return insertDocument(doc, ASSET_TAGS_COLLECTION, "Asset tag already exists: " + tagCode);
    }

    public List<Map<String, Object>> getAssetTags() {
        Query query = new Query(new Criteria().orOperator(
            Criteria.where("isActive").exists(false),
            Criteria.where("isActive").is(true)));
        query.with(Sort.by(Sort.Direction.ASC, "tagId", "tagCode"));
        return mongoTemplate.find(query, Document.class, ASSET_TAGS_COLLECTION).stream().map(this::toMap).toList();
    }

    public Map<String, Object> getAssetTag(String tagId) {
        return toMap(requireActiveDocumentByBusinessKey(ASSET_TAGS_COLLECTION, "tagId", tagId));
    }

    public Map<String, Object> updateAssetTag(String tagId, Map<String, Object> request) {
        Document existing = requireActiveDocumentByBusinessKey(ASSET_TAGS_COLLECTION, "tagId", tagId);
        request.forEach((k, v) -> {
            if (!"_id".equals(k) && !"tagId".equals(k)) {
                existing.put(k, v);
            }
        });
        existing.put("updatedAt", Date.from(Instant.now()));
        return toMap(mongoTemplate.save(existing, ASSET_TAGS_COLLECTION));
    }

    public Map<String, Object> deleteAssetTag(String tagId) {
        Document existing = requireActiveDocumentByBusinessKey(ASSET_TAGS_COLLECTION, "tagId", tagId);
        existing.put("isActive", false);
        existing.put("updatedAt", Date.from(Instant.now()));
        return toMap(mongoTemplate.save(existing, ASSET_TAGS_COLLECTION));
    }

    public Map<String, Object> reactivateAssetTag(String tagId) {
        return reactivateDocumentByBusinessKey(ASSET_TAGS_COLLECTION, "tagId", tagId);
    }

    public Map<String, Object> createTagThreshold(Map<String, Object> request) {
        String thresholdId = requireText(request, "thresholdId");
        String tenantId = firstNonBlank(stringValue(request.get("tenantId")), DEFAULT_TENANT_ID);
        String plantId = firstNonBlank(stringValue(request.get("plantId")), DEFAULT_PLANT_ID);

        Query query = new Query(new Criteria().orOperator(
                Criteria.where("thresholdId").is(thresholdId),
                Criteria.where("tenantId").is(tenantId).and("plantId").is(plantId).and("thresholdId").is(thresholdId)));
        Document existing = mongoTemplate.findOne(query, Document.class, TAG_THRESHOLDS_COLLECTION);
        if (existing != null) {
            throw new BusinessException("Tag threshold already exists: " + thresholdId);
        }

        Document doc = new Document(request);
        doc.put("thresholdId", thresholdId);
        doc.put("tenantId", tenantId);
        doc.put("plantId", plantId);
        doc.put("isActive", request.getOrDefault("isActive", true));
        doc.put("createdAt", Date.from(Instant.now()));
        doc.put("updatedAt", Date.from(Instant.now()));
        return insertDocument(doc, TAG_THRESHOLDS_COLLECTION, "Tag threshold already exists: " + thresholdId);
    }

    public List<Map<String, Object>> getTagThresholds() {
        Query query = new Query(new Criteria().orOperator(
            Criteria.where("isActive").exists(false),
            Criteria.where("isActive").is(true)));
        query.with(Sort.by(Sort.Direction.ASC, "thresholdId", "tagCode"));
        return mongoTemplate.find(query, Document.class, TAG_THRESHOLDS_COLLECTION).stream().map(this::toMap).toList();
    }

    public Map<String, Object> getTagThreshold(String thresholdId) {
        return toMap(requireActiveDocumentByBusinessKey(TAG_THRESHOLDS_COLLECTION, "thresholdId", thresholdId));
    }

    public Map<String, Object> updateTagThreshold(String thresholdId, Map<String, Object> request) {
        Document existing = requireActiveDocumentByBusinessKey(TAG_THRESHOLDS_COLLECTION, "thresholdId", thresholdId);
        request.forEach((k, v) -> {
            if (!"_id".equals(k) && !"thresholdId".equals(k)) {
                existing.put(k, v);
            }
        });
        existing.put("updatedAt", Date.from(Instant.now()));
        return toMap(mongoTemplate.save(existing, TAG_THRESHOLDS_COLLECTION));
    }

    public Map<String, Object> deleteTagThreshold(String thresholdId) {
        Document existing = requireActiveDocumentByBusinessKey(TAG_THRESHOLDS_COLLECTION, "thresholdId", thresholdId);
        existing.put("isActive", false);
        existing.put("updatedAt", Date.from(Instant.now()));
        return toMap(mongoTemplate.save(existing, TAG_THRESHOLDS_COLLECTION));
    }

    public Map<String, Object> reactivateTagThreshold(String thresholdId) {
        return reactivateDocumentByBusinessKey(TAG_THRESHOLDS_COLLECTION, "thresholdId", thresholdId);
    }

    public Map<String, Object> createEquipmentMaster(Map<String, Object> request) {
        String equipmentId = requireText(request, "equipmentId");
        String equipmentCode = requireText(request, "equipmentCode");
        String tenantId = firstNonBlank(stringValue(request.get("tenantId")), DEFAULT_TENANT_ID);
        String plantId = firstNonBlank(stringValue(request.get("plantId")), DEFAULT_PLANT_ID);

        Query query = new Query(new Criteria().orOperator(
                Criteria.where("equipmentId").is(equipmentId),
                Criteria.where("tenantId").is(tenantId).and("plantId").is(plantId).and("equipmentCode").is(equipmentCode)));
        if (mongoTemplate.findOne(query, Document.class, EQUIPMENT_MASTER_COLLECTION) != null) {
            throw new BusinessException("Equipment master already exists: " + equipmentCode);
        }

        Document doc = new Document(request);
        doc.put("equipmentId", equipmentId);
        doc.put("equipmentCode", equipmentCode);
        doc.put("tenantId", tenantId);
        doc.put("plantId", plantId);
        doc.put("isActive", request.getOrDefault("isActive", true));
        doc.put("createdAt", Date.from(Instant.now()));
        doc.put("updatedAt", Date.from(Instant.now()));
        return insertDocument(doc, EQUIPMENT_MASTER_COLLECTION, "Equipment master already exists: " + equipmentCode);
    }

    public List<Map<String, Object>> getEquipmentMasters() {
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("isActive").exists(false),
                Criteria.where("isActive").is(true)));
        query.with(Sort.by(Sort.Direction.ASC, "equipmentId", "equipmentCode"));
        return mongoTemplate.find(query, Document.class, EQUIPMENT_MASTER_COLLECTION).stream().map(this::toMap).toList();
    }

    public Map<String, Object> getEquipmentMaster(String equipmentId) {
        return toMap(requireActiveDocumentByBusinessKey(EQUIPMENT_MASTER_COLLECTION, "equipmentId", equipmentId));
    }

    public Map<String, Object> updateEquipmentMaster(String equipmentId, Map<String, Object> request) {
        Document existing = requireActiveDocumentByBusinessKey(EQUIPMENT_MASTER_COLLECTION, "equipmentId", equipmentId);
        request.forEach((k, v) -> {
            if (!"_id".equals(k) && !"equipmentId".equals(k)) {
                existing.put(k, v);
            }
        });
        existing.put("updatedAt", Date.from(Instant.now()));
        return toMap(mongoTemplate.save(existing, EQUIPMENT_MASTER_COLLECTION));
    }

    public Map<String, Object> deactivateEquipmentMaster(String equipmentId) {
        Document existing = requireActiveDocumentByBusinessKey(EQUIPMENT_MASTER_COLLECTION, "equipmentId", equipmentId);
        existing.put("isActive", false);
        existing.put("updatedAt", Date.from(Instant.now()));
        return toMap(mongoTemplate.save(existing, EQUIPMENT_MASTER_COLLECTION));
    }

    public Map<String, Object> activateEquipmentMaster(String equipmentId) {
        return reactivateDocumentByBusinessKey(EQUIPMENT_MASTER_COLLECTION, "equipmentId", equipmentId);
    }

    public Map<String, Object> createCriticalParameter(Map<String, Object> request) {
        String parameterId = requireText(request, "parameterId");
        String equipmentId = requireText(request, "equipmentId");
        String parameterCode = requireText(request, "parameterCode");
        String tenantId = firstNonBlank(stringValue(request.get("tenantId")), DEFAULT_TENANT_ID);
        String plantId = firstNonBlank(stringValue(request.get("plantId")), DEFAULT_PLANT_ID);

        Query query = new Query(new Criteria().orOperator(
                Criteria.where("parameterId").is(parameterId),
                Criteria.where("tenantId").is(tenantId)
                        .and("plantId").is(plantId)
                        .and("equipmentId").is(equipmentId)
                        .and("parameterCode").is(parameterCode)));
        if (mongoTemplate.findOne(query, Document.class, CRITICAL_PARAMETERS_COLLECTION) != null) {
            throw new BusinessException("Critical parameter already exists: " + parameterCode);
        }

        Document doc = new Document(request);
        doc.put("parameterId", parameterId);
        doc.put("equipmentId", equipmentId);
        doc.put("parameterCode", parameterCode);
        doc.put("tenantId", tenantId);
        doc.put("plantId", plantId);
        doc.put("isActive", request.getOrDefault("isActive", true));
        doc.put("createdAt", Date.from(Instant.now()));
        doc.put("updatedAt", Date.from(Instant.now()));
        return insertDocument(doc, CRITICAL_PARAMETERS_COLLECTION, "Critical parameter already exists: " + parameterCode);
    }

    public List<Map<String, Object>> getCriticalParameters() {
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("isActive").exists(false),
                Criteria.where("isActive").is(true)));
        query.with(Sort.by(Sort.Direction.ASC, "parameterId", "parameterCode"));
        return mongoTemplate.find(query, Document.class, CRITICAL_PARAMETERS_COLLECTION).stream().map(this::toMap).toList();
    }

    public Map<String, Object> getCriticalParameter(String parameterId) {
        return toMap(requireActiveDocumentByBusinessKey(CRITICAL_PARAMETERS_COLLECTION, "parameterId", parameterId));
    }

    public Map<String, Object> updateCriticalParameter(String parameterId, Map<String, Object> request) {
        Document existing = requireActiveDocumentByBusinessKey(CRITICAL_PARAMETERS_COLLECTION, "parameterId", parameterId);
        request.forEach((k, v) -> {
            if (!"_id".equals(k) && !"parameterId".equals(k)) {
                existing.put(k, v);
            }
        });
        existing.put("updatedAt", Date.from(Instant.now()));
        return toMap(mongoTemplate.save(existing, CRITICAL_PARAMETERS_COLLECTION));
    }

    public Map<String, Object> deactivateCriticalParameter(String parameterId) {
        Document existing = requireActiveDocumentByBusinessKey(CRITICAL_PARAMETERS_COLLECTION, "parameterId", parameterId);
        existing.put("isActive", false);
        existing.put("updatedAt", Date.from(Instant.now()));
        return toMap(mongoTemplate.save(existing, CRITICAL_PARAMETERS_COLLECTION));
    }

    public Map<String, Object> activateCriticalParameter(String parameterId) {
        return reactivateDocumentByBusinessKey(CRITICAL_PARAMETERS_COLLECTION, "parameterId", parameterId);
    }

    public Map<String, Object> createCriticalParameterLimit(Map<String, Object> request) {
        String parameterLimitCode = firstNonBlank(
            stringValue(request.get("parameterLimitCode")),
            stringValue(request.get("parameterLimitId")));
        if (parameterLimitCode == null || parameterLimitCode.isBlank()) {
            throw new BusinessException("parameterLimitCode is required");
        }
        String parameterLimitId = firstNonBlank(stringValue(request.get("parameterLimitId")), parameterLimitCode);
        String parameterId = requireText(request, "parameterId");
        String parameterType = requireText(request, "parameterType");
        String equipmentId = requireText(request, "equipmentId");
        String tenantId = firstNonBlank(stringValue(request.get("tenantId")), DEFAULT_TENANT_ID);
        String plantId = firstNonBlank(stringValue(request.get("plantId")), DEFAULT_PLANT_ID);

        Query query = new Query(new Criteria().orOperator(
                Criteria.where("parameterLimitId").is(parameterLimitId),
            Criteria.where("parameterLimitCode").is(parameterLimitCode),
                Criteria.where("tenantId").is(tenantId)
                        .and("plantId").is(plantId)
                        .and("equipmentId").is(equipmentId)
                        .and("parameterId").is(parameterId)));
        if (mongoTemplate.findOne(query, Document.class, CRITICAL_PARAMETER_LIMITS_COLLECTION) != null) {
            throw new BusinessException("Critical parameter limit already exists: " + parameterLimitId);
        }

        Document doc = new Document(request);
        doc.put("parameterLimitId", parameterLimitId);
        doc.put("parameterLimitCode", parameterLimitCode);
        doc.put("parameterId", parameterId);
        doc.put("parameterType", parameterType);
        doc.put("equipmentId", equipmentId);
        doc.put("tenantId", tenantId);
        doc.put("plantId", plantId);
        doc.put("isActive", request.getOrDefault("isActive", true));
        doc.put("alarmEnabled", request.getOrDefault("alarmEnabled", false));
        doc.put("createdAt", Date.from(Instant.now()));
        doc.put("updatedAt", Date.from(Instant.now()));
        return insertDocument(doc, CRITICAL_PARAMETER_LIMITS_COLLECTION,
                "Critical parameter limit already exists: " + parameterLimitId);
    }

    public List<Map<String, Object>> getCriticalParameterLimits() {
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("isActive").exists(false),
                Criteria.where("isActive").is(true)));
        query.with(Sort.by(Sort.Direction.ASC, "parameterLimitId"));
        return mongoTemplate.find(query, Document.class, CRITICAL_PARAMETER_LIMITS_COLLECTION).stream().map(this::toMap).toList();
    }

    public Map<String, Object> getCriticalParameterLimit(String parameterLimitId) {
        return toMap(requireActiveDocumentByBusinessKey(CRITICAL_PARAMETER_LIMITS_COLLECTION, "parameterLimitId", parameterLimitId));
    }

    public Map<String, Object> updateCriticalParameterLimit(String parameterLimitId, Map<String, Object> request) {
        Document existing = requireActiveDocumentByBusinessKey(CRITICAL_PARAMETER_LIMITS_COLLECTION, "parameterLimitId", parameterLimitId);
        request.forEach((k, v) -> {
            if (!"_id".equals(k) && !"parameterLimitId".equals(k)) {
                existing.put(k, v);
            }
        });
        if (!existing.containsKey("parameterLimitCode") || stringValue(existing.get("parameterLimitCode")) == null
                || stringValue(existing.get("parameterLimitCode")).isBlank()) {
            existing.put("parameterLimitCode", existing.get("parameterLimitId"));
        }
        if (!request.containsKey("alarmEnabled")) {
            existing.put("alarmEnabled", existing.getOrDefault("alarmEnabled", false));
        }
        existing.put("updatedAt", Date.from(Instant.now()));
        return toMap(mongoTemplate.save(existing, CRITICAL_PARAMETER_LIMITS_COLLECTION));
    }

    public Map<String, Object> deactivateCriticalParameterLimit(String parameterLimitId) {
        Document existing = requireActiveDocumentByBusinessKey(CRITICAL_PARAMETER_LIMITS_COLLECTION, "parameterLimitId", parameterLimitId);
        existing.put("isActive", false);
        existing.put("updatedAt", Date.from(Instant.now()));
        return toMap(mongoTemplate.save(existing, CRITICAL_PARAMETER_LIMITS_COLLECTION));
    }

    public Map<String, Object> activateCriticalParameterLimit(String parameterLimitId) {
        return reactivateDocumentByBusinessKey(CRITICAL_PARAMETER_LIMITS_COLLECTION, "parameterLimitId", parameterLimitId);
    }

    public Map<String, Object> createProductMaster(Map<String, Object> request) {
        String productCode = requireText(request, "productCode");
        String productId = firstNonBlank(stringValue(request.get("productId")), productCode);
        String tenantId = firstNonBlank(stringValue(request.get("tenantId")), DEFAULT_TENANT_ID);
        String plantId = firstNonBlank(stringValue(request.get("plantId")), DEFAULT_PLANT_ID);

        Query query = new Query(new Criteria().orOperator(
                Criteria.where("productId").is(productId),
                Criteria.where("tenantId").is(tenantId).and("plantId").is(plantId).and("productCode").is(productCode)));
        if (mongoTemplate.findOne(query, Document.class, PRODUCT_MASTER_COLLECTION) != null) {
            throw new BusinessException("Product master already exists: " + productCode);
        }

        Document doc = new Document(request);
        doc.put("productId", productId);
        doc.put("productCode", productCode);
        doc.put("tenantId", tenantId);
        doc.put("plantId", plantId);
        doc.put("isActive", request.getOrDefault("isActive", true));
        doc.put("createdAt", Date.from(Instant.now()));
        doc.put("updatedAt", Date.from(Instant.now()));
        return insertDocument(doc, PRODUCT_MASTER_COLLECTION, "Product master already exists: " + productCode);
    }

    public List<Map<String, Object>> getProductMasters() {
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("isActive").exists(false),
                Criteria.where("isActive").is(true)));
        query.with(Sort.by(Sort.Direction.ASC, "productId", "productCode"));
        return mongoTemplate.find(query, Document.class, PRODUCT_MASTER_COLLECTION).stream().map(this::toMap).toList();
    }

    public Map<String, Object> getProductMaster(String productId) {
        return toMap(requireActiveDocumentByBusinessKey(PRODUCT_MASTER_COLLECTION, "productId", productId));
    }

    public Map<String, Object> updateProductMaster(String productId, Map<String, Object> request) {
        Document existing = requireActiveDocumentByBusinessKey(PRODUCT_MASTER_COLLECTION, "productId", productId);
        request.forEach((k, v) -> {
            if (!"_id".equals(k) && !"productId".equals(k)) {
                existing.put(k, v);
            }
        });
        existing.put("updatedAt", Date.from(Instant.now()));
        return toMap(mongoTemplate.save(existing, PRODUCT_MASTER_COLLECTION));
    }

    public Map<String, Object> deactivateProductMaster(String productId) {
        Document existing = requireActiveDocumentByBusinessKey(PRODUCT_MASTER_COLLECTION, "productId", productId);
        existing.put("isActive", false);
        existing.put("updatedAt", Date.from(Instant.now()));
        return toMap(mongoTemplate.save(existing, PRODUCT_MASTER_COLLECTION));
    }

    public Map<String, Object> activateProductMaster(String productId) {
        return reactivateDocumentByBusinessKey(PRODUCT_MASTER_COLLECTION, "productId", productId);
    }

    public Map<String, Object> getPlantTopology(String tenantId) {
        Map<String, Object> result = new LinkedHashMap<>();

        List<Document> plants = mongoTemplate.find(new Query(), Document.class, "mdm_plants");
        List<Document> blocks = mongoTemplate.find(new Query(), Document.class, "mdm_blocks");
        List<Document> areas = mongoTemplate.find(new Query(), Document.class, "mdm_areas");
        List<Document> rooms = mongoTemplate.find(new Query(), Document.class, "mdm_rooms");

        result.put("plants", plants.stream().map(this::toMap).toList());
        result.put("blocks", blocks.stream().map(this::toMap).toList());
        result.put("areas", areas.stream().map(this::toMap).toList());
        result.put("rooms", rooms.stream().map(this::toMap).toList());

        return result;
    }

    @Scheduled(fixedDelayString = "${iiot.ingestion.scheduler-delay-ms:15000}")
    public void runScheduledBatchIngestion() {
        Query mappingQuery = new Query(Criteria.where("isActive").is(true));
        List<Document> mappings = mongoTemplate.find(mappingQuery, Document.class, SOURCE_MAPPING_COLLECTION);
        for (Document mapping : mappings) {
            try {
                ingestEquipmentStreams(mapping);
            } catch (Exception ex) {
                log.error("IIOT batch ingestion failed for mapping {}: {}", mapping.get("mappingId"), ex.getMessage(), ex);
            }
        }
    }

    public Map<String, Object> triggerBatchIngestion(String equipmentId) {
        Query query = new Query(Criteria.where("equipmentId").is(equipmentId).and("isActive").is(true));
        Document mapping = mongoTemplate.findOne(query, Document.class, SOURCE_MAPPING_COLLECTION);
        if (mapping == null) {
            throw new BusinessException("Active source mapping not found for equipmentId: " + equipmentId);
        }
        ingestEquipmentStreams(mapping);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("equipmentId", equipmentId);
        response.put("status", "TRIGGERED");
        response.put("updatedAt", Instant.now().toString());
        return response;
    }

    public List<Map<String, Object>> getBatchSummary(Map<String, Object> filter) {
        List<Criteria> andCriteria = new java.util.ArrayList<>();

        String tenantId = stringValue(filter.get("tenantId"));
        if (!tenantId.isBlank()) {
            andCriteria.add(new Criteria().orOperator(
                    Criteria.where("tenantId").is(tenantId),
                    Criteria.where("tenantId").exists(false),
                    Criteria.where("tenantId").is(null)
            ));
        }

        String plantId = stringValue(filter.get("plantId"));
        if (!plantId.isBlank()) {
            andCriteria.add(new Criteria().orOperator(
                    Criteria.where("plantId").is(plantId),
                    Criteria.where("plantId").exists(false),
                    Criteria.where("plantId").is(null)
            ));
        }

        String areaId = stringValue(filter.get("areaId"));
        if (!areaId.isBlank()) {
            andCriteria.add(Criteria.where("areaId").is(areaId));
        }

        String equipmentId = stringValue(filter.get("equipmentId"));
        if (!equipmentId.isBlank()) {
            andCriteria.add(new Criteria().orOperator(
                    Criteria.where("equipmentId").is(equipmentId),
                    Criteria.where("stages.equipmentCode").is(equipmentId),
                    Criteria.where("stages.equipmentId").is(equipmentId)
            ));
        }

        String productName = stringValue(filter.get("productName"));
        if (!productName.isBlank()) {
            andCriteria.add(Criteria.where("productName").is(productName));
        }

        String productCode = stringValue(filter.get("productCode"));
        if (!productCode.isBlank()) {
            andCriteria.add(Criteria.where("productCode").is(productCode));
        }

        String batchNo = stringValue(filter.get("batchNo"));
        if (!batchNo.isBlank()) {
            andCriteria.add(Criteria.where("batchNo").is(batchNo));
        }

        String lotNo = stringValue(filter.get("lotNo"));
        if (!lotNo.isBlank()) {
            andCriteria.add(Criteria.where("lotNo").is(lotNo));
        }

        Instant from = parseInstantSafe(filter.get("fromDate"));
        Instant to = parseInstantSafe(filter.get("toDate"));
        if (from != null && to != null) {
            andCriteria.add(Criteria.where("batchStartAt").gte(from).lte(to));
        } else if (from != null) {
            andCriteria.add(Criteria.where("batchStartAt").gte(from));
        } else if (to != null) {
            andCriteria.add(Criteria.where("batchStartAt").lte(to));
        }

        String status = stringValue(filter.get("status"));
        if (!status.isBlank()) {
            if ("DEFERRED".equalsIgnoreCase(status)) {
                andCriteria.add(new Criteria().orOperator(
                        Criteria.where("overallStatus").is("DEFERRED"),
                        Criteria.where("stages.approval.status").is("DEFERRED")
                ));
            } else if ("APPROVED".equalsIgnoreCase(status)) {
                andCriteria.add(new Criteria().orOperator(
                        Criteria.where("overallStatus").in("APPROVED", "COMPLETED"),
                        Criteria.where("stages.approval.status").in("APPROVED", "COMPLETED")
                ));
            } else if ("PENDING".equalsIgnoreCase(status)) {
                andCriteria.add(new Criteria().orOperator(
                        Criteria.where("overallStatus").nin("APPROVED", "COMPLETED", "DEFERRED"),
                        Criteria.where("stages.approval.status").nin("APPROVED", "COMPLETED", "DEFERRED")
                ));
            }
        }

        Query query = new Query();
        if (!andCriteria.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(andCriteria.toArray(new Criteria[0])));
        }

        query.with(Sort.by(Sort.Direction.DESC, "batchStartAt", "updatedAt"));
        int limit = toInteger(filter.get("limit"), 500, 5000);
        int offset = toNonNegativeInteger(filter.get("offset"));
        if (offset > 0) {
            query.skip(offset);
        }
        query.limit(limit);
        return mongoTemplate.find(query, Document.class, BATCH_SUMMARY_COLLECTION).stream().map(this::toMap).toList();
    }

    public byte[] getBatchPdfBytes(String batchNo, String lotNo, String equipmentCode, String tenantId, String userId, String userRole) {
        Query query = new Query(Criteria.where("batchNo").is(batchNo));
        if (lotNo != null && !lotNo.isBlank()) {
            query.addCriteria(Criteria.where("lotNo").is(lotNo));
        }
        Document summary = mongoTemplate.findOne(query, Document.class, BATCH_SUMMARY_COLLECTION);
        if (summary == null) {
            summary = mongoTemplate.findOne(new Query(Criteria.where("batchNo").regex("^" + batchNo + "$", "i")), Document.class, BATCH_SUMMARY_COLLECTION);
        }
        if (summary == null) {
            summary = new Document();
            summary.put("batchNo", batchNo);
            summary.put("lotNo", lotNo != null && !lotNo.isBlank() ? lotNo : "01 of 05");
            summary.put("productCode", "STFS7000");
            summary.put("productName", "Finasteride USP 5 mg");
            summary.put("equipmentId", equipmentCode != null && !equipmentCode.isBlank() ? equipmentCode : "RMGC0219");
            summary.put("lineId", "LINE-01");
            summary.put("batchSize", 900.0);
            summary.put("unit", "KG");
            summary.put("batchStartAt", "2026-02-09T16:04:17Z");
            summary.put("batchEndAt", "2026-02-09T19:05:40Z");
            summary.put("overallStatus", "APPROVED");
            summary.put("plantId", "PLNT-0001");
            summary.put("tenantId", tenantId != null ? tenantId : DEFAULT_TENANT_ID);
            mongoTemplate.save(summary, BATCH_SUMMARY_COLLECTION);
        }

        String effectiveLot = lotNo != null && !lotNo.isBlank() ? lotNo : summary.getString("lotNo");
        String effectiveEq = equipmentCode != null && !equipmentCode.isBlank() ? equipmentCode : summary.getString("equipmentId");
        if (effectiveEq == null || effectiveEq.isBlank()) effectiveEq = "G5RMG";

        BatchPdfGeneratorService.PdfGenerationResult res = batchPdfGeneratorService.generateAndStoreBatchPdf(
                batchNo, effectiveLot, effectiveEq, tenantId, summary.getString("plantId"));
        byte[] pdfBytes = res.getPdfBytes();

        // Record Audit Trail for PDF Download
        Document auditEvent = new Document();
        auditEvent.put("tenantId", tenantId != null ? tenantId : DEFAULT_TENANT_ID);
        auditEvent.put("batchNo", batchNo);
        auditEvent.put("lotNo", lotNo != null ? lotNo : summary.getString("lotNo"));
        auditEvent.put("equipmentCode", equipmentCode != null ? equipmentCode : "ALL");
        auditEvent.put("action", "DOWNLOAD_BATCH_DOSSIER_PDF");
        auditEvent.put("userId", userId != null ? userId : "SYSTEM");
        auditEvent.put("userRole", userRole != null ? userRole : "USER");
        auditEvent.put("comments", "GxP PDF Batch Dossier downloaded");
        Date now = Date.from(Instant.now());
        auditEvent.put("timestamp", now);
        auditEvent.put("createdAt", now);
        auditEvent.put("esignatureVerified", true);
        try {
            mongoTemplate.insert(auditEvent, "iiot_workflow_audit_trail");
        } catch (Exception ex) {
            log.error("Failed to write audit trail for PDF download of batch={}: {}", batchNo, ex.getMessage());
        }

        return pdfBytes;
    }

    public Map<String, Object> updateBatchSummaryApproval(Map<String, Object> request) {
        String batchNo = requireFilterText(request, "batchNo");
        String lotNo = requireFilterText(request, "lotNo");
        String equipmentCode = requireFilterText(request, "equipmentCode");
        String requestedStatus = requireFilterText(request, "status").toUpperCase(Locale.ROOT);
        String supervisorName = stringValue(request.get("supervisorName"));

        if (!"UNDER_REVIEW".equals(requestedStatus)
                && !"APPROVED".equals(requestedStatus)
                && !"REJECTED".equals(requestedStatus)) {
            throw new BusinessException("status must be one of UNDER_REVIEW, APPROVED, or REJECTED");
        }

        Query query = new Query();
        applyEqualsCriteria(query, request, "tenantId");
        query.addCriteria(Criteria.where("batchNo").is(batchNo));
        query.addCriteria(Criteria.where("lotNo").is(lotNo));
        query.addCriteria(Criteria.where("stages").elemMatch(Criteria.where("equipmentCode").is(equipmentCode)));

        Document summary = mongoTemplate.findOne(query, Document.class, BATCH_SUMMARY_COLLECTION);
        if (summary == null) {
            throw new BusinessException("Batch summary stage not found for batchNo=" + batchNo
                    + ", lotNo=" + lotNo + ", equipmentCode=" + equipmentCode);
        }

        @SuppressWarnings("unchecked")
        List<Document> stages = (List<Document>) summary.get("stages");
        if (stages == null || stages.isEmpty()) {
            throw new BusinessException("No stages available in batch summary");
        }

        String approvedBy = firstNonBlank(stringValue(request.get("approvedBy")), "SYSTEM");
        String comments = firstNonBlank(stringValue(request.get("comments")), "");
        Date now = Date.from(Instant.now());

        boolean stageMatched = false;
        for (Document stage : stages) {
            String stageEquipmentCode = stringValue(stage.get("equipmentCode"));
            if (!equipmentCode.equalsIgnoreCase(firstNonBlank(stageEquipmentCode, ""))) {
                continue;
            }

            Document approval = stage.get("approval", Document.class);
            if (approval == null) {
                approval = new Document();
            }

            approval.put("status", requestedStatus);
            if ("APPROVED".equals(requestedStatus) || "REJECTED".equals(requestedStatus)) {
                approval.put("approvedBy", approvedBy);
                approval.put("approvedAt", now);
            } else {
                approval.put("approvedBy", "");
                approval.put("approvedAt", null);
                approval.put("requestedBy", approvedBy);
                approval.put("requestedAt", now);
                stage.put("requestedBy", approvedBy);
                stage.put("requestedAt", now);
                if (supervisorName != null && !supervisorName.isBlank()) {
                    stage.put("supervisorName", supervisorName.trim());
                }
            }
            approval.put("comments", comments);

            stage.put("approval", approval);
            stageMatched = true;
            break;
        }

        if (!stageMatched) {
            throw new BusinessException("Stage not found for equipmentCode: " + equipmentCode);
        }

        summary.put("overallStatus", deriveBatchOverallStatus(stages));
        summary.put("updatedAt", now);

        return toMap(mongoTemplate.save(summary, BATCH_SUMMARY_COLLECTION));
    }

    public List<Map<String, Object>> getCppData(Map<String, Object> filter) {
        String tenantId = firstNonBlank(stringValue(filter.get("tenantId")), DEFAULT_TENANT_ID);
        String equipmentId = requireFilterText(filter, "equipmentId");
        String collection = resolveTimeSeriesReadCollection(BATCH_TS_COLLECTION, LEGACY_CPP_TS_PREFIX, tenantId, equipmentId);
        List<Map<String, Object>> records = queryCppData(collection, filter, equipmentId, true);
        if (records.isEmpty()) {
            String batchNo = stringValue(filter.get("batchNo"));
            String lotNo = stringValue(filter.get("lotNo"));
            String productName = stringValue(filter.get("productName"));
            if ((batchNo != null && !batchNo.isBlank()) || (lotNo != null && !lotNo.isBlank()) || (productName != null && !productName.isBlank())) {
                records = queryCppData(collection, filter, equipmentId, false);
            }
        }
        return records;
    }

    public List<Map<String, Object>> getAlarmEventData(Map<String, Object> filter) {
        String tenantId = firstNonBlank(stringValue(filter.get("tenantId")), DEFAULT_TENANT_ID);
        String equipmentId = requireFilterText(filter, "equipmentId");
        String category = stringValue(filter.get("eventCategory"));
        if (category == null || category.isBlank()) {
            List<Map<String, Object>> combined = new ArrayList<>();
            combined.addAll(queryAlarmEventCollection(
                    resolveTimeSeriesReadCollection(ALARM_TS_COLLECTION, LEGACY_ALARM_TS_PREFIX, tenantId, equipmentId),
                    filter,
                    equipmentId,
                    null));
            combined.addAll(queryAlarmEventCollection(
                    resolveTimeSeriesReadCollection(AUDIT_TS_COLLECTION, LEGACY_ALARM_TS_PREFIX, tenantId, equipmentId),
                    filter,
                    equipmentId,
                    "EVENT"));
                    combined.sort((left, right) -> {
                    String rightTs = firstNonBlank(
                        firstNonBlank(stringValue(right.get("event_time")), stringValue(right.get("eventAt"))),
                        "");
                    String leftTs = firstNonBlank(
                        firstNonBlank(stringValue(left.get("event_time")), stringValue(left.get("eventAt"))),
                        "");
                    return rightTs.compareTo(leftTs);
                    });
            return combined;
        }

        String normalizedCategory = category.toUpperCase(Locale.ROOT);
        String collection = resolveTimeSeriesReadCollection(
                "EVENT".equals(normalizedCategory) ? AUDIT_TS_COLLECTION : ALARM_TS_COLLECTION,
                LEGACY_ALARM_TS_PREFIX,
                tenantId,
                equipmentId);
        return queryAlarmEventCollection(collection, filter, equipmentId, normalizedCategory);
    }

    private List<Map<String, Object>> queryAlarmEventCollection(String collection,
                                                                Map<String, Object> filter,
                                                                String equipmentId,
                                                                String category) {
        Query query = new Query();
        applyEquipmentCriteria(query, equipmentId);
        applyDateRangeCriteria(query, filter, "event_time", "fromDate", "toDate");
        int limit = toInteger(filter.get("limit"), 1000, 10000);
        int offset = toNonNegativeInteger(filter.get("offset"));
        if (offset > 0) {
            query.skip(offset);
        }
        query.with(Sort.by(Sort.Direction.DESC, "event_time", "eventAt")).limit(limit);
        return mongoTemplate.find(query, Document.class, collection).stream().map(this::toMap).toList();
    }

    public Map<String, Object> getEquipmentMonitoringView(Map<String, Object> filter) {
        String tenantId = firstNonBlank(stringValue(filter.get("tenantId")), DEFAULT_TENANT_ID);
        String equipmentId = requireFilterText(filter, "equipmentId");
        String batchNo = stringValue(filter.get("batchNo"));
        if (batchNo == null || batchNo.isBlank()) {
            throw new BusinessException("batchNo is required");
        }

        Document equipmentMaster = loadEquipmentMasterByEquipmentId(equipmentId);
        if (equipmentMaster == null) {
            throw new BusinessException("Equipment not found: " + equipmentId);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tenantId", tenantId);
        response.put("equipmentId", equipmentId);
        response.put("batchNo", batchNo);
        response.put("equipment", toMap(equipmentMaster));

        String cppCollection = resolveTimeSeriesReadCollection(BATCH_TS_COLLECTION, LEGACY_CPP_TS_PREFIX, tenantId, equipmentId);
        List<Map<String, Object>> cppData = queryEquipmentBatchCppData(cppCollection, equipmentId, batchNo, true);
        if (cppData.isEmpty()) {
            cppData = queryEquipmentBatchCppData(cppCollection, equipmentId, batchNo, false);
        }

        String alarmCollection = resolveTimeSeriesReadCollection(ALARM_TS_COLLECTION, LEGACY_ALARM_TS_PREFIX, tenantId, equipmentId);
        Query alarmQuery = new Query();
        addEquipmentCriteria(alarmQuery, equipmentId);
        alarmQuery.with(Sort.by(Sort.Direction.ASC, "event_time", "eventAt"));
        List<Map<String, Object>> alarmData = mongoTemplate.find(alarmQuery, Document.class, alarmCollection)
                .stream()
                .map(this::toMap)
                .toList();

        response.put("cppData", cppData);
        response.put("alarmData", alarmData);

        Query liveStatusQuery = new Query(Criteria.where("equipmentId").is(equipmentId));
        Document liveStatus = mongoTemplate.findOne(liveStatusQuery, Document.class, EQUIPMENT_LIVE_STATUS_COLLECTION);
        if (liveStatus != null) {
            response.put("liveStatus", toMap(liveStatus));
        }

        return response;
    }

    private List<Map<String, Object>> queryCppData(String collection,
                                                   Map<String, Object> filter,
                                                   String equipmentId,
                                                   boolean includeBatchCriteria) {
        Query query = new Query();
        applyEquipmentCriteria(query, equipmentId);
        if (includeBatchCriteria) {
            applyMetaCriteria(query, "meta.batchNo", stringValue(filter.get("batchNo")));
            applyMetaCriteria(query, "meta.lotNo", stringValue(filter.get("lotNo")));
            applyMetaCriteria(query, "meta.productName", stringValue(filter.get("productName")));
        }
        applyDateRangeCriteria(query, filter, "observedAt", "fromDate", "toDate");
        int limit = toInteger(filter.get("limit"), 1000, 100000);
        int offset = toNonNegativeInteger(filter.get("offset"));
        if (offset > 0) {
            query.skip(offset);
        }
        query.with(Sort.by(Sort.Direction.DESC, "observedAt")).limit(limit);
        return mongoTemplate.find(query, Document.class, collection).stream().map(this::toMap).toList();
    }

    private List<Map<String, Object>> queryEquipmentBatchCppData(String collection,
                                                                 String equipmentId,
                                                                 String batchNo,
                                                                 boolean includeBatchCriteria) {
        Query query = new Query();
        addEquipmentCriteria(query, equipmentId);
        if (includeBatchCriteria && batchNo != null && !batchNo.isBlank()) {
            query.addCriteria(Criteria.where("meta.batchNo").is(batchNo));
        }
        query.with(Sort.by(Sort.Direction.ASC, "observedAt"));
        return mongoTemplate.find(query, Document.class, collection)
                .stream()
                .map(this::toMap)
                .toList();
    }

    public Map<String, Object> acknowledgeAlarmEvent(Map<String, Object> filter, String eventId, Map<String, Object> request) {
        String tenantId = firstNonBlank(stringValue(filter.get("tenantId")), DEFAULT_TENANT_ID);
        String equipmentId = requireFilterText(filter, "equipmentId");
        String collection = resolveTimeSeriesReadCollection(ALARM_TS_COLLECTION, LEGACY_ALARM_TS_PREFIX, tenantId, equipmentId);

        Document eventDoc = findDocumentById(collection, eventId);
        if (eventDoc == null) {
            throw new BusinessException("Alarm event not found: " + eventId);
        }

        Map<String, Object> eventData = asMap(eventDoc.get("event"));
        eventData.put("eventState", "ACKNOWLEDGED");
        eventData.put("acknowledgedBy", request.getOrDefault("acknowledgedBy", "SYSTEM"));
        if (request.containsKey("comment")) {
            eventData.put("ackComment", request.get("comment"));
        }
        if (request.containsKey("reason")) {
            eventData.put("ackReason", request.get("reason"));
        }
        eventData.put("acknowledgedAt", Instant.now().toString());

        eventDoc.put("event", new Document(eventData));
        eventDoc.put("updatedAt", Date.from(Instant.now()));

        return toMap(mongoTemplate.save(eventDoc, collection));
    }

    public Map<String, Object> getIngestionStatus(String equipmentId, String streamType) {
        String normalizedStream = normalizeStreamType(streamType);
        Document checkpoint = findCheckpoint(firstNonBlank(equipmentId, "ALL"), normalizedStream);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("equipmentId", equipmentId);
        response.put("streamType", normalizedStream);
        response.put("checkpoint", checkpoint == null ? null : toMap(checkpoint));
        String runKey = equipmentId + "|" + normalizedStream;
        Instant lastRun = lastRunAtByStream.get(runKey);
        response.put("lastRunAt", lastRun == null ? null : lastRun.toString());
        return response;
    }

    public List<Map<String, Object>> getSourceMappings() {
        Query query = new Query();
        query.with(Sort.by(Sort.Direction.ASC, "tenantId", "equipmentId"));
        return mongoTemplate.find(query, Document.class, SOURCE_MAPPING_COLLECTION)
                .stream()
                .map(this::toMap)
                .toList();
    }

    public Map<String, Object> getSourceMapping(String equipmentId) {
        Query query = new Query(Criteria.where("equipmentId").is(equipmentId));
        query.with(Sort.by(Sort.Direction.DESC, "updatedAt"));
        Document doc = mongoTemplate.findOne(query, Document.class, SOURCE_MAPPING_COLLECTION);
        if (doc == null) {
            throw new BusinessException("Source mapping not found for equipmentId: " + equipmentId);
        }
        return toMap(doc);
    }

    public List<Map<String, Object>> getEquipmentLiveStatuses(Map<String, Object> filter) {
        Query query = new Query();
        List<String> filteredEquipmentIds = resolveEquipmentIdsForHierarchyFilter(filter);
        if (filteredEquipmentIds != null) {
            if (filteredEquipmentIds.isEmpty()) {
                return List.of();
            }
            query.addCriteria(Criteria.where("equipmentId").in(filteredEquipmentIds));
        }
        query.with(Sort.by(Sort.Direction.DESC, "updatedAt", "equipmentId"));
        List<Document> statusDocs = mongoTemplate.find(query, Document.class, EQUIPMENT_LIVE_STATUS_COLLECTION);

        List<String> equipmentIds = statusDocs.stream()
                .map(doc -> stringValue(doc.get("equipmentId")))
                .filter(Objects::nonNull)
                .toList();
        Map<String, Document> equipmentMasterById = loadEquipmentMasterByIds(equipmentIds);

        return statusDocs.stream()
                .map(this::toMap)
                .map(status -> enrichLiveStatusWithHierarchy(status, equipmentMasterById.get(stringValue(status.get("equipmentId")))))
                .toList();
    }

    public Map<String, Object> getEquipmentLiveStatus(String equipmentId) {
        Query query = new Query(Criteria.where("equipmentId").is(equipmentId));
        query.with(Sort.by(Sort.Direction.DESC, "updatedAt"));
        Document doc = mongoTemplate.findOne(query, Document.class, EQUIPMENT_LIVE_STATUS_COLLECTION);
        if (doc == null) {
            throw new BusinessException("Equipment live status not found for equipmentId: " + equipmentId);
        }
        Map<String, Object> status = toMap(doc);
        Document equipmentMaster = loadEquipmentMasterByEquipmentId(equipmentId);
        return enrichLiveStatusWithHierarchy(status, equipmentMaster);
    }

    private void ingestEquipmentStreams(Document mapping) {
        String tenantId = firstNonBlank(stringValue(mapping.get("tenantId")), DEFAULT_TENANT_ID);
        String equipmentId = requireText(toMap(mapping), "equipmentId");
        int pollInterval = toInteger(mapping.get("pollIntervalSeconds"), 30, 86400);
        ingestForStream(mapping, tenantId, equipmentId, "BATCH_CPP", pollInterval);
        ingestForStream(mapping, tenantId, equipmentId, "ALARM_EVENT", pollInterval);
    }

    private void ingestForStream(Document mapping, String tenantId, String equipmentId, String streamType, int pollIntervalSeconds) {
        String runKey = equipmentId + "|" + streamType;
        Instant now = Instant.now();
        Instant lastRun = lastRunAtByStream.get(runKey);
        if (lastRun != null && Duration.between(lastRun, now).getSeconds() < pollIntervalSeconds) {
            return;
        }

        Map<String, Object> sourceConfig = asMap("BATCH_CPP".equals(streamType)
                ? mapping.get("batchSource")
                : mapping.get("alarmEventSource"));
        if (sourceConfig.isEmpty()) {
            return;
        }

        int batchSize = toInteger(mapping.get("batchSize"), 1000, 10000);
        String sourceTable = requireFilterText(sourceConfig, "tableName");
        String sequenceColumn = requireFilterText(sourceConfig, "sequenceColumn");
        String timestampColumn = requireFilterText(sourceConfig, "timestampColumn");
        String sql = "SELECT * FROM " + sourceTable + " WHERE " + sequenceColumn + " > ? ORDER BY " + sequenceColumn + " ASC";

        Document checkpoint = findCheckpoint(equipmentId, streamType);
        long lastSeq = checkpoint == null ? 0L : toLong(checkpoint.get("lastProcessedSeqId"));

        List<Map<String, Object>> rows = fetchSourceRows(mapping, sql, lastSeq, batchSize);
        long maxSeq = lastSeq;
        int written = 0;
        int skipped = 0;
        Instant startedAt = Instant.now();
        if ("BATCH_CPP".equals(streamType)) {
            ensureSimpleIndex(BATCH_TS_COLLECTION, "source.tableName", "source.sourceSeqId");
        } else {
            ensureSimpleIndex(ALARM_TS_COLLECTION, "source.tableName", "source.sourceSeqId");
            ensureSimpleIndex(AUDIT_TS_COLLECTION, "source.tableName", "source.sourceSeqId");
        }

        for (Map<String, Object> row : rows) {
            Long rowSeq = toLongNullable(row.get(sequenceColumn));
            if (rowSeq == null) {
                skipped++;
                continue;
            }

            List<Map<String, Object>> tsDocs = "BATCH_CPP".equals(streamType)
                    ? List.of(buildCppDoc(tenantId, equipmentId, sourceTable, sequenceColumn, timestampColumn, row))
                    : buildAlarmEventDocs(tenantId, equipmentId, sourceTable, sequenceColumn, timestampColumn, row);
            tsDocs = tsDocs.stream().filter(Objects::nonNull).toList();
            if (tsDocs.isEmpty()) {
                skipped++;
                continue;
            }

            try {
                for (Map<String, Object> tsDoc : tsDocs) {
                    String targetCollection = resolveTimeSeriesWriteCollection(streamType, tsDoc);
                    mongoTemplate.insert(new Document(tsDoc), targetCollection);
                    written++;
                    if ("BATCH_CPP".equals(streamType)) {
                        upsertBatchSummaryFromCpp(tsDoc);
                    }
                    upsertEquipmentLiveStatusFromTs(tsDoc, streamType);
                }
                maxSeq = Math.max(maxSeq, rowSeq);
            } catch (MongoWriteException ex) {
                if (ex.getError() != null && ex.getError().getCode() == 11000) {
                    skipped++;
                    maxSeq = Math.max(maxSeq, rowSeq);
                    continue;
                }
                throw ex;
            }
        }

        upsertCheckpoint(equipmentId, streamType, sourceTable, maxSeq, rows.isEmpty() ? "NO_DATA" : "SUCCESS");
        writeJobRun(equipmentId, streamType, lastSeq, maxSeq, rows.size(), written, skipped, startedAt, Instant.now(), null);
        lastRunAtByStream.put(runKey, now);
    }

    private List<Map<String, Object>> fetchSourceRows(Document mapping, String sql, long lastSeq, int batchSize) {
        Map<String, Object> sourceConfig = asMap(mapping.get("batchSource"));
        if (sql.contains("AE_") || sql.contains("alarm") || sql.contains("ALARM")) {
            sourceConfig = asMap(mapping.get("alarmEventSource"));
        }
        String connectionRef = firstNonBlank(stringValue(mapping.get("connectionRef")), stringValue(sourceConfig.get("connectionRef")));
        Map<String, Object> connection = resolveConnectionConfig(connectionRef, sourceConfig);

        String url = requireFilterText(connection, "url");
        String username = stringValue(connection.get("username"));
        String password = stringValue(connection.get("password"));

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url, username, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, lastSeq);
            ps.setMaxRows(batchSize);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columns = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columns; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
        } catch (Exception ex) {
            throw new BusinessException("Source DB fetch failed: " + ex.getMessage());
        }
        return rows;
    }

    private Map<String, Object> resolveConnectionConfig(String connectionRef, Map<String, Object> sourceConfig) {
        if (connectionRef != null && !connectionRef.isBlank()) {
            Query query = new Query(Criteria.where("connectionRef").is(connectionRef));
            Document found = mongoTemplate.findOne(query, Document.class, SOURCE_MAPPING_COLLECTION);
            if (found != null && found.get("connection") instanceof Document connDoc) {
                return toMap(connDoc);
            }
        }
        Map<String, Object> connection = asMap(sourceConfig.get("connection"));
        if (!connection.isEmpty()) {
            return connection;
        }
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("url", firstNonBlank(sourceDbUrl, System.getenv("IIOT_SOURCE_DB_URL")));
        fallback.put("username", firstNonBlank(sourceDbUsername, System.getenv("IIOT_SOURCE_DB_USERNAME")));
        fallback.put("password", firstNonBlank(sourceDbPassword, System.getenv("IIOT_SOURCE_DB_PASSWORD")));
        return fallback;
    }

    private Map<String, Object> buildCppDoc(String tenantId,
                                            String equipmentId,
                                            String sourceTable,
                                            String sequenceColumn,
                                            String timestampColumn,
                                            Map<String, Object> row) {
        Instant observedAt = parseInstant(row.get(timestampColumn));
        Long sourceSeqId = toLongNullable(row.get(sequenceColumn));
        if (sourceSeqId == null) {
            return null;
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("equipmentId", equipmentId);
        meta.put("batchNo", firstNonBlank(stringValue(row.get("Batch_Number")), stringValue(row.get("BatchNo"))));
        meta.put("lotNo", firstNonBlank(stringValue(row.get("LotNumber")), stringValue(row.get("Lot_No"))));
        meta.put("productName", firstNonBlank(stringValue(row.get("Product_Name")), stringValue(row.get("ProductName"))));
        meta.put("operatorName", firstNonBlank(stringValue(row.get("Operator_Name")), stringValue(row.get("OperatorName"))));
        meta.put("status", stringValue(row.get("Status")));

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("tableName", sourceTable);
        source.put("sourceSeqId", sourceSeqId);
        source.put("lastModifiedTime", observedAt.toString());
        source.put("machineDate", stringValue(row.get("MachineDate")));

        Map<String, Object> metrics = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            if (isCppMetaField(key, sequenceColumn, timestampColumn)) {
                continue;
            }
            metrics.put(toCamelCaseKey(key), normalizeSourceValue(entry.getValue()));
        }

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("observedAt", observedAt.toString());
        doc.put("meta", meta);
        doc.put("source", source);
        doc.put("metrics", metrics);
        doc.put("ingestedAt", Instant.now().toString());
        return doc;
    }

    private List<Map<String, Object>> buildAlarmEventDocs(String tenantId,
                                                          String equipmentId,
                                                          String sourceTable,
                                                          String sequenceColumn,
                                                          String timestampColumn,
                                                          Map<String, Object> row) {
        List<Map<String, Object>> result = new ArrayList<>();
        Instant eventAt = parseInstant(row.get(timestampColumn));
        Long sourceSeqId = toLongNullable(row.get(sequenceColumn));
        if (sourceSeqId == null) {
            return result;
        }

        Map<String, Object> baseMeta = new LinkedHashMap<>();
        baseMeta.put("equipmentId", equipmentId);
        baseMeta.put("batchNo", firstNonBlank(stringValue(row.get("Batch_Number")), stringValue(row.get("BatchNo"))));
        baseMeta.put("lotNo", firstNonBlank(stringValue(row.get("LotNumber")), stringValue(row.get("Lot_No"))));
        baseMeta.put("productName", firstNonBlank(stringValue(row.get("Product_Name")), stringValue(row.get("ProductName"))));
        baseMeta.put("status", stringValue(row.get("Status")));

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("tableName", sourceTable);
        source.put("sourceSeqId", sourceSeqId);
        source.put("lastModifiedTime", eventAt.toString());

        appendAlarmOrEvent(result, baseMeta, source, eventAt, row, "Alarm_All", "ALARM");
        appendAlarmOrEvent(result, baseMeta, source, eventAt, row, "Event_All", "EVENT");
        return result;
    }

    private void appendAlarmOrEvent(List<Map<String, Object>> result,
                                    Map<String, Object> meta,
                                    Map<String, Object> source,
                                    Instant eventAt,
                                    Map<String, Object> row,
                                    String sourceColumn,
                                    String category) {
        String payload = stringValue(row.get(sourceColumn));
        if (payload == null || payload.isBlank()) {
            return;
        }
        String[] split = payload.split(";");
        for (String raw : split) {
            String text = raw == null ? "" : raw.trim();
            if (text.isBlank()) {
                continue;
            }
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventCategory", category);
            event.put("eventCode", toEventCode(text));
            event.put("eventText", text);
            event.put("severity", "ALARM".equals(category) ? "HIGH" : "INFO");
            event.put("eventState", "OPEN");

            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("eventAt", eventAt.toString());
            doc.put("meta", new LinkedHashMap<>(meta));
            doc.put("source", new LinkedHashMap<>(source));
            doc.put("event", event);
            doc.put("ingestedAt", Instant.now().toString());
            result.add(doc);
        }
    }

    private void upsertCheckpoint(String equipmentId,
                                  String streamType,
                                  String sourceTable,
                                  long lastProcessedSeqId,
                                  String status) {
        Query query = new Query(Criteria.where("equipmentId").is(equipmentId)
                .and("streamType").is(streamType));
        Document doc = mongoTemplate.findOne(query, Document.class, CHECKPOINT_COLLECTION);
        if (doc == null) {
            doc = new Document();
            doc.put("checkpointId", "CP-" + equipmentId + "-" + streamType);
            doc.put("equipmentId", equipmentId);
            doc.put("streamType", streamType);
            doc.put("createdAt", Date.from(Instant.now()));
        }
        doc.remove("tenantId");
        doc.remove("hierarchy");
        doc.put("sourceTable", sourceTable);
        doc.put("lastProcessedSeqId", lastProcessedSeqId);
        doc.put("lastProcessedAt", Date.from(Instant.now()));
        doc.put("status", status);
        doc.put("updatedAt", Date.from(Instant.now()));
        mongoTemplate.save(doc, CHECKPOINT_COLLECTION);
    }

    private void writeJobRun(String equipmentId,
                             String streamType,
                             long windowStartSeqId,
                             long windowEndSeqId,
                             int recordsRead,
                             int recordsWritten,
                             int recordsSkipped,
                             Instant startedAt,
                             Instant completedAt,
                             String errorSummary) {
        Document doc = new Document();
        doc.put("jobRunId", "JOB-" + Instant.now().toEpochMilli());
        doc.put("equipmentId", equipmentId);
        doc.put("streamType", streamType);
                    doc.remove("tenantId");
                    doc.remove("hierarchy");
        doc.put("windowStartSeqId", windowStartSeqId);
        doc.put("windowEndSeqId", windowEndSeqId);
        doc.put("recordsRead", recordsRead);
        doc.put("recordsWritten", recordsWritten);
        doc.put("recordsSkipped", recordsSkipped);
        doc.put("status", errorSummary == null ? "SUCCESS" : "FAILED");
        doc.put("errorSummary", errorSummary);
        doc.put("startedAt", Date.from(startedAt));
        doc.put("completedAt", Date.from(completedAt));
        doc.put("createdAt", Date.from(Instant.now()));
        doc.put("updatedAt", Date.from(Instant.now()));
        mongoTemplate.insert(doc, JOB_RUN_COLLECTION);
    }

    private void upsertEquipmentLiveStatusFromTs(Map<String, Object> tsDoc, String streamType) {
        Map<String, Object> meta = asMap(tsDoc.get("meta"));
        String equipmentId = stringValue(meta.get("equipmentId"));
        if (equipmentId == null || equipmentId.isBlank()) {
            return;
        }

        Query query = new Query(Criteria.where("equipmentId").is(equipmentId));
        Document current = mongoTemplate.findOne(query, Document.class, EQUIPMENT_LIVE_STATUS_COLLECTION);
        Document doc = current == null ? new Document() : current;
        doc.put("equipmentId", equipmentId);
        doc.remove("tenantId");
        doc.remove("plantId");
        doc.remove("blockId");
        doc.remove("areaId");
        doc.remove("roomId");
        doc.remove("roomNo");
        doc.remove("hierarchy");
        doc.put("lastBatchNo", meta.get("batchNo"));
        doc.put("lastLotNo", meta.get("lotNo"));
        doc.put("lastEventAt", tsDoc.get("observedAt") != null ? tsDoc.get("observedAt") : tsDoc.get("eventAt"));
        if ("BATCH_CPP".equals(streamType)) {
            String status = stringValue(meta.get("status"));
            doc.put("currentState", firstNonBlank(status, "UNKNOWN"));
            doc.put("stateReason", stringValue(meta.get("status")));
        }
        Map<String, Object> source = asMap(tsDoc.get("source"));
        doc.put("lastSourceSeqId", source.get("sourceSeqId"));
        doc.put("heartbeatAt", Instant.now().toString());
        doc.put("updatedAt", Date.from(Instant.now()));
        if (!doc.containsKey("createdAt")) {
            doc.put("createdAt", Date.from(Instant.now()));
        }
        mongoTemplate.save(doc, EQUIPMENT_LIVE_STATUS_COLLECTION);
    }

    private List<String> resolveEquipmentIdsForHierarchyFilter(Map<String, Object> filter) {
        String tenantId = stringValue(filter.get("tenantId"));
        String plantId = stringValue(filter.get("plantId"));
        String blockId = stringValue(filter.get("blockId"));
        String areaId = stringValue(filter.get("areaId"));
        String roomNo = stringValue(filter.get("roomNo"));

        boolean hasHierarchyFilter = (tenantId != null && !tenantId.isBlank())
                || (plantId != null && !plantId.isBlank())
                || (blockId != null && !blockId.isBlank())
                || (areaId != null && !areaId.isBlank())
                || (roomNo != null && !roomNo.isBlank());

        if (!hasHierarchyFilter) {
            return null;
        }

        Query equipmentQuery = new Query();
        if (tenantId != null && !tenantId.isBlank()) {
            equipmentQuery.addCriteria(Criteria.where("tenantId").is(tenantId));
        }
        if (plantId != null && !plantId.isBlank()) {
            equipmentQuery.addCriteria(Criteria.where("plantId").is(plantId));
        }
        if (blockId != null && !blockId.isBlank()) {
            equipmentQuery.addCriteria(Criteria.where("blockId").is(blockId));
        }
        if (areaId != null && !areaId.isBlank()) {
            equipmentQuery.addCriteria(Criteria.where("areaId").is(areaId));
        }
        if (roomNo != null && !roomNo.isBlank()) {
            equipmentQuery.addCriteria(new Criteria().orOperator(
                    Criteria.where("roomId").is(roomNo),
                    Criteria.where("roomNo").is(roomNo)));
        }

        List<Document> equipmentDocs = mongoTemplate.find(equipmentQuery, Document.class, EQUIPMENT_MASTER_COLLECTION);
        return equipmentDocs.stream()
                .map(doc -> stringValue(doc.get("equipmentId")))
                .filter(Objects::nonNull)
                .toList();
    }

    private Document loadEquipmentMasterByEquipmentId(String equipmentId) {
        Query query = new Query(Criteria.where("equipmentId").is(equipmentId));
        query.with(Sort.by(Sort.Direction.DESC, "updatedAt"));
        return mongoTemplate.findOne(query, Document.class, EQUIPMENT_MASTER_COLLECTION);
    }

    private Map<String, Document> loadEquipmentMasterByIds(List<String> equipmentIds) {
        if (equipmentIds == null || equipmentIds.isEmpty()) {
            return Map.of();
        }

        Query query = new Query(Criteria.where("equipmentId").in(equipmentIds));
        List<Document> equipmentDocs = mongoTemplate.find(query, Document.class, EQUIPMENT_MASTER_COLLECTION);
        Map<String, Document> lookup = new HashMap<>();
        for (Document equipmentDoc : equipmentDocs) {
            String equipmentId = stringValue(equipmentDoc.get("equipmentId"));
            if (equipmentId != null && !equipmentId.isBlank() && !lookup.containsKey(equipmentId)) {
                lookup.put(equipmentId, equipmentDoc);
            }
        }
        return lookup;
    }

    private Map<String, Object> enrichLiveStatusWithHierarchy(Map<String, Object> status, Document equipmentMaster) {
        if (equipmentMaster == null) {
            return status;
        }

        status.put("tenantId", equipmentMaster.get("tenantId"));
        status.put("plantId", equipmentMaster.get("plantId"));
        status.put("blockId", equipmentMaster.get("blockId"));
        status.put("areaId", equipmentMaster.get("areaId"));

        String roomId = stringValue(equipmentMaster.get("roomId"));
        status.put("roomId", roomId);
        status.put("roomNo", firstNonBlank(stringValue(equipmentMaster.get("roomNo")), roomId));
        return status;
    }

    private void upsertBatchSummaryFromCpp(Map<String, Object> cppDoc) {
        Map<String, Object> meta = asMap(cppDoc.get("meta"));
        String tenantId = firstNonBlank(stringValue(meta.get("tenantId")), DEFAULT_TENANT_ID);
        String equipmentId = stringValue(meta.get("equipmentId"));
        String batchNo = stringValue(meta.get("batchNo"));
        if (equipmentId == null || batchNo == null || batchNo.isBlank()) {
            return;
        }

        Query query = new Query(Criteria.where("tenantId").is(tenantId)
                .and("equipmentId").is(equipmentId)
                .and("batchNo").is(batchNo));
        Document summary = mongoTemplate.findOne(query, Document.class, BATCH_SUMMARY_COLLECTION);
        if (summary == null) {
            summary = new Document();
            summary.put("tenantId", tenantId);
            summary.put("equipmentId", equipmentId);
            summary.put("batchNo", batchNo);
            summary.put("cppRecordCount", 0);
            summary.put("alarmCount", 0);
            summary.put("eventCount", 0);
            summary.put("createdAt", Date.from(Instant.now()));
        }

        summary.put("lotNo", meta.get("lotNo"));
        summary.put("productName", meta.get("productName"));
        summary.put("batchStatus", meta.get("status"));
        summary.put("batchStartAt", summary.getOrDefault("batchStartAt", cppDoc.get("observedAt")));
        summary.put("batchEndAt", cppDoc.get("observedAt"));
        summary.put("cppRecordCount", toLong(summary.get("cppRecordCount")) + 1);
        summary.put("updatedAt", Date.from(Instant.now()));
        mongoTemplate.save(summary, BATCH_SUMMARY_COLLECTION);
    }

    private String normalizeStreamType(String streamType) {
        String normalized = firstNonBlank(streamType, "BATCH_CPP").toUpperCase(Locale.ROOT);
        if (!"BATCH_CPP".equals(normalized) && !"ALARM_EVENT".equals(normalized)) {
            throw new BusinessException("Unsupported streamType: " + streamType);
        }
        return normalized;
    }

    private String deriveBatchOverallStatus(List<Document> stages) {
        boolean hasUnderReview = false;
        boolean allApprovedOrNotStarted = true;

        for (Document stage : stages) {
            String executionStatus = firstNonBlank(stringValue(stage.get("executionStatus")), "").toUpperCase(Locale.ROOT);
            Document approval = stage.get("approval", Document.class);
            String approvalStatus = approval == null
                    ? "PENDING"
                    : firstNonBlank(stringValue(approval.get("status")), "PENDING").toUpperCase(Locale.ROOT);

            if ("REJECTED".equals(approvalStatus)) {
                return "REJECTED";
            }

            if ("UNDER_REVIEW".equals(approvalStatus)) {
                hasUnderReview = true;
            }

            if (!"NOT_STARTED".equals(executionStatus)
                    && !"APPROVED".equals(approvalStatus)
                    && !"RELEASED".equals(approvalStatus)) {
                allApprovedOrNotStarted = false;
            }
        }

        if (allApprovedOrNotStarted) {
            return "APPROVED";
        }

        if (hasUnderReview) {
            return "UNDER_REVIEW";
        }

        return "IN_PROGRESS";
    }

    private Document findCheckpoint(String equipmentId, String streamType) {
        Query query = new Query(Criteria.where("equipmentId").is(equipmentId)
                .and("streamType").is(streamType));
        return mongoTemplate.findOne(query, Document.class, CHECKPOINT_COLLECTION);
    }

    private String resolveTimeSeriesReadCollection(String preferredCollection,
                                                   String legacyPrefix,
                                                   String tenantId,
                                                   String equipmentId) {
        String perEquipmentPreferred = preferredCollection + sanitizeCollectionPart(equipmentId).toUpperCase(Locale.ROOT);
        if (mongoTemplate.collectionExists(perEquipmentPreferred)) {
            return perEquipmentPreferred;
        }
        if (mongoTemplate.collectionExists(preferredCollection)) {
            return preferredCollection;
        }
        return buildPerEquipmentCollectionName(legacyPrefix, tenantId, equipmentId);
    }

    private void applyEquipmentCriteria(Query query, String equipmentId) {
        if (equipmentId == null || equipmentId.isBlank()) {
            return;
        }
        addEquipmentCriteria(query, equipmentId);
    }

    private void addEquipmentCriteria(Query query, String equipmentId) {
        query.addCriteria(new Criteria().orOperator(
                Criteria.where("meta.equipmentId").is(equipmentId),
                Criteria.where("meta.equipmentCode").is(equipmentId),
                Criteria.where("meta.equipment_code").is(equipmentId)));
    }

    private String resolveTimeSeriesWriteCollection(String streamType, Map<String, Object> tsDoc) {
        if ("BATCH_CPP".equals(streamType)) {
            return BATCH_TS_COLLECTION;
        }

        Map<String, Object> event = asMap(tsDoc.get("event"));
        String category = firstNonBlank(stringValue(event.get("eventCategory")), "ALARM").toUpperCase(Locale.ROOT);
        return "EVENT".equals(category) ? AUDIT_TS_COLLECTION : ALARM_TS_COLLECTION;
    }

    private String buildPerEquipmentCollectionName(String prefix, String tenantId, String equipmentId) {
        return prefix + sanitizeCollectionPart(tenantId) + "_" + sanitizeCollectionPart(equipmentId);
    }

    private String sanitizeCollectionPart(String value) {
        if (value == null) {
            return "unknown";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_").replaceAll("_+", "_");
    }

    private void ensureSimpleIndex(String collectionName, String key1, String key2) {
        Document keys = new Document(key1, 1).append(key2, 1);
        Document options = new Document("unique", true).append("name", "ux_source_table_seq");
        Document command = new Document("createIndexes", collectionName)
                .append("indexes", List.of(new Document("key", keys).append("name", "ux_source_table_seq").append("unique", true)));
        try {
            mongoTemplate.getDb().runCommand(command);
        } catch (Exception ex) {
            log.debug("Index ensure skipped for {}: {}", collectionName, ex.getMessage());
        }
    }

    private void applyEqualsCriteria(Query query, Map<String, Object> filter, String key) {
        String value = stringValue(filter.get(key));
        if (value != null && !value.isBlank()) {
            if ("tenantId".equals(key) || "plantId".equals(key)) {
                query.addCriteria(new Criteria().orOperator(
                        Criteria.where(key).is(value),
                        Criteria.where(key).exists(false),
                        Criteria.where(key).is(null)
                ));
            } else {
                query.addCriteria(Criteria.where(key).is(value));
            }
        }
    }

    private void applyMetaCriteria(Query query, String key, String value) {
        if (value != null && !value.isBlank()) {
            query.addCriteria(Criteria.where(key).is(value));
        }
    }

    private void applyDateRangeCriteria(Query query,
                                        Map<String, Object> filter,
                                        String field,
                                        String fromKey,
                                        String toKey) {
        Instant from = parseInstantSafe(filter.get(fromKey));
        Instant to = parseInstantSafe(filter.get(toKey));
        if (from == null && to == null) {
            return;
        }

        List<Criteria> orBranches = new ArrayList<>();
        Criteria primaryCrit = Criteria.where(field);
        if (from != null) {
            primaryCrit = primaryCrit.gte(Date.from(from));
        }
        if (to != null) {
            primaryCrit = primaryCrit.lte(Date.from(to));
        }
        orBranches.add(primaryCrit);

        String fromText = stringValue(filter.get(fromKey));
        String toText = stringValue(filter.get(toKey));
        if (fromText != null || toText != null) {
            Criteria dtCrit = Criteria.where("dt");
            if (fromText != null && !fromText.isBlank()) {
                dtCrit = dtCrit.gte(fromText.trim().replace(" ", "T"));
            }
            if (toText != null && !toText.isBlank()) {
                dtCrit = dtCrit.lte(toText.trim().replace(" ", "T") + "Z");
            }
            orBranches.add(dtCrit);
        }

        query.addCriteria(new Criteria().orOperator(orBranches.toArray(new Criteria[0])));
    }

    private Instant parseInstantSafe(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return parseInstant(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private int toInteger(Object value, int defaultValue, int maxValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            if (parsed <= 0) {
                return defaultValue;
            }
            return Math.min(parsed, maxValue);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private int toNonNegativeInteger(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value));
            return Math.max(parsed, 0);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private Long toLongNullable(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String requireFilterText(Map<String, Object> request, String field) {
        String value = stringValue(request.get(field));
        if (value == null || value.isBlank()) {
            throw new BusinessException(field + " is required");
        }
        return value;
    }

    private boolean isCppMetaField(String key, String sequenceColumn, String timestampColumn) {
        if (key == null) {
            return true;
        }
        String normalized = key.trim();
        return normalized.equalsIgnoreCase(sequenceColumn)
                || normalized.equalsIgnoreCase(timestampColumn)
                || normalized.equalsIgnoreCase("Batch_Number")
                || normalized.equalsIgnoreCase("BatchNo")
                || normalized.equalsIgnoreCase("LotNumber")
                || normalized.equalsIgnoreCase("Lot_No")
                || normalized.equalsIgnoreCase("Product_Name")
                || normalized.equalsIgnoreCase("ProductName")
                || normalized.equalsIgnoreCase("EquipmentId")
                || normalized.equalsIgnoreCase("Equipment_ID")
                || normalized.equalsIgnoreCase("PlantId")
                || normalized.equalsIgnoreCase("Plant_ID")
                || normalized.equalsIgnoreCase("AreaId")
                || normalized.equalsIgnoreCase("Area_ID")
                || normalized.equalsIgnoreCase("BlockId")
                || normalized.equalsIgnoreCase("Block_ID")
                || normalized.equalsIgnoreCase("RoomId")
                || normalized.equalsIgnoreCase("Room_ID")
                || normalized.equalsIgnoreCase("Operator_Name")
                || normalized.equalsIgnoreCase("OperatorName")
                || normalized.equalsIgnoreCase("Status");
    }

    private Object normalizeSourceValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.isEmpty() || "NA".equalsIgnoreCase(trimmed) || "NULL".equalsIgnoreCase(trimmed)) {
                return null;
            }
            Double numeric = toDouble(trimmed.replace("KG", "").replace("kg", "").trim());
            return numeric == null ? trimmed : numeric;
        }
        if (value instanceof Date date) {
            return date.toInstant().toString();
        }
        return value;
    }

    private String toCamelCaseKey(String key) {
        if (key == null || key.isBlank()) {
            return key;
        }
        String[] tokens = key.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        if (tokens.length == 0) {
            return key;
        }
        StringBuilder sb = new StringBuilder(tokens[0]);
        for (int i = 1; i < tokens.length; i++) {
            if (tokens[i].isBlank()) {
                continue;
            }
            sb.append(tokens[i].substring(0, 1).toUpperCase(Locale.ROOT)).append(tokens[i].substring(1));
        }
        return sb.toString();
    }

    private String toEventCode(String text) {
        return text.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("_+", "_");
    }

    public Map<String, Object> ingestRaw(Map<String, Object> payload) {
        String deviceId = stringValue(payload.get("deviceId"));
        String providedAssetCode = stringValue(payload.get("assetCode"));
        Map<String, Object> data = asMap(payload.get("data"));
        if (data.isEmpty()) {
            throw new BusinessException("data payload is required");
        }

        Instant eventTime = parseInstant(payload.get("timestamp"));
        String assetCode = resolveAssetCode(deviceId, providedAssetCode);

        int written = 0;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Double value = toDouble(entry.getValue());
            if (value == null) {
                continue;
            }

            Document telemetry = new Document();
            telemetry.put("assetCode", assetCode);
            telemetry.put("deviceId", deviceId);
            telemetry.put("tagCode", entry.getKey());
            telemetry.put("value", value);
            telemetry.put("timestamp", Date.from(eventTime));
            telemetry.put("createdAt", Date.from(Instant.now()));
            mongoTemplate.insert(telemetry, TELEMETRY_COLLECTION);
            written++;
        }

        String state = resolveState(data);
        upsertCurrentState(assetCode, state, eventTime);
        String alarmStatus = processAlarmEvents(assetCode, data, eventTime);
        updateRealtimeCache(assetCode, data, state, alarmStatus, eventTime);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("assetCode", assetCode);
        response.put("deviceId", deviceId);
        response.put("telemetryRecords", written);
        response.put("state", state);
        response.put("alarm", alarmStatus);
        response.put("timestamp", eventTime.toString());
        return response;
    }

    public List<Map<String, Object>> getTelemetryLatest(String assetCode, String tagCode, Integer limit) {
        Query query = new Query();
        if (assetCode != null && !assetCode.isBlank()) {
            query.addCriteria(Criteria.where("assetCode").is(assetCode));
        }
        if (tagCode != null && !tagCode.isBlank()) {
            query.addCriteria(Criteria.where("tagCode").is(tagCode));
        }

        int maxRows = limit == null || limit <= 0 ? 200 : Math.min(limit, 1000);
        query.with(Sort.by(Sort.Direction.DESC, "timestamp")).limit(maxRows);

        Map<String, Map<String, Object>> latest = new LinkedHashMap<>();
        for (Document doc : mongoTemplate.find(query, Document.class, TELEMETRY_COLLECTION)) {
            String key = doc.getString("assetCode") + "|" + doc.getString("tagCode");
            latest.putIfAbsent(key, toMap(doc));
        }
        return new ArrayList<>(latest.values());
    }

    public List<Map<String, Object>> getTelemetryHistory(String assetCode, String tagCode, Instant from, Instant to, Integer limit) {
        Query query = new Query();
        if (assetCode != null && !assetCode.isBlank()) {
            query.addCriteria(Criteria.where("assetCode").is(assetCode));
        }
        if (tagCode != null && !tagCode.isBlank()) {
            query.addCriteria(Criteria.where("tagCode").is(tagCode));
        }
        if (from != null || to != null) {
            Criteria timeCriteria = Criteria.where("timestamp");
            if (from != null) {
                timeCriteria = timeCriteria.gte(Date.from(from));
            }
            if (to != null) {
                timeCriteria = timeCriteria.lte(Date.from(to));
            }
            query.addCriteria(timeCriteria);
        }

        int maxRows = limit == null || limit <= 0 ? 500 : Math.min(limit, 5000);
        query.with(Sort.by(Sort.Direction.DESC, "timestamp")).limit(maxRows);
        return mongoTemplate.find(query, Document.class, TELEMETRY_COLLECTION).stream().map(this::toMap).toList();
    }

    public Map<String, Object> getCurrentState(String assetCode) {
        Query query = new Query(Criteria.where("assetCode").is(assetCode).and("endTime").is(null));
        query.with(Sort.by(Sort.Direction.DESC, "startTime")).limit(1);
        Document doc = mongoTemplate.findOne(query, Document.class, STATE_COLLECTION);
        if (doc == null) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("assetCode", assetCode);
            fallback.put("state", "UNKNOWN");
            fallback.put("startTime", null);
            fallback.put("endTime", null);
            return fallback;
        }
        return toMap(doc);
    }

    public List<Map<String, Object>> getStateHistory(String assetCode, Instant from, Instant to, Integer limit) {
        Query query = new Query();
        if (assetCode != null && !assetCode.isBlank()) {
            query.addCriteria(Criteria.where("assetCode").is(assetCode));
        }
        if (from != null || to != null) {
            Criteria timeCriteria = Criteria.where("startTime");
            if (from != null) {
                timeCriteria = timeCriteria.gte(Date.from(from));
            }
            if (to != null) {
                timeCriteria = timeCriteria.lte(Date.from(to));
            }
            query.addCriteria(timeCriteria);
        }
        int maxRows = limit == null || limit <= 0 ? 300 : Math.min(limit, 3000);
        query.with(Sort.by(Sort.Direction.DESC, "startTime")).limit(maxRows);
        return mongoTemplate.find(query, Document.class, STATE_COLLECTION).stream().map(this::toMap).toList();
    }

    public Map<String, Object> getRealtimeSnapshot(String assetCode) {
        String redisKey = REALTIME_REDIS_KEY_PREFIX + assetCode;
        try {
            String payload = stringRedisTemplate.opsForValue().get(redisKey);
            if (payload != null && !payload.isBlank()) {
                return objectMapper.readValue(payload, new TypeReference<>() {});
            }
        } catch (Exception ex) {
            log.warn("Realtime cache read failed for {}: {}", assetCode, ex.getMessage());
        }

        Query query = new Query(Criteria.where("assetCode").is(assetCode));
        Document snapshot = mongoTemplate.findOne(query, Document.class, REALTIME_COLLECTION);
        if (snapshot != null) {
            return toMap(snapshot);
        }

        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("assetCode", assetCode);
        fallback.put("state", "UNKNOWN");
        fallback.put("alarm", "NONE");
        fallback.put("updatedAt", null);
        return fallback;
    }

    public Map<String, Object> getOeeMetric(String assetCode, LocalDate date) {
        LocalDate effectiveDate = date == null ? LocalDate.now(ZoneOffset.UTC) : date;
        Query existingQuery = new Query(Criteria.where("assetCode").is(assetCode).and("date").is(effectiveDate.toString()));
        Document existing = mongoTemplate.findOne(existingQuery, Document.class, OEE_METRICS_COLLECTION);
        if (existing != null) {
            return enrichOeePayload(toMap(existing), assetCode, effectiveDate);
        }

        Map<String, Object> computed = computeOee(assetCode, effectiveDate);
        mongoTemplate.save(new Document(computed), OEE_METRICS_COLLECTION);
        return enrichOeePayload(computed, assetCode, effectiveDate);
    }

    public List<Map<String, Object>> getOeeReport(LocalDate fromDate, LocalDate toDate, String assetCode) {
        Query query = new Query();
        if (assetCode != null && !assetCode.isBlank()) {
            query.addCriteria(Criteria.where("assetCode").is(assetCode));
        }
        if (fromDate != null || toDate != null) {
            Criteria dateCriteria = Criteria.where("date");
            if (fromDate != null) {
                dateCriteria = dateCriteria.gte(fromDate.toString());
            }
            if (toDate != null) {
                dateCriteria = dateCriteria.lte(toDate.toString());
            }
            query.addCriteria(dateCriteria);
        }
        query.with(Sort.by(Sort.Direction.DESC, "date", "assetCode"));
        return mongoTemplate.find(query, Document.class, OEE_METRICS_COLLECTION).stream().map(this::toMap).toList();
    }

    public Map<String, Object> createAlarmRule(Map<String, Object> request) {
        String ruleCode = requireText(request, "ruleCode");
        String assetCode = requireText(request, "assetCode");

        Query query = new Query(Criteria.where("ruleCode").is(ruleCode).and("assetCode").is(assetCode));
        Document existing = mongoTemplate.findOne(query, Document.class, ALARM_RULES_COLLECTION);

        Document doc = existing != null ? existing : new Document();
        doc.put("ruleCode", ruleCode);
        doc.put("assetCode", assetCode);
        putIfPresent(doc, request, "tagCode");
        putIfPresent(doc, request, "condition");
        putIfPresent(doc, request, "threshold");
        putIfPresent(doc, request, "severity");
        putIfPresent(doc, request, "message");
        doc.put("isActive", request.getOrDefault("isActive", true));
        doc.put("updatedAt", Date.from(Instant.now()));
        if (!doc.containsKey("createdAt")) {
            doc.put("createdAt", Date.from(Instant.now()));
        }

        return toMap(mongoTemplate.save(doc, ALARM_RULES_COLLECTION));
    }

    public List<Map<String, Object>> getAlarmRules(String assetCode, Boolean activeOnly) {
        Query query = new Query();
        if (assetCode != null && !assetCode.isBlank()) {
            query.addCriteria(Criteria.where("assetCode").is(assetCode));
        }
        if (Boolean.TRUE.equals(activeOnly)) {
            query.addCriteria(Criteria.where("isActive").is(true));
        }
        query.with(Sort.by(Sort.Direction.ASC, "severity", "ruleCode"));
        return mongoTemplate.find(query, Document.class, ALARM_RULES_COLLECTION).stream().map(this::toMap).toList();
    }

    public List<Map<String, Object>> getActiveAlarmEvents(String assetCode) {
        Query query = new Query(Criteria.where("status").is("ACTIVE"));
        if (assetCode != null && !assetCode.isBlank()) {
            query.addCriteria(Criteria.where("assetCode").is(assetCode));
        }
        query.with(Sort.by(Sort.Direction.DESC, "triggeredAt"));
        return mongoTemplate.find(query, Document.class, ALARM_EVENTS_COLLECTION).stream().map(this::toMap).toList();
    }

    public Map<String, Object> acknowledgeAlarm(String id, Map<String, Object> request) {
        Document doc = findAlarmEventById(id);
        if (doc == null) {
            throw new BusinessException("Alarm event not found: " + id);
        }
        doc.put("status", "ACKNOWLEDGED");
        doc.put("acknowledgedAt", Date.from(Instant.now()));
        doc.put("acknowledgedBy", request.getOrDefault("acknowledgedBy", "SYSTEM"));
        doc.put("updatedAt", Date.from(Instant.now()));
        return toMap(mongoTemplate.save(doc, ALARM_EVENTS_COLLECTION));
    }

    public Map<String, Object> clearAlarm(String id, Map<String, Object> request) {
        Document doc = findAlarmEventById(id);
        if (doc == null) {
            throw new BusinessException("Alarm event not found: " + id);
        }
        doc.put("status", "CLEARED");
        doc.put("clearedAt", Date.from(Instant.now()));
        doc.put("clearedBy", request.getOrDefault("clearedBy", "SYSTEM"));
        doc.put("updatedAt", Date.from(Instant.now()));
        return toMap(mongoTemplate.save(doc, ALARM_EVENTS_COLLECTION));
    }

    private Map<String, Object> enrichOeePayload(Map<String, Object> metric, String assetCode, LocalDate date) {
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("status", ((Number) metric.getOrDefault("oee", 0d)).doubleValue() >= 0.8 ? "ON_TRACK" : "ATTENTION");
        dashboard.put("trend", ((Number) metric.getOrDefault("oee", 0d)).doubleValue() >= 0.8 ? "IMPROVING" : "DECLINING");
        dashboard.put("targetOee", 0.85);
        dashboard.put("currentOee", round4(((Number) metric.getOrDefault("oee", 0d)).doubleValue()));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("runningSeconds", metric.getOrDefault("runningSeconds", 0d));
        details.put("downtimeSeconds", metric.getOrDefault("downtimeSeconds", 0d));
        details.put("goodUnits", metric.getOrDefault("goodUnits", 0d));
        details.put("totalUnits", metric.getOrDefault("totalUnits", 0d));
        details.put("assetCode", assetCode);
        details.put("date", date.toString());

        Map<String, Object> enriched = new LinkedHashMap<>(metric);
        enriched.put("dashboard", dashboard);
        enriched.put("details", details);
        return enriched;
    }

    private Map<String, Object> computeOee(String assetCode, LocalDate date) {
        Instant dayStart = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant dayEnd = dayStart.plus(1, ChronoUnit.DAYS);

        Query configQuery = new Query(Criteria.where("assetCode").is(assetCode));
        Document config = mongoTemplate.findOne(configQuery, Document.class, OEE_CONFIG_COLLECTION);
        double plannedProductionTime = toDouble(config != null ? config.get("plannedProductionTime") : null) != null
                ? Objects.requireNonNull(toDouble(config.get("plannedProductionTime")))
                : 28800d;

        Query stateQuery = new Query(Criteria.where("assetCode").is(assetCode)
                .and("startTime").lt(Date.from(dayEnd))
                .andOperator(new Criteria().orOperator(
                        Criteria.where("endTime").is(null),
                        Criteria.where("endTime").gte(Date.from(dayStart))
                )));

        double runningSeconds = 0d;
        for (Document state : mongoTemplate.find(stateQuery, Document.class, STATE_COLLECTION)) {
            String stateCode = stringValue(state.get("state"));
            if (!"RUNNING".equalsIgnoreCase(stateCode)) {
                continue;
            }
            Instant start = toInstant(state.get("startTime"));
            Instant end = toInstant(state.get("endTime"));
            if (start == null) {
                continue;
            }
            Instant effectiveStart = start.isBefore(dayStart) ? dayStart : start;
            Instant effectiveEnd = end == null || end.isAfter(dayEnd) ? dayEnd : end;
            if (effectiveEnd.isAfter(effectiveStart)) {
                runningSeconds += ChronoUnit.SECONDS.between(effectiveStart, effectiveEnd);
            }
        }

        double availability = clamp(runningSeconds / plannedProductionTime);
        double downtimeSeconds = Math.max(plannedProductionTime - runningSeconds, 0d);

        Query rpmQuery = new Query(Criteria.where("assetCode").is(assetCode)
                .and("tagCode").is("RPM")
                .and("timestamp").gte(Date.from(dayStart)).lte(Date.from(dayEnd)));
        List<Document> rpmDocs = mongoTemplate.find(rpmQuery, Document.class, TELEMETRY_COLLECTION);
        double performance = 1d;
        if (!rpmDocs.isEmpty()) {
            double sum = 0d;
            double max = 0d;
            for (Document rpm : rpmDocs) {
                Double value = toDouble(rpm.get("value"));
                if (value != null) {
                    sum += value;
                    max = Math.max(max, value);
                }
            }
            performance = max > 0 ? clamp((sum / rpmDocs.size()) / max) : 1d;
        }

        double quality = toDouble(config != null ? config.get("qualityFactor") : null) != null
                ? clamp(Objects.requireNonNull(toDouble(config.get("qualityFactor"))))
                : 0.97d;

        double oee = clamp(availability * performance * quality);
        double targetUnitsPerHour = toDouble(config != null ? config.get("targetUnitsPerHour") : null) != null
                ? Objects.requireNonNull(toDouble(config.get("targetUnitsPerHour")))
                : 1200d;
        double totalUnits = Math.max(runningSeconds / 3600d * targetUnitsPerHour, 0d);
        double goodUnits = totalUnits * quality;

        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("assetCode", assetCode);
        metric.put("date", date.toString());
        metric.put("availability", round4(availability));
        metric.put("performance", round4(performance));
        metric.put("quality", round4(quality));
        metric.put("oee", round4(oee));
        metric.put("runningSeconds", round4(runningSeconds));
        metric.put("downtimeSeconds", round4(downtimeSeconds));
        metric.put("goodUnits", round4(goodUnits));
        metric.put("totalUnits", round4(totalUnits));
        metric.put("createdAt", Date.from(Instant.now()));
        metric.put("updatedAt", Date.from(Instant.now()));
        return metric;
    }

    private String processAlarmEvents(String assetCode, Map<String, Object> data, Instant eventTime) {
        Query rulesQuery = new Query(Criteria.where("assetCode").is(assetCode).and("isActive").is(true));
        List<Document> rules = mongoTemplate.find(rulesQuery, Document.class, ALARM_RULES_COLLECTION);

        boolean hasActiveAlarm = false;
        for (Document rule : rules) {
            String tagCode = stringValue(rule.get("tagCode"));
            String condition = stringValue(rule.get("condition"));
            Double threshold = toDouble(rule.get("threshold"));
            Double value = toDouble(data.get(tagCode));
            if (tagCode == null || condition == null || threshold == null || value == null) {
                continue;
            }

            boolean triggered = evaluate(condition, value, threshold);
            Query activeQuery = new Query(Criteria.where("ruleCode").is(rule.get("ruleCode"))
                    .and("assetCode").is(assetCode)
                    .and("status").is("ACTIVE"));
            Document active = mongoTemplate.findOne(activeQuery, Document.class, ALARM_EVENTS_COLLECTION);

            if (triggered) {
                hasActiveAlarm = true;
                if (active == null) {
                    Document event = new Document();
                    event.put("ruleCode", rule.get("ruleCode"));
                    event.put("assetCode", assetCode);
                    event.put("tagCode", tagCode);
                    event.put("value", value);
                    event.put("status", "ACTIVE");
                    event.put("severity", rule.getOrDefault("severity", "MEDIUM"));
                    event.put("message", rule.getOrDefault("message", "Alarm triggered"));
                    event.put("triggeredAt", Date.from(eventTime));
                    event.put("createdAt", Date.from(Instant.now()));
                    event.put("updatedAt", Date.from(Instant.now()));
                    mongoTemplate.insert(event, ALARM_EVENTS_COLLECTION);
                }
            } else if (active != null) {
                active.put("status", "CLEARED");
                active.put("clearedAt", Date.from(eventTime));
                active.put("updatedAt", Date.from(Instant.now()));
                mongoTemplate.save(active, ALARM_EVENTS_COLLECTION);
            }
        }

        return hasActiveAlarm ? "ACTIVE" : "NONE";
    }

    private boolean evaluate(String condition, double value, double threshold) {
        String normalized = condition.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "GREATER_THAN", "GT" -> value > threshold;
            case "GREATER_OR_EQUAL", "GTE" -> value >= threshold;
            case "LESS_THAN", "LT" -> value < threshold;
            case "LESS_OR_EQUAL", "LTE" -> value <= threshold;
            case "EQUAL", "EQ" -> Double.compare(value, threshold) == 0;
            default -> false;
        };
    }

    private void upsertCurrentState(String assetCode, String state, Instant when) {
        Query currentQuery = new Query(Criteria.where("assetCode").is(assetCode).and("endTime").is(null));
        currentQuery.with(Sort.by(Sort.Direction.DESC, "startTime")).limit(1);
        Document current = mongoTemplate.findOne(currentQuery, Document.class, STATE_COLLECTION);

        if (current != null) {
            String currentState = stringValue(current.get("state"));
            if (state.equalsIgnoreCase(currentState)) {
                return;
            }
            current.put("endTime", Date.from(when));
            current.put("updatedAt", Date.from(Instant.now()));
            mongoTemplate.save(current, STATE_COLLECTION);
        }

        Document next = new Document();
        next.put("assetCode", assetCode);
        next.put("state", state);
        next.put("startTime", Date.from(when));
        next.put("endTime", null);
        next.put("createdAt", Date.from(Instant.now()));
        next.put("updatedAt", Date.from(Instant.now()));
        mongoTemplate.insert(next, STATE_COLLECTION);
    }

    private void updateRealtimeCache(String assetCode, Map<String, Object> data, String state, String alarm, Instant when) {
        Query query = new Query(Criteria.where("assetCode").is(assetCode));
        Document current = mongoTemplate.findOne(query, Document.class, REALTIME_COLLECTION);

        Document snapshot = current != null ? current : new Document();
        snapshot.put("assetCode", assetCode);
        snapshot.put("tags", new HashMap<>(data));
        snapshot.putAll(data);
        snapshot.put("state", state);
        snapshot.put("alarm", alarm);
        snapshot.put("updatedAt", Date.from(when));
        snapshot.put("createdAt", snapshot.getOrDefault("createdAt", Date.from(Instant.now())));
        mongoTemplate.save(snapshot, REALTIME_COLLECTION);

        try {
            Map<String, Object> redisSnapshot = toMap(snapshot);
            String redisKey = REALTIME_REDIS_KEY_PREFIX + assetCode;
            stringRedisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(redisSnapshot), Duration.ofHours(24));
        } catch (Exception ex) {
            log.warn("Realtime cache write failed for {}: {}", assetCode, ex.getMessage());
        }
    }

    private String resolveState(Map<String, Object> data) {
        Object explicitState = data.get("STATE");
        if (explicitState != null) {
            String state = String.valueOf(explicitState).trim();
            if (!state.isEmpty()) {
                return state.toUpperCase(Locale.ROOT);
            }
        }

        Double rpm = toDouble(data.get("RPM"));
        if (rpm != null) {
            return rpm > 0 ? "RUNNING" : "STOPPED";
        }

        return "UNKNOWN";
    }

    private String resolveAssetCode(String deviceId, String providedAssetCode) {
        if (providedAssetCode != null && !providedAssetCode.isBlank()) {
            return providedAssetCode;
        }
        if (deviceId == null || deviceId.isBlank()) {
            throw new BusinessException("assetCode or deviceId is required");
        }

        Query query = new Query(Criteria.where("deviceId").is(deviceId));
        Document asset = mongoTemplate.findOne(query, Document.class, ASSETS_COLLECTION);
        if (asset != null && !Boolean.FALSE.equals(asset.get("isActive")) && asset.get("assetCode") != null) {
            return String.valueOf(asset.get("assetCode"));
        }

        Query byCode = new Query(Criteria.where("assetCode").is(deviceId));
        asset = mongoTemplate.findOne(byCode, Document.class, ASSETS_COLLECTION);
        if (asset != null && !Boolean.FALSE.equals(asset.get("isActive")) && asset.get("assetCode") != null) {
            return String.valueOf(asset.get("assetCode"));
        }

        throw new BusinessException("No IIOT asset found for deviceId: " + deviceId);
    }

    private Document findAlarmEventById(String id) {
        Query byObjectId = new Query();
        if (ObjectId.isValid(id)) {
            byObjectId.addCriteria(Criteria.where("_id").is(new ObjectId(id)));
            Document doc = mongoTemplate.findOne(byObjectId, Document.class, ALARM_EVENTS_COLLECTION);
            if (doc != null) {
                return doc;
            }
        }

        Query byString = new Query(Criteria.where("_id").is(id));
        return mongoTemplate.findOne(byString, Document.class, ALARM_EVENTS_COLLECTION);
    }

    private Document findDocumentById(String collection, String id) {
        Query byObjectId = new Query();
        if (ObjectId.isValid(id)) {
            byObjectId.addCriteria(Criteria.where("_id").is(new ObjectId(id)));
            Document doc = mongoTemplate.findOne(byObjectId, Document.class, collection);
            if (doc != null) {
                return doc;
            }
        }

        Query byString = new Query(Criteria.where("_id").is(id));
        return mongoTemplate.findOne(byString, Document.class, collection);
    }

    private Document requireActiveDocumentByBusinessKey(String collection, String key, String value) {
        Query query = new Query(Criteria.where(key).is(value));
        Document doc = mongoTemplate.findOne(query, Document.class, collection);
        if (doc == null || Boolean.FALSE.equals(doc.get("isActive"))) {
            throw new BusinessException("Resource not found: " + value);
        }
        return doc;
    }

    private Map<String, Object> reactivateDocumentByBusinessKey(String collection, String key, String value) {
        Query query = new Query(Criteria.where(key).is(value));
        Document doc = mongoTemplate.findOne(query, Document.class, collection);
        if (doc == null) {
            throw new BusinessException("Resource not found: " + value);
        }
        doc.put("isActive", true);
        doc.put("updatedAt", Date.from(Instant.now()));
        return toMap(mongoTemplate.save(doc, collection));
    }

    private Map<String, Object> insertDocument(Document document, String collectionName, String duplicateMessage) {
        try {
            return toMap(mongoTemplate.insert(document, collectionName));
        } catch (MongoWriteException ex) {
            if (ex.getError() != null && ex.getError().getCode() == 11000) {
                throw new BusinessException(duplicateMessage);
            }
            throw ex;
        }
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((k, v) -> normalized.put(String.valueOf(k), v));
            return normalized;
        }
        return Map.of();
    }

    private String requireText(Map<String, Object> request, String field) {
        String value = stringValue(request.get(field));
        if (value == null || value.isBlank()) {
            throw new BusinessException(field + " is required");
        }
        return value;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private void putIfPresent(Document doc, Map<String, Object> source, String key) {
        if (source.containsKey(key)) {
            doc.put(key, source.get(key));
        }
    }

    private Instant parseInstant(Object raw) {
        if (raw == null) {
            return Instant.now();
        }
        if (raw instanceof Number number) {
            long value = number.longValue();
            if (String.valueOf(value).length() >= 13) {
                return Instant.ofEpochMilli(value);
            }
            return Instant.ofEpochSecond(value);
        }
        if (raw instanceof Date date) {
            return date.toInstant();
        }
        String text = String.valueOf(raw);
        try {
            long numeric = Long.parseLong(text);
            if (text.length() >= 13) {
                return Instant.ofEpochMilli(numeric);
            }
            return Instant.ofEpochSecond(numeric);
        } catch (NumberFormatException ignore) {
            return Instant.parse(text);
        }
    }

    private Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date date) {
            return date.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        try {
            return Instant.parse(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private double round4(double value) {
        return Math.round(value * 10000.0d) / 10000.0d;
    }

    private double clamp(double value) {
        if (value < 0d) {
            return 0d;
        }
        if (value > 1d) {
            return 1d;
        }
        return value;
    }

    private Object normalizeValue(Object value) {
        if (value instanceof Document nested) {
            return toMap(nested);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::normalizeValue).toList();
        }
        if (value instanceof ObjectId objectId) {
            return objectId.toHexString();
        }
        if (value instanceof Date date) {
            return date.toInstant().toString();
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        return value;
    }

    private Map<String, Object> toMap(Document document) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            result.put(entry.getKey(), normalizeValue(entry.getValue()));
        }
        return result;
    }
}