{
  "numericFloatExample": {
    "_id": ObjectId("6a1dd2fc11b6e6f110b26680"),         // Default MongoDB unique auto ID
    "parameterLimitId": "CPL-00000000001",               // Auto-generated critical parameter limit ID
    "tenantId": "TNT-0001",                              // Tenant boundary for data isolation
    "plantId": "PLNT-0001",                              // Plant-level hierarchy key under tenant
    "equipmentId": "EQP-HSM-001",                        // Equipment master linkage ID
    "parameterId": "CP-00000000001",                     // Link to equipment critical parameter master
    "parameterCode": "TEMP_INLET",                       // Stable integration code for the parameter
    "parameterName": "Inlet Air Temperature",            // Human-readable parameter name
    "dataType": "FLOAT",                                 // NUMERIC/FLOAT threshold profile
    "unit": "C",                                         // Engineering unit (if applicable)
    "qualityStatus": "GOOD",                             // Data quality: GOOD, BAD, UNCERTAIN
    "limitModel": "RANGE",                               // RANGE for NUMERIC/FLOAT
    "limits": {
      "target": { "operator": "EQ", "value": 60.0 },
      "setPointMin": { "operator": "GTE", "value": 55.0 },
      "setPointMax": { "operator": "LTE", "value": 65.0 },
      "warningLower": { "operator": "LT", "value": 53.0 },
      "warningUpper": { "operator": "GT", "value": 67.0 },
      "alarmLower": { "operator": "LTE", "value": 50.0 },
      "alarmUpper": { "operator": "GTE", "value": 70.0 },
      "deadband": 0.2,                                     // Reduce alarm chattering
      "hysteresis": 0.3                                    // Stabilize state transitions
    },
    "alarmEnabled": true,
    "samplingIntervalSeconds": 1,
    "currentValueNumber": 37.02,                           // Sample FLOAT value
    "isActive": true,
    "createdAt": ISODate("2026-06-26T11:00:00Z"),
    "updatedAt": ISODate("2026-06-26T11:00:00Z"),
    "createdBy": "SYSTEM"
  },

  "booleanExample": {
    "_id": ObjectId("6a1dd2fc11b6e6f110b26681"),         // Default MongoDB unique auto ID
    "parameterLimitId": "CPL-00000000002",               // Auto-generated critical parameter limit ID
    "tenantId": "TNT-0001",                              // Tenant boundary for data isolation
    "plantId": "PLNT-0001",                              // Plant-level hierarchy key under tenant
    "equipmentId": "EQP-HSM-001",                        // Equipment master linkage ID
    "parameterId": "CP-00000000002",                     // Link to equipment critical parameter master
    "parameterCode": "MOTOR_RUNNING",                    // Boolean equipment state parameter
    "parameterName": "Motor Running State",              // Human-readable parameter name
    "dataType": "BOOLEAN",                               // BOOLEAN threshold/state profile
    "unit": null,
    "qualityStatus": "GOOD",
    "limitModel": "STATE",                               // STATE for BOOLEAN
    "booleanRule": {
      "trueLabel": "RUNNING",
      "falseLabel": "STOPPED",
      "warningCondition": { "operator": "EQ", "value": false },
      "alarmCondition": { "operator": "EQ", "value": false }
    },
    "samplingIntervalSeconds": 1,
    "currentValueBoolean": false,                          // Sample BOOLEAN value
    "isActive": true,
    "createdAt": ISODate("2026-06-26T11:00:00Z"),
    "updatedAt": ISODate("2026-06-26T11:00:00Z"),
    "createdBy": "SYSTEM"
  },

  "stringExample": {
    "_id": ObjectId("6a1dd2fc11b6e6f110b26682"),         // Default MongoDB unique auto ID
    "parameterLimitId": "CPL-00000000003",               // Auto-generated critical parameter limit ID
    "tenantId": "TNT-0001",                              // Tenant boundary for data isolation
    "plantId": "PLNT-0001",                              // Plant-level hierarchy key under tenant
    "equipmentId": "EQP-HSM-001",                        // Equipment master linkage ID
    "parameterId": "CP-00000000003",                     // Link to equipment critical parameter master
    "parameterCode": "BATCH_PHASE",                      // String process/state parameter
    "parameterName": "Batch Phase",                      // Human-readable parameter name
    "dataType": "STRING",                                // STRING profile
    "unit": null,
    "qualityStatus": "GOOD",
    "limitModel": "ENUM",                                // ENUM for controlled string values
    "stringRule": {
      "allowedValues": ["IDLE", "HEATING", "MIXING", "COOLING", "COMPLETE"],
      "warningValues": ["IDLE"],
      "alarmValues": ["ERROR", "ABORTED"],
      "comparison": "CASE_INSENSITIVE"                   // CASE_SENSITIVE or CASE_INSENSITIVE
    },
    "samplingIntervalSeconds": 1,
    "currentValueString": "MIXING",                      // Sample STRING value
    "isActive": true,
    "createdAt": ISODate("2026-06-26T11:00:00Z"),
    "updatedAt": ISODate("2026-06-26T11:00:00Z"),
    "createdBy": "SYSTEM"
  }
}