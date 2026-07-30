// ============================================
// Adavis AI Platform - MongoDB Initialization Script
// Destructive reset: drops and recreates the application database on each run
// ============================================

// Use docker compose init database by default (adavis_platform).
var databaseName = 'adavis_platform';
if (typeof process !== 'undefined' && process.env && process.env.MONGO_INITDB_DATABASE) {
    databaseName = process.env.MONGO_INITDB_DATABASE;
}
db = db.getSiblingDB(databaseName);

var DEFAULT_ADAVIS_PASSWORD_HASH = '$2a$10$pzWt1lCtyuKoDTX3cv6ICOvlchKgpk/OAvzZXFbiT6HrodBFtyFxe';
var DEFAULT_PERMISSION_ACTIONS = ['READ', 'WRITE', 'REVIEW', 'APPROVE', 'DEACTIVATE'];

function ensureDatabaseReady() {
    try {
        db.runCommand({ ping: 1 });
    } catch (e) {
        print('[ERROR] MongoDB is not available. Cannot initialize database.');
        throw e;
    }
}

function now() {
    return new Date();
}

function logInfo(msg) {
    print('[INFO] ' + msg);
}

function resetApplicationDatabase() {
    try {
        db.dropDatabase();
        logInfo('Dropped application database: ' + databaseName);
    } catch (e) {
        print('[ERROR] Failed to drop application database: ' + databaseName);
        throw e;
    }
}

function ensureUser() {
    try {
        var existing = db.getUser('admin');
        if (!existing) {
            db.createUser({
                user: 'admin',
                pwd: 'Admin123!',
                roles: [{ role: 'root', db: 'admin' }]
            });
            logInfo('Admin user created');
        } else {
            logInfo('Admin user already exists');
        }
    } catch (e) {
        if (e.codeName === 'DuplicateKey' || e.codeName === 'Unauthorized') {
            logInfo('Admin user already exists or cannot be managed from this context');
        } else {
            throw e;
        }
    }
}

function buildCollectionOptions(defaultOptions, overrideOptions) {
    var merged = Object.assign({}, defaultOptions || {});
    if (overrideOptions) {
        for (var key in overrideOptions) {
            if (overrideOptions.hasOwnProperty(key)) {
                merged[key] = overrideOptions[key];
            }
        }
    }
    return merged;
}

function applyCollectionValidation(name, options) {
    if (!options || !options.validator) {
        return;
    }
    try {
        db.runCommand({
            collMod: name,
            validator: options.validator,
            validationLevel: options.validationLevel || 'moderate',
            validationAction: options.validationAction || 'error'
        });
        logInfo('Applied validator to collection: ' + name);
    } catch (e) {
        if (e.codeName === 'NamespaceNotFound') {
            return;
        }
        throw e;
    }
}

var collectionOptions = {
    mdm_tenants: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['tenantId', 'companyName', 'companyCode', 'isActive'],
                properties: {
                    tenantId: { bsonType: 'string' },
                    companyName: { bsonType: 'string' },
                    companyCode: { bsonType: 'string' },
                    isActive: { bsonType: 'bool' }
                }
            }
        },
        validationLevel: 'moderate',
        validationAction: 'error'
    },
    mdm_plants: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['plantId', 'tenantId', 'plantName', 'plantCode', 'isActive'],
                properties: {
                    plantId: { bsonType: 'string' },
                    tenantId: { bsonType: 'string' },
                    plantName: { bsonType: 'string' },
                    plantCode: { bsonType: 'string' },
                    isActive: { bsonType: 'bool' }
                }
            }
        }
    },
    mdm_blocks: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['tenantId', 'plantId', 'blockCode', 'blockName', 'isActive'],
                properties: {
                    tenantId: { bsonType: 'string' },
                    plantId: { bsonType: 'string' },
                    blockCode: { bsonType: 'string' },
                    blockName: { bsonType: 'string' },
                    isActive: { bsonType: 'bool' }
                }
            }
        }
    },
    mdm_areas: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['tenantId', 'plantId', 'blockId', 'areaCode', 'areaName', 'isActive'],
                properties: {
                    tenantId: { bsonType: 'string' },
                    plantId: { bsonType: 'string' },
                    blockId: {},
                    areaCode: { bsonType: 'string' },
                    areaName: { bsonType: 'string' },
                    isActive: { bsonType: 'bool' }
                }
            }
        }
    },
    mdm_rooms: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['roomId', 'tenantId', 'plantId', 'areaId', 'roomCode', 'roomName', 'isActive'],
                properties: {
                    roomId: { bsonType: 'string' },
                    tenantId: { bsonType: 'string' },
                    plantId: { bsonType: 'string' },
                    areaId: {},
                    roomCode: { bsonType: 'string' },
                    roomName: { bsonType: 'string' },
                    isActive: { bsonType: 'bool' }
                }
            }
        }
    },
    mdm_departments: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['departmentId', 'tenantId', 'plantId', 'departmentCode', 'departmentName', 'path', 'isActive'],
                properties: {
                    departmentId: { bsonType: 'string' },
                    tenantId: { bsonType: 'string' },
                    plantId: { bsonType: 'string' },
                    departmentCode: { bsonType: 'string' },
                    departmentName: { bsonType: 'string' },
                    path: { bsonType: 'string' },
                    isActive: { bsonType: 'bool' }
                }
            }
        }
    },
    mdm_user_profiles: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['userId', 'userTrackId', 'tenantId', 'firstName', 'lastName', 'isActive', 'isBlocked', 'isExternal'],
                properties: {
                    userId: { bsonType: 'string' },
                    userTrackId: { bsonType: 'string' },
                    tenantId: { bsonType: 'string' },
                    firstName: { bsonType: 'string' },
                    lastName: { bsonType: 'string' },
                    email: { bsonType: 'string' },
                    phoneNumber: { bsonType: 'string' },
                    isActive: { bsonType: 'bool' },
                    isBlocked: { bsonType: 'bool' },
                    isExternal: { bsonType: 'bool' }
                }
            }
        }
    },
    auth_users: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['userId', 'status', 'isLocked', 'failedAttempts'],
                properties: {
                    userId: { bsonType: 'string' },
                    email: { bsonType: 'string' },
                    status: { bsonType: 'string' },
                    isLocked: { bsonType: 'bool' },
                    failedAttempts: { bsonType: ['int', 'long', 'double'] }
                }
            }
        }
    },
    mdm_user_auth_credentials: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['userId', 'passwordHash'],
                properties: {
                    userId: { bsonType: 'string' },
                    email: { bsonType: 'string' },
                    passwordHash: { bsonType: 'string' },
                    mustChangePassword: { bsonType: 'bool' },
                    passwordUpdatedAt: { bsonType: 'date' }
                }
            }
        }
    },
    mdm_password_policies: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['tenantId'],
                properties: {
                    tenantId: { bsonType: 'string' }
                }
            }
        }
    },
    mdm_user_groups: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['groupId', 'tenantId', 'groupCode', 'groupName', 'isActive'],
                properties: {
                    groupId: { bsonType: 'string' },
                    tenantId: { bsonType: 'string' },
                    groupCode: { bsonType: 'string' },
                    groupName: { bsonType: 'string' },
                    isActive: { bsonType: 'bool' }
                }
            }
        }
    },
    mdm_user_context_assignments: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['assignmentId', 'tenantId', 'userId', 'groupId', 'isActive'],
                properties: {
                    assignmentId: { bsonType: 'string' },
                    tenantId: { bsonType: 'string' },
                    userId: { bsonType: 'string' },
                    groupId: { bsonType: 'string' },
                    isActive: { bsonType: 'bool' }
                }
            }
        }
    },
    mdm_user_assignments_to_user_groups: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['userId', 'groupId', 'isActive', 'assignedAt', 'assignedBy'],
                properties: {
                    userId: { bsonType: 'string' },
                    groupId: { bsonType: 'string' },
                    isActive: { bsonType: 'bool' },
                    assignedAt: { bsonType: 'date' },
                    assignedBy: { bsonType: 'string' }
                }
            }
        }
    },
    mdm_role_assignments_to_user_groups: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['groupId', 'roleId', 'isActive', 'assignedAt', 'assignedBy'],
                properties: {
                    groupId: { bsonType: 'string' },
                    roleId: { bsonType: 'string' },
                    isActive: { bsonType: 'bool' },
                    assignedAt: { bsonType: 'date' },
                    assignedBy: { bsonType: 'string' }
                }
            }
        }
    },
    mdm_user_sessions: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['userId', 'refreshToken', 'expiresAt', 'isActive'],
                properties: {
                    sessionId: { bsonType: 'string' },
                    tenantId: { bsonType: 'string' },
                    userId: { bsonType: 'string' },
                    refreshToken: { bsonType: 'string' },
                    deviceInfo: { bsonType: 'string' },
                    ipAddress: { bsonType: 'string' },
                    expiresAt: { bsonType: 'date' },
                    lastActivityAt: { bsonType: 'date' },
                    isActive: { bsonType: 'bool' }
                }
            }
        }
    },
    login_history: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['tenantId', 'userId', 'status', 'timestamp'],
                properties: {
                    tenantId: { bsonType: 'string' },
                    userId: { bsonType: 'string' },
                    status: { bsonType: 'string' },
                    timestamp: { bsonType: 'date' }
                }
            }
        }
    },
    mdm_roles: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['roleId', 'tenantId', 'roleCode', 'roleName', 'isActive'],
                properties: {
                    roleId: { bsonType: 'string' },
                    tenantId: { bsonType: 'string' },
                    roleCode: { bsonType: 'string' },
                    roleName: { bsonType: 'string' },
                    isActive: { bsonType: 'bool' }
                }
            }
        }
    },
    mdm_modules: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['moduleId', 'moduleCode', 'moduleName', 'displayOrder', 'isActive'],
                properties: {
                    moduleId: { bsonType: 'string' },
                    moduleCode: { bsonType: 'string' },
                    moduleName: { bsonType: 'string' },
                    displayOrder: { bsonType: ['int', 'long', 'double'] },
                    isActive: { bsonType: 'bool' }
                }
            }
        }
    },
    mdm_screens: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['screenId', 'moduleId', 'moduleCode', 'screenCode', 'screenName', 'displayOrder', 'isActive'],
                properties: {
                    screenId: { bsonType: 'string' },
                    moduleId: { bsonType: 'string' },
                    moduleCode: { bsonType: 'string' },
                    screenCode: { bsonType: 'string' },
                    screenName: { bsonType: 'string' },
                    displayOrder: { bsonType: ['int', 'long', 'double'] },
                    isActive: { bsonType: 'bool' }
                }
            }
        }
    },
    mdm_features: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['featureId', 'moduleId', 'moduleCode', 'screenId', 'screenCode', 'featureCode', 'featureName', 'displayOrder', 'isActive'],
                properties: {
                    featureId: { bsonType: 'string' },
                    moduleId: { bsonType: 'string' },
                    moduleCode: { bsonType: 'string' },
                    screenId: { bsonType: 'string' },
                    screenCode: { bsonType: 'string' },
                    featureCode: { bsonType: 'string' },
                    featureName: { bsonType: 'string' },
                    displayOrder: { bsonType: ['int', 'long', 'double'] },
                    isActive: { bsonType: 'bool' }
                }
            }
        }
    },
    mdm_role_permissions: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['roleId', 'moduleId', 'version', 'isActive', 'screenPermissions'],
                properties: {
                    tenantId: { bsonType: 'string' },
                    roleId: { bsonType: 'string' },
                    moduleId: { bsonType: 'string' },
                    version: { bsonType: ['int', 'long', 'double'] },
                    isActive: { bsonType: 'bool' },
                    effectiveFrom: { bsonType: 'date' },
                    screenPermissions: {
                        bsonType: 'array'
                    }
                }
            }
        }
    },
    iiot_assets: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['assetId', 'tenantId', 'plantId', 'roomId', 'assetCode', 'assetName', 'isActive'],
                properties: {
                    assetId: { bsonType: 'string' },
                    tenantId: { bsonType: 'string' },
                    plantId: { bsonType: 'string' },
                    roomId: {},
                    assetCode: { bsonType: 'string' },
                    assetName: { bsonType: 'string' },
                    isActive: { bsonType: 'bool' }
                }
            }
        }
    },
    iiot_asset_tags: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['tagId', 'tenantId', 'assetId', 'assetCode', 'tagCode', 'tagName', 'isActive'],
                properties: {
                    tagId: { bsonType: 'string' },
                    tenantId: { bsonType: 'string' },
                    assetId: {},
                    assetCode: { bsonType: 'string' },
                    tagCode: { bsonType: 'string' },
                    tagName: { bsonType: 'string' },
                    isActive: { bsonType: 'bool' }
                }
            }
        }
    },
    iiot_tag_thresholds: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['thresholdId', 'tenantId', 'plantId', 'assetId', 'tagId', 'tagCode', 'condition', 'isActive'],
                properties: {
                    thresholdId: { bsonType: 'string' },
                    tenantId: { bsonType: 'string' },
                    plantId: { bsonType: 'string' },
                    assetId: {},
                    tagId: {},
                    tagCode: { bsonType: 'string' },
                    condition: { bsonType: 'string' },
                    isActive: { bsonType: 'bool' }
                }
            }
        }
    },
    iiot_asset_states: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['assetId', 'tagCode', 'timestamp'],
                properties: {
                    assetId: { bsonType: 'string' },
                    tagCode: { bsonType: 'string' },
                    timestamp: { bsonType: 'date' }
                }
            }
        }
    },
    mdm_licenses: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['licenseKey', 'modules', 'maxUsers', 'currentUsers', 'status', 'isDeleted'],
                properties: {
                    tenantId: { bsonType: 'string' },
                    licenseId: { bsonType: 'string' },
                    licenseKey: { bsonType: 'string' },
                    modules: { bsonType: 'array' },
                    maxUsers: { bsonType: ['int', 'long', 'double'] },
                    currentUsers: { bsonType: ['int', 'long', 'double'] },
                    status: { bsonType: 'string' },
                    isDeleted: { bsonType: 'bool' }
                }
            }
        }
    },
    mdm_licence_history: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['licenseId', 'action', 'performedAt'],
                properties: {
                    licenseId: { bsonType: 'string' },
                    action: { bsonType: 'string' },
                    beforeStatus: { bsonType: 'string' },
                    afterStatus: { bsonType: 'string' },
                    beforeMaxUsers: { bsonType: ['int', 'long', 'double'] },
                    afterMaxUsers: { bsonType: ['int', 'long', 'double'] },
                    beforeModules: { bsonType: 'array' },
                    afterModules: { bsonType: 'array' },
                    beforeExpiry: { bsonType: 'date' },
                    afterExpiry: { bsonType: 'date' },
                    reason: { bsonType: 'string' },
                    performedBy: { bsonType: 'string' },
                    performedAt: { bsonType: 'date' }
                }
            }
        }
    },
    dms_documents: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['documentId', 'tenantId'],
                properties: {
                    documentId: { bsonType: 'string' },
                    tenantId: { bsonType: 'string' }
                }
            }
        }
    },
    mdm_audit_trails: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['action', 'timestamp'],
                properties: {
                    tenantId: { bsonType: 'string' },
                    userId: { bsonType: 'string' },
                    action: { bsonType: 'string' },
                    timestamp: { bsonType: 'date' }
                }
            }
        }
    },
    id_sequences: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['sequenceName', 'currentValue'],
                properties: {
                    sequenceName: { bsonType: 'string' },
                    currentValue: { bsonType: ['int', 'long', 'double'] }
                }
            }
        }
    },
    id_sequence_mappings: {
        validator: {
            $jsonSchema: {
                bsonType: 'object',
                required: ['sequenceName', 'collectionName', 'fieldName', 'prefix', 'padLength', 'isActive'],
                properties: {
                    sequenceName: { bsonType: 'string' },
                    collectionName: { bsonType: 'string' },
                    fieldName: { bsonType: 'string' },
                    prefix: { bsonType: 'string' },
                    padLength: { bsonType: ['int', 'long', 'double'] },
                    isActive: { bsonType: 'bool' }
                }
            }
        }
    }
};

