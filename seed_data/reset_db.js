// ============================================
// Adavis Platform - Reset Database Script
// Drops the application database for clean initialization
// ============================================

var databaseName = 'adavis_platform';
if (typeof process !== 'undefined' && process.env && process.env.MONGO_INITDB_DATABASE) {
    databaseName = process.env.MONGO_INITDB_DATABASE;
}
db = db.getSiblingDB(databaseName);

print('[RESET_DB] Resetting database: ' + databaseName);
try {
    db.dropDatabase();
    print('[RESET_DB] Database ' + databaseName + ' successfully dropped.');
} catch (e) {
    print('[RESET_DB] ERROR: Failed to drop database: ' + e.message);
    throw e;
}
