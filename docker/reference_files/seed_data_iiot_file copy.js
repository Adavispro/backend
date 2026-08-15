// ============================================
// Adavis IIOT Seed Data (Pharma-Oriented)
// REALISTIC PHARMA DATA with FULL HIERARCHY (Plant → Block → Area → Room → Equipment)
// ============================================

// Connection test
try {
    var testResult = db.runCommand({ping: 1});
    if (testResult.ok !== 1) {
        print("[IIOT-SEED] ERROR: Cannot connect to MongoDB");
        quit(1);
    }
} catch (e) {
    print("[IIOT-SEED] ERROR: MongoDB connection failed: " + e.message);
    quit(1);
}

var databaseName = "adavis_platform";
if (typeof process !== "undefined" && process.env && process.env.MONGO_INITDB_DATABASE) {
    databaseName = process.env.MONGO_INITDB_DATABASE;
}

db = db.getSiblingDB(databaseName);
print("[IIOT-SEED] Using database: " + databaseName);

var TENANT_ID = "TNT-0001";

// ============================================
// HIERARCHY CONFIGURATION
// ============================================
var PLANT_IDS = ["PLNT-0001"];
var BLOCK_IDS = ["BLK-0001"];
var AREA_IDS = ["AREA-0001"];
var ROOM_IDS = ["ROOM-0001"];
var PLANT_HIERARCHY = {
    "PLNT-0001": {
        blocks: ["BLK-0001"],
        areas: ["AREA-0001"],
        rooms: ["ROOM-0001"]
    }
};
var EXPLICIT_EQUIPMENT_DEFS = [
    {
        equipmentId: "WEG-003-PVII",
        equipmentCode: "WEG-003-PVII",
        equipmentName: "Wet Granulator #WEG-003",
        plantId: "PLNT-0001",
        blockId: "BLK-0001",
        areaId: "AREA-0001",
        roomId: "ROOM-0001",
        make: "GEA Pharma",
        model: "WEG-003",
        equipmentType: "WEG",
        equipmentTypeName: "Wet Granulator",
        stageOrder: 1
    },
    {
        equipmentId: "FBD-004-PVII",
        equipmentCode: "FBD-004-PVII",
        equipmentName: "Fluid Bed Dryer #FBD-004",
        plantId: "PLNT-0001",
        blockId: "BLK-0001",
        areaId: "AREA-0001",
        roomId: "ROOM-0001",
        make: "Glatt",
        model: "FBD-004",
        equipmentType: "FBD",
        equipmentTypeName: "Fluid Bed Dryer",
        stageOrder: 2
    },
    {
        equipmentId: "BLE-003-PVII",
        equipmentCode: "BLE-003-PVII",
        equipmentName: "Bin Blender #BLE-003",
        plantId: "PLNT-0001",
        blockId: "BLK-0001",
        areaId: "AREA-0001",
        roomId: "ROOM-0001",
        make: "Bohle",
        model: "BLE-003",
        equipmentType: "BLE",
        equipmentTypeName: "Bin Blender",
        stageOrder: 3
    }
];

var TOTAL_EQUIPMENT = PLANT_IDS.reduce(function(total, plantId) {
    var config = PLANT_HIERARCHY[plantId] || {};
    var blocks = config.blocks || BLOCK_IDS;
    var areas = config.areas || AREA_IDS;
    var rooms = config.rooms || ROOM_IDS;
    return total + blocks.length * areas.length * rooms.length;
}, 0) + EXPLICIT_EQUIPMENT_DEFS.length;
if (EXPLICIT_EQUIPMENT_DEFS.length > 0) {
    TOTAL_EQUIPMENT = EXPLICIT_EQUIPMENT_DEFS.length;
}

// Data generation parameters
// Use all available hierarchy combinations and keep the generated data compact but representative.
var BATCHES_PER_EQUIPMENT = 3;
var CPP_POINTS_PER_BATCH = 6;
var ALARMS_PER_BATCH = 2;
var LIVE_STATES = ["RUNNING", "IDLE", "ERROR", "MAINTAINENCE", "OFFLINE"];

// Real pharmaceutical equipment types
var EQUIPMENT_TYPES = [
    { type: "WEG", name: "Wet Granulator", models: ["WEG-003"] },
    { type: "FBD", name: "Fluid Bed Dryer", models: ["FBD-004"] },
    { type: "BLE", name: "Bin Blender", models: ["BLE-003"] }
];

// TNT-0001, PLNT-0001, BLK-0001, ROOM-0001 - Based on the realtime_sample_data 

var BATCH_CATALOG = [
    {
        batchNo: "AMR0026001",
        lotNo: "LOT1",
        recipeName: "CelecoxibCaps100mg200mgCB",
        plantId: "PLNT-0001"
    },
    {
        batchNo: "AMR0026002",
        lotNo: "LOT2",
        recipeName: "MirtazapineTablets15mg30mg45mgCB",
        plantId: "PLNT-0001"
    },
    {
        batchNo: "AMR0026003",
        lotNo: "LOT3",
        recipeName: "MirtazapineTablets15mg30mg45mgCB",
        plantId: "PLNT-0001"
    },
    {
        batchNo: "AMR0026004",
        lotNo: "LOT4",
        recipeName: "MirtazapineTablets15mg30mg45mgCB",
        plantId: "PLNT-0001"
    },
    {
        batchNo: "AMR0026005",
        lotNo: "LOT5",
        recipeName: "MirtazapineTablets15mg30mg45mgCB",
        plantId: "PLNT-0001"
    },
    {
        batchNo: "AMR0026006",
        lotNo: "LOT6",
        recipeName: "MirtazapineTablets15mg30mg45mgCB",
        plantId: "PLNT-0001"
    },
    {
        batchNo: "AMR0026007",
        lotNo: "LOT7",
        recipeName: "MirtazapineTablets15mg30mg45mgCB",
        plantId: "PLNT-0001"
    }
];

var RECIPE_PRODUCT_MAP = {
    "MirtazapineTablets15mg30mg45mgCB": {
        productCode: "MIRTAZAPINE_TAB_15_30_45_CB",
        productName: "Mirtazapine Tablets 15mg/30mg/45mg CB",
        productCategory: "Tablets"
    },
    "CelecoxibCaps100mg200mgCB": {
        productCode: "CELECOXIB_CAP_100_200_CB",
        productName: "Celecoxib Capsules 100mg/200mg CB",
        productCategory: "Capsules"
    }
};

var STAGE_SEQUENCE = ["WEG", "FBD", "BLE"];
var EQUIPMENT_TYPE_DEFAULT_LOT = {
    WEG: "LOT1",
    FBD: "LOT2",
    BLE: "LOT3"
};

function normalizeBatchIdentity(rawBatchNo, rawLotNo) {
    var batchNo = (rawBatchNo || "").trim();
    var lotNo = (rawLotNo || "").trim();

    if (!batchNo) {
        return { batchNo: "", lotNo: "" };
    }

    if (!lotNo && batchNo.indexOf("-LOT") > 0) {
        var parts = batchNo.split("-LOT");
        if (parts.length === 2) {
            batchNo = parts[0];
            lotNo = "LOT" + parts[1];
        }
    }

    return {
        batchNo: batchNo,
        lotNo: lotNo
    };
}

function getBatchCatalogEntry(batchNo) {
    var normalizedBatchNo = (batchNo || "").trim();
    for (var i = 0; i < BATCH_CATALOG.length; i++) {
        if (BATCH_CATALOG[i].batchNo === normalizedBatchNo) {
            return BATCH_CATALOG[i];
        }
    }
    return null;
}

function getRecipeForBatch(batchNo, fallbackRecipeName) {
    var entry = getBatchCatalogEntry(batchNo);
    if (entry && entry.recipeName) {
        return entry.recipeName;
    }
    return fallbackRecipeName || "";
}

function getLotNoForEquipmentType(eqType, fallbackLotNo) {
    var mapped = EQUIPMENT_TYPE_DEFAULT_LOT[eqType];
    if (mapped) {
        return mapped;
    }
    return fallbackLotNo || "LOT1";
}

function parseObservedAt(rawObservedAt) {
    if (!rawObservedAt) return null;
    try {
        var dt = new Date(rawObservedAt);
        if (!isNaN(dt.getTime())) {
            return dt;
        }
    } catch (e) {}
    return null;
}

function normalizeStatus(rawStatus) {
    var status = (rawStatus || "").toString().trim().toUpperCase();
    if (!status) return "RUNNING";
    if (status.indexOf("STOP") >= 0 || status.indexOf("COMP") >= 0) return "STOP";
    if (status.indexOf("IDLE") >= 0) return "IDLE";
    if (status.indexOf("HOLD") >= 0) return "IDLE";
    return "RUNNING";
}

function normalizeRecipeName(rawRecipeName) {
    return (rawRecipeName || "").trim().replace(/\s+/g, "");
}

function recipeNameToFallbackTitle(recipeName) {
    var compact = normalizeRecipeName(recipeName);
    if (!compact) return "Unknown Product";

    var title = compact
        .replace(/([a-z])([A-Z])/g, "$1 $2")
        .replace(/(\d+mg)/g, " $1")
        .replace(/\s+/g, " ")
        .trim();

    return title;
}

function getProductFromRecipe(recipeName) {
    var normalized = normalizeRecipeName(recipeName);
    var mapped = RECIPE_PRODUCT_MAP[normalized];
    if (mapped) {
        return {
            recipeName: normalized,
            productCode: mapped.productCode,
            productName: mapped.productName,
            productCategory: mapped.productCategory
        };
    }

    var category = /caps/i.test(normalized) ? "Capsules" : "Tablets";
    return {
        recipeName: normalized,
        productCode: normalized ? normalized.replace(/[^A-Za-z0-9]+/g, "_").toUpperCase() : "UNKNOWN_PRODUCT",
        productName: recipeNameToFallbackTitle(normalized),
        productCategory: category
    };
}

var MAKES = ["GEA Pharma", "Glatt", "Fette", "Korsch", "Bohle", "SKPharma", "Apex Pharma Tech"];

// Real pharmaceutical products with hierarchy assignment
var PRODUCTS = [];

