"use client";

import { useEffect, useState } from "react";
import { ApiError } from "@/api";
import { getUser } from "../api";
import type { User } from "../api/types";

export function useUser(userId: string) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    const controller = new AbortController();

    void getUser(userId, controller.signal)
      .then(setUser)
      .catch((error) => {
        if (error instanceof DOMException && error.name === "AbortError") return;
        setErrorMessage(
          error instanceof ApiError
            ? error.message
            : "Unable to load user. Please try again.",
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });

    return () => controller.abort();
  }, [userId]);

  return {
    errorMessage,
    isLoading,
    user,
    clearError: () => setErrorMessage(""),
  };
}
