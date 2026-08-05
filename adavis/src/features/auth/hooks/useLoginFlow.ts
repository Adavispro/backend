"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { ApiError } from "@/api";
import { initiateLogin, login } from "../api";
import {
  firstSchemaError,
  loginInitiateRequestSchema,
  loginRequestSchema,
} from "../schemas";
import { ROUTES } from "@/config/routes";

export interface LoginFormValues {
  identifier: string;
  password: string;
}

const emptyNotification = { title: "", message: "" };

export function useLoginFlow() {
  const router = useRouter();
  const verificationRequest = useRef(0);
  const [form, setForm] = useState<LoginFormValues>({
    identifier: "",
    password: "",
  });
  const [displayName, setDisplayName] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isVerifying, setIsVerifying] = useState(false);
  const [verifiedIdentifier, setVerifiedIdentifier] = useState("");
  const [notification, setNotification] = useState(emptyNotification);

  const handleChange = (field: "identifier" | "password") =>
    (event: React.ChangeEvent<HTMLInputElement>) => {
      setNotification(emptyNotification);

      if (field === "identifier") {
        verificationRequest.current += 1;
        setVerifiedIdentifier("");
        setDisplayName("");
        setIsVerifying(false);
        setForm((previous) => ({
          ...previous,
          identifier: event.target.value,
          password: "",
        }));
        return;
      }

      setForm((previous) => ({ ...previous, password: event.target.value }));
    };

  const verifyIdentity = useCallback(async (identifierValue = form.identifier) => {
    const parsedRequest = loginInitiateRequestSchema.safeParse({
      identifier: identifierValue,
    });
    if (!parsedRequest.success) {
      setNotification({
        title: "Invalid user ID",
        message: firstSchemaError(parsedRequest.error),
      });
      return false;
    }

    const { identifier } = parsedRequest.data;
    if (!identifier || identifier === verifiedIdentifier) {
      return Boolean(verifiedIdentifier);
    }

    const requestId = ++verificationRequest.current;
    setIsVerifying(true);
    setNotification(emptyNotification);

    try {
      const user = await initiateLogin({ identifier });
      if (requestId !== verificationRequest.current) return false;

      if (!user.passwordSet) {
        throw new ApiError({
          status: 401,
          message: "A password has not been configured for this account.",
        });
      }

      if (user.status !== "ACTIVE") {
        throw new ApiError({
          status: 403,
          message: "This account is not active. Contact your administrator.",
        });
      }

      setVerifiedIdentifier(identifier);
      setDisplayName(user.fullName?.trim() || user.userId);
      return true;
    } catch (error) {
      if (requestId !== verificationRequest.current) return false;

      setVerifiedIdentifier("");
      setDisplayName("");
      setNotification({
        title: "User verification failed",
        message:
          error instanceof ApiError
            ? error.message
            : "Unable to verify this user. Please try again.",
      });
      return false;
    } finally {
      if (requestId === verificationRequest.current) setIsVerifying(false);
    }
  }, [form.identifier, verifiedIdentifier]);

  useEffect(() => {
    const parsedRequest = loginInitiateRequestSchema.safeParse({
      identifier: form.identifier,
    });

    if (!parsedRequest.success) return;

    const { identifier } = parsedRequest.data;
    if (!identifier || identifier === verifiedIdentifier) return;

    const timeout = window.setTimeout(() => {
      void verifyIdentity(identifier);
    }, 600);

    return () => window.clearTimeout(timeout);
  }, [form.identifier, verifiedIdentifier, verifyIdentity]);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setNotification(emptyNotification);
    setIsLoading(true);

    try {
      const verified = await verifyIdentity();
      if (!verified) return;

      const parsedRequest = loginRequestSchema.safeParse({
        identifier: form.identifier,
        password: form.password,
      });
      if (!parsedRequest.success) {
        setNotification({
          title: "Invalid login details",
          message: firstSchemaError(parsedRequest.error),
        });
        return;
      }

      await login(parsedRequest.data);
      router.push(ROUTES.modules);
      router.refresh();
    } catch (error) {
      setNotification({
        title: "Login failed",
        message:
          error instanceof ApiError
            ? error.message
            : "Unable to log in. Please try again.",
      });
    } finally {
      setIsLoading(false);
    }
  };

  return {
    displayName,
    form,
    isIdentityVerified: Boolean(verifiedIdentifier),
    isLoading,
    isVerifying,
    notification,
    showPassword,
    closeNotification: () => setNotification(emptyNotification),
    handleChange,
    handleSubmit,
    togglePassword: () => setShowPassword((value) => !value),
    verifyIdentity,
  };
}
