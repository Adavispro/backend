// ============================================
// Adavis IIOT Master Seed (Minimal)
// Seeds only required master collections.
// ============================================

try {
    var testResult = db.runCommand({ ping: 1 });
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
var PLANT_ID = "PLNT-0001";
var BLOCK_ID = "BLK-0001";
var AREA_ID = "AREA-0001";
var ROOM_ID = "ROOM-0001";
var EQUIPMENT_MASTER_COLLECTIONS = ["iiot_equipment_master", "iiot_equiment_master"];

var DATASET_IDS = [
    "G5RMG",
    "G5FBD",
    "G5OGB",
    "G5COAT"
];

var DATASET_TYPE_MAP = {
    RMG: { name: "Rapid Mixer Granulator", make: "SAAN", modelPrefix: "RMG", area: "PB3", block: "PB3" },
    FBD: { name: "Fluid Bed Dryer", make: "SAAN", modelPrefix: "FBD", area: "GRANULATION", block: "PB3" },
    OGB: { name: "Octagonal Blender", make: "SAAN", modelPrefix: "OGB", area: "BLENDER2", block: "PB3" },
    BLE: { name: "Octagonal Blender", make: "SAAN", modelPrefix: "OGB", area: "BLENDER2", block: "PB3" },
    COAT: { name: "Auto Coater", make: "SAAN", modelPrefix: "COAT", area: "COATING", block: "PB3" }
};

var MOCK_PRODUCTS = [
    {
        productCode: "STFS7000",
        productName: "Finasteride USP 5 mg",
        productCategory: "Tablets"
    },
    {
        productCode: "STAPU1000",
        productName: "Allopurinol tablets",
        productCategory: "Tablets"
    },
    {
        productCode: "STLEV5000",
        productName: "Levetiracetam tablets",
        productCategory: "Tablets"
    }
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
    } catch (e) { }
    return false;
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
        });
        if (ops.length > 0) {
            col.bulkWrite(ops, { ordered: false });
            logInfo("Upserted " + docs.length + " docs into " + collectionName);
        }
    } catch (e) {
        logInfo("Error upserting into " + collectionName + ": " + e.message);
    }
}

function createEquipmentDefinitions() {
    var defs = [];
    DATASET_IDS.forEach(function (datasetId) {
        var type = datasetId.replace(/[^A-Z]/g, "").replace("G", "");
        if (type.startsWith("5") || type.startsWith("6") || type.startsWith("7")) {
            type = type.slice(1);
        }
        if (!DATASET_TYPE_MAP[type]) {
            if (datasetId.includes("RMG")) type = "RMG";
            else if (datasetId.includes("FBD")) type = "FBD";
            else if (datasetId.includes("OGB") || datasetId.includes("BLE")) type = "OGB";
            else if (datasetId.includes("COAT")) type = "COAT";
            else if (datasetId.includes("CIP")) type = "CIP";
            else type = "RMG";
        }
        var typeMeta = DATASET_TYPE_MAP[type] || DATASET_TYPE_MAP.RMG;
        var lineNo = parseInt(datasetId.replace(/[^0-9]/g, ""), 10) || 5;

        defs.push({
            equipmentId: datasetId,
            equipmentCode: datasetId,
            equipmentName: typeMeta.name + " (" + datasetId + ")",
            plantId: PLANT_ID,
            blockId: typeMeta.block || BLOCK_ID,
            areaId: typeMeta.area || AREA_ID,
            roomId: ROOM_ID,
            make: typeMeta.make,
            model: typeMeta.modelPrefix + "-" + lineNo,
            equipmentType: type === "OGB" ? "BLE" : type,
            equipmentTypeName: typeMeta.name,
            hierarchy: {
                plant: PLANT_ID,
                block: typeMeta.block || BLOCK_ID,
                area: typeMeta.area || AREA_ID,
                room: ROOM_ID,
                fullPath: PLANT_ID + "/" + (typeMeta.block || BLOCK_ID) + "/" + (typeMeta.area || AREA_ID) + "/" + ROOM_ID + "/" + datasetId
            }
        });
    });

    defs.sort(function (a, b) {
        return a.equipmentCode.localeCompare(b.equipmentCode);
    });

    return defs;
}

