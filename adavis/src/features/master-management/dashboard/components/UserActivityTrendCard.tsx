"use client";

import { useMemo, useState } from "react";
import LineChart from "@/components/charts/LineChart";
import type { SystemAdminDashboardData } from "../api";
import { getTrendScale } from "../utils/dashboard-formatters";
import DashboardPanel from "./DashboardPanel";

type TrendMode = "quarterly" | "monthly";

const MODE_WINDOW_SIZE: Record<TrendMode, number> = {
  monthly: 4,
  quarterly: 12,
};

const MODE_LABEL_INTERVAL: Record<TrendMode, number> = {
  monthly: 1,
  quarterly: 3,
};

const selectTrendWindow = (
  points: { label: string; value: number }[],
  mode: TrendMode,
) => {
  const size = MODE_WINDOW_SIZE[mode];
  const windowPoints = points.slice(-size);

  if (windowPoints.length > 0) {
    return windowPoints;
  }

  return Array.from({ length: size }, (_, index) => ({
    label: `W${index + 1}`,
    value: 0,
  }));
};

export default function UserActivityTrendCard({
  data,
}: {
  data: SystemAdminDashboardData | null;
}) {
  const [trendMode, setTrendMode] = useState<TrendMode>("quarterly");
  const basePoints = data?.loginActivityTrend ?? [];

  const chartPoints = useMemo(
    () => selectTrendWindow(basePoints, trendMode),
    [basePoints, trendMode],
  );
  const labelInterval = MODE_LABEL_INTERVAL[trendMode];
  const hasActivity = chartPoints.some((point) => point.value > 0);
  const { maxValue, ticks } = getTrendScale(
    chartPoints.map((point) => point.value),
  );

  return (
    <DashboardPanel
      title="Login Activity Trend"
      headerAction={
        <label className="flex items-center gap-2 text-[9px] font-medium text-text-secondary">
          <span>View</span>
          <select
            value={trendMode}
            onChange={(event) => setTrendMode(event.target.value as TrendMode)}
            aria-label="Select login activity trend view"
            className="module-glass-control h-8 min-w-[112px] rounded-lg border border-line bg-white px-2 text-[10px] font-semibold text-text-heading shadow-[0_4px_10px_rgba(35,50,70,0.08)] outline-none"
          >
            <option value="quarterly">Quarterly</option>
            <option value="monthly">Monthly</option>
          </select>
        </label>
      }
    >
      <div className="mt-4">
        <LineChart
          points={chartPoints}
          maxValue={maxValue}
          ticks={ticks}
          height={150}
          chartWidth={320}
          chartHeight={100}
          labelInterval={labelInterval}
          markerSize="h-1 w-1"
          tooltipValueFormatter={(value) =>
            `${value} ${value === 1 ? "login" : "logins"}`
          }
        />
        {!hasActivity ? (
          <p className="mt-2 text-center text-[9px] font-medium text-text-secondary">
            No login activity found for the selected period.
          </p>
        ) : null}
      </div>
    </DashboardPanel>
  );
}