// Real alarm/events with categories
var ALARM_EVENTS = [
    { code: "CO MILL SEAL PRESSURE ERROR", category: "ALARM", severity: "HIGH", text: "Co-mill seal pressure error detected" },
    { code: "IMP_OVER_RANGE", category: "ALARM", severity: "HIGH", text: "Impeller above warning threshold" },
    { code: "TEMP_OVER_RANGE", category: "ALARM", severity: "HIGH", text: "Temperature above critical limit" },
    { code: "PRESS_UNDER_RANGE", category: "ALARM", severity: "MEDIUM", text: "Pressure below minimum requirement" },
    { code: "AIR_FLOW_LOW", category: "ALARM", severity: "MEDIUM", text: "Air flow below set point" },
    { code: "CHOPPER_OVER_RANGE", category: "ALARM", severity: "MEDIUM", text: "Chopper speed above warning threshold" },
    { code: "BED_TEMP_HIGH", category: "ALARM", severity: "HIGH", text: "Bed temperature above critical limit" },
    { code: "BATCH_PHASE_CHANGE", category: "EVENT", severity: "LOW", text: "Batch moved to next phase" },
    { code: "BATCH_START", category: "EVENT", severity: "LOW", text: "Batch started" },
    { code: "BATCH_COMPLETE", category: "EVENT", severity: "LOW", text: "Batch completed" },
    { code: "OPERATOR_CHANGE", category: "EVENT", severity: "LOW", text: "Operator changed" }
];

function logInfo(msg) {
    print("[IIOT-SEED] " + msg);
}

function now() {
    return new Date();
}

function pad2(value) {
    return (value < 10 ? "0" : "") + value;
}

function pad3(value) {
    return (value < 100 ? "0" + pad2(value) : "" + value);
}

function toIsoDate(value) {
    return ISODate(value.toISOString());
}

function addMinutes(base, minutes) {
    return new Date(base.getTime() + minutes * 60000);
}

function randomInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

function randomFloat(min, max, decimals) {
    decimals = decimals || 2;
    return Number((Math.random() * (max - min) + min).toFixed(decimals));
}

function randomChoice(array) {
    return array[Math.floor(Math.random() * array.length)];
}

function safeInsert(collectionName, docs) {
    if (!docs || docs.length === 0) return;
    try {
        var col = db.getCollection(collectionName);
        var batchSize = 100;
        for (var i = 0; i < docs.length; i += batchSize) {
            var batch = docs.slice(i, Math.min(i + batchSize, docs.length));
            col.insertMany(batch, { ordered: false });
        }
        logInfo("Inserted " + docs.length + " docs into " + collectionName);
    } catch (e) {
        logInfo("Error inserting into " + collectionName + ": " + e.message);
    }
}

function safeUpsert(collectionName, docs, keyField) {
    if (!docs || docs.length === 0) return;
    try {
        var col = db.getCollection(collectionName);
        var ops = [];
        docs.forEach(function (doc) {
            var filter = {};
            filter[keyField] = doc[keyField];
            ops.push({
                updateOne: {
                    filter: filter,
                    update: { $set: doc },
                    upsert: true
                }
            });
            if (ops.length >= 200) {
                col.bulkWrite(ops, { ordered: false });
                ops = [];
            }
        });
        if (ops.length > 0) {
            col.bulkWrite(ops, { ordered: false });
        }
        logInfo("Upserted " + docs.length + " docs into " + collectionName);
    } catch (e) {
        logInfo("Error upserting into " + collectionName + ": " + e.message);
    }
}

function ensureCollection(name) {
    try {
        var collections = db.getCollectionNames();
        if (collections.indexOf(name) === -1) {
            db.createCollection(name);
        }
        return true;
    } catch (e) {
        return false;
    }
}

function resetCollection(name) {
    try {
        if (ensureCollection(name)) {
            db.getCollection(name).deleteMany({});
            return true;
        }
    } catch (e) {}
    return false;
}

function createEquipmentDefinitions() {
    var defs = [];
    if (EXPLICIT_EQUIPMENT_DEFS.length > 0) {
        EXPLICIT_EQUIPMENT_DEFS.forEach(function(eq) {
            defs.push({
                equipmentId: eq.equipmentId,
                equipmentCode: eq.equipmentCode,
                equipmentName: eq.equipmentName,
                plantId: eq.plantId,
                blockId: eq.blockId,
                areaId: eq.areaId,
                roomId: eq.roomId,
                make: eq.make,
                model: eq.model,
                equipmentType: eq.equipmentType,
                equipmentTypeName: eq.equipmentTypeName,
                stageOrder: eq.stageOrder || 99,
                hierarchy: {
                    plant: eq.plantId,
                    block: eq.blockId,
                    area: eq.areaId,
                    room: eq.roomId,
                    fullPath: eq.plantId + "/" + eq.blockId + "/" + eq.areaId + "/" + eq.roomId + "/" + eq.equipmentCode
                }
            });
        });
        defs.sort(function(a, b) { return (a.stageOrder || 99) - (b.stageOrder || 99); });
        logInfo("Created " + defs.length + " explicit stage equipment definitions");
        return defs;
    }

    logInfo("Created " + defs.length + " equipment definitions");
    return defs;
}

var EQUIPMENT_DEFS = createEquipmentDefinitions();

function getTimeSeriesCppCollection(equipmentId) {
    return "iiot_ts_cpp_tnt_0001_" + equipmentId.toLowerCase().replace(/[^a-z0-9]+/g, "_");
}

function getTimeSeriesAlarmCollection(equipmentId) {
    return "iiot_ts_alarm_event_tnt_0001_" + equipmentId.toLowerCase().replace(/[^a-z0-9]+/g, "_");
}

function getTimeSeriesCollections() {
    var names = [];
    for (var i = 0; i < EQUIPMENT_DEFS.length; i++) {
        var eq = EQUIPMENT_DEFS[i];
        names.push(getTimeSeriesCppCollection(eq.equipmentId));
        names.push(getTimeSeriesAlarmCollection(eq.equipmentId));
    }
    return names;
}

function createIndexes() {
    logInfo("Creating indexes...");
    try {
        db.iiot_equiment_master.createIndex({ tenantId: 1, equipmentId: 1 }, { unique: true });
        db.iiot_equiment_master.createIndex({ plantId: 1, blockId: 1, areaId: 1, roomId: 1 });
        db.iiot_equiment_master.createIndex({ equipmentType: 1 });
        db.iiot_equiment_master.createIndex({ make: 1 });
        
        db.iiot_equipment_critical_parameters.createIndex(
            { tenantId: 1, equipmentId: 1, parameterId: 1 },
            { unique: true }
        );
        
        db.iiot_equipment_critical_parameters_limit.createIndex(
            { tenantId: 1, equipmentId: 1, parameterId: 1, effectiveFrom: -1 }
        );
        
        db.iiot_product_master.createIndex({ tenantId: 1, productId: 1 }, { unique: true });
        db.iiot_source_table_mapping.createIndex({ tenantId: 1, equipmentId: 1 }, { unique: true });
        db.iiot_ingestion_checkpoint.createIndex({ equipmentId: 1, streamType: 1 }, { unique: true });
        db.iiot_ingestion_job_run.createIndex({ equipmentId: 1, startedAt: -1 });
        db.iiot_equipment_live_status.createIndex({ equipmentId: 1 }, { unique: true });
        db.iiot_batch_summary.createIndex({ tenantId: 1, plantId: 1, areaId: 1, equipmentId: 1, batchNo: 1 }, { unique: true });
        db.iiot_batch_summary.createIndex({ plantId: 1, blockId: 1, areaId: 1, roomId: 1 });
        
        logInfo("Indexes created successfully");
    } catch (e) {
        logInfo("Error creating indexes: " + e.message);
    }
}

function getProductCatalog(ts) {
    var products = [];
    var seen = {};

    BATCH_CATALOG.forEach(function(batch) {
        var derivedProduct = getProductFromRecipe(batch.recipeName || batch.recipeCode);
        var key = derivedProduct.productCode;
        if (seen[key]) return;
        seen[key] = true;
        products.push({
            productId: key,
            productCode: key,
            productName: derivedProduct.productName,
            productCategory: derivedProduct.productCategory,
            tenantId: TENANT_ID,
            plantId: batch.plantId || "PLNT-0001",
            isActive: true,
            createdAt: ts,
            updatedAt: ts
        });
    });

    return products;
}

