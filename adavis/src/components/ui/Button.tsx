"use client";

import React from "react";

// ─── Types ──────────────────────────────────────────────────────────────────

export type ButtonVariant = "primary" | "secondary" | "ghost" | "danger" | "warning";
export type ButtonSize    = "sm" | "md" | "lg";

export interface ButtonProps
  extends Omit<React.ButtonHTMLAttributes<HTMLButtonElement>, "className"> {
  children: React.ReactNode;

  /** Icon placed before the label */
  prefixIcon?: React.ReactNode;
  /** Icon placed after the label */
  suffixIcon?: React.ReactNode;

  /** Visual variant (default: "primary") */
  variant?: ButtonVariant;
  /** Predefined size preset (default: "md") */
  size?: ButtonSize;

  /** Stretch to fill parent width */
  fullWidth?: boolean;
  /** Replaces content with a spinner and disables the button */
  isLoading?: boolean;

  /* ── Style overrides (accept Tailwind utility strings) ── */
  /** Extra Tailwind classes for the root <button> */
  className?: string;
  /** Override font-size, e.g. "text-xs" or "text-lg" */
  textSize?: string;
  /** Override font-weight, e.g. "font-medium" */
  fontWeight?: string;
  /** Override horizontal padding, e.g. "px-8" */
  paddingX?: string;
  /** Override vertical padding, e.g. "py-4" */
  paddingY?: string;
  /** Override gap between icon and label, e.g. "gap-3" */
  gap?: string;
  /** Override border-radius, e.g. "rounded-full" */
  rounded?: string;
}

// ─── Variant styles ──────────────────────────────────────────────────────────

const variantClasses: Record<ButtonVariant, string> = {
  primary:
    "bg-primary text-white hover:bg-primary-hover active:bg-primary disabled:bg-primary/50",
  secondary:
    "bg-primary-light text-primary border border-primary hover:bg-primary hover:text-white active:bg-primary disabled:opacity-50",
  ghost:
    "bg-transparent text-text-primary border border-line hover:bg-surface-muted active:bg-line disabled:opacity-50",
  danger:
    "bg-danger text-white hover:bg-red-700 active:bg-red-800 disabled:opacity-50",
  warning:
    "bg-warning text-white hover:bg-yellow-700 active:bg-yellow-800 disabled:opacity-50",
};

// ─── Size presets ────────────────────────────────────────────────────────────

const sizePresets: Record<ButtonSize, { px: string; py: string; text: string; gap: string }> = {
  sm: { px: "px-4",  py: "py-1.5", text: "text-xs",  gap: "gap-1.5" },
  md: { px: "px-5",  py: "py-2.5", text: "text-sm",  gap: "gap-2"   },
  lg: { px: "px-7",  py: "py-3",   text: "text-base", gap: "gap-2.5" },
};

// ─── Spinner ─────────────────────────────────────────────────────────────────

function Spinner() {
  return (
    <svg
      className="animate-spin h-4 w-4"
      xmlns="http://www.w3.org/2000/svg"
      fill="none"
      viewBox="0 0 24 24"
      aria-hidden="true"
    >
      <circle
        className="opacity-25"
        cx="12" cy="12" r="10"
        stroke="currentColor"
        strokeWidth="4"
      />
      <path
        className="opacity-75"
        fill="currentColor"
        d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
      />
    </svg>
  );
}

// ─── Component ───────────────────────────────────────────────────────────────

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      children,
      prefixIcon,
      suffixIcon,
      variant  = "primary",
      size     = "md",
      fullWidth = false,
      isLoading = false,
      className = "",
      textSize,
      fontWeight,
      paddingX,
      paddingY,
      gap,
      rounded,
      disabled,
      type = "button",
      ...rest
    },
    ref
  ) => {
    const preset = sizePresets[size];

    const resolvedPaddingX  = paddingX  ?? preset.px;
    const resolvedPaddingY  = paddingY  ?? preset.py;
    const resolvedTextSize  = textSize  ?? preset.text;
    const resolvedGap       = gap       ?? preset.gap;
    const resolvedFontWeight= fontWeight ?? "font-semibold";
    const resolvedRounded   = rounded   ?? "rounded-lg";

    const isDisabled = disabled || isLoading;

    return (
      <button
        ref={ref}
        type={type}
        disabled={isDisabled}
        className={`
          inline-flex items-center justify-center
          transition-colors duration-150 cursor-pointer select-none
          focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2
          disabled:cursor-not-allowed
          ${variantClasses[variant]}
          ${resolvedPaddingX}
          ${resolvedPaddingY}
          ${resolvedTextSize}
          ${resolvedFontWeight}
          ${resolvedGap}
          ${resolvedRounded}
          ${fullWidth ? "w-full" : ""}
          ${className}
        `}
        {...rest}
      >
        {isLoading ? (
          <>
            <Spinner />
            <span>{children}</span>
          </>
        ) : (
          <>
            {prefixIcon && <span className="flex-shrink-0">{prefixIcon}</span>}
            <span>{children}</span>
            {suffixIcon && <span className="flex-shrink-0">{suffixIcon}</span>}
          </>
        )}
      </button>
    );
  }
);

Button.displayName = "Button";

export default Button;
