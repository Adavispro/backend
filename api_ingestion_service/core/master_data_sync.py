"""
Master Data Synchronization Manager for API Ingestion Service.
Synchronizes equipment master, critical parameters, product recipes, and topology
for the Aurobindo Unit-VII production line (MB003, MB004, MB005, MB041).
"""

from __future__ import annotations

import logging
import re
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

logger = logging.getLogger("api_ingestion_service.master_sync")

TENANT_ID = "TNT-0001"
PLANT_ID = "PLNT-0001"
BLOCK_ID = "PB1"
AREA_GRANULATION = "AREA-GRAN"
AREA_BLENDING = "AREA-BLEND"
AREA_COATING = "AREA-COAT"

EQUIPMENT_DEFINITIONS = [
    {
        "equipmentSeqId": 10094,
        "tenantId": TENANT_ID,
        "plantId": PLANT_ID,
        "blockId": BLOCK_ID,
        "areaId": AREA_GRANULATION,
        "roomId": "RM-RMG-01",
        "equipmentId": "MB003",
        "equipment_id": "MB003",
        "equipmentCode": "MB003",
        "equipment_code": "MB003",
        "assetId": "10094",
        "asset_id": "10094",
        "equipmentName": "Rapid Mixer Granulator (MB003)",
        "equipmentType": "RMG",
        "equipment_type": "RMG",
        "equipmentTypeName": "Rapid Mixer Granulator",
        "stageOrder": 1,
        "stageName": "Granulation",
        "make": "MITSUBISHI",
        "model": "FX5U",
        "plcType": "MITSUBISHI FX5U",
        "dataSource": "SQL",
        "isActive": True,
    },
    {
        "equipmentSeqId": 10110,
        "tenantId": TENANT_ID,
        "plantId": PLANT_ID,
        "blockId": BLOCK_ID,
        "areaId": AREA_GRANULATION,
        "roomId": "RM-FBD-01",
        "equipmentId": "MB004",
        "equipment_id": "MB004",
        "equipmentCode": "MB004",
        "equipment_code": "MB004",
        "assetId": "10110",
        "asset_id": "10110",
        "equipmentName": "Fluid Bed Dryer (MB004)",
        "equipmentType": "FBD",
        "equipment_type": "FBD",
        "equipmentTypeName": "Fluid Bed Dryer",
        "stageOrder": 2,
        "stageName": "Drying",
        "make": "MITSUBISHI",
        "model": "FX5U",
        "plcType": "MITSUBISHI FX5U",
        "dataSource": "SQL",
        "isActive": True,
    },
    {
        "equipmentSeqId": 10012,
        "tenantId": TENANT_ID,
        "plantId": PLANT_ID,
        "blockId": BLOCK_ID,
        "areaId": AREA_BLENDING,
        "roomId": "RM-BLND-01",
        "equipmentId": "MB005",
        "equipment_id": "MB005",
        "equipmentCode": "MB005",
        "equipment_code": "MB005",
        "assetId": "10012",
        "asset_id": "10012",
        "equipmentName": "Octagonal Blender (MB005)",
        "equipmentType": "BLE",
        "equipment_type": "BLE",
        "equipmentTypeName": "Blender",
        "stageOrder": 3,
        "stageName": "Blending",
        "make": "MITSUBISHI",
        "model": "FX3U",
        "plcType": "MITSUBISHI FX3U",
        "dataSource": "SQL",
        "isActive": True,
    },
    {
        "equipmentSeqId": 10021,
        "tenantId": TENANT_ID,
        "plantId": PLANT_ID,
        "blockId": BLOCK_ID,
        "areaId": AREA_COATING,
        "roomId": "RM-COAT-01",
        "equipmentId": "MB041",
        "equipment_id": "MB041",
        "equipmentCode": "MB041",
        "equipment_code": "MB041",
        "assetId": "10021",
        "asset_id": "10021",
        "equipmentName": "Auto Coater (MB041)",
        "equipmentType": "COAT",
        "equipment_type": "COAT",
        "equipmentTypeName": "Auto Coater",
        "stageOrder": 4,
        "stageName": "Coating",
        "make": "MITSUBISHI",
        "model": "FX3U",
        "plcType": "MITSUBISHI FX3U",
        "dataSource": "SQL",
        "isActive": True,
    },
]