function buildParameterDocs(equipmentId, plantId, equipmentIndex, ts) {
    var eqType = (equipmentId || "").split("-")[0];
    var parameters = [];

    if (eqType === "WEG") {
        parameters = [
            { suffix: "AGI_SPD", code: "agitatorSpeed", name: "Agitator Speed", unitOfMeasure: "rpm", isCritical: true, baseValue: 8.0, lowWarn: 1.0, lowCrit: 2.0, highWarn: 2.0, highCrit: 4.0 },
            { suffix: "AGI_TOR", code: "agitatorTorque", name: "Agitator Torque", unitOfMeasure: "%", isCritical: true, baseValue: 10.0, lowWarn: 2.0, lowCrit: 4.0, highWarn: 6.0, highCrit: 10.0 },
            { suffix: "CHP_SPD", code: "chopperSpeed", name: "Chopper Speed", unitOfMeasure: "rpm", isCritical: true, baseValue: 1200.0, lowWarn: 150.0, lowCrit: 300.0, highWarn: 250.0, highCrit: 400.0 },
            { suffix: "PRC_TIME", code: "processTime", name: "Process Time", unitOfMeasure: "sec", isCritical: false, baseValue: 900.0, lowWarn: 120.0, lowCrit: 240.0, highWarn: 180.0, highCrit: 300.0 }
        ];
    } else if (eqType === "FBD") {
        parameters = [
            { suffix: "IN_AIR_TEMP", code: "inletAirTemperature", name: "Inlet Air Temperature", unitOfMeasure: "celsius", isCritical: true, baseValue: 65.0, lowWarn: 3.0, lowCrit: 6.0, highWarn: 5.0, highCrit: 8.0 },
            { suffix: "PRD_TEMP", code: "productTemperature", name: "Product Temperature", unitOfMeasure: "celsius", isCritical: true, baseValue: 45.0, lowWarn: 2.0, lowCrit: 4.0, highWarn: 4.0, highCrit: 6.0 },
            { suffix: "AIR_FLOW", code: "inletAirFlow", name: "Inlet Air Flow", unitOfMeasure: "m3/hr", isCritical: true, baseValue: 220.0, lowWarn: 20.0, lowCrit: 40.0, highWarn: 40.0, highCrit: 70.0 },
            { suffix: "EXH_TEMP", code: "exhaustAirTemperature", name: "Exhaust Air Temperature", unitOfMeasure: "celsius", isCritical: false, baseValue: 28.0, lowWarn: 3.0, lowCrit: 6.0, highWarn: 5.0, highCrit: 8.0 }
        ];
    } else if (eqType === "BLE") {
        parameters = [
            { suffix: "BLD_SPD", code: "blendingSpeed", name: "Blending Speed", unitOfMeasure: "rpm", isCritical: true, baseValue: 10.0, lowWarn: 1.0, lowCrit: 2.0, highWarn: 2.0, highCrit: 4.0 },
            { suffix: "BLD_TIME", code: "blendingTime", name: "Blending Time", unitOfMeasure: "min", isCritical: true, baseValue: 15.0, lowWarn: 2.0, lowCrit: 4.0, highWarn: 3.0, highCrit: 5.0 },
            { suffix: "BLD_REM", code: "blendingRemainingTime", name: "Blending Remaining Time", unitOfMeasure: "min", isCritical: false, baseValue: 8.0, lowWarn: 2.0, lowCrit: 3.0, highWarn: 2.0, highCrit: 4.0 }
        ];
    }

    var paramDocs = [];
    var limitDocs = [];

    parameters.forEach(function (p, idx) {
        var parameterId = p.code + "_" + pad3(equipmentIndex);
        var parameterCode = parameterId;
        var parameterLimitCode = "LIM-" + p.suffix + "-" + pad3(equipmentIndex);
        // Keep limit id and code aligned to match current code-first UI + API behavior.
        var parameterLimitId = parameterLimitCode;
        var parameterName = p.name + " #" + pad3(equipmentIndex);
        var base = p.baseValue;
        var lowWarnDelta = p.lowWarn || 1.0;
        var lowCritDelta = p.lowCrit || 2.0;
        var highWarnDelta = p.highWarn || 1.0;
        var highCritDelta = p.highCrit || 2.0;

        paramDocs.push({
            parameterSeqId: 50000 + equipmentIndex * 10 + idx,
            tenantId: TENANT_ID,
            plantId: plantId,
            equipmentId: equipmentId,
            parameterId: parameterId,
            parameterCode: parameterCode,
            parameterName: parameterName,
            parameterType: "FLOAT",
            unitOfMeasure: p.unitOfMeasure,
            isCritical: p.isCritical,
            isActive: true,
            createdAt: ts,
            updatedAt: ts
        });

        limitDocs.push({
            parameterLimitId: parameterLimitId,
            parameterLimitCode: parameterLimitCode,
            parameterLimitSeqId: 90000 + equipmentIndex * 10 + idx,
            tenantId: TENANT_ID,
            plantId: plantId,
            equipmentId: equipmentId,
            parameterId: parameterId,
            parameterCode: parameterCode,
            parameterName: parameterName,
            parameterType: "FLOAT",
            floatValue: Number(base.toFixed(2)),
            lowCriticalValue: Number((base - lowCritDelta).toFixed(2)),
            lowWarningValue: Number((base - lowWarnDelta).toFixed(2)),
            idealMinValue: Number((base - lowWarnDelta / 2).toFixed(2)),
            idealMaxValue: Number((base + highWarnDelta / 2).toFixed(2)),
            highWarningValue: Number((base + highWarnDelta).toFixed(2)),
            highCriticalValue: Number((base + highCritDelta).toFixed(2)),
            alarmEnabled: true,
            booleanValue: false,
            enumValue: "",
            stringValue: "",
            effectiveFrom: ISODate("2026-01-01T00:00:00Z"),
            effectiveTo: null,
            isActive: true,
            createdAt: ts,
            updatedAt: ts
        });
    });

    return { params: paramDocs, limits: limitDocs };
}

function ensureCriticalParameterLimitRecords(expectedDocs) {
    var collection = db.getCollection("iiot_equipment_critical_parameters_limit");
    var currentCount = collection.countDocuments({});
    var expectedCount = expectedDocs && expectedDocs.length ? expectedDocs.length : 0;

    if (currentCount >= expectedCount) {
        logInfo("Critical parameter limits verified: " + currentCount + " records");
        return currentCount;
    }

    logInfo("Critical parameter limits missing; re-seeding " + expectedCount + " records");
    var docsToInsert = [];
    expectedDocs.forEach(function(doc) {
        var existing = collection.findOne({ parameterLimitId: doc.parameterLimitId });
        if (!existing) {
            docsToInsert.push(doc);
        }
    });

    if (docsToInsert.length > 0) {
        collection.insertMany(docsToInsert, { ordered: false });
    }

    var finalCount = collection.countDocuments({});
    logInfo("Critical parameter limits after re-seed: " + finalCount);
    return finalCount;
}

function seedMasterData() {
    logInfo("Seeding master data for " + EQUIPMENT_DEFS.length + " equipment...");
    try {
        var ts = now();
        var equipmentDocs = [];
        var parameterDocs = [];
        var parameterLimitDocs = [];
        var sourceMappings = [];
        var productDocs = getProductCatalog(ts);
        
        var totalProcessed = 0;

        EQUIPMENT_DEFS.forEach(function (eq, index) {
            equipmentDocs.push({
                equipmentSeqId: 10000 + index + 1,
                tenantId: TENANT_ID,
                plantId: eq.plantId,
                blockId: eq.blockId,
                areaId: eq.areaId,
                roomId: eq.roomId,
                equipmentId: eq.equipmentId,
                equipmentCode: eq.equipmentCode,
                equipmentName: eq.equipmentName,
                equipmentType: eq.equipmentType,
                equipmentTypeName: eq.equipmentTypeName,
                make: eq.make,
                model: eq.model,
                isActive: true,
                isDeleted: false,
                createdAt: ts,
                updatedAt: ts,
                hierarchy: eq.hierarchy,
                equipmentLocation: eq.hierarchy.fullPath
            });

            var paramPayload = buildParameterDocs(eq.equipmentId, eq.plantId, index + 1, ts);
            parameterDocs = parameterDocs.concat(paramPayload.params);
            parameterLimitDocs = parameterLimitDocs.concat(paramPayload.limits);

            var batchSourceTableName = eq.equipmentId === "RMG-100L-2-PVII"
                ? "SKPharma::CDSSKPharma.B_UDA_RMG_100L_P7_2"
                : "SKPharma::CDSSKPharma.B_UDA_" + eq.equipmentCode;
            var alarmSourceTableName = eq.equipmentId === "RMG-100L-2-PVII"
                ? "SKPharma::CDSSKPharma.AE_RMG100L_P7_2"
                : "SKPharma::CDSSKPharma.AE_" + eq.equipmentCode;

            sourceMappings.push({
                mappingId: "MAP-" + TENANT_ID + "-" + eq.equipmentCode,
                tenantId: TENANT_ID,
                equipmentId: eq.equipmentId,
                batchSource: {
                    dbType: "SAP_HANA",
                    schemaName: "SKPharma",
                    tableName: batchSourceTableName,
                    sequenceColumn: "SerialNumber",
                    timestampColumn: "LastModifiedTime"
                },
                alarmEventSource: {
                    dbType: "SAP_HANA",
                    schemaName: "SKPharma",
                    tableName: alarmSourceTableName,
                    sequenceColumn: "id",
                    timestampColumn: "LastModifiedTime"
                },
                pollIntervalSeconds: 30,
                batchSize: 1000,
                connectionRef: "SAP-HANA-DEV-01",
                validationStatus: "SUCCESS",
                lastValidatedAt: ts,
                isActive: true,
                updatedAt: ts,
                hierarchy: eq.hierarchy
            });

            totalProcessed++;
            if (totalProcessed % 20 === 0) {
                logInfo("  Processed " + totalProcessed + "/" + EQUIPMENT_DEFS.length + " equipment");
            }
        });

        safeUpsert("iiot_equiment_master", equipmentDocs, "equipmentId");
        safeUpsert("iiot_product_master", productDocs, "productId");
        safeUpsert("iiot_equipment_critical_parameters", parameterDocs, "parameterId");
        safeUpsert("iiot_equipment_critical_parameters_limit", parameterLimitDocs, "parameterLimitId");
        ensureCriticalParameterLimitRecords(parameterLimitDocs);
        safeUpsert("iiot_source_table_mapping", sourceMappings, "mappingId");
        
        logInfo("Master data seeded: " + equipmentDocs.length + " equipment");
    } catch (e) {
        logInfo("ERROR in seedMasterData: " + e.message);
    }
}

function loadRealtimeSheetRecords() {
    var candidates = [
        "/tmp/realtime_sample_data_json",
        "realtime_sample_data/json",
        "/seed/realtime_sample_data/json",
        "/docker-entrypoint-initdb.d/realtime_sample_data/json"
    ];

    var eqTypes = ["weg", "fbd", "ble"];
    var allRecords = [];

    for (var c = 0; c < candidates.length; c++) {
        var basePath = candidates[c];
        var loadedAny = false;

        for (var i = 0; i < eqTypes.length; i++) {
            var p = basePath + "/" + eqTypes[i] + ".json";
            var content = readTextFile(p);
            if (!content) {
                continue;
            }

            try {
                var parsed = JSON.parse(content);
                if (parsed && parsed.length) {
                    for (var r = 0; r < parsed.length; r++) {
                        allRecords.push(parsed[r]);
                    }
                    loadedAny = true;
                }
            } catch (e) {
                logInfo("Failed to parse realtime JSON file: " + p + " => " + e.message);
            }
        }

        if (loadedAny) {
            logInfo("Loaded realtime sheet payloads from: " + basePath + " (records=" + allRecords.length + ")");
            return allRecords;
        }
    }

    return [];
}

