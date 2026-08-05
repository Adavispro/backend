"use client";

import { useEffect } from "react";
import {
  CheckCircle,
  Info,
  Warning,
  WarningCircle,
  X,
} from "@phosphor-icons/react";

export type SnackbarVariant = "error" | "info" | "success" | "warning";

export interface SnackbarProps {
  open: boolean;
  message: string;
  onClose: () => void;
  title?: string;
  variant?: SnackbarVariant;
  duration?: number;
}

const variantStyles: Record<
  SnackbarVariant,
  {
    accent: string;
    icon: string;
    iconBackground: string;
    iconBorder: string;
    title: string;
  }
> = {
  error: {
    accent: "bg-danger",
    icon: "text-danger",
    iconBackground: "bg-danger/10",
    iconBorder: "border-danger/15",
    title: "Error",
  },
  success: {
    accent: "bg-success",
    icon: "text-success",
    iconBackground: "bg-success/10",
    iconBorder: "border-success/15",
    title: "Success",
  },
  warning: {
    accent: "bg-warning",
    icon: "text-warning",
    iconBackground: "bg-warning/10",
    iconBorder: "border-warning/15",
    title: "Warning",
  },
  info: {
    accent: "bg-primary",
    icon: "text-primary",
    iconBackground: "bg-primary/10",
    iconBorder: "border-primary/15",
    title: "Information",
  },
};

const icons = {
  error: WarningCircle,
  success: CheckCircle,
  warning: Warning,
  info: Info,
} as const;

export default function Snackbar({
  open,
  message,
  onClose,
  title,
  variant = "info",
  duration = 5000,
}: SnackbarProps) {
  useEffect(() => {
    if (!open || duration <= 0) return;

    const timeout = window.setTimeout(onClose, duration);
    return () => window.clearTimeout(timeout);
  }, [duration, message, onClose, open]);

  if (!open) return null;

  const style = variantStyles[variant];
  const Icon = icons[variant];

  return (
    <div
      className="module-glass-panel fixed right-5 top-5 z-[160] w-[min(calc(100vw-2.5rem),360px)] animate-[snackbar-in_180ms_ease-out] overflow-hidden rounded-lg shadow-[0_12px_28px_rgba(35,50,70,0.16),0_3px_8px_rgba(6,79,165,0.10)]"
      role={variant === "error" ? "alert" : "status"}
      aria-live={variant === "error" ? "assertive" : "polite"}
      aria-atomic="true"
    >
      <div className="flex items-start gap-2.5 px-3 py-3">
        <span
          className={`mt-px flex size-8 shrink-0 items-center justify-center rounded-[6px] border shadow-[inset_0_1px_0_rgba(255,255,255,0.72)] ${style.iconBackground} ${style.iconBorder} ${style.icon}`}
          aria-hidden="true"
        >
          <Icon size={18} weight="fill" />
        </span>

        <div className="min-w-0 flex-1">
          <p className="type-table-title leading-4">
            {title ?? style.title}
          </p>
          <p className="mt-1 text-[10px] leading-[15px] text-text-secondary">
            {message}
          </p>
        </div>

        <button
          type="button"
          onClick={onClose}
          className="module-glass-control flex size-6 shrink-0 cursor-pointer items-center justify-center rounded-[5px] text-text-tertiary transition-colors hover:border-primary/20 hover:bg-primary/5 hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/40"
          aria-label="Dismiss notification"
        >
          <X size={12} weight="bold" />
        </button>
      </div>

      {duration > 0 ? (
        <span
          className={`block h-[2px] origin-left animate-[snackbar-progress_linear_forwards] opacity-80 ${style.accent}`}
          style={{ animationDuration: `${duration}ms` }}
          aria-hidden="true"
        />
      ) : null}
    </div>
  );
}
