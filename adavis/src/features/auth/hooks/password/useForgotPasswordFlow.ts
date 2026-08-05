"use client";

import { useState } from "react";
import { ApiError } from "@/api";
import { forgotPassword } from "../../api";
import { firstSchemaError, forgotPasswordRequestSchema } from "../../schemas";

const emptyNotification = { title: "", message: "" };

export function useForgotPasswordFlow() {
  const [email, setEmail] = useState("");
  const [resetToken, setResetToken] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSubmitted, setIsSubmitted] = useState(false);
  const [notification, setNotification] = useState(emptyNotification);

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setNotification(emptyNotification);

    const parsedRequest = forgotPasswordRequestSchema.safeParse({ email });
    if (!parsedRequest.success) {
      setNotification({
        title: "Invalid email",
        message: firstSchemaError(parsedRequest.error),
      });
      return;
    }

    setIsSubmitting(true);

    try {
      const response = await forgotPassword(parsedRequest.data);
      setResetToken(response.resetToken ?? "");
      setIsSubmitted(true);
    } catch (error) {
      setNotification({
        title: "Unable to send reset link",
        message:
          error instanceof ApiError
            ? error.message
            : "Unable to send password reset instructions. Please try again.",
      });
    } finally {
      setIsSubmitting(false);
    }
  };

  return {
    email,
    isSubmitted,
    isSubmitting,
    notification,
    resetToken,
    closeNotification: () => setNotification(emptyNotification),
    handleEmailChange: (event: React.ChangeEvent<HTMLInputElement>) => {
      setEmail(event.target.value);
      setNotification(emptyNotification);
    },
    handleSubmit,
  };
}
