const dashboardActivityMonths = [
  "Jan",
  "Feb",
  "Mar",
  "Apr",
  "May",
  "Jun",
  "Jul",
  "Aug",
  "Sep",
  "Oct",
  "Nov",
  "Dec",
];

export const getStatusPercent = (value: number, total: number) =>
  total > 0 ? Math.round((value / total) * 100) : 0;

export const normalizeRoleValue = (value?: string | null) =>
  value?.trim().toLowerCase() ?? "";

export function formatDashboardDate(value?: string | null) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "-";

  const hours = date.getHours();
  const minutes = String(date.getMinutes()).padStart(2, "0");
  const hour12 = hours % 12 || 12;
  const suffix = hours >= 12 ? "PM" : "AM";

  return `${dashboardActivityMonths[date.getMonth()]} ${date.getDate()}, ${hour12}:${minutes} ${suffix}`;
}

export function getTrendScale(values: number[]) {
  const highestValue = Math.max(...values, 0);
  const maxValue = Math.max(5, Math.ceil(highestValue / 5) * 5);
  const tickStep = Math.max(1, Math.ceil(maxValue / 5));

  return {
    maxValue,
    ticks: Array.from({ length: 5 }, (_, index) => maxValue - index * tickStep)
      .filter((tick) => tick > 0),
  };
}
