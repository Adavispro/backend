// ==============================================================================
// Adavis Platform - Docker Entrypoint Initializer
// Controls execution of reset and master seeding via environment variables
// ==============================================================================

var databaseName = 'adavis_platform';
if (typeof process !== 'undefined' && process.env && process.env.MONGO_INITDB_DATABASE) {
    databaseName = process.env.MONGO_INITDB_DATABASE;
}
db = db.getSiblingDB(databaseName);

var shouldReset = (typeof process !== 'undefined' && process.env && process.env.MONGO_INIT_RESET_DB === 'true');
var shouldSeed = (typeof process !== 'undefined' && process.env && process.env.MONGO_INIT_SEED_DATA === 'true');

print('================================================================');
print('[INIT_ENTRYPOINT] Starting MongoDB container initialization');
print('[INIT_ENTRYPOINT] Target Database    : ' + databaseName);
print('[INIT_ENTRYPOINT] MONGO_INIT_RESET_DB: ' + shouldReset);
print('[INIT_ENTRYPOINT] MONGO_INIT_SEED_DATA: ' + shouldSeed);
print('================================================================');

if (shouldReset) {
    print('[INIT_ENTRYPOINT] Executing reset_db.js...');
    try {
        load('/seed_data/reset_db.js');
    } catch (e) {
        print('[INIT_ENTRYPOINT] WARNING: Could not load /seed_data/reset_db.js: ' + e.message);
    }
}

if (shouldSeed) {
    print('[INIT_ENTRYPOINT] Executing seed_mdm_data.js...');
    try {
        load('/seed_data/seed_mdm_data.js');
    } catch (e) {
        print('[INIT_ENTRYPOINT] WARNING: Could not load /seed_data/seed_mdm_data.js: ' + e.message);
    }
} else {
    print('[INIT_ENTRYPOINT] Skipping seed_mdm_data.js (MONGO_INIT_SEED_DATA=false)');
}

print('[INIT_ENTRYPOINT] MongoDB initialization complete.');
