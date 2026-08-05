"use client";

import Image from "next/image";
import {
  CheckCircle,
  CircleNotch,
  Eye,
  EyeSlash,
} from "@phosphor-icons/react";
import loginIcon from "@/assets/auth/login-icon.svg";
import logoFull from "@/assets/logo/logo-full.svg";
import { Button, TextField } from "@/components/ui";
import type { LoginFormValues } from "../hooks/useLoginFlow";

interface LoginRightPanelProps {
  displayName: string;
  form: LoginFormValues;
  showPassword: boolean;
  isLoading: boolean;
  isVerifying: boolean;
  isIdentityVerified: boolean;
  onFieldChange: (
    field: "identifier" | "password",
  ) => (event: React.ChangeEvent<HTMLInputElement>) => void;
  onIdentifierBlur: () => void;
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
  onTogglePassword: () => void;
}

export default function LoginRightPanel({
  displayName,
  form,
  showPassword,
  isLoading,
  isVerifying,
  isIdentityVerified,
  onFieldChange,
  onIdentifierBlur,
  onSubmit,
  onTogglePassword,
}: LoginRightPanelProps) {
  return (
    <section className="flex w-full justify-center lg:justify-end">
      <div className="login-card flex w-full max-w-[620px] flex-col rounded-[14px] px-7 py-8 sm:px-11 sm:py-10 lg:min-h-[455px] lg:px-11 lg:py-11">
        <div className="mb-7 flex justify-center lg:hidden">
          <Image
            src={logoFull}
            alt="ADAVIS"
            width={170}
            height={72}
            priority
          />
        </div>

        <div className="mb-8 shrink-0">
          <h2 className="mb-2 flex flex-wrap items-baseline gap-x-2 text-[1.55rem] font-semibold tracking-[0.01em] text-[#0759b5]">
            <span>WELCOME</span>
            {displayName ? (
              <span className="max-w-full truncate text-[1.55rem] font-semibold uppercase text-[#0759b5]">
                {displayName}
              </span>
            ) : null}
          </h2>
          <p className="text-[0.8rem] text-[#626870]">
            Log in to access your account
          </p>
        </div>

        <form onSubmit={onSubmit} className="flex flex-1 flex-col justify-between gap-6">
          <div>
            <TextField
              id="user-id"
              label="USER ID"
              type="text"
              placeholder="Enter your user ID"
              value={form.identifier}
              onChange={onFieldChange("identifier")}
              onBlur={onIdentifierBlur}
              autoComplete="username"
              className="gap-2"
              containerClassName="login-input"
              labelClassName="text-[0.53rem] tracking-[0.04em] text-[#22252a]"
              inputClassName="text-left text-[0.68rem] placeholder:text-[0.6rem] placeholder:text-[#747b84]"
              required
              showRequiredIndicator={false}
            />

            <div className="mt-3 min-h-[18px]">
              {isVerifying ? (
                <div className="flex items-center gap-2 text-[0.62rem] font-medium tracking-[0.02em] text-[#626870]">
                  <CircleNotch
                    className="animate-spin text-primary"
                    size={16}
                    weight="bold"
                  />
                  <span>Checking User...</span>
                </div>
              ) : isIdentityVerified && displayName ? (
                <div className="flex items-center gap-2 text-[0.58rem] font-medium uppercase tracking-[0.05em] text-[#626870]">
                  <CheckCircle
                    className="text-success"
                    size={16}
                    weight="fill"
                  />
                  <span>{displayName}</span>
                </div>
              ) : null}
            </div>
          </div>

          <TextField
            id="password"
            label="PASSWORD"
            type={showPassword ? "text" : "password"}
            placeholder="Enter your password"
            value={form.password}
            onChange={onFieldChange("password")}
            autoComplete="current-password"
            disabled={!isIdentityVerified || isVerifying}
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
            suffixIconLabel={showPassword ? "Hide password" : "Show password"}
            onSuffixIconClick={
              isIdentityVerified && !isVerifying ? onTogglePassword : undefined
            }
          />

          <Button
            type="submit"
            fullWidth
            isLoading={isLoading}
            disabled={!isIdentityVerified || isVerifying}
            suffixIcon={<Image src={loginIcon} alt="" width={14} height={14} />}
            paddingY="py-3.5"
            gap="gap-3"
            rounded="rounded-md"
            className="mt-2 min-h-[42px] text-[0.68rem] tracking-wide shadow-[0_2px_4px_rgba(0,61,139,0.24)]"
          >
            Login
          </Button>

        </form>
      </div>
    </section>
  );
}
