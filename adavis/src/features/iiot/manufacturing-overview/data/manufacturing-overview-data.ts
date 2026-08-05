export const productionTrend = [
  { label: "Jan", value: 68 },
  { label: "Feb", value: 76 },
  { label: "Mar", value: 72 },
  { label: "Apr", value: 66 },
  { label: "May", value: 52 },
  { label: "Jun", value: 63 },
  { label: "Jul", value: 70 },
  { label: "Aug", value: 70 },
  { label: "Sep", value: 68 },
  { label: "Oct", value: 64 },
  { label: "Nov", value: 62 },
  { label: "Dec", value: 60 },
];

export const plantScheduleAdherence = [
  { label: "Adavis Plant I", value: 92.5 },
  { label: "Adavis Plant II", value: 87.8 },
  { label: "Adavis Plant III", value: 83.2 },
  { label: "Adavis Plant IV", value: 81.4 },
  { label: "Adavis Plant V", value: 78.9 },
];

export interface PlantPerformanceRow {
  site: string;
  location: string;
  productionOutput: string;
  batchesCompleted: number;
  qualitySuccessRate: string;
  status: "Operational" | "Attention";
}

export const plantPerformanceRows: PlantPerformanceRow[] = [
  {
    site: "Adavis Plant I",
    location: "Bengaluru",
    productionOutput: "250",
    batchesCompleted: 48,
    qualitySuccessRate: "94%",
    status: "Operational",
  },
  {
    site: "Adavis Plant II",
    location: "Chandigarh",
    productionOutput: "260",
    batchesCompleted: 45,
    qualitySuccessRate: "89%",
    status: "Operational",
  },
  {
    site: "Adavis Plant III",
    location: "Hyderabad",
    productionOutput: "230",
    batchesCompleted: 44,
    qualitySuccessRate: "88%",
    status: "Operational",
  },
  {
    site: "Adavis Plant IV",
    location: "Bengaluru",
    productionOutput: "250",
    batchesCompleted: 46,
    qualitySuccessRate: "82%",
    status: "Operational",
  },
  {
    site: "Adavis Plant V",
    location: "Chandigarh",
    productionOutput: "260",
    batchesCompleted: 45,
    qualitySuccessRate: "83%",
    status: "Operational",
  },
];
