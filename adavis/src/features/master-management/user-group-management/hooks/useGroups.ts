"use client";

import { useEffect, useState } from "react";
import { ApiError } from "@/api";
import { getAllGroups, getGroupAssignments } from "../api";
import type { Group } from "../api/types";

export function useGroups() {
  const [groups, setGroups] = useState<Group[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const [assignments, setAssignments] = useState<Record<string, { roleIds: string[]; userIds: string[] }>>({});

  useEffect(() => {
    const controller = new AbortController();

    void getAllGroups(controller.signal)
      .then(async (loadedGroups) => {
        setGroups(loadedGroups);
        const entries = await Promise.all(loadedGroups.map(async (group) => [group.groupId, await getGroupAssignments(group.groupId, controller.signal)] as const));
        setAssignments(Object.fromEntries(entries));
      })
      .catch((error) => {
        if (error instanceof DOMException && error.name === "AbortError") return;
        setErrorMessage(
          error instanceof ApiError
            ? error.message
            : "Unable to load user groups. Please try again.",
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });

    return () => controller.abort();
  }, []);

  const replaceGroup = (updatedGroup: Group) => {
    setGroups((currentGroups) =>
      currentGroups.map((group) =>
        group.groupId === updatedGroup.groupId ? updatedGroup : group,
      ),
    );
  };

  const addGroupAssignment = (
    groupId: string,
    assignmentUpdates: { roleIds?: string[]; userIds?: string[] },
  ) => {
    setAssignments((currentAssignments) => {
      const currentGroupAssignments = currentAssignments[groupId] ?? {
        roleIds: [],
        userIds: [],
      };
      const nextRoleIds = assignmentUpdates.roleIds ?? [];
      const nextUserIds = assignmentUpdates.userIds ?? [];

      return {
        ...currentAssignments,
        [groupId]: {
          roleIds: nextRoleIds.length > 0
            ? Array.from(
                new Set([
                  ...currentGroupAssignments.roleIds,
                  ...nextRoleIds,
                ]),
              )
            : currentGroupAssignments.roleIds,
          userIds: nextUserIds.length > 0
            ? Array.from(
                new Set([
                  ...currentGroupAssignments.userIds,
                  ...nextUserIds,
                ]),
              )
            : currentGroupAssignments.userIds,
        },
      };
    });
  };

  const removeGroup = (groupId: string) => {
    setGroups((currentGroups) =>
      currentGroups.filter((group) => group.groupId !== groupId),
    );
  };

  return {
    assignments,
    clearError: () => setErrorMessage(""),
    errorMessage,
    groups,
    isLoading,
    addGroupAssignment,
    removeGroup,
    replaceGroup,
  };
}