function seedRealtimeSheetIngestionData() {
    var realtimeRecords = loadRealtimeSheetRecords();
    if (!realtimeRecords || realtimeRecords.length === 0) {
        logInfo("Realtime sheet payloads not found. Falling back to synthetic ingestion data.");
        return false;
    }

    var productCatalog = db.iiot_product_master.find({ tenantId: TENANT_ID, isActive: true }).toArray();
    if (productCatalog.length === 0) {
        logInfo("No products found. Skipping realtime ingestion.");
        return false;
    }

    var productByCode = {};
    productCatalog.forEach(function(p) { productByCode[p.productCode] = p; });

    var eqByType = {};
    EQUIPMENT_DEFS.forEach(function(eq) {
        eqByType[eq.equipmentType] = eq;
    });

    var grouped = {};
    realtimeRecords.forEach(function(rec) {
        if (!rec || !rec.equipmentType) return;
        var eqType = (rec.equipmentType || "").toString().trim().toUpperCase();
        if (!eqByType[eqType]) return;
        if (!grouped[eqType]) grouped[eqType] = [];
        grouped[eqType].push(rec);
    });

    var checkpointDocs = [];
    var jobRunDocs = [];
    var batchSummaryDocs = [];
    var totalCppRecords = 0;
    var totalAlarmRecords = 0;
    var totalEventRecords = 0;

    Object.keys(grouped).forEach(function(eqType) {
        var eq = eqByType[eqType];
        var records = grouped[eqType];
        if (!records || records.length === 0) return;

        records.sort(function(a, b) {
            var da = parseObservedAt(a.observedAt);
            var dbv = parseObservedAt(b.observedAt);
            return (da ? da.getTime() : 0) - (dbv ? dbv.getTime() : 0);
        });

        var cppCollection = getTimeSeriesCppCollection(eq.equipmentId);
        var alarmCollection = getTimeSeriesAlarmCollection(eq.equipmentId);
        ensureCollection(cppCollection);
        ensureCollection(alarmCollection);

        var cppDocs = [];
        var alarmDocs = [];
        var batchStats = {};
        var seqBase = 100000 + (eq.stageOrder || 1) * 1000000;
        var alarmSeqBase = 200000 + (eq.stageOrder || 1) * 1000000;

        for (var idx = 0; idx < records.length; idx++) {
            var rec = records[idx];
            var observedAt = parseObservedAt(rec.observedAt);
            if (!observedAt) continue;

            var normalizedBatch = normalizeBatchIdentity(rec.batchNo || "", "");
            var batchNo = normalizedBatch.batchNo;
            if (!batchNo) continue;

            var lotNo = getLotNoForEquipmentType(eqType, normalizedBatch.lotNo);
            var recipeName = getRecipeForBatch(batchNo, rec.recipeNameRaw);
            var recipeProduct = getProductFromRecipe(recipeName);
            var product = productByCode[recipeProduct.productCode] || {
                productCode: recipeProduct.productCode,
                productName: recipeProduct.productName,
                productCategory: recipeProduct.productCategory
            };

            var status = normalizeStatus(rec.statusRaw || (rec.metrics && rec.metrics.batchStatus) || (rec.metrics && rec.metrics.processManualAutoStatus));
            var sourceSeqId = seqBase + idx + 1;
            var operatorName = (rec.operatorNameRaw || "").toString().trim() || "SYSTEM";
            var metrics = rec.metrics || {};
            metrics.status = status;

            cppDocs.push({
                observedAt: toIsoDate(observedAt),
                meta: {
                    tenantId: TENANT_ID,
                    equipmentId: eq.equipmentId,
                    batchNo: batchNo,
                    lotNo: lotNo,
                    productName: product.productName,
                    productCode: product.productCode,
                    productCategory: product.productCategory,
                    recipeName: recipeProduct.recipeName,
                    operatorName: operatorName,
                    supervisorName: operatorName,
                    equipmentType: eq.equipmentType,
                    equipmentName: eq.equipmentName,
                    equipmentLocation: eq.hierarchy.fullPath,
                    status: status,
                    dateDay: observedAt.getDate(),
                    dayMonth: observedAt.getMonth() + 1,
                    dayYear: observedAt.getFullYear(),
                    timeHH: pad2(observedAt.getHours()),
                    timeMM: pad2(observedAt.getMinutes()),
                    timeSS: pad2(observedAt.getSeconds())
                },
                source: {
                    tableName: "SKPharma::CDSSKPharma.B_UDA_" + eq.equipmentCode,
                    sourceSeqId: sourceSeqId,
                    lastModifiedTime: toIsoDate(observedAt),
                    machineDate: observedAt.toISOString().slice(0, 19).replace("T", " ")
                },
                metrics: metrics,
                ingestedAt: now()
            });
            totalCppRecords++;

            if (!batchStats[batchNo]) {
                batchStats[batchNo] = {
                    minAt: observedAt,
                    maxAt: observedAt,
                    cppCount: 0,
                    alarmCount: 0,
                    eventCount: 0,
                    operatorName: operatorName,
                    product: product,
                    recipeProduct: recipeProduct,
                    lotNo: lotNo
                };
            }
            batchStats[batchNo].cppCount++;
            if (observedAt < batchStats[batchNo].minAt) batchStats[batchNo].minAt = observedAt;
            if (observedAt > batchStats[batchNo].maxAt) batchStats[batchNo].maxAt = observedAt;

            // Alarm/Event fallback when realtime alarm feed is not available.
            if (idx === 0 || (idx > 0 && records[idx - 1].batchNo !== batchNo)) {
                alarmSeqBase++;
                alarmDocs.push({
                    eventAt: toIsoDate(observedAt),
                    meta: {
                        equipmentId: eq.equipmentId,
                        batchNo: batchNo,
                        lotNo: lotNo,
                        productName: product.productName,
                        productCode: product.productCode,
                        operatorName: operatorName,
                        supervisorName: operatorName,
                        equipmentType: eq.equipmentType,
                        equipmentName: eq.equipmentName,
                        equipmentLocation: eq.hierarchy.fullPath,
                        status: "RUNNING",
                        dateDay: observedAt.getDate(),
                        dayMonth: observedAt.getMonth() + 1,
                        dayYear: observedAt.getFullYear(),
                        timeHH: pad2(observedAt.getHours()),
                        timeMM: pad2(observedAt.getMinutes()),
                        timeSS: pad2(observedAt.getSeconds())
                    },
                    source: {
                        tableName: "SKPharma::CDSSKPharma.AE_" + eq.equipmentCode,
                        sourceSeqId: alarmSeqBase,
                        lastModifiedTime: toIsoDate(observedAt),
                        machineDate: observedAt.toISOString().slice(0, 19).replace("T", " ")
                    },
                    event: {
                        eventCategory: "EVENT",
                        eventCode: "BATCH_START",
                        eventText: "Batch started from realtime sheet ingestion",
                        severity: "LOW",
                        eventState: "INFO",
                        alarmAll: "",
                        eventAll: ";BATCH_START;"
                    },
                    ingestedAt: now()
                });
                batchStats[batchNo].eventCount++;
                totalEventRecords++;
            }
        }

        // Close each batch with a completion event.
        Object.keys(batchStats).forEach(function(batchNo) {
            var stat = batchStats[batchNo];
            var endAt = stat.maxAt || now();
            alarmSeqBase++;
            alarmDocs.push({
                eventAt: toIsoDate(endAt),
                meta: {
                    equipmentId: eq.equipmentId,
                    batchNo: batchNo,
                    lotNo: stat.lotNo,
                    productName: stat.product.productName,
                    productCode: stat.product.productCode,
                    operatorName: stat.operatorName,
                    supervisorName: stat.operatorName,
                    equipmentType: eq.equipmentType,
                    equipmentName: eq.equipmentName,
                    equipmentLocation: eq.hierarchy.fullPath,
                    status: "STOP",
                    dateDay: endAt.getDate(),
                    dayMonth: endAt.getMonth() + 1,
                    dayYear: endAt.getFullYear(),
                    timeHH: pad2(endAt.getHours()),
                    timeMM: pad2(endAt.getMinutes()),
                    timeSS: pad2(endAt.getSeconds())
                },
                source: {
                    tableName: "SKPharma::CDSSKPharma.AE_" + eq.equipmentCode,
                    sourceSeqId: alarmSeqBase,
                    lastModifiedTime: toIsoDate(endAt),
                    machineDate: endAt.toISOString().slice(0, 19).replace("T", " ")
                },
                event: {
                    eventCategory: "EVENT",
                    eventCode: "BATCH_COMPLETE",
                    eventText: "Batch completed from realtime sheet ingestion",
                    severity: "LOW",
                    eventState: "INFO",
                    alarmAll: "",
                    eventAll: ";BATCH_COMPLETE;"
                },
                ingestedAt: now()
            });
            stat.eventCount++;
            totalEventRecords++;

            batchSummaryDocs.push({
                tenantId: TENANT_ID,
                plantId: eq.plantId,
                blockId: eq.blockId,
                areaId: eq.areaId,
                roomId: eq.roomId,
                equipmentId: eq.equipmentId,
                batchNo: batchNo,
                lotNo: stat.lotNo,
                productName: stat.product.productName,
                productCode: stat.product.productCode,
                equipmentType: eq.equipmentType,
                equipmentName: eq.equipmentName,
                equipmentLocation: eq.hierarchy.fullPath,
                batchSize: "",
                operatorName: stat.operatorName,
                supervisorName: stat.operatorName,
                batchStartAt: toIsoDate(stat.minAt || now()),
                batchEndAt: toIsoDate(stat.maxAt || now()),
                batchStatus: "COMPLETED",
                cppRecordCount: stat.cppCount,
                alarmCount: stat.alarmCount,
                eventCount: stat.eventCount,
                productionCount: randomInt(1000, 5000),
                createdAt: now(),
                updatedAt: now(),
                hierarchy: eq.hierarchy
            });
        });

        safeInsert(cppCollection, cppDocs);
        safeInsert(alarmCollection, alarmDocs);

        checkpointDocs.push({
            checkpointId: "CP-" + eq.equipmentId + "-BATCH_CPP",
            equipmentId: eq.equipmentId,
            streamType: "BATCH_CPP",
            sourceTable: "SKPharma::CDSSKPharma.B_UDA_" + eq.equipmentCode,
            lastProcessedSeqId: seqBase + records.length,
            lastProcessedAt: now(),
            status: "SUCCESS",
            updatedAt: now()
        });

        checkpointDocs.push({
            checkpointId: "CP-" + eq.equipmentId + "-ALARM_EVENT",
            equipmentId: eq.equipmentId,
            streamType: "ALARM_EVENT",
            sourceTable: "SKPharma::CDSSKPharma.AE_" + eq.equipmentCode,
            lastProcessedSeqId: alarmSeqBase,
            lastProcessedAt: now(),
            status: "SUCCESS",
            updatedAt: now()
        });

        jobRunDocs.push({
            jobRunId: "JOB-REALTIME-" + eq.equipmentType + "-CPP",
            equipmentId: eq.equipmentId,
            streamType: "BATCH_CPP",
            windowStartSeqId: seqBase + 1,
            windowEndSeqId: seqBase + records.length,
            recordsRead: records.length,
            recordsWritten: records.length,
            recordsSkipped: 0,
            status: "SUCCESS",
            startedAt: toIsoDate(now()),
            completedAt: toIsoDate(now()),
            createdAt: now(),
            updatedAt: now()
        });

        jobRunDocs.push({
            jobRunId: "JOB-REALTIME-" + eq.equipmentType + "-ALARM",
            equipmentId: eq.equipmentId,
            streamType: "ALARM_EVENT",
            windowStartSeqId: 1,
            windowEndSeqId: alarmSeqBase,
            recordsRead: alarmDocs.length,
            recordsWritten: alarmDocs.length,
            recordsSkipped: 0,
            status: "SUCCESS",
            startedAt: toIsoDate(now()),
            completedAt: toIsoDate(now()),
            createdAt: now(),
            updatedAt: now()
        });
    });

    // Pipeline summaries expect shared batch number and lot per equipment type.
    var perBatchPipeline = {};
    batchSummaryDocs.forEach(function(doc) {
        if (!doc.batchNo) return;
        if (!perBatchPipeline[doc.batchNo]) {
            perBatchPipeline[doc.batchNo] = {
                startAt: doc.batchStartAt,
                endAt: doc.batchEndAt,
                productName: doc.productName,
                productCode: doc.productCode,
                operatorName: doc.operatorName,
                supervisorName: doc.supervisorName,
                cppCount: 0,
                alarmCount: 0,
                eventCount: 0,
                stageStatus: {}
            };
        }

        var agg = perBatchPipeline[doc.batchNo];
        if (doc.batchStartAt < agg.startAt) agg.startAt = doc.batchStartAt;
        if (doc.batchEndAt > agg.endAt) agg.endAt = doc.batchEndAt;
        agg.cppCount += doc.cppRecordCount || 0;
        agg.alarmCount += doc.alarmCount || 0;
        agg.eventCount += doc.eventCount || 0;
        agg.stageStatus[doc.equipmentType] = "COMPLETED";
    });

    Object.keys(perBatchPipeline).forEach(function(batchNo) {
        var agg = perBatchPipeline[batchNo];
        batchSummaryDocs.push({
            tenantId: TENANT_ID,
            plantId: "PLNT-0001",
            blockId: "BLK-0001",
            areaId: "AREA-0001",
            roomId: "ROOM-0001",
            equipmentId: "PIPELINE-" + batchNo,
            batchNo: batchNo,
            lotNo: "LOT-PIPELINE",
            productName: agg.productName,
            productCode: agg.productCode,
            equipmentType: "PIPELINE",
            equipmentName: "WEG->FBD->BLE Pipeline",
            equipmentLocation: "PLNT-0001/BLK-0001/AREA-0001/ROOM-0001/PIPELINE",
            batchSize: "",
            operatorName: agg.operatorName,
            supervisorName: agg.supervisorName,
            batchStartAt: agg.startAt,
            batchEndAt: agg.endAt,
            batchStatus: "COMPLETED",
            cppRecordCount: agg.cppCount,
            alarmCount: agg.alarmCount,
            eventCount: agg.eventCount,
            productionCount: randomInt(1000, 5000),
            stageStatus: {
                WEG: agg.stageStatus.WEG || "PENDING",
                FBD: agg.stageStatus.FBD || "PENDING",
                BLE: agg.stageStatus.BLE || "PENDING"
            },
            createdAt: now(),
            updatedAt: now(),
            hierarchy: {
                plant: "PLNT-0001",
                block: "BLK-0001",
                area: "AREA-0001",
                room: "ROOM-0001",
                fullPath: "PLNT-0001/BLK-0001/AREA-0001/ROOM-0001/PIPELINE"
            }
        });
    });

    safeInsert("iiot_ingestion_checkpoint", checkpointDocs);
    safeInsert("iiot_ingestion_job_run", jobRunDocs);
    safeInsert("iiot_batch_summary", batchSummaryDocs);

    logInfo("Realtime ingestion from sheets completed!");
    logInfo("  Total CPP records: " + totalCppRecords);
    logInfo("  Synthetic Alarm records: " + totalAlarmRecords);
    logInfo("  Synthetic Event records: " + totalEventRecords);
    logInfo("  Total Batch Summaries: " + batchSummaryDocs.length);
    return true;
}