function ensureCollection(name, options) {
    var effectiveOptions = buildCollectionOptions(collectionOptions[name], options);
    if (db.getCollectionNames().indexOf(name) === -1) {
        db.createCollection(name, effectiveOptions || {});
        logInfo('Created collection: ' + name);
        return;
    }
    applyCollectionValidation(name, effectiveOptions);
}

function ensureIndex(collectionName, spec, options) {
    try {
        db.getCollection(collectionName).createIndex(spec, options || {});
    } catch (e) {
        if (e.codeName === 'IndexOptionsConflict' || e.codeName === 'IndexKeySpecsConflict') {
            logInfo('Index already exists with different options on ' + collectionName + ', skipping');
        } else {
            throw e;
        }
    }
}

function dropIndexIfExists(collectionName, indexName) {
    try {
        var indexes = db.getCollection(collectionName).getIndexes();
        for (var i = 0; i < indexes.length; i++) {
            if (indexes[i].name === indexName) {
                db.getCollection(collectionName).dropIndex(indexName);
                logInfo('Dropped index: ' + collectionName + '.' + indexName);
                return;
            }
        }
    } catch (e) {
        if (e.codeName === 'NamespaceNotFound' || e.codeName === 'IndexNotFound') {
            return;
        }
        throw e;
    }
}

function withTimestamps(doc) {
    var out = Object.assign({}, doc);
    if (!out.createdAt) {
        out.createdAt = now();
    }
    out.updatedAt = now();
    return out;
}

function upsertOne(collectionName, filter, doc) {
    var payload = withTimestamps(doc);
    var setPayload = Object.assign({}, payload);
    delete setPayload.createdAt;
    db.getCollection(collectionName).updateOne(
        filter,
        {
            $set: setPayload,
            $setOnInsert: { createdAt: payload.createdAt }
        },
        { upsert: true }
    );
}

function upsertMany(collectionName, items, keyField) {
    for (var i = 0; i < items.length; i++) {
        var item = items[i];
        var key = item[keyField];
        if (key === undefined || key === null) {
            continue;
        }
        var filter = {};
        filter[keyField] = key;
        upsertOne(collectionName, filter, item);
    }
}

function upsertManyWithAutoId(collectionName, items, keyField, sequenceConfig) {
    for (var i = 0; i < items.length; i++) {
        var item = Object.assign({}, items[i]);
        if ((item[keyField] === undefined || item[keyField] === null || item[keyField] === '') && sequenceConfig) {
            item[keyField] = nextBusinessId(
                sequenceConfig.sequenceName,
                sequenceConfig.prefix,
                sequenceConfig.padLength || 4
            );
        }

        var key = item[keyField];
        if (key === undefined || key === null || key === '') {
            continue;
        }

        var filter = {};
        filter[keyField] = key;
        upsertOne(collectionName, filter, item);
    }
}

function parseNumericSuffix(value, prefix) {
    if (!value || typeof value !== 'string') {
        return -1;
    }
    var pattern = new RegExp('^' + prefix + '-(\\d+)$');
    var match = value.match(pattern);
    if (!match || match.length < 2) {
        return -1;
    }
    return parseInt(match[1], 10);
}

function toPaddedId(prefix, numberValue, padLength) {
    var num = Number(numberValue || 0);
    var numStr = String(num);
    while (numStr.length < padLength) {
        numStr = '0' + numStr;
    }
    return prefix + '-' + numStr;
}

function nextSequenceValue(sequenceName) {
    db.getCollection('id_sequences').updateOne(
        { sequenceName: sequenceName },
        {
            $inc: { currentValue: 1 },
            $set: { updatedAt: now() },
            $setOnInsert: { createdAt: now() }
        },
        { upsert: true }
    );
    var current = db.getCollection('id_sequences').findOne({ sequenceName: sequenceName });
    return current && current.currentValue ? current.currentValue : 1;
}

function nextBusinessId(sequenceName, prefix, padLength) {
    return toPaddedId(prefix, nextSequenceValue(sequenceName), padLength || 4);
}

function initializeSequence(def) {
    var sequenceName = def.sequenceName;
    var collectionName = def.collectionName;
    var fieldName = def.fieldName;
    var prefix = def.prefix;
    var padLength = def.padLength || 4;

    var currentMax = 0;
    var projection = {};
    projection[fieldName] = 1;
    db.getCollection(collectionName)
        .find({}, projection)
        .forEach(function (doc) {
            var candidate = parseNumericSuffix(doc[fieldName], prefix);
            if (candidate > currentMax) {
                currentMax = candidate;
            }
        });

    if (def.seedValues && def.seedValues.length > 0) {
        for (var i = 0; i < def.seedValues.length; i++) {
            var seededCandidate = parseNumericSuffix(def.seedValues[i], prefix);
            if (seededCandidate > currentMax) {
                currentMax = seededCandidate;
            }
        }
    }

    upsertOne('id_sequence_mappings', { sequenceName: sequenceName }, {
        sequenceName: sequenceName,
        collectionName: collectionName,
        fieldName: fieldName,
        prefix: prefix,
        padLength: padLength,
        sampleNextValue: toPaddedId(prefix, currentMax + 1, padLength),
        isActive: true
    });

    db.getCollection('id_sequences').updateOne(
        { sequenceName: sequenceName },
        {
            $set: {
                sequenceName: sequenceName,
                collectionName: collectionName,
                fieldName: fieldName,
                prefix: prefix,
                padLength: padLength,
                currentValue: currentMax,
                updatedAt: now()
            },
            $setOnInsert: {
                createdAt: now()
            }
        },
        { upsert: true }
    );
}

