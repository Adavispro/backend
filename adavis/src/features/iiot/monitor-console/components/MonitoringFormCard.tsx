"use client";

import Image from "next/image";
import { CaretDown, PlayCircle } from "@phosphor-icons/react/dist/ssr";
import type { FormEvent } from "react";
import { Button } from "@/components/ui";
import clearIcon from "@/assets/icons/clear-icon.svg";
import { monitoringFields } from "../data/data";
import type { MonitoringField, MonitoringValues } from "../data/types";

function MonitoringSelectField({
  field,
  value,
  onChange,
  compact = false,
}: {
  field: MonitoringField;
  value: string;
  onChange: (id: string, value: string) => void;
  compact?: boolean;
}) {
  return (
    <label htmlFor={field.id} className={compact ? "grid gap-2.5" : "grid gap-2"}>
      <span
        className={`${compact ? "text-[10px]" : "type-filter-label"} flex items-center gap-1.5 font-semibold text-text-heading`}
      >
        <Image
          src={field.icon}
          alt=""
          aria-hidden="true"
          className={compact ? "h-3 w-3" : "h-[13px] w-[13px]"}
        />
        {field.label}
        {field.required === false ? null : <span className="text-required">*</span>}
      </span>
      <span className={`module-glass-control relative flex items-center rounded-[4px] ${compact ? "h-8" : "h-9"}`}>
        {field.kind === "date" ? (
          <input
            id={field.id}
            name={field.id}
            type="date"
            value={value}
            onChange={(event) => onChange(field.id, event.target.value)}
            className={`${compact ? "text-[10px]" : "type-filter-value"} h-full w-full rounded-[4px] bg-transparent px-4 text-text-secondary outline-none`}
            required={field.required !== false}
          />
        ) : (
          <>
            <select
              id={field.id}
              name={field.id}
              value={value}
              onChange={(event) => onChange(field.id, event.target.value)}
              disabled={field.disabled}
              required={field.required !== false}
              className={`${compact ? "text-[10px]" : "type-filter-value"} h-full w-full appearance-none rounded-[4px] bg-transparent px-4 pr-8 text-text-secondary outline-none disabled:cursor-not-allowed disabled:opacity-60`}
            >
              {field.options.map((option) => (
                <option key={`${field.id}-${option.value}-${option.label}`} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <CaretDown
              aria-hidden="true"
              size={compact ? 10 : 12}
              weight="bold"
              className="pointer-events-none absolute right-3 text-text-secondary"
            />
          </>
        )}
      </span>
    </label>
  );
}

export default function MonitoringFormCard({
  values,
  fields = monitoringFields,
  onValueChange,
  onClear,
  onSubmit,
  compact = false,
}: {
  values: MonitoringValues;
  fields?: MonitoringField[];
  onValueChange: (id: string, value: string) => void;
  onClear: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  compact?: boolean;
}) {
  return (
    <section className={compact ? "" : "module-glass-panel overflow-hidden rounded-lg"}>
      {!compact ? (
        <div className="flex items-start justify-between gap-5 border-b border-[#E6E6E6]/45 px-6 py-5">
          <div>
            <h2 className="text-[18px] font-semibold leading-none text-text-heading">
              Equipment Monitoring
            </h2>
            <p className="mt-3 text-[11px] font-normal text-text-secondary">
              Select details to view live data
            </p>
          </div>

          <Button
            type="button"
            variant="ghost"
            size="sm"
            prefixIcon={
              <Image
                src={clearIcon}
                alt=""
                aria-hidden="true"
                className="h-3.5 w-3.5 text-primary"
              />
            }
            onClick={onClear}
            rounded="rounded-[3px]"
            textSize="text-[11px]"
            paddingX="px-5"
            paddingY="py-0"
            className="h-9 border-primary/35 bg-white/35 !text-primary hover:bg-white/65"
          >
            Clear All
          </Button>
        </div>
      ) : null}

      <form
        onSubmit={onSubmit}
        className={
          compact
            ? "grid gap-6 px-2"
            : "grid gap-x-12 gap-y-7 px-6 pb-5 pt-4 md:grid-cols-3"
        }
      >
        {fields.map((field) => (
          <MonitoringSelectField
            key={field.id}
            field={field}
            value={values[field.id]}
            onChange={onValueChange}
            compact={compact}
          />
        ))}

        <div className={compact ? "mt-2 flex justify-end gap-2" : "flex justify-end md:col-span-3"}>
          {compact ? (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              prefixIcon={
                <Image
                  src={clearIcon}
                  alt=""
                  aria-hidden="true"
                  className="h-3.5 w-3.5 text-primary"
                />
              }
              onClick={onClear}
              rounded="rounded-[3px]"
              textSize="text-[10px]"
              paddingX="px-3"
              paddingY="py-0"
              className="h-8 min-w-[98px] border-primary/35 bg-white/35 !text-primary hover:bg-white/65"
            >
              Clear All
            </Button>
          ) : null}
          <Button
            type="submit"
            size="sm"
            prefixIcon={<PlayCircle size={15} weight="fill" />}
            rounded="rounded-[4px]"
            textSize={compact ? "text-[10px]" : "text-[11px]"}
            paddingX={compact ? "px-4" : "px-5"}
            paddingY="py-0"
            className={`${compact ? "h-8 min-w-[126px]" : "h-9 min-w-[156px]"} shadow-[0_8px_18px_rgba(7,92,175,0.18)]`}
          >
            View Analytics
          </Button>
        </div>
      </form>
    </section>
  );
}