function seedIngestionData() {
    logInfo("Seeding ingestion data...");
    try {
        var productCatalog = db.iiot_product_master.find({ tenantId: TENANT_ID, isActive: true }).toArray();
        if (productCatalog.length === 0) {
            logInfo("No products found. Skipping ingestion data.");
            return;
        }

        var productByCode = {};
        productCatalog.forEach(function(p) { productByCode[p.productCode] = p; });

        var baseDate = new Date("2026-07-01T00:00:00Z");
        logInfo("Processing " + BATCH_CATALOG.length + " batches across " + EQUIPMENT_DEFS.length + " stages");

        var checkpointDocs = [];
        var jobRunDocs = [];
        var batchSummaryDocs = [];
        var totalCppRecords = 0;
        var totalAlarmRecords = 0;
        function getTelemetryMetrics(eqType, pointIdx, recipeProduct, batchNo, lotNo, operatorName) {
            var isRunning = pointIdx < CPP_POINTS_PER_BATCH - 1;
            var status = isRunning ? "RUNNING" : "STOP";
            var stepCounter = pointIdx + 1;

            if (eqType === "WEG") {
                var wetStepName = isRunning ? (pointIdx < 2 ? "DRY_MIXING" : (pointIdx < 4 ? "WET_MIXING" : "DISCHARGE_PREP")) : "COMPLETE";
                return {
                    batchNo: batchNo,
                    lotNo: lotNo,
                    recipeName: recipeProduct.recipeName,
                    recipeNameWIP: recipeProduct.recipeName,
                    userId: operatorName,
                    agitatorCurrent: randomFloat(5.0, 9.0, 2),
                    agitatorPauseTimePV: randomInt(2, 8),
                    agitatorPauseTimeSV: 5,
                    agitatorRunTimePV: randomInt(20, 90),
                    agitatorRunTimeSV: 60,
                    agitatorSpeedPV: randomFloat(7.2, 9.5, 2),
                    agitatorSpeedSV: 8.5,
                    agitatorTorque: randomFloat(8.0, 16.0, 2),
                    agitatorTorqueDlyMaxPV: randomFloat(12.0, 18.0, 2),
                    agitatorTorqueDlyMaxSV: 16.0,
                    agitatorTorqueMax: randomFloat(16.0, 20.0, 2),
                    agitatorTorqueMin: randomFloat(3.0, 7.0, 2),
                    chopperCurrent: randomFloat(0.3, 1.9, 2),
                    chopperSpeedPV: randomFloat(1000, 1400, 0),
                    chopperSpeedSV: 1200,
                    operationTimeProcessPV: pointIdx * 90,
                    processManualAutoCSD: "AUTO",
                    processManualAutoStatus: "AUTO",
                    processTimePV: pointIdx * 90,
                    rotorSieveCurrent: randomFloat(0.6, 2.2, 2),
                    rotorSieveSpeedPV: randomFloat(200, 380, 0),
                    rotorSieveSpeedSV: 300,
                    sprayPausePV: randomInt(2, 12),
                    sprayPauseSV: 5,
                    sprayTimePV: randomInt(30, 120),
                    sprayTimeSV: 90,
                    stepCounterProcess: stepCounter,
                    stepCounterWIP: stepCounter,
                    stepNameProcess: wetStepName,
                    stepNameWIP: wetStepName,
                    operationTime: pointIdx * 90,
                    processTime: pointIdx * 90,
                    status: status
                };
            }
            if (eqType === "FBD") {
                var fbdPhaseName = isRunning ? (pointIdx < 2 ? "PREHEAT" : (pointIdx < 4 ? "DRYING" : "COOLING")) : "COMPLETE";
                return {
                    batchEnable: 1,
                    batchNumber: batchNo + "-" + lotNo,
                    batchNumberWIP: batchNo + "-" + lotNo,
                    batchRequest: 1,
                    batchStatus: status,
                    dehumidificationTempPV: randomFloat(20, 30, 1),
                    dehumidificationTempSV: 25,
                    ecfActProdPhaseNamePTX: fbdPhaseName,
                    ecfActProdPhaseNumberSET: stepCounter,
                    ecfActProdPhaseStatusSTA: status,
                    ecfActProdPhaseTypeSET: "PROD",
                    ecwActWipPhaseNamePTX: fbdPhaseName,
                    ecwActWipPhaseNumberSET: stepCounter,
                    ecwActWipPhaseStatusSTA: status,
                    ecwActWipPhaseTypeSET: "WIP",
                    epfProdProcEnable: 1,
                    epfProdProcEnableCSD: 1,
                    epwWipPhaseNumberRequest: stepCounter,
                    exhaustAirFanSpeedPV: randomFloat(40, 75, 1),
                    exhaustAirTempPV: randomFloat(24, 34, 1),
                    inletAirTemperaturePV: randomFloat(62, 72, 1),
                    inletAirTemperatureSV: 68,
                    productTemperaturePV: randomFloat(42, 50, 1),
                    productTemperatureMaxSET: 52,
                    productTemperatureMinSET: 40,
                    inletAirFlowPV: randomFloat(190, 260, 1),
                    inletAirFlowSV: 230,
                    inletAirHumidityRelativePV: randomFloat(25, 45, 1),
                    inletAirModulatingFlapPV: randomFloat(30, 70, 1),
                    inletAirModulatingFlapSV: 55,
                    p01PreheaterTempSET: 70,
                    recipeName: recipeProduct.recipeName,
                    recipeNameWIP: recipeProduct.recipeName,
                    recipeState: status,
                    user: operatorName,
                    operationTime: pointIdx * 120,
                    exhaustAirTempMaxSET: 38,
                    exhaustAirTempMinSET: 22,
                    inletAirTemperatureMaxSET: 74,
                    inletAirTemperatureMinSET: 60,
                    status: status
                };
            }

            var blendingRemainingMin = Math.max(0, 15 - pointIdx * 2);
            var blendingRemainingSec = Math.max(0, 60 - pointIdx * 8);
            return {
                batchNumber: batchNo,
                blendingSpeedPV: randomFloat(8.5, 11.0, 2),
                blendingSpeedSV: 10.0,
                recipeName: recipeProduct.recipeName,
                stepName: isRunning ? (pointIdx < 2 ? "LOADING" : (pointIdx < 4 ? "BLENDING" : "UNLOADING")) : "COMPLETE",
                stepNo: stepCounter,
                userId: operatorName,
                blendingSetTime: 15,
                blendingSetTimeMin: 15,
                blendingRemainingTimeMin: blendingRemainingMin,
                blendingRemainingTimeSec: blendingRemainingSec,
                blendingStart: pointIdx === 0 ? 1 : 0,
                blendingAbort: 0,
                blendingResume: 0,
                status: status
            };
        }

        function getIndustryEventTemplates(eqType) {
            if (eqType === "WEG") {
                return [
                    { code: "BATCH_START", category: "EVENT", severity: "LOW", text: "Granulation started" },
                    { code: "AGITATOR_TORQUE_HIGH", category: "ALARM", severity: "MEDIUM", text: "Agitator torque above warning" },
                    { code: "CHOPPER_SPEED_DEVIATION", category: "ALARM", severity: "HIGH", text: "Chopper speed deviation beyond limit" },
                    { code: "BATCH_COMPLETE", category: "EVENT", severity: "LOW", text: "Granulation completed" }
                ];
            }
            if (eqType === "FBD") {
                return [
                    { code: "BATCH_START", category: "EVENT", severity: "LOW", text: "Drying started" },
                    { code: "INLET_TEMP_HIGH", category: "ALARM", severity: "HIGH", text: "Inlet temperature above threshold" },
                    { code: "AIR_FLOW_LOW", category: "ALARM", severity: "MEDIUM", text: "Inlet air flow below setpoint" },
                    { code: "BATCH_COMPLETE", category: "EVENT", severity: "LOW", text: "Drying completed" }
                ];
            }
            return [
                { code: "BATCH_START", category: "EVENT", severity: "LOW", text: "Blending started" },
                { code: "BLEND_SPEED_DEVIATION", category: "ALARM", severity: "MEDIUM", text: "Blend speed outside control band" },
                { code: "BLEND_TIME_OVER", category: "ALARM", severity: "LOW", text: "Blend cycle exceeded planned duration" },
                { code: "BATCH_COMPLETE", category: "EVENT", severity: "LOW", text: "Blending completed" }
            ];
        }

        for (var batchIdx = 0; batchIdx < BATCH_CATALOG.length; batchIdx++) {
            var batch = BATCH_CATALOG[batchIdx];
            var normalizedBatch = normalizeBatchIdentity(batch.batchNo, batch.lotNo);
            var batchNo = normalizedBatch.batchNo;
            var lotNo = normalizedBatch.lotNo;
            var recipeProduct = getProductFromRecipe(batch.recipeName || batch.recipeCode);
            var productCode = recipeProduct.productCode;
            var product = productByCode[productCode] || {
                productCode: recipeProduct.productCode,
                productName: recipeProduct.productName,
                productCategory: recipeProduct.productCategory
            };
            var operators = ["KRISHNA", "PRATIK", "RAJESH", "SURESH", "AMIT", "VIKAS"];
            var operatorName = operators[batchIdx % operators.length];
            var supervisorName = operators[(batchIdx + 2) % operators.length];
            var batchSize = (22 + batchIdx * 3) + ".000KG";
            var pipelineStageStatus = {};
            var pipelineStartAt = null;
            var pipelineEndAt = null;
            var pipelineAlarmCount = 0;
            var pipelineEventCount = 0;
            var pipelineCppCount = 0;

            for (var eqIdx = 0; eqIdx < EQUIPMENT_DEFS.length; eqIdx++) {
                var eq = EQUIPMENT_DEFS[eqIdx];
                var cppCollection = getTimeSeriesCppCollection(eq.equipmentId);
                var alarmCollection = getTimeSeriesAlarmCollection(eq.equipmentId);
                ensureCollection(cppCollection);
                ensureCollection(alarmCollection);

                var cppDocs = [];
                var alarmEventDocs = [];
                var latestCpp = null;
                var cppSeq = 100000 + batchIdx * 10000 + eqIdx * 1000;
                var alarmSeq = 200000 + batchIdx * 10000 + eqIdx * 1000;

                var batchStart = addMinutes(baseDate, batchIdx * 120 + eqIdx * 35);
                for (var pointIdx = 0; pointIdx < CPP_POINTS_PER_BATCH; pointIdx++) {
                    cppSeq += 1;
                    var observedAt = addMinutes(batchStart, pointIdx * 2 + randomInt(0, 1));
                    var metrics = getTelemetryMetrics(eq.equipmentType, pointIdx, recipeProduct, batchNo, lotNo, operatorName);

                    var cppDoc = {
                        observedAt: toIsoDate(observedAt),
                        meta: {
                            equipmentId: eq.equipmentId,
                            batchNo: batchNo,
                            lotNo: lotNo,
                            productName: product.productName,
                            productCode: product.productCode,
                            productCategory: product.productCategory,
                            recipeName: recipeProduct.recipeName,
                            operatorName: operatorName,
                            supervisorName: supervisorName,
                            equipmentType: eq.equipmentType,
                            equipmentName: eq.equipmentName,
                            equipmentLocation: eq.hierarchy.fullPath,
                            status: metrics.status,
                            dateDay: observedAt.getDate(),
                            dayMonth: observedAt.getMonth() + 1,
                            dayYear: observedAt.getFullYear(),
                            timeHH: pad2(observedAt.getHours()),
                            timeMM: pad2(observedAt.getMinutes()),
                            timeSS: pad2(observedAt.getSeconds())
                        },
                        source: {
                            tableName: "SKPharma::CDSSKPharma.B_UDA_" + eq.equipmentCode,
                            sourceSeqId: cppSeq,
                            lastModifiedTime: toIsoDate(observedAt),
                            machineDate: observedAt.toISOString().slice(0, 19).replace("T", " ")
                        },
                        metrics: metrics,
                        ingestedAt: now()
                    };
                    cppDocs.push(cppDoc);
                    latestCpp = cppDoc;
                    totalCppRecords++;
                }

                var stageEvents = getIndustryEventTemplates(eq.equipmentType);
                for (var alarmIdx = 0; alarmIdx < stageEvents.length; alarmIdx++) {
                    alarmSeq += 1;
                    var eventAt = addMinutes(batchStart, 2 + alarmIdx * 4 + randomInt(0, 1));
                    var stageEvent = stageEvents[alarmIdx];
                    var isAlarm = stageEvent.category === "ALARM";

                    alarmEventDocs.push({
                        eventAt: toIsoDate(eventAt),
                        meta: {
                            equipmentId: eq.equipmentId,
                            batchNo: batchNo,
                            lotNo: lotNo,
                            productName: product.productName,
                            productCode: product.productCode,
                            productCategory: product.productCategory,
                            recipeName: recipeProduct.recipeName,
                            operatorName: operatorName,
                            supervisorName: supervisorName,
                            equipmentType: eq.equipmentType,
                            equipmentName: eq.equipmentName,
                            equipmentLocation: eq.hierarchy.fullPath,
                            status: "RUNNING",
                            dateDay: eventAt.getDate(),
                            dayMonth: eventAt.getMonth() + 1,
                            dayYear: eventAt.getFullYear(),
                            timeHH: pad2(eventAt.getHours()),
                            timeMM: pad2(eventAt.getMinutes()),
                            timeSS: pad2(eventAt.getSeconds())
                        },
                        source: {
                            tableName: "SKPharma::CDSSKPharma.AE_" + eq.equipmentCode,
                            sourceSeqId: alarmSeq,
                            lastModifiedTime: toIsoDate(eventAt),
                            machineDate: eventAt.toISOString().slice(0, 19).replace("T", " ")
                        },
                        event: {
                            eventCategory: stageEvent.category,
                            eventCode: stageEvent.code,
                            eventText: stageEvent.text,
                            severity: stageEvent.severity,
                            eventState: isAlarm ? "OPEN" : "INFO",
                            alarmAll: isAlarm ? ";" + stageEvent.code + ";" : "",
                            eventAll: isAlarm ? "" : ";" + stageEvent.code + ";"
                        },
                        ingestedAt: now()
                    });
                    totalAlarmRecords++;
                }

                safeInsert(cppCollection, cppDocs);
                safeInsert(alarmCollection, alarmEventDocs);

                checkpointDocs.push({
                    checkpointId: "CP-" + eq.equipmentId + "-" + batchNo + "-BATCH_CPP",
                    equipmentId: eq.equipmentId,
                    streamType: "BATCH_CPP",
                    sourceTable: "SKPharma::CDSSKPharma.B_UDA_" + eq.equipmentCode,
                    lastProcessedSeqId: cppSeq,
                    lastProcessedAt: now(),
                    status: "SUCCESS",
                    updatedAt: now()
                });

                checkpointDocs.push({
                    checkpointId: "CP-" + eq.equipmentId + "-" + batchNo + "-ALARM_EVENT",
                    equipmentId: eq.equipmentId,
                    streamType: "ALARM_EVENT",
                    sourceTable: "SKPharma::CDSSKPharma.AE_" + eq.equipmentCode,
                    lastProcessedSeqId: alarmSeq,
                    lastProcessedAt: now(),
                    status: "SUCCESS",
                    updatedAt: now()
                });

                jobRunDocs.push({
                    jobRunId: "JOB-SEED-" + eq.equipmentType + "-" + batchNo + "-CPP",
                    equipmentId: eq.equipmentId,
                    streamType: "BATCH_CPP",
                    windowStartSeqId: cppSeq - CPP_POINTS_PER_BATCH + 1,
                    windowEndSeqId: cppSeq,
                    recordsRead: CPP_POINTS_PER_BATCH,
                    recordsWritten: CPP_POINTS_PER_BATCH,
                    recordsSkipped: 0,
                    status: "SUCCESS",
                    startedAt: toIsoDate(batchStart),
                    completedAt: toIsoDate(addMinutes(batchStart, 12)),
                    createdAt: now(),
                    updatedAt: now()
                });

                jobRunDocs.push({
                    jobRunId: "JOB-SEED-" + eq.equipmentType + "-" + batchNo + "-ALARM",
                    equipmentId: eq.equipmentId,
                    streamType: "ALARM_EVENT",
                    windowStartSeqId: alarmSeq - stageEvents.length + 1,
                    windowEndSeqId: alarmSeq,
                    recordsRead: stageEvents.length,
                    recordsWritten: stageEvents.length,
                    recordsSkipped: 0,
                    status: "SUCCESS",
                    startedAt: toIsoDate(addMinutes(batchStart, 2)),
                    completedAt: toIsoDate(addMinutes(batchStart, 16)),
                    createdAt: now(),
                    updatedAt: now()
                });

                batchSummaryDocs.push({
                    tenantId: TENANT_ID,
                    plantId: eq.plantId,
                    blockId: eq.blockId,
                    areaId: eq.areaId,
                    roomId: eq.roomId,
                    equipmentId: eq.equipmentId,
                    batchNo: batchNo,
                    lotNo: lotNo,
                    productName: product.productName,
                    productCode: product.productCode,
                    equipmentType: eq.equipmentType,
                    equipmentName: eq.equipmentName,
                    equipmentLocation: eq.hierarchy.fullPath,
                    batchSize: batchSize,
                    operatorName: operatorName,
                    supervisorName: supervisorName,
                    batchStartAt: toIsoDate(batchStart),
                    batchEndAt: toIsoDate(addMinutes(batchStart, 18)),
                    batchStatus: "COMPLETED",
                    cppRecordCount: CPP_POINTS_PER_BATCH,
                    alarmCount: alarmEventDocs.filter(function(a) { return a.event.eventCategory === "ALARM"; }).length,
                    eventCount: alarmEventDocs.filter(function(a) { return a.event.eventCategory === "EVENT"; }).length,
                    productionCount: randomInt(1000, 5000),
                    createdAt: now(),
                    updatedAt: now(),
                    hierarchy: eq.hierarchy
                });

                pipelineStageStatus[eq.equipmentType] = "COMPLETED";
                if (!pipelineStartAt || batchStart < pipelineStartAt) {
                    pipelineStartAt = batchStart;
                }
                var stageEndAt = addMinutes(batchStart, 18);
                if (!pipelineEndAt || stageEndAt > pipelineEndAt) {
                    pipelineEndAt = stageEndAt;
                }
                pipelineCppCount += CPP_POINTS_PER_BATCH;
                pipelineAlarmCount += alarmEventDocs.filter(function(a) { return a.event.eventCategory === "ALARM"; }).length;
                pipelineEventCount += alarmEventDocs.filter(function(a) { return a.event.eventCategory === "EVENT"; }).length;

                if (latestCpp) {
                    var liveState = LIVE_STATES[(batchIdx + eqIdx) % LIVE_STATES.length];
                    db.iiot_equipment_live_status.updateOne(
                        { equipmentId: eq.equipmentId },
                        {
                            $set: {
                                equipmentId: eq.equipmentId,
                                equipmentType: eq.equipmentType,
                                equipmentName: eq.equipmentName,
                                equipmentLocation: eq.hierarchy.fullPath,
                                currentState: liveState,
                                stateReason: liveState,
                                lastBatchNo: latestCpp.meta.batchNo,
                                lastLotNo: latestCpp.meta.lotNo,
                                lastProductName: latestCpp.meta.productName,
                                lastOperatorName: latestCpp.meta.operatorName,
                                lastSourceSeqId: latestCpp.source.sourceSeqId,
                                lastEventAt: latestCpp.observedAt,
                                heartbeatAt: now(),
                                updatedAt: now()
                            },
                            $setOnInsert: { createdAt: now() }
                        },
                        { upsert: true }
                    );
                }
            }

            var allStagesCompleted = STAGE_SEQUENCE.every(function(stage) {
                return pipelineStageStatus[stage] === "COMPLETED";
            });
            batchSummaryDocs.push({
                tenantId: TENANT_ID,
                plantId: "PLNT-0001",
                blockId: "BLK-0001",
                areaId: "AREA-0001",
                roomId: "ROOM-0001",
                equipmentId: "PIPELINE-" + batchNo,
                batchNo: batchNo,
                lotNo: lotNo,
                productName: product.productName,
                productCode: product.productCode,
                equipmentType: "PIPELINE",
                equipmentName: "WEG->FBD->BLE Pipeline",
                equipmentLocation: "PLNT-0001/BLK-0001/AREA-0001/ROOM-0001/PIPELINE",
                batchSize: batchSize,
                operatorName: operatorName,
                supervisorName: supervisorName,
                batchStartAt: toIsoDate(pipelineStartAt || now()),
                batchEndAt: toIsoDate(pipelineEndAt || now()),
                batchStatus: allStagesCompleted ? "COMPLETED" : "IN_PROGRESS",
                cppRecordCount: pipelineCppCount,
                alarmCount: pipelineAlarmCount,
                eventCount: pipelineEventCount,
                productionCount: randomInt(1000, 5000),
                stageStatus: {
                    WEG: pipelineStageStatus.WEG || "PENDING",
                    FBD: pipelineStageStatus.FBD || "PENDING",
                    BLE: pipelineStageStatus.BLE || "PENDING"
                },
                createdAt: now(),
                updatedAt: now(),
                hierarchy: {
                    plant: "PLNT-0001",
                    block: "BLK-0001",
                    area: "AREA-0001",
                    room: "ROOM-0001",
                    fullPath: "PLNT-0001/BLK-0001/AREA-0001/ROOM-0001/PIPELINE"
                }
            });
        }

        safeInsert("iiot_ingestion_checkpoint", checkpointDocs);
        safeInsert("iiot_ingestion_job_run", jobRunDocs);
        safeInsert("iiot_batch_summary", batchSummaryDocs);
        
        logInfo("Ingestion data completed!");
        logInfo("  Total CPP records: " + totalCppRecords);
        logInfo("  Total Alarm records: " + totalAlarmRecords);
        logInfo("  Total Batch Summaries: " + batchSummaryDocs.length);
    } catch (e) {
        logInfo("ERROR in seedIngestionData: " + e.message);
    }
}

