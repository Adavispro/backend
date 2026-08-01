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
var PLANT_IDS = ["PLNT-0001", "PLNT-0002"];
var BLOCK_IDS = ["BLK-0001", "BLK-0002", "BLK-0003", "BLK-0004"];
var AREA_IDS = ["AREA-0001", "AREA-0002", "AREA-0012", "AREA-0013"];
var ROOM_IDS = ["ROOM-0001", "ROOM-0002", "ROOM-0018", "ROOM-0019"];
var PLANT_HIERARCHY = {
    "PLNT-0001": {
        blocks: ["BLK-0001", "BLK-0002"],
        areas: ["AREA-0001", "AREA-0002"],
        rooms: ["ROOM-0001", "ROOM-0002"]
    },
    "PLNT-0002": {
        blocks: ["BLK-0003", "BLK-0004"],
        areas: ["AREA-0012", "AREA-0013"],
        rooms: ["ROOM-0018", "ROOM-0019"]
    }
};
var EXPLICIT_EQUIPMENT_DEFS = [];

var TOTAL_EQUIPMENT = PLANT_IDS.reduce(function(total, plantId) {
    var config = PLANT_HIERARCHY[plantId] || {};
    var blocks = config.blocks || BLOCK_IDS;
    var areas = config.areas || AREA_IDS;
    var rooms = config.rooms || ROOM_IDS;
    return total + blocks.length * areas.length * rooms.length;
}, 0) + EXPLICIT_EQUIPMENT_DEFS.length;

// Data generation parameters
// Use all available hierarchy combinations and keep the generated data compact but representative.
var BATCHES_PER_EQUIPMENT = 3;
var CPP_POINTS_PER_BATCH = 6;
var ALARMS_PER_BATCH = 2;
var LIVE_STATES = ["RUNNING", "IDLE", "ERROR", "MAINTAINENCE", "OFFLINE"];

// Real pharmaceutical equipment types
var EQUIPMENT_TYPES = [
    { type: "RMG", name: "Rapid Mixer Granulator", models: ["RMG-100L", "RMG-200L", "RMG-300L"] },
    { type: "FBD", name: "Fluid Bed Dryer", models: ["FBD-60", "FBD-120", "FBD-200"] },
    { type: "Comill", name: "Comill", models: ["Comill-197", "Comill-197S", "Comill-197U"] },
    { type: "Blender", name: "Blender", models: ["Blender-500", "Blender-1000", "Blender-2000"] },
    { type: "Compression", name: "Compression Machine", models: ["CM-36", "CM-45", "CM-55"] }
];

var MAKES = ["GEA Pharma", "Glatt", "Fette", "Korsch", "Bohle", "SKPharma", "Apex Pharma Tech"];

