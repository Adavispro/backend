#!/usr/bin/env python3
"""Master Data Synchronization Script for 5-Equipment Mock Service Streaming.

Synchronizes:
1. Equipment Master (`iiot_equipment_master`, `iiot_equiment_master`):
   - MB003 (Rapid mixer Granulator, AssetId: 10094, Block: PB1, Area: MODULE-B, PLC: MITSUBISHI FX5U, DataType: SQL)
   - MB004 (Fluid bed drier, AssetId: 10110, Block: PB1, Area: MODULE-B, PLC: MITSUBISHI Fx5U, DataType: SQL)
   - MB005 (Blender, AssetId: 10095, Block: PB1, Area: MODULE-B, PLC: MITSUBISHI Fx3U, DataType: SQL)
   - MB041 (Coating machine, AssetId: 10141, Block: PB1, Area: COATING MODULE-B, PLC: MITSUBISHI Fx3U, DataType: SQL)
   - MB040 (Compression machine, AssetId: 10040, Block: PB1, Area: MODULE-B, PLC: OMRON Sysmac CJ1G, DataType: MS ACCESS + Excel)
2. Products & Recipes (`iiot_product_master`, `iiot_recipe_master`)
3. Critical Parameters & Limits (`iiot_equipment_critical_parameters`, `iiot_equipment_critical_parameters_limit`)
4. User Profiles & Role Mappings (`mdm_user_profiles`, `mdm_user_auth_credentials`, `mdm_user_assignments_to_user_groups`, `mdm_user_context_assignments`)
5. Plant Topology (`mdm_plants`, `mdm_blocks`, `mdm_areas`, `mdm_rooms`)
6. Initial Live Status (`iiot_equipment_live_status`)
"""

from __future__ import annotations

import os
import sys
from datetime import datetime
from pymongo import MongoClient

MONGO_URI = os.getenv("MONGODB_URI", "mongodb://admin:Admin123!@localhost:37017/adavis_platform?authSource=admin")
DB_NAME = os.getenv("MONGODB_DATABASE", "adavis_platform")

# Password hash for "Adavis@123" used across Adavis development environments
DEFAULT_PASSWORD_HASH = "$2a$10$vI8A7sz3.p8W3hM7nFqEweQ8z6A1j9Z6k1q3l5e7m9o1q3s5u7w9y"

TENANT_ID = "TNT-0001"
PLANT_ID = "PLNT-0001"
BLOCK_ID = "BLK-0001"
AREA_MODULE_B = "AREA-0001"
AREA_COATING = "AREA-0002"
ROOM_ID = "ROOM-0001"

