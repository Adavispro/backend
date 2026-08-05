import { ROUTES } from "@/config/routes";
import totalUsersIcon from "@/assets/status/Users.svg";
import userGroupsIcon from "@/assets/status/groups.svg";
import activeUsersIcon from "@/assets/status/running.svg";
import userRolesIcon from "@/assets/status/roles.svg";
import idleUsersIcon from "@/assets/status/warning.svg";
import type { SystemAdminDashboardData } from "../api";
import MetricCard from "./MetricCard";

export default function MetricCards({
  data,
  isLoading,
}: {
  data: SystemAdminDashboardData | null;
  isLoading: boolean;
}) {
  const loadingValue = isLoading ? "..." : "0";
  const stats = data?.userStats;
  const totalUsers = stats?.total ?? 0;
  const activeUserPercent = totalUsers > 0 ? Math.round(((stats?.active ?? 0) / totalUsers) * 100) : 0;
  const idleUserPercent = totalUsers > 0 ? Math.round(((stats?.idle ?? 0) / totalUsers) * 100) : 0;
  const activeRoles = data?.roles.filter((role) => role.isActive).length ?? 0;
  const activeGroups =
    data?.groups.filter((group) => group.isActive).length ?? 0;
  const metricNote = (note: string) =>
    isLoading ? "Loading latest data" : note;
  const cards = [
    {
      label: "Total Registered Users",
      value: data ? String(data.userStats.total) : loadingValue,
      note: metricNote(`${activeUserPercent}% active accounts`),
      icon: totalUsersIcon,
      variant: "primary",
      href: ROUTES.masterUsers,
    },
    {
      label: "Active Logged-in Users",
      value: data ? String(data.userStats.active) : loadingValue,
      note: metricNote(`${activeUserPercent}% of all registered users`),
      icon: activeUsersIcon,
      variant: "green",
      href: ROUTES.masterActiveUsers,
    },
    {
      label: "Idle Users",
      value: data ? String(data.userStats.idle) : loadingValue,
      note: metricNote(`${idleUserPercent}% of all registered users`),
      icon: idleUsersIcon,
      variant: "yellow",
      href: ROUTES.masterIdleUsers,
    },
    // {
    //   label: "Blocked Users",
    //   value: data ? String(data.userStats.blocked) : loadingValue,
    //   note: metricNote(`${blockedUserPercent}% of total users`),
    //   icon: blockedUsersIcon,
    //   variant: "yellow",
    //   href: ROUTES.masterBlockedUsers,
    // },
    // {
    //   label: "Deactivated Users",
    //   value: data ? String(data.userStats.deactivated) : loadingValue,
    //   note: metricNote(`${deactivatedUserPercent}% of total users`),
    //   icon: deactivatedUsersIcon,
    //   variant: "red",
    //   href: ROUTES.masterDeactivatedUsers,
    // },
    {
      label: "Configured Roles",
      value: data ? String(data.roles.length) : loadingValue,
      note: metricNote(`${activeRoles} active roles`),
      icon: userRolesIcon,
      variant: "blue",
      href: ROUTES.masterRoles,
    },
    {
      label: "Configured Groups",
      value: data ? String(data.groups.length) : loadingValue,
      note: metricNote(`${activeGroups} active groups`),
      icon: userGroupsIcon,
      variant: "purple",
      href: ROUTES.masterUserGroups,
    },
  ] as const;

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
      {cards.map((card) => (
        <MetricCard key={card.label} {...card} />
      ))}
    </div>
  );
}