function buildRolePermissionDocument(tenantId, roleId, moduleId, screens, features) {
    var screenPermissions = [];
    for (var i = 0; i < screens.length; i++) {
        var screen = screens[i];
        if (screen.moduleId !== moduleId) {
            continue;
        }
        var featurePermissions = [];
        for (var j = 0; j < features.length; j++) {
            var feature = features[j];
            if (feature.screenId === screen.screenId) {
                featurePermissions.push({
                    featureId: feature.featureId,
                    actions: DEFAULT_PERMISSION_ACTIONS
                });
            }
        }
        screenPermissions.push({
            screenId: screen.screenId,
            actions: DEFAULT_PERMISSION_ACTIONS,
            featurePermissions: featurePermissions
        });
    }

    return {
        tenantId: tenantId,
        roleId: roleId,
        moduleId: moduleId,
        version: 1,
        isActive: true,
        effectiveFrom: ISODate('2026-03-01T00:00:00Z'),
        effectiveTo: null,
        screenPermissions: screenPermissions
    };
}

print('========================================');
print('Initializing Adavis Platform Database');
print('========================================');
print('Target database: ' + databaseName);

ensureDatabaseReady();
resetApplicationDatabase();
ensureUser();

var collections = [
    'mdm_tenants',
    'mdm_plants',
    'mdm_blocks',
    'mdm_areas',
    'mdm_rooms',
    'mdm_departments',
    'mdm_user_profiles',
    'auth_users',
    'mdm_user_auth_credentials',
    'mdm_password_policies',
    'mdm_user_groups',
    'mdm_user_context_assignments',
    'mdm_user_sessions',
    'login_history',
    'mdm_roles',
    'mdm_modules',
    'mdm_screens',
    'mdm_features',
    'mdm_role_permissions',
    'mdm_user_assignments_to_user_groups',
    'mdm_role_assignments_to_user_groups',
    'iiot_assets',
    'iiot_asset_tags',
    'iiot_tag_thresholds',
    'iiot_asset_states',
    'mdm_licenses',
    'mdm_licence_history',
    'dms_documents',
    'mdm_audit_trails',
    'id_sequences',
    'id_sequence_mappings'
];

for (var c = 0; c < collections.length; c++) {
    ensureCollection(collections[c]);
}

// sample_collections defines iiot_telemetry as time-series style
ensureCollection('iiot_telemetry', {
    timeseries: {
        timeField: 'timestamp',
        metaField: 'metaField',
        granularity: 'seconds'
    }
});

logInfo('Creating indexes...');

ensureIndex('mdm_tenants', { tenantId: 1 }, { unique: true });
dropIndexIfExists('mdm_tenants', 'domain_1');
ensureIndex('mdm_tenants', { domain: 1 }, {
    unique: true,
    partialFilterExpression: {
        domain: { $exists: true, $type: 'string', $gt: '' }
    }
});
dropIndexIfExists('mdm_tenants', 'companyCode_1');
ensureIndex('mdm_tenants', { companyCode: 1 }, {
    unique: true,
    partialFilterExpression: {
        companyCode: { $exists: true, $type: 'string', $gt: '' }
    }
});

ensureIndex('mdm_plants', { plantId: 1 }, { unique: true });
ensureIndex('mdm_plants', { tenantId: 1, plantCode: 1 }, { unique: true });

ensureIndex('mdm_blocks', { tenantId: 1, blockCode: 1 }, { unique: true });
ensureIndex('mdm_areas', { tenantId: 1, areaCode: 1 }, { unique: true });
ensureIndex('mdm_areas', { blockId: 1 });
ensureIndex('mdm_rooms', { tenantId: 1, roomCode: 1 }, { unique: true });
ensureIndex('mdm_rooms', { areaId: 1 });

ensureIndex('auth_users', { userId: 1 }, { unique: true });
dropIndexIfExists('auth_users', 'username_1');
ensureIndex('auth_users', { username: 1 }, {
    unique: true,
    partialFilterExpression: {
        username: { $exists: true, $type: 'string', $gt: '' }
    }
});
dropIndexIfExists('auth_users', 'email_1');
ensureIndex('auth_users', { email: 1 }, {
    unique: true,
    partialFilterExpression: {
        email: { $exists: true, $type: 'string', $gt: '' }
    }
});

ensureIndex('mdm_user_auth_credentials', { userId: 1 }, { unique: true });
ensureIndex('mdm_user_auth_credentials', { email: 1 });

ensureIndex('mdm_password_policies', { tenantId: 1 }, { unique: true });

ensureIndex('mdm_user_groups', { groupId: 1 }, { unique: true });
ensureIndex('mdm_user_groups', { tenantId: 1, groupCode: 1 }, { unique: true });
ensureIndex('mdm_user_groups', { tenantId: 1 });

ensureIndex('mdm_user_context_assignments', { assignmentId: 1 }, { unique: true });
dropIndexIfExists('mdm_user_context_assignments', 'tenantId_1_userId_1_plantId_1_departmentId_1_groupId_1');
ensureIndex('mdm_user_context_assignments', { tenantId: 1, userId: 1, plantId: 1, departmentId: 1, groupId: 1 });
ensureIndex('mdm_user_context_assignments', { plantId: 1, departmentId: 1 });

ensureIndex('mdm_user_assignments_to_user_groups', { userId: 1, groupId: 1 }, { unique: true });
ensureIndex('mdm_user_assignments_to_user_groups', { userId: 1 });
ensureIndex('mdm_user_assignments_to_user_groups', { groupId: 1 });

ensureIndex('mdm_role_assignments_to_user_groups', { groupId: 1, roleId: 1 }, { unique: true });
ensureIndex('mdm_role_assignments_to_user_groups', { groupId: 1 });
ensureIndex('mdm_role_assignments_to_user_groups', { roleId: 1 });

ensureIndex('mdm_user_lifecycle_requests', { requestId: 1 }, { unique: true });
ensureIndex('mdm_user_lifecycle_requests', { tenantId: 1, targetUserId: 1 });
ensureIndex('mdm_user_lifecycle_requests', { requestStatus: 1 });

dropIndexIfExists('mdm_user_sessions', 'sessionId_1');
ensureIndex('mdm_user_sessions', { refreshToken: 1 }, { unique: true });
ensureIndex('mdm_user_sessions', { userId: 1, isActive: 1 });
ensureIndex('mdm_user_sessions', { isActive: 1, expiresAt: 1 });
ensureIndex('mdm_user_sessions', { expiresAt: 1 }, { expireAfterSeconds: 0 });

ensureIndex('login_history', { tenantId: 1, userId: 1, timestamp: -1 });
ensureIndex('login_history', { tenantId: 1, status: 1, timestamp: -1 });

ensureIndex('mdm_roles', { roleId: 1 }, { unique: true });
dropIndexIfExists('mdm_roles', 'tenantId_1_roleCode_1');
ensureIndex('mdm_roles', { tenantId: 1, roleCode: 1 }, {
    unique: true,
    partialFilterExpression: {
        roleCode: { $exists: true, $type: 'string', $gt: '' }
    }
});
ensureIndex('mdm_roles', { tenantId: 1 });

ensureIndex('mdm_modules', { moduleId: 1 }, { unique: true });
ensureIndex('mdm_modules', { moduleCode: 1 }, { unique: true });

ensureIndex('mdm_screens', { screenId: 1 }, { unique: true });
ensureIndex('mdm_screens', { screenCode: 1 }, { unique: true });
ensureIndex('mdm_screens', { moduleId: 1 });
ensureIndex('mdm_screens', { moduleCode: 1 });

ensureIndex('mdm_features', { featureId: 1 }, { unique: true });
ensureIndex('mdm_features', { featureCode: 1 }, { unique: true });
ensureIndex('mdm_features', { screenId: 1 });
ensureIndex('mdm_features', { moduleCode: 1, screenCode: 1 });

ensureIndex('mdm_role_permissions', { roleId: 1 });
ensureIndex('mdm_role_permissions', { moduleId: 1 });
ensureIndex('mdm_role_permissions', { roleId: 1, moduleId: 1, version: 1 }, { unique: true });

ensureIndex('iiot_assets', { tenantId: 1, assetCode: 1 }, { unique: true });
ensureIndex('iiot_asset_tags', { tenantId: 1, assetCode: 1, tagCode: 1 }, { unique: true });
ensureIndex('iiot_tag_thresholds', { tenantId: 1, plantId: 1, thresholdId: 1 }, { unique: true });
ensureIndex('iiot_asset_states', { assetId: 1, tagCode: 1 }, { unique: true });
ensureIndex('iiot_asset_states', { timestamp: -1 });
ensureIndex('iiot_telemetry', { 'metaField.tenantId': 1, 'metaField.tagId': 1, timestamp: -1 });

dropIndexIfExists('mdm_licenses', 'tenantId_1');
dropIndexIfExists('mdm_licenses', 'licenseKey_1');
ensureIndex('mdm_licenses', { licenseKey: 1 }, {
    unique: true,
    partialFilterExpression: {
        licenseKey: { $exists: true, $type: 'string', $gt: '' }
    }
});
ensureIndex('mdm_licenses', { 'metadata.tenantId': 1 });
ensureIndex('mdm_licence_history', { tenantId: 1, timestamp: -1 });

ensureIndex('dms_documents', { documentId: 1 }, { unique: true });
ensureIndex('dms_documents', { tenantId: 1, plantId: 1 });
ensureIndex('dms_documents', { sha256Checksum: 1 });

ensureIndex('mdm_audit_trails', { tenantId: 1, plantId: 1, action: 1, timestamp: -1 });
ensureIndex('mdm_audit_trails', { tenantId: 1, userId: 1, timestamp: -1 });

ensureIndex('mdm_user_profiles', { userId: 1 }, { unique: true });
ensureIndex('mdm_user_profiles', { userTrackId: 1 }, { unique: true });
dropIndexIfExists('mdm_user_profiles', 'username_1');
ensureIndex('mdm_user_profiles', { username: 1 }, {
    unique: true,
    partialFilterExpression: {
        username: { $exists: true, $type: 'string', $gt: '' }
    }
});
dropIndexIfExists('mdm_user_profiles', 'email_1');
ensureIndex('mdm_user_profiles', { email: 1 }, {
    unique: true,
    partialFilterExpression: {
        email: { $exists: true, $type: 'string', $gt: '' }
    }
});
ensureIndex('mdm_user_profiles', { tenantId: 1 });
ensureIndex('mdm_user_profiles', { tenantId: 1, userId: 1 }, { unique: true });

