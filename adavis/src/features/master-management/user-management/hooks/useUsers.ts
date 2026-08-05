"use client";

import { useEffect, useState } from "react";
import { ApiError } from "@/api";
import { getUsers } from "../api";
import type { User, UsersPage } from "../api/types";
import type { UsersListQuery } from "../api/types";

const PAGE_SIZE = 20;

export function useUsers({
  pageSize = PAGE_SIZE,
  filters = {},
}: {
  pageSize?: number;
  filters?: Pick<UsersListQuery, "isActive" | "isBlocked" | "lifecycleStatus">;
} = {}) {
  const { isActive, isBlocked, lifecycleStatus } = filters;
  const [page, setPage] = useState(0);
  const [usersPage, setUsersPage] = useState<UsersPage | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    const controller = new AbortController();

    void getUsers({ page, size: pageSize, isActive, isBlocked, lifecycleStatus }, controller.signal)
      .then(setUsersPage)
      .catch((error) => {
        if (error instanceof DOMException && error.name === "AbortError") return;
        setErrorMessage(
          error instanceof ApiError
            ? error.message
            : "Unable to load users. Please try again.",
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });

    return () => controller.abort();
  }, [isActive, isBlocked, lifecycleStatus, page, pageSize]);

  const changePage = (nextPage: number) => {
    setIsLoading(true);
    setErrorMessage("");
    setPage(nextPage);
  };

  const removeUser = (userId: string) => {
    setUsersPage((currentPage) => {
      if (!currentPage) return currentPage;

      const totalElements = Math.max(currentPage.totalElements - 1, 0);
      return {
        ...currentPage,
        content: currentPage.content.filter((user) => user.userId !== userId),
        totalElements,
        totalPages: Math.ceil(totalElements / currentPage.pageSize),
      };
    });
  };

  const replaceUser = (updatedUser: User) => {
    setUsersPage((currentPage) => {
      if (!currentPage) return currentPage;

      return {
        ...currentPage,
        content: currentPage.content.map((user) =>
          user.userId === updatedUser.userId ? updatedUser : user,
        ),
      };
    });
  };

  return {
    errorMessage,
    isLoading,
    page,
    usersPage,
    clearError: () => setErrorMessage(""),
    setPage: changePage,
    removeUser,
    replaceUser,
  };
}
