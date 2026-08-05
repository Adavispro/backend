"use client";

import Image from "next/image";
import { CaretDown, X } from "@phosphor-icons/react";
import { Button } from "@/components/ui";
import clearIcon from "@/assets/icons/clear-icon.svg";
import dateIcon from "@/assets/icons/date-icon.svg";
import modulesIcon from "@/assets/icons/modules.svg";

export interface AuditLogTableFilters {
  modules: string[];
  fromDate: string;
  toDate: string;
}

interface AuditLogFiltersSectionProps {
  filters: AuditLogTableFilters;
  moduleOptions: string[];
  onApply: () => void;
  onChange: (filters: AuditLogTableFilters) => void;
  onClear: () => void;
}

function addValue(values: string[], value: string) {
  if (!value || values.includes(value)) return values;
  return [...values, value];
}

function removeValue(values: string[], value: string) {
  return values.filter((item) => item !== value);
}

export default function AuditLogFiltersSection({
  filters,
  moduleOptions,
  onApply,
  onChange,
  onClear,
}: AuditLogFiltersSectionProps) {
  const handleAddModule = (value: string) => {
    onChange({ ...filters, modules: addValue(filters.modules, value) });
  };

  const handleRemoveModule = (value: string) => {
    onChange({ ...filters, modules: removeValue(filters.modules, value) });
  };

  return (
    <div className="grid gap-8">
      <div className="grid gap-2.5">
        <label
          htmlFor="auditModules"
          className="type-filter-label flex items-center gap-1.5 text-text-heading"
        >
          <Image
            src={modulesIcon}
            alt=""
            aria-hidden="true"
            className="h-3.5 w-3.5"
          />
          Modules
        </label>

        <span className="module-glass-control relative flex h-9 items-center rounded-[4px]">
          <select
            id="auditModules"
            value=""
            onChange={(event) => handleAddModule(event.target.value)}
            className="type-filter-value h-full w-full appearance-none rounded-[4px] bg-transparent px-3 pr-8 text-text-secondary outline-none"
          >
            <option value="">Select Status</option>
            {moduleOptions.map((module) => (
              <option key={module} value={module}>
                {module}
              </option>
            ))}
          </select>
          <CaretDown
            aria-hidden="true"
            size={11}
            weight="bold"
            className="pointer-events-none absolute right-3 text-text-secondary"
          />
        </span>

        {filters.modules.length > 0 ? (
          <div className="flex flex-wrap gap-2">
            {filters.modules.map((module) => (
              <button
                key={module}
                type="button"
                onClick={() => handleRemoveModule(module)}
                className="inline-flex items-center gap-1 rounded-full bg-[#EEF2F6] px-2 py-1 text-[9px] font-medium text-text-heading"
              >
                {module}
                <X size={9} weight="bold" />
              </button>
            ))}
          </div>
        ) : null}
      </div>

      <div className="grid gap-2.5">
        <span className="type-filter-label flex items-center gap-1.5 text-text-heading">
          <Image
            src={dateIcon}
            alt=""
            aria-hidden="true"
            className="h-3.5 w-3.5"
          />
          Activity Date Range
        </span>

        <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-3">
          <input
            type="date"
            value={filters.fromDate}
            onChange={(event) =>
              onChange({ ...filters, fromDate: event.target.value })
            }
            className="module-glass-control type-filter-value h-9 rounded-[4px] px-3 text-text-secondary outline-none"
          />
          <span className="text-[14px] text-text-secondary">-</span>
          <input
            type="date"
            value={filters.toDate}
            onChange={(event) =>
              onChange({ ...filters, toDate: event.target.value })
            }
            className="module-glass-control type-filter-value h-9 rounded-[4px] px-3 text-text-secondary outline-none"
          />
        </div>
      </div>

      <div className="mt-4 flex justify-end gap-3">
        <Button
          type="button"
          variant="ghost"
          size="sm"
          prefixIcon={
            <Image
              src={clearIcon}
              alt=""
              aria-hidden="true"
              className="h-3.5 w-3.5"
            />
          }
          rounded="rounded-[4px]"
          textSize="text-[10px]"
          paddingX="px-4"
          paddingY="py-0"
          className="h-9 border-primary/35 bg-white/35 !text-primary hover:bg-white/65"
          onClick={onClear}
        >
          Clear All
        </Button>
        <Button
          type="button"
          size="sm"
          rounded="rounded-[4px]"
          textSize="text-[10px]"
          paddingX="px-5"
          paddingY="py-0"
          className="h-9 shadow-[0_8px_18px_rgba(7,92,175,0.18)]"
          onClick={onApply}
        >
          Apply Filters
        </Button>
      </div>
    </div>
  );
}
