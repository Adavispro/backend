"use client";

import Image from "next/image";
import { CaretDown, PlayCircle } from "@phosphor-icons/react/dist/ssr";
import type { FormEvent, ReactNode } from "react";
import { Button } from "@/components/ui";
import areaIcon from "@/assets/icons/area-icon.svg";
import blockIcon from "@/assets/icons/block-icon.svg";
import clearIcon from "@/assets/icons/clear-icon.svg";
import dateIcon from "@/assets/icons/date-icon.svg";
import idIcon from "@/assets/icons/id-icon.svg";
import plantIcon from "@/assets/icons/plant-icon.svg";
import roomIcon from "@/assets/icons/room-icon.svg";
import type { AnalyticsValues } from "../data/types";

interface AnalyticsField {
  id: string;
  label: string;
  placeholder: string;
  icon: ReactNode;
  options: string[];
  disabled?: boolean;
}

export interface AnalyticsHierarchyRow {
  plant: string;
  block: string;
  area: string;
  roomNo: string;
  equipmentId: string;
}

const baseFields: Omit<AnalyticsField, "options" | "disabled">[] = [
  {
    id: "plant",
    label: "Plant",
    placeholder: "Select Plant",
    icon: <Image src={plantIcon} alt="" aria-hidden="true" className="h-[13px] w-[13px]" />,
  },
  {
    id: "block",
    label: "Block",
    placeholder: "Select Block",
    icon: <Image src={blockIcon} alt="" aria-hidden="true" className="h-[13px] w-[13px]" />,
  },
  {
    id: "area",
    label: "Area",
    placeholder: "Select Area",
    icon: <Image src={areaIcon} alt="" aria-hidden="true" className="h-[13px] w-[13px]" />,
  },
  {
    id: "roomNo",
    label: "Room No.",
    placeholder: "Select Room Number",
    icon: <Image src={roomIcon} alt="" aria-hidden="true" className="h-[13px] w-[13px]" />,
  },
  {
    id: "equipmentId",
    label: "Equipment ID",
    placeholder: "Select ID",
    icon: <Image src={idIcon} alt="" aria-hidden="true" className="h-[13px] w-[13px]" />,
  },
  {
    id: "dateRange",
    label: "Date Range",
    placeholder: "Select Date Range",
    icon: <Image src={dateIcon} alt="" aria-hidden="true" className="h-[13px] w-[13px]" />,
  },
];

const dateRangeOptions = [
  "Today",
  "Last 7 Days",
  "Last 1 Month",
  "Last 3 Months",
  "Specific Range",
];

const unique = (values: string[]) => Array.from(new Set(values)).sort();

const getAnalyticsFields = (
  values: AnalyticsValues,
  hierarchyRows: AnalyticsHierarchyRow[],
): AnalyticsField[] => {
  const plantOptions = unique(hierarchyRows.map((row) => row.plant));
  const blockOptions = unique(
    hierarchyRows
      .filter((row) => row.plant === values.plant)
      .map((row) => row.block),
  );
  const areaOptions = unique(
    hierarchyRows
      .filter(
        (row) => row.plant === values.plant && row.block === values.block,
      )
      .map((row) => row.area),
  );
  const roomOptions = unique(
    hierarchyRows
      .filter(
        (row) =>
          row.plant === values.plant &&
          row.block === values.block &&
          row.area === values.area,
      )
      .map((row) => row.roomNo),
  );
  const equipmentIdOptions = unique(
    hierarchyRows
      .filter(
        (row) =>
          row.plant === values.plant &&
          row.block === values.block &&
          row.area === values.area &&
          row.roomNo === values.roomNo,
      )
      .map((row) => row.equipmentId),
  );

  return baseFields.map((field) => {
    if (field.id === "plant") {
      return { ...field, options: [field.placeholder, ...plantOptions] };
    }
    if (field.id === "block") {
      return {
        ...field,
        options: [field.placeholder, ...blockOptions],
        disabled: values.plant === "Select Plant",
      };
    }
    if (field.id === "area") {
      return {
        ...field,
        options: [field.placeholder, ...areaOptions],
        disabled: values.block === "Select Block",
      };
    }
    if (field.id === "roomNo") {
      return {
        ...field,
        options: [field.placeholder, ...roomOptions],
        disabled: values.area === "Select Area",
      };
    }
    if (field.id === "equipmentId") {
      return {
        ...field,
        options: [field.placeholder, ...equipmentIdOptions],
        disabled: values.roomNo === "Select Room Number",
      };
    }

    return {
      ...field,
      options: [field.placeholder, ...dateRangeOptions],
    };
  });
};

export function getInitialAnalyticsValues() {
  return {
    ...Object.fromEntries(baseFields.map((field) => [field.id, field.placeholder])),
    startDate: "",
    endDate: "",
  };
}

export function isCompleteAnalyticsSelection(values: AnalyticsValues) {
  const isBaseComplete = baseFields.every((field) => values[field.id] !== field.placeholder);
  if (!isBaseComplete) return false;

  if (values.dateRange !== "Specific Range") return true;
  if (!values.startDate || !values.endDate) return false;

  return new Date(values.startDate).getTime() <= new Date(values.endDate).getTime();
}

