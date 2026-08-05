"use client";

import { useState } from "react";
import { CheckCircle, Eye, EyeSlash } from "@phosphor-icons/react";
import { Button, Snackbar, TextField } from "@/components/ui";
import AuthPasswordShell from "../components/password/AuthPasswordShell";
import { useResetPasswordFlow } from "../hooks/password/useResetPasswordFlow";

export default function ResetPasswordScreen() {
  const flow = useResetPasswordFlow();
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  return (
    <AuthPasswordShell
      title="Reset Password"
      subtitle="Enter your email ID, reset token, and new password to complete the password reset."
    >
      {flow.isComplete ? (
        <div className="rounded-[10px] border border-success/20 bg-success/10 p-4 text-[0.72rem] leading-6 text-[#626870]">
          <div className="flex items-center gap-2 font-semibold text-success">
            <CheckCircle size={17} weight="fill" />
            Password reset successful
          </div>
          <p className="mt-1">
            Redirecting you back to login. Use the new password to sign in.
          </p>
        </div>
      ) : (
        <form onSubmit={flow.handleSubmit} className="grid gap-5">
          <TextField
            id="reset-email"
            label="EMAIL ID"
            type="email"
            placeholder="Enter your email ID"
            value={flow.values.email}
            onChange={flow.handleChange("email")}
            error={flow.fieldErrors.email}
            autoComplete="email"
            className="gap-2"
            containerClassName="login-input"
            labelClassName="text-[0.53rem] tracking-[0.04em] text-[#22252a]"
            inputClassName="text-left text-[0.68rem] placeholder:text-[0.6rem] placeholder:text-[#747b84]"
            required
            showRequiredIndicator={false}
          />

          <TextField
            id="reset-token"
            label="RESET TOKEN"
            placeholder="Enter reset token"
            value={flow.values.token}
            onChange={flow.handleChange("token")}
            error={flow.fieldErrors.token}
            className="gap-2"
            containerClassName="login-input"
            labelClassName="text-[0.53rem] tracking-[0.04em] text-[#22252a]"
            inputClassName="text-left text-[0.68rem] placeholder:text-[0.6rem] placeholder:text-[#747b84]"
            required
            showRequiredIndicator={false}
          />

          <div className="grid gap-5 sm:grid-cols-2">
            <TextField
              id="new-password"
              label="NEW PASSWORD"
              type={showPassword ? "text" : "password"}
              placeholder="Enter new password"
              value={flow.values.password}
              onChange={flow.handleChange("password")}
              error={flow.fieldErrors.password}
              autoComplete="new-password"
              className="gap-2"
              containerClassName="login-input"
              labelClassName="text-[0.53rem] tracking-[0.04em] text-[#22252a]"
              inputClassName="text-left text-[0.68rem] placeholder:text-[0.6rem] placeholder:text-[#747b84]"
              required
              showRequiredIndicator={false}
              suffixIcon={
                showPassword ? (
                  <EyeSlash size={16} weight="regular" />
                ) : (
                  <Eye size={16} weight="regular" />
                )
              }
              suffixIconLabel={
                showPassword ? "Hide new password" : "Show new password"
              }
              onSuffixIconClick={() => setShowPassword((value) => !value)}
            />

            <TextField
              id="confirm-password"
              label="CONFIRM PASSWORD"
              type={showConfirmPassword ? "text" : "password"}
              placeholder="Confirm new password"
              value={flow.values.confirmPassword}
              onChange={flow.handleChange("confirmPassword")}
              error={flow.fieldErrors.confirmPassword}
              autoComplete="new-password"
              className="gap-2"
              containerClassName="login-input"
              labelClassName="text-[0.53rem] tracking-[0.04em] text-[#22252a]"
              inputClassName="text-left text-[0.68rem] placeholder:text-[0.6rem] placeholder:text-[#747b84]"
              required
              showRequiredIndicator={false}
              suffixIcon={
                showConfirmPassword ? (
                  <EyeSlash size={16} weight="regular" />
                ) : (
                  <Eye size={16} weight="regular" />
                )
              }
              suffixIconLabel={
                showConfirmPassword
                  ? "Hide confirm password"
                  : "Show confirm password"
              }
              onSuffixIconClick={() =>
                setShowConfirmPassword((value) => !value)
              }
            />
          </div>

          {flow.policyErrors.length > 0 ? (
            <div className="rounded-[8px] border border-danger/20 bg-danger/10 px-3 py-2 text-[0.62rem] leading-5 text-danger">
              <p className="font-semibold">Password requirements not met:</p>
              <ul className="mt-1 list-disc pl-4">
                {flow.policyErrors.map((error) => (
                  <li key={error}>{error}</li>
                ))}
              </ul>
            </div>
          ) : null}

          <Button
            type="submit"
            fullWidth
            disabled={!flow.canSubmit}
            isLoading={flow.isSubmitting}
            paddingY="py-3.5"
            rounded="rounded-md"
            className="min-h-[42px] text-[0.68rem] tracking-wide shadow-[0_2px_4px_rgba(0,61,139,0.24)]"
          >
            Reset Password
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
