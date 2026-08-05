"use client";

import type { ReactNode } from "react";
import { X } from "@phosphor-icons/react";

export interface DialogProps {
  isOpen: boolean;
  title: string;
  children: ReactNode;
  onClose: () => void;
  widthClassName?: string;
  contentClassName?: string;
}

export default function Dialog({
  isOpen,
  title,
  children,
  onClose,
  widthClassName = "max-w-[760px]",
  contentClassName = "",
}: DialogProps) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[110] grid place-items-center bg-[#DDEAF7]/70 px-5 py-8 backdrop-blur-[2px]">
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="dialog-title"
        className={`flex max-h-[calc(100dvh-64px)] w-full flex-col overflow-hidden rounded-lg border border-white/80 bg-white shadow-[0_24px_70px_rgba(20,43,70,0.2)] ${widthClassName}`}
      >
        <div className="flex shrink-0 items-center justify-between border-b border-[#E6E6E6] px-5 py-4">
          <h2 id="dialog-title" className="text-[13px] font-semibold text-text-heading">
            {title}
          </h2>
          <button
            type="button"
            aria-label="Close dialog"
            onClick={onClose}
            className="grid h-6 w-6 place-items-center rounded-full border border-[#C7D1DE] text-text-secondary transition-colors hover:bg-[#F3F7FB] hover:text-primary"
          >
            <X size={12} weight="bold" />
          </button>
        </div>

        <div className={`min-h-0 flex-1 overflow-y-auto ${contentClassName}`}>
          {children}
        </div>
      </section>
    </div>
  );
}
