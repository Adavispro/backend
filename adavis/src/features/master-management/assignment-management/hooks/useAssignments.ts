"use client";

import { useCallback, useEffect, useState } from "react";
import { getAllAssignments } from "../api";
import type { Assignment } from "../api";

export function useAssignments() {
  const [assignments, setAssignments] = useState<Assignment[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const reload = useCallback(async (signal?: AbortSignal) => {
    setIsLoading(true);
    try { setAssignments(await getAllAssignments(signal)); setErrorMessage(""); }
    catch (error) { if (!signal?.aborted) setErrorMessage(error instanceof Error ? error.message : "Unable to load assignments."); }
    finally { if (!signal?.aborted) setIsLoading(false); }
  }, []);
  useEffect(() => { const controller = new AbortController(); void Promise.resolve().then(() => reload(controller.signal)); return () => controller.abort(); }, [reload]);
  return {
    assignments,
    clearError: () => setErrorMessage(""),
    errorMessage,
    isLoading,
    reload,
    replaceAssignment: (updated: Assignment, previousId = updated.assignmentId) =>
      setAssignments((current) => {
        const next = current.filter((item) => item.assignmentId !== previousId && item.assignmentId !== updated.assignmentId);
        return [...next, updated].sort((first, second) => first.assignmentId.localeCompare(second.assignmentId));
      }),
    removeAssignment: (id: string) => setAssignments((current) => current.filter((item) => item.assignmentId !== id)),
    markInactive: (id: string) => setAssignments((current) => current.map((item) => item.assignmentId === id ? { ...item, isActive: false } : item)),
  };
}
