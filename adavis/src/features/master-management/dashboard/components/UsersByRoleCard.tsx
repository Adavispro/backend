import BarChart from "@/components/charts/BarChart";
import type { SystemAdminDashboardData } from "../api";
import { normalizeRoleValue } from "../utils/dashboard-formatters";
import DashboardPanel from "./DashboardPanel";

const usersByRoleColors = [
  { color: "#2FB1A6", gradientTo: "#8FD1CA" },
  { color: "#D8B852", gradientTo: "#F3D78A" },
  { color: "#EF6A70", gradientTo: "#FF9A9B" },
  { color: "#6F97D6", gradientTo: "#A9C0EA" },
  { color: "#8E9194", gradientTo: "#D4D4D4" },
];

function getUsersByRoleChartItems(data: SystemAdminDashboardData | null) {
  if (!data) return [];

  const roleCounts = data.roles
    .map((role) => {
      const roleDisplayName =
        role.roleName || role.name || role.roleCode || role.roleId;
      const roleName = normalizeRoleValue(role.name);
      const roleDisplay = normalizeRoleValue(roleDisplayName);
      const roleId = normalizeRoleValue(role.roleId);
      const roleCode = normalizeRoleValue(role.roleCode);
      const value = data.users.filter((user) => {
        const designation = normalizeRoleValue(user.designation);
        return (
          designation === roleName ||
          designation === roleDisplay ||
          designation === roleCode ||
          designation === roleId
        );
      }).length;

      return {
        label: roleDisplayName,
        value,
      };
    })
    .sort((first, second) => {
      if (second.value !== first.value) return second.value - first.value;
      return first.label.localeCompare(second.label);
    });

  const topRoles = roleCounts.slice(0, 4);
  const remainingRoles = roleCounts.slice(4);
  const groupedRoles =
    remainingRoles.length > 0
      ? [
          ...topRoles,
          {
            label: "Others",
            value: remainingRoles.reduce((total, role) => total + role.value, 0),
          },
        ]
      : topRoles;

  return groupedRoles.map((item, index) => ({
    ...item,
    color: usersByRoleColors[index]?.color ?? "#6F97D6",
    gradientTo: usersByRoleColors[index]?.gradientTo ?? "#A9C0EA",
  }));
}

export default function UsersByRoleCard({
  data,
  className = "",
}: {
  data: SystemAdminDashboardData | null;
  className?: string;
}) {
  const items = getUsersByRoleChartItems(data);

  return (
    <DashboardPanel title="Users By Role" className={className}>
      <div className="mt-5">
        <BarChart items={items} height={150} />
      </div>
    </DashboardPanel>
  );
}
