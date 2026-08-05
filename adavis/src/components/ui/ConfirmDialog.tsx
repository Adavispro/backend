"use client";

import { WarningCircle, X } from "@phosphor-icons/react";
import Button from "./Button";

export interface ConfirmDialogProps {
  isOpen: boolean;
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  isConfirming?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export default function ConfirmDialog({
  isOpen,
  title,
  message,
  confirmLabel = "Confirm",
  cancelLabel = "Cancel",
  isConfirming = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[110] grid place-items-center bg-[#15304f]/20 px-5 backdrop-blur-[2px]">
      <section
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        aria-describedby="confirm-dialog-message"
        className="module-glass-panel w-full max-w-[390px] rounded-lg p-5 shadow-[0_22px_55px_rgba(20,43,70,0.22)]"
      >
        <div className="flex items-start gap-3">
          <span className="grid h-9 w-9 shrink-0 place-items-center rounded-[6px] border border-danger/15 bg-danger/10 text-danger">
            <WarningCircle size={19} weight="fill" />
          </span>
          <div className="min-w-0 flex-1">
            <h2
              id="confirm-dialog-title"
              className="text-[13px] font-semibold text-text-heading"
            >
              {title}
            </h2>
            <p
              id="confirm-dialog-message"
              className="mt-1.5 text-[10px] leading-4 text-text-secondary"
            >
              {message}
            </p>
          </div>
          <button
            type="button"
            aria-label="Close confirmation"
            onClick={onCancel}
            disabled={isConfirming}
            className="module-glass-control grid h-6 w-6 shrink-0 place-items-center rounded-[5px] text-text-tertiary"
          >
            <X size={12} weight="bold" />
          </button>
        </div>

        <div className="mt-5 flex justify-end gap-2.5">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            rounded="rounded-[4px]"
            onClick={onCancel}
            disabled={isConfirming}
          >
            {cancelLabel}
          </Button>
          <Button
            type="button"
            variant="danger"
            size="sm"
            rounded="rounded-[4px]"
            onClick={onConfirm}
            isLoading={isConfirming}
          >
            {confirmLabel}
          </Button>
        </div>
      </section>
    </div>
  );
}