function createIndexes() {
    logInfo("Creating indexes...");
    try {
        EQUIPMENT_MASTER_COLLECTIONS.forEach(function (name) {
            db.getCollection(name).createIndex({ tenantId: 1, equipmentId: 1 }, { unique: true });
            db.getCollection(name).createIndex({ plantId: 1, blockId: 1, areaId: 1, roomId: 1 });
            db.getCollection(name).createIndex({ equipmentType: 1 });
            db.getCollection(name).createIndex({ make: 1 });
            db.getCollection(name).createIndex({ lineId: 1, equipmentCode: 1 }, { unique: true });
        });

        db.iiot_equipment_critical_parameters.createIndex(
            { tenantId: 1, equipmentId: 1, parameterId: 1 },
            { unique: true }
        );

        db.iiot_equipment_critical_parameters_limit.createIndex(
            { tenantId: 1, equipmentId: 1, parameterId: 1, effectiveFrom: -1 }
        );

        db.iiot_product_master.createIndex({ tenantId: 1, productId: 1 }, { unique: true });
        logInfo("Indexes created successfully");
    } catch (e) {
        logInfo("Error creating indexes: " + e.message);
    }
}

function getProductCatalog(ts) {
    return MOCK_PRODUCTS.map(function (p) {
        return {
            productId: p.productCode,
            productCode: p.productCode,
            productName: p.productName,
            productCategory: p.productCategory,
            tenantId: TENANT_ID,
            plantId: PLANT_ID,
            isActive: true,
            createdAt: ts,
            updatedAt: ts
        };
    });
}

