"use client";

import { useEffect, useState } from "react";
import { ApiError } from "@/api";
import { getSystemAdminDashboardData } from "../api";
import type { SystemAdminDashboardData } from "../api";

export function useSystemAdminDashboard() {
  const [data, setData] = useState<SystemAdminDashboardData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    const controller = new AbortController();

    getSystemAdminDashboardData(controller.signal)
      .then((dashboardData) => {
        setData(dashboardData);
        setErrorMessage("");
      })
      .catch((error) => {
        if (error instanceof DOMException && error.name === "AbortError") return;
        setErrorMessage(
          error instanceof ApiError
            ? error.message
            : "Unable to load dashboard data. Please try again.",
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });

    return () => controller.abort();
  }, []);

  return {
    clearError: () => setErrorMessage(""),
    data,
    errorMessage,
    isLoading,
  };
}
