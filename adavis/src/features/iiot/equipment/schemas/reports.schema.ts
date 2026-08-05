import { z } from "zod";

const optionalRecord = z.record(z.string(), z.unknown()).optional().nullable();

export const equipmentLiveStatusSchema = z
  .object({
    tenantId: z.string().optional().nullable(),
    equipmentId: z.string(),
    plantId: z.string().optional().nullable(),
    blockId: z.string().optional().nullable(),
    areaId: z.string().optional().nullable(),
    roomId: z.string().optional().nullable(),
    roomNo: z.string().optional().nullable(),
    currentState: z.string().optional().nullable(),
    stateReason: z.string().optional().nullable(),
    lastBatchNo: z.string().optional().nullable(),
    lastLotNo: z.string().optional().nullable(),
    lastSourceSeqId: z.union([z.string(), z.number()]).optional().nullable(),
    lastEventAt: z.string().optional().nullable(),
    heartbeatAt: z.string().optional().nullable(),
    createdAt: z.union([z.string(), z.date()]).optional().nullable(),
    updatedAt: z.union([z.string(), z.date()]).optional().nullable(),
  })
  .passthrough();

export const equipmentLiveStatusListSchema = z.array(equipmentLiveStatusSchema);

export const batchSummarySchema = z
  .object({
    tenantId: z.string().optional().nullable(),
    equipmentId: z.string(),
    batchNo: z.string().optional().nullable(),
    lotNo: z.string().optional().nullable(),
    productName: z.string().optional().nullable(),
    plantId: z.string().optional().nullable(),
    areaId: z.string().optional().nullable(),
    batchStatus: z.string().optional().nullable(),
    batchStartAt: z.string().optional().nullable(),
    batchEndAt: z.string().optional().nullable(),
    cppRecordCount: z.number().optional().nullable(),
    alarmCount: z.number().optional().nullable(),
    eventCount: z.number().optional().nullable(),
    updatedAt: z.string().optional().nullable(),
  })
  .passthrough();

export const batchSummaryListSchema = z.array(batchSummarySchema);

export const cppRecordSchema = z
  .object({
    observedAt: z.string().optional().nullable(),
    meta: optionalRecord,
    source: optionalRecord,
    metrics: optionalRecord,
    ingestedAt: z.string().optional().nullable(),
  })
  .passthrough();

export const cppRecordListSchema = z.array(cppRecordSchema);

export const alarmEventRecordSchema = z
  .object({
    eventAt: z.string().optional().nullable(),
    meta: optionalRecord,
    source: optionalRecord,
    event: optionalRecord,
    ingestedAt: z.string().optional().nullable(),
  })
  .passthrough();

export const alarmEventRecordListSchema = z.array(alarmEventRecordSchema);

export const criticalParameterLimitSchema = z
  .object({
    parameterLimitId: z.string().optional().nullable(),
    parameterId: z.string().optional().nullable(),
    equipmentId: z.string().optional().nullable(),
    parameterType: z.string().optional().nullable(),
    parameterCode: z.string().optional().nullable(),
    lowerLimit: z.union([z.string(), z.number()]).optional().nullable(),
    upperLimit: z.union([z.string(), z.number()]).optional().nullable(),
    minValue: z.union([z.string(), z.number()]).optional().nullable(),
    maxValue: z.union([z.string(), z.number()]).optional().nullable(),
    warningLow: z.union([z.string(), z.number()]).optional().nullable(),
    warningHigh: z.union([z.string(), z.number()]).optional().nullable(),
    isActive: z.boolean().optional().nullable(),
  })
  .passthrough();

export const criticalParameterLimitListSchema = z.array(
  criticalParameterLimitSchema,
);

export const oeeMetricsSchema = z.object({
  overallOee: z.number(),
  availability: z.number(),
  performance: z.number(),
  quality: z.number(),
  trendDelta: z.number(),
});

export const oeeTrendPointSchema = z.object({
  label: z.string(),
  value: z.number(),
});

export const oeeDowntimeSegmentSchema = z.object({
  label: z.string(),
  value: z.number(),
  color: z.string(),
  gradientTo: z.string(),
  displayValue: z.string(),
  legendOrder: z.number(),
});

export const oeeShiftComparisonItemSchema = z.object({
  label: z.string(),
  value: z.number(),
  color: z.string(),
  gradientTo: z.string(),
});

export const oeeTopBreakdownLossSchema = z.object({
  label: z.string(),
  value: z.number(),
  color: z.string(),
});

export const oeeSummaryRowSchema = z.object({
  date: z.string(),
  productName: z.string(),
  batchNo: z.string(),
  startTime: z.string(),
  endTime: z.string(),
  runTimePercent: z.string(),
  runTimeHrs: z.string(),
});

export const oeeAnalyticsPayloadSchema = z.object({
  metrics: oeeMetricsSchema,
  trendPoints: z.array(oeeTrendPointSchema),
  downtimeSegments: z.array(oeeDowntimeSegmentSchema),
  shiftComparison: z.array(oeeShiftComparisonItemSchema),
  topBreakdownLosses: z.array(oeeTopBreakdownLossSchema),
  summaryRows: z.array(oeeSummaryRowSchema),
});

export type EquipmentLiveStatus = z.infer<typeof equipmentLiveStatusSchema>;
export type BatchSummary = z.infer<typeof batchSummarySchema>;
export type CppRecord = z.infer<typeof cppRecordSchema>;
export type AlarmEventRecord = z.infer<typeof alarmEventRecordSchema>;
export type CriticalParameterLimit = z.infer<typeof criticalParameterLimitSchema>;
export type OeeAnalyticsPayload = z.infer<typeof oeeAnalyticsPayloadSchema>;