// Real pharmaceutical products with hierarchy assignment
var PRODUCTS = [
    { code: "KRISOMCB-90%", name: "Krisom CB 90%", category: "API", plant: "PLNT-0001" },
    { code: "IBUPROFEN 200 MG", name: "Ibuprofen 200 MG Tablets", category: "Tablets", plant: "PLNT-0001" },
    { code: "PSEUDOEPHEDRINE HCL", name: "Pseudoephedrine HCl", category: "API", plant: "PLNT-0001" },
    { code: "TRAMADOL HCL", name: "Tramadol HCl 50mg", category: "Tablets", plant: "PLNT-0002" },
    { code: "METFORMIN HCL", name: "Metformin HCl 500mg", category: "Tablets", plant: "PLNT-0002" },
    { code: "AMOXICILLIN", name: "Amoxicillin 250mg Capsules", category: "Capsules", plant: "PLNT-0002" },
    { code: "ATORVASTATIN", name: "Atorvastatin 20mg", category: "Tablets", plant: "PLNT-0002" },
    { code: "OMEPRAZOLE", name: "Omeprazole 40mg Capsules", category: "Capsules", plant: "PLNT-0002" },
    { code: "LISINOPRIL", name: "Lisinopril 10mg", category: "Tablets", plant: "PLNT-0002" },
    { code: "LEVOFLOXACIN", name: "Levofloxacin 500mg", category: "Tablets", plant: "PLNT-0001" }
];

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
    var equipmentCounter = 0;

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
            hierarchy: {
                plant: eq.hierarchy.plant,
                block: eq.hierarchy.block,
                area: eq.hierarchy.area,
                room: eq.hierarchy.room,
                fullPath: eq.hierarchy.fullPath
            }
        });
    });
    
    PLANT_IDS.forEach(function(plantId, plantIdx) {
        var plantConfig = PLANT_HIERARCHY[plantId] || {};
        var plantBlocks = plantConfig.blocks || BLOCK_IDS;
        var plantAreas = plantConfig.areas || AREA_IDS;
        var plantRooms = plantConfig.rooms || ROOM_IDS;

        plantBlocks.forEach(function(blockId, blockIdx) {
            plantAreas.forEach(function(areaId, areaIdx) {
                plantRooms.forEach(function(roomId, roomIdx) {
                    equipmentCounter++;
                    var eqIdx = equipmentCounter;
                    var eqTypeObj = EQUIPMENT_TYPES[(eqIdx - 1) % EQUIPMENT_TYPES.length];
                    var eqType = eqTypeObj.type;
                    var model = eqTypeObj.models[(eqIdx - 1) % eqTypeObj.models.length];
                    var make = MAKES[(eqIdx - 1) % MAKES.length];
                    
                    var equipmentId = eqType + "-" + pad3(eqIdx) + "-PVII";
                    defs.push({
                        equipmentId: equipmentId,
                        equipmentCode: equipmentId,
                        equipmentName: eqTypeObj.name + " #" + eqIdx + " (" + make + " " + model + ")",
                        plantId: plantId,
                        blockId: blockId,
                        areaId: areaId,
                        roomId: roomId,
                        make: make,
                        model: model,
                        equipmentType: eqType,
                        equipmentTypeName: eqTypeObj.name,
                        hierarchy: {
                            plant: plantId,
                            block: blockId,
                            area: areaId,
                            room: roomId,
                            fullPath: plantId + "/" + blockId + "/" + areaId + "/" + roomId + "/" + eqType + pad3(eqIdx)
                        }
                    });
                });
            });
        });
    });
    
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
    PRODUCTS.forEach(function(p, idx) {
        products.push({
            // Keep productId aligned with productCode to match current master UI behavior.
            productId: p.code,
            productCode: p.code,
            productName: p.name,
            productCategory: p.category,
            tenantId: TENANT_ID,
            plantId: p.plant,
            isActive: true,
            createdAt: ts,
            updatedAt: ts
        });
    });
    return products;
}