CRITICAL_PARAMETERS = {
    "MB003": [
        {"paramId": "PRM-RMG-01", "code": "IMP_SPD", "name": "Impeller Speed", "unit": "RPM", "base": 150.0, "low": 50.0, "high": 250.0},
        {"paramId": "PRM-RMG-02", "code": "CHP_SPD", "name": "Chopper Speed", "unit": "RPM", "base": 1400.0, "low": 500.0, "high": 2800.0},
        {"paramId": "PRM-RMG-03", "code": "IMP_AMP", "name": "Impeller Current", "unit": "Amp", "base": 18.0, "low": 5.0, "high": 35.0},
    ],
    "MB004": [
        {"paramId": "PRM-FBD-01", "code": "INLET_TEMP", "name": "Inlet Air Temperature", "unit": "°C", "base": 65.0, "low": 40.0, "high": 85.0},
        {"paramId": "PRM-FBD-02", "code": "BED_TEMP", "name": "Product Bed Temperature", "unit": "°C", "base": 45.0, "low": 30.0, "high": 60.0},
        {"paramId": "PRM-FBD-03", "code": "AIR_FLOW", "name": "Inlet Air Flow", "unit": "CFM", "base": 1200.0, "low": 600.0, "high": 2000.0},
    ],
    "MB005": [
        {"paramId": "PRM-BLE-01", "code": "BLD_SPD", "name": "Blender Speed", "unit": "RPM", "base": 12.0, "low": 4.0, "high": 20.0},
        {"paramId": "PRM-BLE-02", "code": "BLD_TIME", "name": "Blending Duration", "unit": "Min", "base": 15.0, "low": 5.0, "high": 45.0},
    ],
    "MB041": [
        {"paramId": "PRM-COT-01", "code": "INLET_AIR_TEMP", "name": "Inlet Air Temperature", "unit": "°C", "base": 60.0, "low": 40.0, "high": 80.0},
        {"paramId": "PRM-COT-02", "code": "EXHAUST_TEMP", "name": "Exhaust Air Temperature", "unit": "°C", "base": 46.0, "low": 35.0, "high": 55.0},
        {"paramId": "PRM-COT-03", "code": "PAN_SPEED", "name": "Pan Speed", "unit": "RPM", "base": 8.0, "low": 2.0, "high": 18.0},
        {"paramId": "PRM-COT-04", "code": "SPRAY_RATE", "name": "Spray Rate", "unit": "g/min", "base": 120.0, "low": 40.0, "high": 250.0},
    ],
}

PRODUCT_DEFINITIONS = [
    {"productId": "PRD-PAROX-01", "productCode": "Paroxetine", "productName": "Paroxetine Tablets USP"},
    {"productId": "PRD-PAROX-20", "productCode": "Paroxetine USP 20mg", "productName": "Paroxetine Tablets USP 20mg"},
    {"productId": "PRD-PAROX-40", "productCode": "PAROXETINE USP 40 mg", "productName": "Paroxetine Tablets USP 40mg"},
    {"productId": "PRD-PAROX-BLD", "productCode": "PAROXETINE BLEND", "productName": "Paroxetine Granulation Blend"},
    {"productId": "PRD-LAMO-01", "productCode": "LAMOTRIGINE", "productName": "Lamotrigine Tablets USP"},
    {"productId": "PRD-CARV-01", "productCode": "Carvedilol 12.5mg", "productName": "Carvedilol Tablets 12.5mg"},
    {"productId": "STFS7000", "productCode": "STFS7000", "productName": "Mirtazapine Tablets USP 5 mg"},
    {"productId": "STAPU1000", "productCode": "STAPU1000", "productName": "Allopurinol tablets"},
    {"productId": "STLEV5000", "productCode": "STLEV5000", "productName": "Levetiracetam tablets"},
    {"productId": "PRD-AMIS-100", "productCode": "Amisulpride 100mg", "productName": "Amisulpride Tablets 100mg"},
    {"productId": "PRD-AMIS-200", "productCode": "Amisulpride 200mg", "productName": "Amisulpride Tablets 200mg"},
    {"productId": "PRD-MIRT-15", "productCode": "Mirtazapine OD 15mg", "productName": "Mirtazapine OD Tablets 15mg"},
    {"productId": "PRD-MIRT-30", "productCode": "Mirtazapine OD 30mg", "productName": "Mirtazapine OD Tablets 30mg"},
    {"productId": "PRD-MIRT-45", "productCode": "Mirtazapine OD 45mg", "productName": "Mirtazapine OD Tablets 45mg"},
    {"productId": "PRD-SERT-50", "productCode": "Sertraline 50mg", "productName": "Sertraline Tablets 50mg"},
    {"productId": "PRD-SERT-100", "productCode": "Sertraline 100mg", "productName": "Sertraline Tablets 100mg"},
]

