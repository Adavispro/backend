import type {
  AlarmRow,
  AlarmSeverity,
  AlarmStatus,
  EventRow,
  EventSeverity,
  ParameterRow,
  ParameterStatus,
} from "./types";

export const parameterRows: ParameterRow[] = [
  { observedAtIso: "2026-05-26T10:40:00Z", date: "26 May 2026", time: "10:40 AM", metricValues: { "Impeller Current": "48 A", "Chopper Current": "45 A", "Bed Temperature": "52 °C", "Mode": "Auto", "Status": "Running", "Cycle": "Granulation", "Impellerb": "40 A", "Granulationtime": "32 min" }, metricKeys: ["impellerA", "chopperA", "bedTemp", "mode", "status", "cycle", "impellerb", "granulationtime"], metric: "iiot_cpp", params: "Batch B001-2026", impellerCurrent: "48 A", chopperCurrent: "45 A", bedTemperature: "52 °C", mode: "Auto", processStatus: "Running", cycle: "Granulation", impellerb: "40 A", granulationtime: "32 min", metricStatuses: { "Impeller Current": "Normal", "Chopper Current": "Normal", "Bed Temperature": "Warning", "Mode": "Normal", "Status": "Normal", "Cycle": "Normal", "Impellerb": "Normal", "Granulationtime": "Normal" }, parameterKey: "impellerA", parameter: "All Metrics", currentValue: "-", unit: "-", status: "Warning", range: "-", rangePosition: 54, lastUpdated: "10:40:00 AM" },
];

export const alarmRows: AlarmRow[] = [
  { id: "A1", occurredAtIso: "2026-05-26T10:00:00Z", date: "26 May 2026", metric: "OUTLET_TEMP_HIGH", params: "iiot_alarm_events", batchNo: "B001-2026", time: "26th May,\n10:00 AM", severity: "Critical", alarm: "Outlet Air Temperature High", currentValue: "72.4°C", threshold: "<50°C", status: "Active", acknowledgedBy: "-", acknowledgedAt: "-", requiresAcknowledge: true },
  { id: "A2", occurredAtIso: "2026-05-26T09:56:00Z", date: "26 May 2026", metric: "BED_PRESSURE_HIGH", params: "iiot_alarm_events", batchNo: "B001-2026", time: "26th May,\n09:56 AM", severity: "Critical", alarm: "Bed Pressure High", currentValue: "35 Pa", threshold: "<30 Pa", status: "Active", acknowledgedBy: "-", acknowledgedAt: "-", requiresAcknowledge: true },
  { id: "A3", occurredAtIso: "2026-05-26T09:50:00Z", date: "26 May 2026", metric: "INLET_TEMP_DRIFT", params: "iiot_alarm_events", batchNo: "B001-2026", time: "26th May,\n09:50 AM", severity: "Warning", alarm: "Inlet Air Temp Drift", currentValue: "48.1°C", threshold: "<46°C", status: "Acknowledged", acknowledgedBy: "Neeraj Joshi", acknowledgedAt: "26 May, 09:52 AM", requiresAcknowledge: false },
];

export const eventRows: EventRow[] = [
  { occurredAtIso: "2026-05-26T10:00:00Z", date: "26 May 2026", metric: "OUTLET_TEMP_THRESHOLD", params: "iiot_alarm_events", batchNo: "B001-2026", time: "26th May,\n10:00 AM", eventType: "Threshold Crossed", severity: "Warning", source: "Outlet Air Temperature", description: "Value 72.4°C crossed Threshold", acknowledgedBy: "-" },
  { occurredAtIso: "2026-05-26T10:00:00Z", date: "26 May 2026", metric: "OUTLET_TEMP_THRESHOLD", params: "iiot_alarm_events", batchNo: "B001-2026", time: "26th May,\n10:00 AM", eventType: "Threshold Crossed", severity: "Warning", source: "Outlet Air Temperature", description: "Value 72.4°C crossed Threshold", acknowledgedBy: "-" },
  { occurredAtIso: "2026-05-26T10:00:00Z", date: "26 May 2026", metric: "STATUS_UPDATED", params: "iiot_alarm_events", batchNo: "B001-2026", time: "26th May,\n10:00 AM", eventType: "Status Updated", severity: "Normal", source: "Outlet Air Temperature", description: "Value 72.4°C crossed Threshold", acknowledgedBy: "-" },
  { occurredAtIso: "2026-05-26T10:00:00Z", date: "26 May 2026", metric: "STATUS_UPDATED", params: "iiot_alarm_events", batchNo: "B001-2026", time: "26th May,\n10:00 AM", eventType: "Status Updated", severity: "Normal", source: "Outlet Air Temperature", description: "Value 72.4°C crossed Threshold", acknowledgedBy: "-" },
  { occurredAtIso: "2026-05-26T10:00:00Z", date: "26 May 2026", metric: "STATUS_UPDATED", params: "iiot_alarm_events", batchNo: "B001-2026", time: "26th May,\n10:00 AM", eventType: "Status Updated", severity: "Normal", source: "Outlet Air Temperature", description: "Value 72.4°C crossed Threshold", acknowledgedBy: "-" },
  { occurredAtIso: "2026-05-26T10:00:00Z", date: "26 May 2026", metric: "STATUS_UPDATED", params: "iiot_alarm_events", batchNo: "B001-2026", time: "26th May,\n10:00 AM", eventType: "Status Updated", severity: "Normal", source: "Outlet Air Temperature", description: "Value 72.4°C crossed Threshold", acknowledgedBy: "-" },
];

export const trendPoints = [
  { label: "10:30 AM", value: 15.5 },
  { label: "10:30 AM", value: 17.5 },
  { label: "10:30 AM", value: 19.1 },
  { label: "10:30 AM", value: 18.4 },
  { label: "10:30 AM", value: 17.2 },
  { label: "10:30 AM", value: 15.2 },
  { label: "10:30 AM", value: 12.1 },
  { label: "10:30 AM", value: 14.5 },
  { label: "10:30 AM", value: 16.4 },
  { label: "10:30 AM", value: 16.5 },
  { label: "10:30 AM", value: 16.6 },
  { label: "10:30 AM", value: 16.1 },
  { label: "10:30 AM", value: 15.5 },
  { label: "10:30 AM", value: 15.1 },
];

export const parameterStatusClasses: Record<ParameterStatus, string> = {
  Normal: "bg-[#DFF8EA] text-[#158047]",
  Warning: "bg-[#FFF4D2] text-[#B48205]",
  Critical: "bg-[#FFE2E2] text-[#D43B3B]",
};

export const alarmSeverityClasses: Record<AlarmSeverity, string> = {
  Critical: "bg-[#FFE2E2] text-[#D43B3B]",
  Warning: "bg-[#FFF4D2] text-[#B48205]",
};

export const alarmStatusClasses: Record<AlarmStatus, string> = {
  Active: "bg-[#FFE2E2] text-[#D43B3B]",
  Acknowledged: "bg-[#DFF8EA] text-[#158047]",
};

export const eventSeverityClasses: Record<EventSeverity, string> = {
  Warning: "bg-[#FFF4D2] text-[#B48205]",
  Normal: "bg-[#DFF8EA] text-[#158047]",
};
