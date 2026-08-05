import type { OeeSummaryRow } from "./types";

export const oeeTrendPoints = [
  { label: "10 May", value: 45 },
  { label: "10 May", value: 60 },
  { label: "10 May", value: 82 },
  { label: "10 May", value: 72 },
  { label: "10 May", value: 63 },
  { label: "10 May", value: 36 },
  { label: "10 May", value: 12 },
  { label: "10 May", value: 28 },
  { label: "10 May", value: 50 },
  { label: "10 May", value: 53 },
  { label: "10 May", value: 55 },
  { label: "10 May", value: 47 },
];

export const shiftComparison = [
  { label: "Shift 1", value: 60, color: "#2FB1A6", gradientTo: "#8FD1CA" },
  { label: "Shift 2", value: 50, color: "#7C63D9", gradientTo: "#A893F5" },
  { label: "Shift 3", value: 72, color: "#145AA9", gradientTo: "#4F8FDF" },
];

export const downtimeSegments = [
  { label: "Equipment Failure", value: 32, displayValue: "3.2 hrs", color: "#2FB1A6", gradientTo: "#89D4CD", legendOrder: 1 },
  { label: "Minor Stoppage", value: 25, displayValue: "3.2 hrs", color: "#3F7ED4", gradientTo: "#5A8FE0", legendOrder: 2 },
  { label: "Changeover", value: 12, displayValue: "3.2 hrs", color: "#FF8588", gradientTo: "#EF646E", legendOrder: 3 },
  { label: "Cleaning", value: 23, displayValue: "3.2 hrs", color: "#806BDF", gradientTo: "#B49AF8", legendOrder: 4 },
  { label: "Others", value: 8, displayValue: "3.2 hrs", color: "#9FA3A6", gradientTo: "#B8BBBD", legendOrder: 5 },
];

export const topBreakdownLosses = [
  { label: "Speed Losses", value: 70, color: "#DDA647" },
  { label: "Minor Stops", value: 70, color: "#9D7AF4" },
  { label: "Quality Losses", value: 70, color: "#FF929C" },
  { label: "Startup Losses", value: 70, color: "#5E9DEE" },
];

export const oeeRows: OeeSummaryRow[] = Array.from({ length: 6 }, () => ({
  date: "26/05",
  productName: "Xanax -200mg",
  batchNo: "BN986469SD",
  startTime: "6:00 AM",
  endTime: "7:00 AM",
  runTimePercent: "50%",
  runTimeHrs: "2.5 hrs",
}));