RECIPE_DEFINITIONS = [
    # Paroxetine
    {
        "recipeId": "RCP-PAROX-RMG",
        "recipeCode": "RCP-Paroxetine-RMG",
        "recipeName": "Paroxetine Granulation Recipe",
        "productCode": "Paroxetine",
        "productName": "Paroxetine Tablets USP",
        "description": "High shear wet granulation recipe for Paroxetine",
        "version": "1.0",
        "associatedBatchSizes": ["1000 KG", "2000 KG", "5000 KG"],
    },
    {
        "recipeId": "RCP-PAROX-FBD",
        "recipeCode": "RCP-Paroxetine-FBD",
        "recipeName": "Paroxetine Fluid Bed Drying Recipe",
        "productCode": "Paroxetine",
        "productName": "Paroxetine Tablets USP",
        "description": "Fluid bed drying cycle for Paroxetine granules",
        "version": "1.0",
        "associatedBatchSizes": ["1000 KG", "2000 KG"],
    },
    {
        "recipeId": "RCP-PAROX-BLE",
        "recipeCode": "RCP-Paroxetine-BLE",
        "recipeName": "Paroxetine Blending & Lubrication Recipe",
        "productCode": "Paroxetine",
        "productName": "Paroxetine Tablets USP",
        "description": "Octagonal blender mixing recipe for Paroxetine",
        "version": "1.0",
        "associatedBatchSizes": ["1000 KG", "2000 KG", "5000 KG"],
    },
    {
        "recipeId": "RCP-PAROX-COAT",
        "recipeCode": "RCP-Paroxetine-COAT",
        "recipeName": "Paroxetine Film Coating Recipe",
        "productCode": "Paroxetine",
        "productName": "Paroxetine Tablets USP",
        "description": "Auto coater film coating recipe for Paroxetine",
        "version": "1.0",
        "associatedBatchSizes": ["1000 KG", "2000 KG"],
    },
    # Lamotrigine
    {
        "recipeId": "RCP-LAMO-RMG",
        "recipeCode": "RCP-LAMO-01",
        "recipeName": "Lamotrigine Granulation Recipe",
        "productCode": "LAMOTRIGINE",
        "productName": "Lamotrigine Tablets USP",
        "description": "Granulation process recipe for Lamotrigine",
        "version": "1.0",
        "associatedBatchSizes": ["1000 KG", "2500 KG"],
    },
    {
        "recipeId": "RCP-LAMO-FBD",
        "recipeCode": "RCP-LAMO-FBD",
        "recipeName": "Lamotrigine Fluid Bed Drying Recipe",
        "productCode": "LAMOTRIGINE",
        "productName": "Lamotrigine Tablets USP",
        "description": "Fluid bed drying cycle for Lamotrigine",
        "version": "1.0",
        "associatedBatchSizes": ["1000 KG", "2500 KG"],
    },
    {
        "recipeId": "RCP-LAMO-BLE",
        "recipeCode": "RCP-LAMO-BLE",
        "recipeName": "Lamotrigine Blending Recipe",
        "productCode": "LAMOTRIGINE",
        "productName": "Lamotrigine Tablets USP",
        "description": "Octagonal blender recipe for Lamotrigine",
        "version": "1.0",
        "associatedBatchSizes": ["1000 KG", "2500 KG"],
    },
    {
        "recipeId": "RCP-LAMO-COAT",
        "recipeCode": "RCP-LAMO-COAT",
        "recipeName": "Lamotrigine Film Coating Recipe",
        "productCode": "LAMOTRIGINE",
        "productName": "Lamotrigine Tablets USP",
        "description": "Auto coater recipe for Lamotrigine",
        "version": "1.0",
        "associatedBatchSizes": ["1000 KG", "2500 KG"],
    },
    # Carvedilol
    {
        "recipeId": "RCP-CARV-RMG",
        "recipeCode": "RCP-CARV-RMG",
        "recipeName": "Carvedilol 12.5mg Granulation Recipe",
        "productCode": "Carvedilol 12.5mg",
        "productName": "Carvedilol Tablets 12.5mg",
        "description": "Granulation recipe for Carvedilol 12.5mg",
        "version": "1.0",
        "associatedBatchSizes": ["1000 KG", "2000 KG"],
    },
    {
        "recipeId": "RCP-CARV-FBD",
        "recipeCode": "RCP-CARV-FBD",
        "recipeName": "Carvedilol 12.5mg Drying Recipe",
        "productCode": "Carvedilol 12.5mg",
        "productName": "Carvedilol Tablets 12.5mg",
        "description": "Fluid bed drying recipe for Carvedilol 12.5mg",
        "version": "1.0",
        "associatedBatchSizes": ["1000 KG", "2000 KG"],
    },
    {
        "recipeId": "RCP-CARV-BLE",
        "recipeCode": "RCP-CARV-01",
        "recipeName": "Carvedilol 12.5mg Blending Recipe",
        "productCode": "Carvedilol 12.5mg",
        "productName": "Carvedilol Tablets 12.5mg",
        "description": "Blending recipe for Carvedilol 12.5mg",
        "version": "1.0",
        "associatedBatchSizes": ["1000 KG", "2000 KG"],
    },
    {
        "recipeId": "RCP-CARV-COAT",
        "recipeCode": "RCP-CARV-COAT",
        "recipeName": "Carvedilol 12.5mg Coating Recipe",
        "productCode": "Carvedilol 12.5mg",
        "productName": "Carvedilol Tablets 12.5mg",
        "description": "Film coating recipe for Carvedilol 12.5mg",
        "version": "1.0",
        "associatedBatchSizes": ["1000 KG", "2000 KG"],
    },
    # Mirtazapine
    {
        "recipeId": "RCP-MIRT-RMG",
        "recipeCode": "RCP-MIRT-RMG",
        "recipeName": "Mirtazapine Granulation Recipe",
        "productCode": "Mirtazapine OD 45mg",
        "productName": "Mirtazapine OD Tablets 45mg",
        "description": "Granulation process for Mirtazapine tablets",
        "version": "1.0",
        "associatedBatchSizes": ["1000 KG", "2000 KG", "5000 KG"],
    },
    {
        "recipeId": "RCP-MIRT-FBD",
        "recipeCode": "RCP-MIRT-FBD",
        "recipeName": "Mirtazapine Fluid Bed Drying Recipe",
        "productCode": "Mirtazapine OD 45mg",
        "productName": "Mirtazapine OD Tablets 45mg",
        "description": "Fluid bed drying recipe for Mirtazapine",
        "version": "1.0",
        "associatedBatchSizes": ["1000 KG", "2000 KG"],
    },
    {
        "recipeId": "RCP-MIRT-BLE",
        "recipeCode": "RCP-MIRT-BLE",
        "recipeName": "Mirtazapine Blending Recipe",
        "productCode": "Mirtazapine OD 45mg",
        "productName": "Mirtazapine OD Tablets 45mg",
        "description": "Blending recipe for Mirtazapine",
        "version": "1.0",
        "associatedBatchSizes": ["1000 KG", "2000 KG"],
    },
    # Allopurinol
    {
        "recipeId": "RCP-ALLO-RMG",
        "recipeCode": "RCP-ALLO-RMG",
        "recipeName": "Allopurinol Granulation Recipe",
        "productCode": "STAPU1000",
        "productName": "Allopurinol tablets",
        "description": "Granulation process for Allopurinol 100mg",
        "version": "1.0",
        "associatedBatchSizes": ["1000 KG", "2500 KG"],
    },
    {
        "recipeId": "RCP-ALLO-BLE",
        "recipeCode": "RCP-ALLO-01",
        "recipeName": "Allopurinol 100mg Direct Compression & Blending Recipe",
        "productCode": "STAPU1000",
        "productName": "Allopurinol tablets",
        "description": "Direct compression and blending process for Allopurinol 100mg",
        "version": "1.0",
        "associatedBatchSizes": ["1000 KG", "2500 KG"],
    },
    # Levetiracetam
    {
        "recipeId": "RCP-LEVE-RMG",
        "recipeCode": "RCP-LEVE-RMG",
        "recipeName": "Levetiracetam Granulation Recipe",
        "productCode": "STLEV5000",
        "productName": "Levetiracetam tablets",
        "description": "Granulation recipe for Levetiracetam 500mg",
        "version": "1.0",
        "associatedBatchSizes": ["1500 KG", "3000 KG"],
    },
    {
        "recipeId": "RCP-LEVE-COAT",
        "recipeCode": "RCP-LEVE-01",
        "recipeName": "Levetiracetam 500mg Coating Recipe",
        "productCode": "STLEV5000",
        "productName": "Levetiracetam tablets",
        "description": "Film coating recipe for Levetiracetam 500mg",
        "version": "1.0",
        "associatedBatchSizes": ["1500 KG", "3000 KG"],
    },
]