EQUIPMENTS = [
    {
        "equipmentSeqId": 10094,
        "tenantId": TENANT_ID,
        "plantId": PLANT_ID,
        "blockId": BLOCK_ID,
        "areaId": AREA_MODULE_B,
        "roomId": ROOM_ID,
        "equipmentId": "MB003",
        "equipment_id": "MB003",
        "equipmentCode": "MB003",
        "equipment_code": "MB003",
        "assetId": "10094",
        "asset_id": "10094",
        "equipmentName": "Rapid mixer Granulator (MB003)",
        "equipmentType": "RMG",
        "equipment_type": "RMG",
        "equipmentTypeName": "Rapid Mixer Granulator",
        "lineId": "PB1",
        "block": "PB1",
        "area": "MODULE-B",
        "make": "BECTOCHEM",
        "model": "MITSUBISHI FX5U 32 MR",
        "plcModel": "MITSUBISHI FX5U 32 MR",
        "plcMake": "MITSUBISHI",
        "hmiType": "Beijer",
        "vendor": "Retron21 / Anmeda",
        "dataType": "SQL",
        "ingestionMode": "Mock API (fwxapi)",
        "sampleProduct": "LAMOTRIGINE (STGW2000)",
        "sampleBatch": "AGO0026016 (Lot 01)",
        "isActive": True,
        "isDeleted": False,
        "hierarchy": {
            "plant": PLANT_ID,
            "block": "PB1",
            "area": "MODULE-B",
            "room": ROOM_ID,
            "fullPath": f"{PLANT_ID}/PB1/MODULE-B/{ROOM_ID}/MB003",
        },
    },
    {
        "equipmentSeqId": 10110,
        "tenantId": TENANT_ID,
        "plantId": PLANT_ID,
        "blockId": BLOCK_ID,
        "areaId": AREA_MODULE_B,
        "roomId": ROOM_ID,
        "equipmentId": "MB004",
        "equipment_id": "MB004",
        "equipmentCode": "MB004",
        "equipment_code": "MB004",
        "assetId": "10110",
        "asset_id": "10110",
        "equipmentName": "Fluid bed drier (MB004)",
        "equipmentType": "FBD",
        "equipment_type": "FBD",
        "equipmentTypeName": "Fluid Bed Dryer",
        "lineId": "PB1",
        "block": "PB1",
        "area": "MODULE-B",
        "make": "ALLIANCE",
        "model": "MITSUBISHI Fx5U 32 MR",
        "plcModel": "MITSUBISHI Fx5U 32 MR",
        "plcMake": "MITSUBISHI",
        "hmiType": "Beijer",
        "vendor": "Retro n21 / Anmeda",
        "dataType": "SQL",
        "ingestionMode": "Mock API (fwxapi)",
        "sampleProduct": "LAMOTRIGINE (STGW2000)",
        "sampleBatch": "AGO0026016 (Lot 1B)",
        "isActive": True,
        "isDeleted": False,
        "hierarchy": {
            "plant": PLANT_ID,
            "block": "PB1",
            "area": "MODULE-B",
            "room": ROOM_ID,
            "fullPath": f"{PLANT_ID}/PB1/MODULE-B/{ROOM_ID}/MB004",
        },
    },
    {
        "equipmentSeqId": 10095,
        "tenantId": TENANT_ID,
        "plantId": PLANT_ID,
        "blockId": BLOCK_ID,
        "areaId": AREA_MODULE_B,
        "roomId": ROOM_ID,
        "equipmentId": "MB005",
        "equipment_id": "MB005",
        "equipmentCode": "MB005",
        "equipment_code": "MB005",
        "assetId": "10095",
        "asset_id": "10095",
        "equipmentName": "Blender (MB005)",
        "equipmentType": "BLE",
        "equipment_type": "BLE",
        "equipmentTypeName": "Octagonal Blender",
        "lineId": "PB1",
        "block": "PB1",
        "area": "MODULE-B",
        "make": "BECTOCHEM",
        "model": "MITSUBISHI Fx3U 32 MR",
        "plcModel": "MITSUBISHI Fx3U 32 MR",
        "plcMake": "MITSUBISHI",
        "hmiType": "Beijer",
        "vendor": "Retron21 / Anmeda",
        "dataType": "SQL",
        "ingestionMode": "Mock API (fwxapi)",
        "sampleProduct": "LAMOTRIGINE (STGW2000)",
        "sampleBatch": "AGO0026015",
        "isActive": True,
        "isDeleted": False,
        "hierarchy": {
            "plant": PLANT_ID,
            "block": "PB1",
            "area": "MODULE-B",
            "room": ROOM_ID,
            "fullPath": f"{PLANT_ID}/PB1/MODULE-B/{ROOM_ID}/MB005",
        },
    },
    {
        "equipmentSeqId": 10040,
        "tenantId": TENANT_ID,
        "plantId": PLANT_ID,
        "blockId": BLOCK_ID,
        "areaId": AREA_MODULE_B,
        "roomId": "ROOM-0002",
        "equipmentId": "MB040",
        "equipment_id": "MB040",
        "equipmentCode": "MB040",
        "equipment_code": "MB040",
        "assetId": "10040",
        "asset_id": "10040",
        "equipmentName": "Compression machine (MB040)",
        "equipmentType": "COMP",
        "equipment_type": "COMP",
        "equipmentTypeName": "Compression Machine",
        "lineId": "PB1",
        "block": "PB1",
        "area": "MODULE-B",
        "make": "SEJONG",
        "model": "OMRON Sysmac CJ1G CPU 44H",
        "plcModel": "OMRON Sysmac CJ1G CPU 44H",
        "plcMake": "OMRON",
        "hmiType": "Proface IPC",
        "vendor": "Lab view / Sejong",
        "dataType": "MS ACCESS + Excel",
        "ingestionMode": "MS Access + Excel (Watch Folder)",
        "sampleProduct": "Amisulpride 200mg (STFS7000)",
        "sampleBatch": "ADNC26011",
        "isActive": True,
        "isDeleted": False,
        "hierarchy": {
            "plant": PLANT_ID,
            "block": "PB1",
            "area": "MODULE-B",
            "room": "ROOM-0002",
            "fullPath": f"{PLANT_ID}/PB1/MODULE-B/ROOM-0002/MB040",
        },
    },
    {
        "equipmentSeqId": 10141,
        "tenantId": TENANT_ID,
        "plantId": PLANT_ID,
        "blockId": BLOCK_ID,
        "areaId": AREA_COATING,
        "roomId": ROOM_ID,
        "equipmentId": "MB041",
        "equipment_id": "MB041",
        "equipmentCode": "MB041",
        "equipment_code": "MB041",
        "assetId": "10141",
        "asset_id": "10141",
        "equipmentName": "Coating machine (MB041)",
        "equipmentType": "COAT",
        "equipment_type": "COAT",
        "equipmentTypeName": "Auto Coater",
        "lineId": "PB1",
        "block": "PB1",
        "area": "COATING MODULE-B",
        "make": "GANSONS",
        "model": "MITSUBISHI Fx3U 32 MR",
        "plcModel": "MITSUBISHI Fx3U 32 MR",
        "plcMake": "MITSUBISHI",
        "hmiType": "Beijer",
        "vendor": "Retro n21 / Anmeda",
        "dataType": "SQL",
        "ingestionMode": "Mock API (fwxapi)",
        "sampleProduct": "PAROXETINE USP 40mg (STPA1D00)",
        "sampleBatch": "PED26009",
        "isActive": True,
        "isDeleted": False,
        "hierarchy": {
            "plant": PLANT_ID,
            "block": "PB1",
            "area": "COATING MODULE-B",
            "room": ROOM_ID,
            "fullPath": f"{PLANT_ID}/PB1/COATING MODULE-B/{ROOM_ID}/MB041",
        },
    },
]