ensureIndex('mdm_departments', { departmentId: 1 }, { unique: true });
ensureIndex('mdm_departments', { tenantId: 1, plantId: 1, departmentCode: 1 }, { unique: true });
ensureIndex('mdm_departments', { parentDepartmentId: 1 });
ensureIndex('mdm_departments', { path: 1 });
ensureIndex('mdm_departments', { isActive: 1 });

ensureIndex('id_sequences', { sequenceName: 1 }, { unique: true });
ensureIndex('id_sequence_mappings', { sequenceName: 1 }, { unique: true });
ensureIndex('id_sequence_mappings', { collectionName: 1, fieldName: 1 }, { unique: true });

logInfo('Seeding default data...');

var manufacturingBlockId = "BLK-0001";
var warehouseBlockId = "BLK-0002";
var utilityBlockId = "BLK-0003";
var qualityControlBlockId = "BLK-0004";

var dispensingAreaId = "AREA-0001";
var compressionAreaId = "AREA-0002";
var packingAreaId = "AREA-0001";
var rawMaterialAreaId = "AREA-0004";
var finishedGoodsAreaId = "AREA-0005";
var purifiedWaterAreaId = "AREA-0006";
var hvacAreaId = "AREA-0007";
var chemicalTestingAreaId = "AREA-0008";
var microbiologyAreaId = "AREA-0009";
var assetObjectId = "AREA-0010";
var tagObjectId = "AREA-0011";
db.mdm_tenants.updateOne(
    {
        tenantId: 'TNT-0001'
    },
    {
        $set: {
            companyName: 'Adavis Technologies Ltd.',
            domain: 'https://adavis.technologies.com',
            companyCode: 'NCP',
            contactEmail: 'compliance@adavis.com',
            isActive: true,
            updatedAt: ISODate()
        },
        $setOnInsert: {
            _description: 'Master registry for root tenant corporate entities on the platform.',
            createdAt: ISODate('2026-01-15T08:00:00Z')
        }
    },
    {
        upsert: true
    }
);

// Plant 1 - Formulation Plant
db.mdm_plants.updateOne(
    {
        plantId: 'PLNT-0001'
    },
    {
        $set: {
            tenantId: 'TNT-0001',
            plantName: 'Formulation Plant - Hyderabad',
            plantCode: 'HYD-01',
            type: 'Manufacturing',
            address: {
                street: 'Plot No. 25, Pharma City',
                city: 'Hyderabad',
                state: 'Telangana',
                zipCode: '500078',
                country: 'India'
            },
            timezone: 'Asia/Kolkata',
            isActive: true,
            updatedAt: ISODate()
        },
        $setOnInsert: {
            createdAt: ISODate('2026-01-15T08:00:00Z')
        }
    },
    {
        upsert: true
    }
);

// Plant 2 - API Plant
db.mdm_plants.updateOne(
    {
        plantId: 'PLNT-0002'
    },
    {
        $set: {
            tenantId: 'TNT-0001',
            plantName: 'API Plant - Visakhapatnam',
            plantCode: 'VZG-01',
            type: 'Manufacturing',
            address: {
                street: 'Survey No. 88, Pharma SEZ',
                city: 'Visakhapatnam',
                state: 'Andhra Pradesh',
                zipCode: '530046',
                country: 'India'
            },
            timezone: 'Asia/Kolkata',
            isActive: true,
            updatedAt: ISODate()
        },
        $setOnInsert: {
            createdAt: ISODate('2026-01-15T08:00:00Z')
        }
    },
    {
        upsert: true
    }
);

upsertManyWithAutoId('mdm_departments', [
    {
        departmentId: 'DEP-0001',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        departmentCode: 'PLT-OPS',
        departmentName: 'Plant Operations',
        level: 1,
        parentDepartmentId: null,
        path: 'DEP-0001',
        isActive: true
    },
    {
        departmentId: 'DEP-0002',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        departmentCode: 'PROD',
        departmentName: 'Production',
        level: 2,
        parentDepartmentId: 'DEP-0001',
        path: 'DEP-0001/DEP-0002',
        isActive: true
    },
    {
        departmentId: 'DEP-0003',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        departmentCode: 'WH',
        departmentName: 'Warehouse',
        level: 2,
        parentDepartmentId: 'DEP-0001',
        path: 'DEP-0001/DEP-0003',
        isActive: true
    },
    {
        departmentId: 'DEP-0004',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        departmentCode: 'SCM',
        departmentName: 'Supply Chain',
        level: 2,
        parentDepartmentId: 'DEP-0001',
        path: 'DEP-0001/DEP-0004',
        isActive: true
    },
    {
        departmentId: 'DEP-0005',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        departmentCode: 'QUAL',
        departmentName: 'Quality Systems',
        level: 1,
        parentDepartmentId: null,
        path: 'DEP-0005',
        isActive: true
    },
    {
        departmentId: 'DEP-0006',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        departmentCode: 'QA',
        departmentName: 'Quality Assurance',
        level: 2,
        parentDepartmentId: 'DEP-0005',
        path: 'DEP-0005/DEP-0006',
        isActive: true
    },
    {
        departmentId: 'DEP-0007',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        departmentCode: 'QC',
        departmentName: 'Quality Control',
        level: 2,
        parentDepartmentId: 'DEP-0005',
        path: 'DEP-0005/DEP-0007',
        isActive: true
    },
    {
        departmentId: 'DEP-0008',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        departmentCode: 'COMP',
        departmentName: 'Compliance',
        level: 2,
        parentDepartmentId: 'DEP-0005',
        path: 'DEP-0005/DEP-0008',
        isActive: true
    },
    {
        departmentId: 'DEP-0009',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        departmentCode: 'DOC',
        departmentName: 'Document Control',
        level: 2,
        parentDepartmentId: 'DEP-0005',
        path: 'DEP-0005/DEP-0009',
        isActive: true
    },
    {
        departmentId: 'DEP-0010',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        departmentCode: 'TECH',
        departmentName: 'Technical Services',
        level: 1,
        parentDepartmentId: null,
        path: 'DEP-0010',
        isActive: true
    },
    {
        departmentId: 'DEP-0011',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        departmentCode: 'MNT',
        departmentName: 'Maintenance',
        level: 2,
        parentDepartmentId: 'DEP-0010',
        path: 'DEP-0010/DEP-0011',
        isActive: true
    },
    {
        departmentId: 'DEP-0012',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        departmentCode: 'UTIL',
        departmentName: 'Utilities',
        level: 2,
        parentDepartmentId: 'DEP-0010',
        path: 'DEP-0010/DEP-0012',
        isActive: true
    },
    {
        departmentId: 'DEP-0013',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        departmentCode: 'AUTO',
        departmentName: 'Automation / Instrumentation',
        level: 2,
        parentDepartmentId: 'DEP-0010',
        path: 'DEP-0010/DEP-0013',
        isActive: true
    },
    {
        departmentId: 'DEP-0014',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        departmentCode: 'DIGI',
        departmentName: 'Digital & Governance',
        level: 1,
        parentDepartmentId: null,
        path: 'DEP-0014',
        isActive: true
    },
    {
        departmentId: 'DEP-0015',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        departmentCode: 'IT',
        departmentName: 'Information Technology',
        level: 2,
        parentDepartmentId: 'DEP-0014',
        path: 'DEP-0014/DEP-0015',
        isActive: true
    },
    {
        departmentId: 'DEP-0016',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        departmentCode: 'MDM',
        departmentName: 'Master Data Management',
        level: 2,
        parentDepartmentId: 'DEP-0014',
        path: 'DEP-0014/DEP-0016',
        isActive: true
    }
], 'departmentId', { sequenceName: 'departmentId', prefix: 'DEP', padLength: 4 });

upsertMany('mdm_blocks', [
    {
        blockId: manufacturingBlockId,
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        blockCode: 'BLK-MFG',
        blockName: 'Manufacturing Block',
        displayOrder: 1,
        isActive: true
    },
    {
        blockId: warehouseBlockId,
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        blockCode: 'BLK-WH',
        blockName: 'Warehouse Block',
        displayOrder: 2,
        isActive: true
    },
    {
        blockId: utilityBlockId,
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        blockCode: 'BLK-UTIL',
        blockName: 'Utility Block',
        displayOrder: 3,
        isActive: true
    },
    {
        blockId: qualityControlBlockId,
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        blockCode: 'BLK-QC',
        blockName: 'Quality Control Block',
        displayOrder: 4,
        isActive: true
    }
], 'blockCode');

upsertMany('mdm_areas', [
    {
        areaId: dispensingAreaId,
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        blockId: manufacturingBlockId,
        areaCode: 'AREA-DISP',
        areaName: 'Dispensing Area',
        displayOrder: 1,
        isActive: true
    },
    {
        areaId: compressionAreaId,
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        blockId: manufacturingBlockId,
        areaCode: 'AREA-COMP',
        areaName: 'Compression Area',
        displayOrder: 2,
        isActive: true
    },
    {
        areaId: packingAreaId,
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        blockId: manufacturingBlockId,
        areaCode: 'AREA-PACK',
        areaName: 'Packing Area',
        displayOrder: 3,
        isActive: true
    },
    {
        areaId: rawMaterialAreaId,
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        blockId: warehouseBlockId,
        areaCode: 'AREA-RM',
        areaName: 'Raw Material Area',
        displayOrder: 1,
        isActive: true
    },
    {
        areaId: finishedGoodsAreaId,
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        blockId: warehouseBlockId,
        areaCode: 'AREA-FG',
        areaName: 'Finished Goods Area',
        displayOrder: 2,
        isActive: true
    },
    {
        areaId: purifiedWaterAreaId,
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        blockId: utilityBlockId,
        areaCode: 'AREA-PW',
        areaName: 'Purified Water Area',
        displayOrder: 1,
        isActive: true
    },
    {
        areaId: hvacAreaId,
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        blockId: utilityBlockId,
        areaCode: 'AREA-HVAC',
        areaName: 'HVAC Area',
        displayOrder: 2,
        isActive: true
    },
    {
        areaId: chemicalTestingAreaId,
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        blockId: qualityControlBlockId,
        areaCode: 'AREA-CHEM',
        areaName: 'Chemical Testing Area',
        displayOrder: 1,
        isActive: true
    },
    {
        areaId: microbiologyAreaId,
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        blockId: qualityControlBlockId,
        areaCode: 'AREA-MICRO',
        areaName: 'Microbiology Area',
        displayOrder: 2,
        isActive: true
    }
], 'areaCode');

