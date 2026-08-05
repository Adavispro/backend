"use client";

import { useCallback, useEffect, useState } from "react";
import { ApiError } from "@/api";
import { getTenantLicense } from "../api";
import type { TenantLicense } from "../api";

export function useTenantLicense(tenantId?: string | null) {
  const [license, setLicense] = useState<TenantLicense | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  const reload = useCallback(async () => {
    if (!tenantId) {
      setIsLoading(false);
      setLicense(null);
      return;
    }

    setIsLoading(true);
    setErrorMessage("");
    try {
      setLicense(await getTenantLicense(tenantId));
    } catch (error) {
      if (error instanceof ApiError && error.status === 404) {
        setLicense(null);
      } else {
        setErrorMessage(
          error instanceof Error ? error.message : "Unable to load the tenant license.",
        );
      }
    } finally {
      setIsLoading(false);
    }
  }, [tenantId]);

  useEffect(() => {
    void Promise.resolve().then(reload);
  }, [reload]);

  return {
    clearError: () => setErrorMessage(""),
    errorMessage,
    isLoading,
    license,
    reload,
    setLicense,
  };
}
