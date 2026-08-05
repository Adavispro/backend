"use client";

import Link from "next/link";
import { ArrowRight } from "@phosphor-icons/react";
import { Button, Snackbar, TextField } from "@/components/ui";
import { ROUTES } from "@/config/routes";
import AuthPasswordShell from "../components/password/AuthPasswordShell";
import { useForgotPasswordFlow } from "../hooks/password/useForgotPasswordFlow";

export default function ForgotPasswordScreen() {
  const flow = useForgotPasswordFlow();
  const resetHref = `${ROUTES.resetPassword}?email=${encodeURIComponent(
    flow.email,
  )}&token=${encodeURIComponent(flow.resetToken)}`;

  return (
    <AuthPasswordShell
      title="Forgot Password"
      subtitle="Enter your registered email ID. We will send password reset instructions if the account exists."
    >
      {flow.isSubmitted ? (
        <div className="rounded-[10px] border border-white/70 bg-white/45 p-4 text-[0.72rem] leading-6 text-[#626870] shadow-[0_8px_18px_rgba(20,45,75,0.08)]">
          <p className="font-semibold text-text-heading">
            Reset instructions sent
          </p>
          <p className="mt-1">
            Check your inbox for the password reset link. The token expires
            automatically, so complete the reset soon.
          </p>
          {flow.resetToken ? (
            <Link
              href={resetHref}
              className="mt-4 inline-flex items-center gap-2 text-[0.68rem] font-semibold text-primary hover:underline"
            >
              Continue to reset password
              <ArrowRight size={13} weight="bold" />
            </Link>
          ) : null}
        </div>
      ) : (
        <form onSubmit={flow.handleSubmit} className="grid gap-6">
          <TextField
            id="forgot-email"
            label="EMAIL ID"
            type="email"
            placeholder="Enter your email ID"
            value={flow.email}
            onChange={flow.handleEmailChange}
            autoComplete="email"
            className="gap-2"
            containerClassName="login-input"
            labelClassName="text-[0.53rem] tracking-[0.04em] text-[#22252a]"
            inputClassName="text-left text-[0.68rem] placeholder:text-[0.6rem] placeholder:text-[#747b84]"
            required
            showRequiredIndicator={false}
          />

          <Button
            type="submit"
            fullWidth
            isLoading={flow.isSubmitting}
            paddingY="py-3.5"
            rounded="rounded-md"
            className="min-h-[42px] text-[0.68rem] tracking-wide shadow-[0_2px_4px_rgba(0,61,139,0.24)]"
          >
            Send Reset Link
          </Button>
        </form>
      )}

      <Snackbar
        open={Boolean(flow.notification.message)}
        variant="error"
        title={flow.notification.title}
        message={flow.notification.message}
        onClose={flow.closeNotification}
      />
    </AuthPasswordShell>
  );
}
