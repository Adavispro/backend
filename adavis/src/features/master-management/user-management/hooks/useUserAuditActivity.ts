"use client";

import { useEffect, useMemo, useState } from "react";
import { ApiError } from "@/api";
import { getAuditLogsByUser } from "../../audit-logs/api";
import type { AuditLog } from "../../audit-logs/api";
import type { UserActivityRow } from "../data/user-activity";

export interface UserAuditMetrics {
  activeDays: string;
  lastLogin: string;
  totalActions: string;
  totalLogins: string;
}

const emptyMetrics: UserAuditMetrics = {
  activeDays: "0",
  lastLogin: "-",
  totalActions: "0",
  totalLogins: "0",
};

const getAuditDate = (log?: AuditLog | null) =>
  log?.timestamp ?? log?.createdAt ?? "";

const isLoginAction = (action: string) => /login|authenticate/i.test(action);

const formatDate = (value?: string | null) => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;

  return new Intl.DateTimeFormat("en-GB", {
    day: "2-digit",
    month: "2-digit",
  }).format(date);
};

const formatDateTime = (value?: string | null) => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;

  return new Intl.DateTimeFormat("en-US", {
    hour: "2-digit",
    minute: "2-digit",
    month: "short",
    day: "2-digit",
  }).format(date);
};

const formatAction = (action: string) =>
  action
    .toLowerCase()
    .split(/[_\s-]+/)
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");

const getDescription = (log: AuditLog) => {
  if (typeof log.metadata?.description === "string") {
    return log.metadata.description;
  }

  const entity = log.entity ?? "Record";
  const entityId = log.entityId ?? log.userId ?? log.eventId ?? "";
  return [entity, entityId, formatAction(log.action).toLowerCase()]
    .filter(Boolean)
    .join(" ");
};

const sortByNewest = (logs: AuditLog[]) =>
  [...logs].sort((first, second) => {
    const firstTime = new Date(getAuditDate(first)).getTime();
    const secondTime = new Date(getAuditDate(second)).getTime();
    return (Number.isNaN(secondTime) ? 0 : secondTime) - (Number.isNaN(firstTime) ? 0 : firstTime);
  });

const toActivityRows = (logs: AuditLog[]): UserActivityRow[] =>
  sortByNewest(logs)
    .slice(0, 7)
    .map((log, index) => ({
      id: log.id ?? log._id ?? log.eventId ?? `${log.action}-${index}`,
      date: formatDate(getAuditDate(log)),
      module: log.entity ?? "Audit",
      activity: formatAction(log.action),
      description: getDescription(log),
    }));

const toMetrics = (logs: AuditLog[], totalActions: number): UserAuditMetrics => {
  const validLogs = logs.filter(Boolean);
  const loginLogs = sortByNewest(
    validLogs.filter((log) => isLoginAction(log.action)),
  );
  const thirtyDaysAgo = Date.now() - 30 * 24 * 60 * 60 * 1000;
  const activeDays = new Set(
    validLogs
      .map((log) => new Date(getAuditDate(log)))
      .filter((date) => !Number.isNaN(date.getTime()) && date.getTime() >= thirtyDaysAgo)
      .map((date) => date.toISOString().slice(0, 10)),
  ).size;

  return {
    activeDays: String(activeDays),
    lastLogin: formatDateTime(getAuditDate(loginLogs[0])),
    totalActions: String(totalActions),
    totalLogins: String(loginLogs.length),
  };
};

export function useUserAuditActivity(userId: string) {
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [totalActions, setTotalActions] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    const controller = new AbortController();

    void getAuditLogsByUser(userId, { page: 0, size: 100 }, controller.signal)
      .then((page) => {
        setAuditLogs(page.content.filter(Boolean));
        setTotalActions(page.totalElements);
        setErrorMessage("");
      })
      .catch((error) => {
        if (error instanceof DOMException && error.name === "AbortError") return;
        setAuditLogs([]);
        setTotalActions(0);
        setErrorMessage(
          error instanceof ApiError
            ? error.message
            : "Unable to load user activity.",
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });

    return () => controller.abort();
  }, [userId]);

  const metrics = useMemo(
    () => toMetrics(auditLogs, totalActions),
    [auditLogs, totalActions],
  );

  const activityRows = useMemo(() => toActivityRows(auditLogs), [auditLogs]);

  return {
    activityRows,
    clearError: () => setErrorMessage(""),
    errorMessage,
    isLoading,
    metrics,
  };
}

export { emptyMetrics };
