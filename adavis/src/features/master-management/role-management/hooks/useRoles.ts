"use client";

import { useEffect, useState } from "react";
import { ApiError } from "@/api";
import { getAllRoles, getPermissionMatrix, getRolePermissions } from "../api";
import type { PermissionMatrix, Role, RolePermission } from "../api/types";

export function useRoles() {
  const [roles, setRoles] = useState<Role[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const [matrix, setMatrix] = useState<PermissionMatrix>({ modules: [] });
  const [permissions, setPermissions] = useState<Record<string, RolePermission[]>>({});

  useEffect(() => {
    const controller = new AbortController();

    void Promise.all([getAllRoles(controller.signal), getPermissionMatrix(controller.signal)])
      .then(async ([loadedRoles, loadedMatrix]) => {
        setRoles(loadedRoles);
        setMatrix(loadedMatrix);
        const entries = await Promise.all(loadedRoles.map(async (role) => {
          try {
            return [role.roleId, await getRolePermissions(role.roleId, controller.signal)] as const;
          } catch {
            return [role.roleId, []] as const;
          }
        }));
        setPermissions(Object.fromEntries(entries));
      })
      .catch((error) => {
        if (error instanceof DOMException && error.name === "AbortError") return;
        setErrorMessage(
          error instanceof ApiError
            ? error.message
            : "Unable to load roles. Please try again.",
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });

    return () => controller.abort();
  }, []);

  return {
    clearError: () => setErrorMessage(""),
    errorMessage,
    isLoading,
    matrix,
    permissions,
    replaceRole: (updatedRole: Role) =>
      setRoles((current) =>
        current.map((role) =>
          role.roleId === updatedRole.roleId ? updatedRole : role,
        ),
      ),
    roles,
  };
}
