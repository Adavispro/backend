"use client";

import { useCallback, useEffect, useState } from "react";
import { getAllTenants } from "../api";
import type { Tenant } from "../api";

export function useTenants() {
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  const reload = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage("");
    try {
      setTenants(await getAllTenants());
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Unable to load tenants.");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void Promise.resolve().then(reload);
  }, [reload]);

  return {
    clearError: () => setErrorMessage(""),
    errorMessage,
    isLoading,
    reload,
    setTenants,
    tenants,
  };
}
