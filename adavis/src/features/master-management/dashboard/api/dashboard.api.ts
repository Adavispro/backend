import { getAllRoles } from "../../role-management/api";
import { getAllGroups } from "../../user-group-management/api";
import { getGroupAssignments } from "../../user-group-management/api";
import { getUsers } from "../../user-management/api";
import type { User } from "../../user-management/api/types";
import type { SystemAdminDashboardData } from "./types";
import { getAuditLogs, getAuditLogsByAction } from "../../audit-logs/api";
import type { AuditLog } from "../../audit-logs/api/types";

const USERS_PAGE_SIZE = 100;
const ACTIVITY_TREND_MONTHS = 12;
const ACTIVITY_LOG_PAGE_SIZE = 100;
const IDLE_LOOKBACK_DAYS = 30;
const DASHBOARD_FETCH_TIMEOUT_MS = 12000;
const FALLBACK_AUDIT_SCAN_PAGES = 12;
const monthLabels = [
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

const getLocalWeekStart = (date: Date) => {
  const value = new Date(date);
  value.setHours(0, 0, 0, 0);

  const day = value.getDay();
  const offset = day === 0 ? -6 : 1 - day;
  value.setDate(value.getDate() + offset);

  return value;
};

const formatWeekLabel = (date: Date) =>
  date.toLocaleDateString("en-US", { month: "short", day: "numeric" });

const getDateKey = (date: Date) =>
  `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`;

const isLoginAction = (value?: string | null) =>
  value?.trim().toUpperCase().includes("LOGIN") ?? false;

const isBetween = (value: Date, start: Date, end: Date) =>
  value.getTime() >= start.getTime() && value.getTime() <= end.getTime();

const getAuditTimestamp = (log: AuditLog) => {
  const raw = log.timestamp ?? log.createdAt;
  if (!raw) return null;

  const date = new Date(raw);
  return Number.isNaN(date.getTime()) ? null : date;
};

const withTimeout = async <T>(
  promise: Promise<T>,
  fallbackValue: T,
  timeoutMs = DASHBOARD_FETCH_TIMEOUT_MS,
): Promise<T> => {
  let timer: ReturnType<typeof setTimeout> | undefined;

  const timeoutPromise = new Promise<T>((resolve) => {
    timer = setTimeout(() => resolve(fallbackValue), timeoutMs);
  });

  try {
    return await Promise.race([promise, timeoutPromise]);
  } finally {
    if (timer) clearTimeout(timer);
  }
};

const getEmptyAuditLogsPage = (pageSize = ACTIVITY_LOG_PAGE_SIZE) => ({
  content: [],
  totalElements: 0,
  totalPages: 0,
  pageNumber: 0,
  pageSize,
  first: true,
  last: true,
  hasNext: false,
  hasPrevious: false,
});

const getLoginLogsFromAuditTrails = async (
  start: Date,
  end: Date,
  signal?: AbortSignal,
) => {
  const loginLogs: AuditLog[] = [];

  for (let page = 0; page < FALLBACK_AUDIT_SCAN_PAGES; page += 1) {
    const auditPage = await withTimeout(
      getAuditLogs({ page, size: ACTIVITY_LOG_PAGE_SIZE }, signal),
      getEmptyAuditLogsPage(),
    );

    if (auditPage.content.length === 0) break;

    auditPage.content.forEach((log) => {
      if (!isLoginAction(log.action)) return;
      const timestamp = getAuditTimestamp(log);
      if (!timestamp) return;
      if (isBetween(timestamp, start, end)) loginLogs.push(log);
    });

    const hasAnyRecentLog = auditPage.content.some((log) => {
      const timestamp = getAuditTimestamp(log);
      return timestamp ? timestamp.getTime() >= start.getTime() : true;
    });

    if (!hasAnyRecentLog) break;
    if (auditPage.last || page >= auditPage.totalPages - 1) break;
  }

  return loginLogs;
};

const getLoginLogsByActionWithFallback = async (
  start: Date,
  end: Date,
  signal?: AbortSignal,
) => {
  const firstPage = await withTimeout(
    getAuditLogsByAction(
      {
        action: "LOGIN",
        page: 0,
        size: ACTIVITY_LOG_PAGE_SIZE,
      },
      signal,
    ).catch(() => getEmptyAuditLogsPage()),
    getEmptyAuditLogsPage(),
  );

  const loginLogs = [...firstPage.content];

  if (firstPage.totalPages > 1) {
    const remainingPages = await Promise.all(
      Array.from({ length: firstPage.totalPages - 1 }, (_, index) =>
        getAuditLogsByAction(
          {
            action: "LOGIN",
            page: index + 1,
            size: ACTIVITY_LOG_PAGE_SIZE,
          },
          signal,
        ).catch(() => getEmptyAuditLogsPage()),
      ),
    );

    loginLogs.push(...remainingPages.flatMap((page) => page.content));
  }

  if (loginLogs.length > 0) {
    return loginLogs.filter((log) => {
      const timestamp = getAuditTimestamp(log);
      return timestamp ? isBetween(timestamp, start, end) : false;
    });
  }

  return getLoginLogsFromAuditTrails(start, end, signal);
};

const getAllUsers = async (signal?: AbortSignal): Promise<User[]> => {
  const firstPage = await getUsers({ page: 0, size: USERS_PAGE_SIZE }, signal);

  if (firstPage.totalPages <= 1) return firstPage.content;

  const remainingPages = await Promise.all(
    Array.from({ length: firstPage.totalPages - 1 }, (_, index) =>
      getUsers({ page: index + 1, size: USERS_PAGE_SIZE }, signal),
    ),
  );

  return [
    ...firstPage.content,
    ...remainingPages.flatMap((page) => page.content),
  ];
};

const getUserStats = (users: User[], idleUserIds: Set<string>) => {
  const active = users.filter((user) => user.isActive && !user.isBlocked).length;

  return {
    active,
    idle: users.filter(
      (user) =>
        user.isActive &&
        !user.isBlocked &&
        !user.isDeleted &&
        !idleUserIds.has(user.userId),
    ).length,
    total: users.filter((user) => !user.isDeleted).length,
  };
};

const getUserCreationTime = (user: User) => {
  const time = new Date(user.createdAt).getTime();
  return Number.isNaN(time) ? 0 : time;
};

const getRecentUsers = (users: User[]) =>
  [...users]
    .sort(
      (first, second) =>
        getUserCreationTime(second) - getUserCreationTime(first),
    )
    .slice(0, 5);

const getTeamActivity = async (
  users: User[],
  groups: Awaited<ReturnType<typeof getAllGroups>>,
  signal?: AbortSignal,
) => {
  const activeUsers = new Set(
    users
      .filter((user) => user.isActive && !user.isBlocked && !user.isDeleted)
      .map((user) => user.userId),
  );
  const activeGroups = groups.filter((group) => group.isActive);
  const assignments = await Promise.allSettled(
    activeGroups.map((group) => getGroupAssignments(group.groupId, signal)),
  );

  return activeGroups
    .map((group, index) => {
      const result = assignments[index];
      const userIds = result.status === "fulfilled"
        ? [...new Set(result.value.userIds)]
        : [];

      return {
        groupId: group.groupId,
        label: group.groupName || group.name || group.groupCode || group.groupId,
        total: userIds.length,
        value: userIds.filter((userId) => activeUsers.has(userId)).length,
      };
    })
    .sort((first, second) => second.total - first.total || first.label.localeCompare(second.label))
    .slice(0, 4);
};

const getMonthWindow = () => {
  const end = new Date();
  const start = new Date(end.getFullYear(), end.getMonth() - (ACTIVITY_TREND_MONTHS - 1), 1);

  return { start, end };
};

const getLoginActivityTrend = async (signal?: AbortSignal) => {
  const { start, end } = getMonthWindow();
  const loginLogs = await getLoginLogsByActionWithFallback(start, end, signal);

  const buckets: Array<{
    key: string;
    label: string;
    value: number;
    bucketStart: Date;
  }> = [];
  const bucketLookup = new Map<string, number>();
  const weekCursor = getLocalWeekStart(start);
  const lastWeekStart = getLocalWeekStart(end);

  for (
    let cursor = new Date(weekCursor);
    cursor <= lastWeekStart;
    cursor.setDate(cursor.getDate() + 7)
  ) {
    const key = getDateKey(cursor);
    buckets.push({
      key,
      label: formatWeekLabel(cursor),
      value: 0,
      bucketStart: new Date(cursor),
    });
    bucketLookup.set(key, 0);
  }

  loginLogs.forEach((log) => {
    const timestamp = getAuditTimestamp(log);
    if (!timestamp) return;

    const date = timestamp;
    const key = getDateKey(getLocalWeekStart(date));
    if (!bucketLookup.has(key)) return;

    bucketLookup.set(key, (bucketLookup.get(key) ?? 0) + 1);
  });

  return buckets.map((bucket) => ({
    label: bucket.label,
    value: bucketLookup.get(bucket.key) ?? 0,
    bucketStart: bucket.bucketStart.toISOString(),
  }));
};

export const getSystemAdminDashboardData = async (
  signal?: AbortSignal,
): Promise<SystemAdminDashboardData> => {
  const [usersResult, rolesResult, groupsResult, auditResult] = await Promise.allSettled([
    withTimeout(getAllUsers(signal), [] as User[]),
    withTimeout(getAllRoles(signal), []),
    withTimeout(getAllGroups(signal), []),
    withTimeout(getAuditLogs({ page: 0, size: 5 }, signal), getEmptyAuditLogsPage(5)),
  ]);

  const users = usersResult.status === "fulfilled" ? usersResult.value : [];
  const roles = rolesResult.status === "fulfilled" ? rolesResult.value : [];
  const groups = groupsResult.status === "fulfilled" ? groupsResult.value : [];
  const auditPage = auditResult.status === "fulfilled"
    ? auditResult.value
    : getEmptyAuditLogsPage(5);

  const { start: idleStart, end: idleEnd } = (() => {
    const end = new Date();
    const start = new Date(end);
    start.setDate(start.getDate() - IDLE_LOOKBACK_DAYS);
    return { start, end };
  })();
  const loginWindowPromise = getLoginLogsByActionWithFallback(
    idleStart,
    idleEnd,
    signal,
  );
  const trendPromise = getLoginActivityTrend(signal);

  const recentLoginLogs = await loginWindowPromise;
  const recentLoginUsers = new Set(
    recentLoginLogs
      .map((log) => log.userId)
      .filter((userId): userId is string => Boolean(userId)),
  );
  const teamActivity = await withTimeout(
    getTeamActivity(users, groups, signal).catch(() => []),
    [],
  );
  const loginActivityTrend = await withTimeout(trendPromise.catch(() => []), []);

  return {
    groups,
    recentAuditLogs: auditPage.content.slice(0, 5),
    recentUsers: getRecentUsers(users),
    roles,
    teamActivity,
    loginActivityTrend,
    users,
    userStats: getUserStats(users, recentLoginUsers),
  };
};
