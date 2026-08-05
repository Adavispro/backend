"use client";

import type { ReactNode } from "react";
import { X } from "@phosphor-icons/react";

export interface SidePanelProps {
  isOpen: boolean;
  title: string;
  children: ReactNode;
  onClose: () => void;
  widthClassName?: string;
}

export default function SidePanel({
  isOpen,
  title,
  children,
  onClose,
  widthClassName = "w-[360px]",
}: SidePanelProps) {
  if (!isOpen) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-[100] flex justify-end bg-white/60 backdrop-blur-[1px]">
      <aside
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={`h-full overflow-y-auto bg-white shadow-[-18px_0_32px_rgba(20,43,70,0.16)] ${widthClassName}`}
      >
        <div className="flex items-center justify-between border-b border-[#E6E6E6] px-4 py-4">
          <h2 className="text-[13px] font-semibold text-text-heading">{title}</h2>
          <button
            type="button"
            aria-label="Close panel"
            onClick={onClose}
            className="grid h-6 w-6 place-items-center rounded-full border border-[#C7D1DE] text-text-secondary hover:bg-[#F3F7FB]"
          >
            <X size={12} weight="bold" />
          </button>
        </div>

        <div className="px-5 py-5">{children}</div>
      </aside>
    </div>
  );
}
