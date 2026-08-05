"use client";

import { useEffect, useState } from "react";
import { ApiError } from "@/api";
import { getMasterLookupOptions } from "../api";
import type { MasterLookupOptions } from "../api";

const emptyLookupOptions: MasterLookupOptions = {
  departments: [],
  groups: [],
  roles: [],
  users: [],
};

export function useMasterLookups() {
  const [options, setOptions] =
    useState<MasterLookupOptions>(emptyLookupOptions);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    const controller = new AbortController();

    getMasterLookupOptions(controller.signal)
      .then((lookupOptions) => {
        setOptions(lookupOptions);
        setErrorMessage("");
      })
      .catch((error) => {
        if (controller.signal.aborted) return;
        setErrorMessage(
          error instanceof ApiError
            ? error.message
            : "Unable to load master lookup values.",
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });

    return () => controller.abort();
  }, []);

  return { errorMessage, isLoading, options };
}