upsertManyWithAutoId('mdm_rooms', [
    {
        roomId: 'ROOM-0001',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        areaId: compressionAreaId,
        roomCode: 'RM-COMP-101',
        roomName: 'Compression Room 101',
        classification: 'ISO_7',
        isActive: true
    },
    {
        roomId: 'ROOM-0002',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        areaId: purifiedWaterAreaId,
        roomCode: 'RM-PW-201',
        roomName: 'Purified Water Plant Room',
        classification: 'CNC',
        isActive: true
    },
    {
        roomId: 'ROOM-0003',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        areaId: dispensingAreaId,
        roomCode: 'RM-DISP-01',
        roomName: 'Dispensing Room 01',
        classification: 'ISO_8',
        isActive: true
    },
    {
        roomId: 'ROOM-0004',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        areaId: dispensingAreaId,
        roomCode: 'RM-MATL-AIRLOCK',
        roomName: 'Material Airlock',
        classification: 'CNC',
        isActive: true
    },
    {
        roomId: 'ROOM-0005',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        areaId: compressionAreaId,
        roomCode: 'RM-COMP-102',
        roomName: 'Compression Room 102',
        classification: 'ISO_7',
        isActive: true
    },
    {
        roomId: 'ROOM-0006',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        areaId: packingAreaId,
        roomCode: 'RM-PACK-PRI',
        roomName: 'Primary Packing Room',
        classification: 'ISO_8',
        isActive: true
    },
    {
        roomId: 'ROOM-0007',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        areaId: packingAreaId,
        roomCode: 'RM-PACK-SEC',
        roomName: 'Secondary Packing Room',
        classification: 'CNC',
        isActive: true
    },
    {
        roomId: 'ROOM-0008',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        areaId: rawMaterialAreaId,
        roomCode: 'RM-RM-STORE',
        roomName: 'Raw Material Store',
        classification: 'WAREHOUSE',
        isActive: true
    },
    {
        roomId: 'ROOM-0009',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        areaId: rawMaterialAreaId,
        roomCode: 'RM-SAMPLING',
        roomName: 'Sampling Room',
        classification: 'ISO_8',
        isActive: true
    },
    {
        roomId: 'ROOM-0010',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        areaId: finishedGoodsAreaId,
        roomCode: 'RM-FG-STAGE',
        roomName: 'FG Staging Room',
        classification: 'WAREHOUSE',
        isActive: true
    },
    {
        roomId: 'ROOM-0011',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        areaId: finishedGoodsAreaId,
        roomCode: 'RM-DISPATCH',
        roomName: 'Dispatch Holding Room',
        classification: 'WAREHOUSE',
        isActive: true
    },
    {
        roomId: 'ROOM-0012',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        areaId: purifiedWaterAreaId,
        roomCode: 'RM-PW-GEN',
        roomName: 'PW Generation Room',
        classification: 'UTILITY',
        isActive: true
    },
    {
        roomId: 'ROOM-0013',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        areaId: hvacAreaId,
        roomCode: 'RM-AHU',
        roomName: 'AHU Room',
        classification: 'UTILITY',
        isActive: true
    },
    {
        roomId: 'ROOM-0014',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        areaId: chemicalTestingAreaId,
        roomCode: 'RM-HPLC',
        roomName: 'HPLC Room',
        classification: 'LAB',
        isActive: true
    },
    {
        roomId: 'ROOM-0015',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        areaId: chemicalTestingAreaId,
        roomCode: 'RM-WETLAB',
        roomName: 'Wet Lab',
        classification: 'LAB',
        isActive: true
    },
    {
        roomId: 'ROOM-0016',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        areaId: microbiologyAreaId,
        roomCode: 'RM-MICRO',
        roomName: 'Micro Lab',
        classification: 'LAB',
        isActive: true
    },
    {
        roomId: 'ROOM-0017',
        tenantId: 'TNT-0001',
        plantId: 'PLNT-0001',
        areaId: microbiologyAreaId,
        roomCode: 'RM-INCUB',
        roomName: 'Incubation Room',
        classification: 'LAB',
        isActive: true
    }
], 'roomId', { sequenceName: 'roomId', prefix: 'ROOM', padLength: 4 });

// ============================================
// Seed - SUPER_ADMIN
// ============================================

db.mdm_user_profiles.updateOne(
    { userId: 'SUPER_ADMIN' },
    {
        $set: {
            userTrackId: 'USR-0001',
            tenantId: 'TNT-0001',
            firstName: 'System',
            lastName: 'Administrator',
            phoneNumber: '+91-9000000001',
            title: 'Platform Super Administrator',
            userType: 'INTERNAL_EMPLOYEE',
            email: 'super_admin@adavis.com',
            empId: 'EMP-00001',
            isActive: true,
            isBlocked: false,
            isExternal: false,
            updatedAt: ISODate()
        },
        $setOnInsert: {
            createdAt: ISODate('2026-03-01T10:00:00Z')
        }
    },
    { upsert: true }
);

db.auth_users.updateOne(
    { userId: 'SUPER_ADMIN' },
    {
        $set: {
            username: 'super_admin',
            email: 'super_admin@adavis.com',
            status: 'ACTIVE',
            isLocked: false,
            failedAttempts: 0,
            updatedAt: ISODate()
        },
        $setOnInsert: {
            createdAt: ISODate('2026-03-01T10:00:00Z')
        }
    },
    { upsert: true }
);


// ============================================
// Seed - IT_ADMIN
// ============================================

db.mdm_user_profiles.updateOne(
    { userId: 'IT_ADMIN' },
    {
        $set: {
            userTrackId: 'USR-0002',
            tenantId: 'TNT-0001',
            firstName: 'IT',
            lastName: 'Administrator',
            phoneNumber: '+91-9000000002',
            title: 'IT Administrator',
            userType: 'INTERNAL_EMPLOYEE',
            email: 'it_admin@adavis.com',
            empId: 'EMP-00002',
            isActive: true,
            isBlocked: false,
            isExternal: false,
            updatedAt: ISODate()
        },
        $setOnInsert: {
            createdAt: ISODate('2026-03-01T10:00:00Z')
        }
    },
    { upsert: true }
);

db.auth_users.updateOne(
    { userId: 'IT_ADMIN' },
    {
        $set: {
            username: 'it_admin',
            email: 'it_admin@adavis.com',
            status: 'ACTIVE',
            isLocked: false,
            failedAttempts: 0,
            updatedAt: ISODate()
        },
        $setOnInsert: {
            createdAt: ISODate('2026-03-01T10:00:00Z')
        }
    },
    { upsert: true }
);


// ============================================
// Seed - Kishore User
// ============================================

db.mdm_user_profiles.updateOne(
    { userId: 'kishoreginguru' },
    {
        $set: {
            userTrackId: 'USR-0003',
            tenantId: 'TNT-0001',
            firstName: 'Kishore',
            lastName: 'Ginguru',
            phoneNumber: '+91-9000000002',
            title: 'IT Administrator',
            userType: 'INTERNAL_EMPLOYEE',
            email: 'kishoreginguru@gmail.com',
            empId: 'EMP-00003',
            isActive: true,
            isBlocked: false,
            isExternal: false,
            updatedAt: ISODate()
        },
        $setOnInsert: {
            createdAt: ISODate('2026-03-01T10:00:00Z')
        }
    },
    { upsert: true }
);

db.auth_users.updateOne(
    { userId: 'kishoreginguru' },
    {
        $set: {
            username: 'kishoreginguru',
            email: 'kishoreginguru@gmail.com',
            status: 'ACTIVE',
            isLocked: false,
            failedAttempts: 0,
            updatedAt: ISODate()
        },
        $setOnInsert: {
            createdAt: ISODate('2026-03-01T10:00:00Z')
        }
    },
    { upsert: true }
);


// ============================================
// Seed - Pallavi User
// ============================================

db.mdm_user_profiles.updateOne(
    { userId: 'pallu543' },
    {
        $set: {
            userTrackId: 'USR-0004',
            tenantId: 'TNT-0001',
            firstName: 'Pallavi Shetty',
            lastName: 'Shetty',
            phoneNumber: '+91-9000000002',
            title: 'IT Administrator',
            userType: 'INTERNAL_EMPLOYEE',
            email: 'pallu543@gmail.com',
            empId: 'EMP-00004',
            isActive: true,
            isBlocked: false,
            isExternal: false,
            updatedAt: ISODate()
        },
        $setOnInsert: {
            createdAt: ISODate('2026-03-01T10:00:00Z')
        }
    },
    { upsert: true }
);

db.auth_users.updateOne(
    { userId: 'pallu543' },
    {
        $set: {
            username: 'pallu543',
            email: 'pallu543@gmail.com',
            status: 'ACTIVE',
            isLocked: false,
            failedAttempts: 0,
            updatedAt: ISODate()
        },
        $setOnInsert: {
            createdAt: ISODate('2026-03-01T10:00:00Z')
        }
    },
    { upsert: true }
);



// ============================================
// SUPER_ADMIN Credentials
// ============================================

db.mdm_user_auth_credentials.updateOne(
    { userId: 'SUPER_ADMIN' },
    {
        $set: {
            email: 'super_admin@adavis.com',
            passwordHash: DEFAULT_ADAVIS_PASSWORD_HASH,
            mustChangePassword: false,
            passwordUpdatedAt: ISODate(),
            updatedAt: ISODate()
        },
        $setOnInsert: {
            createdAt: ISODate('2026-03-01T10:05:00Z')
        }
    },
    { upsert: true }
);

// ============================================
// IT_ADMIN Credentials
// ============================================

db.mdm_user_auth_credentials.updateOne(
    { userId: 'IT_ADMIN' },
    {
        $set: {
            email: 'it_admin@adavis.com',
            passwordHash: DEFAULT_ADAVIS_PASSWORD_HASH,
            mustChangePassword: false,
            passwordUpdatedAt: ISODate(),
            updatedAt: ISODate()
        },
        $setOnInsert: {
            createdAt: ISODate('2026-03-01T10:05:00Z')
        }
    },
    { upsert: true }
);

db.mdm_user_auth_credentials.updateOne(
    { userId: 'kishoreginguru' },
    {
        $set: {
            email: 'kishoreginguru@gmail.com',
            passwordHash: DEFAULT_ADAVIS_PASSWORD_HASH,
            mustChangePassword: false,
            passwordUpdatedAt: ISODate(),
            updatedAt: ISODate()
        },
        $setOnInsert: {
            createdAt: ISODate('2026-03-01T10:05:00Z')
        }
    },
    { upsert: true }
);


db.mdm_user_auth_credentials.updateOne(
    { userId: 'pallu543' },
    {
        $set: {
            email: 'pallu543@gmail.com',
            passwordHash: DEFAULT_ADAVIS_PASSWORD_HASH,
            mustChangePassword: false,
            passwordUpdatedAt: ISODate(),
            updatedAt: ISODate()
        },
        $setOnInsert: {
            createdAt: ISODate('2026-03-01T10:05:00Z')
        }
    },
    { upsert: true }
);