RECIPE_MANAGEMENT_DEFINITIONS = [
    # MB003 (RMG) - Paroxetine
    {
        "recipeManagementId": "RCM-MB003-PAROX-01",
        "productCode": "Paroxetine",
        "recipeCode": "RCP-Paroxetine-RMG",
        "equipmentCode": "MB003",
        "parameterCode": "IMP_SPD",
        "parameterName": "Impeller Speed",
        "unitOfMeasure": "RPM",
        "targetSetpoint": 150.0,
        "lowLimit": 100.0,
        "highLimit": 200.0,
        "batchSize": "1000 KG",
    },
    {
        "recipeManagementId": "RCM-MB003-PAROX-02",
        "productCode": "Paroxetine",
        "recipeCode": "RCP-Paroxetine-RMG",
        "equipmentCode": "MB003",
        "parameterCode": "CHP_SPD",
        "parameterName": "Chopper Speed",
        "unitOfMeasure": "RPM",
        "targetSetpoint": 1400.0,
        "lowLimit": 1000.0,
        "highLimit": 1800.0,
        "batchSize": "1000 KG",
    },
    {
        "recipeManagementId": "RCM-MB003-PAROX-03",
        "productCode": "Paroxetine",
        "recipeCode": "RCP-Paroxetine-RMG",
        "equipmentCode": "MB003",
        "parameterCode": "IMP_AMP",
        "parameterName": "Impeller Current",
        "unitOfMeasure": "Amp",
        "targetSetpoint": 18.0,
        "lowLimit": 10.0,
        "highLimit": 30.0,
        "batchSize": "1000 KG",
    },
    # MB003 (RMG) - Lamotrigine
    {
        "recipeManagementId": "RCM-MB003-LAMO-01",
        "productCode": "LAMOTRIGINE",
        "recipeCode": "RCP-LAMO-01",
        "equipmentCode": "MB003",
        "parameterCode": "IMP_SPD",
        "parameterName": "Impeller Speed",
        "unitOfMeasure": "RPM",
        "targetSetpoint": 145.0,
        "lowLimit": 95.0,
        "highLimit": 195.0,
        "batchSize": "1000 KG",
    },
    {
        "recipeManagementId": "RCM-MB003-LAMO-02",
        "productCode": "LAMOTRIGINE",
        "recipeCode": "RCP-LAMO-01",
        "equipmentCode": "MB003",
        "parameterCode": "CHP_SPD",
        "parameterName": "Chopper Speed",
        "unitOfMeasure": "RPM",
        "targetSetpoint": 1350.0,
        "lowLimit": 950.0,
        "highLimit": 1750.0,
        "batchSize": "1000 KG",
    },
    # MB004 (FBD) - Paroxetine
    {
        "recipeManagementId": "RCM-MB004-PAROX-01",
        "productCode": "Paroxetine",
        "recipeCode": "RCP-Paroxetine-FBD",
        "equipmentCode": "MB004",
        "parameterCode": "INLET_TEMP",
        "parameterName": "Inlet Air Temperature",
        "unitOfMeasure": "°C",
        "targetSetpoint": 65.0,
        "lowLimit": 55.0,
        "highLimit": 75.0,
        "batchSize": "1000 KG",
    },
    {
        "recipeManagementId": "RCM-MB004-PAROX-02",
        "productCode": "Paroxetine",
        "recipeCode": "RCP-Paroxetine-FBD",
        "equipmentCode": "MB004",
        "parameterCode": "BED_TEMP",
        "parameterName": "Product Bed Temperature",
        "unitOfMeasure": "°C",
        "targetSetpoint": 45.0,
        "lowLimit": 38.0,
        "highLimit": 52.0,
        "batchSize": "1000 KG",
    },
    {
        "recipeManagementId": "RCM-MB004-PAROX-03",
        "productCode": "Paroxetine",
        "recipeCode": "RCP-Paroxetine-FBD",
        "equipmentCode": "MB004",
        "parameterCode": "AIR_FLOW",
        "parameterName": "Inlet Air Flow",
        "unitOfMeasure": "CFM",
        "targetSetpoint": 1200.0,
        "lowLimit": 800.0,
        "highLimit": 1600.0,
        "batchSize": "1000 KG",
    },
    # MB005 (Blender) - Paroxetine
    {
        "recipeManagementId": "RCM-MB005-PAROX-01",
        "productCode": "Paroxetine",
        "recipeCode": "RCP-Paroxetine-BLE",
        "equipmentCode": "MB005",
        "parameterCode": "BLD_SPD",
        "parameterName": "Blender Speed",
        "unitOfMeasure": "RPM",
        "targetSetpoint": 12.0,
        "lowLimit": 8.0,
        "highLimit": 16.0,
        "batchSize": "1000 KG",
    },
    {
        "recipeManagementId": "RCM-MB005-PAROX-02",
        "productCode": "Paroxetine",
        "recipeCode": "RCP-Paroxetine-BLE",
        "equipmentCode": "MB005",
        "parameterCode": "BLD_TIME",
        "parameterName": "Blending Duration",
        "unitOfMeasure": "Min",
        "targetSetpoint": 15.0,
        "lowLimit": 10.0,
        "highLimit": 25.0,
        "batchSize": "1000 KG",
    },
    # MB005 (Blender) - Carvedilol
    {
        "recipeManagementId": "RCM-MB005-CARV-01",
        "productCode": "Carvedilol 12.5mg",
        "recipeCode": "RCP-CARV-01",
        "equipmentCode": "MB005",
        "parameterCode": "BLD_SPD",
        "parameterName": "Blender Speed",
        "unitOfMeasure": "RPM",
        "targetSetpoint": 14.0,
        "lowLimit": 9.0,
        "highLimit": 18.0,
        "batchSize": "1000 KG",
    },
    {
        "recipeManagementId": "RCM-MB005-CARV-02",
        "productCode": "Carvedilol 12.5mg",
        "recipeCode": "RCP-CARV-01",
        "equipmentCode": "MB005",
        "parameterCode": "BLD_TIME",
        "parameterName": "Blending Duration",
        "unitOfMeasure": "Min",
        "targetSetpoint": 18.0,
        "lowLimit": 12.0,
        "highLimit": 28.0,
        "batchSize": "1000 KG",
    },
    # MB041 (Auto Coater) - Paroxetine
    {
        "recipeManagementId": "RCM-MB041-PAROX-01",
        "productCode": "Paroxetine",
        "recipeCode": "RCP-Paroxetine-COAT",
        "equipmentCode": "MB041",
        "parameterCode": "INLET_AIR_TEMP",
        "parameterName": "Inlet Air Temperature",
        "unitOfMeasure": "°C",
        "targetSetpoint": 60.0,
        "lowLimit": 50.0,
        "highLimit": 70.0,
        "batchSize": "1000 KG",
    },
    {
        "recipeManagementId": "RCM-MB041-PAROX-02",
        "productCode": "Paroxetine",
        "recipeCode": "RCP-Paroxetine-COAT",
        "equipmentCode": "MB041",
        "parameterCode": "EXHAUST_TEMP",
        "parameterName": "Exhaust Air Temperature",
        "unitOfMeasure": "°C",
        "targetSetpoint": 46.0,
        "lowLimit": 40.0,
        "highLimit": 52.0,
        "batchSize": "1000 KG",
    },
    {
        "recipeManagementId": "RCM-MB041-PAROX-03",
        "productCode": "Paroxetine",
        "recipeCode": "RCP-Paroxetine-COAT",
        "equipmentCode": "MB041",
        "parameterCode": "PAN_SPEED",
        "parameterName": "Pan Speed",
        "unitOfMeasure": "RPM",
        "targetSetpoint": 8.0,
        "lowLimit": 5.0,
        "highLimit": 12.0,
        "batchSize": "1000 KG",
    },
    {
        "recipeManagementId": "RCM-MB041-PAROX-04",
        "productCode": "Paroxetine",
        "recipeCode": "RCP-Paroxetine-COAT",
        "equipmentCode": "MB041",
        "parameterCode": "SPRAY_RATE",
        "parameterName": "Spray Rate",
        "unitOfMeasure": "g/min",
        "targetSetpoint": 120.0,
        "lowLimit": 80.0,
        "highLimit": 160.0,
        "batchSize": "1000 KG",
    },
    # MB041 (Auto Coater) - Levetiracetam
    {
        "recipeManagementId": "RCM-MB041-LEVE-01",
        "productCode": "STLEV5000",
        "recipeCode": "RCP-LEVE-01",
        "equipmentCode": "MB041",
        "parameterCode": "INLET_AIR_TEMP",
        "parameterName": "Inlet Air Temperature",
        "unitOfMeasure": "°C",
        "targetSetpoint": 58.0,
        "lowLimit": 48.0,
        "highLimit": 68.0,
        "batchSize": "1500 KG",
    },
    {
        "recipeManagementId": "RCM-MB041-LEVE-02",
        "productCode": "STLEV5000",
        "recipeCode": "RCP-LEVE-01",
        "equipmentCode": "MB041",
        "parameterCode": "SPRAY_RATE",
        "parameterName": "Spray Rate",
        "unitOfMeasure": "g/min",
        "targetSetpoint": 115.0,
        "lowLimit": 75.0,
        "highLimit": 155.0,
        "batchSize": "1500 KG",
    },
]