USERS = [
    # OPERATORS (Role: OPERATOR, Group: GRP-0011)
    {
        "userId": "96828",
        "username": "96828",
        "email": "96828@adavis.com",
        "firstName": "PB1 RMG",
        "lastName": "Operator",
        "title": "Operator",
        "role": "OPERATOR",
        "groupId": "GRP-0011",
        "context": "MB003 (RMG) / MODULE-B",
        "designation": "96828 (PB1-RMG (MB003) Operator)",
    },
    {
        "userId": "11173",
        "username": "11173",
        "email": "11173@adavis.com",
        "firstName": "PB1 Module-B",
        "lastName": "Operator",
        "title": "Operator",
        "role": "OPERATOR",
        "groupId": "GRP-0011",
        "context": "MB004 (FBD) & MB005 (Blender)",
        "designation": "11173 (PB1-Module-B (MB004) Operator)",
    },
    {
        "userId": "29995",
        "username": "29995",
        "email": "29995@adavis.com",
        "firstName": "PB1 Coating",
        "lastName": "Operator",
        "title": "Operator",
        "role": "OPERATOR",
        "groupId": "GRP-0011",
        "context": "MB041 (Coating) / COATING MODULE-B",
        "designation": "29995 (PB1-Module-B-Operator)",
    },
    {
        "userId": "10401",
        "username": "10401",
        "email": "10401@adavis.com",
        "firstName": "PB1 Compression",
        "lastName": "Operator",
        "title": "Operator",
        "role": "OPERATOR",
        "groupId": "GRP-0011",
        "context": "MB040 (Compression) / MODULE-B",
        "designation": "10401 (PB1-Compression-Operator)",
    },
    # SUPERVISORS (Role: REVIEWER, UI/Display: SUPERVISOR, Group: GRP-0016)
    {
        "userId": "96365",
        "username": "96365",
        "email": "96365@adavis.com",
        "firstName": "PB1 RMG",
        "lastName": "Supervisor",
        "title": "SUPERVISOR",
        "role": "REVIEWER",
        "groupId": "GRP-0016",
        "context": "MB003 (RMG) & MB005 (Blender)",
        "designation": "96365 (PB1-RMG (MB003) Supervisor)",
    },
    {
        "userId": "191555",
        "username": "191555",
        "email": "191555@adavis.com",
        "firstName": "PB1 Module-B",
        "lastName": "Supervisor",
        "title": "SUPERVISOR",
        "role": "REVIEWER",
        "groupId": "GRP-0016",
        "context": "MB004 (FBD) / MODULE-B",
        "designation": "191555 (PB1-Module-B (MB004) Supervisor)",
    },
    {
        "userId": "191164",
        "username": "191164",
        "email": "191164@adavis.com",
        "firstName": "Harish Chandra",
        "lastName": "Mishra",
        "title": "SUPERVISOR",
        "role": "REVIEWER",
        "groupId": "GRP-0016",
        "context": "MB005 (Blender) / MODULE-B",
        "designation": "Harish Chandra Mishra-191164(PB1-Module-B-Blender-Supervisor)",
    },
    {
        "userId": "191257",
        "username": "191257",
        "email": "191257@adavis.com",
        "firstName": "PB1 Coating",
        "lastName": "Supervisor",
        "title": "SUPERVISOR",
        "role": "REVIEWER",
        "groupId": "GRP-0016",
        "context": "MB041 (Coating) / COATING MODULE-B",
        "designation": "191257 (PB1-Module-B-Supervisor)",
    },
    {
        "userId": "10402",
        "username": "10402",
        "email": "10402@adavis.com",
        "firstName": "PB1 Compression",
        "lastName": "Supervisor",
        "title": "SUPERVISOR",
        "role": "REVIEWER",
        "groupId": "GRP-0016",
        "context": "MB040 (Compression) / MODULE-B",
        "designation": "10402 (PB1-Compression-Supervisor)",
    },
    # APPROVERS (Role: APPROVER, Group: GRP-0017)
    {
        "userId": "QA-001",
        "username": "qa_001",
        "email": "qa_001@adavis.com",
        "firstName": "Quality Assurance",
        "lastName": "Approver",
        "title": "QA Approver",
        "role": "APPROVER",
        "groupId": "GRP-0017",
        "context": "Plant Quality Assurance",
        "designation": "QA Lead Approver & E-Signatory",
    },
    {
        "userId": "APPROVER-01",
        "username": "approver_01",
        "email": "approver_01@adavis.com",
        "firstName": "Production Head",
        "lastName": "Approver",
        "title": "Plant Approver",
        "role": "APPROVER",
        "groupId": "GRP-0017",
        "context": "PB1 Production Plant",
        "designation": "Production Head Approver",
    },
]