function AnalyticsSelectField({
  field,
  value,
  onChange,
  compact = false,
}: {
  field: AnalyticsField;
  value: string;
  onChange: (id: string, value: string) => void;
  compact?: boolean;
}) {
  return (
    <label htmlFor={field.id} className="grid gap-2">
      <span className="type-filter-label flex items-center gap-1.5 text-text-heading">
        <span className="text-primary">{field.icon}</span>
        {field.label}
      </span>
      <span className={`module-glass-control relative flex items-center rounded-[4px] ${compact ? "h-8" : "h-9"}`}>
        <select
          id={field.id}
          name={field.id}
          value={value}
          disabled={field.disabled}
          onChange={(event) => onChange(field.id, event.target.value)}
          className={`type-filter-value h-full w-full appearance-none rounded-[4px] bg-transparent pr-8 text-text-secondary outline-none disabled:cursor-not-allowed disabled:opacity-60 ${compact ? "px-3" : "px-4"}`}
        >
          {field.options.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
        <CaretDown
          aria-hidden="true"
          size={compact ? 10 : 12}
          weight="bold"
          className="pointer-events-none absolute right-3 text-text-secondary"
        />
      </span>
    </label>
  );
}

export default function AnalyticsFormCard({
  values,
  hierarchyRows,
  onValueChange,
  onClear,
  onSubmit,
  compact = false,
}: {
  values: AnalyticsValues;
  hierarchyRows: AnalyticsHierarchyRow[];
  onValueChange: (id: string, value: string) => void;
  onClear: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  compact?: boolean;
}) {
  const analyticsFields = getAnalyticsFields(values, hierarchyRows);
  const isSpecificDateRange = values.dateRange === "Specific Range";

  return (
    <section className={compact ? "" : "module-glass-panel overflow-hidden rounded-lg"}>
      {!compact ? (
        <div className="flex items-start justify-between gap-5 px-6 pb-4 pt-5">
          <div>
            <h2 className="text-[18px] font-semibold leading-none text-text-heading">
              Overall Equipment Efficiency
            </h2>
            <p className="mt-3 text-[11px] font-normal text-text-secondary">
              Select details to view live data
            </p>
          </div>

          <Button
            type="button"
            variant="ghost"
            size="sm"
            prefixIcon={<Image src={clearIcon} alt="" aria-hidden="true" className="h-3.5 w-3.5 text-primary" />}
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
            : "grid min-h-[240px] gap-x-12 gap-y-7 border-t border-[#E6E6E6]/45 px-6 pb-5 pt-4 md:grid-cols-3"
        }
      >
        {analyticsFields.map((field) => (
          <AnalyticsSelectField
            key={field.id}
            field={field}
            value={values[field.id]}
            onChange={onValueChange}
            compact={compact}
          />
        ))}

        {isSpecificDateRange ? (
          <>
            <label htmlFor="startDate" className="grid gap-2">
              <span className="type-filter-label flex items-center gap-1.5 text-text-heading">
                <span className="text-primary">
                  <Image src={dateIcon} alt="" aria-hidden="true" className="h-[13px] w-[13px]" />
                </span>
                From Date
              </span>
              <span className={`module-glass-control relative flex items-center rounded-[4px] ${compact ? "h-8" : "h-9"}`}>
                <input
                  id="startDate"
                  name="startDate"
                  type="date"
                  value={values.startDate ?? ""}
                  onChange={(event) => onValueChange("startDate", event.target.value)}
                  className={`type-filter-value h-full w-full rounded-[4px] bg-transparent text-text-secondary outline-none ${compact ? "px-3" : "px-4"}`}
                  max={values.endDate || undefined}
                />
              </span>
            </label>

            <label htmlFor="endDate" className="grid gap-2">
              <span className="type-filter-label flex items-center gap-1.5 text-text-heading">
                <span className="text-primary">
                  <Image src={dateIcon} alt="" aria-hidden="true" className="h-[13px] w-[13px]" />
                </span>
                To Date
              </span>
              <span className={`module-glass-control relative flex items-center rounded-[4px] ${compact ? "h-8" : "h-9"}`}>
                <input
                  id="endDate"
                  name="endDate"
                  type="date"
                  value={values.endDate ?? ""}
                  onChange={(event) => onValueChange("endDate", event.target.value)}
                  className={`type-filter-value h-full w-full rounded-[4px] bg-transparent text-text-secondary outline-none ${compact ? "px-3" : "px-4"}`}
                  min={values.startDate || undefined}
                />
              </span>
            </label>
          </>
        ) : null}

        <div className={compact ? "mt-2 flex justify-end gap-2" : "flex items-end justify-end md:col-span-2"}>
          {compact ? (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              prefixIcon={<Image src={clearIcon} alt="" aria-hidden="true" className="h-3.5 w-3.5 text-primary" />}
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
            View OEE
          </Button>
        </div>

        {isSpecificDateRange && (!values.startDate || !values.endDate) ? (
          <p className="md:col-span-3 text-[10px] text-[#A06A00]">
            Please select both From Date and To Date for Specific Range.
          </p>
        ) : null}
      </form>
    </section>
  );
}