def sanitize_code(value: Any) -> str:
    """Sanitize code strings (alphanumeric with hyphens/underscores)."""
    if value is None:
        return ""
    text = str(value).strip()
    text = re.sub(r"[^A-Za-z0-9_-]", "-", text)
    return re.sub(r"-+", "-", text).strip("-")


def normalize_batch_size_str(value: Any) -> str:
    """Normalize batch size representation to standard string."""
    if value is None:
        return ""
    text = str(value).strip()
    if not text or text.lower() in ("-", "null", "none", "na"):
        return ""
    return text


class MasterDataSyncManager:
    def __init__(self, db: Any) -> None:
        self.db = db

    def sync_equipment_master(self) -> None:
        """Upsert standard equipment records into iiot_equipment_master & iiot_equiment_master."""
        if self.db is None:
            return
        now_dt = datetime.now(timezone.utc)
        for eq in EQUIPMENT_DEFINITIONS:
            doc = dict(eq)
            doc["updatedAt"] = now_dt
            self.db.iiot_equipment_master.update_one(
                {"equipmentId": eq["equipmentId"]},
                {"$set": doc, "$setOnInsert": {"createdAt": now_dt}},
                upsert=True,
            )
            # Legacy typo collection support
            self.db.iiot_equiment_master.update_one(
                {"equipmentId": eq["equipmentId"]},
                {"$set": doc, "$setOnInsert": {"createdAt": now_dt}},
                upsert=True,
            )
        logger.info(f"Synchronized {len(EQUIPMENT_DEFINITIONS)} equipment master records.")

    def sync_critical_parameters(self) -> None:
        """Upsert critical process parameters and operational limits."""
        if self.db is None:
            return
        now_dt = datetime.now(timezone.utc)
        for eq_code, params in CRITICAL_PARAMETERS.items():
            for p in params:
                param_doc = {
                    "tenantId": TENANT_ID,
                    "plantId": PLANT_ID,
                    "equipmentId": eq_code,
                    "equipmentCode": eq_code,
                    "parameterId": p["paramId"],
                    "parameterCode": p["code"],
                    "parameterName": p["name"],
                    "unitOfMeasure": p["unit"],
                    "dataType": "NUMERIC",
                    "isActive": True,
                    "createdAt": now_dt,
                    "updatedAt": now_dt,
                }
                self.db.iiot_equipment_critical_parameters.update_one(
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
                    "effectiveFrom": datetime(2026, 1, 1, tzinfo=timezone.utc),
                    "isActive": True,
                    "createdAt": now_dt,
                    "updatedAt": now_dt,
                }
                self.db.iiot_equipment_critical_parameters_limit.update_one(
                    {"equipmentId": eq_code, "parameterId": p["paramId"]},
                    {"$set": limit_doc},
                    upsert=True,
                )
        logger.info("Synchronized critical parameters and recipe limits.")

    def sync_product_master(self) -> None:
        """Upsert standard and active pharmaceutical products into iiot_product_master & products."""
        if self.db is None:
            return
        now_dt = datetime.now(timezone.utc)
        for prod in PRODUCT_DEFINITIONS:
            prod_id = prod.get("productId") or prod["productCode"]
            doc = {
                "productId": prod_id,
                "productCode": prod["productCode"],
                "productName": prod["productName"],
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
                "isActive": True,
                "updatedAt": now_dt,
            }
            self.db.iiot_product_master.update_one(
                {"productCode": prod["productCode"]},
                {"$set": doc, "$setOnInsert": {"createdAt": now_dt}},
                upsert=True,
            )
            self.db.products.update_one(
                {"product_code": prod["productCode"]},
                {
                    "$set": {
                        "product_name": prod["productName"],
                        "tenantId": TENANT_ID,
                        "plantId": PLANT_ID,
                        "updated_at": now_dt,
                    },
                    "$setOnInsert": {"product_code": prod["productCode"], "created_at": now_dt},
                },
                upsert=True,
            )
        logger.info(f"Synchronized {len(PRODUCT_DEFINITIONS)} product master records.")

    def sync_recipe_master(self) -> None:
        """Upsert standard recipe master records into iiot_recipe_master & iiot_batch_recipes."""
        if self.db is None:
            return
        now_dt = datetime.now(timezone.utc)
        for r in RECIPE_DEFINITIONS:
            doc = {
                "recipeId": r["recipeId"],
                "recipeCode": r["recipeCode"],
                "recipeName": r["recipeName"],
                "productId": r.get("productId") or r["productCode"],
                "productCode": r["productCode"],
                "productName": r.get("productName", ""),
                "description": r.get("description", ""),
                "version": r.get("version", "1.0"),
                "associatedBatchSizes": r.get("associatedBatchSizes", ["1000 KG", "2000 KG", "5000 KG"]),
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
                "isActive": True,
                "updatedAt": now_dt,
            }
            self.db.iiot_recipe_master.update_one(
                {"recipeCode": r["recipeCode"]},
                {"$set": doc, "$setOnInsert": {"createdAt": now_dt}},
                upsert=True,
            )
        logger.info(f"Synchronized {len(RECIPE_DEFINITIONS)} recipe master records.")

    def sync_recipe_management(self) -> None:
        """Upsert recipe management operational setpoints and parameter limits."""
        if self.db is None:
            return
        now_dt = datetime.now(timezone.utc)
        eq_name_map = {eq["equipmentCode"]: eq["equipmentName"] for eq in EQUIPMENT_DEFINITIONS}
        for rm in RECIPE_MANAGEMENT_DEFINITIONS:
            eq_code = rm["equipmentCode"]
            eq_name = eq_name_map.get(eq_code, eq_code)
            doc = {
                "recipeManagementId": rm["recipeManagementId"],
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
                "productId": rm.get("productId") or rm["productCode"],
                "productCode": rm["productCode"],
                "productName": rm.get("productName") or rm["productCode"],
                "recipeId": rm.get("recipeId") or rm["recipeCode"],
                "recipeCode": rm["recipeCode"],
                "recipeName": rm.get("recipeName") or rm["recipeCode"],
                "batchSize": rm.get("batchSize", "1000 KG"),
                "equipmentId": eq_code,
                "equipmentCode": eq_code,
                "equipmentName": eq_name,
                "parameterCode": rm["parameterCode"],
                "parameterName": rm["parameterName"],
                "unitOfMeasure": rm.get("unitOfMeasure", ""),
                "uom": rm.get("unitOfMeasure", ""),
                "targetSetpoint": rm.get("targetSetpoint"),
                "lowLimit": rm.get("lowLimit"),
                "highLimit": rm.get("highLimit"),
                "isActive": True,
                "updatedAt": now_dt,
            }
            self.db.iiot_recipe_management.update_one(
                {
                    "equipmentCode": eq_code,
                    "recipeCode": rm["recipeCode"],
                    "parameterCode": rm["parameterCode"],
                },
                {"$set": doc, "$setOnInsert": {"createdAt": now_dt}},
                upsert=True,
            )
        logger.info(f"Synchronized {len(RECIPE_MANAGEMENT_DEFINITIONS)} recipe management parameter limits.")

    def sync_all_master_data(self) -> None:
        """Synchronize complete master data: equipment, critical parameters, products, recipes, and recipe limits."""
        self.sync_equipment_master()
        self.sync_critical_parameters()
        self.sync_product_master()
        self.sync_recipe_master()
        self.sync_recipe_management()

    def validate_and_sync_batch_master(
        self,
        *,
        product_code: str,
        product_name: str,
        recipe_code: Optional[str] = None,
        recipe_name: Optional[str] = None,
        batch_size: Optional[str] = None,
        equipment_code: Optional[str] = None,
        equipment_type: Optional[str] = None,
    ) -> None:
        """Sync product master, recipe master, and recipe management definitions during batch ingestion."""
        if self.db is None or not product_code:
            return
        now_dt = datetime.now(timezone.utc)
        clean_prod = sanitize_code(product_code)
        prod_name = product_name or product_code

        # 1. Product Master Upsert
        prod_doc = {
            "productId": clean_prod or product_code,
            "productCode": product_code,
            "productName": prod_name,
            "tenantId": TENANT_ID,
            "plantId": PLANT_ID,
            "isActive": True,
            "updatedAt": now_dt,
        }
        self.db.iiot_product_master.update_one(
            {"productCode": product_code},
            {"$set": prod_doc, "$setOnInsert": {"createdAt": now_dt}},
            upsert=True,
        )
        self.db.products.update_one(
            {"product_code": product_code},
            {
                "$set": {
                    "product_name": prod_name,
                    "updated_at": now_dt,
                    "tenantId": TENANT_ID,
                    "plantId": PLANT_ID,
                },
                "$setOnInsert": {"product_code": product_code, "created_at": now_dt},
            },
            upsert=True,
        )

        # 2. Recipe Master Upsert (if recipe provided)
        if recipe_code:
            clean_rcp = sanitize_code(recipe_code)
            rcp_name = recipe_name or recipe_code
            rcp_doc = {
                "recipeId": clean_rcp or recipe_code,
                "recipeCode": recipe_code,
                "recipeName": rcp_name,
                "productId": clean_prod or product_code,
                "productCode": product_code,
                "productName": prod_name,
                "description": f"Recipe {rcp_name} for {prod_name}",
                "version": "1.0",
                "associatedBatchSizes": [batch_size] if batch_size else ["1000 KG", "2000 KG", "5000 KG"],
                "tenantId": TENANT_ID,
                "plantId": PLANT_ID,
                "isActive": True,
                "updatedAt": now_dt,
            }
            self.db.iiot_recipe_master.update_one(
                {"recipeCode": recipe_code},
                {"$set": rcp_doc, "$setOnInsert": {"createdAt": now_dt}},
                upsert=True,
            )

            # 3. Recipe Management Upsert for Equipment Critical Parameters
            if equipment_code and equipment_code in CRITICAL_PARAMETERS:
                for param in CRITICAL_PARAMETERS[equipment_code]:
                    rm_id = f"RCM-{equipment_code}-{clean_rcp}-{param['code']}"
                    rm_doc = {
                        "recipeManagementId": rm_id,
                        "tenantId": TENANT_ID,
                        "plantId": PLANT_ID,
                        "productId": clean_prod or product_code,
                        "productCode": product_code,
                        "productName": prod_name,
                        "recipeId": clean_rcp or recipe_code,
                        "recipeCode": recipe_code,
                        "recipeName": rcp_name,
                        "batchSize": batch_size or "1000 KG",
                        "equipmentId": equipment_code,
                        "equipmentCode": equipment_code,
                        "equipmentName": f"Equipment {equipment_code}",
                        "parameterCode": param["code"],
                        "parameterName": param["name"],
                        "unitOfMeasure": param.get("unit", ""),
                        "uom": param.get("unit", ""),
                        "targetSetpoint": param.get("base"),
                        "lowLimit": param.get("low"),
                        "highLimit": param.get("high"),
                        "isActive": True,
                        "updatedAt": now_dt,
                    }
                    self.db.iiot_recipe_management.update_one(
                        {
                            "equipmentCode": equipment_code,
                            "recipeCode": recipe_code,
                            "parameterCode": param["code"],
                        },
                        {"$set": rm_doc, "$setOnInsert": {"createdAt": now_dt}},
                        upsert=True,
                    )

    def cascade_truncate_and_reset(
        self, target_equipment: Optional[List[str]] = None, reset_master: bool = False
    ) -> Dict[str, Any]:
        """Reset transactional data while keeping master records intact."""
        if self.db is None:
            return {"truncatedCollections": []}
        truncated = []
        existing = set(self.db.list_collection_names())
        cols_to_clear = [
            "iiot_batch_summary",
            "iiot_equipment_live_status",
            "iiot_batch_recipes",
            "iiot_batch_audit_trail",
            "iiot_ingested_events_registry",
            "iiot_ingestion_job_run",
        ]
        eqs = target_equipment or [e["equipmentCode"] for e in EQUIPMENT_DEFINITIONS]
        for eq in eqs:
            cols_to_clear.extend([
                f"iiot_ts_batch_{eq}",
                f"iiot_ts_alarm_{eq}",
                f"iiot_ts_audit_{eq}",
                f"iiot_ts_login_{eq}",
            ])
        for c in cols_to_clear:
            if c in existing:
                try:
                    self.db[c].delete_many({})
                    truncated.append(c)
                except Exception as exc:
                    logger.warning(f"Error truncating collection {c}: {exc}")
        if reset_master:
            self.sync_equipment_master()
            self.sync_critical_parameters()
        return {"truncatedCollections": truncated}
