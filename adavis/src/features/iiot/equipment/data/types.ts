export type DetailTab = "parameters" | "trends" | "alarms" | "events";

export type ParameterStatus = "Normal" | "Warning" | "Critical";
export type AlarmSeverity = "Critical" | "Warning";
export type AlarmStatus = "Active" | "Acknowledged";
export type EventSeverity = "Warning" | "Normal";
export type EventType = "Threshold Crossed" | "Status Updated";

export interface ParameterRow {
  observedAtIso: string;
  date: string;
  time: string;
  metricValues: Record<string, string>;
  metricKeys: string[];
  metric: string;
  params: string;
  impellerCurrent: string;
  chopperCurrent: string;
  bedTemperature: string;
  mode: string;
  processStatus: string;
  cycle: string;
  impellerb: string;
  granulationtime: string;
  metricStatuses: Record<string, ParameterStatus>;
  parameterKey: string;
  parameter: string;
  currentValue: string;
  unit: string;
  status: ParameterStatus;
  range: string;
  rangePosition: number;
  lastUpdated: string;
}

export interface AlarmRow {
  id: string;
  occurredAtIso: string;
  date: string;
  metric: string;
  params: string;
  batchNo: string;
  time: string;
  severity: AlarmSeverity;
  alarm: string;
  currentValue: string;
  threshold: string;
  status: AlarmStatus;
  acknowledgedBy: string;
  acknowledgedAt?: string;
  requiresAcknowledge?: boolean;
}

export interface EventRow {
  occurredAtIso: string;
  date: string;
  metric: string;
  params: string;
  batchNo: string;
  time: string;
  eventType: EventType;
  severity: EventSeverity;
  source: string;
  description: string;
  acknowledgedBy: string;
}
