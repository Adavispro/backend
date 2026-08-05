export type AnalyticsValues = Record<string, string>;

export interface OeeSummaryRow {
  date: string;
  productName: string;
  batchNo: string;
  startTime: string;
  endTime: string;
  runTimePercent: string;
  runTimeHrs: string;
}

export interface OeeMetrics {
  overallOee: number;
  availability: number;
  performance: number;
  quality: number;
  trendDelta: number;
}

export interface OeeTrendPoint {
  label: string;
  value: number;
}

export interface OeeDowntimeSegment {
  label: string;
  value: number;
  color: string;
  gradientTo: string;
  displayValue: string;
  legendOrder: number;
}

export interface OeeShiftComparisonItem {
  label: string;
  value: number;
  color: string;
  gradientTo: string;
}

export interface OeeTopBreakdownLoss {
  label: string;
  value: number;
  color: string;
}

export interface OeeAnalyticsPayload {
  metrics: OeeMetrics;
  trendPoints: OeeTrendPoint[];
  downtimeSegments: OeeDowntimeSegment[];
  shiftComparison: OeeShiftComparisonItem[];
  topBreakdownLosses: OeeTopBreakdownLoss[];
  summaryRows: OeeSummaryRow[];
}
