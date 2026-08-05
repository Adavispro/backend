"use client";

import { useEffect, useState } from "react";
import { getLoginContext } from "../api";
import type { CurrentUser, LoginContext } from "../api/types";

let loginContextRequest: Promise<LoginContext> | null = null;
const LOGIN_CONTEXT_CHANGED_EVENT = "adavis:login-context-changed";

const loadLoginContext = () => {
  loginContextRequest ??= getLoginContext().catch((error) => {
    loginContextRequest = null;
    throw error;
  });

  return loginContextRequest;
};

export const invalidateLoginContext = () => {
  loginContextRequest = null;
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(LOGIN_CONTEXT_CHANGED_EVENT));
  }
};

export function useLoginContext() {
  const [context, setContext] = useState<LoginContext | null>(null);

  useEffect(() => {
    let active = true;
    const refresh = () => {
      void loadLoginContext()
        .then((value) => {
          if (active) setContext(value);
        })
        .catch(() => {
          if (active) setContext(null);
        });
    };

    refresh();
    window.addEventListener(LOGIN_CONTEXT_CHANGED_EVENT, refresh);

    return () => {
      active = false;
      window.removeEventListener(LOGIN_CONTEXT_CHANGED_EVENT, refresh);
    };
  }, []);

  return context;
}

export function useCurrentUser() {
  const [user, setUser] = useState<CurrentUser | null>(null);

  useEffect(() => {
    let active = true;
    const refresh = () => {
      void loadLoginContext()
        .then((context) => {
          if (active) setUser(context.user);
        })
        .catch(() => {
          if (active) setUser(null);
        });
    };

    refresh();
    window.addEventListener(LOGIN_CONTEXT_CHANGED_EVENT, refresh);

    return () => {
      active = false;
      window.removeEventListener(LOGIN_CONTEXT_CHANGED_EVENT, refresh);
    };
  }, []);

  return user;
}
