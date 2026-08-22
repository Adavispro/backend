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
    "G5RMG", "G6RMG", "G7RMG",
    "G5FBD", "G6FBD", "G7FBD",
    "G5OGB", "G6OGB", "G7OGB"
];

var DATASET_TYPE_MAP = {
    RMG: { name: "Rapid Mixer Granulator", make: "GEA Pharma", modelPrefix: "RMG" },
    FBD: { name: "Fluid Bed Dryer", make: "Glatt", modelPrefix: "FBD" },
    OGB: { name: "Oscillating Granulator Blender", make: "Bohle", modelPrefix: "OGB" }
};

var MOCK_PRODUCTS = [
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

function createEquipmentDefinitions() {
    var defs = [];
    DATASET_IDS.forEach(function (datasetId) {
        var type = datasetId.slice(-3).toUpperCase();
        var typeMeta = DATASET_TYPE_MAP[type] || DATASET_TYPE_MAP.RMG;
        var lineNo = parseInt(datasetId.replace(/[^0-9]/g, ""), 10) || 0;

        defs.push({
            equipmentId: datasetId,
            equipmentCode: datasetId,
            equipmentName: typeMeta.name + " " + datasetId,
            plantId: PLANT_ID,
            blockId: BLOCK_ID,
            areaId: AREA_ID,
            roomId: ROOM_ID,
            make: typeMeta.make,
            model: typeMeta.modelPrefix + "-" + lineNo,
            equipmentType: type,
            equipmentTypeName: typeMeta.name,
            hierarchy: {
                plant: PLANT_ID,
                block: BLOCK_ID,
                area: AREA_ID,
                room: ROOM_ID,
                fullPath: PLANT_ID + "/" + BLOCK_ID + "/" + AREA_ID + "/" + ROOM_ID + "/" + datasetId
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

function createBatchSummaryDocs(equipmentDefs, productDocs, ts) {
    var defsByCode = {};
    equipmentDefs.forEach(function (eq) {
        defsByCode[eq.equipmentCode] = eq;
    });

    var lineKeys = ["G5", "G6", "G7"];
    var docs = [];

    lineKeys.forEach(function (lineKey, idx) {
        var rmgCode = lineKey + "RMG";
        var fbdCode = lineKey + "FBD";
        var ogbCode = lineKey + "OGB";

        var product = productDocs[idx % productDocs.length] || {
            productCode: "STAPU1000",
            productName: "Allopurinol tablets"
        };

        var startAt = new Date(ts.getTime() - ((idx + 1) * 3 * 60 * 60 * 1000));
        var midAt = new Date(startAt.getTime() + 60 * 60 * 1000);
        var endAt = new Date(midAt.getTime() + 60 * 60 * 1000);

        var lineEquipment = defsByCode[rmgCode] || defsByCode[fbdCode] || defsByCode[ogbCode] || {};

        docs.push({
            tenantId: TENANT_ID,
            plantId: PLANT_ID,
            blockId: BLOCK_ID,
            areaId: AREA_ID,
            roomId: ROOM_ID,
            lineId: lineKey,
            equipmentId: lineEquipment.equipmentId || rmgCode,
            productCode: product.productCode,
            productName: product.productName,
            batchNo: "BATCH-" + lineKey + "-" + pad3(idx + 1),
            lotNo: "LOT-" + lineKey + "-" + pad3(idx + 1),
            overallStatus: "IN_PROGRESS",
            batchStartAt: startAt,
            batchEndAt: endAt,
            stages: [
                {
                    equipmentType: "RMG",
                    equipmentCode: rmgCode,
                    sequenceOrder: 1,
                    executionStatus: "COMPLETED",
                    stageStartAt: startAt,
                    stageEndAt: midAt,
                    operatorName: "production_operator",
                    supervisorName: "shift_supervisor",
                    recordCount: 120,
                    approval: {
                        status: "UNDER_REVIEW",
                        approvedBy: "",
                        approvedAt: null,
                        comments: "Awaiting supervisor review"
                    }
                },
                {
                    equipmentType: "FBD",
                    equipmentCode: fbdCode,
                    sequenceOrder: 2,
                    executionStatus: "IN_PROGRESS",
                    stageStartAt: midAt,
                    stageEndAt: endAt,
                    operatorName: "production_operator",
                    supervisorName: "shift_supervisor",
                    recordCount: 60,
                    approval: {
                        status: "PENDING",
                        approvedBy: "",
                        approvedAt: null,
                        comments: ""
                    }
                },
                {
                    equipmentType: "OGB",
                    equipmentCode: ogbCode,
                    sequenceOrder: 3,
                    executionStatus: "NOT_STARTED",
                    stageStartAt: null,
                    stageEndAt: null,
                    operatorName: "",
                    supervisorName: "shift_supervisor",
                    recordCount: 0,
                    approval: {
                        status: "PENDING",
                        approvedBy: "",
                        approvedAt: null,
                        comments: ""
                    }
                }
            ],
            createdAt: ts,
            updatedAt: ts
        });
    });

    return docs;
}

function buildParameterDocs(equipmentId, plantId, equipmentIndex, ts) {
    var eqType = (equipmentId || "").toString().trim().toUpperCase().slice(-3);
    var parameters = [];

    if (eqType === "RMG") {
        parameters = [
            { suffix: "AG_AMPS", code: "agAmps", name: "Agitator Amps", unitOfMeasure: "A", baseValue: 16.0, lowWarn: 2.0, lowCrit: 4.0, highWarn: 3.0, highCrit: 5.0 },
            { suffix: "AG_SPD", code: "agSpeed", name: "Agitator Speed", unitOfMeasure: "rpm", baseValue: 34.0, lowWarn: 4.0, lowCrit: 7.0, highWarn: 5.0, highCrit: 8.0 },
            { suffix: "CHP_AMPS", code: "chpAmps", name: "Chopper Amps", unitOfMeasure: "A", baseValue: 16.0, lowWarn: 2.0, lowCrit: 4.0, highWarn: 3.0, highCrit: 5.0 },
            { suffix: "CHP_SPD", code: "chpSpeed", name: "Chopper Speed", unitOfMeasure: "rpm", baseValue: 50.0, lowWarn: 4.0, lowCrit: 8.0, highWarn: 5.0, highCrit: 10.0 },
            { suffix: "HEATER_TEMP", code: "heaterTemp", name: "Heater Temperature", unitOfMeasure: "celsius", baseValue: 3320.0, lowWarn: 25.0, lowCrit: 45.0, highWarn: 35.0, highCrit: 55.0 }
        ];
    } else if (eqType === "FBD") {
        parameters = [
            { suffix: "INLET_TEMP", code: "inletTemp", name: "Inlet Temperature", unitOfMeasure: "celsius", baseValue: 58.0, lowWarn: 4.0, lowCrit: 8.0, highWarn: 6.0, highCrit: 10.0 },
            { suffix: "OUTLET_TEMP", code: "outletTemp", name: "Outlet Temperature", unitOfMeasure: "celsius", baseValue: 45.0, lowWarn: 3.0, lowCrit: 6.0, highWarn: 5.0, highCrit: 8.0 },
            { suffix: "BED_TEMP", code: "bedTemp", name: "Bed Temperature", unitOfMeasure: "celsius", baseValue: 40.0, lowWarn: 3.0, lowCrit: 6.0, highWarn: 5.0, highCrit: 8.0 },
            { suffix: "EXH_FAN_SPD", code: "exhaustFanSpeed", name: "Exhaust Fan Speed", unitOfMeasure: "%", baseValue: 45.0, lowWarn: 8.0, lowCrit: 15.0, highWarn: 10.0, highCrit: 18.0 },
            { suffix: "STEAM_PRESS", code: "steamPressure", name: "Steam Pressure", unitOfMeasure: "bar", baseValue: 1.5, lowWarn: 0.3, lowCrit: 0.5, highWarn: 0.4, highCrit: 0.7 }
        ];
    } else {
        parameters = [
            { suffix: "IMP_SPD", code: "impellerSpeed", name: "Impeller Speed", unitOfMeasure: "rpm", baseValue: 30.0, lowWarn: 5.0, lowCrit: 9.0, highWarn: 6.0, highCrit: 10.0 },
            { suffix: "IMP_AMPS", code: "impellerAmps", name: "Impeller Amps", unitOfMeasure: "A", baseValue: 9.0, lowWarn: 2.0, lowCrit: 3.0, highWarn: 2.0, highCrit: 4.0 },
            { suffix: "CHOP_SPD", code: "chopperSpeed", name: "Chopper Speed", unitOfMeasure: "rpm", baseValue: 24.0, lowWarn: 4.0, lowCrit: 7.0, highWarn: 5.0, highCrit: 8.0 },
            { suffix: "BINDER_FLOW", code: "binderFlow", name: "Binder Flow", unitOfMeasure: "lph", baseValue: 10.0, lowWarn: 2.0, lowCrit: 3.0, highWarn: 2.0, highCrit: 4.0 },
            { suffix: "GRAN_MOIST", code: "granuleMoisture", name: "Granule Moisture", unitOfMeasure: "%", baseValue: 9.0, lowWarn: 1.5, lowCrit: 3.0, highWarn: 2.0, highCrit: 3.5 }
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
    var batchSummaryDocs = createBatchSummaryDocs(equipmentDefs, productDocs, ts);

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

    logInfo("Master data seeded: equipment=" + equipmentDocs.length + ", products=" + productDocs.length + ", parameters=" + parameterDocs.length + ", limits=" + parameterLimitDocs.length + ", summaries=" + batchSummaryDocs.length + ", liveStatuses=" + liveStatusDocs.length);
}

function runSeed() {
    logInfo("=== STARTING IIOT MASTER SEED ===");

    var coreCollections = [
        "iiot_equipment_master",
        // "iiot_equipment_live_status",
        "iiot_equipment_critical_parameters",
        "iiot_equipment_critical_parameters_limit",
        "iiot_product_master"

    ];

    coreCollections.forEach(function (name) {
        resetCollection(name);
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
