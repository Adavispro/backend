// ============================================
// Adavis IIOT Master Seed (Authentic Target Equipments)
// Seeds master collections for 4 Target Equipments:
// MB003 (RMG), MB004 (FBD), MB005 (BLE), MB041 (COAT)
// (MB040 Compression handled separately per proposal)
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

// Target Equipments (Excluding legacy G5)
var DATASET_IDS = [
    "MB003",
    "MB004",
    "MB005",
    "MB041"
];

var EQUIPMENT_SPECS = {
    MB003: {
        equipmentId: "MB003",
        equipmentCode: "MB003",
        equipmentName: "Rapid mixer Granulator",
        assetId: "10094",
        make: "BECTOCHEM",
        model: "FX5U 32 MR",
        plc: "MITSUBISHI FX5U 32 MR",
        plcModel: "MITSUBISHI FX5U 32 MR",
        hmiIpc: "Beijer",
        dataType: "SQL",
        vendor: "Retron21 / Anmeda",
        equipmentType: "RMG",
        equipmentTypeName: "Rapid Mixer Granulator",
        area: "MODULE-B",
        block: "PB1",
        batchNo: "AGO0026016",
        lotNo: "01",
        productCode: "STGW2000",
        productName: "LAMOTRIGINE",
        recipeCode: "AGO",
        recipeName: "Lamotrigine Granulation & Drying Recipe (AGO)",
        batchSize: "248.640 Kg",
        operator: "96828 (PB1-RMG (MB003) Operator)",
        supervisor: "96365 (PB1-RMG (MB003) Supervisor)"
    },
    MB004: {
        equipmentId: "MB004",
        equipmentCode: "MB004",
        equipmentName: "Fluid bed drier",
        assetId: "10110",
        make: "ALLIANCE",
        model: "Fx5U 32 MR",
        plc: "MITSUBISHI Fx5U 32 MR",
        plcModel: "MITSUBISHI Fx5U 32 MR",
        hmiIpc: "Beijer",
        dataType: "SQL",
        vendor: "Retro n21 / Anmeda",
        equipmentType: "FBD",
        equipmentTypeName: "Fluid Bed Drier",
        area: "MODULE-B",
        block: "PB1",
        batchNo: "AGO0026016",
        lotNo: "1B",
        productCode: "STGW2000",
        productName: "LAMOTRIGINE",
        recipeCode: "AGO",
        recipeName: "Lamotrigine Granulation & Drying Recipe (AGO)",
        batchSize: "248.640 Kg",
        operator: "11173 (PB1-Module-B (MB004) Operator)",
        supervisor: "191555 (PB1-Module-B (MB004) Supervisor)"
    },
    MB005: {
        equipmentId: "MB005",
        equipmentCode: "MB005",
        equipmentName: "Octagonal Blender",
        assetId: "10095",
        make: "BECTOCHEM",
        model: "Fx3U 32 MR",
        plc: "MITSUBISHI Fx3U 32 MR",
        plcModel: "MITSUBISHI Fx3U 32 MR",
        hmiIpc: "Beijer",
        dataType: "SQL",
        vendor: "Retron21 / Anmeda",
        equipmentType: "BLE",
        equipmentTypeName: "Octagonal Blender",
        area: "MODULE B",
        block: "PB1",
        batchNo: "AGO0026015",
        lotNo: "01",
        productCode: "STGW2000",
        productName: "LAMOTRIGINE",
        recipeCode: "AGO0026015",
        recipeName: "Lamotrigine Octagonal Blending Recipe (AGO0026015)",
        batchSize: "248.640 Kg",
        operator: "11173 (PB1-Module-B-Blender-Operator)",
        supervisor: "Harish Chandra Mishra-191164(PB1-Module-B-Blender-Supervisor)"
    },
    MB041: {
        equipmentId: "MB041",
        equipmentCode: "MB041",
        equipmentName: "Auto Coater",
        assetId: "10141",
        make: "GANSONS",
        model: "Fx3U 32 MR",
        plc: "MITSUBISHI Fx3U 32 MR",
        plcModel: "MITSUBISHI Fx3U 32 MR",
        hmiIpc: "Beijer",
        dataType: "SQL",
        vendor: "Retron21 / Anmeda",
        equipmentType: "COAT",
        equipmentTypeName: "Auto Coater",
        area: "COATING MODULE-B",
        block: "PB1",
        batchNo: "PED26009",
        lotNo: "NA",
        productCode: "STPA1D00",
        productName: "PAROXETINE USP 40 mg",
        recipeCode: "PAROXE40",
        recipeName: "Paroxetine USP 40mg Film Coating Recipe (PAROXE40)",
        batchSize: "625000 Tablets",
        operator: "29995 (PB1-Module-B-Operator)",
        supervisor: "191257 (PB1-Module-B-Supervisor)"
    }
};

var MOCK_PRODUCTS = [
    {
        productId: "STGW2000",
        productCode: "STGW2000",
        productName: "LAMOTRIGINE",
        productCategory: "Tablets"
    },
    {
        productId: "STPA1D00",
        productCode: "STPA1D00",
        productName: "PAROXETINE USP 40 mg",
        productCategory: "Tablets"
    }
];

