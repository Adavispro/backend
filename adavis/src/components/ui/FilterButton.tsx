"use client";

import { CaretDown } from "@phosphor-icons/react";

export interface FilterButtonProps {
  appliedCount?: number;
  onClick: () => void;
  label?: string;
}

export default function FilterButton({
  appliedCount = 0,
  onClick,
  label = "Filter",
}: FilterButtonProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="module-glass-control type-filter-button relative flex h-8 items-center gap-2 rounded-[4px] px-3 text-text-heading"
    >
      {label}
      <CaretDown size={12} weight="bold" />
      {appliedCount > 0 ? (
        <span className="absolute -right-2 -top-2 grid h-5 min-w-5 place-items-center rounded-full bg-[#F04444] px-1 text-[10px] font-semibold leading-none text-white">
          {appliedCount}
        </span>
      ) : null}
    </button>
  );
}