def sync_master_data():
    print(f"Connecting to MongoDB at {MONGO_URI}...")
    client = MongoClient(MONGO_URI)
    db = client[DB_NAME]
    now_dt = datetime.utcnow()

    print("\n--- 1. Synchronizing Topology (Plants, Blocks, Areas, Rooms) ---")
    db.mdm_plants.update_one(
        {"plantId": PLANT_ID},
        {"$set": {"plantName": "PB1 Production Plant", "plantCode": "PB1", "tenantId": TENANT_ID, "isActive": True, "updatedAt": now_dt}},
        upsert=True,
    )
    db.mdm_blocks.update_one(
        {"blockId": BLOCK_ID},
        {"$set": {"blockName": "PB1 Block", "blockCode": "PB1", "plantId": PLANT_ID, "tenantId": TENANT_ID, "isActive": True, "updatedAt": now_dt}},
        upsert=True,
    )
    db.mdm_areas.update_one(
        {"areaId": AREA_MODULE_B},
        {"$set": {"areaName": "MODULE-B", "areaCode": "MODULE-B", "blockId": BLOCK_ID, "plantId": PLANT_ID, "tenantId": TENANT_ID, "isActive": True, "updatedAt": now_dt}},
        upsert=True,
    )
    db.mdm_areas.update_one(
        {"areaId": AREA_COATING},
        {"$set": {"areaName": "COATING MODULE-B", "areaCode": "COAT-MOD-B", "blockId": BLOCK_ID, "plantId": PLANT_ID, "tenantId": TENANT_ID, "isActive": True, "updatedAt": now_dt}},
        upsert=True,
    )
    db.mdm_rooms.update_one(
        {"roomId": ROOM_ID},
        {"$set": {"roomName": "Processing Room 01", "roomCode": "RM-PB1-01", "areaId": AREA_MODULE_B, "plantId": PLANT_ID, "tenantId": TENANT_ID, "classification": "ISO_8", "isActive": True, "updatedAt": now_dt}},
        upsert=True,
    )
    db.mdm_rooms.update_one(
        {"roomId": "ROOM-0002"},
        {"$set": {"roomName": "Compression Room 02", "roomCode": "RM-PB1-02", "areaId": AREA_MODULE_B, "plantId": PLANT_ID, "tenantId": TENANT_ID, "classification": "ISO_7", "isActive": True, "updatedAt": now_dt}},
        upsert=True,
    )
    print("✓ Plant topology synchronized.")

    print("\n--- 2. Synchronizing Equipment Master (`iiot_equipment_master`) ---")
    for eq in EQUIPMENTS:
        doc = dict(eq)
        doc["updatedAt"] = now_dt
        for col_name in ["iiot_equipment_master", "iiot_equiment_master"]:
            db[col_name].update_one(
                {"equipmentId": eq["equipmentId"]},
                {"$set": doc, "$setOnInsert": {"createdAt": now_dt}},
                upsert=True,
            )
        print(f"  ✓ Equipment synced: {eq['equipmentId']} ({eq['equipmentName']}) - AssetId {eq['assetId']}")

    print("\n--- 3. Synchronizing Product Catalog & Recipes ---")
    products = [
        {"productId": "STGW2000", "productCode": "STGW2000", "productName": "LAMOTRIGINE", "productCategory": "Tablets"},
        {"productId": "STPA1D00", "productCode": "STPA1D00", "productName": "PAROXETINE USP 40mg", "productCategory": "Coated Tablets"},
        {"productId": "STFS7000", "productCode": "STFS7000", "productName": "Amisulpride 200mg", "productCategory": "Tablets"},
    ]
    for p in products:
        p_doc = dict(p)
        p_doc.update({"tenantId": TENANT_ID, "plantId": PLANT_ID, "isActive": True, "updatedAt": now_dt})
        db.iiot_product_master.update_one(
            {"productCode": p["productCode"]},
            {"$set": p_doc, "$setOnInsert": {"createdAt": now_dt}},
            upsert=True,
        )

    recipes = [
        {"recipeId": "RCP-AGO-01", "recipeCode": "AGO", "recipeName": "Lamotrigine Granulation & Drying Recipe", "productId": "STGW2000", "productCode": "STGW2000", "productName": "LAMOTRIGINE"},
        {"recipeId": "RCP-AGO-02", "recipeCode": "AGO0026015", "recipeName": "Lamotrigine Blending Recipe", "productId": "STGW2000", "productCode": "STGW2000", "productName": "LAMOTRIGINE"},
        {"recipeId": "RCP-PAROXE40", "recipeCode": "PAROXE40", "recipeName": "Paroxetine Coating Recipe", "productId": "STPA1D00", "productCode": "STPA1D00", "productName": "PAROXETINE USP 40mg"},
        {"recipeId": "RCP-SEJONG-01", "recipeCode": "SEJONG-49D", "recipeName": "Sejong Compression Recipe", "productId": "STFS7000", "productCode": "STFS7000", "productName": "Amisulpride 200mg"},
    ]
    for r in recipes:
        r_doc = dict(r)
        r_doc.update({"tenantId": TENANT_ID, "plantId": PLANT_ID, "isActive": True, "updatedAt": now_dt})
        db.iiot_recipe_master.update_one(
            {"recipeCode": r["recipeCode"]},
            {"$set": r_doc, "$setOnInsert": {"createdAt": now_dt}},
            upsert=True,
        )
    print("✓ Products and recipes synchronized.")

    print("\n--- 4. Synchronizing Critical Parameters & Limits ---")
    param_specs = {
        "MB003": [
            {"paramId": "MB003_AG_AMPS", "code": "agAmps", "name": "Impeller Current", "unit": "A", "base": 22.35, "low": 18.0, "high": 26.7},
            {"paramId": "MB003_CHP_AMPS", "code": "chpAmps", "name": "Chopper Current", "unit": "A", "base": 4.85, "low": 3.5, "high": 6.2},
            {"paramId": "MB003_PUMP_RPM", "code": "pumpRpm", "name": "Pump Speed", "unit": "RPM", "base": 60.0, "low": 50.0, "high": 70.0},
        ],
        "MB004": [
            {"paramId": "MB004_INLET_TEMP", "code": "inletTemp", "name": "Inlet Temperature", "unit": "°C", "base": 58.0, "low": 26.0, "high": 61.0},
            {"paramId": "MB004_OUTLET_TEMP", "code": "outletTemp", "name": "Exhaust Temperature", "unit": "°C", "base": 35.0, "low": 20.0, "high": 49.0},
        ],
        "MB005": [
            {"paramId": "MB005_ACTUAL_RPM", "code": "actualRpm", "name": "Blender Speed", "unit": "RPM", "base": 5.0, "low": 4.5, "high": 5.5},
        ],
        "MB041": [
            {"paramId": "MB041_INLET_AIR_TEMP", "code": "inletAirTemp", "name": "Inlet Air Temperature", "unit": "°C", "base": 60.0, "low": 29.0, "high": 63.4},
            {"paramId": "MB041_BED_TEMP", "code": "bedTemp", "name": "Tablet Bed Temperature", "unit": "°C", "base": 44.0, "low": 23.6, "high": 50.5},
            {"paramId": "MB041_PAN_SPEED", "code": "panSpeed", "name": "Pan Speed", "unit": "RPM", "base": 2.1, "low": 2.0, "high": 2.2},
            {"paramId": "MB041_SPRAY_RATE", "code": "sprayRate", "name": "Dosing Speed / Spray Rate", "unit": "RPM", "base": 14.6, "low": 13.6, "high": 15.6},
        ],
        "MB040": [
            {"paramId": "MB040_TURRET_RPM", "code": "turretRpm", "name": "Turret Speed", "unit": "RPM", "base": 30.0, "low": 25.0, "high": 40.0},
            {"paramId": "MB040_MAIN_FORCE", "code": "mainCompForce", "name": "Main Compression Force", "unit": "kN", "base": 19.5, "low": 15.0, "high": 22.0},
            {"paramId": "MB040_PRE_FORCE", "code": "preCompForce", "name": "Pre Compression Force", "unit": "kN", "base": 2.3, "low": 1.5, "high": 3.0},
        ],
    }

    for eq_code, params in param_specs.items():
        for p in params:
            param_doc = {
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
                "equipmentId": eq_code,
                "parameterId": p["paramId"],
                "parameterCode": p["code"],
                "parameterName": p["name"],
                "unitOfMeasure": p["unit"],
                "dataType": "NUMERIC",
                "isActive": True,
                "createdAt": now_dt,
                "updatedAt": now_dt,
            }
            db.iiot_equipment_critical_parameters.update_one(
                {"equipmentId": eq_code, "parameterId": p["paramId"]},
                {"$set": param_doc},
                upsert=True,
            )

            limit_doc = {
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
                "equipmentId": eq_code,
                "parameterId": p["paramId"],
                "parameterCode": p["code"],
                "baseValue": p["base"],
                "lowerLimitWarning": p["low"],
                "lowerLimitCritical": p["low"] * 0.9,
                "upperLimitWarning": p["high"],
                "upperLimitCritical": p["high"] * 1.1,
                "effectiveFrom": datetime(2026, 1, 1),
                "isActive": True,
                "createdAt": now_dt,
                "updatedAt": now_dt,
            }
            db.iiot_equipment_critical_parameters_limit.update_one(
                {"equipmentId": eq_code, "parameterId": p["paramId"]},
                {"$set": limit_doc},
                upsert=True,
            )
    print("✓ Critical parameters and limits synchronized.")

    print("\n--- 5. Synchronizing Users, Credentials & Role Mappings ---")
    for u in USERS:
        # 1. Profile
        prof_doc = {
            "userId": u["userId"],
            "userTrackId": f"USR-{u['userId']}",
            "empId": f"EMP-{u['userId']}",
            "username": u["username"],
            "email": u["email"],
            "firstName": u["firstName"],
            "lastName": u["lastName"],
            "title": u["title"],
            "role": u["role"],
            "tenantId": TENANT_ID,
            "isActive": True,
            "isBlocked": False,
            "isExternal": False,
            "userType": "INTERNAL_EMPLOYEE",
            "updatedAt": now_dt,
        }
        db.mdm_user_profiles.update_one(
            {"userId": u["userId"]},
            {"$set": prof_doc, "$setOnInsert": {"createdAt": now_dt}},
            upsert=True,
        )

        # 1b. Auth Users
        auth_doc = {
            "userId": u["userId"],
            "username": u["username"],
            "email": u["email"],
            "status": "ACTIVE",
            "isLocked": False,
            "failedAttempts": 0,
            "updatedAt": now_dt,
        }
        db.auth_users.update_one(
            {"userId": u["userId"]},
            {"$set": auth_doc, "$setOnInsert": {"createdAt": now_dt}},
            upsert=True,
        )

        # 2. Auth Credentials (Adavis@123)
        cred_doc = {
            "userId": u["userId"],
            "email": u["email"],
            "passwordHash": DEFAULT_PASSWORD_HASH,
            "mustChangePassword": False,
            "passwordUpdatedAt": now_dt,
            "updatedAt": now_dt,
        }
        db.mdm_user_auth_credentials.update_one(
            {"userId": u["userId"]},
            {"$set": cred_doc, "$setOnInsert": {"createdAt": now_dt}},
            upsert=True,
        )

        # 3. Group assignment
        db.mdm_user_assignments_to_user_groups.update_one(
            {"userId": u["userId"], "groupId": u["groupId"]},
            {
                "$set": {
                    "userId": u["userId"],
                    "groupId": u["groupId"],
                    "isActive": True,
                    "assignedAt": now_dt,
                    "assignedBy": "SYSTEM",
                }
            },
            upsert=True,
        )

        # 4. Context assignment
        db.mdm_user_context_assignments.update_one(
            {"userId": u["userId"], "plantId": PLANT_ID},
            {
                "$set": {
                    "assignmentId": f"ASGN-{u['userId']}",
                    "tenantId": TENANT_ID,
                    "userId": u["userId"],
                    "plantId": PLANT_ID,
                    "departmentId": "DEP-0002",
                    "groupId": u["groupId"],
                    "isActive": True,
                }
            },
            upsert=True,
        )

        print(f"  ✓ User synced: {u['userId']} ({u['firstName']} {u['lastName']}) - {u['role']} ({u['title']})")

    print("\n--- 6. Initializing Live Status (`iiot_equipment_live_status`) ---")
    live_statuses = [
        {"equipmentId": "MB003", "lastBatchNo": "AGO0026016", "lastLotNo": "01", "state": "Running", "reason": "Batch in progress: AGO0026016"},
        {"equipmentId": "MB004", "lastBatchNo": "AGO0026016", "lastLotNo": "1B", "state": "Running", "reason": "Batch in progress: AGO0026016"},
        {"equipmentId": "MB005", "lastBatchNo": "AGO0026015", "lastLotNo": "01", "state": "Running", "reason": "Batch in progress: AGO0026015"},
        {"equipmentId": "MB040", "lastBatchNo": "ADNC26011", "lastLotNo": "01", "state": "Running", "reason": "Batch in progress: ADNC26011"},
        {"equipmentId": "MB041", "lastBatchNo": "PED26009", "lastLotNo": "01", "state": "Running", "reason": "Batch in progress: PED26009"},
    ]
    for ls in live_statuses:
        db.iiot_equipment_live_status.update_one(
            {"equipmentId": ls["equipmentId"]},
            {
                "$set": {
                    "equipmentId": ls["equipmentId"],
                    "currentState": ls["state"],
                    "stateReason": ls["reason"],
                    "lastBatchNo": ls["lastBatchNo"],
                    "lastLotNo": ls["lastLotNo"],
                    "lastEventAt": now_dt.isoformat() + "Z",
                    "heartbeatAt": now_dt.isoformat() + "Z",
                    "updatedAt": now_dt,
                },
                "$setOnInsert": {"createdAt": now_dt},
            },
            upsert=True,
        )
    print("✓ Live statuses initialized.")

    print("\n=======================================================")
    print(" Master data synchronization completed successfully! ")
    print("=======================================================")


if __name__ == "__main__":
    sync_master_data()