function logInfo(msg) {
    print("[IIOT-SEED] " + msg);
}

function now() {
    return new Date();
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
        db.iiot_recipe_master.createIndex({ tenantId: 1, recipeId: 1 }, { unique: true });
        db.iiot_recipe_master.createIndex({ tenantId: 1, plantId: 1, recipeCode: 1 });
        db.iiot_recipe_management.createIndex({ tenantId: 1, plantId: 1, productId: 1, recipeId: 1, batchSize: 1, equipmentId: 1, parameterCode: 1 });
        logInfo("Indexes created successfully");
    } catch (e) {
        logInfo("Error creating indexes: " + e.message);
    }
}

function getParametersForEquipment(equipmentId) {
    if (equipmentId === "MB003") {
        return [
            { code: "agSpeed", name: "Agitator Speed", unitOfMeasure: "RPM", baseValue: 140.0, lowWarn: 120.0, lowCrit: 100.0, highWarn: 160.0, highCrit: 175.0 },
            { code: "agAmps", name: "Agitator Current", unitOfMeasure: "A", baseValue: 28.0, lowWarn: 0.0, lowCrit: 0.0, highWarn: 30.5, highCrit: 33.0 },
            { code: "chpSpeed", name: "Granulator Speed", unitOfMeasure: "RPM", baseValue: 1420.0, lowWarn: 1250.0, lowCrit: 1000.0, highWarn: 1500.0, highCrit: 1600.0 },
            { code: "chpAmps", name: "Granulator Current", unitOfMeasure: "A", baseValue: 5.5, lowWarn: 0.0, lowCrit: 0.0, highWarn: 6.2, highCrit: 7.5 },
            { code: "heaterTemp", name: "Granulation Temperature", unitOfMeasure: "°C", baseValue: 55.0, lowWarn: 42.0, lowCrit: 35.0, highWarn: 68.0, highCrit: 75.0 },
            { code: "durationSec", name: "Duration Sec", unitOfMeasure: "Sec", baseValue: 180.0, lowWarn: 0.0, lowCrit: 0.0, highWarn: 480.0, highCrit: 600.0 },
            { code: "pumpRpm", name: "Pump RPM", unitOfMeasure: "RPM", baseValue: 60.0, lowWarn: 10.0, lowCrit: 0.0, highWarn: 70.0, highCrit: 80.0 }
        ];
    } else if (equipmentId === "MB004") {
        return [
            { code: "inletTemp", name: "Inlet Air Temperature", unitOfMeasure: "°C", baseValue: 60.0, lowWarn: 27.0, lowCrit: 25.0, highWarn: 64.0, highCrit: 66.0 },
            { code: "exhaustTemp", name: "Outlet Exhaust Temperature", unitOfMeasure: "°C", baseValue: 37.0, lowWarn: 20.0, lowCrit: 19.0, highWarn: 48.0, highCrit: 52.0 },
            { code: "shakingState", name: "Shaking State", unitOfMeasure: "state", baseValue: 0.0, lowWarn: 0.0, lowCrit: 0.0, highWarn: 1.0, highCrit: 1.0 }
        ];
    } else if (equipmentId === "MB005") {
        return [
            { code: "blenderSpeed", name: "Blender Speed", unitOfMeasure: "RPM", baseValue: 6.0, lowWarn: 5.5, lowCrit: 5.0, highWarn: 6.5, highCrit: 7.0 },
            { code: "actualRpm", name: "Actual Blender RPM", unitOfMeasure: "RPM", baseValue: 6.0, lowWarn: 5.8, lowCrit: 5.0, highWarn: 6.2, highCrit: 7.0 },
            { code: "mixingCountdown", name: "Mixing Countdown", unitOfMeasure: "min", baseValue: 10.0, lowWarn: 0.0, lowCrit: 0.0, highWarn: 10.0, highCrit: 10.0 },
            { code: "vacuumStatus", name: "Vacuum Status", unitOfMeasure: "state", baseValue: 1.0, lowWarn: 0.0, lowCrit: 0.0, highWarn: 1.0, highCrit: 1.0 },
            { code: "mixingNo", name: "Mixing Cycle Number", unitOfMeasure: "count", baseValue: 1.0, lowWarn: 1.0, lowCrit: 1.0, highWarn: 2.0, highCrit: 2.0 }
        ];
    } else if (equipmentId === "MB041") {
        return [
            { code: "inletAirTemp", name: "Inlet Air Temperature", unitOfMeasure: "°C", baseValue: 60.0, lowWarn: 55.0, lowCrit: 50.0, highWarn: 65.0, highCrit: 68.0 },
            { code: "exhaustAirTemp", name: "Exhaust Air Temperature", unitOfMeasure: "°C", baseValue: 48.0, lowWarn: 43.0, lowCrit: 40.0, highWarn: 50.0, highCrit: 53.0 },
            { code: "bedTemp", name: "Tablet Bed Temperature", unitOfMeasure: "°C", baseValue: 48.0, lowWarn: 43.0, lowCrit: 40.0, highWarn: 50.5, highCrit: 53.0 },
            { code: "panSpeed", name: "Pan Rotation Speed", unitOfMeasure: "RPM", baseValue: 2.1, lowWarn: 2.0, lowCrit: 1.8, highWarn: 2.2, highCrit: 2.5 },
            { code: "dosingSpeed", name: "Dosing Pump Speed", unitOfMeasure: "RPM", baseValue: 14.0, lowWarn: 12.0, lowCrit: 10.0, highWarn: 18.0, highCrit: 19.0 },
            { code: "cycleCounter", name: "Coating Cycle Counter", unitOfMeasure: "cycles", baseValue: 133.0, lowWarn: 0.0, lowCrit: 0.0, highWarn: 265.0, highCrit: 300.0 },
            { code: "coatStatus", name: "Coating Status", unitOfMeasure: "state", baseValue: 0.0, lowWarn: 0.0, lowCrit: 0.0, highWarn: 1.0, highCrit: 1.0 }
        ];
    }
    return [];
}