var roleSeed = [
    {
        roleId: 'ROLE-0001',
        tenantId: 'TNT-0001',
        roleName: 'Platform Super Administrator',
        description: 'Platform Super Administrator',
        roleCode: 'SUPER_ADMIN',
        isActive: true,
        createdAt: ISODate('2026-01-20T10:30:00Z')
    },
    {
        roleId: 'ROLE-0002',
        tenantId: 'TNT-0001',
        roleName: 'IT Administrator',
        description: 'IT Administrator',
        roleCode: 'IT_ADMIN',
        isActive: true,
        createdAt: ISODate('2026-01-20T10:30:00Z')
    },
    {
        roleId: 'ROLE-0003',
        tenantId: 'TNT-0001',
        roleName: 'Plant Administrator',
        description: 'Plant Administrator',
        roleCode: 'PLANT_ADMIN',
        isActive: true,
        createdAt: ISODate('2026-01-20T10:30:00Z')
    },
    {
        roleId: 'ROLE-0004',
        tenantId: 'TNT-0001',
        roleName: 'Master Data Steward',
        description: 'Master Data Steward',
        roleCode: 'MASTER_DATA_STEWARD',
        isActive: true,
        createdAt: ISODate('2026-01-20T10:30:00Z')
    },
    {
        roleId: 'ROLE-0005',
        tenantId: 'TNT-0001',
        roleName: 'QA Manager',
        description: 'QA Manager',
        roleCode: 'QA_MANAGER',
        isActive: true,
        createdAt: ISODate('2026-01-20T10:30:00Z')
    },
    {
        roleId: 'ROLE-0006',
        tenantId: 'TNT-0001',
        roleName: 'QC Manager',
        description: 'QC Manager',
        roleCode: 'QC_MANAGER',
        isActive: true,
        createdAt: ISODate('2026-01-20T10:30:00Z')
    },
    {
        roleId: 'ROLE-0007',
        tenantId: 'TNT-0001',
        roleName: 'Compliance Officer',
        description: 'Compliance Officer',
        roleCode: 'COMPLIANCE_OFFICER',
        isActive: true,
        createdAt: ISODate('2026-01-20T10:30:00Z')
    },
    {
        roleId: 'ROLE-0008',
        tenantId: 'TNT-0001',
        roleName: 'Production Operator',
        description: 'Production Operator',
        roleCode: 'PRODUCTION_OPERATOR',
        isActive: true,
        createdAt: ISODate('2026-01-20T10:30:00Z')
    },
    {
        roleId: 'ROLE-0009',
        tenantId: 'TNT-0001',
        roleName: 'Shift Supervisor',
        description: 'Shift Supervisor',
        roleCode: 'SHIFT_SUPERVISOR',
        isActive: true,
        createdAt: ISODate('2026-01-20T10:30:00Z')
    },
    {
        roleId: 'ROLE-0010',
        tenantId: 'TNT-0001',
        roleName: 'Maintenance Technician',
        description: 'Maintenance Technician',
        roleCode: 'MAINTENANCE_TECHNICIAN',
        isActive: true,
        createdAt: ISODate('2026-01-20T10:30:00Z')
    },
    {
        roleId: 'ROLE-0011',
        tenantId: 'TNT-0001',
        roleName: 'Automation Engineer',
        description: 'Automation Engineer',
        roleCode: 'AUTOMATION_ENGINEER',
        isActive: true,
        createdAt: ISODate('2026-01-20T10:30:00Z')
    },
    {
        roleId: 'ROLE-0012',
        tenantId: 'TNT-0001',
        roleName: 'Utilities Engineer',
        description: 'Utilities Engineer',
        roleCode: 'UTILITIES_ENGINEER',
        isActive: true,
        createdAt: ISODate('2026-01-20T10:30:00Z')
    },
    {
        roleId: 'ROLE-0013',
        tenantId: 'TNT-0001',
        roleName: 'Supply Chain Manager',
        description: 'Supply Chain Manager',
        roleCode: 'SUPPLY_CHAIN_MANAGER',
        isActive: true,
        createdAt: ISODate('2026-01-20T10:30:00Z')
    },
    {
        roleId: 'ROLE-0014',
        tenantId: 'TNT-0001',
        roleName: 'Document Controller',
        description: 'Document Controller',
        roleCode: 'DOCUMENT_CONTROLLER',
        isActive: true,
        createdAt: ISODate('2026-01-20T10:30:00Z')
    },
    {
        roleId: 'ROLE-0015',
        tenantId: 'TNT-0001',
        roleName: 'IIOT Administrator',
        description: 'IIOT Administrator',
        roleCode: 'IIOT_ADMIN',
        isActive: true,
        createdAt: ISODate('2026-01-20T10:30:00Z')
    }
];
upsertManyWithAutoId('mdm_roles', roleSeed, 'roleId', { sequenceName: 'roleId', prefix: 'ROLE', padLength: 4 });

var superAdminRole = db.mdm_roles.findOne({ tenantId: 'TNT-0001', roleCode: 'SUPER_ADMIN' });
var itAdminRole = db.mdm_roles.findOne({ tenantId: 'TNT-0001', roleCode: 'IT_ADMIN' });
var superAdminRoleId = superAdminRole && superAdminRole.roleId ? superAdminRole.roleId : 'ROLE-0001';
var itAdminRoleId = itAdminRole && itAdminRole.roleId ? itAdminRole.roleId : 'ROLE-0002';

var groupSeed = [
    {
        groupId: 'GRP-0001',
        tenantId: 'TNT-0001',
        groupName: 'Platform Administrators',
        description: 'Platform Administrators',
        groupCode: 'PLATFORM_ADMIN',
        isActive: true,
        createdAt: ISODate('2026-01-20T11:00:00Z')
    },
    {
        groupId: 'GRP-0002',
        tenantId: 'TNT-0001',
        groupName: 'IT Administrators',
        description: 'IT Administrators',
        groupCode: 'IT_ADMIN',
        isActive: true,
        createdAt: ISODate('2026-01-20T11:00:00Z')
    },
    {
        groupId: 'GRP-0003',
        tenantId: 'TNT-0001',
        groupName: 'Plant Administration',
        description: 'Plant Administration',
        groupCode: 'PLANT_ADMIN',
        isActive: true,
        createdAt: ISODate('2026-01-20T11:00:00Z')
    },
    {
        groupId: 'GRP-0004',
        tenantId: 'TNT-0001',
        groupName: 'MDM Governance',
        description: 'MDM Governance',
        groupCode: 'MDM_GOVERNANCE',
        isActive: true,
        createdAt: ISODate('2026-01-20T11:00:00Z')
    },
    {
        groupId: 'GRP-0005',
        tenantId: 'TNT-0001',
        groupName: 'Quality and Compliance',
        description: 'Quality and Compliance',
        groupCode: 'QUALITY_COMPLIANCE',
        isActive: true,
        createdAt: ISODate('2026-01-20T11:00:00Z')
    },
    {
        groupId: 'GRP-0006',
        tenantId: 'TNT-0001',
        groupName: 'Production Operations',
        description: 'Production Operations',
        groupCode: 'PRODUCTION_OPERATIONS',
        isActive: true,
        createdAt: ISODate('2026-01-20T11:00:00Z')
    },
    {
        groupId: 'GRP-0007',
        tenantId: 'TNT-0001',
        groupName: 'Maintenance and Engineering',
        description: 'Maintenance and Engineering',
        groupCode: 'MAINTENANCE_ENGINEERING',
        isActive: true,
        createdAt: ISODate('2026-01-20T11:00:00Z')
    },
    {
        groupId: 'GRP-0008',
        tenantId: 'TNT-0001',
        groupName: 'Supply Chain Operations',
        description: 'Supply Chain Operations',
        groupCode: 'SUPPLY_CHAIN',
        isActive: true,
        createdAt: ISODate('2026-01-20T11:00:00Z')
    },
    {
        groupId: 'GRP-0009',
        tenantId: 'TNT-0001',
        groupName: 'Document Control',
        description: 'Document Control',
        groupCode: 'DOCUMENT_CONTROL',
        isActive: true,
        createdAt: ISODate('2026-01-20T11:00:00Z')
    },
    {
        groupId: 'GRP-0010',
        tenantId: 'TNT-0001',
        groupName: 'IIOT Operations',
        description: 'IIOT Operations',
        groupCode: 'IIOT_OPERATIONS',
        isActive: true,
        createdAt: ISODate('2026-01-20T11:00:00Z')
    }
];
upsertManyWithAutoId('mdm_user_groups', groupSeed, 'groupId', { sequenceName: 'groupId', prefix: 'GRP', padLength: 4 });

var platformAdminGroup = db.mdm_user_groups.findOne({ tenantId: 'TNT-0001', groupCode: 'PLATFORM_ADMIN' });
var platformAdminGroupId = platformAdminGroup && platformAdminGroup.groupId ? platformAdminGroup.groupId : 'GRP-0001';