function buildParameterDocs(equipmentId, plantId, equipmentIndex, ts) {
    var eqType = EQUIPMENT_TYPES[(equipmentIndex - 1) % EQUIPMENT_TYPES.length].type;
    var parameters = [];
    
    // Common parameters for all equipment with pharma-oriented names and codes
    parameters.push(
        {
            suffix: "MIX_SPD",
            code: "mixingSpeed",
            name: "Mixing Speed",
            parameterType: "FLOAT",
            unitOfMeasure: "rpm",
            isCritical: true,
            baseValue: 6.1
        },
        {
            suffix: "BLD_PRES",
            code: "blendPressure",
            name: "Blend Pressure",
            parameterType: "FLOAT",
            unitOfMeasure: "bar",
            isCritical: true,
            baseValue: 1.2
        },
        {
            suffix: "BED_TEMP",
            code: "bedTemperature",
            name: "Bed Temperature",
            parameterType: "FLOAT",
            unitOfMeasure: "celsius",
            isCritical: false,
            baseValue: 24.0
        }
    );
    
    // Equipment-specific parameters
    if (eqType === "RMG") {
        parameters.push(
            {
                suffix: "GRAN_MIX",
                code: "granulationMixing",
                name: "Granulation Mixing",
                parameterType: "FLOAT",
                unitOfMeasure: "rpm",
                isCritical: true,
                baseValue: 11.99
            },
            {
                suffix: "GRAN_TIME",
                code: "granulationTime",
                name: "Granulation Time",
                parameterType: "FLOAT",
                unitOfMeasure: "minutes",
                isCritical: true,
                baseValue: 10.0
            }
        );
    } else if (eqType === "FBD") {
        parameters.push(
            {
                suffix: "AIR_FLOW",
                code: "airFlowRate",
                name: "Air Flow Rate",
                parameterType: "FLOAT",
                unitOfMeasure: "m3/hr",
                isCritical: true,
                baseValue: 500.0
            },
            {
                suffix: "INLET_TEMP",
                code: "inletTemperature",
                name: "Inlet Temperature",
                parameterType: "FLOAT",
                unitOfMeasure: "celsius",
                isCritical: true,
                baseValue: 70.0
            }
        );
    } else if (eqType === "Comill") {
        parameters.push(
            {
                suffix: "MILL_SPD",
                code: "millSpeed",
                name: "Mill Speed",
                parameterType: "FLOAT",
                unitOfMeasure: "rpm",
                isCritical: true,
                baseValue: 2000.0
            },
            {
                suffix: "FEED_RATE",
                code: "feedRate",
                name: "Feed Rate",
                parameterType: "FLOAT",
                unitOfMeasure: "kg/hr",
                isCritical: false,
                baseValue: 50.0
            }
        );
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
        var base = p.baseValue + (equipmentIndex % 10) * 0.2 + idx * 0.15;

        paramDocs.push({
            parameterSeqId: 50000 + equipmentIndex * 10 + idx,
            tenantId: TENANT_ID,
            plantId: plantId,
            equipmentId: equipmentId,
            parameterId: parameterId,
            parameterCode: parameterCode,
            parameterName: parameterName,
            parameterType: p.parameterType,
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
            parameterType: p.parameterType,
            floatValue: Number(base.toFixed(2)),
            lowCriticalValue: Number((base - 2.0).toFixed(2)),
            lowWarningValue: Number((base - 1.0).toFixed(2)),
            idealMinValue: Number((base - 0.5).toFixed(2)),
            idealMaxValue: Number((base + 0.5).toFixed(2)),
            highWarningValue: Number((base + 1.0).toFixed(2)),
            highCriticalValue: Number((base + 2.0).toFixed(2)),
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

function seedIngestionData() {
    logInfo("Seeding ingestion data...");
    try {
        var allProducts = db.iiot_product_master.find({ tenantId: TENANT_ID, isActive: true }).toArray();
        if (allProducts.length === 0) {
            logInfo("No products found. Skipping ingestion data.");
            return;
        }
        
        var baseDate = new Date("2026-07-01T00:00:00Z");
        var maxEquipment = EQUIPMENT_DEFS.length;
        
        logInfo("Processing all available equipment combinations: " + maxEquipment);
        
        var checkpointDocs = [];
        var jobRunDocs = [];
        var batchSummaryDocs = [];
        var totalCppRecords = 0;
        var totalAlarmRecords = 0;

        for (var eqIdx = 0; eqIdx < maxEquipment; eqIdx++) {
            var eq = EQUIPMENT_DEFS[eqIdx];
            
            if (eqIdx % 5 === 0 && eqIdx > 0) {
                logInfo("  Processing equipment " + eqIdx + "/" + maxEquipment);
            }
            
            var cppCollection = getTimeSeriesCppCollection(eq.equipmentId);
            var alarmCollection = getTimeSeriesAlarmCollection(eq.equipmentId);
            
            ensureCollection(cppCollection);
            ensureCollection(alarmCollection);

            var cppDocs = [];
            var alarmEventDocs = [];
            var latestCpp = null;

            var cppSeq = 100000 + eqIdx * 10000;
            var alarmSeq = 200000 + eqIdx * 10000;

            for (var batchIdx = 1; batchIdx <= BATCHES_PER_EQUIPMENT; batchIdx++) {
                // Select product based on plant
                var plantProducts = allProducts.filter(function(p) { return p.plantId === eq.plantId; });
                if (plantProducts.length === 0) {
                    plantProducts = allProducts;
                }
                var product = plantProducts[(eqIdx + batchIdx) % plantProducts.length];
                
                var batchNo = "B" + pad3(batchIdx) + "-" + (2026 - (eqIdx % 2));
                var lotNo = "L" + pad2(batchIdx) + "-" + pad2(eqIdx + 1);
                
                var batchStart = addMinutes(baseDate, eqIdx * 45 + batchIdx * 35 + randomInt(-5, 5));
                
                var operators = ["KRISHNA", "PRATIK", "RAJESH", "SURESH", "AMIT", "VIKAS"];
                var operatorName = operators[(eqIdx + batchIdx) % operators.length];
                var supervisorName = operators[(eqIdx + batchIdx + 3) % operators.length];
                
                var batchSizes = ["23.000KG", "35.70KG", "18.000KG", "42.50KG", "25.000KG", "38.20KG"];
                var batchSize = batchSizes[(eqIdx + batchIdx) % batchSizes.length];

                for (var pointIdx = 0; pointIdx < CPP_POINTS_PER_BATCH; pointIdx++) {
                    cppSeq += 1;
                    var observedAt = addMinutes(batchStart, pointIdx * 2 + randomInt(0, 1));
                    
                    // Realistic metrics
                    var impellerA = randomFloat(5.0, 15.0, 2);
                    var chopperA = randomFloat(0, 2.0, 2);
                    var bedTemp = randomFloat(22.0, 28.0, 1);
                    
                    var metrics = {
                        impellerA: impellerA,
                        chopperA: chopperA,
                        bedTemp: bedTemp,
                        batchSize: batchSize,
                        mode: pointIdx < CPP_POINTS_PER_BATCH / 2 ? "DRY MIXING - " + (randomInt(1, 2)) + " RUNNING" : "WET MIXING - " + (randomInt(1, 2)) + " RUNNING",
                        status: pointIdx < CPP_POINTS_PER_BATCH - 1 ? "START" : "STOP",
                        cycle: pointIdx < CPP_POINTS_PER_BATCH / 2 ? "DRY MIXING" : "WET MIXING"
                    };
                    
                    var eqType = eq.equipmentType;
                    if (eqType === "RMG") {
                        metrics.impellerB = randomFloat(8.0, 15.0, 2);
                        metrics.granulationTime = randomInt(5, 15);
                    } else if (eqType === "FBD") {
                        metrics.airFlow = randomFloat(400, 650, 1);
                        metrics.inletTemp = randomFloat(60, 80, 1);
                    } else if (eqType === "Comill") {
                        metrics.impellerRPM = randomFloat(1500, 2500, 0);
                        metrics.feedRate = randomFloat(40, 65, 1);
                    }

                    var cppDoc = {
                        observedAt: toIsoDate(observedAt),
                        meta: {
                            equipmentId: eq.equipmentId,
                            batchNo: batchNo,
                            lotNo: lotNo,
                            productName: product.productName,
                            productCode: product.productCode,
                            operatorName: operatorName,
                            supervisorName: supervisorName,
                            equipmentType: eq.equipmentType,
                            equipmentName: eq.equipmentName,
                            equipmentLocation: eq.hierarchy.fullPath,
                            status: metrics.status,
                            // Time components for filtering
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

                // Generate alarms with room context
                for (var alarmIdx = 0; alarmIdx < ALARMS_PER_BATCH; alarmIdx++) {
                    alarmSeq += 1;
                    var eventAt = addMinutes(batchStart, 3 + alarmIdx * 8 + randomInt(0, 3));
                    
                    var alarmEvent = ALARM_EVENTS[(eqIdx + batchIdx + alarmIdx) % ALARM_EVENTS.length];
                    var isAlarm = alarmEvent.category === "ALARM";
                    
                    var alarmDoc = {
                        eventAt: toIsoDate(eventAt),
                        meta: {
                            equipmentId: eq.equipmentId,
                            batchNo: batchNo,
                            lotNo: lotNo,
                            productName: product.productName,
                            productCode: product.productCode,
                            operatorName: operatorName,
                            supervisorName: supervisorName,
                            equipmentType: eq.equipmentType,
                            equipmentName: eq.equipmentName,
                            equipmentLocation: eq.hierarchy.fullPath,
                            status: "RUNNING",
                            // Time components for filtering
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
                            eventCategory: alarmEvent.category,
                            eventCode: alarmEvent.code,
                            eventText: alarmEvent.text,
                            severity: alarmEvent.severity,
                            eventState: isAlarm ? "OPEN" : "INFO",
                            alarmAll: isAlarm ? ";" + alarmEvent.code + ";" : "",
                            eventAll: isAlarm ? "" : ";" + alarmEvent.code + ";"
                        },
                        ingestedAt: now()
                    };
                    
                    alarmEventDocs.push(alarmDoc);
                    totalAlarmRecords++;
                }

                batchSummaryDocs.push({
                    tenantId: TENANT_ID,
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
                    batchEndAt: toIsoDate(addMinutes(batchStart, (CPP_POINTS_PER_BATCH - 1) * 2 + 5)),
                    batchStatus: "COMPLETED",
                    cppRecordCount: CPP_POINTS_PER_BATCH,
                    alarmCount: ALARMS_PER_BATCH - 1,
                    eventCount: 1,
                    productionCount: randomInt(1000, 5000),
                    createdAt: now(),
                    updatedAt: now(),
                    hierarchy: eq.hierarchy
                });
            }

            safeInsert(cppCollection, cppDocs);
            safeInsert(alarmCollection, alarmEventDocs);

            checkpointDocs.push({
                checkpointId: "CP-" + eq.equipmentId + "-BATCH_CPP",
                equipmentId: eq.equipmentId,
                streamType: "BATCH_CPP",
                sourceTable: "SKPharma::CDSSKPharma.B_UDA_" + eq.equipmentCode,
                lastProcessedSeqId: cppSeq,
                lastProcessedAt: now(),
                status: "SUCCESS",
                updatedAt: now()
            });

            checkpointDocs.push({
                checkpointId: "CP-" + eq.equipmentId + "-ALARM_EVENT",
                equipmentId: eq.equipmentId,
                streamType: "ALARM_EVENT",
                sourceTable: "SKPharma::CDSSKPharma.AE_" + eq.equipmentCode,
                lastProcessedSeqId: alarmSeq,
                lastProcessedAt: now(),
                status: "SUCCESS",
                updatedAt: now()
            });

            jobRunDocs.push({
                jobRunId: "JOB-SEED-BATCH-" + pad3(eqIdx + 1),
                equipmentId: eq.equipmentId,
                streamType: "BATCH_CPP",
                windowStartSeqId: cppSeq - (BATCHES_PER_EQUIPMENT * CPP_POINTS_PER_BATCH) + 1,
                windowEndSeqId: cppSeq,
                recordsRead: BATCHES_PER_EQUIPMENT * CPP_POINTS_PER_BATCH,
                recordsWritten: BATCHES_PER_EQUIPMENT * CPP_POINTS_PER_BATCH,
                recordsSkipped: 0,
                status: "SUCCESS",
                startedAt: toIsoDate(addMinutes(baseDate, eqIdx * 25)),
                completedAt: toIsoDate(addMinutes(baseDate, eqIdx * 25 + 5)),
                createdAt: now(),
                updatedAt: now()
            });

            jobRunDocs.push({
                jobRunId: "JOB-SEED-ALARM-" + pad3(eqIdx + 1),
                equipmentId: eq.equipmentId,
                streamType: "ALARM_EVENT",
                windowStartSeqId: alarmSeq - (BATCHES_PER_EQUIPMENT * ALARMS_PER_BATCH) + 1,
                windowEndSeqId: alarmSeq,
                recordsRead: BATCHES_PER_EQUIPMENT * ALARMS_PER_BATCH,
                recordsWritten: BATCHES_PER_EQUIPMENT * ALARMS_PER_BATCH,
                recordsSkipped: 0,
                status: "SUCCESS",
                startedAt: toIsoDate(addMinutes(baseDate, eqIdx * 25 + 7)),
                completedAt: toIsoDate(addMinutes(baseDate, eqIdx * 25 + 11)),
                createdAt: now(),
                updatedAt: now()
            });

            if (latestCpp) {
                var liveState = LIVE_STATES[eqIdx % LIVE_STATES.length];
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
        seedIngestionData();
        seedLegacySampleCsvIngestionData();
        
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