function buildParameterDocs(equipmentId, plantId, equipmentIndex, ts) {
    var rawType = (equipmentId || "").toString().trim().toUpperCase();
    var eqType = "RMG";
    if (rawType.includes("RMG")) eqType = "RMG";
    else if (rawType.includes("FBD")) eqType = "FBD";
    else if (rawType.includes("OGB") || rawType.includes("BLE")) eqType = "BLE";
    else if (rawType.includes("COAT")) eqType = "COAT";
    else if (rawType.includes("CIP")) eqType = "CIP";

    var parameters = [];

    if (eqType === "RMG") {
        parameters = [
            { suffix: "AG_SPD", code: "agSpeed", name: "Agitator Speed", unitOfMeasure: "RPM", baseValue: 140.0, lowWarn: 20.0, lowCrit: 40.0, highWarn: 20.0, highCrit: 35.0 },
            { suffix: "AG_AMPS", code: "agAmps", name: "Agitator Current", unitOfMeasure: "A", baseValue: 28.0, lowWarn: 18.0, lowCrit: 28.0, highWarn: 2.5, highCrit: 5.0 },
            { suffix: "CHP_SPD", code: "chpSpeed", name: "Granulator Speed", unitOfMeasure: "RPM", baseValue: 1420.0, lowWarn: 170.0, lowCrit: 420.0, highWarn: 80.0, highCrit: 180.0 },
            { suffix: "CHP_AMPS", code: "chpAmps", name: "Granulator Current", unitOfMeasure: "A", baseValue: 5.5, lowWarn: 3.5, lowCrit: 5.5, highWarn: 0.7, highCrit: 2.0 },
            { suffix: "GRAN_TEMP", code: "heaterTemp", name: "Granulation Temperature", unitOfMeasure: "°C", baseValue: 55.0, lowWarn: 13.0, lowCrit: 20.0, highWarn: 13.0, highCrit: 20.0 },
            { suffix: "DURATION_SEC", code: "durationSec", name: "Duration Sec", unitOfMeasure: "Sec", baseValue: 180.0, lowWarn: 180.0, lowCrit: 180.0, highWarn: 300.0, highCrit: 420.0 }
        ];
    } else if (eqType === "FBD") {
        parameters = [
            { suffix: "INLET_TEMP", code: "inletTemp", name: "Inlet Air Temperature", unitOfMeasure: "°C", baseValue: 60.0, lowWarn: 33.0, lowCrit: 35.0, highWarn: 4.0, highCrit: 6.0 },
            { suffix: "OUTLET_TEMP", code: "outletTemp", name: "Outlet Exhaust Temperature", unitOfMeasure: "°C", baseValue: 37.0, lowWarn: 17.0, lowCrit: 18.0, highWarn: 11.0, highCrit: 15.0 }
        ];
    } else if (eqType === "COAT") {
        parameters = [
            { suffix: "INLET_AIR_TEMP", code: "inletAirTemp", name: "Inlet Air Temperature", unitOfMeasure: "°C", baseValue: 65.0, lowWarn: 15.0, lowCrit: 20.0, highWarn: 5.0, highCrit: 10.0 },
            { suffix: "BED_TEMP", code: "bedTemp", name: "Tablet Bed Temperature", unitOfMeasure: "°C", baseValue: 44.0, lowWarn: 5.0, lowCrit: 8.0, highWarn: 4.0, highCrit: 7.0 },
            { suffix: "PAN_SPEED", code: "panSpeed", name: "Pan Rotation Speed", unitOfMeasure: "rpm", baseValue: 8.0, lowWarn: 2.0, lowCrit: 4.0, highWarn: 2.0, highCrit: 4.0 },
            { suffix: "SPRAY_RATE", code: "sprayRate", name: "Coating Spray Rate", unitOfMeasure: "g/min", baseValue: 120.0, lowWarn: 15.0, lowCrit: 30.0, highWarn: 15.0, highCrit: 30.0 },
            { suffix: "ATOM_AIR_PRESS", code: "atomAirPress", name: "Atomizing Air Pressure", unitOfMeasure: "bar", baseValue: 2.5, lowWarn: 0.5, lowCrit: 1.0, highWarn: 0.5, highCrit: 1.0 }
        ];
    } else {
        // BLE (Octagonal Blender)
        parameters = [
            { suffix: "ACTUAL_RPM", code: "actualRpm", name: "Blending Speed", unitOfMeasure: "RPM", baseValue: 5.0, lowWarn: 1.0, lowCrit: 2.0, highWarn: 1.0, highCrit: 2.0 }
        ];
    }

    var paramDocs = [];
    var limitDocs = [];

    parameters.forEach(function (p, idx) {
        var parameterId = p.code + "_" + pad3(equipmentIndex);
        var parameterLimitCode = "LIM-" + p.suffix + "-" + pad3(equipmentIndex);

        paramDocs.push({
            parameterSeqId: 50000 + equipmentIndex * 10 + idx,
            tenantId: TENANT_ID,
            plantId: plantId,
            equipmentId: equipmentId,
            parameterId: parameterId,
            parameterCode: parameterId,
            parameterName: p.name + " #" + pad3(equipmentIndex),
            parameterType: "FLOAT",
            unitOfMeasure: p.unitOfMeasure,
            isCritical: true,
            isActive: true,
            createdAt: ts,
            updatedAt: ts
        });

        limitDocs.push({
            parameterLimitId: parameterLimitCode,
            parameterLimitCode: parameterLimitCode,
            parameterLimitSeqId: 90000 + equipmentIndex * 10 + idx,
            tenantId: TENANT_ID,
            plantId: plantId,
            equipmentId: equipmentId,
            parameterId: parameterId,
            parameterCode: parameterId,
            parameterName: p.name + " #" + pad3(equipmentIndex),
            parameterType: "FLOAT",
            floatValue: Number(p.baseValue.toFixed(2)),
            lowCriticalValue: Number((p.baseValue - p.lowCrit).toFixed(2)),
            lowWarningValue: Number((p.baseValue - p.lowWarn).toFixed(2)),
            idealMinValue: Number((p.baseValue - p.lowWarn / 2).toFixed(2)),
            idealMaxValue: Number((p.baseValue + p.highWarn / 2).toFixed(2)),
            highWarningValue: Number((p.baseValue + p.highWarn).toFixed(2)),
            highCriticalValue: Number((p.baseValue + p.highCrit).toFixed(2)),
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

function seedMasterData() {
    var equipmentDefs = createEquipmentDefinitions();
    var ts = now();

    var equipmentDocs = [];
    var parameterDocs = [];
    var parameterLimitDocs = [];
    var productDocs = getProductCatalog(ts);

    var liveStatusDocs = [];
    equipmentDefs.forEach(function (eq, index) {
        var lineId = (eq.equipmentCode || "").slice(0, 2).toUpperCase();
        equipmentDocs.push({
            equipmentSeqId: 10000 + index + 1,
            tenantId: TENANT_ID,
            plantId: eq.plantId,
            blockId: eq.blockId,
            areaId: eq.areaId,
            roomId: eq.roomId,
            equipmentId: eq.equipmentId,
            equipment_id: eq.equipmentId,
            equipmentCode: eq.equipmentCode,
            equipment_code: eq.equipmentCode,
            equipmentName: eq.equipmentName,
            equipmentType: eq.equipmentType,
            equipment_type: eq.equipmentType,
            equipmentTypeName: eq.equipmentTypeName,
            lineId: lineId,
            make: eq.make,
            model: eq.model,
            isActive: true,
            isDeleted: false,
            createdAt: ts,
            updatedAt: ts,
            hierarchy: eq.hierarchy,
            equipmentLocation: eq.hierarchy.fullPath
        });

        // liveStatusDocs.push({
        //     equipmentId: eq.equipmentId,
        //     currentState: isRunning ? "Running" : "Idle",
        //     stateReason: isRunning ? "Auto Cycle Started" : "Waiting for Batch",
        //     lastBatchNo: batchNo,
        //     lastLotNo: "01 of 05",
        //     lastEventAt: ts.toISOString(),
        //     heartbeatAt: ts.toISOString(),
        //     createdAt: ts,
        //     updatedAt: ts
        // });

        var payload = buildParameterDocs(eq.equipmentId, eq.plantId, index + 1, ts);
        parameterDocs = parameterDocs.concat(payload.params);
        parameterLimitDocs = parameterLimitDocs.concat(payload.limits);
    });

    EQUIPMENT_MASTER_COLLECTIONS.forEach(function (name) {
        safeUpsert(name, equipmentDocs, "equipmentId");
    });
    //safeUpsert("iiot_equipment_live_status", liveStatusDocs, "equipmentId");
    safeUpsert("iiot_product_master", productDocs, "productId");
    safeUpsert("iiot_equipment_critical_parameters", parameterDocs, "parameterId");
    safeUpsert("iiot_equipment_critical_parameters_limit", parameterLimitDocs, "parameterLimitId");

    logInfo("Master data seeded: equipment=" + equipmentDocs.length + ", products=" + productDocs.length + ", parameters=" + parameterDocs.length + ", limits=" + parameterLimitDocs.length + ", liveStatuses=" + liveStatusDocs.length);
}

function runSeed() {
    logInfo("=== STARTING IIOT MASTER SEED ===");

    var coreCollections = [
        "iiot_equipment_master",
        "iiot_equiment_master",
        "iiot_equipment_critical_parameters",
        "iiot_equipment_critical_parameters_limit",
        "iiot_product_master",
        "ingestion_state",
        "iiot_ingested_events_registry",
        "iiot_batch_summary"
    ];

    coreCollections.forEach(function (name) {
        resetCollection(name);
    });

    DATASET_IDS.forEach(function (ds) {
        resetCollection("iiot_ts_batch_" + ds);
        resetCollection("iiot_ts_alarm_" + ds);
        resetCollection("iiot_ts_audit_" + ds);
    });

    createIndexes();
    seedMasterData();

    logInfo("Collection counts (master-only):");
    coreCollections.forEach(function (name) {
        var count = db.getCollection(name).countDocuments({});
        print(" - " + name + ": " + count);
    });

    logInfo("=== MASTER SEED COMPLETED ===");
}

runSeed();
print("[IIOT-SEED] Script completed.");
