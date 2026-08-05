"use client";

import { useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ApiError } from "@/api";
import { ROUTES } from "@/config/routes";
import { resetPassword, verifyPasswordPolicy } from "../../api";
import { firstSchemaError, resetPasswordRequestSchema } from "../../schemas";

interface ResetPasswordValues {
  email: string;
  token: string;
  password: string;
  confirmPassword: string;
}

const emptyNotification = { title: "", message: "" };

export function useResetPasswordFlow() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const initialEmail = searchParams.get("email") ?? "";
  const initialToken = searchParams.get("token") ?? "";
  const [values, setValues] = useState<ResetPasswordValues>({
    email: initialEmail,
    token: initialToken,
    password: "",
    confirmPassword: "",
  });
  const [fieldErrors, setFieldErrors] = useState<
    Partial<Record<keyof ResetPasswordValues, string>>
  >({});
  const [policyErrors, setPolicyErrors] = useState<string[]>([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isComplete, setIsComplete] = useState(false);
  const [notification, setNotification] = useState(emptyNotification);

  const canSubmit = useMemo(
    () =>
      Boolean(
        values.email &&
          values.token &&
          values.password &&
          values.confirmPassword,
      ),
    [values],
  );

  const handleChange =
    (field: keyof ResetPasswordValues) =>
    (event: React.ChangeEvent<HTMLInputElement>) => {
      setValues((previous) => ({ ...previous, [field]: event.target.value }));
      setFieldErrors((previous) => ({ ...previous, [field]: undefined }));
      setPolicyErrors([]);
      setNotification(emptyNotification);
    };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setNotification(emptyNotification);
    setPolicyErrors([]);

    if (values.password !== values.confirmPassword) {
      setFieldErrors((previous) => ({
        ...previous,
        confirmPassword: "Passwords do not match.",
      }));
      return;
    }

    const parsedRequest = resetPasswordRequestSchema.safeParse({
      email: values.email,
      token: values.token,
      newPassword: values.password,
    });

    if (!parsedRequest.success) {
      const errors = parsedRequest.error.flatten().fieldErrors;
      setFieldErrors({
        email: errors.email?.[0],
        token: errors.token?.[0],
        password: errors.newPassword?.[0],
      });
      setNotification({
        title: "Invalid reset details",
        message: firstSchemaError(parsedRequest.error),
      });
      return;
    }

    setIsSubmitting(true);

    try {
      const policyResult = await verifyPasswordPolicy({
        password: parsedRequest.data.newPassword,
      });
      const isValid = policyResult.valid ?? policyResult.isValid ?? false;
      const messages = policyResult.errors ?? policyResult.messages ?? [];

      if (!isValid) {
        setPolicyErrors(
          messages.length > 0
            ? messages
            : ["Password does not meet policy requirements."],
        );
        return;
      }

      await resetPassword(parsedRequest.data);
      setIsComplete(true);
      window.setTimeout(() => {
        router.replace(ROUTES.login);
      }, 1200);
    } catch (error) {
      setNotification({
        title: "Unable to reset password",
        message:
          error instanceof ApiError
            ? error.message
            : "Unable to reset your password. Please try again.",
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  return {
    canSubmit,
    fieldErrors,
    isComplete,
    isSubmitting,
    notification,
    policyErrors,
    values,
    closeNotification: () => setNotification(emptyNotification),
    handleChange,
    handleSubmit,
  };
}