function seedMasterData() {
    var ts = now();
    var equipmentDocs = [];
    var parameterDocs = [];
    var parameterLimitDocs = [];
    var recipeManagementDocs = [];

    // 1. Equipments & Parameters
    DATASET_IDS.forEach(function (eqId, index) {
        var spec = EQUIPMENT_SPECS[eqId];
        equipmentDocs.push({
            equipmentSeqId: 10000 + index + 1,
            tenantId: TENANT_ID,
            plantId: PLANT_ID,
            blockId: BLOCK_ID,
            areaId: AREA_ID,
            roomId: ROOM_ID,
            equipmentId: spec.equipmentId,
            equipment_id: spec.equipmentId,
            equipmentCode: spec.equipmentCode,
            equipment_code: spec.equipmentCode,
            equipmentName: spec.equipmentName,
            equipmentType: spec.equipmentType,
            equipment_type: spec.equipmentType,
            equipmentTypeName: spec.equipmentTypeName,
            assetId: spec.assetId,
            lineId: "PB1",
            make: spec.make,
            model: spec.model,
            plc: spec.plc,
            plcModel: spec.plcModel,
            hmiIpc: spec.hmiIpc,
            dataType: spec.dataType,
            vendor: spec.vendor,
            isActive: true,
            isDeleted: false,
            createdAt: ts,
            updatedAt: ts,
            hierarchy: {
                plant: PLANT_ID,
                block: BLOCK_ID,
                area: AREA_ID,
                room: ROOM_ID,
                fullPath: PLANT_ID + "/" + BLOCK_ID + "/" + AREA_ID + "/" + ROOM_ID + "/" + spec.equipmentId
            },
            equipmentLocation: PLANT_ID + "/" + BLOCK_ID + "/" + AREA_ID + "/" + ROOM_ID + "/" + spec.equipmentId
        });

        var params = getParametersForEquipment(eqId);
        params.forEach(function (p, pIdx) {
            var paramLimitId = "LIM-" + eqId + "-" + p.code.toUpperCase();
            parameterDocs.push({
                parameterSeqId: 50000 + (index + 1) * 20 + pIdx,
                tenantId: TENANT_ID,
                plantId: PLANT_ID,
                equipmentId: eqId,
                parameterId: p.code,
                parameterCode: p.code,
                parameterName: p.name,
                parameterType: "FLOAT",
                unitOfMeasure: p.unitOfMeasure,
                isCritical: true,
                isActive: true,
                createdAt: ts,
                updatedAt: ts
            });

            parameterLimitDocs.push({
                parameterLimitId: paramLimitId,
                parameterLimitCode: paramLimitId,
                parameterLimitSeqId: 90000 + (index + 1) * 20 + pIdx,
                tenantId: TENANT_ID,
                plantId: PLANT_ID,
                equipmentId: eqId,
                parameterId: p.code,
                parameterCode: p.code,
                parameterName: p.name,
                parameterType: "FLOAT",
                floatValue: Number(p.baseValue.toFixed(2)),
                lowCriticalValue: Number(p.lowCrit.toFixed(2)),
                lowWarningValue: Number(p.lowWarn.toFixed(2)),
                idealMinValue: Number(p.lowWarn.toFixed(2)),
                idealMaxValue: Number(p.highWarn.toFixed(2)),
                highWarningValue: Number(p.highWarn.toFixed(2)),
                highCriticalValue: Number(p.highCrit.toFixed(2)),
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

            // Recipe Management association
            var rcmId = "RCM-" + eqId + "-" + p.code.toUpperCase();
            recipeManagementDocs.push({
                recipeManagementId: rcmId,
                tenantId: TENANT_ID,
                plantId: PLANT_ID,
                productId: spec.productCode,
                productCode: spec.productCode,
                productName: spec.productName,
                recipeId: spec.recipeCode,
                recipeCode: spec.recipeCode,
                recipeName: spec.recipeName,
                batchSize: spec.batchSize,
                equipmentId: eqId,
                equipmentCode: eqId,
                equipmentName: spec.equipmentName,
                parameterCode: p.code,
                parameterName: p.name,
                unitOfMeasure: p.unitOfMeasure,
                uom: p.unitOfMeasure,
                targetSetpoint: Number(p.baseValue.toFixed(2)),
                lowLimit: Number(p.lowCrit.toFixed(2)),
                highLimit: Number(p.highCrit.toFixed(2)),
                isActive: true,
                createdAt: ts,
                updatedAt: ts
            });
        });
    });

    // 2. Product Master
    var productDocs = MOCK_PRODUCTS.map(function (p) {
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

    // 3. Recipe Master
    var recipeDocs = [
        {
            recipeId: "RCP-AGO-01",
            recipeCode: "AGO",
            recipeName: "Lamotrigine Granulation & Drying Recipe (AGO)",
            productId: "STGW2000",
            productCode: "STGW2000",
            productName: "LAMOTRIGINE",
            description: "Granulation and drying recipe for Lamotrigine 248.640 Kg",
            version: "1.0",
            associatedBatchSizes: ["248.640 Kg"],
            tenantId: TENANT_ID,
            plantId: PLANT_ID,
            isActive: true,
            createdAt: ts,
            updatedAt: ts
        },
        {
            recipeId: "RCP-BLEN-01",
            recipeCode: "AGO0026015",
            recipeName: "Lamotrigine Octagonal Blending Recipe (AGO0026015)",
            productId: "STGW2000",
            productCode: "STGW2000",
            productName: "LAMOTRIGINE",
            description: "Octagonal blending recipe for Lamotrigine 248.640 Kg",
            version: "1.0",
            associatedBatchSizes: ["248.640 Kg"],
            tenantId: TENANT_ID,
            plantId: PLANT_ID,
            isActive: true,
            createdAt: ts,
            updatedAt: ts
        },
        {
            recipeId: "RCP-COAT-01",
            recipeCode: "PAROXE40",
            recipeName: "Paroxetine USP 40mg Film Coating Recipe (PAROXE40)",
            productId: "STPA1D00",
            productCode: "STPA1D00",
            productName: "PAROXETINE USP 40 mg",
            description: "Film coating recipe for Paroxetine USP 40 mg tablets",
            version: "1.0",
            associatedBatchSizes: ["625000 Tablets"],
            tenantId: TENANT_ID,
            plantId: PLANT_ID,
            isActive: true,
            createdAt: ts,
            updatedAt: ts
        }
    ];

    // 4. Batch Summary: Exactly ONE single batch record per equipment
    var batchSummaryDocs = [
        {
            batchNo: "AGO0026016",
            lotNo: "01",
            lineId: "PB1",
            productCode: "STGW2000",
            productName: "LAMOTRIGINE",
            overallStatus: "IN_PROGRESS",
            batchStartAt: new Date(ts.getTime() - 2 * 3600 * 1000),
            batchEndAt: ts,
            stages: [
                {
                    stageId: "STAGE-1",
                    stageName: "Granulation",
                    equipmentType: "RMG",
                    equipmentCode: "MB003",
                    equipmentId: "MB003",
                    sequenceOrder: 1,
                    sequence: 1,
                    executionStatus: "IN_PROGRESS",
                    stageStartAt: new Date(ts.getTime() - 2 * 3600 * 1000),
                    stageEndAt: ts,
                    operatorName: "96828 (PB1-RMG (MB003) Operator)",
                    supervisorName: "96365 (PB1-RMG (MB003) Supervisor)",
                    recordCount: 1,
                    approval: {
                        status: "PENDING",
                        approvedBy: "",
                        approvedAt: null,
                        comments: ""
                    }
                }
            ],
            tenantId: TENANT_ID,
            plantId: PLANT_ID,
            createdAt: ts,
            updatedAt: ts
        },
        {
            batchNo: "AGO0026016",
            lotNo: "1B",
            lineId: "PB1",
            productCode: "STGW2000",
            productName: "LAMOTRIGINE",
            overallStatus: "IN_PROGRESS",
            batchStartAt: new Date(ts.getTime() - 3 * 3600 * 1000),
            batchEndAt: ts,
            stages: [
                {
                    stageId: "STAGE-2",
                    stageName: "Drying",
                    equipmentType: "FBD",
                    equipmentCode: "MB004",
                    equipmentId: "MB004",
                    sequenceOrder: 2,
                    sequence: 2,
                    executionStatus: "IN_PROGRESS",
                    stageStartAt: new Date(ts.getTime() - 3 * 3600 * 1000),
                    stageEndAt: ts,
                    operatorName: "11173 (PB1-Module-B (MB004) Operator)",
                    supervisorName: "191555 (PB1-Module-B (MB004) Supervisor)",
                    recordCount: 1,
                    approval: {
                        status: "PENDING",
                        approvedBy: "",
                        approvedAt: null,
                        comments: ""
                    }
                }
            ],
            tenantId: TENANT_ID,
            plantId: PLANT_ID,
            createdAt: ts,
            updatedAt: ts
        },
        {
            batchNo: "AGO0026015",
            lotNo: "01",
            lineId: "PB1",
            productCode: "STGW2000",
            productName: "LAMOTRIGINE",
            overallStatus: "IN_PROGRESS",
            batchStartAt: new Date(ts.getTime() - 1 * 3600 * 1000),
            batchEndAt: ts,
            stages: [
                {
                    stageId: "STAGE-3",
                    stageName: "Blending",
                    equipmentType: "BLE",
                    equipmentCode: "MB005",
                    equipmentId: "MB005",
                    sequenceOrder: 3,
                    sequence: 3,
                    executionStatus: "IN_PROGRESS",
                    stageStartAt: new Date(ts.getTime() - 1 * 3600 * 1000),
                    stageEndAt: ts,
                    operatorName: "11173 (PB1-Module-B-Blender-Operator)",
                    supervisorName: "Harish Chandra Mishra-191164(PB1-Module-B-Blender-Supervisor)",
                    recordCount: 1,
                    approval: {
                        status: "PENDING",
                        approvedBy: "",
                        approvedAt: null,
                        comments: ""
                    }
                }
            ],
            tenantId: TENANT_ID,
            plantId: PLANT_ID,
            createdAt: ts,
            updatedAt: ts
        },
        {
            batchNo: "PED26009",
            lotNo: "NA",
            lineId: "PB1",
            productCode: "STPA1D00",
            productName: "PAROXETINE USP 40 mg",
            overallStatus: "IN_PROGRESS",
            batchStartAt: new Date(ts.getTime() - 4 * 3600 * 1000),
            batchEndAt: ts,
            stages: [
                {
                    stageId: "STAGE-5",
                    stageName: "Coating",
                    equipmentType: "COAT",
                    equipmentCode: "MB041",
                    equipmentId: "MB041",
                    sequenceOrder: 5,
                    sequence: 5,
                    executionStatus: "IN_PROGRESS",
                    stageStartAt: new Date(ts.getTime() - 4 * 3600 * 1000),
                    stageEndAt: ts,
                    operatorName: "29995 (PB1-Module-B-Operator)",
                    supervisorName: "191257 (PB1-Module-B-Supervisor)",
                    recordCount: 1,
                    approval: {
                        status: "PENDING",
                        approvedBy: "",
                        approvedAt: null,
                        comments: ""
                    }
                }
            ],
            tenantId: TENANT_ID,
            plantId: PLANT_ID,
            createdAt: ts,
            updatedAt: ts
        }
    ];

    // 5. Workflow instances for each of the 4 batch stages
    var workflowInstances = [
        {
            instanceId: "WFI-MB003-01",
            workflowCode: "IIOT_BATCH_STAGE_WORKFLOW",
            workflowVersion: "1.0.0",
            entityType: "BATCH_STAGE",
            entityId: "AGO0026016:01:MB003",
            batchNo: "AGO0026016",
            lotNo: "01",
            equipmentCode: "MB003",
            currentStageCode: "SUBMISSION",
            currentStatus: "PENDING",
            initiatedBy: "SYSTEM",
            initiatedAt: ts,
            isTerminal: false,
            _class: "com.adavis.iiot.model.WorkflowInstance"
        },
        {
            instanceId: "WFI-MB004-01",
            workflowCode: "IIOT_BATCH_STAGE_WORKFLOW",
            workflowVersion: "1.0.0",
            entityType: "BATCH_STAGE",
            entityId: "AGO0026016:1B:MB004",
            batchNo: "AGO0026016",
            lotNo: "1B",
            equipmentCode: "MB004",
            currentStageCode: "SUBMISSION",
            currentStatus: "PENDING",
            initiatedBy: "SYSTEM",
            initiatedAt: ts,
            isTerminal: false,
            _class: "com.adavis.iiot.model.WorkflowInstance"
        },
        {
            instanceId: "WFI-MB005-01",
            workflowCode: "IIOT_BATCH_STAGE_WORKFLOW",
            workflowVersion: "1.0.0",
            entityType: "BATCH_STAGE",
            entityId: "AGO0026015:01:MB005",
            batchNo: "AGO0026015",
            lotNo: "01",
            equipmentCode: "MB005",
            currentStageCode: "SUBMISSION",
            currentStatus: "PENDING",
            initiatedBy: "SYSTEM",
            initiatedAt: ts,
            isTerminal: false,
            _class: "com.adavis.iiot.model.WorkflowInstance"
        },
        {
            instanceId: "WFI-MB041-01",
            workflowCode: "IIOT_BATCH_STAGE_WORKFLOW",
            workflowVersion: "1.0.0",
            entityType: "BATCH_STAGE",
            entityId: "PED26009:NA:MB041",
            batchNo: "PED26009",
            lotNo: "NA",
            equipmentCode: "MB041",
            currentStageCode: "SUBMISSION",
            currentStatus: "PENDING",
            initiatedBy: "SYSTEM",
            initiatedAt: ts,
            isTerminal: false,
            _class: "com.adavis.iiot.model.WorkflowInstance"
        }
    ];

    // 6. Live Status for each equipment
    var liveStatusDocs = [
        {
            equipmentId: "MB003",
            equipmentCode: "MB003",
            assetId: "10094",
            currentState: "Running",
            state: "RUNNING",
            stateReason: "Batch in progress: AGO0026016",
            lastBatchNo: "AGO0026016",
            lastLotNo: "01",
            batchNo: "AGO0026016",
            lotNo: "01",
            activeBatch: "AGO0026016",
            activeLot: "01",
            productCode: "STGW2000",
            productName: "LAMOTRIGINE",
            operator: "96828 (PB1-RMG (MB003) Operator)",
            operatorName: "96828 (PB1-RMG (MB003) Operator)",
            supervisor: "96365 (PB1-RMG (MB003) Supervisor)",
            supervisorName: "96365 (PB1-RMG (MB003) Supervisor)",
            telemetry: {
                "CURRENT (Amp)": 22.4,
                "Impeller_Current_Amp": 22.4,
                "Chopper_Current_Amp": 4.5,
                "Pump_RPM": 60.0,
                "agAmps": 22.4,
                "chpAmps": 4.5,
                "pumpRpm": 60.0
            },
            tags: {
                "CURRENT (Amp)": 22.4,
                "Impeller_Current_Amp": 22.4,
                "Chopper_Current_Amp": 4.5,
                "Pump_RPM": 60.0,
                "agAmps": 22.4,
                "chpAmps": 4.5,
                "pumpRpm": 60.0
            },
            lastEventAt: ts.toISOString(),
            heartbeatAt: ts.toISOString(),
            updatedAt: ts,
            createdAt: ts
        },
        {
            equipmentId: "MB004",
            equipmentCode: "MB004",
            assetId: "10110",
            currentState: "Running",
            state: "RUNNING",
            stateReason: "Batch in progress: AGO0026016",
            lastBatchNo: "AGO0026016",
            lastLotNo: "1B",
            batchNo: "AGO0026016",
            lotNo: "1B",
            activeBatch: "AGO0026016",
            activeLot: "1B",
            productCode: "STGW2000",
            productName: "LAMOTRIGINE",
            operator: "11173 (PB1-Module-B (MB004) Operator)",
            operatorName: "11173 (PB1-Module-B (MB004) Operator)",
            supervisor: "191555 (PB1-Module-B (MB004) Supervisor)",
            supervisorName: "191555 (PB1-Module-B (MB004) Supervisor)",
            telemetry: {
                "INLET TEMPARATURE": 57.0,
                "EXHAUST TEMPARATURE": 29.0,
                "Inlet_Temp": 57.0,
                "Exhaust_Temp": 29.0,
                "Shaking_State": "DRYING",
                "inletTemp": 57.0,
                "exhaustTemp": 29.0
            },
            tags: {
                "INLET TEMPARATURE": 57.0,
                "EXHAUST TEMPARATURE": 29.0,
                "Inlet_Temp": 57.0,
                "Exhaust_Temp": 29.0,
                "Shaking_State": "DRYING",
                "inletTemp": 57.0,
                "exhaustTemp": 29.0
            },
            lastEventAt: ts.toISOString(),
            heartbeatAt: ts.toISOString(),
            updatedAt: ts,
            createdAt: ts
        },
        {
            equipmentId: "MB005",
            equipmentCode: "MB005",
            assetId: "10095",
            currentState: "Running",
            state: "RUNNING",
            stateReason: "Batch in progress: AGO0026015",
            lastBatchNo: "AGO0026015",
            lastLotNo: "01",
            batchNo: "AGO0026015",
            lotNo: "01",
            activeBatch: "AGO0026015",
            activeLot: "01",
            productCode: "STGW2000",
            productName: "LAMOTRIGINE",
            operator: "11173 (PB1-Module-B-Blender-Operator)",
            operatorName: "11173 (PB1-Module-B-Blender-Operator)",
            supervisor: "Harish Chandra Mishra-191164(PB1-Module-B-Blender-Supervisor)",
            supervisorName: "Harish Chandra Mishra-191164(PB1-Module-B-Blender-Supervisor)",
            telemetry: {
                "BLENDING SPEED (RPM)": 6.0,
                "Blender_Speed_RPM": 6.0,
                "ACTUAL RPM": 6,
                "actualRpm": 6,
                "BLENDER STATUS": "MIXING 1 STARTED",
                "blenderStatus": "MIXING 1 STARTED",
                "Mixing_Countdown_Min": 10,
                "mixingCountdown": 10,
                "Vacuum_Status": "ON",
                "vacuumStatus": "ON",
                "Mixing_No": 1,
                "mixingNo": 1,
                "blenderSpeed": 6.0
            },
            tags: {
                "BLENDING SPEED (RPM)": 6.0,
                "Blender_Speed_RPM": 6.0,
                "ACTUAL RPM": 6,
                "actualRpm": 6,
                "BLENDER STATUS": "MIXING 1 STARTED",
                "blenderStatus": "MIXING 1 STARTED",
                "Mixing_Countdown_Min": 10,
                "mixingCountdown": 10,
                "Vacuum_Status": "ON",
                "vacuumStatus": "ON",
                "Mixing_No": 1,
                "mixingNo": 1,
                "blenderSpeed": 6.0
            },
            lastEventAt: ts.toISOString(),
            heartbeatAt: ts.toISOString(),
            updatedAt: ts,
            createdAt: ts
        },
        {
            equipmentId: "MB041",
            equipmentCode: "MB041",
            assetId: "10141",
            currentState: "Running",
            state: "RUNNING",
            stateReason: "Batch in progress: PED26009",
            lastBatchNo: "PED26009",
            lastLotNo: "NA",
            batchNo: "PED26009",
            lotNo: "NA",
            activeBatch: "PED26009",
            activeLot: "NA",
            productCode: "STPA1D00",
            productName: "PAROXETINE USP 40 mg",
            operator: "29995 (PB1-Module-B-Operator)",
            operatorName: "29995 (PB1-Module-B-Operator)",
            supervisor: "191257 (PB1-Module-B-Supervisor)",
            supervisorName: "191257 (PB1-Module-B-Supervisor)",
            telemetry: {
                "INLET AIR TEMP (°C)": 60.0,
                "EXHAUST AIR TEMP (°C)": 48.0,
                "BED TEMP (°C)": 48.0,
                "DOSING PUMP SPEED (RPM)": 14.0,
                "PAN SPEED (RPM)": 2.1,
                "CYCLE COUNTER": 133,
                "Inlet_Air_Temp": 60.0,
                "Exhaust_Air_Temp": 48.0,
                "Bed_Temp": 48.0,
                "Dosing_Speed_RPM": 14.0,
                "Pan_Speed_RPM": 2.1,
                "Cycle_Counter": 133,
                "inletAirTemp": 60.0,
                "exhaustAirTemp": 48.0,
                "bedTemp": 48.0,
                "dosingSpeed": 14.0,
                "panSpeed": 2.1,
                "cycleCounter": 133,
                "Coat_Status": "SPRAYING",
                "coatStatus": "SPRAYING"
            },
            tags: {
                "INLET AIR TEMP (°C)": 60.0,
                "EXHAUST AIR TEMP (°C)": 48.0,
                "BED TEMP (°C)": 48.0,
                "DOSING PUMP SPEED (RPM)": 14.0,
                "PAN SPEED (RPM)": 2.1,
                "CYCLE COUNTER": 133,
                "Inlet_Air_Temp": 60.0,
                "Exhaust_Air_Temp": 48.0,
                "Bed_Temp": 48.0,
                "Dosing_Speed_RPM": 14.0,
                "Pan_Speed_RPM": 2.1,
                "Cycle_Counter": 133,
                "inletAirTemp": 60.0,
                "exhaustAirTemp": 48.0,
                "bedTemp": 48.0,
                "dosingSpeed": 14.0,
                "panSpeed": 2.1,
                "cycleCounter": 133,
                "Coat_Status": "SPRAYING",
                "coatStatus": "SPRAYING"
            },
            lastEventAt: ts.toISOString(),
            heartbeatAt: ts.toISOString(),
            updatedAt: ts,
            createdAt: ts
        }
    ];

    // 7. Single Telemetry Docs for each equipment collection
    var singleTelemetryMap = {
        MB003: {
            observedAt: ts,
            event_time: ts.toISOString(),
            ingestedAt: ts,
            source: { tableName: "BATCHDATA", equipmentCode: "MB003" },
            meta: { batchNo: "AGO0026016", lotNo: "01", equipmentCode: "MB003", equipmentType: "RMG", operatorName: "96828 (PB1-RMG (MB003) Operator)", status: "RUNNING" },
            metrics: { "CURRENT (Amp)": 22.4, "Impeller_Current_Amp": 22.4, "Chopper_Current_Amp": 4.5, "Pump_RPM": 60.0, "agAmps": 22.4, "chpAmps": 4.5, "pumpRpm": 60.0 }
        },
        MB004: {
            observedAt: ts,
            event_time: ts.toISOString(),
            ingestedAt: ts,
            source: { tableName: "BATCHDATA", equipmentCode: "MB004" },
            meta: { batchNo: "AGO0026016", lotNo: "1B", equipmentCode: "MB004", equipmentType: "FBD", operatorName: "11173 (PB1-Module-B (MB004) Operator)", status: "RUNNING" },
            metrics: { "INLET TEMPARATURE": 57.0, "EXHAUST TEMPARATURE": 29.0, "Inlet_Temp": 57.0, "Exhaust_Temp": 29.0, "Shaking_State": "DRYING", "inletTemp": 57.0, "exhaustTemp": 29.0 }
        },
        MB005: {
            observedAt: ts,
            event_time: ts.toISOString(),
            ingestedAt: ts,
            source: { tableName: "BATCHDATA", equipmentCode: "MB005" },
            meta: { batchNo: "AGO0026015", lotNo: "01", equipmentCode: "MB005", equipmentType: "BLE", operatorName: "11173 (PB1-Module-B-Blender-Operator)", status: "RUNNING" },
            metrics: { "BLENDING SPEED (RPM)": 6.0, "Blender_Speed_RPM": 6.0, "ACTUAL RPM": 6, "actualRpm": 6, "BLENDER STATUS": "MIXING 1 STARTED", "blenderStatus": "MIXING 1 STARTED", "Mixing_Countdown_Min": 10, "mixingCountdown": 10, "Vacuum_Status": "ON", "vacuumStatus": "ON", "Mixing_No": 1, "mixingNo": 1, "blenderSpeed": 6.0 }
        },
        MB041: {
            observedAt: ts,
            event_time: ts.toISOString(),
            ingestedAt: ts,
            source: { tableName: "BATCHDATA", equipmentCode: "MB041" },
            meta: { batchNo: "PED26009", lotNo: "NA", equipmentCode: "MB041", equipmentType: "COAT", operatorName: "29995 (PB1-Module-B-Operator)", status: "RUNNING" },
            metrics: { "INLET AIR TEMP (°C)": 60.0, "EXHAUST AIR TEMP (°C)": 48.0, "BED TEMP (°C)": 48.0, "DOSING PUMP SPEED (RPM)": 14.0, "PAN SPEED (RPM)": 2.1, "CYCLE COUNTER": 133, "Inlet_Air_Temp": 60.0, "Exhaust_Air_Temp": 48.0, "Bed_Temp": 48.0, "Dosing_Speed_RPM": 14.0, "Pan_Speed_RPM": 2.1, "Cycle_Counter": 133, "inletAirTemp": 60.0, "exhaustAirTemp": 48.0, "bedTemp": 48.0, "dosingSpeed": 14.0, "panSpeed": 2.1, "cycleCounter": 133, "Coat_Status": "SPRAYING", "coatStatus": "SPRAYING" }
        }
    };

    EQUIPMENT_MASTER_COLLECTIONS.forEach(function (name) {
        safeUpsert(name, equipmentDocs, "equipmentId");
    });
    safeUpsert("iiot_product_master", productDocs, "productId");
    safeUpsert("iiot_equipment_critical_parameters", parameterDocs, "parameterId");
    safeUpsert("iiot_equipment_critical_parameters_limit", parameterLimitDocs, "parameterLimitId");
    safeUpsert("iiot_recipe_master", recipeDocs, "recipeId");
    safeUpsert("iiot_recipe_management", recipeManagementDocs, "recipeManagementId");
    var batchCol = db.getCollection("iiot_batch_summary");
    batchCol.deleteMany({});
    batchCol.insertMany(batchSummaryDocs);
    safeUpsert("iiot_workflow_instances", workflowInstances, "instanceId");
    safeUpsert("iiot_equipment_live_status", liveStatusDocs, "equipmentId");

    // Single telemetry write
    DATASET_IDS.forEach(function (eqId) {
        if (singleTelemetryMap[eqId]) {
            var col = db.getCollection("iiot_ts_batch_" + eqId);
            col.deleteMany({});
            col.insertOne(singleTelemetryMap[eqId]);
            logInfo("Inserted single telemetry doc for " + eqId);
        }
    });

    logInfo("Master data seeded successfully: equipment=" + equipmentDocs.length + ", products=" + productDocs.length + ", parameters=" + parameterDocs.length + ", limits=" + parameterLimitDocs.length + ", recipes=" + recipeDocs.length + ", recipeConfigs=" + recipeManagementDocs.length + ", batches=" + batchSummaryDocs.length);
}

function runSeed() {
    logInfo("=== STARTING IIOT MASTER SEED & CLEANUP ===");

    var coreCollections = [
        "iiot_equipment_master",
        "iiot_equiment_master",
        "iiot_equipment_critical_parameters",
        "iiot_equipment_critical_parameters_limit",
        "iiot_product_master",
        "iiot_recipe_master",
        "iiot_recipe_management",
        "ingestion_state",
        "iiot_ingested_events_registry",
        "iiot_ingestion_job_run",
        "iiot_batch_summary",
        "iiot_equipment_live_status",
        "iiot_workflow_instances"
    ];

    // Truncate all old collections
    coreCollections.forEach(function (name) {
        resetCollection(name);
    });

    // Clean up timeseries collections (both legacy G5 and MB codes)
    var allDatasets = ["MB003", "MB004", "MB005", "MB040", "MB041", "G5RMG", "G5FBD", "G5OGB", "G5COAT"];
    allDatasets.forEach(function (ds) {
        resetCollection("iiot_ts_batch_" + ds);
        resetCollection("iiot_ts_alarm_" + ds);
        resetCollection("iiot_ts_audit_" + ds);
    });

    createIndexes();
    seedMasterData();

    logInfo("=== Collection Counts ===");
    coreCollections.forEach(function (name) {
        var count = db.getCollection(name).countDocuments({});
        print(" - " + name + ": " + count);
    });

    logInfo("=== MASTER SEED COMPLETED ===");
}

runSeed();
print("[IIOT-SEED] Script completed.");
