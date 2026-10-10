// Run with mongosh against adavis_platform; only this named test dataset is upserted.
const dataset = "OEE-TEST-2026-10-03-09";
const tenantId = "TNT-0001";
const plantId = "PLNT-0001";
const equipmentCodes = ["MC081", "MB003", "MB004", "MB005", "MB041"];
const fromDate = "2026-10-03";
const toDate = "2026-10-09";
const database = db.getSiblingDB("adavis_platform");
const timestamp = new Date().toISOString();

// Complete preflight before writing: never overwrite user-configured inputs.
for (const equipmentId of equipmentCodes) {
  if (!database.iiot_equipment_master.findOne({ tenantId, plantId, equipmentCode: equipmentId })) {
    throw new Error(`Missing equipment master for ${equipmentId}; no test inputs seeded.`);
  }
  const id = `${tenantId}|${plantId}|${equipmentId}`;
  const existing = database.iiot_oee_batch_settings.findOne({ _id: id });
  if (existing && existing.testDataset !== dataset) {
    throw new Error(`Existing settings for ${equipmentId} are not owned by ${dataset}; refusing to replace them.`);
  }
}

for (const [index, equipmentId] of equipmentCodes.entries()) {
  const id = `${tenantId}|${plantId}|${equipmentId}`;
  const settings = {
    _id: id, tenantId, plantId, equipmentId, fromDate, toDate,
    scheduledShifts: ["Shift 1", "Shift 2", "Shift 3"],
    scheduledWeekdays: [0, 1, 2, 3, 4, 5, 6],
    timeZone: "Asia/Kolkata", idealBatchHours: {}, downtimeComplete: true,
    isTestData: true, testDataset: dataset,
    comments: "TEST ONLY: assumed 24-hour schedule and synthetic complete downtime log; not a production assertion.",
    updatedBy: "OEE_TEST_SEED", updatedAt: timestamp,
  };
  const previous = database.iiot_oee_batch_settings.findOne({ _id: id });
  database.iiot_oee_batch_settings.replaceOne({ _id: id }, settings, { upsert: true });
  database.iiot_oee_input_audit.insertOne({
    recordId: id, action: "SEED_TEST_SETTINGS", previous, current: settings,
    actor: "OEE_TEST_SEED", timestamp, isTestData: true, testDataset: dataset,
  });
  for (let day = 3; day <= 9; day++) {
    const date = `2026-10-${String(day).padStart(2, "0")}`;
    const intervals = [
      { startTime: "06:00", endTime: "06:30", durationHours: 0.5, classification: "PLANNED", category: "Cleaning" },
      { startTime: "14:00", endTime: "14:30", durationHours: 0.5, classification: "PLANNED", category: "Changeover" },
      { startTime: "10:00", endTime: `10:${String(10 + index * 5).padStart(2, "0")}`,
        durationHours: (10 + index * 5) / 60, classification: "UNPLANNED", category: "Minor Stoppage" },
      { startTime: "22:30", endTime: "22:45", durationHours: 0.25, classification: "UNPLANNED", category: "Equipment Failure" },
    ];
    for (const [slot, interval] of intervals.entries()) {
      const recordId = `${dataset}|${equipmentId}|${date}|${slot}`;
      database.iiot_oee_downtime.updateOne({ _id: recordId }, { $setOnInsert: {
        tenantId, plantId, equipmentId, date, ...interval, timeZone: "Asia/Kolkata",
        reason: `TEST ONLY: synthetic ${interval.category.toLowerCase()} for OEE screen validation`,
        comments: "Synthetic event; not sourced from PLC/ingestion. Do not use for production reporting.",
        createdBy: "OEE_TEST_SEED", createdAt: timestamp, isTestData: true, testDataset: dataset,
      } }, { upsert: true });
    }
  }
}
printjson({
  dataset, fromDate, toDate,
  settings: database.iiot_oee_batch_settings.countDocuments({ testDataset: dataset }),
  downtime: database.iiot_oee_downtime.countDocuments({ testDataset: dataset }),
  realBatchesModified: 0, qaApprovalsModified: 0,
});
