"use client";

import { Snackbar } from "@/components/ui";
import { useSystemAdminDashboard } from "../hooks";
import MetricCards from "./MetricCards";
import RecentUserActivityCard from "./RecentUserActivityCard";
import TeamActivityCard from "./TeamActivityCard";
import UserActivityTrendCard from "./UserActivityTrendCard";
import UserStatusOverviewCard from "./UserStatusOverviewCard";
import UsersByRoleCard from "./UsersByRoleCard";

export default function SystemAdminDashboard() {
  const { clearError, data, errorMessage, isLoading } =
    useSystemAdminDashboard();

  return (
    <section aria-label="System Admin Dashboard" className="grid gap-4">
      <MetricCards data={data} isLoading={isLoading} />

      <div className="grid gap-4 xl:grid-cols-3">
        <UserActivityTrendCard data={data} />
        <UserStatusOverviewCard data={data} />
        <UsersByRoleCard data={data} />
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        <TeamActivityCard activities={data?.teamActivity ?? []} isLoading={isLoading} />
        <RecentUserActivityCard auditLogs={data?.recentAuditLogs ?? []} />
      </div>

      <Snackbar
        open={Boolean(errorMessage)}
        variant="error"
        title="Unable to load dashboard"
        message={errorMessage}
        onClose={clearError}
      />
    </section>
  );
}