function parseCsvLine(line) {
    var values = [];
    var current = "";
    var inQuotes = false;
    for (var i = 0; i < line.length; i++) {
        var ch = line[i];
        if (ch === '"') {
            if (inQuotes && i + 1 < line.length && line[i + 1] === '"') {
                current += '"';
                i++;
            } else {
                inQuotes = !inQuotes;
            }
        } else if (ch === ',' && !inQuotes) {
            values.push(current.trim());
            current = "";
        } else {
            current += ch;
        }
    }
    values.push(current.trim());
    return values.map(function(value) {
        return value.replace(/^"(.*)"$/, "$1").trim();
    });
}

function readTextFile(filePath) {
    try {
        if (typeof cat === "function") {
            return cat(filePath);
        }
    } catch (e) {}

    try {
        if (typeof require === "function") {
            var fs = require("fs");
            if (fs && typeof fs.readFileSync === "function") {
                return fs.readFileSync(filePath, "utf8");
            }
        }
    } catch (e) {}

    try {
        if (typeof require === "function") {
            var fs2 = require("fs");
            if (fs2 && typeof fs2.readFileSync === "function") {
                return fs2.readFileSync("/workspaces/" + filePath.replace(/^\.\//, ""), "utf8");
            }
        }
    } catch (e2) {}

    return null;
}

function parseSampleDate(value) {
    if (!value) return null;
    var cleaned = String(value).trim().replace(/\s+/, " ");
    if (!cleaned) return null;
    if (cleaned.indexOf("-") >= 0 && cleaned.indexOf(":") >= 0) {
        try {
            var isoCandidate = cleaned.replace(" ", "T");
            var parsed = new Date(isoCandidate);
            if (!isNaN(parsed.getTime())) {
                return parsed;
            }
        } catch (e) {}
    }
    try {
        return new Date(cleaned);
    } catch (e) {
        return null;
    }
}

function toNumber(value) {
    if (value === null || value === undefined || value === "") return null;
    var cleaned = String(value).trim();
    if (!cleaned) return null;
    var parsed = Number(cleaned.replace(/[^0-9.+-]/g, ""));
    return isNaN(parsed) ? null : parsed;
}

function seedSampleCsvIngestionData() {
    return;
}

function seedLegacySampleCsvIngestionData() {
    var targetEquipmentId = "RMG-100L-2-PVII";
    var targetEquipment = EQUIPMENT_DEFS.find(function(eq) {
        return eq.equipmentId === targetEquipmentId;
    });
    if (!targetEquipment) {
        logInfo("Skipping CSV ingestion because target equipment was not mapped: " + targetEquipmentId);
        return;
    }

    var candidates = [
        "sample_ingestion_data/Batch_Report_Ingestion/data.csv",
        "/docker-entrypoint-initdb.d/sample_ingestion_data/Batch_Report_Ingestion/data.csv",
        "/seed/sample_ingestion_data/Batch_Report_Ingestion/data.csv"
    ];
    var batchCsvContent = null;
    for (var i = 0; i < candidates.length; i++) {
        batchCsvContent = readTextFile(candidates[i]);
        if (batchCsvContent) {
            break;
        }
    }
    if (!batchCsvContent) {
        logInfo("Batch CSV not found; skipping sample ingestion load for " + targetEquipmentId);
        return;
    }

    var alarmCandidates = [
        "sample_ingestion_data/Alarms and Events - Ingestion/data__.csv",
        "/docker-entrypoint-initdb.d/sample_ingestion_data/Alarms and Events - Ingestion/data__.csv",
        "/seed/sample_ingestion_data/Alarms and Events - Ingestion/data__.csv"
    ];
    var alarmCsvContent = null;
    for (var j = 0; j < alarmCandidates.length; j++) {
        alarmCsvContent = readTextFile(alarmCandidates[j]);
        if (alarmCsvContent) {
            break;
        }
    }
    if (!alarmCsvContent) {
        logInfo("Alarm CSV not found; skipping sample ingestion load for " + targetEquipmentId);
        return;
    }

    var cppCollection = getTimeSeriesCppCollection(targetEquipmentId);
    var alarmCollection = getTimeSeriesAlarmCollection(targetEquipmentId);
    ensureCollection(cppCollection);
    ensureCollection(alarmCollection);

    var cppDocs = [];
    batchCsvContent.split(/\r?\n/).forEach(function(line) {
        if (!line || line.trim() === "") return;
        var values = parseCsvLine(line);
        if (values.length < 23) return;
        var equipmentIdValue = values[1] || values[10] || "";
        if (equipmentIdValue !== targetEquipmentId) return;
        var lastModifiedTime = values[16] || values[17] || values[18] || "";
        var observedAt = parseSampleDate(lastModifiedTime);
        if (!observedAt) return;
        var batchNo = values[2] || "";
        var lotNo = values[4] || "";
        var operatorName = values[5] || "";
        var productName = values[6] || "";
        var supervisorName = values[12] || "";
        var mode = values[17] || "";
        var machineDate = values[18] || "";
        var status = values[22] || "START";
        var timeHH = values[13] || "00";
        var timeMM = values[14] || "00";
        var timeSS = values[15] || "00";
        var cppDoc = {
            observedAt: toIsoDate(observedAt),
            meta: {
                tenantId: TENANT_ID,
                equipmentId: targetEquipmentId,
                batchNo: batchNo,
                lotNo: lotNo,
                productName: productName,
                productCode: productName,
                operatorName: operatorName,
                supervisorName: supervisorName,
                equipmentType: targetEquipment.equipmentType,
                equipmentName: targetEquipment.equipmentName,
                equipmentLocation: targetEquipment.hierarchy.fullPath,
                status: status,
                dateDay: parseInt(values[7] || "0", 10),
                dayMonth: parseInt(values[8] || "0", 10),
                dayYear: parseInt(values[9] || "0", 10),
                timeHH: pad2(parseInt(timeHH, 10) || 0),
                timeMM: pad2(parseInt(timeMM, 10) || 0),
                timeSS: pad2(parseInt(timeSS, 10) || 0)
            },
            source: {
                tableName: "SKPharma::CDSSKPharma.B_UDA_RMG_100L_P7_2",
                sourceSeqId: parseInt(values[0] || "0", 10),
                lastModifiedTime: toIsoDate(observedAt),
                machineDate: machineDate
            },
            metrics: {
                impellerA: toNumber(values[19]),
                chopperA: toNumber(values[20]),
                cycle: values[21] || "",
                mode: mode,
                batchSize: values[3] || "",
                status: status
            },
            ingestedAt: now()
        };
        cppDocs.push(cppDoc);
    });

    if (cppDocs.length > 0) {
        safeInsert(cppCollection, cppDocs);
    }

    var alarmDocs = [];
    alarmCsvContent.split(/\r?\n/).forEach(function(line) {
        if (!line || line.trim() === "") return;
        var values = parseCsvLine(line);
        if (values.length < 22) return;
        var equipmentIdValue = values[14] || values[15] || "";
        if (equipmentIdValue !== targetEquipmentId) return;
        var lastModifiedTime = values[16] || "";
        var eventAt = parseSampleDate(lastModifiedTime);
        if (!eventAt) return;
        var eventText = values[7] || values[8] || "";
        if (eventText.indexOf(";") === 0) {
            eventText = eventText.substring(1);
        }
        var eventCategory = /error|alarm|pressure|air|temp/i.test(eventText) ? "ALARM" : "EVENT";
        var alarmDoc = {
            eventAt: toIsoDate(eventAt),
            meta: {
                tenantId: TENANT_ID,
                equipmentId: targetEquipmentId,
                batchNo: values[9] || "",
                lotNo: values[11] || "",
                productName: values[13] || "",
                productCode: values[13] || "",
                operatorName: values[12] || "",
                supervisorName: values[20] || "",
                equipmentType: targetEquipment.equipmentType,
                equipmentName: targetEquipment.equipmentName,
                equipmentLocation: targetEquipment.hierarchy.fullPath,
                status: values[17] || "RUNNING",
                dateDay: parseInt(values[1] || "0", 10),
                dayMonth: parseInt(values[2] || "0", 10),
                dayYear: parseInt(values[3] || "0", 10),
                timeHH: pad2(parseInt(values[4] || "0", 10) || 0),
                timeMM: pad2(parseInt(values[5] || "0", 10) || 0),
                timeSS: pad2(parseInt(values[6] || "0", 10) || 0)
            },
            source: {
                tableName: "SKPharma::CDSSKPharma.AE_RMG100L_P7_2",
                sourceSeqId: parseInt(values[0] || "0", 10),
                lastModifiedTime: toIsoDate(eventAt),
                machineDate: values[21] || ""
            },
            event: {
                eventCategory: eventCategory,
                eventCode: eventText,
                eventText: eventText,
                severity: eventCategory === "ALARM" ? "HIGH" : "LOW",
                eventState: eventCategory === "ALARM" ? "OPEN" : "INFO",
                alarmAll: eventCategory === "ALARM" ? ";" + eventText + ";" : "",
                eventAll: eventCategory === "EVENT" ? ";" + eventText + ";" : ""
            },
            ingestedAt: now()
        };
        alarmDocs.push(alarmDoc);
    });

    if (alarmDocs.length > 0) {
        safeInsert(alarmCollection, alarmDocs);
    }

    var summaryDoc = {
        tenantId: TENANT_ID,
        equipmentId: targetEquipmentId,
        batchNo: "CSV-LOAD",
        lotNo: "",
        productName: "",
        productCode: "",
        equipmentType: targetEquipment.equipmentType,
        equipmentName: targetEquipment.equipmentName,
        equipmentLocation: targetEquipment.hierarchy.fullPath,
        batchSize: "",
        operatorName: "",
        supervisorName: "",
        batchStartAt: now(),
        batchEndAt: now(),
        batchStatus: "COMPLETED",
        cppRecordCount: cppDocs.length,
        alarmCount: alarmDocs.length,
        eventCount: alarmDocs.length,
        productionCount: 0,
        createdAt: now(),
        updatedAt: now(),
        hierarchy: targetEquipment.hierarchy
    };
    safeInsert("iiot_batch_summary", [summaryDoc]);

    logInfo("Loaded " + cppDocs.length + " CPP rows and " + alarmDocs.length + " alarm rows from sample CSVs for " + targetEquipmentId);
}

function validateHierarchy() {
    logInfo("=== HIERARCHY VALIDATION ===");
    logInfo("Total Equipment: " + EQUIPMENT_DEFS.length);
    logInfo("Expected: " + TOTAL_EQUIPMENT);
    
    PLANT_IDS.forEach(function(plantId) {
        var count = EQUIPMENT_DEFS.filter(function(eq) { return eq.plantId === plantId; }).length;
        var config = PLANT_HIERARCHY[plantId] || {};
        logInfo("  " + plantId + ": " + count + " equipment (blocks=" + (config.blocks || BLOCK_IDS).length + ", areas=" + (config.areas || AREA_IDS).length + ", rooms=" + (config.rooms || ROOM_IDS).length + ")");
    });
    
    logInfo("=== SAMPLE HIERARCHY ===");
    // Show sample hierarchy
    var sample = EQUIPMENT_DEFS[0];
    logInfo("  Plant: " + sample.plantId);
    logInfo("  Block: " + sample.blockId);
    logInfo("  Area: " + sample.areaId);
    logInfo("  Room: " + sample.roomId);
    logInfo("  Equipment: " + sample.equipmentName);
    
    logInfo("=== VALIDATION COMPLETE ===");
    return true;
}

function runSeed() {
    logInfo("=== STARTING IIOT SEED ===");
    logInfo("Plants: " + PLANT_IDS.length + ", Blocks: " + BLOCK_IDS.length + ", Areas: " + AREA_IDS.length + ", Rooms: " + ROOM_IDS.length);
    logInfo("Total Equipment: " + TOTAL_EQUIPMENT);
    
    try {
        validateHierarchy();
        
        var coreCollections = [
            "iiot_equiment_master",
            "iiot_equipment_critical_parameters",
            "iiot_equipment_critical_parameters_limit",
            "iiot_product_master",
            "iiot_source_table_mapping",
            "iiot_ingestion_checkpoint",
            "iiot_ingestion_job_run",
            "iiot_equipment_live_status",
            "iiot_batch_summary"
        ];
        
        var tsCollections = getTimeSeriesCollections();
        var allCollections = coreCollections.concat(tsCollections);
        
        logInfo("Resetting " + allCollections.length + " collections...");
        allCollections.forEach(function(name) {
            resetCollection(name);
        });
        
        createIndexes();
        seedMasterData();
        var loadedRealtime = seedRealtimeSheetIngestionData();
        if (!loadedRealtime) {
            seedIngestionData();
        }
        // Keep legacy sample CSV loader disabled for stage-based WEG/FBD/BLE seeding.
        
        logInfo("=== SEED COMPLETED ===");
        logInfo("Database: " + databaseName);
        logInfo("Total Equipment: " + EQUIPMENT_DEFS.length);
        
        var collections = db.getCollectionNames().filter(function(name) { 
            return name.startsWith('iiot_');
        });
        logInfo("Collection counts:");
        collections.forEach(function(name) {
            try {
                var count = db.getCollection(name).countDocuments({});
                print(" - " + name + ": " + count);
            } catch(e) {}
        });
        
        // Show sample data with room hierarchy
        logInfo("=== SAMPLE DATA WITH ROOM HIERARCHY ===");
        var sampleBatch = db.iiot_batch_summary.findOne({});
        if (sampleBatch) {
            logInfo("  Batch: " + sampleBatch.batchNo);
            logInfo("  Plant: " + sampleBatch.plantId);
            logInfo("  Block: " + sampleBatch.blockId);
            logInfo("  Area: " + sampleBatch.areaId);
            logInfo("  Room: " + sampleBatch.roomId);
            logInfo("  Equipment: " + sampleBatch.equipmentName);
        }
    } catch (e) {
        logInfo("FATAL ERROR: " + e.message);
        quit(1);
    }
}

runSeed();
print("[IIOT-SEED] Script completed.");