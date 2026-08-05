"use client";

import React, { useId } from "react";

// ─── Types ──────────────────────────────────────────────────────────────────

export type TextFieldVariant = "outlined" | "filled" | "transparent";

export interface TextFieldProps
  extends Omit<
    React.InputHTMLAttributes<HTMLInputElement>,
    "className" | "prefix"
  > {
  /** Optional label rendered above the input */
  label?: string;
  /** Optional action rendered on the right side of the label row */
  labelAction?: React.ReactNode;
  /** Appends a red asterisk to the label when true (default: false) */
  required?: boolean;
  /** Controls whether required fields show a visual asterisk */
  showRequiredIndicator?: boolean;
  /** Hint / helper text shown below the input */
  hint?: string;
  /** Error message — replaces hint and applies error styling */
  error?: string;

  /**
   * Visual style of the input box.
   * - `outlined`    — white fill + visible border (default)
   * - `filled`      — light-muted fill, no visible border
   * - `transparent` — no background, no border
   */
  variant?: TextFieldVariant;

  /** Override the border color via a CSS color value (hex, rgb, var(--…)) */
  borderColor?: string;

  /** Node rendered inside the input on the left */
  prefixIcon?: React.ReactNode;
  /** Node rendered inside the input on the right */
  suffixIcon?: React.ReactNode;
  /** Click handler for the prefix icon wrapper */
  onPrefixIconClick?: () => void;
  /** Click handler for the suffix icon wrapper */
  onSuffixIconClick?: () => void;
  /** Accessible label for an interactive suffix icon */
  suffixIconLabel?: string;

  /** Additional classes applied to the outer wrapper div */
  className?: string;
  /** Additional classes applied to the <input> element */
  inputClassName?: string;
  /** Additional classes applied to the input container */
  containerClassName?: string;
  /** Additional classes applied to the <label> element */
  labelClassName?: string;
  /** Additional classes applied to the hint / error text */
  hintClassName?: string;
}

// ─── Variant style maps ──────────────────────────────────────────────────────

const variantBase: Record<TextFieldVariant, string> = {
  outlined:    "bg-white border border-transparent focus-within:border-primary",
  filled:      "bg-surface-muted border border-transparent focus-within:border-primary",
  transparent: "bg-transparent border border-transparent focus-within:border-primary",
};

// ─── Component ───────────────────────────────────────────────────────────────

const TextField = React.forwardRef<HTMLInputElement, TextFieldProps>(
  (
    {
      label,
      labelAction,
      required = false,
      showRequiredIndicator = true,
      hint,
      error,
      variant = "outlined",
      borderColor,
      prefixIcon,
      suffixIcon,
      onPrefixIconClick,
      onSuffixIconClick,
      suffixIconLabel,
      className = "",
      inputClassName = "",
      containerClassName = "",
      labelClassName = "",
      hintClassName = "",
      disabled = false,
      type = "text",
      id,
      ...inputProps
    },
    ref
  ) => {
    const autoId = useId();
    const inputId = id ?? autoId;

    const hasError = Boolean(error);
    const bottomText = error ?? hint;

    const borderStyle = borderColor ? { borderColor } : undefined;

    const wrapperBorderClass = hasError
      ? "border-danger focus-within:border-danger"
      : variantBase[variant];

    return (
      <div className={`flex flex-col gap-1 ${className}`}>
        {/* Label */}
        {label && (
          <div className="flex items-center justify-between gap-3">
            <label
              htmlFor={inputId}
              className={`type-field-label ${labelClassName}`}
            >
              {label}
              {required && showRequiredIndicator && (
                <span className="ml-0.5 text-danger" aria-hidden="true">
                  *
                </span>
              )}
            </label>
            {labelAction}
          </div>
        )}

        {/* Input row */}
        <div
          className={`
            flex items-center gap-2 rounded-md px-3 py-2.5
            transition-colors duration-150
            ${wrapperBorderClass}
            ${disabled ? "opacity-50 cursor-not-allowed" : ""}
            ${containerClassName}
          `}
          style={borderStyle}
        >
          {/* Prefix icon */}
          {prefixIcon && (
            <span
              className={`
                flex-shrink-0 text-text-secondary
                ${onPrefixIconClick ? "cursor-pointer hover:text-primary transition-colors" : ""}
              `}
              onClick={onPrefixIconClick}
              role={onPrefixIconClick ? "button" : undefined}
              tabIndex={onPrefixIconClick ? 0 : undefined}
              onKeyDown={
                onPrefixIconClick
                  ? (e) => e.key === "Enter" && onPrefixIconClick()
                  : undefined
              }
            >
              {prefixIcon}
            </span>
          )}

          {/* Input element */}
          <input
            ref={ref}
            id={inputId}
            type={type}
            disabled={disabled}
            aria-invalid={hasError}
            aria-describedby={bottomText ? `${inputId}-hint` : undefined}
            className={`
              flex-1 min-w-0 bg-transparent outline-none
              type-field-value placeholder:text-text-muted
              disabled:cursor-not-allowed
              ${inputClassName}
            `}
            {...inputProps}
          />

          {/* Suffix icon */}
          {suffixIcon &&
            (onSuffixIconClick ? (
              <button
                type="button"
                aria-label={suffixIconLabel}
                className="flex-shrink-0 cursor-pointer text-text-secondary transition-colors hover:text-primary"
                onClick={onSuffixIconClick}
              >
                {suffixIcon}
              </button>
            ) : (
              <span className="flex-shrink-0 text-text-secondary">
                {suffixIcon}
              </span>
            ))}
        </div>

        {/* Hint / error text */}
        {bottomText && (
          <p
            id={`${inputId}-hint`}
            className={`
              text-xs
              ${hasError ? "text-danger" : "text-text-secondary"}
              ${hintClassName}
            `}
          >
            {bottomText}
          </p>
        )}
      </div>
    );
  }
);

TextField.displayName = "TextField";

export default TextField;
