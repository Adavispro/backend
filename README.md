# backend

## OEE calculation inputs

The IIoT OEE page stores its inputs through `/api/v1/iiot/reports/oee-inputs`:

- `GET`: returns saved settings and downtime; optional `tenantId` and `plantId`
  restrict both collections.
- `PUT /settings`: saves a tenant/plant/equipment-scoped production schedule,
  effective dates, IANA timezone, weekdays/shifts, product-specific ideal batch
  durations and explicit downtime-log completeness confirmation.
- `POST /downtime`: records a planned/unplanned interval, validated category and
  mandatory reason. Duration is calculated server-side, including overnight
  intervals and timezone offsets.

Writes require a valid authenticated JWT actor and verify the equipment's
tenant/plant association. Settings are stored in `iiot_oee_batch_settings`,
with previous/current snapshots in `iiot_oee_input_audit`; downtime records
are stored in `iiot_oee_downtime` with actor/time metadata. No demonstration
schedule or downtime is seeded. Replacing a setting changes its effective
range; earlier settings remain in the audit collection, not as active inputs.
Only validated ranges should be confirmed complete.

Targeted backend regression test: `mvn -o -Dtest=OeeInputsServiceTest test`.

### Explicit OEE testing inputs

The manually run [seed script](scripts/seed-oee-test-inputs.js) adds testing inputs
for MC081, MB003, MB004, MB005 and MB041 in tenant `TNT-0001`, plant `PLNT-0001`,
for production dates October 3-9, 2026. It does not run automatically.
It assumes all three shifts on all weekdays in `Asia/Kolkata` solely for testing,
and creates 140 synthetic planned/unplanned downtime records. Each record is
marked `isTestData: true` and `testDataset: OEE-TEST-2026-10-03-09`.

The script refuses to overwrite existing settings not owned by this dataset.
Repeated runs do not duplicate downtime. It never changes production batches,
QA approvals, or ideal batch durations. The frontend displays a TEST DATA warning;
these schedule/downtime-derived results are not validated production KPIs.
Availability/utilization are testable; performance and full OEE still require
validated ideal durations and genuine final QA outcomes.

## Final compression batch PDF

MC081 (the Sejong compression machine) is the only equipment
with a consolidated batch PDF. Other compression machines are not consolidated.
File ingestion loads production reports and their
derived lot identities; it does not generate the final QA PDF.

The IIoT PDF service requires a production report and QA approval for every lot
in the batch/equipment/tenant/plant scope. Machine execution status `COMPLETED`
alone is not approval. Earlier lot approvals remain valid and set PDF readiness
to `WAITING_FOR_ALL_LOTS`; after the last lot is approved the final report can be
generated. Batch-summary responses include `compressionPdfReadiness` for the UI.

The final document repeats the source production-report layout for each lot,
then includes batch histories, workflow changes and controlled print history.
Missing source measurements are `-`, not sample values. Shared history records
are deduplicated; source-lot provenance is distinguished from recorded event lot
attribution. QA approval timestamps and actors are separate from generation and
print timestamps and actors.

Final compression documents use `lotNo: CONSOLIDATED`,
`reportScope: COMPRESSION_BATCH`, `reportLayoutVersion: 2` and `includedLots`.
Old lot PDFs are not eligible for final retrieval. Print/download regenerate the
final PDF to include the current actor and print metadata. Other equipment keeps
its existing lot/stage PDF behavior.
