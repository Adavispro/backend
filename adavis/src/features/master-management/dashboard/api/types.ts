import type { Group } from "../../user-group-management/api/types";
import type { Role } from "../../role-management/api/types";
import type { User } from "../../user-management/api/types";
import type { AuditLog } from "../../audit-logs/api/types";

export interface DashboardTrendPoint {
  label: string;
  value: number;
  bucketStart?: string;
}

export interface TeamActivityItem {
  groupId: string;
  label: string;
  value: number;
  total: number;
}

export interface SystemAdminDashboardData {
  groups: Group[];
  recentAuditLogs: AuditLog[];
  recentUsers: User[];
  roles: Role[];
  teamActivity: TeamActivityItem[];
  loginActivityTrend: DashboardTrendPoint[];
  users: User[];
  userStats: {
    active: number;
    idle: number;
    total: number;
  };
}
