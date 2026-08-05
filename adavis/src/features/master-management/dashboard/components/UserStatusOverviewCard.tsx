import DoughnutChart from "@/components/charts/DoughnutChart";
import type { SystemAdminDashboardData } from "../api";
import { getStatusPercent } from "../utils/dashboard-formatters";
import DashboardPanel from "./DashboardPanel";

export default function UserStatusOverviewCard({
  data,
}: {
  data: SystemAdminDashboardData | null;
}) {
  const stats = data?.userStats ?? {
    active: 0,
    idle: 0,
    total: 0,
  };
  const segments = [
    {
      label: "Active Users",
      value: stats.active,
      displayValue: String(stats.active),
      color: "#2FB1A6",
      gradientTo: "#89D4CD",
      legendOrder: 1,
    },
    {
      label: "Idle Users",
      value: stats.idle,
      displayValue: String(stats.idle),
      color: "#FFB857",
      gradientTo: "#F2D07A",
      legendOrder: 2,
    },
  ];

  return (
    <DashboardPanel title="User Activity Overview">
      <div className="mt-5 flex justify-center">
        <DoughnutChart
          segments={segments}
          centerValue={String(stats.total)}
          centerLabel="users"
          size={112}
          strokeWidth={15}
          gapDegrees={4}
          legendValueSuffix=""
          legendLabelWidth={92}
          centerLabelClassName="type-chart-center-label mt-0.5 capitalize"
        />
      </div>
    </DashboardPanel>
  );
}
