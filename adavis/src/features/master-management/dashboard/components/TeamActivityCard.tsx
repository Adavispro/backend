import type { TeamActivityItem } from "../api";
import DashboardPanel from "./DashboardPanel";

export default function TeamActivityCard({
  activities,
  isLoading,
}: {
  activities: TeamActivityItem[];
  isLoading: boolean;
}) {
  return (
    <DashboardPanel
      title="Team Activity Today"
      subtitle="Live Status of user groups"
      className="min-h-[212px]"
    >
      <div className="mb-3 mt-5 flex min-h-[145px] flex-col justify-between">
        {activities.map((item) => (
          <div
            key={item.groupId}
            className="grid grid-cols-[120px_minmax(0,1fr)_38px] items-center gap-4"
          >
            <span className="type-table-compact truncate text-text-heading" title={item.label}>{item.label}</span>
            <span className="block h-1.5 w-full rounded-full bg-[#D0D5DB]">
              <span
                className="block h-full rounded-full bg-[#128A20]"
                style={{ width: `${item.total ? (item.value / item.total) * 100 : 0}%` }}
              />
            </span>
            <span className="type-table-compact text-text-heading">
              {item.value}/{item.total}
            </span>
          </div>
        ))}
        {!activities.length ? (
          <div className="grid min-h-[145px] place-items-center text-[10px] text-text-secondary">
            {isLoading ? "Loading team activity..." : "No active group assignments found."}
          </div>
        ) : null}
      </div>
    </DashboardPanel>
  );
}
