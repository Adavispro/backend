"use client";

import { useEffect, useState } from "react";
import { ApiError } from "@/api";
import { getAllDepartments } from "../api";
import type { Department } from "../api/types";

export function useDepartments() {
  const [departments, setDepartments] = useState<Department[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    const controller = new AbortController();

    void getAllDepartments(controller.signal)
      .then(setDepartments)
      .catch((error) => {
        if (error instanceof DOMException && error.name === "AbortError") return;
        setErrorMessage(
          error instanceof ApiError
            ? error.message
            : "Unable to load departments. Please try again.",
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });

    return () => controller.abort();
  }, []);

  const replaceDepartment = (updatedDepartment: Department) => {
    setDepartments((currentDepartments) =>
      currentDepartments.map((department) =>
        department.departmentId === updatedDepartment.departmentId
          ? updatedDepartment
          : department,
      ),
    );
  };

  const removeDepartment = (departmentId: string) => {
    setDepartments((currentDepartments) =>
      currentDepartments.filter(
        (department) => department.departmentId !== departmentId,
      ),
    );
  };

  return {
    clearError: () => setErrorMessage(""),
    departments,
    errorMessage,
    isLoading,
    removeDepartment,
    replaceDepartment,
  };
}