var moduleSeed = [
    { moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', moduleName: 'Master Data Management', displayOrder: 1, isActive: true },
    { moduleId: 'MOD-0002', moduleCode: 'MOD-IIOT', moduleName: 'IIOT Data Management', displayOrder: 2, isActive: true },
    { moduleId: 'MOD-0003', moduleCode: 'MOD-DMS', moduleName: 'Data Management System', displayOrder: 3, isActive: true }
];
upsertManyWithAutoId('mdm_modules', moduleSeed, 'moduleId', { sequenceName: 'moduleId', prefix: 'MOD', padLength: 4 });

var screenSeed = [
    { screenId: 'SCR-0001', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenCode: 'SCR-MDM-DASHBOARD', screenName: 'Dashboard', displayOrder: 1, isActive: true },
    { screenId: 'SCR-0002', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenCode: 'SCR-MDM-TENANT-LICENSE', screenName: 'Tenant & License Governance', displayOrder: 2, isActive: true },
    { screenId: 'SCR-0003', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenCode: 'SCR-MDM-PLANT-TOPOLOGY', screenName: 'Plant Topology', displayOrder: 3, isActive: true },
    { screenId: 'SCR-0004', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenCode: 'SCR-MDM-DEPARTMENT', screenName: 'Department', displayOrder: 4, isActive: true },
    { screenId: 'SCR-0005', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenCode: 'SCR-MDM-USER-ROLE-GROUPS', screenName: 'User, Role & Access Management', displayOrder: 5, isActive: true },
    { screenId: 'SCR-0006', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenCode: 'SCR-MDM-IIOT-MASTER', screenName: 'Equipment & Critical Parameter Master Data', displayOrder: 6, isActive: true },
    { screenId: 'SCR-0007', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenCode: 'SCR-MDM-AUDIT-REPORTING', screenName: 'Audit & Reporting', displayOrder: 7, isActive: true },
    { screenId: 'SCR-0008', moduleId: 'MOD-0002', moduleCode: 'MOD-IIOT', screenCode: 'SCR-IIOT-EQUIPMENT-OVERVIEW', screenName: 'Equipment Overview', displayOrder: 1, isActive: true },
    { screenId: 'SCR-0009', moduleId: 'MOD-0002', moduleCode: 'MOD-IIOT', screenCode: 'SCR-IIOT-MONITORING-CONSOLE', screenName: 'Monitoring Console', displayOrder: 2, isActive: true },
    { screenId: 'SCR-0010', moduleId: 'MOD-0002', moduleCode: 'MOD-IIOT', screenCode: 'SCR-IIOT-ANALYTICS-OEE', screenName: 'Analytics OEE', displayOrder: 3, isActive: true }
];
upsertManyWithAutoId('mdm_screens', screenSeed, 'screenId', { sequenceName: 'screenId', prefix: 'SCR', padLength: 4 });

var featureSeed = [
    { featureId: 'FEAT-0001', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenId: 'SCR-0001', screenCode: 'SCR-MDM-DASHBOARD', featureCode: 'FEAT-MDM-DASHBOARD-OVERVIEW', featureName: 'Overview', displayOrder: 1, isActive: true },
    { featureId: 'FEAT-0002', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenId: 'SCR-0002', screenCode: 'SCR-MDM-TENANT-LICENSE', featureCode: 'FEAT-MDM-MANAGE-TENANT', featureName: 'Manage Tenant', displayOrder: 1, isActive: true },
    { featureId: 'FEAT-0003', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenId: 'SCR-0002', screenCode: 'SCR-MDM-TENANT-LICENSE', featureCode: 'FEAT-MDM-MANAGE-LICENSE', featureName: 'Manage License', displayOrder: 2, isActive: true },
    { featureId: 'FEAT-0004', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenId: 'SCR-0003', screenCode: 'SCR-MDM-PLANT-TOPOLOGY', featureCode: 'FEAT-MDM-MANAGE-PLANTS', featureName: 'Manage Plants', displayOrder: 1, isActive: true },
    { featureId: 'FEAT-0005', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenId: 'SCR-0003', screenCode: 'SCR-MDM-PLANT-TOPOLOGY', featureCode: 'FEAT-MDM-MANAGE-BUILDINGS', featureName: 'Manage Buildings', displayOrder: 2, isActive: true },
    { featureId: 'FEAT-0006', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenId: 'SCR-0003', screenCode: 'SCR-MDM-PLANT-TOPOLOGY', featureCode: 'FEAT-MDM-MANAGE-AREAS', featureName: 'Manage Areas', displayOrder: 3, isActive: true },
    { featureId: 'FEAT-0007', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenId: 'SCR-0003', screenCode: 'SCR-MDM-PLANT-TOPOLOGY', featureCode: 'FEAT-MDM-MANAGE-ROOMS', featureName: 'Manage Rooms', displayOrder: 4, isActive: true },
    { featureId: 'FEAT-0008', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenId: 'SCR-0004', screenCode: 'SCR-MDM-DEPARTMENT', featureCode: 'FEAT-MDM-MANAGE-DEPARTMENTS', featureName: 'Manage Departments', displayOrder: 1, isActive: true },
    { featureId: 'FEAT-0009', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenId: 'SCR-0005', screenCode: 'SCR-MDM-USER-ROLE-GROUPS', featureCode: 'FEAT-MDM-MANAGE-USERS', featureName: 'Manage Users', displayOrder: 1, isActive: true },
    { featureId: 'FEAT-0010', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenId: 'SCR-0005', screenCode: 'SCR-MDM-USER-ROLE-GROUPS', featureCode: 'FEAT-MDM-MANAGE-ROLES', featureName: 'Manage Roles', displayOrder: 2, isActive: true },
    { featureId: 'FEAT-0011', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenId: 'SCR-0005', screenCode: 'SCR-MDM-USER-ROLE-GROUPS', featureCode: 'FEAT-MDM-MANAGE-ROLE-PERMISSIONS', featureName: 'Manage Role Permissions', displayOrder: 3, isActive: true },
    { featureId: 'FEAT-0012', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenId: 'SCR-0005', screenCode: 'SCR-MDM-USER-ROLE-GROUPS', featureCode: 'FEAT-MDM-TAG-USERS-TO-PLANTS-AND-ROLES', featureName: 'Tag Users to Plants & Roles', displayOrder: 4, isActive: true },
    { featureId: 'FEAT-0014', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenId: 'SCR-0006', screenCode: 'SCR-MDM-IIOT-MASTER', featureCode: 'FEAT-MDM-MANAGE-EQUIPMENT', featureName: 'Manage Equipment', displayOrder: 1, isActive: true },
    { featureId: 'FEAT-0015', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenId: 'SCR-0006', screenCode: 'SCR-MDM-IIOT-MASTER', featureCode: 'FEAT-MDM-MANAGE-EQUIPMENT-CRITICAL-PARAMETERS', featureName: 'Manage Equipment Critical Parameters', displayOrder: 2, isActive: true },
    { featureId: 'FEAT-0016', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenId: 'SCR-0006', screenCode: 'SCR-MDM-IIOT-MASTER', featureCode: 'FEAT-MDM-MANAGE-EQUIPMENT-CRITICAL-PARAMETER-LIMITS', featureName: 'Manage Equipment Critical Parameter Limits', displayOrder: 3, isActive: true },
    { featureId: 'FEAT-0017', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenId: 'SCR-0007', screenCode: 'SCR-MDM-AUDIT-REPORTING', featureCode: 'FEAT-MDM-AUDIT-LOGS', featureName: 'Audit Logs', displayOrder: 1, isActive: true },
    { featureId: 'FEAT-0018', moduleId: 'MOD-0001', moduleCode: 'MOD-MDM', screenId: 'SCR-0007', screenCode: 'SCR-MDM-AUDIT-REPORTING', featureCode: 'FEAT-MDM-REPORTS', featureName: 'Reports', displayOrder: 2, isActive: true },
    { featureId: 'FEAT-0019', moduleId: 'MOD-0002', moduleCode: 'MOD-IIOT', screenId: 'SCR-0008', screenCode: 'SCR-IIOT-EQUIPMENT-OVERVIEW', featureCode: 'FEAT-IIOT-EQUIPMENT-HEALTH-STATUS-PERFORMANCE-TREND', featureName: 'Equipment Health, Status & Performance Trend', displayOrder: 1, isActive: true },
    { featureId: 'FEAT-0020', moduleId: 'MOD-0002', moduleCode: 'MOD-IIOT', screenId: 'SCR-0009', screenCode: 'SCR-IIOT-MONITORING-CONSOLE', featureCode: 'FEAT-IIOT-EQUIPMENT-MONITORING', featureName: 'Equipment Monitoring', displayOrder: 1, isActive: true },
    { featureId: 'FEAT-0021', moduleId: 'MOD-0002', moduleCode: 'MOD-IIOT', screenId: 'SCR-0010', screenCode: 'SCR-IIOT-ANALYTICS-OEE', featureCode: 'FEAT-IIOT-OVERALL-EQUIPMENT-EFFICIENCY', featureName: 'Overall Equipment Efficiency', displayOrder: 1, isActive: true }
];
upsertManyWithAutoId('mdm_features', featureSeed, 'featureId', { sequenceName: 'featureId', prefix: 'FEAT', padLength: 4 });

upsertOne('mdm_role_assignments_to_user_groups', { groupId: platformAdminGroupId, roleId: superAdminRoleId }, {
    groupId: platformAdminGroupId,
    roleId: superAdminRoleId,
    isActive: true,
    assignedAt: ISODate('2026-03-01T10:10:00Z'),
    assignedBy: 'SYSTEM'
});

upsertMany('mdm_role_assignments_to_user_groups', [
    { groupId: 'GRP-0002', roleId: 'ROLE-0002', isActive: true, assignedAt: ISODate('2026-03-01T10:10:00Z'), assignedBy: 'SYSTEM' },
    { groupId: 'GRP-0003', roleId: 'ROLE-0003', isActive: true, assignedAt: ISODate('2026-03-01T10:10:00Z'), assignedBy: 'SYSTEM' },
    { groupId: 'GRP-0004', roleId: 'ROLE-0004', isActive: true, assignedAt: ISODate('2026-03-01T10:10:00Z'), assignedBy: 'SYSTEM' },
    { groupId: 'GRP-0005', roleId: 'ROLE-0005', isActive: true, assignedAt: ISODate('2026-03-01T10:10:00Z'), assignedBy: 'SYSTEM' },
    { groupId: 'GRP-0005', roleId: 'ROLE-0006', isActive: true, assignedAt: ISODate('2026-03-01T10:10:00Z'), assignedBy: 'SYSTEM' },
    { groupId: 'GRP-0005', roleId: 'ROLE-0007', isActive: true, assignedAt: ISODate('2026-03-01T10:10:00Z'), assignedBy: 'SYSTEM' },
    { groupId: 'GRP-0006', roleId: 'ROLE-0008', isActive: true, assignedAt: ISODate('2026-03-01T10:10:00Z'), assignedBy: 'SYSTEM' },
    { groupId: 'GRP-0006', roleId: 'ROLE-0009', isActive: true, assignedAt: ISODate('2026-03-01T10:10:00Z'), assignedBy: 'SYSTEM' },
    { groupId: 'GRP-0007', roleId: 'ROLE-0010', isActive: true, assignedAt: ISODate('2026-03-01T10:10:00Z'), assignedBy: 'SYSTEM' },
    { groupId: 'GRP-0007', roleId: 'ROLE-0011', isActive: true, assignedAt: ISODate('2026-03-01T10:10:00Z'), assignedBy: 'SYSTEM' },
    { groupId: 'GRP-0007', roleId: 'ROLE-0012', isActive: true, assignedAt: ISODate('2026-03-01T10:10:00Z'), assignedBy: 'SYSTEM' },
    { groupId: 'GRP-0008', roleId: 'ROLE-0013', isActive: true, assignedAt: ISODate('2026-03-01T10:10:00Z'), assignedBy: 'SYSTEM' },
    { groupId: 'GRP-0009', roleId: 'ROLE-0014', isActive: true, assignedAt: ISODate('2026-03-01T10:10:00Z'), assignedBy: 'SYSTEM' },
    { groupId: 'GRP-0010', roleId: 'ROLE-0015', isActive: true, assignedAt: ISODate('2026-03-01T10:10:00Z'), assignedBy: 'SYSTEM' }
], 'roleId');

upsertOne('mdm_user_assignments_to_user_groups', { userId: 'SUPER_ADMIN', groupId: platformAdminGroupId }, {
    userId: 'SUPER_ADMIN',
    groupId: platformAdminGroupId,
    isActive: true,
    assignedAt: ISODate('2026-03-01T10:10:00Z'),
    assignedBy: 'SYSTEM'
});

upsertOne('mdm_user_assignments_to_user_groups', { userId: 'kishoreginguru', groupId: platformAdminGroupId }, {
    userId: 'kishoreginguru',
    groupId: platformAdminGroupId,
    isActive: true,
    assignedAt: ISODate('2026-03-01T10:10:00Z'),
    assignedBy: 'SYSTEM'
});

upsertOne('mdm_user_assignments_to_user_groups', { userId: 'pallu543', groupId: platformAdminGroupId }, {
    userId: 'pallu543',
    groupId: platformAdminGroupId,
    isActive: true,
    assignedAt: ISODate('2026-03-01T10:10:00Z'),
    assignedBy: 'SYSTEM'
});

upsertOne('mdm_user_context_assignments', { assignmentId: 'ASGN-000001' }, {
    assignmentId: 'ASGN-000001',
    tenantId: 'TNT-0001',
    userId: 'SUPER_ADMIN',
    plantId: 'PLNT-0001',
    departmentId: 'DEP-0002',
    groupId: platformAdminGroupId,
    isActive: true
});

upsertOne('mdm_user_context_assignments', { assignmentId: 'ASGN-000002' }, {
    assignmentId: 'ASGN-000002',
    tenantId: 'TNT-0001',
    userId: 'IT_ADMIN',
    plantId: 'PLNT-0001',
    departmentId: 'DEP-0002',
    groupId: platformAdminGroupId,
    isActive: true
});

upsertOne('mdm_user_context_assignments', { assignmentId: 'ASGN-000003' }, {
    assignmentId: 'ASGN-000003',
    tenantId: 'TNT-0001',
    userId: 'USR-0003',
    plantId: 'PLNT-0001',
    departmentId: 'DEP-0002',
    groupId: platformAdminGroupId,
    isActive: true
});

upsertOne('mdm_user_context_assignments', { assignmentId: 'ASGN-000004' }, {
    assignmentId: 'ASGN-000004',
    tenantId: 'TNT-0001',
    userId: 'USR-0004',
    plantId: 'PLNT-0001',
    departmentId: 'DEP-0002',
    groupId: platformAdminGroupId,
    isActive: true
});

upsertOne('mdm_role_permissions', { roleId: superAdminRoleId, moduleId: 'MOD-0001', version: 1 },
    buildRolePermissionDocument('TNT-0001', superAdminRoleId, 'MOD-0001', screenSeed, featureSeed)
);


upsertOne('mdm_licenses', { tenantId: 'TNT-0001' }, {
    tenantId: 'TNT-0001',
    licenseKey: 'LIC-ADAVIS-TNT-0001-2026',
    plan: {
        planId: 'PLAN_ENTERPRISE',
        planName: 'Enterprise',
        planType: 'PAID'
    },
    modules: ['MOD-MDM', 'MOD-IIOT'],
    maxUsers: 500,
    currentUsers: 4,
    status: 'ACTIVE',
    metadata: {
        tenantId: 'TNT-0001',
        tokenIssuer: 'ADAVIS',
        version: 1
    },
    startDate: now(),
    expiryDate: new Date(now().getTime() + 365 * 24 * 60 * 60 * 1000),
    encryptedLicenseToken: 'eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0ZW5hbnRJZCI6IlROVC0wMDAxIiwicGxhbiI6eyJwbGFuSWQiOiJQTEFOX0VOVEVSUFJJU0UiLCJwbGFuTmFtZSI6IkVudGVycHJpc2UiLCJwbGFuVHlwZSI6IlBBSUQifSwibW9kdWxlcyI6WyJNRE0iLCJJSU9UIl0sIm1heFVzZXJzIjoyMDAsInN0YXJ0RGF0ZSI6IjIwMjYtMDctMDEiLCJleHBpcnlEYXRlIjoiMjAyNy0wNy0zMCIsInZlcnNpb24iOjEsImlzcyI6IkFEQVZJUyIsImlhdCI6MTc4MzA1OTcyOSwiZXhwIjoxODE0NTk1NzI5fQ.mpBG-_rFCqUavoqfKSdYi2XJllKZotryn3IgsNqYieOJKgGfK6rWEER1ctJcfbTCbtIqyYNNhqR0Xb-7owPr81fJDZn-B_LaJ0BxunYWmRakrKmhWIBPEVN-Nw9NoO8nZ6NYEni1PFc1JTO0D9s1pc-Jd98EftaSTnYpAveBE4pQj2i6W_fILM_kXJEIMLJOj9OyJ-tFIUvDaOiMu73so6MK3euPw6eji_5UcjNpYGvMwliXE8lhdPXgCs5rFZum4wQ2gOzRTXRSom6UJgxWK_gkcy9QeLf3kf7y5iCVBl5E382iKD3fb7N8uaEyK1ldIF0E8UqwlK-muX2E2BsqaQ',
    isDeleted: false
});

logInfo('Initializing auto-increment ID sequences...');

var sequenceDefinitions = [
    { sequenceName: 'tenantId', collectionName: 'mdm_tenants', fieldName: 'tenantId', prefix: 'TNT', padLength: 4, seedValues: ['TNT-0001'] },
    { sequenceName: 'plantId', collectionName: 'mdm_plants', fieldName: 'plantId', prefix: 'PLNT', padLength: 4, seedValues: ['PLNT-0001', 'PLNT-0002'] },
    { sequenceName: 'blockId', collectionName: 'mdm_blocks', fieldName: 'blockId', prefix: 'BLK', padLength: 4, seedValues: ['BLK-0001', 'BLK-0002', 'BLK-0003', 'BLK-0004' ] },
    { sequenceName: 'areaId', collectionName: 'mdm_areas', fieldName: 'areaId', prefix: 'AREA', padLength: 4, seedValues: ['AREA-0001', 'AREA-0002', 'AREA-0003', 'AREA-0004', 'AREA-0005', 'AREA-0006', 'AREA-0007', 'AREA-0008', 'AREA-0009', 'AREA-0010', 'AREA-0011' ] },
    { sequenceName: 'departmentId', collectionName: 'mdm_departments', fieldName: 'departmentId', prefix: 'DEP', padLength: 4, seedValues: ['DEP-0001', 'DEP-0002', 'DEP-0003', 'DEP-0004', 'DEP-0005', 'DEP-0006', 'DEP-0007', 'DEP-0008', 'DEP-0009', 'DEP-0010', 'DEP-0011', 'DEP-0012', 'DEP-0013', 'DEP-0014', 'DEP-0015', 'DEP-0016'] },
    { sequenceName: 'roomId', collectionName: 'mdm_rooms', fieldName: 'roomId', prefix: 'ROOM', padLength: 4, seedValues: ['ROOM-0001', 'ROOM-0002', 'ROOM-0003', 'ROOM-0004', 'ROOM-0005', 'ROOM-0006', 'ROOM-0007', 'ROOM-0008', 'ROOM-0009', 'ROOM-0010', 'ROOM-0011', 'ROOM-0012', 'ROOM-0013', 'ROOM-0014', 'ROOM-0015', 'ROOM-0016', 'ROOM-0017'] },
    { sequenceName: 'userTrackId', collectionName: 'mdm_user_profiles', fieldName: 'userTrackId', prefix: 'USR', padLength: 4, seedValues: ['USR-0001', 'USR-0002', 'USR-0003', 'USR-0004'] },
    { sequenceName: 'groupId', collectionName: 'mdm_user_groups', fieldName: 'groupId', prefix: 'GRP', padLength: 4, seedValues: ['GRP-0001', 'GRP-0002', 'GRP-0003', 'GRP-0004', 'GRP-0005', 'GRP-0006', 'GRP-0007', 'GRP-0008', 'GRP-0009', 'GRP-0010'] },
    { sequenceName: 'roleId', collectionName: 'mdm_roles', fieldName: 'roleId', prefix: 'ROLE', padLength: 4, seedValues: ['ROLE-0001', 'ROLE-0002', 'ROLE-0003', 'ROLE-0004', 'ROLE-0005', 'ROLE-0006', 'ROLE-0007', 'ROLE-0008', 'ROLE-0009', 'ROLE-0010', 'ROLE-0011', 'ROLE-0012', 'ROLE-0013', 'ROLE-0014', 'ROLE-0015'] },
    { sequenceName: 'moduleId', collectionName: 'mdm_modules', fieldName: 'moduleId', prefix: 'MOD', padLength: 4, seedValues: ['MOD-0001', 'MOD-0002', 'MOD-0003'] },
    { sequenceName: 'screenId', collectionName: 'mdm_screens', fieldName: 'screenId', prefix: 'SCR', padLength: 4, seedValues: ['SCR-0001', 'SCR-0002', 'SCR-0003', 'SCR-0004', 'SCR-0005', 'SCR-0006', 'SCR-0007', 'SCR-0008', 'SCR-0009', 'SCR-0010'] },
    { sequenceName: 'featureId', collectionName: 'mdm_features', fieldName: 'featureId', prefix: 'FEAT', padLength: 4, seedValues: ['FEAT-0001', 'FEAT-0002', 'FEAT-0003', 'FEAT-0004', 'FEAT-0005', 'FEAT-0006', 'FEAT-0007', 'FEAT-0008', 'FEAT-0009', 'FEAT-0010', 'FEAT-0011', 'FEAT-0012', 'FEAT-0013', 'FEAT-0014', 'FEAT-0015', 'FEAT-0016', 'FEAT-0017', 'FEAT-0018', 'FEAT-0019', 'FEAT-0020', 'FEAT-0021'] },
    { sequenceName: 'assignmentId', collectionName: 'mdm_user_context_assignments', fieldName: 'assignmentId', prefix: 'ASGN', padLength: 6, seedValues: ['ASGN-000001'] },
    { sequenceName: 'licenseId', collectionName: 'mdm_licenses', fieldName: 'licenseId', prefix: 'LIC', padLength: 4, seedValues: ['LIC-0001'] },
    { sequenceName: 'assetId', collectionName: 'iiot_assets', fieldName: 'assetId', prefix: 'EQP-RMG', padLength: 4, seedValues: ['EQP-RMG-0042'] },
    { sequenceName: 'tagId', collectionName: 'iiot_asset_tags', fieldName: 'tagId', prefix: 'TAG', padLength: 6, seedValues: ['TAG-000512'] },
    { sequenceName: 'thresholdId', collectionName: 'iiot_tag_thresholds', fieldName: 'thresholdId', prefix: 'THR', padLength: 6, seedValues: ['THR-001042'] },
    { sequenceName: 'requestId', collectionName: 'mdm_user_lifecycle_requests', fieldName: 'requestId', prefix: 'REQ', padLength: 6, seedValues: [] },
    { sequenceName: 'documentId', collectionName: 'dms_documents', fieldName: 'documentId', prefix: 'DOC', padLength: 6, seedValues: [] }
];

for (var s = 0; s < sequenceDefinitions.length; s++) {
    initializeSequence(sequenceDefinitions[s]);
}

print('========================================');
print('Database initialization complete');
print('Collections, validators, sequences and default seed data aligned');
print('========================================');

